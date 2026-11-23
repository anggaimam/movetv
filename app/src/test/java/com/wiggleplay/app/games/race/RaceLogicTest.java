package com.wiggleplay.app.games.race;

import static org.junit.Assert.*;

import org.junit.Test;

public class RaceLogicTest {
    private static final float DT = 1f / 60f;

    /** Pure-pursuit auto driver: steers at a point on the centre line ahead of the truck. */
    private static float autoSteer(RaceLogic r, TrackLayout t, int p, float look) {
        CarModel c = r.car(p);
        float[] a = new float[3];
        t.sample(c.s() + look, a);
        float dx = a[0] - c.x(), dz = a[1] - c.z();
        float want = (float) Math.atan2(dx, dz);
        float d = want - c.yaw();
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return Math.max(-1f, Math.min(1f, -d * 2.5f));
    }

    private static float[] drive(int level, int players, long seed, float look) {
        TrackLayout t = TrackMaps.forLevel(level);
        RaceLogic r = new RaceLogic(t, RaceLevel.fromNumber(level + 1), players, seed);
        float worst = 0f;
        for (int i = 0; i < 150 * 60 && !r.over(); i++) {
            for (int p = 0; p < players; p++) r.setControls(p, autoSteer(r, t, p, look), false);
            r.update(DT);
            for (int p = 0; p < players; p++) worst = Math.max(worst, Math.abs(r.car(p).offset()));
        }
        return new float[] {r.finished(0) ? 1f : 0f, r.time(), worst, r.score(0), r.offered(0), r.stars(0)};
    }

    @Test public void autoDriverFinishesThreeLapsOnAllMaps() {
        for (int lv = 0; lv < 3; lv++) {
            float[] res = drive(lv, 1, 42L, 7f);
            System.out.println("level " + lv + ": finished=" + res[0] + " time=" + res[1] + " worstOffset=" + res[2]
                    + " score=" + res[3] + " offered=" + res[4] + " stars=" + res[5]);
            assertEquals("level " + lv + " finished", 1f, res[0], 0f);
            assertTrue("level " + lv + " stays on the road, worst offset " + res[2], res[2] <= CarModel.WALL_LIMIT + 1e-3f);
        }
    }

