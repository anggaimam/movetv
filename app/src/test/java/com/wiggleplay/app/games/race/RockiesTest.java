package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import com.wiggleplay.app.gl.StaticBatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Rocky Lakes (T-30b): the lakes, their water mesh, the shores, the props, the backdrop and the budgets. */
public class RockiesTest {
    private static final Theme R = Themes.ROCKIES;
    private static final TrackDef DEF = TrackDefs.byId("rockies");

    private static TrackSpline forward(RaceLevel level) {
        return TrackSpline.build(DEF, false, level);
    }

    private static PaletteSwatches palette() {
        PaletteSwatches p = new PaletteSwatches();
        R.fillPalette(p);
        return p;
    }

    private static int block(MeshData.Part p, int vertex) {
        float u = p.vertices[vertex * 8 + 6] * 512f, v = p.vertices[vertex * 8 + 7] * 512f;
        return (int) Math.floor(u / 16f) + 32 * ((int) Math.floor(v / 16f) - 1);
    }

    @Test
    public void themeIsRegisteredAndFitsThePalette() {
        assertTrue(Themes.byId("rockies") == R);
        assertTrue(R.hasSky());
        assertEquals(4, R.extraCount());
        for (Theme.PropRule r : R.props) assertEquals("kind " + r.kind, PropMeshes.RAMPS[r.kind], r.ramps());
        assertTrue(R.fillPalette(new PaletteSwatches()) < PaletteSwatches.MAX);
        assertEquals(3, R.rings.length);
        assertEquals(Theme.ROLLING, R.rings[0].profile);
        assertEquals(Theme.RIDGE, R.rings[1].profile);
        assertEquals(Theme.PEAKS, R.rings[2].profile);
        assertTrue(!R.rings[0].low && R.rings[1].low && R.rings[2].low);
        assertEquals(Theme.SLICK_ICE, R.slick);
    }

    @Test
    public void fourLakesKeepNineAndAHalfMetresFromTheRoadAndDoNotOverlap() {
        Lakes lk = Lakes.of(DEF);
        assertEquals(4, lk.count());
        for (RaceLevel level : new RaceLevel[] {RaceLevel.EASY, RaceLevel.MEDIUM, RaceLevel.HARD}) {
            for (boolean reversed : new boolean[] {false, true}) {
                TrackSpline t = TrackSpline.build(DEF, reversed, level);
                float[] o = new float[3];
                float min = Float.MAX_VALUE;
                for (int i = 0; i < t.sampleCount(); i++) min = Math.min(min, lk.edge(t.sampleX(i), t.sampleZ(i)));
                System.out.println("Rocky Lakes " + level + (reversed ? " reversed" : "") + ": nearest lake edge to the centre line " + min + " m");
                assertTrue("clearance " + min, min >= 9.5f);
            }
        }
        float[] p = new float[2];
        for (int i = 0; i < lk.count(); i++) {
            for (int j = 0; j < lk.count(); j++) {
                if (i == j) continue;
                for (int q = 0; q < 64; q++) {
                    lk.point(i, (float) (2 * Math.PI * q / 64), 1f, p);
                    assertTrue("lakes " + i + " and " + j + " overlap", lk.edge(j, p[0], p[1]) > 1f);
                }
            }
        }
    }

    @Test
    public void edgeDistanceIsSignedAndMatchesTheEllipse() {
        Lakes lk = Lakes.of(DEF);
        assertTrue(lk.edge(24f, 64f) < -10f);   // the middle of lake 1
        assertEquals(0f, lk.edge(0, 24f + 0f, 64f + 26f), 0.3f);   // lake 1 is turned 90 degrees: rx runs along z
        assertTrue(lk.edge(0, 24f + 15f + 5f, 64f) > 4.5f);
        float[] p = new float[2];
        lk.point(2, 0f, 1f, p);
        assertEquals(-20f + 28f, p[0], 1e-3f);
        assertEquals(-24f, p[1], 1e-3f);
    }

