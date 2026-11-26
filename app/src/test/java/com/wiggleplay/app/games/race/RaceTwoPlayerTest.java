package com.wiggleplay.app.games.race;

import static org.junit.Assert.*;

import org.junit.Test;

/** T-24 Part A: the rules of a two-player race on the real tracks (grid, places, end rule, coins and cans, pushing in the air). */
public class RaceTwoPlayerTest {
    private static final float DT = 1f / 60f;

    private static float steerToward(CarModel c, TrackSpline t, float look) {
        float[] a = new float[3];
        t.sample(c.s() + look, a);
        float d = (float) Math.atan2(a[0] - c.x(), a[1] - c.z()) - c.yaw();
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return Math.max(-1f, Math.min(1f, -d * 2.5f));
    }

    private static RaceLogic race(TrackDef def, boolean rev, RaceLevel lv, long seed) {
        return new RaceLogic(TrackSpline.build(def, rev, lv), lv, 2, seed);
    }

    @Test
    public void startGridIsLateralMinus2AndPlus2AtTheSameS() {
        for (RaceLevel lv : RaceLevel.values()) {
            RaceLogic r = race(TrackDefs.ALL[0], false, lv, 1L);
            CarModel a = r.car(0), b = r.car(1);
            float lap = TrackSpline.build(TrackDefs.ALL[0], false, lv).lapLength();
            float ds = Math.abs(a.s() - b.s());
            assertEquals(0f, Math.min(ds, lap - ds), 0.05f);   // the same s (one may sit just before the line)
            assertEquals(-2f, a.offset(), 0.05f);
            assertEquals(2f, b.offset(), 0.05f);
            assertEquals(2, r.players());
        }
    }

    @Test
    public void bothAutoDriversFinishEveryTrackAndLevel() {
        StringBuilder bad = new StringBuilder();
        for (RaceLevel lv : RaceLevel.values()) {
            for (TrackDef def : TrackDefs.ALL) {
                for (boolean rev : new boolean[] {false, true}) {
                    TrackSpline t = TrackSpline.build(def, rev, lv);
                    RaceLogic r = new RaceLogic(t, lv, 2, 7L);
                    for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); i++) {
                        r.setControls(0, steerToward(r.car(0), t, 7f), false);
                        r.setControls(1, steerToward(r.car(1), t, 8f), false);
                        r.update(DT);
                    }
                    String id = lv + " " + TrackSpline.key(def, rev) + " ";
                    if (!r.finished(0)) bad.append(id).append("P1 did not finish; ");
                    if (!r.finished(1)) bad.append(id).append("P2 did not finish; ");
                    if (r.place(0) == r.place(1)) bad.append(id).append("same place; ");
                }
            }
        }
        assertEquals("races: " + bad, 0, bad.length());
    }

    @Test
    public void raceEndsWhenBothFinishedOrTenSecondsAfterTheFirst() {
        TrackSpline t = TrackSpline.build(TrackDefs.ALL[0], false, RaceLevel.EASY);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 2, 3L);
        for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); i++) {
            r.setControls(0, steerToward(r.car(0), t, 7f), false);
            r.setControls(1, 0f, true);   // the second player just brakes
            r.update(DT);
        }
        assertTrue(r.over());
        assertTrue(r.finished(0));
        assertFalse(r.finished(1));
        assertEquals(1, r.place(0));
        assertEquals(2, r.place(1));
        assertTrue("ends 10 s after the first finisher", r.time() <= r.finishTime(0) + RaceLogic.GRACE_SECONDS + 0.1f);
    }

    @Test
    public void coinsAndCansAreCountedPerPlayer() {
        TrackSpline t = TrackSpline.build(TrackDefs.ALL[0], false, RaceLevel.MEDIUM);
        RaceLogic r = new RaceLogic(t, RaceLevel.MEDIUM, 2, 5L);
        for (int i = 0; i < 60 * 15; i++) {
            r.setControls(0, steerToward(r.car(0), t, 7f), false);
            r.setControls(1, 0f, true);   // player 2 crawls
            r.update(DT);
        }
        assertTrue("player 1 collected coins: " + r.score(0), r.score(0) > 0);
        assertTrue("player 1 (driving) scored more than player 2 (braking): " + r.score(0) + " vs " + r.score(1), r.score(0) > r.score(1));
        assertTrue("slots are separate per player", r.slotCount(0) > 0 && r.slotCount(1) > 0);
        assertTrue("player 1's coin is gone only for player 1", countGone(r, 0) > countGone(r, 1));
    }

    private static int countGone(RaceLogic r, int p) {
        int n = 0;
        for (int i = 0; i < r.slotCount(p); i++) if (r.slotGone(p, i)) n++;
        return n;
    }

    @Test
    public void trucksAreNotPushedWhileOneIsInTheAir() {
        TrackSpline t = null;
        int ramp = -1;
        outer:
        for (TrackDef def : TrackDefs.ALL) {
            t = TrackSpline.build(def, false, RaceLevel.EASY);
            RoadObjects ro = t.roadObjects();
            for (int i = 0; i < ro.count(); i++) {
                if (ro.kind(i) == RoadObjects.RAMP) { ramp = i; break outer; }
            }
        }
        assertTrue("a track with a ramp", ramp >= 0);
        RoadObjects ro = t.roadObjects();
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 2, 9L);
        CarModel a = r.car(0), b = r.car(1);
        a.reset(t, ro.start(ramp) - 14f, ro.lateral(ramp));
        b.reset(t, ro.start(ramp) - 30f, 0f);
        int guard = 0;
        while (!a.airborne() && guard++ < 60 * 10) {
            r.setControls(0, steerToward(a, t, 5f), false);
            r.setControls(1, 0f, true);
            r.update(DT);
        }
        assertTrue("player 1 is in the air", a.airborne() || a.y() > 0.3f);
        // put player 2 right under player 1 and step once: no push while one is flying
        b.nudge(a.x() - b.x() + 0.6f, a.z() - b.z());
        float before = (float) Math.hypot(b.x() - a.x(), b.z() - a.z());
        r.update(DT);
        float after = (float) Math.hypot(b.x() - a.x(), b.z() - a.z());
        assertTrue("not pushed apart: " + before + " -> " + after, after < 1.5f);
    }

    @Test
    public void paintJobsStayInRangeAndOnlyFactoryKeepsTheTruckColours() {
        assertEquals(0, TruckPaints.clean(-1));
        assertEquals(0, TruckPaints.clean(TruckPaints.RGB.length));
        assertEquals(3, TruckPaints.clean(3));
        assertEquals(0, TruckPaints.renderColour(0, 0));
        assertEquals(0, TruckPaints.renderColour(99, 0));
        assertEquals(0x014FA8E8, TruckPaints.renderColour(1, 0));
        assertEquals(0x034FA8E8, TruckPaints.renderColour(1, 2));
        assertEquals(40, RaceLogic.timeBonus(10f, 100f));
        assertEquals(0, RaceLogic.timeBonus(200f, 100f));
        assertTrue(RaceLogic.timeBonus(100f, 100f) < 40 && RaceLogic.timeBonus(100f, 100f) > 0);
        assertEquals(TruckPaints.RGB.length, TruckPaints.NAMES.length);
        assertEquals(4, TruckPaints.TRUCK_NAMES.length);
    }
}
