package com.angga.movetv.games.fruit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FruitLogicTest {

    private static final class Counter implements FruitLogic.Listener {
        int slices, coconuts, misses, combos, points;
        float maxR;

        @Override
        public void onSlice(FruitLogic.Fruit f, float dx, float dy, int pts, boolean combo) {
            slices++;
            points += pts;
            if (combo) combos++;
        }

        @Override
        public void onCoconut(FruitLogic.Fruit f, float dx, float dy) { coconuts++; }

        @Override
        public void onMiss(FruitLogic.Fruit f) { misses++; }
    }

    private static final HandTrack[] NONE = new HandTrack[] {null};

    @Test
    public void starsThresholds() {
        assertEquals(1, FruitLogic.stars(1, 0));
        assertEquals(1, FruitLogic.stars(1, 24));
        assertEquals(2, FruitLogic.stars(1, 25));
        assertEquals(3, FruitLogic.stars(1, 45));
        assertEquals(1, FruitLogic.stars(2, 37));
        assertEquals(2, FruitLogic.stars(2, 38));
        assertEquals(2, FruitLogic.stars(2, 67));
        assertEquals(3, FruitLogic.stars(2, 68));
        assertEquals(2, FruitLogic.stars(3, 50));
        assertEquals(2, FruitLogic.stars(3, 89));
        assertEquals(3, FruitLogic.stars(3, 90));
    }

    @Test
    public void levelParameters() {
        FruitLogic.Params e = FruitLogic.params(1, 1);
        assertEquals(1, e.maxAtOnce);
        assertEquals(90f, e.radius, 0.01f);
        assertEquals(0f, e.coconut, 0f);
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
        ht.update(0.016f, true, f.x, f.y, now);
        ht.update(0.016f, true, f.x, f.y, now + 16);
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
        assertTrue(ht.hits(500f, 540f, 90f, 16));
        assertFalse(ht.hits(500f, 900f, 90f, 16));
    }

    @Test
    public void handRemembersRecentPositions() {
        HandTrack ht = new HandTrack(0f);
        ht.update(0.016f, true, 500f, 500f, 0);
        ht.update(0.016f, true, 900f, 100f, 100);
        ht.update(0.016f, true, 1000f, 100f, 200);
        assertTrue(ht.hits(500f, 500f, 60f, 200));
        assertFalse(ht.hits(500f, 500f, 60f, 400));
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
                ht.update(0.016f, true, f.x, f.y, now);
                l.update(0.0f, now, new HandTrack[] {ht}, false);
            }
        }
        assertTrue(cn.coconuts > 0);
        assertTrue(cn.slices > 20);
        assertEquals(cn.points, l.score());
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
        assertEquals(cn.points, l.score());
        assertTrue(l.score() >= 6);
    }
}
