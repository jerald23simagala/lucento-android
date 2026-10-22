"""Tiny Lottie (Bodymovin 5.x) authoring helpers for Lucento's original animations.

Only features that lottie-android renders natively are used: shape layers, groups, ellipses, rects,
bezier paths, fills, strokes, trim paths and transforms. No expressions, effects, mattes, images,
text layers or merge paths.
"""
import json
import math

FPS = 60


def hexc(h, a=1.0):
    h = h.lstrip('#')
    return [round(int(h[i:i + 2], 16) / 255, 4) for i in (0, 2, 4)] + [a]


# ---------------------------------------------------------------- easing / keyframes
EASE = ([0.42], [0.0], [0.58], [1.0])        # ease in-out
EASE_OUT = ([0.0], [0.0], [0.2], [1.0])      # decelerate
EASE_IN = ([0.4], [0.0], [1.0], [1.0])
SOFT = ([0.33], [0.0], [0.25], [1.0])        # gentle standard
OVERSHOOT = ([0.34], [0.0], [0.36], [1.42])  # restrained overshoot (y > 1)
LINEAR = ([0.0], [0.0], [1.0], [1.0])


def static(v):
    return {"a": 0, "k": v}


def anim(keys, ease=SOFT):
    """keys: list of (frame, value) or (frame, value, ease). Value may be a number or list."""
    out = []
    for idx, key in enumerate(keys):
        t, v = key[0], key[1]
        e = key[2] if len(key) > 2 else ease
        s = v if isinstance(v, list) else [v]
        kf = {"t": t, "s": s}
        if idx < len(keys) - 1:
            ox, oy, ix, iy = e
            kf["o"] = {"x": ox, "y": oy}
            kf["i"] = {"x": ix, "y": iy}
        out.append(kf)
    if len(out) == 1:
        return static(keys[0][1])
    return {"a": 1, "k": out}


def prop(v):
    """Accept a static value or an already-built animated property."""
    if isinstance(v, dict) and "a" in v:
        return v
    return static(v)


# ---------------------------------------------------------------- shapes
def tr(p=(0, 0), a=(0, 0), s=(100, 100), r=0, o=100):
    return {
        "ty": "tr",
        "p": prop(list(p) if not isinstance(p, dict) else p),
        "a": prop(list(a) if not isinstance(a, dict) else a),
        "s": prop(list(s) if not isinstance(s, dict) else s),
        "r": prop(r), "o": prop(o),
        "sk": static(0), "sa": static(0), "nm": "Transform",
    }


def group(name, items, **t):
    return {"ty": "gr", "nm": name, "np": len(items), "cix": 2, "bm": 0, "ix": 1, "it": list(items) + [tr(**t)]}


def ellipse(cx, cy, w, h=None, name="Ellipse"):
    h = w if h is None else h
    return {"ty": "el", "nm": name, "d": 1, "p": prop([cx, cy]), "s": prop([w, h])}


def ellipse_anim(p, s, name="Ellipse"):
    return {"ty": "el", "nm": name, "d": 1, "p": prop(p), "s": prop(s)}


def rect(cx, cy, w, h, r=0, name="Rect"):
    return {"ty": "rc", "nm": name, "d": 1, "p": prop([cx, cy]), "s": prop([w, h]), "r": prop(r)}


def rect_anim(p, s, r=0, name="Rect"):
    return {"ty": "rc", "nm": name, "d": 1, "p": prop(p), "s": prop(s), "r": prop(r)}


def path(points, closed=False, ins=None, outs=None, name="Path"):
    n = len(points)
    ins = ins or [[0, 0]] * n
    outs = outs or [[0, 0]] * n
    return {"ty": "sh", "nm": name, "d": 1, "ks": static({"c": closed, "v": [list(p) for p in points], "i": ins, "o": outs})}


def path_anim(keys, ease=SOFT, name="Path"):
    """keys: (frame, points, closed, ins, outs). All keyframes must share the vertex count."""
    frames = []
    for idx, (t, pts, closed, ins, outs) in enumerate(keys):
        n = len(pts)
        shape = {"c": closed, "v": [list(p) for p in pts], "i": ins or [[0, 0]] * n, "o": outs or [[0, 0]] * n}
        kf = {"t": t, "s": [shape]}
        if idx < len(keys) - 1:
            ox, oy, ix, iy = ease
            kf["o"] = {"x": ox, "y": oy}
            kf["i"] = {"x": ix, "y": iy}
        frames.append(kf)
    return {"ty": "sh", "nm": name, "d": 1, "ks": {"a": 1, "k": frames}}


