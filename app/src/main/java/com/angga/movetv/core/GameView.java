package com.angga.movetv.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.angga.movetv.pose.PoseHub;

/**
 * The one screen every scene draws on. Hardware accelerated, redrawn at the TV's refresh rate
 * ({@code postInvalidateOnAnimation}). Each frame it reads the newest pose from the {@link PoseHub},
 * updates the current scene and draws it on the fixed 1920x1080 {@link Stage}, scaled to the view and
 * letterboxed (black bars) on any window shape. Nothing is allocated per frame.
 */
public final class GameView extends View {

    /** Quality level: everything on. */
    public static final int HIGH = 0;
    /** Quality level: half the particles, no parallax layers. Switched on automatically when the game runs slowly. */
    public static final int LOW = 1;

    /**
     * The current quality level. Scenes and loaders read it; it goes from HIGH to LOW once, when the draw
     * speed stays under 45 fps for 3 seconds in a row, and never goes back during the session.
     */
    public static volatile int quality = HIGH;

    private static final float LOW_FPS = 45f;
    private static final int LOW_SECONDS = 3;

    private final PoseHub hub;
    private final SceneManager scenes;
    private final PerfOverlay perf;
    private final Input input = new Input();
    private final Scene.Frame frame = new Scene.Frame(input);

    private float scale = 1f, offX = 0f, offY = 0f;
    private long lastNanos = 0;
    private int slowSeconds = 0;

    /**
     * Creates the view.
     * @param context any context
     * @param hub where the poses and the camera picture come from
     * @param scenes the registered scenes
     * @param perf the performance overlay (drawn on top of the scene)
     */
    public GameView(Context context, PoseHub hub, SceneManager scenes, PerfOverlay perf) {
        super(context);
        this.hub = hub;
        this.scenes = scenes;
        this.perf = perf;
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
    }

    /**
     * The input state scenes read; the activity feeds it key events.
     * @return the input
     */
    public Input input() {
        return input;
    }

    /** Shows or hides the performance overlay. */
    public void togglePerf() {
        perf.toggle();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        scale = Math.min(w / Stage.W, h / Stage.H);
        offX = (w - Stage.W * scale) / 2f;
        offY = (h - Stage.H * scale) / 2f;
    }

    /** Forgets frame timing, so a pause or a screen switch is not counted as a slow game. */
    private void resetTiming() {
        lastNanos = 0;
        slowSeconds = 0;
        perf.reset();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        resetTiming();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) resetTiming();
    }

    @Override
    protected void onDraw(Canvas c) {
        long nowN = SystemClock.elapsedRealtimeNanos();
        float dt = lastNanos == 0 ? 0f : Math.min(0.05f, (nowN - lastNanos) / 1_000_000_000f);
        lastNanos = nowN;
        if (perf.onFrame(nowN)) checkQuality(perf.fps());

        frame.nowMs = SystemClock.uptimeMillis();
        hub.latest(frame.pose);
        frame.camera = hub.cameraPicture();
        if (perf.isVisible()) perf.setLock(hub.lockState().ordinal(), hub.lockRejections());
        input.beginFrame();
        scenes.update(dt, frame);

        c.save();
        c.translate(offX, offY);
        c.scale(scale, scale);
        c.clipRect(0f, 0f, Stage.W, Stage.H);
        scenes.draw(c);
        perf.draw(c, frame.nowMs, frame.pose.aiFps, quality);
        c.restore();

        postInvalidateOnAnimation(); // keep drawing at the TV's refresh rate
    }

    /** Once a second: after 3 slow seconds in a row, switch to LOW quality for the rest of the session. */
    private void checkQuality(float fps) {
        if (quality != HIGH) return;
        if (fps > 0f && fps < LOW_FPS) {
            if (++slowSeconds >= LOW_SECONDS) quality = LOW;
        } else {
            slowSeconds = 0;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = (e.getX() - offX) / scale, y = (e.getY() - offY) / scale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                input.touch(Input.TOUCH_DOWN, x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                input.touch(Input.TOUCH_MOVE, x, y);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                input.touch(Input.TOUCH_UP, x, y);
                break;
            default:
                break;
        }
        hub.handleTouch(e, getWidth(), getHeight()); // in test mode the touch also moves the test body's hand
        return true;
    }
}
