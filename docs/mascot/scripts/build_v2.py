"""Round-2 build: raw chroma sources -> keyed, edge-decontaminated, crystal-levelled,
2048 canvas masters (`*_v2.png`). Non-destructive: reads assets/source/generated_chroma,
writes assets/processed/*_v2_native1024.png and assets/{wellness_tiers,expressions}/*_v2.png.
Run from docs/mascot: python scripts/build_v2.py"""
import sys, json
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import mascot_assets as M
from PIL import Image

LEVEL = {"thriving": 1.0, "steady": 0.80, "getting_tight": 0.60, "concerned": 0.42, "over_plan": 0.28}
G = "assets/source/generated_chroma/"
# (tier, pose) -> raw source file. Note: v1 "steady_*" pose raws visually match thriving
# (top cowlick, pristine clothes) and are reassigned to thriving.
SRC = {
 ("thriving","hero_neutral"): "thriving_hero_neutral_v2", ("thriving","greeting_happy"): "steady_greeting_happy",
 ("thriving","thinking"): "steady_thinking", ("thriving","pointing"): "steady_pointing", ("thriving","caution"): "steady_caution",
 ("thriving","success"): "thriving_success_v2", ("thriving","sleeping"): "thriving_sleeping_v2",
 ("steady","hero_neutral"): "steady_hero_neutral_v2", ("steady","greeting_happy"): "steady_greeting_happy_v2",
 ("steady","thinking"): "steady_thinking_v2", ("steady","pointing"): "steady_pointing_v2", ("steady","caution"): "steady_caution_v2",
 ("steady","success"): "steady_success_v2", ("steady","sleeping"): "steady_sleeping_v2",
 ("getting_tight","hero_neutral"): "getting_tight_hero_neutral",
 ("concerned","hero_neutral"): "concerned_hero_neutral_v2", ("over_plan","hero_neutral"): "over_plan_hero_neutral_v2",
}
for t in ("getting_tight", "concerned", "over_plan"):
    for p in ("greeting_happy", "thinking", "pointing", "caution", "success", "sleeping"):
        SRC[(t, p)] = f"{t}_{p}"

def main():
    log = []
    for (t, p), src in SRC.items():
        raw = Image.open(G + src + "_chroma.jpg")
        keyed = M.chroma_key(raw)
        clean = M.decontaminate(keyed)
        lv_img, npx = M.set_crystal_level(clean, LEVEL[t])
        Path("assets/processed").mkdir(exist_ok=True)
        lv_img.save(f"assets/processed/{t}_{p}_v2_native1024.png")
        d = "wellness_tiers" if p == "hero_neutral" else "expressions"
        out = f"assets/{d}/lucento_{t}_{p}_v2.png"
        M.normalize(lv_img, 2048, 2.0).save(out)
        log.append({"tier": t, "pose": p, "source": G + src + "_chroma.jpg", "out": out,
                    "crystal_px": npx, "crystal_luma_before": round(M.crystal_luma(clean) or 0, 1),
                    "crystal_luma_after": round(M.crystal_luma(lv_img, M.crystal_mask(clean)) or 0, 1)})
        print(t, p, log[-1]["crystal_luma_before"], "->", log[-1]["crystal_luma_after"])
    Path("reports/build_v2_log.json").write_text(json.dumps(log, indent=2) + "\n")

if __name__ == "__main__":
    main()
