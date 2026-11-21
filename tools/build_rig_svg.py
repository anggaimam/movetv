#!/usr/bin/env python3
"""Builds art-src/mascots/kids-rig.svg from Angga's untouched drawing art-src/mascots/kids.svg.

Angga's shapes and colours are reused as they are. The script only
  * splits each arm at the elbow (about 45% of the way from shoulder to wrist) and adds skin-coloured round
    joint caps so a rotating arm shows no seam,
  * lifts the shirt sleeve into its own part that turns with the upper arm,
  * adds what animation needs: closed eyes, eyelids, an open and a closed mouth,
  * replaces the blurred floor shadow with a flat ellipse (#E8D6BA at 55%).
Every part is a top-level <g id="a_..."/"b_..."> with data-parent (another part id, or "root") and
data-pivot ("x,y" in drawing units). tools/rig_mascot.py renders the parts to PNGs.

Run from the repo root:  python3 tools/build_rig_svg.py
"""
import math
import re

SRC = 'art-src/mascots/kids.svg'
OUT = 'art-src/mascots/kids-rig.svg'

SHIFT = {'a': 0, 'b': 530}          # kid B is kid A moved 530 units to the right
ARM_SHOULDER = (270.0, 428.0)       # left (screen-left) arm of kid A: shoulder joint ...
ARM_WRIST = (172.0, 541.0)          # ... and the middle of the hand
ELBOW_AT = 0.45                     # elbow, as a fraction of the way from shoulder to wrist
CAP_R = 19.0
MIRROR_X = 670.0                    # x -> 670 - x mirrors kid A's left arm onto its right arm (kid A's body centre is 335)
TOKEN = re.compile(r'<(?:path|ellipse|circle|rect)\b[^>]*?/>|<g\b.*?</g>', re.S)

SKIN = {'a': ('#FFB06F', '#F79258'), 'b': ('#FFD0AE', '#FFAA82')}
SKIN_ID = {'a': 'skinA', 'b': 'skinB'}
MOUTH = {'a': ('#172146', '#EF5257'), 'b': ('#11284F', '#F05658')}
BROW = {'a': '#202544', 'b': '#D85D2C'}


