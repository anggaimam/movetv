package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.Arrays;
import java.util.Random;

/**
 * The backdrop rings of a theme (T-29 Part A): silhouette bands round the camera that look like a painted horizon. Plain Java.
 *
 * <p>A band has {@code segments} columns, each one quad (two triangles) from y = {@link #BASE_Y} up to the column's height,
 * facing the centre only. Every normal points up, so the light is the same on every column (flat, like paint). A PEAKS ring
 * with an accent colour has a second row of quads for the snow cap (the top 22% of each peak). A STARS ring is not a band: it
 * is {@code segments} small quads placed on the ring, facing the centre.
 *
 * <p>The scene draws each ring as a dynamic item without fog, moved to the camera's x and z every frame (no turning), so the
 * backdrop does not swing with the camera.
 */
public final class Backdrop {

    /** Bottom of every band, below the ground. */
    public static final float BASE_Y = -2f;
    /** Horizontal strata of a MESAS ring that has an accent colour: levels from the base up to maxH, alternating body and accent. */
    public static final int STRATA = 6;
    /** Share of a peak's height where the snow cap starts. */
    public static final float CAP_START = 0.78f;
    /** Lowest top of a MESAS gap and of a PEAKS valley, as a share of minH. */
    public static final float MESA_GAP = 0.3f, PEAK_VALLEY = 0.25f;

    private Backdrop() { }

    /** The top height at the left and right edge of every column ("left" = the larger angle, as seen from the centre). */
    public static final class Profile {
        public final float[] left, right;

        Profile(float[] left, float[] right) {
            this.left = left;
            this.right = right;
        }
    }

    /**
     * The lowest top height a ring's profile can have.
     * @param r a band ring
     * @return minH, or less for MESAS (gaps) and PEAKS (valleys)
     */
    public static float lowestHeight(Theme.Ring r) {
        if (r.profile == Theme.MESAS) return MESA_GAP * r.minH;
        if (r.profile == Theme.PEAKS) return PEAK_VALLEY * r.minH;
        return r.minH;
    }

    /**
     * The heights of a band ring: deterministic by seed, closed round 360 degrees.
     * @param r a ring that is not STARS
     * @return the heights
     * @throws IllegalArgumentException for a STARS ring
     */
    public static Profile profile(Theme.Ring r) {
        int n = r.segments;
        Random rnd = new Random(r.seed);
        switch (r.profile) {
            case Theme.ROLLING:
                return fromVertices(rolling(r, rnd));
            case Theme.CRATERS: {
                float[] hv = rolling(r, rnd);
                int dips = 4 + rnd.nextInt(3);
                for (int d = 0; d < dips; d++) {
                    int centre = rnd.nextInt(n);
                    float radius = 3f + rnd.nextInt(3);
                    for (int k = -5; k <= 5; k++) {
                        float u = Math.abs(k) / radius;
                        if (u >= 1f) continue;
                        int i = ((centre + k) % n + n) % n;
                        float bump = 0.5f * (1f + (float) Math.cos(Math.PI * u));
                        hv[i] = hv[i] - (hv[i] - r.minH) * bump;
                    }
                }
                return fromVertices(hv);
            }
            case Theme.PEAKS:
                return fromVertices(peaks(r, rnd));
            case Theme.MESAS:
                return mesas(r, rnd);
            case Theme.SKYLINE:
                return skyline(r, rnd);
            default:
                throw new IllegalArgumentException("not a band ring: " + r.profile);
        }
    }

    private static Profile fromVertices(float[] hv) {
        int n = hv.length;
        float[] l = new float[n], rr = new float[n];
        for (int c = 0; c < n; c++) {
            l[c] = hv[c];
            rr[c] = hv[(c + 1) % n];
        }
        return new Profile(l, rr);
    }

    /** Three sines with periods 360, 120 and 45 degrees and random phases, scaled to minH..maxH. */
    private static float[] rolling(Theme.Ring r, Random rnd) {
        int n = r.segments;
        double p1 = rnd.nextDouble() * 2 * Math.PI, p2 = rnd.nextDouble() * 2 * Math.PI, p3 = rnd.nextDouble() * 2 * Math.PI;
        float[] hv = new float[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double s = Math.sin(a + p1) + 0.5 * Math.sin(3 * a + p2) + 0.25 * Math.sin(8 * a + p3);
            double t = (s / 1.75 + 1.0) / 2.0;
            hv[i] = (float) (r.minH + t * (r.maxH - r.minH));
        }
        return hv;
    }

