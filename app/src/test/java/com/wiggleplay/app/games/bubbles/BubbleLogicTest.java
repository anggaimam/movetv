package com.wiggleplay.app.games.bubbles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.games.fruit.HandTrack;

import org.junit.Test;

public class BubbleLogicTest {

    private static final class Log implements BubbleLogic.Listener {
        int pops, wrong, decoys, fades, points, lost;

        @Override public void onPop(BubbleLogic.Bubble b, int p) { pops++; points += p; }
        @Override public void onWrongHand(BubbleLogic.Bubble b) { wrong++; }
        @Override public void onDecoy(BubbleLogic.Bubble b, int l) { decoys++; lost += l; }
        @Override public void onFade(BubbleLogic.Bubble b) { fades++; }
    }

    private long now;
    private final HandTrack left = new HandTrack(0f), right = new HandTrack(0f);

    private void far() {
        left.update(0.016f, true, 5f, 5f, now);
        right.update(0.016f, true, 5f, 5f, now);
    }

    private void step(BubbleLogic l, int frames) {
        for (int i = 0; i < frames; i++) {
            now += 16;
            l.update(0.016f, now, left, right, true);
        }
    }

    /** Runs until a bubble of the wanted kind is on screen (a fresh logic each time is the caller's job). */
    private BubbleLogic.Bubble waitFor(BubbleLogic l, int kind, int maxFrames) {
        for (int i = 0; i < maxFrames; i++) {
            far();
            step(l, 1);
            for (int k = 0; k < l.activeCount(); k++) {
                BubbleLogic.Bubble b = l.at(k);
                if (b.kind == kind && b.age > 0.2f) return b;
            }
        }
        return null;
    }

    @Test
    public void easyShowsOneBubbleInsideTheReachAndAwayFromTheFace() {
        for (int seed = 0; seed < 40; seed++) {
            Log log = new Log();
            BubbleLogic l = new BubbleLogic(1, seed, 0f, 1920f, log);
            l.setReach(960f, 560f, 200f, 960f, 300f);
            far();
            step(l, 60);
            assertTrue(l.activeCount() <= 1);
            for (int k = 0; k < l.activeCount(); k++) {
                BubbleLogic.Bubble b = l.at(k);
                assertTrue(Math.hypot(b.x - 960f, b.y - 560f) <= 560f + 1f || b.x <= 120.5f || b.x >= 1799.5f || b.y >= 879f || b.y <= 231f);
                assertTrue(Math.hypot(b.x - 960f, b.y - 300f) >= 0.6f * 200f + 85f - 1f);
            }
        }
    }

    @Test
    public void aHandOnABubblePopsIt() {
        Log log = new Log();
        BubbleLogic l = new BubbleLogic(1, 3, 0f, 1920f, log);
        BubbleLogic.Bubble b = waitFor(l, BubbleLogic.ANY, 600);
        assertTrue(b != null);
        right.update(0.016f, true, b.x, b.y, now);
        step(l, 2);
        assertEquals(1, log.pops);
        assertEquals(1, l.score());
    }

    @Test
    public void aBubbleThatFadesCostsNothing() {
        Log log = new Log();
        BubbleLogic l = new BubbleLogic(1, 5, 0f, 1920f, log);
        far();
        step(l, 400);
        assertTrue(log.fades >= 1);
        assertEquals(0, l.score());
        assertEquals(0, log.pops);
    }

    @Test
    public void blueIsForTheLeftHandAndRedForTheRight() {
        Log log = new Log();
        BubbleLogic l = new BubbleLogic(2, 11, 0f, 1920f, log);
        BubbleLogic.Bubble b = waitFor(l, BubbleLogic.BLUE, 3000);
        assertTrue(b != null);
        right.update(0.016f, true, b.x, b.y, now);
        step(l, 2);
        assertEquals(0, log.pops);
        assertTrue(log.wrong >= 1);
        far();
        step(l, 30);
        left.update(0.016f, true, b.x, b.y, now);
        step(l, 2);
        assertEquals(1, log.pops);
    }

    @Test
    public void twoHandBubblesNeedBothHands() {
        Log log = new Log();
        BubbleLogic l = new BubbleLogic(3, 21, 0f, 1920f, log);
        BubbleLogic.Bubble b = waitFor(l, BubbleLogic.BOTH, 6000);
        assertTrue(b != null);
        left.update(0.016f, true, b.x, b.y, now);
        step(l, 2);
        assertEquals(0, log.pops);
        right.update(0.016f, true, b.x + 10f, b.y, now);
        step(l, 2);
        assertEquals(3, log.points);
    }

