#!/usr/bin/env python3
"""
Exports the props of Lucento Pro's perk cards from their sources into app resources.

    python tools/illustrations/export_perk_art.py --import <folder>   # copy new images in as sources, then export
    python tools/illustrations/export_perk_art.py                     # export from the committed sources

Each card shows the approved Lucento mascot (drawn by the app from its own sprites, never by this tool) beside one
simple prop for the perk, on the cards' blue (`PerkArt.kt`). The props are soft-3D renders, each made on a flat,
plain #2F56E8 ground (the prompts are in docs/design/illustrations/perks/README.md).

--import takes the images as the image model delivered them, named like `01-wallets.png` … `10-themes.png` (the number
is the card's place in the deck; the words after it are ignored), and stores each one, pixel for pixel, as
docs/design/illustrations/perks/<stop>_source.webp (lossless). Never edit a source; generate a new one instead.

Needs Python 3 with Pillow, numpy and scipy. Outputs (committed; the build never needs Python):
  app/src/main/res/drawable-nodpi/perk_art_<stop>.webp    the prop cut out (transparent around it), ART_SIZE px square
  app/src/main/res/drawable-nodpi/perk_thumb_<stop>.webp  the prop on the cards' blue for the collection, THUMB_SIZE px

The export
  1. measures the ground (the median of the image's edges) and shifts the image's colours so the ground is exactly
     BLUE, the colour the app paints the art band (AwardArtwork.CardBlue);
  2. cuts the prop out of its ground: pixels within FLAT of BLUE are ground (transparent), pixels between FLAT and BLEND
     are partly transparent (a soft shadow fades out without a ring), and any ground-coloured pixel enclosed by the
     prop stays opaque, so a prop never has a hole; the band's doodles show around the prop, never through it;
  3. frames the prop in a square with the same room around it on every card and its foot at the same height, so the
     props and the mascot stand on one line in the app. The thumbnail is the same square on the cards' blue.
"""
from __future__ import annotations

import argparse
import io
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "docs/design/illustrations/perks"
RES = ROOT / "app/src/main/res/drawable-nodpi"

# The cards' order (ProTourStop), as the resource names spell them.
STOPS = [
    "wallets", "net_worth", "smart_reading", "goals", "insights_periods",
    "statements", "ai_answers", "sync", "shared", "themes",
]

BLUE = np.array([0x2F, 0x56, 0xE8], dtype=np.float32)  # AwardArtwork.CardBlue
FLAT = 8      # within this many levels (per channel, max) of BLUE: the ground itself
BLEND = 36    # from FLAT to BLEND: blends towards BLUE (shadow tails, edge fringes)
MARGIN = 0.04  # room around the prop, as a share of the square's side (the app's shadows need a little)
ART_SIZE = 640  # the prop is drawn at most ~180 dp wide: 640 px covers 3.5x
THUMB_SIZE = 192  # 48 dp at 4x
QUALITY = 86


def cut_out(src: Image.Image) -> np.ndarray:
    """The image as RGBA: its colours shifted so the ground is exactly BLUE, its alpha 0 on the ground, 1 on the prop."""
    a = np.asarray(src.convert("RGB")).astype(np.float32)
    band = max(4, round(min(a.shape[:2]) * 0.02))
    edges = np.concatenate([a[:band].reshape(-1, 3), a[-band:].reshape(-1, 3), a[:, :band].reshape(-1, 3), a[:, -band:].reshape(-1, 3)])
    a = np.clip(a + (BLUE - np.median(edges, axis=0)), 0, 255)
    distance = np.abs(a - BLUE).max(axis=2)
    alpha = np.clip((distance - FLAT) / (BLEND - FLAT), 0, 1)
    # A hole is ground-coloured but walled in by the prop (blue light on its underside): keep it opaque. The outside is
    # whatever low-alpha region the image's border reaches.
    regions, _ = ndimage.label(alpha < 0.5)
    outside = np.unique(np.concatenate([regions[0], regions[-1], regions[:, 0], regions[:, -1]]))
    holes = (regions > 0) & ~np.isin(regions, outside)
    alpha[holes] = 1.0
    rgba = np.dstack([a, alpha * 255]).round().astype(np.uint8)
    rgba[alpha == 0, :3] = BLUE.astype(np.uint8)  # what bleeds into an edge when scaled is the band's own blue
    return rgba