    /** Triangles 4 to 9 columns wide, an apex between minH and maxH, valleys at 0.25 x minH. */
    private static float[] peaks(Theme.Ring r, Random rnd) {
        int n = r.segments;
        int np = Math.max(2, Math.round(n / 6.5f));
        int[] cut = new int[np + 1];
        cut[np] = n;
        for (int k = 1; k < np; k++) {
            float jitter = (rnd.nextFloat() * 2f - 1f) * 1.2f;
            int c = Math.round(k * (float) n / np + jitter);
            cut[k] = Math.max(cut[k - 1] + 4, Math.min(c, n - 4 * (np - k)));
        }
        float valley = PEAK_VALLEY * r.minH;
        float[] hv = new float[n];
        for (int k = 0; k < np; k++) {
            int a = cut[k], b = cut[k + 1], w = b - a;
            int apexAt = w / 2;
            float apex = r.minH + rnd.nextFloat() * (r.maxH - r.minH);
            for (int j = 0; j < w; j++) {
                float t = j <= apexAt ? (float) j / apexAt : (float) (w - j) / (w - apexAt);
                hv[a + j] = valley + (apex - valley) * t;
            }
        }
        return hv;
    }

    /** Flat-topped plateaus 3 to 8 columns wide between minH and maxH, one steep column on each side, low gaps 2 to 6 wide. */
    private static Profile mesas(Theme.Ring r, Random rnd) {
        int n = r.segments;
        float low = MESA_GAP * r.minH;
        float[] l = new float[n], rr = new float[n];
        int pos = 0;
        float h = r.minH + rnd.nextFloat() * (r.maxH - r.minH);
        while (pos < n) {
            int w = 3 + rnd.nextInt(6);
            for (int i = 0; i < w && pos < n; i++, pos++) l[pos] = rr[pos] = h;
            if (pos < n) {
                l[pos] = h;
                rr[pos] = low;
                pos++;
            }
            int g = 2 + rnd.nextInt(5);
            for (int i = 0; i < g && pos < n; i++, pos++) l[pos] = rr[pos] = low;
            float next = r.minH + rnd.nextFloat() * (r.maxH - r.minH);
            if (pos < n) {
                l[pos] = low;
                rr[pos] = next;
                pos++;
            }
            h = next;
        }
        return new Profile(l, rr);
    }

    /** Blocks 1 to 3 columns wide, heights stepped in 1.5 m units between minH and maxH, no gaps. */
    private static Profile skyline(Theme.Ring r, Random rnd) {
        int n = r.segments;
        int steps = (int) Math.floor((r.maxH - r.minH) / 1.5f) + 1;
        float[] l = new float[n], rr = new float[n];
        int pos = 0;
        while (pos < n) {
            int w = 1 + rnd.nextInt(3);
            float h = r.minH + 1.5f * rnd.nextInt(steps);
            for (int i = 0; i < w && pos < n; i++, pos++) l[pos] = rr[pos] = h;
        }
        return new Profile(l, rr);
    }

    /**
     * Number of triangles of a ring's mesh.
     * @param r the ring
     * @return STARS: 2 per star; PEAKS with an accent: 4 per column; others: 2 per column
     */
    public static int triangleCount(Theme.Ring r) {
        if (r.profile == Theme.STARS) return 2 * r.segments;
        if (r.profile == Theme.MESAS && r.accent != 0) {
            Profile p = profile(r);
            int quads = 0;
            for (int c = 0; c < r.segments; c++) {
                for (int k = 0; k < STRATA; k++) if (strataQuad(r, p, c, k) != null) quads++;
            }
            return 2 * quads;
        }
        return (r.profile == Theme.PEAKS && r.accent != 0 ? 4 : 2) * r.segments;
    }

