package com.wiggleplay.app.gl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class MeshDataTest {

    /** Writes the .mesh format the same way tools/glb_to_mesh.py does (the Java side of the round trip). */
    static byte[] write(MeshData m, int version, String magic) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ByteBuffer h = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        h.put(magic.getBytes(StandardCharsets.US_ASCII));
        h.putShort((short) version);
        h.putShort((short) m.parts.length);
        h.put((byte) (m.doubleSided ? 1 : 0));
        bo.write(h.array(), 0, 9);
        for (MeshData.Part p : m.parts) {
            byte[] nb = p.name.getBytes(StandardCharsets.UTF_8);
            ByteBuffer b = ByteBuffer.allocate(2 + nb.length + 2 + 40 + 8 + p.vertices.length * 4 + p.indices.length * 2)
                    .order(ByteOrder.LITTLE_ENDIAN);
            b.putShort((short) nb.length);
            b.put(nb);
            b.putShort((short) p.parent);
            for (float f : p.translation) b.putFloat(f);
            for (float f : p.rotation) b.putFloat(f);
            for (float f : p.scale) b.putFloat(f);
            b.putInt(p.vertexCount);
            b.putInt(p.indices.length);
            for (float f : p.vertices) b.putFloat(f);
            for (short s : p.indices) b.putShort(s);
            bo.write(b.array(), 0, b.position());
        }
        return bo.toByteArray();
    }

    static MeshData sample() {
        float[] v = new float[3 * 8];
        for (int i = 0; i < v.length; i++) v[i] = i * 0.25f - 1f;
        MeshData.Part a = new MeshData.Part("body", -1, new float[] {0, 0.4f, 0}, new float[] {0, 0, 0, 1},
                new float[] {1, 1, 1}, v, new short[] {0, 1, 2}, 3);
        MeshData.Part b = new MeshData.Part("wheel-front-left", 0, new float[] {0.55f, 0.3f, 0.857f},
                new float[] {0, 0.7071068f, 0, 0.7071068f}, new float[] {1, 2, 3}, v.clone(), new short[] {2, 1, 0, 0, 1, 2}, 3);
        return new MeshData(true, new MeshData.Part[] {a, b});
    }

    @Test
    public void roundTripKeepsEverything() throws IOException {
        MeshData src = sample();
        MeshData got = MeshData.read(new ByteArrayInputStream(write(src, 1, "MTVM")));
        assertTrue(got.doubleSided);
        assertEquals(2, got.parts.length);
        for (int i = 0; i < 2; i++) {
            MeshData.Part s = src.parts[i], g = got.parts[i];
            assertEquals(s.name, g.name);
            assertEquals(s.parent, g.parent);
            assertArrayEquals(s.translation, g.translation, 0f);
            assertArrayEquals(s.rotation, g.rotation, 0f);
            assertArrayEquals(s.scale, g.scale, 0f);
            assertEquals(s.vertexCount, g.vertexCount);
            assertArrayEquals(s.vertices, g.vertices, 0f);
            assertArrayEquals(s.indices, g.indices);
        }
        assertEquals(1, got.partIndex("wheel-front-left"));
        assertEquals(-1, got.partIndex("nope"));
        assertEquals(3, got.triangleCount());
    }

    @Test
    public void singleSidedFlagIsKept() throws IOException {
        MeshData s = sample();
        MeshData one = new MeshData(false, s.parts);
        assertFalse(MeshData.read(new ByteArrayInputStream(write(one, 1, "MTVM"))).doubleSided);
    }

    @Test
    public void badMagicIsAnIoExceptionWithAClearMessage() {
        try {
            MeshData.read(new ByteArrayInputStream(write(sample(), 1, "XXXX")));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("magic"));
        }
    }

    @Test
    public void badVersionIsAnIoExceptionWithAClearMessage() {
        try {
            MeshData.read(new ByteArrayInputStream(write(sample(), 2, "MTVM")));
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("version 2"));
        }
    }

    @Test
    public void truncatedFileIsAnIoException() {
        byte[] full = write(sample(), 1, "MTVM");
        for (int cut : new int[] {0, 3, 8, 20, full.length - 1}) {
            byte[] part = java.util.Arrays.copyOf(full, cut);
            try {
                MeshData.read(new ByteArrayInputStream(part));
                fail("expected IOException for cut " + cut);
            } catch (IOException expected) {
                // fine
            }
        }
    }

    // ---- the committed meshes (tests run with the app module as working directory) ----

    private static MeshData load(String name) throws IOException {
        try (InputStream in = new FileInputStream("src/main/assets/race/meshes/" + name + ".mesh")) {
            return MeshData.read(in);
        }
    }

    private static void near(String name, int expected) throws IOException {
        int got = load(name).triangleCount();
        assertTrue(name + " has " + got + " triangles, expected about " + expected,
                Math.abs(got - expected) <= Math.max(1, expected / 100));
    }

    @Test
    public void committedMeshesHaveTheExpectedTriangleCounts() throws IOException {
        near("vehicle-truck-yellow", 989);
        near("track-straight", 106);
        near("track-corner", 1136);
        near("track-finish", 274);
        near("decoration-forest", 2006);
        near("decoration-tents", 1900);
        near("decoration-empty", 596);
    }

    @Test
    public void truckHasTheSixNamedPartsAtTheMeasuredPlaces() throws IOException {
        MeshData t = load("vehicle-truck-yellow");
        assertTrue(t.doubleSided);
        assertEquals(6, t.parts.length);
        int w = t.partIndex("wheel-front-left");
        assertTrue(w >= 0);
        assertArrayEquals(new float[] {0.55f, 0.3f, 0.857f}, t.parts[w].translation, 1e-3f);
        assertArrayEquals(new float[] {0, 0.4f, 0}, t.parts[t.partIndex("body")].translation, 1e-3f);
        assertArrayEquals(new float[] {0, 0, 0, 1}, t.parts[w].rotation, 1e-6f);
        for (String n : new String[] {"underside", "wheel-front-right", "wheel-back-left", "wheel-back-right"}) {
            assertTrue(n, t.partIndex(n) >= 0);
        }
    }

    @Test
    public void allTenMeshesLoad() throws IOException {
        String[] names = {"vehicle-truck-yellow", "vehicle-truck-red", "vehicle-truck-green", "vehicle-truck-purple",
                "track-straight", "track-corner", "track-finish", "decoration-forest", "decoration-tents", "decoration-empty"};
        for (String n : names) assertTrue(n, load(n).triangleCount() > 0);
    }
}
