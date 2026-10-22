"""Lucento's Sun (light theme) and Moon (dark theme) mascot as Lottie, with expression markers.

Geometry mirrors the Compose vector mascot (core/designsystem/component/Mascot.kt) in a 100-unit box so the
Lottie and the static vector read as the same character. Markers:
  idle      0–180   loop  gentle float, slow ray sway, one blink
  success   180–270 once  small hop, wider smile, rays bloom
  curious   270–450 loop  head tilt, raised brow, eyes look around
  celebrate 450–600 once  rays turn one step, hop, four sparkles
"""
from lot import *

OP = 600
C = (50, 52)            # face centre in mascot units
S = 2.0                 # 100 units -> 200 px, leaving room for hops and sparkles


def P(x, y):
    return [x, y]


def head_motion():
    """Position / scale / rotation of the whole character, in px (parent null)."""
    cx, cy = 120, 126
    pos = anim([
        (0, [cx, cy]), (90, [cx, cy - 4]), (180, [cx, cy]),                       # idle float
        (190, [cx, cy + 3], EASE_OUT), (212, [cx, cy - 14], SOFT), (236, [cx, cy + 1], EASE), (252, [cx, cy]),  # success hop
        (270, [cx, cy]), (450, [cx, cy]),
        (462, [cx, cy + 3], EASE_OUT), (486, [cx, cy - 12], SOFT), (512, [cx, cy]), (600, [cx, cy]),             # celebrate hop
    ], ease=EASE)
    scale = anim([
        (0, [S * 100, S * 100]), (180, [S * 100, S * 100]),
        (190, [S * 104, S * 96], EASE_OUT), (212, [S * 97, S * 103]), (236, [S * 101, S * 99]), (252, [S * 100, S * 100]),
        (450, [S * 100, S * 100]), (462, [S * 104, S * 96], EASE_OUT), (486, [S * 97, S * 103]), (512, [S * 100, S * 100]),
        (600, [S * 100, S * 100]),
    ], ease=EASE)
    rot = anim([
        (0, 0), (270, 0), (300, -7, EASE), (400, -7, EASE), (432, 0), (450, 0), (600, 0),
    ], ease=EASE)
    return pos, scale, rot


def eyes_open(colors, look):
    ink = colors["face"]
    items = []
    for cx, name in ((39.5, "Eye L"), (60.5, "Eye R")):
        blink = anim([(0, [100, 100]), (118, [100, 100]), (123, [100, 8], EASE_IN), (130, [100, 100], EASE_OUT), (600, [100, 100])], ease=EASE)
        items.append(group(name, [
            group("Highlight", [ellipse(cx - 1.1, 44.6, 3), fill(colors["glint"])]),
            group("Pupil", [ellipse(cx, 46.5, 7, 9), fill(ink)]),
        ], p=P(cx, 46.5), a=P(cx, 46.5), s=blink))
    return group("Eyes", items, p=look, a=P(0, 0))


def eyes_sleepy(colors, look):
    ink = colors["face"]
    items = []
    for cx, name in ((39, "Eye L"), (61, "Eye R")):
        pts, ins, outs = arc_path(cx, 48, 5, 4, 200, 140, segments=2)
        blink = anim([(0, [100, 100]), (118, [100, 100]), (123, [100, 40], EASE_IN), (130, [100, 100], EASE_OUT), (600, [100, 100])], ease=EASE)
        items.append(group(name, [path(pts, ins=ins, outs=outs), stroke(ink, 2.6)], p=P(cx, 47), a=P(cx, 47), s=blink))
    return group("Eyes", items, p=look, a=P(0, 0))


def face(colors, sleepy):
    ink = colors["face"]
    look = anim([
        (0, [0, 0]), (270, [0, 0]), (310, [-2.2, 0.4], EASE), (350, [-2.2, 0.4]), (372, [2.2, 0.4], EASE),
        (405, [2.2, 0.4]), (430, [0, 0], EASE), (600, [0, 0]),
    ], ease=EASE)
    brow_lift = anim([(0, [0, 0]), (280, [0, 0]), (300, [0, -2.6], OVERSHOOT), (405, [0, -2.6]), (430, [0, 0]), (600, [0, 0])], ease=EASE)
    smile_scale = anim([
        (0, [100, 100]), (190, [100, 100]), (206, [122, 135], OVERSHOOT), (244, [122, 135]), (266, [100, 100]),
        (450, [100, 100]), (468, [124, 138], OVERSHOOT), (560, [124, 138]), (590, [100, 100]), (600, [100, 100]),
    ], ease=EASE)
    cheek_o = anim([(0, 45), (190, 45), (206, 75), (250, 75), (266, 45), (450, 45), (468, 75), (580, 75), (600, 45)], ease=EASE)
    sp, si, so = arc_path(50.5, 56.5, 9.5, 6.5, 25, 125, segments=3)
    return [
        group("Cheeks", [ellipse(31, 58, 8), ellipse(69, 58, 8), fill(colors["cheek"], o=cheek_o)]),
        group("Smile", [path(sp, ins=si, outs=so), stroke(ink, 2.6)], p=P(50.5, 57), a=P(50.5, 57), s=smile_scale),
        eyes_sleepy(colors, look) if sleepy else eyes_open(colors, look),
        group("Brow R", [path([P(57, 37), P(66, 39)]), stroke(ink, 2.4)], p=brow_lift, a=P(0, 0)),
        group("Brow L", [path([P(34, 39), P(43, 37)]), stroke(ink, 2.4)]),
    ]


