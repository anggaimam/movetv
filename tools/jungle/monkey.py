import cairo, math, numpy as np
from PIL import Image

LINE = (0.30, 0.16, 0.08)
FUR = (0.75, 0.42, 0.19)
FUR_FAR = (0.55, 0.29, 0.13)
PEACH = (0.98, 0.80, 0.58)
PEACH_FAR = (0.86, 0.64, 0.44)
BELLY = (0.95, 0.72, 0.50)
STAR = (1.0, 0.82, 0.25)
OL = 5.0
PENDING = []
BODY_SHAPES = []
BODY = None   # the body ellipse just drawn (cx, cy, rx, ry, ang), for merging limb roots

def ell(ctx, cx, cy, rx, ry, ang=0):
    ctx.save(); ctx.translate(cx, cy); ctx.rotate(math.radians(ang)); ctx.scale(rx, ry)
    ctx.arc(0, 0, 1, 0, 2 * math.pi); ctx.restore()

def shape(ctx, s, widen=0.0):
    """Adds a shape to the path. Lines become strokes; widen adds outline width."""
    ctx.new_path()
    if s[0] == 'ell':
        ell(ctx, *s[1:]); return 'fill'
    if s[0] == 'poly':
        ctx.move_to(*s[1][0]); [ctx.line_to(*p) for p in s[1][1:]]; ctx.close_path(); return 'fill'
    if s[0] == 'line':
        ctx.move_to(*s[1][0]); [ctx.line_to(*p) for p in s[1][1:]]
        ctx.set_line_width(s[2] + widen); return 'stroke'

def merged(ctx, parts):
    """parts: list of (shape, fill colour). One outline around all of them, then the fills in order:
    where parts touch there is no line, only a change of colour."""
    ctx.set_line_cap(cairo.LINE_CAP_ROUND); ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    ctx.set_source_rgb(*LINE)
    for s, _ in parts:
        k = shape(ctx, s, 2 * OL)
        if k == 'fill':
            ctx.set_line_width(2 * OL); ctx.stroke_preserve(); ctx.fill()
        else:
            ctx.stroke()
    for s, col in parts:
        ctx.set_source_rgb(*col)
        k = shape(ctx, s)
        ctx.fill() if k == 'fill' else ctx.stroke()

def chain(start, segs):
    pts = [start]; x, y = start
    for L, a in segs:
        x += L * math.sin(math.radians(a)); y += L * math.cos(math.radians(a)); pts.append((x, y))
    return pts

def merge_root(ctx, root, w, col):
    """Hides a limb's outline where it joins the body: repaint the fur inside the body around the joint."""
    if BODY is None: return
    for sh in BODY_SHAPES:
        ctx.save(); ctx.new_path(); ell(ctx, *sh[1:]); ctx.clip()
        ctx.set_source_rgb(*col); ell(ctx, root[0], root[1], w * 0.5 + OL + 1, w * 0.5 + OL + 1); ctx.fill()
        ctx.restore()

def arm(ctx, shoulder, a1, a2, far=False, L=(26, 24)):
    pts = chain(shoulder, [(L[0], a1), (L[1], a2)])
    fur, peach = (FUR_FAR, PEACH_FAR) if far else (FUR, PEACH)
    hx, hy = pts[-1]
    merged(ctx, [(('line', pts[:2], 17), fur), (('line', pts[1:], 14), fur), (('ell', hx, hy, 10, 10, 0), peach)])
    if not far: merge_root(ctx, shoulder, 17, FUR)

def leg(ctx, hip, a1, a2, foot_ang=0, far=False, L=(30, 28)):
    pts = chain(hip, [(L[0], a1), (L[1], a2)])
    fur, peach = (FUR_FAR, PEACH_FAR) if far else (FUR, PEACH)
    ax, ay = pts[-1]
    fa = math.radians(foot_ang)
    fx, fy = ax + 7 * math.cos(fa), ay + 4 + 7 * math.sin(fa)
    merged(ctx, [(('line', pts[:2], 22), fur), (('line', pts[1:], 17), fur), (('ell', fx, fy, 16, 9, foot_ang), peach)])
    if not far: merge_root(ctx, hip, 22, FUR)

