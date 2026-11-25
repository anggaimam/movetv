package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the whole road of a {@link TrackSpline} as static meshes in world space (T-28 Part C, detailed in T-29R Part R3): mottled
 * asphalt in strips across the road with worn wheel lanes, worn painted edge lines and centre dashes, raised bevelled kerbs, a
 * W-profile guard rail on posts, the start line with painted grid boxes, a truss gantry, tyre marks at the bends and a dark strip
 * beside the barrier. Plain Java, build time only.
 *
 * <p>Colours come from ramp swatches ({@link RoadStyle}); each vertex picks its position on the ramp (noise stretched along the
 * road makes the asphalt flow with it). A triangle never mixes swatch blocks. The road is cut into pieces of 60 m of s. Rows are
 * 1 m apart on the straights and closer in the bends. The rail's inner face stays 0.6 m outside the road edge, as the box barrier
 * was, so the wall limit of the car does not change.
 */
public final class TrackMeshBuilder {

    /** Metres of s per piece, the kerb width, the rail's inner face offset from the road edge, post spacing. */
    static final float PIECE = 60f, KERB = 0.6f, RAIL_OFFSET = 0.6f, POST_SPACING = 2f;
    /** Bends tighter than this radius get rows every 0.7 m. */
    static final float BEND_RADIUS = 30f, BEND_STEP = 0.8f;
    private static final float Y_KERB = 0.08f, Y_PAINT = 0.004f, Y_MARK = 0.005f, Y_START = 0.006f, POST_H = 0.75f;
    /** The rail profile (outward offset from the inner plane, height), bottom to top: a W shape of 5 faces. */
    private static final float[][] RAIL = {{0.04f, 0.42f}, {0f, 0.50f}, {0.05f, 0.58f}, {0f, 0.66f}, {0.04f, 0.74f}, {0.04f, 0.79f}};
    private static final float[] RAIL_TONE = {0.3f, 0.85f, 0.35f, 0.85f, 0.4f, 0.6f};

    private TrackMeshBuilder() { }

    /**
     * Builds the road.
     * @param t the track (its half width sets the road width)
     * @param style the colours; they are painted into the palette ({@link RoadStyle#paint})
     * @param pal the palette whose swatch uvs the quads use
     * @return the pieces, each a one-part {@link MeshData}, ceil(lapLength / 60) of them
     */
    public static List<MeshData> build(TrackSpline t, RoadStyle style, PaletteSwatches pal) {
        return build(t, style, pal, -1);
    }

