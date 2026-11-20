package com.angga.movetv.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Renders sounds to PCM. Plain Java, no android imports, so it is unit-tested on the JVM.
 * Everything is 16-bit mono at {@link #RATE} Hz. Rendering is offline (not real time):
 * effects and whole music loops are rendered once, then just played back.
 */
public final class Synth {

    public static final int RATE = 22050;
    /** Longest sound effect, in seconds. */
    public static final float MAX_EFFECT_SECONDS = 1.2f;

    /** Basic wave shapes. */
    public enum Wave { SINE, TRIANGLE, SQUARE }

    /** Attack / decay / sustain / release envelope. Always starts and ends at 0 (no clicks). */
    public static final class Env {
        final float a, d, s, r;

        private Env(float a, float d, float s, float r) {
            this.a = Math.max(0.003f, a);
            this.d = Math.max(0.001f, d);
            this.s = s;
            this.r = Math.max(0.005f, r);
        }

        /**
         * Creates an envelope.
         * @param a attack seconds (at least 3 ms is used)
         * @param d decay seconds
         * @param s sustain level 0..1
         * @param r release seconds, ending exactly at the sound's end
         * @return the envelope
         */
        public static Env of(float a, float d, float s, float r) {
            return new Env(a, d, s, r);
        }

        private float base(float t) {
            if (t < a) return t / a;
            if (t < a + d) return 1f - (1f - s) * ((t - a) / d);
            return s;
        }

        /**
         * Envelope level.
         * @param t seconds since the start
         * @param len total length of the sound in seconds
         * @return 0..1; exactly 0 at t = 0 and at t >= len
         */
        public float level(float t, float len) {
            if (t >= len || t < 0f) return 0f;
            float rr = Math.min(r, len * 0.5f);
            float rel = len - rr;
            if (t <= rel) return base(t);
            return base(rel) * (len - t) / rr;
        }
    }

    private Synth() { }

    /**
     * Number of samples for a duration.
     * @param seconds duration
     * @return sample count
     */
    public static int samples(float seconds) {
        return Math.max(1, Math.round(seconds * RATE));
    }

    /**
     * Frequency of a MIDI note.
     * @param midi note number (69 = A4 = 440 Hz)
     * @return Hz
     */
    public static float freq(float midi) {
        return (float) (440.0 * Math.pow(2.0, (midi - 69.0) / 12.0));
    }

    /**
     * One-pole low-pass coefficient.
     * @param hz cut-off frequency
     * @return coefficient 0..1 for {@code y += c * (x - y)}
     */
    public static float lowPassCoef(float hz) {
        return (float) (1.0 - Math.exp(-2.0 * Math.PI * hz / RATE));
    }

    /**
     * Pitched voice (sine, triangle or square) with an optional pitch glide.
     * Square waves are always smoothed by a low-pass (band-limited-ish).
     * @param w wave shape
     * @param f0 start frequency, Hz
     * @param f1 end frequency, Hz (same as f0 for no glide; the glide is exponential)
     * @param seconds length
     * @param env envelope
     * @param lpHz low-pass cut-off in Hz, or 0 for none (square then uses 5x its pitch)
     * @return float samples, about -1..1
     */
    public static float[] osc(Wave w, float f0, float f1, float seconds, Env env, float lpHz) {
        int n = samples(seconds);
        float[] out = new float[n];
        double ph = 0;
        double f = f0;
        double ratio = (n > 1 && f1 != f0) ? Math.pow((double) f1 / f0, 1.0 / (n - 1)) : 1.0;
        float lp = 0f;
        float fixedCoef = lpHz > 0 ? lowPassCoef(lpHz) : 0f;
        for (int i = 0; i < n; i++) {
            ph += f / RATE;
            ph -= Math.floor(ph);
            float x;
            switch (w) {
                case SINE: x = (float) Math.sin(2.0 * Math.PI * ph); break;
                case TRIANGLE: x = (float) (4.0 * Math.abs(ph - 0.5) - 1.0); break;
                default: x = ph < 0.5 ? 1f : -1f; break;
            }
            if (w == Wave.SQUARE) {
                float c = lpHz > 0 ? fixedCoef : lowPassCoef((float) Math.min(f * 5.0, 9000.0));
                lp += c * (x - lp);
                x = lp;
            } else if (lpHz > 0) {
                lp += fixedCoef * (x - lp);
                x = lp;
            }
            out[i] = x * env.level(i / (float) RATE, seconds);
            f *= ratio;
        }
        out[n - 1] = 0f;
        return out;
    }

    /**
     * Noise, low-passed (or high-passed) with a cut-off that can sweep.
     * @param seconds length
     * @param env envelope
     * @param fc0 cut-off at the start, Hz
     * @param fc1 cut-off at the end, Hz (exponential sweep)
     * @param highPass true to keep the highs (hi-hats), false to keep the lows
     * @param seed random seed (same seed, same sound)
     * @return float samples
     */
    public static float[] noise(float seconds, Env env, float fc0, float fc1, boolean highPass, long seed) {
        int n = samples(seconds);
        float[] out = new float[n];
        Random rnd = new Random(seed);
        double fc = fc0;
        double ratio = (n > 1 && fc1 != fc0) ? Math.pow((double) fc1 / fc0, 1.0 / (n - 1)) : 1.0;
        float lp = 0f;
        for (int i = 0; i < n; i++) {
            float x = rnd.nextFloat() * 2f - 1f;
            lp += lowPassCoef((float) fc) * (x - lp);
            float y = highPass ? x - lp : lp;
            out[i] = y * env.level(i / (float) RATE, seconds);
            fc *= ratio;
        }
        out[n - 1] = 0f;
        return out;
    }

    /**
     * Adds a sound into a buffer (clipped at the end of the buffer).
     * @param dst target
     * @param atSec start time in seconds
     * @param src sound to add
     * @param gain volume of the added sound
     */
    public static void add(float[] dst, float atSec, float[] src, float gain) {
        int off = Math.round(atSec * RATE);
        for (int i = 0; i < src.length; i++) {
            int j = off + i;
            if (j < 0 || j >= dst.length) continue;
            dst[j] += src[i] * gain;
        }
    }

    /**
     * Converts to 16-bit PCM with a soft limiter, so it can never clip.
     * @param x float samples
     * @param master overall volume 0..1
     * @return PCM samples, within +/-32767
     */
    public static short[] toPcm(float[] x, float master) {
        short[] out = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = (short) Math.round(Math.tanh(x[i]) * 32767.0 * master);
        }
        return out;
    }

    /**
     * Wraps PCM in a WAV file (16-bit mono).
     * @param pcm samples
     * @return the bytes of a .wav file
     */
    public static byte[] toWav(short[] pcm) {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        put(b, 0, "RIFF");
        le32(b, 4, 36 + dataLen);
        put(b, 8, "WAVE");
        put(b, 12, "fmt ");
        le32(b, 16, 16);
        le16(b, 20, 1);          // PCM
        le16(b, 22, 1);          // mono
        le32(b, 24, RATE);
        le32(b, 28, RATE * 2);   // byte rate
        le16(b, 32, 2);          // block align
        le16(b, 34, 16);         // bits
        put(b, 36, "data");
        le32(b, 40, dataLen);
        for (int i = 0; i < pcm.length; i++) le16(b, 44 + i * 2, pcm[i]);
        return b;
    }

    private static void put(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }

    private static void le16(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
    }

    private static void le32(byte[] b, int off, int v) {
        le16(b, off, v);
        le16(b, off + 2, v >> 16);
    }

    // ---- sound effects ----

    /** Names of all sound effects. */
    public static final String[] EFFECTS = {
            "hover", "tick", "select", "back", "pop", "splat", "coin", "whoosh",
            "boing", "star", "cheer", "count", "go", "jump", "duck", "bonk"};

    private static float[] sine(float f0, float f1, float len, Env e) {
        return osc(Wave.SINE, f0, f1, len, e, 0f);
    }

    private static float[] tri(float f0, float f1, float len, Env e) {
        return osc(Wave.TRIANGLE, f0, f1, len, e, 0f);
    }

    /**
     * Renders one sound effect (each is at most {@link #MAX_EFFECT_SECONDS} long, gentle, and starts and ends at 0).
     * @param name one of {@link #EFFECTS}
     * @return PCM, or null if the name is unknown
     */
    public static short[] effect(String name) {
        float[] m;
        switch (name) {
            case "hover":
                m = sine(1200, 1200, 0.07f, Env.of(0.003f, 0.04f, 0f, 0.02f));
                scale(m, 0.35f);
                break;
            case "tick":
                m = sine(600, 1100, 0.09f, Env.of(0.004f, 0.05f, 0f, 0.03f));
                scale(m, 0.4f);
                break;
            case "select":
                m = new float[samples(0.2f)];
                add(m, 0, sine(300, 1200, 0.14f, Env.of(0.003f, 0.08f, 0f, 0.05f)), 0.5f);
                add(m, 0, tri(1000, 1000, 0.1f, Env.of(0.003f, 0.06f, 0f, 0.03f)), 0.25f);
                add(m, 0, noise(0.025f, Env.of(0.003f, 0.015f, 0f, 0.01f), 6000, 6000, true, 1), 0.2f);
                break;
            case "back":
                m = sine(800, 400, 0.15f, Env.of(0.004f, 0.08f, 0f, 0.05f));
                scale(m, 0.4f);
                break;
            case "pop":
                m = new float[samples(0.15f)];
                add(m, 0, sine(900, 250, 0.13f, Env.of(0.003f, 0.07f, 0f, 0.05f)), 0.5f);
                add(m, 0, noise(0.02f, Env.of(0.003f, 0.01f, 0f, 0.008f), 4000, 4000, false, 2), 0.3f);
                break;
            case "splat":
                m = new float[samples(0.3f)];
                add(m, 0, noise(0.24f, Env.of(0.004f, 0.16f, 0f, 0.07f), 2600, 700, false, 3), 0.8f);
                add(m, 0, sine(220, 90, 0.2f, Env.of(0.004f, 0.12f, 0f, 0.06f)), 0.45f);
                break;
            case "coin":
                m = new float[samples(0.5f)];
                add(m, 0, tri(1318.5f, 1318.5f, 0.1f, Env.of(0.003f, 0.05f, 0.2f, 0.04f)), 0.35f);
                add(m, 0.08f, tri(1760, 1760, 0.42f, Env.of(0.003f, 0.3f, 0f, 0.1f)), 0.35f);
                add(m, 0.08f, sine(3520, 3520, 0.3f, Env.of(0.003f, 0.15f, 0f, 0.1f)), 0.1f);
                break;
            case "whoosh":
                m = noise(0.5f, Env.of(0.12f, 0.15f, 0.5f, 0.2f), 300, 3500, false, 4);
                scale(m, 0.7f);
                break;
            case "boing":
                m = new float[samples(0.4f)];
                add(m, 0, sine(400, 160, 0.34f, Env.of(0.004f, 0.2f, 0.1f, 0.12f)), 0.45f);
                add(m, 0, tri(800, 320, 0.3f, Env.of(0.004f, 0.15f, 0f, 0.1f)), 0.15f);
                break;
            case "star": {
                float[] notes = {1046.5f, 1318.5f, 1568f, 2093f, 2637f};
                m = new float[samples(0.75f)];
                for (int i = 0; i < notes.length; i++) {
                    add(m, i * 0.06f, sine(notes[i], notes[i], 0.3f, Env.of(0.003f, 0.2f, 0f, 0.08f)), 0.3f);
                    add(m, i * 0.06f, tri(notes[i] * 2, notes[i] * 2, 0.2f, Env.of(0.003f, 0.1f, 0f, 0.08f)), 0.08f);
                }
                break;
            }
            case "cheer": {
                m = new float[samples(1.05f)];
                float[] c1 = {523.25f, 659.25f, 783.99f};
                float[] c2 = {659.25f, 783.99f, 1046.5f};
                for (float f : c1) add(m, 0, tri(f, f, 0.25f, Env.of(0.01f, 0.08f, 0.5f, 0.1f)), 0.2f);
                for (float f : c2) add(m, 0.22f, tri(f, f, 0.8f, Env.of(0.01f, 0.15f, 0.6f, 0.4f)), 0.2f);
                add(m, 0.22f, sine(523.25f, 523.25f, 0.8f, Env.of(0.01f, 0.15f, 0.5f, 0.4f)), 0.15f);
                break;
            }
            case "count":
                m = sine(880, 880, 0.18f, Env.of(0.005f, 0.03f, 0.7f, 0.08f));
                scale(m, 0.45f);
                break;
            case "go":
                m = new float[samples(0.4f)];
                add(m, 0, sine(1320, 1320, 0.38f, Env.of(0.005f, 0.05f, 0.7f, 0.15f)), 0.4f);
                add(m, 0, tri(1980, 1980, 0.3f, Env.of(0.005f, 0.05f, 0.4f, 0.12f)), 0.15f);
                break;
            case "jump":
                m = sine(300, 700, 0.17f, Env.of(0.004f, 0.08f, 0.2f, 0.07f));
                scale(m, 0.45f);
                break;
            case "duck":
                m = sine(500, 250, 0.15f, Env.of(0.004f, 0.08f, 0.2f, 0.06f));
                scale(m, 0.45f);
                break;
            case "bonk":
                m = new float[samples(0.45f)];
                add(m, 0, sine(200, 70, 0.26f, Env.of(0.004f, 0.15f, 0f, 0.09f)), 0.6f);
                add(m, 0, noise(0.03f, Env.of(0.003f, 0.015f, 0f, 0.01f), 1500, 1500, false, 5), 0.3f);
                add(m, 0.2f, sine(160, 80, 0.2f, Env.of(0.004f, 0.1f, 0f, 0.08f)), 0.4f);
                break;
            default:
                return null;
        }
        m[m.length - 1] = 0f;
        return toPcm(m, 0.8f);
    }

    private static void scale(float[] x, float g) {
        for (int i = 0; i < x.length; i++) x[i] *= g;
    }

    // ---- music ----

    /**
     * Renders one note of an instrument. The result includes the release tail, so it is a bit longer than the note.
     * @param inst the instrument
     * @param midi pitch (drums use it only for tom pitch)
     * @param lenSec nominal note length in seconds
     * @return float samples
     */
    public static float[] note(Tracks.Inst inst, int midi, float lenSec) {
        float f = freq(midi);
        float[] m;
        switch (inst) {
            case MARIMBA: {
                float len = Math.min(Math.max(lenSec, 0.15f), 0.6f) + 0.08f;
                m = new float[samples(len)];
                add(m, 0, sine(f, f, len, Env.of(0.002f, 0.3f, 0f, 0.06f)), 0.6f);
                add(m, 0, sine(f * 4f, f * 4f, len, Env.of(0.002f, 0.1f, 0f, 0.04f)), 0.18f);
                add(m, 0, noise(0.02f, Env.of(0.002f, 0.01f, 0f, 0.008f), 3000, 3000, false, midi), 0.15f);
                break;
            }
            case BASS: {
                float len = Math.max(lenSec, 0.1f) + 0.06f;
                m = new float[samples(len)];
                add(m, 0, tri(f, f, len, Env.of(0.006f, 0.1f, 0.7f, 0.06f)), 0.6f);
                add(m, 0, sine(f, f, len, Env.of(0.006f, 0.1f, 0.7f, 0.06f)), 0.4f);
                break;
            }
            case KICK:
                m = new float[samples(0.25f)];
                add(m, 0, sine(150, 45, 0.24f, Env.of(0.002f, 0.15f, 0f, 0.06f)), 1f);
                break;
            case CLAP:
                m = new float[samples(0.18f)];
                for (int i = 0; i < 3; i++) {
                    add(m, i * 0.011f, noise(0.1f, Env.of(0.002f, 0.04f, 0f, 0.04f), 3500, 3500, false, 10 + i), 0.5f);
                }
                break;
            case HAT:
                m = noise(0.06f, Env.of(0.002f, 0.025f, 0f, 0.03f), 6000, 6000, true, 20);
                scale(m, 0.7f);
                break;
            case PLUCK: {
                float len = Math.min(Math.max(lenSec, 0.1f), 0.35f) + 0.05f;
                m = osc(Wave.SQUARE, f, f, len, Env.of(0.003f, 0.12f, 0f, 0.04f), 2500);
                scale(m, 0.7f);
                break;
            }
            case PAD: {
                float len = Math.max(lenSec, 0.5f) + 0.3f;
                m = new float[samples(len)];
                Env e = Env.of(0.2f, 0.15f, 0.8f, 0.4f);
                add(m, 0, tri(f, f, len, e), 0.5f);
                add(m, 0, tri(f * 1.005f, f * 1.005f, len, e), 0.5f);
                break;
            }
            case BELL: {
                float len = 0.9f;
                m = new float[samples(len)];
                add(m, 0, sine(f, f, len, Env.of(0.002f, 0.7f, 0f, 0.15f)), 0.6f);
                add(m, 0, sine(f * 2.76f, f * 2.76f, len, Env.of(0.002f, 0.4f, 0f, 0.1f)), 0.25f);
                add(m, 0, sine(f * 5.4f, f * 5.4f, len, Env.of(0.002f, 0.2f, 0f, 0.1f)), 0.12f);
                break;
            }
            case LEAD: {
                float len = Math.max(lenSec, 0.08f) + 0.06f;
                m = new float[samples(len)];
                Env e = Env.of(0.01f, 0.05f, 0.7f, 0.06f);
                add(m, 0, tri(f, f, len, e), 0.6f);
                add(m, 0, osc(Wave.SQUARE, f, f, len, e, 3000), 0.15f);
                break;
            }
            default: { // PERC: a woody tom
                m = new float[samples(0.2f)];
                add(m, 0, sine(f * 2f, f, 0.18f, Env.of(0.002f, 0.1f, 0f, 0.07f)), 0.8f);
                add(m, 0, noise(0.015f, Env.of(0.002f, 0.008f, 0f, 0.006f), 2500, 2500, false, 30), 0.2f);
                break;
            }
        }
        m[m.length - 1] = 0f;
        return m;
    }

    /**
     * Renders a whole track as one seamless loop. Notes that ring past the end wrap around to the start.
     * The length is exactly {@link Tracks#loopSamples(int, int)}, so the tempo never drifts.
     * @param t the track
     * @param bpm tempo (use {@link Tracks#clampBpm})
     * @return PCM of one loop
     */
    public static short[] renderTrack(Tracks.Track t, int bpm) {
        int total = Tracks.loopSamples(t.steps(), bpm);
        float[] mix = new float[total];
        Map<Long, float[]> cache = new HashMap<>();
        for (Tracks.Note n : t.notes) {
            float lenSec = n.len * 15f / bpm;
            long key = ((long) n.inst.ordinal() << 40) | ((long) n.midi << 20) | Math.round(lenSec * 1000f);
            float[] s = cache.get(key);
            if (s == null) {
                s = note(n.inst, n.midi, lenSec);
                cache.put(key, s);
            }
            int start = Tracks.stepStartSample(n.step, bpm);
            for (int i = 0; i < s.length; i++) mix[(start + i) % total] += s[i] * n.vel;
        }
        float peak = 0f;
        for (float v : mix) peak = Math.max(peak, Math.abs(v));
        if (peak > 0f) scale(mix, 0.55f / peak); // moderate volume, same loudness for every track
        return toPcm(mix, 1f);
    }
}
