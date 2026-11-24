package com.wiggleplay.app.games.race;

import static org.junit.Assert.*;

import org.junit.Test;

public class RaceLogicTest {
    private static final float DT = 1f / 60f;

    /** Pure-pursuit auto driver: steers at a point on the centre line ahead of the truck. */
    private static float autoSteer(RaceLogic r, TrackPath t, int p, float look) {
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
        TrackSpline t = TestTracks.forLevel(level);
        RaceLogic r = new RaceLogic(t, RaceLevel.fromNumber(level + 1), players, seed);
        float worst = 0f;
        for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); i++) {
            for (int p = 0; p < players; p++) r.setControls(p, autoSteer(r, t, p, look), false);
            r.update(DT);
            for (int p = 0; p < players; p++) worst = Math.max(worst, Math.abs(r.car(p).offset()));
        }
        return new float[] {r.finished(0) ? 1f : 0f, r.time(), worst, r.score(0), r.offered(0), r.stars(0)};
    }

    @Test public void autoDriverFinishesAllLapsOnAllMaps() {
        for (int lv = 0; lv < 3; lv++) {
            float[] res = drive(lv, 1, 42L, 7f);
            System.out.println("level " + lv + ": finished=" + res[0] + " time=" + res[1] + " worstOffset=" + res[2]
                    + " score=" + res[3] + " offered=" + res[4] + " stars=" + res[5]);
            assertEquals("level " + lv + " finished", 1f, res[0], 0f);
            assertTrue("level " + lv + " stays on the road, worst offset " + res[2], res[2] <= CarModel.wallLimit(TestTracks.forLevel(lv).roadHalfWidth()) + 1e-3f);
        }
    }

    @Test public void lapsCountOnceEach() {
        TrackSpline t = TestTracks.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 1L);
        int lapEvents = 0;
        for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); i++) {
            r.setControls(0, autoSteer(r, t, 0, 7f), false);
            r.update(DT);
            if (r.consumeLap(0)) lapEvents++;
        }
        assertEquals(RaceLevel.EASY.laps, lapEvents);
        assertEquals(RaceLevel.EASY.laps, r.lapsDone(0));
        assertTrue(r.finished(0));
        assertEquals(1, r.place(0));
    }

    /** Steps one frame with the auto driver and returns the meter gain expected from coins picked up in it. */
    private static float step(RaceLogic r, TrackPath t, boolean brake) {
        r.setControls(0, autoSteer(r, t, 0, 7f), brake);
        r.update(DT);
        int coins = r.consumeCoins(0);
        boolean star = r.consumeStar(0);
        return 0.15f * (coins + (star ? 1 : 0));
    }

    @Test public void boostLastsExactlyAndIgnoresBrakeAndJumps() {
        TrackSpline t = TestTracks.forLevel(0);
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
        TrackSpline t = TestTracks.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 1L);
        r.onJump(0);
        for (int i = 0; i < 100; i++) step(r, t, false);
        assertFalse(r.boosting(0));
        boolean full = r.boostMeter(0) >= 0.9999f;
        r.onJump(0);
        assertEquals("a jump boosts only with a full meter", full, r.boosting(0));
    }

    @Test public void meterRefillsAtPointTwoPerSecondPlusCoins() {
        TrackSpline t = TestTracks.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 1L);
        r.onJump(0);
        for (int i = 0; i < 100; i++) step(r, t, false); // boost over
        float m0 = r.boostMeter(0), expect = 0f;
        for (int i = 0; i < 60; i++) expect += 0.2f * DT + step(r, t, false);
        assertEquals(Math.min(1f, m0 + expect), r.boostMeter(0), 0.02f);
    }

    @Test public void scoreOfferedAndStars() {
        TrackSpline t = TestTracks.forLevel(1);
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
        TrackSpline t = TestTracks.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 1L);
        assertEquals(1, r.stars(0));
    }

    @Test public void twoPlayersStartApartAndBumpApartSymmetrically() {
        TrackSpline t = TestTracks.forLevel(0);
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
        TrackSpline t = TestTracks.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 2, 3L);
        for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); i++) {
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
    private static float seekerSteer(RaceLogic r, TrackPath t, float look) {
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
        TrackSpline t = TestTracks.forLevel(lv);
        RaceLogic r = new RaceLogic(t, RaceLevel.fromNumber(lv + 1), 1, 42L);
        int n = r.slotCount(0);
        float[] best = new float[n];
        java.util.Arrays.fill(best, 1e9f);
        for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); i++) {
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

    // ---- T-26: earned nitro on Medium and Hard ----

    private static int cans(RaceLogic r) {
        int n = 0;
        for (int k = 0; k < r.slotCount(0); k++) if (r.slotIsNitro(0, k)) n++;
        return n;
    }

    @Test public void nitroCansPerLapAre0On_Easy_4On_Medium_5On_Hard() {
        assertEquals(0, cans(new RaceLogic(TestTracks.forLevel(0), RaceLevel.EASY, 1, 42L)));
        assertEquals(4, cans(new RaceLogic(TestTracks.forLevel(1), RaceLevel.MEDIUM, 1, 42L)));
        assertEquals(5, cans(new RaceLogic(TestTracks.forLevel(2), RaceLevel.HARD, 1, 42L)));
    }

    /** Medium and Hard: the meter starts empty, never rises by time or coins, and rises 0.5 per can; two cans give a rocket. */
    @Test public void earnedBoostOnlyFillsFromCans() {
        for (int lv = 1; lv < 3; lv++) {
            TrackSpline t = TestTracks.forLevel(lv);
            RaceLogic r = new RaceLogic(t, RaceLevel.fromNumber(lv + 1), 1, 42L);
            assertEquals("starts empty", 0f, r.boostMeter(0), 0f);
            r.onJump(0);
            assertFalse("no boost from an empty meter", r.boosting(0));
            int canCount = 0, coinCount = 0, boosts = 0;
            boolean was = false;
            for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); i++) {
                r.setControls(0, seekerSteer(r, t, 2.5f), false);
                float before = r.boostMeter(0);
                r.onJump(0);                 // a kid who jumps all the time: it only works with a full meter
                if (r.boosting(0) && !was) boosts++;
                was = r.boosting(0);
                before = r.boostMeter(0);
                r.update(DT);
                int c = r.consumeNitro(0);
                canCount += c;
                coinCount += r.consumeCoins(0);
                float rise = r.boostMeter(0) - before;
                if (c == 0) assertTrue("level " + lv + ": the meter rose by " + rise + " with no can", rise <= 1e-6f);
                else assertEquals("level " + lv + ": a can fills half", 0.5f * c, rise, 1e-4f);
            }
            assertTrue("level " + lv + " picked up cans: " + canCount, canCount >= 3);
            assertTrue("level " + lv + " picked up coins: " + coinCount, coinCount > 20);
            assertTrue("level " + lv + " boosts: " + boosts, boosts >= 1);
            assertEquals("level " + lv + ": two cans per rocket", canCount / 2, boosts);
        }
    }

    /** Cans are worth no points, so the stars do not depend on them. */
    @Test public void nitroCansAreWorthNothing() {
        TrackSpline t = TestTracks.forLevel(1);
        RaceLogic r = new RaceLogic(t, RaceLevel.MEDIUM, 1, 42L);
        int canSlots = cans(r);
        assertTrue(canSlots > 0);
        int coinsAndStar = 0;
        for (int k = 0; k < r.slotCount(0); k++) {
            if (r.slotIsNitro(0, k)) continue;
            coinsAndStar += r.slotIsStar(0, k) ? RaceLogic.STAR_VALUE : RaceLogic.COIN_VALUE;
        }
        // drive one full lap with the seeker; "offered" for a lap is every coin and the star, nothing for the cans
        for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over() && r.lapsDone(0) < 1; i++) {
            r.setControls(0, seekerSteer(r, t, 2.5f), false);
            r.update(DT);
        }
        assertEquals(1, r.lapsDone(0));
        assertEquals(coinsAndStar, r.offered(0));
        assertTrue(r.score(0) <= r.offered(0));
    }

    // ---- the magnet ----

    private static int magnets(RaceLogic r) {
        int n = 0;
        for (int k = 0; k < r.slotCount(0); k++) if (r.slotIsMagnet(0, k)) n++;
        return n;
    }

    @Test public void oneMagnetPerLapOnEveryLevelAndItIsNotACanOrTheStar() {
        for (int lv = 0; lv < 3; lv++) {
            RaceLogic r = new RaceLogic(TestTracks.forLevel(lv), RaceLevel.fromNumber(lv + 1), 1, 42L);
            assertEquals("level " + lv, 1, magnets(r));
            for (int k = 0; k < r.slotCount(0); k++) {
                if (r.slotIsMagnet(0, k)) assertFalse(r.slotIsNitro(0, k) || r.slotIsStar(0, k));
            }
        }
    }

    /** The magnet is worth a coin; picking it up starts 6 s in which coins slide in; the coins go back for the next lap. */
    @Test public void magnetPullsCoinsInAndTheyGoBackNextLap() {
        TrackSpline t = TestTracks.forLevel(0);
        RaceLogic r = new RaceLogic(t, RaceLevel.EASY, 1, 42L);
        int n = r.slotCount(0);
        float[] x0 = new float[n], z0 = new float[n];
        for (int k = 0; k < n; k++) { x0[k] = r.slotX(0, k); z0[k] = r.slotZ(0, k); }
        boolean got = false, moved = false, wrapped = false;
        float maxLeft = 0f;
        int coinsWhile = 0;
        for (int i = 0; i < 100 * 60 && !r.over(); i++) {
            r.setControls(0, seekerSteer(r, t, 2.5f), false);
            r.update(DT);
            if (r.consumeMagnet(0) > 0) got = true;
            maxLeft = Math.max(maxLeft, r.magnetLeft(0));
            if (r.magnetLeft(0) > 0f) {
                coinsWhile += r.consumeCoins(0);
                for (int k = 0; k < n; k++) {
                    if (!r.slotGone(0, k) && (r.slotX(0, k) != x0[k] || r.slotZ(0, k) != z0[k])) moved = true;
                }
            }
            if (r.consumeLap(0) && r.lapsDone(0) == 1) {
                wrapped = true;
                for (int k = 0; k < n; k++) {
                    assertEquals("slot " + k + " back in place", x0[k], r.slotX(0, k), 1e-6f);
                    assertEquals(z0[k], r.slotZ(0, k), 1e-6f);
                }
            }
        }
        assertTrue("picked up the magnet", got);
        assertEquals(RaceLogic.MAGNET_SECONDS, maxLeft, 0.1f);
        assertTrue("coins slid towards the truck", moved);
        assertTrue("coins collected while it pulled: " + coinsWhile, coinsWhile >= 2);
        assertTrue("a lap was finished", wrapped);
    }

    // ---- T-28 Part B: spline tracks, per-level laps, the 180 s limit and the three-driver table ----

    private static RaceLogic splineRace(TrackSpline t, RaceLevel lv) {
        return new RaceLogic(t, lv, 1, 42L);
    }

    @Test public void lapsPerLevelAndTheSafetyLimit() {
        assertEquals(180f, RaceLogic.SAFETY_SECONDS, 0f);
        int[] laps = {2, 3, 3};
        for (int i = 0; i < 3; i++) {
            RaceLevel lv = RaceLevel.values()[i];
            assertEquals(laps[i], lv.laps);
            TrackSpline t = TrackSpline.build(TrackDefs.ALL[1], false, lv);
            RaceLogic r = splineRace(t, lv);
            assertEquals(laps[i], r.lapsTotal());
            assertEquals(1, r.currentLap(0));
            int events = 0;
            for (int k = 0; k < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); k++) {
                r.setControls(0, autoSteer(r, t, 0, 7f), false);
                r.update(DT);
                if (r.consumeLap(0)) events++;
            }
            assertEquals(lv + " lap events", laps[i], events);
            assertTrue(lv + " finished", r.finished(0));
            assertEquals(laps[i], r.currentLap(0));
        }
        // a truck that never steers: the race ends by the limit at 180 s, not 150
        TrackSpline t = TrackSpline.build(TrackDefs.ALL[0], false, RaceLevel.HARD);
        RaceLogic r = splineRace(t, RaceLevel.HARD);
        for (int k = 0; k < 170 * 60; k++) {
            r.setControls(0, 0f, true);
            r.update(DT);
        }
        assertFalse("not over at 170 s", r.over());
        for (int k = 0; k < 11 * 60; k++) {
            r.setControls(0, 0f, true);
            r.update(DT);
        }
        assertTrue("over after 180 s", r.over());
    }

    @Test public void nitroRulesHoldOnSplineTracks() {
        for (int lvI = 1; lvI < 3; lvI++) {
            RaceLevel lv = RaceLevel.values()[lvI];
            for (TrackDef def : TrackDefs.ALL) {
                for (boolean rev : new boolean[] {false, true}) {
                    TrackSpline t = TrackSpline.build(def, rev, lv);
                    RaceLogic r = splineRace(t, lv);
                    String id = TrackSpline.key(def, rev) + " " + lv;
                    assertEquals(id + " starts empty", 0f, r.boostMeter(0), 0f);
                    assertEquals(id + " cans per lap", lv.nitroPerLap, cans(r));
                    int canCount = 0, boosts = 0;
                    boolean was = false;
                    for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); i++) {
                        r.setControls(0, seekerSteer(r, t, 2.5f), false);
                        r.onJump(0);
                        if (r.boosting(0) && !was) boosts++;
                        was = r.boosting(0);
                        float before = r.boostMeter(0);
                        r.update(DT);
                        int c = r.consumeNitro(0);
                        canCount += c;
                        r.consumeCoins(0);
                        float rise = r.boostMeter(0) - before;
                        if (c == 0) assertTrue(id + ": the meter rose by " + rise + " with no can", rise <= 1e-6f);
                        else assertEquals(id + ": a can fills half", 0.5f * c, rise, 1e-4f);
                    }
                    assertEquals(id + " the seeker gets every can", lv.nitroPerLap * lv.laps, canCount);
                    assertEquals(id + ": two cans per rocket", canCount / 2, boosts);
                }
            }
        }
    }

    @Test public void easyRefillIsUnchangedOnSplineTracks() {
        TrackSpline t = TrackSpline.build(TrackDefs.ALL[2], false, RaceLevel.EASY);
        RaceLogic r = splineRace(t, RaceLevel.EASY);
        assertEquals(1f, r.boostMeter(0), 1e-6f);
        r.onJump(0);
        assertTrue(r.boosting(0));
        for (int i = 0; i < 100; i++) step(r, t, false);   // the boost is over
        float m0 = r.boostMeter(0), expect = 0f;
        for (int i = 0; i < 60; i++) expect += 0.2f * DT + step(r, t, false);
        assertEquals(Math.min(1f, m0 + expect), r.boostMeter(0), 0.02f);
    }

    /** One race by one driver on a spline track: {score, offered, cans, finished, seconds}. */
    private static float[] splineRun(TrackSpline t, RaceLevel lv, int kind) {
        RaceLogic r = splineRace(t, lv);
        int cans = 0;
        for (int i = 0; i < (int) (RaceLogic.SAFETY_SECONDS * 60) && !r.over(); i++) {
            float st = kind == CUTTER ? autoSteer(r, t, 0, 7f) : kind == CENTRE ? autoSteer(r, t, 0, 2.5f) : seekerSteer(r, t, 2.5f);
            r.setControls(0, st, false);
            r.update(DT);
            cans += r.consumeNitro(0);
        }
        return new float[] {r.score(0), r.offered(0), cans, r.finished(0) ? 1f : 0f, r.time()};
    }

    /** T-28 B: three auto-drivers on 4 layouts x 2 directions x 3 levels (24 races each, the magnet on, seed 42). */
    @Test public void threeDriversOnTheSplineTracks() {
        float[] lo = {50f, 45f, 40f}, hi = {65f, 60f, 55f};   // centre driver bands per level (8-track average)
        StringBuilder bad = new StringBuilder();
        for (int lvI = 0; lvI < 3; lvI++) {
            RaceLevel lv = RaceLevel.values()[lvI];
            for (int kind = 0; kind < 3; kind++) {
                float sum = 0f;
                int races = 0;
                for (TrackDef def : TrackDefs.ALL) {
                    for (boolean rev : new boolean[] {false, true}) {
                        TrackSpline t = TrackSpline.build(def, rev, lv);
                        float[] x = splineRun(t, lv, kind);
                        float pc = 100f * x[0] / x[1];
                        sum += pc;
                        races++;
                        int stars = com.wiggleplay.app.games.common.StarRules.stars((int) x[0], (int) x[1]);
                        System.out.printf("%-6s %-11s %-8s score %3.0f / offered %3.0f = %5.1f%% stars %d cans/lap %.1f time %.0f s%n",
                                lv, DRIVER_NAMES[kind], TrackSpline.key(def, rev), x[0], x[1], pc, stars, x[2] / lv.laps, x[4]);
                        String id = lv + " " + DRIVER_NAMES[kind] + " " + TrackSpline.key(def, rev) + " ";
                        if (x[3] != 1f) bad.append(id).append("did not finish; ");
                        // a single race may be 3 points under the low edge or 5 over the high edge
                        if (kind == CENTRE && (pc < lo[lvI] - 3f || pc > hi[lvI] + 5f)) bad.append(id).append("centre ").append(pc).append("%; ");
                        if (kind == SEEKER && pc < 72f) bad.append(id).append("seeker ").append(pc).append("%; ");
                        if (kind == CUTTER && pc < 37f) bad.append(id).append("cutter ").append(pc).append("%; ");
                        if (kind == CENTRE && lvI == 1 && x[2] / lv.laps < 3f) bad.append(id).append("cans/lap ").append(x[2] / lv.laps).append("; ");
                    }
                }
                float avg = sum / races;
                System.out.printf("%-6s %-11s AVERAGE over %d tracks %5.1f%%%n", lv, DRIVER_NAMES[kind], races, avg);
                String id = lv + " " + DRIVER_NAMES[kind] + " average ";
                if (kind == CENTRE && (avg < lo[lvI] || avg > hi[lvI])) bad.append(id).append(avg).append("%; ");
                if (kind == SEEKER && avg < 75f) bad.append(id).append(avg).append("%; ");
                if (kind == CUTTER && avg < 40f) bad.append(id).append(avg).append("%; ");
            }
        }
        assertEquals("out of band: " + bad, 0, bad.length());
    }
}
