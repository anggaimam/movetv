package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import org.junit.Test;

public class BackdropTest {

    private static Theme.Ring ring(int profile, float minH, float maxH, int segs, long seed, int accent) {
        return new Theme.Ring(54f, 0xD99A6E, profile, minH, maxH, segs, seed, true, accent);
    }

    private static Theme themeOf(Theme.Ring... rings) {
        Theme d = Themes.DESERT;
        return new Theme("t", d.sky, d.horizon, d.fogNear, d.fogFar, d.sunDir, d.ground, d.patchA, d.patchB, 0, 0f, 0f,
                d.road, rings, 0, 0);
    }

    private static MeshData mesh(Theme.Ring r) {
        Theme th = themeOf(r);
        PaletteSwatches pal = new PaletteSwatches();
        th.fillPalette(pal);
        return Backdrop.build(th, 0, pal);
    }

    private static final int[] BANDS = {Theme.MESAS, Theme.ROLLING, Theme.SKYLINE, Theme.PEAKS, Theme.CRATERS};

    @Test
    public void everyProfileStaysBetweenItsLowestAndMaxHeight() {
        for (int prof : BANDS) {
            for (long seed = 1; seed <= 20; seed++) {
                Theme.Ring r = ring(prof, 2f, 9f, 96, seed, 0);
                Backdrop.Profile p = Backdrop.profile(r);
                float lo = Backdrop.lowestHeight(r);
                float top = -1f;
                for (int c = 0; c < 96; c++) {
                    for (float h : new float[] {p.left[c], p.right[c]}) {
                        assertTrue("profile " + prof + " seed " + seed + " h " + h, h >= lo - 1e-4f && h <= r.maxH + 1e-4f);
                        top = Math.max(top, h);
                    }
                }
                // some top reaches the real range (not only the gaps)
                assertTrue("profile " + prof + " seed " + seed + " top " + top, top >= r.minH - 1e-4f);
            }
        }
    }

    @Test
    public void rollingAndCratersNeverGoBelowMinH() {
        for (int prof : new int[] {Theme.ROLLING, Theme.CRATERS, Theme.SKYLINE}) {
            Theme.Ring r = ring(prof, 0.6f, 2.2f, 96, 5L, 0);
            Backdrop.Profile p = Backdrop.profile(r);
            for (int c = 0; c < 96; c++) {
                assertTrue(p.left[c] >= 0.6f - 1e-4f && p.right[c] >= 0.6f - 1e-4f);
            }
        }
    }

    @Test
    public void mesasHaveGapsAndPlateausAndPeaksHaveValleys() {
        Backdrop.Profile m = Backdrop.profile(ring(Theme.MESAS, 4f, 11f, 96, 3L, 0));
        boolean gap = false, plateau = false;
        for (int c = 0; c < 96; c++) {
            if (Math.abs(m.left[c] - 1.2f) < 1e-4f && Math.abs(m.right[c] - 1.2f) < 1e-4f) gap = true;
            if (m.left[c] >= 4f && m.left[c] == m.right[c]) plateau = true;
        }
        assertTrue(gap);
        assertTrue(plateau);
        Backdrop.Profile pk = Backdrop.profile(ring(Theme.PEAKS, 4f, 11f, 96, 3L, 0));
        boolean valley = false;
        for (int c = 0; c < 96; c++) if (Math.abs(pk.left[c] - 1f) < 1e-4f) valley = true;
        assertTrue(valley);
    }

    @Test
    public void continuousProfilesJoinAtTheSeamAndMeshesCloseTheCircle() {
        for (int prof : new int[] {Theme.ROLLING, Theme.PEAKS, Theme.CRATERS}) {
            Backdrop.Profile p = Backdrop.profile(ring(prof, 2f, 9f, 96, 8L, 0));
            assertEquals(p.right[95], p.left[0], 1e-5f);
        }
        for (int prof : BANDS) {
            MeshData md = mesh(ring(prof, 2f, 9f, 96, 8L, 0));
            float[] v = md.parts[0].vertices;
            int last = 96 - 1;
            // column c has vertices 4c (bottom at edge 0), 4c+1 (bottom at edge 1), 4c+2, 4c+3
            float lx = v[(4 * last + 1) * 8], lz = v[(4 * last + 1) * 8 + 2];
            float fx = v[0], fz = v[2];
            assertEquals(fx, lx, 1e-3f);
            assertEquals(fz, lz, 1e-3f);
        }
    }

    @Test
    public void bandTriangleCountIsTwoPerColumnAndFourForSnowCaps() {
        for (int prof : new int[] {Theme.MESAS, Theme.ROLLING, Theme.SKYLINE, Theme.CRATERS}) {
            Theme.Ring r = ring(prof, 2f, 9f, 96, 2L, 0);
            assertEquals(192, Backdrop.triangleCount(r));
            assertEquals(192, mesh(r).parts[0].indices.length / 3);
        }
        Theme.Ring plain = ring(Theme.PEAKS, 4f, 11f, 96, 2L, 0);
        assertEquals(192, mesh(plain).parts[0].indices.length / 3);
        Theme.Ring snowy = ring(Theme.PEAKS, 4f, 11f, 96, 2L, 0xFFFFFF);
        assertEquals(384, Backdrop.triangleCount(snowy));
        assertEquals(384, mesh(snowy).parts[0].indices.length / 3);
    }

