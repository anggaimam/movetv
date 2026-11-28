#!/usr/bin/env python3
"""F-00b: turns a TerraPrinter 3MF (Waterton) into the compact world data of Gone Fishing.

Usage:  python3 tools/terrain_import.py <terrain.3mf> [--out app/src/main/assets/fishing/world] [--docs docs]

Writes (relative to the project root unless --out / --docs are given):
  app/src/main/assets/fishing/world/{meta.json,height.bin,height_far.bin,masks.bin,shore.bin,depth.bin,
                                     buildings.json,piers.json,waterfalls.json,spots_candidates.json}
  docs/fishing_world_preview.png, docs/fishing_world_spots.png

Needs Python 3, numpy, scipy, shapely, matplotlib (tool only; nothing here runs in the app). Output is deterministic.

Coordinates: game x = east, z = south (= -north), origin = export circle centre, metres. Mesh x = east, mesh y = north, mm.
All binary files are little-endian; layouts are in ticket F-00b (grid files: magic, u16 w, u16 h, f32 cell, f32 originX, f32 originZ;
the origin is the minimum-x, minimum-z (west, north) corner, row 0 = most negative z; FHM1 adds f32 minM, f32 maxM).
"""
import argparse
import json
import math
import os
import re
import struct
import sys
import zipfile

import numpy as np
from scipy import ndimage as ndi

M_PER_MM = 2318.1 / 100.0     # export circle radius 2.3181 km over 100 mm
VE = 1.5                      # vertical exaggeration of the print
PLAY_R = 1500.0
FAR_R = 2318.0
HOTEL = (-348.0, -1275.0)
CELL_H, CELL_FAR, CELL_MASK, CELL_SHORE, CELL_DEPTH = 4.0, 16.0, 2.0, 4.0, 8.0
SEED = 20261126


class StopAndReport(Exception):
    """A file breaks an assumption of ticket F-00b; report instead of guessing."""


# ---------------------------------------------------------------- 3MF reading

def read_3mf(path):
    with zipfile.ZipFile(path) as z:
        text = z.read("3D/3dmodel.model").decode("utf-8")
    meta = dict(re.findall(r'<metadata name="([^"]+)">([^<]*)', text))
    objs = {}
    for m in re.finditer(r'<object id="(\d+)"[^>]*name="([^"]*)"[^>]*>(.*?)</object>', text, re.S):
        oid, name, body = int(m.group(1)), m.group(2), m.group(3)
        v = np.array(re.findall(r'<vertex x="([-\d.e]+)" y="([-\d.e]+)" z="([-\d.e]+)"', body), dtype=np.float64)
        t = np.array(re.findall(r'<triangle v1="(\d+)" v2="(\d+)" v3="(\d+)"', body), dtype=np.int64)
        objs[name.split("·")[0].replace("3D map –", "").strip().lower()] = (v.reshape(-1, 3), t.reshape(-1, 3))
    return meta, objs


def components(n, tris):
    """Connected components of a triangle soup by shared vertices; returns (count, label per vertex)."""
    import scipy.sparse as sp
    import scipy.sparse.csgraph as cg
    e = np.concatenate([tris[:, [0, 1]], tris[:, [1, 2]]])
    g = sp.coo_matrix((np.ones(len(e)), (e[:, 0], e[:, 1])), shape=(n, n))
    return cg.connected_components(g, directed=False)


def tri_normals(v, t):
    a, b, c = v[t[:, 0]], v[t[:, 1]], v[t[:, 2]]
    n = np.cross(b - a, c - a)
    ln = np.linalg.norm(n, axis=1)
    return n, ln


# ------------------------------------------------------------------ rasteriser

def raster(P, Z, shape, small=64):
    """Rasterises triangles into a grid by cell centres.

    P: (n,3,2) float, vertex positions in cell units (cell (row i, col j) has its centre at (j+.5, i+.5)).
    Z: (n,3) per-vertex values, or None for a coverage mask (1.0 where covered).
    Returns float32 array (rows, cols) with NaN where nothing covers a cell.
    """
    rows, cols = shape
    out = np.full(shape, np.nan, dtype=np.float32)
    if len(P) == 0:
        return out
    x, y = P[:, :, 0], P[:, :, 1]
    j0 = np.maximum(np.ceil(x.min(1) - 0.5), 0).astype(np.int64)
    j1 = np.minimum(np.floor(x.max(1) - 0.5), cols - 1).astype(np.int64)
    i0 = np.maximum(np.ceil(y.min(1) - 0.5), 0).astype(np.int64)
    i1 = np.minimum(np.floor(y.max(1) - 0.5), rows - 1).astype(np.int64)
    ok = (j1 >= j0) & (i1 >= i0)
    w = np.where(ok, j1 - j0 + 1, 0)
    h = np.where(ok, i1 - i0 + 1, 0)
    x0, y0, x1, y1, x2, y2 = x[:, 0], y[:, 0], x[:, 1], y[:, 1], x[:, 2], y[:, 2]
    den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    ok &= np.abs(den) > 1e-12
    small_idx = np.nonzero(ok & (w * h <= small) & (w <= 8) & (h <= 8))[0]
    big_idx = np.nonzero(ok & ~((w * h <= small) & (w <= 8) & (h <= 8)))[0]

    def bary(px, py, k):
        l0 = ((y1[k] - y2[k]) * (px - x2[k]) + (x2[k] - x1[k]) * (py - y2[k])) / den[k]
        l1 = ((y2[k] - y0[k]) * (px - x2[k]) + (x0[k] - x2[k]) * (py - y2[k])) / den[k]
        return l0, l1, 1.0 - l0 - l1

    if len(small_idx):
        wm, hm = int(w[small_idx].max()), int(h[small_idx].max())
        for di in range(hm):
            for dj in range(wm):
                k = small_idx[(h[small_idx] > di) & (w[small_idx] > dj)]
                if not len(k):
                    continue
                jj, ii = j0[k] + dj, i0[k] + di
                l0, l1, l2 = bary(jj + 0.5, ii + 0.5, k)
                inside = (l0 >= -1e-9) & (l1 >= -1e-9) & (l2 >= -1e-9)
                if not inside.any():
                    continue
                kk = k[inside]
                val = 1.0 if Z is None else (l0[inside] * Z[kk, 0] + l1[inside] * Z[kk, 1] + l2[inside] * Z[kk, 2])
                out[ii[inside], jj[inside]] = val
    for k in big_idx:
        jj, ii = np.meshgrid(np.arange(j0[k], j1[k] + 1), np.arange(i0[k], i1[k] + 1))
        l0, l1, l2 = bary(jj + 0.5, ii + 0.5, k)
        inside = (l0 >= -1e-9) & (l1 >= -1e-9) & (l2 >= -1e-9)
        if inside.any():
            val = 1.0 if Z is None else (l0[inside] * Z[k, 0] + l1[inside] * Z[k, 1] + l2[inside] * Z[k, 2])
            out[ii[inside], jj[inside]] = val
    return out


