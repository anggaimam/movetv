package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import org.junit.Test;

public class SkyDomeTest {

    private static final Theme T = Themes.DESERT;

    private static MeshData.Part build(PaletteSwatches pal) {
        T.fillPalette(pal);
        return SkyDome.build(T, pal).parts[0];
    }

    @Test
    public void triangleCountIsTheDomePlusTheSun() {
        MeshData.Part p = build(new PaletteSwatches());
        assertEquals(SkyDome.DOME_TRIANGLES + SkyDome.SUN_TRIANGLES, p.indices.length / 3);
        assertTrue(p.indices.length / 3 <= 700);
        assertEquals(608, SkyDome.DOME_TRIANGLES);
    }

    @Test
    public void everyVertexIsInsideTheFarPlane() {
        MeshData.Part p = build(new PaletteSwatches());
        float max = 0f;
        for (int i = 0; i < p.vertexCount; i++) {
            float x = p.vertices[i * 8], y = p.vertices[i * 8 + 1], z = p.vertices[i * 8 + 2];
            float r = (float) Math.sqrt(x * x + y * y + z * z);
            max = Math.max(max, r);
            assertTrue("above the horizon", y >= -1e-3f);
        }
        assertEquals(SkyDome.RADIUS, max, 0.01f);
        assertTrue(max < 60f);
    }

    @Test
    public void everyFaceLooksToTheCentreAndHasOneBlock() {
        PaletteSwatches pal = new PaletteSwatches();
        MeshData.Part p = build(pal);
        int[] swatches = {T.skySwatch(0), T.skySwatch(1), T.skySwatch(2)};
        for (int t = 0; t < p.indices.length / 3; t++) {
            float cx = 0f, cy = 0f, cz = 0f;
            float[] v0 = null;
            int block = -1;
            for (int k = 0; k < 3; k++) {
                int i = p.indices[t * 3 + k] & 0xFFFF;
                cx += p.vertices[i * 8] / 3f;
                cy += p.vertices[i * 8 + 1] / 3f;
                cz += p.vertices[i * 8 + 2] / 3f;
                int found = -1;
                for (int s : swatches) {
                    float u0 = (16f * (s % 32)) / 512f, u1 = u0 + 16f / 512f;
                    float u = p.vertices[i * 8 + 6], v = p.vertices[i * 8 + 7];
                    if (u > u0 && u < u1 && Math.abs(v - pal.v(s)) < 1e-6f) found = s;
                }
                assertTrue("vertex of triangle " + t + " is in a sky block", found >= 0);
                if (block < 0) block = found;
                assertEquals("triangle " + t + " mixes blocks", block, found);
            }
            int a = p.indices[t * 3] & 0xFFFF, b = p.indices[t * 3 + 1] & 0xFFFF, c = p.indices[t * 3 + 2] & 0xFFFF;
            float ux = p.vertices[b * 8] - p.vertices[a * 8], uy = p.vertices[b * 8 + 1] - p.vertices[a * 8 + 1],
                    uz = p.vertices[b * 8 + 2] - p.vertices[a * 8 + 2];
            float wx = p.vertices[c * 8] - p.vertices[a * 8], wy = p.vertices[c * 8 + 1] - p.vertices[a * 8 + 1],
                    wz = p.vertices[c * 8 + 2] - p.vertices[a * 8 + 2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
            assertTrue("triangle " + t + " looks outward", nx * cx + ny * cy + nz * cz < 0f);
        }
    }

    @Test
    public void colourRunsFromTheHorizonColourToTheZenithColour() {
        PaletteSwatches pal = new PaletteSwatches();
        MeshData.Part p = build(pal);
        int ramp = T.skySwatch(0);
        assertEquals(T.horizon, pal.columnRgb(ramp, 0));
        assertEquals(T.look.zenith, pal.columnRgb(ramp, 15));
        float lowest = 1f, highest = 0f;
        for (int i = 0; i < p.vertexCount; i++) {
            float u = p.vertices[i * 8 + 6];
            if (Math.abs(p.vertices[i * 8 + 7] - pal.v(ramp)) > 1e-6f) continue;
            float t = (u * 512f - 16f * (ramp % 32) - 0.5f) / 15f;
            float y = p.vertices[i * 8 + 1];
            if (y < 0.5f) lowest = Math.min(lowest, t);
            if (y > SkyDome.RADIUS - 0.5f) highest = Math.max(highest, t);
        }
        assertEquals("horizon vertices are at the dark end", 0f, lowest, 0.01f);
        assertEquals("the apex is at the light end", 1f, highest, 0.01f);
    }

    @Test
    public void theSunSitsAlongMinusSunDirAtItsElevation() {
        PaletteSwatches pal = new PaletteSwatches();
        MeshData.Part p = build(pal);
        int sun = T.skySwatch(1);
        float sx = 0f, sy = 0f, sz = 0f;
        int n = 0;
        for (int i = 0; i < p.vertexCount; i++) {
            if (Math.abs(p.vertices[i * 8 + 7] - pal.v(sun)) > 1e-6f) continue;
            float u = p.vertices[i * 8 + 6];
            if (Math.abs(u - pal.u(sun)) > 1e-6f) continue;
            sx += p.vertices[i * 8];
            sy += p.vertices[i * 8 + 1];
            sz += p.vertices[i * 8 + 2];
            n++;
        }
        assertTrue(n > 0);
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        float[] d = SkyDome.sunDirection(T);
        assertEquals(d[0], sx / l, 0.03f);
        assertEquals(d[1], sy / l, 0.03f);
        assertEquals(d[2], sz / l, 0.03f);
        assertTrue("the sun is above the horizon", T.sunElevationDeg() > 30f && T.sunElevationDeg() < 80f);
        assertEquals(0xFFF6DD, pal.rgb(sun));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aThemeWithoutASkyHasNoDome() {
        SkyDome.build(Themes.MEADOW, new PaletteSwatches());
    }
}
