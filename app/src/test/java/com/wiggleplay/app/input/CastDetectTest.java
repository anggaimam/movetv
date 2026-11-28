package com.wiggleplay.app.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Cast power and aim through the camera model ({@link LagSim}): 8 Hz and 12 Hz, 250 ms late, +/-8 px noise. */
public class CastDetectTest {

    private static final class Result {
        int casts;
        float power, aim;
        boolean jerky;
    }

    private static Result run(PoseGen.Fn fn, float dur, float hz, long seed) {
        CastDetect d = new CastDetect();
        Result r = new Result();
        d.setListener((power, aim, jerky) -> {
            r.casts++;
            r.power = power;
            r.aim = aim;
            r.jerky = jerky;
        });
        LagSim.run(fn, dur, hz, 0.25f, 8f, seed, (p, tMs, dt, t) -> d.update(p, tMs, dt));
        return r;
    }

    @Test
    public void shortHoldGivesSmallPower() {
        float sum = 0f;
        int n = 0;
        for (float hz : new float[] {8f, 12f}) {
            for (long seed = 1; seed <= 20; seed++) {
                for (boolean right : new boolean[] {true, false}) {
                    Result r = run(PoseGen.cast(right, 1f, 0.6f, 0.3f, 0f), 4f, hz, seed);
                    assertEquals("one cast (hz " + hz + " seed " + seed + ")", 1, r.casts);
                    assertTrue("power " + r.power + " for a 0.3 s hold (hz " + hz + " seed " + seed + ")", r.power >= 0.1f && r.power <= 0.3f);
                    sum += r.power;
                    n++;
                }
            }
        }
        System.out.println("CAST hold 0.3 s: mean power " + sum / n);
    }

    @Test
    public void longHoldGivesFullPower() {
        float min = 9f;
        for (float hz : new float[] {8f, 12f}) {
            for (long seed = 1; seed <= 20; seed++) {
                Result r = run(PoseGen.cast(seed % 2 == 0, 1f, 0.6f, 1.4f, 0f), 5f, hz, seed);
                assertEquals(1, r.casts);
                assertTrue("power " + r.power + " for a 1.4 s hold (hz " + hz + " seed " + seed + ")", r.power >= 0.85f);
                min = Math.min(min, r.power);
            }
        }
        System.out.println("CAST hold 1.4 s: min power " + min);
    }

    @Test
    public void aimFollowsTheSidewaysHold() {
        float worst = 0f;
        for (float aimX : new float[] {-0.8f, -0.4f, 0f, 0.4f, 0.8f}) {
            for (long seed = 1; seed <= 10; seed++) {
                Result r = run(PoseGen.cast(seed % 2 == 0, 1f, 0.6f, 1.0f, aimX), 4f, 8f, seed);
                assertEquals(1, r.casts);
                float err = Math.abs(r.aim - aimX);
                worst = Math.max(worst, err);
                assertTrue("aim " + r.aim + " expected " + aimX, err <= 0.15f);
            }
        }
        System.out.println("CAST aim: worst error " + worst);
    }

    @Test
    public void cheeringStandingAndJumpingNeverCast() {
        for (long seed = 1; seed <= 5; seed++) {
            assertEquals("stand", 0, run(PoseGen.stand(), 20f, 8f, seed).casts);
            assertEquals("hand at chest", 0, run(PoseGen.restAtChest(true), 20f, 8f, seed).casts);
            assertEquals("cheer held", 0, run(PoseGen.cheer(1f), 20f, 8f, seed).casts);
            assertEquals("jump", 0, run(PoseGen.jump(), 20f, 8f, seed).casts);
        }
    }

    @Test
    public void quickRiseAndFallIsIgnored() {
        for (long seed = 1; seed <= 10; seed++) {
            assertEquals("a flick of the hand", 0, run(PoseGen.cast(true, 1f, 0.12f, 0f, 0f), 4f, 12f, seed).casts);
        }
    }

    @Test
    public void jerkyDropIsFlagged() {
        Result r = run(PoseGen.cast(true, 1f, 0.6f, 1.0f, 0f), 4f, 12f, 3);
        assertEquals(1, r.casts);
        assertTrue("the standard 0.35 s drop (about 6 sw/s) is jerky", r.jerky);
    }
}
