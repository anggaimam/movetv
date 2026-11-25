package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import org.junit.Test;

/** T-31 Part B: potholes, ice and water patches, their meshes and signs, the camera shake. */
public class HazardTest {
    private static final float DT = 1f / 60f;

    /** A straight road along +z (x = -offset) with a given object list. */
    private static final class Straight implements TrackPath {
        final RoadObjects ro;
        final float hw;

        Straight(RaceLevel level, RoadObject... objs) {
            hw = level.halfWidth;
            TrackDef d = new TrackDef("t", "t", "desert", new float[] {0, 0, 1, 0, 1, 1}, new float[0], objs, new RoadObject[0]);
            ro = RoadObjects.of(d, false, level, hw, RoadObjects.ALL);
        }

        @Override public float lapLength() { return 10000f; }
        @Override public float roadHalfWidth() { return hw; }
        @Override public void sample(float s, float[] out) { out[0] = 0f; out[1] = s; out[2] = 0f; }
        @Override public int project(float x, float z, int hint, TrackPoint out) {
            out.s = z;
            out.offset = -x;
            out.tangentYaw = 0f;
            return 0;
        }
        @Override public Slots coinSlots(long seed, int nitroCans, int magnets) { throw new UnsupportedOperationException(); }
        @Override public RoadObjects roadObjects() { return ro; }
    }

    private static RoadObject pit(float s, int lane) { return new RoadObject(RoadObject.POTHOLE, s, lane, RaceLevel.EASY); }
    private static RoadObject ice(float s, int lane) { return new RoadObject(RoadObject.ICE, s, lane, RaceLevel.EASY); }

    /** Steering that keeps the truck on a lateral line (the road's own assist would pull it to the centre). */
    private static float hold(CarModel c, float target) {
        return Math.max(-1f, Math.min(1f, 0.8f * (target - c.offset()) + 2f * c.yaw()));
    }

    // ---- potholes ----

    private static int bumps(RaceLevel lv, Straight t, float lateral, boolean brake) {
        CarModel c = new CarModel(lv, false);
        c.reset(t, 0f, lateral);
        int n = 0;
        for (int i = 0; i < 60 * 12 && c.s() < 90f; i++) {
            c.update(DT, 0f, false, brake, t);
            if (c.consumeBump()) n++;
        }
        return n;
    }

    @Test
    public void aSinglePotholeIsHitOnceAndTheSpeedRecovers() {
        for (RaceLevel lv : RaceLevel.values()) {
            Straight t = new Straight(lv, pit(60f, 0));
            assertEquals(1, t.ro.potholes(0));
            CarModel c = new CarModel(lv, false);
            c.reset(t, 0f, 0f);
            for (int i = 0; i < 60 * 20 && c.s() < 50f; i++) c.update(DT, 0f, false, false, t);
            float before = c.speed();
            assertEquals(lv.baseSpeed, before, 0.05f);
            int hits = 0;
            float hitAt = -1f;
            float afterBump = 0f;
            int frames = 0;
            boolean recovered = false;
            for (int i = 0; i < 60 * 6 && c.s() < 95f; i++) {
                c.update(DT, 0f, false, false, t);
                if (c.consumeBump()) {
                    hits++;
                    hitAt = c.s();
                    afterBump = c.speed();
                    frames = 0;
                } else if (hits > 0) {
                    frames++;
                    if (!recovered && c.speed() >= 0.95f * lv.baseSpeed) {
                        recovered = true;
                        assertTrue(lv + " recovered after " + frames * DT + " s", frames * DT <= 1.0f);
                    }
                }
            }
            assertEquals(lv + " one bump", 1, hits);
            assertEquals(lv + " speed x 0.8", 0.8f * before, afterBump, 0.4f);   // the frame's easing already moves it a little
            assertTrue(lv + " hit at " + hitAt, Math.abs(hitAt - 60f) < 1.2f);
            assertTrue(lv + " recovered", recovered);
        }
    }

