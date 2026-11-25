package com.wiggleplay.app.games.race;

import java.util.Random;

/**
 * The props of Rocky Lakes (T-30b), built with {@link MeshKit}: pines and pine groves (drooping jagged tiers), aspens, layered rock
 * outcrops, boulders, a log cabin, a dock, a canoe, fallen logs, ferns, wildflowers and grass tufts. Plain Java.
 *
 * <p>Local frame as in {@link CityProps}: the front (cabin door, the way a dock runs out) faces +z; the base is at y = 0 except that
 * trunks and rock feet reach down to y = -0.4 so that they stay planted on a slope; no bottom faces. Ramps follow the kind's rule
 * ({@link PropMeshes#RAMPS}).
 */
final class RockyProps {

    private RockyProps() { }

    /** Heights of the three pine variants (m) and the number of tiers. */
    static final float[] PINE_H = {5f, 8f, 11f};
    private static final int[] PINE_TIERS = {6, 7, 8};

    // ---- pine: an 8-sided trunk and drooping 12-sided tiers with jagged lower edges ----

    /** One pine of height h at (cx, cz) with its trunk reaching down to {@code foot}; tiers tiers; sw = needles, trunk. */
    static void pine(MeshKit k, float cx, float cz, float h, int tiers, float foot, int swNeedle, int swTrunk, Random rnd) {
        float trunkTop = h * 0.5f;
        float[][] path = {{cx, foot, cz}, {cx, trunkTop * 0.5f, cz}, {cx, trunkTop, cz}};
        float rb = 0.1f + 0.014f * h;
        k.tube(path, new float[] {rb * 1.15f, rb, rb * 0.6f}, new float[] {0.1f, 0.35f, 0.6f}, 8, 1f, swTrunk, false);
        float rBase = 0.3f * h;
        float lowest = h * 0.16f, span = h - lowest;
        float tierH = span / tiers * 1.55f;
        float phase = rnd.nextFloat() * 6.28f;
        for (int i = 0; i < tiers; i++) {
            float f = (float) i / tiers;
            float yBottom = lowest + span * f * 0.92f;
            float yTop = Math.min(h, yBottom + tierH);
            float rOut = rBase * (1f - 0.88f * f) + 0.12f;
            float[][][] p = new float[3][12][3];
            float[][] t = new float[3][12];
            for (int s = 0; s < 12; s++) {
                double a = 2.0 * Math.PI * s / 12 + phase + i * 0.5;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                boolean jag = (s & 1) == 1;
                float ro = rOut * (jag ? 0.78f : 1f) * (1f + 0.08f * (rnd.nextFloat() - 0.5f));
                float yo = yBottom + (jag ? 0.22f * tierH : 0f) - 0.08f * tierH * (1f - f);
                p[0][s] = new float[] {cx, yTop, cz};
                p[1][s] = new float[] {cx + ca * ro * 0.5f, yTop - 0.45f * (yTop - yo), cz + sa * ro * 0.5f};
                p[2][s] = new float[] {cx + ca * ro, yo, cz + sa * ro};
                t[0][s] = 0.85f - 0.2f * f;
                t[1][s] = 0.6f - 0.15f * f;
                t[2][s] = (jag ? 0.2f : 0.38f) + 0.3f * f;
            }
            k.grid(p, t, swNeedle, true);
        }
    }

    /** A single tree, 3 variants (5, 8 and 11 m). */
    static void pine(MeshKit k, int variant, int[] sw, Random rnd) {
        pine(k, 0f, 0f, PINE_H[variant], PINE_TIERS[variant], -0.4f, sw[0], sw[1], rnd);
    }

    /** 3 to 7 pines of mixed height within 5 m of the centre; 4 variants (3, 4, 5 and 7 pines). */
    static void grove(MeshKit k, int variant, int[] sw, Random rnd) {
        int[] counts = {3, 4, 5, 7};
        int n = counts[variant];
        for (int i = 0; i < n; i++) {
            float a = i * 2.4f + variant;
            float d = i == 0 ? 0f : 1.4f + 3.2f * (float) Math.sqrt((i + rnd.nextFloat()) / n);
            float h = 5f + 5f * rnd.nextFloat();
            pine(k, (float) Math.cos(a) * d, (float) Math.sin(a) * d, h, 6, -0.4f, sw[0], sw[1], rnd);
        }
    }

