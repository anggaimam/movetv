#!/usr/bin/env python3
"""Cuts Angga's two kid characters (the front-facing figure of each character sheet in art-src/mascots/) out of
their cream background and writes trimmed transparent PNGs to app/src/main/assets/mascots/kid_boy.png and
kid_girl.png. The drawing itself is not changed: only the flat background and the floor shadow are removed.
Usage: python3 tools/cut_mascots.py [--preview out.png]"""
import sys
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

SHEETS = {
    # name: (sheet, crop box of the front figure incl. its shadow: left, top, right, bottom)
    "boy": ("art-src/mascots/kid-boy-sheet.png", (20, 85, 460, 945)),
    "girl": ("art-src/mascots/kid-girl-sheet.png", (10, 95, 480, 925)),
}
OUT_H = 640  # px height of the saved figure (the tiles draw it at about 400 px)


def cut(path, box):
    im = Image.open(path).convert("RGB").crop(box)
    a = np.asarray(im).astype(np.float32)
    bg = np.median(np.concatenate([a[:6, :6].reshape(-1, 3), a[:6, -6:].reshape(-1, 3)]), axis=0)
    dist = np.sqrt(((a - bg) ** 2).sum(axis=2))
    lum = a.mean(axis=2)
    bg_lum = bg.mean()
    h = a.shape[0]
    # the flat background: everything that is (almost) the background colour, connected or not, so the gaps between
    # the arms, the body and the legs go too. The darkest real colour near it (a cream stripe) is 25 away, so 9 is safe.
    gone = dist < 9
    # the soft floor shadow: a bit darker and warm, only in the band around the feet, never brighter than the background
    yy = np.arange(h)[:, None]
    shadow = (dist < 42) & (lum < bg_lum - 4) & ((a[:, :, 0] - a[:, :, 2]) < 60) & (yy > 0.90 * h)
    gone |= shadow
    figure = ~gone
    # keep only the biggest piece (drops specks), fill holes inside the figure
    lab3, n3 = ndimage.label(figure)
    if n3 > 1:
        sizes = ndimage.sum(figure, lab3, range(1, n3 + 1))
        figure = lab3 == (1 + int(np.argmax(sizes)))
    # fill only tiny holes (specks inside the picture), not the real gaps between the limbs
    holes = ndimage.binary_fill_holes(figure) & ~figure
    labh, nh = ndimage.label(holes)
    for i in range(1, nh + 1):
        if (labh == i).sum() < 40:
            figure |= labh == i
    figure = ndimage.binary_erosion(figure, iterations=1)
    alpha = Image.fromarray((figure * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.9))
    rgba = im.convert("RGBA")
    rgba.putalpha(alpha)
    rgba = rgba.crop(rgba.getbbox())
    s = OUT_H / rgba.height
    return rgba.resize((round(rgba.width * s), OUT_H), Image.LANCZOS)


def main():
    outs = {}
    for name, (path, box) in SHEETS.items():
        img = cut(path, box)
        img.save(f"app/src/main/assets/mascots/kid_{name}.png", optimize=True)
        outs[name] = img
        print(name, img.size)
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        W = sum(i.width for i in outs.values()) + 120
        prev = Image.new("RGBA", (W, OUT_H + 40), (90, 140, 200, 255))
        x = 40
        for i in outs.values():
            prev.alpha_composite(i, (x, 20))
            x += i.width + 40
        prev.save(out)


if __name__ == "__main__":
    main()