    @Test
    public void lakeMeshStaysUnderItsLimitInsideTheLakeFacingUp() {
        PaletteSwatches pal = palette();
        Lakes lk = Lakes.of(DEF);
        for (int i = 0; i < lk.count(); i++) {
            MeshData m = LakeMesh.build(lk, i, pal, R.extraSwatch(0), R.extraSwatch(1));
            assertTrue("lake " + i + " has " + m.triangleCount(), m.triangleCount() <= LakeMesh.MAX_TRIANGLES);
            MeshData.Part p = m.parts[0];
            for (int v = 0; v < p.vertexCount; v++) {
                assertEquals(LakeMesh.Y, p.vertices[v * 8 + 1], 1e-6f);
                assertTrue("vertex outside lake " + i, lk.edge(i, p.vertices[v * 8], p.vertices[v * 8 + 2]) <= 0.05f);
                assertEquals(1f, p.vertices[v * 8 + 4], 1e-4f);
            }
            for (int t = 0; t < p.indices.length; t += 3) {
                int a = (p.indices[t] & 0xFFFF) * 8, b = (p.indices[t + 1] & 0xFFFF) * 8, c = (p.indices[t + 2] & 0xFFFF) * 8;
                float ux = p.vertices[b] - p.vertices[a], uz = p.vertices[b + 2] - p.vertices[a + 2];
                float wx = p.vertices[c] - p.vertices[a], wz = p.vertices[c + 2] - p.vertices[a + 2];
                assertTrue("triangle faces down", uz * wx - ux * wz > 0f);
                int blk = block(p, p.indices[t] & 0xFFFF);
                assertEquals(blk, block(p, p.indices[t + 1] & 0xFFFF));
                assertEquals(blk, block(p, p.indices[t + 2] & 0xFFFF));
                assertTrue(blk == R.extraSwatch(0) || blk == R.extraSwatch(1));
            }
        }
    }

    @Test
    public void waterRampIsDeepInTheMiddleAndShallowAtTheEdge() {
        PaletteSwatches pal = palette();
        Lakes lk = Lakes.of(DEF);
        MeshData.Part p = LakeMesh.build(lk, 0, pal, R.extraSwatch(0), R.extraSwatch(1)).parts[0];
        float prevT = -1f;
        float minEdge = Float.MAX_VALUE;
        for (int v = 0; v < p.vertexCount; v++) {
            if (block(p, v) != R.extraSwatch(0)) continue;
            float u = p.vertices[v * 8 + 6] * 512f;
            float t = (u - 16f * (float) Math.floor(u / 16f) - 0.5f) / 15f;
            float e = -lk.edge(0, p.vertices[v * 8], p.vertices[v * 8 + 2]);
            // the deeper (further from the edge), the lower t
            if (e > 12f) assertTrue("t " + t + " at " + e, t < 0.35f);
            if (e < 3f) assertTrue("t " + t + " at " + e, t > 0.7f);
            minEdge = Math.min(minEdge, e);
        }
        assertTrue(minEdge < 3f);
    }

    @Test
    public void shoresAreFlatAndNoTerrainCellLiesInsideALake() {
        Lakes lk = Lakes.of(DEF);
        Terrain te = Terrain.create(forward(RaceLevel.HARD), R, DEF.id.hashCode(), false, lk);
        float[] p = new float[2];
        for (int i = 0; i < lk.count(); i++) {
            for (int q = 0; q < 96; q++) {
                float a = (float) (2 * Math.PI * q / 96);
                for (float away : new float[] {0f, 1.5f, 3f}) {
                    lk.point(i, a, 1f, p);
                    float cx = lk.centreX(i), cz = lk.centreZ(i);
                    float dx = p[0] - cx, dz = p[1] - cz, l = (float) Math.sqrt(dx * dx + dz * dz);
                    float x = p[0] + dx / l * away, z = p[1] + dz / l * away;
                    if (lk.edge(x, z) < 0f) continue;
                    assertEquals("shore " + i + " " + q + " away " + away, Terrain.ROAD_Y, te.height(x, z), 0.05f);
                }
            }
        }
        PaletteSwatches pal = palette();
        for (Terrain.Tile tile : te.build(pal, R.terrainSwatch(0), R.terrainSwatch(1), -1, R.extraSwatch(2), R.extraSwatch(3))) {
            MeshData.Part part = tile.mesh.parts[0];
            for (int t = 0; t < part.indices.length; t += 3) {
                boolean allInside = true;
                for (int c = 0; c < 3; c++) {
                    int o = (part.indices[t + c] & 0xFFFF) * 8;
                    if (lk.edge(part.vertices[o], part.vertices[o + 2]) >= -0.2f) allInside = false;
                }
                assertTrue("a terrain triangle lies wholly inside a lake", !allInside);
            }
        }
    }

