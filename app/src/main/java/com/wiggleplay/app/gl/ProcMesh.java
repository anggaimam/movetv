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
