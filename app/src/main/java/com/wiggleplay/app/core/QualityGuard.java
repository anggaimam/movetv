package com.wiggleplay.app.core;

/**
 * Decides the picture quality (T-28 D2). Plain Java, fed with the time of every frame, so it is unit-tested on the JVM.
 * <ul>
 * <li>Picture "high" and "low" are fixed: the level never changes by itself.</li>
 * <li>Picture "auto" drops from HIGH (0) to LOW (1), once, when the speed stays under 45 fps for a 3 s window.
 * The first 3 s after a scene change never count (loading), and a window that holds one frame over 250 ms
 * (a loading hitch, a GC, a texture upload) is thrown away and started again.</li>
 * </ul>
 */
public final class QualityGuard {

    /** Picture setting: switch to LOW by itself when play is slow. */
    public static final int AUTO = 0;
    /** Picture setting: always HIGH. */
    public static final int FORCE_HIGH = 1;
    /** Picture setting: always LOW. */
    public static final int FORCE_LOW = 2;

    /** Level HIGH (same value as {@code GameView.HIGH}). */
    public static final int HIGH = 0;
    /** Level LOW (same value as {@code GameView.LOW}). */
    public static final int LOW = 1;

    /** Seconds after a scene change that never count. */
    public static final float GRACE_S = 3f;
    /** A frame longer than this restarts the window. */
    public static final float SPIKE_MS = 250f;
    /** Under this many frames per second the window counts as slow. */
    public static final float LOW_FPS = 45f;
    /** Length of the window in seconds. */
    public static final float WINDOW_S = 3f;

    private int mode = AUTO;
    private boolean dropped = false;
    private float sceneT = 0f;
    private float winT = 0f;
    private int winFrames = 0;
    private int droppedAtS = -1;

    /**
     * Turns a saved Picture value into a mode.
     * @param key "auto", "high" or "low" (anything else, even null, is auto)
     * @return AUTO, FORCE_HIGH or FORCE_LOW
     */
    public static int modeOf(String key) {
        if ("high".equals(key)) return FORCE_HIGH;
        if ("low".equals(key)) return FORCE_LOW;
        return AUTO;
    }

    /**
     * Sets the Picture mode. A change of mode forgets an earlier automatic drop.
     * @param m AUTO, FORCE_HIGH or FORCE_LOW (anything else is AUTO)
     */
    public void setMode(int m) {
        int clean = m == FORCE_HIGH || m == FORCE_LOW ? m : AUTO;
        if (clean != mode) {
            mode = clean;
            dropped = false;
        }
    }

    /**
     * The Picture mode.
     * @return AUTO, FORCE_HIGH or FORCE_LOW
     */
    public int mode() { return mode; }

    /**
     * The level to draw at now.
     * @return HIGH (0) or LOW (1)
     */
    public int level() {
        if (mode == FORCE_HIGH) return HIGH;
        if (mode == FORCE_LOW) return LOW;
        return dropped ? LOW : HIGH;
    }

    /** Starts the grace time and a new window (call when another scene becomes current). */
    public void onSceneChange() {
        sceneT = 0f;
        winT = 0f;
        winFrames = 0;
        droppedAtS = -1;
    }

    /**
     * Seconds since the last scene change.
     * @return seconds
     */
    public float sceneSeconds() { return sceneT; }

    /**
     * The second, counted from the scene change, at which the automatic drop happened in this scene.
     * @return the second, or -1 if it did not happen in this scene
     */
    public int droppedAtSeconds() { return droppedAtS; }

    /**
     * Feeds one frame.
     * @param ms time since the previous frame in milliseconds (0 or less is ignored: a pause)
     * @return true when the level has just dropped from HIGH to LOW
     */
    public boolean onFrame(float ms) {
        if (ms <= 0f) return false;
        sceneT += ms / 1000f;
        if (mode != AUTO || dropped) return false;
        if (sceneT < GRACE_S || ms > SPIKE_MS) {
            winT = 0f;
            winFrames = 0;
            return false;
        }
        winT += ms / 1000f;
        winFrames++;
        if (winT < WINDOW_S) return false;
        float fps = winFrames / winT;
        winT = 0f;
        winFrames = 0;
        if (fps < LOW_FPS) {
            dropped = true;
            droppedAtS = (int) (sceneT + 0.5f);
            return true;
        }
        return false;
    }
}
