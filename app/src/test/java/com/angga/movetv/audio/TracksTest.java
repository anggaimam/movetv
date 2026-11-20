package com.angga.movetv.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TracksTest {

    @Test
    public void allFiveTracksExistWithTheAgreedTempos() {
        assertEquals(96, Tracks.get("menu").bpm);
        assertEquals(112, Tracks.get("dance").bpm);
        assertEquals(120, Tracks.get("jungle").bpm);
        assertEquals(100, Tracks.get("sky").bpm);
        assertEquals(116, Tracks.get("fruit").bpm);
        assertEquals(5, Tracks.names().length);
        for (String n : Tracks.names()) assertNotNull(n, Tracks.get(n));
        assertNull(Tracks.get("nope"));
        assertNull(Tracks.get(null));
    }

    @Test
    public void danceTempoIsAdjustableFromOneHundredToOneTwentyEight() {
        Tracks.Track d = Tracks.get("dance");
        assertEquals(100, Tracks.clampBpm(d, 60));
        assertEquals(124, Tracks.clampBpm(d, 124));
        assertEquals(128, Tracks.clampBpm(d, 200));
        Tracks.Track m = Tracks.get("menu");
        assertEquals(96, Tracks.clampBpm(m, 128));
    }

    @Test
    public void tracksAreFourToEightBarsOfSixteenSteps() {
        for (String n : Tracks.names()) {
            Tracks.Track t = Tracks.get(n);
            assertTrue(n, t.bars >= 4 && t.bars <= 8);
            assertEquals(t.bars * 16, t.steps());
        }
    }

    @Test
    public void everyNoteIsInsideTheLoopAndSane() {
        for (String n : Tracks.names()) {
            Tracks.Track t = Tracks.get(n);
            assertTrue(n + " has notes", t.notes.size() > 20);
            for (Tracks.Note note : t.notes) {
                assertTrue(n + " step " + note.step, note.step >= 0 && note.step < t.steps());
                assertTrue(n + " midi " + note.midi, note.midi >= 24 && note.midi <= 108);
                // a note may ring past the loop end: the renderer wraps it to the start
                assertTrue(n + " len", note.len > 0f && note.len <= t.steps());
                assertTrue(n + " vel", note.vel > 0f && note.vel <= 1f);
            }
        }
    }

    @Test
    public void neverMoreThanSixVoicesAtOnce() {
        for (String n : Tracks.names()) {
            assertTrue(n + " polyphony " + Tracks.maxPolyphony(Tracks.get(n)), Tracks.maxPolyphony(Tracks.get(n)) <= 6);
        }
    }

    @Test
    public void stepTimingMatchesTheBpmExactly() {
        // 120 BPM: one beat = 0.5 s = 11025 samples, one bar = 2 s
        assertEquals(0, Tracks.stepStartSample(0, 120));
        assertEquals(11025, Tracks.stepStartSample(4, 120));
        assertEquals(44100, Tracks.stepStartSample(16, 120));
        // 112 BPM: one bar = 4 * 60/112 s = 47250 samples
        assertEquals(47250, Tracks.stepStartSample(16, 112));
        // 96 BPM: one bar = 2.5 s
        assertEquals(55125, Tracks.stepStartSample(16, 96));
    }

    @Test
    public void loopLengthNeverDriftsOverFiveMinutes() {
        for (String n : Tracks.names()) {
            Tracks.Track t = Tracks.get(n);
            int bpm = t.bpm;
            int loop = Tracks.loopSamples(t.steps(), bpm);
            double exact = t.steps() * Synth.RATE * 15.0 / bpm;
            assertTrue(n, Math.abs(loop - exact) <= 0.5);
            int loops = (int) Math.ceil(300.0 * Synth.RATE / loop);
            // after N loops the position is N * loop samples; exact time differs by at most N/2 samples (< 1 ms)
            double driftSeconds = Math.abs(loops * (loop - exact)) / Synth.RATE;
            assertTrue(n + " drift " + driftSeconds, driftSeconds < 0.001);
        }
    }
}
