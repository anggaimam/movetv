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
            case Theme.RIDGE:
                return ridge(r, rnd);
            case Theme.CRATER_RIM:
                return fromVertices(craterRim(r, rnd));
            case Theme.HIGHLANDS:
                return highlands(r, rnd);
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
                // v2 (T-30b): jagged flanks, no jitter at the valley and the apex
                float jag = (rnd.nextFloat() - 0.5f) * 0.2f * (apex - valley) * Math.min(1f, 3f * t) * Math.min(1f, 4f * (1f - t));
                hv[a + j] = Math.max(valley, valley + (apex - valley) * t + jag);
            }
        }
        return hv;
    }

    /**
     * CRATER_RIM v2 (T-30c): low uneven rims. 10 to 14 humps 6 to 16 columns wide whose flanks fall off steeply, between minH and
     * maxH, with a little jitter; the flat between them stays at minH.
     */
    private static float[] craterRim(Theme.Ring r, Random rnd) {
        int n = r.segments;
        float[] hv = new float[n];
        Arrays.fill(hv, r.minH);
        int humps = 10 + rnd.nextInt(5);
        for (int q = 0; q < humps; q++) {
            int centre = rnd.nextInt(n), w = 6 + rnd.nextInt(11);
            float h = r.minH + (r.maxH - r.minH) * (0.35f + 0.65f * rnd.nextFloat());
            for (int k = -w; k <= w; k++) {
                float u = (float) Math.abs(k) / w;
                int i = ((centre + k) % n + n) % n;
                hv[i] = Math.max(hv[i], r.minH + (h - r.minH) * (float) Math.pow(1f - u, 0.8));
            }
        }
        for (int i = 0; i < n; i++) hv[i] = Math.max(r.minH, Math.min(r.maxH, hv[i] + (rnd.nextFloat() - 0.5f) * 0.25f * (hv[i] > r.minH + 0.01f ? 1f : 0f)));
        return hv;
    }

    /**
     * HIGHLANDS (T-30c): jagged stepped terraces. Blocks 3 to 8 columns wide at one of four terrace levels between minH and maxH, each
     * column a little uneven (0.6 m) and never outside the range.
     */
    private static Profile highlands(Theme.Ring r, Random rnd) {
        int n = r.segments;
        float[] l = new float[n], rr = new float[n];
        int pos = 0;
        while (pos < n) {
            int w = 3 + rnd.nextInt(6);
            float h = r.minH + (r.maxH - r.minH) * rnd.nextInt(4) / 3f;
            for (int i = 0; i < w && pos < n; i++, pos++) {
                float hh = Math.max(r.minH, Math.min(r.maxH, h + (rnd.nextFloat() - 0.5f) * 0.6f));
                l[pos] = rr[pos] = hh;
            }
        }
        return new Profile(l, rr);
    }

    /**
     * RIDGE (T-30b): forested ridges. A slow base between minH and maxH minus the tooth height, and a serrated top: every pair of
     * columns is one tooth (rising, then falling) 0.5 to 1.3 m tall, so the edge reads as a line of trees.
     */
    private static Profile ridge(Theme.Ring r, Random rnd) {
        int n = r.segments;
        float[] base = rolling(r, rnd);
        float[] l = new float[n], rr = new float[n];
        float tooth = Math.min(1.3f, Math.max(0f, r.maxH - r.minH) * 0.2f);
        for (int c = 0; c < n; c += 2) {
            float lo = Math.max(r.minH, base[c] - tooth), up = Math.min(r.maxH, lo + 0.5f * tooth + tooth * 0.5f * rnd.nextFloat() * 2f);
            float next = base[(c + 2) % n];
            l[c] = lo;
            rr[c] = up;
            if (c + 1 < n) {
                l[c + 1] = up;
                rr[c + 1] = Math.max(r.minH, Math.min(r.maxH, next - tooth));
            }
        }
        // close the circle: the last right edge meets the first left edge
        rr[n - 1] = l[0];
        return new Profile(l, rr);
    }

    /**
     * Flat-topped plateaus 3 to 12 columns wide between minH and maxH, with stepped tops (an outer column one step lower), now and
     * then a narrow spire, a lower shoulder before the cliff, one steep column on each side and low gaps 2 to 6 wide.
     */
    private static Profile mesas(Theme.Ring r, Random rnd) {
        int n = r.segments;
        float low = MESA_GAP * r.minH;
        float[] l = new float[n], rr = new float[n];
        int pos = 0;
        float h = r.minH + rnd.nextFloat() * (r.maxH - r.minH);
        while (pos < n) {
            int w = 3 + rnd.nextInt(10);
            int spire = rnd.nextInt(10) < 4 && w >= 4 ? 1 + rnd.nextInt(w - 2) : -1;
            boolean stepL = rnd.nextBoolean(), stepR = rnd.nextBoolean();
            for (int i = 0; i < w && pos < n; i++, pos++) {
                float hh = h;
                if (i == spire || (i == spire + 1 && w >= 8 && spire >= 0 && rnd.nextBoolean())) hh = Math.min(r.maxH, h * 1.22f);
                else if ((i == 0 && stepL) || (i == w - 1 && stepR)) hh = h * 0.84f;
                l[pos] = rr[pos] = hh;
            }
            if (pos < n) {   // a lower shoulder, then the cliff
                l[pos] = rr[pos] = Math.max(low, h * 0.7f);
                pos++;
            }
            if (pos < n) {
                l[pos] = Math.max(low, h * 0.7f);
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

    /**
     * SKYLINE v2 (T-30a): blocks 1 to 4 columns wide whose heights step in 1.5 m units between minH and maxH, each with an uneven top
     * (a lower outer column, now and then a second setback) and, in some blocks, a thin spire up to 1.1 times the block height (never
     * above maxH). No column goes below minH.
     */
    private static Profile skyline(Theme.Ring r, Random rnd) {
        int n = r.segments;
        int steps = (int) Math.floor((r.maxH - r.minH) / 1.5f) + 1;
        float[] l = new float[n], rr = new float[n];
        int pos = 0;
        while (pos < n) {
            int w = 1 + rnd.nextInt(4);
            float h = r.minH + 1.5f * rnd.nextInt(steps);
            float[] f = new float[w];
            java.util.Arrays.fill(f, 1f);
            if (w == 2) {
                f[rnd.nextBoolean() ? 0 : 1] = 0.86f;
            } else if (w == 3) {
                f[0] = 0.82f;
                if (rnd.nextBoolean()) f[2] = 0.82f;
            } else if (w == 4) {
                f[0] = 0.8f;
                f[3] = rnd.nextBoolean() ? 0.8f : 0.9f;
                if (rnd.nextBoolean()) f[1] = 0.9f;
            }
            if (w >= 2 && rnd.nextInt(8) == 0) f[w / 2] = 1.1f;   // a spire
            for (int i = 0; i < w && pos < n; i++, pos++) {
                float hh = Math.max(r.minH, Math.min(r.maxH, h * f[i]));
                l[pos] = rr[pos] = hh;
            }
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
        return ((r.profile == Theme.PEAKS || r.profile == Theme.CRATER_RIM) && r.accent != 0 ? 4 : 2) * r.segments;
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
        return build(th, ringIndex, pal, false);
    }

    /**
     * Builds a ring; on LOW a STARS ring keeps {@code min(120, segments)} stars (T-30c). A STARS ring of a theme with four extra ramps
     * also holds the two planets.
     * @param low true for LOW quality
     */
    public static MeshData build(Theme th, int ringIndex, PaletteSwatches pal, boolean low) {
        Theme.Ring r = th.rings[ringIndex];
        Buf b = new Buf();
        if (r.profile == Theme.STARS) {
            stars(r, th, ringIndex, pal, b, low ? Math.min(120, r.segments) : r.segments);
            if (th.extraCount() >= 4) planets(th, pal, b);
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
        boolean cap = (r.profile == Theme.PEAKS || r.profile == Theme.CRATER_RIM) && r.accent != 0;
        if (r.profile == Theme.MESAS && r.accent != 0) {
            float uA = pal.u(th.ringSwatch(ringIndex)), vA = pal.v(th.ringSwatch(ringIndex));
            float uB = pal.u(th.ringAccentSwatch(ringIndex)), vB = pal.v(th.ringAccentSwatch(ringIndex));
            for (int c = 0; c < n; c++) {
                float a0 = (float) (2 * Math.PI * c / n), a1 = (float) (2 * Math.PI * (c + 1) / n);
                for (int k = 0; k < STRATA; k++) {
                    float[] q = strataQuad(r, p, c, k);
                    if (q == null) continue;
                    boolean body = ((k & 1) == 0) != streak(r, c, k);
                    quad(b, r.radius, a0, a1, q[0], q[1], q[2], q[3], body ? uA : uB, body ? vA : vB);
                }
            }
            return;
        }
        float u0 = pal.u(th.ringSwatch(ringIndex)), v0 = pal.v(th.ringSwatch(ringIndex));
        float u1 = pal.u(th.ringAccentSwatch(ringIndex)), v1 = pal.v(th.ringAccentSwatch(ringIndex));
        if (r.profile == Theme.SKYLINE && r.accent != 0) {
            // vertical window streaks: every second column of some groups of four takes the light colour
            for (int c = 0; c < n; c++) {
                float a0 = (float) (2 * Math.PI * c / n), a1 = (float) (2 * Math.PI * (c + 1) / n);
                boolean light = skylineStreak(r, c);
                quad(b, r.radius, a0, a1, BASE_Y, BASE_Y, p.left[c], p.right[c], light ? u1 : u0, light ? v1 : v0);
            }
            return;
        }
        for (int c = 0; c < n; c++) {
            float a0 = (float) (2 * Math.PI * c / n), a1 = (float) (2 * Math.PI * (c + 1) / n);
            float hl = p.left[c], hr = p.right[c];
            if (cap) {
                // v2: an uneven snow line (wobbling by up to 3 m) and gullies (a column swaps rock and snow colours)
                boolean peaks = r.profile == Theme.PEAKS;
                float sl = peaks ? snowLine(r, c, hl) : 0.82f * hl, sr = peaks ? snowLine(r, c + 1, hr) : 0.82f * hr;   // CRATER_RIM: lit edge on top
                boolean gully = peaks && gully(r, c);
                quad(b, r.radius, a0, a1, BASE_Y, BASE_Y, sl, sr, gully ? u1 : u0, gully ? v1 : v0);
                quad(b, r.radius, a0, a1, sl, sr, hl, hr, gully ? u0 : u1, gully ? v0 : v1);
            } else if ((r.profile == Theme.ROLLING || r.profile == Theme.HIGHLANDS) && r.accent != 0) {
                boolean streak = Noise.hash(c / 2, 5, r.seed) < 0.2f;   // vertical rock streaks
                quad(b, r.radius, a0, a1, BASE_Y, BASE_Y, hl, hr, streak ? u1 : u0, streak ? v1 : v0);
            } else {
                quad(b, r.radius, a0, a1, BASE_Y, BASE_Y, hl, hr, u0, v0);
            }
        }
    }

    /** Height of the snow line at ring vertex i of a peak of top height h: 78% of h, wobbling by up to 3 m. */
    static float snowLine(Theme.Ring r, int i, float h) {
        float wob = (Noise.fbm(i * 0.23f + 3f, 8.1f, 2, r.seed) - 0.5f) * 6f;
        return Math.max(0.4f * h, Math.min(0.95f * h, CAP_START * h + wob));
    }

    /** Whether column c of a snowy PEAKS ring is a gully (2 columns wide). */
    static boolean gully(Theme.Ring r, int c) {
        return Noise.hash(c / 2, 7, r.seed) < 0.22f;
    }

    /** Whether column c of a SKYLINE ring with an accent takes the light colour (groups of 4 columns, every second one). */
    static boolean skylineStreak(Theme.Ring r, int c) {
        return (c & 1) == 0 && Noise.hash(c / 4, 11, r.seed) < (r.radius > 55.5f ? 0.3f : 0.6f);
    }

    /** The boundaries between the strata of a ring, from BASE_Y up: uneven thicknesses (0.6 to 1.7 shares), seeded by the ring. */
    private static float[] bounds(Theme.Ring r) {
        Random rnd = new Random(r.seed * 31L + 7L);
        float[] w = new float[STRATA];
        float sum = 0f;
        for (int k = 0; k < STRATA; k++) {
            w[k] = 0.6f + 1.1f * rnd.nextFloat();
            sum += w[k];
        }
        float total = r.maxH - BASE_Y;
        float[] bd = new float[STRATA + 1];
        for (int k = 0; k < STRATA; k++) bd[k + 1] = bd[k] + total * w[k] / sum;
        for (int k = 0; k <= STRATA; k++) bd[k] += BASE_Y;
        bd[STRATA] = r.maxH + 50f;   // the top stratum has no ceiling: the column height clips it
        return bd;
    }

    /** Boundary k at ring vertex i: its level plus a slow wobble that never crosses the neighbouring boundaries. */
    private static float boundary(Theme.Ring r, float[] bd, int k, int i) {
        if (k == 0 || k == STRATA) return bd[k];
        float room = 0.35f * Math.min(bd[k] - bd[k - 1], k + 1 == STRATA ? 1e9f : bd[k + 1] - bd[k]);
        float t = Noise.fbm(i * 0.17f + 31f * k, 5.3f * k, 2, r.seed);
        return bd[k] + (t - 0.5f) * 2f * room;
    }

    /** The clipped heights {bottom0, bottom1, top0, top1} of stratum k of column c, or null when the column does not reach it. */
    private static float[] strataQuad(Theme.Ring r, Profile p, int c, int k) {
        float[] bd = bounds(r);
        int n = r.segments, c1 = (c + 1) % n;
        float h0 = p.left[c], h1 = p.right[c];
        float b0 = Math.min(boundary(r, bd, k, c), h0), t0 = Math.min(boundary(r, bd, k + 1, c), h0);
        float b1 = Math.min(boundary(r, bd, k, c1), h1), t1 = Math.min(boundary(r, bd, k + 1, c1), h1);
        if (t0 <= b0 + 1e-4f && t1 <= b1 + 1e-4f) return null;
        return new float[] {b0, b1, t0, t1};
    }

    /** Whether stratum k of column c takes the other colour: short vertical streaks (1 to 2 columns) of weathering. */
    private static boolean streak(Theme.Ring r, int c, int k) {
        return Noise.hash(c / 2, k, r.seed) < 0.16f;
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

    private static void stars(Theme.Ring r, Theme th, int ringIndex, PaletteSwatches pal, Buf b, int count) {
        Random rnd = new Random(r.seed);
        int[] sw = {Theme.SW_WHITE, th.ringAccentSwatch(ringIndex), Theme.SW_PALE_BLUE};
        for (int i = 0; i < count; i++) {
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

    /** Distance of the planets' centres from the camera (inside the far plane of 60), and their sizes: azimuth degrees, height, radius. */
    public static final float PLANET_DIST = 56f;
    static final float[][] PLANETS = {{40f, 24f, 7f}, {200f, 18f, 4f}};

    /**
     * The two planets (T-30c): a ringed one (azimuth 40 degrees, centre height 24 m, radius 7 m) and a blue one with green blobs
     * (azimuth 200 degrees, height 18 m, radius 4 m). Discs facing the camera at {@link #PLANET_DIST}, shaded across by their ramp;
     * the ring's near half is drawn 0.3 m in front of the disc and its far half 0.3 m behind it.
     */
    private static void planets(Theme th, PaletteSwatches pal, Buf b) {
        planetDisc(b, pal, PLANETS[0], th.extraSwatch(0), 0f, 24);
        // ring: an ellipse 1.35 to 1.95 radii wide, tilted 0.35 rad
        float az = (float) Math.toRadians(PLANETS[0][0]), h = PLANETS[0][1], rad = PLANETS[0][2];
        float[] f = frame(az, h);
        int segs = 32;
        int[][] in = new int[segs][2], out = new int[segs][2];
        float tilt = 0.35f, ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        for (int s = 0; s < segs; s++) {
            double a = 2.0 * Math.PI * s / segs;
            for (int q = 0; q < 2; q++) {
                float rr = rad * (q == 0 ? 1.35f : 1.95f);
                float ex = (float) Math.cos(a) * rr, ey = (float) Math.sin(a) * rr * 0.32f;
                float rx = ex * ct - ey * st, ry = ex * st + ey * ct;
                float depth = ry < 0f ? -0.3f : 0.3f;   // the near half is in front
                float t = 0.35f + 0.55f * (0.5f + 0.5f * (float) Math.cos(a - 0.8));
                int[] idx = q == 0 ? in[s] : out[s];
                float cx = f[0] + f[3] * rx + f[6] * ry + f[9] * depth, cy = f[1] + f[4] * rx + f[7] * ry + f[10] * depth,
                        cz = f[2] + f[5] * rx + f[8] * ry + f[11] * depth;
                idx[0] = b.vert(cx, cy, cz, pal.uRamp(th.extraSwatch(1), t), pal.v(th.extraSwatch(1)));
            }
        }
        for (int s = 0; s < segs; s++) {
            int s2 = (s + 1) % segs;
            facing(b, in[s][0], out[s][0], out[s2][0]);
            facing(b, in[s][0], out[s2][0], in[s2][0]);
        }
        planetDisc(b, pal, PLANETS[1], th.extraSwatch(2), 0f, 24);
        // green blobs on the blue planet, 0.05 m in front
        float az2 = (float) Math.toRadians(PLANETS[1][0]), h2 = PLANETS[1][1], rad2 = PLANETS[1][2];
        float[] f2 = frame(az2, h2);
        float[][] blobs = {{-0.35f, 0.25f, 0.34f}, {0.3f, -0.2f, 0.3f}, {0.05f, 0.5f, 0.2f}};
        for (float[] bl : blobs) {
            float cx = f2[0] + f2[3] * bl[0] * rad2 + f2[6] * bl[1] * rad2 - f2[9] * 0.05f;
            float cy = f2[1] + f2[4] * bl[0] * rad2 + f2[7] * bl[1] * rad2 - f2[10] * 0.05f;
            float cz = f2[2] + f2[5] * bl[0] * rad2 + f2[8] * bl[1] * rad2 - f2[11] * 0.05f;
            fan(b, pal, cx, cy, cz, f2, bl[2] * rad2, th.extraSwatch(3), 12, 0.6f, 0.4f);
        }
    }

    /**
     * Frame of a planet: centre (3), the unit vector along the sky to the right (3), up (3) and away from the camera (3).
     */
    private static float[] frame(float az, float h) {
        float hd = (float) Math.sqrt(PLANET_DIST * PLANET_DIST - h * h);
        float cx = (float) Math.sin(az) * hd, cz = (float) Math.cos(az) * hd;
        float[] dir = {cx / PLANET_DIST, h / PLANET_DIST, cz / PLANET_DIST};
        float[] u = {(float) Math.cos(az), 0f, -(float) Math.sin(az)};
        float[] v = {dir[1] * u[2] - dir[2] * u[1], dir[2] * u[0] - dir[0] * u[2], dir[0] * u[1] - dir[1] * u[0]};
        if (v[1] < 0f) {
            v[0] = -v[0];
            v[1] = -v[1];
            v[2] = -v[2];
        }
        return new float[] {cx, h, cz, u[0], u[1], u[2], v[0], v[1], v[2], dir[0], dir[1], dir[2]};
    }

    private static void planetDisc(Buf b, PaletteSwatches pal, float[] pl, int swatch, float depth, int sides) {
        float[] f = frame((float) Math.toRadians(pl[0]), pl[1]);
        fan(b, pal, f[0], f[1], f[2], f, pl[2], swatch, sides, 0.55f, 0.45f);
    }

    /** A fan disc of radius r in the plane of frame f round (cx, cy, cz), ramp position lit from the upper left. */
    private static void fan(Buf b, PaletteSwatches pal, float cx, float cy, float cz, float[] f, float r, int swatch, int sides,
                            float base, float gain) {
        float v0 = pal.v(swatch);
        int c = b.vert(cx, cy, cz, pal.uRamp(swatch, base + gain * 0.25f), v0);
        int[] rim = new int[sides];
        for (int i = 0; i < sides; i++) {
            double a = 2.0 * Math.PI * i / sides;
            float ox = (float) Math.cos(a), oy = (float) Math.sin(a);
            float t = Math.max(0f, Math.min(1f, base + gain * (-0.6f * ox + 0.8f * oy)));
            rim[i] = b.vert(cx + (f[3] * ox + f[6] * oy) * r, cy + (f[4] * ox + f[7] * oy) * r, cz + (f[5] * ox + f[8] * oy) * r,
                    pal.uRamp(swatch, t), v0);
        }
        for (int i = 0; i < sides; i++) facing(b, c, rim[i], rim[(i + 1) % sides]);
    }

    /** A triangle that faces the origin (the centre of the ring). */
    private static void facing(Buf b, int ia, int ib, int ic) {
        float[] v = b.v;
        float ux = v[ib * 8] - v[ia * 8], uy = v[ib * 8 + 1] - v[ia * 8 + 1], uz = v[ib * 8 + 2] - v[ia * 8 + 2];
        float wx = v[ic * 8] - v[ia * 8], wy = v[ic * 8 + 1] - v[ia * 8 + 1], wz = v[ic * 8 + 2] - v[ia * 8 + 2];
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        if (nx * -v[ia * 8] + ny * -v[ia * 8 + 1] + nz * -v[ia * 8 + 2] >= 0f) b.tri(ia, ib, ic);
        else b.tri(ia, ic, ib);
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
