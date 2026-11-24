package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.List;
import java.util.Random;
import org.junit.Test;

public class TrackMeshBuilderTest {

    private interface Check {
        void run(String id, TrackSpline t, List<MeshData> pieces, PaletteSwatches pal);
    }

    private static void forAll(boolean dashes, Check c) {
        for (RaceLevel lv : new RaceLevel[] {RaceLevel.EASY, RaceLevel.HARD}) {
            for (TrackDef def : TrackDefs.ALL) {
                for (boolean rev : new boolean[] {false, true}) {
                    TrackSpline t = TrackSpline.build(def, rev, lv);
                    PaletteSwatches pal = new PaletteSwatches();
                    RoadStyle style = dashes
                            ? new RoadStyle(0x5A5F66, 0xE8574A, 0xF3EBDD, 0xD7DCE0, 0xE89A4C, 0x2F3A45, 0xF3EBDD, 0xF3EBDD, 0xE89A4C, 0xFFFFFF)
                            : RoadStyle.MEADOW;
                    c.run(TrackSpline.key(def, rev) + " " + lv, t, TrackMeshBuilder.build(t, style, pal), pal);
                }
            }
        }
    }

    private static boolean sameUv(float[] v, int o, PaletteSwatches pal, int sw) {
        return Math.abs(v[o + 6] - pal.u(sw)) < 1e-6f && Math.abs(v[o + 7] - pal.v(sw)) < 1e-6f;
    }

    @Test
    public void budgetPiecesAndVertexLimit() {
        forAll(false, (id, t, pieces, pal) -> {
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
            System.out.printf("%-12s L=%.1f pieces=%d triangles=%d (%.2f/m, budget %.0f) vertices=%d (%.2f/m)%n", id, lap, pieces.size(),
                    tris, tris / lap, 18.5f * lap + 120f, verts, verts / lap);
            assertTrue(id + " triangles " + tris, tris <= 18.5f * lap + 120f);
            assertTrue(id + " vertices " + verts, verts <= 36f * lap);
        });
    }

    @Test
    public void everyRoadSurfaceTriangleFacesUpAndNoneIsFlipped() {
        forAll(false, (id, t, pieces, pal) -> {
            int road = 0;
            for (MeshData m : pieces) {
                MeshData.Part p = m.parts[0];
                for (int i = 0; i < p.indices.length; i += 3) {
                    int a = (p.indices[i] & 0xFFFF) * 8, b = (p.indices[i + 1] & 0xFFFF) * 8, c = (p.indices[i + 2] & 0xFFFF) * 8;
                    if (!sameUv(p.vertices, a, pal, RoadStyle.SW_ROAD) || p.vertices[a + 1] != 0f) continue;
                    road++;
                    assertTrue(id + " normal y " + p.vertices[a + 4], p.vertices[a + 4] > 0.99f);
                    float ux = p.vertices[b] - p.vertices[a], uz = p.vertices[b + 2] - p.vertices[a + 2];
                    float vx = p.vertices[c] - p.vertices[a], vz = p.vertices[c + 2] - p.vertices[a + 2];
                    float crossY = uz * vx - ux * vz;   // positive = counter-clockwise seen from above
                    assertTrue(id + " road triangle flipped or folded (cross y " + crossY + ")", crossY > 0f);
                }
            }
            assertTrue(road > 0);
        });
    }

