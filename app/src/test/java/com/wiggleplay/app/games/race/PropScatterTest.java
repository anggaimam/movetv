package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import com.wiggleplay.app.gl.StaticBatcher;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class PropScatterTest {

    private static final Theme T = Themes.DESERT;

    private static List<StaticBatcher.Placement> scatter(TrackDef def, boolean tierB) {
        List<StaticBatcher.Placement> out = new ArrayList<>();
        TrackSpline t = TrackSpline.build(def, false, 5.5f);
        PropScatter.scatter(t, T, def.id.hashCode(), tierB, out, Terrain.create(t, T, def.id.hashCode(), !tierB),
                new ArrayList<PropScatter.Instance>());
        return out;
    }

    private static int kindOf(StaticBatcher.Placement p) {
        return Integer.parseInt(p.meshName.split("_")[1]);
    }

    private static float scaleOf(StaticBatcher.Placement p) {
        float[] m = p.matrix;
        return (float) Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
    }

    private static float reachOf(StaticBatcher.Placement p) {
        for (Theme.PropRule r : T.props) {
            if (r.kind == kindOf(p)) return Math.max(r.footprint, PropMeshes.reach(r.kind)) * scaleOf(p);
        }
        throw new IllegalStateException();
    }

    @Test
    public void rulesKeepTheirMinimumDistance() {
        for (Theme.PropRule r : T.props) {
            assertTrue("kind " + r.kind, r.minDist >= 6.4f + r.footprint + 0.5f - 1e-4f);
            assertTrue(r.maxDist > r.minDist);
            assertTrue(r.minScale > 0f && r.maxScale >= r.minScale);
        }
    }

    @Test
    public void everyLayoutHasNoPropNearTheRoadOrInTheStartZone() {
        for (TrackDef def : TrackDefs.ALL) {
            TrackSpline t = TrackSpline.build(def, false, 5.5f);
            int n = t.sampleCount();
            float ds = t.lapLength() / n;
            for (StaticBatcher.Placement p : scatter(def, true)) {
                float x = p.matrix[12], z = p.matrix[14];
                float near = Float.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    float d = (float) Math.hypot(t.sampleX(i) - x, t.sampleZ(i) - z);
                    near = Math.min(near, d);
                    boolean startSample = i * ds < 30f || i * ds > t.lapLength() - 30f;
                    if (startSample) assertTrue(def.id + " start zone", d >= 15f);
                }
                assertTrue(def.id + " " + p.meshName + " at " + near + " from the road", near >= 6.4f + reachOf(p) + 0.5f - 1e-3f);
                assertTrue(def.id + " " + p.meshName + " too far from the road (" + near + ")", near <= PropScatter.SEEN_RANGE);
            }
        }
    }

    @Test
    public void propsDoNotOverlapEachOther() {
        for (TrackDef def : TrackDefs.ALL) {
            List<StaticBatcher.Placement> all = scatter(def, true);
            for (int i = 0; i < all.size(); i++) {
                for (int j = i + 1; j < all.size(); j++) {
                    float d = (float) Math.hypot(all.get(i).matrix[12] - all.get(j).matrix[12],
                            all.get(i).matrix[14] - all.get(j).matrix[14]);
                    assertTrue(def.id + " props " + i + " and " + j, d >= reachOf(all.get(i)) + reachOf(all.get(j)) + 1f - 1e-3f);
                }
            }
        }
    }

    @Test
    public void countIsWithin75To100PercentOfTheAsk() {
        for (TrackDef def : TrackDefs.ALL) {
            TrackSpline t = TrackSpline.build(def, false, 5.5f);
            List<StaticBatcher.Placement> all = scatter(def, true);
            for (Theme.PropRule r : T.props) {
                int want = Math.round(r.per100m * t.lapLength() / 100f);
                int got = 0;
                for (StaticBatcher.Placement p : all) if (kindOf(p) == r.kind) got++;
                assertTrue(def.id + " kind " + r.kind + ": " + got + " of " + want, got <= want && got >= Math.floor(0.75 * want));
            }
        }
    }

    @Test
    public void sameSeedGivesSamePlacementsAndAnotherSeedDoesNot() {
        TrackDef def = TrackDefs.ALL[0];
        List<StaticBatcher.Placement> a = scatter(def, true), b = scatter(def, true);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).meshName, b.get(i).meshName);
            assertTrue(java.util.Arrays.equals(a.get(i).matrix, b.get(i).matrix));
            assertEquals(a.get(i).cellX, b.get(i).cellX);
            assertEquals(a.get(i).cellZ, b.get(i).cellZ);
        }
        List<StaticBatcher.Placement> other = new ArrayList<>();
        TrackSpline ts = TrackSpline.build(def, false, 5.5f);
        PropScatter.scatter(ts, T, 12345L, true, other, Terrain.create(ts, T, 12345L, false), new ArrayList<PropScatter.Instance>());
        assertTrue(other.size() != a.size() || !java.util.Arrays.equals(other.get(0).matrix, a.get(0).matrix));
    }

    @Test
    public void tierBIsAbsentWhenNotIncluded() {
        for (TrackDef def : TrackDefs.ALL) {
            List<StaticBatcher.Placement> low = scatter(def, false), high = scatter(def, true);
            assertTrue(low.size() < high.size());
            for (StaticBatcher.Placement p : low) {
                for (Theme.PropRule r : T.props) if (r.kind == kindOf(p)) assertTrue("tier A only", !r.tierB);
            }
        }
    }

    @Test
    public void cellsMatchThePositionAndTheLapTotalsAreInBudget() {
        for (TrackDef def : TrackDefs.ALL) {
            List<StaticBatcher.Placement> all = scatter(def, true);
            int high = 0, low = 0;
            for (StaticBatcher.Placement p : all) {
                assertEquals((int) Math.floor(p.matrix[12] / 10f), p.cellX);
                assertEquals((int) Math.floor(p.matrix[14] / 10f), p.cellZ);
                PaletteSwatches pal = new PaletteSwatches();
                int kind = kindOf(p), variant = Integer.parseInt(p.meshName.split("_")[2]);
                int rule = -1;
                for (int r = 0; r < T.props.length; r++) if (T.props[r].kind == kind) rule = r;
                int tris = PropMeshes.build(kind, variant, pal, T.propSwatches(rule)).triangleCount();
                high += tris;
                if (!T.props[rule].tierB) low += tris;
            }
            assertTrue(def.id + " HIGH triangles " + high, high <= 40000);
            assertTrue(def.id + " LOW triangles " + low, low <= 16000);
        }
    }

    @Test
    public void chunksStayUnderTheVertexLimitWithTheProps() {
        for (TrackDef def : TrackDefs.ALL) {
            final PaletteSwatches pal = new PaletteSwatches();
            T.fillPalette(pal);
            TrackSpline t = TrackSpline.build(def, false, RaceLevel.fromNumber(3));
            final java.util.Map<String, MeshData> lib = new java.util.HashMap<>();
            List<StaticBatcher.Placement> all = new ArrayList<>();
            float[] ident = new float[16];
            com.wiggleplay.app.gl.Mat4.identity(ident);
            List<MeshData> pieces = TrackMeshBuilder.build(t, T.road, pal, T.terrainSwatch(1));
            for (int i = 0; i < pieces.size(); i++) {
                lib.put("trk_road_" + i, pieces.get(i));
                MeshData.Part part = pieces.get(i).parts[0];
                float x = 0f, z = 0f;
                for (int v = 0; v < part.vertexCount; v++) {
                    x += part.vertices[v * 8] / part.vertexCount;
                    z += part.vertices[v * 8 + 2] / part.vertexCount;
                }
                all.add(new StaticBatcher.Placement("trk_road_" + i, ident, (int) Math.floor(x / 10f), (int) Math.floor(z / 10f)));
            }
            for (int r = 0; r < T.props.length; r++) {
                for (int v = 0; v < PropMeshes.variants(T.props[r].kind); v++) {
                    lib.put(PropMeshes.name(T.props[r].kind, v), PropMeshes.build(T.props[r].kind, v, pal, T.propSwatches(r)));
                }
            }
            Terrain terrain = Terrain.create(t, T, def.id.hashCode(), false);
            for (Terrain.Tile tile : terrain.build(pal, T.terrainSwatch(0), T.terrainSwatch(1))) {
                String name = "terrain_" + all.size();
                lib.put(name, tile.mesh);
                all.add(new StaticBatcher.Placement(name, ident, tile.cellX, tile.cellZ));
            }
            List<PropScatter.Instance> inst = new ArrayList<>();
            PropScatter.scatter(t, T, def.id.hashCode(), true, all, terrain, inst);
            java.util.LinkedHashMap<String, MeshData> shadowLib = new java.util.LinkedHashMap<>();
            PropShadows.build(terrain, pal, T.terrainSwatch(0), inst, shadowLib, all);
            lib.putAll(shadowLib);
            List<StaticBatcher.Chunk> chunks = StaticBatcher.build(all, new StaticBatcher.MeshLibrary() {
                @Override
                public MeshData get(String name) {
                    return lib.get(name);
                }
            }, 6);
            int tris = 0;
            for (StaticBatcher.Chunk c : chunks) {
                assertTrue(def.id + " chunk vertices " + c.vertexCount, c.vertexCount < 65535);
                tris += c.indexCount / 3;
            }
            assertTrue(def.id + " whole static world " + tris + " triangles (limit 140,000)", tris <= 140000);
        }
    }
}
