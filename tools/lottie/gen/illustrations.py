"""Lucento's original spot animations: onboarding, empty states, receipt scanning, confirmation, milestone, loading.

Flat brand shapes on a 240×200 (illustrations) or square (feedback) artboard. Each illustration has a
light ("day") and a night variant; feedback animations use colours that work on both themes.
All one-shot animations end on a complete, static frame so a held last frame is a finished picture.
"""
from lot import *

DAY = dict(ink=hexc("#0F1B3D"), blue=hexc("#2563EB"), blueSoft=hexc("#DCE6FF"), sky=hexc("#60A5FA"),
           sun=hexc("#FBBF24"), sunSoft=hexc("#FFE6C7"), paper=hexc("#FFFFFF"), line=hexc("#C9D3E6"),
           muted=hexc("#8A93A8"), green=hexc("#22C55E"), greenSoft=hexc("#DCFCE7"), coral=hexc("#F87171"),
           backdrop=hexc("#F3F6FF"))
NIGHT = dict(ink=hexc("#E8EDF8"), blue=hexc("#8AB0FF"), blueSoft=hexc("#1E3366"), sky=hexc("#60A5FA"),
             sun=hexc("#FBBF24"), sunSoft=hexc("#4A3A12"), paper=hexc("#222E50"), line=hexc("#3A4A72"),
             muted=hexc("#6B7694"), green=hexc("#4ADE80"), greenSoft=hexc("#123B26"), coral=hexc("#FC8A8A"),
             backdrop=hexc("#16203B"))

W, H = 240, 200


def pop(t0, dur=18, to=100, ease=OVERSHOOT, total=None):
    keys = [(0, [0, 0]), (t0, [0, 0]), (t0 + dur, [to, to], ease)]
    if total:
        keys.append((total, [to, to]))
    return anim(keys)


def fade(t0, dur=14, total=None):
    keys = [(0, 0), (t0, 0), (t0 + dur, 100, EASE_OUT)]
    if total:
        keys.append((total, 100))
    return anim(keys)


def slide(t0, frm, to, dur=20, ease=SOFT):
    return anim([(0, list(frm)), (t0, list(frm)), (t0 + dur, list(to), ease)])


# ---------------------------------------------------------------- onboarding: the mark assembles
def onboarding(c):
    op = 150
    u = 1.3  # mark units -> px
    ox, oy = 120 - 50 * u, 100 - 50 * u
    backdrop = group("Backdrop", [ellipse(120, 100, 168), fill(c["backdrop"])], p=[120, 100], a=[120, 100], s=pop(0, 26, ease=EASE_OUT))
    # The L as one rounded stroke drawn down then across.
    lw = 21 * u
    l_path = path([[ox + 30.5 * u, oy + 24.5 * u], [ox + 30.5 * u, oy + 75.5 * u], [ox + 67.5 * u, oy + 75.5 * u]])
    l_mark = group("L", [l_path, trim(0, anim([(0, 0), (14, 0), (58, 100, SOFT)])), stroke(c["blue"], lw, cap=2, join=2)])
    base = [ox + 50 * u, oy + 58 * u]
    leaf = path(
        [base, [ox + 84 * u, oy + 16 * u]], closed=True,
        outs=[[(-2) * u, (-20) * u], [0, 22 * u]],
        ins=[[22 * u, -2 * u], [-22 * u, 6 * u]],
    )
    leaf_g = group("Leaf", [leaf, fill(c["sun"])], p=base, a=base, s=pop(52, 26), r=anim([(0, -14), (52, -14), (84, 0, SOFT)]))
    sparks = []
    for n, (x, y, r, t0) in enumerate([(196, 46, 9, 78), (44, 60, 6, 92), (188, 150, 5, 104)]):
        sc = anim([(0, [0, 0]), (t0, [0, 0]), (t0 + 16, [100, 100], OVERSHOOT), (t0 + 34, [72, 72], EASE), (op, [72, 72])])
        sparks.append(group(f"Spark {n}", [sparkle(x, y, r, c["sun"] if n != 1 else c["sky"])], p=[x, y], a=[x, y], s=sc))
    return doc("Lucento welcome", W, H, op, [
        layer("Backdrop", [backdrop], 1, op),
        layer("Mark", [leaf_g, l_mark], 2, op),
        layer("Sparks", sparks, 3, op),
    ])