def lerp_colour(c0, c1, t):
    a = [int(c0[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(c1[i:i + 2], 16) for i in (1, 3, 5)]
    return '#%02X%02X%02X' % tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def bbox_of(d):
    nums = [float(n) for n in re.findall(r'-?\d+(?:\.\d+)?', d)]
    xs, ys = nums[0::2], nums[1::2]
    return min(xs), min(ys), max(xs), max(ys)


def skin_at(kid, bbox, x, y):
    x0, y0, x1, y1 = bbox
    t = ((x - x0) / (x1 - x0) + (y - y0) / (y1 - y0)) / 2.0
    c0, c1 = SKIN[kid]
    return lerp_colour(c0, c1, max(0.0, min(1.0, t)))


def split_sections(text):
    """Returns {kid: [(comment, [element strings])...]} using the comments in Angga's file."""
    body = text[text.index('</defs>') + len('</defs>'):]
    parts = re.split(r'(<!--.*?-->)', body, flags=re.S)
    kid = None
    out = {'a': [], 'b': []}
    comment = None
    for p in parts:
        if p.startswith('<!--'):
            c = re.sub(r'[=\n]', ' ', p[4:-3]).strip()
            if c.startswith('CHARACTER 1'):
                kid = 'a'
            elif c.startswith('CHARACTER 2'):
                kid = 'b'
            else:
                comment = c.strip()
            continue
        if kid is None:
            continue
        els = TOKEN.findall(p)
        if els and comment:
            out[kid].append((comment, els))
    return out


def find(sec, key):
    for c, els in sec:
        if key in c:
            return els
    raise KeyError(key)


def half_plane(e, d, side, L=500.0):
    nx, ny = -d[1], d[0]
    pts = [(e[0] + nx * L, e[1] + ny * L), (e[0] - nx * L, e[1] - ny * L),
           (e[0] - nx * L + side * d[0] * L, e[1] - ny * L + side * d[1] * L),
           (e[0] + nx * L + side * d[0] * L, e[1] + ny * L + side * d[1] * L)]
    return 'M' + ' L'.join('%.1f %.1f' % p for p in pts) + ' Z'


def main():
    text = open(SRC).read()
    defs = re.search(r'<defs>.*?</defs>', text, re.S).group(0)
    defs = re.sub(r'\s*<filter id="softShadow".*?</filter>', '', defs, flags=re.S)
    secs = split_sections(text)
    extra_defs = []
    parts = []   # (id, parent, pivot, inner svg)

    for kid in ('a', 'b'):
        sec = secs[kid]
        dx = SHIFT[kid]
        cx = 335 + dx
        skin_id = SKIN_ID[kid]

        def add(pid, parent, pivot, inner):
            parts.append((kid + '_' + pid, (kid + '_' + parent) if parent != 'root' else 'root', pivot, inner))

        # --- legs, shoes ---
        legs = find(sec, 'legs')
        socks = find(sec, 'socks')
        shoes_left = [els for c, els in sec if ('shoes left' in c or 'left shoe' in c)][0]
        shoes_right = [els for c, els in sec if ('shoes right' in c or 'right shoe' in c)][0]
        add('shadow', 'root', (cx, 674.0),
            '<ellipse cx="%d" cy="674" rx="132" ry="17" fill="#E8D6BA" opacity=".55"/>' % cx)
        add('leg_l', 'root', (cx - 28.0, 535.0), '\n'.join([legs[0], socks[0]] + shoes_left))
        add('leg_r', 'root', (cx + 28.0, 535.0), '\n'.join([legs[1], socks[1]] + shoes_right))
        shorts = [els for c, els in sec if 'shorts' in c][0]
        add('shorts', 'root', (cx, 527.0), '\n'.join(shorts))

        # --- torso, sleeves, neck ---
        shirt = [els for c, els in sec if c in ('shirt base', 'shirt')][0]
        stripes = [els for c, els in sec if 'stripes' in c][0]
        breaks = [els for c, els in sec if 'sleeve' in c and 'break' in c][0]
        collar = [els for c, els in sec if 'collar' in c][0]
        shirt_all = '\n'.join(shirt + stripes + breaks)
        add('torso', 'root', (cx, 530.0), '\n'.join(shirt + stripes + breaks + collar))
        add('neck', 'torso', (cx, 391.0), '\n'.join(find(sec, 'neck')))

        # --- arms ---
        arms = find(sec, 'arms')
        for side, label in (('l', 'left'), ('r', 'right')):
            if side == 'l':
                s = (ARM_SHOULDER[0] + dx, ARM_SHOULDER[1])
                w = (ARM_WRIST[0] + dx, ARM_WRIST[1])
                arm_el = arms[0]
                sleeve_poly = 'M268 391 L213 461 L262 457 Z'
            else:
                s = (MIRROR_X - ARM_SHOULDER[0] + dx, ARM_SHOULDER[1])
                w = (MIRROR_X - ARM_WRIST[0] + dx, ARM_WRIST[1])
                arm_el = arms[1]
                sleeve_poly = 'M402 391 L457 461 L408 457 Z'
            ln = math.hypot(w[0] - s[0], w[1] - s[1])
            d = ((w[0] - s[0]) / ln, (w[1] - s[1]) / ln)
            e = (s[0] + (w[0] - s[0]) * ELBOW_AT, s[1] + (w[1] - s[1]) * ELBOW_AT)
            dd = re.search(r' d="([^"]*)"', arm_el).group(1)
            bb = bbox_of(dd)
            cap = skin_at(kid, bb, e[0], e[1])
            capS = skin_at(kid, bb, s[0], s[1])
            up_id, fo_id = '%s_clip_up_%s' % (kid, side), '%s_clip_fo_%s' % (kid, side)
            extra_defs.append('<clipPath id="%s"><path d="%s"/></clipPath>' % (up_id, half_plane(e, d, -1)))
            extra_defs.append('<clipPath id="%s"><path d="%s"/></clipPath>' % (fo_id, half_plane(e, d, +1)))
            upper = ('<g clip-path="url(#%s)">%s</g>\n<circle cx="%.1f" cy="%.1f" r="%.1f" fill="%s"/>\n'
                     '<circle cx="%.1f" cy="%.1f" r="%.1f" fill="%s"/>'
                     % (up_id, arm_el, e[0], e[1], CAP_R, cap, s[0], s[1], CAP_R, capS))
            fore = ('<g clip-path="url(#%s)">%s</g>\n<circle cx="%.1f" cy="%.1f" r="%.1f" fill="%s"/>'
                    % (fo_id, arm_el, e[0], e[1], CAP_R, cap))
            sl_id = '%s_clip_sleeve_%s' % (kid, side)
            sp = sleeve_poly if dx == 0 else re.sub(r'(\d+) (\d+)', lambda m: '%d %s' % (int(m.group(1)) + dx, m.group(2)), sleeve_poly)
            extra_defs.append('<clipPath id="%s"><path d="%s"/></clipPath>' % (sl_id, sp))
            add('upper_arm_' + side, 'torso', s, upper)
            add('sleeve_' + side, 'upper_arm_' + side, s, '<g clip-path="url(#%s)">\n%s\n</g>' % (sl_id, shirt_all))
            add('fore_arm_' + side, 'upper_arm_' + side, e, fore)

        # --- head and face ---
        ears = [els for c, els in sec if c == 'ears'][0]
        ear_lines = [els for c, els in sec if c.startswith('ear ')][0]
        face = [els for c, els in sec if c == 'face'][0]
        nose = find(sec, 'nose')
        cheeks = find(sec, 'cheeks')
        chin = find(sec, 'chin')
        pivot_head = (float(cx), 375.0)
        add('head', 'torso', pivot_head, '\n'.join(ears + ear_lines + face + nose + cheeks + chin))
        hair = []
        for c, els in sec:
            if 'hair' in c:
                hair += els
        add('hair', 'head', pivot_head, '\n'.join(hair))
        add('brows', 'head', pivot_head, '\n'.join(find(sec, 'brows')))
        eyes = find(sec, 'eyes')
        # eyes: two white ellipses, then two pupils (fill #17234C / #112952)
        add('eyes', 'head', pivot_head, '\n'.join(eyes[:2]))
        add('pupils', 'head', pivot_head, '\n'.join(eyes[2:]))
        # lids and closed eyes
        face_d = re.search(r' d="([^"]*)"', face[0]).group(1)
        fb = bbox_of(face_d)
        lids = []
        closed = []
        for ex in (281 + dx, 389 + dx):
            col = skin_at(kid, fb, ex, 286)
            lids.append('<ellipse cx="%d" cy="286" rx="31" ry="36" fill="%s"/>' % (ex, col))
            closed.append('<path d="M%d 292 Q%d 306 %d 292" fill="none" stroke="%s" stroke-width="7" stroke-linecap="round"/>'
                          % (ex - 20, ex, ex + 20, BROW[kid]))
        add('lids', 'head', pivot_head, '\n'.join(lids))
        add('eyes_closed', 'head', pivot_head, '\n'.join(closed))
        mouth_c, tongue_c = MOUTH[kid]
        add('mouth_smile', 'head', pivot_head, '\n'.join(find(sec, 'mouth')))
        add('mouth_open', 'head', pivot_head,
            '<path d="M%d 333 Q%d 338 %d 333 Q%d 368 %d 376 Q%d 368 %d 333 Z" fill="%s"/>\n'
            '<path d="M%d 363 Q%d 353 %d 363 Q%d 375 %d 375 Q%d 375 %d 363 Z" fill="%s"/>'
            % (303 + dx, 335 + dx, 367 + dx, 377 + dx, 335 + dx, 293 + dx, 303 + dx, mouth_c,
               319 + dx, 336 + dx, 353 + dx, 346 + dx, 336 + dx, 326 + dx, 319 + dx, tongue_c))
        add('mouth_closed', 'head', pivot_head,
            '<path d="M%d 339 Q%d 357 %d 339" fill="none" stroke="%s" stroke-width="7" stroke-linecap="round"/>'
            % (306 + dx, 335 + dx, 364 + dx, mouth_c))

    # draw order, back to front, per kid (the Puppet reads it from rig.json)
    ids = [p[0] for p in parts]
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 720" width="1200" height="720">',
           defs.replace('</defs>', '\n    ' + '\n    '.join(extra_defs) + '\n  </defs>'),
           '<!-- Rig of Angga\'s kids: one group per part. data-parent / data-pivot are read by tools/rig_mascot.py. -->']
    for pid, parent, pivot, inner in parts:
        out.append('<g id="%s" data-parent="%s" data-pivot="%.1f,%.1f">\n%s\n</g>' % (pid, parent, pivot[0], pivot[1], inner))
    out.append('</svg>')
    open(OUT, 'w').write('\n'.join(out) + '\n')
    print('wrote', OUT, len(parts), 'parts')


if __name__ == '__main__':
    main()