    @Test public void lapsCountOnceEach() {
        TrackLayout t = TrackMaps.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 1L);
        int lapEvents = 0;
        for (int i = 0; i < 150 * 60 && !r.over(); i++) {
            r.setControls(0, autoSteer(r, t, 0, 7f), false);
            r.update(DT);
            if (r.consumeLap(0)) lapEvents++;
        }
        assertEquals(3, lapEvents);
        assertEquals(3, r.lapsDone(0));
        assertTrue(r.finished(0));
        assertEquals(1, r.place(0));
    }

    /** Steps one frame with the auto driver and returns the meter gain expected from coins picked up in it. */
    private static float step(RaceLogic r, TrackLayout t, boolean brake) {
        r.setControls(0, autoSteer(r, t, 0, 7f), brake);
        r.update(DT);
        int coins = r.consumeCoins(0);
        boolean star = r.consumeStar(0);
        return 0.15f * (coins + (star ? 1 : 0));
    }

    @Test public void boostLastsExactlyAndIgnoresBrakeAndJumps() {
        TrackLayout t = TrackMaps.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 1L);
        assertEquals(1f, r.boostMeter(0), 1e-6f);
        r.onJump(0);
        assertTrue(r.boosting(0));
        assertEquals(0f, r.boostMeter(0), 1e-6f);
        float t0 = 0f, expect = 0f;
        boolean jumped = false;
        while (r.boosting(0) && t0 < 3f) {
            if (t0 > 0.5f && !jumped) { r.onJump(0); jumped = true; }
            expect += 0.2f * DT + step(r, t, t0 > 0.2f);
            t0 += DT;
        }
        assertEquals(RaceLogic.BOOST_SECONDS, t0, 2 * DT);
        assertFalse(r.boosting(0));
        // the meter filled while boosting: 0.2 per second plus 0.15 per coin, and no second boost happened
        assertEquals(Math.min(1f, expect), r.boostMeter(0), 0.02f);
    }

    @Test public void jumpDoesNothingWhenMeterNotFull() {
        TrackLayout t = TrackMaps.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 1L);
        r.onJump(0);
        for (int i = 0; i < 100; i++) step(r, t, false);
        assertFalse(r.boosting(0));
        boolean full = r.boostMeter(0) >= 0.9999f;
        r.onJump(0);
        assertEquals("a jump boosts only with a full meter", full, r.boosting(0));
    }

    @Test public void meterRefillsAtPointTwoPerSecondPlusCoins() {
        TrackLayout t = TrackMaps.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 1L);
        r.onJump(0);
        for (int i = 0; i < 100; i++) step(r, t, false); // boost over
        float m0 = r.boostMeter(0), expect = 0f;
        for (int i = 0; i < 60; i++) expect += 0.2f * DT + step(r, t, false);
        assertEquals(Math.min(1f, m0 + expect), r.boostMeter(0), 0.02f);
    }

    @Test public void scoreOfferedAndStars() {
        TrackLayout t = TrackMaps.forLevel(1);
        RaceLogic r = new RaceLogic(t, RaceLevel.MEDIUM, 1, 5L);
        // a truck that never steers collects ~nothing but still gets one star and offered grows
        for (int i = 0; i < 20 * 60; i++) {
            r.setControls(0, 0f, false);
            r.update(DT);
        }
        assertTrue(r.score(0) >= 0);
        assertTrue(r.offered(0) >= r.score(0));
        assertTrue(r.stars(0) >= 1 && r.stars(0) <= 3);
        // a good driver gets coins
        float[] res = drive(1, 1, 5L, 7f);
        assertTrue("score " + res[3], res[3] > 0);
        assertTrue(res[4] >= res[3]);
    }

    @Test public void zeroCoinsStillOneStar() {
        TrackLayout t = TrackMaps.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 1L);
        assertEquals(1, r.stars(0));
    }

    @Test public void twoPlayersStartApartAndBumpApartSymmetrically() {
        TrackLayout t = TrackMaps.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 2, 3L);
        CarModel a = r.car(0), b = r.car(1);
        float d0 = (float) Math.hypot(b.x() - a.x(), b.z() - a.z());
        assertEquals(4f, d0, 0.1f);
        // put them on top of each other
        b.nudge(a.x() - b.x() + 0.6f, a.z() - b.z());
        float mx = (a.x() + b.x()) / 2f, mz = (a.z() + b.z()) / 2f;
        r.update(DT);
        float mx2 = (a.x() + b.x()) / 2f, mz2 = (a.z() + b.z()) / 2f;
        float d1 = (float) Math.hypot(b.x() - a.x(), b.z() - a.z());
        assertTrue("pushed apart: " + d1, d1 > 0.6f);
        assertEquals("symmetric push keeps the midpoint (up to driving)", mz, mz2, 0.5f);
        assertEquals(mx, mx2, 0.5f);
    }

    @Test public void twoPlayerRaceEndsAfterTheGrace() {
        TrackLayout t = TrackMaps.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 2, 3L);
        for (int i = 0; i < 150 * 60 && !r.over(); i++) {
            r.setControls(0, autoSteer(r, t, 0, 7f), false);
            r.setControls(1, 0f, true); // player 2 just brakes
            r.update(DT);
        }
        assertTrue(r.over());
        assertTrue(r.finished(0));
        assertEquals(1, r.place(0));
        assertEquals(2, r.place(1));
        assertTrue(r.time() <= r.finishTime(0) + RaceLogic.GRACE_SECONDS + 0.1f || r.time() >= RaceLogic.SAFETY_SECONDS);
    }

    /** Driver kinds for the section 6 tests. */
    private static final int CUTTER = 0, CENTRE = 1, SEEKER = 2;

    private static float steerToward(CarModel c, float tx, float tz) {
        float d = (float) Math.atan2(tx - c.x(), tz - c.z()) - c.yaw();
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return Math.max(-1f, Math.min(1f, -d * 2.5f));
    }

    /** Lane-seeker: aims at the next uncollected coin ahead when it is near, otherwise follows the centre line. */
    private static float seekerSteer(RaceLogic r, TrackLayout t, float look) {
        CarModel c = r.car(0);
        int n = r.slotCount(0);
        float bestD = 1e9f;
        int best = -1;
        for (int k = 0; k < n; k++) {
            if (r.slotGone(0, k)) continue;
            float dx = r.slotX(0, k) - c.x(), dz = r.slotZ(0, k) - c.z();
            float fwd = dx * (float) Math.sin(c.yaw()) + dz * (float) Math.cos(c.yaw());
            if (fwd < 0.5f) continue;
            float d = (float) Math.hypot(dx, dz);
            if (d < bestD) { bestD = d; best = k; }
        }
        if (best >= 0 && bestD < 10f) return steerToward(c, r.slotX(0, best), r.slotZ(0, best));
        return autoSteer(r, t, 0, look);
    }

    /** Drives a full race; returns {score, offered, cornerReached, cornerTotal} (corner = min distance in lap 1). */
    private static int[] run(int lv, int kind) {
        TrackLayout t = TrackMaps.forLevel(lv);
        RaceLogic r = new RaceLogic(t, RaceLevel.fromNumber(lv + 1), 1, 42L);
        int n = r.slotCount(0);
        float[] best = new float[n];
        java.util.Arrays.fill(best, 1e9f);
        for (int i = 0; i < 150 * 60 && !r.over(); i++) {
            float st = kind == CUTTER ? autoSteer(r, t, 0, 7f)
                    : kind == CENTRE ? autoSteer(r, t, 0, 2.5f) : seekerSteer(r, t, 2.5f);
            r.setControls(0, st, false);
            r.update(DT);
            if (r.lapsDone(0) < 1) {
                CarModel c = r.car(0);
                for (int k = 0; k < n; k++) {
                    if (!r.slotIsCorner(0, k)) continue;
                    float d = (float) Math.hypot(r.slotX(0, k) - c.x(), r.slotZ(0, k) - c.z());
                    if (d < best[k]) best[k] = d;
                }
            }
        }
        int corner = 0, got = 0;
        for (int k = 0; k < n; k++) {
            if (!r.slotIsCorner(0, k)) continue;
            corner++;
            if (best[k] <= RaceLogic.PICKUP_RADIUS) got++;
        }
        return new int[] {r.score(0), r.offered(0), got, corner};
    }

    private static final String[] DRIVER_NAMES = {"cutter(7)", "centre(2.5)", "seeker"};

    /** Section 6: three auto-drivers; prints the map x driver table for the report. */
    @Test public void threeDriversCornerCoinsAndStars() {
        int[][][] res = new int[3][3][];
        for (int lv = 0; lv < 3; lv++) for (int d = 0; d < 3; d++) res[lv][d] = run(lv, d);
        for (int lv = 0; lv < 3; lv++) for (int d = 0; d < 3; d++) {
            int[] x = res[lv][d];
            System.out.println("map " + lv + " " + DRIVER_NAMES[d] + ": score " + x[0] + " / offered " + x[1] + " = "
                    + (x[1] == 0 ? 0 : 100 * x[0] / x[1]) + "%, corner coins " + x[2] + "/" + x[3]);
        }
        for (int lv = 0; lv < 3; lv++) {
            for (int d : new int[] {CUTTER, CENTRE}) {
                int[] x = res[lv][d];
                assertTrue("map " + lv + " " + DRIVER_NAMES[d] + " corner " + x[2] + "/" + x[3], x[2] * 3 >= x[3] * 2);
            }
            int[] c = res[lv][CENTRE];
            float pc = 100f * c[0] / c[1];
            assertTrue("map " + lv + " centre " + pc + "%", pc >= 40f && pc < 70f);
        }
        for (int lv = 0; lv < 2; lv++) {
            int[] x = res[lv][SEEKER];
            float pc = 100f * x[0] / x[1];
            assertTrue("map " + lv + " seeker " + pc + "%", pc >= 70f);
        }
    }
}