    /**
     * Builds the road.
     * @param shoulderSwatch the packed-shoulder ramp swatch for the dark strip beside the barrier, or -1 for none
     */
    public static List<MeshData> build(TrackSpline t, RoadStyle style, PaletteSwatches pal, int shoulderSwatch) {
        style.paint(pal);
        final float lap = t.lapLength(), hw = t.roadHalfWidth();
        final long seed = 7L + Math.round(lap);

        // rows: the s of each row, then centre, right vector, per row
        List<Float> sList = new ArrayList<>();
        float s = 0f;
        int count = t.sampleCount();
        while (s < lap - 0.01f) {
            sList.add(s);
            int idx = Math.min(count - 1, Math.round(s / lap * count));
            float step = Math.abs(t.radiusAt(idx)) < BEND_RADIUS ? BEND_STEP : 1f;
            s += step;
        }
        sList.add(lap);
        final int rows = sList.size();
        float[] rs = new float[rows], cx = new float[rows], cz = new float[rows], rx = new float[rows], rz = new float[rows],
                fx = new float[rows], fz = new float[rows];
        float[] o = new float[3];
        for (int j = 0; j < rows; j++) {
            rs[j] = sList.get(j);
            t.sample(rs[j] % lap, o);
            cx[j] = o[0];
            cz[j] = o[1];
            rx[j] = -(float) Math.cos(o[2]);
            rz[j] = (float) Math.sin(o[2]);
            fx[j] = (float) Math.sin(o[2]);
            fz[j] = (float) Math.cos(o[2]);
        }
        int pieces = (int) Math.ceil(lap / PIECE);
        int[] cut = new int[pieces + 1];
        int jj = 0;
        for (int p = 1; p < pieces; p++) {
            while (jj < rows - 1 && rs[jj] < p * PIECE) jj++;
            cut[p] = jj;
        }
        cut[pieces] = rows - 1;

        // tyre mark spans: the middle of every bend, as s ranges
        List<float[]> marks = tyreMarks(t, hw);

        List<MeshData> out = new ArrayList<>(pieces);
        float edge = hw - 0.425f, lineIn = hw - 0.275f, wc = 0.5f * hw, ww = 0.45f;
        // strips across the road: off0, off1, kind (0 asphalt, 1 wear, 2 edge shade, 3 paint line)
        float[][] strips = {
                {-hw, -edge, 2}, {-edge, -lineIn, 3}, {-lineIn, -wc - ww, 0}, {-wc - ww, -wc + ww, 1}, {-wc + ww, wc - ww, 0},
                {wc - ww, wc + ww, 1}, {wc + ww, lineIn, 0}, {lineIn, edge, 3}, {edge, hw, 2}};
        for (int p = 0; p < pieces; p++) {
            int j0 = cut[p], j1 = cut[p + 1];
            MeshKit k = new MeshKit(pal);
            float[] a = new float[3], b = new float[3], c = new float[3], d = new float[3];

            // asphalt, wear lanes, edge shade strips and paint lines
            for (float[] st : strips) {
                int kind = (int) st[2];
                int sw = kind == 3 ? RoadStyle.RAMP_PAINT : RoadStyle.RAMP_ASPHALT;
                float y = kind == 3 ? Y_PAINT : 0f;
                int[] l = new int[j1 - j0 + 1], r = new int[j1 - j0 + 1];
                for (int j = j0; j <= j1; j++) {
                    float tl = tone(kind, rs[j], st[0], seed), tr = tone(kind, rs[j], st[1], seed);
                    l[j - j0] = k.vert(cx[j] + rx[j] * st[0], y, cz[j] + rz[j] * st[0], 0f, 1f, 0f, sw, tl);
                    r[j - j0] = k.vert(cx[j] + rx[j] * st[1], y, cz[j] + rz[j] * st[1], 0f, 1f, 0f, sw, tr);
                }
                for (int j = j0; j < j1; j++) {
                    int q = j - j0;
                    // up-facing winding for the row direction forward and the offset direction along the right vector
                    k.tri(l[q], l[q + 1], r[q]);
                    k.tri(r[q], l[q + 1], r[q + 1]);
                }
            }
            fixUp(k);

            for (int j = j0; j < j1; j++) {
                float sMid = (rs[j] + rs[j + 1]) * 0.5f;
                for (int side = -1; side <= 1; side += 2) {
                    int ks = ((int) Math.floor(sMid / 1.5f) & 1) == 0 ? RoadStyle.RAMP_KERB_A : RoadStyle.RAMP_KERB_B;
                    // kerb: a bevel up from the road edge, then the flat top
                    row(k, cx, cz, rx, rz, j, side * hw, 0f, side * (hw + Y_KERB), Y_KERB, ks, 0.35f, 0.45f, 0f, 1f, 0f, a, b, c, d);
                    row(k, cx, cz, rx, rz, j, side * (hw + Y_KERB), Y_KERB, side * (hw + KERB), Y_KERB, ks, 0.8f, 0.85f, 0f, 1f, 0f, a, b, c, d);
                    if (shoulderSwatch >= 0) {
                        float o0 = side * (hw + KERB + 0.3f + 0.3f), o1 = side * (hw + KERB + 0.3f + 1.1f);
                        row(k, cx, cz, rx, rz, j, o0, -0.012f, o1, -0.012f, shoulderSwatch, 0.08f, 0.2f, 0f, 1f, 0f, a, b, c, d);
                    }
                }
                if (style.centreDash != 0 && ((int) Math.floor(sMid / 3f) & 1) == 0) {
                    row(k, cx, cz, rx, rz, j, -0.125f, Y_PAINT, 0.125f, Y_PAINT, RoadStyle.RAMP_PAINT, 0.7f, 0.75f, 0f, 1f, 0f, a, b, c, d);
                }
            }

            // guard rail on both sides
            for (int side = -1; side <= 1; side += 2) {
                float[][][] pp = new float[j1 - j0 + 1][RAIL.length][3];
                float[][] tt = new float[j1 - j0 + 1][RAIL.length];
                for (int j = j0; j <= j1; j++) {
                    for (int q = 0; q < RAIL.length; q++) {
                        // left side (-1) runs bottom to top, the right side top to bottom, so both faces look at the road
                        int qq = side < 0 ? q : RAIL.length - 1 - q;
                        float off = side * (hw + RAIL_OFFSET + RAIL[qq][0]);
                        pp[j - j0][q][0] = cx[j] + rx[j] * off;
                        pp[j - j0][q][1] = RAIL[qq][1];
                        pp[j - j0][q][2] = cz[j] + rz[j] * off;
                        tt[j - j0][q] = RAIL_TONE[qq];
                    }
                }
                k.grid(pp, tt, RoadStyle.RAMP_RAIL, false);
            }
            // posts every 2 m of s that fall in this piece
            float s0 = rs[j0], s1 = rs[j1];
            for (float sp = (float) Math.ceil(s0 / POST_SPACING) * POST_SPACING; sp < s1 - 1e-3f || (p == pieces - 1 && sp < lap - 1e-3f); sp += POST_SPACING) {
                if (sp >= s1 && p < pieces - 1) break;
                t.sample(sp, o);
                float ax = (float) Math.sin(o[2]), az = (float) Math.cos(o[2]);
                float bx = -(float) Math.cos(o[2]), bz = (float) Math.sin(o[2]);
                for (int side = -1; side <= 1; side += 2) {
                    float off = side * (hw + RAIL_OFFSET + 0.04f + 0.08f);
                    box(k, o[0] + bx * off, o[1] + bz * off, 0f, POST_H, 0.06f, 0.06f, ax, az, bx, bz, false, RoadStyle.RAMP_POST, 0.3f, 0.7f);
                }
            }
            // tyre marks and painted grid boxes
            for (float[] m : marks) markStrip(k, t, m[0], m[1], m[2], hw, a, b, c, d);
            if (p == 0) {
                startLine(k, t, hw, pal, a, b, c, d);
                gantry(k, t, hw);
            }
            if (p == pieces - 1) gridBoxes(k, t, hw, a, b, c, d);
            out.add(k.mesh("road"));
        }
        return out;
    }

