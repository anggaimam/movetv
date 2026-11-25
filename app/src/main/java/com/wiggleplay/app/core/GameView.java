package com.wiggleplay.app.core;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.wiggleplay.app.gl.RaceGlView;
import com.wiggleplay.app.pose.PoseHub;

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
     * The current quality level. Scenes and loaders read it. It is set at every scene change from the Picture
     * setting; with Picture on auto it also goes from HIGH to LOW once (see {@link QualityGuard}) and stays LOW.
     */
    public static volatile int quality = HIGH;

    private final PoseHub hub;
    private final SceneManager scenes;
    private final PerfOverlay perf;
    private final Input input = new Input();
    private final Scene.Frame frame = new Scene.Frame(input);

    private float scale = 1f, offX = 0f, offY = 0f;
    private long lastNanos = 0;
    private final QualityGuard guard = new QualityGuard();
    private Profile profile;

    private RaceGlView gl;
    private boolean glWanted = false;
    private final Runnable applyGl = new Runnable() {
        @Override
        public void run() {
            if (gl == null) return;
            if (glWanted) gl.show();
            else gl.hide();
        }
    };

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
        // No background of its own: the black letterbox comes from the activity's root layout, so the GL view
        // underneath shows through while a 3D scene is current.
        setFocusable(true);
        scenes.setSwapListener(new SceneManager.SwapListener() {
            @Override
            public void beforeEnter(String name) {
                onSceneChanged();
            }
        });
    }

    /**
     * Connects the GL view that sits under this view. It is shown while the current scene returns
     * {@link Scene#world3d()} true and hidden otherwise.
     * @param view the GL view (may be null to disconnect)
     */
    public void setGlView(RaceGlView view) {
        gl = view;
        glWanted = false;
        perf.setGlVisible(false);
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

    private long displayAt = 0;

    @Override
    protected void onDraw(Canvas c) {
        long nowN = SystemClock.elapsedRealtimeNanos();
        float dt = lastNanos == 0 ? 0f : Math.min(0.05f, (nowN - lastNanos) / 1_000_000_000f);
        float rawMs = lastNanos == 0 ? 0f : (nowN - lastNanos) / 1_000_000f;
        lastNanos = nowN;
        perf.onFrame(nowN);
        if (guard.onFrame(rawMs)) {
            quality = guard.level();
            showQualityMode();
        }

        boolean want = gl != null && scenes.world3d();
        if (want != glWanted) { // switch the GL view on the next message-loop turn, not in the middle of a draw
            glWanted = want;
            perf.setGlVisible(want);
            post(applyGl);
        }

        frame.nowMs = SystemClock.uptimeMillis();
        if (perf.isVisible() && frame.nowMs - displayAt >= 1000) {   // T-30 Gate 0: what the screen itself is set to
            displayAt = frame.nowMs;
            android.view.Display d = getDisplay();
            if (d != null) {
                android.view.Display.Mode m = d.getMode();
                perf.setDisplay(d.getRefreshRate(), m.getPhysicalWidth(), m.getPhysicalHeight());
            }
        }
        hub.latest(frame.pose);
        frame.camera = hub.cameraPicture();
        if (perf.isVisible()) perf.setLock(hub.lockState().ordinal(), hub.lockRejections());
        input.beginFrame();
        long t0 = System.nanoTime();
        scenes.update(dt, frame);
        long t1 = System.nanoTime();

        c.save();
        c.translate(offX, offY);
        c.scale(scale, scale);
        c.clipRect(0f, 0f, Stage.W, Stage.H);
        scenes.draw(c);
        long t2 = System.nanoTime();
        if (perf.isVisible()) perf.setTiming(scenes.currentName(), (t1 - t0) / 1_000_000f, (t2 - t1) / 1_000_000f);
        perf.draw(c, frame.nowMs, frame.pose.aiFps, quality);
        c.restore();

        postInvalidateOnAnimation(); // keep drawing at the TV's refresh rate
    }

    /** Gives the view the saved settings, so the Picture setting is read at every scene change. */
    public void setProfile(Profile p) {
        profile = p;
    }

    /**
     * A new scene is about to be entered (the scene manager calls this just before {@code enter}, so the scene already sees the
     * quality it will run at): restart the grace time, read the Picture setting and clear the speed meter.
     */
    private void onSceneChanged() {
        guard.setMode(QualityGuard.modeOf(profile != null ? profile.quality() : null));
        guard.onSceneChange();
        quality = guard.level();
        perf.reset();
        showQualityMode();
    }

    private void showQualityMode() {
        perf.setQualityMode(guard.mode() == QualityGuard.AUTO ? "auto" : "forced", guard.droppedAtSeconds());
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