    @Test
    public void allNormalsPointUpAndUvsAreSwatchCentres() {
        Theme.Ring r = ring(Theme.PEAKS, 4f, 11f, 96, 4L, 0xFFFFFF);
        Theme th = themeOf(r);
        PaletteSwatches pal = new PaletteSwatches();
        th.fillPalette(pal);
        MeshData.Part p = Backdrop.build(th, 0, pal).parts[0];
        float u0 = pal.u(th.ringSwatch(0)), u1 = pal.u(th.ringAccentSwatch(0));
        for (int i = 0; i < p.vertexCount; i++) {
            assertEquals(0f, p.vertices[i * 8 + 3], 0f);
            assertEquals(1f, p.vertices[i * 8 + 4], 0f);
            assertEquals(0f, p.vertices[i * 8 + 5], 0f);
            float u = p.vertices[i * 8 + 6];
            assertTrue(u == u0 || u == u1);
        }
    }

    @Test
    public void everyFaceLooksAtTheCentre() {
        for (int prof : BANDS) {
            MeshData.Part p = mesh(ring(prof, 2f, 9f, 96, 6L, 0xFFFFFF)).parts[0];
            for (int t = 0; t < p.indices.length; t += 3) {
                float[] a = pos(p, p.indices[t] & 0xFFFF), b = pos(p, p.indices[t + 1] & 0xFFFF), c = pos(p, p.indices[t + 2] & 0xFFFF);
                float ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2], fx = c[0] - a[0], fy = c[1] - a[1], fz = c[2] - a[2];
                float nx = ey * fz - ez * fy, nz = ex * fy - ey * fx;
                float area2 = (float) Math.sqrt(nx * nx + nz * nz + (ez * fx - ex * fz) * (ez * fx - ex * fz));
                if (area2 < 1e-6f) continue; // a flat sliver at the base of a zero-height column
                // the horizontal part of the normal must point toward the origin
                assertTrue("profile " + prof + " tri " + t, nx * (-a[0]) + nz * (-a[2]) > 0f);
            }
        }
    }

    @Test
    public void sameSeedGivesTheSameRingAndAnotherSeedAnotherOne() {
        Theme.Ring r = ring(Theme.MESAS, 4f, 11f, 96, 9L, 0);
        assertArrayEquals(mesh(r).parts[0].vertices, mesh(r).parts[0].vertices, 0f);
        Backdrop.Profile a = Backdrop.profile(r), b = Backdrop.profile(ring(Theme.MESAS, 4f, 11f, 96, 10L, 0));
        assertFalse(java.util.Arrays.equals(a.left, b.left));
    }

    @Test
    public void starsAreSmallQuadsFacingTheCentre() {
        Theme.Ring r = new Theme.Ring(57f, 0xFFFFFF, Theme.STARS, 0f, 0f, 150, 3L, true, 0xFFE9A8);
        MeshData.Part p = mesh(r).parts[0];
        assertEquals(150 * 4, p.vertexCount);
        assertEquals(150 * 2, p.indices.length / 3);
        assertEquals(300, Backdrop.triangleCount(r));
        for (int s = 0; s < 150; s++) {
            float cx = 0f, cy = 0f, cz = 0f;
            for (int k = 0; k < 4; k++) {
                float[] q = pos(p, s * 4 + k);
                cx += q[0] / 4f;
                cy += q[1] / 4f;
                cz += q[2] / 4f;
            }
            assertEquals(57f, (float) Math.hypot(cx, cz), 0.01f);
            assertTrue(cy >= 2f - 1e-3f && cy <= 40f + 1e-3f);
            float side = Math.abs(pos(p, s * 4)[1] - pos(p, s * 4 + 3)[1]);
            assertTrue(side >= 0.25f - 1e-4f && side <= 0.5f + 1e-4f);
        }
        for (int t = 0; t < p.indices.length; t += 3) {
            float[] a = pos(p, p.indices[t] & 0xFFFF), b = pos(p, p.indices[t + 1] & 0xFFFF), c = pos(p, p.indices[t + 2] & 0xFFFF);
            float ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2], fx = c[0] - a[0], fy = c[1] - a[1], fz = c[2] - a[2];
            float nx = ey * fz - ez * fy, nz = ex * fy - ey * fx;
            assertTrue(nx * (-a[0]) + nz * (-a[2]) > 0f);
        }
    }

    @Test
    public void desertRingsFitTheBudget() {
        Theme d = Themes.DESERT;
        int tris = 0;
        for (Theme.Ring r : d.rings) {
            assertTrue("ring radius " + r.radius, r.radius < 60f);
            tris += Backdrop.triangleCount(r);
        }
        assertTrue("ring triangles " + tris, tris <= 3600);   // 192 segments each, with strata on the mesas
        assertTrue(d.rings.length <= 4);
        PaletteSwatches pal = new PaletteSwatches();
        d.fillPalette(pal);
        for (int i = 0; i < d.rings.length; i++) {
            MeshData m = Backdrop.build(d, i, pal);
            assertTrue(m.parts[0].vertexCount < 65535);
            assertEquals(Backdrop.triangleCount(d.rings[i]), m.parts[0].indices.length / 3);
        }
    }

    private static float[] pos(MeshData.Part p, int i) {
        return new float[] {p.vertices[i * 8], p.vertices[i * 8 + 1], p.vertices[i * 8 + 2]};
    }
}