def fill_nearest(a):
    """Fills NaN cells with the nearest valid cell; returns (filled, number of cells that were empty)."""
    bad = np.isnan(a)
    n = int(bad.sum())
    if n == 0:
        return a, 0
    idx = ndi.distance_transform_edt(bad, return_distances=False, return_indices=True)
    return a[tuple(idx)], n


# ------------------------------------------------------------------- grid maths

class Grid:
    """A square grid over game coordinates; row 0 is the most negative z (north), column 0 the most negative x."""

    def __init__(self, half, cell):
        self.cell = float(cell)
        self.n = int(math.ceil(2 * half / cell))
        self.ox = -self.n * cell / 2.0
        self.oz = -self.n * cell / 2.0

    def xz_to_ij(self, x, z):
        return (np.asarray(z) - self.oz) / self.cell, (np.asarray(x) - self.ox) / self.cell  # (row, col) in cell units

    def centres(self):
        c = (np.arange(self.n) + 0.5) * self.cell
        return self.ox + c[None, :] + 0 * c[:, None], self.oz + c[:, None] + 0 * c[None, :]

    def ij_to_xz(self, i, j):
        return self.ox + (np.asarray(j) + 0.5) * self.cell, self.oz + (np.asarray(i) + 0.5) * self.cell


def to_game(v):
    """Mesh mm (x east, y north, z up) -> game metres (x east, z south) and raw z."""
    return v[:, 0] * M_PER_MM, -v[:, 1] * M_PER_MM


def tri_cells(v, t, grid):
    gx, gz = to_game(v)
    r, c = grid.xz_to_ij(gx, gz)
    pts = np.stack([c, r], axis=1)
    return pts[t]  # (n,3,2) as (x=col, y=row)


def write_height(path, grid, h, vmin, vmax):
    q = np.round((h - vmin) / (vmax - vmin) * 65535.0).clip(0, 65535).astype("<u2")
    with open(path, "wb") as f:
        f.write(b"FHM1")
        f.write(struct.pack("<HHfffff", grid.n, grid.n, grid.cell, grid.ox, grid.oz, vmin, vmax))
        f.write(q.tobytes())


# --------------------------------------------------------------------- the job

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mf3")
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap.add_argument("--out", default=os.path.join(root, "app", "src", "main", "assets", "fishing", "world"))
    ap.add_argument("--docs", default=os.path.join(root, "docs"))
    ap.add_argument("--no-png", action="store_true")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    os.makedirs(a.docs, exist_ok=True)
    try:
        run(a)
    except StopAndReport as e:
        print("STOP AND REPORT: %s" % e)
        return 1
    return 0


