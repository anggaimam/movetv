package com.wiggleplay.app.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PullDetectTest {

    private static final long T0 = LagSim.T0;

    /** Runs {@code fn}; the window opens at true time {@code ws} and closes {@code len} later (armed from {@code ws} on). */
    private static int[] run(PoseGen.Fn fn, float dur, float ws, float len, boolean easy, float hz, long seed) {
        PullDetect d = new PullDetect();
        d.setEasy(easy);
        int[] fired = {0};
        d.setListener(() -> fired[0]++);
        boolean[] armed = {false};
        LagSim.run(fn, dur, hz, 0.25f, 8f, seed, (p, tMs, dt, t) -> {
            if (!armed[0] && ws >= 0 && t >= ws) {
                armed[0] = true;
                d.arm(T0 + Math.round(ws * 1000f), T0 + Math.round((ws + len) * 1000f));
            }
            d.update(p, tMs, dt);
        });
        return new int[] {fired[0], d.earlyPulls()};
    }

    @Test
    public void pullInsideTheWindowFires() {
        for (float hz : new float[] {8f, 12f}) {
            for (long seed = 1; seed <= 20; seed++) {
                // "!" at 2.0 s, a child reacts at 2.5 s with a 0.6 sw pull
                int[] r = run(PoseGen.pull(seed % 2 == 0, 2.5f, 0.6f), 6f, 2f, 2f, false, hz, seed);
                assertEquals("fired (hz " + hz + " seed " + seed + ")", 1, r[0]);
            }
        }
    }

    @Test
    public void pullThatStartedJustBeforeTheWindowEndStillCounts() {
        for (long seed = 1; seed <= 20; seed++) {
            int[] r = run(PoseGen.pull(true, 3.8f, 0.6f), 7f, 2f, 2f, false, 8f, seed);   // window 2.0 to 4.0; pull starts 200 ms before the end
            assertEquals("seed " + seed, 1, r[0]);
        }
    }

    @Test
    public void pullAfterTheWindowAndPullsNeverArmedAreEarly() {
        for (long seed = 1; seed <= 10; seed++) {
            int[] late = run(PoseGen.pull(true, 4.6f, 0.6f), 8f, 2f, 2f, false, 8f, seed);
            assertEquals(0, late[0]);
            assertTrue("counted as early", late[1] >= 1);
            int[] unarmed = run(PoseGen.pull(true, 2.5f, 0.6f), 6f, -1f, 0f, false, 8f, seed);
            assertEquals(0, unarmed[0]);
            assertTrue(unarmed[1] >= 1);
        }
    }

    @Test
    public void standingWigglingAndCheeringNeverFireOutsideTheWindow() {
        for (long seed = 1; seed <= 10; seed++) {
            assertEquals(0, run(PoseGen.stand(), 20f, -1f, 0f, false, 8f, seed)[0]);
            assertEquals(0, run(PoseGen.wiggle(true, 0.25f, 3f, 0f), 20f, -1f, 0f, false, 8f, seed)[0]);
            assertEquals(0, run(PoseGen.cheer(2f), 20f, -1f, 0f, false, 8f, seed)[0]);
            assertEquals(0, run(PoseGen.jump(), 20f, -1f, 0f, true, 8f, seed)[0]);
        }
    }

    @Test
    public void wigglingInsideTheWindowIsNotAPull() {
        for (float hz : new float[] {8f, 12f}) {
            for (long seed = 1; seed <= 50; seed++) {
                assertEquals("seed " + seed + " hz " + hz, 0, run(PoseGen.wiggle(seed % 2 == 0, 0.25f, 3f, 0f), 8f, 3f, 4f, false, hz, seed)[0]);
            }
        }
    }

    @Test
    public void easyModeAcceptsASmallerPull() {
        int easy = 0, plain = 0;
        for (long seed = 1; seed <= 20; seed++) {
            plain += run(PoseGen.pull(true, 2.5f, 0.2f), 6f, 2f, 2f, false, 8f, seed)[0];
            easy += run(PoseGen.pull(true, 2.5f, 0.5f), 6f, 2f, 2f, true, 8f, seed)[0];
        }
        assertEquals("a 0.2 sw lift is never a pull", 0, plain);
        assertTrue("easy accepts a 0.5 sw rise (fired " + easy + " of 20)", easy >= 19);
    }
}
