package com.wiggleplay.app.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pattern data for the music tracks. Plain Java, no android imports.
 * Every track is a loop of 16 steps per bar (4 steps = 1 beat, 4 beats = 1 bar).
 * All tunes here are original.
 */
public final class Tracks {

    /** Instruments the synth knows how to play. */
    public enum Inst { MARIMBA, BASS, KICK, CLAP, HAT, PLUCK, PAD, BELL, LEAD, PERC }

    /** One note: where it starts, what plays it, which pitch, how long, how loud. */
    public static final class Note {
        /** Start, in steps from the start of the loop. */
        public final int step;
        public final Inst inst;
        /** MIDI note number (60 = middle C). Drums mostly ignore it. */
        public final int midi;
        /** Length in steps. */
        public final float len;
        /** Loudness 0..1. */
        public final float vel;

        Note(int step, Inst inst, int midi, float len, float vel) {
            this.step = step;
            this.inst = inst;
            this.midi = midi;
            this.len = len;
            this.vel = vel;
        }
    }

    /** A looping track. */
    public static final class Track {
        public final String name;
        /** Default tempo. */
        public final int bpm;
        /** Allowed tempo range for {@link Music#setTempo(int)}. */
        public final int minBpm, maxBpm;
        public final int bars;
        public final List<Note> notes;

        Track(String name, int bpm, int minBpm, int maxBpm, int bars, List<Note> notes) {
            this.name = name;
            this.bpm = bpm;
            this.minBpm = minBpm;
            this.maxBpm = maxBpm;
            this.bars = bars;
            this.notes = Collections.unmodifiableList(notes);
        }

        /**
         * Length of the loop.
         * @return steps (bars x 16)
         */
        public int steps() {
            return bars * 16;
        }
    }

    private static final String[] NAMES = {"menu", "dance", "jungle", "sky", "fruit"};

    private Tracks() { }

    /**
     * Names of all tracks.
     * @return a new array: menu, dance, jungle, sky, fruit
     */
    public static String[] names() {
        return NAMES.clone();
    }

    /**
     * Looks up a track.
     * @param name one of {@link #names()}
     * @return the track, or null if unknown
     */
    public static Track get(String name) {
        if (name == null) return null;
        switch (name) {
            case "menu": return menu();
            case "dance": return dance();
            case "jungle": return jungle();
            case "sky": return sky();
            case "fruit": return fruit();
            default: return null;
        }
    }

    /**
     * Keeps a tempo inside the track's allowed range.
     * @return the clamped tempo
     */
    public static int clampBpm(Track t, int bpm) {
        return Math.max(t.minBpm, Math.min(t.maxBpm, bpm));
    }

    /**
     * Exact start of a step inside the looped audio.
     * @param step step number from the loop start
     * @param bpm tempo
     * @return sample index at {@link Synth#RATE}
     */
    public static int stepStartSample(int step, int bpm) {
        return (int) Math.round(step * (double) Synth.RATE * 15.0 / bpm);
    }

    /**
     * Length of one loop in samples (a whole number, so loops never drift).
     * @param steps loop length in steps
     * @param bpm tempo
     * @return samples
     */
    public static int loopSamples(int steps, int bpm) {
        return stepStartSample(steps, bpm);
    }

    /**
     * Most notes sounding at the same time (counted by note length in steps).
     * @return the peak
     */
    public static int maxPolyphony(Track t) {
        int max = 0;
        for (int s = 0; s < t.steps(); s++) {
            int n = 0;
            for (Note note : t.notes) if (note.step <= s && s < note.step + note.len) n++;
            max = Math.max(max, n);
        }
        return max;
    }

    // ---- track data ----

    private static final class B {
        final List<Note> notes = new ArrayList<>();

        void n(Inst inst, int step, int midi, float len, float vel) {
            notes.add(new Note(step, inst, midi, len, vel));
        }

        /** Same 16-character pattern ('x' = hit) in bars from..to-1. */
        void drums(Inst inst, String pattern, int midi, float vel, int from, int to) {
            for (int bar = from; bar < to; bar++) {
                for (int i = 0; i < 16; i++) {
                    if (pattern.charAt(i) == 'x') n(inst, bar * 16 + i, midi, 1f, vel);
                }
            }
        }

