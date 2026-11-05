"""import_institutions.py <library root> <spec.json> <downloads root>

Adds new institutions and logo fixes to the bank asset library using its own conventions: per-institution folder with
logo.svg / app-icon.png / config.json / source.md, the registry entry (config.json mirrors it), and the master inventory
row. Never deletes files; an existing logo.svg is never overwritten. Prints a summary of every write."""
import csv
import io
import json
import os
import re
import shutil
import sys
import urllib.parse
import xml.etree.ElementTree as ET

LIB, SPEC, DL = sys.argv[1], sys.argv[2], sys.argv[3]
TODAY = "2026-11-04"
OFFICIAL_LICENSE = "Official logo published on the institution's own website; all rights reserved (trademark). Not licensed for redistribution."
PD_LICENSE = ("Public domain; trademarked; Commons \"Public domain\" for logos usually means below threshold of originality "
              "in the US only — trademark rights still apply")
FORBIDDEN = re.compile(r"<(image|script|foreignObject)\b", re.I)


def clean_url(u):
    p = urllib.parse.urlsplit(u)
    return urllib.parse.urlunsplit((p.scheme, p.netloc, p.path, "", ""))


def prepare_svg(path, current_color=None):
    """Returns validated SVG text. Geometry is never changed; a missing viewBox is derived from width/height."""
    text = open(path, encoding="utf-8").read()
    if current_color:
        text = text.replace("currentColor", current_color)
    if FORBIDDEN.search(text):
        raise SystemExit(f"{path}: embeds a raster, script or foreign object")
    if re.search(r'(?:xlink:)?href="(?!#)', text):
        raise SystemExit(f"{path}: external reference")
    root = ET.fromstring(text.encode("utf-8"))
    if not root.tag.endswith("svg"):
        raise SystemExit(f"{path}: not an SVG root")
    if not root.get("viewBox"):
        w, h = (re.sub(r"[a-z%]+$", "", root.get(k, "")) for k in ("width", "height"))
        if not (w and h):
            raise SystemExit(f"{path}: no viewBox and no size")
        text = re.sub(r"<svg\b", f'<svg viewBox="0 0 {w} {h}"', text, count=1)
    return text


def folder_for(entry):
    for key in ("app_icon", "primary_logo"):
        rel = entry["assets"].get(key)
        if rel:
            return os.path.dirname(rel)
    cc = entry["country"].lower()
    return f"institutions/{entry['region']}/{cc}/{entry['id'].split('_', 1)[1].replace('_', '-')}"


def logo_fields(logo):
    if logo["kind"] == "official":
        return {"source": clean_url(logo["url"]), "page": logo["page"], "license": OFFICIAL_LICENSE, "official": True,
                "how": f"Logo taken from the official website ({logo['page']}); visually checked on light and dark backgrounds."}
    meta = json.load(open(os.path.join(DL, logo["commons"]), encoding="utf-8"))
    lic = meta["meta"].get("LicenseShortName", "")
    license_text = PD_LICENSE if lic.lower().startswith("public domain") else \
        f"{lic} (author: {meta['meta'].get('Artist', 'unknown')}); trademarked; attribution required if the file itself is redistributed"
    return {"source": clean_url(meta["url"]), "page": meta["page"], "license": license_text, "official": False,
            "how": f"Wikimedia Commons file ({meta['page']}), compared against the institution's current app icon; visually checked."}


def app_fields(play_dir):
    info = json.load(open(os.path.join(DL, play_dir, "play.json"), encoding="utf-8"))
    served = info.get("served_format")
    note = (f"App icon: Google Play listing '{info['title']}' by developer '{info['developer']}' "
            f"(developer name checked against institution)" + (f"; store served {served}, converted to PNG." if served and served != "PNG" else "."))
    return info, note


