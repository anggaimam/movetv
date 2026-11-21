#!/usr/bin/env python3
"""Cuts Angga's kid character sheets into transparent PNG pieces: the front and side body and the three faces
(laughing, surprised, sad) of each kid, plus 'lid' overlays that fake a blink by covering the eyes with skin colour.
Output goes to the folder given as the first argument (default /tmp/mascot_parts).
The drawing is not changed: only the flat cream background and the floor shadow are removed."""
import sys, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

PARTS = {
    "boy": {
        "sheet": "art-src/mascots/kid-boy-sheet.png",
        "pieces": {
            "body_front": (20, 85, 460, 945),
            "body_side": (490, 90, 860, 945),
            "face_laugh": (975, 70, 1395, 510),
            "face_wow": (820, 515, 1160, 900),
            "face_sad": (1175, 515, 1520, 900),
        },
    },
    "girl": {
        "sheet": "art-src/mascots/kid-girl-sheet.png",
        "pieces": {
            "body_front": (10, 95, 480, 925),
            "body_side": (495, 125, 860, 925),
            "face_laugh": (950, 85, 1415, 515),
            "face_wow": (800, 535, 1160, 905),
            "face_sad": (1160, 535, 1530, 905),
        },
    },
}
OUT_H = {"body_front": 640, "body_side": 640, "face_laugh": 320, "face_wow": 320, "face_sad": 320}


def cut(path, box):
    im = Image.open(path).convert("RGB").crop(box)
    a = np.asarray(im).astype(np.float32)
    bg = np.median(np.concatenate([a[:6, :6].reshape(-1, 3), a[:6, -6:].reshape(-1, 3)]), axis=0)
    dist = np.sqrt(((a - bg) ** 2).sum(axis=2))
    lum = a.mean(axis=2)
    h = a.shape[0]
    gone = dist < 9
    yy = np.arange(h)[:, None]
    shadow = (dist < 42) & (lum < bg.mean() - 4) & ((a[:, :, 0] - a[:, :, 2]) < 60) & (yy > 0.84 * h)
    gone |= shadow
    figure = ~gone
    lab, n = ndimage.label(figure)
    if n > 1:
        sizes = ndimage.sum(figure, lab, range(1, n + 1))
        figure = lab == (1 + int(np.argmax(sizes)))
    holes = ndimage.binary_fill_holes(figure) & ~figure
    labh, nh = ndimage.label(holes)
    for i in range(1, nh + 1):
        if (labh == i).sum() < 40:
            figure |= labh == i
    figure = ndimage.binary_erosion(figure, iterations=1)
    alpha = Image.fromarray((figure * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.9))
    rgba = im.convert("RGBA")
    rgba.putalpha(alpha)
    return rgba.crop(rgba.getbbox())


def scale_to(img, h):
    s = h / img.height
    return img.resize((round(img.width * s), h), Image.LANCZOS)


def hull(m):
    """Convex hull of a mask (the pupil often touches the edge of the eye white, so fill_holes alone leaves it out)."""
    from scipy.spatial import ConvexHull
    ys, xs = np.nonzero(m)
    pts = np.stack([xs, ys], 1)
    hv = pts[ConvexHull(pts).vertices]
    im = Image.new("L", (m.shape[1], m.shape[0]), 0)
    ImageDraw.Draw(im).polygon([tuple(map(float, q)) for q in hv], fill=255)
    return np.asarray(im) > 0


def find_eyes(img):
    """The two eye whites of a front-facing figure: bright blobs in the upper half, holes (pupils) filled."""
    a = np.asarray(img.convert("RGBA")).astype(np.int32)
    h, w = a.shape[:2]
    white = (a[:, :, 0] > 238) & (a[:, :, 1] > 238) & (a[:, :, 2] > 238) & (a[:, :, 3] > 200)
    white[int(h * 0.38):] = False
    # fill the pupils: close the blob
    closed = ndimage.binary_closing(white, iterations=1)
    lab, n = ndimage.label(closed)
    comps = []
    for i in range(1, n + 1):
        m = lab == i
        if m.sum() < 120:
            continue
        ys, xs = np.nonzero(m)
        comps.append((m.sum(), m, xs.mean(), ys.mean()))
    comps.sort(key=lambda c: -c[0])
    eyes = sorted(comps[:2], key=lambda c: c[2])
    out = []
    for _, m, cx, cy in eyes:
        out.append(hull(ndimage.binary_fill_holes(m)))
    return out


def skin_colour(img, mask):
    a = np.asarray(img.convert("RGBA")).astype(np.int32)
    ring = ndimage.binary_dilation(mask, iterations=9) & ~ndimage.binary_dilation(mask, iterations=4)
    px = a[ring]
    px = px[(px[:, 3] > 200) & (px[:, 0] > 170) & (px[:, 1] > 110) & (px[:, 0] - px[:, 2] > 25)]
    return tuple(int(v) for v in np.median(px[:, :3], axis=0))


def lids(img, eyes, grow):
    """Two overlay images the size of the figure: half closed and fully closed eyes."""
    w, h = img.size
    skin = skin_colour(img, eyes[0] | eyes[1])
    half = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    closed = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for m in eyes:
        mm = ndimage.binary_dilation(m, iterations=grow)
        ys, xs = np.nonzero(mm)
        y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
        for target, frac in ((half, 0.55), (closed, 1.0)):
            lidmask = mm.copy()
            lidmask[int(y0 + (y1 - y0 + 1) * frac) + 1:] = False
            soft = Image.fromarray((lidmask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))
            layer = Image.new("RGBA", (w, h), skin + (255,))
            layer.putalpha(soft)
            target.alpha_composite(layer)
        d = ImageDraw.Draw(closed)
        ew = x1 - x0
        yb = y0 + (y1 - y0) * 0.55
        pts = []
        for i in range(21):
            t = i / 20
            pts.append((x0 + t * ew, yb + np.sin(t * np.pi) * (y1 - y0) * 0.18))
        d.line(pts, fill=(38, 28, 44, 255), width=max(3, round(ew * 0.09)), joint="curve")
        dh = ImageDraw.Draw(half)
        dh.line([(x0, y0 + (y1 - y0) * 0.55), (x1, y0 + (y1 - y0) * 0.55)], fill=(38, 28, 44, 140), width=2)
    return half, closed


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "/tmp/mascot_parts"
    os.makedirs(out, exist_ok=True)
    for kid, spec in PARTS.items():
        for name, box in spec["pieces"].items():
            img = scale_to(cut(spec["sheet"], box), OUT_H[name])
            img.save(f"{out}/{kid}_{name}.png", optimize=True)
            print(kid, name, img.size)
            if name == "body_front":
                eyes = find_eyes(img)
                print("  eyes found:", len(eyes), [int(e.sum()) for e in eyes])
                if len(eyes) == 2:
                    half, closed = lids(img, eyes, 6 if kid == "girl" else 3)
                    half.save(f"{out}/{kid}_lids_half.png")
                    closed.save(f"{out}/{kid}_lids_closed.png")


if __name__ == "__main__":
    main()
