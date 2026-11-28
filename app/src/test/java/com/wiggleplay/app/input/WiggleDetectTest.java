package com.wiggleplay.app.input;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WiggleDetectTest {

    /** min and max of wiggle() from {@code from} seconds on. */
    private static float[] range(PoseGen.Fn fn, float dur, float from, float hz, long seed) {
        WiggleDetect d = new WiggleDetect();
        float[] r = {9f, -9f};
        LagSim.run(fn, dur, hz, 0.25f, 8f, seed, (p, tMs, dt, t) -> {
            d.update(p, tMs, dt);
            if (t >= from) {
                r[0] = Math.min(r[0], d.wiggle());
                r[1] = Math.max(r[1], d.wiggle());
            }
        });
        return r;
    }

    @Test
    public void gentleWiggleIsRecognised() {
        float worst = 9f;
        for (float hz : new float[] {8f, 12f}) {
            for (long seed = 1; seed <= 20; seed++) {
                float[] r = range(PoseGen.wiggle(seed % 2 == 0, 0.25f, 3f, 0.5f), 6f, 2.5f, hz, seed);
                worst = Math.min(worst, r[0]);
                assertTrue("wiggle min " + r[0] + " (hz " + hz + " seed " + seed + ")", r[0] >= 0.6f);
            }
        }
        System.out.println("WIGGLE gentle 0.25 sw / 3 Hz: worst min " + worst);
    }

    @Test
    public void restingHandAndSlowLiftGiveNothing() {
        float worst = 0f;
        for (float hz : new float[] {8f, 12f}) {
            for (long seed = 1; seed <= 20; seed++) {
                worst = Math.max(worst, range(PoseGen.stand(), 10f, 1f, hz, seed)[1]);
                worst = Math.max(worst, range(PoseGen.restAtChest(true), 10f, 1f, hz, seed)[1]);
                worst = Math.max(worst, range(PoseGen.slowLift(seed % 2 == 0, 3f), 4f, 0f, hz, seed)[1]);
            }
        }
        System.out.println("WIGGLE rest/slow lift: worst max " + worst);
        assertTrue("worst " + worst, worst < 0.1f);
    }

    @Test
    public void bigMovementIsNotAWiggle() {
        for (long seed = 1; seed <= 10; seed++) {
            float[] r = range(PoseGen.wiggle(true, 0.6f, 2f, 0.5f), 6f, 2.5f, 8f, seed);
            assertTrue("a 1.2 sw swing is a pump, not a wiggle: " + r[1], r[1] < 0.1f);
        }
    }
}
