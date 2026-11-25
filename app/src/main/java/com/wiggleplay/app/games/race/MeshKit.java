package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.Arrays;

/**
 * A small mesh builder for the detailed props and road parts (T-29R): vertices coloured from ramp swatches (a vertex picks its
 * position {@code t} on the ramp), smooth surfaces from ring-by-side grids, flat faces, and tubes along a path. Plain Java.
 * A triangle must use one swatch block; every method here takes one swatch for everything it emits.
 */
final class MeshKit {

    final PaletteSwatches pal;
    float[] v = new float[8 * 1024];
    short[] ix = new short[3 * 2048];
    int vc, ic;

    MeshKit(PaletteSwatches pal) {
        this.pal = pal;
    }

    int triangles() {
        return ic / 3;
    }

    int vert(float x, float y, float z, float nx, float ny, float nz, int sw, float t) {
        if ((vc + 1) * 8 > v.length) v = Arrays.copyOf(v, v.length * 2);
        if (vc >= 65535) throw new IllegalStateException("more than 65535 vertices");
        int o = vc * 8;
        v[o] = x;
        v[o + 1] = y;
        v[o + 2] = z;
        v[o + 3] = nx;
        v[o + 4] = ny;
        v[o + 5] = nz;
        v[o + 6] = pal.uRamp(sw, t);
        v[o + 7] = pal.v(sw);
        return vc++;
    }

    void tri(int a, int b, int c) {
        if (ic + 3 > ix.length) ix = Arrays.copyOf(ix, ix.length * 2);
        ix[ic++] = (short) a;
        ix[ic++] = (short) b;
        ix[ic++] = (short) c;
    }