def run(a):
    meta3, objs = read_3mf(a.mf3)
    desc = meta3.get("Description", "")
    from urllib.parse import unquote
    desc = unquote(desc.replace("&amp;", "&"))
    m = re.search(r"sel=circle:([-\d.]+),([-\d.]+),([-\d.]+)", desc)
    ve = re.search(r"ve=([\d.]+)", desc)
    print("export circle %s  ve %s" % (m.groups() if m else "?", ve.group(1) if ve else "?"))
    if not m or abs(float(m.group(3)) * 1000 / 100.0 - M_PER_MM) > 0.05 or not ve or abs(float(ve.group(1)) - VE) > 1e-6:
        raise StopAndReport("scale cannot be confirmed from the 3MF description (expected circle radius 2.3181 km, ve 1.5)")
    centre = [float(m.group(1)), float(m.group(2))]
    for need in ("terrain", "buildings", "water", "piers", "waterfalls", "sand and beaches", "trails", "roads"):
        if need not in objs:
            raise StopAndReport("object %r missing; found %s" % (need, sorted(objs)))
    tv, tt = objs["terrain"]
    print("terrain: %d vertices, %d triangles, z %.2f..%.2f mm" % (len(tv), len(tt), tv[:, 2].min(), tv[:, 2].max()))

    # ---- lake level: top face of the largest water slab (the 3MF water is a slab: bottom 5.12, top 6.35)
    wv, wt = objs["water"]
    n, ln = tri_normals(wv, wt)
    nz = n[:, 2] / np.maximum(ln, 1e-12)
    area = 0.5 * ln
    ncomp, lab = components(len(wv), wt)
    comp = lab[wt[:, 0]]
    water_info = []
    for ci in range(ncomp):
        sel = comp == ci
        up = sel & (nz > 0.5)
        topz = float(wv[wt[up]][:, :, 2].mean()) if up.any() else float("nan")
        botz = float(wv[wv[:, 2] > 0][lab == ci, 2].min())
        ac = float(area[up].sum())
        water_info.append((ci, topz, botz, ac))
        print("  water slab %d: top z %.2f bottom z %.2f footprint %.0f mm2 (%.2f km2)" % (
            ci, topz, botz, ac, ac * M_PER_MM ** 2 / 1e6))
    main_w = max(water_info, key=lambda r: r[3])
    lake_z = main_w[1]
    print("lake level z = %.3f mm (top of the largest water slab); slab bottom %.2f" % (lake_z, main_w[2]))
    if not np.isfinite(lake_z):
        raise StopAndReport("lake level cannot be determined (no up-facing water triangles)")
    # water polygons used: every slab whose top is within 0.1 mm (about 1.5 m) of the lake level
    use_water = [r[0] for r in water_info if abs(r[1] - lake_z) <= 0.1 + 1e-9]
    other_water = [r for r in water_info if r[0] not in use_water]
    print("water slabs used as lake: %s; others ignored (different level): %s" % (
        use_water, [(r[0], round(r[1], 2)) for r in other_water]))

    hscale = M_PER_MM / VE

    # ---- terrain: top-facing triangles only
    n, ln = tri_normals(tv, tt)
    topf = (n[:, 2] > 0) & (tv[tt][:, :, 2].max(1) >= 0.01)
    ttop = tt[topf]
    print("terrain top-facing triangles: %d of %d" % (len(ttop), len(tt)))
    zmm = tv[:, 2]

    gh = Grid(PLAY_R, CELL_H)
    gf = Grid(FAR_R, CELL_FAR)
    gm = Grid(PLAY_R, CELL_MASK)
    gs = Grid(PLAY_R, CELL_SHORE)
    gd = Grid(PLAY_R, CELL_DEPTH)
    assert gh.n == 750 and gf.n == 290 and gm.n == 1500 and gs.n == 750 and gd.n == 375

    def terrain_height(grid):
        P = tri_cells(tv, ttop, grid)
        z = (zmm[ttop] - lake_z) * hscale
        r = raster(P, z, (grid.n, grid.n))
        r, empty = fill_nearest(r)
        return r, empty

    H, empty = terrain_height(gh)
    HF, emptyF = terrain_height(gf)
    print("height.bin %dx%d, empty cells before fill: %d (%.3f%%)" % (gh.n, gh.n, empty, 100.0 * empty / gh.n ** 2))
    print("height_far.bin %dx%d, empty cells before fill: %d (%.3f%%)" % (gf.n, gf.n, emptyF, 100.0 * emptyF / gf.n ** 2))
    # empty cells outside the far circle are expected (the export is a circle); measure inside the play circle only
    X, Z = gh.centres()
    inside_h = X ** 2 + Z ** 2 <= PLAY_R ** 2
    if empty > 0.01 * gh.n ** 2:
        raise StopAndReport("terrain has holes: %d empty cells (%.2f%%) in the height grid" % (empty, 100.0 * empty / gh.n ** 2))
    hmin, hmax = float(H[inside_h].min()), float(H[inside_h].max())
    print("relief inside the 1.5 km circle: %.1f .. %.1f m above the lake" % (hmin, hmax))
    fmin, fmax = float(HF.min()), float(HF.max())
    print("relief over the whole export circle: %.1f .. %.1f m" % (fmin, fmax))
    write_height(os.path.join(a.out, "height.bin"), gh, H, float(H.min()), float(H.max()))
    write_height(os.path.join(a.out, "height_far.bin"), gf, HF, float(HF.min()), float(HF.max()))

    # ---- masks (2 m): bit0 water, bit1 building, bit2 road, bit3 trail, bit4 sand, bit5 pier, bit6 waterfall
    def cover(name, sel=None, up_only=False, grid=gm):
        v, t = objs[name]
        if sel is not None:
            t = t[sel]
        if up_only:
            n_, l_ = tri_normals(v, t)
            t = t[n_[:, 2] / np.maximum(l_, 1e-12) > 0.5]
        r = raster(tri_cells(v, t, grid), None, (grid.n, grid.n))
        return ~np.isnan(r)

    wsel = np.isin(comp, use_water)
    bit0 = cover("water", wsel, up_only=True)
    bit1 = cover("buildings", up_only=True)
    bit2 = cover("roads", up_only=True)
    bit3 = cover("trails", up_only=True)
    bit4 = cover("sand and beaches", up_only=True)
    bit5 = cover("piers", up_only=True)
    bit6 = cover("waterfalls")
    # a pier over the water counts as pier, not as land: water bit stays (the shore field uses water only)
    masks = (bit0.astype(np.uint8) | (bit1 << 1).astype(np.uint8) | (bit2 << 2).astype(np.uint8) | (bit3 << 3).astype(np.uint8)
             | (bit4 << 4).astype(np.uint8) | (bit5 << 5).astype(np.uint8) | (bit6 << 6).astype(np.uint8))
    with open(os.path.join(a.out, "masks.bin"), "wb") as f:
        f.write(b"FMK1")
        f.write(struct.pack("<HHfff", gm.n, gm.n, gm.cell, gm.ox, gm.oz))
        f.write(masks.tobytes())
    Xm, Zm = gm.centres()
    in_play_m = Xm ** 2 + Zm ** 2 <= PLAY_R ** 2
    print("masks (cells in play circle): water %d  building %d  road %d  trail %d  sand %d  pier %d  waterfall %d" % tuple(
        int((((masks >> b) & 1).astype(bool) & in_play_m).sum()) for b in range(7)))

    # ---- shore distance (4 m): signed, water negative, land positive, clipped to +/-300 m, i16 decimetres
    water4 = bit0.reshape(gs.n, 2, gs.n, 2).sum(axis=(1, 3)) >= 2
    d_land = ndi.distance_transform_edt(~water4) * CELL_SHORE      # land cells: distance to nearest water cell
    d_water = ndi.distance_transform_edt(water4) * CELL_SHORE      # water cells: distance to nearest land cell
    shore = np.where(water4, -(d_water - CELL_SHORE / 2.0), d_land - CELL_SHORE / 2.0)
    shore = np.clip(shore, -300.0, 300.0)
    with open(os.path.join(a.out, "shore.bin"), "wb") as f:
        f.write(b"FSD1")
        f.write(struct.pack("<HHfff", gs.n, gs.n, gs.cell, gs.ox, gs.oz))
        f.write(np.round(shore * 10.0).astype("<i2").tobytes())

    # ---- depth (8 m): synthetic, d * slope of the nearest land, smoothed
    gy, gx = np.gradient(H, CELL_H)
    slope = np.hypot(gx, gy)
    land4 = ~water4
    r_px = int(round(40.0 / CELL_H))
    k = np.ones((2 * r_px + 1, 2 * r_px + 1))
    k = ndi.uniform_filter(land4.astype(float), size=2 * r_px + 1)
    ks = ndi.uniform_filter(np.where(land4, slope, 0.0), size=2 * r_px + 1)
    mean_slope = np.where(k > 1e-6, ks / np.maximum(k, 1e-6), 0.3)
    idx = ndi.distance_transform_edt(water4, return_distances=False, return_indices=True)  # nearest land cell for water cells
    s_near = mean_slope[idx[0], idx[1]]
    s_near = np.clip(s_near, 0.15, 0.8)
    dist_w = np.maximum(-shore, 0.0)
    depth4 = np.minimum(120.0, dist_w * s_near)
    depth4 = np.where(water4, depth4, 0.0)
    sig = 20.0 / CELL_SHORE
    num = ndi.gaussian_filter(depth4, sig)
    den = ndi.gaussian_filter(water4.astype(float), sig)
    depth4 = np.where(water4, num / np.maximum(den, 1e-6), 0.0)
    rng = np.random.RandomState(SEED)
    noise = ndi.gaussian_filter(rng.uniform(-1.0, 1.0, depth4.shape), 3.0)
    noise = noise / max(float(np.abs(noise).max()), 1e-9) * 0.08
    depth4 = np.where(water4, depth4 * (1.0 + noise), 0.0)
    depth4 = np.where(water4, np.clip(depth4, 0.3, 120.0), 0.0)
    depth8 = depth4.reshape(gd.n, 2, gd.n, 2).mean(axis=(1, 3))
    w8 = water4.reshape(gd.n, 2, gd.n, 2).sum(axis=(1, 3)) >= 2
    depth8 = np.where(w8, depth8 * 4.0 / np.maximum(water4.reshape(gd.n, 2, gd.n, 2).sum(axis=(1, 3)), 1), 0.0)
    # the average above divided by the water count of the 2x2 block (not by 4)
    depth8 = np.where(w8, depth4.reshape(gd.n, 2, gd.n, 2).sum(axis=(1, 3)) / np.maximum(water4.reshape(gd.n, 2, gd.n, 2).sum(axis=(1, 3)), 1), 0.0)
    with open(os.path.join(a.out, "depth.bin"), "wb") as f:
        f.write(b"FDP1")
        f.write(struct.pack("<HHfff", gd.n, gd.n, gd.cell, gd.ox, gd.oz))
        f.write(np.clip(np.round(depth8 * 2.0), 0, 255).astype(np.uint8).tobytes())
    Xs, Zs = gs.centres()
    in_play_s = Xs ** 2 + Zs ** 2 <= PLAY_R ** 2
    print("lake in play circle: %.2f km2, max depth %.1f m (synthetic)" % (
        (water4 & in_play_s).sum() * CELL_SHORE ** 2 / 1e6, depth8.max()))

    # ---- buildings
    bv, bt = objs["buildings"]
    buildings = make_buildings(bv, bt, gh, H, hscale)
    # ---- piers (needs the shore field for "farthest from land")
    piers = make_piers(objs["piers"], gs, shore, hscale)
    # ---- waterfalls
    falls = make_falls(objs["waterfalls"], gh, H, hscale)

    # ---- start: pier nearest to the townsite building cluster
    start, pier_report = choose_start(buildings, piers, gs, shore)
    for line in pier_report:
        print("  " + line)

    hx, hz = HOTEL
    hi, hj = gh.xz_to_ij(hx, hz)
    hotel_y = float(H[int(hi), int(hj)])
    meta = {
        "mPerMm": round(M_PER_MM, 3), "ve": VE, "lakeZmm": round(lake_z, 4), "centreLonLat": centre,
        "playRadius": int(PLAY_R), "farRadius": int(FAR_R),
        "hotel": {"x": hx, "z": hz, "groundY": round(hotel_y, 2)},
        "start": start,
        "files": {"height": "height.bin", "heightFar": "height_far.bin", "masks": "masks.bin", "shore": "shore.bin",
                  "depth": "depth.bin"},
        "waterSlabsUsed": [int(u) for u in use_water],
        "reliefM": [round(hmin, 1), round(hmax, 1)],
    }
    write_json(os.path.join(a.out, "meta.json"), meta)
    write_json(os.path.join(a.out, "buildings.json"), buildings)
    write_json(os.path.join(a.out, "piers.json"), piers)
    write_json(os.path.join(a.out, "waterfalls.json"), falls)

    # ---- spot candidates
    cand, landmarks, counts = make_candidates(gs, shore, water4, masks, bit4, gm, H, gh, depth4, piers, start, hotel_y, falls)
    write_json(os.path.join(a.out, "spots_candidates.json"), {"spots": cand, "landmarks": landmarks})
    print("candidates kept: %d  by type %s" % (len(cand), counts))
    near = [c for c in cand if c["distFromStart"] <= 700]
    print("within 700 m of start: %d total, PIER %d, others %d" % (
        len(near), sum(c["type"] == "PIER" for c in near), sum(c["type"] != "PIER" for c in near)))

    total = sum(os.path.getsize(os.path.join(a.out, f)) for f in os.listdir(a.out))
    print("output size: %.2f MB" % (total / 1e6))
    for f in sorted(os.listdir(a.out)):
        print("   %-22s %9d bytes" % (f, os.path.getsize(os.path.join(a.out, f))))

    if not a.no_png:
        draw_previews(a.docs, gh, H, gs, shore, water4, buildings, piers, falls, cand, landmarks, start, hotel_y)