# ---------------------------------------------------------------- empty: transactions (a ledger and a lens)
def empty_transactions(c):
    op = 120
    card = group("Card", [rect(110, 100, 132, 148, r=18), fill(c["paper"])], p=[110, 100], a=[110, 100], s=pop(0, 22, ease=EASE_OUT))
    shadow = group("Shadow", [rect(114, 106, 132, 148, r=18), fill(c["blueSoft"])], p=[114, 106], a=[114, 106], s=pop(0, 22, ease=EASE_OUT))
    rows = []
    tones = [c["green"], c["coral"], c["sun"]]
    for i in range(3):
        y = 56 + i * 34
        t0 = 16 + i * 10
        rows.append(group(f"Row {i}", [
            group("Dot", [ellipse(70, y + 8, 18), fill(tones[i])]),
            group("Line", [rect(108, y + 4, 44, 7, r=3.5), fill(c["line"])]),
            group("Line2", [rect(100, y + 14, 28, 6, r=3), fill(c["line"])], o=60),
            group("Amount", [rect(150, y + 8, 22, 7, r=3.5), fill(c["muted"])], o=70),
        ], p=slide(t0, (0, 8), (0, 0)), a=[0, 0], o=fade(t0)))
    lens = group("Lens", [
        group("Handle", [path([[188, 150], [206, 168]]), stroke(c["blue"], 10)]),
        group("Glass", [ellipse(170, 132, 40), fill(c["blueSoft"], o=70)]),
        group("Ring", [ellipse(170, 132, 40), stroke(c["blue"], 7)]),
    ], p=anim([(0, [60, 40]), (40, [60, 40]), (78, [0, 0], SOFT), (op, [0, 0])]), a=[0, 0], o=fade(40, 16),
        r=anim([(0, -12), (40, -12), (84, 0, SOFT)]))
    return doc("No transactions", W, H, op, [
        layer("Shadow", [shadow], 1, op), layer("Card", [card], 2, op), layer("Rows", rows, 3, op), layer("Lens", [lens], 4, op),
    ])


# ---------------------------------------------------------------- empty: budgets (a gauge filling calmly)
def empty_budgets(c):
    op = 110
    cx, cy, r = 120, 104, 62
    pts, ins, outs = arc_path(cx, cy, r, r, 150, 240, segments=6)
    track = group("Track", [path(pts, ins=ins, outs=outs), stroke(c["line"], 16)])
    prog = group("Progress", [path(pts, ins=ins, outs=outs), trim(0, anim([(0, 0), (14, 0), (70, 62, SOFT), (op, 62)])), stroke(c["blue"], 16)])
    coin = group("Coin", [
        group("Face", [ellipse(cx, cy + 6, 46), fill(c["sun"])]),
        group("Inner", [ellipse(cx, cy + 6, 32), stroke(c["paper"], 4)], o=80),
    ], p=[cx, cy + 6], a=[cx, cy + 6], s=pop(36, 22))
    ticks = []
    for i, a in enumerate((150, 270, 390)):
        import math
        x = cx + (r + 22) * math.cos(math.radians(a))
        y = cy + (r + 22) * math.sin(math.radians(a))
        ticks.append(group(f"Tick {i}", [ellipse(x, y, 7), fill(c["muted"])], o=fade(20 + i * 8)))
    return doc("No budgets", W, H, op, [layer("Track", [track], 1, op), layer("Progress", [prog], 2, op),
                                         layer("Coin", [coin], 3, op), layer("Ticks", ticks, 4, op)])


# ---------------------------------------------------------------- empty: goals (a target and a sun-flag)
def empty_goals(c):
    op = 110
    cx, cy = 112, 112
    rings = [
        group("R1", [ellipse(cx, cy, 128), fill(c["blueSoft"])], p=[cx, cy], a=[cx, cy], s=pop(0, 22, ease=EASE_OUT)),
        group("R2", [ellipse(cx, cy, 88), fill(c["paper"])], p=[cx, cy], a=[cx, cy], s=pop(8, 22, ease=EASE_OUT)),
        group("R3", [ellipse(cx, cy, 50), fill(c["sky"])], p=[cx, cy], a=[cx, cy], s=pop(16, 22, ease=EASE_OUT)),
        group("R4", [ellipse(cx, cy, 18), fill(c["blue"])], p=[cx, cy], a=[cx, cy], s=pop(24, 20)),
    ]
    flag = group("Flag", [
        group("Cloth", [path([[cx + 2, cy - 62], [cx + 40, cy - 52], [cx + 2, cy - 40]], closed=True), fill(c["sun"])]),
        group("Pole", [path([[cx, cy - 66], [cx, cy]]), stroke(c["ink"], 5)]),
    ], p=[cx, cy], a=[cx, cy], s=anim([(0, [100, 0]), (40, [100, 0]), (66, [100, 100], OVERSHOOT), (op, [100, 100])]))
    spark = group("Spark", [sparkle(cx + 64, cy - 66, 9, c["sun"])], p=[cx + 64, cy - 66], a=[cx + 64, cy - 66], s=pop(64, 18))
    return doc("No goals", W, H, op, [layer("Target", list(reversed(rings)), 1, op), layer("Flag", [flag], 2, op), layer("Spark", [spark], 3, op)])