def tail(ctx, base, c1, c2, top, hook=10):
    pts = []
    for k in range(21):
        t = k / 20.0; u = 1 - t
        pts.append((u**3*base[0] + 3*u*u*t*c1[0] + 3*u*t*t*c2[0] + t**3*top[0],
                    u**3*base[1] + 3*u*u*t*c1[1] + 3*u*t*t*c2[1] + t**3*top[1]))
    cx, cy = top[0] + hook, top[1]
    for k in range(1, 15):
        a = math.radians(180 + k * 15); r = hook * (1 - 0.012 * k)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    merged(ctx, [(('line', pts, 11), FUR)])

def qcurve(p0, c, p1, n=8):
    return [((1-t)**2*p0[0] + 2*(1-t)*t*c[0] + t*t*p1[0], (1-t)**2*p0[1] + 2*(1-t)*t*c[1] + t*t*p1[1])
            for t in [k / n for k in range(1, n + 1)]]

# hair: two curved spikes sweeping forward from the crown
TUFT = ([(-18, -34)] + qcurve((-18, -34), (-10, -54), (8, -56))
        + qcurve((8, -56), (2, -48), (4, -42)) + qcurve((4, -42), (14, -50), (26, -48))
        + qcurve((26, -48), (20, -40), (18, -32)))

def head(ctx, cx, cy, tilt=0, r=46, expr='happy'):
    s = r / 46.0
    body = PENDING[0] if PENDING else []
    belly = PENDING[1] if PENDING else None
    # outline pass for the body (world space), then the head's outline, then all fills: one silhouette
    ctx.set_line_cap(cairo.LINE_CAP_ROUND); ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    ctx.set_source_rgb(*LINE)
    for sh, _ in body:
        shape(ctx, sh); ctx.set_line_width(2 * OL); ctx.stroke_preserve(); ctx.fill()
    ctx.save(); ctx.translate(cx, cy); ctx.rotate(math.radians(tilt)); ctx.scale(s, s)
    skull = ('ell', 0, 0, 44, 42, 0)
    tuft = ('poly', TUFT)
    ear = ('ell', -38, 4, 18, 19, 0)
    eye_patch = ('ell', 25, -10, 21, 23, -10)   # reaches the outline on the right     # oval around the eye
    snout = ('ell', 27, 18, 28, 19, -6)        # bigger, starts further left, bulges past the outline           # oval for the snout and mouth, bulging past the head
    head_parts = [(ear, FUR), (tuft, FUR), (skull, FUR), (eye_patch, PEACH), (snout, PEACH)]
    ctx.set_source_rgb(*LINE)
    for sh, _ in head_parts:
        shape(ctx, sh); ctx.set_line_width(2 * OL); ctx.stroke_preserve(); ctx.fill()
    ctx.restore()
    for sh, col in body:
        ctx.set_source_rgb(*col); shape(ctx, sh); ctx.fill()
    if belly:
        bcx, bcy, brx, bry, bt = belly
        bx, by, rx_, ry_, ang = BODY
        ctx.save(); ell(ctx, bx, by, rx_ - 4, ry_ - 4, ang); ctx.clip()
        ctx.set_source_rgb(*BELLY); ell(ctx, bcx, bcy, brx, bry, bt); ctx.fill(); ctx.restore()
    BODY_SHAPES[:] = [sh for sh, _ in body if sh[0] == 'ell']
    PENDING.clear()
    ctx.save(); ctx.translate(cx, cy); ctx.rotate(math.radians(tilt)); ctx.scale(s, s)
    for sh, col in head_parts:
        ctx.set_source_rgb(*col); shape(ctx, sh); ctx.fill()
    # ear lobe with its C line (open towards the head)
    ctx.set_source_rgb(*PEACH); ell(ctx, -37, 6, 11, 12); ctx.fill()
    ctx.set_source_rgb(*LINE); ctx.set_line_width(3.2); ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    ctx.new_path(); ctx.save(); ctx.translate(-37, 6); ctx.scale(12, 13)
    ctx.arc(0, 0, 1, math.radians(55), math.radians(305)); ctx.restore(); ctx.stroke()
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    # eye
    ctx.set_source_rgb(0.08, 0.06, 0.05)
    if expr == 'happy':
        ell(ctx, 28, -8, 4.6, 6.8); ctx.fill()
        ctx.set_source_rgb(1, 1, 1); ell(ctx, 29.5, -10.6, 1.5, 1.7); ctx.fill()
    elif expr == 'oops':
        ell(ctx, 28, -9, 6.0, 7.8); ctx.fill()
        ctx.set_source_rgb(1, 1, 1); ell(ctx, 30, -12, 2, 2.2); ctx.fill()
    else:
        ctx.set_line_width(3.6)
        ctx.move_to(22, -15); ctx.line_to(34, -2); ctx.move_to(34, -15); ctx.line_to(22, -2); ctx.stroke()
    # mouth, clipped to the snout so the smile runs into the outline
    ctx.save(); ell(ctx, 27, 18, 28, 19, -6); ctx.clip()
    ctx.set_source_rgb(*LINE); ctx.set_line_width(3.4)
    if expr == 'happy':
        ctx.move_to(30, 18); ctx.curve_to(35, 29, 48, 29, 60, 17); ctx.stroke()
    elif expr == 'oops':
        ell(ctx, 42, 22, 5, 6); ctx.stroke()
    else:
        ctx.move_to(30, 22)
        for k in range(1, 9):
            ctx.line_to(30 + k * 4, 22 + (3 if k % 2 else -1))
        ctx.stroke()
    ctx.restore()
    ctx.restore()

