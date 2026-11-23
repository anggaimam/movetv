package com.wiggleplay.app.games.bowling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BowlingScoreTest {

    @Test
    public void aStrikeEndsTheFrameAndGivesTenBonus() {
        BowlingScore s = new BowlingScore(1);
        assertEquals(BowlingScore.STRIKE, s.record(10));
        assertEquals(1, s.frame());
        assertEquals(0, s.roll());
        assertEquals(20, s.total(0));
    }

    @Test
    public void aSpareGivesFiveBonus() {
        BowlingScore s = new BowlingScore(1);
        assertEquals(BowlingScore.NORMAL, s.record(4));
        assertEquals(6, s.pinsUpNow());
        assertEquals(BowlingScore.SPARE, s.record(6));
        assertEquals(15, s.total(0));
    }

    @Test
    public void nothingKnockedDownIsAGutter() {
        BowlingScore s = new BowlingScore(1);
        assertEquals(BowlingScore.GUTTER, s.record(0));
        assertEquals(BowlingScore.GUTTER, s.record(0));
        assertEquals(0, s.total(0));
    }

    @Test
    public void bowlersTakeTurnsFrameByFrame() {
        BowlingScore s = new BowlingScore(3);
        s.record(10);                       // player 0 strike
        assertEquals(1, s.player());
        s.record(3);
        assertEquals(1, s.player());
        s.record(2);
        assertEquals(2, s.player());
        s.record(10);
        assertEquals(0, s.player());
        assertEquals(1, s.frame());
    }

    @Test
    public void theGameEndsAfterFiveFramesForEveryone() {
        BowlingScore s = new BowlingScore(4);
        int rolls = 0;
        while (!s.finished()) {
            s.record(3);
            rolls++;
            assertTrue(rolls < 100);
        }
        assertEquals(4 * BowlingScore.FRAMES * 2, rolls);
        assertEquals(30, s.total(2));
        assertEquals(BowlingScore.FRAMES, s.frame());
    }

    @Test
    public void pinsAreClampedToWhatStands() {
        BowlingScore s = new BowlingScore(1);
        s.record(7);
        s.record(9);      // only 3 stood
        assertEquals(10, s.framePins(0, 0));
        assertEquals(BowlingScore.SPARE, s.frameKind(0, 0));
    }

    @Test
    public void scoresAreNeverNegativeAndTheLeaderWins() {
        BowlingScore s = new BowlingScore(2);
        s.record(0);
        s.record(0);
        s.record(5);
        s.record(4);
        assertTrue(s.total(0) >= 0);
        assertEquals(1, s.leader());
        assertFalse(s.finished());
    }

    @Test
    public void starsRiseWithPointsAndLevel() {
        assertEquals(1, BowlingScore.stars(0, 1));
        assertEquals(1, BowlingScore.stars(84, 1));
        assertEquals(2, BowlingScore.stars(85, 1));
        assertEquals(3, BowlingScore.stars(110, 1));
        assertEquals(2, BowlingScore.stars(103, 2));   // Medium needs 104
        assertEquals(3, BowlingScore.stars(104, 2));
        assertEquals(1, BowlingScore.stars(69, 3));
        assertEquals(2, BowlingScore.stars(91, 3));    // Hard needs 92
        assertEquals(3, BowlingScore.stars(92, 3));
    }

    @Test
    public void theGoldenPinAddsFivePointsToItsFrame() {
        BowlingScore s = new BowlingScore(2);
        s.addGold();            // player 0, frame 0
        s.record(7);
        s.record(2);            // 9 pins + 5
        assertTrue(s.frameGold(0, 0));
        assertFalse(s.frameGold(1, 0));
        assertEquals(14, s.framePoints(0, 0));
        assertEquals(14, s.total(0));
        s.addGold();            // player 1 strikes and hits gold: 10 + 10 + 5
        s.record(10);
        assertEquals(25, s.framePoints(1, 0));
        assertEquals(9, s.framePins(0, 0));
    }
}