# ---------------------------------------------------------------- empty: recurring (a calendar and a loop)
def empty_recurring(c):
    op = 120
    card = group("Calendar", [
        group("Header", [rect(112, 61, 120, 30, r=16), rect(112, 70, 120, 12, r=0), fill(c["blue"])]),
        group("Body", [rect(112, 100, 120, 108, r=16), fill(c["paper"])]),
    ], p=[112, 100], a=[112, 100], s=pop(0, 22, ease=EASE_OUT))
    rings_ = group("Binder", [rect(86, 46, 6, 16, r=3), rect(138, 46, 6, 16, r=3), fill(c["ink"])], o=fade(10))
    dots = []
    for row in range(3):
        for col in range(4):
            x = 76 + col * 24
            y = 96 + row * 20
            t0 = 14 + (row * 4 + col) * 2
            highlight = (row, col) == (1, 2)
            dots.append(group(f"D{row}{col}", [ellipse(x, y, 11 if highlight else 8), fill(c["sun"] if highlight else c["line"])],
                              p=[x, y], a=[x, y], s=pop(t0, 12)))
    pts, ins, outs = arc_path(176, 140, 26, 26, 200, 290, segments=6)
    loop_ = group("Loop", [
        group("Arrow", [path(pts, ins=ins, outs=outs), trim(0, anim([(0, 0), (40, 0), (82, 100, SOFT)])), stroke(c["green"], 7)]),
        group("Head", [path([[pts[-1][0] - 9, pts[-1][1] - 4], [pts[-1][0], pts[-1][1]], [pts[-1][0] + 2, pts[-1][1] - 10]]), stroke(c["green"], 7)],
              o=fade(80, 6)),
    ], p=[176, 140], a=[176, 140], r=anim([(0, -30), (40, -30), (96, 0, SOFT)]))
    return doc("No recurring payments", W, H, op, [layer("Calendar", [card], 1, op), layer("Binder", [rings_], 2, op),
                                                   layer("Dots", dots, 3, op), layer("Loop", [loop_], 4, op)])


# ---------------------------------------------------------------- receipt scanning (loop)
def receipt_scan(c):
    op = 120
    zig = []
    x0, x1, yb = 72, 168, 168
    n = 8
    for i in range(n + 1):
        zig.append([x0 + (x1 - x0) * i / n, yb if i % 2 == 0 else yb - 7])
    paper_pts = [[x0, 30], [x1, 30]] + list(reversed(zig))
    paper = group("Paper", [path(paper_pts, closed=True), fill(c["paper"])])
    lines = []
    for i, (w, y) in enumerate([(56, 50), (40, 66), (64, 86), (48, 100), (60, 114), (36, 140)]):
        lines.append(group(f"L{i}", [rect(x0 + 14 + w / 2, y, w, 6, r=3), fill(c["line"] if i != 5 else c["muted"])]))
    beam_y = anim([(0, [0, 0]), (54, [0, 128], EASE), (60, [0, 128]), (114, [0, 0], EASE), (op, [0, 0])])
    beam = group("Beam", [
        group("Glow", [rect(120, 34, 124, 18, r=9), fill(c["sky"], o=22)]),
        group("Line", [rect(120, 34, 124, 4, r=2), fill(c["blue"])]),
    ], p=beam_y, a=[0, 0])
    corners = []
    for (x, y, dx, dy) in [(56, 18, 1, 1), (184, 18, -1, 1), (56, 182, 1, -1), (184, 182, -1, -1)]:
        corners.append(group("Corner", [path([[x, y + 18 * dy], [x, y], [x + 18 * dx, y]]), stroke(c["blue"], 6)]))
    pulse = anim([(0, [100, 100]), (60, [103, 103], EASE), (op, [100, 100], EASE)])
    return doc("Scanning receipt", W, H, op, [
        layer("Paper", [group("Receipt", lines + [paper])], 1, op),
        layer("Beam", [beam], 2, op),
        layer("Frame", [group("Corners", corners, p=[120, 100], a=[120, 100], s=pulse)], 3, op),
    ])


