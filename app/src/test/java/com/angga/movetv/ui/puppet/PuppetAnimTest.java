package com.angga.movetv.ui.puppet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class PuppetAnimTest {

    private static final float DT = 1f / 60f;

    private static void run(PuppetAnim a, float seconds) {
        int n = Math.round(seconds / DT);
        for (int i = 0; i < n; i++) a.update(DT);
    }

    @Test
    public void blinkGapsAreInRangeAndDoubleBlinkIsAbout15Percent() {
        PuppetAnim a = new PuppetAnim(11);
        run(a, 4000f);
        List<Float> s = a.blinkStarts();
        assertTrue(s.size() > 800);
        int doubles = 0, sequences = 0;
        for (int i = 1; i < s.size(); i++) {
            float gap = s.get(i) - s.get(i - 1);
            if (gap < 1f) {
                assertEquals(0.28f, gap, 0.03f);
                doubles++;
            } else {
                assertTrue("gap " + gap, gap >= 2.45f && gap <= 5.55f);
                sequences++;
            }
        }
        float rate = doubles / (float) (doubles + sequences);
        assertTrue("double rate " + rate, rate > 0.09f && rate < 0.21f);
    }

    @Test
    public void twoKidsNeverBlinkTogetherOver60Seconds() {
        PuppetAnim a = new PuppetAnim(1), b = new PuppetAnim(2);
        run(a, 60f);
        run(b, 60f);
        for (float x : a.blinkStarts()) {
            for (float y : b.blinkStarts()) assertTrue("shared blink " + x + " " + y, Math.abs(x - y) > 0.1f);
        }
    }

    @Test
    public void blinkShowsHalfThenClosedThenOpens() {
        PuppetAnim a = new PuppetAnim(5);
        PuppetPose p = new PuppetPose();
        boolean half = false, closed = false;
        for (int i = 0; i < 60 * 8; i++) {
            a.update(DT);
            a.fill(p);
            if (p.eyes == PuppetPose.EYES_HALF) half = true;
            if (p.eyes == PuppetPose.EYES_CLOSED) closed = true;
        }
        assertTrue(half && closed);
        run(a, 1.5f);
        // between blinks the eyes are open
        int open = 0;
        for (int i = 0; i < 120; i++) {
            a.update(DT);
            a.fill(p);
            if (p.eyes == PuppetPose.EYES_OPEN) open++;
        }
        assertTrue(open > 60);
    }

    @Test
    public void waveStaysWithinLimitsAndReturnsToRest() {
        for (int side = 0; side < 2; side++) {
            PuppetAnim a = new PuppetAnim(3);
            PuppetPose p = new PuppetPose();
            a.wave(side);
            float maxLift = 0f, maxBend = -999f, minBend = 999f;
            int frames = 0;
            while (a.busy() && frames < 600) {
                a.update(DT);
                a.fill(p);
                maxLift = Math.max(maxLift, p.armLift[side]);
                maxBend = Math.max(maxBend, p.foreBend[side]);
                minBend = Math.min(minBend, p.foreBend[side]);
                assertTrue(p.finite());
                frames++;
            }
            assertTrue("lift " + maxLift, maxLift > 60f && maxLift <= 76f);
            assertTrue("bend " + maxBend, maxBend > 60f && maxBend <= 80f);
            assertTrue("bend min " + minBend, minBend >= -1f);
            assertTrue(frames < 600);
            a.fill(p);
            assertTrue(Math.abs(p.armLift[side]) <= 2.01f);
            assertEquals(0f, p.foreBend[side], 0.001f);
        }
    }

    @Test
    public void waveSwingsThreeToFourTimes() {
        PuppetAnim a = new PuppetAnim(8);
        PuppetPose p = new PuppetPose();
        a.wave(0);
        int peaks = 0;
        float prev = 0f, prev2 = 0f;
        while (a.busy()) {
            a.update(DT);
            a.fill(p);
            float v = p.foreBend[0];
            if (prev > prev2 && prev > v && prev > WAVE_PEAK) peaks++;
            prev2 = prev;
            prev = v;
        }
        assertTrue("peaks " + peaks, peaks >= 3 && peaks <= 4);
    }

    private static final float WAVE_PEAK = 70f;

    @Test
    public void cheerRaisesBothArmsHopsAndEndsAtRest() {
        PuppetAnim a = new PuppetAnim(4);
        PuppetPose p = new PuppetPose();
        a.cheer();
        float maxHop = 0f, minLift = 999f;
        int frames = 0;
        while (a.busy() && frames < 300) {
            a.update(DT);
            a.fill(p);
            maxHop = Math.max(maxHop, -p.rootDy);
            if (frames == 30) minLift = Math.min(p.armLift[0], p.armLift[1]);
            frames++;
        }
        assertTrue("hop " + maxHop, maxHop >= 20f && maxHop <= 40f);
        assertTrue("lift " + minLift, minLift > 90f);
        a.fill(p);
        assertEquals(0f, p.rootDy, 0.001f);
        assertTrue(Math.abs(p.armLift[0]) <= 2.01f && Math.abs(p.armLift[1]) <= 2.01f);
    }

    @Test
    public void pointHoldsTheAngleThenLowers() {
        PuppetAnim a = new PuppetAnim(6);
        PuppetPose p = new PuppetPose();
        a.point(PuppetPose.RIGHT, 100f);
        run(a, 0.7f);
        a.fill(p);
        assertEquals(100f - PuppetAnim.REST_ARM_DEG, p.armLift[PuppetPose.RIGHT], 2.5f);
        assertEquals(0f, p.foreBend[PuppetPose.RIGHT], 0.001f);
        run(a, 1.0f);
        a.fill(p);
        assertTrue(!a.busy());
        assertTrue(Math.abs(p.armLift[PuppetPose.RIGHT]) <= 2.01f);
        assertEquals(90f, PuppetAnim.angleTo(-50f, 0f), 0.01f);
        assertEquals(0f, PuppetAnim.angleTo(0f, 50f), 0.01f);
    }

    @Test
    public void talkingMovesTheMouthAtAboutEightHertz() {
        PuppetAnim a = new PuppetAnim(9);
        PuppetPose p = new PuppetPose();
        int changes = 0, last = -1;
        for (int i = 0; i < 120; i++) {
            a.talk(0.1f);
            a.update(DT);
            a.fill(p);
            if (p.mouth != last) {
                changes++;
                last = p.mouth;
            }
        }
        // 2 seconds at 7 to 9 open/close cycles per second: about 28 to 36 changes
        assertTrue("changes " + changes, changes >= 20 && changes <= 45);
    }

    @Test
    public void lookFollowsTargetAndNothingIsNaN() {
        PuppetAnim a = new PuppetAnim(10);
        PuppetPose p = new PuppetPose();
        a.lookAt(1f, -1f);
        run(a, 1f);
        a.fill(p);
        assertEquals(1f, p.pupilX, 0.02f);
        assertEquals(-1f, p.pupilY, 0.02f);
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < 60 * 120; i++) {
            if (i % 200 == 0) {
                switch (r.nextInt(3)) {
                    case 0: a.wave(r.nextInt(2)); break;
                    case 1: a.cheer(); break;
                    default: a.point(r.nextInt(2), r.nextFloat() * 200f); break;
                }
            }
            a.update(DT);
            a.fill(p);
            assertTrue(p.finite());
        }
    }
}