    /**
     * A flat triangle with its face normal; the winding is turned so that the normal points to the side of {@code hint}.
     */
    void flatTri(float[] a, float[] b, float[] c, float ta, float tb, float tc, int sw, float hx, float hy, float hz) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-9f) return;
        nx /= len;
        ny /= len;
        nz /= len;
        boolean flip = nx * hx + ny * hy + nz * hz < 0f;
        if (flip) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        int ia = vert(a[0], a[1], a[2], nx, ny, nz, sw, ta);
        int ib = vert(b[0], b[1], b[2], nx, ny, nz, sw, tb);
        int ic2 = vert(c[0], c[1], c[2], nx, ny, nz, sw, tc);
        if (flip) tri(ia, ic2, ib);
        else tri(ia, ib, ic2);
    }

    /** A flat quad (a, b, c, d in order) as two triangles, facing the side of the hint. */
    void flatQuad(float[] a, float[] b, float[] c, float[] d, float ta, float tb, float tc, float td, int sw, float hx, float hy,
                  float hz) {
        flatTri(a, b, c, ta, tb, tc, sw, hx, hy, hz);
        flatTri(a, c, d, ta, tc, td, sw, hx, hy, hz);
    }

    /**
     * A smooth surface from rings (rows) of vertices: {@code p[r][s] = {x, y, z}}, {@code t[r][s]} the ramp position. Rings go up
     * and sides run round counter-clockwise seen from above (x towards z), which makes the faces point out. Collapsed triangles
     * (a ring that is a single point) are left out. Normals are the average of the faces round each vertex.
     */
    void grid(float[][][] p, float[][] t, int sw, boolean wrap) {
        int rings = p.length, sides = p[0].length;
        float[][][] n = new float[rings][sides][3];
        int sCells = wrap ? sides : sides - 1;
        for (int r = 0; r + 1 < rings; r++) {
            for (int s = 0; s < sCells; s++) {
                int s2 = (s + 1) % sides;
                faceNormal(p[r][s], p[r + 1][s2], p[r][s2], n[r][s], n[r + 1][s2], n[r][s2]);
                faceNormal(p[r][s], p[r + 1][s], p[r + 1][s2], n[r][s], n[r + 1][s], n[r + 1][s2]);
            }
        }
        int base = vc;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sides; s++) {
                float[] q = n[r][s];
                float l = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                if (l < 1e-9f) {
                    q[0] = 0f;
                    q[1] = 1f;
                    q[2] = 0f;
                } else {
                    q[0] /= l;
                    q[1] /= l;
                    q[2] /= l;
                }
                vert(p[r][s][0], p[r][s][1], p[r][s][2], q[0], q[1], q[2], sw, Math.max(0f, Math.min(1f, t[r][s])));
            }
        }
        for (int r = 0; r + 1 < rings; r++) {
            for (int s = 0; s < sCells; s++) {
                int s2 = (s + 1) % sides;
                int a = base + r * sides + s, b = base + r * sides + s2, c = base + (r + 1) * sides + s2, d = base + (r + 1) * sides + s;
                if (area(p[r][s], p[r + 1][s2], p[r][s2]) > 1e-9f) tri(a, c, b);
                if (area(p[r][s], p[r + 1][s], p[r + 1][s2]) > 1e-9f) tri(a, d, c);
            }
        }
    }

    private static float area(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        return nx * nx + ny * ny + nz * nz;
    }

    private static void faceNormal(float[] a, float[] b, float[] c, float[] na, float[] nb, float[] nc) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;   // area weighted
        for (float[] q : new float[][] {na, nb, nc}) {
            q[0] += nx;
            q[1] += ny;
            q[2] += nz;
        }
    }

    /**
     * A tube along a path, smooth normals, rings at the path points, rounded closed end when {@code dome} is true.
     * @param path points {x, y, z}
     * @param rad radius at each point
     * @param tone ramp position at each point (the rib ridges are lighter, the grooves darker)
     * @param sides sides (even when {@code valley} < 1)
     * @param valley radius factor of every second side (1 = round, 0.86 = ribbed)
     */
    void tube(float[][] path, float[] rad, float[] tone, int sides, float valley, int sw, boolean dome) {
        int n = path.length;
        int extra = dome ? 3 : 0;
        float[][] pts = new float[n + extra][];
        float[] r = new float[n + extra], tn = new float[n + extra];
        for (int i = 0; i < n; i++) {
            pts[i] = path[i];
            r[i] = rad[i];
            tn[i] = tone[i];
        }
        float[] tang = new float[3];
        dir(path, n - 1, tang);
        if (dome) {
            float rr = rad[n - 1];
            float[][] f = {{0.45f, 0.9f, 0.08f}, {0.85f, 0.55f, 0.16f}, {1.0f, 0f, 0.24f}};
            for (int i = 0; i < 3; i++) {
                pts[n + i] = new float[] {path[n - 1][0] + tang[0] * rr * f[i][0], path[n - 1][1] + tang[1] * rr * f[i][0],
                        path[n - 1][2] + tang[2] * rr * f[i][0]};
                r[n + i] = rr * f[i][1];
                tn[n + i] = tone[n - 1] + f[i][2];
            }
        }
        int m = pts.length;
        float[][][] p = new float[m][sides][3];
        float[][] t = new float[m][sides];
        float[] nrm = new float[3];
        float[] td = new float[3];
        dir(pts, 0, td);
        float[] ref = Math.abs(td[1]) < 0.9f ? new float[] {0f, 1f, 0f} : new float[] {1f, 0f, 0f};
        float d0 = ref[0] * td[0] + ref[1] * td[1] + ref[2] * td[2];
        nrm[0] = ref[0] - td[0] * d0;
        nrm[1] = ref[1] - td[1] * d0;
        nrm[2] = ref[2] - td[2] * d0;
        norm(nrm);
        for (int i = 0; i < m; i++) {
            dir(pts, i, td);
            float d = nrm[0] * td[0] + nrm[1] * td[1] + nrm[2] * td[2];
            nrm[0] -= td[0] * d;
            nrm[1] -= td[1] * d;
            nrm[2] -= td[2] * d;
            norm(nrm);
            // b = n x tangent
            float bx = nrm[1] * td[2] - nrm[2] * td[1], by = nrm[2] * td[0] - nrm[0] * td[2], bz = nrm[0] * td[1] - nrm[1] * td[0];
            for (int s = 0; s < sides; s++) {
                double a = 2.0 * Math.PI * s / sides;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float rr = r[i] * ((s & 1) == 0 ? 1f : valley);
                p[i][s][0] = pts[i][0] + (nrm[0] * ca + bx * sa) * rr;
                p[i][s][1] = pts[i][1] + (nrm[1] * ca + by * sa) * rr;
                p[i][s][2] = pts[i][2] + (nrm[2] * ca + bz * sa) * rr;
                t[i][s] = tn[i] + (valley < 1f ? ((s & 1) == 0 ? 0.12f : -0.12f) : 0f);
            }
        }
        grid(p, t, sw, true);
    }

    private static void dir(float[][] pts, int i, float[] out) {
        int a = Math.max(0, i - 1), b = Math.min(pts.length - 1, i + 1);
        out[0] = pts[b][0] - pts[a][0];
        out[1] = pts[b][1] - pts[a][1];
        out[2] = pts[b][2] - pts[a][2];
        norm(out);
    }

    static void norm(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (l < 1e-9f) {
            a[0] = 0f;
            a[1] = 1f;
            a[2] = 0f;
            return;
        }
        a[0] /= l;
        a[1] /= l;
        a[2] /= l;
    }

    MeshData mesh() {
        return mesh("prop");
    }

    MeshData mesh(String name) {
        MeshData.Part part = new MeshData.Part(name, -1, new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f, 1f},
                new float[] {1f, 1f, 1f}, Arrays.copyOf(v, vc * 8), Arrays.copyOf(ix, ic), vc);
        return new MeshData(false, new MeshData.Part[] {part});
    }
}
