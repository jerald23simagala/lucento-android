#!/usr/bin/env python3
"""Lucento mascot asset utilities (non-destructive).

Every command reads inputs and writes to a SEPARATE output path. Refuses to
overwrite an input file. Requires Python 3.9+, Pillow, numpy.

Commands:
  inspect   PATH...                  metadata, mode, alpha stats, checkerboard score (JSON)
  checker   PATH...                  baked-checkerboard detection only
  key       IN OUT [--tol N]         chroma-key (green/magenta) -> RGBA with despill
  normalize IN OUT [--size 2048 --scale S --anchor-y FRAC]  place on square transparent canvas
  preview   IN OUTDIR                render over light (#F5F1E8) and dark (#14161C) backgrounds
  thumb     IN OUT [--size 256]
  contact   OUT PATH... [--bg light|dark|checker]
  glow      OUTDIR [--size 1024]     procedural radial torch glow, 5 tier levels
  manifest  MANIFEST PATH...         refresh measured fields (dims/mode/alpha/bbox) in manifest JSON
"""
import argparse, json, os, sys
from pathlib import Path

try:
    import numpy as np
    from PIL import Image, ImageDraw
except ImportError as e:  # clear error
    sys.exit(f"ERROR: missing dependency ({e}). Install locally: python -m pip install --user pillow numpy")

LIGHT = (245, 241, 232)
DARK = (20, 22, 28)
TIER_GLOW = {"thriving": 1.00, "steady": 0.80, "getting_tight": 0.60, "concerned": 0.42, "over_plan": 0.28}


def die(msg):
    sys.exit(f"ERROR: {msg}")


def load(path):
    p = Path(path)
    if not p.is_file():
        die(f"file not found: {p}")
    try:
        im = Image.open(p)
        im.load()
        return im
    except Exception as e:
        die(f"cannot open image {p}: {e}")


def safe_out(inp, out):
    if Path(out).resolve() == Path(inp).resolve():
        die(f"refusing to overwrite input {inp}; choose a different output path")
    Path(out).parent.mkdir(parents=True, exist_ok=True)