    /** Ramp position at a point of the road surface: noise stretched along the road, darker in the wheel lanes and next to the kerb. */
    private static float tone(int kind, float s, float off, long seed) {
        float n = Noise.fbm(s * 0.12f, off * 0.9f + 40f, 3, seed);
        float t = 0.5f + 0.4f * (n - 0.5f) * 2f;
        if (kind == 1) t -= 0.12f;
        if (kind == 2) t -= 0.2f;
        if (kind == 3) t = 0.5f + 0.4f * (Noise.fbm(s * 0.5f, off * 4f, 2, seed + 3L) - 0.5f) * 2f;
        return Math.max(0f, Math.min(1f, t));
    }

    /** The strip winding is checked against up and flipped where it came out the other way. */
    private static void fixUp(MeshKit k) {
        for (int i = 0; i + 2 < k.ic; i += 3) {
            int a = k.ix[i] & 0xFFFF, b = k.ix[i + 1] & 0xFFFF, c = k.ix[i + 2] & 0xFFFF;
            if (k.v[a * 8 + 4] < 0.99f) continue;   // only the flat up-facing surface vertices
            float ux = k.v[b * 8] - k.v[a * 8], uz = k.v[b * 8 + 2] - k.v[a * 8 + 2];
            float vx = k.v[c * 8] - k.v[a * 8], vz = k.v[c * 8 + 2] - k.v[a * 8 + 2];
            float ny = uz * vx - ux * vz;
            if (ny < 0f) {
                k.ix[i + 1] = (short) c;
                k.ix[i + 2] = (short) b;
            }
        }
    }

