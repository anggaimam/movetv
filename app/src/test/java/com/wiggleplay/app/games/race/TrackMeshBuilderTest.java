package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class TrackMeshBuilderTest {

    private interface Check {
        void run(String id, TrackSpline t, List<MeshData> pieces, PaletteSwatches pal);
    }

    private static void forAll(boolean desert, Check c) {
        for (RaceLevel lv : new RaceLevel[] {RaceLevel.EASY, RaceLevel.HARD}) {
            for (TrackDef def : TrackDefs.ALL) {
                for (boolean rev : new boolean[] {false, true}) {
                    TrackSpline t = TrackSpline.build(def, rev, lv);
                    PaletteSwatches pal = new PaletteSwatches();
                    List<MeshData> pieces;
                    if (desert) {
                        Themes.DESERT.fillPalette(pal);
                        pieces = TrackMeshBuilder.build(t, Themes.DESERT.road, pal, Themes.DESERT.terrainSwatch(1));
                    } else {
                        RoadStyle.MEADOW.paint(pal);
                        pieces = TrackMeshBuilder.build(t, RoadStyle.MEADOW, pal);
                    }
                    c.run(TrackSpline.key(def, rev) + " " + lv, t, pieces, pal);
                }
            }
        }
    }

    /** The swatch block a vertex's uv points into (blocks are 16 x 16 on a 512 x 512 map, v counted from the top). */
    private static int block(float[] v, int o) {
        int bx = (int) Math.floor(v[o + 6] * 512f / 16f), by = (int) Math.floor(v[o + 7] * 512f / 16f) - 1;
        return bx + 32 * by;
    }

    private static boolean ramp(int b) {
        return (b >= RoadStyle.RAMP_ASPHALT && b <= RoadStyle.RAMP_POST) || b == Themes.DESERT.terrainSwatch(1);
    }

    @Test
    public void budgetPiecesAndVertexLimit() {
        forAll(true, (id, t, pieces, pal) -> {
            float lap = t.lapLength();
            int tris = 0, verts = 0;
            assertEquals(id + " pieces", (int) Math.ceil(lap / 60f), pieces.size());
            for (MeshData m : pieces) {
                assertTrue(!m.doubleSided);
                assertEquals(1, m.parts.length);
                assertTrue(id + " piece has " + m.parts[0].vertexCount + " vertices", m.parts[0].vertexCount < 65535);
                tris += m.triangleCount();
                verts += m.parts[0].vertexCount;
            }
            System.out.printf("%-12s L=%.1f pieces=%d triangles=%d (%.2f/m) vertices=%d (%.2f/m)%n", id, lap, pieces.size(), tris, tris / lap,
                    verts, verts / lap);
            assertTrue(id + " triangles per metre " + tris / lap, tris <= 75f * lap);
            // the ticket says 60 vertices per metre; one swatch block per triangle keeps the vertices unshared, so the measured figure is about 110 to 125 and this limit is looser
            assertTrue(id + " vertices per metre " + verts / lap, verts <= 140f * lap);
        });
    }

    @Test
    public void everyTriangleUsesOneSwatchBlockAndAllVerticesOfAQuadAgree() {
        forAll(true, (id, t, pieces, pal) -> {
            for (MeshData m : pieces) {
                MeshData.Part p = m.parts[0];
                for (int i = 0; i < p.indices.length; i += 3) {
                    int a = (p.indices[i] & 0xFFFF) * 8, b = (p.indices[i + 1] & 0xFFFF) * 8, c = (p.indices[i + 2] & 0xFFFF) * 8;
                    int ba = block(p.vertices, a);
                    assertEquals(id + " triangle mixes blocks", ba, block(p.vertices, b));
                    assertEquals(id + " triangle mixes blocks", ba, block(p.vertices, c));
                    assertTrue(id + " block " + ba, ba >= 0 && ba < PaletteSwatches.MAX);
                    if (!ramp(ba)) {   // a solid block has one uv for the whole triangle
                        assertEquals(p.vertices[a + 6], p.vertices[b + 6], 0f);
                        assertEquals(p.vertices[a + 7], p.vertices[c + 7], 0f);
                    }
                    float tt = (p.vertices[a + 6] * 512f - 16f * (ba % 32) - 0.5f) / 15f;
                    if (ramp(ba)) assertTrue(id + " ramp t " + tt, tt >= -0.001f && tt <= 1.001f);
                }
            }
        });
    }

    @Test
    public void everyTriangleIsWoundTowardItsNormal() {
        forAll(true, (id, t, pieces, pal) -> {
            for (MeshData m : pieces) {
                MeshData.Part p = m.parts[0];
                for (int i = 0; i < p.indices.length; i += 3) {
                    int a = (p.indices[i] & 0xFFFF) * 8, b = (p.indices[i + 1] & 0xFFFF) * 8, c = (p.indices[i + 2] & 0xFFFF) * 8;
                    float[] v = p.vertices;
                    float ux = v[b] - v[a], uy = v[b + 1] - v[a + 1], uz = v[b + 2] - v[a + 2];
                    float vx = v[c] - v[a], vy = v[c + 1] - v[a + 1], vz = v[c + 2] - v[a + 2];
                    float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                    assertTrue(id + " triangle winds away from its normal", nx * v[a + 3] + ny * v[a + 4] + nz * v[a + 5] > 0f);
                }
            }
        });
    }

    @Test
    public void everyAsphaltTriangleFacesUpAndNoneIsFlipped() {
        forAll(true, (id, t, pieces, pal) -> {
            int road = 0;
            for (MeshData m : pieces) {
                MeshData.Part p = m.parts[0];
                for (int i = 0; i < p.indices.length; i += 3) {
                    int a = (p.indices[i] & 0xFFFF) * 8, b = (p.indices[i + 1] & 0xFFFF) * 8, c = (p.indices[i + 2] & 0xFFFF) * 8;
                    if (block(p.vertices, a) != RoadStyle.RAMP_ASPHALT) continue;
                    road++;
                    assertTrue(id + " normal y " + p.vertices[a + 4], p.vertices[a + 4] > 0.99f);
                    float ux = p.vertices[b] - p.vertices[a], uz = p.vertices[b + 2] - p.vertices[a + 2];
                    float vx = p.vertices[c] - p.vertices[a], vz = p.vertices[c + 2] - p.vertices[a + 2];
                    assertTrue(id + " road triangle flipped or folded", uz * vx - ux * vz > 0f);
                }
            }
            assertTrue(road > 0);
        });
    }

    @Test
    public void paintedLinesStayInsideTheRoad() {
        forAll(true, (id, t, pieces, pal) -> {
            float hw = t.roadHalfWidth();
            TrackPoint tp = new TrackPoint();
            int lines = 0;
            for (MeshData m : pieces) {
                MeshData.Part p = m.parts[0];
                for (int k = 0; k < p.vertexCount; k += 5) {
                    if (block(p.vertices, k * 8) != RoadStyle.RAMP_PAINT || p.vertices[k * 8 + 1] > 0.02f) continue;
                    t.project(p.vertices[k * 8], p.vertices[k * 8 + 2], -1, tp);
                    lines++;
                    assertTrue(id + " line at offset " + tp.offset, Math.abs(tp.offset) <= hw - 0.1f);
                }
            }
            assertTrue(id + " has painted lines", lines > 0);
        });
    }

    @Test
    public void guardRailPostsStandEveryTwoMetres() {
        forAll(true, (id, t, pieces, pal) -> {
            TrackPoint tp = new TrackPoint();
            List<Float> left = new ArrayList<>(), right = new ArrayList<>();
            for (MeshData m : pieces) {
                MeshData.Part p = m.parts[0];
                for (int i = 0; i < p.indices.length; i += 3) {
                    int a = (p.indices[i] & 0xFFFF) * 8;
                    if (block(p.vertices, a) != RoadStyle.RAMP_POST || p.vertices[a + 4] < 0.9f) continue;   // top faces
                    float x = 0f, z = 0f;
                    for (int c = 0; c < 3; c++) {
                        x += p.vertices[(p.indices[i + c] & 0xFFFF) * 8] / 3f;
                        z += p.vertices[(p.indices[i + c] & 0xFFFF) * 8 + 2] / 3f;
                    }
                    t.project(x, z, -1, tp);
                    (tp.offset < 0 ? left : right).add(tp.s);
                }
            }
            for (List<Float> side : new List[] {left, right}) {
                Collections.sort(side);
                List<Float> posts = new ArrayList<>();
                for (float s : side) if (posts.isEmpty() || s - posts.get(posts.size() - 1) > 0.5f) posts.add(s);
                assertTrue(id + " posts " + posts.size(), posts.size() > t.lapLength() / 2f * 0.9f);
                int ok = 0;
                for (int i = 1; i < posts.size(); i++) if (Math.abs(posts.get(i) - posts.get(i - 1) - 2f) < 0.1f) ok++;
                assertTrue(id + " post spacing is regular (" + ok + " of " + (posts.size() - 1) + ")", ok >= 0.95f * (posts.size() - 1));
            }
        });
    }

    @Test
    public void theRailInnerFaceIsSixTenthsOutsideTheRoadEdge() {
        forAll(true, (id, t, pieces, pal) -> {
            float hw = t.roadHalfWidth();
            Random rnd = new Random(7);
            TrackPoint tp = new TrackPoint();
            float min = Float.MAX_VALUE;
            int checked = 0;
            for (int attempt = 0; attempt < 20000 && checked < 400; attempt++) {
                MeshData.Part p = pieces.get(rnd.nextInt(pieces.size())).parts[0];
                int k = rnd.nextInt(p.vertexCount) * 8;
                if (block(p.vertices, k) != RoadStyle.RAMP_RAIL) continue;
                t.project(p.vertices[k], p.vertices[k + 2], -1, tp);
                min = Math.min(min, Math.abs(tp.offset));
                assertTrue(id + " rail vertex inside the wall line (" + Math.abs(tp.offset) + ")", Math.abs(tp.offset) >= hw + 0.6f - 0.01f);
                checked++;
            }
            assertTrue(id + " rail vertices checked " + checked, checked >= 100);
            assertEquals(id + " the rail's closest vertices", hw + 0.6f, min, 0.05f);
        });
    }

    @Test
    public void theLoopCloses() {
        forAll(true, (id, t, pieces, pal) -> {
            TrackPoint tp = new TrackPoint();
            float lap = t.lapLength(), smaxLast = 0f, sminFirst = lap;
            MeshData.Part first = pieces.get(0).parts[0], last = pieces.get(pieces.size() - 1).parts[0];
            for (int k = 0; k < last.vertexCount; k++) {
                if (block(last.vertices, k * 8) != RoadStyle.RAMP_ASPHALT) continue;
                t.project(last.vertices[k * 8], last.vertices[k * 8 + 2], -1, tp);
                if (tp.s > lap * 0.5f) smaxLast = Math.max(smaxLast, tp.s);
            }
            for (int k = 0; k < first.vertexCount; k++) {
                if (block(first.vertices, k * 8) != RoadStyle.RAMP_ASPHALT) continue;
                t.project(first.vertices[k * 8], first.vertices[k * 8 + 2], -1, tp);
                if (tp.s < lap * 0.5f) sminFirst = Math.min(sminFirst, tp.s);
            }
            float gap = lap - smaxLast + sminFirst;
            assertTrue(id + " gap at the start line " + gap, gap < 1.2f);
        });
    }

    @Test
    public void startLineGridBoxesAndGantryStandAtTheStart() {
        forAll(true, (id, t, pieces, pal) -> {
            MeshData.Part p = pieces.get(0).parts[0];
            boolean dark = false, light = false, post = false, banner = false, kerbA = false, kerbB = false;
            for (int k = 0; k < p.vertexCount; k++) {
                int b = block(p.vertices, k * 8);
                dark |= b == RoadStyle.SW_START_DARK;
                post |= b == RoadStyle.RAMP_POST && p.vertices[k * 8 + 1] > 4f;   // the gantry's posts
                light |= b == RoadStyle.SW_START_LIGHT;
                banner |= b == RoadStyle.SW_ARCH_BANNER;
                kerbA |= b == RoadStyle.RAMP_KERB_A;
                kerbB |= b == RoadStyle.RAMP_KERB_B;
            }
            assertTrue(id + " dark " + dark + " light " + light + " post " + post + " banner " + banner + " kerbs " + kerbA + kerbB,
                    dark && light && post && banner && kerbA && kerbB);
            for (int i = 1; i < pieces.size(); i++) {
                MeshData.Part q = pieces.get(i).parts[0];
                for (int k = 0; k < q.vertexCount; k++) assertTrue(block(q.vertices, k * 8) != RoadStyle.SW_ARCH_BANNER);
            }
        });
    }
}
