package com.angga.movetv.input;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DwellButtonsTest {
    static final float DT = 1f / 60f;

    /** A cursor placed by hand. */
    static class Fake extends HandCursor {
        boolean vis = true;
        float fx, fy, fs;

        Fake(int id, float x, float y) {
            super(id);
            fx = x;
            fy = y;
        }

        @Override public boolean visible() { return vis; }
        @Override public float x() { return fx; }
        @Override public float y() { return fy; }
        @Override public float speed() { return fs; }
    }

    static class Sim {
        final DwellButtons d = new DwellButtons();
        final List<DwellEvent> events = new ArrayList<>();
        final List<Long> selectTimes = new ArrayList<>();
        long frame;

        long nowMs() { return Math.round(frame * 1000.0 / 60.0); }

        void step(HandCursor... cs) {
            frame++;
            events.clear();
            d.update(DT, cs, nowMs(), events);
            for (DwellEvent e : events) if (e.type == DwellEvent.SELECT) selectTimes.add(nowMs());
        }

        void run(int ms, HandCursor... cs) {
            int n = Math.round(ms / 1000f * 60f);
            for (int i = 0; i < n; i++) step(cs);
        }
    }

    static List<Target> one() {
        List<Target> l = new ArrayList<>();
        l.add(new Target("play", 760, 390, 400, 300));
        return l;
    }

    @Test
    public void exactlyOneSelectAt1150msAndNoneInNext600() {
        Sim s = new Sim();
        s.d.setTargets(one());
        Fake c = new Fake(0, 960, 540);
        int hovers = 0;
        for (int i = 0; i < 60 * 1750 / 1000; i++) {   // up to 1150 + 600 ms
            s.step(c);
            for (DwellEvent e : s.events) if (e.type == DwellEvent.HOVER) hovers++;
        }
        assertEquals("one hover event", 1, hovers);
        assertEquals("one select, none in the 600 ms after it", 1, s.selectTimes.size());
        long at = s.selectTimes.get(0);
        // first frame is t = 17 ms (hover starts on frame 1, time at frame k is k*16.7)
        assertEquals(1150f, at, 34f);
        assertEquals("progress was reset by the select", 0f, s.d.progress("play"), 0f);
    }

    @Test
    public void selectTimingIsWithin17msOfDwellPlusHold() {
        Sim s = new Sim();
        s.d.setTargets(one());
        Fake c = new Fake(0, 960, 540);
        s.step(c);                       // frame 1: hover starts at t0
        long t0 = s.nowMs() - 17;        // the frame covered [t0, t0+17]
        while (s.selectTimes.isEmpty() && s.frame < 200) s.step(c);
        long elapsed = s.selectTimes.get(0) - t0;
        assertTrue("elapsed " + elapsed, Math.abs(elapsed - 1150) <= 17);
    }

    @Test
    public void noSelectDuringCooldown() {
        Sim s = new Sim();
        List<Target> l = one();
        l.add(new Target("other", 100, 100, 300, 200));
        s.d.setTargets(l);
        Fake c = new Fake(0, 960, 540);
        s.run(1300, c);
        assertEquals(1, s.selectTimes.size());
        c.fx = 200;
        c.fy = 200;                       // move to the other target right after the select
        s.run(500, c);
        assertEquals("cooldown still running", 0f, s.d.progress("other"), 0.0001f);
        assertEquals(1, s.selectTimes.size());
    }

    @Test
    public void excursionLosesProgressButNotToZero() {
        Sim s = new Sim();
        s.d.setTargets(one());
        Fake c = new Fake(0, 960, 540);
        s.run(1000, c);
        float before = s.d.progress("play");
        assertTrue(before > 0.8f && before < 1f);
        c.fx = 1700;                      // far outside
        s.run(200, c);
        float after = s.d.progress("play");
        assertTrue("lost some: " + after, after < before - 0.5f);
        assertTrue("not zero: " + after, after > 0.05f);
        c.fx = 960;                       // back: continues, no reset
        s.run(100, c);
        assertTrue(s.d.progress("play") > after);
        assertEquals(0, s.selectTimes.size());
    }

    @Test
    public void staysStickyInside25PercentMargin() {
        Sim s = new Sim();
        s.d.setTargets(one());               // x 760..1160, margin = 50 px each side
        Fake c = new Fake(0, 960, 540);
        s.run(600, c);
        float p1 = s.d.progress("play");
        c.fx = 1160 + 40;                    // 40 px outside the real edge, inside the sticky edge
        s.run(200, c);
        assertTrue("keeps filling", s.d.progress("play") > p1);
        c.fx = 1160 + 70;                    // beyond the sticky edge
        s.run(100, c);
        float p2 = s.d.progress("play");
        s.run(100, c);
        assertTrue(s.d.progress("play") < p2);
    }

    @Test
    public void notStickyBeforeHoverStarts() {
        Sim s = new Sim();
        s.d.setTargets(one());
        Fake c = new Fake(0, 1160 + 40, 540);   // in the margin, but not hovering yet
        s.run(1000, c);
        assertEquals(0f, s.d.progress("play"), 0f);
    }

    @Test
    public void holdTimesFollowSetting() {
        for (int hold : new int[] {700, 1000, 1400}) {
            Sim s = new Sim();
            s.d.setHoldMs(hold);
            s.d.setTargets(one());
            Fake c = new Fake(0, 960, 540);
            while (s.selectTimes.isEmpty() && s.frame < 400) s.step(c);
            assertEquals(150 + hold, s.selectTimes.get(0), 34f);
        }
    }

    @Test
    public void targetHoldOverridesDefault() {
        Sim s = new Sim();
        List<Target> l = new ArrayList<>();
        l.add(new Target("home", 760, 390, 400, 300, 1500));
        s.d.setTargets(l);
        Fake c = new Fake(0, 960, 540);
        while (s.selectTimes.isEmpty() && s.frame < 400) s.step(c);
        assertEquals(1650, s.selectTimes.get(0), 34f);
    }

    @Test
    public void invalidHoldSnapsToAllowed() {
        Sim s = new Sim();
        s.d.setHoldMs(5000);
        s.d.setTargets(one());
        Fake c = new Fake(0, 960, 540);
        while (s.selectTimes.isEmpty() && s.frame < 400) s.step(c);
        assertEquals(1550, s.selectTimes.get(0), 34f);
    }

    @Test
    public void invisibleCursorDoesNothing() {
        Sim s = new Sim();
        s.d.setTargets(one());
        Fake c = new Fake(0, 960, 540);
        c.vis = false;
        s.run(2000, c);
        assertEquals(0, s.selectTimes.size());
        assertEquals(0f, s.d.progress("play"), 0f);
    }

    @Test
    public void twoPlayersOnDifferentTargetsBothSelect() {
        Sim s = new Sim();
        List<Target> l = new ArrayList<>();
        l.add(new Target("a", 100, 100, 300, 300));
        l.add(new Target("b", 1500, 100, 300, 300));
        s.d.setTargets(l);
        Fake c0 = new Fake(0, 200, 200), c1 = new Fake(1, 1600, 200);
        s.run(1300, c0, c1);
        assertEquals(2, s.selectTimes.size());
    }

    @Test
    public void progressPlayerReportsOwner() {
        Sim s = new Sim();
        s.d.setTargets(one());
        Fake c1 = new Fake(1, 960, 540);
        s.run(500, new Fake(0, 5000, 5000) {{ vis = false; }}, c1);
        assertEquals(1, s.d.progressPlayer("play"));
    }

    @Test
    public void magnetPullsTowardsNearbyTargetWhenSlow() {
        Sim s = new Sim();
        s.d.setTargets(one());               // left edge x = 760
        Fake c = new Fake(0, 700, 540);      // 60 px left of the edge
        s.step(c);
        assertEquals(0.35f * (960 - 700), s.d.magnetDx(0), 0.01f);
        assertEquals(0f, s.d.magnetDy(0), 0.01f);
        c.fs = 700;                          // too fast
        s.step(c);
        assertEquals(0f, s.d.magnetDx(0), 0f);
        c.fs = 0;
        c.fx = 500;                          // 260 px away, out of range
        s.step(c);
        assertEquals(0f, s.d.magnetDx(0), 0f);
    }

    static List<Target> grid() {
        List<Target> l = new ArrayList<>();
        l.add(new Target("tl", 100, 100, 400, 300));
        l.add(new Target("tr", 1000, 100, 400, 300));
        l.add(new Target("bl", 100, 600, 400, 300));
        l.add(new Target("br", 1000, 600, 400, 300));
        l.add(new Target("far", 1500, 700, 300, 300));
        return l;
    }

    @Test
    public void moveFocusPicksVisuallyNearestInDirection() {
        DwellButtons d = new DwellButtons();
        d.setTargets(grid());
        d.setFocus("tl");
        d.moveFocus(1, 0);
        assertEquals("tr", d.focusId());
        d.moveFocus(0, 1);
        assertEquals("br", d.focusId());
        d.moveFocus(-1, 0);
        assertEquals("bl", d.focusId());
        d.moveFocus(0, -1);
        assertEquals("tl", d.focusId());
        d.moveFocus(-1, 0);
        assertEquals("nothing further left", "tl", d.focusId());
        d.moveFocus(0, -1);
        assertEquals("nothing further up", "tl", d.focusId());
    }

    @Test
    public void moveFocusPrefersAlignedOverCloserDiagonal() {
        List<Target> l = new ArrayList<>();
        l.add(new Target("me", 100, 400, 200, 200));
        l.add(new Target("diag", 400, 800, 200, 200));     // closer along x but way below
        l.add(new Target("aligned", 800, 400, 200, 200));
        DwellButtons d = new DwellButtons();
        d.setTargets(l);
        d.setFocus("me");
        d.moveFocus(1, 0);
        assertEquals("aligned", d.focusId());
    }

    @Test
    public void moveFocusWithNoFocusStartsAtFirstTarget() {
        DwellButtons d = new DwellButtons();
        d.setTargets(grid());
        d.moveFocus(1, 0);
        assertEquals("tl", d.focusId());
    }

    @Test
    public void selectFocusedEmitsSelectForThatPlayer() {
        Sim s = new Sim();
        s.d.setTargets(grid());
        s.d.setFocus("br");
        s.d.selectFocused(1);
        s.step();
        assertEquals(1, s.events.size());
        assertEquals(DwellEvent.SELECT, s.events.get(0).type);
        assertEquals("br", s.events.get(0).targetId);
        assertEquals(1, s.events.get(0).player);
        s.step();
        assertEquals(0, s.events.size());
    }

    @Test
    public void unknownIdsAreHarmless() {
        DwellButtons d = new DwellButtons();
        d.setTargets(grid());
        d.setFocus("nope");
        assertNull(d.focusId());
        assertEquals(0f, d.progress("nope"), 0f);
        assertEquals(-1, d.progressPlayer("nope"));
        d.selectFocused(0);
    }
}