        void bar(Inst inst, int bar, int step, int midi, float len, float vel) {
            n(inst, bar * 16 + step, midi, len, vel);
        }

        Track build(String name, int bpm, int minBpm, int maxBpm, int bars) {
            return new Track(name, bpm, minBpm, maxBpm, bars, notes);
        }
    }

    /** 96 BPM, calm marimba arpeggios. */
    private static Track menu() {
        B b = new B();
        int[][] chords = {{60, 64, 67, 72}, {57, 60, 64, 69}, {53, 57, 60, 65}, {55, 59, 62, 67}};
        int[] roots = {48, 45, 41, 43};
        int[] arp = {0, 1, 2, 3, 2, 1, 2, 3};
        for (int bar = 0; bar < 8; bar++) {
            int c = bar / 2;
            for (int k = 0; k < 8; k++) b.bar(Tracks.Inst.MARIMBA, bar, k * 2, chords[c][arp[k]], 2.5f, 0.40f);
            b.bar(Inst.BASS, bar, 0, roots[c], 7f, 0.5f);
            b.bar(Inst.BASS, bar, 8, roots[c], 7f, 0.4f);
        }
        int[][] mel = {
                {0, 0, 76, 3}, {0, 6, 79, 2}, {0, 10, 76, 2}, {0, 14, 74, 2},
                {1, 0, 72, 4}, {1, 8, 74, 3}, {1, 12, 76, 4},
                {2, 0, 76, 3}, {2, 6, 81, 2}, {2, 10, 79, 2}, {2, 14, 76, 2},
                {3, 0, 72, 4}, {3, 8, 74, 3}, {3, 12, 76, 4},
                {4, 0, 81, 3}, {4, 6, 79, 2}, {4, 10, 77, 2}, {4, 14, 72, 2},
                {5, 0, 77, 4}, {5, 8, 76, 3}, {5, 12, 72, 4},
                {6, 0, 79, 3}, {6, 6, 83, 2}, {6, 10, 79, 2}, {6, 14, 74, 2},
                {7, 0, 74, 6}, {7, 8, 72, 8}};
        for (int[] m : mel) b.bar(Inst.MARIMBA, m[0], m[1], m[2], m[3], 0.6f);
        return b.build("menu", 96, 96, 96, 8);
    }

    /** 112 BPM (adjustable 100..128), bouncy pop with a 4-bar hook. */
    private static Track dance() {
        B b = new B();
        int[][] stabs = {{60, 64}, {60, 65}, {64, 67}, {62, 67}};
        int[] roots = {45, 41, 48, 43};
        b.drums(Inst.KICK, "x...x...x...x...", 36, 0.9f, 0, 8);
        b.drums(Inst.CLAP, "....x.......x...", 40, 0.6f, 0, 8);
        b.drums(Inst.HAT, "..x...x...x...x.", 42, 0.4f, 0, 8);
        int[] bassSteps = {0, 3, 6, 8, 11, 14};
        int[] stabSteps = {0, 6, 10};
        for (int bar = 0; bar < 8; bar++) {
            int c = bar % 4;
            for (int s : bassSteps) b.bar(Inst.BASS, bar, s, roots[c], 2f, 0.8f);
            for (int s : stabSteps) for (int m : stabs[c]) b.bar(Inst.PLUCK, bar, s, m, 2f, 0.45f);
        }
        int[][] hook = {
                {0, 0, 76, 2}, {0, 3, 76, 1}, {0, 4, 79, 2}, {0, 8, 76, 2}, {0, 12, 74, 2},
                {1, 0, 72, 2}, {1, 3, 74, 1}, {1, 4, 76, 4}, {1, 10, 72, 2}, {1, 14, 74, 2},
                {2, 0, 79, 2}, {2, 3, 79, 1}, {2, 4, 76, 2}, {2, 8, 74, 2}, {2, 12, 72, 2},
                {3, 0, 74, 2}, {3, 4, 72, 2}, {3, 8, 69, 4}, {3, 14, 72, 2}};
        for (int rep = 0; rep < 2; rep++) {
            for (int[] h : hook) b.bar(Inst.LEAD, h[0] + rep * 4, h[1], h[2], h[3], 0.7f);
        }
        return b.build("dance", 112, 100, 128, 8);
    }