def write_json(path, obj):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=1, sort_keys=True)
        f.write("\n")


# ------------------------------------------------------------------- features

def make_buildings(bv, bt, gh, H, hscale):
    from shapely.geometry import Polygon
    from shapely.ops import unary_union
    n, ln = tri_normals(bv, bt)
    up = n[:, 2] / np.maximum(ln, 1e-12) > 0.5
    ncomp, lab = components(len(bv), bt)
    comp = lab[bt[:, 0]]
    gx, gz = to_game(bv)
    xz = np.stack([gx, gz], axis=1)
    items = []
    # components that touch (share footprint edges) are merged by the union below; first one polygon per component
    polys = []
    for ci in range(ncomp):
        sel = comp == ci
        if not sel.any():
            continue
        top = bt[sel & up]
        if not len(top):
            continue
        zs = bv[bt[sel]][:, :, 2]
        height = (zs.max() - zs.min()) * hscale
        tris = [Polygon(xz[t]) for t in top if Polygon(xz[t]).area > 1e-9]
        u = unary_union([p.buffer(0.01) for p in tris]).buffer(-0.01)
        if u.is_empty:
            continue
        polys.append((u, height))
    # merge touching footprints
    geoms = [p for p, _ in polys]
    heights = [h for _, h in polys]
    order = np.argsort([g.centroid.x * 1e4 + g.centroid.y for g in geoms], kind="stable")
    merged = []
    used = set()
    for i in order:
        if i in used:
            continue
        group = [i]
        used.add(i)
        changed = True
        while changed:
            changed = False
            for j in order:
                if j in used:
                    continue
                if any(geoms[g].buffer(0.3).intersects(geoms[j]) for g in group):
                    group.append(j)
                    used.add(j)
                    changed = True
        merged.append(group)
    out = []
    for bid, group in enumerate(merged):
        u = unary_union([geoms[g] for g in group])
        if u.geom_type != "Polygon":
            u = max(u.geoms, key=lambda g: g.area)
        height = max(heights[g] for g in group)
        if height < 3.0:
            height = 7.0
        poly = u
        tol = 0.4
        simp = poly.simplify(tol, preserve_topology=True)
        while len(simp.exterior.coords) - 1 > 12 and tol < 20:
            tol *= 1.5
            simp = poly.simplify(tol, preserve_topology=True)
        pts = [[round(x, 1), round(z, 1)] for x, z in list(simp.exterior.coords)[:-1]]
        if len(pts) < 3:
            continue
        cx, cz = poly.centroid.x, poly.centroid.y
        i, j = gh.xz_to_ij(cx, cz)
        base = float(H[int(np.clip(i, 0, gh.n - 1)), int(np.clip(j, 0, gh.n - 1))]) if True else 0.0
        out.append({"id": bid, "polygon": pts, "height": round(float(height), 1), "baseY": round(base, 1),
                    "area": round(float(poly.area), 1)})
    # keep only buildings whose centroid is inside the play circle plus a margin (far ones are scenery)
    keep = [b for b in out if math.hypot(np.mean([p[0] for p in b["polygon"]]), np.mean([p[1] for p in b["polygon"]])) <= PLAY_R + 60]
    for k, b in enumerate(keep):
        b["id"] = k
    print("buildings: %d components -> %d merged footprints -> %d inside the play circle (+60 m)" % (
        ncomp, len(out), len(keep)))
    return keep