    // ---- aspen: a white trunk with dark marks and three crown lobes ----

    static void aspen(MeshKit k, int variant, int[] sw, Random rnd) {
        float h = 5.6f + 1.2f * variant;
        float[][] path = new float[5][];
        float[] rad = new float[5], tone = new float[5];
        for (int i = 0; i < 5; i++) {
            path[i] = new float[] {0.1f * (float) Math.sin(i + variant), i == 0 ? -0.4f : h * 0.65f * i / 4f, 0f};
            rad[i] = 0.2f - 0.025f * i;
            tone[i] = (i & 1) == 1 ? 0.12f : 0.85f;   // dark marks in bands
        }
        k.tube(path, rad, tone, 8, 1f, sw[0], false);
        float top = h * 0.65f;
        float[][] lobes = {{0f, top + 0.4f, 0f, 1.5f, 1.6f}, {0.9f, top + 1.6f, 0.4f, 1.2f, 1.3f}, {-0.7f, top + 2.2f, -0.5f, 1.1f, 1.2f}};
        for (float[] l : lobes) CityProps.blob(k, l[0], l[1], l[2], l[3], l[4], l[3], 8, 5, sw[1], 0.15f, 0.95f, rnd);
    }

    // ---- rock outcrop: angular layered slabs with flat cut faces ----

    static void outcrop(MeshKit k, int variant, int[] sw, Random rnd) {
        int tiers = 2 + variant % 2 + (variant == 2 ? 0 : 0);
        float y = -0.5f;
        float w = 1.9f + 0.4f * variant;
        for (int i = 0; i < tiers; i++) {
            int slabs = i == 0 ? 3 : 2;
            float th = 0.9f + 0.5f * rnd.nextFloat();
            for (int s = 0; s < slabs; s++) {
                float a = s * 2.1f + i * 0.9f + variant;
                float d = (s == 0 ? 0f : 0.9f + 0.4f * rnd.nextFloat()) * (1f - 0.25f * i);
                slab(k, (float) Math.cos(a) * d, y, (float) Math.sin(a) * d, w * (s == 0 ? 1f : 0.65f) * (1f - 0.22f * i), th, sw[0],
                        0.15f + 0.2f * i, rnd);
            }
            y += th * 0.85f;
        }
    }

