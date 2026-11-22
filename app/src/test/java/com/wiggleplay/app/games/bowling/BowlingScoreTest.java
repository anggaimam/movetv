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
    public void starsRiseWithPinsAndLevel() {
        assertEquals(1, BowlingScore.stars(0, 1));
        assertEquals(2, BowlingScore.stars(32, 1));
        assertEquals(3, BowlingScore.stars(42, 1));
        assertEquals(2, BowlingScore.stars(42, 3));   // Hard needs 50
        assertEquals(3, BowlingScore.stars(50, 3));
        assertEquals(2, BowlingScore.stars(35, 2));
    }
}
