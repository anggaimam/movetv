#!/usr/bin/env python3
"""Draws the Jungle Dash art (crayon doodle) into app/src/main/assets/jungle/.
Needs pycairo, numpy and Pillow (pip install pycairo numpy pillow). Run from the repo root:
    python3 tools/jungle/export.py
The game loads these files (JungleArt); if one is missing it falls back to its code-drawn picture."""
import os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
import monkey, forest

OUT = 'app/src/main/assets/jungle'
os.makedirs(OUT, exist_ok=True)

def save(img, name):
    img.save(os.path.join(OUT, name + '.png'), optimize=True)

# monkey: 320 px frames, feet at 238/256 of the height
for fn, name in [(monkey.walk, 'walk'), (monkey.duck, 'duck'), (monkey.jump, 'jump'), (monkey.fall, 'fall')]:
    for i in range(4):
        save(monkey.frame(fn, i, 320), '%s%d' % (name, i))
# backdrop: sky per level, back and middle trees, ground (all drawn at twice their size)
for lv in range(3):
    save(forest.sky(lv).convert('RGB'), 'sky%d' % (lv + 1))
save(forest.far_layer(), 'far')
save(forest.mid_layer(), 'mid')
save(forest.ground_layer(), 'ground')
# 10 tree species (440 x 680, trunk base at 220, 672)
for i, (name, fn) in enumerate(forest.SPECIES):
    save(forest.tree_img(fn, i + 1), 'tree%d' % i)
# rainforest things and the macaw
for name, fn, (w, h) in forest.THINGS:
    if name == 'morpho':
        for f in range(2):
            s, c = forest.surf(w, h); fn(c, random.Random(1), f); save(forest.to_img(s), 'morpho%d' % f)
    else:
        s, c = forest.surf(w, h); fn(c, random.Random(1)); save(forest.to_img(s), name)
for f in range(3):
    s, c = forest.surf(160, 110); forest.bird(c, f); save(forest.to_img(s), 'bird%d' % f)
print('done')
