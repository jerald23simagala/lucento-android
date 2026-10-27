"""Unit tests for mascot_assets.py. Run: python -m pytest scripts/test_mascot_assets.py  (or python scripts/test_mascot_assets.py)"""
import json, sys, tempfile, unittest
from pathlib import Path
import numpy as np
from PIL import Image
sys.path.insert(0, str(Path(__file__).parent))
import mascot_assets as M


def checker_img(n=256, t=16):
    yy, xx = np.mgrid[0:n, 0:n]
    v = np.where(((xx // t + yy // t) % 2) == 0, 255, 204).astype(np.uint8)
    a = np.dstack([v, v, v]); a[96:160, 96:160] = (230, 140, 40)
    return Image.fromarray(a, "RGB")


class T(unittest.TestCase):
    def setUp(self):
        self.d = Path(tempfile.mkdtemp())

    def test_checkerboard_detected(self):
        s, t = M.checkerboard_score(checker_img())
        self.assertGreater(s, 0.6); self.assertEqual(t, 16)

    def test_flat_bg_not_checker(self):
        im = Image.new("RGB", (256, 256), (240, 236, 228))
        self.assertLess(M.checkerboard_score(im)[0], 0.6)

    def test_key_preserves_pale_and_yellow(self):
        a = np.zeros((64, 64, 3), np.uint8); a[:] = (14, 224, 30)
        a[10:20, 10:20] = (250, 248, 240)   # cream/white
        a[30:40, 30:40] = (255, 230, 120)   # pale gold highlight
        a[45:55, 45:55] = (128, 128, 128)   # neutral gray
        out = np.asarray(M.chroma_key(Image.fromarray(a)))
        self.assertTrue((out[10:20, 10:20, 3] == 255).all())
        self.assertTrue((out[30:40, 30:40, 3] == 255).all())
        self.assertTrue((out[45:55, 45:55, 3] == 255).all())
        self.assertTrue((out[0:5, 0:5, 3] == 0).all())

    def test_inspect_alpha(self):
        im = Image.new("RGBA", (100, 100), (0, 0, 0, 0)); im.paste((200, 120, 40, 255), (30, 30, 70, 70))
        p = self.d / "a.png"; im.save(p)
        i = M.inspect(p)
        self.assertTrue(i["alpha_genuine"]); self.assertEqual(i["content_bbox"], [30, 30, 70, 70]); self.assertFalse(i["edge_touch"])

    def test_inspect_rgb_no_alpha(self):
        p = self.d / "b.png"; Image.new("RGB", (10, 10)).save(p)
        self.assertFalse(M.inspect(p)["alpha_genuine"])

    def test_normalize_canvas(self):
        im = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
        self.assertEqual(M.normalize(im, 2048, 2.0).size, (2048, 2048))

    def test_refuse_overwrite(self):
        p = self.d / "c.png"; Image.new("RGB", (8, 8)).save(p)
        with self.assertRaises(SystemExit): M.safe_out(p, p)

    def test_missing_file_error(self):
        with self.assertRaises(SystemExit): M.load(self.d / "nope.png")

    def test_glow_levels_monotonic(self):
        peaks = [np.asarray(M.make_glow(64, lv))[..., 3].max() for lv in M.TIER_GLOW.values()]
        self.assertEqual(peaks, sorted(peaks, reverse=True)); self.assertGreater(peaks[-1], 0)

    def test_manifest_update(self):
        p = self.d / "x.png"; im = Image.new("RGBA", (50, 50), (0, 0, 0, 0)); im.paste((1, 2, 3, 255), (10, 10, 20, 20)); im.save(p)
        m = self.d / "m.json"; M.update_manifest(m, [p], self.d)
        a = json.loads(m.read_text())["assets"][0]
        self.assertEqual(a["dimensions"], [50, 50]); self.assertEqual(a["production_status"], "prototype")

    def test_decontaminate_removes_green_fringe_keeps_alpha(self):
        a = np.zeros((40, 40, 4), np.uint8); a[10:30, 10:30] = (100, 70, 40, 255)
        a[10:30, 10] = (120, 150, 40, 255)   # green-bled opaque edge column
        a[10:30, 9] = (100, 140, 40, 120)    # soft edge
        out = np.asarray(M.decontaminate(Image.fromarray(a, "RGBA"))).astype(int)
        self.assertTrue((out[..., 3] == a[..., 3]).all())          # alpha shape untouched
        self.assertTrue((out[10:30, 9:11, 1] <= np.maximum(out[10:30, 9:11, 0], out[10:30, 9:11, 2])).all())

    def test_crystal_dimming_only_touches_crystal(self):
        a = np.zeros((200, 200, 4), np.uint8)
        a[100:190, 20:120] = (245, 165, 40, 255)   # orange body
        a[20:60, 150:180] = (250, 240, 200, 255)   # pale crystal top-right
        im = Image.fromarray(a, "RGBA")
        out, n = M.set_crystal_level(im, 0.28)
        o = np.asarray(out)
        self.assertGreater(n, 0)
        self.assertTrue((o[100:190, 20:120] == a[100:190, 20:120]).all())   # body unchanged
        self.assertLess(o[40, 165, :3].mean(), a[40, 165, :3].mean())       # crystal dimmed
        self.assertGreater(o[40, 165, :3].mean(), 60)                       # still visible

    def test_crystal_levels_monotonic(self):
        a = np.zeros((200, 200, 4), np.uint8); a[20:60, 150:180] = (250, 240, 200, 255)
        im = Image.fromarray(a, "RGBA"); mk = M.crystal_mask(im)
        lum = [M.crystal_luma(M.set_crystal_level(im, lv)[0], mk) for lv in M.TIER_GLOW.values()]
        self.assertEqual(lum, sorted(lum, reverse=True))


if __name__ == "__main__":
    unittest.main(verbosity=2)