    /**
     * Builds the mesh of ring {@code ringIndex} of a theme, centred on the origin (the scene moves it to the camera). One part,
     * named "ring", single sided, every normal (0, 1, 0), every quad coloured from one swatch.
     * @param th the theme
     * @param ringIndex index into {@code th.rings}
     * @param pal the palette (only its swatch uvs are used)
     * @return the mesh
     */
    public static MeshData build(Theme th, int ringIndex, PaletteSwatches pal) {
        Theme.Ring r = th.rings[ringIndex];
        Buf b = new Buf();
        if (r.profile == Theme.STARS) {
            stars(r, th, ringIndex, pal, b);
        } else {
            band(r, th, ringIndex, pal, b);
        }
        MeshData.Part part = new MeshData.Part("ring", -1, new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f, 1f},
                new float[] {1f, 1f, 1f}, Arrays.copyOf(b.v, b.vc * 8), Arrays.copyOf(b.ix, b.ic), b.vc);
        return new MeshData(false, new MeshData.Part[] {part});
    }

    private static void band(Theme.Ring r, Theme th, int ringIndex, PaletteSwatches pal, Buf b) {
        Profile p = profile(r);
        int n = r.segments;
        boolean cap = r.profile == Theme.PEAKS && r.accent != 0;
        if (r.profile == Theme.MESAS && r.accent != 0) {
            float uA = pal.u(th.ringSwatch(ringIndex)), vA = pal.v(th.ringSwatch(ringIndex));
            float uB = pal.u(th.ringAccentSwatch(ringIndex)), vB = pal.v(th.ringAccentSwatch(ringIndex));
            for (int c = 0; c < n; c++) {
                float a0 = (float) (2 * Math.PI * c / n), a1 = (float) (2 * Math.PI * (c + 1) / n);
                for (int k = 0; k < STRATA; k++) {
                    float[] q = strataQuad(r, p, c, k);
                    if (q == null) continue;
                    quad(b, r.radius, a0, a1, q[0], q[1], q[2], q[3], (k & 1) == 0 ? uA : uB, (k & 1) == 0 ? vA : vB);
                }
            }
            return;
        }
        float u0 = pal.u(th.ringSwatch(ringIndex)), v0 = pal.v(th.ringSwatch(ringIndex));
        float u1 = pal.u(th.ringAccentSwatch(ringIndex)), v1 = pal.v(th.ringAccentSwatch(ringIndex));
        for (int c = 0; c < n; c++) {
            float a0 = (float) (2 * Math.PI * c / n), a1 = (float) (2 * Math.PI * (c + 1) / n);
            float hl = p.left[c], hr = p.right[c];
            if (cap) {
                quad(b, r.radius, a0, a1, BASE_Y, BASE_Y, CAP_START * hl, CAP_START * hr, u0, v0);
                quad(b, r.radius, a0, a1, CAP_START * hl, CAP_START * hr, hl, hr, u1, v1);
            } else {
                quad(b, r.radius, a0, a1, BASE_Y, BASE_Y, hl, hr, u0, v0);
            }
        }
    }

    /** The clipped heights {bottom0, bottom1, top0, top1} of stratum k of column c, or null when the column does not reach it. */
    private static float[] strataQuad(Theme.Ring r, Profile p, int c, int k) {
        float unit = (r.maxH - BASE_Y) / STRATA;
        float lo = BASE_Y + unit * k, hi = lo + unit;
        float hl = p.left[c], hr = p.right[c];
        float bl = Math.min(lo, hl), tl = Math.min(hi, hl), br = Math.min(lo, hr), tr = Math.min(hi, hr);
        if (tl <= bl + 1e-4f && tr <= br + 1e-4f) return null;
        return new float[] {bl, br, tl, tr};
    }

    /**
     * One column quad seen from the centre: edge 0 at angle a0 (the right edge), edge 1 at a1 (the left edge). Counter clockwise
     * from the centre: (bottom1, bottom0, top0) and (bottom1, top0, top1).
     */
    private static void quad(Buf b, float radius, float a0, float a1, float bot0, float bot1, float top0, float top1, float u, float v) {
        float s0 = (float) Math.sin(a0) * radius, c0 = (float) Math.cos(a0) * radius;
        float s1 = (float) Math.sin(a1) * radius, c1 = (float) Math.cos(a1) * radius;
        int vb0 = b.vert(s0, bot0, c0, u, v);   // bottom at edge 0 (right)
        int vb1 = b.vert(s1, bot1, c1, u, v);   // bottom at edge 1 (left)
        int vt0 = b.vert(s0, top0, c0, u, v);
        int vt1 = b.vert(s1, top1, c1, u, v);
        b.tri(vb1, vb0, vt0);
        b.tri(vb1, vt0, vt1);
    }

    private static void stars(Theme.Ring r, Theme th, int ringIndex, PaletteSwatches pal, Buf b) {
        Random rnd = new Random(r.seed);
        int[] sw = {Theme.SW_WHITE, th.ringAccentSwatch(ringIndex), Theme.SW_PALE_BLUE};
        for (int i = 0; i < r.segments; i++) {
            float a = (float) (rnd.nextDouble() * 2 * Math.PI);
            float h = 2f + rnd.nextFloat() * 38f;
            float side = 0.25f + rnd.nextFloat() * 0.25f;
            int swatch = sw[rnd.nextInt(3)];
            float u = pal.u(swatch), v = pal.v(swatch);
            float cx = (float) Math.sin(a) * r.radius, cz = (float) Math.cos(a) * r.radius;
            float tx = (float) Math.cos(a) * side / 2f, tz = -(float) Math.sin(a) * side / 2f, hs = side / 2f;
            int lb = b.vert(cx + tx, h - hs, cz + tz, u, v);
            int rb = b.vert(cx - tx, h - hs, cz - tz, u, v);
            int rt = b.vert(cx - tx, h + hs, cz - tz, u, v);
            int lt = b.vert(cx + tx, h + hs, cz + tz, u, v);
            b.tri(lb, rb, rt);
            b.tri(lb, rt, lt);
        }
    }

    /** Growing vertex and index arrays (every vertex normal is (0, 1, 0)). */
    private static final class Buf {
        float[] v = new float[8 * 256];
        short[] ix = new short[6 * 128];
        int vc, ic;

        int vert(float x, float y, float z, float u, float tv) {
            if ((vc + 1) * 8 > v.length) v = Arrays.copyOf(v, v.length * 2);
            int o = vc * 8;
            v[o] = x;
            v[o + 1] = y;
            v[o + 2] = z;
            v[o + 3] = 0f;
            v[o + 4] = 1f;
            v[o + 5] = 0f;
            v[o + 6] = u;
            v[o + 7] = tv;
            return vc++;
        }

        void tri(int a, int b, int c) {
            if (ic + 3 > ix.length) ix = Arrays.copyOf(ix, ix.length * 2);
            ix[ic++] = (short) a;
            ix[ic++] = (short) b;
            ix[ic++] = (short) c;
        }
    }
}