    @Test
    public void everyTriangleIsWoundTowardItsNormalAndAllFourCornersOfAQuadShareOneUv() {
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
                    // the three corners of a triangle share one uv, and it is the centre of a swatch block
                    assertEquals(v[a + 6], v[b + 6], 0f);
                    assertEquals(v[a + 6], v[c + 6], 0f);
                    assertEquals(v[a + 7], v[b + 7], 0f);
                    assertEquals(v[a + 7], v[c + 7], 0f);
                }
                // a vertex belongs to one colour only: every vertex uv is the centre of one of the road swatches
                for (int k = 0; k < p.vertexCount; k++) {
                    boolean known = false;
                    for (int sw = 0; sw <= RoadStyle.SW_CENTRE_DASH; sw++) known |= sameUv(p.vertices, k * 8, pal, sw);
                    assertTrue(id + " vertex " + k + " has a uv that is not a road swatch centre", known);
                }
            }
        });
    }

    @Test
    public void quadsHaveFourVerticesOfOneUvAndDifferentColoursNeverShareVertices() {
        forAll(false, (id, t, pieces, pal) -> {
            for (MeshData m : pieces) {
                MeshData.Part p = m.parts[0];
                // map each vertex to the colours of the triangles that use it
                int[] colour = new int[p.vertexCount];
                java.util.Arrays.fill(colour, -1);
                for (int i = 0; i < p.indices.length; i++) {
                    int vi = p.indices[i] & 0xFFFF, sw = -1;
                    for (int k = 0; k <= RoadStyle.SW_CENTRE_DASH && sw < 0; k++) if (sameUv(p.vertices, vi * 8, pal, k)) sw = k;
                    if (colour[vi] >= 0) assertEquals(id + " vertex shared by two colours", colour[vi], sw);
                    colour[vi] = sw;
                }
            }
        });
    }

    @Test
    public void theBarrierInnerFaceIsSixTenthsOutsideTheRoadEdge() {
        forAll(false, (id, t, pieces, pal) -> {
            float hw = t.roadHalfWidth();
            Random rnd = new Random(7);
            TrackPoint tp = new TrackPoint();
            int checked = 0;
            for (int attempt = 0; attempt < 5000 && checked < 50; attempt++) {
                MeshData.Part p = pieces.get(rnd.nextInt(pieces.size())).parts[0];
                int k = rnd.nextInt(p.vertexCount) * 8;
                boolean barrier = sameUv(p.vertices, k, pal, RoadStyle.SW_BARRIER) || sameUv(p.vertices, k, pal, RoadStyle.SW_BARRIER_STRIPE);
                if (!barrier || Math.abs(p.vertices[k + 4]) > 0.01f) continue;   // vertical faces only
                t.project(p.vertices[k], p.vertices[k + 2], -1, tp);
                float rx = -(float) Math.cos(tp.tangentYaw), rz = (float) Math.sin(tp.tangentYaw);
                float towardCentre = -(p.vertices[k + 3] * rx + p.vertices[k + 5] * rz) * Math.signum(tp.offset);
                float expect = towardCentre > 0f ? hw + 0.6f : hw + 0.9f;   // the face that looks at the road is the inner one
                assertEquals(id + " vertex at s=" + tp.s, expect, Math.abs(tp.offset), 0.01f);
                checked++;
            }
            assertEquals(50, checked);
        });
    }

    @Test
    public void theLoopCloses() {
        forAll(false, (id, t, pieces, pal) -> {
            MeshData.Part first = pieces.get(0).parts[0], last = pieces.get(pieces.size() - 1).parts[0];
            // the road strip is the first block of every piece: its first two vertices are the left and right edge of the
            // first row, its last two those of the last row
            int strip = 0;
            while (strip < last.vertexCount && last.vertices[strip * 8 + 1] == 0f && sameUv(last.vertices, strip * 8, pal, RoadStyle.SW_ROAD)) strip++;
            assertTrue(strip >= 4 && strip % 2 == 0);
            for (int e = 0; e < 2; e++) {
                float dx = first.vertices[e * 8] - last.vertices[(strip - 2 + e) * 8];
                float dz = first.vertices[e * 8 + 2] - last.vertices[(strip - 2 + e) * 8 + 2];
                assertTrue(id + " edge " + e + " gap " + Math.sqrt(dx * dx + dz * dz), dx * dx + dz * dz < 0.01f * 0.01f);
            }
        });
    }

    @Test
    public void startLineAndArchStandAtTheStartAndKerbsAlternate() {
        forAll(false, (id, t, pieces, pal) -> {
            MeshData.Part p = pieces.get(0).parts[0];
            boolean dark = false, light = false, post = false, banner = false, kerbA = false, kerbB = false, barrierStripe = false;
            float maxY = 0f;
            for (int k = 0; k < p.vertexCount; k++) {
                dark |= sameUv(p.vertices, k * 8, pal, RoadStyle.SW_START_DARK);
                light |= sameUv(p.vertices, k * 8, pal, RoadStyle.SW_START_LIGHT);
                post |= sameUv(p.vertices, k * 8, pal, RoadStyle.SW_ARCH_POST);
                banner |= sameUv(p.vertices, k * 8, pal, RoadStyle.SW_ARCH_BANNER);
                kerbA |= sameUv(p.vertices, k * 8, pal, RoadStyle.SW_KERB_A);
                kerbB |= sameUv(p.vertices, k * 8, pal, RoadStyle.SW_KERB_B);
                barrierStripe |= sameUv(p.vertices, k * 8, pal, RoadStyle.SW_BARRIER_STRIPE);
                maxY = Math.max(maxY, p.vertices[k * 8 + 1]);
            }
            assertTrue(id, dark && light && post && banner && kerbA && kerbB && barrierStripe);
            assertEquals(id + " the banner top", 6.2f, maxY, 1e-4f);
            // no arch or start line in any other piece
            for (int i = 1; i < pieces.size(); i++) {
                MeshData.Part q = pieces.get(i).parts[0];
                for (int k = 0; k < q.vertexCount; k++) assertTrue(!sameUv(q.vertices, k * 8, pal, RoadStyle.SW_ARCH_POST));
            }
        });
    }

    @Test
    public void centreDashesOnlyWhenTheStyleAsksForThem() {
        forAll(true, (id, t, pieces, pal) -> {
            int dashQuads = 0;
            for (MeshData m : pieces) {
                MeshData.Part p = m.parts[0];
                for (int k = 0; k < p.vertexCount; k++) if (sameUv(p.vertices, k * 8, pal, RoadStyle.SW_CENTRE_DASH)) dashQuads++;
            }
            // 3 m dash, 3 m gap: about one quarter of the rows have a dash quad (4 vertices each)
            float expected = t.lapLength() / 2f * 4f;
            assertTrue(id + " dash vertices " + dashQuads, dashQuads > 0.8f * expected && dashQuads < 1.2f * expected);
        });
        // swatch 9 holds a dash colour only when the style has dashes
        PaletteSwatches pal = new PaletteSwatches();
        TrackMeshBuilder.build(TrackSpline.build(TrackDefs.ALL[0], false, RaceLevel.EASY), RoadStyle.MEADOW, pal);
        assertEquals(RoadStyle.MEADOW.road, pal.rgb(RoadStyle.SW_CENTRE_DASH));
    }
}
