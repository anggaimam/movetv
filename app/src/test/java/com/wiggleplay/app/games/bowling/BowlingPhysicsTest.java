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
    public void aWellAimedBallKnocksAlmostEverythingOnEasyAndMedium() {
        assertTrue(roll(1, 0f, 0f, 0.6f, 0f) >= 7);
        BowlingPhysics p = new BowlingPhysics(2, new java.util.Random(1));
        p.setPullSide(1);
        p.roll(-0.235f, 0f, 0.6f, 0f);      // starts on the far side so the planet bends it into the pocket
        assertTrue(p.runToEnd(10f) >= 7);
    }

    @Test
    public void strikesAreNotGivenAwayOnMediumAndHard() {
        // sweep the start across the middle of the lane at several powers: on Hard no better than half are strikes
        int hardStrikes = 0, tries = 0;
        for (float x = -0.12f; x <= 0.121f; x += 0.03f) {
            for (float pw = 0.3f; pw <= 1f; pw += 0.35f) {
                BowlingPhysics p = new BowlingPhysics(3, new java.util.Random(2));
                p.roll(x, 0f, pw, 0f);
                if (p.runToEnd(10f) == 10) hardStrikes++;
                tries++;
            }
        }
        assertTrue("hard strikes " + hardStrikes + "/" + tries, hardStrikes * 2 < tries);
    }

    @Test
    public void theMediumPlanetPullsTheBallTowardItsSide() {
        for (int side = -1; side <= 1; side += 2) {
            BowlingPhysics p = new BowlingPhysics(2, new java.util.Random(3));
            p.setPullSide(side);
            assertEquals(side, p.pullSide());
            p.roll(0f, 0f, 0.6f, 0f);
            while (p.ballOnLane() && p.ballY() < 15.5f) p.step();
            assertTrue("side " + side + " x " + p.ballX(), p.ballX() * side > 0.1f);
        }
    }

    @Test
    public void onlyMediumHasAPlanetAndOnlyHardHasAPlatform() {
        assertEquals(0, new BowlingPhysics(1).pullSide());
        assertEquals(0, new BowlingPhysics(3).pullSide());
        assertTrue(new BowlingPhysics(2).pullSide() != 0);
        BowlingPhysics easy = new BowlingPhysics(1), hard = new BowlingPhysics(3);
        easy.drift(1.5f);
        hard.drift(1.5f);
        assertEquals(0f, easy.platformX(), 0f);
        assertTrue(Math.abs(hard.platformX()) > 0.05f);
    }

    @Test
    public void thePlatformCarriesUntouchedPinsWithoutKnockingThemDown() {
        BowlingPhysics p = new BowlingPhysics(3, new java.util.Random(4));
        float x0 = p.pinX(0);
        for (int i = 0; i < 480; i++) p.drift(1f / 120f);     // four seconds of sliding
        assertTrue(Math.abs(p.pinX(0) - x0) > 0.05f);
        assertEquals(0, p.pinsDown());
        assertEquals(10, p.pinsUp());
        // the pins keep their shape as they slide
        assertEquals(p.pinX(1) - p.pinX(0), -0.115f, 0.001f);
    }

    @Test
    public void exactlyOnePinIsGoldenAndNeverTheHeadPin() {
        for (int seed = 0; seed < 40; seed++) {
            BowlingPhysics p = new BowlingPhysics(2, new java.util.Random(seed));
            assertTrue(p.goldenPin() >= 1 && p.goldenPin() <= 9);
            assertTrue(p.goldenStanding());
        }
    }

    @Test
    public void knockingTheGoldenPinDownPaysOnceAndOnlyOnTheFirstBall() {
        BowlingPhysics p = new BowlingPhysics(1, new java.util.Random(5));
        p.setGolden(4);
        p.roll(0f, 0f, 0.6f, 0f);
        assertTrue(p.runToEnd(10f) >= 8);
        assertTrue(p.takeGolden());
        assertFalse(p.takeGolden());       // paid once
        p.sweep();
        assertEquals(-1, p.goldenPin());   // no golden pin on the second ball
        assertFalse(p.takeGolden());
        p.resetPins();                     // a new frame has a new golden pin
        assertTrue(p.goldenPin() >= 1);
        assertFalse(p.takeGolden());
    }

    @Test
    public void aMissedGoldenPinPaysNothing() {
        BowlingPhysics p = new BowlingPhysics(2, new java.util.Random(6));
        p.setPullSide(-1);
        p.setGolden(3);
        p.roll(-0.4f, -0.12f, 0.6f, 0f);    // straight into the gutter
        p.runToEnd(10f);
        assertFalse(p.takeGolden());
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