    @Test
    public void aGroupOfTwoGivesTwoBumpsAndNeverStopsTheTruck() {
        for (RaceLevel lv : RaceLevel.values()) {
            Straight t = new Straight(lv, pit(61f, 0));
            assertEquals(2, t.ro.potholes(0));
            assertEquals(lv + " two bumps", 2, bumps(lv, t, 0f, false));
            // braking at 0.4 x base: 0.8 x that is under the 0.35 x base floor, which holds
            CarModel c = new CarModel(lv, false);
            c.reset(t, 0f, 0f);
            float min = 1e9f;
            for (int i = 0; i < 60 * 12 && c.s() < 90f; i++) {
                c.update(DT, 0f, false, true, t);
                if (c.s() > 40f) min = Math.min(min, c.speed());
            }
            assertTrue(lv + " floor " + min, min >= 0.35f * lv.baseSpeed - 1e-3f);
        }
    }

    @Test
    public void aTruckBesideThePotholeIsNotHit() {
        RaceLevel lv = RaceLevel.HARD;
        // the truck drives the centre line; the pothole is in the centre, or a lane away on either side
        assertEquals(1, bumps(lv, new Straight(lv, pit(60f, 0)), 0f, false));
        assertEquals(0, bumps(lv, new Straight(lv, pit(60f, 1)), 0f, false));
        assertEquals(0, bumps(lv, new Straight(lv, pit(60f, -1)), 0f, false));
    }

    @Test
    public void thePitchKickShowsAtTheBumpAndIsGoneAfterwards() {
        RaceLevel lv = RaceLevel.MEDIUM;
        Straight t = new Straight(lv, pit(60f, 0));
        CarModel c = new CarModel(lv, false);
        c.reset(t, 0f, 0f);
        for (int i = 0; i < 60 * 20 && c.s() < 50f; i++) c.update(DT, 0f, false, false, t);
        float rest = c.bodyPitch();
        float peak = 0f;
        int after = 0;
        boolean seen = false;
        for (int i = 0; i < 60 * 6 && c.s() < 120f; i++) {
            c.update(DT, 0f, false, false, t);
            if (c.consumeBump()) seen = true;
            if (seen) {
                after++;
                peak = Math.max(peak, Math.abs(c.bodyPitch() - rest));
                if (after * DT > 2.0f) assertEquals(rest, c.bodyPitch(), 0.02f);
            }
        }
        assertTrue("the kick shows " + peak, peak > 0.03f);
    }

    // ---- patches ----

    @Test
    public void withTheSteeringStraightTheTruckCrossesThePatchInsideTheRoad() {
        RaceLevel lv = RaceLevel.HARD;
        Straight t = new Straight(lv, ice(60f, 0));
        CarModel c = new CarModel(lv, false);
        c.reset(t, 0f, 0f);
        boolean was = false;
        for (int i = 0; i < 60 * 12 && c.s() < 90f; i++) {
            c.update(DT, 0f, false, false, t);
            if (c.slick()) was = true;
            assertTrue(Math.abs(c.offset()) <= CarModel.wallLimit(lv.halfWidth) + 1e-3f);
            assertEquals("no slide without steering", c.yaw(), c.travelYaw(), 1e-3f);
        }
        assertTrue(was);
        assertEquals(0f, c.offset(), 0.2f);
        assertFalse(c.slick());
    }