def prop_box(rgba: np.ndarray) -> tuple[int, int, int, int]:
    solid = rgba[..., 3] > 128
    rows = np.where(solid.sum(axis=1) > 3)[0]
    cols = np.where(solid.sum(axis=0) > 3)[0]
    if rows.size == 0 or cols.size == 0:
        raise SystemExit("no prop found: is the ground flat #2F56E8?")
    return int(cols.min()), int(rows.min()), int(cols.max()) + 1, int(rows.max()) + 1


def framed(rgba: np.ndarray, box) -> Image.Image:
    """A square around the prop: centred across, its foot MARGIN above the bottom, transparent where the source ends."""
    x0, y0, x1, y1 = box
    side = max(x1 - x0, y1 - y0) / (1 - 2 * MARGIN)
    left = (x0 + x1) / 2 - side / 2
    bottom = y1 + side * MARGIN
    top = bottom - side
    left, top, side = round(left), round(top), round(side)
    canvas = Image.new("RGBA", (side, side), tuple(int(v) for v in BLUE) + (0,))
    canvas.paste(Image.fromarray(rgba, "RGBA"), (-left, -top))
    return canvas


def on_blue(im: Image.Image) -> Image.Image:
    ground = Image.new("RGBA", im.size, tuple(int(v) for v in BLUE) + (255,))
    ground.alpha_composite(im)
    return ground.convert("RGB")


def encode(im: Image.Image) -> bytes:
    buf = io.BytesIO()
    im.save(buf, "WEBP", quality=QUALITY, alpha_quality=100, method=6)
    return buf.getvalue()


def export(stop: str) -> dict[str, bytes]:
    rgba = cut_out(Image.open(SOURCES / f"{stop}_source.webp"))
    box = prop_box(rgba)
    square = framed(rgba, box)
    art = square.resize((ART_SIZE, ART_SIZE), Image.LANCZOS)
    thumb = on_blue(square).resize((THUMB_SIZE, THUMB_SIZE), Image.LANCZOS)
    files = {f"perk_art_{stop}.webp": encode(art), f"perk_thumb_{stop}.webp": encode(thumb)}
    h, w = rgba.shape[:2]
    edge = box[0] <= 1 or box[1] <= 1 or box[2] >= w - 1 or box[3] >= h - 1
    warn = "  ! the prop touches the image's edge: generate a new one" if edge else ""
    print(f"{stop:17s} prop {box} in {w}x{h}  art {len(files[f'perk_art_{stop}.webp']) // 1024} KB{warn}")
    return files


def import_folder(folder: Path) -> None:
    SOURCES.mkdir(parents=True, exist_ok=True)
    found = {}
    for path in sorted(folder.iterdir()):
        m = re.match(r"(\d{1,2})\D", path.name)
        if m and path.suffix.lower() in (".png", ".webp", ".jpg", ".jpeg"):
            found[int(m.group(1))] = path
    missing = [n for n in range(1, len(STOPS) + 1) if n not in found]
    if missing:
        raise SystemExit(f"missing images for cards {missing} in {folder}")
    for n, stop in enumerate(STOPS, start=1):
        Image.open(found[n]).convert("RGB").save(SOURCES / f"{stop}_source.webp", "WEBP", lossless=True, method=6)
        print(f"source {stop}_source.webp  <- {found[n].name}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--import", dest="folder", type=Path, help="folder of new images, 01-… to 10-…")
    args = parser.parse_args()
    if args.folder:
        import_folder(args.folder)
    total = 0
    for stop in STOPS:
        for name, data in export(stop).items():
            (RES / name).write_bytes(data)
            total += len(data)
    print(f"{len(STOPS) * 2} files, {total // 1024} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
