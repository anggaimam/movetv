package com.wiggleplay.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EaseTest {

    private interface Fn { float at(float t); }

    private static void checkEnds(Fn f) {
        assertEquals(0f, f.at(0f), 1e-6f);
        assertEquals(1f, f.at(1f), 1e-6f);
    }

    @Test
    public void everyCurveStartsAtZeroAndEndsAtOne() {
        checkEnds(Ease::outCubic);
        checkEnds(Ease::inOutCubic);
        checkEnds(Ease::outBack);
        checkEnds(Ease::outElastic);
    }

    @Test
    public void inputOutsideZeroToOneIsClamped() {
        assertEquals(0f, Ease.outCubic(-3f), 0f);
        assertEquals(1f, Ease.outCubic(7f), 0f);
        assertEquals(0f, Ease.inOutCubic(-1f), 0f);
        assertEquals(1f, Ease.inOutCubic(2f), 0f);
        assertEquals(0f, Ease.outBack(-1f), 0f);
        assertEquals(1f, Ease.outBack(5f), 1e-6f);
        assertEquals(0f, Ease.outElastic(-1f), 0f);
        assertEquals(1f, Ease.outElastic(3f), 0f);
    }

    @Test
    public void outCubicIsMonotonicAndFastAtTheStart() {
        float prev = 0f;
        for (int i = 1; i <= 100; i++) {
            float v = Ease.outCubic(i / 100f);
            assertTrue(v >= prev);
            prev = v;
        }
        assertTrue(Ease.outCubic(0.25f) > 0.25f);
        assertEquals(0.875f, Ease.outCubic(0.5f), 1e-6f);
    }

    @Test
    public void inOutCubicIsSymmetricAroundTheMiddle() {
        assertEquals(0.5f, Ease.inOutCubic(0.5f), 1e-6f);
        for (int i = 0; i <= 10; i++) {
            float t = i / 10f;
            assertEquals(1f, Ease.inOutCubic(t) + Ease.inOutCubic(1f - t), 1e-5f);
        }
        assertTrue(Ease.inOutCubic(0.25f) < 0.25f);
        assertTrue(Ease.inOutCubic(0.75f) > 0.75f);
    }

    @Test
    public void outBackOvershootsAboveOneThenSettles() {
        float max = 0f;
        for (int i = 0; i <= 100; i++) max = Math.max(max, Ease.outBack(i / 100f));
        assertTrue("overshoot " + max, max > 1.05f && max < 1.15f);
        assertTrue(Ease.outBack(0.99f) < max);
    }

    @Test
    public void outElasticWobblesAroundOneAndSettles() {
        boolean above = false, below = false;
        for (int i = 20; i < 100; i++) {
            float v = Ease.outElastic(i / 100f);
            if (v > 1.001f) above = true;
            if (v < 0.999f) below = true;
        }
        assertTrue(above && below);
        assertEquals(1f, Ease.outElastic(0.95f), 0.01f);
    }
}