    @Test
    public void withSteeringHeldTheSlipStaysUnderTwentyDegreesAndTheTruckKeepsMoving() {
        for (RaceLevel lv : RaceLevel.values()) {
            for (float steer : new float[] {1f, -1f}) {
                Straight t = new Straight(lv, ice(60f, 1));
                float lat = t.ro.lateral(0);
                CarModel c = new CarModel(lv, false);
                c.reset(t, 0f, lat);
                float widest = 0f, minSpeed = 1e9f, exitT = -1f;
                boolean was = false;
                for (int i = 0; i < 60 * 14 && c.s() < 110f; i++) {
                    // keep the lane up to the patch, then hold the steering hard to one side
                    float st = c.s() < 58f ? hold(c, lat) : steer;
                    c.update(DT, st, false, false, t);
                    float gap = Math.abs(CarModelAngle.diff(c.travelYaw(), c.yaw()));
                    widest = Math.max(widest, gap);
                    assertTrue("wall: " + c.offset(), Math.abs(c.offset()) <= CarModel.wallLimit(lv.halfWidth) + 1e-3f);
                    if (c.s() > 30f) minSpeed = Math.min(minSpeed, c.speed());
                    if (c.slick()) was = true;
                    if (was && !c.slick() && exitT < 0f) exitT = 0f;
                    if (exitT >= 0f) exitT += DT;
                    if (exitT > 0.05f) assertEquals(c.yaw(), c.travelYaw(), 1e-4f);   // off the patch the heading is the direction again
                }
                assertTrue(lv + " steer " + steer + ": slip " + Math.toDegrees(widest) + " deg", widest <= Math.toRadians(20.0) + 1e-3f);
                assertTrue(lv + " steer " + steer + ": slip " + Math.toDegrees(widest) + " deg is a slide", widest > Math.toRadians(2.0));
                assertTrue(lv + " min speed " + minSpeed, minSpeed >= 0.35f * lv.baseSpeed - 1e-3f);
                assertTrue(was);
            }
        }
    }

    @Test
    public void slickLastsWhileOnThePatchAndFourTenthsAfterAndHalvesTheSteering() {
        RaceLevel lv = RaceLevel.HARD;
        Straight t = new Straight(lv, ice(60f, 0));
        CarModel c = new CarModel(lv, false);
        c.reset(t, 0f, 0f);
        float onAt = -1f, offAt = -1f;
        float time = 0f;
        for (int i = 0; i < 60 * 12 && c.s() < 100f; i++) {
            c.update(DT, 0f, false, false, t);
            time += DT;
            if (c.slick() && onAt < 0f) onAt = time;
            if (!c.slick() && onAt >= 0f && offAt < 0f) offAt = time;
        }
        // 8 m at 15 u/s = 0.53 s on the patch, plus 0.4 s
        assertEquals(0.53f + 0.4f, offAt - onAt, 0.08f);
        CarModel a = new CarModel(lv, false), b = new CarModel(lv, false);
        Straight plain = new Straight(lv);
        a.reset(t, 0f, 0f);
        b.reset(plain, 0f, 0f);
        for (int i = 0; i < 60 * 12 && a.s() < 60.5f; i++) {
            a.update(DT, 0f, false, false, t);
            b.update(DT, 0f, false, false, plain);
        }
        assertTrue(a.slick());
        float ay = a.yaw(), by = b.yaw();
        a.update(DT, 1f, false, false, t);
        b.update(DT, 1f, false, false, plain);
        assertEquals(0.5f, (a.yaw() - ay) / (b.yaw() - by), 0.05f);
    }

    // ---- meshes and signs ----

    private static int block(MeshData.Part p, int vertex) {
        float u = p.vertices[vertex * 8 + 6] * 512f, v = p.vertices[vertex * 8 + 7] * 512f;
        return (int) Math.floor(u / 16f) + 32 * ((int) Math.floor(v / 16f) - 1);
    }

    private static void oneSwatchEach(MeshData m, int lo1, int hi1) {
        MeshData.Part p = m.parts[0];
        for (int k = 0; k < p.indices.length; k += 3) {
            int b0 = block(p, p.indices[k] & 0xFFFF);
            assertEquals(b0, block(p, p.indices[k + 1] & 0xFFFF));
            assertEquals(b0, block(p, p.indices[k + 2] & 0xFFFF));
            assertTrue("swatch " + b0, (b0 >= lo1 && b0 <= hi1) || (b0 >= ObjectMeshes.SW_FIRST_HAZARD && b0 <= ObjectMeshes.SW_BLUE));
        }
    }

