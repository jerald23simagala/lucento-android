#!/usr/bin/env python3
"""
Builds Lucento's bundled institution registry and artwork from the bank asset library and the wallet design system.

    python tools/institutions/build_institutions.py \
        --library  <...>/lucento-bank-assets/Global-Banking-Assets \
        --designs  <...>/lucento-wallet-design/registry \
        --node-tools <dir with node_modules/.bin/svgo>   # npm i svgo@3.3.2 (CSS/fill clean-up only)
    # Conversion uses Android's own Svg2Vector (com.android.tools:sdk-common, from the Gradle cache; JAVA_HOME = JDK 17+).

Outputs (all committed; CI and the app never need Node or the source libraries):
  app/src/main/assets/banks/institutions.json          trimmed registry: identity, card face, list icon
  app/src/main/res/drawable/inst_logo_<id>.xml           card logo (VectorDrawable)
  app/src/main/res/drawable/inst_icon_<id>.xml           square list icon (VectorDrawable)
  app/src/main/res/drawable-nodpi/inst_app_<id>.webp     square list icon from the official app icon (144px, never vectorised)
  app/src/main/java/com/lucento/app/core/banks/InstitutionArt.kt   id -> drawable maps
  docs/design/INSTITUTION_ASSETS.md                     what shipped, what fell back and why, sizes

Rules (HANDOFF-ASSETS.md, lucento-wallet-design docs): nothing is invented — ids, names, colours and files come from the
registries; a source the converter can't render faithfully falls back (logo -> monogram, icon -> app icon -> monogram);
logos are never recoloured (only the two monochrome-only logos carry the design's tint); card networks are not bundled.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
APP = os.path.join(REPO, "app", "src", "main")
DRAWABLE = os.path.join(APP, "res", "drawable")
NODPI = os.path.join(APP, "res", "drawable-nodpi")
ASSETS = os.path.join(APP, "assets", "banks")
KOTLIN = os.path.join(APP, "java", "com", "lucento", "app", "core", "banks", "InstitutionArt.kt")
REPORT = os.path.join(REPO, "docs", "design", "INSTITUTION_ASSETS.md")

# Things VectorDrawable cannot draw (gradients are supported on API 24+ and are converted).
UNSUPPORTED = re.compile(r"<(mask|filter|pattern|image|text|foreignObject)\b")
MAX_PATH_DATA = 32_000  # AAPT2 cannot encode longer strings in a compiled resource
APP_ICON_PX = 144  # 48dp at xxhdpi; the largest avatar is 40dp
WEBP_QUALITY = 85


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def luminance(hex_colour):
    h = hex_colour.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    lin = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in (r, g, b)]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


def contrast(a, b):
    la, lb = sorted((luminance(a), luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def monogram_for(name, background):
    """A fallback monogram from registry data only: the name's first letter on the brand colour."""
    letter = next((c for c in name if c.isalnum()), "?").upper()
    foreground = "#FFFFFF" if contrast(background, "#FFFFFF") >= contrast(background, "#000000") else "#000000"
    return {"text": letter, "bg": background, "fg": foreground}


def java_bin():
    home = os.environ.get("JAVA_HOME") or r"C:\Program Files\Android\Android Studio\jbr"
    return os.path.join(home, "bin", "java")


def converter_classpath():
    """Android's SVG converter and its runtime deps, from the Gradle cache AGP already populated."""
    cache = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1")
    wanted = [
        ("com.android.tools", "sdk-common", "31.7.3"),
        ("com.android.tools", "common", "31.7.3"),
        ("com.google.guava", "guava", "32.0.1-jre"),
        ("org.jetbrains.kotlin", "kotlin-stdlib", "2.0.21"),
    ]
    jars = []
    for group, artifact, version in wanted:
        folder = os.path.join(cache, group, artifact, version)
        found = [os.path.join(root, f) for root, _, files in os.walk(folder) for f in files
                 if f == f"{artifact}-{version}.jar"]
        if not found:
            sys.exit(f"missing {group}:{artifact}:{version} in the Gradle cache; run ./gradlew assembleDebug once")
        jars.append(found[0])
    return jars


def run(cmd):
    result = subprocess.run(cmd, capture_output=True, text=True, shell=os.name == "nt")
    if result.returncode != 0:
        sys.exit(f"command failed: {' '.join(cmd)}\n{result.stdout}\n{result.stderr}")