    /** 120 BPM, percussive adventure in D minor pentatonic. */
    private static Track jungle() {
        B b = new B();
        b.drums(Inst.KICK, "x.......x.....x.", 36, 0.9f, 0, 8);
        b.drums(Inst.PERC, "x..x..x.x..x..x.", 50, 0.7f, 0, 8);
        b.drums(Inst.PERC, "..x...x...x...x.", 57, 0.5f, 0, 8);
        b.drums(Inst.HAT, "xxxxxxxxxxxxxxxx", 42, 0.2f, 0, 8);
        int[] roots = {38, 38, 41, 38, 43, 43, 41, 45};
        int[] bassSteps = {0, 3, 6, 8, 11, 14};
        int[][] mel = {{74, 77, 72}, {79, 77, 74}, {77, 72, 69}, {72, 74, 77},
                {79, 81, 79}, {77, 74, 72}, {81, 77, 74}, {74, 72, 69}};
        int[] melSteps = {2, 5, 10};
        for (int bar = 0; bar < 8; bar++) {
            for (int s : bassSteps) b.bar(Inst.BASS, bar, s, roots[bar], 2f, 0.7f);
            for (int i = 0; i < 3; i++) b.bar(Inst.MARIMBA, bar, melSteps[i], mel[bar][i], 2f, 0.55f);
        }
        return b.build("jungle", 120, 120, 120, 8);
    }

    /** 100 BPM, airy pads and bells. */
    private static Track sky() {
        B b = new B();
        int[][] pads = {{60, 64, 67, 71}, {57, 60, 64, 67}, {53, 57, 60, 64}, {55, 59, 62, 64}};
        int[] roots = {36, 33, 29, 31};
        for (int c = 0; c < 4; c++) {
            for (int m : pads[c]) b.bar(Inst.PAD, c * 2, 0, m, 32f, 0.5f);
            b.bar(Inst.BASS, c * 2, 0, roots[c], 32f, 0.35f);
        }
        int[][] bells = {
                {0, 4, 84}, {0, 10, 88}, {1, 2, 91}, {1, 8, 88}, {1, 12, 84},
                {2, 4, 81}, {2, 10, 84}, {3, 2, 88}, {3, 8, 84}, {3, 12, 81},
                {4, 4, 84}, {4, 10, 81}, {5, 2, 89}, {5, 8, 84}, {5, 12, 81},
                {6, 4, 83}, {6, 10, 86}, {7, 2, 91}, {7, 8, 86}, {7, 12, 83}};
        for (int[] bl : bells) b.bar(Inst.BELL, bl[0], bl[1], bl[2], 4f, 0.5f);
        return b.build("sky", 100, 100, 100, 8);
    }

    /** 116 BPM, plucky and upbeat in G major. */
    private static Track fruit() {
        B b = new B();
        int[][] chords = {{67, 71, 74}, {64, 67, 71}, {60, 64, 67}, {62, 66, 69}};
        int[] roots = {43, 40, 48, 38};
        int[] arp = {0, 1, 2, 1, 0, 1, 2, 1};
        int[][][] mel = {
                {{0, 79}, {3, 83}, {6, 81}, {10, 79}},
                {{0, 76}, {3, 79}, {6, 83}, {10, 79}},
                {{0, 79}, {3, 84}, {6, 83}, {10, 79}},
                {{0, 81}, {3, 86}, {6, 84}, {10, 81}}};
        b.drums(Inst.KICK, "x...x...x...x...", 36, 0.9f, 0, 8);
        b.drums(Inst.CLAP, "....x.......x...", 40, 0.5f, 0, 8);
        b.drums(Inst.HAT, "..x...x...x...x.", 42, 0.4f, 0, 8);
        int[] bassSteps = {0, 3, 8, 11};
        for (int bar = 0; bar < 8; bar++) {
            int c = bar % 4;
            for (int s : bassSteps) b.bar(Inst.BASS, bar, s, roots[c], 2f, 0.7f);
            for (int k = 0; k < 8; k++) b.bar(Inst.PLUCK, bar, k * 2, chords[c][arp[k]] + 12, 1.5f, 0.4f);
            for (int[] m : mel[c]) b.bar(Inst.LEAD, bar, m[0], m[1], 2f, 0.6f);
        }
        return b.build("fruit", 116, 116, 116, 8);
    }
}
