"""Round-3 build (user-requested tier 4/5 facial hair + tears, and 5 cleanup fixes).
Same pipeline as build_v2 (key -> decontaminate -> crystal level -> premultiplied 2x to 2048).
Writes *_v3 files; v2 outputs were moved to assets/backup_v2/. Run from docs/mascot."""
import sys, json
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import mascot_assets as M
from PIL import Image
from build_v2 import SRC as SRC_V2, LEVEL, G

V3 = {(t, p) for t in ("concerned", "over_plan") for p in
      ("hero_neutral", "greeting_happy", "thinking", "pointing", "success", "caution", "sleeping")}
V3 |= {("getting_tight", "sleeping"), ("getting_tight", "success")}
SRC = dict(SRC_V2)
for k in V3:
    SRC[k] = f"{k[0]}_{k[1]}_v3"

def main():
    log = []
    for (t, p), src in SRC.items():
        clean = M.decontaminate(M.chroma_key(Image.open(G + src + "_chroma.jpg")))
        lv, npx = M.set_crystal_level(clean, LEVEL[t])
        lv.save(f"assets/processed/{t}_{p}_v3_native1024.png")
        d = "wellness_tiers" if p == "hero_neutral" else "expressions"
        out = f"assets/{d}/lucento_{t}_{p}_v3.png"
        M.normalize(lv, 2048, 2.0).save(out)
        mk = M.crystal_mask(clean)
        log.append({"tier": t, "pose": p, "source": G + src + "_chroma.jpg", "out": out, "changed_in_v3": (t, p) in V3,
                    "crystal_px": npx, "crystal_luma_before": round(M.crystal_luma(clean, mk) or 0, 1),
                    "crystal_luma_after": round(M.crystal_luma(lv, mk) or 0, 1)})
        print(t, p, log[-1]["crystal_luma_after"])
    Path("reports/build_v3_log.json").write_text(json.dumps(log, indent=2) + "\n")

if __name__ == "__main__":
    main()
