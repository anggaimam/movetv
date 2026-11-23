package com.wiggleplay.app.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.wiggleplay.app.pose.PoseFrame;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * One screen of the app (a menu, a game). A scene draws in stage coordinates (1920x1080); the
 * {@link GameView} scales the stage to the real screen. Nothing may be allocated in {@code update} or
 * {@code draw}.
 */
public interface Scene {

    /**
     * Called when the scene becomes the current one (after the fade-out of the previous scene).
     * @param args what the caller passed to {@link SceneManager#go}; never null
     */
    void enter(SceneArgs args);

    /** Called when the scene is replaced (free what {@link #enter} created). */
    void exit();

    /**
     * Advances the scene by one frame.
     * @param dt seconds since the last frame, at most 0.05
     * @param f this frame's pose, camera picture, time and input
     */
    void update(float dt, Frame f);

    /**
     * Draws the scene. The canvas is already scaled so that (0,0)-(1920,1080) is the stage.
     * @param c the canvas
     */
    void draw(Canvas c);

    /**
     * Whether this scene draws a 3D world with OpenGL under the Canvas. When true, the {@link GameView} shows the
     * GL view and leaves the stage transparent, so the scene must not paint an opaque full-screen background.
     * @return true for a 3D scene; false (the default) for every ordinary screen
     */
    default boolean world3d() {
        return false;
    }

    /** Everything a scene needs to know about the current frame. */
    final class Frame {
        /** The newest pose, copied once per frame (stage pixels, mirrored). */
        public final PoseFrame pose = new PoseFrame();
        /** The newest camera picture (not yet mirrored), or null (test mode, no camera). Do not modify. */
        public Bitmap camera;
        /** SystemClock.uptimeMillis() at the start of the frame. */
        public long nowMs;
        /** Keys and touches of this frame. */
        public final Input input;

        /**
         * Creates a frame holder.
         * @param input the input state shared by all frames
         */
        public Frame(Input input) {
            this.input = input;
        }
    }

    /** A small immutable map of named values handed to a scene when it is entered. */
    final class SceneArgs {
        /** No arguments. */
        public static final SceneArgs EMPTY = new SceneArgs(Collections.<String, Object>emptyMap());

        private final Map<String, Object> map;

        private SceneArgs(Map<String, Object> map) {
            this.map = map;
        }

        /**
         * Builds arguments from key/value pairs.
         * @param keyValues alternating String keys and values, for example {@code of("level", 2, "players", 1)}
         * @return the arguments
         */
        public static SceneArgs of(Object... keyValues) {
            if (keyValues.length % 2 != 0) throw new IllegalArgumentException("keys and values must come in pairs");
            Map<String, Object> m = new HashMap<>();
            for (int i = 0; i < keyValues.length; i += 2) {
                if (!(keyValues[i] instanceof String)) throw new IllegalArgumentException("keys must be Strings");
                m.put((String) keyValues[i], keyValues[i + 1]);
            }
            return new SceneArgs(Collections.unmodifiableMap(m));
        }

        /**
         * Looks a value up.
         * @param key the name
         * @return the value, or null
         */
        public Object get(String key) {
            return map.get(key);
        }

        /**
         * Looks a text value up.
         * @param key the name
         * @param def returned when missing or not a String
         * @return the text
         */
        public String getString(String key, String def) {
            Object o = map.get(key);
            return o instanceof String ? (String) o : def;
        }

        /**
         * Looks a number up.
         * @param key the name
         * @param def returned when missing or not a number
         * @return the number as an int
         */
        public int getInt(String key, int def) {
            Object o = map.get(key);
            return o instanceof Number ? ((Number) o).intValue() : def;
        }

        /**
         * Whether a name is present.
         * @param key the name
         * @return true if present
         */
        public boolean has(String key) {
            return map.containsKey(key);
        }
    }
}
