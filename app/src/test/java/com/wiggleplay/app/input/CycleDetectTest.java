package com.wiggleplay.app.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CycleDetectTest {

    /** Counts cycles between {@code from} and the end; returns {count, final crank angle}. */
    private static float[] count(PoseGen.Fn fn, float dur, float from, float hz, long seed) {
        CycleDetect d = new CycleDetect();
        int[] c0 = {-1};
        LagSim.run(fn, dur, hz, 0.25f, 8f, seed, (p, tMs, dt, t) -> {
            d.update(p, tMs, dt);
            if (c0[0] < 0 && t >= from) c0[0] = d.cycleCount();
        });
        return new float[] {d.cycleCount() - c0[0], d.crankAngle()};
    }

    private static void within(float turnsPerS, float radius, float tol, String label) {
        float worst = 0f;
        for (float hz : new float[] {8f, 12f}) {
            for (long seed = 1; seed <= 20; seed++) {
                boolean right = seed % 2 == 0;
                float aim = (seed % 5 - 2) * 0.25f;
                float dur = 14f;
                // start counting 2 s after the circles start, stop counting about 1 s before the end (the camera is late)
                float[] r = count(PoseGen.circles(right, turnsPerS, radius, aim, 1f), dur, 3f, hz, seed);
                float expected = turnsPerS * (dur - 3f - 0.25f);    // pictures are 0.25 s late
                float err = Math.abs(r[0] - expected) / expected;
                worst = Math.max(worst, err);
                assertTrue(label + ": counted " + r[0] + " expected " + expected + " (hz " + hz + " seed " + seed + ")", err <= tol);
            }
        }
        System.out.println("CYCLE " + label + ": worst error " + Math.round(worst * 1000) / 10f + " %");
    }

    @Test
    public void countsCirclesAt1_5TurnsPerSecond() {
        within(1.5f, 0.4f, 0.10f, "1.5 turns/s radius 0.4 sw");
    }

    @Test
    public void countsCirclesAt2_5TurnsPerSecond() {
        within(2.5f, 0.4f, 0.20f, "2.5 turns/s radius 0.4 sw");
    }

    @Test
    public void standingAndWigglingCountNothing() {
        int jumpCycles = 0;
        for (long seed = 1; seed <= 20; seed++) {
            for (float hz : new float[] {8f, 12f}) {
                assertEquals("stand", 0f, count(PoseGen.stand(), 20f, 0f, hz, seed)[0], 0f);
                assertEquals("chest", 0f, count(PoseGen.restAtChest(true), 20f, 0f, hz, seed)[0], 0f);
                assertEquals("wiggle", 0f, count(PoseGen.wiggle(seed % 2 == 0, 0.25f, 3f, 0f), 20f, 0f, hz, seed)[0], 0f);
                assertEquals("cheer", 0f, count(PoseGen.cheer(1f), 20f, 0f, hz, seed)[0], 0f);
                jumpCycles += count(PoseGen.jump(), 20f, 0f, hz, seed)[0];
            }
        }
        System.out.println("CYCLE false cycles while jumping: " + jumpCycles + " in 800 s");
        assertTrue("jumping gave " + jumpCycles + " cycles in 800 s", jumpCycles <= 2);
    }

    @Test
    public void pumpingCountsAsCycles() {
        for (long seed = 1; seed <= 10; seed++) {
            float[] r = count(PoseGen.pump(true, 0.5f, 1.5f), 14f, 3f, 8f, seed);
            float expected = 1.5f * (14f - 3f - 0.25f);
            assertTrue("pump counted " + r[0] + " expected " + expected, Math.abs(r[0] - expected) / expected <= 0.2f);
        }
    }

    @Test
    public void crankAngleIsMonotonicSmoothAndKeepsUp() {
        float worstStep = 0f, worstErr = 0f;
        for (float hz : new float[] {8f, 12f}) {
            for (long seed = 1; seed <= 20; seed++) {
                CycleDetect d = new CycleDetect();
                float[] prev = {0f}, step = {0f}, a4 = {Float.NaN}, aEnd = {0f};
                boolean[] mono = {true};
                float turns = 1.5f;
                LagSim.run(PoseGen.circles(seed % 2 == 0, turns, 0.4f, 0f, 1f), 10f, hz, 0.25f, 8f, seed, (p, tMs, dt, t) -> {
                    d.update(p, tMs, dt);
                    float a = d.crankAngle();
                    if (t > 3f) {
                        if (Math.abs(a - prev[0]) > 1e-4f && (a - prev[0]) * Math.signum(a4[0] == a4[0] ? a - a4[0] : 1f) < -1e-4f) mono[0] = false;
                        step[0] = Math.max(step[0], Math.abs(a - prev[0]));
                    }
                    if (t >= 4f && Float.isNaN(a4[0])) a4[0] = a;
                    aEnd[0] = a;
                    prev[0] = a;
                });
                float truth = (float) (2 * Math.PI * turns * 6f);
                float err = Math.abs(Math.abs(aEnd[0] - a4[0]) - truth) / truth;
                assertTrue("angle moved backwards (hz " + hz + " seed " + seed + ")", mono[0]);
                worstStep = Math.max(worstStep, step[0]);
                worstErr = Math.max(worstErr, err);
            }
        }
        System.out.println("CRANK ANGLE: worst per-frame step " + worstStep + " rad (average " + (2 * Math.PI * 1.5 / 60) + "), worst error of the angle turned in 6 s " + Math.round(worstErr * 1000) / 10f + " %");
        assertTrue("smooth: no frame moves more than 0.45 rad (" + worstStep + ")", worstStep <= 0.45f);
        assertTrue("speed matches within 10% (" + worstErr + ")", worstErr <= 0.10f);
    }

    @Test
    public void rateReadsTheTurnSpeed() {
        for (float turns : new float[] {0.8f, 1.5f, 2.5f}) {
            CycleDetect d = new CycleDetect();
            float[] rate = {0f};
            LagSim.run(PoseGen.circles(true, turns, 0.4f, 0f, 1f), 10f, 8f, 0.25f, 8f, 5, (p, tMs, dt, t) -> {
                d.update(p, tMs, dt);
                if (t > 8f && t < 9f) rate[0] = Math.max(rate[0], d.rate());
            });
            System.out.println("CYCLE rate at " + turns + " turns/s reads (max) " + rate[0]);
            assertTrue("rate " + rate[0] + " for " + turns, Math.abs(rate[0] - turns) / turns <= 0.3f);
        }
    }
}
