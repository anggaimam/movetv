package com.wiggleplay.app.games.jungle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JungleLogicTest {

    private static final float DT = 1f / 60f;

    private static final class Log implements JungleLogic.Listener {
        int jumps, lands, ducks, clears, hits, bananas, goldens;

        @Override
        public void onEvent(int type, int index, int points) {
            switch (type) {
                case JungleLogic.EV_JUMP: jumps++; break;
                case JungleLogic.EV_LAND: lands++; break;
                case JungleLogic.EV_DUCK: ducks++; break;
                case JungleLogic.EV_CLEAR: clears++; break;
                case JungleLogic.EV_HIT: hits++; break;
                case JungleLogic.EV_BANANA: bananas++; break;
                case JungleLogic.EV_GOLDEN: goldens++; break;
                default: break;
            }
        }
    }

    private static void run(JungleLogic l, float seconds) {
        int n = Math.round(seconds / DT);
        for (int i = 0; i < n; i++) l.update(DT);
    }

    /** Runs until the course clock reaches t. */
    private static void runTo(JungleLogic l, float t) {
        while (l.clock() < t) l.update(DT);
    }

    private static int firstOf(JungleLogic l, int kind) {
        for (int i = 0; i < l.obstacleCount(); i++) if (l.obstacleKind(i) == kind) return i;
        return -1;
    }

    // ---- the course ----

    @Test
    public void sameSeedGivesTheSameCourseAndOtherSeedsDiffer() {
        JungleLogic a = new JungleLogic(3, 42L, null), b = new JungleLogic(3, 42L, null), c = new JungleLogic(3, 43L, null);
        assertEquals(a.obstacleCount(), b.obstacleCount());
        assertEquals(a.itemCount(), b.itemCount());
        boolean differs = a.obstacleCount() != c.obstacleCount();
        for (int i = 0; i < a.obstacleCount(); i++) {
            assertEquals(a.obstacleTime(i), b.obstacleTime(i), 0f);
            assertEquals(a.obstacleKind(i), b.obstacleKind(i));
            if (i < c.obstacleCount() && a.obstacleTime(i) != c.obstacleTime(i)) differs = true;
        }
        assertTrue(differs);
    }

    @Test
    public void easyHasLogsOnlyEveryFourSeconds() {
        JungleLogic l = new JungleLogic(1, 5L, null);
        assertTrue(l.obstacleCount() >= 12);
        for (int i = 0; i < l.obstacleCount(); i++) {
            assertEquals(JungleLogic.LOG, l.obstacleKind(i));
            if (i > 0) {
                float gap = l.obstacleTime(i) - l.obstacleTime(i - 1);
                assertTrue("gap " + gap, gap >= 3.55f && gap <= 4.45f);
            }
        }
    }

    @Test
    public void mediumHasLogsAndVinesAndNeverThreeTheSameInARow() {
        for (long seed = 1; seed <= 20; seed++) {
            JungleLogic l = new JungleLogic(2, seed, null);
            boolean log = false, vine = false;
            for (int i = 0; i < l.obstacleCount(); i++) {
                int k = l.obstacleKind(i);
                assertTrue(k == JungleLogic.LOG || k == JungleLogic.VINE);
                log |= k == JungleLogic.LOG;
                vine |= k == JungleLogic.VINE;
                if (i >= 2) {
                    assertFalse(k == l.obstacleKind(i - 1) && k == l.obstacleKind(i - 2));
                }
            }
            assertTrue(log && vine);
        }
    }

    @Test
    public void hardHasCombosAndDoubleLogsWithTheLogThenVineGapOf1point2() {
        boolean combo = false, dbl = false;
        for (long seed = 1; seed <= 20; seed++) {
            JungleLogic l = new JungleLogic(3, seed, null);
            for (int i = 1; i < l.obstacleCount(); i++) {
                float gap = l.obstacleTime(i) - l.obstacleTime(i - 1);
                if (l.obstacleKind(i - 1) == JungleLogic.LOG && l.obstacleKind(i) == JungleLogic.VINE
                        && Math.abs(gap - 1.2f) < 0.001f) combo = true;
                assertTrue("obstacles too close: " + gap, gap >= 1.19f);
            }
            dbl |= firstOf(l, JungleLogic.DOUBLE_LOG) >= 0;
        }
        assertTrue(combo);
        assertTrue(dbl);
    }

    @Test
    public void everythingArrivesInsideTheRoundAndTheFirstObstacleWaitsForCalibration() {
        for (int lv = 1; lv <= 3; lv++) {
            JungleLogic l = new JungleLogic(lv, 9L, null);
            assertEquals(JungleLogic.FIRST_AT, l.obstacleTime(0), 0f);
            for (int i = 0; i < l.obstacleCount(); i++) {
                assertTrue(l.obstacleTime(i) <= JungleLogic.LAST_AT);
            }
            for (int i = 0; i < l.itemCount(); i++) {
                assertTrue(l.itemTime(i) <= JungleLogic.LAST_AT);
                if (i > 0) assertTrue(l.itemTime(i) >= l.itemTime(i - 1));
            }
        }
    }

    @Test
    public void everyLevelHasBananasAndAGoldenCoconut() {
        for (int lv = 1; lv <= 3; lv++) {
            JungleLogic l = new JungleLogic(lv, 11L, null);
            int g = 0, high = 0, ground = 0;
            for (int i = 0; i < l.itemCount(); i++) {
                if (l.itemKind(i) == JungleLogic.GOLDEN) g++;
                if (l.itemKind(i) == JungleLogic.HIGH_BANANA) high++;
                if (l.itemKind(i) == JungleLogic.GROUND_BANANA) ground++;
            }
            assertTrue(g >= 1 && high >= 3 && ground >= 3);
        }
    }

    // ---- logs ----

    @Test
    public void aJumpStartedHalfASecondBeforeClearsTheLog() {
        Log log = new Log();
        JungleLogic l = new JungleLogic(1, 5L, log);
        float t = l.obstacleTime(0);
        runTo(l, t - 0.45f);
        l.jump();
        run(l, 1.5f);
        assertEquals(1, log.clears);
        assertEquals(0, log.hits);
        assertEquals(JungleLogic.CLEARED, l.obstacleState(0));
    }

    @Test
    public void windowIsPlusMinusPointFourFiveAroundTheBestMoment() {
        // best start is 0.45 s before arrival: 0.9 s before and the moment of arrival are the edges
        float[] starts = {-0.85f, -0.45f, -0.05f};
        for (float s : starts) {
            JungleLogic l = new JungleLogic(1, 5L, null);
            runTo(l, l.obstacleTime(0) + s);
            l.jump();
            run(l, 1.2f);
            assertEquals("start " + s, JungleLogic.CLEARED, l.obstacleState(0));
        }
        float[] late = {-1.1f, 0.15f};
        for (float s : late) {
            JungleLogic l = new JungleLogic(1, 5L, null);
            runTo(l, l.obstacleTime(0) + s);
            l.jump();
            run(l, 1.2f);
            assertEquals("start " + s, JungleLogic.HIT, l.obstacleState(0));
        }
    }

    @Test
    public void hittingALogStumblesSlowsTheClockAndGivesNoPointsButNeverEndsTheRun() {
        Log log = new Log();
        JungleLogic l = new JungleLogic(1, 5L, log);
        runTo(l, l.obstacleTime(0) + 0.02f);
        assertEquals(1, log.hits);
        assertEquals(0, log.clears);
        assertEquals(log.bananas * JungleLogic.BANANA_POINTS, l.score());   // only the bananas on the ground
        assertTrue(l.stumble() > 0.9f);
        assertTrue(l.slow());
        float c0 = l.clock();
        run(l, 0.5f);
        assertEquals(0.5f * JungleLogic.SLOW_RATE, l.clock() - c0, 0.03f);
        run(l, 1.0f);
        assertFalse(l.slow());
        assertEquals(0f, l.stumble(), 0f);
        float c1 = l.clock();
        run(l, 0.5f);
        assertEquals(0.5f, l.clock() - c1, 0.03f);
        // the run goes on: the next obstacle still comes and can be cleared
        runTo(l, l.obstacleTime(1) - 0.45f);
        l.jump();
        run(l, 1.2f);
        assertEquals(JungleLogic.CLEARED, l.obstacleState(1));
    }

    @Test
    public void aSecondJumpInTheAirIsIgnored() {
        Log log = new Log();
        JungleLogic l = new JungleLogic(1, 5L, log);
        l.jump();
        run(l, 0.3f);
        l.jump();
        assertEquals(1, log.jumps);
        run(l, 0.7f);
        assertEquals(1, log.lands);
        l.jump();
        assertEquals(2, log.jumps);
    }

    // ---- double log ----

    @Test
    public void aDoubleLogNeedsABigJump() {
        JungleLogic small = null, big = null;
        for (long seed = 1; seed < 40 && (small == null); seed++) {
            JungleLogic l = new JungleLogic(3, seed, null);
            if (firstOf(l, JungleLogic.DOUBLE_LOG) >= 0 && firstOf(l, JungleLogic.DOUBLE_LOG) <= 2) {
                small = l;
                big = new JungleLogic(3, seed, null);
            }
        }
        int dl = firstOf(small, JungleLogic.DOUBLE_LOG);
        assertTrue(dl >= 0);
        // everything before it is skipped by just standing there
        runTo(small, small.obstacleTime(dl) - 0.45f);
        small.jump();
        small.setJumpRise(0.40f);
        run(small, 0.45f);
        run(small, 0.9f);
        assertEquals(JungleLogic.HIT, small.obstacleState(dl));

        runTo(big, big.obstacleTime(dl) - 0.45f);
        big.jump();
        for (int i = 0; i < 70; i++) {
            big.setJumpRise(0.7f);
            big.update(DT);
        }
        run(big, 0.5f);
        assertEquals(JungleLogic.CLEARED, big.obstacleState(dl));
        assertTrue(big.obstacleTime(dl) > 0f);
    }

    @Test
    public void aBigJumpShowsHigherThanANormalOne() {
        JungleLogic a = new JungleLogic(1, 5L, null), b = new JungleLogic(1, 5L, null);
        a.jump();
        b.jump();
        float ha = 0f, hb = 0f;
        for (int i = 0; i < 54; i++) {
            b.setJumpRise(0.7f);
            a.update(DT);
            b.update(DT);
            ha = Math.max(ha, a.height());
            hb = Math.max(hb, b.height());
        }
        assertEquals(1f, ha, 0.02f);
        assertEquals(1.3f, hb, 0.03f);
        assertTrue(b.bigJump() || !b.airborne());
    }

    // ---- vines ----

    @Test
    public void aDuckClearsAVineAndIsShownForAtLeastPointSixSeconds() {
        Log log = new Log();
        JungleLogic l = null;
        for (long seed = 1; seed < 40; seed++) {
            l = new JungleLogic(2, seed, log);
            if (firstOf(l, JungleLogic.VINE) == 0) break;
        }
        int v = firstOf(l, JungleLogic.VINE);
        // pass any logs before the vine by jumping
        for (int i = 0; i < v; i++) {
            runTo(l, l.obstacleTime(i) - 0.45f);
            l.jump();
        }
        runTo(l, l.obstacleTime(v) - 0.2f);
        l.setDuck(true);
        l.update(DT);
        assertTrue(l.ducking());
        l.setDuck(false);                     // the player stands up at once...
        assertTrue(l.ducking());              // ...but the duck is still shown
        run(l, 0.5f);
        assertTrue(l.ducking() || l.obstacleState(v) != JungleLogic.PENDING);
        assertEquals(JungleLogic.CLEARED, l.obstacleState(v));
        run(l, 0.7f);
        assertFalse(l.ducking());
        assertEquals(1, log.ducks);
    }

    @Test
    public void standingTallInFrontOfAVineIsAHit() {
        JungleLogic l = null;
        for (long seed = 1; seed < 40; seed++) {
            l = new JungleLogic(2, seed, null);
            if (firstOf(l, JungleLogic.VINE) == 0) break;
        }
        runTo(l, l.obstacleTime(0) + 0.02f);
        assertEquals(JungleLogic.HIT, l.obstacleState(0));
    }

    @Test
    public void duckingIsIgnoredInTheAir() {
        JungleLogic l = new JungleLogic(2, 3L, null);
        l.jump();
        l.setDuck(true);
        assertFalse(l.ducking());
    }

    // ---- bananas ----

    @Test
    public void groundBananasAreTakenOnTheGroundAndMissedInTheAir() {
        JungleLogic l = new JungleLogic(1, 5L, null);
        int g = -1;
        for (int i = 0; i < l.itemCount(); i++) if (l.itemKind(i) == JungleLogic.GROUND_BANANA) { g = i; break; }
        float t = l.itemTime(g);
        JungleLogic air = new JungleLogic(1, 5L, null);
        runTo(l, t + 0.05f);
        assertTrue(l.itemTaken(g));
        // in the air at the top of a jump the same banana is missed
        runTo(air, t - 0.45f);
        air.jump();
        runTo(air, t + 0.05f);
        assertTrue(air.itemDone(g));
        assertFalse(air.itemTaken(g));
    }

    @Test
    public void highBananasNeedAJump() {
        JungleLogic stand = new JungleLogic(1, 5L, null), jump = new JungleLogic(1, 5L, null);
        int h = -1;
        for (int i = 0; i < stand.itemCount(); i++) if (stand.itemKind(i) == JungleLogic.HIGH_BANANA) { h = i; break; }
        runTo(stand, stand.itemTime(h) + 0.05f);
        assertFalse(stand.itemTaken(h));
        float ot = jump.obstacleTime(0);
        runTo(jump, ot - 0.45f);
        jump.jump();
        run(jump, 1.0f);
        assertTrue(jump.itemTaken(h));
        assertTrue(jump.score() >= JungleLogic.CLEAR_POINTS + 3 * JungleLogic.BANANA_POINTS);
    }

    @Test
    public void theGoldenCoconutNeedsAJumpAndIsWorthFive() {
        Log log = new Log();
        JungleLogic l = new JungleLogic(1, 5L, log);
        int g = -1;
        for (int i = 0; i < l.itemCount(); i++) if (l.itemKind(i) == JungleLogic.GOLDEN) { g = i; break; }
        assertTrue(g >= 0);
        JungleLogic no = new JungleLogic(1, 5L, null);
        runTo(no, no.itemTime(g) + 0.5f);
        assertFalse(no.itemTaken(g));
        runTo(l, l.itemTime(g) - 0.45f);
        l.jump();
        run(l, 1.0f);
        assertTrue(l.itemTaken(g));
        assertEquals(1, log.goldens);
    }

    // ---- stars ----

    @Test
    public void starsAreFair() {
        assertEquals(1, JungleLogic.stars(0, 0));
        assertEquals(1, JungleLogic.stars(0, 50));
        assertEquals(1, JungleLogic.stars(19, 50));
        assertEquals(2, JungleLogic.stars(20, 50));
        assertEquals(2, JungleLogic.stars(34, 50));
        assertEquals(3, JungleLogic.stars(35, 50));
        assertEquals(3, JungleLogic.stars(50, 50));
        assertEquals(1, JungleLogic.stars(-3, 50));
    }

    /** A pilot who jumps and ducks at the best moment, and also jumps for coconuts. */
    private static JungleLogic fly(int level, long seed, boolean perfect) {
        JungleLogic l = new JungleLogic(level, seed, null);
        int nextO = 0, nextG = 0;
        float duckUntil = -1f;
        for (int i = 0; i < 70 * 60; i++) {
            float t = l.clock();
            if (perfect) {
                while (nextO < l.obstacleCount() && l.obstacleTime(nextO) < t - 0.1f) nextO++;
                if (nextO < l.obstacleCount()) {
                    float ot = l.obstacleTime(nextO);
                    int k = l.obstacleKind(nextO);
                    if (k == JungleLogic.VINE) {
                        if (t >= ot - 0.3f && t <= ot + 0.3f) l.setDuck(true); else l.setDuck(false);
                    } else if (!l.airborne() && t >= ot - 0.45f && t < ot) {
                        l.jump();
                    }
                    if (k != JungleLogic.VINE && l.airborne()) l.setJumpRise(k == JungleLogic.DOUBLE_LOG ? 0.7f : 0.4f);
                }
                while (nextG < l.itemCount() && (l.itemKind(nextG) != JungleLogic.GOLDEN || l.itemTime(nextG) < t - 0.45f)) nextG++;
                if (nextG < l.itemCount() && !l.airborne() && t >= l.itemTime(nextG) - 0.45f && t < l.itemTime(nextG) - 0.3f) {
                    l.jump();
                }
            }
            l.update(DT);
        }
        return l;
    }

    @Test
    public void aPerfectPilotGetsThreeStarsOnEveryLevelAndNeverCrashes() {
        for (int lv = 1; lv <= 3; lv++) {
            for (long seed = 1; seed <= 5; seed++) {
                JungleLogic l = fly(lv, seed, true);
                assertEquals("level " + lv + " seed " + seed + " hits", 0, l.hits());
                assertEquals("level " + lv + " seed " + seed, 3, JungleLogic.stars(l.score(), l.offered()));
            }
        }
    }

    @Test
    public void aPlayerWhoNeverMovesStillGetsOneStarAndTheScoreStaysAboveZero() {
        for (int lv = 1; lv <= 3; lv++) {
            JungleLogic l = fly(lv, 3L, false);
            assertTrue(l.score() >= 0);
            assertTrue(l.offered() > 0);
            assertEquals(1, JungleLogic.stars(l.score(), l.offered()));
            assertTrue(l.hits() > 0);
        }
    }

    @Test
    public void eachPlayerOfATwoPlayerGameGetsTheSameCourse() {
        JungleLogic p1 = new JungleLogic(2, 77L, null), p2 = new JungleLogic(2, 77L, null);
        for (int i = 0; i < p1.obstacleCount(); i++) {
            assertEquals(p1.obstacleKind(i), p2.obstacleKind(i));
            assertEquals(p1.obstacleTime(i), p2.obstacleTime(i), 0f);
        }
        for (int i = 0; i < p1.itemCount(); i++) assertEquals(p1.itemKind(i), p2.itemKind(i));
    }

    @Test
    public void obstacleSpeedAndWarningGetHarderPerLevel() {
        float s1 = new JungleLogic(1, 1L, null).speed(), s3 = new JungleLogic(3, 1L, null).speed();
        assertTrue(s3 > s1);
        assertEquals(2.0f, new JungleLogic(1, 1L, null).warn(), 0f);
        assertEquals(1.6f, new JungleLogic(2, 1L, null).warn(), 0f);
        assertEquals(1.3f, new JungleLogic(3, 1L, null).warn(), 0f);
    }
}
