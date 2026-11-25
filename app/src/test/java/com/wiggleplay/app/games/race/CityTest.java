package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import com.wiggleplay.app.gl.StaticBatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** City Lights (T-30a): the theme, the street props, the sidewalk, the parks, the skyline and the budgets. */
public class CityTest {
    private static final Theme C = Themes.CITY;

    private static int ruleOf(int kind) {
        for (int r = 0; r < C.props.length; r++) if (C.props[r].kind == kind) return r;
        throw new IllegalStateException("kind " + kind);
    }

    private static TrackDef city() {
        return TrackDefs.byId("city");
    }

    private static TrackSpline forward(TrackDef def) {
        return TrackSpline.build(def, false, RaceLevel.HARD);
    }

    private static PaletteSwatches palette() {
        PaletteSwatches p = new PaletteSwatches();
        C.fillPalette(p);
        return p;
    }

    private static int block(MeshData.Part p, int vertex) {
        float u = p.vertices[vertex * 8 + 6] * 512f, v = p.vertices[vertex * 8 + 7] * 512f;
        return (int) Math.floor(u / 16f) + 32 * ((int) Math.floor(v / 16f) - 1);
    }

    @Test
    public void themeIsRegisteredAndFitsThePalette() {
        assertTrue(Themes.byId("city") == C);
        assertTrue(C.hasSky());
        assertEquals(PropMeshes.RAMPS.length, PropMeshes.COUNT);
        for (Theme.PropRule r : C.props) assertEquals("kind " + r.kind, PropMeshes.RAMPS[r.kind], r.ramps());
        assertTrue(C.fillPalette(new PaletteSwatches()) < PaletteSwatches.MAX);
        assertEquals(2, C.rings.length);
        assertEquals(Theme.SKYLINE, C.rings[0].profile);
        assertTrue(C.rings[0].low && C.rings[1].low);
    }

    @Test
    public void everyBuildingVariantIsUnderItsLimitAndItsWindowsAreInsideItsWalls() {
        PaletteSwatches pal = palette();
        int[] sw = C.propSwatches(ruleOf(PropMeshes.BUILDING));
        for (int v = 0; v < PropMeshes.variants(PropMeshes.BUILDING); v++) {
            MeshData m = PropMeshes.build(PropMeshes.BUILDING, v, pal, sw);
            assertTrue("variant " + v + " has " + m.triangleCount(), m.triangleCount() <= 1000);
            MeshData.Part p = m.parts[0];
            float h = CityProps.buildingHeight(v);
            for (int i = 0; i < p.vertexCount; i++) {
                int b = block(p, i);
                if (b != sw[3] && b != sw[4]) continue;
                float x = p.vertices[i * 8], y = p.vertices[i * 8 + 1], z = p.vertices[i * 8 + 2];
                assertTrue("window x " + x, Math.abs(x) <= 4.6f);
                assertTrue("window z " + z, Math.abs(z) <= CityProps.B_D / 2f + 0.1f);
                assertTrue("window y " + y, y >= 0f && y <= h);
            }
        }
    }

    @Test
    public void towersAndStreetFurnitureStayUnderTheirLimits() {
        PaletteSwatches pal = palette();
        for (int kind = PropMeshes.BUILDING; kind <= PropMeshes.FLOWER_PLANTER; kind++) {
            for (int v = 0; v < PropMeshes.variants(kind); v++) {
                MeshData m = PropMeshes.build(kind, v, pal, C.propSwatches(ruleOf(kind)));
                assertTrue("kind " + kind + " v" + v + " has " + m.triangleCount(), m.triangleCount() <= PropMeshes.MAX_TRIANGLES[kind]);
            }
        }
        assertTrue(PropMeshes.height(PropMeshes.TOWER) >= 24f && PropMeshes.height(PropMeshes.TOWER) <= 50f);
    }