def checkerboard_score(im, tile_sizes=(8, 10, 12, 16, 20, 24, 32)):
    """Detect a periodic two-tone gray/white tile pattern baked into RGB.
    Examines the image border band (where background lives). Returns best score
    in [0,1] (>0.6 = suspicious) and the tile size."""
    rgb = np.asarray(im.convert("RGB"), dtype=np.float32)
    g = rgb.mean(axis=2)
    sat = rgb.max(axis=2) - rgb.min(axis=2)
    h, w = g.shape
    band = max(16, min(h, w) // 8)
    mask = np.zeros_like(g, dtype=bool)
    mask[:band, :] = mask[-band:, :] = True
    mask[:, :band] = mask[:, -band:] = True
    mask &= sat < 18  # neutral pixels only
    if mask.sum() < 500:
        return 0.0, None
    yy, xx = np.mgrid[0:h, 0:w]
    best, best_t = 0.0, None
    for t in tile_sizes:
        for ox in (0, t // 2):
            par = (((xx + ox) // t + (yy + ox) // t) % 2).astype(bool)
            a, b = g[mask & par], g[mask & ~par]
            if len(a) < 100 or len(b) < 100:
                continue
            diff = abs(a.mean() - b.mean())
            spread = (a.std() + b.std()) / 2 + 1e-3
            if diff < 6:
                continue
            score = float(min(1.0, diff / (diff + 3 * spread)))
            if score > best:
                best, best_t = score, t
    return best, best_t


def inspect(path):
    im = load(path)
    info = {"file": str(path), "format": im.format, "size": list(im.size), "mode": im.mode}
    has_alpha = im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info)
    info["has_alpha_channel"] = has_alpha
    if has_alpha:
        a = np.asarray(im.convert("RGBA"))[:, :, 3]
        info["alpha"] = {
            "min": int(a.min()), "max": int(a.max()),
            "transparent_pct": round(float((a == 0).mean() * 100), 2),
            "opaque_pct": round(float((a == 255).mean() * 100), 2),
            "partial_pct": round(float(((a > 0) & (a < 255)).mean() * 100), 2),
        }
        info["alpha_genuine"] = bool(a.min() == 0 and (a == 0).mean() > 0.05)
        ys, xs = np.nonzero(a > 8)
        if len(xs):
            x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1
            info["content_bbox"] = [x0, y0, x1, y1]
            W, H = im.size
            info["edge_touch"] = bool(x0 == 0 or y0 == 0 or x1 == W or y1 == H)
            info["min_padding_px"] = min(x0, y0, W - x1, H - y1)
            # interior holes: transparent pixels fully enclosed inside opaque region rows
            solid = a > 128
            holes = 0
            for row in solid[y0:y1:4]:
                idx = np.nonzero(row)[0]
                if len(idx) > 1:
                    holes += int((~row[idx[0]:idx[-1]]).sum())
            info["interior_gap_px_sampled"] = holes
    else:
        info["alpha_genuine"] = False
    s, t = checkerboard_score(im)
    info["checkerboard_score"] = round(s, 3)
    info["checkerboard_tile"] = t
    info["checkerboard_suspected"] = s > 0.6
    return info


def chroma_key(im, tol=40, key="green"):
    rgb = np.asarray(im.convert("RGB"), dtype=np.float32)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    if key == "green":
        dom = g - np.maximum(r, b)
    else:  # magenta
        dom = np.minimum(r, b) - g
    # Only pixels where the KEY hue dominates are touched. Pale/cream/white/
    # yellow/gray pixels have dom <= ~10 and are never removed.
    alpha = np.clip(1.0 - (dom - 10) / tol, 0, 1)
    alpha[dom <= 10] = 1.0
    out = rgb.copy()
    # Un-mix edge pixels: C = a*F + (1-a)*K  =>  F = (C - (1-a)*K)/a, K = median border colour.
    hh, ww = r.shape
    border = np.concatenate([rgb[:8].reshape(-1, 3), rgb[-8:].reshape(-1, 3), rgb[:, :8].reshape(-1, 3), rgb[:, -8:].reshape(-1, 3)])
    K = np.median(border, axis=0)
    part = (alpha > 0.05) & (alpha < 1)
    a3 = alpha[part][:, None]
    out[part] = np.clip((rgb[part] - (1 - a3) * K) / a3, 0, 255)
    r, g, b = out[..., 0], out[..., 1], out[..., 2]
    if key == "green":  # despill
        lim = np.maximum(r, b)
        spill = g > lim
        out[..., 1] = np.where(spill, np.minimum(g, lim), g)
    else:
        lim = g
        out[..., 0] = np.where(r > b, np.minimum(r, np.maximum(b, lim + 20)), r)
        out[..., 2] = np.where(b > r, np.minimum(b, np.maximum(r, lim + 20)), b)
    a8 = (alpha * 255).round().astype(np.uint8)
    rgba = np.dstack([out.clip(0, 255).astype(np.uint8), a8])
    rgba[a8 == 0, :3] = 0
    return Image.fromarray(rgba, "RGBA")


def _shift(a, dy, dx):
    out = np.zeros_like(a)
    h, w = a.shape[:2]
    ys, yd = (slice(dy, h), slice(0, h - dy)) if dy >= 0 else (slice(0, h + dy), slice(-dy, h))
    xs, xd = (slice(dx, w), slice(0, w - dx)) if dx >= 0 else (slice(0, w + dx), slice(-dx, w))
    out[ys, xs] = a[yd, xd]
    return out


def decontaminate(im, band=3, iters=6):
    """Edge colour decontamination (non-destructive to alpha shape).
    Pixels that are semi-transparent or within `band` px of transparency get
    their RGB replaced by colour propagated from solid interior pixels, which
    removes key-colour (green/olive) fringe without deleting any pixels."""
    a = np.asarray(im.convert("RGBA")).astype(np.float32)
    rgb, al = a[..., :3], a[..., 3]
    solid = al >= 250
    interior = solid.copy()
    for _ in range(band):  # erode
        nb = interior.copy()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb &= _shift(interior, dy, dx)
        interior = nb
    edge = (al > 0) & ~interior
    known = interior.copy()
    col = np.where(known[..., None], rgb, 0)
    for _ in range(iters + band):
        acc = np.zeros_like(rgb); cnt = np.zeros(al.shape, np.float32)
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                k = _shift(known, dy, dx); acc += _shift(col, dy, dx) * k[..., None]; cnt += k
        newk = (~known) & (cnt > 0)
        col[newk] = acc[newk] / cnt[newk][:, None]
        known |= newk
    # blend: semi-transparent pixels fully replaced; opaque band pixels 50/50 (keeps hair texture)
    near = al < 250
    for _ in range(2):  # opaque pixels within 2px of the soft edge are JPEG-bled: replace fully
        d = near.copy()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            d |= _shift(near, dy, dx)
        near = d
    w = np.where(near, 1.0, 0.5)[..., None]
    fixed = np.where((edge & known)[..., None], rgb * (1 - w) + col * w, rgb)
    # also cap residual green dominance anywhere on edge
    g = fixed[..., 1]; lim = np.maximum(fixed[..., 0], fixed[..., 2])
    fixed[..., 1] = np.where(edge, np.minimum(g, lim), g)
    out = np.dstack([fixed.clip(0, 255), al]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def crystal_mask(im, x_min_frac=0.70, y_max_frac=0.50):
    """Locate the torch crystal: pale (blue-channel-high) opaque pixels in the
    upper-right region (torch hand side). The orange body and brass collar have
    low blue, so they are excluded. Returns bool mask (largest component)."""
    a = np.asarray(im.convert("RGBA")).astype(np.int32)
    h, w = a.shape[:2]
    r, g, b, al = a[..., 0], a[..., 1], a[..., 2], a[..., 3]
    m = (b > 70) & (g > 160) & (r > 190) & (al > 40)
    m[:, : int(w * x_min_frac)] = False
    m[int(h * y_max_frac):, :] = False
    if m.sum() == 0:
        return m
    from collections import deque
    lab = np.zeros(m.shape, np.int32); best, bestn, n = 0, 0, 0
    ys, xs = np.nonzero(m)
    for y0, x0 in zip(ys, xs):
        if lab[y0, x0]:
            continue
        n += 1; q = deque([(y0, x0)]); lab[y0, x0] = n; c = 0
        while q:
            y, x = q.popleft(); c += 1
            for yy, xx in ((y+1, x), (y-1, x), (y, x+1), (y, x-1)):
                if 0 <= yy < h and 0 <= xx < w and m[yy, xx] and not lab[yy, xx]:
                    lab[yy, xx] = n; q.append((yy, xx))
        if c > bestn:
            best, bestn = n, c
    mk = lab == best
    # closing (dilate 5, erode 3) fills darker facets and includes soft edges
    for _ in range(5):
        d = mk.copy()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            d |= _shift(mk, dy, dx)
        mk = d
    for _ in range(3):
        e = mk.copy()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            e &= _shift(mk, dy, dx)
        mk = e
    return mk & (al > 0)


def set_crystal_level(im, level, ref_luma=230.0):
    """Dim the crystal only (body untouched). Scales crystal pixels so mean
    crystal luminance = ref_luma*(0.35+0.65*level) (never brightens), with a
    warm amber tint as it dims. level 1.0 = unchanged."""
    a = np.asarray(im.convert("RGBA")).astype(np.float32)
    mk = crystal_mask(im)
    if level >= 0.999 or mk.sum() == 0:
        return im.convert("RGBA"), int(mk.sum())
    cur = float((a[..., :3][mk] @ np.array([0.2126, 0.7152, 0.0722])).mean())
    f = min(1.0, ref_luma * (0.35 + 0.65 * level) / max(cur, 1.0))
    t = 1 - level
    tint = np.array([1.0, 1.0 - 0.15 * t, 1.0 - 0.40 * t])
    rgb = a[..., :3]
    rgb[mk] = (rgb[mk] * f * tint).clip(0, 255)
    a[..., :3] = rgb
    return Image.fromarray(a.astype(np.uint8), "RGBA"), int(mk.sum())


def crystal_luma(im, mask=None):
    """Mean crystal luminance. Pass the mask detected BEFORE dimming when
    measuring a dimmed image (dimmed pixels no longer pass detection)."""
    mk = crystal_mask(im) if mask is None else mask
    if mk.sum() == 0:
        return None
    a = np.asarray(im.convert("RGB")).astype(np.float32)
    return float((a[mk] @ np.array([0.2126, 0.7152, 0.0722])).mean())


def normalize(im, size=2048, scale=2.0, anchor_y=None):
    """Scale whole frame uniformly (same transform for every asset => consistent
    placement) and centre on a square transparent canvas. No per-image re-fit."""
    im = im.convert("RGBA")
    w, h = im.size
    nw, nh = round(w * scale), round(h * scale)
    if nw > size or nh > size:
        die(f"scaled size {nw}x{nh} exceeds canvas {size}")
    # resample in premultiplied space (RGBa) so transparent pixels' RGB cannot bleed into edges
    rs = im.convert("RGBa").resize((nw, nh), Image.LANCZOS).convert("RGBA") if scale != 1 else im
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(rs, ((size - nw) // 2, (size - nh) // 2))
    return canvas


def on_bg(im, color):
    bg = Image.new("RGBA", im.size, color + (255,))
    bg.alpha_composite(im.convert("RGBA"))
    return bg.convert("RGB")


def checker_bg(size, t=32):
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w]
    v = np.where(((xx // t + yy // t) % 2) == 0, 200, 150).astype(np.uint8)
    return Image.fromarray(np.dstack([v, v, v]), "RGB")


def make_glow(size, level):
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    c = (size - 1) / 2
    d = np.sqrt((xx - c) ** 2 + (yy - c) ** 2) / c
    fall = np.clip(1 - d, 0, 1) ** 2.2
    a = (fall * 255 * level).astype(np.uint8)
    col = np.zeros((size, size, 3), np.uint8)
    col[..., 0], col[..., 1], col[..., 2] = 255, 206, 120  # warm gold #FFCE78
    return Image.fromarray(np.dstack([col, a]), "RGBA")


def contact(paths, bg="light", cell=320, cols=4, labels=True):
    n = len(paths)
    rows = (n + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell, rows * (cell + 24)), LIGHT if bg != "dark" else DARK)
    d = ImageDraw.Draw(sheet)
    for i, p in enumerate(paths):
        im = load(p).convert("RGBA")
        im.thumbnail((cell, cell), Image.LANCZOS)
        if bg == "checker":
            base = checker_bg(im.size, 16).convert("RGBA")
        else:
            base = Image.new("RGBA", im.size, (LIGHT if bg == "light" else DARK) + (255,))
        base.alpha_composite(im)
        x, y = (i % cols) * cell, (i // cols) * (cell + 24)
        sheet.paste(base.convert("RGB"), (x + (cell - im.width) // 2, y))
        if labels:
            d.text((x + 6, y + cell + 4), Path(p).stem[:44], fill=(40, 40, 40) if bg != "dark" else (220, 220, 220))
    return sheet


def update_manifest(mpath, paths, root):
    m = json.loads(Path(mpath).read_text()) if Path(mpath).exists() else {"assets": []}
    byfile = {a.get("filename"): a for a in m["assets"]}
    for p in paths:
        rel = os.path.relpath(p, root).replace(os.sep, "/")
        info = inspect(p)
        a = byfile.get(rel)
        if a is None:
            a = {"id": Path(p).stem, "filename": rel, "production_status": "prototype", "validation_status": "unvalidated"}
            m["assets"].append(a)
            byfile[rel] = a
        a.update({"dimensions": info["size"], "color_mode": info["mode"],
                  "alpha_status": ("genuine" if info["alpha_genuine"] else ("present_not_used" if info["has_alpha_channel"] else "none")),
                  "content_bbox": info.get("content_bbox"), "checkerboard_suspected": info["checkerboard_suspected"]})
    Path(mpath).write_text(json.dumps(m, indent=2) + "\n")
    return m


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = ap.add_subparsers(dest="cmd", required=True)
    s = sp.add_parser("inspect"); s.add_argument("paths", nargs="+")
    s = sp.add_parser("checker"); s.add_argument("paths", nargs="+")
    s = sp.add_parser("key"); s.add_argument("inp"); s.add_argument("out"); s.add_argument("--tol", type=float, default=40); s.add_argument("--key", choices=["green", "magenta"], default="green")
    s = sp.add_parser("normalize"); s.add_argument("inp"); s.add_argument("out"); s.add_argument("--size", type=int, default=2048); s.add_argument("--scale", type=float, default=2.0)
    s = sp.add_parser("preview"); s.add_argument("inp"); s.add_argument("outdir")
    s = sp.add_parser("thumb"); s.add_argument("inp"); s.add_argument("out"); s.add_argument("--size", type=int, default=256)
    s = sp.add_parser("contact"); s.add_argument("out"); s.add_argument("paths", nargs="+"); s.add_argument("--bg", default="light", choices=["light", "dark", "checker"]); s.add_argument("--cols", type=int, default=4)
    s = sp.add_parser("glow"); s.add_argument("outdir"); s.add_argument("--size", type=int, default=1024)
    s = sp.add_parser("manifest"); s.add_argument("manifest"); s.add_argument("paths", nargs="+"); s.add_argument("--root", default=".")
    a = ap.parse_args(argv)
    if a.cmd == "inspect":
        print(json.dumps([inspect(p) for p in a.paths], indent=2))
    elif a.cmd == "checker":
        for p in a.paths:
            s_, t = checkerboard_score(load(p)); print(f"{p}\tscore={s_:.3f}\ttile={t}\tsuspected={s_ > 0.6}")
    elif a.cmd == "key":
        safe_out(a.inp, a.out); chroma_key(load(a.inp), a.tol, a.key).save(a.out); print(f"wrote {a.out}")
    elif a.cmd == "normalize":
        safe_out(a.inp, a.out); normalize(load(a.inp), a.size, a.scale).save(a.out); print(f"wrote {a.out}")
    elif a.cmd == "preview":
        im = load(a.inp); o = Path(a.outdir); o.mkdir(parents=True, exist_ok=True); st = Path(a.inp).stem
        for name, c in (("light", LIGHT), ("dark", DARK)):
            r = on_bg(im, c); r.thumbnail((1024, 1024)); r.save(o / f"{st}__on_{name}.png")
        print(f"wrote previews for {st} in {o}")
    elif a.cmd == "thumb":
        safe_out(a.inp, a.out); im = load(a.inp).convert("RGBA"); im.thumbnail((a.size, a.size), Image.LANCZOS); im.save(a.out); print(f"wrote {a.out}")
    elif a.cmd == "contact":
        Path(a.out).parent.mkdir(parents=True, exist_ok=True); contact(a.paths, a.bg, cols=a.cols).save(a.out); print(f"wrote {a.out}")
    elif a.cmd == "glow":
        o = Path(a.outdir); o.mkdir(parents=True, exist_ok=True)
        for tier, lv in TIER_GLOW.items():
            make_glow(a.size, lv).save(o / f"torch_glow_{tier}.png")
        print(f"wrote 5 glow layers to {o}")
    elif a.cmd == "manifest":
        update_manifest(a.manifest, a.paths, a.root); print(f"updated {a.manifest}")


if __name__ == "__main__":
    main()
