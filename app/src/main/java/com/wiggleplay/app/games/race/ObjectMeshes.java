package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

/**
 * The static meshes of the road objects (T-31), in world space like the lake water: the ramp, the chevrons painted on the road before
 * it, and the warning signs. Every triangle uses one swatch block (the flat swatches 10 to 15 that {@link Theme#fillPalette} writes for
 * every theme). Plain Java.
 */
public final class ObjectMeshes {

    /** Swatches: ramp top and sign board yellow, dark chevrons and arrows, ramp side, cream road paint, sign post grey, orange board. */
    public static final int SW_YELLOW = 10, SW_DARK = 11, SW_SIDE = 12, SW_CREAM = 13, SW_POST = 14, SW_ORANGE = 15;
    /**
     * T-31 Part B: the hazard swatches, an appended block at the top of the palette (above every theme's own colours, which must stay
     * below {@link #SW_FIRST_HAZARD}): pothole floor and rim, ice, water, white streaks and glyphs, water edge, blue sign board.
     */
    public static final int SW_FIRST_HAZARD = 216, SW_PIT_FLOOR = 216, SW_PIT_RIM = 217, SW_ICE = 218, SW_WATER = 219, SW_WHITE = 220,
            SW_WATER_EDGE = 221, SW_BLUE = 222;
    /** Colours of the hazard swatches. */
    public static final int PIT_FLOOR = 0x3A3633, PIT_RIM = 0x6E6A66, ICE = 0xD6F0FA, WATER = 0x6CC3E6, WHITE = 0xFFFFFF,
            WATER_EDGE = 0xA8DDF2, BLUE = 0x5B8DB8;
    /** Colours of those swatches. */
    public static final int YELLOW = 0xF2C572, DARK = 0x2F3A45, SIDE = 0x8C7E72, CREAM = 0xF3EBDD, POST = 0x7C858E, ORANGE = 0xE89A4C;

    /** Triangle limits of the ticket. */
    public static final int MAX_RAMP = 30, MAX_SIGN = 30, MAX_MARKS = 30, MAX_POTHOLE = 24, MAX_PATCH = 24;
    /** Road paint height above the road. */
    public static final float PAINT_Y = 0.006f;
    /** Distance of a sign from the road centre beyond the half-width (outside the barrier), and the sign's sizes. */
    public static final float SIGN_OUT = 1.6f, POST_HEIGHT = 1.6f, BOARD = 1.2f;

    private ObjectMeshes() { }

    /** Writes the object swatches into a palette (called for every theme). */
    public static void paint(PaletteSwatches p) {
        p.set(SW_YELLOW, YELLOW);
        p.set(SW_DARK, DARK);
        p.set(SW_SIDE, SIDE);
        p.set(SW_CREAM, CREAM);
        p.set(SW_POST, POST);
        p.set(SW_ORANGE, ORANGE);
        p.set(SW_PIT_FLOOR, PIT_FLOOR);
        p.set(SW_PIT_RIM, PIT_RIM);
        p.set(SW_ICE, ICE);
        p.set(SW_WATER, WATER);
        p.set(SW_WHITE, WHITE);
        p.set(SW_WATER_EDGE, WATER_EDGE);
        p.set(SW_BLUE, BLUE);
    }

    /** The road frame at s: x, z, forward x, forward z, right x, right z (right = toward positive offset). */
    private static float[] frame(TrackPath t, float s) {
        float[] p = new float[3];
        t.sample(s, p);
        float fx = (float) Math.sin(p[2]), fz = (float) Math.cos(p[2]);
        return new float[] {p[0], p[1], fx, fz, -(float) Math.cos(p[2]), (float) Math.sin(p[2])};
    }

    private static float[] pt(float[] f, float along, float lateral, float y) {
        return new float[] {f[0] + f[2] * along + f[4] * lateral, y, f[1] + f[3] * along + f[5] * lateral};
    }