def arc_path(cx, cy, rx, ry, start_deg, sweep_deg, segments=4):
    """Open bezier arc. Angles in degrees, 0 = +x, clockwise on screen (y down)."""
    pts, ins, outs = [], [], []
    step = sweep_deg / segments
    k = 4 / 3 * math.tan(math.radians(step) / 4)
    for i in range(segments + 1):
        a = math.radians(start_deg + step * i)
        x, y = cx + rx * math.cos(a), cy + ry * math.sin(a)
        tx, ty = -rx * math.sin(a) * k, ry * math.cos(a) * k
        pts.append([round(x, 3), round(y, 3)])
        ins.append([round(-tx, 3), round(-ty, 3)])
        outs.append([round(tx, 3), round(ty, 3)])
    ins[0] = [0, 0]
    outs[-1] = [0, 0]
    return pts, ins, outs


def fill(color, o=100, name="Fill"):
    return {"ty": "fl", "nm": name, "c": prop(color), "o": prop(o), "r": 1, "bm": 0}


def stroke(color, w, o=100, cap=2, join=2, name="Stroke"):
    return {"ty": "st", "nm": name, "c": prop(color), "o": prop(o), "w": prop(w), "lc": cap, "lj": join, "ml": 4, "bm": 0}


def trim(s=0, e=100, o=0, name="Trim"):
    return {"ty": "tm", "nm": name, "s": prop(s), "e": prop(e), "o": prop(o), "m": 1}


def sparkle(cx, cy, r, color, name="Sparkle"):
    """Four-point star built from one closed bezier path."""
    c = 0.18 * r
    pts = [[cx, cy - r], [cx + c, cy - c], [cx + r, cy], [cx + c, cy + c], [cx, cy + r], [cx - c, cy + c], [cx - r, cy], [cx - c, cy - c]]
    return group(name, [path(pts, closed=True), fill(color)])


# ---------------------------------------------------------------- layers / document
def layer(name, shapes, ind, op, ip=0, p=(0, 0), a=(0, 0), s=(100, 100), r=0, o=100, parent=None):
    ks = {
        "o": prop(o), "r": prop(r),
        "p": prop(list(p) + [0] if isinstance(p, (list, tuple)) else p),
        "a": prop(list(a) + [0] if isinstance(a, (list, tuple)) else a),
        "s": prop(list(s) + [100] if isinstance(s, (list, tuple)) else s),
    }
    lay = {"ddd": 0, "ind": ind, "ty": 4, "nm": name, "sr": 1, "ks": ks, "ao": 0, "shapes": shapes,
           "ip": ip, "op": op, "st": 0, "bm": 0}
    if parent is not None:
        lay["parent"] = parent
    return lay


def null_layer(name, ind, op, p=(0, 0), a=(0, 0), s=(100, 100), r=0, parent=None):
    ks = {
        "o": static(0), "r": prop(r),
        "p": prop(list(p) + [0] if isinstance(p, (list, tuple)) else p),
        "a": prop(list(a) + [0] if isinstance(a, (list, tuple)) else a),
        "s": prop(list(s) + [100] if isinstance(s, (list, tuple)) else s),
    }
    lay = {"ddd": 0, "ind": ind, "ty": 3, "nm": name, "sr": 1, "ks": ks, "ao": 0, "ip": 0, "op": op, "st": 0, "bm": 0}
    if parent is not None:
        lay["parent"] = parent
    return lay


def doc(name, w, h, op, layers, markers=None):
    # Lottie draws the first layer on top; callers list layers back-to-front, so reverse here.
    d = {"v": "5.7.4", "fr": FPS, "ip": 0, "op": op, "w": w, "h": h, "nm": name, "ddd": 0, "assets": [],
         "layers": list(reversed(layers)), "meta": {"g": "Lucento original — generated by tools/lottie/gen"}}
    if markers:
        d["markers"] = [{"tm": t, "cm": n, "dr": dr} for (n, t, dr) in markers]
    return d


def write(d, path_):
    with open(path_, "w") as f:
        json.dump(d, f, separators=(",", ":"))


def rnd(x):
    """Round floats recursively to keep files small."""
    if isinstance(x, float):
        return round(x, 3)
    if isinstance(x, list):
        return [rnd(i) for i in x]
    if isinstance(x, dict):
        return {k: rnd(v) for k, v in x.items()}
    return x
