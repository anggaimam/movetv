#!/usr/bin/env python3
"""Splits art-src/fruit/fruits-sheet.svg into one 256x256 PNG per fruit (app/src/main/assets/fruit/NN_name.png).
Needs rsvg-convert and Pillow. Run from the repo root."""
import re, subprocess, io, sys
from PIL import Image

src = open('art-src/fruit/fruits-sheet.svg').read()
defs = re.search(r'<defs>.*?</defs>', src, re.S).group(0)
groups = re.findall(r'<g id="fruit-(\d+)-([a-z]+)" transform="translate\([^)]*\)"(.*?)\n</g>', src, re.S)
SIZE = 256
for num, name, body in groups:
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1200" viewBox="-40 -40 400 400">'
           + defs + '<g' + body.replace(' filter="url(#shadow)">', '>', 1) + '\n</g></svg>')
    png = subprocess.run(['rsvg-convert', '-w', '1200'], input=svg.encode(), capture_output=True, check=True).stdout
    im = Image.open(io.BytesIO(png)).convert('RGBA')
    bb = im.getbbox()
    im = im.crop(bb)
    s = (SIZE - 12) / max(im.size)
    im = im.resize((max(1, round(im.size[0] * s)), max(1, round(im.size[1] * s))), Image.LANCZOS)
    out = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    out.paste(im, ((SIZE - im.size[0]) // 2, (SIZE - im.size[1]) // 2), im)
    out.save('app/src/main/assets/fruit/%s_%s.png' % (num, name), optimize=True)
    print(num, name, bb)