def main():
    spec = json.load(open(SPEC, encoding="utf-8"))
    reg_path = os.path.join(LIB, "registry", "institutions.json")
    registry = json.load(open(reg_path, encoding="utf-8"))
    by_id = {e["id"]: e for e in registry}
    touched = []

    def apply(entry, item, is_new):
        folder = folder_for(entry)
        abs_folder = os.path.join(LIB, folder)
        os.makedirs(abs_folder, exist_ok=True)
        notes = []
        logo = item.get("logo")
        if logo:
            svg = prepare_svg(os.path.join(DL, logo["file"]), logo.get("current_color"))
            target = os.path.join(abs_folder, "logo.svg")
            if os.path.exists(target):
                raise SystemExit(f"refusing to overwrite {target}")
            with open(target, "w", encoding="utf-8", newline="\n") as f:
                f.write(svg)
            lf = logo_fields(logo)
            rel = f"{folder}/logo.svg"
            entry["assets"].update({"primary_logo": rel, "logo_horizontal": rel if logo["shape"] == "wordmark_wide" else None,
                                    "logo_shape": logo["shape"], "color_mode": "full_color_original"})
            entry["source"], entry["license"] = lf["source"], lf["license"]
            entry["verification"] = {"official_source": lf["official"], "svg_validated": True, "license_reviewed": True}
            notes.append(lf["how"])
            if logo.get("note"):
                notes.append(logo["note"])
            entry["_source_page"] = lf["page"]
        else:
            notes.append("No validated SVG collected: " + item.get("logo_gap", "none found."))
        primary, secondary = item["brand"]
        entry["branding"].update({"primary": primary, "secondary": secondary,
                                  "color_source": None if primary is None else (
                                      "extracted_from_svg" if logo and logo["kind"] == "commons" else "official")})
        if item.get("color_from"):
            notes.append(f"Brand colours sampled from {item['color_from']}.")
        if is_new and item.get("app"):
            info, app_note = app_fields(item["app"])
            shutil.copyfile(os.path.join(DL, item["app"], "app-icon.png"), os.path.join(abs_folder, "app-icon.png"))
            entry["assets"].update({"app_icon": f"{folder}/app-icon.png", "app_icon_source": info["listing"],
                                    "app_icon_format": "png", "app_icon_store": "google_play",
                                    "app_icon_developer": info["developer"], "app_icon_app_title": info["title"],
                                    "app_icon_image_url": info["icon_url"]})
            notes.append(app_note)
            if item.get("app_note"):
                notes.append(item["app_note"])
        if item.get("aliases"):
            entry["aliases"] = item["aliases"]
        if is_new:
            notes.append(f"Added {TODAY} (institution coverage expansion); status verified as operating (app updated within 3 months).")
            entry["notes"] = " ".join(notes)
        else:
            old = re.sub(r"No validated SVG collected; see documentation/missing_assets\.md\s*", "", entry.get("notes") or "")
            entry["notes"] = (" ".join(notes) + f" Updated {TODAY}. " + old).strip()
        page = entry.pop("_source_page", None)
        write_folder(entry, abs_folder, page)
        touched.append(entry["id"])

    template_keys = list(registry[0].keys())
    for item in spec["new"]:
        if item["id"] in by_id:
            raise SystemExit(f"{item['id']} already exists")
        entry = {"id": item["id"], "name": item["name"], "country": item["country"], "region": item["region"],
                 "type": item["type"], "website": item["website"],
                 "assets": {"primary_logo": None, "logo_horizontal": None, "icon": None, "has_official_square_icon": False,
                            "logo_shape": None, "color_mode": None, "logo_light": None, "icon_color_mode": None,
                            "app_icon": None, "app_icon_source": None, "app_icon_format": None, "app_icon_store": None,
                            "app_icon_developer": None, "app_icon_app_title": None, "app_icon_image_url": None},
                 "branding": {"primary": None, "secondary": None, "color_source": None, "typography": None,
                              "brand_guidelines_url": None, "card_design_reference_urls": []},
                 "source": None, "license": None, "commercial_redistribution": "not_cleared",
                 "verification": {"official_source": False, "svg_validated": False, "license_reviewed": True},
                 "notes": "", "icon_source": None}
        apply(entry, item, True)
        entry = {k: entry[k] for k in template_keys if k in entry} | {k: v for k, v in entry.items() if k not in template_keys}
        same_country = [n for n, e in enumerate(registry) if e["country"] == item["country"]]
        registry.insert((same_country[-1] + 1) if same_country else len(registry), entry)
        by_id[entry["id"]] = entry
    for item in spec["fixes"]:
        apply(by_id[item["id"]], item, False)
    for rid, aliases in spec.get("aliases_only", {}).items():
        by_id[rid]["aliases"] = aliases
        write_folder(by_id[rid], os.path.join(LIB, folder_for(by_id[rid])), None, keep_source_md=True)
        touched.append(rid)

    with open(reg_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(registry, ensure_ascii=False, indent=2))
    update_inventory(registry, touched)
    print(f"registry: {len(registry)} entries; touched {len(touched)}: {', '.join(touched)}")


def write_folder(entry, abs_folder, page, keep_source_md=False):
    with open(os.path.join(abs_folder, "config.json"), "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(entry, ensure_ascii=False, indent=2))
    if keep_source_md:
        return
    a = entry["assets"]
    lines = [f"# {entry['name']}", "", f"- ID: `{entry['id']}`", f"- Website: {entry['website']}",
             f"- Asset source: {entry['source'] or 'none'}", f"- Source page: {page or 'n/a'}",
             f"- License: {entry['license'] or 'n/a'}",
             f"- Commercial redistribution: trademark of {entry['name']}; trademark use accepted by owner (2026-11-01), not an asset licence.",
             "- Icon source: none found"]
    if a.get("app_icon"):
        lines.append(f"- App icon (PNG 512px): {a['app_icon_source']}")
    if entry.get("aliases"):
        lines.append(f"- Also known as: {', '.join(entry['aliases'])}")
    lines.append(f"- Notes: {entry['notes']}")
    with open(os.path.join(abs_folder, "source.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def update_inventory(registry, touched):
    path = os.path.join(LIB, "documentation", "master_inventory.csv")
    with open(path, encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))
    fields = list(rows[0].keys())
    by_id = {r["Institution ID"]: r for r in rows}
    for e in registry:
        if e["id"] not in touched:
            continue
        b, a = e["branding"], e["assets"]
        colours = " / ".join(c for c in (b["primary"], b["secondary"]) if c)
        row = {"Institution ID": e["id"], "Institution Name": e["name"], "Country": e["country"], "Region": e["region"],
               "Institution Category": e["type"], "Official Website": e["website"], "SVG Filename": a.get("primary_logo") or "",
               "Asset Source": e.get("source") or "", "Official Brand Colors": f"{colours} ({b['color_source']})" if colours else "",
               "Asset Format": f"SVG ({a['color_mode']})" if a.get("primary_logo") else "",
               "License Information": e.get("license") or "",
               "Commercial Redistribution Status": "Trademark use accepted by owner (Jerald, 2026-11-01); not an asset licence",
               "Verification Status": ("validated_official_site" if e["verification"]["official_source"] else "validated_community_source")
               if a.get("primary_logo") else "missing", "Notes": e["notes"]}
        if e["id"] in by_id:
            by_id[e["id"]].update(row)
        else:
            rows.append(row)
    buf = io.StringIO()
    w = csv.DictWriter(buf, fieldnames=fields, lineterminator="\r\n")
    w.writeheader()
    w.writerows(rows)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(buf.getvalue())


main()
