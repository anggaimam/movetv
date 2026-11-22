package com.wiggleplay.app.games.bowling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BowlingPhysicsTest {

    private static int roll(int level, float x, float angle, float power, float spin) {
        BowlingPhysics p = new BowlingPhysics(level);
        p.roll(x, angle, power, spin);
        return p.runToEnd(10f);
    }

    @Test
    public void tenPinsStandAtTheStart() {
        BowlingPhysics p = new BowlingPhysics(2);
        assertEquals(10, p.pinsUp());
        assertEquals(0, p.pinsDown());
        assertEquals(10, p.pinsOnLane());
    }

    @Test
    public void aStraightBallAtTheHeadPinKnocksSevenOrMore() {
        for (int level = 1; level <= 3; level++) {
            assertTrue("level " + level, roll(level, 0f, 0f, 0.6f, 0f) >= 7);
        }
    }

    @Test
    public void mediumAndHardHaveGutters() {
        for (int level = 2; level <= 3; level++) {
            BowlingPhysics p = new BowlingPhysics(level);
            p.roll(-0.4f, -0.12f, 0.6f, 0f);
            assertEquals(0, p.runToEnd(10f));
            assertTrue(p.inGutter());
        }
    }

    @Test
    public void easyHasBumpersAndNoGutter() {
        BowlingPhysics p = new BowlingPhysics(1);
        assertTrue(p.hasBumpers());
        p.roll(0.3f, 0.12f, 0.7f, 0f);
        p.runToEnd(10f);
        assertFalse(p.inGutter());
        assertTrue(p.pinsDown() > 0);
    }

    @Test
    public void theSameRollGivesTheSameResult() {
        BowlingPhysics a = new BowlingPhysics(2), b = new BowlingPhysics(2);
        a.roll(0.1f, 0f, 0.8f, 0.3f);
        b.roll(0.1f, 0f, 0.8f, 0.3f);
        assertEquals(a.runToEnd(10f), b.runToEnd(10f));
        for (int i = 0; i < BowlingPhysics.PINS; i++) {
            assertEquals(a.pinX(i), b.pinX(i), 0f);
            assertEquals(a.pinY(i), b.pinY(i), 0f);
        }
    }

    @Test
    public void spinCurvesTheBall() {
        BowlingPhysics a = new BowlingPhysics(2), b = new BowlingPhysics(2);
        a.roll(0f, 0f, 0.7f, 0f);
        b.roll(0f, 0f, 0.7f, 1f);
        for (int i = 0; i < 120; i++) {
            a.step();
            b.step();
        }
        assertTrue(b.ballX() > a.ballX() + 0.05f);
    }

    @Test
    public void harderThrowsAreFaster() {
        BowlingPhysics slow = new BowlingPhysics(2), fast = new BowlingPhysics(2);
        slow.roll(0f, 0f, 0f, 0f);
        fast.roll(0f, 0f, 1f, 0f);
        for (int i = 0; i < 60; i++) {
            slow.step();
            fast.step();
        }
        assertTrue(fast.ballY() > slow.ballY() * 1.5f);
    }

    @Test
    public void everyRollEndsAndTheSecondRollHasOnlyTheStandingPins() {
        BowlingPhysics p = new BowlingPhysics(2);
        p.roll(0.15f, 0f, 0.6f, 0f);
        int first = p.runToEnd(10f);
        assertTrue(p.settled());
        p.sweep();
        assertEquals(10 - first, p.pinsOnLane());
        assertEquals(0, p.pinsDown());
    }
}