def body_up(ctx, cx, cy, tilt=6, rx=27, ry=38, neck=True):
    global BODY
    t = math.radians(tilt)
    nx, ny = cx + (ry + 2) * math.sin(t) + 6, cy - (ry + 2) * math.cos(t)
    parts = [(('ell', cx, cy, rx, ry, tilt), FUR)]
    if neck: parts.insert(0, (('ell', nx, ny, 12, 17, tilt), FUR))
    BODY = (cx, cy, rx, ry, tilt)
    PENDING[:] = [parts, (cx + 13, cy + 4, 13, 26, tilt)]

def body_flat(ctx, cx, cy, tilt=0, rx=48, ry=25):
    global BODY
    BODY = (cx, cy, rx, ry, tilt)
    PENDING[:] = [[(('ell', cx + 44, cy - 16, 15, 17, 30), FUR), (('ell', cx, cy, rx, ry, tilt), FUR)],
                  (cx + 10, cy + 15, 26, 10, tilt)]

def stars(ctx, cx, cy, phase):
    for k in range(3):
        a = phase + k * 2 * math.pi / 3
        x, y = cx + 56 * math.cos(a), cy + 12 * math.sin(a)
        pts = []
        for j in range(10):
            rr = 10 if j % 2 == 0 else 4.4
            b = -math.pi / 2 + j * math.pi / 5
            pts.append((x + rr * math.cos(b), y + rr * math.sin(b)))
        global OL
        keep = OL; OL = 2.5
        merged(ctx, [(('poly', pts), STAR)])
        OL = keep

# ---------------- poses (256 box, feet on y ~ 238, facing right) ----------------

def walk(ctx, i):
    global BODY
    P = [
        (30, 20, -28, -22, 45, 95, -30, -10, 0, -15, 8),
        (2, -2, -20, -100, 15, 75, -10, 15, -4, -30, 0),
        (-28, -22, 30, 20, -30, -5, 45, 95, 0, 8, -15),
        (-20, -100, 2, -2, 25, 80, -10, 10, -4, 0, -30),
    ][i]
    nl1, nl2, fl1, fl2, na1, na2, fa1, fa2, bob, ffa, nfa = P
    hip = (134, 180 + bob); sh = (142, 130 + bob)
    BODY = None
    arm(ctx, (sh[0] + 6, sh[1]), fa1, fa2, far=True)
    leg(ctx, (hip[0] + 4, hip[1]), fl1, fl2, ffa, far=True)
    sw = [0, 6, 0, -6][i]; up = [0, -4, 0, 3][i]
    tail(ctx, (112, 176 + bob), (68 - sw, 186), (44 + sw, 150), (50 + sw * 1.4, 104 + bob + up), hook=10 + sw * 0.3)
    body_up(ctx, 138, 156 + bob)
    head(ctx, 156, 72 + bob)
    leg(ctx, (hip[0] - 6, hip[1]), nl1, nl2, nfa)
    arm(ctx, sh, na1, na2)

