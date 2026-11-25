package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.Arrays;
import java.util.Random;

/**
 * Low-poly scenery props built in code (T-29 Part B): a few flat-shaded triangles each, every colour a swatch of the colormap
 * ({@link PaletteSwatches}), base at y = 0, centred on the origin, so the scene can merge many of them into the static chunks.
 * Plain Java. Each face has its own normal (flat shading); nothing is drawn from below, so a prop has no bottom.
 *
 * <p>Colours: {@code sw} holds one swatch per colour of the rule: SAGUARO body and ribs, BUTTE sides and top, BARREL_CACTI
 * barrel and flower, ROCKS two greys, JOSHUA_TREE trunk and leaves.
 */
public final class PropMeshes {

    /** Prop kinds. */
    public static final int SAGUARO = 0, BUTTE = 1, BARREL_CACTI = 2, ROCKS = 3, JOSHUA_TREE = 4;
    /** Number of kinds. */
    public static final int COUNT = 5;
    /** Triangle limits of the ticket, per kind. */
    public static final int[] MAX_TRIANGLES = {80, 120, 60, 72, 48};

    private static final float[] REACH = new float[COUNT];

    private PropMeshes() { }

    /**
     * How many shapes a kind has (the scatter picks one by chance).
     * @param kind a kind
     * @return 2 for SAGUARO (one arm or two), 1 for the others
     */
    public static int variants(int kind) {
        return kind == SAGUARO ? 2 : 1;
    }

    /**
     * The name a prop mesh is registered under.
     * @param kind a kind
     * @param variant 0 to {@code variants(kind) - 1}
     * @return for example {@code prop_0_1}
     */
    public static String name(int kind, int variant) {
        return "prop_" + kind + "_" + variant;
    }

    /**
     * The farthest a prop reaches from its origin sideways, at scale 1.
     * @param kind a kind
     * @return metres
     */
    public static synchronized float reach(int kind) {
        if (REACH[kind] == 0f) {
            PaletteSwatches pal = new PaletteSwatches();
            int[] sw = {0, 1};
            float r = 0f;
            for (int v = 0; v < variants(kind); v++) {
                MeshData.Part p = build(kind, v, pal, sw).parts[0];
                for (int i = 0; i < p.vertexCount; i++) {
                    r = Math.max(r, (float) Math.hypot(p.vertices[i * 8], p.vertices[i * 8 + 2]));
                }
            }
            REACH[kind] = r;
        }
        return REACH[kind];
    }

