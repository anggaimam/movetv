#!/usr/bin/env python3
"""Renders every part of art-src/mascots/kids-rig.svg to a trimmed PNG at 1.5x and writes the rig description.

Output (commit it):
  app/src/main/assets/mascots/<part id>.png
  app/src/main/assets/mascots/rig.json
Part ids are a_* for Kid A (dark hair, green shirt) and b_* for Kid B (blond hair, red shirt).
rig.json units are "rig pixels" (the drawing units x SCALE). Per part:
  file, parent ("root" or a part id), pivot [x, y] inside the PNG, offset [x, y] from the parent's pivot to this
  part's pivot, order (draw order, back to front within the kid).
Needs rsvg-convert and Pillow. Run from the repo root:
  python3 tools/build_rig_svg.py && python3 tools/rig_mascot.py [--check out.png]
--check also composes the parts at rest into one picture, to compare with the original drawing.
"""
import io
import json
import os
import re
import subprocess
import sys

from PIL import Image

SVG = 'art-src/mascots/kids-rig.svg'
OUT_DIR = 'app/src/main/assets/mascots'
SCALE = 1.5
ORDER = ['shadow', 'leg_l', 'leg_r', 'upper_arm_l', 'upper_arm_r', 'torso', 'shorts', 'sleeve_l', 'sleeve_r',
         'neck', 'head', 'hair', 'brows', 'eyes', 'pupils', 'lids', 'eyes_closed',
         'mouth_smile', 'mouth_open', 'mouth_closed', 'fore_arm_l', 'fore_arm_r']


def parse(text):
    defs = re.search(r'<defs>.*?</defs>', text, re.S).group(0)
    parts = {}
    for m in re.finditer(r'<g id="(\w+)" data-parent="(\w+)" data-pivot="([\d.]+),([\d.]+)">\n(.*?)\n</g>\n(?=<g id=|</svg>)',
                         text, re.S):
        parts[m.group(1)] = (m.group(2), (float(m.group(3)), float(m.group(4))), m.group(5))
    return defs, parts


def render(defs, inner):
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 720" width="%d" height="%d">%s\n%s\n</svg>'
           % (1200 * SCALE, 720 * SCALE, defs, inner))
    png = subprocess.run(['rsvg-convert', '-w', str(int(1200 * SCALE))], input=svg.encode(),
                         capture_output=True, check=True).stdout
    return Image.open(io.BytesIO(png)).convert('RGBA')


def main():
    check = sys.argv[sys.argv.index('--check') + 1] if '--check' in sys.argv else None
    defs, parts = parse(open(SVG).read())
    os.makedirs(OUT_DIR, exist_ok=True)
    rig = {'scale': SCALE, 'kids': {}}
    placed = []
    for kid in ('a', 'b'):
        entries = []
        for order, name in enumerate(ORDER):
            pid = '%s_%s' % (kid, name)
            parent, pivot, inner = parts[pid]
            im = render(defs, inner)
            bb = im.getbbox()
            if bb is None:
                raise SystemExit('part %s is empty' % pid)
            crop = im.crop(bb)
            crop.save('%s/%s.png' % (OUT_DIR, pid), optimize=True)
            px, py = pivot[0] * SCALE - bb[0], pivot[1] * SCALE - bb[1]
            if parent == 'root':
                rp = parts['%s_shadow' % kid][1]   # the root origin is the ground point under the kid
                off = ((pivot[0] - rp[0]) * SCALE, (pivot[1] - rp[1]) * SCALE)
            else:
                pp = parts[parent][1]
                off = ((pivot[0] - pp[0]) * SCALE, (pivot[1] - pp[1]) * SCALE)
            entries.append({'id': name, 'file': 'mascots/%s.png' % pid, 'parent': parent[2:] if parent != 'root' else 'root',
                            'pivot': [round(px, 1), round(py, 1)], 'offset': [round(off[0], 1), round(off[1], 1)],
                            'order': order})
            placed.append((kid, name, crop, bb))
        base = parts['%s_shadow' % kid][1]
        rig['kids'][kid] = {'rootPivotDrawing': list(base), 'parts': entries}
    json.dump(rig, open(OUT_DIR + '/rig.json', 'w'), indent=1)
    total = sum(os.path.getsize(OUT_DIR + '/' + f) for f in os.listdir(OUT_DIR))
    print('wrote', len(placed), 'parts,', total // 1024, 'KB')
    if check:
        # compose at rest: parts keep their position inside the 1800x1080 picture
        canvas = Image.new('RGBA', (int(1200 * SCALE), int(720 * SCALE)), (251, 245, 233, 255))
        for kid, name, crop, bb in placed:
            if name in ('lids', 'eyes_closed', 'mouth_open', 'mouth_closed'):
                continue
            canvas.alpha_composite(crop, (bb[0], bb[1]))
        canvas.save(check)
        print('check picture', check)


if __name__ == '__main__':
    main()
