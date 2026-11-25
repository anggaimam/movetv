package com.wiggleplay.app.games.race;

import java.util.Random;

/**
 * The street props of City Lights (T-30a), built with {@link MeshKit}: buildings and towers (window bays, shopfronts, awnings,
 * parapets), lamps, street trees, parked cars, bus stops, benches, hydrants, bins, signs, cones and flower planters. Plain Java.
 *
 * <p>Local frame: the front (shopfront, windows, lamp arm, bench seat) faces +z; a parked car's length runs along z; the base is at
 * y = 0; there are no bottom faces. Colour ramps are given as {@code sw}, in the order of the kind's rule (see {@link #RAMPS}).
 * Flat faces are used for walls, windows and boxes; the smooth ones (tree crowns, car body, posts) come from {@link MeshKit#grid}.
 */
final class CityProps {

    private CityProps() { }

    // ---- small helpers ----

    /** A box without a bottom (and, with {@code top} false, without a top); the ramp position runs from tb at the base to tt at the top. */
    static void box(MeshKit k, int sw, float x0, float y0, float z0, float x1, float y1, float z1, float tb, float tt, boolean top) {
        k.flatQuad(f(x0, y0, z1), f(x1, y0, z1), f(x1, y1, z1), f(x0, y1, z1), tb, tb, tt, tt, sw, 0f, 0f, 1f);
        k.flatQuad(f(x1, y0, z0), f(x0, y0, z0), f(x0, y1, z0), f(x1, y1, z0), tb, tb, tt, tt, sw, 0f, 0f, -1f);
        k.flatQuad(f(x1, y0, z1), f(x1, y0, z0), f(x1, y1, z0), f(x1, y1, z1), tb, tb, tt, tt, sw, 1f, 0f, 0f);
        k.flatQuad(f(x0, y0, z0), f(x0, y0, z1), f(x0, y1, z1), f(x0, y1, z0), tb, tb, tt, tt, sw, -1f, 0f, 0f);
        if (top) k.flatQuad(f(x0, y1, z1), f(x1, y1, z1), f(x1, y1, z0), f(x0, y1, z0), tt, tt, tt, tt, sw, 0f, 1f, 0f);
    }

