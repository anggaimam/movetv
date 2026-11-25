package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.Arrays;

import org.junit.Test;

public class PropMeshesTest {

    private static final Theme T = Themes.DESERT;

    private static MeshData build(int ruleIndex, int variant) {
        PaletteSwatches pal = new PaletteSwatches();
        T.fillPalette(pal);
        return PropMeshes.build(T.props[ruleIndex].kind, variant, pal, T.propSwatches(ruleIndex));
    }

    @Test
    public void everyKindIsUnderItsTriangleLimit() {
        for (int r = 0; r < T.props.length; r++) {
            int kind = T.props[r].kind;
            for (int v = 0; v < PropMeshes.variants(kind); v++) {
                int tris = build(r, v).triangleCount();
                assertTrue("kind " + kind + " variant " + v + " has " + tris, tris > 0 && tris <= PropMeshes.MAX_TRIANGLES[kind]);
            }
        }
    }

    @Test
    public void exactCountsOfTheKinds() {
        assertEquals(49, build(0, 0).triangleCount());   // saguaro, one arm
        assertEquals(74, build(0, 1).triangleCount());   // saguaro, two arms
        assertEquals(54, build(1, 0).triangleCount());   // butte
        assertEquals(60, build(2, 0).triangleCount());   // barrels
        assertEquals(60, build(3, 0).triangleCount());   // rocks
        assertEquals(46, build(4, 0).triangleCount());   // joshua tree
    }

    @Test
    public void baseIsAtGroundLevelAndNothingIsBelow() {
        for (int r = 0; r < T.props.length; r++) {
            for (int v = 0; v < PropMeshes.variants(T.props[r].kind); v++) {
                MeshData.Part p = build(r, v).parts[0];
                float min = Float.MAX_VALUE;
                for (int i = 0; i < p.vertexCount; i++) min = Math.min(min, p.vertices[i * 8 + 1]);
                assertEquals("kind " + T.props[r].kind, 0f, min, 1e-4f);
            }
        }
    }

    @Test
    public void everyUvIsAtASwatchCentreOfTheRule() {
        PaletteSwatches pal = new PaletteSwatches();
        T.fillPalette(pal);
        for (int r = 0; r < T.props.length; r++) {
            int[] sw = T.propSwatches(r);
            MeshData.Part p = build(r, 0).parts[0];
            for (int i = 0; i < p.vertexCount; i++) {
                boolean found = false;
                for (int s : sw) {
                    if (Math.abs(p.vertices[i * 8 + 6] - pal.u(s)) < 1e-6f && Math.abs(p.vertices[i * 8 + 7] - pal.v(s)) < 1e-6f) found = true;
                }
                assertTrue("kind " + T.props[r].kind + " vertex " + i, found);
            }
        }
    }

    @Test
    public void propSwatchesAreDistinctAndKeepTheColoursOfTheRules() {
        PaletteSwatches pal = new PaletteSwatches();
        int count = T.fillPalette(pal);
        assertTrue(count <= PaletteSwatches.MAX);
        int next = Theme.SW_FIRST_RING + 2 * T.rings.length;
        for (int r = 0; r < T.props.length; r++) {
            for (int c = 0; c < T.props[r].colors.length; c++) {
                assertEquals(next, T.propSwatch(r, c));
                assertEquals(T.props[r].colors[c], pal.rgb(next));
                next++;
            }
        }
        assertEquals(next, count);
    }

    @Test
    public void everyTriangleHasAUnitNormalMatchingItsWinding() {
        for (int r = 0; r < T.props.length; r++) {
            for (int v = 0; v < PropMeshes.variants(T.props[r].kind); v++) {
                MeshData.Part p = build(r, v).parts[0];
                for (int t = 0; t < p.indices.length / 3; t++) {
                    int a = p.indices[t * 3] & 0xFFFF, b = p.indices[t * 3 + 1] & 0xFFFF, c = p.indices[t * 3 + 2] & 0xFFFF;
                    float[] n = cross(p.vertices, a, b, c);
                    float nl = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                    assertTrue("degenerate triangle", nl > 1e-6f);
                    float dot = (n[0] * p.vertices[a * 8 + 3] + n[1] * p.vertices[a * 8 + 4] + n[2] * p.vertices[a * 8 + 5]) / nl;
                    assertEquals("normal against winding in kind " + T.props[r].kind, 1f, dot, 1e-3f);
                }
            }
        }
    }

    private static float[] cross(float[] v, int a, int b, int c) {
        float ux = v[b * 8] - v[a * 8], uy = v[b * 8 + 1] - v[a * 8 + 1], uz = v[b * 8 + 2] - v[a * 8 + 2];
        float wx = v[c * 8] - v[a * 8], wy = v[c * 8 + 1] - v[a * 8 + 1], wz = v[c * 8 + 2] - v[a * 8 + 2];
        return new float[] {uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx};
    }

