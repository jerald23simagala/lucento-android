"""Net-flow indicator for the Home dashboard: one 96×96 file with three one-shot segments (markers).

  up    0–58    money in exceeded money out: a rising trend line draws itself, then the arrow head settles
  down  60–118  money out exceeded money in: the same line falling (never alarming — no shake, no flash)
  flat  120–178 exactly zero: a level line draws in from the centre

Each segment ends on a complete, static frame so a held last frame (or reduced motion) is a finished glyph.
Colours match Lucento's semantic tokens (positive / negative / neutral) for day and night; the amount beside the
icon always carries the sign, so colour is never the only signal.
"""
from lot import *

S = 96
SEG = 60
DAY = dict(upTile=hexc("#DCFCE7"), up=hexc("#15803D"), downTile=hexc("#FDE2E2"), down=hexc("#C53030"),
           flatTile=hexc("#E6EBF5"), flat=hexc("#5B6478"))
NIGHT = dict(upTile=hexc("#123B26"), up=hexc("#4ADE80"), downTile=hexc("#4A1A1A"), down=hexc("#FC8A8A"),
             flatTile=hexc("#222D4A"), flat=hexc("#A4AFC7"))


def visible_only(start):
    """Opacity that is 100 only inside [start, start + SEG - 1]."""
    keys = []
    if start > 0:
        keys += [(0, 0), (start - 1, 0, LINEAR)]
    keys += [(start, 100, LINEAR), (start + SEG - 1, 100, LINEAR), (start + SEG, 0, LINEAR), (3 * SEG, 0)]
    return anim(keys, ease=LINEAR)


def draw(start, t0, dur):
    return trim(0, anim([(0, 0), (start + t0, 0), (start + t0 + dur, 100, SOFT), (3 * SEG, 100)]))


def pop_at(start, t0):
    return anim([(0, [0, 0]), (start + t0, [0, 0]), (start + t0 + 14, [100, 100], OVERSHOOT), (3 * SEG, [100, 100])])


def state(name, start, tile, ink, line_pts, head_pts, head_anchor):
    tile_g = group("Tile", [rect(48, 48, 88, 88, r=26), fill(tile)],
                   p=[48, 48], a=[48, 48], s=anim([(0, [92, 92]), (start, [92, 92]), (start + 12, [100, 100], EASE_OUT), (3 * SEG, [100, 100])]))
    line = group("Line", [path(line_pts), draw(start, 4, 26), stroke(ink, 7)])
    items = [line]
    if head_pts:
        items.append(group("Head", [path(head_pts), stroke(ink, 7)], p=head_anchor, a=head_anchor, s=pop_at(start, 26)))
    return group(name, [group("Glyph", items), tile_g], o=visible_only(start))


def flow(c):
    op = 3 * SEG
    up = state("Up", 0, c["upTile"], c["up"],
               [[24, 64], [40, 47], [51, 56], [70, 35]], [[58, 34], [70, 35], [70.5, 47]], [70, 35])
    down = state("Down", SEG, c["downTile"], c["down"],
                 [[24, 32], [40, 49], [51, 40], [70, 61]], [[58, 62], [70, 61], [70.5, 49]], [70, 61])
    flat = state("Flat", 2 * SEG, c["flatTile"], c["flat"], [[28, 48], [68, 48]], None, None)
    markers = [("up", 0, SEG - 2), ("down", SEG, SEG - 2), ("flat", 2 * SEG, SEG - 2)]
    return doc("Net flow", S, S, op, [layer("Up", [up], 1, op), layer("Down", [down], 2, op), layer("Flat", [flat], 3, op)], markers)


def build(out):
    write(rnd(flow(DAY)), f"{out}/net_flow_indicator.json")
    write(rnd(flow(NIGHT)), f"{out}/net_flow_indicator_night.json")