def convert(svgs, node_tools, workdir):
    """svgs: {res_name: source path}. Returns {res_name: vector xml text} for every file that converted faithfully,
    and {res_name: reason} for the rest."""
    src, mid, out = (os.path.join(workdir, d) for d in ("src", "mid", "out"))
    for d in (src, mid, out):
        os.makedirs(d, exist_ok=True)
    rejected = {}
    for name, path in svgs.items():
        text = open(path, encoding="utf-8").read()
        bad = UNSUPPORTED.search(text)
        if bad:
            rejected[name] = f"uses <{bad.group(1)}>"
            continue
        shutil.copy(path, os.path.join(src, name + ".svg"))
    bin_dir = os.path.join(node_tools, "node_modules", ".bin")
    config = os.path.join(os.path.dirname(os.path.abspath(__file__)), "svgo.config.cjs")
    run([os.path.join(bin_dir, "svgo"), "--config", config, "-f", src, "-o", mid, "--quiet"])
    runner = os.path.join(os.path.dirname(os.path.abspath(__file__)), "Svg2Vd.java")
    run([java_bin(), "-cp", os.pathsep.join(converter_classpath()), runner, mid, out])
    converted, warnings = {}, {}
    for name in svgs:
        if name in rejected:
            continue
        cleaned = open(os.path.join(mid, name + ".svg"), encoding="utf-8").read()
        target = os.path.join(out, name + ".xml")
        if not os.path.exists(target):
            rejected[name] = "converter produced nothing"
            continue
        xml = open(target, encoding="utf-8").read()
        err_file = os.path.join(out, name + ".err")
        errors = open(err_file, encoding="utf-8").read() if os.path.exists(err_file) else ""
        if "ERROR" in errors:
            rejected[name] = "converter: " + errors.strip().splitlines()[0][:120].replace("|", "/")
        elif "<path" not in xml:
            rejected[name] = "no drawable paths"
        elif "<clipPath" in cleaned and "clip-path" not in xml and not clips_cover_artboard(cleaned):
            rejected[name] = "clip path dropped by the converter"
        elif 'android:pathData=""' in xml:
            rejected[name] = "empty path data"
        elif any(not re.fullmatch(r"#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})", c)
                 for c in re.findall(r'android:(?:fillColor|strokeColor)="([^"@?]*)"', xml)):
            rejected[name] = "converter wrote an invalid colour"
        elif max((len(d) for d in re.findall(r'android:pathData="([^"]*)"', xml)), default=0) > MAX_PATH_DATA:
            rejected[name] = "path data over the resource compiler's 32 KB string limit"
        else:
            converted[name] = xml
            if errors.strip():
                warnings[name] = errors.strip().splitlines()[0][:120].replace("|", "/")
    return converted, rejected, warnings


RECT_PATH = re.compile(r"^M\s*(-?[\d.]+)[ ,]*(-?[\d.]+)\s*([hHvVlL][\s\d.,-]*)+[zZ]?$")


def clips_cover_artboard(svg):
    """True when every <clipPath> is one axis-aligned rectangle covering the whole viewBox (an exporter's artboard clip),
    so dropping it changes nothing. Anything else keeps the rejection."""
    import xml.etree.ElementTree as ET
    try:
        root = ET.fromstring(svg)
    except ET.ParseError:
        return False
    vb = [float(v) for v in re.split(r"[ ,]+", root.get("viewBox", "").strip())] if root.get("viewBox") else None
    if not vb or len(vb) != 4:
        return False
    vx, vy, vw, vh = vb
    clips = [el for el in root.iter() if el.tag.endswith("clipPath")]
    for clip in clips:
        shapes = [el for el in clip if not el.tag.endswith("title")]
        if len(shapes) != 1 or shapes[0].get("transform") or clip.get("transform"):
            return False
        shape = shapes[0]
        tag = shape.tag.split("}")[-1]
        if tag == "rect":
            x, y = float(shape.get("x", 0)), float(shape.get("y", 0))
            w, h = float(shape.get("width", 0)), float(shape.get("height", 0))
        elif tag == "path" and RECT_PATH.match(shape.get("d", "").strip()):
            box = path_bbox(shape.get("d"))
            if box is None:
                return False
            x, y, w, h = box
        else:
            return False
        tol = 0.01 * max(vw, vh)
        if x > vx + tol or y > vy + tol or x + w < vx + vw - tol or y + h < vy + vh - tol:
            return False
    return bool(clips)