def make_piers(pv_pt, gs, shore, hscale):
    """One pier per cluster of touching boxes (components within 3 m of each other are one pier)."""
    from shapely.geometry import MultiPoint
    from shapely.ops import unary_union
    pv, pt = pv_pt
    ncomp, lab = components(len(pv), pt)
    gx, gz = to_game(pv)
    hulls = []
    for ci in range(ncomp):
        m = lab == ci
        hulls.append(MultiPoint(np.stack([gx[m], gz[m]], axis=1)).convex_hull.buffer(1.5))
    merged = unary_union(hulls)
    groups = list(merged.geoms) if merged.geom_type == "MultiPolygon" else [merged]
    groups.sort(key=lambda g: (round(g.centroid.x, 1), round(g.centroid.y, 1)))
    out = []

    def sh(pt_):
        i, j = gs.xz_to_ij(pt_[0], pt_[1])
        i, j = int(np.clip(i, 0, gs.n - 1)), int(np.clip(j, 0, gs.n - 1))
        return float(shore[i, j])

    for gid, g in enumerate(groups):
        pts = np.array([[gx[k], gz[k]] for k in range(len(pv)) if g.contains(MultiPoint([(gx[k], gz[k])]))])
        if len(pts) < 3:
            continue
        c = pts.mean(0)
        u, s_, vt = np.linalg.svd(pts - c)
        ax = vt[0]
        t = (pts - c) @ ax
        a, b = c + ax * t.min(), c + ax * t.max()
        if sh(a) > sh(b):          # a = land end, b = water end (farthest from land = most negative shore)
            a, b = b, a
        land_end, water_end = a, b
        length = float(np.linalg.norm(water_end - land_end))
        out.append({"id": len(out), "polyline": [[round(float(land_end[0]), 1), round(float(land_end[1]), 1)],
                                                [round(float(water_end[0]), 1), round(float(water_end[1]), 1)]],
                    "end": [round(float(water_end[0]), 1), round(float(water_end[1]), 1)],
                    "width": 2.5, "deckY": 0.9, "length": round(length, 1), "endShore": round(sh(water_end), 1)})
    print("piers: %d boxes -> %d piers" % (ncomp, len(out)))
    return out


