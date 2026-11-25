package com.wiggleplay.app.games.race;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * The props of Star Road (T-30c), built with {@link MeshKit}: a habitat dome with an airlock and solar panels, a lattice antenna with a
 * dish, a six-wheel rover, a solar array, sharp boulders and moon rocks, crystal clusters and dust pebbles. Plain Java.
 *
 * <p>Local frame as in {@link CityProps}: the front (airlock, the rover's nose) faces +z, the base is at y = 0, no bottom faces. Rocks
 * are flat shaded (sharp facets); the dome is smooth. Ramps follow the kind's rule ({@link PropMeshes#RAMPS}).
 */
final class SpaceProps {

    private SpaceProps() { }

    // ---- habitat dome ----

    static void dome(MeshKit k, int variant, int[] sw, Random rnd) {
        float r = variant == 0 ? 3.2f : 3.8f;
        domeShell(k, 0f, 0f, r, sw);
        // airlock tube out of the front, with a dark door at its end
        float ay = 0.9f;
        k.tube(new float[][] {{0f, ay, r * 0.9f}, {0f, ay, r + 2f}}, new float[] {0.75f, 0.75f}, new float[] {0.5f, 0.5f}, 10, 1f, sw[0], false);
        float zEnd = r + 2.001f;
        float[] c = {0f, ay, zEnd};
        for (int i = 0; i < 10; i++) {
            double a0 = 2.0 * Math.PI * i / 10, a1 = 2.0 * Math.PI * (i + 1) / 10;
            k.flatTri(c, new float[] {(float) Math.cos(a0) * 0.6f, ay + (float) Math.sin(a0) * 0.6f, zEnd},
                    new float[] {(float) Math.cos(a1) * 0.6f, ay + (float) Math.sin(a1) * 0.6f, zEnd}, 0.3f, 0.3f, 0.3f, sw[1], 0f, 0f, 1f);
        }
        if (variant == 1) {   // a smaller dome joined by a tube
            float r2 = r * 0.55f, cx = r + r2 + 1.0f;
            k.tube(new float[][] {{r * 0.85f, 0.8f, 0f}, {cx - r2 * 0.85f, 0.8f, 0f}}, new float[] {0.5f, 0.5f}, new float[] {0.45f, 0.45f}, 8, 1f, sw[0], false);
            domeShell(k, cx, 0f, r2, sw);
        }
        // solar panels on legs behind the dome: one each side (variant 1 has the second dome on the right, so only the left one)
        panel(k, -(r + 1.9f), -r * 0.55f - 0.8f, 2.6f, 1.6f, 1.0f, sw[2], sw[0]);
        if (variant == 0) panel(k, r + 1.9f, -r * 0.55f - 0.8f, 2.6f, 1.6f, 1.0f, sw[2], sw[0]);
    }

    /** A 16 by 8 dome of radius r (squashed to 85%) at (cx, cz), with eight windows round it. */
    private static void domeShell(MeshKit k, float cx, float cz, float r, int[] sw) {
        int sides = 16, rings = 9;
        float[][][] p = new float[rings][sides][3];
        float[][] t = new float[rings][sides];
        for (int i = 0; i < rings; i++) {
            double el = Math.PI / 2 * i / (rings - 1);
            float rad = r * (float) Math.cos(el), y = r * 0.85f * (float) Math.sin(el);
            for (int s = 0; s < sides; s++) {
                double a = 2.0 * Math.PI * s / sides;
                p[i][s] = new float[] {cx + (float) Math.cos(a) * rad, y, cz + (float) Math.sin(a) * rad};
                t[i][s] = 0.25f + 0.7f * (float) i / (rings - 1) + ((s & 1) == 0 ? 0.04f : 0f);
            }
        }
        k.grid(p, t, sw[0], true);
        for (int s = 0; s < sides; s += 2) {
            double a0 = 2.0 * Math.PI * (s - 0.35) / sides, a1 = 2.0 * Math.PI * (s + 0.35) / sides;
            float[][] q = new float[4][];
            double[] el = {0.28, 0.28, 0.6, 0.6};
            double[] ang = {a0, a1, a1, a0};
            for (int i = 0; i < 4; i++) {
                float rad = r * (float) Math.cos(el[i]) * 1.012f, y = r * 0.85f * (float) Math.sin(el[i]);
                q[i] = new float[] {cx + (float) Math.cos(ang[i]) * rad, y, cz + (float) Math.sin(ang[i]) * rad};
            }
            float mx = (q[0][0] + q[2][0]) / 2f - cx, mz = (q[0][2] + q[2][2]) / 2f - cz;
            k.flatQuad(q[0], q[1], q[2], q[3], 0.2f, 0.2f, 0.9f, 0.9f, sw[1], mx, 0.3f, mz);
        }
    }

    /** One tilted panel centred at (x, z), w wide and d deep, on two short legs. */
    private static void panel(MeshKit k, float x, float z, float w, float d, float h, int swPanel, int swLeg) {
        float[] a = {x - w / 2, h, z - d / 2}, b = {x + w / 2, h, z - d / 2}, c = {x + w / 2, h + 0.9f, z + d / 2}, e = {x - w / 2, h + 0.9f, z + d / 2};
        float[] ma = {x, h, z - d / 2}, mc = {x, h + 0.9f, z + d / 2};
        k.flatQuad(a, ma, mc, e, 0.3f, 0.3f, 0.5f, 0.5f, swPanel, 0f, 1f, -0.9f);
        k.flatQuad(ma, b, c, mc, 0.7f, 0.7f, 0.9f, 0.9f, swPanel, 0f, 1f, -0.9f);
        k.flatQuad(a, b, c, e, 0.2f, 0.2f, 0.2f, 0.2f, swPanel, 0f, -1f, 0.9f);
        for (int s = -1; s <= 1; s += 2) {
            k.tube(new float[][] {{x + s * w * 0.3f, -0.1f, z}, {x + s * w * 0.3f, h + 0.45f, z}}, new float[] {0.06f, 0.05f},
                    new float[] {0.3f, 0.6f}, 4, 1f, swLeg, false);
        }
    }

    // ---- antenna ----

    static void antenna(MeshKit k, int variant, int[] sw, Random rnd) {
        float h = variant == 0 ? 5f : 7f;
        float[][] foot = new float[3][], top = new float[3][];
        for (int i = 0; i < 3; i++) {
            double a = 2.0 * Math.PI * i / 3 + 0.4;
            foot[i] = new float[] {(float) Math.cos(a) * 0.55f, -0.2f, (float) Math.sin(a) * 0.55f};
            top[i] = new float[] {(float) Math.cos(a) * 0.12f, h, (float) Math.sin(a) * 0.12f};
            k.tube(new float[][] {foot[i], top[i]}, new float[] {0.05f, 0.04f}, new float[] {0.2f, 0.8f}, 4, 1f, sw[0], false);
        }
        for (float f : new float[] {0.3f, 0.55f, 0.8f}) {
            for (int i = 0; i < 3; i++) {
                float[] p = lerp(foot[i], top[i], f), q = lerp(foot[(i + 1) % 3], top[(i + 1) % 3], f);
                k.tube(new float[][] {p, q}, new float[] {0.03f, 0.03f}, new float[] {0.4f, 0.5f}, 4, 1f, sw[0], false);
            }
        }
        if (variant == 0) {
            // a dish tilted up and forward, concave side to the sky, drawn from both sides
            float tilt = 0.6f;
            float[][] ring0 = new float[8][], ring1 = new float[8][];
            float[] apex = tilted(0f, 0f, 0f, tilt, h + 0.15f);
            for (int s = 0; s < 8; s++) {
                double a = 2.0 * Math.PI * s / 8;
                ring0[s] = tilted((float) Math.cos(a) * 0.5f, (float) Math.sin(a) * 0.5f, 0.22f, tilt, h + 0.15f);
                ring1[s] = tilted((float) Math.cos(a) * 1.0f, (float) Math.sin(a) * 1.0f, 0.5f, tilt, h + 0.15f);
            }
            float nx = 0f, ny = (float) Math.cos(tilt), nz = (float) Math.sin(tilt);
            for (int side = -1; side <= 1; side += 2) {
                for (int s = 0; s < 8; s++) {
                    int s2 = (s + 1) % 8;
                    k.flatTri(apex, ring0[s2], ring0[s], 0.3f, 0.6f, 0.6f, sw[0], side * nx, side * ny, side * nz);
                    k.flatQuad(ring0[s], ring0[s2], ring1[s2], ring1[s], 0.6f, 0.6f, 0.95f, 0.95f, sw[0], side * nx, side * ny, side * nz);
                }
            }
            k.tube(new float[][] {{0f, h - 0.1f, 0f}, {0f, h + 0.35f, 0f}}, new float[] {0.05f, 0.04f}, new float[] {0.3f, 0.3f}, 4, 1f, sw[0], false);
        } else {
            k.tube(new float[][] {{0f, h - 0.1f, 0f}, {0f, h + 0.7f, 0f}}, new float[] {0.05f, 0.03f}, new float[] {0.4f, 0.8f}, 4, 1f, sw[0], false);
        }
        // the red tip lamp
        k.tube(new float[][] {{0f, h + 0.5f, 0f}, {0f, h + 0.75f, 0f}}, new float[] {0.09f, 0.07f}, new float[] {0.3f, 0.95f}, 6, 1f, sw[1], true);
    }

    private static float[] lerp(float[] a, float[] b, float f) {
        return new float[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    /** A point of the dish (x, y across it, depth along its axis) with the axis tilted from vertical towards +z, at the mast top. */
    private static float[] tilted(float x, float y, float depth, float tilt, float top) {
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        return new float[] {x, top + depth * ct - y * st, depth * st + y * ct};
    }

    // ---- rover ----

    static void rover(MeshKit k, int variant, int[] sw, Random rnd) {
        float w = 0.85f, l = variant == 0 ? 1.3f : 1.5f;
        CityProps.box(k, sw[0], -w, 0.55f, -l, w, 1.05f, l, 0.2f, 0.7f, true);
        CityProps.box(k, sw[0], -w * 0.7f, 1.05f, -l * 0.6f, w * 0.7f, 1.45f, l * 0.35f, 0.4f, 0.9f, true);
        float zs = l * 0.85f;
        for (int row = -1; row <= 1; row++) {
            for (int s = -1; s <= 1; s += 2) wheel(k, sw[1], s * (w + 0.18f), 0.4f, row * zs, 0.4f);
        }
        k.tube(new float[][] {{0.3f, 1.4f, -l * 0.45f}, {0.3f, 2.1f, -l * 0.45f}, {0.3f, 2.3f, -l * 0.45f}}, new float[] {0.04f, 0.04f, 0.04f},
                new float[] {0.3f, 0.6f, 0.8f}, 6, 1f, sw[0], false);
        CityProps.box(k, sw[1], 0.18f, 2.3f, -l * 0.45f - 0.12f, 0.42f, 2.5f, -l * 0.45f + 0.12f, 0.3f, 0.7f, true);
        // a solar panel on the back
        k.flatQuad(new float[] {-w * 0.6f, 1.47f, -l * 0.95f}, new float[] {w * 0.6f, 1.47f, -l * 0.95f}, new float[] {w * 0.6f, 1.5f, -l * 0.5f},
                new float[] {-w * 0.6f, 1.5f, -l * 0.5f}, 0.9f, 0.9f, 0.95f, 0.95f, sw[0], 0f, 1f, 0f);
        if (variant == 1) CityProps.box(k, sw[0], -0.4f, 1.05f, l * 0.4f, 0.4f, 1.3f, l * 0.9f, 0.3f, 0.8f, true);
    }

    /** A wheel with its axis along x: a 10-sided tube with caps, centre (cx, cy, cz). */
    private static void wheel(MeshKit k, int sw, float cx, float cy, float cz, float r) {
        float hw = 0.16f;
        k.tube(new float[][] {{cx - hw, cy, cz}, {cx + hw, cy, cz}}, new float[] {r, r}, new float[] {0.4f, 0.4f}, 10, 1f, sw, false);
        for (int sd = -1; sd <= 1; sd += 2) {
            float[] c = {cx + sd * (hw + 0.001f), cy, cz};
            for (int i = 0; i < 10; i++) {
                double a0 = 2.0 * Math.PI * i / 10, a1 = 2.0 * Math.PI * (i + 1) / 10;
                k.flatTri(c, new float[] {c[0], cy + (float) Math.cos(a0) * r, cz + (float) Math.sin(a0) * r},
                        new float[] {c[0], cy + (float) Math.cos(a1) * r, cz + (float) Math.sin(a1) * r}, 0.9f, 0.7f, 0.7f, sw, sd, 0f, 0f);
            }
        }
    }

    // ---- solar array ----

    static void array(MeshKit k, int[] sw, Random rnd) {
        int n = 5;
        for (int i = 0; i < n; i++) {
            float x = (i - (n - 1) / 2f) * 1.75f;
            float h = 0.55f;
            float[] a = {x - 0.75f, h, -0.5f}, b = {x + 0.75f, h, -0.5f}, c = {x + 0.75f, h + 0.6f, 0.5f}, e = {x - 0.75f, h + 0.6f, 0.5f};
            float[] ma = {x, h, -0.5f}, mc = {x, h + 0.6f, 0.5f};
            k.flatQuad(a, ma, mc, e, 0.3f, 0.3f, 0.5f, 0.5f, sw[0], 0f, 1f, -0.9f);
            k.flatQuad(ma, b, c, mc, 0.7f, 0.7f, 0.9f, 0.9f, sw[0], 0f, 1f, -0.9f);
            k.flatQuad(a, b, c, e, 0.2f, 0.2f, 0.2f, 0.2f, sw[0], 0f, -1f, 0.9f);
            for (int s = -1; s <= 1; s += 2) {
                k.tube(new float[][] {{x + s * 0.45f, -0.1f, 0f}, {x + s * 0.45f, h + 0.3f, 0f}}, new float[] {0.05f, 0.04f}, new float[] {0.3f, 0.7f}, 4, 1f, sw[1], false);
            }
        }
    }

    // ---- sharp rocks ----

    /** Faces of an icosphere cut {@code subdiv} times (1: 80 faces, 2: 320), as {a, b, c} indices into the returned vertices (unit length). */
    private static int[][] icoFaces(int subdiv, ArrayList<float[]> verts) {
        for (float[] p : PropMeshes.ICO) verts.add(p.clone());
        int[][] faces = PropMeshes.ICO_F;
        for (int s = 0; s < subdiv; s++) {
            HashMap<Long, Integer> mid = new HashMap<>();
            int[][] next = new int[faces.length * 4][];
            int fc = 0;
            for (int[] f : faces) {
                int a = f[0], b = f[1], c = f[2];
                int ab = PropMeshes.midpoint(verts, mid, a, b), bc = PropMeshes.midpoint(verts, mid, b, c), ca = PropMeshes.midpoint(verts, mid, c, a);
                next[fc++] = new int[] {a, ab, ca};
                next[fc++] = new int[] {b, bc, ab};
                next[fc++] = new int[] {c, ca, bc};
                next[fc++] = new int[] {ab, bc, ca};
            }
            faces = next;
        }
        return faces;
    }

    /** One displaced, flat shaded rock: radii (rx, ry, rz), lighter on top, faces on the ground left out. */
    static void sharpRock(MeshKit k, float cx, float cz, float rx, float ry, float rz, int sw, long seed, int subdiv) {
        ArrayList<float[]> verts = new ArrayList<>();
        int[][] faces = icoFaces(subdiv, verts);
        int nv = verts.size();
        float[][] pos = new float[nv][3];
        for (int i = 0; i < nv; i++) {
            float[] p = verts.get(i);
            float disp = 0.7f + 0.6f * Noise.noise(p[0] * 2.3f + seed, p[2] * 2.3f + p[1] * 1.7f, seed * 3L + 1L);
            pos[i][0] = cx + p[0] * rx * disp;
            pos[i][1] = Math.max(0f, p[1] * ry * disp + ry * 0.5f);
            pos[i][2] = cz + p[2] * rz * disp;
        }
        for (int[] f : faces) {
            float[] a = pos[f[0]], b = pos[f[1]], c = pos[f[2]];
            if (a[1] < 0.03f && b[1] < 0.03f && c[1] < 0.03f) continue;
            if ((a[1] + b[1] + c[1]) / 3f < ry * 0.1f) continue;
            float ty = (a[1] + b[1] + c[1]) / (3f * ry * 1.5f);
            float jit = 0.2f * (Noise.hash((int) (a[0] * 37), (int) (a[2] * 37), seed) - 0.5f);
            float t = Math.max(0.05f, Math.min(1f, 0.2f + 0.65f * ty + jit));
            float hx = (a[0] + b[0] + c[0]) / 3f - cx, hy = (a[1] + b[1] + c[1]) / 3f - ry * 0.5f, hz = (a[2] + b[2] + c[2]) / 3f - cz;
            k.flatTri(a, b, c, t, t, t, sw, hx, hy, hz);
        }
    }

    static void bigBoulder(MeshKit k, int variant, int[] sw, Random rnd) {
        sharpRock(k, 0f, 0f, 1.9f + 0.3f * variant, 1.5f, 1.7f, sw[0], 11L + variant, 2);
    }

    static void moonRocks(MeshKit k, int variant, int[] sw, Random rnd) {
        int count = 3 + variant;
        for (int i = 0; i < count; i++) {
            float a = i * 6.28f / count + rnd.nextFloat() * 0.7f;
            float d = i == 0 ? 0f : 0.9f + 0.5f * rnd.nextFloat();
            float s = i == 0 ? 0.8f : 0.35f + 0.3f * rnd.nextFloat();
            sharpRock(k, (float) Math.cos(a) * d, (float) Math.sin(a) * d, s * 1.15f, s * 0.8f, s, sw[0], i * 13L + variant * 7L + rnd.nextInt(50), 1);
        }
    }

    // ---- crystals ----

    static void crystals(MeshKit k, int variant, int[] sw, Random rnd) {
        int count = 4 + (variant + 1) * 1 + (variant == 2 ? 1 : 0);
        for (int i = 0; i < count; i++) {
            float a = i * 2.4f + variant, d = i == 0 ? 0f : 0.15f + 0.4f * rnd.nextFloat();
            float cx = (float) Math.cos(a) * d, cz = (float) Math.sin(a) * d;
            float h = (i == 0 ? 1.4f : 0.6f + 0.6f * rnd.nextFloat());
            float r = 0.1f + 0.1f * h;
            float lx = (rnd.nextFloat() - 0.5f) * 0.5f, lz = (rnd.nextFloat() - 0.5f) * 0.5f;   // lean per metre of height
            int swatch = variant == 0 ? sw[0] : (variant == 1 ? sw[1] : sw[i & 1]);
            float[][] lo = new float[6][], hi = new float[6][];
            for (int s = 0; s < 6; s++) {
                double ang = 2.0 * Math.PI * s / 6;
                float ox = (float) Math.cos(ang) * r, oz = (float) Math.sin(ang) * r;
                lo[s] = new float[] {cx + ox, -0.1f, cz + oz};
                hi[s] = new float[] {cx + ox * 0.9f + lx * h * 0.7f, h * 0.7f, cz + oz * 0.9f + lz * h * 0.7f};
            }
            float[] tip = {cx + lx * h, h, cz + lz * h};
            float tone = 0.3f + 0.3f * rnd.nextFloat();
            for (int s = 0; s < 6; s++) {
                int s2 = (s + 1) % 6;
                float mx = (lo[s][0] + lo[s2][0]) / 2f - cx, mz = (lo[s][2] + lo[s2][2]) / 2f - cz;
                k.flatQuad(lo[s], lo[s2], hi[s2], hi[s], tone, tone, tone + 0.3f, tone + 0.3f, swatch, mx, 0f, mz);
                k.flatTri(hi[s], hi[s2], tip, 0.75f + 0.1f * (s & 1), 0.75f + 0.1f * (s & 1), 1f, swatch, mx, 0.6f, mz);
            }
        }
    }

    // ---- dust pebbles ----

    static void pebbles(MeshKit k, int[] sw, Random rnd) {
        for (int i = 0; i < 10; i++) {
            float a = i * 0.9f, d = 0.1f + 0.7f * rnd.nextFloat();
            float cx = (float) Math.cos(a) * d, cz = (float) Math.sin(a) * d;
            float r = 0.06f + 0.08f * rnd.nextFloat(), h = 0.04f + 0.05f * rnd.nextFloat();
            float tone = 0.25f + 0.5f * rnd.nextFloat();
            float[] apex = {cx, h, cz};
            for (int s = 0; s < 3; s++) {
                double a0 = a + 2.0 * Math.PI * s / 3, a1 = a + 2.0 * Math.PI * (s + 1) / 3;
                float[] p = {cx + (float) Math.cos(a0) * r, 0f, cz + (float) Math.sin(a0) * r};
                float[] q = {cx + (float) Math.cos(a1) * r, 0f, cz + (float) Math.sin(a1) * r};
                k.flatTri(p, q, apex, tone * 0.6f, tone * 0.6f, tone + 0.2f, sw[0], (p[0] + q[0]) / 2f - cx, 0.4f, (p[2] + q[2]) / 2f - cz);
            }
        }
    }
}
