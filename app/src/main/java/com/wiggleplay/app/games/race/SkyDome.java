package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.Arrays;

/**
 * The sky of a theme (T-29R Part R1): a hemisphere round the camera whose colour runs from the horizon colour at 0 degrees to
 * the zenith colour at 90 degrees, with a sun disc and a two-ring glow that fades into the sky colour. Plain Java.
 *
 * <p>The colour of every vertex is a position on a ramp swatch ({@link Theme#skySwatch}); the renderer draws the dome first,
 * without fog, light or depth writes, at the camera's position. Radius 58 stays inside the far plane (60). All faces look to
 * the centre. Triangles: 32 segments x 9 bands x 2 + 32 for the cap = 608, plus 80 for the sun.
 */
public final class SkyDome {

    /** Radius of the dome and of the sun disc (a little inside, so the disc is never hidden by the dome). */
    public static final float RADIUS = 58f, SUN_RADIUS = 56.5f;
    /** Columns round the dome and rows from the horizon to the apex. */
    public static final int SEGMENTS = 32, ROWS = 10;
    /** Radius of the sun disc and of the two glow rings, in metres at the sun's distance. */
    public static final float DISC = 2.5f, GLOW1 = 6f, GLOW2 = 10f;
    /** Triangles of the dome and of the sun with its glow. */
    public static final int DOME_TRIANGLES = SEGMENTS * (ROWS - 1) * 2 + SEGMENTS, SUN_TRIANGLES = 16 + 32 + 32;

    private SkyDome() { }

