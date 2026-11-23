package com.wiggleplay.app.gl;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Merges many static scenery pieces into a few big vertex arrays (chunks) so the renderer needs only a
 * few draw calls. Plain Java (no Android classes), runs once when a level is built.
 *
 * <p>All placements whose tile cell falls in the same {@code chunkCells x chunkCells} block (and that
 * share the same culling mode) go into one chunk. Positions and normals are pre-transformed by the
 * placement matrix (and by each part's own node transform). A chunk that would pass 65,535 vertices is
 * split. Every chunk has a bounding sphere for culling.
 */
public final class StaticBatcher {

    /** The default block size in tile cells. */
    public static final int DEFAULT_CHUNK_CELLS = 4;
    /** The most vertices one chunk may hold (16-bit indices). */
    public static final int MAX_CHUNK_VERTICES = 65535;

    private StaticBatcher() { }

    /** Finds meshes by name. */
    public interface MeshLibrary {
        /**
         * Looks a mesh up.
         * @param name the mesh name, for example {@code decoration-forest}
         * @return the mesh, or null if unknown
         */
        MeshData get(String name);
    }

    /** One static piece of scenery: which mesh, where, and in which tile cell. */
    public static final class Placement {
        /** Mesh name. */
        public final String meshName;
        /** The model matrix (16 floats, column-major); not copied. */
        public final float[] matrix;
        /** Tile cell x (column). */
        public final int cellX;
        /** Tile cell z (row). */
        public final int cellZ;

        /**
         * Creates a placement.
         * @param meshName mesh name
         * @param matrix model matrix, 16 floats
         * @param cellX tile cell x
         * @param cellZ tile cell z
         */
        public Placement(String meshName, float[] matrix, int cellX, int cellZ) {
            this.meshName = meshName;
            this.matrix = matrix;
            this.cellX = cellX;
            this.cellZ = cellZ;
        }
    }

    /** A merged piece of scenery ready to upload. */
    public static final class Chunk {
        /** Interleaved vertices: px,py,pz, nx,ny,nz, u,v (only the first {@code vertexCount * 8} floats count). */
        public final float[] vertices;
        /** Triangle indices, unsigned 16 bit. */
        public final short[] indices;
        /** Number of vertices. */
        public final int vertexCount;
        /** Number of indices. */
        public final int indexCount;
        /** Bounding sphere centre x. */
        public final float cx;
        /** Bounding sphere centre y. */
        public final float cy;
        /** Bounding sphere centre z. */
        public final float cz;
        /** Bounding sphere radius. */
        public final float radius;
        /** True if the chunk's meshes need back faces drawn. */
        public final boolean doubleSided;

        Chunk(float[] vertices, short[] indices, int vertexCount, int indexCount,
              float cx, float cy, float cz, float radius, boolean doubleSided) {
            this.vertices = vertices;
            this.indices = indices;
            this.vertexCount = vertexCount;
            this.indexCount = indexCount;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.radius = radius;
            this.doubleSided = doubleSided;
        }
    }

    /**
     * Builds chunks with the default block size of 4 cells.
     * @param placements the scenery
     * @param library where the meshes come from
     * @return the chunks
     */
    public static List<Chunk> build(List<Placement> placements, MeshLibrary library) {
        return build(placements, library, DEFAULT_CHUNK_CELLS);
    }

    /**
     * Builds chunks.
     * @param placements the scenery
     * @param library where the meshes come from
     * @param chunkCells block size in cells (at least 1)
     * @return the chunks, in a fixed order (by block, then single-sided before double-sided)
     * @throws IllegalArgumentException for a block size below 1 or an unknown mesh name
     */
    public static List<Chunk> build(List<Placement> placements, MeshLibrary library, int chunkCells) {
        if (chunkCells < 1) throw new IllegalArgumentException("chunkCells must be at least 1: " + chunkCells);
        TreeMap<Long, List<Placement>> groups = new TreeMap<>();
        for (Placement p : placements) {
            MeshData m = library.get(p.meshName);
            if (m == null) throw new IllegalArgumentException("Unknown mesh: " + p.meshName);
            long bx = Math.floorDiv(p.cellX, chunkCells);
            long bz = Math.floorDiv(p.cellZ, chunkCells);
            long key = ((bx + 100000L) * 200000L + (bz + 100000L)) * 2L + (m.doubleSided ? 1L : 0L);
            List<Placement> g = groups.get(key);
            if (g == null) {
                g = new ArrayList<>();
                groups.put(key, g);
            }
            g.add(p);
        }
        List<Chunk> out = new ArrayList<>();
        for (java.util.Map.Entry<Long, List<Placement>> e : groups.entrySet()) {
            boolean ds = (e.getKey() & 1L) != 0L;
            Builder b = new Builder(ds);
            for (Placement p : e.getValue()) {
                MeshData m = library.get(p.meshName);
                for (MeshData.Part part : m.parts) {
                    if (part.vertexCount > MAX_CHUNK_VERTICES) {
                        throw new IllegalArgumentException("Part " + part.name + " of " + p.meshName + " has too many vertices");
                    }
                    if (b.vertexCount + part.vertexCount > MAX_CHUNK_VERTICES) {
                        out.add(b.finish());
                        b = new Builder(ds);
                    }
                    b.add(part, p.matrix);
                }
            }
            if (b.vertexCount > 0) out.add(b.finish());
        }
        return out;
    }

    /** Collects transformed parts into one chunk. */
    private static final class Builder {
        private final boolean doubleSided;
        private float[] verts = new float[MeshData.FLOATS_PER_VERTEX * 1024];
        private short[] idx = new short[4096];
        int vertexCount;
        private int indexCount;
        private final float[] local = new float[16];
        private final float[] rot = new float[16];
        private final float[] scl = new float[16];
        private final float[] tra = new float[16];
        private final float[] full = new float[16];
        private final float[] tmp = new float[3];

        Builder(boolean doubleSided) {
            this.doubleSided = doubleSided;
        }

        void add(MeshData.Part part, float[] placement) {
            // full = placement * T * R * S (the part's own node transform, then the placement)
            Mat4.translate(tra, part.translation[0], part.translation[1], part.translation[2]);
            Mat4.fromQuat(rot, part.rotation[0], part.rotation[1], part.rotation[2], part.rotation[3]);
            Mat4.scale(scl, part.scale[0], part.scale[1], part.scale[2]);
            Mat4.multiply(local, tra, rot);
            Mat4.multiply(local, local, scl);
            Mat4.multiply(full, placement, local);

            int needV = (vertexCount + part.vertexCount) * MeshData.FLOATS_PER_VERTEX;
            if (needV > verts.length) verts = java.util.Arrays.copyOf(verts, Math.max(needV, verts.length * 2));
            int needI = indexCount + part.indices.length;
            if (needI > idx.length) idx = java.util.Arrays.copyOf(idx, Math.max(needI, idx.length * 2));

            for (int i = 0; i < part.vertexCount; i++) {
                int s = i * MeshData.FLOATS_PER_VERTEX;
                int d = (vertexCount + i) * MeshData.FLOATS_PER_VERTEX;
                Mat4.transformPoint(tmp, full, part.vertices[s], part.vertices[s + 1], part.vertices[s + 2]);
                verts[d] = tmp[0];
                verts[d + 1] = tmp[1];
                verts[d + 2] = tmp[2];
                float nx = part.vertices[s + 3], ny = part.vertices[s + 4], nz = part.vertices[s + 5];
                float ox = full[0] * nx + full[4] * ny + full[8] * nz;
                float oy = full[1] * nx + full[5] * ny + full[9] * nz;
                float oz = full[2] * nx + full[6] * ny + full[10] * nz;
                float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (len > 1e-6f) {
                    ox /= len;
                    oy /= len;
                    oz /= len;
                }
                verts[d + 3] = ox;
                verts[d + 4] = oy;
                verts[d + 5] = oz;
                verts[d + 6] = part.vertices[s + 6];
                verts[d + 7] = part.vertices[s + 7];
            }
            for (int i = 0; i < part.indices.length; i++) {
                idx[indexCount + i] = (short) ((part.indices[i] & 0xFFFF) + vertexCount);
            }
            vertexCount += part.vertexCount;
            indexCount += part.indices.length;
        }

        Chunk finish() {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < vertexCount; i++) {
                int s = i * MeshData.FLOATS_PER_VERTEX;
                minX = Math.min(minX, verts[s]);
                maxX = Math.max(maxX, verts[s]);
                minY = Math.min(minY, verts[s + 1]);
                maxY = Math.max(maxY, verts[s + 1]);
                minZ = Math.min(minZ, verts[s + 2]);
                maxZ = Math.max(maxZ, verts[s + 2]);
            }
            float cx = (minX + maxX) * 0.5f, cy = (minY + maxY) * 0.5f, cz = (minZ + maxZ) * 0.5f;
            float r2 = 0f;
            for (int i = 0; i < vertexCount; i++) {
                int s = i * MeshData.FLOATS_PER_VERTEX;
                float dx = verts[s] - cx, dy = verts[s + 1] - cy, dz = verts[s + 2] - cz;
                r2 = Math.max(r2, dx * dx + dy * dy + dz * dz);
            }
            float[] v = java.util.Arrays.copyOf(verts, vertexCount * MeshData.FLOATS_PER_VERTEX);
            short[] ix = java.util.Arrays.copyOf(idx, indexCount);
            return new Chunk(v, ix, vertexCount, indexCount, cx, cy, cz, (float) Math.sqrt(r2) + 1e-4f, doubleSided);
        }
    }
}