    private static float[] f(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    /** A vertical quad from (ax, az) to (bx, bz) in plan, between two heights, facing the side of (hx, hz). */
    static void vquad(MeshKit k, int sw, float ax, float az, float bx, float bz, float y0, float y1, float t0, float t1, float hx,
                      float hz) {
        k.flatQuad(f(ax, y0, az), f(bx, y0, bz), f(bx, y1, bz), f(ax, y1, az), t0, t0, t1, t1, sw, hx, 0f, hz);
    }

    /** A flat quad facing up. */
    static void hquad(MeshKit k, int sw, float x0, float z0, float x1, float z1, float y, float t) {
        k.flatQuad(f(x0, y, z1), f(x1, y, z1), f(x1, y, z0), f(x0, y, z0), t, t, t, t, sw, 0f, 1f, 0f);
    }

    /** A disc of n triangles in the plane z = const, centre (cx, cy), facing +z (dir 1) or -z (dir -1). */
    static void disc(MeshKit k, int sw, float cx, float cy, float z, float r, int n, float t, float dir) {
        float[] c = f(cx, cy, z);
        for (int i = 0; i < n; i++) {
            double a0 = 2.0 * Math.PI * i / n, a1 = 2.0 * Math.PI * (i + 1) / n;
            k.flatTri(c, f(cx + r * (float) Math.cos(a0), cy + r * (float) Math.sin(a0), z),
                    f(cx + r * (float) Math.cos(a1), cy + r * (float) Math.sin(a1), z), t, t, t, sw, 0f, 0f, dir);
        }
    }

    /** A rounded blob (an ellipsoid with a little jitter): rings from the bottom pole to the top pole, smooth normals. */
    static void blob(MeshKit k, float cx, float cy, float cz, float rx, float ry, float rz, int sides, int rings, int sw,
                             float t0, float t1, Random rnd) {
        float[][][] p = new float[rings][sides][3];
        float[][] t = new float[rings][sides];
        float[] jit = new float[sides];
        for (int s = 0; s < sides; s++) jit[s] = 1f + 0.14f * (rnd.nextFloat() - 0.5f);
        for (int r = 0; r < rings; r++) {
            double phi = Math.PI * r / (rings - 1);
            float y = -(float) Math.cos(phi), sr = (float) Math.sin(phi);
            for (int s = 0; s < sides; s++) {
                double th = 2.0 * Math.PI * s / sides;
                float j = r == 0 || r == rings - 1 ? 1f : jit[s];
                p[r][s][0] = cx + rx * sr * (float) Math.cos(th) * j;
                p[r][s][1] = cy + ry * y;
                p[r][s][2] = cz + rz * sr * (float) Math.sin(th) * j;
                t[r][s] = t0 + (t1 - t0) * (0.5f + 0.5f * y) + 0.06f * (jit[s] - 1f) / 0.07f;
            }
        }
        k.grid(p, t, sw, true);
    }

    // ---- buildings ----

    /** Building width (along the road), depth, floors and bay width, per variant: brick, glass office, apartments. */
    private static final float[] B_W = {8f, 8f, 9f}, B_BAY = {2f, 1.6f, 2.25f};
    private static final int[] B_FLOORS = {4, 7, 5};
    static final float FLOOR = 3f, B_D = 7f;

    /** Height of a building variant at scale 1 (before the parapet). */
    static float buildingHeight(int variant) {
        return B_FLOORS[variant] * FLOOR;
    }

    /**
     * One building. Ramps: 0 brick, 1 office, 2 apartments, 3 windows, 4 lit windows, 5 roof, 6 awning.
     */
    static void building(MeshKit k, int variant, int[] sw, Random rnd) {
        int wall = sw[variant], win = sw[3], lit = sw[4], roof = sw[5], awn = sw[6];
        float w = B_W[variant], d = B_D, bw = B_BAY[variant], h = B_FLOORS[variant] * FLOOR;
        float x0 = -w / 2f, x1 = w / 2f, zf = d / 2f, zb = -d / 2f;
        // walls
        vquad(k, wall, x0, zf, x1, zf, 0f, h, 0.3f, 0.92f, 0f, 1f);
        vquad(k, wall, x1, zb, x0, zb, 0f, h, 0.3f, 0.92f, 0f, -1f);
        vquad(k, wall, x1, zf, x1, zb, 0f, h, 0.3f, 0.92f, 1f, 0f);
        vquad(k, wall, x0, zb, x0, zf, 0f, h, 0.3f, 0.92f, -1f, 0f);
        // windows on the front and both sides, floors 1 up
        float winW = variant == 1 ? 1.3f : (variant == 0 ? 1.1f : 1.0f);
        float winH = variant == 1 ? 2.0f : 1.5f, sill = variant == 1 ? 0.55f : 0.9f;
        int bays = Math.round(w / bw), sideBays = Math.round(d / bw);
        for (int fl = 1; fl < B_FLOORS[variant]; fl++) {
            float y0 = fl * FLOOR + sill, y1 = y0 + winH;
            float tl = 0.2f + 0.5f * fl / B_FLOORS[variant];
            for (int b = 0; b < bays; b++) {
                float cx = x0 + bw * (b + 0.5f);
                window(k, wall, win, lit, cx - winW / 2f, cx + winW / 2f, y0, y1, zf, 0f, 1f, variant != 1, tl, rnd, 0.25f);
                if (variant == 2) {   // a balcony slab with a rail under each window
                    box(k, wall, cx - winW / 2f - 0.15f, y0 - 0.2f, zf, cx + winW / 2f + 0.15f, y0 - 0.1f, zf + 0.8f, 0.6f, 0.9f, true);
                }
            }
            for (int s = -1; s <= 1; s += 2) {
                for (int b = 0; b < sideBays; b++) {
                    float cz = zb + bw * (b + 0.5f) + (d - sideBays * bw) / 2f;
                    window(k, wall, win, lit, cz - winW / 2f, cz + winW / 2f, y0, y1, s * w / 2f, s, 0f, variant != 1, tl, rnd, 0.25f);
                }
            }
        }
        // ground floor: shopfront (brick), glass lobby (office), door and canopy (apartments)
        if (variant == 0) {
            vquad(k, win, x0 + 0.5f, zf + 0.05f, x1 - 1.9f, zf + 0.05f, 0.45f, 2.4f, 0.3f, 0.8f, 0f, 1f);
            vquad(k, roof, x0 + 0.3f, zf + 0.04f, x1 - 0.3f, zf + 0.04f, 2.45f, 3.0f, 0.3f, 0.45f, 0f, 1f);
            vquad(k, roof, x1 - 1.6f, zf + 0.05f, x1 - 0.6f, zf + 0.05f, 0f, 2.2f, 0.12f, 0.3f, 0f, 1f);
            awning(k, awn, x0 + 0.3f, x1 - 0.3f, zf, 1f, 1f);
        } else if (variant == 1) {
            for (int b = 0; b < bays; b++) {
                float cx = x0 + bw * (b + 0.5f);
                vquad(k, win, cx - 0.65f, zf + 0.05f, cx + 0.65f, zf + 0.05f, 0.3f, 2.6f, 0.3f, 0.75f, 0f, 1f);
            }
        } else {
            vquad(k, roof, -0.5f, zf + 0.05f, 0.5f, zf + 0.05f, 0f, 2.2f, 0.12f, 0.3f, 0f, 1f);
            awning(k, awn, -0.9f, 0.9f, zf, 0.9f, 0.5f);
            for (int s = -1; s <= 1; s += 2) {
                vquad(k, win, s * 1.8f, zf + 0.05f, s * 3.2f, zf + 0.05f, 0.6f, 2.2f, 0.3f, 0.8f, 0f, 1f);
            }
        }
        // roof, parapet, air conditioners, water tank
        hquad(k, roof, x0, zb, x1, zf, h, 0.5f);
        float pt = 0.25f, ph = 0.7f;
        box(k, wall, x0, h, zf - pt, x1, h + ph, zf, 0.6f, 0.95f, true);
        box(k, wall, x0, h, zb, x1, h + ph, zb + pt, 0.6f, 0.95f, true);
        box(k, wall, x0, h, zb + pt, x0 + pt, h + ph, zf - pt, 0.6f, 0.95f, true);
        box(k, wall, x1 - pt, h, zb + pt, x1, h + ph, zf - pt, 0.6f, 0.95f, true);
        float ax = (rnd.nextBoolean() ? -1f : 1f) * w * 0.22f;
        box(k, roof, ax - 0.7f, h, -0.3f, ax + 0.7f, h + 0.75f, 0.6f, 0.4f, 0.85f, true);
        box(k, roof, -ax * 0.6f - 0.5f, h, -1.5f, -ax * 0.6f + 0.5f, h + 0.55f, -0.7f, 0.4f, 0.85f, true);
        if (variant != 1) {
            float tx = -ax, tz = -0.9f;
            for (int i = 0; i < 4; i++) {
                float lx = tx + ((i & 1) == 0 ? -0.55f : 0.55f), lz = tz + ((i & 2) == 0 ? -0.55f : 0.55f);
                box(k, roof, lx - 0.05f, h, lz - 0.05f, lx + 0.05f, h + 0.9f, lz + 0.05f, 0.3f, 0.5f, false);
            }
            k.tube(new float[][] {{tx, h + 0.9f, tz}, {tx, h + 2.2f, tz}}, new float[] {0.8f, 0.8f}, new float[] {0.55f, 0.75f}, 8, 1f,
                    roof, true);
        }
    }

    /** One window, with a lighter frame behind it when {@code frame}; one in {@code 1 / (1 / litShare)} is lit. */
    private static void window(MeshKit k, int wall, int win, int lit, float a0, float a1, float y0, float y1, float fixed, float hx,
                               float hz, boolean frame, float tl, Random rnd, float litShare) {
        // on the front (hz = 1) the quad runs along x at z = fixed; on a side (hx = +-1) along z at x = fixed
        boolean front = hz != 0f;
        boolean isLit = rnd.nextFloat() < litShare;
        int sw = isLit ? lit : win;
        float t0 = isLit ? 0.35f : tl, t1 = isLit ? 0.9f : tl + 0.4f;
        float off = frame ? 0.03f : 0f;
        if (frame) {
            float m = 0.09f;
            if (front) vquad(k, wall, a0 - m, fixed + off, a1 + m, fixed + off, y0 - m, y1 + m, 0.95f, 0.95f, 0f, 1f);
            else vquad(k, wall, fixed + off * hx, a0 - m, fixed + off * hx, a1 + m, y0 - m, y1 + m, 0.95f, 0.95f, hx, 0f);
        }
        float o = off + 0.03f;
        if (front) vquad(k, sw, a0, fixed + o, a1, fixed + o, y0, y1, t0, t1, 0f, 1f);
        else vquad(k, sw, fixed + o * hx, a0, fixed + o * hx, a1, y0, y1, t0, t1, hx, 0f);
    }

    /** A striped awning of 1 m stripes from x0 to x1, sloping down and out by {@code out} from a wall at z = zf. */
    private static void awning(MeshKit k, int sw, float x0, float x1, float zf, float out, float width) {
        int n = Math.max(1, Math.round((x1 - x0) / 1f));
        for (int i = 0; i < n; i++) {
            float a = x0 + (x1 - x0) * i / n, b = x0 + (x1 - x0) * (i + 1) / n;
            float t = (i & 1) == 0 ? 0.25f : 0.8f;
            k.flatQuad(f(a, 3.0f, zf + 0.05f), f(b, 3.0f, zf + 0.05f), f(b, 2.5f, zf + out), f(a, 2.5f, zf + out), t, t, t, t, sw, 0f, 1f, 0.6f);
            vquad(k, sw, a, zf + out, b, zf + out, 2.2f, 2.5f, t, t, 0f, 1f);
        }
    }

    // ---- towers ----

    /** Height of a tower variant at scale 1. */
    static float towerHeight(int variant) {
        return variant == 0 ? 24f : (variant == 1 ? 33f : 42f);
    }

    /** One tower: 2 or 3 setback tiers, vertical window strips on every face, a roof antenna. Ramps: 0 office, 1 windows, 2 roof. */
    static void tower(MeshKit k, int variant, int[] sw, Random rnd) {
        int wall = sw[0], win = sw[1], roof = sw[2];
        float h = towerHeight(variant);
        float[] widths = variant == 2 ? new float[] {8f, 6.4f, 4.8f} : new float[] {8f, 6f};
        float[] tops = variant == 2 ? new float[] {0.45f * h, 0.8f * h, h} : new float[] {0.6f * h, h};
        float base = 0f;
        for (int tier = 0; tier < widths.length; tier++) {
            float hw = widths[tier] / 2f, top = tops[tier];
            vquad(k, wall, -hw, hw, hw, hw, base, top, 0.3f, 0.9f, 0f, 1f);
            vquad(k, wall, hw, -hw, -hw, -hw, base, top, 0.3f, 0.9f, 0f, -1f);
            vquad(k, wall, hw, hw, hw, -hw, base, top, 0.3f, 0.9f, 1f, 0f);
            vquad(k, wall, -hw, -hw, -hw, hw, base, top, 0.3f, 0.9f, -1f, 0f);
            hquad(k, roof, -hw, -hw, hw, hw, top, 0.5f);
            int strips = Math.max(3, (int) Math.floor((widths[tier] - 0.6f) / 1.6f));
            float pitch = widths[tier] / strips;
            float y0 = base + (tier == 0 ? 3.4f : 0.8f), y1 = top - 0.7f;
            for (int s = 0; s < strips; s++) {
                float c = -hw + pitch * (s + 0.5f), a = c - 0.45f, b = c + 0.45f;
                float t0 = 0.2f + 0.12f * ((s + tier) & 1), t1 = t0 + 0.55f;
                vquad(k, win, a, hw + 0.05f, b, hw + 0.05f, y0, y1, t0, t1, 0f, 1f);
                vquad(k, win, b, -hw - 0.05f, a, -hw - 0.05f, y0, y1, t0, t1, 0f, -1f);
                vquad(k, win, hw + 0.05f, b, hw + 0.05f, a, y0, y1, t0, t1, 1f, 0f);
                vquad(k, win, -hw - 0.05f, a, -hw - 0.05f, b, y0, y1, t0, t1, -1f, 0f);
            }
            if (tier == 0) {   // a lobby band at the foot of the front
                vquad(k, win, -hw + 0.6f, hw + 0.05f, hw - 0.6f, hw + 0.05f, 0.3f, 2.8f, 0.3f, 0.7f, 0f, 1f);
            }
            base = top;
        }
        float top = tops[tops.length - 1];
        k.tube(new float[][] {{0f, top, 0f}, {0f, top + 3.5f, 0f}, {0f, top + 6f, 0f}}, new float[] {0.12f, 0.08f, 0.04f},
                new float[] {0.3f, 0.6f, 0.9f}, 4, 1f, roof, false);
        box(k, roof, -0.9f, top, -0.9f, 0.9f, top + 0.8f, 0.9f, 0.4f, 0.85f, true);
    }

    // ---- street furniture ----

    /** Lamp: tapered 8-sided post, a curved arm and a head with a lens; variant 1 has two arms (along x). Ramps: 0 post, 1 lens. */
    static void lamp(MeshKit k, int variant, int[] sw) {
        k.tube(new float[][] {{0f, 0f, 0f}, {0f, 2.8f, 0f}, {0f, 5.4f, 0f}}, new float[] {0.13f, 0.1f, 0.07f},
                new float[] {0.12f, 0.4f, 0.7f}, 8, 1f, sw[0], false);
        int arms = variant == 0 ? 1 : 2;
        for (int a = 0; a < arms; a++) {
            // the single arm points along +z (towards the road); the double ones along +x and -x
            float dx = variant == 0 ? 0f : (a == 0 ? 1f : -1f), dz = variant == 0 ? 1f : 0f;
            k.tube(new float[][] {{0f, 5.35f, 0f}, {dx * 0.4f, 5.75f, dz * 0.4f}, {dx * 0.8f, 5.7f, dz * 0.8f}},
                    new float[] {0.05f, 0.045f, 0.04f}, new float[] {0.8f, 0.8f, 0.8f}, 6, 1f, sw[0], false);
            float cx = dx * 0.82f, cz = dz * 0.82f;
            float hx = dx != 0f ? 0.2f : 0.15f, hz = dz != 0f ? 0.2f : 0.15f;
            box(k, sw[0], cx - hx, 5.58f, cz - hz, cx + hx, 5.74f, cz + hz, 0.7f, 0.9f, true);
            k.flatQuad(f(cx - hx + 0.03f, 5.575f, cz + hz - 0.03f), f(cx + hx - 0.03f, 5.575f, cz + hz - 0.03f),
                    f(cx + hx - 0.03f, 5.575f, cz - hz + 0.03f), f(cx - hx + 0.03f, 5.575f, cz - hz + 0.03f), 0.9f, 0.9f, 0.9f, 0.9f,
                    sw[1], 0f, -1f, 0f);
        }
    }

    /** Street tree: a square planter, a trunk and a crown of 2 or 3 smooth lobes. Ramps: 0 crown, 1 trunk, 2 planter. */
    static void streetTree(MeshKit k, int variant, int[] sw, Random rnd) {
        box(k, sw[2], -0.45f, 0f, -0.45f, 0.45f, 0.45f, 0.45f, 0.45f, 0.8f, false);
        hquad(k, sw[2], -0.45f, -0.45f, 0.45f, 0.45f, 0.45f, 0.1f);
        float trunk = variant == 0 ? 2.3f : 2.9f;
        k.tube(new float[][] {{0f, 0.4f, 0f}, {0f, 0.4f + (trunk - 0.4f) * 0.5f, 0f}, {0f, trunk, 0f}}, new float[] {0.11f, 0.09f, 0.07f},
                new float[] {0.15f, 0.4f, 0.7f}, 6, 1f, sw[1], false);
        float cy = trunk + 0.9f;
        blob(k, 0f, cy, 0f, 1.1f, 1.0f, 1.1f, 8, 6, sw[0], 0.15f, 0.95f, rnd);
        blob(k, 0.5f, cy - 0.35f, 0.25f, 0.8f, 0.75f, 0.8f, 8, 6, sw[0], 0.1f, 0.85f, rnd);
        if (variant == 1) blob(k, -0.45f, cy - 0.2f, -0.3f, 0.75f, 0.8f, 0.75f, 8, 6, sw[0], 0.1f, 0.9f, rnd);
    }

    private static final float[][] CAR_SECTIONS = {   // z, half-width scale, bottom, top
            {-2.14f, 0.02f, 0.4f, 0.5f}, {-2.1f, 0.5f, 0.32f, 0.62f}, {-1.9f, 0.9f, 0.28f, 0.84f}, {-1.0f, 1f, 0.28f, 0.86f},
            {0.2f, 1f, 0.28f, 0.86f}, {1.2f, 1f, 0.28f, 0.8f}, {1.9f, 0.92f, 0.28f, 0.7f}, {2.1f, 0.55f, 0.3f, 0.6f},
            {2.14f, 0.02f, 0.4f, 0.5f}};

    /** Parked car along z: a rounded body, a glass cabin, 4 wheels, lights. Ramps: 0 red, 1 blue, 2 white, 3 glass, 4 tyre. */
    static void car(MeshKit k, int variant, int[] sw) {
        int body = sw[variant], glass = sw[3], tyre = sw[4];
        int n = CAR_SECTIONS.length;
        float[][][] p = new float[n][8][3];
        float[][] t = new float[n][8];
        for (int i = 0; i < n; i++) {
            float z = CAR_SECTIONS[i][0], w = 0.92f * CAR_SECTIONS[i][1], yb = CAR_SECTIONS[i][2], yt = CAR_SECTIONS[i][3], hh = yt - yb;
            float[][] loop = {{0.7f * w, yb}, {w, yb + 0.2f * hh}, {w, yb + 0.65f * hh}, {0.75f * w, yt}, {-0.75f * w, yt},
                    {-w, yb + 0.65f * hh}, {-w, yb + 0.2f * hh}, {-0.7f * w, yb}};
            float[] tt = {0.1f, 0.35f, 0.65f, 0.95f, 0.95f, 0.65f, 0.35f, 0.1f};
            for (int s = 0; s < 8; s++) {
                p[i][s][0] = loop[7 - s][0];
                p[i][s][1] = loop[7 - s][1];
                p[i][s][2] = z;
                t[i][s] = tt[7 - s];
            }
        }
        k.grid(p, t, body, true);
        // cabin
        float hb = 0.78f, ht = 0.68f, yb = 0.84f, yt = 1.38f;
        hquad(k, body, -ht, -0.8f, ht, 0.3f, yt, 0.9f);
        k.flatQuad(f(-hb, yb, 1.0f), f(hb, yb, 1.0f), f(ht, yt, 0.3f), f(-ht, yt, 0.3f), 0.35f, 0.35f, 0.8f, 0.8f, glass, 0f, 0.6f, 1f);
        k.flatQuad(f(hb, yb, -1.2f), f(-hb, yb, -1.2f), f(-ht, yt, -0.8f), f(ht, yt, -0.8f), 0.3f, 0.3f, 0.6f, 0.6f, glass, 0f, 0.6f, -1f);
        k.flatQuad(f(hb, yb, 1.0f), f(hb, yb, -1.2f), f(ht, yt, -0.8f), f(ht, yt, 0.3f), 0.3f, 0.3f, 0.7f, 0.7f, glass, 1f, 0.3f, 0f);
        k.flatQuad(f(-hb, yb, -1.2f), f(-hb, yb, 1.0f), f(-ht, yt, 0.3f), f(-ht, yt, -0.8f), 0.3f, 0.3f, 0.7f, 0.7f, glass, -1f, 0.3f, 0f);
        // wheels: 8-sided, axis along x, the lighter hub on the outside
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                wheel(k, tyre, sx * 0.88f, 0.32f, sz * 1.35f, sx);
            }
        }
        // head and tail lights
        for (int s = -1; s <= 1; s += 2) {
            box(k, sw[2], s * 0.55f - 0.15f, 0.5f, 1.95f, s * 0.55f + 0.15f, 0.66f, 2.03f, 0.95f, 0.98f, true);
            box(k, sw[0], s * 0.55f - 0.15f, 0.5f, -2.03f, s * 0.55f + 0.15f, 0.64f, -1.95f, 0.9f, 0.98f, true);
        }
    }

    private static void wheel(MeshKit k, int sw, float cx, float cy, float cz, int sx) {
        float r = 0.32f, half = 0.11f;
        float xo = cx + sx * half, xi = cx - sx * half;
        float[] c = f(xo, cy, cz);
        for (int i = 0; i < 8; i++) {
            double a0 = 2.0 * Math.PI * i / 8, a1 = 2.0 * Math.PI * (i + 1) / 8;
            float y0 = cy + r * (float) Math.sin(a0), z0 = cz + r * (float) Math.cos(a0);
            float y1 = cy + r * (float) Math.sin(a1), z1 = cz + r * (float) Math.cos(a1);
            float ym = (y0 + y1) / 2f - cy, zm = (z0 + z1) / 2f - cz;
            k.flatQuad(f(xi, y0, z0), f(xo, y0, z0), f(xo, y1, z1), f(xi, y1, z1), 0.3f, 0.3f, 0.5f, 0.5f, sw, 0f, ym, zm);
            k.flatTri(c, f(xo, y0, z0), f(xo, y1, z1), 0.9f, 0.7f, 0.7f, sw, sx, 0f, 0f);
        }
    }

    /** Bus stop: a glass shelter with a roof, a bench and a sign. Variant 1 is longer with a second bench. Ramps: 0 frame, 1 glass. */
    static void busStop(MeshKit k, int variant, int[] sw) {
        float hl = variant == 0 ? 1.5f : 2.1f, z0 = -0.55f, z1 = 0.55f, h = 2.4f;
        int fr = sw[0], gl = sw[1];
        box(k, fr, -hl - 0.1f, h - 0.1f, z0 - 0.1f, hl + 0.1f, h, z1 + 0.1f, 0.6f, 0.9f, true);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) box(k, fr, sx * hl - 0.04f, 0f, sz * z1 - 0.04f, sx * hl + 0.04f, h - 0.1f, sz * z1 + 0.04f, 0.2f, 0.6f, false);
        }
        // glass back and sides, seen from both sides
        k.flatQuad(f(-hl, 0.15f, z0), f(hl, 0.15f, z0), f(hl, h - 0.1f, z0), f(-hl, h - 0.1f, z0), 0.3f, 0.3f, 0.8f, 0.8f, gl, 0f, 0f, 1f);
        k.flatQuad(f(hl, 0.15f, z0 - 0.02f), f(-hl, 0.15f, z0 - 0.02f), f(-hl, h - 0.1f, z0 - 0.02f), f(hl, h - 0.1f, z0 - 0.02f), 0.3f, 0.3f, 0.8f, 0.8f, gl, 0f, 0f, -1f);
        for (int s = -1; s <= 1; s += 2) {
            k.flatQuad(f(s * hl, 0.15f, z0), f(s * hl, 0.15f, z1), f(s * hl, h - 0.1f, z1), f(s * hl, h - 0.1f, z0), 0.3f, 0.3f, 0.8f, 0.8f, gl, s, 0f, 0f);
            k.flatQuad(f(s * hl, 0.15f, z0), f(s * hl, 0.15f, z1), f(s * hl, h - 0.1f, z1), f(s * hl, h - 0.1f, z0), 0.3f, 0.3f, 0.8f, 0.8f, gl, -s, 0f, 0f);
        }
        int benches = variant == 0 ? 1 : 2;
        for (int b = 0; b < benches; b++) {
            float bx = benches == 1 ? 0f : (b == 0 ? -0.9f : 0.9f);
            box(k, fr, bx - 0.7f, 0.42f, -0.5f, bx + 0.7f, 0.48f, -0.1f, 0.5f, 0.8f, true);
            box(k, fr, bx - 0.6f, 0f, -0.45f, bx - 0.5f, 0.42f, -0.15f, 0.2f, 0.5f, false);
            box(k, fr, bx + 0.5f, 0f, -0.45f, bx + 0.6f, 0.42f, -0.15f, 0.2f, 0.5f, false);
        }
        float sx = hl + 0.45f;
        box(k, fr, sx - 0.03f, 0f, 0.3f, sx + 0.03f, 2.7f, 0.36f, 0.2f, 0.6f, false);
        box(k, fr, sx - 0.3f, 2.2f, 0.3f, sx + 0.3f, 2.7f, 0.36f, 0.7f, 0.95f, true);
        if (variant == 1) box(k, fr, -hl + 0.2f, 0.4f, z0 + 0.03f, -hl + 1.2f, 2.0f, z0 + 0.08f, 0.55f, 0.8f, true);
    }

    /** Bench: slatted seat, iron legs; variant 1 has no back. Ramps: 0 wood, 1 iron. */
    static void bench(MeshKit k, int variant, int[] sw) {
        for (int i = 0; i < 3; i++) box(k, sw[0], -0.9f, 0.42f, -0.2f + 0.13f * i, 0.9f, 0.46f, -0.1f + 0.13f * i, 0.5f, 0.85f, true);
        if (variant == 0) {
            box(k, sw[0], -0.9f, 0.6f, -0.26f, 0.9f, 0.7f, -0.22f, 0.5f, 0.85f, true);
            box(k, sw[0], -0.9f, 0.76f, -0.26f, 0.9f, 0.86f, -0.22f, 0.5f, 0.85f, true);
        }
        for (int s = -1; s <= 1; s += 2) box(k, sw[1], s * 0.8f - 0.03f, 0f, -0.28f, s * 0.8f + 0.03f, 0.42f, 0.22f, 0.2f, 0.6f, false);
    }

    /** Hydrant. Ramp 0. */
    static void hydrant(MeshKit k, int[] sw) {
        k.tube(new float[][] {{0f, 0f, 0f}, {0f, 0.45f, 0f}, {0f, 0.7f, 0f}}, new float[] {0.17f, 0.15f, 0.12f},
                new float[] {0.15f, 0.5f, 0.8f}, 6, 1f, sw[0], true);
        box(k, sw[0], 0.12f, 0.35f, -0.07f, 0.3f, 0.5f, 0.07f, 0.5f, 0.8f, true);
        box(k, sw[0], -0.3f, 0.35f, -0.07f, -0.12f, 0.5f, 0.07f, 0.5f, 0.8f, true);
    }

    /** Bin: round (variant 0) or square with a lid. Ramp 0. */
    static void bin(MeshKit k, int variant, int[] sw) {
        if (variant == 0) {
            k.tube(new float[][] {{0f, 0f, 0f}, {0f, 0.9f, 0f}}, new float[] {0.27f, 0.3f}, new float[] {0.2f, 0.7f}, 8, 1f, sw[0], true);
        } else {
            box(k, sw[0], -0.25f, 0f, -0.25f, 0.25f, 0.9f, 0.25f, 0.2f, 0.7f, false);
            box(k, sw[0], -0.29f, 0.9f, -0.29f, 0.29f, 0.98f, 0.29f, 0.8f, 0.95f, true);
        }
    }

    /** Sign on a post: 0 stop (octagon), 1 arrow (blue square), 2 speed (round). Ramps: 0 post, 1 red, 2 blue. */
    static void sign(MeshKit k, int variant, int[] sw) {
        box(k, sw[0], -0.025f, 0f, -0.025f, 0.025f, 2.2f, 0.025f, 0.3f, 0.7f, false);
        float cy = 2.0f, z = 0.04f;
        if (variant == 0) {
            disc(k, sw[1], 0f, cy, z, 0.34f, 8, 0.5f, 1f);
            disc(k, sw[0], 0f, cy, -z, 0.34f, 8, 0.6f, -1f);
        } else if (variant == 1) {
            vquad(k, sw[2], -0.3f, z, 0.3f, z, cy - 0.3f, cy + 0.3f, 0.4f, 0.7f, 0f, 1f);
            vquad(k, sw[0], 0.3f, -z, -0.3f, -z, cy - 0.3f, cy + 0.3f, 0.6f, 0.6f, 0f, -1f);
            k.flatTri(f(-0.18f, cy, z + 0.01f), f(0.02f, cy + 0.18f, z + 0.01f), f(0.02f, cy - 0.18f, z + 0.01f), 1f, 1f, 1f, sw[0], 0f, 0f, 1f);
            vquad(k, sw[0], 0.02f, z + 0.01f, 0.2f, z + 0.01f, cy - 0.06f, cy + 0.06f, 1f, 1f, 0f, 1f);
        } else {
            disc(k, sw[1], 0f, cy, z, 0.32f, 12, 0.5f, 1f);
            disc(k, sw[0], 0f, cy, z + 0.01f, 0.23f, 12, 1f, 1f);
            disc(k, sw[0], 0f, cy, -z, 0.32f, 12, 0.6f, -1f);
        }
    }

    /** Traffic cone, or a pair. Ramps: 0 cone, 1 white band. */
    static void cone(MeshKit k, int variant, int[] sw) {
        int cones = variant == 0 ? 1 : 2;
        int sides = variant == 0 ? 8 : 5;
        for (int i = 0; i < cones; i++) {
            float cx = cones == 1 ? 0f : (i == 0 ? -0.3f : 0.3f), cz = cones == 1 ? 0f : (i == 0 ? -0.1f : 0.15f);
            k.tube(new float[][] {{cx, 0f, cz}, {cx, 0.6f, cz}}, new float[] {0.2f, 0.05f}, new float[] {0.2f, 0.85f}, sides, 1f, sw[0], false);
            // the band sits on the cone's surface (radius 0.2 - 0.15 * y / 0.6, plus a little)
            float y0 = 0.22f, y1 = 0.38f;
            float r0 = 0.2f - 0.25f * y0 + 0.012f, r1 = 0.2f - 0.25f * y1 + 0.012f;
            k.tube(new float[][] {{cx, y0, cz}, {cx, y1, cz}}, new float[] {r0, r1}, new float[] {0.5f, 0.6f}, sides, 1f, sw[1], false);
        }
    }

    /** Flower planter: a box with clumps of flowers. Ramps: 0 box, 1 flowers. */
    static void planter(MeshKit k, int[] sw, Random rnd) {
        box(k, sw[0], -0.7f, 0f, -0.25f, 0.7f, 0.45f, 0.25f, 0.45f, 0.8f, false);
        hquad(k, sw[0], -0.7f, -0.25f, 0.7f, 0.25f, 0.45f, 0.08f);
        for (int i = 0; i < 8; i++) {
            float cx = -0.55f + 1.1f * i / 7f + 0.05f * (rnd.nextFloat() - 0.5f), cz = (i & 1) == 0 ? -0.1f : 0.1f;
            float r = 0.16f + 0.08f * rnd.nextFloat();
            blob(k, cx, 0.5f + r * 0.5f, cz, r, r * 0.8f, r, 5, 3, sw[1], 0.3f, 0.95f, rnd);
        }
    }
}