    /**
     * Builds the sky.
     * @param theme a theme with a sky dome ({@link Theme#hasSky()})
     * @param pal the palette the theme was filled into (only the swatch uvs are used)
     * @return one part named "sky", single sided, in local space round the origin
     * @throws IllegalArgumentException if the theme has no sky dome
     */
    public static MeshData build(Theme theme, PaletteSwatches pal) {
        if (!theme.hasSky()) throw new IllegalArgumentException("theme " + theme.id + " has no sky dome");
        Builder b = new Builder();
        int ramp = theme.skySwatch(0), disc = theme.skySwatch(1), glow = theme.skySwatch(2);
        // dome: rows j = 0..ROWS-1 at elevation 90 j / ROWS degrees, then the apex
        float[][][] ring = new float[ROWS][SEGMENTS + 1][];
        float[] rowT = new float[ROWS];
        for (int j = 0; j < ROWS; j++) {
            double e = Math.PI / 2.0 * j / ROWS;
            rowT[j] = j / (float) ROWS;
            for (int i = 0; i <= SEGMENTS; i++) {
                double a = 2.0 * Math.PI * (i % SEGMENTS) / SEGMENTS;
                ring[j][i] = new float[] {(float) (Math.cos(e) * Math.sin(a)) * RADIUS, (float) Math.sin(e) * RADIUS,
                        (float) (Math.cos(e) * Math.cos(a)) * RADIUS};
            }
        }
        float[] apex = {0f, RADIUS, 0f};
        for (int j = 0; j < ROWS - 1; j++) {
            for (int i = 0; i < SEGMENTS; i++) {
                b.inward(ring[j][i], pal.uRamp(ramp, rowT[j]), ring[j][i + 1], pal.uRamp(ramp, rowT[j]),
                        ring[j + 1][i + 1], pal.uRamp(ramp, rowT[j + 1]), pal.v(ramp));
                b.inward(ring[j][i], pal.uRamp(ramp, rowT[j]), ring[j + 1][i + 1], pal.uRamp(ramp, rowT[j + 1]),
                        ring[j + 1][i], pal.uRamp(ramp, rowT[j + 1]), pal.v(ramp));
            }
        }
        for (int i = 0; i < SEGMENTS; i++) {
            b.inward(ring[ROWS - 1][i], pal.uRamp(ramp, rowT[ROWS - 1]), ring[ROWS - 1][i + 1], pal.uRamp(ramp, rowT[ROWS - 1]),
                    apex, pal.uRamp(ramp, 1f), pal.v(ramp));
        }
        // sun: a disc and two glow rings on a plane facing the centre, along -sunDir
        float sl = (float) Math.sqrt(theme.sunDir[0] * theme.sunDir[0] + theme.sunDir[1] * theme.sunDir[1]
                + theme.sunDir[2] * theme.sunDir[2]);
        float[] d = {-theme.sunDir[0] / sl, -theme.sunDir[1] / sl, -theme.sunDir[2] / sl};
        float[] centre = {d[0] * SUN_RADIUS, d[1] * SUN_RADIUS, d[2] * SUN_RADIUS};
        float[] right = cross(new float[] {0f, 1f, 0f}, d);
        float rl = (float) Math.sqrt(right[0] * right[0] + right[1] * right[1] + right[2] * right[2]);
        if (rl < 1e-4f) right = new float[] {1f, 0f, 0f};
        else right = new float[] {right[0] / rl, right[1] / rl, right[2] / rl};
        float[] up = cross(d, right);
        int n = 16;
        float[][] r0 = circle(centre, right, up, DISC, n), r1 = circle(centre, right, up, GLOW1, n),
                r2 = circle(centre, right, up, GLOW2, n);
        float discU = pal.u(disc), discV = pal.v(disc);
        for (int i = 0; i < n; i++) {
            b.inward(centre, discU, r0[i], discU, r0[i + 1], discU, discV);
        }
        float gv = pal.v(glow);
        for (int i = 0; i < n; i++) {
            b.inward(r0[i], pal.uRamp(glow, 0f), r1[i], pal.uRamp(glow, 0.5f), r1[i + 1], pal.uRamp(glow, 0.5f), gv);
            b.inward(r0[i], pal.uRamp(glow, 0f), r1[i + 1], pal.uRamp(glow, 0.5f), r0[i + 1], pal.uRamp(glow, 0f), gv);
            b.inward(r1[i], pal.uRamp(glow, 0.5f), r2[i], pal.uRamp(glow, 1f), r2[i + 1], pal.uRamp(glow, 1f), gv);
            b.inward(r1[i], pal.uRamp(glow, 0.5f), r2[i + 1], pal.uRamp(glow, 1f), r1[i + 1], pal.uRamp(glow, 0.5f), gv);
        }
        MeshData.Part part = new MeshData.Part("sky", -1, new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f, 1f},
                new float[] {1f, 1f, 1f}, Arrays.copyOf(b.v, b.vc * 8), Arrays.copyOf(b.ix, b.vc), b.vc);
        return new MeshData(false, new MeshData.Part[] {part});
    }

    /**
     * The direction to the sun disc's centre, for tests.
     * @param theme a theme
     * @return a unit vector
     */
    public static float[] sunDirection(Theme theme) {
        float sl = (float) Math.sqrt(theme.sunDir[0] * theme.sunDir[0] + theme.sunDir[1] * theme.sunDir[1]
                + theme.sunDir[2] * theme.sunDir[2]);
        return new float[] {-theme.sunDir[0] / sl, -theme.sunDir[1] / sl, -theme.sunDir[2] / sl};
    }

    private static float[][] circle(float[] c, float[] right, float[] up, float r, int n) {
        float[][] out = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            double a = 2.0 * Math.PI * (i % n) / n;
            float cx = (float) Math.cos(a) * r, cy = (float) Math.sin(a) * r;
            out[i] = new float[] {c[0] + right[0] * cx + up[0] * cy, c[1] + right[1] * cx + up[1] * cy,
                    c[2] + right[2] * cx + up[2] * cy};
        }
        return out;
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Collects triangles that look to the centre; the uv is (u, v) per vertex with one v for the triangle. */
    private static final class Builder {
        float[] v = new float[8 * 3 * 800];
        short[] ix = new short[3 * 800];
        int vc;

        void inward(float[] a, float ua, float[] b, float ub, float[] c, float uc, float tv) {
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            float wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            float mx = (a[0] + b[0] + c[0]) / 3f, my = (a[1] + b[1] + c[1]) / 3f, mz = (a[2] + b[2] + c[2]) / 3f;
            boolean flip = nx * mx + ny * my + nz * mz > 0f;   // facing away from the centre: swap two corners
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9f) return;
            float s = flip ? -1f / l : 1f / l;   // the stored normal points to the centre
            if ((vc + 3) * 8 > v.length) {
                v = Arrays.copyOf(v, v.length * 2);
                ix = Arrays.copyOf(ix, ix.length * 2);
            }
            float[][] p = flip ? new float[][] {a, c, b} : new float[][] {a, b, c};
            float[] us = flip ? new float[] {ua, uc, ub} : new float[] {ua, ub, uc};
            for (int k = 0; k < 3; k++) {
                int o = vc * 8;
                v[o] = p[k][0];
                v[o + 1] = p[k][1];
                v[o + 2] = p[k][2];
                v[o + 3] = nx * s;
                v[o + 4] = ny * s;
                v[o + 5] = nz * s;
                v[o + 6] = us[k];
                v[o + 7] = tv;
                ix[vc] = (short) vc;
                vc++;
            }
        }
    }
}