    @Test
    public void lampsStandOutsideTheBarrierOnTheSidewalkFacingTheRoad() {
        for (TrackDef def : TrackDefs.ALL) {
            TrackSpline t = forward(def);
            List<PropScatter.Instance> inst = new ArrayList<>();
            Terrain te = Terrain.create(t, C, def.id.hashCode(), false);
            PropScatter.scatter(t, C, def.id.hashCode(), true, new ArrayList<StaticBatcher.Placement>(), te, inst);
            int lamps = 0;
            for (PropScatter.Instance in : inst) {
                if (in.kind != PropMeshes.LAMP) continue;
                lamps++;
                float d = PropScatter.nearestRoad(t, in.x, in.z);
                assertTrue(def.id + " lamp at " + d, d >= PropScatter.BARRIER_EDGE + PropScatter.BARRIER_GAP && d <= 8.1f);
                assertEquals("lamp stands on the sidewalk", Terrain.SIDEWALK_Y, te.surface(in.x, in.z), 1e-4f);
                assertTrue(PropMeshes.reach(PropMeshes.LAMP) <= 1.1f);
            }
            assertTrue(def.id + " lamps " + lamps, lamps >= 10);
        }
    }

    @Test
    public void facingPropsLookAtTheRoadAndParkedCarsLieAlongIt() {
        TrackDef def = city();
        TrackSpline t = forward(def);
        List<PropScatter.Instance> inst = new ArrayList<>();
        PropScatter.scatter(t, C, def.id.hashCode(), true, new ArrayList<StaticBatcher.Placement>(), null, inst);
        int cars = 0, buildings = 0;
        for (PropScatter.Instance in : inst) {
            if (in.kind != PropMeshes.PARKED_CAR && in.kind != PropMeshes.BUILDING && in.kind != PropMeshes.LAMP) continue;
            int near = 0;
            float best = Float.MAX_VALUE;
            for (int i = 0; i < t.sampleCount(); i++) {
                float dx = t.sampleX(i) - in.x, dz = t.sampleZ(i) - in.z;
                if (dx * dx + dz * dz < best) {
                    best = dx * dx + dz * dz;
                    near = i;
                }
            }
            float yaw = t.sampleYaw(near);
            if (in.kind == PropMeshes.PARKED_CAR) {
                cars++;
                float diff = Math.abs((float) Math.sin(in.yaw - yaw));
                assertTrue("car angle " + Math.toDegrees(Math.asin(diff)), diff < Math.sin(Math.toRadians(5.5)));
                assertTrue("car clear of the wall", Math.sqrt(best) - in.footprint >= PropScatter.BARRIER_EDGE + PropScatter.BARRIER_GAP - 1e-3f);
            } else {
                // the local +z (sin yaw, cos yaw) points to the road: its dot with the vector to the road is positive
                float fx = (float) Math.sin(in.yaw), fz = (float) Math.cos(in.yaw);
                float tx = t.sampleX(near) - in.x, tz = t.sampleZ(near) - in.z;
                float len = (float) Math.sqrt(tx * tx + tz * tz);
                assertTrue("facing " + (fx * tx + fz * tz) / len, (fx * tx + fz * tz) / len > 0.9f);
                if (in.kind == PropMeshes.BUILDING) buildings++;
            }
        }
        assertTrue("cars " + cars, cars >= 3);
        assertTrue("buildings " + buildings, buildings >= 8);
    }

    @Test
    public void buildingsTallerThan18MetresStandAtLeast18MetresFromTheRoad() {
        for (TrackDef def : TrackDefs.ALL) {
            TrackSpline t = forward(def);
            List<PropScatter.Instance> inst = new ArrayList<>();
            PropScatter.scatter(t, C, def.id.hashCode(), true, new ArrayList<StaticBatcher.Placement>(), null, inst);
            for (PropScatter.Instance in : inst) {
                if (in.kind != PropMeshes.BUILDING && in.kind != PropMeshes.TOWER) continue;
                float h = PropMeshes.height(in.kind, in.variant) * in.scale;
                if (h > 18f) assertTrue(def.id + " tall " + h, PropScatter.nearestRoad(t, in.x, in.z) >= 18f - 0.01f);
            }
        }
    }