    @Test
    public void hazardMeshesStayUnderTheirLimitsAndUseTheHazardSwatches() {
        PaletteSwatches pal = new PaletteSwatches();
        Themes.DESERT.fillPalette(pal);
        for (TrackDef def : TrackDefs.ALL) {
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = TrackSpline.build(def, rev, RaceLevel.HARD);
                RoadObjects ro = t.roadObjects();
                for (int i = 0; i < ro.count(); i++) {
                    for (int slick : new int[] {Theme.SLICK_ICE, Theme.SLICK_WATER}) {
                        MeshData sign = ObjectMeshes.sign(ro, i, t, pal, slick);
                        assertTrue(sign.triangleCount() <= ObjectMeshes.MAX_SIGN);
                        oneSwatchEach(sign, 10, 15);
                        if (ro.kind(i) == RoadObjects.POTHOLE) {
                            MeshData m = ObjectMeshes.pothole(ro, i, t, pal);
                            assertTrue(m.triangleCount() <= ObjectMeshes.MAX_POTHOLE * ro.potholes(i));
                            oneSwatchEach(m, 99, 98);
                        } else if (ro.kind(i) == RoadObjects.ICE) {
                            MeshData m = ObjectMeshes.patch(ro, i, t, pal, slick);
                            assertTrue(m.triangleCount() + " triangles", m.triangleCount() <= ObjectMeshes.MAX_PATCH && m.triangleCount() > 0);
                            oneSwatchEach(m, 99, 98);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void patchAndPotholeLieFlatOnTheRoadAtSixThousandthsOfAMetre() {
        PaletteSwatches pal = new PaletteSwatches();
        Themes.DESERT.fillPalette(pal);
        TrackSpline t = TrackSpline.build(TrackDefs.byId("rockies"), false, RaceLevel.HARD);
        RoadObjects ro = t.roadObjects();
        for (int i = 0; i < ro.count(); i++) {
            if (ro.kind(i) == RoadObjects.RAMP) continue;
            MeshData.Part p = (ro.kind(i) == RoadObjects.POTHOLE ? ObjectMeshes.pothole(ro, i, t, pal) : ObjectMeshes.patch(ro, i, t, pal, Theme.SLICK_ICE)).parts[0];
            for (int v = 0; v < p.vertexCount; v++) {
                float y = p.vertices[v * 8 + 1];
                assertTrue("y " + y, y >= 0.0059f && y <= 0.0081f);
            }
        }
    }

    @Test
    public void everyThemeLeavesRoomForTheHazardSwatches() {
        for (Theme th : new Theme[] {Themes.MEADOW, Themes.DESERT, Themes.CITY, Themes.ROCKIES, Themes.SPACE}) {
            PaletteSwatches pal = new PaletteSwatches();
            int count = th.fillPalette(pal);
            assertTrue(th.id + " uses " + count, count <= ObjectMeshes.SW_FIRST_HAZARD);
            assertEquals(ObjectMeshes.ICE, pal.rgb(ObjectMeshes.SW_ICE) & 0xFFFFFF);
            assertEquals(ObjectMeshes.PIT_FLOOR, pal.rgb(ObjectMeshes.SW_PIT_FLOOR) & 0xFFFFFF);
        }
    }

    // ---- the camera shake ----

    @Test
    public void theCameraShakesVerticallyForAQuarterSecondAndSettlesExactly() {
        ChaseCamera cam = new ChaseCamera();
        cam.snap(0f, 0f, 0f);
        for (int i = 0; i < 300; i++) cam.update(DT, 0f, 0f, 0f, false, 0f);
        float rest = cam.eye()[1];
        cam.onBump();
        float peak = 0f;
        for (int i = 0; i < 90; i++) {
            cam.update(DT, 0f, 0f, 0f, false, 0f);
            peak = Math.max(peak, Math.abs(cam.eye()[1] - rest));
        }
        assertTrue("peak " + peak, peak > 0.02f && peak <= ChaseCamera.SHAKE_AMP + 0.01f);
        assertEquals(rest, cam.eye()[1], 1e-3f);   // 1.5 s later the height is back exactly
    }

    /** Small angle helper (CarModel's own is private). */
    private static final class CarModelAngle {
        static float diff(float to, float from) {
            float d = to - from;
            while (d > Math.PI) d -= 2 * Math.PI;
            while (d <= -Math.PI) d += 2 * Math.PI;
            return d;
        }
    }
}
