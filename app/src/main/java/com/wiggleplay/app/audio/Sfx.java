package com.wiggleplay.app.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
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

    private final Context appCtx;
    private final SoundPool pool;
    private final Map<String, Integer> ids = new ConcurrentHashMap<>();
    private final Set<Integer> loaded = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> streams = new ConcurrentHashMap<>();
    private final Random rnd = new Random();
    private volatile float volume = 0.8f;

    /**
     * Creates the sound effects and starts rendering them in the background.
     * @param ctx any context
     */
    public Sfx(Context ctx) {
        appCtx = ctx.getApplicationContext();
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
     * Plays an effect at a chosen pitch and a share of the normal volume (for example an impact that is louder at speed).
     * @param name one of {@link Synth#EFFECTS} or a name loaded with {@link #loadAsset}
     * @param pitch playback rate, 1 = normal (limited to 0.5..2)
     * @param gain 0..1, multiplied with the effects volume
     */
    public void play(String name, float pitch, float gain) {
        Integer id = ids.get(name);
        if (id == null || !loaded.contains(id)) return;
        float rate = Math.max(0.5f, Math.min(2f, pitch));
        float v = volume * Math.max(0f, Math.min(1f, gain));
        pool.play(id, v, v, 1, 0, rate);
    }

    /**
     * Loads a sound file from the app's assets (for example an .ogg) under a name. Loading is asynchronous; calls to
     * play it are ignored until it is ready. Loading a name twice does nothing.
     * @param name the name to play it by
     * @param assetPath path inside assets, for example "audio/race/engine.ogg"
     */
    public void loadAsset(String name, String assetPath) {
        if (ids.containsKey(name)) return;
        try (AssetFileDescriptor afd = appCtx.getAssets().openFd(assetPath)) {
            ids.put(name, pool.load(afd, 1));
        } catch (Throwable t) {
            Log.w(TAG, "cannot load asset " + assetPath, t);
        }
    }

    /**
     * Frees a sound loaded with {@link #loadAsset} (and stops its loop).
     * @param name the name it was loaded under
     */
    public void unloadAsset(String name) {
        stopLoop(name);
        Integer id = ids.remove(name);
        if (id != null) {
            loaded.remove(id);
            pool.unload(id);
        }
    }

    /**
     * Starts a sound repeating, or changes the rate and volume of one that already repeats.
     * @param name the sound
     * @param rate playback rate (limited to 0.5..2)
     * @param gain 0..1, multiplied with the effects volume
     */
    public void loop(String name, float rate, float gain) {
        Integer id = ids.get(name);
        if (id == null || !loaded.contains(id)) return;
        float r = Math.max(0.5f, Math.min(2f, rate));
        float v = volume * Math.max(0f, Math.min(1f, gain));
        Integer stream = streams.get(name);
        if (stream == null) {
            int st = pool.play(id, v, v, 1, -1, r);
            if (st != 0) streams.put(name, st);
        } else {
            pool.setRate(stream, r);
            pool.setVolume(stream, v, v);
        }
    }

    /**
     * Changes the rate of a repeating sound.
     * @param name the sound
     * @param rate playback rate (limited to 0.5..2)
     */
    public void setRate(String name, float rate) {
        Integer stream = streams.get(name);
        if (stream != null) pool.setRate(stream, Math.max(0.5f, Math.min(2f, rate)));
    }

    /**
     * Stops a repeating sound.
     * @param name the sound
     */
    public void stopLoop(String name) {
        Integer stream = streams.remove(name);
        if (stream != null) pool.stop(stream);
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
