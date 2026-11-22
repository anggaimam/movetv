import cairo, math, random, numpy as np
from PIL import Image
import monkey as draw2
from monkey import ell

INK = (0.22, 0.17, 0.09)
G = (0.38, 0.68, 0.26); GD = (0.24, 0.50, 0.18); GL = (0.62, 0.82, 0.34)
TR = (0.58, 0.38, 0.21); TRD = (0.42, 0.26, 0.13); PALE = (0.80, 0.73, 0.58); PALED = (0.64, 0.57, 0.44)

def surf(w, h):
    s = cairo.ImageSurface(cairo.FORMAT_ARGB32, w, h); c = cairo.Context(s)
    c.set_line_cap(cairo.LINE_CAP_ROUND); c.set_line_join(cairo.LINE_JOIN_ROUND)
    return s, c

def to_img(s, grain=1.0, seed=1, streak=True):
    w, h = s.get_width(), s.get_height()
    arr = np.frombuffer(bytes(s.get_data()), np.uint8).reshape(h, w, 4)[..., [2, 1, 0, 3]].astype(np.float32)
    al = arr[..., 3:4] / 255.0
    arr[..., :3] = np.where(al > 0, arr[..., :3] / np.maximum(al, 1e-6), 0)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
    return soft_grain(img, grain, seed, streak)

def wobble(img, amp=2.2, cell=48, seed=1):
    """Hand-drawn wobble: a smooth random displacement field, like a line drawn freehand."""
    r = np.random.default_rng(seed + 100)
    a = np.asarray(img)
    h, w = a.shape[:2]
    gy, gx = h // cell + 3, w // cell + 3
    fields = []
    for _ in range(2):
        g = r.random((gy, gx)).astype(np.float32) * 2 - 1
        f = np.asarray(Image.fromarray(g).resize((gx * cell, gy * cell), Image.BICUBIC))[:h, :w]
        fields.append(f * amp)
    yy, xx = np.mgrid[0:h, 0:w]
    sx = np.clip((xx + fields[0]).round().astype(int), 0, w - 1)
    sy = np.clip((yy + fields[1]).round().astype(int), 0, h - 1)
    return Image.fromarray(a[sy, sx], img.mode)