def make_falls(fv_ft, gh, H, hscale):
    fv, ft = fv_ft
    ncomp, lab = components(len(fv), ft)
    gx, gz = to_game(fv)
    out = []
    for ci in range(ncomp):
        m = lab == ci
        drop = float(fv[m, 2].max() - fv[m, 2].min()) * hscale
        top = fv[m][np.argmax(fv[m, 2])]
        x, z = float(gx[m].mean()), float(gz[m].mean())
        out.append({"id": ci, "x": round(x, 1), "z": round(z, 1), "drop": round(drop, 1)})
    print("waterfalls: %d" % len(out))
    return out


def choose_start(buildings, piers, gs, shore):
    """Start = water 25 m beyond the end of the pier nearest the townsite building cluster centroid."""
    cx = np.array([np.mean([p[0] for p in b["polygon"]]) for b in buildings])
    cz = np.array([np.mean([p[1] for p in b["polygon"]]) for b in buildings])
    # townsite = densest part: the median of buildings with at least 10 neighbours within 150 m
    cnt = np.array([(np.hypot(cx - x, cz - z) < 150).sum() for x, z in zip(cx, cz)])
    dense = cnt >= max(10, np.percentile(cnt, 60))
    centroid = (float(cx[dense].mean()), float(cz[dense].mean())) if dense.any() else (float(cx.mean()), float(cz.mean()))
    report = ["building cluster centroid (dense part): (%.0f, %.0f) m" % centroid]
    cands = []
    for p in piers:
        e = np.array(p["end"])
        d = float(np.hypot(e[0] - centroid[0], e[1] - centroid[1]))
        i, j = gs.xz_to_ij(e[0], e[1])
        i, j = int(np.clip(i, 0, gs.n - 1)), int(np.clip(j, 0, gs.n - 1))
        inplay = math.hypot(e[0], e[1]) <= PLAY_R
        cands.append((d, p["id"], inplay, p["length"], float(shore[i, j])))
    cands.sort()
    for d, pid, inplay, length, sh in cands[:8]:
        report.append("pier candidate %d: end %.0f m from the townsite centroid, length %.1f m, end shore %.1f m, in play circle %s" % (
            pid, d, length, sh, inplay))
    best = None
    for d, pid, inplay, length, sh in cands:
        p = piers[pid]
        if inplay and sh < -1.0 and length >= 8.0:
            best = p
            break
    if best is None:
        raise StopAndReport("no usable pier end in the water near the townsite")
    land, end = np.array(best["polyline"][0]), np.array(best["polyline"][1])
    dirv = (end - land) / max(np.linalg.norm(end - land), 1e-9)
    # step 25 m beyond the end; if that is not water, search the compass for open water
    def water_at(pt):
        i, j = gs.xz_to_ij(pt[0], pt[1])
        i, j = int(np.clip(i, 0, gs.n - 1)), int(np.clip(j, 0, gs.n - 1))
        return float(shore[i, j]) < -6.0
    pos = end + dirv * 25.0
    if not water_at(pos):
        bestscore = -1
        for ang in np.arange(0, 360, 15):
            dv = np.array([math.sin(math.radians(ang)), math.cos(math.radians(ang))])  # yaw 0 -> +z
            sc = 0
            for rr in range(5, 300, 5):
                q = end + dv * rr
                if not water_at(q):
                    break
                sc = rr
            if sc > bestscore and water_at(end + dv * 25.0):
                bestscore, dirv = sc, dv
        pos = end + dirv * 25.0
    # yaw: 0 faces +Z (south), positive turns toward +X (east): yaw = atan2(dx, dz)
    # point to the open water: the direction of longest clear water run within 15 degrees of the pier axis
    bestrun, bestang = -1, 0.0
    base = math.degrees(math.atan2(dirv[0], dirv[1]))
    for off in range(-60, 61, 10):
        ang = base + off
        dv = np.array([math.sin(math.radians(ang)), math.cos(math.radians(ang))])
        run = 0
        for rr in range(5, 800, 5):
            if not water_at(pos + dv * rr):
                break
            run = rr
        if run > bestrun:
            bestrun, bestang = run, ang
    st = {"x": round(float(pos[0]), 1), "z": round(float(pos[1]), 1), "yawDeg": round(float(bestang % 360.0), 1),
          "pierId": best["id"], "openWaterM": bestrun}
    report.append("start: (%.0f, %.0f) 25 m beyond the end of pier %d, yaw %.0f deg, open water %d m ahead" % (
        st["x"], st["z"], best["id"], st["yawDeg"], bestrun))
    return st, report


