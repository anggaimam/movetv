package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import org.junit.Test;

public class PropMeshesTest {
    private static final Theme T = Themes.DESERT;

    private static int ruleOf(int kind) {
        for (int r = 0; r < T.props.length; r++) if (T.props[r].kind == kind) return r;
        throw new IllegalStateException("kind " + kind);
    }

    private static MeshData build(int kind, int v, PaletteSwatches pal) {
        return PropMeshes.build(kind, v, pal, T.propSwatches(ruleOf(kind)));
    }

    private static PaletteSwatches pal() {
        PaletteSwatches p = new PaletteSwatches();
        T.fillPalette(p);
        return p;
    }

    @Test
    public void everyKindStaysUnderItsTriangleLimit() {
        PaletteSwatches pal = pal();
        for (int k = 0; k < PropMeshes.COUNT; k++) {
            assertTrue(PropMeshes.variants(k) >= 1);
            for (int v = 0; v < PropMeshes.variants(k); v++) {
                MeshData m = build(k, v, pal);
                assertEquals(1, m.parts.length);
                assertTrue("kind " + k + " variant " + v + " has " + m.triangleCount(), m.triangleCount() <= PropMeshes.MAX_TRIANGLES[k]);
                assertTrue(m.triangleCount() > 0);
                assertTrue(m.parts[0].vertexCount < 65535);
            }
        }
    }

    @Test
    public void baseIsAtGroundLevelAndNothingIsBelowIt() {
        PaletteSwatches pal = pal();
        for (int k = 0; k < PropMeshes.COUNT; k++) {
            for (int v = 0; v < PropMeshes.variants(k); v++) {
                MeshData.Part p = build(k, v, pal).parts[0];
                float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
                for (int i = 0; i < p.vertexCount; i++) {
                    min = Math.min(min, p.vertices[i * 8 + 1]);
                    max = Math.max(max, p.vertices[i * 8 + 1]);
                }
                assertTrue("kind " + k + " base " + min, min >= -0.03f && min <= 0.08f);
                assertTrue("kind " + k + " height " + max + " vs " + PropMeshes.height(k), max <= PropMeshes.height(k) + 1e-3f);
            }
        }
    }

    @Test
    public void normalsAreUnitLength() {
        PaletteSwatches pal = pal();
        for (int k = 0; k < PropMeshes.COUNT; k++) {
            for (int v = 0; v < PropMeshes.variants(k); v++) {
                MeshData.Part p = build(k, v, pal).parts[0];
                for (int i = 0; i < p.vertexCount; i++) {
                    float x = p.vertices[i * 8 + 3], y = p.vertices[i * 8 + 4], z = p.vertices[i * 8 + 5];
                    assertEquals("kind " + k + " normal", 1f, (float) Math.sqrt(x * x + y * y + z * z), 0.01f);
                }
            }
        }
    }

    @Test
    public void everyUvIsInsideItsRulesSwatchesAndOneBlockPerTriangle() {
        PaletteSwatches pal = pal();
        for (int k = 0; k < PropMeshes.COUNT; k++) {
            int[] sw = T.propSwatches(ruleOf(k));
            for (int v = 0; v < PropMeshes.variants(k); v++) {
                MeshData.Part p = build(k, v, pal).parts[0];
                for (int i = 0; i < p.indices.length; i += 3) {
                    int block = -1;
                    for (int c = 0; c < 3; c++) {
                        int o = (p.indices[i + c] & 0xFFFF) * 8;
                        float u = p.vertices[o + 6] * 512f, vv = p.vertices[o + 7] * 512f;
                        int bx = (int) Math.floor(u / 16f), by = (int) Math.floor(vv / 16f) - 1;
                        int b = bx + 32 * by;
                        boolean ok = false;
                        for (int s : sw) ok |= s == b;
                        assertTrue("kind " + k + " variant " + v + " uv block " + b, ok);
                        if (block < 0) block = b;
                        assertEquals("kind " + k + " triangle mixes blocks", block, b);
                        float t = (u - 16f * bx - 0.5f) / 15f;
                        assertTrue("ramp t " + t, t >= -0.001f && t <= 1.001f);
                    }
                }
            }
        }
    }

    @Test
    public void reachCoversTheFootprintOfTheMesh() {
        PaletteSwatches pal = pal();
        for (int k = 0; k < PropMeshes.COUNT; k++) {
            float r = PropMeshes.reach(k);
            for (int v = 0; v < PropMeshes.variants(k); v++) {
                MeshData.Part p = build(k, v, pal).parts[0];
                for (int i = 0; i < p.vertexCount; i++) {
                    float d = (float) Math.hypot(p.vertices[i * 8], p.vertices[i * 8 + 2]);
                    assertTrue("kind " + k + " reach " + d + " > " + r, d <= r + 1e-3f);
                }
            }
        }
    }

    @Test
    public void sameInputsGiveTheSameMesh() {
        PaletteSwatches pal = pal();
        for (int k = 0; k < PropMeshes.COUNT; k++) {
            MeshData a = build(k, 0, pal), b = build(k, 0, pal);
            assertTrue(java.util.Arrays.equals(a.parts[0].vertices, b.parts[0].vertices));
            assertTrue(java.util.Arrays.equals(a.parts[0].indices, b.parts[0].indices));
        }
    }
}