    /** A quad on row segment j between two offsets and heights; faces the side of the hint. */
    private static void row(MeshKit k, float[] cx, float[] cz, float[] rx, float[] rz, int j, float off0, float y0, float off1,
                            float y1, int sw, float tLow, float tHigh, float hx, float hy, float hz, float[] a, float[] b, float[] c,
                            float[] d) {
        a[0] = cx[j] + rx[j] * off0;
        a[1] = y0;
        a[2] = cz[j] + rz[j] * off0;
        b[0] = cx[j] + rx[j] * off1;
        b[1] = y1;
        b[2] = cz[j] + rz[j] * off1;
        c[0] = cx[j + 1] + rx[j + 1] * off1;
        c[1] = y1;
        c[2] = cz[j + 1] + rz[j + 1] * off1;
        d[0] = cx[j + 1] + rx[j + 1] * off0;
        d[1] = y0;
        d[2] = cz[j + 1] + rz[j + 1] * off0;
        float tb = y1 > y0 ? tHigh : tLow;
        k.flatQuad(a, b, c, d, y0 >= y1 ? tHigh : tLow, tb, tb, y0 >= y1 ? tHigh : tLow, sw, hx, hy, hz);
    }

    /** The spans (s0, s1, outside sign) of tyre marks: one per bend, centred on the tightest point. */
    private static List<float[]> tyreMarks(TrackSpline t, float hw) {
        List<float[]> out = new ArrayList<>();
        int n = t.sampleCount();
        float ds = t.lapLength() / n;
        int i = 0;
        while (i < n) {
            if (Math.abs(t.radiusAt(i)) >= BEND_RADIUS) {
                i++;
                continue;
            }
            int a = i;
            while (i < n && Math.abs(t.radiusAt(i)) < BEND_RADIUS) i++;
            int mid = (a + i - 1) / 2;
            float dyaw = t.sampleYaw((mid + 1) % n) - t.sampleYaw(mid);
            while (dyaw > Math.PI) dyaw -= 2 * (float) Math.PI;
            while (dyaw < -Math.PI) dyaw += 2 * (float) Math.PI;
            float outside = dyaw > 0f ? 1f : -1f;   // a left turn (yaw growing) has its outside on the right vector
            float s0 = mid * ds - 6f;
            if (s0 > 8f && s0 + 12f < t.lapLength() - 8f) out.add(new float[] {s0, s0 + 12f, outside});
        }
        return out;
    }

    /** Two dark streaks along the outer part of a bend. */
    private static void markStrip(MeshKit k, TrackSpline t, float s0, float s1, float outside, float hw, float[] a, float[] b,
                                  float[] c, float[] d) {
        float[] p = new float[3], q = new float[3];
        for (int line = 0; line < 2; line++) {
            float off = outside * (hw * 0.55f - line * 0.9f * 1f);
            for (float s = s0; s < s1 - 0.01f; s += 1f) {
                t.sample(s, p);
                t.sample(s + 1f, q);
                float prx = -(float) Math.cos(p[2]), prz = (float) Math.sin(p[2]), qrx = -(float) Math.cos(q[2]), qrz = (float) Math.sin(q[2]);
                float fade = Math.min(1f, Math.min((s - s0) / 3f, (s1 - s) / 3f));
                float tone = 0.25f - 0.2f * fade;
                a[0] = p[0] + prx * (off - 0.15f);
                a[1] = Y_MARK;
                a[2] = p[1] + prz * (off - 0.15f);
                b[0] = p[0] + prx * (off + 0.15f);
                b[1] = Y_MARK;
                b[2] = p[1] + prz * (off + 0.15f);
                c[0] = q[0] + qrx * (off + 0.15f);
                c[1] = Y_MARK;
                c[2] = q[1] + qrz * (off + 0.15f);
                d[0] = q[0] + qrx * (off - 0.15f);
                d[1] = Y_MARK;
                d[2] = q[1] + qrz * (off - 0.15f);
                k.flatQuad(a, b, c, d, tone, tone, tone, tone, RoadStyle.RAMP_ASPHALT, 0f, 1f, 0f);
            }
        }
    }

