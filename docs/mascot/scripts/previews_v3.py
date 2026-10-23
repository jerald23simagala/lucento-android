"""Round-3 previews. Run from docs/mascot."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import mascot_assets as M
from PIL import Image
T = ["thriving", "steady", "getting_tight", "concerned", "over_plan"]
P = ["hero_neutral", "greeting_happy", "thinking", "pointing", "success", "caution", "sleeping"]
f = lambda t, p: f"assets/{'wellness_tiers' if p == 'hero_neutral' else 'expressions'}/lucento_{t}_{p}_v3.png"
pv = Path("assets/previews"); (pv / "individual").mkdir(parents=True, exist_ok=True)
allp = [f(t, p) for t in T for p in P]
for bg in ("light", "dark"):
    M.contact(allp, bg, cell=300, cols=7).save(pv / f"contact_sheet_v3_{bg}.png")
co = [f(t, p) for t in ("concerned", "over_plan") for p in P]
M.contact(co, "light", cell=420, cols=7).save(pv / "contact_sheet_v3_concerned_overplan.png")
M.contact(co, "dark", cell=420, cols=7).save(pv / "contact_sheet_v3_concerned_overplan_dark.png")
# face zoom on dark: crop head region of native processed
crops = []
for t in ("concerned", "over_plan"):
    for p in P:
        im = Image.open(f"assets/processed/{t}_{p}_v3_native1024.png").convert("RGBA")
        bb = im.getchannel("A").getbbox(); x0, y0, x1, _ = bb
        crops.append(M.on_bg(im.crop((x0, y0, x1, y0 + int((x1 - x0) * 0.8))), M.DARK))
w = 360; sheet = Image.new("RGB", (w * 7, w * 2), M.DARK)
for i, c in enumerate(crops):
    c.thumbnail((w, w)); sheet.paste(c, ((i % 7) * w, (i // 7) * w))
sheet.save(pv / "qa_faces_concerned_overplan_on_dark_v3.png")
for p in allp:
    im = Image.open(p)
    for bg, col in (("light", M.LIGHT), ("dark", M.DARK)):
        t = M.on_bg(im, col); t.thumbnail((768, 768)); t.save(pv / "individual" / f"{Path(p).stem}_on_{bg}.png")
