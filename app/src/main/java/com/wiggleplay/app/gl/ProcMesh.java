package com.wiggleplay.app.gl;

/**
 * Small meshes made in code (coin, star, shadow disc, ground). Each is a single-part {@link MeshData}
 * meant to be drawn flat (with a colour from the render list, not the palette texture), so uvs are 0.
 * Triangles wind counter-clockwise seen from outside, so back-face culling can stay on.
 * Plain Java (no Android classes). Coordinates: +Y up, right-handed.
 */
public final class ProcMesh {

    private ProcMesh() { }

    /**
     * A coin standing upright facing +Z with the default size (radius 0.6, thickness 0.15, 16 sides).
     * @return the mesh
     */
    public static MeshData coin() {
        return coin(0.6f, 0.15f, 16);
    }

    /**
     * A coin standing upright facing +Z (a disc in the XY plane, thickness along Z).
     * Vertices: 4 * sides + 2; triangles: 4 * sides.
     * @param radius coin radius
     * @param thickness thickness along Z
     * @param sides number of sides (at least 3)
     * @return the mesh
     */
    public static MeshData coin(float radius, float thickness, int sides) {
        if (sides < 3) throw new IllegalArgumentException("sides must be at least 3");
        float h = thickness * 0.5f;
        Buf b = new Buf(4 * sides + 2, 12 * sides);
        int front = b.v(0, 0, h, 0, 0, 1);
        int back = b.v(0, 0, -h, 0, 0, -1);
        int[] fr = new int[sides];
        int[] br = new int[sides];
        for (int i = 0; i < sides; i++) {
            double a = 2.0 * Math.PI * i / sides;
            float x = (float) (Math.cos(a) * radius), y = (float) (Math.sin(a) * radius);
            fr[i] = b.v(x, y, h, 0, 0, 1);
            br[i] = b.v(x, y, -h, 0, 0, -1);
        }
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            b.t(front, fr[i], fr[j]);
            b.t(back, br[j], br[i]);
        }
        int[] rf = new int[sides];
        int[] rb = new int[sides];
        for (int i = 0; i < sides; i++) {
            double a = 2.0 * Math.PI * i / sides;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            rf[i] = b.v(c * radius, s * radius, h, c, s, 0);
            rb[i] = b.v(c * radius, s * radius, -h, c, s, 0);
        }
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            b.t(rf[i], rb[j], rf[j]);
            b.t(rf[i], rb[i], rb[j]);
        }
        return b.mesh("coin");
    }

    /**
     * A five-point star standing upright facing +Z with the default size (outer 0.9, inner 0.4, depth 0.2).
     * @return the mesh
     */
    public static MeshData star() {
        return star(0.9f, 0.4f, 0.2f);
    }

    /**
     * A five-point star in the XY plane (one point up), thickness along Z.
     * Vertices: 62; triangles: 40.
     * @param outer distance of the points from the centre
     * @param inner distance of the inner corners from the centre
     * @param depth thickness along Z
     * @return the mesh
     */
    public static MeshData star(float outer, float inner, float depth) {
        final int n = 10;
        float h = depth * 0.5f;
        float[] px = new float[n], py = new float[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI / 2.0 + 2.0 * Math.PI * i / n;
            float r = (i % 2 == 0) ? outer : inner;
            px[i] = (float) (Math.cos(a) * r);
            py[i] = (float) (Math.sin(a) * r);
        }
        Buf b = new Buf(2 * (n + 1) + 4 * n, 3 * (2 * n + 2 * n));
        int front = b.v(0, 0, h, 0, 0, 1);
        int back = b.v(0, 0, -h, 0, 0, -1);
        int[] fr = new int[n];
        int[] br = new int[n];
        for (int i = 0; i < n; i++) {
            fr[i] = b.v(px[i], py[i], h, 0, 0, 1);
            br[i] = b.v(px[i], py[i], -h, 0, 0, -1);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            b.t(front, fr[i], fr[j]);
            b.t(back, br[j], br[i]);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float dx = px[j] - px[i], dy = py[j] - py[i];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float nx = dy / len, ny = -dx / len;
            int af = b.v(px[i], py[i], h, nx, ny, 0);
            int ab = b.v(px[i], py[i], -h, nx, ny, 0);
            int bf = b.v(px[j], py[j], h, nx, ny, 0);
            int bb = b.v(px[j], py[j], -h, nx, ny, 0);
            b.t(af, bb, bf);
            b.t(af, ab, bb);
        }
        return b.mesh("star");
    }

    /**
     * A flat disc lying on Y = 0 facing up, with 16 sides (for the truck shadow).
     * @param radius disc radius
     * @return the mesh
     */
    public static MeshData disc(float radius) {
        return disc(radius, 16);
    }

    /**
     * A flat disc lying on Y = 0 facing up. Vertices: sides + 1; triangles: sides.
     * @param radius disc radius
     * @param sides number of sides (at least 3)
     * @return the mesh
     */
    public static MeshData disc(float radius, int sides) {
        if (sides < 3) throw new IllegalArgumentException("sides must be at least 3");
        Buf b = new Buf(sides + 1, 3 * sides);
        int c = b.v(0, 0, 0, 0, 1, 0);
        int[] r = new int[sides];
        for (int i = 0; i < sides; i++) {
            double a = 2.0 * Math.PI * i / sides;
            r[i] = b.v((float) (Math.cos(a) * radius), 0, (float) (Math.sin(a) * radius), 0, 1, 0);
        }
        for (int i = 0; i < sides; i++) b.t(c, r[(i + 1) % sides], r[i]);
        return b.mesh("disc");
    }

    /**
     * A flat square ground quad centred on the origin at Y = -0.02, facing up. 4 vertices, 2 triangles.
     * @param size side length
     * @return the mesh
     */
    public static MeshData ground(float size) {
        float h = size * 0.5f, y = -0.02f;
        Buf b = new Buf(4, 6);
        int v0 = b.v(-h, y, -h, 0, 1, 0);
        int v1 = b.v(h, y, -h, 0, 1, 0);
        int v2 = b.v(h, y, h, 0, 1, 0);
        int v3 = b.v(-h, y, h, 0, 1, 0);
        b.t(v0, v2, v1);
        b.t(v0, v3, v2);
        return b.mesh("ground");
    }

    /**
     * A flat square ground grid centred on the origin at Y = -0.02, facing up, split into cells x cells squares.
     * Fog and light are worked out per vertex, so a big ground needs vertices near the camera: a single 400-unit quad
     * has only far corners and is fogged to the sky colour everywhere (T-23). (cells + 1)^2 vertices, 2 cells^2
     * triangles.
     * @param size side length
     * @param cells squares per side (at least 1)
     * @return the mesh
     */
    public static MeshData groundGrid(float size, int cells) {
        if (cells < 1) throw new IllegalArgumentException("cells must be at least 1");
        float h = size * 0.5f, y = -0.02f, step = size / cells;
        int n = cells + 1;
        Buf b = new Buf(n * n, 6 * cells * cells);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) b.v(-h + i * step, y, -h + j * step, 0, 1, 0);
        }
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int v0 = j * n + i, v1 = v0 + 1, v3 = v0 + n, v2 = v3 + 1;
                b.t(v0, v2, v1);
                b.t(v0, v3, v2);
            }
        }
        return b.mesh("ground");
    }

    // ---- T-23 Part C: shiny toy coins and the golden star, coloured from swatches in the palette texture ----

    /** Palette swatch of the coin face (mustard). */
    public static final int SWATCH_COIN_FACE = 0;
    /** Palette swatch of the raised rim (darker mustard). */
    public static final int SWATCH_COIN_RIM = 1;
    /** Palette swatch of the grooves and the bevel (dark mustard). */
    public static final int SWATCH_COIN_GROOVE = 2;
    /** Palette swatch of the embossed star on the coin (light). */
    public static final int SWATCH_COIN_STAR = 3;
    /** Palette swatch of the golden star's faces. */
    public static final int SWATCH_STAR_FACE = 4;
    /** Palette swatch of the golden star's lighter facets (the ridge). */
    public static final int SWATCH_STAR_RIDGE = 5;
    /** Palette swatch of the golden star's sides. */
    public static final int SWATCH_STAR_SIDE = 6;
    /** The swatch colours, 0xRRGGBB, in swatch order. {@code tools/add_coin_swatches.py} paints them into the palette. */
    public static final int[] SWATCH_RGB = {0xF2C572, 0xD9A441, 0xC48E32, 0xFFE08A, 0xFFC933, 0xFFE680, 0xD9A21E};
    /** Side of one swatch block in the 512 x 512 palette texture; swatch k sits at x = 16 k, y = 0 (an unused corner). */
    public static final int SWATCH_BLOCK = 16;

    private static final float ATLAS = 512f;
    private static final int COIN_FACE_SIDES = 16, COIN_EDGE_STRIPS = 36, STAR_POINTS = 5;

    /**
     * A shiny toy coin standing upright facing +Z: radius 0.6, thickness 0.15, a raised rim, a sloped inner bevel, a dark
     * groove band, an embossed five-point star on both faces and a reeded edge of 36 alternating light and dark strips.
     * One part, one draw call (draw it with {@code flat = false}: every vertex takes its colour from a palette swatch).
     * 356 triangles, 472 vertices.
     * @return the mesh
     */
    public static MeshData coinReeded() {
        final float r = 0.6f, h = 0.075f, fieldZ = h - 0.03f;
        Buf b = new Buf(2 * (3 * 2 * COIN_FACE_SIDES + 1 + COIN_FACE_SIDES + 1 + 2 * STAR_POINTS + 4 * 2 * STAR_POINTS)
                + 4 * COIN_EDGE_STRIPS, 3 * (2 * (3 * 2 * COIN_FACE_SIDES + COIN_FACE_SIDES + 2 * STAR_POINTS
                + 4 * STAR_POINTS) + 2 * COIN_EDGE_STRIPS));
        for (int s = 1; s >= -1; s -= 2) {
            b.ring(s, COIN_FACE_SIDES, 0.54f, h, r, h, 0f, 1f, SWATCH_COIN_RIM);
            b.ring(s, COIN_FACE_SIDES, 0.46f, fieldZ, 0.54f, h, -0.351f, 0.936f, SWATCH_COIN_GROOVE);
            b.ring(s, COIN_FACE_SIDES, 0.38f, fieldZ, 0.46f, fieldZ, 0f, 1f, SWATCH_COIN_GROOVE);
            // the face inside the groove band: a fan around the centre
            int c = b.vu(0, 0, s * fieldZ, 0, 0, s, SWATCH_COIN_FACE);
            int[] ring = new int[COIN_FACE_SIDES];
            for (int i = 0; i < COIN_FACE_SIDES; i++) {
                double a = 2.0 * Math.PI * i / COIN_FACE_SIDES;
                ring[i] = b.vu((float) (Math.cos(a) * 0.38f), (float) (Math.sin(a) * 0.38f), s * fieldZ, 0, 0, s, SWATCH_COIN_FACE);
            }
            for (int i = 0; i < COIN_FACE_SIDES; i++) b.t2(s, c, ring[i], ring[(i + 1) % COIN_FACE_SIDES]);
            // the embossed star, raised to the rim's height
            final int n = 2 * STAR_POINTS;
            float[] px = new float[n], py = new float[n];
            for (int i = 0; i < n; i++) {
                double a = Math.PI / 2.0 + 2.0 * Math.PI * i / n;
                float rr = (i % 2 == 0) ? 0.30f : 0.13f;
                px[i] = (float) (Math.cos(a) * rr);
                py[i] = (float) (Math.sin(a) * rr);
            }
            int top = b.vu(0, 0, s * h, 0, 0, s, SWATCH_COIN_STAR);
            int[] tp = new int[n];
            for (int i = 0; i < n; i++) tp[i] = b.vu(px[i], py[i], s * h, 0, 0, s, SWATCH_COIN_STAR);
            for (int i = 0; i < n; i++) b.t2(s, top, tp[i], tp[(i + 1) % n]);
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float dx = px[j] - px[i], dy = py[j] - py[i];
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                float nx = dy / len, ny = -dx / len;
                int af = b.vu(px[i], py[i], s * h, nx, ny, 0, SWATCH_COIN_GROOVE);
                int ab = b.vu(px[i], py[i], s * fieldZ, nx, ny, 0, SWATCH_COIN_GROOVE);
                int bf = b.vu(px[j], py[j], s * h, nx, ny, 0, SWATCH_COIN_GROOVE);
                int bb = b.vu(px[j], py[j], s * fieldZ, nx, ny, 0, SWATCH_COIN_GROOVE);
                b.t2(s, af, bb, bf);
                b.t2(s, af, ab, bb);
            }
        }
        // the reeded edge: flat strips, light and dark in turn, each lit by its own normal
        for (int i = 0; i < COIN_EDGE_STRIPS; i++) {
            double a0 = 2.0 * Math.PI * i / COIN_EDGE_STRIPS, a1 = 2.0 * Math.PI * (i + 1) / COIN_EDGE_STRIPS;
            double am = 0.5 * (a0 + a1);
            float nx = (float) Math.cos(am), ny = (float) Math.sin(am);
            int sw = (i % 2 == 0) ? SWATCH_COIN_FACE : SWATCH_COIN_GROOVE;
            int f0 = b.vu((float) (Math.cos(a0) * r), (float) (Math.sin(a0) * r), h, nx, ny, 0, sw);
            int k0 = b.vu((float) (Math.cos(a0) * r), (float) (Math.sin(a0) * r), -h, nx, ny, 0, sw);
            int f1 = b.vu((float) (Math.cos(a1) * r), (float) (Math.sin(a1) * r), h, nx, ny, 0, sw);
            int k1 = b.vu((float) (Math.cos(a1) * r), (float) (Math.sin(a1) * r), -h, nx, ny, 0, sw);
            b.t(f0, k1, f1);
            b.t(f0, k0, k1);
        }
        return b.mesh("coin");
    }

    /**
     * The golden star pickup, polished: a faceted pyramid on both faces (the facets are lighter and darker in turn, so a
     * ridge runs from every point to the centre) and dark sides. Same size as {@link #star()}. 40 triangles, 100 vertices.
     * Drawn with {@code flat = false} (palette swatches); the glow behind it can use the same mesh drawn flat.
     * @param outer distance of the points from the centre
     * @param inner distance of the inner corners from the centre
     * @param depth thickness along Z
     * @return the mesh
     */
    public static MeshData starBevelled(float outer, float inner, float depth) {
        final int n = 2 * STAR_POINTS;
        float h = depth * 0.5f, e = h * 0.35f;
        float[] px = new float[n], py = new float[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI / 2.0 + 2.0 * Math.PI * i / n;
            float rr = (i % 2 == 0) ? outer : inner;
            px[i] = (float) (Math.cos(a) * rr);
            py[i] = (float) (Math.sin(a) * rr);
        }
        Buf b = new Buf(6 * n + 4 * n, 3 * (2 * n + 2 * n));
        for (int s = 1; s >= -1; s -= 2) {
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                // facet normal from the geometry: apex at z = s h, the two points at z = s e
                float ux = px[i], uy = py[i], uz = s * (e - h);
                float vx = px[j], vy = py[j], vz = s * (e - h);
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                if (s < 0) { nx = -nx; ny = -ny; nz = -nz; }
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len; ny /= len; nz /= len;
                int sw = (i % 2 == 0) ? SWATCH_STAR_RIDGE : SWATCH_STAR_FACE;
                int a = b.vu(0, 0, s * h, nx, ny, nz, sw);
                int p = b.vu(px[i], py[i], s * e, nx, ny, nz, sw);
                int q = b.vu(px[j], py[j], s * e, nx, ny, nz, sw);
                b.t2(s, a, p, q);
            }
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float dx = px[j] - px[i], dy = py[j] - py[i];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            float nx = dy / len, ny = -dx / len;
            int af = b.vu(px[i], py[i], e, nx, ny, 0, SWATCH_STAR_SIDE);
            int ab = b.vu(px[i], py[i], -e, nx, ny, 0, SWATCH_STAR_SIDE);
            int bf = b.vu(px[j], py[j], e, nx, ny, 0, SWATCH_STAR_SIDE);
            int bb = b.vu(px[j], py[j], -e, nx, ny, 0, SWATCH_STAR_SIDE);
            b.t(af, bb, bf);
            b.t(af, ab, bb);
        }
        return b.mesh("star");
    }

    /** Collects vertices and triangles. */
    private static final class Buf {
        final float[] vertices;
        final short[] indices;
        int vc;
        int ic;

        Buf(int vertexCount, int indexCount) {
            vertices = new float[vertexCount * MeshData.FLOATS_PER_VERTEX];
            indices = new short[indexCount];
        }

        int v(float x, float y, float z, float nx, float ny, float nz) {
            int o = vc * MeshData.FLOATS_PER_VERTEX;
            vertices[o] = x;
            vertices[o + 1] = y;
            vertices[o + 2] = z;
            vertices[o + 3] = nx;
            vertices[o + 4] = ny;
            vertices[o + 5] = nz;
            return vc++;
        }

        /** A vertex whose colour is the middle of palette swatch {@code swatch} (u, v inside its 16 x 16 block). */
        int vu(float x, float y, float z, float nx, float ny, float nz, int swatch) {
            int i = v(x, y, z, nx, ny, nz);
            int o = i * MeshData.FLOATS_PER_VERTEX;
            vertices[o + 6] = (swatch * SWATCH_BLOCK + SWATCH_BLOCK / 2f) / ATLAS;
            vertices[o + 7] = (SWATCH_BLOCK / 2f) / ATLAS;
            return i;
        }

        /** A triangle that is counter-clockwise seen from +Z-side {@code side} (+1), or mirrored for the back (-1). */
        void t2(int side, int a, int b, int c) {
            if (side > 0) t(a, b, c);
            else t(a, c, b);
        }

        /**
         * An annulus from (r1, z1) to (r2, z2), {@code sides} quads, on the front (side +1) or back (-1) of the coin; the
         * vertex normal is (nr cos, nr sin, side nz).
         */
        void ring(int side, int sides, float r1, float z1, float r2, float z2, float nr, float nz, int swatch) {
            int[] in = new int[sides], out = new int[sides];
            for (int i = 0; i < sides; i++) {
                double a = 2.0 * Math.PI * i / sides;
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                in[i] = vu(c * r1, s * r1, side * z1, c * nr, s * nr, side * nz, swatch);
                out[i] = vu(c * r2, s * r2, side * z2, c * nr, s * nr, side * nz, swatch);
            }
            for (int i = 0; i < sides; i++) {
                int j = (i + 1) % sides;
                t2(side, in[i], out[i], out[j]);
                t2(side, in[i], out[j], in[j]);
            }
        }

        void t(int a, int b, int c) {
            indices[ic++] = (short) a;
            indices[ic++] = (short) b;
            indices[ic++] = (short) c;
        }

        MeshData mesh(String name) {
            if (vc != vertices.length / MeshData.FLOATS_PER_VERTEX || ic != indices.length) {
                throw new IllegalStateException(name + ": buffer sizes were miscounted");
            }
            MeshData.Part p = new MeshData.Part(name, -1, new float[] {0, 0, 0}, new float[] {0, 0, 0, 1},
                    new float[] {1, 1, 1}, vertices, indices, vc);
            return new MeshData(false, new MeshData.Part[] {p});
        }
    }
}
