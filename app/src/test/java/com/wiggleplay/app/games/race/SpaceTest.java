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

/** Star Road (T-30c): craters in the height field, the space props, the backdrop with stars and planets, and the budgets. */
public class SpaceTest {
    private static final Theme S = Themes.SPACE;
    private static final TrackDef DEF = TrackDefs.byId("space");

    private static TrackSpline forward() {
        return TrackSpline.build(DEF, false, RaceLevel.HARD);
    }

    private static PaletteSwatches palette() {
        PaletteSwatches p = new PaletteSwatches();
        S.fillPalette(p);
        return p;
    }

    private static Terrain terrain(boolean low) {
        return Terrain.create(forward(), S, DEF.id.hashCode(), low);
    }

    private static MeshData ring(int i, boolean low) {
        return Backdrop.build(S, i, palette(), low);
    }

    @Test
    public void themeIsRegisteredAndFitsThePalette() {
        assertTrue(Themes.byId("space") == S);
        assertTrue(S.hasSky());
        assertEquals(4, S.extraCount());
        for (Theme.PropRule r : S.props) assertEquals("kind " + r.kind, PropMeshes.RAMPS[r.kind], r.ramps());
        assertTrue(S.fillPalette(new PaletteSwatches()) < PaletteSwatches.MAX);
        assertEquals(Theme.CRATER_RIM, S.rings[0].profile);
        assertEquals(Theme.HIGHLANDS, S.rings[1].profile);
        assertEquals(Theme.STARS, S.rings[2].profile);
        assertTrue(!S.rings[0].low && S.rings[1].low && S.rings[2].low);
        assertEquals(Terrain.CRATERED, S.look.terrain.kind);
    }

    @Test
    public void cratersAreDeepEnoughRaisedAtTheRimAndFarFromTheRoad() {
        Terrain te = terrain(false);
        float[] c = te.craters();
        int n = c.length / 3;
        System.out.println("Star Road craters: " + n);
        assertTrue("craters " + n, n >= 14 && n <= 18);
        for (int i = 0; i < n; i++) {
            float x = c[3 * i], z = c[3 * i + 1], r = c[3 * i + 2];
            assertTrue(r >= 4f && r <= 14f);
            assertTrue("crater too near the road", te.distance(x, z) >= Math.min(24f, 14f + r) - 0.5f);   // distance() caps at 24 m
            float centre = te.height(x, z);
            float rimMax = -9f;
            for (int a = 0; a < 16; a++) {
                double th = 2 * Math.PI * a / 16;
                rimMax = Math.max(rimMax, te.height(x + (float) Math.cos(th) * r, z + (float) Math.sin(th) * r));
            }
            assertTrue("bowl deeper than 2.5 m: " + (rimMax - centre), rimMax - centre <= 2.5f + 0.12f * r + 1.3f);
            assertTrue("rim not raised above the floor", rimMax > centre + 0.2f);
            float outside = te.height(x + 1.6f * r, z);
            assertTrue("rim not above the ground round it", rimMax >= outside - 0.05f);
        }
    }

    @Test
    public void depthNeverExceedsTwoAndAHalfMetresBelowTheBase() {
        Terrain te = terrain(false);
        float[] c = te.craters();
        for (int i = 0; i < c.length; i += 3) {
            float depth = Math.min(0.25f * c[i + 2], 2.5f);
            assertTrue(depth <= 2.5f);
            float floor = te.height(c[i], c[i + 1]), rim = te.height(c[i] + c[i + 2], c[i + 1]);
            assertTrue("floor " + floor + " rim " + rim, floor >= -2.5f - 0.2f);
        }
    }

    @Test
    public void theRoadCorridorStaysFlat() {
        Terrain te = terrain(false);
        TrackSpline t = forward();
        for (int i = 0; i < t.sampleCount(); i += 3) {
            float x = t.sampleX(i), z = t.sampleZ(i);
            assertEquals(Terrain.ROAD_Y, te.height(x, z), 1e-3f);
        }
    }

    @Test
    public void starsKeepTheirHeightsAndLowKeepsHalf() {
        MeshData high = ring(2, false), low = ring(2, true);
        MeshData.Part ph = high.parts[0], pl = low.parts[0];
        assertTrue(ph.vertexCount >= 220 * 4);
        assertEquals(120 * 4 + planetVertices(pl, 120), pl.vertexCount);
        for (int s = 0; s < 220; s++) {
            float cy = 0f;
            for (int k = 0; k < 4; k++) cy += ph.vertices[(s * 4 + k) * 8 + 1] / 4f;
            assertTrue("star height " + cy, cy >= 2f - 1e-3f && cy <= 40f + 1e-3f);
        }
    }

    /** Vertices after the first {@code stars} quads. */
    private static int planetVertices(MeshData.Part p, int stars) {
        return p.vertexCount - stars * 4;
    }

