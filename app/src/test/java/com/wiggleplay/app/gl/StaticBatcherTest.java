package com.wiggleplay.app.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StaticBatcherTest {

    private final Map<String, MeshData> meshes = new HashMap<>();
    private final StaticBatcher.MeshLibrary lib = meshes::get;

    /** A one-triangle mesh with {@code vertexCount} vertices; vertex 0 is at (1,0,0), normal +Z. */
    private static MeshData mesh(int vertexCount, boolean doubleSided) {
        float[] v = new float[vertexCount * 8];
        for (int i = 0; i < vertexCount; i++) {
            v[i * 8] = i == 0 ? 1f : 0f;
            v[i * 8 + 1] = i == 1 ? 1f : 0f;
            v[i * 8 + 5] = 1f; // normal +Z
        }
        short[] idx = {0, 1, (short) (vertexCount - 1)};
        MeshData.Part p = new MeshData.Part("p", -1, new float[] {0, 0, 0}, new float[] {0, 0, 0, 1},
                new float[] {1, 1, 1}, v, idx, vertexCount);
        return new MeshData(doubleSided, new MeshData.Part[] {p});
    }

    private static StaticBatcher.Placement place(String name, float x, float z, int cx, int cz) {
        float[] m = new float[16];
        Mat4.translate(m, x, 0, z);
        return new StaticBatcher.Placement(name, m, cx, cz);
    }

    @Test
    public void placementsInOneBlockMergeIntoOneChunk() {
        meshes.put("a", mesh(3, true));
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        for (int cx = 0; cx < 4; cx++) for (int cz = 0; cz < 4; cz++) ps.add(place("a", cx * 10, cz * 10, cx, cz));
        List<StaticBatcher.Chunk> chunks = StaticBatcher.build(ps, lib, 4);
        assertEquals(1, chunks.size());
        assertEquals(16 * 3, chunks.get(0).vertexCount);
        assertEquals(16 * 3, chunks.get(0).indexCount);
    }

    @Test
    public void neighbouringBlocksAreSeparateChunks() {
        meshes.put("a", mesh(3, true));
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        ps.add(place("a", 0, 0, 3, 3));
        ps.add(place("a", 40, 0, 4, 3));
        ps.add(place("a", 0, 40, 3, 4));
        ps.add(place("a", -10, 0, -1, 0)); // negative cells use their own block
        assertEquals(4, StaticBatcher.build(ps, lib, 4).size());
        assertEquals(2, StaticBatcher.build(ps, lib, 1000).size()); // cells 3,4 share block 0; cell -1 is block -1
    }

    @Test
    public void defaultBlockSizeIsFour() {
        meshes.put("a", mesh(3, true));
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        ps.add(place("a", 0, 0, 3, 0));
        ps.add(place("a", 0, 0, 4, 0));
        assertEquals(2, StaticBatcher.build(ps, lib).size());
    }

    @Test
    public void positionsAndNormalsAreTransformed() {
        meshes.put("a", mesh(3, true));
        float[] t = new float[16], r = new float[16], m = new float[16];
        Mat4.translate(t, 10, 0, 20);
        Mat4.rotY(r, (float) Math.toRadians(90));
        Mat4.multiply(m, t, r);
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        ps.add(new StaticBatcher.Placement("a", m, 0, 0));
        StaticBatcher.Chunk c = StaticBatcher.build(ps, lib, 4).get(0);
        // vertex 0 was (1,0,0): rotY(90) sends +X to -Z, then +(10,0,20) -> (10,0,19)
        assertEquals(10f, c.vertices[0], 1e-4f);
        assertEquals(0f, c.vertices[1], 1e-4f);
        assertEquals(19f, c.vertices[2], 1e-4f);
        // normal +Z rotates to +X
        assertEquals(1f, c.vertices[3], 1e-4f);
        assertEquals(0f, c.vertices[4], 1e-4f);
        assertEquals(0f, c.vertices[5], 1e-4f);
    }

    @Test
    public void indicesAreOffsetPerMergedPart() {
        meshes.put("a", mesh(3, true));
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        ps.add(place("a", 0, 0, 0, 0));
        ps.add(place("a", 5, 0, 1, 0));
        StaticBatcher.Chunk c = StaticBatcher.build(ps, lib, 4).get(0);
        assertEquals(6, c.indexCount);
        assertEquals(0, c.indices[0]);
        assertEquals(3, c.indices[3]);
        assertEquals(4, c.indices[4]);
        assertEquals(5, c.indices[5]);
    }

    @Test
    public void boundingSphereContainsEveryVertex() {
        meshes.put("a", mesh(3, true));
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        ps.add(place("a", 0, 0, 0, 0));
        ps.add(place("a", 30, 5, 3, 0));
        StaticBatcher.Chunk c = StaticBatcher.build(ps, lib, 4).get(0);
        for (int i = 0; i < c.vertexCount; i++) {
            float dx = c.vertices[i * 8] - c.cx, dy = c.vertices[i * 8 + 1] - c.cy, dz = c.vertices[i * 8 + 2] - c.cz;
            assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= c.radius);
        }
        assertTrue(c.radius > 10f);
    }

    @Test
    public void aChunkThatWouldPass65535VerticesIsSplit() {
        meshes.put("big", mesh(40000, true));
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        for (int i = 0; i < 3; i++) ps.add(place("big", i, 0, 0, 0));
        List<StaticBatcher.Chunk> chunks = StaticBatcher.build(ps, lib, 4);
        assertEquals(3, chunks.size()); // 40000 + 40000 > 65535, so each copy gets its own chunk
        int total = 0;
        for (StaticBatcher.Chunk c : chunks) {
            assertTrue(c.vertexCount <= 65535);
            total += c.vertexCount;
        }
        assertEquals(120000, total);
    }

    @Test
    public void smallPartsFillAChunkUpToTheLimit() {
        meshes.put("mid", mesh(30000, true));
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        for (int i = 0; i < 3; i++) ps.add(place("mid", i, 0, 0, 0));
        List<StaticBatcher.Chunk> chunks = StaticBatcher.build(ps, lib, 4);
        assertEquals(2, chunks.size());
        assertEquals(60000, chunks.get(0).vertexCount);
        assertEquals(30000, chunks.get(1).vertexCount);
        // indices of the second copy point at the right vertices inside the first chunk
        assertEquals(30000, chunks.get(0).indices[3] & 0xFFFF);
    }

    @Test
    public void singleAndDoubleSidedMeshesNeverShareAChunk() {
        meshes.put("one", mesh(3, false));
        meshes.put("two", mesh(3, true));
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        ps.add(place("one", 0, 0, 0, 0));
        ps.add(place("two", 0, 0, 0, 0));
        List<StaticBatcher.Chunk> chunks = StaticBatcher.build(ps, lib, 4);
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).doubleSided != chunks.get(1).doubleSided);
    }

    @Test
    public void badInputIsRejected() {
        meshes.put("a", mesh(3, true));
        List<StaticBatcher.Placement> ps = new ArrayList<>();
        ps.add(place("missing", 0, 0, 0, 0));
        try {
            StaticBatcher.build(ps, lib, 4);
            fail("unknown mesh must throw");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("missing"));
        }
        try {
            StaticBatcher.build(new ArrayList<StaticBatcher.Placement>(), lib, 0);
            fail("chunkCells 0 must throw");
        } catch (IllegalArgumentException expected) {
            // fine
        }
        assertEquals(0, StaticBatcher.build(new ArrayList<StaticBatcher.Placement>(), lib, 4).size());
    }
}
