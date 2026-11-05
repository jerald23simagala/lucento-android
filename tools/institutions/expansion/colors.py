"""colors.py <raster.png> [--svg <source.svg>] [--top]  -> JSON {primary, secondary, top}

Pixels are binned to find the dominant colours by area; each reported colour is the mean of the real pixels in its bin,
snapped to the nearest colour the SVG declares when --svg is given (so official hex values survive). Chromatic colours
(HLS saturation >= 0.25, lightness 0.06-0.94) win over greys; near-white and transparent pixels are ignored."""
import colorsys
import json
import re
import sys
from collections import defaultdict

from PIL import Image


def declared(svg_path):
    text = open(svg_path, encoding="utf-8", errors="replace").read()
    out = set()
    for v in re.findall(r'(?:fill|stop-color|stroke)\s*[:=]\s*"?\s*(#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b)', text):
        v = v.upper()
        if len(v) == 4:
            v = "#" + "".join(c * 2 for c in v[1:])
        out.add(v)
    for r, g, b in re.findall(r'rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)', text):
        out.add("#%02X%02X%02X" % (int(r), int(g), int(b)))
    return sorted(out)


def to_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def main():
    args = sys.argv[1:]
    path = args[0]
    svg = args[args.index("--svg") + 1] if "--svg" in args else None
    im = Image.open(path).convert("RGBA")
    px = im.load()
    w, h = im.size
    bins = defaultdict(lambda: [0, 0, 0, 0])
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 200 or min(r, g, b) > 225:
                continue
            k = (r // 16, g // 16, b // 16)
            s = bins[k]
            s[0] += 1; s[1] += r; s[2] += g; s[3] += b
    total = sum(v[0] for v in bins.values()) or 1
    ranked = sorted(((v[0], (v[1] // v[0], v[2] // v[0], v[3] // v[0])) for v in bins.values()), reverse=True)

    def sat(c):
        _, l_, s_ = colorsys.rgb_to_hls(*(v / 255 for v in c))
        return s_ if 0.06 < l_ < 0.94 else 0

    decl = [to_rgb(d) for d in declared(svg)] if svg else []

    def snap(c):
        if not decl:
            return c
        best = min(decl, key=lambda d: sum((x - y) ** 2 for x, y in zip(c, d)))
        return best if sum((x - y) ** 2 for x, y in zip(c, best)) ** 0.5 < 60 else c

    merged = []
    for n, c in ranked:
        c = snap(c)
        for m in merged:
            if sum((x - y) ** 2 for x, y in zip(m[1], c)) ** 0.5 < 40:
                m[0] += n
                break
        else:
            merged.append([n, c])
    merged.sort(reverse=True)
    hexs = lambda c: "#%02X%02X%02X" % c
    chroma = [m for m in merged if sat(m[1]) >= 0.25 and m[0] / total >= 0.015]
    pool = chroma or [m for m in merged if m[0] / total >= 0.05]
    primary = pool[0][1] if pool else None
    secondary = next((m[1] for m in pool[1:] if sum((x - y) ** 2 for x, y in zip(m[1], primary)) ** 0.5 > 90), None) if primary else None
    print(json.dumps({"primary": primary and hexs(primary), "secondary": secondary and hexs(secondary),
                      "top": [f"{hexs(c)}:{n / total:.2f}" for n, c in merged[:5]]}))


main()
