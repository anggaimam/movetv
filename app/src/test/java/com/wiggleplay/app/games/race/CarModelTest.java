package com.wiggleplay.app.games.race;

import static org.junit.Assert.*;

import org.junit.Test;

public class CarModelTest {
    private static final float DT = 1f / 60f;

    private static CarModel start(RaceLevel lv, boolean two, TrackPath t, float lateral) {
        CarModel c = new CarModel(lv, two);
        c.reset(t, 0f, lateral);
        return c;
    }

    @Test public void steeringRightTurnsRight() {
        TrackLayout t = TrackMaps.forLevel(0);
        CarModel c = start(RaceLevel.EASY, false, t, 0f);
        float yaw0 = c.yaw();
        for (int i = 0; i < 30; i++) c.update(DT, 1f, false, false, t);
        assertTrue("yaw decreases when steering right", c.yaw() < yaw0);
        assertTrue(c.frontWheelYaw() < 0f);
    }

    @Test public void assistPullsBackToTheCentre() {
        TrackLayout t = TrackMaps.forLevel(0);
        CarModel e = start(RaceLevel.EASY, false, t, 2f);
        CarModel h = start(RaceLevel.HARD, false, t, 2f);
        for (int i = 0; i < 108; i++) { // 1.8 s: Hard is still on the straight (first corner starts at about 26 units)
            e.update(DT, 0f, false, false, t);
            h.update(DT, 0f, false, false, t);
        }
        assertTrue("easy offset " + e.offset(), Math.abs(e.offset()) < 1f);
        assertTrue("easy pulls harder than hard: " + e.offset() + " vs " + h.offset(),
                Math.abs(h.offset()) > Math.abs(e.offset()));
    }

    @Test public void neverTurnsMoreThan75DegreesFromTheRoad() {
        TrackLayout t = TrackMaps.forLevel(0);
        CarModel c = start(RaceLevel.HARD, false, t, 0f);
        float limit = (float) Math.toRadians(75) + 0.05f;
        for (int i = 0; i < 600; i++) {
            c.update(DT, 1f, false, false, t);
            TrackPoint tp = new TrackPoint();
            t.project(c.x(), c.z(), c.trackHint(), tp);
            float d = c.yaw() - tp.tangentYaw;
            while (d > Math.PI) d -= 2 * Math.PI;
            while (d < -Math.PI) d += 2 * Math.PI;
            assertTrue("angle " + d, Math.abs(d) <= limit);
            assertTrue("never stops", c.speed() > 0.3f * RaceLevel.HARD.baseSpeed - 0.01f || i < 30);
        }
    }

    private static float auto(CarModel c, TrackPath t) {
        float[] a = new float[3];
        t.sample(c.s() + 7f, a);
        float d = (float) Math.atan2(a[0] - c.x(), a[1] - c.z()) - c.yaw();
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return Math.max(-1f, Math.min(1f, -d * 2.5f));
    }

    @Test public void speedTargets() {
        TrackLayout t = TrackMaps.forLevel(0);
        RaceLevel lv = RaceLevel.MEDIUM;
        CarModel c = start(lv, false, t, 0f);
        for (int i = 0; i < 300; i++) c.update(DT, auto(c, t), false, false, t);
        assertEquals(lv.baseSpeed, c.speed(), 0.5f);
        CarModel b = start(lv, false, t, 0f);
        for (int i = 0; i < 300; i++) b.update(DT, auto(b, t), true, false, t);
        assertEquals(lv.baseSpeed * 1.5f, b.speed(), 0.5f);
        CarModel k = start(lv, false, t, 0f);
        for (int i = 0; i < 120; i++) k.update(DT, auto(k, t), false, false, t);
        for (int i = 0; i < 120; i++) k.update(DT, auto(k, t), false, true, t);
        assertEquals(lv.baseSpeed * 0.4f, k.speed(), 0.6f);
        CarModel bb = start(lv, false, t, 0f); // boost beats brake
        for (int i = 0; i < 300; i++) bb.update(DT, auto(bb, t), true, true, t);
        assertEquals(lv.baseSpeed * 1.5f, bb.speed(), 0.5f);
        CarModel off = start(lv, false, t, 0f); // never steered: scrapes the barriers but never stops
        for (int i = 0; i < 600; i++) off.update(DT, 0f, false, false, t);
        assertTrue(off.speed() >= 0.35f * lv.baseSpeed);
    }

