package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.Random;

/**
 * Scenery props built in code (T-29R Part R4): smooth, rounded shapes with the colour set per vertex from ramp swatches (darker
 * toward the base and in the grooves, lighter on top and on the ridges: a baked ambient occlusion), 2 or 3 variants of most kinds,
 * base at y = 0, centred on the origin, no bottom faces. Plain Java; the scene builds each variant once and merges the placements
 * into the static chunks.
 *
 * <p>Organic surfaces (cacti, rocks) have smooth normals; cut faces (mesa cliffs, pebbles, leaf spikes) are flat. Colours:
 * {@code sw} holds one ramp swatch per ramp of the rule: SAGUARO green; BUTTE strata A and B; BARREL_CACTI green and flower;
 * ROCKS one; JOSHUA_TREE trunk and leaves; SCRUB, DRY_GRASS and PEBBLES one each.
 */
public final class PropMeshes {

    /** Prop kinds. */
    public static final int SAGUARO = 0, BUTTE = 1, BARREL_CACTI = 2, ROCKS = 3, JOSHUA_TREE = 4, SCRUB = 5, DRY_GRASS = 6,
            PEBBLES = 7;
    /** Number of kinds. */
    public static final int COUNT = 8;
    /** Triangle limits of the ticket, per kind. */
    public static final int[] MAX_TRIANGLES = {450, 900, 300, 400, 400, 120, 14, 48};
    /** Number of ramps each kind needs in its rule. */
    public static final int[] RAMPS = {1, 2, 2, 1, 2, 1, 1, 1};

    private static final int[] VARIANTS = {3, 2, 3, 3, 2, 1, 2, 2};
    private static final float[] REACH = new float[COUNT];

    private PropMeshes() { }

