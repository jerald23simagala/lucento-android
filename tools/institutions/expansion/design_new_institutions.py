"""design_new_institutions.py <wallet-design root> <library root> <node-tools dir> <id,id,...> [--write]

Card designs and list icons for the given institution ids only, made with the wallet design system's own functions
(tools/designs.py, tools/icons.py); every other entry in registry/card-designs.json and institution-icons.json stays
byte-identical. The white-logo share is measured with sharp (whiteshare.js beside this file; `npm i sharp@0.33.5` in
<node-tools dir>) because cairosvg isn't available on Windows without Cairo; same 240px raster and thresholds as
designs.white_pixel_share. Overrides in registry/card-design-overrides.json are merged as designs.py does.
Without --write it only prints each design for review.
"""
import json
import os
import subprocess
import sys

W, LIB, NODE_TOOLS, IDS = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4].split(",")
WRITE = "--write" in sys.argv
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(W, "tools"))
import designs  # noqa: E402
import icons  # noqa: E402


def white_pixel_share(rel):
    if not rel:
        return None
    path = os.path.join(LIB, rel)
    if not os.path.exists(path) or not path.endswith(".svg"):
        return None
    out = subprocess.run(["node", os.path.join(HERE, "whiteshare.js"), path], capture_output=True, text=True,
                         env=dict(os.environ, SHARP_DIR=NODE_TOOLS), shell=os.name == "nt")
    return None if out.returncode or out.stdout.strip() == "null" else float(out.stdout.strip())


designs.white_pixel_share = white_pixel_share
icons.white_pixel_share = white_pixel_share

registry = json.load(open(os.path.join(LIB, "registry", "institutions.json"), encoding="utf-8"))
by_id = {r["id"]: r for r in registry}
card_path, icon_path = os.path.join(W, "registry", "card-designs.json"), os.path.join(W, "registry", "institution-icons.json")
cards, icon_doc = json.load(open(card_path)), json.load(open(icon_path))
old_cards = {d["institution_id"]: d for d in cards["designs"]}
old_icons = {d["institution_id"]: d for d in icon_doc["icons"]}
card_ov = json.load(open(os.path.join(W, "registry", "card-design-overrides.json"), encoding="utf-8"))


def merge(a, b):  # the deep merge in designs.py's __main__
    for k, v in b.items():
        if k.startswith("_"):
            if k == "_reason":
                a.setdefault("overrides", []).append(v)
            continue
        if isinstance(v, dict) and isinstance(a.get(k), dict):
            merge(a[k], v)
        else:
            a[k] = v


new_cards, new_icons = {}, {}
for rid in IDS:
    d = designs.design(by_id[rid])
    if rid in card_ov:
        merge(d, card_ov[rid])
    new_cards[rid] = d
    new_icons[rid] = dict(institution_id=rid, **icons.resolve(by_id[rid]))
    logo = d.get("logo") or {}
    print(f"{rid:18} {d['template']:20} stops={d['face']['stops']} ink={d['ink']['primary']} {d['ink']['balance_contrast']}"
          f" logo={'-' if not logo else logo['slot'] + '/' + str(logo['plate']) + ('/WHITE' if logo.get('white_logo') else '')}"
          f" [{logo.get('white_logo_evidence', '')}] icon={new_icons[rid]['source']}")

if WRITE:
    cards["designs"] = [new_cards.get(r["id"]) or old_cards[r["id"]] for r in registry]
    icon_doc["icons"] = [new_icons.get(r["id"]) or old_icons[r["id"]] for r in registry]
    for path, doc in ((card_path, cards), (icon_path, icon_doc)):
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(doc, indent=2))
    print("wrote", len(cards["designs"]), "designs and", len(icon_doc["icons"]), "icons")