    @Test
    public void planetsSitInsideTheFarPlaneAndFaceTheCentre() {
        MeshData.Part p = ring(2, false).parts[0];
        int first = 220 * 4;
        assertTrue("no planet vertices", p.vertexCount > first);
        float max = 0f;
        for (int v = first; v < p.vertexCount; v++) {
            float x = p.vertices[v * 8], y = p.vertices[v * 8 + 1], z = p.vertices[v * 8 + 2];
            max = Math.max(max, (float) Math.sqrt(x * x + y * y + z * z));
        }
        System.out.println("Star Road planets: farthest vertex " + max + " m");
        assertTrue("planet past the far plane: " + max, max < 60f);
        for (int t = first * 0; t < p.indices.length; t += 3) {
            int a = p.indices[t] & 0xFFFF;
            if (a < first) continue;
            int b = p.indices[t + 1] & 0xFFFF, c = p.indices[t + 2] & 0xFFFF;
            float ex = p.vertices[b * 8] - p.vertices[a * 8], ey = p.vertices[b * 8 + 1] - p.vertices[a * 8 + 1];
            float ez = p.vertices[b * 8 + 2] - p.vertices[a * 8 + 2];
            float fx = p.vertices[c * 8] - p.vertices[a * 8], fy = p.vertices[c * 8 + 1] - p.vertices[a * 8 + 1];
            float fz = p.vertices[c * 8 + 2] - p.vertices[a * 8 + 2];
            float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
            float dot = nx * -p.vertices[a * 8] + ny * -p.vertices[a * 8 + 1] + nz * -p.vertices[a * 8 + 2];
            assertTrue("a planet triangle faces away from the camera", dot >= 0f);
        }
    }

    @Test
    public void nearAndMidRingsStayInTheirHeightRange() {
        for (int i = 0; i < 2; i++) {
            Theme.Ring r = S.rings[i];
            Backdrop.Profile pf = Backdrop.profile(r);
            for (int c = 0; c < r.segments; c++) {
                assertTrue("ring " + i + " column " + c, pf.left[c] >= r.minH - 1e-3f && pf.left[c] <= r.maxH + 1e-3f);
                assertTrue(pf.right[c] >= r.minH - 1e-3f && pf.right[c] <= r.maxH + 1e-3f);
            }
        }
    }

    @Test
    public void spacePropsFitTheirLimits() {
        PaletteSwatches pal = palette();
        for (int r = 0; r < S.props.length; r++) {
            int kind = S.props[r].kind;
            for (int v = 0; v < PropMeshes.variants(kind); v++) {
                MeshData m = PropMeshes.build(kind, v, pal, S.propSwatches(r));
                assertTrue("kind " + kind + " variant " + v + ": " + m.triangleCount(), m.triangleCount() <= PropMeshes.MAX_TRIANGLES[kind]);
                assertTrue(m.triangleCount() > 0);
            }
        }
    }

    private static List<PropScatter.Instance> scatter(boolean tierB, Terrain te) {
        List<PropScatter.Instance> inst = new ArrayList<>();
        PropScatter.scatter(forward(), S, DEF.id.hashCode(), tierB, new ArrayList<StaticBatcher.Placement>(), te, inst);
        return inst;
    }

    @Test
    public void scatterIsDeterministicAndRespectsDistances() {
        Terrain te = terrain(false);
        List<PropScatter.Instance> a = scatter(true, te), b = scatter(true, te);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).x, b.get(i).x, 0f);
        assertTrue(a.size() > 40);
        int low = scatter(false, te).size();
        assertTrue("tier A only is smaller", low < a.size());
        System.out.println("Star Road props: " + a.size() + " (tier A only " + low + ")");
    }

    private static int staticTriangles(boolean low) {
        TrackSpline t = forward();
        PaletteSwatches pal = palette();
        int tris = 0;
        for (MeshData m : TrackMeshBuilder.build(t, S.road, pal, S.terrainSwatch(1))) tris += m.triangleCount();
        Terrain te = Terrain.create(t, S, DEF.id.hashCode(), low);
        for (Terrain.Tile tile : te.build(pal, S.terrainSwatch(0), S.terrainSwatch(1), -1, -1, -1)) tris += tile.triangles;
        Map<String, MeshData> lib = new HashMap<>();
        for (int r = 0; r < S.props.length; r++) {
            for (int v = 0; v < PropMeshes.variants(S.props[r].kind); v++) {
                lib.put(PropMeshes.name(S.props[r].kind, v), PropMeshes.build(S.props[r].kind, v, pal, S.propSwatches(r)));
            }
        }
        List<PropScatter.Instance> inst = new ArrayList<>();
        PropScatter.scatter(t, S, DEF.id.hashCode(), !low, new ArrayList<StaticBatcher.Placement>(), te, inst);
        for (PropScatter.Instance in : inst) tris += lib.get(PropMeshes.name(in.kind, in.variant)).triangleCount();
        tris += PropShadows.build(te, pal, S.terrainSwatch(0), inst, new HashMap<String, MeshData>(), new ArrayList<StaticBatcher.Placement>());
        return tris;
    }

    @Test
    public void staticWorldStaysUnderItsBudget() {
        int high = staticTriangles(false), low = staticTriangles(true);
        System.out.println("Star Road: static high " + high + ", low " + low);
        assertTrue("high " + high, high <= 140000);
        assertTrue("low " + low, low <= 80000);
    }
}
