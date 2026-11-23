package com.wiggleplay.app.games.race;

import static org.junit.Assert.*;

import org.junit.Test;

public class CarModelTest {
    private static final float DT = 1f / 60f;

    private static CarModel start(RaceLevel lv, boolean two, TrackLayout t, float lateral) {
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

    private static float auto(CarModel c, TrackLayout t) {
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
        CarModel off = start(lv, false, t, 0f); // never steered: leaves the road, slows to 60% but never stops
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
            assertTrue("offset " + c.offset(), Math.abs(c.offset()) <= t.roadHalfWidth() + 2.6f);
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
}