    /** The chequered start line at s = 0 to 2: squares of about 1 x 1 m, two rows across the whole road. */
    private static void startLine(MeshKit k, TrackSpline t, float hw, PaletteSwatches pal, float[] a, float[] b, float[] c, float[] d) {
        int n = Math.max(2, Math.round(2f * hw));
        float[] o0 = new float[3], o1 = new float[3], o2 = new float[3];
        t.sample(0f, o0);
        t.sample(1f, o1);
        t.sample(2f, o2);
        for (int row = 0; row < 2; row++) {
            float[] p = row == 0 ? o0 : o1, q = row == 0 ? o1 : o2;
            float prx = -(float) Math.cos(p[2]), prz = (float) Math.sin(p[2]);
            float qrx = -(float) Math.cos(q[2]), qrz = (float) Math.sin(q[2]);
            for (int i = 0; i < n; i++) {
                float f0 = -hw + 2f * hw * i / n, f1 = -hw + 2f * hw * (i + 1) / n;
                int sw = ((i + row) & 1) == 0 ? RoadStyle.SW_START_DARK : RoadStyle.SW_START_LIGHT;
                a[0] = p[0] + prx * f0;
                a[1] = Y_START;
                a[2] = p[1] + prz * f0;
                b[0] = p[0] + prx * f1;
                b[1] = Y_START;
                b[2] = p[1] + prz * f1;
                c[0] = q[0] + qrx * f1;
                c[1] = Y_START;
                c[2] = q[1] + qrz * f1;
                d[0] = q[0] + qrx * f0;
                d[1] = Y_START;
                d[2] = q[1] + qrz * f0;
                k.flatQuad(a, b, c, d, 0.5f, 0.5f, 0.5f, 0.5f, sw, 0f, 1f, 0f);
            }
        }
    }

    /** Two painted start boxes before the line (2.4 m x 4.5 m outlines, 0.12 m wide) at the lap's end. */
    private static void gridBoxes(MeshKit k, TrackSpline t, float hw, float[] a, float[] b, float[] c, float[] d) {
        float lap = t.lapLength();
        float[] p = new float[3];
        for (int slot = 0; slot < 2; slot++) {
            float lat = (slot == 0 ? -0.5f : 0.5f) * hw;
            float sA = lap - 8f, sB = lap - 3.5f;
            for (int edge = 0; edge < 4; edge++) {
                float sa = edge == 0 ? sA : edge == 1 ? sB : sA, sb = edge == 0 ? sA : edge == 1 ? sB : sB;
                float la = edge < 2 ? lat - 1.2f : lat + (edge == 2 ? -1.2f : 1.2f), lb = edge < 2 ? lat + 1.2f : la;
                // edge 0 and 1: the cross lines at sA and sB; edge 2 and 3: the side lines
                quadAt(k, t, sa, la, sb, lb, edge < 2, a, b, c, d, p);
            }
        }
    }

    private static void quadAt(MeshKit k, TrackSpline t, float sa, float la, float sb, float lb, boolean cross, float[] a, float[] b,
                               float[] c, float[] d, float[] p) {
        float w = 0.06f;
        float[] pa = new float[3], pb = new float[3];
        t.sample(sa, pa);
        t.sample(sb, pb);
        float ra = -(float) Math.cos(pa[2]), za = (float) Math.sin(pa[2]), rb = -(float) Math.cos(pb[2]), zb = (float) Math.sin(pb[2]);
        float fax = (float) Math.sin(pa[2]), faz = (float) Math.cos(pa[2]);
        if (cross) {
            a[0] = pa[0] + ra * la - fax * w;
            a[2] = pa[1] + za * la - faz * w;
            b[0] = pa[0] + ra * lb - fax * w;
            b[2] = pa[1] + za * lb - faz * w;
            c[0] = pa[0] + ra * lb + fax * w;
            c[2] = pa[1] + za * lb + faz * w;
            d[0] = pa[0] + ra * la + fax * w;
            d[2] = pa[1] + za * la + faz * w;
        } else {
            a[0] = pa[0] + ra * (la - w);
            a[2] = pa[1] + za * (la - w);
            b[0] = pa[0] + ra * (la + w);
            b[2] = pa[1] + za * (la + w);
            c[0] = pb[0] + rb * (la + w);
            c[2] = pb[1] + zb * (la + w);
            d[0] = pb[0] + rb * (la - w);
            d[2] = pb[1] + zb * (la - w);
        }
        a[1] = b[1] = c[1] = d[1] = Y_MARK;
        k.flatQuad(a, b, c, d, 0.85f, 0.85f, 0.85f, 0.85f, RoadStyle.RAMP_PAINT, 0f, 1f, 0f);
    }

