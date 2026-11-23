package com.wiggleplay.app.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProcMeshTest {

    private static MeshData.Part only(MeshData m) {
        assertEquals(1, m.parts.length);
        return m.parts[0];
    }

    /** Every triangle must wind counter-clockwise seen from outside: its geometric normal agrees with its vertex normals. */
    private static void assertOutwardWinding(MeshData m) {
        assertFalse("proc meshes rely on back-face culling", m.doubleSided);
        MeshData.Part p = only(m);
        for (int t = 0; t < p.indices.length; t += 3) {
            int a = (p.indices[t] & 0xFFFF) * 8, b = (p.indices[t + 1] & 0xFFFF) * 8, c = (p.indices[t + 2] & 0xFFFF) * 8;
            float ux = p.vertices[b] - p.vertices[a], uy = p.vertices[b + 1] - p.vertices[a + 1], uz = p.vertices[b + 2] - p.vertices[a + 2];
            float vx = p.vertices[c] - p.vertices[a], vy = p.vertices[c + 1] - p.vertices[a + 1], vz = p.vertices[c + 2] - p.vertices[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float dot = nx * p.vertices[a + 3] + ny * p.vertices[a + 4] + nz * p.vertices[a + 5];
            assertTrue("triangle " + t / 3 + " faces inward (dot " + dot + ")", dot > 0f);
        }
    }

    private static void assertIndicesInRange(MeshData m) {
        MeshData.Part p = only(m);
        for (short s : p.indices) assertTrue((s & 0xFFFF) < p.vertexCount);
        assertEquals(p.vertexCount * 8, p.vertices.length);
        assertEquals(0, p.indices.length % 3);
    }

    @Test
    public void coinCounts() {
        MeshData m = ProcMesh.coin(0.6f, 0.15f, 16);
        assertEquals(4 * 16 + 2, only(m).vertexCount);
        assertEquals(4 * 16, m.triangleCount());
        assertIndicesInRange(m);
        assertEquals(only(ProcMesh.coin()).vertexCount, only(m).vertexCount);
    }

    @Test
    public void coinSizeAndFacing() {
        MeshData.Part p = only(ProcMesh.coin(0.6f, 0.15f, 16));
        float maxR = 0, maxZ = 0;
        for (int i = 0; i < p.vertexCount; i++) {
            float x = p.vertices[i * 8], y = p.vertices[i * 8 + 1], z = p.vertices[i * 8 + 2];
            maxR = Math.max(maxR, (float) Math.sqrt(x * x + y * y));
            maxZ = Math.max(maxZ, Math.abs(z));
        }
        assertEquals(0.6f, maxR, 1e-5f);
        assertEquals(0.075f, maxZ, 1e-5f);
        assertOutwardWinding(ProcMesh.coin());
    }

    @Test
    public void starCounts() {
        MeshData m = ProcMesh.star(0.9f, 0.4f, 0.2f);
        assertEquals(62, only(m).vertexCount);
        assertEquals(40, m.triangleCount());
        assertIndicesInRange(m);
        assertEquals(62, only(ProcMesh.star()).vertexCount);
    }

    @Test
    public void starHasOnePointUpAndIsWoundOutward() {
        MeshData.Part p = only(ProcMesh.star(0.9f, 0.4f, 0.2f));
        float maxY = -9, minY = 9, maxZ = 0;
        for (int i = 0; i < p.vertexCount; i++) {
            maxY = Math.max(maxY, p.vertices[i * 8 + 1]);
            minY = Math.min(minY, p.vertices[i * 8 + 1]);
            maxZ = Math.max(maxZ, Math.abs(p.vertices[i * 8 + 2]));
        }
        assertEquals(0.9f, maxY, 1e-5f);
        assertTrue(minY > -0.9f); // the bottom has two feet, not a point straight down
        assertEquals(0.1f, maxZ, 1e-5f);
        assertOutwardWinding(ProcMesh.star());
    }

    @Test
    public void discLiesOnTheGroundAndFacesUp() {
        MeshData m = ProcMesh.disc(1.2f, 16);
        MeshData.Part p = only(m);
        assertEquals(17, p.vertexCount);
        assertEquals(16, m.triangleCount());
        assertIndicesInRange(m);
        float maxR = 0;
        for (int i = 0; i < p.vertexCount; i++) {
            assertEquals(0f, p.vertices[i * 8 + 1], 0f);
            maxR = Math.max(maxR, (float) Math.hypot(p.vertices[i * 8], p.vertices[i * 8 + 2]));
        }
        assertEquals(1.2f, maxR, 1e-5f);
        assertOutwardWinding(m);
        assertEquals(17, only(ProcMesh.disc(1f)).vertexCount);
    }

    @Test
    public void groundIsAFlatQuadJustBelowZero() {
        MeshData m = ProcMesh.ground(400f);
        MeshData.Part p = only(m);
        assertEquals(4, p.vertexCount);
        assertEquals(2, m.triangleCount());
        assertIndicesInRange(m);
        float maxX = 0;
        for (int i = 0; i < 4; i++) {
            assertEquals(-0.02f, p.vertices[i * 8 + 1], 1e-6f);
            maxX = Math.max(maxX, Math.abs(p.vertices[i * 8]));
        }
        assertEquals(200f, maxX, 1e-4f);
        assertOutwardWinding(m);
    }

    @Test
    public void tooFewSidesIsRejected() {
        try {
            ProcMesh.coin(1f, 1f, 2);
            assertTrue("expected IllegalArgumentException", false);
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }
}