    /**
     * How many shapes a kind has (the scatter picks one by chance).
     * @param kind a kind
     * @return the count
     */
    public static int variants(int kind) {
        return VARIANTS[kind];
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

    private static final float[] HEIGHT = new float[COUNT];

    /**
     * The height of a kind at scale 1 (the tallest variant).
     * @param kind a kind
     * @return metres
     */
    public static synchronized float height(int kind) {
        if (HEIGHT[kind] == 0f) {
            PaletteSwatches pal = new PaletteSwatches();
            float h = 0f;
            for (int v = 0; v < variants(kind); v++) {
                MeshData.Part p = build(kind, v, pal, new int[] {0, 1}).parts[0];
                for (int i = 0; i < p.vertexCount; i++) h = Math.max(h, p.vertices[i * 8 + 1]);
            }
            HEIGHT[kind] = h;
        }
        return HEIGHT[kind];
    }

    /**
     * Builds one prop.
     * @param kind a kind
     * @param variant 0 to {@code variants(kind) - 1}
     * @param pal the palette (only its swatch uvs are used)
     * @param sw the ramp swatches of the rule
     * @return the mesh, one part named "prop", single sided, local space
     */
    public static MeshData build(int kind, int variant, PaletteSwatches pal, int[] sw) {
        MeshKit k = new MeshKit(pal);
        Random rnd = new Random(kind * 1000L + variant * 17L + 5L);
        switch (kind) {
            case SAGUARO:
                saguaro(k, variant + 1, sw[0], rnd);
                break;
            case BUTTE:
                butte(k, variant, sw[0], sw[1], rnd);
                break;
            case BARREL_CACTI:
                barrels(k, variant == 0 ? 2 : 3, sw[0], sw[1], rnd);
                break;
            case ROCKS:
                rocks(k, variant + 3, sw[0], rnd);
                break;
            case JOSHUA_TREE:
                joshua(k, variant, sw[0], sw[1], rnd);
                break;
            case SCRUB:
                scrub(k, sw[0], rnd);
                break;
            case DRY_GRASS:
                grass(k, variant, sw[0], rnd);
                break;
            case PEBBLES:
                pebbles(k, variant, sw[0], rnd);
                break;
            default:
                throw new IllegalArgumentException("unknown prop kind " + kind);
        }
        return k.mesh();
    }

    // ---- saguaro: ribbed 16-sided trunk with a rounded top and 1 to 3 arms ----

    private static void saguaro(MeshKit k, int arms, int sw, Random rnd) {
        float h = 4.2f + rnd.nextFloat() * 0.8f;
        float[][] trunk = {{0f, 0f, 0f}, {0f, h * 0.35f, 0f}, {0f, h * 0.7f, 0f}, {0f, h * 0.93f, 0f}};
        k.tube(trunk, new float[] {0.42f, 0.41f, 0.38f, 0.33f}, new float[] {0.12f, 0.35f, 0.6f, 0.75f}, 16, 0.86f, sw, true);
        float yaw = rnd.nextFloat() * 6.28f;
        for (int i = 0; i < arms; i++) {
            float a = yaw + i * (arms == 1 ? 0f : 6.28f / arms) + (i == 0 ? 0f : 0.4f);
            float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
            float y0 = h * (0.4f + 0.12f * i + 0.05f * rnd.nextFloat());
            float len = 0.95f + 0.25f * rnd.nextFloat();
            float up = 0.9f + 0.6f * rnd.nextFloat();
            float[][] arm = {{dx * 0.15f, y0, dz * 0.15f}, {dx * (0.6f + 0.2f), y0 + 0.02f, dz * (0.6f + 0.2f)},
                    {dx * len, y0 + 0.28f, dz * len}, {dx * (len + 0.05f), y0 + 0.28f + up, dz * (len + 0.05f)}};
            k.tube(arm, new float[] {0.2f, 0.19f, 0.18f, 0.16f}, new float[] {0.3f, 0.4f, 0.55f, 0.72f}, 8, 0.86f, sw, true);
        }
    }

    // ---- butte: a mesa with a talus skirt, stepped strata bands in two colours and a lipped flat top ----

    private static void butte(MeshKit k, int variant, int swA, int swB, Random rnd) {
        final int n = 24;
        float height = 7f + 1.5f * variant + rnd.nextFloat();
        float radius = 3.6f + 0.5f * variant;
        int bands = 4 + variant + 1;
        float skirt = 1.4f;
        float talusTop = height * 0.2f;
        float[][] ring = new float[bands + 2][n];   // radius per ring per side
        float[] ys = new float[bands + 2];
        ys[0] = 0f;
        ys[1] = talusTop;
        for (int b = 1; b <= bands; b++) ys[b + 1] = talusTop + (height - talusTop) * b / bands;
        for (int s = 0; s < n; s++) {
            float wob = 0.08f * (rnd.nextFloat() - 0.5f) * 2f + 0.1f * (float) Math.sin(s * 2.0 * Math.PI / n * 3 + variant);
            ring[0][s] = radius * skirt * (1f + wob * 0.5f);
            for (int b = 1; b <= bands + 1; b++) ring[b][s] = radius * (1f + wob);
        }
        // each band gets its own in/out step so the cliff reads as layered rock
        float[] step = new float[bands + 2];
        for (int b = 2; b <= bands + 1; b++) step[b] = 1f + (rnd.nextFloat() - 0.5f) * 0.12f;
        float[] c0 = new float[3], c1 = new float[3], c2 = new float[3], c3 = new float[3];
        for (int s = 0; s < n; s++) {
            int s2 = (s + 1) % n;
            double a0 = 2.0 * Math.PI * s / n, a1 = 2.0 * Math.PI * s2 / n;
            float mx = (float) Math.cos((a0 + a1) / 2.0), mz = (float) Math.sin((a0 + a1) / 2.0);
            // talus slope
            put(c0, ring[0][s], 0f, a0);
            put(c1, ring[0][s2], 0f, a1);
            put(c2, ring[1][s2], talusTop, a1);
            put(c3, ring[1][s], talusTop, a0);
            k.flatQuad(c0, c1, c2, c3, 0.1f, 0.12f, 0.3f, 0.28f, swA, mx, 0.7f, mz);
            // cliff bands
            for (int b = 1; b <= bands; b++) {
                boolean even = (b & 1) == 0;
                int sw = even ? swA : swB;
                float r0 = ring[b][s] * step[b], r1 = ring[b][s2] * step[b];
                float rt0 = ring[b + 1][s] * step[b], rt1 = ring[b + 1][s2] * step[b];
                put(c0, r0, ys[b], a0);
                put(c1, r1, ys[b], a1);
                put(c2, rt1, ys[b + 1], a1);
                put(c3, rt0, ys[b + 1], a0);
                float jitter = 0.08f * Noise.hash(s, b, 91L + variant);
                k.flatQuad(c0, c1, c2, c3, 0.25f + jitter, 0.28f + jitter, 0.75f + jitter, 0.72f + jitter, sw, mx, 0f, mz);
                // the ledge where the next band steps in or out
                if (b < bands) {
                    float nr0 = ring[b + 1][s] * step[b + 1], nr1 = ring[b + 1][s2] * step[b + 1];
                    float[] l0 = {mx * 0 + (float) Math.cos(a0) * rt0, ys[b + 1], (float) Math.sin(a0) * rt0};
                    float[] l1 = {(float) Math.cos(a1) * rt1, ys[b + 1], (float) Math.sin(a1) * rt1};
                    float[] l2 = {(float) Math.cos(a1) * nr1, ys[b + 1], (float) Math.sin(a1) * nr1};
                    float[] l3 = {(float) Math.cos(a0) * nr0, ys[b + 1], (float) Math.sin(a0) * nr0};
                    k.flatQuad(l0, l1, l2, l3, 0.85f, 0.85f, 0.85f, 0.85f, even ? swB : swA, 0f, 1f, 0f);
                }
            }
            // flat top: lip ring at the top edge, then a fan to the centre
            float rTop0 = ring[bands + 1][s] * step[bands], rTop1 = ring[bands + 1][s2] * step[bands];
            float[] t0 = {(float) Math.cos(a0) * rTop0, height, (float) Math.sin(a0) * rTop0};
            float[] t1 = {(float) Math.cos(a1) * rTop1, height, (float) Math.sin(a1) * rTop1};
            float[] t2 = {(float) Math.cos(a1) * rTop1 * 0.9f, height + 0.12f, (float) Math.sin(a1) * rTop1 * 0.9f};
            float[] t3 = {(float) Math.cos(a0) * rTop0 * 0.9f, height + 0.12f, (float) Math.sin(a0) * rTop0 * 0.9f};
            k.flatQuad(t0, t1, t2, t3, 0.9f, 0.9f, 0.95f, 0.95f, swB, 0f, 1f, 0f);
            float[] cen = {0f, height + 0.1f, 0f};
            k.flatTri(t3, t2, cen, 0.95f, 0.95f, 0.88f, swB, 0f, 1f, 0f);
        }
    }

    private static void put(float[] out, float r, float y, double a) {
        out[0] = (float) Math.cos(a) * r;
        out[1] = y;
        out[2] = (float) Math.sin(a) * r;
    }

    // ---- barrel cacti: 2 to 4 ribbed barrels with dome tops and some flowers ----

    private static void barrels(MeshKit k, int count, int sw, int swFlower, Random rnd) {
        for (int i = 0; i < count; i++) {
            float a = i * 6.28f / count + rnd.nextFloat() * 0.6f;
            float d = i == 0 ? 0f : 0.55f + 0.2f * rnd.nextFloat();
            float cx = (float) Math.cos(a) * d, cz = (float) Math.sin(a) * d;
            float s = i == 0 ? 1f : 0.65f + 0.3f * rnd.nextFloat();
            float h = 0.62f * s;
            float[][] path = {{cx, 0f, cz}, {cx, h * 0.45f, cz}};
            k.tube(path, new float[] {0.30f * s, 0.36f * s}, new float[] {0.12f, 0.5f}, i == 0 ? 14 : 10, 0.88f, sw, true);
            if (i < 2 && rnd.nextFloat() < 0.8f) {
                // six petals round the crown
                float top = h * 0.45f + 0.36f * s * 0.95f;
                for (int p = 0; p < 6; p++) {
                    double pa = 2.0 * Math.PI * p / 6.0 + a;
                    float[] b0 = {cx + (float) Math.cos(pa - 0.3) * 0.04f, top, cz + (float) Math.sin(pa - 0.3) * 0.04f};
                    float[] b1 = {cx + (float) Math.cos(pa + 0.3) * 0.04f, top, cz + (float) Math.sin(pa + 0.3) * 0.04f};
                    float[] tip = {cx + (float) Math.cos(pa) * 0.12f, top + 0.05f, cz + (float) Math.sin(pa) * 0.12f};
                    k.flatTri(b0, b1, tip, 0.4f, 0.4f, 0.9f, swFlower, 0f, 1f, 0f);
                }
            }
        }
    }

    // ---- rocks: 3 to 5 noise-displaced icospheres, lighter on top ----

    private static void rocks(MeshKit k, int count, int sw, Random rnd) {
        for (int i = 0; i < count; i++) {
            float a = i * 6.28f / count + rnd.nextFloat() * 0.7f;
            float d = i == 0 ? 0f : 0.7f + 0.5f * rnd.nextFloat();
            float cx = (float) Math.cos(a) * d, cz = (float) Math.sin(a) * d;
            float s = i == 0 ? 0.75f : 0.35f + 0.25f * rnd.nextFloat();
            rock(k, cx, cz, s * (1f + 0.3f * rnd.nextFloat()), s * 0.7f, s * (1f + 0.3f * rnd.nextFloat()), sw, i * 7L + rnd.nextInt(100));
        }
    }

    private static final float[][] ICO = icoVerts();
    private static final int[][] ICO_F = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4},
            {11, 10, 2}, {10, 7, 6}, {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11},
            {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};