def thin(points, mind, limit):
    """Greedy pick by score with a minimum separation; points: list of dicts with x, z, score."""
    out = []
    for p in sorted(points, key=lambda q: (-q["score"], q["x"], q["z"])):
        if all(math.hypot(p["x"] - o["x"], p["z"] - o["z"]) >= mind for o in out):
            out.append(p)
            if len(out) >= limit:
                break
    return out


def make_candidates(gs, shore, water4, masks, sand2, gm, H, gh, depth4, piers, start, hotel_y, falls):
    Xs, Zs = gs.centres()
    play = Xs ** 2 + Zs ** 2 <= (PLAY_R - 30.0) ** 2
    sx, sz = start["x"], start["z"]
    dstart = np.hypot(Xs - sx, Zs - sz)
    cands = []
    water_depth = np.where(water4, depth4, np.nan)

    def pick(mask, score, ctype, limit, mind=120.0, extra=None):
        ii, jj = np.nonzero(mask & play)
        if not len(ii):
            return []
        pts = [{"x": float(Xs[i, j]), "z": float(Zs[i, j]), "score": float(score[i, j]), "type": ctype} for i, j in zip(ii, jj)]
        keep = thin(pts, mind, limit)
        return keep

    # PIER: dock POV at every pier end in the play circle
    for p in piers:
        x, z = p["end"]
        if math.hypot(x, z) <= PLAY_R - 20:
            cands.append({"x": x, "z": z, "score": 1.0 + min(p["length"], 60.0) / 60.0, "type": "PIER"})
    # BEACH: water cells within 20 m of sand, depth < 3 m
    sand4 = sand2.reshape(gs.n, 2, gs.n, 2).sum(axis=(1, 3)) >= 1
    near_sand = ndi.distance_transform_edt(~sand4) * CELL_SHORE <= 20.0
    beach = water4 & near_sand & (depth4 < 3.0) & (depth4 > 0.2)
    cands += pick(beach, 1.0 - np.minimum(depth4, 3.0) / 3.0 + 0.5 * (sand4.reshape(-1).sum() > 0), "BEACH", 30)
    # DROPOFF: steep shore (slope > 0.5) with depth 4..12 m within 60 m of it
    gy, gx = np.gradient(H, CELL_H)
    slope = np.hypot(gx, gy)
    landmask = ~water4
    ks = ndi.uniform_filter(np.where(landmask, slope, 0.0), size=21) / np.maximum(ndi.uniform_filter(landmask.astype(float), size=21), 1e-6)
    idx = ndi.distance_transform_edt(water4, return_distances=False, return_indices=True)
    s_near = ks[idx[0], idx[1]]
    drop = water4 & (s_near > 0.5) & (depth4 >= 4.0) & (depth4 <= 12.0) & (-shore <= 60.0)
    cands += pick(drop, s_near, "DROPOFF", 30)
    # BAY: concave shoreline: water cells where the smoothed land fraction (sigma 60 m) is high; depth < 6 m
    landfrac = ndi.gaussian_filter((~water4).astype(float), 60.0 / CELL_SHORE)
    bay = water4 & (landfrac > 0.42) & (depth4 < 6.0) & (depth4 > 0.5)
    cands += pick(bay, landfrac, "BAY", 30)
    # STREAM: shore cells near a road/trail crossing the water edge, plus other water polygons
    rt = ((masks >> 2) & 1) | ((masks >> 3) & 1)
    rt4 = rt.reshape(gs.n, 2, gs.n, 2).sum(axis=(1, 3)) >= 1
    near_rt = ndi.distance_transform_edt(~rt4) * CELL_SHORE <= 40.0
    stream = water4 & near_rt & (-shore <= 40.0) & (depth4 < 8.0)
    narrow = ndi.gaussian_filter(water4.astype(float), 25.0 / CELL_SHORE)
    cands += pick(stream, 1.0 - narrow, "STREAM", 30)
    # BASIN: depth > 40 m and at least 150 m from shore
    basin = water4 & (depth4 > 40.0) & (-shore >= 150.0)
    cands += pick(basin, depth4 / 120.0, "BASIN", 20)
    # score = type score blended with closeness to the start (so the first round has near spots)
    for c in cands:
        d = math.hypot(c["x"] - sx, c["z"] - sz)
        c["distFromStart"] = round(d, 0)
        i, j = gs.xz_to_ij(c["x"], c["z"])
        i, j = int(np.clip(i, 0, gs.n - 1)), int(np.clip(j, 0, gs.n - 1))
        c["depth"] = round(float(depth4[i, j]), 1)
        c["radius"] = 14
        c["score"] = round(float(c["score"]) + 0.6 * max(0.0, 1.0 - d / 1500.0), 3)
    # keep the best 24, at least 120 m apart: first up to 12 within 700 m of the start (so the first round has choice),
    # with per-type quotas so every type can appear, then the best of the rest of the lake
    picked = []
    counts = {k: 0 for k in ("PIER", "BEACH", "DROPOFF", "BAY", "STREAM", "BASIN")}

    def ok_pick(c):
        return all(math.hypot(c["x"] - o["x"], c["z"] - o["z"]) >= 120.0 for o in picked)

    ranked = sorted(cands, key=lambda q: (-q["score"], q["x"], q["z"]))
    near_quota = {"PIER": 2, "BEACH": 2, "DROPOFF": 3, "BAY": 2, "STREAM": 2, "BASIN": 1}
    for c in ranked:
        if c["distFromStart"] <= 700 and counts[c["type"]] < near_quota[c["type"]] and ok_pick(c):
            picked.append(c)
            counts[c["type"]] += 1
    far_quota = {"PIER": 4, "BEACH": 4, "DROPOFF": 5, "BAY": 4, "STREAM": 3, "BASIN": 3}
    for c in ranked:
        if len(picked) >= 24:
            break
        if c not in picked and counts[c["type"]] < far_quota[c["type"]] and ok_pick(c):
            picked.append(c)
            counts[c["type"]] += 1
    for c in ranked:                      # fill up if the quotas left room
        if len(picked) >= 24:
            break
        if c not in picked and ok_pick(c):
            picked.append(c)
            counts[c["type"]] += 1
    picked.sort(key=lambda q: (q["distFromStart"], q["x"], q["z"]))
    for k, c in enumerate(picked):
        c["id"] = k
        c["x"], c["z"] = round(c["x"], 1), round(c["z"], 1)
    landmarks = [{"type": "HOTEL", "x": HOTEL[0], "z": HOTEL[1], "groundY": round(hotel_y, 1)}]
    for f in falls:
        if math.hypot(f["x"], f["z"]) <= PLAY_R + 100:
            landmarks.append({"type": "WATERFALL", "x": f["x"], "z": f["z"], "drop": f["drop"]})
    return picked, landmarks, counts