    @Test
    public void hillsRiseToEightMetresAndSteepCellsAreRock() {
        Terrain te = Terrain.create(forward(RaceLevel.HARD), R, DEF.id.hashCode(), false, Lakes.of(DEF));
        PaletteSwatches pal = palette();
        float max = 0f;
        int rock = 0, ground = 0;
        for (Terrain.Tile tile : te.build(pal, R.terrainSwatch(0), R.terrainSwatch(1), -1, R.extraSwatch(2), R.extraSwatch(3))) {
            MeshData.Part part = tile.mesh.parts[0];
            for (int t = 0; t < part.indices.length; t += 3) {
                int a = (part.indices[t] & 0xFFFF), b = (part.indices[t + 1] & 0xFFFF), c = (part.indices[t + 2] & 0xFFFF);
                for (int q : new int[] {a, b, c}) max = Math.max(max, part.vertices[q * 8 + 1]);
                float ny = part.vertices[a * 8 + 4];
                int blk = block(part, a);
                if (blk == R.extraSwatch(3)) {
                    rock++;
                } else if (blk == R.terrainSwatch(0)) {
                    ground++;
                    float ux = part.vertices[b * 8] - part.vertices[a * 8], uy = part.vertices[b * 8 + 1] - part.vertices[a * 8 + 1];
                    float uz = part.vertices[b * 8 + 2] - part.vertices[a * 8 + 2];
                    float wx = part.vertices[c * 8] - part.vertices[a * 8], wy = part.vertices[c * 8 + 1] - part.vertices[a * 8 + 1];
                    float wz = part.vertices[c * 8 + 2] - part.vertices[a * 8 + 2];
                    float nx = uy * wz - uz * wy, nyy = uz * wx - ux * wz, nz = ux * wy - uy * wx;
                    float l = (float) Math.sqrt(nx * nx + nyy * nyy + nz * nz);
                    assertTrue("a steep cell kept the grass ramp", Math.abs(nyy) / l >= Terrain.ROCK_NY - 1e-3f);
                }
            }
        }
        System.out.println("Rocky Lakes terrain: highest " + max + " m, rock cells " + rock + ", grass cells " + ground);
        assertTrue("highest " + max, max > 3f && max <= 8.5f);
        assertTrue(rock > 0);
    }

    private static List<PropScatter.Instance> scatter(boolean tierB, Terrain te) {
        List<PropScatter.Instance> inst = new ArrayList<>();
        PropScatter.scatter(forward(RaceLevel.HARD), R, DEF.id.hashCode(), tierB, new ArrayList<StaticBatcher.Placement>(), te, inst);
        return inst;
    }

    @Test
    public void propsKeepOutOfLakesDocksAndCanoesStandOnTheShores() {
        Lakes lk = Lakes.of(DEF);
        Terrain te = Terrain.create(forward(RaceLevel.HARD), R, DEF.id.hashCode(), false, lk);
        int docks = 0, canoes = 0, total = 0;
        TrackSpline t = forward(RaceLevel.HARD);
        for (PropScatter.Instance in : scatter(true, te)) {
            total++;
            if (in.kind == PropMeshes.DOCK) {
                docks++;
                assertEquals(0f, lk.edge(in.x, in.z), 0.6f);
                assertTrue(PropScatter.nearestRoad(t, in.x, in.z) >= 9f);
            } else if (in.kind == PropMeshes.CANOE) {
                canoes++;
                assertTrue(lk.edge(in.x, in.z) > 0.3f && lk.edge(in.x, in.z) < 3f);
                assertTrue(PropScatter.nearestRoad(t, in.x, in.z) >= 8f);
            } else {
                assertTrue("kind " + in.kind + " at " + in.x + ", " + in.z + " edge " + lk.edge(in.x, in.z), lk.edge(in.x, in.z) >= 3f);
            }
        }
        assertEquals(2, docks);
        assertEquals(4, canoes);
        System.out.println("Rocky Lakes: " + total + " props on the lap");
    }

    @Test
    public void outcropsStandOnlyOnSlopes() {
        Terrain te = Terrain.create(forward(RaceLevel.HARD), R, DEF.id.hashCode(), false, Lakes.of(DEF));
        int n = 0;
        for (PropScatter.Instance in : scatter(true, te)) {
            if (in.kind != PropMeshes.ROCK_OUTCROP) continue;
            n++;
            assertTrue("slope " + te.slopeDeg(in.x, in.z), te.slopeDeg(in.x, in.z) >= PropScatter.OUTCROP_MIN_SLOPE - 0.01f);
        }
        System.out.println("Rocky Lakes: " + n + " outcrops");
    }

