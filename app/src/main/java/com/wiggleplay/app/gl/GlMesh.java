package com.wiggleplay.app.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The GPU copy of a model (one vertex buffer and one index buffer per part) or of one static chunk.
 * It keeps the CPU arrays so the buffers can be uploaded again if the GL context is ever recreated.
 * Everything except the constructors and getters must be called on the GL thread.
 */
public final class GlMesh {

    private static final int STRIDE = MeshData.FLOATS_PER_VERTEX * 4;

    private final float[][] vertices;
    private final short[][] indices;
    private final int[] vertexCounts;
    private final boolean doubleSided;
    private final int[] vbo;
    private final int[] ibo;
    private boolean uploaded;

    private GlMesh(float[][] vertices, short[][] indices, int[] vertexCounts, boolean doubleSided) {
        this.vertices = vertices;
        this.indices = indices;
        this.vertexCounts = vertexCounts;
        this.doubleSided = doubleSided;
        this.vbo = new int[vertices.length];
        this.ibo = new int[vertices.length];
    }

    /**
     * Wraps a model (nothing is uploaded yet).
     * @param m the model
     * @return the mesh, with one buffer pair per part
     */
    public static GlMesh of(MeshData m) {
        int n = m.parts.length;
        float[][] v = new float[n][];
        short[][] ix = new short[n][];
        int[] vc = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = m.parts[i].vertices;
            ix[i] = m.parts[i].indices;
            vc[i] = m.parts[i].vertexCount;
        }
        return new GlMesh(v, ix, vc, m.doubleSided);
    }

    /**
     * Wraps a merged static chunk (nothing is uploaded yet).
     * @param c the chunk
     * @return the mesh, with a single buffer pair
     */
    public static GlMesh of(StaticBatcher.Chunk c) {
        return new GlMesh(new float[][] {c.vertices}, new short[][] {c.indices}, new int[] {c.vertexCount}, c.doubleSided);
    }

    /**
     * Whether back faces must be drawn.
     * @return true for double-sided
     */
    public boolean doubleSided() { return doubleSided; }

    /**
     * Number of parts.
     * @return the part count
     */
    public int partCount() { return vertices.length; }

    /**
     * Whether the buffers exist on the GPU.
     * @return true after {@link #upload()}
     */
    public boolean isUploaded() { return uploaded; }

    /** Creates and fills the buffers (GL thread). One-off cost at load time; it allocates temporary direct buffers. */
    public void upload() {
        if (uploaded) return;
        GLES20.glGenBuffers(vbo.length, vbo, 0);
        GLES20.glGenBuffers(ibo.length, ibo, 0);
        for (int i = 0; i < vbo.length; i++) {
            int vb = vertexCounts[i] * STRIDE;
            ByteBuffer vbuf = ByteBuffer.allocateDirect(vb).order(ByteOrder.nativeOrder());
            vbuf.asFloatBuffer().put(vertices[i], 0, vertexCounts[i] * MeshData.FLOATS_PER_VERTEX);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[i]);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vb, vbuf, GLES20.GL_STATIC_DRAW);

            int ib = indices[i].length * 2;
            ByteBuffer ibuf = ByteBuffer.allocateDirect(ib).order(ByteOrder.nativeOrder());
            ibuf.asShortBuffer().put(indices[i]);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[i]);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, ib, ibuf, GLES20.GL_STATIC_DRAW);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        uploaded = true;
    }

    /**
     * Draws one part with the given attribute slots (GL thread, allocation free).
     * @param part part index
     * @param aPos position attribute location
     * @param aNormal normal attribute location
     * @param aUv texture coordinate attribute location
     * @return the number of triangles drawn (0 if the part does not exist)
     */
    public int draw(int part, int aPos, int aNormal, int aUv) {
        if (part < 0 || part >= vbo.length) return 0;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[part]);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE, 0);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, STRIDE, 12);
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, STRIDE, 24);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo[part]);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices[part].length, GLES20.GL_UNSIGNED_SHORT, 0);
        return indices[part].length / 3;
    }

    /** Deletes the GPU buffers (GL thread, while the context still exists). The CPU arrays stay. */
    public void release() {
        if (!uploaded) return;
        GLES20.glDeleteBuffers(vbo.length, vbo, 0);
        GLES20.glDeleteBuffers(ibo.length, ibo, 0);
        uploaded = false;
    }

    /** Forgets the GPU buffers without deleting them: the context they belonged to is gone. */
    public void invalidate() {
        uploaded = false;
    }
}
