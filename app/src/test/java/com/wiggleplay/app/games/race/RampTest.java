package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import org.junit.Test;

/** T-31 Part A: the road-object data and its placement rules, the ramp and the air physics, the ramp coins, the camera and the meshes. */
public class RampTest {
    private static final float DT = 1f / 60f;
    private static final float LIP = 35f;

    // ---- a straight road with one ramp, for the physics ----

    private static class Straight implements TrackPath {
        final RoadObjects ro;
        final float hw;

        Straight(RaceLevel level) {
            hw = level.halfWidth;
            TrackDef d = new TrackDef("t", "t", "desert", new float[] {0, 0, 1, 0, 1, 1},
                    new float[0], new RoadObject[] {new RoadObject(RoadObject.RAMP, LIP, 0, RaceLevel.EASY)}, new RoadObject[0]);
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

    /** Result of driving straight over the ramp. */
    private static final class Flight {
        float speedAtLip, vy0, seconds, peak, minPitch = 9f, maxPitch = -9f, speedBefore, speedAfter;
        int launches, landings;
        int frames;
    }

    private static Flight fly(RaceLevel level, boolean boost) {
        Straight t = new Straight(level);
        CarModel c = new CarModel(level, false);
        c.reset(t, 0f, 0f);
        Flight f = new Flight();
        boolean was = false;
        float last = 0f;
        for (int i = 0; i < 60 * 8; i++) {
            last = c.speed();
            c.update(DT, 0f, boost, false, t);
            if (c.consumeLaunch()) {
                f.launches++;
                f.speedAtLip = c.speed();
                f.vy0 = c.verticalSpeed();
            }
            if (c.airborne()) {
                f.frames++;
                f.peak = Math.max(f.peak, c.y());
                f.minPitch = Math.min(f.minPitch, c.airPitch());
                f.maxPitch = Math.max(f.maxPitch, c.airPitch());
                f.speedBefore = c.speed();
            }
            if (c.consumeLanding()) {
                f.landings++;
                f.speedAfter = c.speed();
                break;
            }
            was = c.airborne();
        }
        f.seconds = f.frames * DT;
        return f;
    }

    private static float flightTime(float vy0) {
        return (vy0 + (float) Math.sqrt(vy0 * vy0 + 2f * CarModel.GRAVITY * RoadObjects.RAMP_HEIGHT)) / CarModel.GRAVITY;
    }

    @Test
    public void launchSpeedFlightTimeAndPeakMatchTheReference() {
        float[][] cases = {{0f, 0f}, {1f, 0f}, {2f, 0f}, {2f, 1f}};   // Easy, Medium, Hard, Hard boosting
        for (float[] cs : cases) {
            RaceLevel lv = RaceLevel.values()[(int) cs[0]];
            Flight f = fly(lv, cs[1] > 0f);
            float expectVy = Math.max(4f, Math.min(9f, 0.5f * f.speedAtLip));
            assertEquals(lv + " launched once", 1, f.launches);
            assertEquals(lv + " vy", expectVy, f.vy0, 1e-3f);
            float expect = flightTime(expectVy);
            System.out.println(lv + (cs[1] > 0f ? " boost" : "") + ": speed " + f.speedAtLip + ", flight " + f.seconds + " s (expected " + expect
                    + "), peak " + f.peak + " m");
            assertEquals(lv + " flight time", expect, f.seconds, 2f * DT);
            assertEquals(lv + " peak", RoadObjects.RAMP_HEIGHT + expectVy * expectVy / (2f * CarModel.GRAVITY), f.peak, 0.12f);
            assertEquals(lv + " landed once", 1, f.landings);
        }
        // the reference figures of the ticket (within a frame or two of rounding): Easy 11 u/s about 0.67 s, Hard 15 about 0.84 s
        assertEquals(0.67f, fly(RaceLevel.EASY, false).seconds, 0.06f);
        assertEquals(0.84f, fly(RaceLevel.HARD, false).seconds, 0.06f);
        assertEquals(0.98f, fly(RaceLevel.HARD, true).seconds, 0.06f);
    }

    @Test
    public void pitchStaysBetweenMinusTenAndPlusTwelveDegreesAndTheSpeedIsHeld() {
        for (RaceLevel lv : RaceLevel.values()) {
            for (boolean boost : new boolean[] {false, true}) {
                Flight f = fly(lv, boost);
                assertTrue(lv + " nose " + f.maxPitch, f.maxPitch <= CarModel.PITCH_MAX + 1e-4f);
                assertTrue(lv + " tail " + f.minPitch, f.minPitch >= CarModel.PITCH_MIN - 1e-4f);
                assertTrue("the nose goes up then down", f.maxPitch > 0.05f && f.minPitch < -0.02f);
                // landing keeps the speed (it may rise toward the target, never fall below what it was in the air)
                assertTrue(lv + " lost speed " + f.speedBefore + " -> " + f.speedAfter, f.speedAfter >= f.speedBefore - 1e-3f || boost);
            }
        }
    }

    @Test
    public void onTheRampTheTruckRisesToEightTenthsOfAMetreWithNineDegreesOfPitch() {
        Straight t = new Straight(RaceLevel.EASY);
        CarModel c = new CarModel(RaceLevel.EASY, false);
        c.reset(t, 0f, 0f);
        boolean saw = false;
        for (int i = 0; i < 60 * 6 && !c.airborne(); i++) {
            c.update(DT, 0f, false, false, t);
            if (c.s() > LIP - 2.5f && c.s() < LIP - 2.3f) {
                assertEquals(0.4f, c.y(), 0.09f);
                assertEquals(Math.toRadians(9.0), c.airPitch(), 0.02f);
                saw = true;
            }
        }
        assertTrue(saw);
    }

    @Test
    public void inTheAirSteeringIsFortyPercentOfTheGroundRate() {
        RaceLevel lv = RaceLevel.HARD;
        Straight t = new Straight(lv);
        CarModel air = new CarModel(lv, false);
        air.reset(t, 0f, 0f);
        for (int i = 0; i < 600 && !air.airborne(); i++) air.update(DT, 0f, false, false, t);
        for (int i = 0; i < 6; i++) air.update(DT, 0f, false, false, t);   // a little way into the flight
        assertTrue(air.airborne());
        float y0 = air.yaw();
        air.update(DT, 1f, false, false, t);
        float dAir = air.yaw() - y0;
        // the same on the ground: a road without the ramp, at the same speed
        Straight plain = new Straight(lv) {
            @Override public RoadObjects roadObjects() { return RoadObjects.NONE; }
        };
        CarModel ground = new CarModel(lv, false);
        ground.reset(plain, 0f, 0f);
        for (int i = 0; i < 400; i++) ground.update(DT, 0f, false, false, plain);
        float g0 = ground.yaw();
        ground.update(DT, 1f, false, false, plain);
        float dGround = ground.yaw() - g0;
        System.out.println("steering: air " + dAir + " ground " + dGround + " ratio " + dAir / dGround);
        assertEquals(CarModel.AIR_STEER, dAir / dGround, 0.03f);
    }

    @Test
    public void theWallHoldsInTheAirAndAfterLanding() {
        for (RaceLevel lv : RaceLevel.values()) {
            for (float steer : new float[] {1f, -1f}) {
                Straight t = new Straight(lv);
                CarModel c = new CarModel(lv, false);
                c.reset(t, 0f, 0f);
                float limit = CarModel.wallLimit(lv.halfWidth);
                for (int i = 0; i < 60 * 8; i++) {
                    c.update(DT, c.airborne() ? steer : 0f, false, false, t);
                    assertTrue(lv + " offset " + c.offset(), Math.abs(c.offset()) <= limit + 1e-3f);
                    assertTrue("heading within 75 degrees", Math.abs(c.yaw()) <= Math.toRadians(75.0) + 1e-3f);
                }
                c.consumeLaunch();
            }
        }
    }

    @Test
    public void leavingTheSideOfTheRampDropsFromWhereItIs() {
        RaceLevel lv = RaceLevel.EASY;
        Straight t = new Straight(lv);
        CarModel c = new CarModel(lv, false);
        c.reset(t, 0f, 0f);
        for (int i = 0; i < 600 && !(c.y() > 0.3f); i++) c.update(DT, 0f, false, false, t);
        assertTrue(c.y() > 0.3f && !c.airborne());
        // shove it sideways off the ramp (a nudge, as the two-player push does)
        c.nudge(-RoadObjects.RAMP_HIT - 0.5f, 0f);
        c.update(DT, 0f, false, false, t);
        assertTrue("dropping", c.airborne());
        assertEquals("no launch from the side", 0f, c.verticalSpeed(), 0.5f);
        assertFalse(c.consumeLaunch());
        for (int i = 0; i < 120 && c.airborne(); i++) c.update(DT, 0f, false, false, t);
        assertFalse(c.airborne());
        assertTrue(c.consumeLanding());
    }

    // ---- the data and its placement rules ----

    private static final TrackDef[] DEFS = TrackDefs.ALL;

    private static RoadObjects objects(TrackDef def, boolean rev, RaceLevel lv) {
        return RoadObjects.of(def, rev, lv, lv.halfWidth, RoadObjects.ALL);
    }

    private static int count(RoadObjects ro, int kind) {
        int n = 0;
        for (int i = 0; i < ro.count(); i++) if (ro.kind(i) == kind) n++;
        return n;
    }

    @Test
    public void everyLevelHasTheObjectsOfTheTicket() {
        for (TrackDef def : DEFS) {
            for (boolean rev : new boolean[] {false, true}) {
                String id = TrackSpline.key(def, rev);
                RoadObjects e = objects(def, rev, RaceLevel.EASY), m = objects(def, rev, RaceLevel.MEDIUM), h = objects(def, rev, RaceLevel.HARD);
                assertEquals(id + " Easy: 2 ramps", 2, e.count());
                assertEquals(id + " Easy: ramps only", 2, count(e, RoadObjects.RAMP));
                for (int i = 0; i < 2; i++) assertEquals(id + " Easy ramps are in the centre lane", 0, e.lane(i));
                assertEquals(id + " Medium: 2 ramps", 2, count(m, RoadObjects.RAMP));
                assertEquals(id + " Medium: 3 potholes", 3, count(m, RoadObjects.POTHOLE));
                assertEquals(id + " Medium: no ice", 0, count(m, RoadObjects.ICE));
                assertTrue(id + " Hard has everything Medium has", h.count() >= m.count());
                assertTrue(id + " Hard has ice", count(h, RoadObjects.ICE) >= 1);
                assertTrue(id + " Hard has at most 2 ice patches", count(h, RoadObjects.ICE) <= 2);
                assertTrue(id + " Hard has at most one more ramp", count(h, RoadObjects.RAMP) <= 3);
            }
        }
        // no lane ramp on Rocky Lakes
        assertEquals(2, count(objects(TrackDefs.byId("rockies"), false, RaceLevel.HARD), RoadObjects.RAMP));
        assertEquals(2, count(objects(TrackDefs.byId("rockies"), true, RaceLevel.HARD), RoadObjects.RAMP));
    }

    @Test
    public void lateralPositionIsLaneTimesLaneFracTimesHalfWidth() {
        for (RaceLevel lv : RaceLevel.values()) {
            RoadObjects ro = objects(TrackDefs.byId("desert"), false, lv);
            for (int i = 0; i < ro.count(); i++) {
                assertEquals(ro.lane(i) * lv.laneFrac * lv.halfWidth, ro.lateral(i), 1e-5f);
            }
        }
    }

    @Test
    public void placementRulesHoldOnEverySplineLevelAndDirection() {
        StringBuilder bad = new StringBuilder();
        for (TrackDef def : DEFS) {
            for (boolean rev : new boolean[] {false, true}) {
                for (RaceLevel lv : RaceLevel.values()) {
                    TrackSpline t = TrackSpline.build(def, rev, lv, 0);
                    RoadObjects ro = objects(def, rev, lv);
                    float lap = t.lapLength();
                    int m = t.sampleCount();
                    float ds = lap / m;
                    String id = TrackSpline.key(def, rev) + " " + lv + ": ";
                    for (int i = 0; i < ro.count(); i++) {
                        String o = id + "kind " + ro.kind(i) + " at " + ro.s(i) + " ";
                        if (ro.start(i) < 30f) bad.append(o).append("starts in the start area; ");
                        if (ro.end(i) > lap - 15f) bad.append(o).append("ends in the finish area (lap ").append(lap).append("); ");
                        if (i > 0 && ro.start(i) - ro.end(i - 1) < 20f) bad.append(o).append("is closer than 20 m to the one before; ");
                        float from, to;
                        float need;
                        switch (ro.kind(i)) {
                            case RoadObjects.RAMP: from = ro.s(i) - 10f; to = ro.s(i) + 25f; need = 30f; break;
                            case RoadObjects.POTHOLE: from = ro.s(i) - 4f; to = ro.end(i) + 4f; need = 25f; break;
                            default: from = ro.s(i); to = ro.end(i); need = 25f; break;
                        }
                        float tight = Float.MAX_VALUE;
                        for (float s = from; s <= to; s += ds) tight = Math.min(tight, Math.abs(t.radiusAt(Math.floorMod(Math.round(s / ds), m))));
                        if (tight < need) bad.append(o).append("radius ").append(tight).append(" under ").append(need).append("; ");
                        if (ro.kind(i) == RoadObjects.ICE) {
                            float after = Float.MAX_VALUE;
                            for (float s = ro.end(i); s <= ro.end(i) + 30f; s += ds) after = Math.min(after, Math.abs(t.radiusAt(Math.floorMod(Math.round(s / ds), m))));
                            if (after < 16f) bad.append(o).append("bend of ").append(after).append(" m within 30 m after; ");
                            if (rev && ro.s(i) < 60f) bad.append(o).append("ice in the first 60 m; ");
                        }
                        // a free strip of 3 m between the object and the barrier on at least one side
                        float edge = lv.halfWidth + 0.6f, width = ro.kind(i) == RoadObjects.POTHOLE ? 2f * RoadObjects.POTHOLE_RADIUS
                                : RoadObjects.RAMP_WIDTH;
                        float left = (ro.lateral(i) - width / 2f) + edge, right = edge - (ro.lateral(i) + width / 2f);
                        if (Math.max(left, right) < 3f) bad.append(o).append("free strip ").append(Math.max(left, right)).append("; ");
                    }
                }
            }
        }
        assertEquals("placement problems: " + bad, 0, bad.length());
    }

    // ---- ramp coins ----

    @Test
    public void everyRampHasFourCoinsAfterItsLipAndNoRunComesNearIt() {
        for (RaceLevel lv : RaceLevel.values()) {
            for (TrackDef def : DEFS) {
                for (boolean rev : new boolean[] {false, true}) {
                    TrackSpline t = TrackSpline.build(def, rev, lv);
                    RoadObjects ro = t.roadObjects();
                    Slots sl = t.coinSlots(42L, lv.nitroPerLap, 1);
                    String id = TrackSpline.key(def, rev) + " " + lv;
                    int ramps = 0, rampCoins = 0;
                    for (int i = 0; i < ro.count(); i++) {
                        if (ro.kind(i) != RoadObjects.RAMP) continue;
                        ramps++;
                        for (int j = 0; j < 4; j++) {
                            int found = -1;
                            for (int k = 0; k < sl.count; k++) {
                                if (sl.height[k] > 0f && Math.abs(sl.s[k] - (ro.s(i) + 2f + 2f * j)) < 1e-3f) found = k;
                            }
                            assertTrue(id + " ramp " + i + " coin " + j, found >= 0);
                            rampCoins++;
                            assertEquals(ro.lateral(i), sl.offset[found], 1e-5f);
                            assertEquals(new float[] {1.4f, 1.8f, 1.8f, 1.4f}[j], sl.height[found], 1e-5f);
                            assertEquals(id + " can on the third coin of Medium and Hard", lv.earnedBoost && j == 2, sl.nitro[found]);
                        }
                    }
                    int high = 0;
                    for (int k = 0; k < sl.count; k++) if (sl.height[k] > 0f) high++;
                    assertEquals(id, 4 * ramps, high);
                    for (int k = 0; k < sl.count; k++) {
                        if (sl.height[k] > 0f) continue;
                        for (int i = 0; i < ro.count(); i++) {
                            assertTrue(id + " a run coin at " + sl.s[k] + " near object " + i,
                                    sl.s[k] < ro.start(i) - 6f || sl.s[k] > ro.end(i) + 6f);
                        }
                    }
                    for (int k = 1; k < sl.count; k++) assertTrue(id + " sorted by s", sl.s[k] >= sl.s[k - 1]);
                }
            }
        }
    }

    @Test
    public void aTruckFlyingOverTheRampCollectsItsFourCoins() {
        for (RaceLevel lv : RaceLevel.values()) {
            TrackSpline t = TrackSpline.build(TrackDefs.byId("desert"), false, lv);
            RaceLogic r = new RaceLogic(t, lv, 1, 42L);
            RoadObjects ro = t.roadObjects();
            int rampIdx = -1;
            for (int i = 0; i < ro.count(); i++) if (ro.kind(i) == RoadObjects.RAMP) { rampIdx = i; break; }
            float lip = ro.s(rampIdx);
            int rampCoins = 0;
            for (int k = 0; k < r.slotCount(0); k++) if (r.slotHeight(0, k) > 0f && r.slotX(0, k) != 0f) rampCoins++;
            int[] got = new int[1];
            boolean airSeen = false;
            for (int i = 0; i < 60 * 12 && r.car(0).s() < lip + 15f; i++) {
                float[] a = new float[3];
                t.sample(r.car(0).s() + 6f, a);
                float d = (float) Math.atan2(a[0] - r.car(0).x(), a[1] - r.car(0).z()) - r.car(0).yaw();
                while (d > Math.PI) d -= 2 * Math.PI;
                while (d < -Math.PI) d += 2 * Math.PI;
                r.setControls(0, Math.max(-1f, Math.min(1f, -d * 2.5f)), false);
                r.update(DT);
                got[0] += r.consumeCoins(0) + r.consumeNitro(0);
                airSeen |= r.airborne(0);
            }
            assertTrue(lv + " flew", airSeen);
            // coins (and a can on Medium and Hard) of that ramp: 4 pickups in the lane; other coins may come before it
            assertTrue(lv + " picked up " + got[0], got[0] >= 4);
        }
    }

    // ---- the camera ----

    @Test
    public void theCameraRisesWithTheTruckAndDipsOnLanding() {
        ChaseCamera cam = new ChaseCamera();
        cam.snap(0f, 0f, 0f);
        for (int i = 0; i < 300; i++) cam.update(DT, 0f, 0f, 0f, false, 2f);
        assertEquals(ChaseCamera.HEIGHT + 0.6f * 2f, cam.eye()[1], 0.02f);
        assertEquals(ChaseCamera.LOOK_HEIGHT + 0.5f * 2f, cam.target()[1], 0.02f);
        for (int i = 0; i < 300; i++) cam.update(DT, 0f, 0f, 0f, false, 0f);
        assertEquals(ChaseCamera.HEIGHT, cam.eye()[1], 0.02f);
        cam.onLanding();
        float low = Float.MAX_VALUE;
        for (int i = 0; i < 60; i++) {
            cam.update(DT, 0f, 0f, 0f, false, 0f);
            low = Math.min(low, cam.eye()[1]);
        }
        assertTrue("the eye dipped " + low, low < ChaseCamera.HEIGHT - 0.03f && low > ChaseCamera.HEIGHT - 0.25f);
        assertEquals(ChaseCamera.HEIGHT, cam.eye()[1], 0.02f);
    }

    // ---- the meshes ----

    private static int block(MeshData.Part p, int vertex) {
        float u = p.vertices[vertex * 8 + 6] * 512f, v = p.vertices[vertex * 8 + 7] * 512f;
        return (int) Math.floor(u / 16f) + 32 * ((int) Math.floor(v / 16f) - 1);
    }

    @Test
    public void meshesStayUnderTheirLimitsAndEachTriangleUsesOneSwatch() {
        PaletteSwatches pal = new PaletteSwatches();
        Themes.DESERT.fillPalette(pal);
        for (RaceLevel lv : RaceLevel.values()) {
            for (TrackDef def : DEFS) {
                for (boolean rev : new boolean[] {false, true}) {
                    TrackSpline t = TrackSpline.build(def, rev, lv);
                    RoadObjects ro = t.roadObjects();
                    for (int i = 0; i < ro.count(); i++) {
                        MeshData[] ms = {ObjectMeshes.ramp(ro, i, t, pal), ObjectMeshes.rampMarks(ro, i, t, pal), ObjectMeshes.sign(ro, i, t, pal)};
                        int[] max = {ObjectMeshes.MAX_RAMP, ObjectMeshes.MAX_MARKS, ObjectMeshes.MAX_SIGN};
                        for (int q = 0; q < 3; q++) {
                            assertTrue(ms[q].triangleCount() + " triangles", ms[q].triangleCount() <= max[q] && ms[q].triangleCount() > 0);
                            MeshData.Part p = ms[q].parts[0];
                            for (int k = 0; k < p.indices.length; k += 3) {
                                int b0 = block(p, p.indices[k] & 0xFFFF);
                                assertEquals(b0, block(p, p.indices[k + 1] & 0xFFFF));
                                assertEquals(b0, block(p, p.indices[k + 2] & 0xFFFF));
                                assertTrue("object swatch " + b0, (b0 >= 10 && b0 <= 15) || b0 >= ObjectMeshes.SW_FIRST_HAZARD);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void theRampRisesToTheLipAndFacesUp() {
        PaletteSwatches pal = new PaletteSwatches();
        Themes.DESERT.fillPalette(pal);
        TrackSpline t = TrackSpline.build(TrackDefs.byId("city"), false, RaceLevel.EASY);
        RoadObjects ro = t.roadObjects();
        MeshData.Part p = ObjectMeshes.ramp(ro, 0, t, pal).parts[0];
        float max = 0f, min = 9f;
        for (int v = 0; v < p.vertexCount; v++) {
            max = Math.max(max, p.vertices[v * 8 + 1]);
            min = Math.min(min, p.vertices[v * 8 + 1]);
        }
        assertEquals(RoadObjects.RAMP_HEIGHT, max, 0.001f);
        assertTrue(min >= 0f && min < 0.01f);
        float[] f = new float[3];
        t.sample(ro.s(0) - 2.5f, f);
        // every vertex lies within the ramp's footprint: 5 m along the road, 4 m across, round its middle
        float fx = (float) Math.sin(f[2]), fz = (float) Math.cos(f[2]);
        for (int v = 0; v < p.vertexCount; v++) {
            float dx = p.vertices[v * 8] - f[0], dz = p.vertices[v * 8 + 2] - f[1];
            assertTrue(Math.abs(dx * fx + dz * fz) <= 2.51f);
            assertTrue(Math.abs(dx * -fz + dz * fx) <= 2.01f);
        }
        // the top faces up
        for (int k = 0; k < p.indices.length; k += 3) {
            int a = p.indices[k] & 0xFFFF;
            if (block(p, a) == ObjectMeshes.SW_YELLOW) assertTrue(p.vertices[a * 8 + 4] > 0.9f);
        }
    }

    @Test
    public void signsStandOutsideTheBarrierTwentyMetresBefore() {
        for (TrackDef def : DEFS) {
            TrackSpline t = TrackSpline.build(def, false, RaceLevel.HARD);
            RoadObjects ro = t.roadObjects();
            float[] spot = new float[4], c = new float[3];
            for (int i = 0; i < ro.count(); i++) {
                ObjectMeshes.signSpot(ro, i, t, spot);
                int h = t.project(spot[0], spot[1], -1, new TrackPoint());
                TrackPoint tp = new TrackPoint();
                t.project(spot[0], spot[1], -1, tp);
                assertEquals(ro.s(i) - 20f, tp.s, 0.6f);
                assertEquals(Math.signum(ro.lane(i) < 0 ? -1f : 1f) * (t.roadHalfWidth() + ObjectMeshes.SIGN_OUT), tp.offset, 0.05f);
                assertTrue(Math.abs(tp.offset) > t.roadHalfWidth() + 0.6f);
            }
        }
    }

    // ---- races with the ramps on ----

    private static float race(TrackSpline t, RaceLevel lv, boolean[] finished) {
        RaceLogic r = new RaceLogic(t, lv, 1, 42L);
        float[] a = new float[3];
        for (int i = 0; i < 60 * 180 && !r.over(); i++) {
            t.sample(r.car(0).s() + 2.5f, a);
            float d = (float) Math.atan2(a[0] - r.car(0).x(), a[1] - r.car(0).z()) - r.car(0).yaw();
            while (d > Math.PI) d -= 2 * Math.PI;
            while (d < -Math.PI) d += 2 * Math.PI;
            r.setControls(0, Math.max(-1f, Math.min(1f, -d * 2.5f)), false);
            r.update(DT);
            assertTrue(Math.abs(r.car(0).offset()) <= CarModel.wallLimit(t.roadHalfWidth()) + 1e-3f);
        }
        finished[0] = r.finished(0);
        return r.time();
    }

    @Test
    public void everyRaceFinishesWithinEightPercentOfTheRaceWithoutObjects() {
        StringBuilder bad = new StringBuilder();
        for (RaceLevel lv : RaceLevel.values()) {
            for (TrackDef def : DEFS) {
                for (boolean rev : new boolean[] {false, true}) {
                    boolean[] f1 = new boolean[1], f2 = new boolean[1];
                    float with = race(TrackSpline.build(def, rev, lv), lv, f1);
                    float without = race(TrackSpline.build(def, rev, lv, 0), lv, f2);
                    String id = TrackSpline.key(def, rev) + " " + lv;
                    System.out.printf("%-10s %-6s with ramps %.1f s, without %.1f s (%.1f%%)%n", TrackSpline.key(def, rev), lv, with, without,
                            100f * (with - without) / without);
                    if (!f1[0]) bad.append(id).append(" did not finish; ");
                    if (with > without * 1.08f) bad.append(id).append(" took ").append(with).append(" s against ").append(without).append("; ");
                }
            }
        }
        assertEquals(bad.toString(), 0, bad.length());
    }
}