def duck(ctx, i):
    global BODY
    P = [
        ((55, 15), (15, 0), (-5, -30), (-60, -85), 0),
        ((-10, -20), (-25, 10), (40, 0), (15, -20), -8),
        ((75, 45), (40, 15), (-45, -75), (-25, -55), -2),
        ((-20, -5), (10, -15), (30, -20), (55, 15), -4),
    ][i]
    fn, ff, bn, bf, bob = P
    shoulder = (158, 178 + bob); hip = (96, 186 + bob)
    BODY = None
    arm(ctx, (shoulder[0] - 8, shoulder[1]), ff[0], ff[1], far=True, L=(30, 28))
    leg(ctx, (hip[0] - 6, hip[1]), bf[0], bf[1], 0, far=True, L=(26, 24))
    sw = [0, 7, 2, -6][i]
    tail(ctx, (70, 176 + bob), (44 - sw, 168), (30 + sw, 120), (40 + sw * 1.5, 86 - abs(sw) * 0.5))
    body_flat(ctx, 124, 176 + bob, tilt=-6, rx=46, ry=26)
    head(ctx, 192, 140 + bob, r=44)
    leg(ctx, hip, bn[0], bn[1], 0, L=(26, 24))
    arm(ctx, shoulder, fn[0], fn[1], L=(30, 28))

def jump(ctx, i):
    global BODY
    BODY = None
    if i == 0:
        sh = (148, 158); hip = (116, 196)
        arm(ctx, (sh[0] + 4, sh[1]), -30, -15, far=True)
        leg(ctx, (hip[0] + 6, hip[1]), 105, -10, 0, far=True, L=(26, 22))
        tail(ctx, (96, 196), (50, 212), (28, 180), (40, 140))
        body_up(ctx, 132, 178, tilt=32, rx=27, ry=33)
        head(ctx, 168, 112)
        leg(ctx, hip, 110, -5, 0, L=(26, 22))
        arm(ctx, sh, -40, -20)
    elif i == 1:
        sh = (150, 116); hip = (128, 168)
        arm(ctx, (sh[0] + 4, sh[1]), 140, 115, far=True)
        leg(ctx, (hip[0] + 2, hip[1]), -10, -30, -60, far=True)
        tail(ctx, (110, 166), (70, 178), (46, 140), (52, 100))
        body_up(ctx, 136, 142, tilt=16, rx=26, ry=38)
        head(ctx, 162, 58, tilt=-6)
        leg(ctx, (hip[0] - 4, hip[1]), 5, -5, -30)
        arm(ctx, sh, 115, 95)
    elif i == 2:
        sh = (150, 134); hip = (128, 176)
        arm(ctx, (sh[0] + 4, sh[1]), 100, 80, far=True)
        leg(ctx, (hip[0] + 6, hip[1]), 120, -5, 10, far=True, L=(26, 22))
        tail(ctx, (106, 176), (60, 196), (34, 160), (42, 116))
        body_up(ctx, 136, 158, tilt=22, rx=27, ry=34)
        head(ctx, 166, 84)
        leg(ctx, (hip[0] - 2, hip[1]), 110, 5, 15, L=(26, 22))
        arm(ctx, sh, 95, 75)
    else:
        sh = (148, 156); hip = (118, 196)
        arm(ctx, (sh[0] + 4, sh[1]), 70, 90, far=True)
        leg(ctx, (hip[0] + 6, hip[1]), 100, -10, 0, far=True, L=(26, 22))
        tail(ctx, (98, 196), (50, 212), (26, 176), (38, 136))
        body_up(ctx, 132, 176, tilt=26, rx=27, ry=33)
        head(ctx, 170, 110)
        leg(ctx, hip, 105, -5, 0, L=(26, 22))
        arm(ctx, sh, 70, 92)

