package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the whole road of a {@link TrackSpline} as static meshes in world space (T-28 Part C): surface, kerbs, barriers,
 * optional centre dashes, start line and finish arch. Plain Java, build time only.
 *
 * <p>Every vertex of a quad carries the uv of the centre of its colour's swatch block, so quads of different colours never
 * share vertices (only the road surface, one colour, shares its row vertices). The road is cut into pieces of 60 m of s;
 * the start line and the arch are in the first piece. Triangles are wound counter-clockwise seen from the side the normal
 * points to; the meshes are single sided.
 */
public final class TrackMeshBuilder {

    /** Metres of s per piece, between two rows, and the kerb / barrier geometry. */
    static final float PIECE = 60f, KERB = 0.6f, BARRIER_W = 0.3f, BARRIER_H = 0.7f;
    private static final float Y_ROAD = 0f, Y_KERB = 0.005f, Y_DASH = 0.004f, Y_START = 0.006f;

    private TrackMeshBuilder() { }

    /** Growable vertex and index buffers of one piece. */
    private static final class Buf {
        float[] v = new float[8 * 2048];
        short[] ix = new short[3 * 4096];
        int vc, ic;

        int vert(float x, float y, float z, float nx, float ny, float nz, float u, float w) {
            if ((vc + 1) * 8 > v.length) v = Arrays.copyOf(v, v.length * 2);
            int o = vc * 8;
            v[o] = x;
            v[o + 1] = y;
            v[o + 2] = z;
            v[o + 3] = nx;
            v[o + 4] = ny;
            v[o + 5] = nz;
            v[o + 6] = u;
            v[o + 7] = w;
            if (vc >= 65535) throw new IllegalStateException("a road piece has more than 65535 vertices");
            return vc++;
        }

        void tri(int a, int b, int c) {
            if (ic + 3 > ix.length) ix = Arrays.copyOf(ix, ix.length * 2);
            ix[ic++] = (short) a;
            ix[ic++] = (short) b;
            ix[ic++] = (short) c;
        }