    private static float[][] icoVerts() {
        float t = (1f + (float) Math.sqrt(5.0)) / 2f;
        float[][] v = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t}, {t, 0, -1},
                {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        for (float[] p : v) MeshKit.norm(p);
        return v;
    }

    private static void rock(MeshKit k, float cx, float cz, float rx, float ry, float rz, int sw, long seed) {
        // subdivide once: 42 vertices, 80 faces
        java.util.ArrayList<float[]> verts = new java.util.ArrayList<>();
        for (float[] p : ICO) verts.add(p.clone());
        java.util.HashMap<Long, Integer> mid = new java.util.HashMap<>();
        int[][] faces = new int[80][3];
        int fc = 0;
        for (int[] f : ICO_F) {
            int a = f[0], b = f[1], c = f[2];
            int ab = midpoint(verts, mid, a, b), bc = midpoint(verts, mid, b, c), ca = midpoint(verts, mid, c, a);
            faces[fc++] = new int[] {a, ab, ca};
            faces[fc++] = new int[] {b, bc, ab};
            faces[fc++] = new int[] {c, ca, bc};
            faces[fc++] = new int[] {ab, bc, ca};
        }
        int nv = verts.size();
        float[][] pos = new float[nv][3];
        float[] tone = new float[nv];
        for (int i = 0; i < nv; i++) {
            float[] p = verts.get(i);
            float disp = 0.78f + 0.44f * Noise.noise(p[0] * 2.1f + seed, p[2] * 2.1f + p[1] * 1.3f, seed * 3L + 1L);
            pos[i][0] = cx + p[0] * rx * disp;
            pos[i][1] = Math.max(0f, p[1] * ry * disp + ry * 0.55f);
            pos[i][2] = cz + p[2] * rz * disp;
            tone[i] = 0.2f + 0.55f * (0.5f + 0.5f * p[1]) + 0.2f * (Noise.noise(p[0] * 5f, p[2] * 5f, seed) - 0.5f);
        }
        // smooth normals, faces whose three vertices are on the ground are left out (no bottom)
        float[][] nrm = new float[nv][3];
        boolean[] keep = new boolean[80];
        for (int f = 0; f < 80; f++) {
            int a = faces[f][0], b = faces[f][1], c = faces[f][2];
            keep[f] = !(pos[a][1] < 0.02f && pos[b][1] < 0.02f && pos[c][1] < 0.02f)
                    && (pos[a][1] + pos[b][1] + pos[c][1]) / 3f > ry * 0.12f;
            float ux = pos[b][0] - pos[a][0], uy = pos[b][1] - pos[a][1], uz = pos[b][2] - pos[a][2];
            float wx = pos[c][0] - pos[a][0], wy = pos[c][1] - pos[a][1], wz = pos[c][2] - pos[a][2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            for (int q : faces[f]) {
                nrm[q][0] += nx;
                nrm[q][1] += ny;
                nrm[q][2] += nz;
            }
        }
        int[] map = new int[nv];
        java.util.Arrays.fill(map, -1);
        for (int f = 0; f < 80; f++) {
            if (!keep[f]) continue;
            int[] id = new int[3];
            for (int q = 0; q < 3; q++) {
                int vi = faces[f][q];
                if (map[vi] < 0) {
                    MeshKit.norm(nrm[vi]);
                    map[vi] = k.vert(pos[vi][0], pos[vi][1], pos[vi][2], nrm[vi][0], nrm[vi][1], nrm[vi][2], sw, tone[vi]);
                }
                id[q] = map[vi];
            }
            // the icosahedron faces are counter-clockwise seen from outside
            k.tri(id[0], id[1], id[2]);
        }
    }

    private static int midpoint(java.util.ArrayList<float[]> verts, java.util.HashMap<Long, Integer> cache, int a, int b) {
        long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
        Integer have = cache.get(key);
        if (have != null) return have;
        float[] pa = verts.get(a), pb = verts.get(b);
        float[] m = {(pa[0] + pb[0]) / 2f, (pa[1] + pb[1]) / 2f, (pa[2] + pb[2]) / 2f};
        MeshKit.norm(m);
        verts.add(m);
        cache.put(key, verts.size() - 1);
        return verts.size() - 1;
    }

    // ---- joshua tree: a trunk that forks, with a ball of spikes at every tip ----

    private static void joshua(MeshKit k, int variant, int swTrunk, int swLeaf, Random rnd) {
        float h = 1.7f + 0.3f * variant;
        float[][] trunk = {{0f, 0f, 0f}, {0.05f, h * 0.5f, 0f}, {0.1f, h, 0.02f}};
        k.tube(trunk, new float[] {0.17f, 0.15f, 0.12f}, new float[] {0.15f, 0.45f, 0.6f}, 6, 1f, swTrunk, false);
        java.util.ArrayList<float[]> tips = new java.util.ArrayList<>();
        int branches = 2 + variant;
        for (int b = 0; b < branches; b++) {
            float a = b * 6.28f / branches + 0.5f + variant;
            float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
            float len = 0.75f + 0.25f * rnd.nextFloat();
            float[][] br = {{0.1f, h, 0.02f}, {0.1f + dx * len * 0.5f, h + len * 0.35f, 0.02f + dz * len * 0.5f},
                    {0.1f + dx * len, h + len * 0.8f, 0.02f + dz * len}};
            k.tube(br, new float[] {0.1f, 0.085f, 0.07f}, new float[] {0.45f, 0.55f, 0.65f}, 6, 1f, swTrunk, false);
            if (b == 0) {
                // a second fork on the first branch
                for (int f = 0; f < 2; f++) {
                    float fa = a + (f == 0 ? 0.9f : -0.9f);
                    float fx = (float) Math.cos(fa), fz = (float) Math.sin(fa);
                    float[][] sub = {br[2], {br[2][0] + fx * 0.3f, br[2][1] + 0.3f, br[2][2] + fz * 0.3f}};
                    k.tube(sub, new float[] {0.065f, 0.05f}, new float[] {0.55f, 0.65f}, 6, 1f, swTrunk, false);
                    tips.add(sub[1]);
                }
            } else {
                tips.add(br[2]);
            }
        }
        for (float[] tip : tips) spikeBall(k, tip, 0.5f, swLeaf, rnd);
    }

    /** A ball of 12 thin spikes (3 faces each) round a point. */
    private static void spikeBall(MeshKit k, float[] c, float len, int sw, Random rnd) {
        float golden = (float) (Math.PI * (3.0 - Math.sqrt(5.0)));
        for (int i = 0; i < 12; i++) {
            float y = 1f - 2f * (i + 0.5f) / 12f;
            float r = (float) Math.sqrt(1f - y * y);
            float a = i * golden + rnd.nextFloat() * 0.3f;
            float dx = (float) Math.cos(a) * r, dz = (float) Math.sin(a) * r;
            float l = len * (0.8f + 0.4f * rnd.nextFloat());
            float[] apex = {c[0] + dx * l, c[1] + y * l, c[2] + dz * l};
            // base triangle round the centre, perpendicular to the spike
            float px = -dz, pz = dx;
            float[] b0 = {c[0] + px * 0.05f, c[1] + 0.04f, c[2] + pz * 0.05f};
            float[] b1 = {c[0] - px * 0.05f, c[1] + 0.04f, c[2] - pz * 0.05f};
            float[] b2 = {c[0] - dx * 0.03f, c[1] - 0.05f, c[2] - dz * 0.03f};
            k.flatTri(b0, b1, apex, 0.2f, 0.2f, 0.95f, sw, dx, y, dz);
            k.flatTri(b1, b2, apex, 0.2f, 0.15f, 0.95f, sw, dx - px, y, dz - pz);
            k.flatTri(b2, b0, apex, 0.15f, 0.2f, 0.95f, sw, dx + px, y, dz + pz);
        }
    }

    // ---- scrub: a low dome of leaf cards (both sides drawn) ----

    private static void scrub(MeshKit k, int sw, Random rnd) {
        int cards = 24;
        float golden = (float) (Math.PI * (3.0 - Math.sqrt(5.0)));
        for (int i = 0; i < cards; i++) {
            float el = 0.3f + 1.0f * (i + 0.5f) / cards;   // 17 to 75 degrees
            float a = i * golden;
            float dx = (float) Math.cos(a) * (float) Math.cos(el), dz = (float) Math.sin(a) * (float) Math.cos(el), dy = (float) Math.sin(el);
            float l = 0.38f + 0.18f * rnd.nextFloat();
            float px = -dz, pz = dx;
            float[] b0 = {dx * 0.05f + px * 0.07f, 0.04f + dy * 0.05f, dz * 0.05f + pz * 0.07f};
            float[] b1 = {dx * 0.05f - px * 0.07f, 0.04f + dy * 0.05f, dz * 0.05f - pz * 0.07f};
            float[] t1 = {dx * l - px * 0.05f, dy * l * 0.9f, dz * l - pz * 0.05f};
            float[] t0 = {dx * l + px * 0.05f, dy * l * 0.9f, dz * l + pz * 0.05f};
            k.flatQuad(b0, b1, t1, t0, 0.2f, 0.2f, 0.9f, 0.9f, sw, dx, dy + 0.3f, dz);
            k.flatQuad(b1, b0, t0, t1, 0.2f, 0.2f, 0.9f, 0.9f, sw, -dx, -dy - 0.3f, -dz);
        }
    }

    // ---- dry grass: a tuft of bent blades (each drawn from both sides) ----

    private static void grass(MeshKit k, int variant, int sw, Random rnd) {
        int blades = 7;
        for (int i = 0; i < blades; i++) {
            float a = i * 0.9f + variant;
            float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
            float lean = 0.15f + 0.25f * rnd.nextFloat();
            float h = 0.45f + 0.35f * rnd.nextFloat();
            float px = -dz * 0.025f, pz = dx * 0.025f;
            float ox = dx * 0.04f, oz = dz * 0.04f;
            float[] b0 = {ox + px, 0f, oz + pz}, b1 = {ox - px, 0f, oz - pz};
            float[] tip = {ox + dx * lean, h, oz + dz * lean};
            k.flatTri(b0, b1, tip, 0.15f, 0.15f, 1f, sw, px * 0 + dz, 0.2f, -dx);
            k.flatTri(b1, b0, tip, 0.15f, 0.15f, 1f, sw, -dz, 0.2f, dx);
        }
    }

    // ---- pebbles: 8 stones of 5 faces ----

    private static void pebbles(MeshKit k, int variant, int sw, Random rnd) {
        for (int i = 0; i < 8; i++) {
            float a = i * 0.8f + variant * 0.4f;
            float d = 0.12f + 0.4f * rnd.nextFloat();
            float cx = (float) Math.cos(a) * d, cz = (float) Math.sin(a) * d;
            float r = 0.07f + 0.08f * rnd.nextFloat(), hgt = 0.04f + 0.05f * rnd.nextFloat();
            float tone = 0.25f + 0.5f * rnd.nextFloat();
            float[] apex = {cx + 0.01f, hgt, cz};
            float[][] ringP = new float[5][];
            for (int s = 0; s < 5; s++) {
                double sa = 2.0 * Math.PI * s / 5.0 + rnd.nextFloat() * 0.4;
                float rr = r * (0.8f + 0.4f * rnd.nextFloat());
                ringP[s] = new float[] {cx + (float) Math.cos(sa) * rr, 0f, cz + (float) Math.sin(sa) * rr};
            }
            for (int s = 0; s < 5; s++) {
                float[] p = ringP[s], q = ringP[(s + 1) % 5];
                k.flatTri(p, q, apex, tone * 0.6f, tone * 0.6f, tone + 0.2f, sw, (p[0] + q[0]) / 2f - cx, 0.4f, (p[2] + q[2]) / 2f - cz);
            }
        }
    }
}