    /** One slab: a 7-corner polygon prism that narrows a little towards the top, flat faces, lighter on top. */
    private static void slab(MeshKit k, float cx, float y0, float cz, float r, float th, int sw, float tBase, Random rnd) {
        int n = 7;
        float[][] lo = new float[n][], hi = new float[n][];
        float rot = rnd.nextFloat() * 6.28f;
        for (int i = 0; i < n; i++) {
            double a = rot + 2.0 * Math.PI * i / n;
            float rr = r * (0.7f + 0.55f * rnd.nextFloat());
            lo[i] = new float[] {cx + (float) Math.cos(a) * rr, y0, cz + (float) Math.sin(a) * rr};
            hi[i] = new float[] {cx + (float) Math.cos(a) * rr * 0.86f, y0 + th * (0.9f + 0.2f * rnd.nextFloat()), cz + (float) Math.sin(a) * rr * 0.86f};
        }
        float[] top = {cx, y0 + th * 1.02f, cz};
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float mx = (lo[i][0] + lo[j][0]) / 2f - cx, mz = (lo[i][2] + lo[j][2]) / 2f - cz;
            float jit = 0.12f * (rnd.nextFloat() - 0.5f);
            k.flatQuad(lo[i], lo[j], hi[j], hi[i], tBase + jit, tBase + jit, tBase + 0.3f + jit, tBase + 0.3f + jit, sw, mx, 0f, mz);
            k.flatTri(hi[i], hi[j], top, 0.8f + jit, 0.8f + jit, 0.9f, sw, 0f, 1f, 0f);
        }
    }

    // ---- boulders ----

    static void boulders(MeshKit k, int variant, int[] sw, Random rnd) {
        int count = 3 + variant;
        for (int i = 0; i < count; i++) {
            float a = i * 6.28f / count + rnd.nextFloat() * 0.7f;
            float d = i == 0 ? 0f : 0.9f + 0.6f * rnd.nextFloat();
            float s = i == 0 ? 0.85f : 0.4f + 0.3f * rnd.nextFloat();
            PropMeshes.rock(k, (float) Math.cos(a) * d, (float) Math.sin(a) * d, s * 1.2f, s * 0.85f, s * 1.1f, sw[0], i * 11L + variant * 5L + rnd.nextInt(50));
        }
    }

    // ---- cabin: stacked 8-sided logs, a gable roof of shingles, a chimney and a porch ----

    static void cabin(MeshKit k, int variant, int[] sw, Random rnd) {
        float w = 4.4f, d = 3.6f, r = 0.16f;
        int logs = 7;
        for (int i = 0; i < logs; i++) {
            float y = r + i * r * 1.85f;
            float t = 0.25f + 0.5f * ((i * 37) % 7) / 7f;
            // front and back walls along x, the side walls along z, ends crossing the corners
            for (int side = -1; side <= 1; side += 2) {
                k.tube(new float[][] {{-w / 2 - 0.2f, y, side * d / 2}, {w / 2 + 0.2f, y, side * d / 2}}, new float[] {r, r},
                        new float[] {t, t}, 8, 1f, sw[0], false);
                k.tube(new float[][] {{side * w / 2, y + r * 0.9f, -d / 2 - 0.2f}, {side * w / 2, y + r * 0.9f, d / 2 + 0.2f}},
                        new float[] {r, r}, new float[] {t + 0.1f, t + 0.1f}, 8, 1f, sw[0], false);
            }
        }
        float wallTop = r + (logs - 1) * r * 1.85f + r * 1.9f;
        float ridge = wallTop + 1.5f, over = 0.5f, ov = d / 2 + over;
        // the gable ends (logs showing as wood) and the two roof planes in strips of shingles
        CityProps.vquad(k, sw[0], -w / 2, d / 2, -w / 2, -d / 2, wallTop - 0.1f, wallTop + 0.1f, 0.5f, 0.5f, -1f, 0f);
        float[] a = {-w / 2 - over, wallTop - 0.05f, ov}, b = {w / 2 + over, wallTop - 0.05f, ov}, c = {w / 2 + over, ridge, 0f},
                e = {-w / 2 - over, ridge, 0f};
        int strips = 5;
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            for (int s = 0; s < strips; s++) {
                float f0 = (float) s / strips, f1 = (float) (s + 1) / strips;
                float z0 = sgn * ov * (1f - f0), z1 = sgn * ov * (1f - f1);
                float y0 = wallTop - 0.05f + (ridge - wallTop + 0.05f) * f0, y1 = wallTop - 0.05f + (ridge - wallTop + 0.05f) * f1;
                float t = 0.2f + 0.5f * f0 + 0.06f * (s & 1);
                k.flatQuad(new float[] {-w / 2 - over, y0, z0}, new float[] {w / 2 + over, y0, z0}, new float[] {w / 2 + over, y1, z1},
                        new float[] {-w / 2 - over, y1, z1}, t, t, t + 0.1f, t + 0.1f, sw[1], 0f, 1f, sgn);
            }
        }
        // gable triangles from log ends
        for (int sx = -1; sx <= 1; sx += 2) {
            k.flatTri(new float[] {sx * w / 2, wallTop, d / 2}, new float[] {sx * w / 2, wallTop, -d / 2}, new float[] {sx * w / 2, ridge - 0.1f, 0f},
                    0.4f, 0.4f, 0.55f, sw[0], sx, 0f, 0f);
        }
        // chimney
        CityProps.box(k, sw[1], w / 2 - 1.0f, wallTop, -d / 2 + 0.2f, w / 2 - 0.5f, ridge + 0.9f, -d / 2 + 0.7f, 0.1f, 0.5f, true);
        // door (front, +z) with a step; a porch roof and two posts in variant 1
        CityProps.vquad(k, sw[2], -0.4f, d / 2 + r + 0.01f, 0.4f, d / 2 + r + 0.01f, 0f, 1.7f, 0.2f, 0.7f, 0f, 1f);
        CityProps.hquad(k, sw[1], -0.7f, d / 2 + r, 0.7f, d / 2 + 0.7f, 0.12f, 0.3f);
        if (variant == 1) {
            float pz = d / 2 + 1.1f;
            for (int sx = -1; sx <= 1; sx += 2) CityProps.box(k, sw[0], sx * 1.5f - 0.07f, 0f, pz - 0.07f, sx * 1.5f + 0.07f, 2.1f, pz + 0.07f, 0.3f, 0.6f, true);
            k.flatQuad(new float[] {-1.8f, 2.1f, pz + 0.2f}, new float[] {1.8f, 2.1f, pz + 0.2f}, new float[] {1.8f, 2.5f, d / 2 - 0.1f},
                    new float[] {-1.8f, 2.5f, d / 2 - 0.1f}, 0.3f, 0.3f, 0.45f, 0.45f, sw[1], 0f, 1f, 1f);
        }
    }

    // ---- dock: a wooden jetty running out along +z ----

    static void dock(MeshKit k, int[] sw) {
        float len = 6.4f, wd = 0.8f, y = 0.38f;
        int planks = 16;
        for (int i = 0; i < planks; i++) {
            float z0 = -0.4f + len * i / planks, z1 = z0 + len / planks - 0.03f;
            float t = 0.45f + 0.35f * Noise.hash(i, 3, 17L);
            CityProps.hquad(k, sw[0], -wd, z0, wd, z1, y, t);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            CityProps.vquad(k, sw[0], sx * wd, -0.4f, sx * wd, len - 0.4f, y - 0.14f, y, 0.2f, 0.35f, sx, 0f);
            for (int i = 0; i <= 3; i++) {
                float z = 0.2f + (len - 1f) * i / 3f;
                CityProps.box(k, sw[0], sx * wd - 0.09f, -0.5f, z - 0.09f, sx * wd + 0.09f, y + 0.35f, z + 0.09f, 0.1f, 0.5f, true);
            }
        }
    }

    // ---- canoe: a slim lens hull (red) with two seats, running along z ----

    static void canoe(MeshKit k, int[] sw) {
        int rings = 7, sides = 8;
        float len = 4.2f, wd = 0.5f;
        float[][][] p = new float[rings][sides][3];
        float[][] t = new float[rings][sides];
        for (int r = 0; r < rings; r++) {
            float u = (float) r / (rings - 1), z = (u - 0.5f) * len;
            float scale = (float) Math.sqrt(Math.max(0f, 1f - (2f * u - 1f) * (2f * u - 1f)));
            scale = 0.12f + 0.88f * scale;
            float lift = 0.18f * (float) Math.pow(Math.abs(2f * u - 1f), 2.0);   // raised bow and stern
            for (int s = 0; s < sides; s++) {
                double a = 2.0 * Math.PI * s / sides;
                p[r][s] = new float[] {(float) Math.cos(a) * wd * scale, 0.22f + lift + (float) Math.sin(a) * 0.2f * scale, z};
                t[r][s] = 0.3f + 0.5f * (float) (0.5 + 0.5 * Math.sin(a));
            }
        }
        k.grid(p, t, sw[0], true);
        for (int sg = -1; sg <= 1; sg += 2) CityProps.box(k, sw[0], -0.34f, 0.3f, sg * 0.7f - 0.1f, 0.34f, 0.42f, sg * 0.7f + 0.1f, 0.2f, 0.9f, true);
    }

    // ---- fallen log ----

    static void log(MeshKit k, int variant, int[] sw, Random rnd) {
        float len = 3.2f + 1.2f * variant, r = 0.26f + 0.04f * variant;
        float[][] path = new float[4][];
        float[] rad = new float[4], tone = new float[4];
        for (int i = 0; i < 4; i++) {
            path[i] = new float[] {-len / 2 + len * i / 3f, r * 0.85f + 0.06f * (float) Math.sin(i * 1.4 + variant), 0.1f * (float) Math.sin(i * 2.0)};
            rad[i] = r * (1f - 0.1f * i);
            tone[i] = 0.35f + 0.1f * i;
        }
        k.tube(path, rad, tone, 8, 1f, sw[0], true);
        // a cut end with rings, and a branch stub
        float[] c = {path[0][0] - 0.01f, path[0][1], path[0][2]};
        for (int i = 0; i < 8; i++) {
            double a0 = 2.0 * Math.PI * i / 8, a1 = 2.0 * Math.PI * (i + 1) / 8;
            k.flatTri(c, new float[] {c[0], c[1] + (float) Math.cos(a0) * r, c[2] + (float) Math.sin(a0) * r},
                    new float[] {c[0], c[1] + (float) Math.cos(a1) * r, c[2] + (float) Math.sin(a1) * r}, 0.95f, 0.8f, 0.8f, sw[0], -1f, 0f, 0f);
        }
        k.tube(new float[][] {{0.2f, r * 1.4f, 0f}, {0.3f, r * 2.4f, 0.1f}}, new float[] {r * 0.35f, r * 0.2f}, new float[] {0.5f, 0.6f}, 6, 1f, sw[0], true);
    }

    // ---- fern: 8 to 12 arched fronds ----

    static void fern(MeshKit k, int[] sw, Random rnd) {
        int fronds = 11;
        for (int i = 0; i < fronds; i++) {
            float a = i * 2.4f + rnd.nextFloat() * 0.3f;
            float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
            float len = 0.7f + 0.35f * rnd.nextFloat(), up = 0.4f + 0.25f * rnd.nextFloat();
            float px = -dz, pz = dx;
            float[] prevL = null, prevR = null;
            int segs = 3;
            for (int s = 0; s <= segs; s++) {
                float f = (float) s / segs;
                float out = len * f, h = up * (float) Math.sin(f * 1.45f) * 1.0f + 0.04f - 0.12f * f * f;
                float wd = 0.09f * (1f - f * f) + 0.004f;
                float[] l = {dx * out + px * wd, Math.max(0.02f, h), dz * out + pz * wd};
                float[] r = {dx * out - px * wd, Math.max(0.02f, h), dz * out - pz * wd};
                if (prevL != null) {
                    float t0 = 0.2f + 0.7f * (f - 1f / segs), t1 = 0.2f + 0.7f * f;
                    k.flatQuad(prevL, prevR, r, l, t0, t0, t1, t1, sw[0], 0f, 1f, 0f);
                }
                prevL = l;
                prevR = r;
            }
        }
    }

    // ---- wildflowers: stems with heads (variant 0 purple, 1 yellow; ramps: purple, yellow, green stems) ----

    static void flowers(MeshKit k, int variant, int[] sw, Random rnd) {
        for (int i = 0; i < 6; i++) {
            float a = i * 1.1f + variant, d = 0.05f + 0.22f * rnd.nextFloat();
            float cx = (float) Math.cos(a) * d, cz = (float) Math.sin(a) * d;
            float h = 0.35f + 0.2f * rnd.nextFloat();
            float lean = 0.04f;
            k.flatTri(new float[] {cx - 0.012f, 0f, cz}, new float[] {cx + 0.012f, 0f, cz}, new float[] {cx + lean, h, cz}, 0.2f, 0.2f, 0.6f, sw[2], 0f, 0f, 1f);
            k.flatTri(new float[] {cx + 0.012f, 0f, cz}, new float[] {cx - 0.012f, 0f, cz}, new float[] {cx + lean, h, cz}, 0.2f, 0.2f, 0.6f, sw[2], 0f, 0f, -1f);
            float[] top = {cx + lean, h + 0.09f, cz};
            float hr = 0.065f;
            float[][] rim = {{cx + lean - hr, h, cz}, {cx + lean, h, cz + hr}, {cx + lean + hr, h, cz}, {cx + lean, h, cz - hr}};
            for (int q = 0; q < 4; q++) {
                float[] pa = rim[q], pb = rim[(q + 1) % 4];
                k.flatTri(pa, pb, top, 0.35f, 0.35f, 0.95f, sw[variant], (pa[0] + pb[0]) / 2 - cx, 0.5f, (pa[2] + pb[2]) / 2 - cz);
            }
        }
    }

    // ---- grass tuft: 6 blades, a triangle each side ----

    static void grass(MeshKit k, int[] sw, Random rnd) {
        for (int i = 0; i < 6; i++) {
            float a = i * 1.05f;
            float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
            float lean = 0.12f + 0.2f * rnd.nextFloat(), h = 0.38f + 0.3f * rnd.nextFloat();
            float px = -dz * 0.03f, pz = dx * 0.03f, ox = dx * 0.04f, oz = dz * 0.04f;
            float[] b0 = {ox + px, 0f, oz + pz}, b1 = {ox - px, 0f, oz - pz}, tip = {ox + dx * lean, h, oz + dz * lean};
            k.flatTri(b0, b1, tip, 0.15f, 0.15f, 1f, sw[0], dz, 0.2f, -dx);
            k.flatTri(b1, b0, tip, 0.15f, 0.15f, 1f, sw[0], -dz, 0.2f, dx);
        }
    }
}
