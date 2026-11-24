#!/usr/bin/env python3
"""Paints the seven coin / star swatches (T-23 Part C) into the shared palette texture.

Usage:  python3 tools/add_coin_swatches.py            (run from anywhere; edits the project's colormap.png in place)

The palette texture is Kenney's 512 x 512 colormap. Its top 140 rows are never sampled by any Kenney mesh (checked:
every uv of the 10 meshes lies in x 16..432, y 140..499), so swatch k is a 16 x 16 block at x = 16 k, y = 0..15.
The colours must match ProcMesh.SWATCH_RGB (a JVM test, ProcMeshTest.paletteHoldsTheCoinSwatches, checks the pixels).
tools/glb_to_mesh.py copies Kenney's original colormap over this file, so run this tool again after it.
Standard library + Pillow only. Idempotent.
"""
import os
from PIL import Image

SWATCH_RGB = [0xF2C572, 0xD9A441, 0xC48E32, 0xFFE08A, 0xFFC933, 0xFFE680, 0xD9A21E]
BLOCK = 16

path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "art", "race",
                    "colormap.png")
im = Image.open(path)
mode = im.mode
rgba = im.convert("RGBA")
for k, rgb in enumerate(SWATCH_RGB):
    colour = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)
    for y in range(BLOCK):
        for x in range(BLOCK):
            rgba.putpixel((k * BLOCK + x, y), colour)
out = rgba.convert(mode) if mode != "RGBA" else rgba
out.save(path, optimize=True)
print("painted %d swatches into %s" % (len(SWATCH_RGB), os.path.normpath(path)))