def rays(colors):
    items = []
    for i in range(12):
        long = i % 2 == 0
        h = 15 if long else 11
        top = 52 - 46 + (0 if long else 3)
        items.append(group(f"Ray {i}", [rect(50, top + h / 2, 9, h, r=4.5), fill(colors["shade"])], p=P(50, 52), a=P(50, 52), r=i * 30))
    rot = anim([(0, 0), (90, 6), (180, 0), (450, 0), (470, -4, EASE_OUT), (570, 60, SOFT), (600, 60)], ease=EASE)
    bloom = anim([(0, [100, 100]), (196, [100, 100]), (214, [112, 112], OVERSHOOT), (244, [100, 100]), (450, [100, 100]),
                  (480, [110, 110]), (560, [100, 100]), (600, [100, 100])], ease=EASE)
    return group("Rays", items, p=P(50, 52), a=P(50, 52), r=rot, s=bloom)


def celebration_sparkles(colors):
    out = []
    spots = [(12, 22, 6, 470), (88, 18, 5, 486), (90, 80, 4.5, 500), (10, 78, 4, 514)]
    for n, (x, y, r, t0) in enumerate(spots):
        sc = anim([(0, [0, 0]), (t0, [0, 0]), (t0 + 16, [100, 100], OVERSHOOT), (t0 + 46, [100, 100]), (t0 + 70, [0, 0], EASE_IN), (600, [0, 0])])
        rot = anim([(0, 0), (t0, 0), (t0 + 70, 45), (600, 45)])
        out.append(group(f"Spark {n}", [sparkle(x, y, r, colors["spark"])], p=P(x, y), a=P(x, y), s=sc, r=rot))
    return group("Celebrate", out)


def sun(colors):
    pos, scale, rot = head_motion()
    body = [
        group("Body", [ellipse(48.8, 50.8, 58), fill(colors["body"])]),
        group("Shade", [ellipse(50, 52, 62), fill(colors["shade"])]),
    ]
    root = null_layer("Character", 1, OP, p=pos, a=(50, 52), s=scale, r=rot)
    # Positions in the parented layers are in mascot units.
    layers = [
        root,
        layer("Celebrate", [celebration_sparkles(colors)], 2, OP, parent=1),
        layer("Rays", [rays(colors)], 3, OP, parent=1),
        layer("Face", face(colors, sleepy=False) + body, 4, OP, parent=1),
    ]
    # Put the null first, then sparkles behind rays behind face (doc() reverses for drawing order).
    return layers


def moon(colors):
    pos, scale, rot = head_motion()
    twinkle = lambda t0: anim([(0, [100, 100]), (t0, [100, 100]), (t0 + 20, [55, 55], EASE), (t0 + 40, [100, 100], EASE), (600, [100, 100])], ease=EASE)
    body = [
        group("Craters", [ellipse(68, 32, 9), ellipse(31, 72, 6), ellipse(72, 70, 4.4), fill(colors["shade"])]),
        group("Body", [ellipse(48.8, 50.8, 62), fill(colors["body"])]),
        group("Shade", [ellipse(50, 52, 66), fill(colors["shade"])]),
    ]
    stars = group("Stars", [
        group("Star A", [sparkle(14, 18, 6, colors["spark"])], p=P(14, 18), a=P(14, 18), s=twinkle(40)),
        group("Star B", [sparkle(88, 12, 4, colors["spark"])], p=P(88, 12), a=P(88, 12), s=twinkle(100)),
    ])
    root = null_layer("Character", 1, OP, p=pos, a=(50, 52), s=scale, r=rot)
    return [
        root,
        layer("Celebrate", [celebration_sparkles(colors)], 2, OP, parent=1),
        layer("Stars", [stars], 3, OP, parent=1),
        layer("Face", face(colors, sleepy=True) + body, 4, OP, parent=1),
    ]


MARKERS = [("idle", 0, 180), ("success", 180, 90), ("curious", 270, 180), ("celebrate", 450, 150)]

SUN = dict(body=hexc("#FBBF24"), shade=hexc("#F59E0B"), face=hexc("#0F1B3D"), cheek=hexc("#FB923C"),
           glint=hexc("#FFFFFF"), spark=hexc("#2563EB"))
MOON = dict(body=hexc("#E4EBFF"), shade=hexc("#B9C7EE"), face=hexc("#1B2850"), cheek=hexc("#F9A8D4"),
            glint=hexc("#FFFFFF"), spark=hexc("#FBBF24"))


def build(out_dir):
    write(rnd(doc("Lucento Sun", 240, 240, OP, sun(SUN), MARKERS)), f"{out_dir}/lucento_mascot_sun.json")
    write(rnd(doc("Lucento Moon", 240, 240, OP, moon(MOON), MARKERS)), f"{out_dir}/lucento_mascot_moon.json")
