package com.wiggleplay.app.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sound effects. At startup every effect is rendered by {@link Synth} (on a background thread),
 * written as a 16-bit mono 22050 Hz WAV file to {@code cacheDir/sfx/}, and loaded into a SoundPool
 * for low-delay playback. Calls made before an effect has loaded are silently ignored.
 */
public final class Sfx {

    private static final String TAG = "WigglePlay-Sfx";

    private final SoundPool pool;
    private final Map<String, Integer> ids = new ConcurrentHashMap<>();
    private final Set<Integer> loaded = ConcurrentHashMap.newKeySet();
    private final Random rnd = new Random();
    private volatile float volume = 0.8f;

    /**
     * Creates the sound effects and starts rendering them in the background.
     * @param ctx any context
     */
    public Sfx(Context ctx) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener((sp, id, status) -> {
            if (status == 0) loaded.add(id); else Log.w(TAG, "sound " + id + " failed to load: " + status);
        });
        final File dir = new File(ctx.getCacheDir(), "sfx");
        Thread t = new Thread(() -> renderAll(dir), "movetv-sfx");
        t.start();
    }

    private void renderAll(File dir) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                Log.w(TAG, "cannot create " + dir);
                return;
            }
            for (String name : Synth.EFFECTS) {
                short[] pcm = Synth.effect(name);
                if (pcm == null) continue;
                File f = new File(dir, name + ".wav");
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(Synth.toWav(pcm));
                }
                ids.put(name, pool.load(f.getAbsolutePath(), 1));
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not prepare sound effects", t);
        }
    }

    /**
     * Plays an effect at normal pitch ("pop" gets a random pitch between 0.9 and 1.15).
     * @param name one of {@link Synth#EFFECTS}
     */
    public void play(String name) {
        play(name, "pop".equals(name) ? 0.9f + rnd.nextFloat() * 0.25f : 1f);
    }

    /**
     * Plays an effect at a chosen pitch, e.g. "tick" with 0.8..1.6 to follow dwell progress.
     * @param name one of {@link Synth#EFFECTS}
     * @param pitch playback rate, 1 = normal (limited to 0.5..2)
     */
    public void play(String name, float pitch) {
        Integer id = ids.get(name);
        if (id == null || !loaded.contains(id)) return;
        float rate = Math.max(0.5f, Math.min(2f, pitch));
        float v = volume;
        pool.play(id, v, v, 1, 0, rate);
    }

    /**
     * Sets the effects volume.
     * @param v 0..1
     */
    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    /** Frees the sounds. Call when the app closes. */
    public void release() {
        pool.release();
    }
}
