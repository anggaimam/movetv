package com.angga.movetv.games.fruit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FruitLogicTest {

    private static final class Counter implements FruitLogic.Listener {
        int slices, coconuts, misses, combos, points, lost;
        float maxR;

        @Override
        public void onSlice(FruitLogic.Fruit f, float dx, float dy, int pts, boolean combo) {
            slices++;
            points += pts;
            if (combo) combos++;
        }

        @Override
        public void onCoconut(FruitLogic.Fruit f, float dx, float dy, int l) { coconuts++; lost += l; }

        @Override
        public void onMiss(FruitLogic.Fruit f) { misses++; }
    }

    private static final HandTrack[] NONE = new HandTrack[] {null};

    @Test
    public void starsUseTheFairShareOfWhatWasOffered() {
        assertEquals(1, FruitLogic.stars(0, 60));
        assertEquals(1, FruitLogic.stars(23, 60));
        assertEquals(2, FruitLogic.stars(24, 60));
        assertEquals(2, FruitLogic.stars(41, 60));
        assertEquals(3, FruitLogic.stars(42, 60));
    }

    @Test
    public void offeredCountsEveryItemLaunchedExceptTheCoconut() {
        for (int level = 1; level <= 2; level++) {
            Counter cn = new Counter();
            FruitLogic l = new FruitLogic(level, 1, 31L + level, 0f, 1920f, cn);
            long now = 0;
            // slice every fruit that is not a coconut for 60 seconds, then let the field empty
            for (int i = 0; i < 60 * 60 || l.activeCount() > 0; i++) {
                l.update(0.016f, now, NONE, i < 60 * 60);
                now += 16;
                for (int k = 0; k < l.activeCount(); k++) {
                    if (l.at(k).kind != FruitLogic.COCONUT) {
                        l.forceSlice(k, now);
                        break;
                    }
                }
                assertTrue(i < 200000);
            }
            assertTrue(l.offered() > 0);
            assertEquals("level " + level, l.offered(), l.score());
            assertEquals(3, FruitLogic.stars(l.score(), l.offered()));
        }
    }

    @Test
    public void aPlayerWhoSlicesNothingStillGetsOneStar() {
        FruitLogic l = new FruitLogic(1, 1, 5L, 0f, 1920f, new Counter());
        long now = 0;
        for (int i = 0; i < 60 * 40; i++) {
            l.update(0.016f, now, NONE, true);
            now += 16;
        }
        assertTrue(l.offered() > 0);
        assertEquals(0, l.score());
        assertEquals(1, FruitLogic.stars(l.score(), l.offered()));
    }

    @Test
    public void levelParameters() {
        FruitLogic.Params e = FruitLogic.params(1, 1);
        assertEquals(1, e.maxAtOnce);
        assertEquals(90f, e.radius, 0.01f);
        assertEquals(0f, e.coconut, 0f);
        assertEquals(75f, FruitLogic.params(2, 1).radius, 0.01f);
        assertEquals(0, FruitLogic.params(2, 1).coconutPenalty);
        assertEquals(55f, FruitLogic.params(3, 1).radius, 0.01f);
        assertEquals(3, FruitLogic.params(3, 1).coconutPenalty);
        FruitLogic.Params h = FruitLogic.params(3, 1);
        assertEquals(5, h.maxAtOnce);
        assertTrue(h.combo);
        FruitLogic.Params two = FruitLogic.params(1, 2);
        assertEquals(112.5f, two.radius, 0.01f);
        assertTrue(two.rise > e.rise);
    }

    @Test
    public void sameSeedSameGame() {
        FruitLogic a = new FruitLogic(2, 1, 5L, 0f, 1920f, new Counter());
        FruitLogic b = new FruitLogic(2, 1, 5L, 0f, 1920f, new Counter());
        for (int i = 0; i < 300; i++) {
            a.update(0.016f, i * 16L, NONE, true);
            b.update(0.016f, i * 16L, NONE, true);
        }
        assertEquals(a.activeCount(), b.activeCount());
        assertTrue(a.activeCount() > 0);
        for (int i = 0; i < a.activeCount(); i++) {
            assertEquals(a.at(i).x, b.at(i).x, 0.001f);
            assertEquals(a.at(i).kind, b.at(i).kind);
        }
    }

    @Test
    public void easyKeepsOneFruitInTheAir() {
        FruitLogic l = new FruitLogic(1, 1, 3L, 0f, 1920f, new Counter());
        int most = 0;
        for (int i = 0; i < 2000; i++) {
            l.update(0.016f, i * 16L, NONE, true);
            most = Math.max(most, l.activeCount());
        }
        assertEquals(1, most);
    }

    @Test
    public void fruitStaysInsideItsRegion() {
        FruitLogic l = new FruitLogic(1, 2, 9L, 960f, 1920f, new Counter());
        for (int i = 0; i < 3000; i++) {
            l.update(0.016f, i * 16L, NONE, true);
            for (int k = 0; k < l.activeCount(); k++) assertTrue(l.at(k).x >= 960f && l.at(k).x <= 1920f);
        }
    }

    @Test
    public void slicingScoresOnePoint() {
        Counter cn = new Counter();
        FruitLogic l = new FruitLogic(1, 1, 4L, 0f, 1920f, cn);
        HandTrack ht = new HandTrack(0f);
        long now = 0;
        while (l.activeCount() == 0) {
            l.update(0.016f, now, NONE, true);
            now += 16;
        }
        FruitLogic.Fruit f = l.at(0);
        ht.update(0.016f, true, f.x - 150f, f.y, now);
        ht.update(0.016f, true, f.x + 150f, f.y, now + 16);
        l.update(0.016f, now + 16, new HandTrack[] {ht}, true);
        assertEquals(1, cn.slices);
        assertEquals(1, l.score());
        assertEquals(0, l.activeCount());
    }

    @Test
    public void missedFruitCostsNothingAndHelpsAfterFour() {
        Counter cn = new Counter();
        FruitLogic l = new FruitLogic(1, 1, 8L, 0f, 1920f, cn);
        float biggest = 0f;
        for (int i = 0; i < 3000; i++) {
            l.update(0.016f, i * 16L, NONE, true);
            for (int k = 0; k < l.activeCount(); k++) biggest = Math.max(biggest, l.at(k).r);
        }
        assertEquals(0, l.score());
        assertTrue(cn.misses >= 4);
        assertEquals(108f, biggest, 0.5f);
    }

    @Test
    public void handFollowsSweepAcrossFruit() {
        HandTrack ht = new HandTrack(0f);
        ht.update(0.016f, true, 0f, 500f, 0);
        ht.update(0.016f, true, 1000f, 500f, 16);
        assertTrue(ht.hits(500f, 540f, 90f, 16, 90f));
        assertFalse(ht.hits(500f, 900f, 90f, 16, 90f));
    }

    @Test
    public void handRemembersRecentPositions() {
        HandTrack ht = new HandTrack(0f);
        ht.update(0.016f, true, 500f, 500f, 0);
        ht.update(0.016f, true, 900f, 100f, 100);
        ht.update(0.016f, true, 1000f, 100f, 200);
        assertTrue(ht.hits(500f, 500f, 60f, 200, 90f));
        assertFalse(ht.hits(500f, 500f, 60f, 400, 90f));
    }

    @Test
    public void coconutScoresNothingAndPointsAddUp() {
        Counter cn = new Counter();
        FruitLogic l = new FruitLogic(3, 1, 11L, 0f, 1920f, cn);
        HandTrack ht = new HandTrack(0f);
        long now = 0;
        for (int i = 0; i < 8000; i++) {
            now = i * 16L;
            l.update(0.016f, now, NONE, true);
            if (l.activeCount() > 0) {
                FruitLogic.Fruit f = l.at(0);
                ht.update(0.016f, true, f.x + (i % 2 == 0 ? -200f : 200f), f.y, now);
                l.update(0.0f, now, new HandTrack[] {ht}, false);
            }
        }
        assertTrue(cn.coconuts > 0);
        assertTrue(cn.slices > 20);
        assertTrue(cn.lost > 0);
        assertEquals(cn.points - cn.lost, l.score());
    }

    @Test
    public void threeSlicesInOneSecondMakeACombo() {
        Counter cn = new Counter();
        FruitLogic l = new FruitLogic(3, 1, 13L, 0f, 1920f, cn);
        long now = 0;
        int guard = 0;
        while (guard++ < 5000) {
            l.update(0.016f, now, NONE, true);
            now += 16;
            int good = 0;
            for (int i = 0; i < l.activeCount(); i++) if (l.at(i).kind != FruitLogic.COCONUT) good++;
            if (good >= 3) break;
        }
        for (int s = 0; s < 3; s++) {
            for (int i = 0; i < l.activeCount(); i++) {
                if (l.at(i).kind != FruitLogic.COCONUT) {
                    l.forceSlice(i, now + s * 200L);
                    break;
                }
            }
        }
        assertEquals(1, cn.combos);
        assertEquals(cn.points - cn.lost, l.score());
        assertTrue(l.score() >= 6 - cn.lost);
    }

    @Test
    public void aRestingHandNeverSlices() {
        Counter cn = new Counter();
        FruitLogic l = new FruitLogic(2, 1, 21L, 0f, 1920f, cn);
        HandTrack ht = new HandTrack(14f);
        for (int i = 0; i < 4000; i++) {
            long now = i * 16L;
            ht.update(0.016f, true, 960f + (i % 2) * 2f, 540f, now); // only pose jitter
            l.update(0.016f, now, new HandTrack[] {ht}, true);
        }
        assertEquals(0, cn.slices);
        assertEquals(0, cn.coconuts);
    }

    @Test
    public void movingHandIsMoving() {
        HandTrack ht = new HandTrack(0f);
        ht.update(0.016f, true, 100f, 100f, 0);
        ht.update(0.016f, true, 130f, 100f, 16);
        ht.update(0.016f, true, 160f, 100f, 32);
        ht.update(0.016f, true, 190f, 100f, 48);
        assertTrue(ht.moving(48, 90f));
        assertFalse(ht.moving(48, 200f));
        assertFalse(ht.moving(600, 90f));
    }

    @Test
    public void hardCoconutTakesPointsButNeverBelowZero() {
        Counter cn = new Counter();
        FruitLogic l = new FruitLogic(3, 1, 17L, 0f, 1920f, cn);
        long now = 0;
        int guard = 0;
        // slice a few good fruit, then a coconut
        while (guard++ < 20000 && !(l.score() >= 4 && cn.lost > 0)) {
            l.update(0.016f, now, NONE, true);
            now += 16;
            for (int i = 0; i < l.activeCount(); i++) {
                FruitLogic.Fruit f = l.at(i);
                if ((f.kind != FruitLogic.COCONUT) == (cn.slices < 5 || l.score() < 4)) {
                    l.forceSlice(i, now);
                    break;
                }
            }
        }
        assertTrue(cn.lost > 0);
        assertTrue(l.score() >= 0);
        assertEquals(cn.points - cn.lost, l.score());
    }

    @Test
    public void mediumCoconutOnlySaysOops() {
        Counter cn = new Counter();
        FruitLogic l = new FruitLogic(2, 1, 19L, 0f, 1920f, cn);
        long now = 0;
        int guard = 0;
        while (guard++ < 20000 && cn.coconuts == 0) {
            l.update(0.016f, now, NONE, true);
            now += 16;
            for (int i = 0; i < l.activeCount(); i++) {
                if (l.at(i).kind == FruitLogic.COCONUT) {
                    l.forceSlice(i, now);
                    break;
                }
            }
        }
        assertTrue(cn.coconuts > 0);
        assertEquals(0, cn.lost);
    }

    @Test
    public void hardFruitStaysInsideItsRegionToo() {
        FruitLogic l = new FruitLogic(3, 2, 23L, 0f, 960f, new Counter());
        for (int i = 0; i < 5000; i++) {
            l.update(0.016f, i * 16L, NONE, true);
            for (int k = 0; k < l.activeCount(); k++) assertTrue(l.at(k).x >= 0f && l.at(k).x <= 960f);
        }
    }

    @Test
    public void fruitComesInDifferentSizesOfFlight() {
        FruitLogic l = new FruitLogic(2, 1, 31L, 0f, 1920f, new Counter());
        float lo = 1e9f, hi = 0f;
        for (int i = 0; i < 4000; i++) {
            l.update(0.016f, i * 16L, NONE, true);
            for (int k = 0; k < l.activeCount(); k++) {
                lo = Math.min(lo, l.at(k).apex);
                hi = Math.max(hi, l.at(k).apex);
            }
        }
        assertTrue(hi - lo > 200f);
    }
}