def soft_grain(img, k=1.0, seed=1, streak=True):
    """Waxy crayon tooth like the monkey: short strokes all leaning one way, paper specks, rough edges."""
    img = wobble(img, seed=seed)
    r = np.random.default_rng(seed)
    a = np.asarray(img).astype(np.float32) / 255.0
    h, w = a.shape[:2]
    n = r.random((h, w)).astype(np.float32)
    st = np.zeros_like(n)
    for d in range(-3, 4):            # one direction only (no cross pattern)
        st += np.roll(np.roll(n, d // 2, 0), -d, 1)
    st = st / 7.0; st = (st - st.mean()) / st.std()
    if not streak: st = st * 0
    fine = r.random((h, w)).astype(np.float32) - 0.5
    rgb = a[..., :3] * (1.0 + k * (0.09 * st + 0.09 * fine))[..., None]
    specks = (r.random((h, w)) < 0.012 * k) & (a[..., 3] > 0.9)
    rgb[specks] = rgb[specks] * 0.7 + 0.3
    alpha = a[..., 3]
    edge = (alpha > 0.02) & (alpha < 0.98)
    alpha = np.where(edge, np.clip(alpha + 0.6 * (r.random((h, w)) - 0.5), 0, 1), alpha)
    return Image.fromarray((np.dstack([np.clip(rgb, 0, 1), alpha]) * 255).astype(np.uint8), 'RGBA')

def soft_grain_old(img, k=1.0, seed=1):
    """Gentle crayon tooth: fine, low-contrast, no stripes."""
    r = np.random.default_rng(seed)
    a = np.asarray(img).astype(np.float32) / 255.0
    h, w = a.shape[:2]
    n = r.random((h, w)).astype(np.float32)
    s = (n + np.roll(n, 1, 1) + np.roll(n, 1, 0)) / 3.0 - 0.5
    rgb = a[..., :3] * (1.0 + k * (0.10 * s + 0.04 * (r.random((h, w)) - 0.5)))[..., None]
    specks = (r.random((h, w)) < 0.012 * k) & (a[..., 3] > 0.9)
    rgb[specks] = rgb[specks] * 0.7 + 0.3
    return Image.fromarray((np.dstack([np.clip(rgb, 0, 1), a[..., 3]]) * 255).astype(np.uint8), 'RGBA')

def outlined(c, shapes, fill, line=INK, lw=4.5):
    """shapes: callables that add a path. One outline around all, then the fill."""
    c.set_source_rgb(*line)
    for f in shapes:
        c.new_path(); f(c); c.set_line_width(lw * 2); c.stroke_preserve(); c.fill()
    c.set_source_rgb(*fill)
    for f in shapes:
        c.new_path(); f(c); c.fill()

def E(cx, cy, rx, ry, a=0):
    return lambda c: ell(c, cx, cy, rx, ry, a)

def poly(pts):
    def f(c):
        c.move_to(*pts[0]); [c.line_to(*p) for p in pts[1:]]; c.close_path()
    return f

def thick(pts, w):
    """A stroked polyline as a fill-able outline: returns a function that strokes (used for trunks/branches)."""
    return pts, w

def branches(c, lines, fill, line=INK, lw=4.5):
    for kind in ('o', 'f'):
        c.set_source_rgb(*(line if kind == 'o' else fill))
        for pts, w in lines:
            c.new_path(); c.move_to(*pts[0]); [c.line_to(*p) for p in pts[1:]]
            c.set_line_width(w + (2 * lw if kind == 'o' else 0)); c.stroke()

def crown(c, cx, cy, rx, ry, n, rng, base=G, dark=GD, light=GL, lump=0.34, lw=4.5):
    """Clumpy cauliflower crown: blobs inside an ellipse, merged outline, darker underside, light dabs on top."""
    blobs = []
    for i in range(n):
        a = rng.random() * 2 * math.pi; d = math.sqrt(rng.random()) * 0.78
        x = cx + math.cos(a) * rx * d; y = cy + math.sin(a) * ry * d
        r = (rx + ry) * 0.5 * lump * (0.75 + 0.5 * rng.random())
        blobs.append((x, y, r))
    # rim blobs to keep a lumpy outline
    for i in range(int(n * 0.8)):
        a = i / (n * 0.8) * 2 * math.pi
        x = cx + math.cos(a) * rx * 0.86; y = cy + math.sin(a) * ry * 0.82
        r = (rx + ry) * 0.5 * lump * (0.65 + 0.35 * rng.random())
        blobs.append((x, y, r))
    outlined(c, [E(x, y, r, r * 0.9) for x, y, r in blobs], base, lw=lw)
    c.save(); c.new_path()
    for x, y, r in blobs: ell(c, x, y, r, r * 0.9)
    c.clip()
    c.set_source_rgb(*dark)
    for x, y, r in blobs:
        if y > cy - ry * 0.05:
            ell(c, x - r * 0.15, y + r * 0.35, r * 0.85, r * 0.6); c.fill()
    c.set_source_rgb(*light)
    for x, y, r in blobs:
        if y < cy - ry * 0.1 and rng.random() < 0.7:
            ell(c, x + r * 0.2, y - r * 0.3, r * 0.45, r * 0.3); c.fill()
    scribble(c, cx - rx * 1.1, cy - ry * 1.1, rx * 2.2, ry * 2.2, rng, dark, 0.22, gap=10, slope=0.3)
    # little leafy ticks
    c.set_source_rgba(*INK, 0.25); c.set_line_width(2.0)
    for i in range(n // 2):
        x = cx + (rng.random() - 0.5) * rx * 1.6; y = cy + (rng.random() - 0.5) * ry * 1.5
        c.move_to(x, y); c.curve_to(x + 4, y - 6, x + 9, y - 5, x + 12, y - 1); c.stroke()
    c.restore()

def scribble(c, x, y, w, h, rng, col, alpha=0.3, gap=9, lw=3.0, slope=0.3):
    """Kid-style colouring: loose zig-zag strokes, all at the same slant, inside the current clip."""
    c.set_source_rgba(*col, alpha); c.set_line_width(lw)
    yy = y
    while yy < y + h + w * slope:
        x0 = x + rng.random() * 8; y0 = yy + rng.random() * 4
        c.move_to(x0, y0)
        c.curve_to(x0 + w * 0.3, y0 - w * 0.3 * slope + rng.random() * 6, x0 + w * 0.7, y0 - w * 0.7 * slope + rng.random() * 6, x0 + w, y0 - w * slope)
        c.stroke()
        yy += gap + rng.random() * 6

def leaf(c, x, y, L, W, ang, fill=G, vein=True, lw=3.2):
    a = math.radians(ang)
    tx, ty = x + L * math.cos(a), y + L * math.sin(a)
    nx, ny = -math.sin(a) * W, math.cos(a) * W
    mx, my = (x + tx) / 2, (y + ty) / 2
    def f(cc):
        cc.move_to(x, y)
        cc.curve_to(mx + nx, my + ny, tx - (tx - x) * 0.1 + nx * 0.3, ty - (ty - y) * 0.1 + ny * 0.3, tx, ty)
        cc.curve_to(tx - (tx - x) * 0.1 - nx * 0.3, ty - (ty - y) * 0.1 - ny * 0.3, mx - nx, my - ny, x, y)
        cc.close_path()
    outlined(c, [f], fill, lw=lw)
    if vein:
        c.set_source_rgba(*INK, 0.55); c.set_line_width(1.8)
        c.move_to(x, y); c.line_to(x + (tx - x) * 0.85, y + (ty - y) * 0.85); c.stroke()

def frond(c, x0, y0, ang, L, droop, rng, fill=G, n=16, rib=(0.20, 0.42, 0.16)):
    """Palm frond: a curved rib with thin leaflets on both sides."""
    pts = []
    for k in range(n + 1):
        t = k / n
        a = math.radians(ang + droop * t * t)
        pts.append((x0 + L * t * math.cos(math.radians(ang)) + 0, y0 + L * t * math.sin(math.radians(ang)) + droop * 0.9 * t * t))
    for side in (-1, 1):
        for k in range(2, n):
            px, py = pts[k]; qx, qy = pts[k + 1]
            d = math.atan2(qy - py, qx - px) + side * 1.1
            ll = L * 0.22 * (1 - abs(k / n - 0.45))
            if k % 2 == 0 or n < 12: leaf(c, px, py, ll, 4.0, math.degrees(d) + side * 12 * (k / n), fill=fill, vein=False, lw=1.8)
    branches(c, [(pts, 4)], rib, lw=1.4)

def trunk(c, base_x, base_y, top_y, w_base, w_top, col=TR, flare=0, lean=0, lw=4.5):
    pts = [(base_x - w_base / 2 - flare, base_y), (base_x - w_top / 2 + lean, top_y),
           (base_x + w_top / 2 + lean, top_y), (base_x + w_base / 2 + flare, base_y)]
    def f(cc):
        cc.move_to(*pts[0])
        cc.curve_to(base_x - w_base / 2, base_y - 30, base_x - w_top / 2 + lean, top_y + 60, *pts[1])
        cc.line_to(*pts[2])
        cc.curve_to(base_x + w_top / 2 + lean, top_y + 60, base_x + w_base / 2, base_y - 30, *pts[3])
        cc.close_path()
    outlined(c, [f], col, lw=lw)
    c.save(); c.new_path(); f(c); c.clip()
    scribble(c, base_x - w_base, top_y, w_base * 2, base_y - top_y, random.Random(int(base_x + top_y)), TRD if col == TR else PALED, 0.30, gap=11, slope=0.15)
    c.restore()

def bark(c, x, y0, y1, w, rng, col=TRD, n=7):
    c.set_source_rgba(*col, 0.8); c.set_line_width(2.4)
    for i in range(n):
        xx = x + (rng.random() - 0.5) * w * 0.7; ya = y0 + rng.random() * (y1 - y0); yb = ya + 20 + rng.random() * 40
        c.move_to(xx, ya); c.curve_to(xx + 3, ya + 10, xx - 3, yb - 10, xx + 1, yb); c.stroke()

# ---------------- 10 species, each on a 440 x 680 canvas, base at (220, 672) ----------------
TW, TH = 440, 680
BX, BY = 220, 672

def kapok(c, r):
    # buttress roots
    for dx, s in [(-95, -1), (-50, -1), (50, 1), (95, 1)]:
        outlined(c, [poly([(BX + dx, BY), (BX + s * 14, BY - 150), (BX + s * 2, BY - 120), (BX + dx - s * 30, BY)])], TRD)
    trunk(c, BX, BY, 250, 90, 46, TR, flare=22)
    bark(c, BX, 280, 640, 60, r)
    branches(c, [([(BX - 6, 290), (BX - 90, 220), (BX - 150, 200)], 16), ([(BX + 6, 280), (BX + 100, 214), (BX + 160, 196)], 16)], TR)
    crown(c, BX, 170, 195, 95, 36, r)
    # white kapok flowers
    for i in range(5):
        x = BX + (r.random() - 0.5) * 300; y = 150 + (r.random() - 0.5) * 100
        for k in range(5):
            a = k * 2 * math.pi / 5
            outlined(c, [E(x + 7 * math.cos(a), y + 7 * math.sin(a), 6, 4, math.degrees(a))], (1, 0.98, 0.92), lw=1.6)
        c.set_source_rgb(0.95, 0.75, 0.3); ell(c, x, y, 3, 3); c.fill()

def rubber(c, r):
    trunk(c, BX, BY, 300, 40, 26, PALE, flare=6)
    c.set_source_rgba(*PALED, 0.9)
    for i in range(14):
        y = 320 + i * 24; c.set_line_width(3); c.move_to(BX - 14, y); c.curve_to(BX - 4, y + 4, BX + 6, y + 2, BX + 14, y - 2); c.stroke()
    # latex cup
    c.set_source_rgb(0.95, 0.95, 0.92); c.set_line_width(4); c.move_to(BX - 16, 560); c.line_to(BX + 16, 548); c.stroke()
    outlined(c, [poly([(BX + 6, 566), (BX + 40, 566), (BX + 36, 600), (BX + 10, 600)])], (0.64, 0.42, 0.24), lw=3)
    c.set_source_rgb(1, 1, 0.97); ell(c, BX + 23, 569, 15, 4); c.fill()
    branches(c, [([(BX, 330), (BX - 70, 250), (BX - 110, 200)], 12), ([(BX, 320), (BX + 70, 240), (BX + 100, 190)], 12), ([(BX, 300), (BX, 160)], 12)], PALE)
    crown(c, BX, 200, 150, 140, 40, r, lump=0.36)
    for i in range(14):
        a = i / 14 * 2 * math.pi
        x = BX + math.cos(a) * 130; y = 200 + math.sin(a) * 118
        leaf(c, x, y, 38, 12, math.degrees(a) + (r.random() - .5) * 30, fill=GL if i % 2 else G)

def brazil(c, r):
    trunk(c, BX, BY, 210, 56, 40, TR, flare=14)
    bark(c, BX, 240, 640, 40, r, n=10)
    branches(c, [([(BX, 230), (BX - 120, 150)], 14), ([(BX, 225), (BX + 120, 150)], 14), ([(BX, 220), (BX - 10, 120)], 14)], TR)
    crown(c, BX, 120, 195, 80, 38, r, lump=0.3)
    for x, y in [(BX + 90, 186), (BX - 100, 180)]:
        c.set_source_rgb(*INK); c.set_line_width(3); c.move_to(x, y - 26); c.line_to(x, y - 12); c.stroke()
        outlined(c, [E(x, y, 18, 16)], (0.50, 0.32, 0.16))
        c.set_source_rgba(*INK, 0.5); c.set_line_width(2); ell(c, x, y, 7, 6); c.stroke()

def cacao(c, r):
    trunk(c, BX, BY, 340, 46, 32, TR, flare=8, lean=-10)
    branches(c, [([(BX - 6, 380), (BX - 100, 300), (BX - 150, 270)], 18), ([(BX - 6, 370), (BX + 90, 290), (BX + 140, 260)], 18)], TR)
    crown(c, BX, 230, 190, 120, 22, r, lump=0.34)
    for i in range(16):
        x = BX + (r.random() - 0.5) * 330; y = 250 + (r.random() - 0.3) * 170
        leaf(c, x, y, 70, 18, 90 + (r.random() - 0.5) * 140, fill=G if i % 3 else GL)
    for (x, y, col) in [(BX - 30, 420, (0.93, 0.45, 0.15)), (BX + 20, 470, (0.88, 0.30, 0.15)), (BX - 26, 520, (0.97, 0.62, 0.15)),
                        (BX - 110, 330, (0.90, 0.38, 0.12)), (BX + 105, 320, (0.95, 0.55, 0.12))]:
        outlined(c, [E(x, y, 15, 30, 8)], col, lw=3.5)
        c.set_source_rgba(*INK, 0.45); c.set_line_width(1.8)
        for k in (-6, 0, 6): c.move_to(x + k, y - 24); c.line_to(x + k, y + 24); c.stroke()

def fig(c, r):
    # twisted root columns
    for k in range(7):
        x0 = BX - 120 + k * 40; x1 = BX - 40 + k * 14
        pts = [(x0 + (r.random() - .5) * 20, BY)]
        for j in range(1, 9):
            t = j / 8; pts.append((x0 + (x1 - x0) * t + math.sin(t * 6 + k) * 14, BY - t * 400))
        branches(c, [(pts, 14)], TR if k % 2 else TRD, lw=3.5)
    crown(c, BX, 190, 195, 150, 48, r, lump=0.32)
    c.set_source_rgba(*GD, 1.0)
    for i in range(6):  # hanging aerial roots
        x = BX - 160 + i * 64; c.set_source_rgb(*TRD); c.set_line_width(3)
        c.move_to(x, 260); c.curve_to(x + 6, 330, x - 6, 380, x + 2, 420 + r.random() * 60); c.stroke()

def acai(c, r):
    for k, (dx, top) in enumerate([(-40, 190), (-10, 130), (25, 160), (55, 230)]):
        trunk(c, BX + dx, BY, top, 16, 12, PALE, lean=dx * 0.3)
        c.set_source_rgba(*PALED, 0.9); c.set_line_width(2)
        for j in range(int((BY - top) / 28)):
            y = top + 20 + j * 28; c.move_to(BX + dx - 7, y); c.line_to(BX + dx + 7, y); c.stroke()
        tx = BX + dx * 1.3
        for a in range(0, 360, 40):
            frond(c, tx, top, a - 90 + r.random() * 20, 105, 50, r, fill=G if a % 80 else GL, n=12)
    for x, y in [(BX + 70, 280), (BX - 60, 250)]:
        for k in range(26):
            outlined(c, [E(x + (r.random() - .5) * 30, y + k * 3.4, 6, 6)], (0.38, 0.12, 0.40), lw=1.6)

def mahogany(c, r):
    trunk(c, BX, BY, 250, 52, 34, (0.52, 0.30, 0.18), flare=10)
    bark(c, BX, 280, 640, 36, r, col=(0.36, 0.18, 0.10))
    branches(c, [([(BX, 280), (BX - 90, 210), (BX - 130, 180)], 14), ([(BX, 270), (BX + 90, 200), (BX + 130, 170)], 14)], (0.52, 0.30, 0.18))
    crown(c, BX, 160, 190, 130, 30, r, base=(0.30, 0.58, 0.24), dark=(0.20, 0.42, 0.16), light=(0.50, 0.72, 0.30))


def balsa(c, r):
    trunk(c, BX, BY, 260, 36, 22, PALE, flare=6)
    branches(c, [([(BX, 330), (BX - 110, 250)], 10), ([(BX, 300), (BX + 110, 230)], 10), ([(BX, 260), (BX - 20, 150)], 10)], PALE)
    for x, y in [(BX - 110, 250), (BX + 110, 230), (BX - 20, 150), (BX - 60, 200), (BX + 60, 180), (BX, 250)]:
        for k in range(4):
            a = -90 + (k - 1.5) * 55 + (r.random() - .5) * 20
            leaf(c, x, y, 82, 36, a, fill=GL if k % 2 else G)
    for x, y in [(BX + 70, 300), (BX - 90, 330)]:
        for k in range(5):
            a = k * 2 * math.pi / 5
            outlined(c, [E(x + 12 * math.cos(a), y + 12 * math.sin(a), 11, 7, math.degrees(a))], (1, 0.97, 0.88), lw=2)
        c.set_source_rgb(0.92, 0.6, 0.25); ell(c, x, y, 5, 5); c.fill()

def cecropia(c, r):
    trunk(c, BX, BY, 180, 26, 16, PALE, lean=10)
    c.set_source_rgba(*PALED, 0.9); c.set_line_width(3)
    for j in range(14):
        y = 200 + j * 34; c.move_to(BX - 10, y); c.line_to(BX + 12, y); c.stroke()
    tips = [(BX - 130, 230), (BX + 140, 210), (BX + 10, 130), (BX - 70, 330), (BX + 90, 320)]
    branches(c, [([(BX, 360 if y > 300 else 300), (x, y)], 8) for x, y in tips], PALE)
    for x, y in tips:
        for k in range(8):   # palmate umbrella leaf
            a = -90 + (k - 3.5) * 26
            leaf(c, x, y, 66, 13, a + 180 if False else a, fill=G if k % 2 else GL, lw=2.6)
        outlined(c, [E(x, y, 7, 5)], GD, lw=2)
    for k in range(4):
        outlined(c, [E(BX + 40 + k * 8, 250, 4, 22, 10)], (0.75, 0.80, 0.40), lw=1.8)

def durian(c, r):
    trunk(c, BX, BY, 300, 44, 30, TR, flare=8)
    bark(c, BX, 320, 640, 30, r)
    branches(c, [([(BX, 340), (BX - 120, 280)], 14), ([(BX, 330), (BX + 120, 270)], 14)], TR)
    crown(c, BX, 210, 190, 120, 26, r, base=(0.34, 0.62, 0.24))
    for x, y in [(BX - 120, 305), (BX + 125, 295), (BX - 40, 330)]:
        c.set_source_rgb(*INK); c.set_line_width(3); c.move_to(x, y - 40); c.line_to(x, y - 24); c.stroke()
        outlined(c, [E(x, y, 24, 28)], (0.72, 0.72, 0.28), lw=3)
        c.set_source_rgba(*INK, 0.6)
        for k in range(14):
            a = r.random() * 2 * math.pi; d = r.random() * 20
            px, py = x + math.cos(a) * d, y + math.sin(a) * d * 1.1
            c.move_to(px - 3, py + 2); c.line_to(px, py - 3); c.line_to(px + 3, py + 2); c.close_path(); c.fill()

SPECIES = [('kapok', kapok), ('rubber', rubber), ('brazilnut', brazil), ('cacao', cacao), ('fig', fig),
           ('acai', acai), ('mahogany', mahogany), ('balsa', balsa), ('cecropia', cecropia), ('durian', durian)]

def tree_img(fn, seed):
    s, c = surf(TW, TH); fn(c, random.Random(seed)); return to_img(s, 1.0, seed)

# ---------------- 5 rainforest things ----------------
def toucan(c, r):   # 260 x 220, perched on a branch
    branches(c, [([(10, 190), (130, 180), (250, 186)], 14)], TR)
    leaf(c, 210, 182, 40, 12, -40); leaf(c, 220, 184, 36, 10, 10, fill=GL)
    outlined(c, [E(95, 120, 34, 50, -15), poly([(70, 150), (40, 205), (62, 205), (95, 160)])], (0.12, 0.11, 0.12))
    c.set_source_rgb(0.98, 0.95, 0.80); ell(c, 118, 104, 20, 22); c.fill()
    c.set_source_rgb(0.85, 0.18, 0.15); ell(c, 108, 162, 10, 7); c.fill()
    def beak(cc):
        cc.move_to(128, 86); cc.curve_to(170, 70, 215, 84, 230, 112); cc.curve_to(200, 108, 160, 112, 132, 112); cc.close_path()
    outlined(c, [beak], (0.98, 0.62, 0.12), lw=3.5)
    c.set_source_rgb(0.10, 0.10, 0.10); c.move_to(218, 98); c.curve_to(226, 104, 230, 108, 230, 112); c.line_to(214, 110); c.close_path(); c.fill()
    c.set_source_rgb(0.92, 0.28, 0.15); c.move_to(130, 108); c.line_to(150, 104); c.line_to(150, 112); c.close_path(); c.fill()
    outlined(c, [E(117, 92, 8, 8)], (0.45, 0.75, 0.95), lw=2)
    c.set_source_rgb(0, 0, 0); ell(c, 118, 92, 3.5, 3.5); c.fill()
    for x in (90, 104):
        c.set_source_rgb(0.40, 0.62, 0.85); c.set_line_width(5); c.move_to(x, 165); c.line_to(x + 4, 180); c.stroke()

def frog(c, r):     # 260 x 200, on a big leaf
    leaf(c, 10, 170, 240, 44, -6, fill=G)
    outlined(c, [E(130, 130, 52, 32, -8), E(160, 96, 30, 26), E(178, 76, 15, 15), E(148, 78, 14, 14)], (0.45, 0.82, 0.28))
    c.set_source_rgb(0.97, 0.95, 0.82); ell(c, 150, 142, 34, 14); c.fill()
    c.set_source_rgb(0.20, 0.45, 0.90)
    for x in (100, 118): ell(c, x, 130, 8, 16, 20); c.fill()
    for (ex, ey) in [(178, 76), (148, 78)]:
        c.set_source_rgb(0.92, 0.18, 0.12); ell(c, ex, ey, 10, 10); c.fill()
        c.set_source_rgb(0, 0, 0); ell(c, ex, ey, 3, 7); c.fill()
    for (x, y) in [(84, 160), (200, 156), (186, 128)]:
        outlined(c, [E(x, y, 12, 6)], (0.98, 0.58, 0.18), lw=2.2)
    c.set_source_rgb(*INK); c.set_line_width(2.4); c.move_to(170, 104); c.curve_to(178, 110, 186, 108, 190, 100); c.stroke()

def morpho(c, r, flap=0):  # 200 x 160; flap 0 open, 1 closed-ish
    sx = 1.0 if flap == 0 else 0.45
    for side in (-1, 1):
        up = lambda cc, s=side: ell(cc, 100 + s * 44 * sx, 64, 46 * sx, 40, s * 20)
        lo = lambda cc, s=side: ell(cc, 100 + s * 32 * sx, 110, 32 * sx, 30, -s * 20)
        outlined(c, [up, lo], (0.18, 0.48, 0.95), line=(0.15, 0.10, 0.10), lw=4)
        c.set_source_rgb(0.45, 0.75, 1.0); ell(c, 100 + side * 40 * sx, 66, 24 * sx, 20, side * 20); c.fill()
        c.set_source_rgb(1, 1, 1)
        for k in range(3): ell(c, 100 + side * (70 - k * 8) * sx, 40 + k * 10, 2.5, 2.5); c.fill()
    outlined(c, [E(100, 88, 6, 34)], (0.20, 0.15, 0.12), lw=2)
    c.set_source_rgb(0.2, 0.15, 0.12); c.set_line_width(2)
    c.move_to(98, 56); c.curve_to(94, 40, 88, 32, 84, 28); c.move_to(102, 56); c.curve_to(106, 40, 112, 32, 116, 28); c.stroke()

def fern(c, r):     # 300 x 200
    for k in range(7):
        a = -160 + k * 23
        frond(c, 150, 196, a, 120 + r.random() * 30, 40, r, fill=GL if k % 2 else G, n=12)
    c.set_source_rgb(*GD); c.set_line_width(6)
    # fiddlehead
    c.move_to(150, 196); c.curve_to(150, 120, 160, 80, 176, 78); c.stroke()
    outlined(c, [E(180, 84, 10, 10)], GL, lw=2.5)

def mushrooms(c, r):  # 300 x 200, on a mossy log
    def log(cc):
        cc.move_to(10, 150); cc.curve_to(100, 130, 200, 128, 290, 140); cc.line_to(290, 196); cc.curve_to(200, 196, 100, 196, 10, 196); cc.close_path()
    outlined(c, [log], TR)
    outlined(c, [E(290, 168, 14, 28)], (0.80, 0.62, 0.40))
    c.set_source_rgb(0.45, 0.70, 0.25)
    for k in range(18): ell(c, 20 + k * 15, 142 - (k % 3) * 2, 12, 6); c.fill()
    for (x, h, w) in [(120, 90, 52), (175, 60, 36), (90, 45, 28)]:
        outlined(c, [E(x, 140 - h / 2, w * 0.18, h / 2)], (0.97, 0.92, 0.80), lw=3)
        def cap(cc, x=x, h=h, w=w):
            cc.move_to(x - w, 140 - h + 6); cc.curve_to(x - w, 140 - h - w * 0.9, x + w, 140 - h - w * 0.9, x + w, 140 - h + 6); cc.close_path()
        outlined(c, [cap], (0.95, 0.45, 0.15), lw=3.5)
        c.set_source_rgb(1, 0.95, 0.85)
        for k in range(3): ell(c, x - w * 0.5 + k * w * 0.5, 140 - h - w * 0.35 + (k % 2) * 6, 4, 3); c.fill()

THINGS = [('toucan', toucan, (260, 220)), ('frog', frog, (260, 200)), ('morpho', morpho, (200, 160)),
          ('fern', fern, (300, 200)), ('mushrooms', mushrooms, (300, 200))]

def bird(c, flap):  # scarlet macaw-ish, 160 x 110, flying right
    wy = [-40, 0, 26][flap]
    def wing(cc):
        cc.move_to(70, 52); cc.curve_to(60, 52 + wy * 0.6, 40, 52 + wy, 20, 54 + wy * 1.1); cc.curve_to(50, 62, 70, 64, 92, 60); cc.close_path()
    def tail(cc):
        cc.move_to(48, 64); cc.line_to(4, 82); cc.line_to(10, 90); cc.line_to(54, 70); cc.close_path()
    outlined(c, [tail], (0.20, 0.45, 0.85), lw=3)
    outlined(c, [E(80, 62, 34, 15, -6), E(116, 52, 15, 14)], (0.90, 0.18, 0.15), lw=3.5)
    outlined(c, [wing], (0.98, 0.80, 0.20), lw=3)
    c.set_source_rgb(0.20, 0.45, 0.85); c.move_to(30, 52 + wy * 1.0); c.line_to(20, 54 + wy * 1.1); c.line_to(40, 58); c.close_path(); c.fill()
    def beak(cc):
        cc.move_to(126, 48); cc.curve_to(140, 48, 142, 58, 134, 64); cc.line_to(127, 58); cc.close_path()
    outlined(c, [beak], (0.95, 0.92, 0.85), lw=2.5)
    c.set_source_rgb(1, 1, 1); ell(c, 118, 48, 6, 5); c.fill()
    c.set_source_rgb(0, 0, 0); ell(c, 119, 48, 2.4, 2.4); c.fill()

# ---------------- backdrop layers ----------------
def sky(level, w=960, h=540):
    tops = [(0.80, 0.91, 0.93), (0.98, 0.88, 0.70), (0.62, 0.62, 0.82)][level]
    s, c = surf(w, h)
    c.set_source_rgb(*tops); c.paint()
    # soft, wide, low-contrast crayon strokes, mostly horizontal
    r = random.Random(level)
    for i in range(70):
        y = r.random() * h; x = r.random() * w; L = 120 + r.random() * 260
        c.set_source_rgba(1, 1, 1, 0.10 + r.random() * 0.08); c.set_line_width(6 + r.random() * 10)
        c.move_to(x, y); c.curve_to(x + L * 0.3, y - 3, x + L * 0.7, y + 3, x + L, y); c.stroke()
    if level == 0:
        c.set_source_rgba(1, 0.95, 0.7, 0.9); ell(c, 780, 90, 46, 46); c.fill()
    return to_img(s, 0.6, 10 + level, streak=False)

def canopy_band(w, h, top, rng, base, dark, light, n_trunks, trunk_col, lw, lump=0.32, crowns=26, cr=(70, 120)):
    s, c = surf(w, h)
    # solid understory so nothing shows through
    c.set_source_rgb(*dark); c.rectangle(0, top + 60, w, h); c.fill()
    for i in range(n_trunks):
        x = rng.random() * w; tw = 12 + rng.random() * 18
        for dx in (-w, 0, w):
            c.set_source_rgb(*trunk_col); c.rectangle(x + dx - tw / 2, top + 40, tw, h); c.fill()
    for i in range(crowns):
        x = i * w / crowns + rng.random() * 20; y = top + rng.random() * 70
        rx = cr[0] + rng.random() * (cr[1] - cr[0]); ry = rx * (0.6 + rng.random() * 0.3)
        for dx in (-w, 0, w):
            st = rng.getstate()
            crown(c, x + dx, y, rx, ry, 14, rng, base=base, dark=dark, light=light, lump=lump, lw=lw)
            rng.setstate(st)
        crown(c, -9999, 0, 1, 1, 14, rng)  # advance rng identically
    # second, lower row to close any gaps
    for i in range(crowns):
        x = (i + 0.5) * w / crowns; y = top + 120 + rng.random() * 60
        rx = cr[0] * 0.9 + rng.random() * 30; ry = rx * 0.7
        for dx in (-w, 0, w):
            st = rng.getstate()
            crown(c, x + dx, y, rx, ry, 12, rng, base=dark, dark=tuple(v * 0.85 for v in dark), light=base, lump=lump, lw=lw)
            rng.setstate(st)
        crown(c, -9999, 0, 1, 1, 12, rng)
    return s

def far_layer():
    rng = random.Random(3)
    s = canopy_band(960, 400, 90, rng, (0.30, 0.50, 0.40), (0.20, 0.36, 0.30), (0.40, 0.60, 0.48), 18, (0.22, 0.28, 0.22), 2.5, crowns=16, cr=(55, 85))
    return to_img(s, 0.9, 21)

def mid_layer():
    rng = random.Random(5)
    s = canopy_band(960, 380, 110, rng, (0.28, 0.55, 0.26), (0.18, 0.40, 0.18), (0.42, 0.68, 0.32), 12, (0.36, 0.24, 0.14), 3.0, crowns=12, cr=(60, 95))
    c = cairo.Context(s)
    # hanging lianas
    for i in range(9):
        x = rng.random() * 960
        c.set_source_rgb(0.20, 0.36, 0.16); c.set_line_width(4)
        c.move_to(x, 160); c.curve_to(x + 10, 220, x - 10, 270, x + 4, 310 + rng.random() * 50); c.stroke()
    return to_img(s, 0.9, 22)

def ground_layer(w=960, h=150):
    s, c = surf(w, h)
    c.set_source_rgb(0.70, 0.50, 0.30); c.rectangle(0, 0, w, h); c.fill()
    r = random.Random(9)
    for i in range(50):
        x = r.random() * w; y = 20 + r.random() * (h - 30)
        for dx in (-w, 0, w):
            c.set_source_rgba(0.55, 0.38, 0.22, 0.6); ell(c, x + dx, y, 8 + r.random() * 8, 4 + r.random() * 3); c.fill()
    c.save(); c.rectangle(0, 14, w, h); c.clip()
    scribble(c, -20, 0, w + 40, h, random.Random(4), (0.60, 0.42, 0.24), 0.30, gap=10)
    c.restore()
    # grass lip on top
    c.set_source_rgb(0.30, 0.55, 0.22); c.rectangle(0, 0, w, 10); c.fill()
    for i in range(80):
        x = i * w / 80
        c.move_to(x, 12); c.line_to(x + 6, 0); c.line_to(x + 12, 12); c.close_path(); c.fill()
    c.set_source_rgb(*INK); c.set_line_width(2.5); c.move_to(0, 12); c.line_to(w, 12); c.stroke()
    return to_img(s, 0.7, 23)