# --------------------------------------------------------------------- pictures

def draw_previews(docs, gh, H, gs, shore, water4, buildings, piers, falls, cand, landmarks, start, hotel_y):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.patches import Circle, Polygon as MPoly
    gy, gx = np.gradient(H, CELL_H)
    hs = np.clip(0.5 + (gx * 0.7 + gy * 0.7) / (2 * 1.2), 0, 1)
    ext = [gh.ox, gh.ox + gh.n * gh.cell, gh.oz + gh.n * gh.cell, gh.oz]

    def base(title, figsize=9):
        fig, ax = plt.subplots(figsize=(figsize, figsize))
        ax.imshow(hs, extent=ext, cmap="gray", alpha=0.9)
        ax.imshow(np.ma.masked_where(water4.repeat(1, 0) if False else np.zeros_like(H, bool), H), extent=ext, cmap="terrain", alpha=0.0)
        im = ax.imshow(np.clip(H, -20, 900), extent=ext, cmap="terrain", alpha=0.4)
        wm = np.ma.masked_where(~water4, np.ones_like(water4, float))
        ax.imshow(wm, extent=ext, cmap="Blues", vmin=0, vmax=2, alpha=0.85)
        for b in buildings:
            ax.add_patch(MPoly(b["polygon"], closed=True, fc="#c0392b", ec="#c0392b", lw=0.4))
        for p in piers:
            (x0, z0), (x1, z1) = p["polyline"]
            ax.plot([x0, x1], [z0, z1], color="#f1c40f", lw=1.6)
        ax.add_patch(Circle((0, 0), PLAY_R, fill=False, ec="yellow", ls="--", lw=1.5))
        ax.plot(HOTEL[0], HOTEL[1], marker="*", color="magenta", ms=14, mec="k")
        ax.annotate("Prince of Wales (hotel)", HOTEL, color="magenta", fontsize=7, xytext=(6, 6), textcoords="offset points")
        ax.plot(start["x"], start["z"], marker="v", color="lime", ms=10, mec="k")
        ax.set_xlim(-1650, 1650)
        ax.set_ylim(1650, -1650)
        ax.set_xlabel("x east (m)")
        ax.set_ylabel("z south (m)")
        ax.set_title(title, fontsize=9)
        return fig, ax, im

    fig, ax, im = base("Waterton: hillshade, lake (blue), buildings (red), piers (yellow), start (green), hotel (magenta). "
                       "Yellow circle = 1.5 km play radius")
    plt.colorbar(im, ax=ax, fraction=0.03, label="m above lake")
    fig.savefig(os.path.join(docs, "fishing_world_preview.png"), dpi=100, bbox_inches="tight")
    plt.close(fig)
    cols = {"PIER": "#f1c40f", "BEACH": "#f5cba7", "DROPOFF": "#e74c3c", "BAY": "#1abc9c", "STREAM": "#3498db", "BASIN": "#8e44ad"}
    fig, ax, im = base("Spot candidates by type (numbers = id); circle = 1.5 km play radius")
    for c in cand:
        ax.plot(c["x"], c["z"], "o", color=cols[c["type"]], mec="k", ms=7)
        ax.annotate("%d" % c["id"], (c["x"], c["z"]), fontsize=6, xytext=(4, 4), textcoords="offset points")
    for t, col in cols.items():
        ax.plot([], [], "o", color=col, mec="k", label=t)
    ax.legend(loc="lower left", fontsize=7)
    fig.savefig(os.path.join(docs, "fishing_world_spots.png"), dpi=100, bbox_inches="tight")
    plt.close(fig)


if __name__ == "__main__":
    sys.exit(main())