        /**
         * A flat quad a, b, c, d (in order round it) with a flat normal that points the same way as {@code hint}.
         */
        void quad(float[] a, float[] b, float[] c, float[] d, float hx, float hy, float hz, float u, float w) {
            float e1x = c[0] - a[0], e1y = c[1] - a[1], e1z = c[2] - a[2];
            float e2x = d[0] - b[0], e2y = d[1] - b[1], e2z = d[2] - b[2];
            float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9f) return;   // a collapsed quad (nothing to draw)
            nx /= len;
            ny /= len;
            nz /= len;
            boolean flip = nx * hx + ny * hy + nz * hz < 0f;
            if (flip) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            int ia = vert(a[0], a[1], a[2], nx, ny, nz, u, w);
            int ib = vert(b[0], b[1], b[2], nx, ny, nz, u, w);
            int ic2 = vert(c[0], c[1], c[2], nx, ny, nz, u, w);
            int id = vert(d[0], d[1], d[2], nx, ny, nz, u, w);
            if (!flip) {
                tri(ia, ib, ic2);
                tri(ia, ic2, id);
            } else {
                tri(ia, id, ic2);
                tri(ia, ic2, ib);
            }
        }
    }

    /**
     * Builds the road.
     * @param t the track (its half width sets the road width)
     * @param style the colours; they are painted into swatches 0 to 9 of {@code pal}
     * @param pal the palette whose swatch uvs the quads use
     * @return the pieces, each a one-part {@link MeshData}, ceil(lapLength / 60) of them
     */
    public static List<MeshData> build(TrackSpline t, RoadStyle style, PaletteSwatches pal) {
        style.paint(pal);
        final float lap = t.lapLength(), hw = t.roadHalfWidth();
        final int rows = Math.max(1, Math.round(lap));
        final float step = lap / rows;

        // row j: centre, yaw, right vector
        float[] cx = new float[rows + 1], cz = new float[rows + 1], rx = new float[rows + 1], rz = new float[rows + 1];
        float[] o = new float[3];
        for (int j = 0; j <= rows; j++) {
            t.sample(j * step, o);
            cx[j] = o[0];
            cz[j] = o[1];
            rx[j] = -(float) Math.cos(o[2]);
            rz[j] = (float) Math.sin(o[2]);
        }

        int pieces = (int) Math.ceil(lap / PIECE);
        List<MeshData> out = new ArrayList<>(pieces);
        float[] a = new float[3], b = new float[3], c = new float[3], d = new float[3];
        for (int p = 0; p < pieces; p++) {
            int j0 = Math.min(rows, Math.round(p * PIECE / step));
            int j1 = p == pieces - 1 ? rows : Math.min(rows, Math.round((p + 1) * PIECE / step));
            Buf buf = new Buf();

            // road surface: one strip with shared row vertices
            float ru = pal.u(RoadStyle.SW_ROAD), rv = pal.v(RoadStyle.SW_ROAD);
            int[] left = new int[j1 - j0 + 1], right = new int[j1 - j0 + 1];
            for (int j = j0; j <= j1; j++) {
                left[j - j0] = buf.vert(cx[j] - rx[j] * hw, Y_ROAD, cz[j] - rz[j] * hw, 0f, 1f, 0f, ru, rv);
                right[j - j0] = buf.vert(cx[j] + rx[j] * hw, Y_ROAD, cz[j] + rz[j] * hw, 0f, 1f, 0f, ru, rv);
            }
            for (int j = j0; j < j1; j++) {
                int k = j - j0;
                // left(k), right(k), right(k+1), left(k+1) seen from above is the order that faces up for this handedness
                buf.tri(left[k], right[k], right[k + 1]);
                buf.tri(left[k], right[k + 1], left[k + 1]);
            }
            fixStripWinding(buf, 2 * (j1 - j0 + 1));

            for (int j = j0; j < j1; j++) {
                float sMid = (j + 0.5f) * step;
                for (int side = -1; side <= 1; side += 2) {
                    // kerbs
                    int kerb = ((int) Math.floor(sMid / 2f) & 1) == 0 ? RoadStyle.SW_KERB_A : RoadStyle.SW_KERB_B;
                    rowQuad(buf, cx, cz, rx, rz, j, side * hw, side * (hw + KERB), Y_KERB, Y_KERB, 0f, 1f, 0f, a, b, c, d,
                            pal.u(kerb), pal.v(kerb));
                    // barrier: inner face, top, outer face
                    int inner = (sMid % 4f) < 3f ? RoadStyle.SW_BARRIER : RoadStyle.SW_BARRIER_STRIPE;
                    float in = side * (hw + KERB), out2 = side * (hw + KERB + BARRIER_W);
                    wallQuad(buf, cx, cz, rx, rz, j, in, 0f, in, BARRIER_H, -side * rx[j], 0f, -side * rz[j], a, b, c, d,
                            pal.u(inner), pal.v(inner));
                    rowQuad(buf, cx, cz, rx, rz, j, in, out2, BARRIER_H, BARRIER_H, 0f, 1f, 0f, a, b, c, d,
                            pal.u(RoadStyle.SW_BARRIER), pal.v(RoadStyle.SW_BARRIER));
                    wallQuad(buf, cx, cz, rx, rz, j, out2, 0f, out2, BARRIER_H, side * rx[j], 0f, side * rz[j], a, b, c, d,
                            pal.u(RoadStyle.SW_BARRIER), pal.v(RoadStyle.SW_BARRIER));
                }
                // centre dashes: 3 m dash, 3 m gap, 0.25 m wide
                if (style.centreDash != 0 && ((int) Math.floor(sMid / 3f) & 1) == 0) {
                    rowQuad(buf, cx, cz, rx, rz, j, -0.125f, 0.125f, Y_DASH, Y_DASH, 0f, 1f, 0f, a, b, c, d,
                            pal.u(RoadStyle.SW_CENTRE_DASH), pal.v(RoadStyle.SW_CENTRE_DASH));
                }
            }
            if (p == 0) {
                startLine(buf, t, hw, pal, a, b, c, d);
                arch(buf, t, hw, pal);
            }
            out.add(toMesh(buf));
        }
        return out;
    }

    /** The strip's winding is checked against up (normal y) and reversed when it came out the other way. */
    private static void fixStripWinding(Buf buf, int stripVerts) {
        for (int i = 0; i + 2 < buf.ic; i += 3) {
            int a = buf.ix[i] & 0xFFFF, b = buf.ix[i + 1] & 0xFFFF, c = buf.ix[i + 2] & 0xFFFF;
            if (a >= stripVerts || b >= stripVerts || c >= stripVerts) break;
            float ux = buf.v[b * 8] - buf.v[a * 8], uz = buf.v[b * 8 + 2] - buf.v[a * 8 + 2];
            float vx = buf.v[c * 8] - buf.v[a * 8], vz = buf.v[c * 8 + 2] - buf.v[a * 8 + 2];
            float ny = uz * vx - ux * vz;   // y of cross(u, v)
            if (ny < 0f) {
                buf.ix[i + 1] = (short) c;
                buf.ix[i + 2] = (short) b;
            }
        }
    }

    /** A quad on row segment j between two offsets from the centre line and two heights, flat. */
    private static void rowQuad(Buf buf, float[] cx, float[] cz, float[] rx, float[] rz, int j, float off0, float off1,
                                float y0, float y1, float hx, float hy, float hz, float[] a, float[] b, float[] c, float[] d,
                                float u, float w) {
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
        buf.quad(a, b, c, d, hx, hy, hz, u, w);
    }

    /** A vertical quad along row segment j at offset {@code off0} from height {@code y0} to {@code y1}. */
    private static void wallQuad(Buf buf, float[] cx, float[] cz, float[] rx, float[] rz, int j, float off0, float y0,
                                 float off1, float y1, float hx, float hy, float hz, float[] a, float[] b, float[] c,
                                 float[] d, float u, float w) {
        a[0] = cx[j] + rx[j] * off0;
        a[1] = y0;
        a[2] = cz[j] + rz[j] * off0;
        b[0] = cx[j + 1] + rx[j + 1] * off0;
        b[1] = y0;
        b[2] = cz[j + 1] + rz[j + 1] * off0;
        c[0] = cx[j + 1] + rx[j + 1] * off1;
        c[1] = y1;
        c[2] = cz[j + 1] + rz[j + 1] * off1;
        d[0] = cx[j] + rx[j] * off1;
        d[1] = y1;
        d[2] = cz[j] + rz[j] * off1;
        buf.quad(a, b, c, d, hx, hy, hz, u, w);
    }

    /** The chequered start line at s = 0 to 2: squares of about 1 x 1 m, two rows across the whole road. */
    private static void startLine(Buf buf, TrackSpline t, float hw, PaletteSwatches pal, float[] a, float[] b, float[] c,
                                  float[] d) {
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
                buf.quad(a, b, c, d, 0f, 1f, 0f, pal.u(sw), pal.v(sw));
            }
        }
    }

    /** The finish arch at s = 0: two posts 0.5 x 0.5 x 6.0 at +-(hw + 1.3) and a banner box from y 5.0 to 6.2, 0.4 deep. */
    private static void arch(Buf buf, TrackSpline t, float hw, PaletteSwatches pal) {
        float[] o = new float[3];
        t.sample(0f, o);
        float fx = (float) Math.sin(o[2]), fz = (float) Math.cos(o[2]);
        float rx = -(float) Math.cos(o[2]), rz = (float) Math.sin(o[2]);
        float post = hw + 1.3f;
        float u = pal.u(RoadStyle.SW_ARCH_POST), v = pal.v(RoadStyle.SW_ARCH_POST);
        box(buf, o[0] + rx * post, o[1] + rz * post, 0f, 6f, 0.25f, 0.25f, fx, fz, rx, rz, false, u, v);
        box(buf, o[0] - rx * post, o[1] - rz * post, 0f, 6f, 0.25f, 0.25f, fx, fz, rx, rz, false, u, v);
        box(buf, o[0], o[1], 5f, 6.2f, 0.2f, post, fx, fz, rx, rz, true, pal.u(RoadStyle.SW_ARCH_BANNER),
                pal.v(RoadStyle.SW_ARCH_BANNER));
    }

    /**
     * An upright box with outward normals: centre (x, z), heights y0..y1, half size hf along the forward vector (fx, fz) and
     * hr along the right vector (rx, rz). The bottom face is left out unless {@code bottom} is true.
     */
    private static void box(Buf buf, float x, float z, float y0, float y1, float hf, float hr, float fx, float fz,
                            float rx, float rz, boolean bottom, float u, float w) {
        float[][] k = new float[8][3];   // 0..3 bottom: (-f,-r) (+f,-r) (+f,+r) (-f,+r); 4..7 the same on top
        for (int i = 0; i < 8; i++) {
            float sf = (i & 3) == 0 || (i & 3) == 3 ? -hf : hf, sr = (i & 3) < 2 ? -hr : hr;
            k[i][0] = x + fx * sf + rx * sr;
            k[i][1] = i < 4 ? y0 : y1;
            k[i][2] = z + fz * sf + rz * sr;
        }
        buf.quad(k[0], k[1], k[5], k[4], -rx, 0f, -rz, u, w);   // -r side
        buf.quad(k[1], k[2], k[6], k[5], fx, 0f, fz, u, w);     // +f side
        buf.quad(k[2], k[3], k[7], k[6], rx, 0f, rz, u, w);     // +r side
        buf.quad(k[3], k[0], k[4], k[7], -fx, 0f, -fz, u, w);   // -f side
        buf.quad(k[4], k[5], k[6], k[7], 0f, 1f, 0f, u, w);     // top
        if (bottom) buf.quad(k[0], k[1], k[2], k[3], 0f, -1f, 0f, u, w);
    }

    private static MeshData toMesh(Buf buf) {
        MeshData.Part part = new MeshData.Part("road", -1, new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f, 1f},
                new float[] {1f, 1f, 1f}, Arrays.copyOf(buf.v, buf.vc * 8), Arrays.copyOf(buf.ix, buf.ic), buf.vc);
        return new MeshData(false, new MeshData.Part[] {part});
    }
}