    /**
     * Builds one prop.
     * @param kind a kind
     * @param variant 0 to {@code variants(kind) - 1}
     * @param pal the palette (only its swatch uvs are used)
     * @param sw the swatches of the rule's colours
     * @return the mesh, one part named "prop", single sided, local space
     */
    public static MeshData build(int kind, int variant, PaletteSwatches pal, int[] sw) {
        Builder b = new Builder(pal);
        switch (kind) {
            case SAGUARO:
                saguaro(b, variant, sw[0], sw[1]);
                break;
            case BUTTE:
                butte(b, sw[0], sw[1]);
                break;
            case BARREL_CACTI:
                barrels(b, sw[0], sw[1]);
                break;
            case ROCKS:
                rocks(b, sw[0], sw[1]);
                break;
            default:
                joshua(b, sw[0], sw[1]);
                break;
        }
        MeshData.Part part = new MeshData.Part("prop", -1, new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f, 1f},
                new float[] {1f, 1f, 1f}, Arrays.copyOf(b.v, b.vc * 8), Arrays.copyOf(b.ix, b.vc), b.vc);
        return new MeshData(false, new MeshData.Part[] {part});
    }

    // ---- the kinds ----

    /** 8-sided ribbed trunk with a pointed top and one or two arms (5-sided, up-turned). At most 74 triangles. */
    private static void saguaro(Builder b, int variant, int body, int rib) {
        float h = 4.2f;
        b.cylinder(0, 0, 0, 0, 1, 0, h, 0.32f, 0.28f, 8, rib, body, false);
        b.cone(0, h, 0, 0, 1, 0, 0.35f, 0.28f, 8, body);
        arm(b, 0.5f * h, 0f, 1.2f, body);
        if (variant == 1) arm(b, 0.4f * h, (float) Math.PI, 0.9f, body);
    }

    private static void arm(Builder b, float y, float angle, float up, int body) {
        float cx = (float) Math.cos(angle), cz = (float) Math.sin(angle);
        float out = 0.62f;
        b.cylinder(0, y, 0, cx, 0, cz, out, 0.14f, 0.14f, 5, body, body, false);
        b.cylinder(cx * out, y, cz * out, 0, 1, 0, up, 0.14f, 0.14f, 5, body, body, false);
        b.cone(cx * out, y + up, cz * out, 0, 1, 0, 0.25f, 0.14f, 5, body);
    }

    /** A flat-topped mesa: two 8-sided steps, a ledge between them, a flat top. 54 triangles. */
    private static void butte(Builder b, int sides, int top) {
        float r = 5.5f, h1 = 4.4f, h = 8f;
        b.cylinder(0, 0, 0, 0, 1, 0, h1, r, 5.0f, 8, sides, sides, false);
        b.annulus(h1, 5.0f, 4.2f, 8, top);
        b.cylinder(0, h1, 0, 0, 1, 0, h - h1, 4.2f, 3.9f, 8, sides, sides, false);
        b.fanUp(h, 3.9f, 8, top);
    }

    /** Three squat 6-sided barrels with a pink flower on top (20 triangles each). 60 triangles. */
    private static void barrels(Builder b, int barrel, int flower) {
        barrel(b, 0f, 0f, 0.45f, 0.6f, barrel, flower);
        barrel(b, 0.55f, 0.15f, 0.35f, 0.5f, barrel, flower);
        barrel(b, -0.4f, 0.5f, 0.3f, 0.4f, barrel, flower);
    }

    private static void barrel(Builder b, float x, float z, float r, float h, int barrel, int flower) {
        b.cylinder(x, 0, z, 0, 1, 0, h, r, r * 0.9f, 6, barrel, barrel, false);
        b.fanUpAt(x, h, z, r * 0.9f, 6, barrel);
        b.cone(x, h, z, 0, 1, 0, 0.15f, 0.12f, 4, flower);
    }

    /** Three 20-triangle rocks (jittered icosahedra, flattened), 60 triangles. */
    private static void rocks(Builder b, int a, int c) {
        Random rnd = new Random(77L);
        rock(b, rnd, 0f, 0f, 0.75f, a);
        rock(b, rnd, 0.85f, 0.15f, 0.5f, c);
        rock(b, rnd, -0.6f, 0.6f, 0.35f, a);
    }

    private static final float PHI = (1f + (float) Math.sqrt(5.0)) / 2f;
    private static final float[][] ICO = {
            {-1, PHI, 0}, {1, PHI, 0}, {-1, -PHI, 0}, {1, -PHI, 0},
            {0, -1, PHI}, {0, 1, PHI}, {0, -1, -PHI}, {0, 1, -PHI},
            {PHI, 0, -1}, {PHI, 0, 1}, {-PHI, 0, -1}, {-PHI, 0, 1}};
    private static final int[][] ICO_FACES = {
            {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
            {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
            {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};

    private static void rock(Builder b, Random rnd, float x, float z, float radius, int swatch) {
        float[][] p = new float[12][3];
        float minY = Float.MAX_VALUE;
        for (int i = 0; i < 12; i++) {
            float len = (float) Math.sqrt(ICO[i][0] * ICO[i][0] + ICO[i][1] * ICO[i][1] + ICO[i][2] * ICO[i][2]);
            float j = radius * (0.85f + 0.3f * rnd.nextFloat()) / len;
            p[i][0] = ICO[i][0] * j;
            p[i][1] = ICO[i][1] * j * 0.7f;
            p[i][2] = ICO[i][2] * j;
            minY = Math.min(minY, p[i][1]);
        }
        for (int i = 0; i < 12; i++) {
            p[i][0] += x;
            p[i][1] -= minY;
            p[i][2] += z;
        }
        float cy = -minY;
        for (int[] f : ICO_FACES) b.triOut(p[f[0]], p[f[1]], p[f[2]], x, cy, z, swatch);
    }

    /** A forked trunk (5-sided, then two 4-sided forks) with five 4-sided leaf cones. 46 triangles. */
    private static void joshua(Builder b, int trunk, int leaves) {
        b.cylinder(0, 0, 0, 0, 1, 0, 1.5f, 0.2f, 0.15f, 5, trunk, trunk, false);
        float[] a = unit(0.57f, 0.82f, 0f), c = unit(-0.5f, 0.85f, 0.15f);
        float len = 1.3f;
        b.cylinder(0, 1.5f, 0, a[0], a[1], a[2], len, 0.14f, 0.1f, 4, trunk, trunk, false);
        b.cylinder(0, 1.5f, 0, c[0], c[1], c[2], len, 0.14f, 0.1f, 4, trunk, trunk, false);
        b.cone(a[0] * len, 1.5f + a[1] * len, a[2] * len, a[0], a[1], a[2], 0.9f, 0.45f, 4, leaves);
        b.cone(c[0] * len, 1.5f + c[1] * len, c[2] * len, c[0], c[1], c[2], 0.9f, 0.45f, 4, leaves);
        b.cone(0, 1.5f, 0, 0, 1, 0, 0.8f, 0.35f, 4, leaves);
        float[] sa = unit(0.9f, 0.3f, 0f), sc = unit(-0.9f, 0.3f, 0.2f);
        b.cone(a[0] * len * 0.6f, 1.5f + a[1] * len * 0.6f, a[2] * len * 0.6f, sa[0], sa[1], sa[2], 0.6f, 0.3f, 4, leaves);
        b.cone(c[0] * len * 0.6f, 1.5f + c[1] * len * 0.6f, c[2] * len * 0.6f, sc[0], sc[1], sc[2], 0.6f, 0.3f, 4, leaves);
    }

    private static float[] unit(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / l, y / l, z / l};
    }

    // ---- the triangle builder (package visible for the tests) ----

    /** Collects flat-shaded triangles: three vertices each, one swatch uv per triangle, one normal per triangle. */
    static final class Builder {
        final PaletteSwatches pal;
        float[] v = new float[8 * 3 * 64];
        short[] ix = new short[3 * 64];
        int vc;

        Builder(PaletteSwatches pal) {
            this.pal = pal;
        }

        int triangles() {
            return vc / 3;
        }

        /** One triangle, counter clockwise seen from the side its normal points to. */
        void tri(float[] a, float[] b, float[] c, int swatch) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            float wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9f) return;
            nx /= l;
            ny /= l;
            nz /= l;
            if ((vc + 3) * 8 > v.length) {
                v = Arrays.copyOf(v, v.length * 2);
                ix = Arrays.copyOf(ix, ix.length * 2);
            }
            float u = pal.u(swatch), t = pal.v(swatch);
            float[][] pts = {a, b, c};
            for (float[] p : pts) {
                int o = vc * 8;
                v[o] = p[0];
                v[o + 1] = p[1];
                v[o + 2] = p[2];
                v[o + 3] = nx;
                v[o + 4] = ny;
                v[o + 5] = nz;
                v[o + 6] = u;
                v[o + 7] = t;
                ix[vc] = (short) vc;
                vc++;
            }
        }

        /** A triangle turned to face away from the point (cx, cy, cz). */
        void triOut(float[] a, float[] b, float[] c, float cx, float cy, float cz, int swatch) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            float wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            float mx = (a[0] + b[0] + c[0]) / 3f - cx, my = (a[1] + b[1] + c[1]) / 3f - cy, mz = (a[2] + b[2] + c[2]) / 3f - cz;
            if (nx * mx + ny * my + nz * mz >= 0f) tri(a, b, c, swatch);
            else tri(a, c, b, swatch);
        }

        /**
         * A frustum (or cylinder) round an axis, from a base point along a unit axis; sides are 2 triangles per side,
         * alternating the two swatches round the ring.
         */
        void cylinder(float bx, float by, float bz, float ax, float ay, float az, float len, float r0, float r1, int sides,
                      int swEven, int swOdd, boolean capTop) {
            float[][] basis = basis(ax, ay, az);
            float[] u = basis[0], w = basis[1];
            float[][] lo = new float[sides + 1][], hi = new float[sides + 1][];
            for (int i = 0; i <= sides; i++) {
                double a = 2 * Math.PI * (i % sides) / sides;
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                lo[i] = new float[] {bx + (u[0] * c + w[0] * s) * r0, by + (u[1] * c + w[1] * s) * r0, bz + (u[2] * c + w[2] * s) * r0};
                hi[i] = new float[] {bx + ax * len + (u[0] * c + w[0] * s) * r1, by + ay * len + (u[1] * c + w[1] * s) * r1,
                        bz + az * len + (u[2] * c + w[2] * s) * r1};
            }
            for (int i = 0; i < sides; i++) {
                int sw = (i & 1) == 0 ? swEven : swOdd;
                tri(lo[i], lo[i + 1], hi[i + 1], sw);
                tri(lo[i], hi[i + 1], hi[i], sw);
            }
            if (capTop) {
                for (int i = 1; i < sides - 1; i++) tri(hi[0], hi[i], hi[i + 1], swEven);
            }
        }

        /** A cone: {@code sides} triangles from a ring round the axis up to the apex. */
        void cone(float bx, float by, float bz, float ax, float ay, float az, float len, float r, int sides, int swatch) {
            float[][] basis = basis(ax, ay, az);
            float[] u = basis[0], w = basis[1];
            float[] apex = {bx + ax * len, by + ay * len, bz + az * len};
            float[][] ring = new float[sides + 1][];
            for (int i = 0; i <= sides; i++) {
                double a = 2 * Math.PI * (i % sides) / sides;
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                ring[i] = new float[] {bx + (u[0] * c + w[0] * s) * r, by + (u[1] * c + w[1] * s) * r, bz + (u[2] * c + w[2] * s) * r};
            }
            for (int i = 0; i < sides; i++) tri(ring[i], ring[i + 1], apex, swatch);
        }

        /** A flat disc facing up, centred on the y axis: {@code sides - 2} triangles. */
        void fanUp(float y, float r, int sides, int swatch) {
            fanUpAt(0f, y, 0f, r, sides, swatch);
        }

        void fanUpAt(float x, float y, float z, float r, int sides, int swatch) {
            float[][] p = new float[sides][];
            for (int i = 0; i < sides; i++) {
                double a = 2 * Math.PI * i / sides;
                p[i] = new float[] {x + (float) Math.cos(a) * r, y, z - (float) Math.sin(a) * r};
            }
            for (int i = 1; i < sides - 1; i++) tri(p[0], p[i], p[i + 1], swatch);
        }

        /** A flat ring facing up between two radii: 2 triangles per side. */
        void annulus(float y, float rOut, float rIn, int sides, int swatch) {
            for (int i = 0; i < sides; i++) {
                double a0 = 2 * Math.PI * i / sides, a1 = 2 * Math.PI * (i + 1) / sides;
                float[] o0 = {(float) Math.cos(a0) * rOut, y, -(float) Math.sin(a0) * rOut};
                float[] o1 = {(float) Math.cos(a1) * rOut, y, -(float) Math.sin(a1) * rOut};
                float[] i0 = {(float) Math.cos(a0) * rIn, y, -(float) Math.sin(a0) * rIn};
                float[] i1 = {(float) Math.cos(a1) * rIn, y, -(float) Math.sin(a1) * rIn};
                tri(o0, i1, i0, swatch);
                tri(o0, o1, i1, swatch);
            }
        }

        /** Two unit vectors u, w round a unit axis with u x w = axis. */
        private static float[][] basis(float ax, float ay, float az) {
            float hx = 0f, hy = 1f, hz = 0f;
            if (Math.abs(ay) > 0.9f) {
                hx = 1f;
                hy = 0f;
            }
            float ux = ay * hz - az * hy, uy = az * hx - ax * hz, uz = ax * hy - ay * hx;
            float l = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= l;
            uy /= l;
            uz /= l;
            float wx = ay * uz - az * uy, wy = az * ux - ax * uz, wz = ax * uy - ay * ux;
            return new float[][] {{ux, uy, uz}, {wx, wy, wz}};
        }
    }
}
