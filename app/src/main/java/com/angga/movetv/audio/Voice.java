package com.angga.movetv.audio;

import android.content.Context;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * Spoken names ("Fruit Splash") using Android TextToSpeech. If the device has no speech engine
 * (or no English voice), every call quietly does nothing.
 */
public final class Voice {

    private static final String TAG = "MoveTV-Voice";

    private TextToSpeech tts;
    private volatile boolean ready = false;
    private volatile boolean failed = false;
    private String waiting;
    private volatile long speakingUntil;

    /**
     * Starts the speech engine. It becomes ready a moment later; words said before that are spoken once ready.
     * @param ctx any context
     */
    public Voice(Context ctx) {
        try {
            tts = new TextToSpeech(ctx.getApplicationContext(), this::onInit);
        } catch (Throwable t) {
            Log.w(TAG, "no text to speech", t);
            failed = true;
        }
    }

    private synchronized void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            failed = true;
            return;
        }
        try {
            int r = tts.setLanguage(Locale.ENGLISH);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                failed = true;
                return;
            }
            tts.setSpeechRate(0.95f);
            tts.setPitch(1.15f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { speakingUntil = 0; }
                @Override public void onError(String id) { speakingUntil = 0; }
                @Override public void onStop(String id, boolean interrupted) { speakingUntil = 0; }
            });
            ready = true;
            if (waiting != null) {
                String w = waiting;
                waiting = null;
                speak(w);
            }
        } catch (Throwable t) {
            Log.w(TAG, "text to speech setup failed", t);
            failed = true;
        }
    }

    private void speak(String text) {
        // if the engine never reports back, the mouth still moves for about 120 ms per character
        speakingUntil = SystemClock.uptimeMillis() + 120L * text.length();
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "movetv");
        } catch (Throwable t) {
            Log.w(TAG, "speak failed", t);
        }
    }

    /**
     * Says a word or phrase, replacing anything still being spoken.
     * @param text what to say
     */
    public synchronized void say(String text) {
        if (failed || tts == null || text == null || text.isEmpty()) return;
        if (!ready) {
            waiting = text;
            return;
        }
        speak(text);
    }

    /**
     * Whether a phrase is being spoken (the kid puppets move their mouths while it is).
     * @return true while speaking
     */
    public boolean speaking() {
        return SystemClock.uptimeMillis() < speakingUntil;
    }

    /** Stops speaking at once. */
    public synchronized void cancel() {
        waiting = null;
        speakingUntil = 0;
        if (ready && tts != null) {
            try { tts.stop(); } catch (Throwable ignored) { }
        }
    }

    /** Frees the speech engine. Call when the app closes. */
    public synchronized void release() {
        ready = false;
        waiting = null;
        if (tts != null) {
            try { tts.shutdown(); } catch (Throwable ignored) { }
            tts = null;
        }
    }
}