def fall(ctx, i):
    """0 trip (oops!), 1 bump down on the bottom, 2-3 dizzy with stars (loop 2,3)."""
    global BODY
    BODY = None
    if i == 0:   # tripping forward, arms flung out
        sh = (160, 148); hip = (118, 182)
        arm(ctx, (sh[0] + 4, sh[1]), 105, 125, far=True)
        leg(ctx, (hip[0] + 4, hip[1]), -95, -125, -100, far=True)
        tail(ctx, (100, 178), (64, 166), (44, 126), (58, 96))
        body_up(ctx, 140, 162, tilt=55, rx=27, ry=36)
        head(ctx, 192, 112, tilt=16, expr='oops')
        leg(ctx, hip, 15, -12, 5)
        arm(ctx, sh, 78, 62)
    else:        # sitting on the ground, legs out in front
        wob = [0, 0, -9, 9][i]
        sh = (130, 160); hip = (126, 212)
        arm(ctx, (sh[0] + 6, sh[1]), 60, 20, far=True)
        leg(ctx, (hip[0] + 6, hip[1] - 2), 95, 80, -10, far=True, L=(28, 24))
        tw = [0, 0, -5, 5][i]
        tail(ctx, (106, 220), (60, 236), (30 + tw, 200), (36 + tw * 1.6, 168))
        body_up(ctx, 124, 186, tilt=-10, rx=27, ry=34)
        expr = 'oops' if i == 1 else 'dizzy'
        head(ctx, 134 + wob * 0.4, 112, tilt=wob, expr=expr)
        leg(ctx, hip, 100, 85, -15, L=(28, 24))
        arm(ctx, sh, -30 if i == 1 else 15, 10 if i == 1 else 40)
        if i >= 2:
            stars(ctx, 136, 34, 0.0 if i == 2 else math.pi / 3)

# ---------------- crayon finish ----------------
rng = np.random.default_rng(7)

def crayon_finish(img):
    a = np.asarray(img).astype(np.float32) / 255.0
    h, w = a.shape[:2]
    n = rng.random((h, w)).astype(np.float32)
    s = np.zeros_like(n)
    for k in range(-2, 3):
        s += np.roll(np.roll(n, k, 0), k, 1)
    s = (s / 5.0 - (s / 5.0).mean()) / (s / 5.0).std()
    fine = rng.random((h, w)).astype(np.float32) - 0.5
    rgb = a[..., :3] * (1.0 + 0.06 * s + 0.07 * fine)[..., None]
    specks = (rng.random((h, w)) < 0.02) & (a[..., 3] > 0.9)
    rgb[specks] = rgb[specks] * 0.55 + 0.45
    alpha = a[..., 3]
    edge = (alpha > 0.02) & (alpha < 0.98)
    alpha = np.where(edge, np.clip(alpha + 0.5 * (rng.random((h, w)) - 0.5), 0, 1), alpha)
    return Image.fromarray((np.dstack([np.clip(rgb, 0, 1), alpha]) * 255).astype(np.uint8), 'RGBA')

def frame(fn, i, size=360):
    surf = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx = cairo.Context(surf); ctx.scale(size / 256.0, size / 256.0)
    fn(ctx, i)
    arr = np.frombuffer(bytes(surf.get_data()), np.uint8).reshape(size, size, 4)[..., [2, 1, 0, 3]].astype(np.float32)
    al = arr[..., 3:4] / 255.0
    arr[..., :3] = np.where(al > 0, arr[..., :3] / np.maximum(al, 1e-6), 0)
    return crayon_finish(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'))

ROWS = [(walk, 'walk'), (duck, 'duck'), (jump, 'jump'), (fall, 'fall')]

if __name__ == '__main__':
    S = 360
    sheet = Image.new('RGBA', (S * 4, S * 4), (255, 255, 255, 255))
    for row, (fn, _) in enumerate(ROWS):
        for i in range(4):
            sheet.alpha_composite(frame(fn, i, S), (i * S, row * S))
    sheet.convert('RGB').save('monkey-redraw-v2.png')
    print('ok')
