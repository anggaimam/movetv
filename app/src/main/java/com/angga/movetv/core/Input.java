package com.angga.movetv.core;

/**
 * What the player pressed or touched, as scenes see it. Plain Java, no android imports.
 *
 * The activity and the view report events as they happen ({@link #keyDown}, {@link #keyUp},
 * {@link #touch}); the game loop calls {@link #beginFrame()} once per frame, and from then on
 * {@link #pressed(int)} etc. describe "this frame". Everything runs on the main thread.
 * Key codes are Android key codes; touch positions are stage pixels (1920x1080).
 */
public final class Input {

    /** Touch action: finger or mouse button went down. */
    public static final int TOUCH_DOWN = 0;
    /** Touch action: moved while down. */
    public static final int TOUCH_MOVE = 1;
    /** Touch action: released. */
    public static final int TOUCH_UP = 2;

    private static final int MAX_KEYS = 320;

    private final boolean[] held = new boolean[MAX_KEYS];
    private final boolean[] pendingDown = new boolean[MAX_KEYS];
    private final boolean[] pendingUp = new boolean[MAX_KEYS];
    private final boolean[] pressedNow = new boolean[MAX_KEYS];
    private final boolean[] releasedNow = new boolean[MAX_KEYS];

    private boolean touchHeld, pendingTouchDown, pendingTouchUp, touchPressedNow, touchReleasedNow;
    private float touchX, touchY;

    /**
     * A key went down (auto-repeat counts as another press).
     * @param code Android key code
     */
    public void keyDown(int code) {
        if (code < 0 || code >= MAX_KEYS) return;
        held[code] = true;
        pendingDown[code] = true;
    }

    /**
     * A key was released.
     * @param code Android key code
     */
    public void keyUp(int code) {
        if (code < 0 || code >= MAX_KEYS) return;
        held[code] = false;
        pendingUp[code] = true;
    }

    /**
     * A touch or mouse event.
     * @param action TOUCH_DOWN, TOUCH_MOVE or TOUCH_UP
     * @param x stage x
     * @param y stage y
     */
    public void touch(int action, float x, float y) {
        touchX = x;
        touchY = y;
        if (action == TOUCH_DOWN) {
            touchHeld = true;
            pendingTouchDown = true;
        } else if (action == TOUCH_UP) {
            touchHeld = false;
            pendingTouchUp = true;
        }
    }

    /** Starts a new frame: events reported since the last call become "this frame's" presses and releases. */
    public void beginFrame() {
        for (int i = 0; i < MAX_KEYS; i++) {
            pressedNow[i] = pendingDown[i];
            releasedNow[i] = pendingUp[i];
            pendingDown[i] = false;
            pendingUp[i] = false;
        }
        touchPressedNow = pendingTouchDown;
        touchReleasedNow = pendingTouchUp;
        pendingTouchDown = false;
        pendingTouchUp = false;
    }

    /**
     * Whether a key went down since the last frame.
     * @param code Android key code
     * @return true for exactly one frame per press
     */
    public boolean pressed(int code) {
        return code >= 0 && code < MAX_KEYS && pressedNow[code];
    }

    /**
     * Whether a key went up since the last frame.
     * @param code Android key code
     * @return true for exactly one frame per release
     */
    public boolean released(int code) {
        return code >= 0 && code < MAX_KEYS && releasedNow[code];
    }

    /**
     * Whether a key is down right now.
     * @param code Android key code
     * @return true while held
     */
    public boolean held(int code) {
        return code >= 0 && code < MAX_KEYS && held[code];
    }

    /** @return true while a finger or mouse button is down */
    public boolean touchHeld() { return touchHeld; }

    /** @return true for the frame in which a touch started */
    public boolean touchPressed() { return touchPressedNow; }

    /** @return true for the frame in which a touch ended */
    public boolean touchReleased() { return touchReleasedNow; }

    /** @return last touch x in stage pixels */
    public float touchX() { return touchX; }

    /** @return last touch y in stage pixels */
    public float touchY() { return touchY; }
}