    /** The finish gantry at s = 0: two truss posts (4 corner members and 6 braces each) and a framed banner box. */
    private static void gantry(MeshKit k, TrackSpline t, float hw) {
        float[] o = new float[3];
        t.sample(0f, o);
        float fx = (float) Math.sin(o[2]), fz = (float) Math.cos(o[2]);
        float rx = -(float) Math.cos(o[2]), rz = (float) Math.sin(o[2]);
        float post = hw + 1.3f;
        for (int side = -1; side <= 1; side += 2) {
            float px = o[0] + rx * post * side, pz = o[1] + rz * post * side;
            for (int m = 0; m < 4; m++) {
                float ox = (m & 1) == 0 ? -0.22f : 0.22f, oz = (m & 2) == 0 ? -0.22f : 0.22f;
                box(k, px + rx * ox + fx * oz, pz + rz * ox + fz * oz, 0f, 6f, 0.05f, 0.05f, fx, fz, rx, rz, false,
                        RoadStyle.RAMP_RAIL, 0.4f, 0.8f);
            }
            for (int b = 0; b < 6; b++) {
                float y = 0.6f + b * 0.95f;
                box(k, px + fx * 0.22f, pz + fz * 0.22f, y, y + 0.06f, 0.03f, 0.22f, fx, fz, rx, rz, false, RoadStyle.RAMP_RAIL, 0.5f, 0.7f);
            }
        }
        box(k, o[0], o[1], 4.85f, 6.35f, 0.14f, post + 0.3f, fx, fz, rx, rz, true, RoadStyle.RAMP_POST, 0.1f, 0.5f);
        PaletteSwatches pal = k.pal;
        box(k, o[0] - fx * 0.02f, o[1] - fz * 0.02f, 5.0f, 6.2f, 0.16f, post - 0.05f, fx, fz, rx, rz, true, RoadStyle.SW_ARCH_BANNER, 0.5f, 0.5f);
    }

    /**
     * An upright box with outward normals: centre (x, z), heights y0..y1, half size hf along the forward vector (fx, fz) and
     * hr along the right vector (rx, rz); {@code tLow} and {@code tHigh} are the ramp positions at the bottom and the top.
     */
    private static void box(MeshKit k, float x, float z, float y0, float y1, float hf, float hr, float fx, float fz, float rx, float rz,
                            boolean bottom, int sw, float tLow, float tHigh) {
        float[][] c = new float[8][3];
        for (int i = 0; i < 8; i++) {
            float sf = (i & 3) == 0 || (i & 3) == 3 ? -hf : hf, sr = (i & 3) < 2 ? -hr : hr;
            c[i][0] = x + fx * sf + rx * sr;
            c[i][1] = i < 4 ? y0 : y1;
            c[i][2] = z + fz * sf + rz * sr;
        }
        k.flatQuad(c[0], c[1], c[5], c[4], tLow, tLow, tHigh, tHigh, sw, -rx, 0f, -rz);
        k.flatQuad(c[1], c[2], c[6], c[5], tLow, tLow, tHigh, tHigh, sw, fx, 0f, fz);
        k.flatQuad(c[2], c[3], c[7], c[6], tLow, tLow, tHigh, tHigh, sw, rx, 0f, rz);
        k.flatQuad(c[3], c[0], c[4], c[7], tLow, tLow, tHigh, tHigh, sw, -fx, 0f, -fz);
        k.flatQuad(c[4], c[5], c[6], c[7], tHigh, tHigh, tHigh, tHigh, sw, 0f, 1f, 0f);
        if (bottom) k.flatQuad(c[0], c[1], c[2], c[3], tLow, tLow, tLow, tLow, sw, 0f, -1f, 0f);
    }
}