def path_bbox(d):
    """Bounding box of a path made only of M/H/V/L (absolute or relative) segments; None if it isn't rectangular."""
    tokens = re.findall(r"[MmHhVvLlZz]|-?\d*\.?\d+(?:e-?\d+)?", d)
    x = y = 0.0
    pts, cmd, i = [], None, 0
    while i < len(tokens):
        t = tokens[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in "Zz":
                continue
            continue
        if cmd in ("M", "L"):
            x, y = float(tokens[i]), float(tokens[i + 1]); i += 2
        elif cmd in ("m", "l"):
            x, y = x + float(tokens[i]), y + float(tokens[i + 1]); i += 2
        elif cmd == "H":
            x = float(t); i += 1
        elif cmd == "h":
            x += float(t); i += 1
        elif cmd == "V":
            y = float(t); i += 1
        elif cmd == "v":
            y += float(t); i += 1
        else:
            return None
        pts.append((x, y))
    if len(pts) < 4:
        return None
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    if any(px not in (min(xs), max(xs)) or py not in (min(ys), max(ys)) for px, py in pts):
        return None
    return min(xs), min(ys), max(xs) - min(xs), max(ys) - min(ys)


def finish_vector(xml):
    """Lint: generated logos are long by nature; they are rasterised once and cached by the platform."""
    xml = xml.replace(
        'xmlns:android="http://schemas.android.com/apk/res/android"',
        'xmlns:android="http://schemas.android.com/apk/res/android"\n    '
        'xmlns:tools="http://schemas.android.com/tools"\n    tools:ignore="VectorPath,VectorRaster"',
        1,
    )
    return xml if xml.endswith("\n") else xml + "\n"


def write_webp(source, target):
    with Image.open(source) as image:
        image = image.convert("RGBA").resize((APP_ICON_PX, APP_ICON_PX), Image.LANCZOS)
        image.save(target, "WEBP", quality=WEBP_QUALITY, method=6)


def clean_generated():
    for folder, pattern in ((DRAWABLE, r"^inst_(logo|icon)_.*\.xml$"), (NODPI, r"^inst_app_.*\.webp$")):
        if os.path.isdir(folder):
            for f in os.listdir(folder):
                if re.match(pattern, f):
                    os.remove(os.path.join(folder, f))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--library", required=True)
    parser.add_argument("--designs", required=True)
    parser.add_argument("--node-tools", required=True)
    args = parser.parse_args()

    registry = [r for r in load(os.path.join(args.library, "registry", "institutions.json")) if r["type"] != "card_network"]
    designs = {d["institution_id"]: d for d in load(os.path.join(args.designs, "card-designs.json"))["designs"]}
    icons = {i["institution_id"]: i for i in load(os.path.join(args.designs, "institution-icons.json"))["icons"]}
    icon_overrides = {k: v for k, v in load(os.path.join(args.designs, "institution-icon-overrides.json")).items() if not k.startswith("_")}
    lib = lambda rel: os.path.join(args.library, rel.replace("/", os.sep))

    # 1. Which file each slot uses.
    logo_svgs, icon_svgs, icon_plan, decisions = {}, {}, {}, []
    for r in registry:
        rid, assets = r["id"], r["assets"]
        design, icon = designs[rid], icons[rid]
        if design.get("logo") and design["logo"].get("asset", "").endswith(".svg") and os.path.exists(lib(design["logo"]["asset"])):
            logo_svgs[f"inst_logo_{rid}"] = lib(design["logo"]["asset"])
        forced = icon_overrides.get(rid, {}).get("force_source")
        app_icon = lib(assets["app_icon"]) if assets.get("app_icon") and os.path.exists(lib(assets["app_icon"])) else None
        source = icon["source"]
        # Owner-delegated decision (Chase, N26, Robinhood and 42 others): an official app icon beats a recoloured
        # community glyph. The design system's order picked icon.svg first; for recoloured glyphs that is not the
        # institution's own artwork, so the recognisable official icon is used instead. Overrides (DANA) still win.
        if source == "icon_svg" and icon.get("color_mode") == "recolored" and app_icon and not forced:
            source = "app_icon"
            decisions.append(rid)
        icon_plan[rid] = {"source": source, "app_icon": None if forced else app_icon}
        if source == "icon_svg":
            icon_svgs[f"inst_icon_{rid}"] = lib(icon["asset"])
        elif source == "app_icon":
            icon_plan[rid]["app_icon"] = lib(icon["asset"]) if icon.get("asset") and icon["source"] == "app_icon" else app_icon

    # 2. Convert.
    clean_generated()
    os.makedirs(DRAWABLE, exist_ok=True)
    os.makedirs(NODPI, exist_ok=True)
    os.makedirs(ASSETS, exist_ok=True)
    with tempfile.TemporaryDirectory() as work:
        vectors, rejected, warnings = convert({**logo_svgs, **icon_svgs}, args.node_tools, work)
    for name, xml in vectors.items():
        with open(os.path.join(DRAWABLE, name + ".xml"), "w", encoding="utf-8", newline="\n") as f:
            f.write(finish_vector(xml))

    # 3. Registry + bitmaps.
    entries, logos, vector_icons, bitmap_icons, fallbacks = [], {}, {}, {}, []
    bitmap_by_hash = {}  # identical app icons (one app listed in two countries) ship once
    for r in registry:
        rid, design, icon = r["id"], designs[r["id"]], icons[r["id"]]
        brand = (r.get("branding") or {}).get("primary") or design["face"]["accent"]
        logo = design.get("logo")
        logo_res = f"inst_logo_{rid}"
        card_logo = None
        if logo and logo_res in vectors:
            logos[rid] = logo_res
            card_logo = {"slot": logo["slot"], "plate": logo["plate"] or "none", "white": bool(logo.get("white_logo")), "tint": logo.get("tint")}
        elif logo:
            fallbacks.append((rid, "card logo", rejected.get(logo_res, "no SVG logo")))
        mono = design.get("monogram")
        card_mono = {"text": mono["text"], "bg": mono["background"], "fg": mono["foreground"]} if mono else monogram_for(r["name"], brand)

        plan = icon_plan[rid]
        kind, icon_mono = "monogram", None
        if plan["source"] == "icon_svg" and f"inst_icon_{rid}" in vectors:
            kind = "vector"
            vector_icons[rid] = f"inst_icon_{rid}"
        else:
            if plan["source"] == "icon_svg":
                fallbacks.append((rid, "list icon", rejected.get(f"inst_icon_{rid}", "missing")))
            if plan["source"] in ("icon_svg", "app_icon") and plan["app_icon"]:
                kind = "bitmap"
                target = os.path.join(NODPI, f"inst_app_{rid}.webp")
                write_webp(plan["app_icon"], target)
                digest = hashlib.sha256(open(target, "rb").read()).hexdigest()
                if digest in bitmap_by_hash:
                    os.remove(target)
                    bitmap_icons[rid] = bitmap_by_hash[digest]
                else:
                    bitmap_by_hash[digest] = f"inst_app_{rid}"
                    bitmap_icons[rid] = f"inst_app_{rid}"
        if kind == "monogram":
            m = icon.get("monogram")
            icon_mono = {"text": m["text"], "bg": m["background"], "fg": m["foreground"]} if m else card_mono
        tile = icon.get("tile") or {}
        entries.append({
            "id": rid,
            "name": r["name"],
            "country": r["country"],
            "type": r["type"],
            "brand": brand,
            "card": {
                "template": design["template"],
                "stops": design["face"]["stops"],
                "angle": design["face"]["angle_deg"],
                "accent": design["face"]["accent"],
                "ink": design["ink"]["primary"],
                "muted_alpha": design["ink"]["muted_alpha"],
                "logo": card_logo,
                "monogram": card_mono,
            },
            "icon": {
                "kind": kind,
                "tile_bg": tile.get("background"),
                "tile_border": tile.get("border"),
                "padding_pct": tile.get("padding_pct", 0),
                "white_mark": bool(icon.get("white_mark")),
                "monogram": icon_mono,
            },
        })

    entries.sort(key=lambda e: e["id"])
    body = json.dumps({"schema_version": 1, "institutions": entries}, ensure_ascii=False, separators=(",", ":"))
    with open(os.path.join(ASSETS, "institutions.json"), "w", encoding="utf-8", newline="\n") as f:
        f.write(body + "\n")

    write_kotlin(logos, vector_icons, bitmap_icons)
    write_report(registry, logos, vector_icons, bitmap_icons, fallbacks, decisions, body, warnings)


def kotlin_map(name, mapping):
    lines = [f'        "{k}" to R.drawable.{v},' for k, v in sorted(mapping.items())]
    return f"    val {name}: Map<String, Int> by lazy {{\n        hashMapOf(\n" + "\n".join("    " + l for l in lines) + "\n        )\n    }\n"


def write_kotlin(logos, vector_icons, bitmap_icons):
    os.makedirs(os.path.dirname(KOTLIN), exist_ok=True)
    text = (
        "// Generated by tools/institutions/build_institutions.py. Do not edit by hand.\n"
        "package com.lucento.app.core.banks\n\n"
        "import com.lucento.app.R\n\n"
        "/** Bundled institution artwork by registry id. Ids without an entry fall back (see [InstitutionRepository]). */\n"
        "internal object InstitutionArt {\n"
        + kotlin_map("logos", logos) + "\n"
        + kotlin_map("vectorIcons", vector_icons) + "\n"
        + kotlin_map("bitmapIcons", bitmap_icons)
        + "}\n"
    )
    with open(KOTLIN, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def folder_bytes(folder, pattern):
    return sum(os.path.getsize(os.path.join(folder, f)) for f in os.listdir(folder) if re.match(pattern, f))


def write_report(registry, logos, vector_icons, bitmap_icons, fallbacks, decisions, body, warnings):
    sizes = {
        "Card logos (VectorDrawable XML)": folder_bytes(DRAWABLE, r"^inst_logo_.*\.xml$"),
        "List icons (VectorDrawable XML)": folder_bytes(DRAWABLE, r"^inst_icon_.*\.xml$"),
        "List icons (WebP, 144px)": folder_bytes(NODPI, r"^inst_app_.*\.webp$"),
        "Registry JSON": len(body.encode("utf-8")),
    }
    monograms = len(registry) - len(vector_icons) - len(bitmap_icons)
    lines = [
        "# Bundled institution assets",
        "",
        "Generated by `tools/institutions/build_institutions.py`; regenerate rather than editing. Sources: the bank asset",
        "library (`registry/institutions.json`) and the wallet design system (`card-designs.json`, `institution-icons.json`).",
        f"Registry digest: `{hashlib.sha256(body.encode('utf-8')).hexdigest()[:16]}`.",
        "",
        f"- Institutions bundled: **{len(registry)}** (card networks are not bundled: a wallet is never a network).",
        f"- Card logos: **{len(logos)}** vector; the rest use a monogram on the card.",
        f"- List icons: **{len(vector_icons)}** vector, **{len(bitmap_icons)}** official app icons (WebP), **{monograms}** monograms.",
        "",
        "## Decision: official app icon over a recoloured glyph",
        "Where the design system's order picked `icon.svg` but that icon is a recoloured community glyph (Simple Icons) and",
        "the institution's official app icon exists, the app icon is used. It is the institution's own, recognisable",
        "artwork; the glyph is not. Overrides still win (DANA keeps its icon.svg: its App Store icon is a seasonal promo).",
        f"Applies to {len(decisions)}: " + ", ".join(f"`{d}`" for d in sorted(decisions)) + ".",
        "",
        "## Size (uncompressed files; the APK delta is measured in docs/handover)",
        "",
        "| Group | Bytes |",
        "|---|---|",
    ] + [f"| {k} | {v:,} |" for k, v in sizes.items()] + [
        f"| **Total** | **{sum(sizes.values()):,}** |",
        "",
        "## Fallbacks",
        "",
        "| Institution | Slot | Reason |",
        "|---|---|---|",
    ] + [f"| `{rid}` | {slot} | {reason} |" for rid, slot, reason in sorted(fallbacks)] + [
        "",
        "## Converted with warnings (shipped; check visually)",
        "",
        "| Drawable | First warning |",
        "|---|---|",
    ] + [f"| `{n}` | {w} |" for n, w in sorted(warnings.items())] + [
        "",
        "## Largest drawables (rasterised once per size and cached by the platform)",
        "",
    ] + [f"- `{f}`: {os.path.getsize(os.path.join(DRAWABLE, f)):,} bytes" for f in sorted(
        (f for f in os.listdir(DRAWABLE) if f.startswith("inst_")), key=lambda f: -os.path.getsize(os.path.join(DRAWABLE, f)))[:5]] + [""]
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    with open(REPORT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    print(f"logos={len(logos)} vector_icons={len(vector_icons)} bitmap_icons={len(bitmap_icons)} monograms={monograms} "
          f"fallbacks={len(fallbacks)} bytes={sum(sizes.values()):,}")


if __name__ == "__main__":
    main()