    /**
     * The ramp of object {@code i}: a yellow top with three dark chevrons, a back face and two side triangles. It is a straight wedge
     * on the road direction at its middle (the road has at least a 30 m radius there). 4 m wide, 5 m long, 0.8 m high at the lip.
     * @param ro the objects
     * @param i a ramp
     * @param t the track
     * @param pal the palette
     * @return the mesh (one part "ramp"), at most {@link #MAX_RAMP} triangles
     */
    public static MeshData ramp(RoadObjects ro, int i, TrackPath t, PaletteSwatches pal) {
        float lip = ro.s(i), lat = ro.lateral(i), hw = RoadObjects.RAMP_WIDTH / 2f, h = RoadObjects.RAMP_HEIGHT;
        float len = RoadObjects.RAMP_LENGTH;
        float[] f = frame(t, lip - len / 2f);
        float y0 = 0.004f;
        float[] a = pt(f, -len / 2f, lat - hw, y0), b = pt(f, -len / 2f, lat + hw, y0);
        float[] c = pt(f, len / 2f, lat + hw, h), d = pt(f, len / 2f, lat - hw, h);
        float[] c0 = pt(f, len / 2f, lat + hw, y0), d0 = pt(f, len / 2f, lat - hw, y0);
        MeshKit k = new MeshKit(pal);
        float up = 1f;
        // top (normal up and slightly back), back face (normal forward), sides
        k.flatTri(copy(a), copy(b), copy(c), 0f, 0f, 0f, SW_YELLOW, 0f, up, 0f);
        k.flatTri(copy(a), copy(c), copy(d), 0f, 0f, 0f, SW_YELLOW, 0f, up, 0f);
        k.flatTri(copy(d), copy(c), copy(c0), 0f, 0f, 0f, SW_SIDE, f[2], 0f, f[3]);
        k.flatTri(copy(d), copy(c0), copy(d0), 0f, 0f, 0f, SW_SIDE, f[2], 0f, f[3]);
        k.flatTri(copy(a), copy(d0), copy(d), 0f, 0f, 0f, SW_SIDE, -f[4], 0f, -f[5]);
        k.flatTri(copy(b), copy(c), copy(c0), 0f, 0f, 0f, SW_SIDE, f[4], 0f, f[5]);
        // three dark chevrons pointing forward, lifted 1.2 cm off the slope
        float[] us = {0.25f, 0.5f, 0.75f};
        for (float u : us) {
            for (int side = -1; side <= 1; side += 2) {
                float[] p1 = onTop(f, lat, u - 0.14f, side * 1.3f, len, h), p2 = onTop(f, lat, u - 0.07f, side * 1.3f, len, h);
                float[] p3 = onTop(f, lat, u, 0f, len, h), p4 = onTop(f, lat, u - 0.07f, 0f, len, h);
                k.flatTri(p1, p2, p3, 0f, 0f, 0f, SW_DARK, 0f, up, 0f);
                k.flatTri(copy(p1), copy(p3), copy(p4), 0f, 0f, 0f, SW_DARK, 0f, up, 0f);
            }
        }
        return k.mesh("ramp");
    }

    /** A point on the ramp's top at u (0 foot to 1 lip) and lateral w from the ramp's middle, 1.2 cm above it. */
    private static float[] onTop(float[] f, float lat, float u, float w, float len, float h) {
        return pt(f, (u - 0.5f) * len, lat + w, u * h + 0.012f);
    }

    private static float[] copy(float[] a) {
        return new float[] {a[0], a[1], a[2]};
    }

    /**
     * The three cream chevrons painted on the road before a ramp, at 12, 9 and 6 m before the lip, in the ramp's lane.
     * @param ro the objects
     * @param i a ramp
     * @param t the track
     * @param pal the palette
     * @return the mesh (one part "marks"), 12 triangles
     */
    public static MeshData rampMarks(RoadObjects ro, int i, TrackPath t, PaletteSwatches pal) {
        MeshKit k = new MeshKit(pal);
        float lat = ro.lateral(i);
        for (float before : new float[] {12f, 9f, 6f}) {
            float[] f = frame(t, ro.s(i) - before);
            for (int side = -1; side <= 1; side += 2) {
                float[] p1 = pt(f, -0.45f, lat + side * 1.2f, PAINT_Y), p2 = pt(f, 0.05f, lat + side * 1.2f, PAINT_Y);
                float[] p3 = pt(f, 0.45f, lat, PAINT_Y), p4 = pt(f, -0.05f, lat, PAINT_Y);
                k.flatTri(p1, p2, p3, 0f, 0f, 0f, SW_CREAM, 0f, 1f, 0f);
                k.flatTri(copy(p1), copy(p3), copy(p4), 0f, 0f, 0f, SW_CREAM, 0f, 1f, 0f);
            }
        }
        return k.mesh("marks");
    }

