package com.wiggleplay.app.gl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A 3D model made of one or more parts, read from the small {@code .mesh} format written by
 * {@code tools/glb_to_mesh.py}. Plain Java (no Android classes) so it can be tested on the JVM.
 *
 * <p>Vertices stay in each part's local space; the part's node transform (translation, rotation as a
 * quaternion x,y,z,w, scale) is kept separately so a wheel can spin about its own centre.
 */
public final class MeshData {

    /** Floats per vertex: position (3), normal (3), uv (2). */
    public static final int FLOATS_PER_VERTEX = 8;

    /** Whether back faces must be drawn (both sides of every triangle). */
    public final boolean doubleSided;
    /** The parts, in file order. */
    public final Part[] parts;

    /**
     * Creates a model.
     * @param doubleSided whether back-face culling must be off
     * @param parts the parts
     */
    public MeshData(boolean doubleSided, Part[] parts) {
        this.doubleSided = doubleSided;
        this.parts = parts;
    }

    /** One part (a node with a mesh) of a model. */
    public static final class Part {
        /** Node name, for example {@code wheel-front-left}. */
        public final String name;
        /** Index of the parent part, or -1 for a root. */
        public final int parent;
        /** Local translation x,y,z. */
        public final float[] translation;
        /** Local rotation as a quaternion x,y,z,w. */
        public final float[] rotation;
        /** Local scale x,y,z. */
        public final float[] scale;
        /** Interleaved vertices: px,py,pz, nx,ny,nz, u,v. */
        public final float[] vertices;
        /** Triangle indices (unsigned 16 bit; read with {@code & 0xFFFF}). */
        public final short[] indices;
        /** Number of vertices. */
        public final int vertexCount;

        /**
         * Creates a part.
         * @param name node name
         * @param parent parent index or -1
         * @param translation x,y,z
         * @param rotation quaternion x,y,z,w
         * @param scale x,y,z
         * @param vertices interleaved vertices
         * @param indices triangle indices
         * @param vertexCount number of vertices in {@code vertices}
         */
        public Part(String name, int parent, float[] translation, float[] rotation, float[] scale,
                    float[] vertices, short[] indices, int vertexCount) {
            this.name = name;
            this.parent = parent;
            this.translation = translation;
            this.rotation = rotation;
            this.scale = scale;
            this.vertices = vertices;
            this.indices = indices;
            this.vertexCount = vertexCount;
        }
    }

    /**
     * Finds a part by name.
     * @param name the node name
     * @return the part index, or -1 if there is no such part
     */
    public int partIndex(String name) {
        for (int i = 0; i < parts.length; i++) if (parts[i].name.equals(name)) return i;
        return -1;
    }

    /**
     * Total number of triangles of all parts.
     * @return the triangle count
     */
    public int triangleCount() {
        int n = 0;
        for (Part p : parts) n += p.indices.length / 3;
        return n;
    }

    /**
     * Reads a model. The stream is read to its end but not closed.
     * @param in the {@code .mesh} bytes
     * @return the model
     * @throws IOException on a wrong magic or version, or a truncated or inconsistent file
     */
    public static MeshData read(InputStream in) throws IOException {
        byte[] all = readAll(in);
        ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        try {
            if (all.length < 4 || all[0] != 'M' || all[1] != 'T' || all[2] != 'V' || all[3] != 'M') {
                throw new IOException("Not a .mesh file: bad magic (expected MTVM)");
            }
            b.position(4);
            int version = b.getShort() & 0xFFFF;
            if (version != 1) throw new IOException("Unsupported .mesh version " + version + " (expected 1)");
            int partCount = b.getShort() & 0xFFFF;
            boolean doubleSided = b.get() != 0;
            Part[] parts = new Part[partCount];
            for (int i = 0; i < partCount; i++) {
                int nameLen = b.getShort() & 0xFFFF;
                byte[] nb = new byte[nameLen];
                b.get(nb);
                String name = new String(nb, StandardCharsets.UTF_8);
                int parent = b.getShort();
                float[] t = new float[3];
                float[] r = new float[4];
                float[] s = new float[3];
                for (int k = 0; k < 3; k++) t[k] = b.getFloat();
                for (int k = 0; k < 4; k++) r[k] = b.getFloat();
                for (int k = 0; k < 3; k++) s[k] = b.getFloat();
                long vc = b.getInt() & 0xFFFFFFFFL;
                long ic = b.getInt() & 0xFFFFFFFFL;
                if (vc > 65535) throw new IOException("Part " + name + " has " + vc + " vertices (more than 65535)");
                if (vc * 32 + ic * 2 > b.remaining()) throw new IOException("Part " + name + " is cut off (file too short)");
                float[] v = new float[(int) vc * FLOATS_PER_VERTEX];
                for (int k = 0; k < v.length; k++) v[k] = b.getFloat();
                short[] idx = new short[(int) ic];
                for (int k = 0; k < idx.length; k++) {
                    idx[k] = b.getShort();
                    if ((idx[k] & 0xFFFF) >= vc) throw new IOException("Part " + name + " has an index outside its vertices");
                }
                parts[i] = new Part(name, parent, t, r, s, v, idx, (int) vc);
            }
            return new MeshData(doubleSided, parts);
        } catch (java.nio.BufferUnderflowException e) {
            throw new IOException("Mesh file is cut off (file too short)", e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        byte[] buf = new byte[16384];
        byte[] out = new byte[65536];
        int n = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            if (n + r > out.length) out = java.util.Arrays.copyOf(out, Math.max(out.length * 2, n + r));
            System.arraycopy(buf, 0, out, n, r);
            n += r;
        }
        return java.util.Arrays.copyOf(out, n);
    }
}
