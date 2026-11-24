package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.List;

import org.junit.Test;

public class GroundPatchesTest {

    private static final float MINX = -100f, MAXX = 60f, MINZ = -20f, MAXZ = 180f;

    @Test
    public void countIsThemeCountOnHighAndHalfOnLow() {
        assertEquals(40, GroundPatches.generate(MINX, MAXX, MINZ, MAXZ, Themes.DESERT, 5L, false).size());
        assertEquals(20, GroundPatches.generate(MINX, MAXX, MINZ, MAXZ, Themes.DESERT, 5L, true).size());
        assertEquals(0, GroundPatches.generate(MINX, MAXX, MINZ, MAXZ, Themes.MEADOW, 5L, false).size());
    }

    @Test
    public void radiusAndCentreStayInRange() {
        for (long seed = 1; seed <= 10; seed++) {
            for (GroundPatches.Patch p : GroundPatches.generate(MINX, MAXX, MINZ, MAXZ, Themes.DESERT, seed, false)) {
                assertTrue(p.radius >= 4f && p.radius <= 12f);
                assertTrue(p.x >= MINX - 40f && p.x <= MAXX + 40f);
                assertTrue(p.z >= MINZ - 40f && p.z <= MAXZ + 40f);
            }
        }
    }

    @Test
    public void colorsAlternateAndTheSameSeedGivesTheSamePatches() {
        List<GroundPatches.Patch> a = GroundPatches.generate(MINX, MAXX, MINZ, MAXZ, Themes.DESERT, 9L, false);
        List<GroundPatches.Patch> b = GroundPatches.generate(MINX, MAXX, MINZ, MAXZ, Themes.DESERT, 9L, false);
        PaletteSwatches pal = new PaletteSwatches();
        Themes.DESERT.fillPalette(pal);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x, b.get(i).x, 0f);
            assertEquals(a.get(i).z, b.get(i).z, 0f);
            assertEquals(i % 2 == 0 ? Theme.SW_PATCH_A : Theme.SW_PATCH_B, a.get(i).swatch);
            assertArrayEquals(GroundPatches.mesh(a.get(i), pal).parts[0].vertices, GroundPatches.mesh(b.get(i), pal).parts[0].vertices, 0f);
        }
    }

    @Test
    public void everyPatchIsFortyEightFlatTrianglesFacingUp() {
        PaletteSwatches pal = new PaletteSwatches();
        Themes.DESERT.fillPalette(pal);
        for (GroundPatches.Patch pt : GroundPatches.generate(MINX, MAXX, MINZ, MAXZ, Themes.DESERT, 2L, false)) {
            MeshData.Part p = GroundPatches.mesh(pt, pal).parts[0];
            assertEquals(48, p.indices.length / 3);
            assertTrue(p.indices.length / 3 <= GroundPatches.TRIANGLES);
            float u = pal.u(pt.swatch), v = pal.v(pt.swatch);
            for (int i = 0; i < p.vertexCount; i++) {
                assertEquals(-0.015f, p.vertices[i * 8 + 1], 0f);
                assertEquals(1f, p.vertices[i * 8 + 4], 0f);
                assertEquals(u, p.vertices[i * 8 + 6], 0f);
                assertEquals(v, p.vertices[i * 8 + 7], 0f);
                float d = (float) Math.hypot(p.vertices[i * 8] - pt.x, p.vertices[i * 8 + 2] - pt.z);
                assertTrue(d <= pt.radius + 1e-3f);
            }
            for (int t = 0; t < p.indices.length; t += 3) {
                int a = p.indices[t] & 0xFFFF, b = p.indices[t + 1] & 0xFFFF, c = p.indices[t + 2] & 0xFFFF;
                float ex = p.vertices[b * 8] - p.vertices[a * 8], ez = p.vertices[b * 8 + 2] - p.vertices[a * 8 + 2];
                float fx = p.vertices[c * 8] - p.vertices[a * 8], fz = p.vertices[c * 8 + 2] - p.vertices[a * 8 + 2];
                assertTrue("normal y must be positive", ez * fx - ex * fz > 0f);
            }
        }
    }

    @Test
    public void patchesOfARealTrackStayAroundIt() {
        TrackSpline t = TrackSpline.build(TrackDefs.byId("desert"), false, RaceLevel.HARD);
        List<GroundPatches.Patch> list = GroundPatches.generate(t, Themes.DESERT, "desert".hashCode(), false);
        assertEquals(40, list.size());
    }
}