    /**
     * Where the warning sign of object {@code i} stands: 20 m before the object (a ramp: before its lip), outside the barrier on the
     * side of the object's lane (the right for the centre lane).
     * @param ro the objects
     * @param i an object
     * @param t the track
     * @param out receives x, z, forward x, forward z (the sign faces back along -forward)
     */
    public static void signSpot(RoadObjects ro, int i, TrackPath t, float[] out) {
        float s = ro.s(i) - RoadObjects.SIGN_BEFORE;
        float[] f = frame(t, s);
        float side = ro.lane(i) < 0 ? -1f : 1f;
        float lateral = side * (t.roadHalfWidth() + SIGN_OUT);
        out[0] = f[0] + f[4] * lateral;
        out[1] = f[1] + f[5] * lateral;
        out[2] = f[2];
        out[3] = f[3];
    }

    /**
     * The warning sign of object {@code i}, for a theme with ice (see {@link #sign(RoadObjects, int, TrackPath, PaletteSwatches, int)}).
     * @param ro the objects
     * @param i an object
     * @param t the track
     * @param pal the palette
     * @return the mesh
     */
    public static MeshData sign(RoadObjects ro, int i, TrackPath t, PaletteSwatches pal) {
        return sign(ro, i, t, pal, Theme.SLICK_ICE);
    }

