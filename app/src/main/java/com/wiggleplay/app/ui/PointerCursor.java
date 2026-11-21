package com.wiggleplay.app.ui;

import com.wiggleplay.app.core.Input;
import com.wiggleplay.app.input.HandCursor;

/**
 * The menu cursor in test mode: it sits exactly under the mouse or finger instead of going through the
 * comfort-box mapping that {@link HandCursor} applies to a real wrist. It shows while the pointer is down and
 * for 2 seconds after it is released, like the test arm. It keeps {@code playerId()}, {@code visible()},
 * {@code x()}, {@code y()} (and {@code hand()}, {@code speed()}) so {@link com.wiggleplay.app.input.DwellButtons}
 * can use it as it is. Nothing in {@link HandCursor}'s own mapping is changed.
 */
public final class PointerCursor extends HandCursor {

    /** How long the cursor stays after the pointer is released, like the test arm. */
    public static final long HIDE_AFTER_MS = 2000;

    private boolean visible;
    private float x, y, speed;
    private long hideAtMs = -1;

    /**
     * Creates the cursor.
     * @param playerId the player it belongs to
     */
    public PointerCursor(int playerId) {
        super(playerId);
    }

    /**
     * Advances the cursor by one display frame.
     * @param dt seconds since the last call
     * @param in the input state (touch position is in stage pixels)
     * @param nowMs clock in ms
     */
    public void updatePointer(float dt, Input in, long nowMs) {
        float ox = x, oy = y;
        if (in.touchHeld() || in.touchPressed()) {
            visible = true;
            hideAtMs = -1;
            x = in.touchX();
            y = in.touchY();
        } else if (visible && hideAtMs < 0) {
            hideAtMs = nowMs + HIDE_AFTER_MS;
        }
        if (visible && hideAtMs >= 0 && nowMs >= hideAtMs) {
            visible = false;
            hideAtMs = -1;
        }
        speed = visible && dt > 0f ? (float) Math.hypot(x - ox, y - oy) / dt : 0f;
    }

    @Override
    public boolean visible() { return visible; }

    @Override
    public float x() { return x; }

    @Override
    public float y() { return y; }

    @Override
    public int hand() { return visible ? 1 : -1; }

    @Override
    public float speed() { return speed; }
}