    @Test public void staysNearTheRoadAndWallHitIsRateLimited() {
        TrackLayout t = TrackMaps.forLevel(0);
        CarModel c = start(RaceLevel.EASY, false, t, 0f);
        int hits = 0;
        float time = 0;
        for (int i = 0; i < 600; i++) {
            c.update(DT, -1f, false, false, t); // keep steering left, drive into the wall
            time += DT;
            assertTrue("offset " + c.offset(), Math.abs(c.offset()) <= CarModel.wallLimit(t.roadHalfWidth()) + 1e-3f);
            if (c.consumeHitWall()) hits++;
        }
        assertTrue("at most one hit per 0.5 s: " + hits, hits <= (int) (time / 0.5f) + 1);
    }

    @Test public void nudgeAndScaleSpeed() {
        TrackLayout t = TrackMaps.forLevel(0);
        CarModel c = start(RaceLevel.EASY, false, t, 0f);
        for (int i = 0; i < 120; i++) c.update(DT, 0f, false, false, t);
        float x = c.x(), sp = c.speed();
        c.nudge(0.5f, 0f);
        assertEquals(x + 0.5f, c.x(), 1e-4f);
        c.scaleSpeed(0.5f);
        assertEquals(sp * 0.5f, c.speed(), 1e-4f);
    }

    /** T-23 Part A: steer held at +1 or -1 for 10 s on every map and level never passes the barrier limit 3.85 (tile road). */
    @Test public void neverPassesTheBarrierOnAnyMap() {
        for (int lv = 0; lv < 3; lv++) {
            TrackLayout t = TrackMaps.forLevel(lv);
            for (float steer : new float[] {-1f, 1f}) {
                for (boolean boost : new boolean[] {false, true}) {
                    CarModel c = start(RaceLevel.fromNumber(lv + 1), false, t, 0f);
                    float worst = 0f;
                    for (int i = 0; i < 600; i++) {
                        c.update(DT, steer, boost, false, t);
                        worst = Math.max(worst, Math.abs(c.offset()));
                    }
                    assertTrue("map " + lv + " steer " + steer + " boost " + boost + " worst " + worst,
                            worst <= CarModel.wallLimit(t.roadHalfWidth()) + 1e-3f);
                }
            }
        }
    }

    /**
     * T-23 Part A: against the barrier the truck keeps moving along the road (no sticking), never goes backwards,
     * never drops under 0.35 x base, and does not jitter (offset moves little from frame to frame).
     */
    @Test public void scrapesAlongTheBarrierWithoutStickingOrJitter() {
        for (int lv = 0; lv < 3; lv++) {
            TrackLayout t = TrackMaps.forLevel(lv);
            RaceLevel level = RaceLevel.fromNumber(lv + 1);
            for (float steer : new float[] {-1f, 1f}) {
                CarModel c = start(level, false, t, 0f);
                // s comes from projecting onto the sampled centre line: on the outside of a corner it saw-tooths
                // by about 0.2 at each sample, so progress is checked per 0.5 s window, not per frame
                float lap = t.lapLength(), driven = 0f, lastS = c.s(), lastOff = c.offset(), worstJump = 0f;
                float window = 0f;
                int atWall = 0, hits = 0;
                for (int i = 0; i < 600; i++) {
                    c.update(DT, steer, false, false, t);
                    float ds = c.s() - lastS;
                    if (ds < -lap / 2f) ds += lap;
                    if (ds > lap / 2f) ds -= lap;
                    assertTrue("map " + lv + " frame " + i + " jumped back " + ds, ds > -0.3f);
                    driven += ds;
                    window += ds;
                    if (i % 30 == 29) {
                        assertTrue("map " + lv + " window ending " + i + " went backwards " + window, window > 0.5f);
                        window = 0f;
                    }
                    lastS = c.s();
                    if (i > 60) {
                        assertTrue("speed " + c.speed(), c.speed() >= 0.35f * level.baseSpeed - 1e-3f);
                        worstJump = Math.max(worstJump, Math.abs(c.offset() - lastOff));
                    }
                    lastOff = c.offset();
                    if (Math.abs(c.offset()) > CarModel.wallLimit(t.roadHalfWidth()) - 0.01f) atWall++;
                    if (c.consumeHitWall()) hits++;
                }
                System.out.println("map " + lv + " steer " + steer + ": driven " + driven + " in 10 s, frames at the wall "
                        + atWall + ", hits " + hits + ", worst offset change per frame " + worstJump);
                assertTrue("map " + lv + " reached the wall", atWall > 0);
                assertTrue("map " + lv + " kept driving: " + driven, driven > 0.35f * level.baseSpeed * 10f);
                assertTrue("map " + lv + " jitter " + worstJump, worstJump < 0.3f);
                assertTrue("map " + lv + " hits rate-limited: " + hits, hits <= 21);
            }
        }
    }