    /**
     * The warning sign of object {@code i}: a grey post 1.6 m high and a 1.2 m diamond board facing the oncoming truck. A ramp has a
     * yellow board with a dark up-arrow, a pothole an orange board with a dark dot, a patch a blue board with a white snowflake (ice)
     * or drop (water).
     * @param ro the objects
     * @param i an object
     * @param t the track
     * @param pal the palette
     * @param slick the theme's {@link Theme#slick}, which picks the glyph of a patch's sign
     * @return the mesh (one part "sign"), at most {@link #MAX_SIGN} triangles
     */
    public static MeshData sign(RoadObjects ro, int i, TrackPath t, PaletteSwatches pal, int slick) {
        float[] spot = new float[4];
        signSpot(ro, i, t, spot);
        float fx = spot[2], fz = spot[3], rx = -fz, rz = fx;   // right of the road direction
        MeshKit k = new MeshKit(pal);
        float px = spot[0], pz = spot[1], r = 0.06f;
        float baseY = -0.1f;
        // post: four sides
        float[][] c = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        for (int q = 0; q < 4; q++) {
            float[] a = c[q], b = c[(q + 1) % 4];
            float[] a0 = {px + rx * a[0] + fx * a[1], baseY, pz + rz * a[0] + fz * a[1]};
            float[] b0 = {px + rx * b[0] + fx * b[1], baseY, pz + rz * b[0] + fz * b[1]};
            float[] a1 = {a0[0], POST_HEIGHT + 0.3f, a0[2]}, b1 = {b0[0], POST_HEIGHT + 0.3f, b0[2]};
            float mx = (a[0] + b[0]) / 2f, my = (a[1] + b[1]) / 2f;
            float hx = rx * mx + fx * my, hz = rz * mx + fz * my;
            k.flatTri(a0, b0, b1, 0f, 0f, 0f, SW_POST, hx, 0f, hz);
            k.flatTri(copy(a0), copy(b1), copy(a1), 0f, 0f, 0f, SW_POST, hx, 0f, hz);
        }
        // board: a diamond (a square turned 45 degrees) facing back along -forward, a little in front of the post
        float cy = POST_HEIGHT + 0.3f, half = BOARD * 0.7071f;
        float bx = px - fx * 0.08f, bz = pz - fz * 0.08f;
        int kind = ro.kind(i);
        int boardSw = kind == RoadObjects.RAMP ? SW_YELLOW : kind == RoadObjects.POTHOLE ? SW_ORANGE : SW_BLUE;
        float[] top = {bx, cy + half, bz}, bot = {bx, cy - half, bz};
        float[] lf = {bx - rx * half, cy, bz - rz * half}, rt = {bx + rx * half, cy, bz + rz * half};
        k.flatTri(copy(lf), copy(bot), copy(rt), 0f, 0f, 0f, boardSw, -fx, 0f, -fz);
        k.flatTri(copy(lf), copy(rt), copy(top), 0f, 0f, 0f, boardSw, -fx, 0f, -fz);
        // the glyph, 1 cm in front of the board; u to the right of the road, v up
        float[] o = {bx - fx * 0.012f, cy, bz - fz * 0.012f};
        if (kind == RoadObjects.RAMP) {
            glyphTri(k, o, rx, rz, fx, fz, 0f, 0.38f, -0.26f, 0.02f, 0.26f, 0.02f, SW_DARK);
            glyphQuad(k, o, rx, rz, fx, fz, -0.09f, 0.02f, 0.09f, -0.32f, SW_DARK);
        } else if (kind == RoadObjects.POTHOLE) {
            glyphQuad(k, o, rx, rz, fx, fz, -0.2f, 0.2f, 0.2f, -0.2f, SW_DARK);
            glyphTri(k, o, rx, rz, fx, fz, -0.2f, 0.2f, 0.2f, 0.2f, 0f, 0.4f, SW_DARK);
        } else if (slick == Theme.SLICK_WATER) {
            // a drop: a triangle pointing up over a diamond
            glyphTri(k, o, rx, rz, fx, fz, 0f, 0.4f, -0.22f, 0f, 0.22f, 0f, SW_WHITE);
            glyphTri(k, o, rx, rz, fx, fz, -0.22f, 0f, 0.22f, 0f, 0f, -0.3f, SW_WHITE);
        } else {
            // a snowflake: three bars through the middle
            for (int b = 0; b < 3; b++) {
                double a = b * Math.PI / 3.0;
                float ux = (float) Math.sin(a) * 0.36f, vy = (float) Math.cos(a) * 0.36f, wx = (float) Math.cos(a) * 0.05f, wy = -(float) Math.sin(a) * 0.05f;
                float[] p1 = glyphPoint(o, rx, rz, fx, fz, -ux - wx, -vy - wy), p2 = glyphPoint(o, rx, rz, fx, fz, ux - wx, vy - wy);
                float[] p3 = glyphPoint(o, rx, rz, fx, fz, ux + wx, vy + wy), p4 = glyphPoint(o, rx, rz, fx, fz, -ux + wx, -vy + wy);
                k.flatTri(p1, p2, p3, 0f, 0f, 0f, SW_WHITE, -fx, 0f, -fz);
                k.flatTri(copy(p1), copy(p3), p4, 0f, 0f, 0f, SW_WHITE, -fx, 0f, -fz);
            }
        }
        return k.mesh("sign");
    }

    private static float[] glyphPoint(float[] o, float rx, float rz, float fx, float fz, float u, float v) {
        return new float[] {o[0] + rx * u, o[1] + v, o[2] + rz * u};
    }

    private static void glyphTri(MeshKit k, float[] o, float rx, float rz, float fx, float fz, float u1, float v1, float u2, float v2,
                                 float u3, float v3, int sw) {
        k.flatTri(glyphPoint(o, rx, rz, fx, fz, u1, v1), glyphPoint(o, rx, rz, fx, fz, u2, v2), glyphPoint(o, rx, rz, fx, fz, u3, v3),
                0f, 0f, 0f, sw, -fx, 0f, -fz);
    }

    private static void glyphQuad(MeshKit k, float[] o, float rx, float rz, float fx, float fz, float u0, float v0, float u1, float v1, int sw) {
        glyphTri(k, o, rx, rz, fx, fz, u0, v0, u1, v0, u1, v1, sw);
        glyphTri(k, o, rx, rz, fx, fz, u0, v0, u1, v1, u0, v1, sw);
    }

