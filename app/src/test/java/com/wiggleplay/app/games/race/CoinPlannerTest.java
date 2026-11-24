package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CoinPlannerTest {

    private static final long SEED = 42L;

    /** Visits every layout x direction x level and hands the plan to the check. */
    private interface Check {
        void run(String id, TrackSpline t, RaceLevel lv, Slots sl);
    }

    private static void forAll(Check c) {
        for (RaceLevel lv : RaceLevel.values()) {
            for (TrackDef def : TrackDefs.ALL) {
                for (boolean rev : new boolean[] {false, true}) {
                    TrackSpline t = TrackSpline.build(def, rev, lv);
                    c.run(TrackSpline.key(def, rev) + " " + lv, t, lv, t.coinSlots(SEED, lv.nitroPerLap, 1));
                }
            }
        }
    }

    /** True when any sample from s to s + 6.4 has |radius| under 30. */
    private static boolean bendRun(TrackSpline t, float s0) {
        int n = t.sampleCount();
        float ds = t.lapLength() / n;
        for (int i = (int) Math.floor(s0 / ds); i <= (int) Math.ceil((s0 + 6.4f) / ds); i++) {
            if (Math.abs(t.radiusAt(i % n)) < 30f) return true;
        }
        return false;
    }

    /** Sign of the radius at the tightest sample of the run's span: +1 left bend, -1 right bend. */
    private static float bendSign(TrackSpline t, float s0) {
        int n = t.sampleCount();
        float ds = t.lapLength() / n, tight = Float.MAX_VALUE, sign = 1f;
        for (int i = (int) Math.floor(s0 / ds); i <= (int) Math.ceil((s0 + 6.4f) / ds); i++) {
            float r = t.radiusAt(i % n);
            if (Math.abs(r) < tight) {
                tight = Math.abs(r);
                sign = r > 0f ? 1f : -1f;
            }
        }
        return sign;
    }

    @Test
    public void noCoinsInTheStartAreaAndRunsAreFifteenMetresApart() {
        forAll((id, t, lv, sl) -> {
            float lap = t.lapLength();
            assertTrue(id + " coins exist", sl.count > 0 && sl.count % 5 == 0);
            for (int i = 0; i < sl.count; i++) {
                assertTrue(id + " coin " + i + " at s=" + sl.s[i], sl.s[i] >= 25f - 1e-3f && sl.s[i] < lap - 15f);
                if (i > 0) assertTrue(id + " sorted", sl.s[i] >= sl.s[i - 1]);
            }
            for (int r = 0; r < sl.count / 5; r++) {
                for (int j = 1; j < 5; j++) assertEquals(id + " coins 1.6 apart", 1.6f, sl.s[r * 5 + j] - sl.s[r * 5 + j - 1], 1e-3f);
                float start = sl.s[r * 5];
                assertEquals(id + " run starts on the 15 m grid", 0f, ((start - 25f) / 15f) - Math.round((start - 25f) / 15f), 1e-3f);
                if (r > 0) assertTrue(id + " runs at least 15 m apart", start - sl.s[(r - 1) * 5] >= 15f - 1e-3f);
            }
            assertTrue(id + " the last run ends before L - 15", sl.s[sl.count - 1] < lap - 15f);
        });
    }

    @Test
    public void bendRunsSitOnTheOutsideAndStraightRunsInLanes() {
        forAll((id, t, lv, sl) -> {
            float hw = t.roadHalfWidth();
            int prevLane = 0;
            for (int r = 0; r < sl.count / 5; r++) {
                float s0 = sl.s[r * 5];
                boolean bend = bendRun(t, s0);
                for (int j = 0; j < 5; j++) assertEquals(id + " one flag per run", bend, sl.corner[r * 5 + j]);
                float off = sl.offset[r * 5];
                for (int j = 1; j < 5; j++) assertEquals(sl.offset[r * 5], sl.offset[r * 5 + j], 0f);
                if (bend) {
                    // a left bend (positive radius) has its outside on the right: offset > 0
                    assertEquals(id + " bend run at s=" + s0, bendSign(t, s0) * lv.bendFrac * hw, off, 1e-4f);
                } else {
                    int lane = off == 0f ? 0 : off > 0f ? 1 : -1;
                    assertEquals(id + " lane offset", lane * lv.laneFrac * hw, off, 1e-4f);
                    if (lane != 0) assertTrue(id + " same side lane twice in a row at s=" + s0, lane != prevLane);
                    prevLane = lane;
                }
            }
        });
    }

    @Test
    public void everyCoinStarAndCanIsInsideTheBarrier() {
        forAll((id, t, lv, sl) -> {
            float limit = CarModel.wallLimit(t.roadHalfWidth()) - 1f;
            for (int i = 0; i < sl.count; i++) assertTrue(id + " slot " + i + " offset " + sl.offset[i], Math.abs(sl.offset[i]) <= limit);
            assertTrue(Math.abs(sl.starOffset) <= limit);
        });
    }

    @Test
    public void theStarIsAtABendApexAndItsRunIsGone() {
        forAll((id, t, lv, sl) -> {
            int n = t.sampleCount();
            float ds = t.lapLength() / n;
            int i = Math.round(sl.starS / ds) % n;
            assertTrue(id + " star at s=" + sl.starS + " radius " + t.radiusAt(i), Math.abs(t.radiusAt(i)) < 25f);
            assertEquals(0f, sl.starOffset, 0f);
            for (int k = 0; k < sl.count; k += 5) {
                assertFalse(id + " a run still covers the star", sl.starS >= sl.s[k] && sl.starS <= sl.s[k] + 6.4f);
            }
        });
    }

    @Test
    public void canCountSpacingAndPlace() {
        forAll((id, t, lv, sl) -> {
            float lap = t.lapLength();
            int cans = 0;
            float[] at = new float[8];
            for (int i = 0; i < sl.count; i++) {
                if (!sl.nitro[i]) continue;
                assertTrue(id + " can is the middle coin of its run", i % 5 == 2);
                at[cans++] = sl.s[i];
            }
            assertEquals(id + " cans per lap", lv.nitroPerLap, cans);
            for (int a = 0; a < cans; a++) {
                for (int b = a + 1; b < cans; b++) {
                    assertTrue(id + " cans " + at[a] + " and " + at[b] + " closer than 50 m", CoinPlanner.gap(at[a], at[b], lap) >= 50f);
                }
            }
        });
    }

    @Test
    public void everyMediumCanIsOnABendRunOrInTheCentreLane() {
        StringBuilder bad = new StringBuilder();
        for (TrackDef def : TrackDefs.ALL) {
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = TrackSpline.build(def, rev, RaceLevel.MEDIUM);
                Slots sl = t.coinSlots(SEED, RaceLevel.MEDIUM.nitroPerLap, 1);
                float limit = RaceLevel.MEDIUM.bendFrac * t.roadHalfWidth() + 1e-4f;
                for (int i = 0; i < sl.count; i++) {
                    if (sl.nitro[i] && Math.abs(sl.offset[i]) > limit) bad.append(TrackSpline.key(def, rev)).append(" can at s=").append(sl.s[i]).append(" offset ").append(sl.offset[i]).append("; ");
                }
            }
        }
        assertEquals("Medium cans in a side lane: " + bad, 0, bad.length());
    }

    @Test
    public void hardHasTwoCansOnBendsAndThreeInSideLanes() {
        for (TrackDef def : TrackDefs.ALL) {
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = TrackSpline.build(def, rev, RaceLevel.HARD);
                Slots sl = t.coinSlots(SEED, RaceLevel.HARD.nitroPerLap, 1);
                int onBend = 0, side = 0;
                for (int i = 0; i < sl.count; i++) {
                    if (!sl.nitro[i]) continue;
                    if (sl.corner[i]) onBend++;
                    else if (sl.offset[i] != 0f) side++;
                }
                String id = TrackSpline.key(def, rev);
                System.out.println("Hard " + id + ": cans on bends " + onBend + ", in side lanes " + side);
                assertEquals(id + " bend cans", 2, onBend);
                assertEquals(id + " side-lane cans", 3, side);
            }
        }
    }

    @Test
    public void oneMagnetPerLapOnAStraightRunWithoutACanAndAsFarFromCansAsItCan() {
        forAll((id, t, lv, sl) -> {
            float lap = t.lapLength();
            int mags = 0, at = -1;
            for (int i = 0; i < sl.count; i++) {
                if (!sl.magnet[i]) continue;
                mags++;
                at = i;
                assertTrue(id + " the middle coin of a run", i % 5 == 2);
                assertFalse(id + " not a corner coin", sl.corner[i]);
                for (int k = 0; k < sl.count; k++) if (k / 5 == i / 5) assertFalse(id + " no can in the magnet's run", sl.nitro[k]);
                assertTrue(id + " not in the first 60 m: " + sl.s[i], sl.s[i] >= 60f);
            }
            assertEquals(id + " magnets per lap", 1, mags);
            // distance from the magnet to its nearest can; when under 25 m no other candidate may be farther
            float mid = sl.s[at];
            float nearest = distToCans(sl, mid, lap);
            if (nearest < 25f) {
                for (int r = 0; r < sl.count / 5; r++) {
                    int c = r * 5 + 2;
                    boolean candidate = !sl.corner[c] && sl.s[c] >= 60f;
                    for (int k = r * 5; k < r * 5 + 5; k++) if (sl.nitro[k]) candidate = false;
                    if (candidate) assertTrue(id + " run at s=" + sl.s[c] + " is farther from the cans than the magnet",
                            distToCans(sl, sl.s[c], lap) <= nearest + 1e-3f);
                }
            }
        });
    }

    private static float distToCans(Slots sl, float s, float lap) {
        float best = Float.MAX_VALUE;
        for (int k = 0; k < sl.count; k++) if (sl.nitro[k]) best = Math.min(best, CoinPlanner.gap(s, sl.s[k], lap));
        return best;
    }

    @Test
    public void cansAndMagnetNeverMoveALane() {
        for (TrackDef def : TrackDefs.ALL) {
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = TrackSpline.build(def, rev, RaceLevel.HARD);
                Slots plain = t.coinSlots(SEED, 0, 0), full = t.coinSlots(SEED, 5, 1);
                assertEquals(plain.count, full.count);
                assertEquals(plain.starS, full.starS, 0f);
                for (int i = 0; i < plain.count; i++) {
                    assertEquals(plain.s[i], full.s[i], 0f);
                    assertEquals(plain.offset[i], full.offset[i], 0f);
                    assertFalse(plain.nitro[i] || plain.magnet[i]);
                }
            }
        }
    }

    @Test
    public void plansDifferBetweenTheTwoDirectionsAndSeedsButNotBetweenCalls() {
        for (TrackDef def : TrackDefs.ALL) {
            TrackSpline f = TrackSpline.build(def, false, RaceLevel.MEDIUM), r = TrackSpline.build(def, true, RaceLevel.MEDIUM);
            Slots a = f.coinSlots(SEED, 4, 1), b = r.coinSlots(SEED, 4, 1), a2 = f.coinSlots(SEED, 4, 1), c = f.coinSlots(SEED + 7919, 4, 1);
            boolean differs = a.count != b.count || a.starS != b.starS, seedDiffers = false;
            for (int i = 0; i < Math.min(a.count, b.count) && !differs; i++) {
                if (a.offset[i] != b.offset[i] || a.nitro[i] != b.nitro[i]) differs = true;
            }
            for (int i = 0; i < Math.min(a.count, c.count); i++) if (a.offset[i] != c.offset[i]) seedDiffers = true;
            assertTrue(def.id + " the two directions have different plans", differs);
            assertTrue(def.id + " another seed changes a lane", seedDiffers);
            assertEquals(a.count, a2.count);
            assertEquals(a.starS, a2.starS, 0f);
            for (int i = 0; i < a.count; i++) {
                assertEquals(a.s[i], a2.s[i], 0f);
                assertEquals(a.offset[i], a2.offset[i], 0f);
                assertEquals(a.nitro[i], a2.nitro[i]);
                assertEquals(a.magnet[i], a2.magnet[i]);
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void aSplineBuiltWithoutALevelCannotPlanCoins() {
        TrackSpline.build(TrackDefs.ALL[0], false, 5.5f).coinSlots(1L, 0, 0);
    }
}