    @Test
    public void aDecoyTakesPointsAwayButNeverBelowZero() {
        for (int lv = 2; lv <= 3; lv++) {
            Log log = new Log();
            BubbleLogic l = new BubbleLogic(lv, 33, 0f, 1920f, log);
            for (int i = 0; i < 6000 && l.score() < 3; i++) {
                now += 16;
                far();
                for (int k = 0; k < l.activeCount(); k++) {
                    BubbleLogic.Bubble b = l.at(k);
                    if (b.kind == BubbleLogic.DECOY || b.age < 0.4f) continue;
                    left.update(0.016f, true, b.x, b.y, now);
                    right.update(0.016f, true, b.x, b.y, now);
                    break;
                }
                l.update(0.016f, now, left, right, true);
            }
            assertTrue(l.score() >= 3);
            far();
            step(l, 40);
            BubbleLogic.Bubble d = waitFor(l, BubbleLogic.DECOY, 6000);
            assertTrue("a decoy in level " + lv, d != null);
            int before = l.score();
            left.update(0.016f, true, d.x, d.y, now);
            step(l, 2);
            assertEquals(1, log.decoys);
            assertEquals(l.penalty(), log.lost);
            assertEquals(before - l.penalty(), l.score());
        }
        Log log = new Log();
        BubbleLogic l = new BubbleLogic(3, 5, 0f, 1920f, log);
        BubbleLogic.Bubble d = waitFor(l, BubbleLogic.DECOY, 6000);
        assertTrue(d != null);
        left.update(0.016f, true, d.x, d.y, now);
        step(l, 2);
        assertEquals(0, l.score());
        assertEquals(0, log.lost);
    }

    @Test
    public void penaltyIsOneOnMediumAndTwoOnHard() {
        assertEquals(1, new BubbleLogic(2, 1, 0f, 1920f, new Log()).penalty());
        assertEquals(2, new BubbleLogic(3, 1, 0f, 1920f, new Log()).penalty());
    }

    @Test
    public void inTwoPlayerModeBubblesStayInTheirOwnHalf() {
        for (int seed = 0; seed < 20; seed++) {
            BubbleLogic p1 = new BubbleLogic(3, seed, 960f, 1920f, new Log());
            p1.setReach(500f, 560f, 300f, Float.NaN, 0f); // reach reaches over to the other half
            far();
            for (int i = 0; i < 300; i++) {
                far();
                step(p1, 1);
                for (int k = 0; k < p1.activeCount(); k++) assertTrue(p1.at(k).x >= 960f + 119f);
            }
        }
    }

    @Test
    public void levelsShowMoreBubblesAndNeverMoreThanThree() {
        int[] max = new int[4];
        for (int lv = 1; lv <= 3; lv++) {
            BubbleLogic l = new BubbleLogic(lv, 99, 0f, 1920f, new Log());
            for (int i = 0; i < 1500; i++) {
                far();
                step(l, 1);
                max[lv] = Math.max(max[lv], l.activeCount());
            }
        }
        assertEquals(1, max[1]);
        assertEquals(2, max[2]);
        assertEquals(3, max[3]);
    }

    @Test
    public void starsFollowTheShareOfPointsOffered() {
        assertEquals(1, BubbleLogic.stars(0, 0));
        assertEquals(1, BubbleLogic.stars(10, 100));
        assertEquals(2, BubbleLogic.stars(40, 100));
        assertEquals(2, BubbleLogic.stars(69, 100));
        assertEquals(3, BubbleLogic.stars(70, 100));
        assertFalse(BubbleLogic.stars(100, 100) < 3);
    }

    @Test
    public void aWholeRoundOfPerfectPlayEarnsThreeStars() {
        for (int lv = 1; lv <= 3; lv++) {
            Log log = new Log();
            BubbleLogic l = new BubbleLogic(lv, 7 + lv, 0f, 1920f, log);
            for (int i = 0; i < 3750; i++) { // 60 s
                now += 16;
                far();
                // a perfect child: both hands on the first poppable bubble, decoys left alone
                for (int k = 0; k < l.activeCount(); k++) {
                    BubbleLogic.Bubble b = l.at(k);
                    if (b.kind == BubbleLogic.DECOY || b.age < 0.4f) continue;
                    left.update(0.016f, true, b.x, b.y, now);
                    right.update(0.016f, true, b.x, b.y, now);
                    break;
                }
                l.update(0.016f, now, left, right, true);
            }
            assertTrue("level " + lv, BubbleLogic.stars(l.score(), l.offered()) == 3);
        }
    }
}