    /**
     * The potholes of group {@code i}: each a dark floor (an octagon, 0.9 m radius) inside a lighter rim ring 0.15 m wide, flat on the
     * road at y = 0.006.
     * @param ro the objects
     * @param i a pothole group
     * @param t the track
     * @param pal the palette
     * @return the mesh (one part "pothole"), 24 triangles per pothole
     */
    public static MeshData pothole(RoadObjects ro, int i, TrackPath t, PaletteSwatches pal) {
        MeshKit k = new MeshKit(pal);
        float lat = ro.lateral(i);
        int n = 8;
        float rIn = RoadObjects.POTHOLE_RADIUS - 0.15f, rOut = RoadObjects.POTHOLE_RADIUS;
        for (int j = 0; j < ro.potholes(i); j++) {
            float[] f = frame(t, ro.potholeS(i, j));
            for (int q = 0; q < n; q++) {
                double a0 = 2.0 * Math.PI * q / n, a1 = 2.0 * Math.PI * (q + 1) / n;
                float[] c0 = pt(f, 0f, lat, PAINT_Y);
                float[] i0 = pt(f, (float) Math.cos(a0) * rIn, lat + (float) Math.sin(a0) * rIn, PAINT_Y);
                float[] i1 = pt(f, (float) Math.cos(a1) * rIn, lat + (float) Math.sin(a1) * rIn, PAINT_Y);
                float[] o0 = pt(f, (float) Math.cos(a0) * rOut, lat + (float) Math.sin(a0) * rOut, PAINT_Y);
                float[] o1 = pt(f, (float) Math.cos(a1) * rOut, lat + (float) Math.sin(a1) * rOut, PAINT_Y);
                k.flatTri(c0, i0, i1, 0f, 0f, 0f, SW_PIT_FLOOR, 0f, 1f, 0f);
                k.flatTri(copy(i0), copy(o0), copy(o1), 0f, 0f, 0f, SW_PIT_RIM, 0f, 1f, 0f);
                k.flatTri(copy(i0), copy(o1), copy(i1), 0f, 0f, 0f, SW_PIT_RIM, 0f, 1f, 0f);
            }
        }
        return k.mesh("pothole");
    }

    /**
     * The ice or water patch {@code i}: 8 m long and 4 m wide in four 2 m sections that follow the road. Ice: pale blue with three white
     * streaks; water: blue with a lighter edge along both long sides. Flat at y = 0.006.
     * @param ro the objects
     * @param i a patch
     * @param t the track
     * @param pal the palette
     * @param slick {@link Theme#SLICK_ICE} or {@link Theme#SLICK_WATER}
     * @return the mesh (one part "slick"), at most {@link #MAX_PATCH} triangles
     */
    public static MeshData patch(RoadObjects ro, int i, TrackPath t, PaletteSwatches pal, int slick) {
        MeshKit k = new MeshKit(pal);
        float lat = ro.lateral(i), h = RoadObjects.PATCH_HALF;
        boolean water = slick == Theme.SLICK_WATER;
        float[][] fr = new float[5][];
        for (int q = 0; q <= 4; q++) fr[q] = frame(t, ro.s(i) + q * 2f);
        int base = water ? SW_WATER : SW_ICE;
        for (int q = 0; q < 4; q++) strip(k, fr[q], fr[q + 1], lat - h, lat + h, PAINT_Y, base);
        if (water) {
            for (int q = 0; q < 4; q++) {
                strip(k, fr[q], fr[q + 1], lat - h, lat - h + 0.3f, PAINT_Y + 0.002f, SW_WATER_EDGE);
                strip(k, fr[q], fr[q + 1], lat + h - 0.3f, lat + h, PAINT_Y + 0.002f, SW_WATER_EDGE);
            }
        } else {
            // three streaks, each over the middle two sections
            float[] ws = {-1.1f, 0f, 1.1f};
            for (float w : ws) {
                for (int q = 1; q <= 2; q++) strip(k, fr[q], fr[q + 1], lat + w - 0.06f, lat + w + 0.06f, PAINT_Y + 0.002f, SW_WHITE);
            }
        }
        return k.mesh("slick");
    }

    private static void strip(MeshKit k, float[] f0, float[] f1, float l0, float l1, float y, int sw) {
        float[] a = pt(f0, 0f, l0, y), b = pt(f0, 0f, l1, y), c = pt(f1, 0f, l1, y), d = pt(f1, 0f, l0, y);
        k.flatTri(a, b, c, 0f, 0f, 0f, sw, 0f, 1f, 0f);
        k.flatTri(copy(a), copy(c), d, 0f, 0f, 0f, sw, 0f, 1f, 0f);
    }
}
