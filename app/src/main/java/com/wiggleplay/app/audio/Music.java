package com.wiggleplay.app.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Music player. A track is rendered once into one seamless loop ({@link Synth#renderTrack}) on a
 * background thread, and a sequencer thread streams that loop into an AudioTrack
 * (MODE_STREAM, 22050 Hz mono, about 100 ms buffer). Playing a loop costs almost no CPU, and the tempo
 * is exact because the loop length is a whole number of samples.
 * Beat callbacks are timed from the AudioTrack's playback position, not from a clock.
 */
public final class Music {

    private static final String TAG = "WigglePlay-Music";
    private static final int CHUNK = Synth.RATE / 50;        // 20 ms per write
    private static final int FADE_SWITCH_MS = 80;

    /** Hears every beat of the music. */
    public interface BeatListener {
        /**
         * Called on the main thread when a beat is heard (not when it was written to the audio buffer).
         * @param beat running beat number since the track started, 0, 1, 2, ... (beat % 4 = beat inside the bar)
         * @param bar running bar number since the track started, 0, 1, 2, ... (= beat / 4)
         */
        void onBeat(int beat, int bar);
    }

    private static final int PLAY = 1, TEMPO = 2, STOP = 3;

    private static final class Cmd {
        final int type;
        final Tracks.Track track;
        final int bpm;
        final int fadeMs;

        Cmd(int type, Tracks.Track track, int bpm, int fadeMs) {
            this.type = type;
            this.track = track;
            this.bpm = bpm;
            this.fadeMs = fadeMs;
        }
    }

    private final Object lock = new Object();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService renderer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "movetv-music-render");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private Thread thread;
    private Cmd pending;           // latest command, guarded by lock
    private boolean released;      // guarded by lock
    private int tempoRequest = 0;  // tempo asked for with setTempo, 0 = track default; guarded by lock
    private Tracks.Track playingTrack; // guarded by lock
    private volatile BeatListener listener;

    /** Creates the player. Nothing is made until the first {@link #play(String)}. */
    public Music() { }

    /**
     * Sets who hears the beats.
     * @param l the listener, or null for none
     */
    public void setBeatListener(BeatListener l) {
        listener = l;
    }

    /**
     * Starts a track (if one is playing it fades out quickly first). Starts at the tempo last given to
     * {@link #setTempo(int)} if that fits this track, otherwise at the track's own tempo.
     * @param track one of {@link Tracks#names()}
     */
    public void play(String track) {
        Tracks.Track t = Tracks.get(track);
        if (t == null) {
            Log.w(TAG, "unknown track " + track);
            return;
        }
        synchronized (lock) {
            if (released) return;
            playingTrack = t;
            int bpm = tempoRequest > 0 ? Tracks.clampBpm(t, tempoRequest) : t.bpm;
            pending = new Cmd(PLAY, t, bpm, 0);
            ensureThread();
            lock.notifyAll();
        }
    }

    /**
     * Fades out and stops.
     * @param fadeMs fade length in milliseconds (0 = as fast as possible)
     */
    public void stop(int fadeMs) {
        synchronized (lock) {
            if (released) return;
            playingTrack = null;
            pending = new Cmd(STOP, null, 0, Math.max(0, fadeMs));
            lock.notifyAll();
        }
    }

    /**
     * Changes the tempo. It is kept inside the track's allowed range (the dance track allows 100..128;
     * other tracks only allow their own tempo). On a track that is already playing it takes effect at the
     * start of the next loop; call it before {@link #play(String)} to have it right from the start.
     * @param bpm beats per minute
     */
    public void setTempo(int bpm) {
        synchronized (lock) {
            if (released) return;
            tempoRequest = bpm;
            if (playingTrack != null) {
                int clamped = Tracks.clampBpm(playingTrack, bpm);
                if (pending != null && pending.type == PLAY) {
                    // play() was just called and has not started yet: start at the new tempo instead
                    pending = new Cmd(PLAY, pending.track, clamped, 0);
                } else {
                    pending = new Cmd(TEMPO, playingTrack, clamped, 0);
                }
                lock.notifyAll();
            }
        }
    }

    /** Stops everything and frees the audio. Call when the app closes. */
    public void release() {
        synchronized (lock) {
            released = true;
            lock.notifyAll();
        }
        renderer.shutdownNow();
    }

    private void ensureThread() {
        if (thread != null) return;
        thread = new Thread(this::sequencer, "movetv-music");
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    private AudioTrack createAudioTrack() {
        try {
            int min = AudioTrack.getMinBufferSize(Synth.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bytes = Math.max(min, Synth.RATE / 10 * 2); // about 100 ms
            return new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Synth.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (Throwable t) {
            Log.w(TAG, "no audio output", t);
            return null;
        }
    }

    // beats written to the buffer but not yet heard: frame at which they are heard, and their number
    private final long[] evFrame = new long[64];
    private final int[] evBeat = new int[64];
    private int evHead = 0, evTail = 0;

    private Future<short[]> render(Tracks.Track t, int bpm) {
        return renderer.submit(() -> Synth.renderTrack(t, bpm));
    }

    private void sequencer() {
        AudioTrack at = createAudioTrack();
        if (at == null) return;
        try {
            final short[] chunk = new short[CHUNK];
            boolean running = false;      // AudioTrack is playing
            long written = 0;             // frames handed to the AudioTrack so far (silence included)

            short[] loop = null;          // what is playing now (null = nothing)
            Tracks.Track cur = null;
            int curBpm = 0;
            int pos = 0;                  // position inside the loop
            int beatInLoop = 0, beatsPerLoop = 0, nextBeatPos = 0;
            int beatCounter = 0;          // running beat number

            float gain = 1f;
            float fadeStep = 0f;          // gain lost per sample while fading out; 0 = not fading
            boolean stopAfterFade = false;

            Future<short[]> nextRender = null; // a new track waiting to start
            Tracks.Track nextTrack = null;
            int nextBpm = 0;
            Future<short[]> tempoRender = null; // same track, new tempo, waiting for the loop end
            int tempoBpm = 0;

            while (true) {
                Cmd cmd;
                synchronized (lock) {
                    if (released) break;
                    cmd = pending;
                    pending = null;
                    if (cmd == null && loop == null && nextRender == null) {
                        // nothing to do: pause the audio and sleep until a command arrives
                        if (running) {
                            at.pause();
                            running = false;
                            evHead = evTail = 0;
                        }
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            break;
                        }
                        continue;
                    }
                }

                if (cmd != null) {
                    if (cmd.type == PLAY) {
                        if (nextRender != null) nextRender.cancel(true);
                        nextTrack = cmd.track;
                        nextBpm = cmd.bpm;
                        nextRender = render(nextTrack, nextBpm);
                        if (tempoRender != null) { tempoRender.cancel(true); tempoRender = null; }
                        stopAfterFade = false;
                        if (loop != null) fadeStep = 1f / Math.max(1, FADE_SWITCH_MS * Synth.RATE / 1000);
                    } else if (cmd.type == TEMPO) {
                        if (nextRender != null && nextTrack == cmd.track) {
                            nextRender.cancel(true);
                            nextBpm = cmd.bpm;
                            nextRender = render(nextTrack, nextBpm);
                        } else if (cur == cmd.track && loop != null && cmd.bpm != curBpm && nextRender == null) {
                            if (tempoRender != null) tempoRender.cancel(true);
                            tempoBpm = cmd.bpm;
                            tempoRender = render(cur, tempoBpm);
                        }
                    } else if (cmd.type == STOP) {
                        if (nextRender != null) { nextRender.cancel(true); nextRender = null; }
                        if (tempoRender != null) { tempoRender.cancel(true); tempoRender = null; }
                        if (loop != null) {
                            fadeStep = 1f / Math.max(1, cmd.fadeMs * Synth.RATE / 1000);
                            stopAfterFade = true;
                        }
                    }
                }

                // nothing playing, a new track is ready (or the AudioTrack is paused, so waiting is safe): start it
                if (loop == null && nextRender != null && (!running || nextRender.isDone())) {
                    short[] ready;
                    try {
                        ready = nextRender.get();
                    } catch (Throwable t) {
                        nextRender = null;
                        continue;
                    }
                    nextRender = null;
                    loop = ready;
                    cur = nextTrack;
                    curBpm = nextBpm;
                    pos = 0;
                    gain = 1f;
                    fadeStep = 0f;
                    beatInLoop = 0;
                    beatsPerLoop = cur.steps() / 4;
                    nextBeatPos = 0;
                    beatCounter = 0;
                    evHead = evTail = 0;
                    if (!running) {
                        at.play();
                        running = true;
                    }
                }

                // fill one chunk
                int filled = 0;
                if (loop == null) {
                    // fading finished, new track not ready yet: play silence meanwhile
                    java.util.Arrays.fill(chunk, (short) 0);
                    filled = CHUNK;
                }
                while (filled < CHUNK) {
                    int take = Math.min(CHUNK - filled, loop.length - pos);
                    for (int i = 0; i < take; i++) {
                        chunk[filled + i] = (short) (loop[pos + i] * gain);
                        if (fadeStep > 0f) gain = Math.max(0f, gain - fadeStep);
                    }
                    while (nextBeatPos < pos + take && beatInLoop < beatsPerLoop) {
                        enqueueBeat(written + filled + (nextBeatPos - pos), beatCounter++);
                        beatInLoop++;
                        nextBeatPos = beatInLoop < beatsPerLoop
                                ? Tracks.stepStartSample(beatInLoop * 4, curBpm) : Integer.MAX_VALUE;
                    }
                    pos += take;
                    filled += take;
                    if (pos >= loop.length) {
                        pos = 0;
                        beatInLoop = 0;
                        nextBeatPos = 0;
                        // loop boundary: this is where a tempo change takes effect
                        if (tempoRender != null && tempoRender.isDone()) {
                            try {
                                loop = tempoRender.get();
                                curBpm = tempoBpm;
                            } catch (Throwable ignored) { }
                            tempoRender = null;
                        }
                    }
                    if (gain <= 0f && fadeStep > 0f) {
                        // faded out completely: zero the rest of this chunk
                        java.util.Arrays.fill(chunk, filled, CHUNK, (short) 0);
                        filled = CHUNK;
                    }
                }

                at.write(chunk, 0, CHUNK);
                written += CHUNK;

                if (fadeStep > 0f && gain <= 0f) {
                    // fade-out is over
                    loop = null;
                    cur = null;
                    fadeStep = 0f;
                    gain = 1f;
                    if (stopAfterFade) {
                        stopAfterFade = false;
                        evHead = evTail = 0; // beats of music that was never heard
                    }
                    // if a new track is waiting, the top of the loop starts it
                }

                // tell the game about the beats that are being heard right now
                long head = at.getPlaybackHeadPosition() & 0xFFFFFFFFL;
                while (evHead != evTail && evFrame[evHead & 63] <= head) {
                    final int beat = evBeat[evHead & 63];
                    evHead++;
                    final BeatListener l = listener;
                    if (l != null) main.post(() -> l.onBeat(beat, beat / 4));
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "music stopped", t);
        } finally {
            try { at.stop(); } catch (Throwable ignored) { }
            at.release();
        }
    }

    private void enqueueBeat(long frame, int beat) {
        if (evTail - evHead >= 64) evHead++; // listener far behind: drop the oldest
        evFrame[evTail & 63] = frame;
        evBeat[evTail & 63] = beat;
        evTail++;
    }
}