    @Test
    public void sidewalkBandIsFlatAtItsHeightOnAllEightTracks() {
        for (TrackDef def : TrackDefs.ALL) {
            for (boolean reversed : new boolean[] {false, true}) {
                TrackSpline t = TrackSpline.build(def, reversed, RaceLevel.HARD);
                // the ground is made from the forward track, whichever way the car drives
                Terrain te = Terrain.create(forward(def), C, def.id.hashCode(), false);
                int checked = 0;
                for (int i = 0; i < t.sampleCount(); i += 5) {
                    float yaw = t.sampleYaw(i), lx = (float) Math.cos(yaw), lz = -(float) Math.sin(yaw);
                    for (float off = 6.5f; off <= 8.9f; off += 0.6f) {
                        for (int s = -1; s <= 1; s += 2) {
                            float x = t.sampleX(i) + s * off * lx, z = t.sampleZ(i) + s * off * lz;
                            float d = te.distance(x, z);
                            if (d < Terrain.SIDEWALK_FROM || d > Terrain.SIDEWALK_TO) continue;
                            assertEquals(def.id + " sidewalk", 0.15f, te.surface(x, z), 1e-4f);
                            checked++;
                        }
                    }
                }
                assertTrue(def.id + " checked " + checked, checked > 100);
            }
        }
    }

    @Test
    public void sidewalkMeshHasOnlyTwoHeightsAndStaysInItsBand() {
        for (TrackDef def : TrackDefs.ALL) {
            Terrain te = Terrain.create(forward(def), C, def.id.hashCode(), false);
            List<Terrain.Tile> tiles = te.sidewalk(palette(), C.terrainSwatch(1));
            assertFalse(tiles.isEmpty());
            int tris = 0;
            for (Terrain.Tile tile : tiles) {
                MeshData.Part p = tile.mesh.parts[0];
                assertTrue(p.vertexCount < 65535);
                tris += tile.triangles;
                for (int i = 0; i < p.vertexCount; i++) {
                    float y = p.vertices[i * 8 + 1];
                    assertTrue(def.id + " y " + y, Math.abs(y - 0.15f) < 1e-4f || Math.abs(y - Terrain.ROAD_Y) < 1e-4f);
                    float d = te.distance(p.vertices[i * 8], p.vertices[i * 8 + 2]);
                    assertTrue(def.id + " d " + d, d >= Terrain.SIDEWALK_FROM - 0.4f && d <= Terrain.SIDEWALK_TO + 0.1f);
                    assertEquals(block(p, i), C.terrainSwatch(1));
                }
            }
            assertTrue(def.id + " sidewalk triangles " + tris, tris < 9000);
        }
    }

    @Test
    public void parkCellsUseOneRampPerTriangleAndExist() {
        TrackDef def = city();
        Terrain te = Terrain.create(forward(def), C, def.id.hashCode(), false);
        List<Terrain.Tile> tiles = te.build(palette(), C.terrainSwatch(0), C.terrainSwatch(1), C.terrainSwatch(2));
        int park = 0, ground = 0;
        for (Terrain.Tile tile : tiles) {
            MeshData.Part p = tile.mesh.parts[0];
            for (int i = 0; i < p.indices.length; i += 3) {
                int b = block(p, p.indices[i] & 0xFFFF);
                assertEquals(b, block(p, p.indices[i + 1] & 0xFFFF));
                assertEquals(b, block(p, p.indices[i + 2] & 0xFFFF));
                if (b == C.terrainSwatch(2)) park++;
                if (b == C.terrainSwatch(0)) ground++;
            }
        }
        assertTrue("park " + park + " ground " + ground, park > 50 && ground > park);
    }

    @Test
    public void cityBlocksAreFlatNearTheRoad() {
        TrackDef def = city();
        Terrain te = Terrain.create(forward(def), C, def.id.hashCode(), false);
        for (int i = 0; i < 300; i++) {
            float x = (i * 37 % 300 - 150) * 1.1f, z = (i * 91 % 300 - 150) * 1.1f;
            float d = te.distance(x, z);
            float h = te.height(x, z);
            if (d <= 30f) assertEquals(Terrain.ROAD_Y, h, 1e-3f);
            assertTrue(h <= 0.75f);
        }
    }