# ---------------------------------------------------------------- feedback: saved
def success(c):
    op = 72
    S = 160
    halo = group("Halo", [ellipse(80, 80, 112), stroke(c["green"], 6, o=anim([(0, 0), (10, 0), (12, 45), (48, 0, EASE_OUT), (op, 0)]))],
                 p=[80, 80], a=[80, 80], s=anim([(0, [80, 80]), (10, [80, 80]), (48, [125, 125], EASE_OUT), (op, [125, 125])]))
    disc = group("Disc", [ellipse(80, 80, 104), fill(c["green"])], p=[80, 80], a=[80, 80], s=pop(0, 20))
    check = group("Check", [path([[56, 82], [73, 98], [105, 64]]), trim(0, anim([(0, 0), (14, 0), (38, 100, SOFT), (op, 100)])),
                            stroke(hexc("#FFFFFF"), 10)])
    return doc("Saved", S, S, op, [layer("Halo", [halo], 1, op), layer("Disc", [disc], 2, op), layer("Check", [check], 3, op)])


# ---------------------------------------------------------------- feedback: milestone (calm celebration)
def milestone(c):
    op = 120
    S = 200
    cx = cy = 100
    track = group("Track", [ellipse(cx, cy, 128), stroke(c["line"], 12)])
    pts, ins, outs = arc_path(cx, cy, 64, 64, -90, 360, segments=8)
    ring = group("Ring", [path(pts, ins=ins, outs=outs), trim(0, anim([(0, 72), (40, 100, SOFT), (op, 100)])), stroke(c["green"], 12)])
    star = group("Star", [sparkle(cx, cy, 30, c["sun"])], p=[cx, cy], a=[cx, cy], s=pop(34, 22),
                 r=anim([(0, -30), (34, -30), (64, 0, SOFT)]))
    rays_ = []
    import math
    for i in range(8):
        a = math.radians(i * 45 - 90)
        x0, y0 = cx + 80 * math.cos(a), cy + 80 * math.sin(a)
        x1, y1 = cx + 94 * math.cos(a), cy + 94 * math.sin(a)
        o = anim([(0, 0), (44, 0), (52, 100), (96, 0, EASE_OUT), (op, 0)])
        rays_.append(group(f"Ray {i}", [path([[x0, y0], [x1, y1]]), stroke(c["sun"], 6, o=o)],
                           p=[cx, cy], a=[cx, cy], s=anim([(0, [90, 90]), (44, [90, 90]), (96, [112, 112], EASE_OUT), (op, [112, 112])])))
    return doc("Milestone", S, S, op, [layer("Track", [track], 1, op), layer("Ring", [ring], 2, op),
                                       layer("Rays", rays_, 3, op), layer("Star", [star], 4, op)])


# ---------------------------------------------------------------- loading (loop)
def loading(c):
    op = 72
    dots = []
    for i in range(3):
        x = 30 + i * 30
        t0 = i * 10
        sc = anim([(0, [70, 70]), (t0, [70, 70]), (t0 + 18, [100, 100], EASE), (t0 + 36, [70, 70], EASE), (op, [70, 70])])
        o = anim([(0, 45), (t0, 45), (t0 + 18, 100, EASE), (t0 + 36, 45, EASE), (op, 45)])
        dots.append(group(f"Dot {i}", [ellipse(x, 20, 14), fill(c["blue"], o=o)], p=[x, 20], a=[x, 20], s=sc))
    return doc("Working", 120, 40, op, [layer("Dots", dots, 1, op)])


def build(out):
    # onboarding_welcome (the old L-and-leaf mark assembling) is retired: onboarding shows the Rising Crest symbol.
    for name, fn in [("empty_transactions", empty_transactions), ("empty_budgets", empty_budgets),
                     ("empty_goals", empty_goals), ("empty_recurring", empty_recurring), ("receipt_scanning", receipt_scan)]:
        write(rnd(fn(DAY)), f"{out}/{name}.json")
        write(rnd(fn(NIGHT)), f"{out}/{name}_night.json")
    write(rnd(success(DAY)), f"{out}/feedback_saved.json")
    write(rnd(milestone(DAY)), f"{out}/feedback_milestone.json")
    write(rnd(loading(DAY)), f"{out}/loading_dots.json")
