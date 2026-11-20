package com.angga.movetv.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class SynthTest {

    private static int peak(short[] p) {
        int m = 0;
        for (short s : p) m = Math.max(m, Math.abs((int) s));
        return m;
    }

    @Test
    public void samplesMatchDuration() {
        assertEquals(11025, Synth.samples(0.5f));
        assertEquals(22050, Synth.samples(1f));
    }

    @Test
    public void freqOfA4IsFourForty() {
        assertEquals(440f, Synth.freq(69), 1e-3f);
        assertEquals(880f, Synth.freq(81), 1e-2f);
    }

    @Test
    public void envelopeStartsAndEndsAtZero() {
        Synth.Env e = Synth.Env.of(0.01f, 0.1f, 0.6f, 0.2f);
        assertEquals(0f, e.level(0f, 1f), 0f);
        assertEquals(0f, e.level(1f, 1f), 0f);
        assertEquals(0f, e.level(2f, 1f), 0f);
        assertEquals(1f, e.level(0.01f, 1f), 1e-3f);
        assertEquals(0.6f, e.level(0.5f, 1f), 1e-3f);
        assertTrue(e.level(0.95f, 1f) < 0.3f);
    }

    @Test
    public void oscillatorsHaveRightLengthAndEndAtZero() {
        for (Synth.Wave w : Synth.Wave.values()) {
            float[] x = Synth.osc(w, 440, 440, 0.25f, Synth.Env.of(0.005f, 0.05f, 0.5f, 0.05f), 0f);
            assertEquals(Synth.samples(0.25f), x.length);
            assertEquals(0f, x[0], 0.05f);
            assertEquals(0f, x[x.length - 1], 0f);
            float p = 0;
            for (float v : x) p = Math.max(p, Math.abs(v));
            assertTrue(p > 0.1f && p <= 1.0001f);
        }
    }

    @Test
    public void noiseIsRepeatableForTheSameSeed() {
        Synth.Env e = Synth.Env.of(0.005f, 0.05f, 0.5f, 0.05f);
        assertArrayEquals(Synth.noise(0.1f, e, 2000, 500, false, 7), Synth.noise(0.1f, e, 2000, 500, false, 7), 0f);
    }

    @Test
    public void everyEffectIsShortGentleAndEndsAtZero() {
        for (String name : Synth.EFFECTS) {
            short[] p = Synth.effect(name);
            assertNotNull(name, p);
            assertTrue(name + " too long", p.length <= Synth.samples(Synth.MAX_EFFECT_SECONDS));
            assertTrue(name + " too short", p.length >= Synth.samples(0.05f));
            assertTrue(name + " silent", peak(p) > 2000);
            assertTrue(name + " clips", peak(p) <= 32767);
            assertEquals(name + " ends", 0, p[p.length - 1]);
            assertTrue(name + " starts with a click", Math.abs(p[0]) < 700);
        }
    }

    @Test
    public void effectsAreAllDifferent() {
        for (int i = 0; i < Synth.EFFECTS.length; i++) {
            for (int j = i + 1; j < Synth.EFFECTS.length; j++) {
                assertFalse(Synth.EFFECTS[i] + " == " + Synth.EFFECTS[j],
                        Arrays.equals(Synth.effect(Synth.EFFECTS[i]), Synth.effect(Synth.EFFECTS[j])));
            }
        }
    }

    @Test
    public void unknownEffectIsNull() {
        assertNull(Synth.effect("nope"));
    }

    @Test
    public void noSampleLeavesSixteenBitRangeEvenWhenOverdriven() {
        float[] loud = new float[1000];
        Arrays.fill(loud, 50f);
        loud[1] = -50f;
        for (short s : Synth.toPcm(loud, 1f)) assertTrue(s <= 32767 && s >= -32767);
    }

    @Test
    public void wavHeaderIsValid() {
        short[] pcm = {0, 1000, -1000, 5};
        byte[] w = Synth.toWav(pcm);
        assertEquals(44 + 8, w.length);
        assertEquals("RIFF", new String(w, 0, 4));
        assertEquals("WAVE", new String(w, 8, 4));
        assertEquals("data", new String(w, 36, 4));
        assertEquals(8, (w[40] & 0xff));
        assertEquals(Synth.RATE & 0xff, w[24] & 0xff);
        assertEquals((Synth.RATE >> 8) & 0xff, w[25] & 0xff);
        assertEquals(1000 & 0xff, w[46] & 0xff);
    }

    @Test
    public void everyInstrumentRendersAFiniteNoteThatEndsAtZero() {
        for (Tracks.Inst inst : Tracks.Inst.values()) {
            float[] n = Synth.note(inst, 60, 0.3f);
            assertTrue(inst + " empty", n.length > 100);
            float p = 0;
            for (float v : n) {
                assertFalse(inst + " NaN", Float.isNaN(v) || Float.isInfinite(v));
                p = Math.max(p, Math.abs(v));
            }
            assertTrue(inst + " silent", p > 0.05f);
            assertEquals(inst + " ends", 0f, n[n.length - 1], 0f);
        }
    }

    @Test
    public void renderedTrackHasExactLoopLengthAndStaysInRange() {
        for (String name : Tracks.names()) {
            Tracks.Track t = Tracks.get(name);
            short[] p = Synth.renderTrack(t, t.bpm);
            assertEquals(name, Tracks.loopSamples(t.steps(), t.bpm), p.length);
            int pk = peak(p);
            assertTrue(name + " silent", pk > 3000);
            assertTrue(name + " too loud", pk <= 32767 * 0.6);
        }
    }

    @Test
    public void renderingIsRepeatable() {
        Tracks.Track t = Tracks.get("menu");
        assertArrayEquals(Synth.renderTrack(t, 96), Synth.renderTrack(t, 96));
    }
}
