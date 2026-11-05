"""vd2svg.py <in.xml> <out.svg> -- renders an Android VectorDrawable back to SVG for visual checks (groups with
translate/scale, clip-path, solid and gradient fills, strokes, evenOdd). Not a general converter."""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"


def color(v):
    if not v:
        return None, 1.0
    v = v.lstrip("#")
    if len(v) == 8:
        return "#" + v[2:], int(v[:2], 16) / 255
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    return "#" + v[-6:], 1.0


defs, counter = [], [0]


def gradient(el):
    counter[0] += 1
    gid = f"g{counter[0]}"
    kind = el.get(A + "type", "linear")
    stops = []
    items = [i for i in el if i.tag == "item"]
    if items:
        for it in items:
            c, a = color(it.get(A + "color"))
            stops.append(f'<stop offset="{it.get(A + "offset")}" stop-color="{c}" stop-opacity="{a}"/>')
    else:
        for off, key in ((0, "startColor"), (1, "endColor")):
            c, a = color(el.get(A + key))
            stops.append(f'<stop offset="{off}" stop-color="{c}" stop-opacity="{a}"/>')
    if kind == "radial":
        defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{el.get(A + "centerX")}" cy="{el.get(A + "centerY")}" r="{el.get(A + "gradientRadius")}">{"".join(stops)}</radialGradient>')
    else:
        defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{el.get(A + "startX")}" y1="{el.get(A + "startY")}" x2="{el.get(A + "endX")}" y2="{el.get(A + "endY")}">{"".join(stops)}</linearGradient>')
    return f"url(#{gid})"


def walk(el):
    out = []
    clip_open = 0
    for ch in el:
        tag = ch.tag
        if tag == "group":
            t = []
            tx, ty = ch.get(A + "translateX", "0"), ch.get(A + "translateY", "0")
            sx, sy = ch.get(A + "scaleX", "1"), ch.get(A + "scaleY", "1")
            px, py = ch.get(A + "pivotX", "0"), ch.get(A + "pivotY", "0")
            rot = ch.get(A + "rotation", "0")
            t = f'translate({tx},{ty}) translate({px},{py}) rotate({rot}) scale({sx},{sy}) translate(-{px},-{py})'
            out.append(f'<g transform="{t}">' + walk(ch) + "</g>")
        elif tag == "clip-path":
            counter[0] += 1
            cid = f"c{counter[0]}"
            defs.append(f'<clipPath id="{cid}"><path d="{ch.get(A + "pathData")}"/></clipPath>')
            out.append(f'<g clip-path="url(#{cid})">')
            clip_open += 1
        elif tag == "path":
            fill, fa = color(ch.get(A + "fillColor"))
            for attr in ch.iter(AAPT + "attr"):
                if attr.get("name") == "android:fillColor":
                    fill, fa = gradient(attr.find("gradient")), 1.0
            fa *= float(ch.get(A + "fillAlpha", "1"))
            stroke, sa = color(ch.get(A + "strokeColor"))
            rule = "evenodd" if ch.get(A + "fillType") == "evenOdd" else "nonzero"
            s = f' stroke="{stroke}" stroke-opacity="{sa}" stroke-width="{ch.get(A + "strokeWidth", "1")}"' if stroke else ""
            out.append(f'<path d="{ch.get(A + "pathData")}" fill="{fill or "none"}" fill-opacity="{fa}" fill-rule="{rule}"{s}/>')
    out.append("</g>" * clip_open)
    return "".join(out)


root = ET.parse(sys.argv[1]).getroot()
w, h = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
body = walk(root)
open(sys.argv[2], "w", encoding="utf-8").write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{float(w) * 4}" height="{float(h) * 4}"><defs>{"".join(defs)}</defs>{body}</svg>')