    @Test
    public void skylineHasBlocksUnevenTopsAndSpiresAndNoColumnBelowMinH() {
        for (int ri = 0; ri < 2; ri++) {
            Theme.Ring r = C.rings[ri];
            Backdrop.Profile p = Backdrop.profile(r);
            int n = r.segments, runs = 0, widest = 0, run = 1, distinctTops = 0, tallest = 0;
            java.util.Set<Float> tops = new java.util.HashSet<>();
            for (int c = 0; c < n; c++) {
                assertTrue(p.left[c] >= r.minH - 1e-4f && p.left[c] <= r.maxH + 1e-4f);
                tops.add(p.left[c]);
                if (c > 0 && p.left[c] == p.left[c - 1]) run++;
                else {
                    widest = Math.max(widest, run);
                    run = 1;
                    runs++;
                }
            }
            assertTrue("ring " + ri + " distinct heights " + tops.size(), tops.size() >= 8);
            assertTrue("ring " + ri + " runs " + runs, runs >= 40);
            assertTrue("ring " + ri + " widest run " + widest, widest <= 12);
            int streaks = 0;
            for (int c = 0; c < n; c++) if (Backdrop.skylineStreak(r, c)) streaks++;
            assertTrue("ring " + ri + " streak columns " + streaks, streaks > 5 && streaks < n / 2);
        }
        // the far ring has fewer streaks than the mid ring
        int mid = 0, far = 0;
        for (int c = 0; c < 192; c++) {
            if (Backdrop.skylineStreak(C.rings[0], c)) mid++;
            if (Backdrop.skylineStreak(C.rings[1], c)) far++;
        }
        assertTrue("mid " + mid + " far " + far, far < mid);
    }

    @Test
    public void dashesAndRailsUseTheCityRamps() {
        PaletteSwatches p = palette();
        assertTrue(C.road.centreDash != 0);
        assertEquals(0xC9A44A, C.road.dashDark);
        float u = p.uRamp(RoadStyle.RAMP_DASH, 0.5f);
        assertTrue(u > 0f);
    }

    /** Static triangles of a lap, counted like the scene builds them: road, terrain, sidewalk, props and shadows. */
    private static int staticTriangles(TrackDef def, boolean low) {
        TrackSpline t = forward(def);
        PaletteSwatches pal = palette();
        int tris = 0;
        for (MeshData m : TrackMeshBuilder.build(t, C.road, pal, C.terrainSwatch(1))) tris += m.triangleCount();
        Terrain te = Terrain.create(t, C, def.id.hashCode(), low);
        for (Terrain.Tile tile : te.build(pal, C.terrainSwatch(0), C.terrainSwatch(1), C.terrainSwatch(2))) tris += tile.triangles;
        for (Terrain.Tile tile : te.sidewalk(pal, C.terrainSwatch(1))) tris += tile.triangles;
        Map<String, MeshData> lib = new HashMap<>();
        for (int r = 0; r < C.props.length; r++) {
            for (int v = 0; v < PropMeshes.variants(C.props[r].kind); v++) {
                lib.put(PropMeshes.name(C.props[r].kind, v), PropMeshes.build(C.props[r].kind, v, pal, C.propSwatches(r)));
            }
        }
        List<StaticBatcher.Placement> placements = new ArrayList<>();
        List<PropScatter.Instance> inst = new ArrayList<>();
        PropScatter.scatter(t, C, def.id.hashCode(), !low, placements, te, inst);
        for (PropScatter.Instance in : inst) tris += lib.get(PropMeshes.name(in.kind, in.variant)).triangleCount();
        tris += PropShadows.build(te, pal, C.terrainSwatch(0), inst, new HashMap<String, MeshData>(), new ArrayList<StaticBatcher.Placement>());
        return tris;
    }

    @Test
    public void staticWorldStaysUnderItsBudgetOnEveryTrack() {
        for (TrackDef def : TrackDefs.ALL) {
            int high = staticTriangles(def, false), low = staticTriangles(def, true);
            System.out.println("City on " + def.id + ": static high " + high + ", low " + low);
            assertTrue(def.id + " high " + high, high <= 140000);
            assertTrue(def.id + " low " + low, low <= 80000);
        }
    }

    @Test
    public void sameTrackGivesTheSameLayout() {
        TrackDef def = city();
        TrackSpline t = forward(def);
        List<PropScatter.Instance> a = new ArrayList<>(), b = new ArrayList<>();
        PropScatter.scatter(t, C, 7L, true, new ArrayList<StaticBatcher.Placement>(), null, a);
        PropScatter.scatter(t, C, 7L, true, new ArrayList<StaticBatcher.Placement>(), null, b);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x, b.get(i).x, 0f);
            assertEquals(a.get(i).yaw, b.get(i).yaw, 0f);
        }
    }
}