    /** T-23 Part A: every coin and the star sit inside the barrier limit (lane coins at 2.5, corner coins at 0.75). */
    @Test public void allCoinsAreInsideTheBarrier() {
        for (int lv = 0; lv < 3; lv++) {
            TrackLayout tile = TrackMaps.forLevel(lv);
            Slots sl = tile.coinSlots(42L);
            for (int i = 0; i < sl.count; i++) {
                assertTrue("map " + lv + " slot " + i + " offset " + sl.offset[i],
                        Math.abs(sl.offset[i]) <= CarModel.wallLimit(tile.roadHalfWidth()) - 1f);
            }
            assertTrue(Math.abs(sl.starOffset) <= CarModel.wallLimit(tile.roadHalfWidth()) - 1f);
        }
    }

    // ---- T-28 Part B: the same rules on the spline tracks, 4 layouts x 2 directions x 3 levels ----

    /** Steer held at +1 or -1 for 10 s, with and without boost, never passes wallLimit(halfWidth). */
    @Test public void neverPassesTheBarrierOnAnySplineTrack() {
        for (TrackDef def : TrackDefs.ALL) {
            for (boolean rev : new boolean[] {false, true}) {
                for (RaceLevel level : RaceLevel.values()) {
                    TrackSpline t = TrackSpline.build(def, rev, level);
                    float limit = CarModel.wallLimit(level.halfWidth);
                    for (float steer : new float[] {-1f, 1f}) {
                        for (boolean boost : new boolean[] {false, true}) {
                            CarModel c = start(level, false, t, 0f);
                            float worst = 0f;
                            for (int i = 0; i < 600; i++) {
                                c.update(DT, steer, boost, false, t);
                                worst = Math.max(worst, Math.abs(c.offset()));
                            }
                            assertTrue(TrackSpline.key(def, rev) + " " + level + " steer " + steer + " boost " + boost
                                    + " worst " + worst, worst <= limit + 0.01f);
                        }
                    }
                }
            }
        }
    }

    /** Against the barrier the truck keeps moving along the road, never goes backwards, keeps its speed floor, hardly jitters. */
    @Test public void scrapesAlongTheBarrierOnAnySplineTrack() {
        for (TrackDef def : TrackDefs.ALL) {
            for (boolean rev : new boolean[] {false, true}) {
                for (RaceLevel level : RaceLevel.values()) {
                    TrackSpline t = TrackSpline.build(def, rev, level);
                    float limit = CarModel.wallLimit(level.halfWidth);
                    String id = TrackSpline.key(def, rev) + " " + level;
                    for (float steer : new float[] {-1f, 1f}) {
                        CarModel c = start(level, false, t, 0f);
                        float lap = t.lapLength(), driven = 0f, lastS = c.s(), lastOff = c.offset(), worstJump = 0f, window = 0f;
                        int atWall = 0, hits = 0;
                        for (int i = 0; i < 600; i++) {
                            c.update(DT, steer, false, false, t);
                            float ds = c.s() - lastS;
                            if (ds < -lap / 2f) ds += lap;
                            if (ds > lap / 2f) ds -= lap;
                            assertTrue(id + " frame " + i + " jumped back " + ds, ds > -0.05f);
                            driven += ds;
                            window += ds;
                            if (i % 30 == 29) {
                                assertTrue(id + " window ending " + i + " went backwards " + window, window > 0.5f);
                                window = 0f;
                            }
                            lastS = c.s();
                            if (i > 60) {
                                assertTrue(id + " speed " + c.speed(), c.speed() >= 0.35f * level.baseSpeed - 1e-3f);
                                worstJump = Math.max(worstJump, Math.abs(c.offset() - lastOff));
                            }
                            lastOff = c.offset();
                            if (Math.abs(c.offset()) > limit - 0.01f) atWall++;
                            if (c.consumeHitWall()) hits++;
                        }
                        assertTrue(id + " steer " + steer + " reached the wall", atWall > 0);
                        assertTrue(id + " steer " + steer + " kept driving: " + driven, driven > 0.35f * level.baseSpeed * 10f);
                        assertTrue(id + " steer " + steer + " jitter " + worstJump, worstJump < 0.3f);
                        assertTrue(id + " steer " + steer + " hits rate-limited: " + hits, hits <= 21);
                    }
                }
            }
        }
    }

    @Test public void wallLimitIsHalfWidthMinusAQuarterOfAMetreOrSo() {
        assertEquals(3.85f, CarModel.wallLimit(4.0f), 1e-6f);
        assertEquals(5.35f, CarModel.wallLimit(RaceLevel.EASY.halfWidth), 1e-6f);
        assertEquals(4.85f, CarModel.wallLimit(RaceLevel.MEDIUM.halfWidth), 1e-6f);
        assertEquals(4.35f, CarModel.wallLimit(RaceLevel.HARD.halfWidth), 1e-6f);
    }
}