    @Test
    public void groveKeepsAllPinesWithinTenMetres() {
        assertTrue("reach " + PropMeshes.reach(PropMeshes.PINE_GROVE), PropMeshes.reach(PropMeshes.PINE_GROVE) <= 10f);
        assertTrue(PropMeshes.reach(PropMeshes.PINE_GROVE) >= 3f);
        assertEquals(11f, PropMeshes.height(PropMeshes.PINE, 2), 0.6f);
        assertEquals(5f, PropMeshes.height(PropMeshes.PINE, 0), 0.4f);
    }

    @Test
    public void ridgeStaysInsideItsRangeAndClosesTheCircle() {
        Theme.Ring r = R.rings[1];
        Backdrop.Profile p = Backdrop.profile(r);
        for (int c = 0; c < r.segments; c++) {
            assertTrue(p.left[c] >= r.minH - 1e-4f && p.left[c] <= r.maxH + 1e-4f);
            assertTrue(p.right[c] >= r.minH - 1e-4f && p.right[c] <= r.maxH + 1e-4f);
            assertEquals(p.right[c], p.left[(c + 1) % r.segments], 1e-4f);
        }
        assertEquals(2 * r.segments, Backdrop.triangleCount(r));
    }

    @Test
    public void snowLineWobblesInsideItsBoundsOnTheFarPeaks() {
        Theme.Ring r = R.rings[2];
        Backdrop.Profile p = Backdrop.profile(r);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int c = 0; c < r.segments; c++) {
            float h = p.left[c];
            if (h < 8f) continue;
            float ratio = Backdrop.snowLine(r, c, h) / h;
            min = Math.min(min, ratio);
            max = Math.max(max, ratio);
            assertTrue(ratio >= 0.4f - 1e-4f && ratio <= 0.95f + 1e-4f);
        }
        assertTrue("snow line ratio " + min + " to " + max, max - min > 0.05f);
        assertEquals(4 * r.segments, Backdrop.triangleCount(r));
    }

    private static int staticTriangles(boolean low) {
        TrackSpline t = forward(RaceLevel.HARD);
        PaletteSwatches pal = palette();
        Lakes lk = Lakes.of(DEF);
        int tris = 0;
        for (MeshData m : TrackMeshBuilder.build(t, R.road, pal, R.terrainSwatch(1))) tris += m.triangleCount();
        Terrain te = Terrain.create(t, R, DEF.id.hashCode(), low, lk);
        for (Terrain.Tile tile : te.build(pal, R.terrainSwatch(0), R.terrainSwatch(1), -1, R.extraSwatch(2), R.extraSwatch(3))) tris += tile.triangles;
        for (int i = 0; i < lk.count(); i++) tris += LakeMesh.build(lk, i, pal, R.extraSwatch(0), R.extraSwatch(1)).triangleCount();
        Map<String, MeshData> lib = new HashMap<>();
        for (int r = 0; r < R.props.length; r++) {
            for (int v = 0; v < PropMeshes.variants(R.props[r].kind); v++) {
                lib.put(PropMeshes.name(R.props[r].kind, v), PropMeshes.build(R.props[r].kind, v, pal, R.propSwatches(r)));
            }
        }
        List<PropScatter.Instance> inst = new ArrayList<>();
        PropScatter.scatter(t, R, DEF.id.hashCode(), !low, new ArrayList<StaticBatcher.Placement>(), te, inst);
        for (PropScatter.Instance in : inst) tris += lib.get(PropMeshes.name(in.kind, in.variant)).triangleCount();
        tris += PropShadows.build(te, pal, R.terrainSwatch(0), inst, new HashMap<String, MeshData>(), new ArrayList<StaticBatcher.Placement>());
        return tris;
    }

    @Test
    public void staticWorldStaysUnderItsBudget() {
        int high = staticTriangles(false), low = staticTriangles(true);
        System.out.println("Rocky Lakes: static high " + high + ", low " + low);
        assertTrue("high " + high, high <= 140000);
        assertTrue("low " + low, low <= 80000);
    }

    @Test
    public void sameTrackGivesTheSameLayout() {
        Terrain te = Terrain.create(forward(RaceLevel.HARD), R, DEF.id.hashCode(), false, Lakes.of(DEF));
        List<PropScatter.Instance> a = scatter(true, te), b = scatter(true, te);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).x, b.get(i).x, 0f);
    }
}
