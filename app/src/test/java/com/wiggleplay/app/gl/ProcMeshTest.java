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

    @Test
    public void groundGridHasVerticesNearTheMiddleAndFacesUp() {
        MeshData m = ProcMesh.groundGrid(140f, 14);
        MeshData.Part p = only(m);
        assertEquals(15 * 15, p.vertexCount);
        assertEquals(2 * 14 * 14, m.triangleCount());
        assertIndicesInRange(m);
        float nearest = 1e9f, maxX = 0f;
        for (int v = 0; v < p.vertexCount; v++) {
            assertEquals(-0.02f, p.vertices[v * 8 + 1], 1e-6f);
            nearest = Math.min(nearest, (float) Math.hypot(p.vertices[v * 8], p.vertices[v * 8 + 2]));
            maxX = Math.max(maxX, Math.abs(p.vertices[v * 8]));
        }
        assertEquals("a vertex sits under the camera, so the fog is right near it", 0f, nearest, 1e-4f);
        assertEquals(70f, maxX, 1e-4f);
        assertOutwardWinding(m);
    }

    // ---- T-23 Part C ----

    /** The palette block of a vertex's uv (swatch index), or -1 if the uv is outside the swatch row. */
    private static int swatchOf(MeshData.Part p, int v) {
        float u = p.vertices[v * 8 + 6], t = p.vertices[v * 8 + 7];
        int px = (int) (u * 512f), py = (int) (t * 512f);
        if (py < 0 || py >= ProcMesh.SWATCH_BLOCK || px < 0) return -1;
        int k = px / ProcMesh.SWATCH_BLOCK;
        return k < ProcMesh.SWATCH_RGB.length ? k : -1;
    }

    @Test
    public void reededCoinIsOnePartUnder400TrianglesWithAReededRimAndAStar() {
        MeshData m = ProcMesh.coinReeded();
        MeshData.Part p = only(m);
        assertEquals(356, m.triangleCount());
        assertTrue("one draw call, at most 400 triangles", m.triangleCount() <= 400);
        assertEquals(472, p.vertexCount);
        assertIndicesInRange(m);
        assertOutwardWinding(m);
        float maxR = 0f, maxZ = 0f, starR = 0f;
        boolean[] swatchUsed = new boolean[ProcMesh.SWATCH_RGB.length];
        for (int v = 0; v < p.vertexCount; v++) {
            float x = p.vertices[v * 8], y = p.vertices[v * 8 + 1], z = p.vertices[v * 8 + 2];
            maxR = Math.max(maxR, (float) Math.hypot(x, y));
            maxZ = Math.max(maxZ, Math.abs(z));
            int k = swatchOf(p, v);
            assertTrue("vertex " + v + " takes its colour from a swatch", k >= 0);
            swatchUsed[k] = true;
            if (k == ProcMesh.SWATCH_COIN_STAR) starR = Math.max(starR, (float) Math.hypot(x, y));
        }
        assertEquals(0.6f, maxR, 1e-5f);
        assertEquals(0.075f, maxZ, 1e-5f);
        assertTrue("the embossed star sits inside the groove band", starR > 0.25f && starR < 0.35f);
        for (int k : new int[] {ProcMesh.SWATCH_COIN_FACE, ProcMesh.SWATCH_COIN_RIM, ProcMesh.SWATCH_COIN_GROOVE,
                ProcMesh.SWATCH_COIN_STAR}) {
            assertTrue("swatch " + k + " is used", swatchUsed[k]);
        }
        // 36 edge strips alternate between the light and the dark swatch
        int edgeLight = 0, edgeDark = 0;
        for (int v = 0; v < p.vertexCount; v++) {
            float r = (float) Math.hypot(p.vertices[v * 8], p.vertices[v * 8 + 1]);
            if (Math.abs(r - 0.6f) > 1e-4f || p.vertices[v * 8 + 5] != 0f) continue;   // edge vertices have normal z 0
            if (swatchOf(p, v) == ProcMesh.SWATCH_COIN_FACE) edgeLight++;
            else if (swatchOf(p, v) == ProcMesh.SWATCH_COIN_GROOVE) edgeDark++;
        }
        assertEquals(72, edgeLight);
        assertEquals(72, edgeDark);
    }

    @Test
    public void reededCoinIsTheSameOnBothFaces() {
        MeshData.Part p = only(ProcMesh.coinReeded());
        int front = 0, back = 0;
        for (int v = 0; v < p.vertexCount; v++) {
            float z = p.vertices[v * 8 + 2];
            if (p.vertices[v * 8 + 5] != 0f || Math.abs(z) > 0.05f) {
                if (p.vertices[v * 8 + 5] > 0.5f) front++;
                if (p.vertices[v * 8 + 5] < -0.5f) back++;
            }
        }
        assertEquals(front, back);
        assertTrue(front > 50);
    }

    @Test
    public void bevelledStarKeepsTheStarShapeAndUsesThreeSwatches() {
        MeshData m = ProcMesh.starBevelled(0.9f, 0.4f, 0.2f);
        MeshData.Part p = only(m);
        assertEquals(40, m.triangleCount());
        assertEquals(100, p.vertexCount);
        assertIndicesInRange(m);
        assertOutwardWinding(m);
        float maxY = -1e9f, maxZ = 0f;
        boolean ridge = false, face = false, side = false;
        for (int v = 0; v < p.vertexCount; v++) {
            maxY = Math.max(maxY, p.vertices[v * 8 + 1]);
            maxZ = Math.max(maxZ, Math.abs(p.vertices[v * 8 + 2]));
            int k = swatchOf(p, v);
            assertTrue(k >= 0);
            ridge |= k == ProcMesh.SWATCH_STAR_RIDGE;
            face |= k == ProcMesh.SWATCH_STAR_FACE;
            side |= k == ProcMesh.SWATCH_STAR_SIDE;
        }
        assertEquals("one point up", 0.9f, maxY, 1e-5f);
        assertEquals("the centre ridge is as deep as the old star", 0.1f, maxZ, 1e-5f);
        assertTrue(ridge && face && side);
    }

    /** The palette texture really holds the swatch colours (tools/glb_to_mesh.py would overwrite them with Kenney's copy). */
    @Test
    public void paletteHoldsTheCoinSwatches() throws Exception {
        PngPixels im = new PngPixels(new java.io.File("src/main/assets/art/race/colormap.png"));
        assertEquals(512, im.width);
        assertEquals(512, im.height);
        for (int k = 0; k < ProcMesh.SWATCH_RGB.length; k++) {
            for (int d : new int[] {0, 8, 15}) {
                int rgb = im.rgb(k * ProcMesh.SWATCH_BLOCK + d, d);
                assertEquals("swatch " + k + " pixel " + d, ProcMesh.SWATCH_RGB[k], rgb);
            }
        }
    }
}
