package com.wiggleplay.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QualityGuardTest {

    /** Feeds frames of the same length for the given seconds; returns true if the guard dropped to LOW at any frame. */
    private static boolean feed(QualityGuard g, float frameMs, float seconds) {
        boolean dropped = false;
        int n = Math.round(seconds * 1000f / frameMs);
        for (int i = 0; i < n; i++) if (g.onFrame(frameMs)) dropped = true;
        return dropped;
    }

    @Test
    public void aLoadHitchAtSceneStartDoesNotSwitch() {
        QualityGuard g = new QualityGuard();
        g.onSceneChange();
        assertFalse(g.onFrame(600f));
        assertFalse(feed(g, 20f, 10f)); // 50 fps afterwards
        assertEquals(QualityGuard.HIGH, g.level());
    }

    @Test
    public void slowFramesInTheGraceTimeDoNotCount() {
        QualityGuard g = new QualityGuard();
        g.onSceneChange();
        assertFalse(feed(g, 40f, 2.9f)); // 25 fps, but still loading
        assertFalse(feed(g, 20f, 10f));
        assertEquals(QualityGuard.HIGH, g.level());
    }

    @Test
    public void threeSecondsOfSteady40FpsAfterTheGraceTimeSwitches() {
        QualityGuard g = new QualityGuard();
        g.onSceneChange();
        assertFalse(feed(g, 25f, 3f));
        assertFalse(feed(g, 25f, 2.5f));
        assertTrue(feed(g, 25f, 1f));
        assertEquals(QualityGuard.LOW, g.level());
        assertTrue(g.droppedAtSeconds() >= 6 && g.droppedAtSeconds() <= 7);
    }

    @Test
    public void steady50FpsNeverSwitches() {
        QualityGuard g = new QualityGuard();
        g.onSceneChange();
        assertFalse(feed(g, 20f, 60f));
        assertEquals(QualityGuard.HIGH, g.level());
    }

    @Test
    public void aHitchInsideSlowPlayRestartsTheWindow() {
        QualityGuard g = new QualityGuard();
        g.onSceneChange();
        assertFalse(feed(g, 25f, 5f));   // grace, then 2 s of the window
        assertFalse(g.onFrame(300f));    // a hitch: the window starts again
        assertFalse(feed(g, 25f, 2.8f)); // would have switched without the hitch
        assertTrue(feed(g, 25f, 0.4f));  // 3 s since the hitch
        assertEquals(QualityGuard.LOW, g.level());
    }

    @Test
    public void aSceneChangeStartsTheGraceTimeAgainAndForgetsTheNote() {
        QualityGuard g = new QualityGuard();
        g.onSceneChange();
        feed(g, 25f, 7f);
        assertEquals(QualityGuard.LOW, g.level());
        assertTrue(g.droppedAtSeconds() >= 0);
        g.onSceneChange();
        assertEquals(-1, g.droppedAtSeconds());
        assertEquals(QualityGuard.LOW, g.level()); // LOW stays until the app restarts
        assertEquals(0f, g.sceneSeconds(), 0f);
    }

    @Test
    public void highIsFixedWhateverTheSpeed() {
        QualityGuard g = new QualityGuard();
        g.setMode(QualityGuard.FORCE_HIGH);
        g.onSceneChange();
        assertFalse(feed(g, 50f, 30f)); // 20 fps for 30 s
        assertEquals(QualityGuard.HIGH, g.level());
    }

    @Test
    public void lowIsFixedWhateverTheSpeed() {
        QualityGuard g = new QualityGuard();
        g.setMode(QualityGuard.FORCE_LOW);
        g.onSceneChange();
        assertEquals(QualityGuard.LOW, g.level());
        assertFalse(feed(g, 10f, 30f));
        assertEquals(QualityGuard.LOW, g.level());
    }

    @Test
    public void changingTheModeForgetsAnEarlierAutomaticDrop() {
        QualityGuard g = new QualityGuard();
        g.onSceneChange();
        feed(g, 25f, 7f);
        assertEquals(QualityGuard.LOW, g.level());
        g.setMode(QualityGuard.FORCE_HIGH);
        assertEquals(QualityGuard.HIGH, g.level());
        g.setMode(QualityGuard.AUTO);
        assertEquals(QualityGuard.HIGH, g.level());
    }

    @Test
    public void aPauseFrameIsIgnoredAndTheDropHappensOnlyOnce() {
        QualityGuard g = new QualityGuard();
        g.onSceneChange();
        assertFalse(g.onFrame(0f));
        assertTrue(feed(g, 25f, 7f));
        assertFalse(feed(g, 25f, 7f));
    }

    @Test
    public void savedValuesBecomeModes() {
        assertEquals(QualityGuard.AUTO, QualityGuard.modeOf(null));
        assertEquals(QualityGuard.AUTO, QualityGuard.modeOf("auto"));
        assertEquals(QualityGuard.FORCE_HIGH, QualityGuard.modeOf("high"));
        assertEquals(QualityGuard.FORCE_LOW, QualityGuard.modeOf("low"));
        assertEquals(QualityGuard.AUTO, QualityGuard.modeOf("x"));
    }
}