    /** The winding says which way a face looks; check that on the closed pieces it is outward. */
    @Test
    public void facesLookAwayFromTheirOwnPart() {
        // a barrel cluster, the butte and a rock are convex pieces: every face looks away from the vertical axis of its piece
        MeshData.Part butte = build(1, 0).parts[0];
        for (int t = 0; t < butte.indices.length / 3; t++) {
            int a = butte.indices[t * 3] & 0xFFFF;
            float nx = butte.vertices[a * 8 + 3], ny = butte.vertices[a * 8 + 4], nz = butte.vertices[a * 8 + 5];
            if (Math.abs(ny) > 0.99f) {
                assertTrue("butte tops and ledges face up", ny > 0f);
            } else {
                float cx = 0f, cz = 0f;
                for (int k = 0; k < 3; k++) {
                    int i = butte.indices[t * 3 + k] & 0xFFFF;
                    cx += butte.vertices[i * 8] / 3f;
                    cz += butte.vertices[i * 8 + 2] / 3f;
                }
                assertTrue("butte side looks outward", nx * cx + nz * cz > 0f);
            }
        }
        MeshData.Part rocks = build(3, 0).parts[0];
        for (int rock = 0; rock < 3; rock++) {
            float mx = 0f, my = 0f, mz = 0f;
            for (int t = rock * 20; t < rock * 20 + 20; t++) {
                for (int k = 0; k < 3; k++) {
                    int i = rocks.indices[t * 3 + k] & 0xFFFF;
                    mx += rocks.vertices[i * 8] / 60f;
                    my += rocks.vertices[i * 8 + 1] / 60f;
                    mz += rocks.vertices[i * 8 + 2] / 60f;
                }
            }
            for (int t = rock * 20; t < rock * 20 + 20; t++) {
                int a = rocks.indices[t * 3] & 0xFFFF;
                float cx = -mx, cy = -my, cz = -mz;
                for (int k = 0; k < 3; k++) {
                    int i = rocks.indices[t * 3 + k] & 0xFFFF;
                    cx += rocks.vertices[i * 8] / 3f;
                    cy += rocks.vertices[i * 8 + 1] / 3f;
                    cz += rocks.vertices[i * 8 + 2] / 3f;
                }
                float d = rocks.vertices[a * 8 + 3] * cx + rocks.vertices[a * 8 + 4] * cy + rocks.vertices[a * 8 + 5] * cz;
                assertTrue("rock " + rock + " face " + t + " looks inward", d > 0f);
            }
        }
    }

    @Test
    public void barrelCactiAndTheSaguaroTopFaceOutwardAndUp() {
        // barrels: sides are upright, tops and flower cones look up: no face looks down
        MeshData.Part barrels = build(2, 0).parts[0];
        for (int i = 0; i < barrels.vertexCount; i++) assertTrue("barrel face looks down", barrels.vertices[i * 8 + 4] >= -1e-4f);
        // saguaro trunk: each upright side face looks away from the trunk axis
        MeshData.Part sag = build(0, 0).parts[0];
        for (int t = 0; t < 16; t++) {
            float cx = 0f, cz = 0f;
            for (int k = 0; k < 3; k++) {
                cx += sag.vertices[(t * 3 + k) * 8] / 3f;
                cz += sag.vertices[(t * 3 + k) * 8 + 2] / 3f;
            }
            assertTrue("trunk face " + t, sag.vertices[t * 3 * 8 + 3] * cx + sag.vertices[t * 3 * 8 + 5] * cz > 0f);
        }
    }

    @Test
    public void deterministicAndEachNameIsUnique() {
        for (int r = 0; r < T.props.length; r++) {
            MeshData.Part a = build(r, 0).parts[0], b = build(r, 0).parts[0];
            assertTrue(Arrays.equals(a.vertices, b.vertices));
            assertTrue(Arrays.equals(a.indices, b.indices));
        }
        assertEquals("prop_0_1", PropMeshes.name(PropMeshes.SAGUARO, 1));
    }

    @Test
    public void reachStaysInsideTheFootprintPlusTheGap() {
        for (Theme.PropRule rule : T.props) {
            float reach = PropMeshes.reach(rule.kind);
            assertTrue("kind " + rule.kind + " reach " + reach, reach > 0f && reach <= rule.footprint + 0.5f);
        }
    }

    @Test
    public void sizesAreAsInTheTicket() {
        MeshData.Part sag = build(0, 0).parts[0];
        float top = 0f;
        for (int i = 0; i < sag.vertexCount; i++) top = Math.max(top, sag.vertices[i * 8 + 1]);
        assertTrue("saguaro 3 to 5.5 m tall at scale 1 (" + top + ")", top >= 3f && top <= 5.5f);
        MeshData.Part bu = build(1, 0).parts[0];
        float h = 0f;
        for (int i = 0; i < bu.vertexCount; i++) h = Math.max(h, bu.vertices[i * 8 + 1]);
        assertTrue("butte 6 to 10 m tall (" + h + ")", h >= 6f && h <= 10f);
        assertTrue("butte 8 to 14 m wide", PropMeshes.reach(PropMeshes.BUTTE) * 2f >= 8f && PropMeshes.reach(PropMeshes.BUTTE) * 2f <= 14f);
    }
}
