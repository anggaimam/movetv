package com.angga.movetv.pose;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * The one place that always delivers PoseFrames: from the camera (TV USB webcam, or the emulator's
 * webcam) or from a no-camera test mode where touch/mouse and keyboard act as the player's body.
 *
 * It starts in camera mode. If there is no camera, or the camera permission is missing, it switches to
 * test mode by itself. Key T (or a long press of the menu key, handled by the activity) toggles test
 * mode. In test mode the TV remote's arrows and OK do NOT leave the app's own controls: they drive the
 * test body only while test mode is on.
 */
public final class PoseHub {

    private final Object lock = new Object();
    private final TestPoseSource test = new TestPoseSource();
    private final TestPoseInput testInput = new TestPoseInput(test);

    private Context ctx;
    private PoseEngine engine;
    private boolean engineRunning;
    private boolean started;
    private boolean permissionOk = true;
    private boolean forcedTest;
    private int players = 1;
    private PoseEngine.StatusListener statusListener;

    // newest camera result (copied from the AI thread)
    private final PoseFrame camLatest = new PoseFrame();
    private boolean camHasFrame;

    /** Creates a hub. Nothing runs until {@link #start(Context)}. */
    public PoseHub() {
        camLatest.source = "camera";
    }

    /**
     * Starts (or re-checks) the camera. Safe to call again, for example after the camera permission was
     * granted: the camera starts then, unless test mode was switched on by hand.
     * @param context any context
     */
    public void start(Context context) {
        synchronized (lock) {
            ctx = context.getApplicationContext();
            started = true;
            permissionOk = Build.VERSION.SDK_INT < 23
                    || ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            if (engine == null) {
                engine = new PoseEngine(ctx, this::onCameraFrame);
                engine.setStatusListener(statusListener);
                engine.setPlayers(players);
            }
            if (!forcedTest && permissionOk && !engineRunning) {
                engine.start();
                engineRunning = true;
            }
        }
    }

    /** Stops the camera and releases it, so other apps (Zoom etc.) can use it. */
    public void stop() {
        synchronized (lock) {
            started = false;
            if (engine != null && engineRunning) engine.stop();
            engineRunning = false;
            test.releaseAll();
        }
    }

    /**
     * Sets the number of players for both the camera and the test body.
     * @param players 1 or 2
     */
    public void setMode(int players) {
        int n = players == 2 ? 2 : 1;
        synchronized (lock) {
            this.players = n;
            test.setPlayers(n);
            if (engine != null) engine.setPlayers(n);
            camHasFrame = false;
        }
    }

    /**
     * Current number of players.
     * @return 1 or 2
     */
    public int players() {
        synchronized (lock) {
            return players;
        }
    }

    /**
     * Switches test mode on by hand, or off again (then the camera is used if there is one).
     * @param on true for test mode
     */
    public void useTestMode(boolean on) {
        synchronized (lock) {
            forcedTest = on;
            if (on) {
                if (engine != null && engineRunning) engine.stop();
                engineRunning = false;
            } else {
                test.releaseAll();
                if (started && permissionOk && engine != null && !engineRunning) {
                    engine.start();
                    engineRunning = true;
                }
            }
        }
    }

    /**
     * Copies the newest PoseFrame.
     * @param dst frame to write into (reused); a new frame is created only if null
     * @return the frame that was written to; with no camera frame yet, nobody is present
     */
    public PoseFrame latest(PoseFrame dst) {
        long now = SystemClock.uptimeMillis();
        synchronized (lock) {
            if (testActive()) return test.sample(now, dst);
            if (!camHasFrame) {
                camLatest.mode = players;
                camLatest.timeMs = now;
                for (PlayerPose p : camLatest.players) {
                    p.present = false;
                    for (Keypoint k : p.kps) k.s = 0f;
                }
            }
            return camLatest.copyInto(dst);
        }
    }

    /**
     * Newest camera picture (mirrored later by the drawing code), or null.
     * @return a bitmap owned by the engine, or null in test mode or before the first frame
     */
    public Bitmap cameraPicture() {
        synchronized (lock) {
            if (testActive() || engine == null) return null;
            return engine.cameraPicture();
        }
    }

    /**
     * The camera's picture size and speed, for the speed meter.
     * @param out receives width, height and frames per second (rounded); all 0 in test mode or without a camera
     */
    public void cameraInfo(int[] out) {
        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        synchronized (lock) {
            if (testActive() || engine == null) return;
            out[0] = engine.cameraWidth();
            out[1] = engine.cameraHeight();
            out[2] = (int) (engine.cameraFps() + 0.5f);
        }
    }

    /**
     * What the hub is doing.
     * @return "camera", "test", "no camera — test mode" or "camera busy (close Zoom)"
     */
    public String status() {
        synchronized (lock) {
            if (testActive()) return forcedTest ? "test" : "no camera — test mode";
            if (engine != null && engine.cameraState() == PoseEngine.CAM_BUSY) return "camera busy (close Zoom)";
            return "camera";
        }
    }

    // ---- small additions used by the debug screen (T-01 view) and later screens ----

    /**
     * Makes the 1-player camera forget the person it follows, so whoever stands in the middle gets control.
     * Does nothing in test mode or with 2 players.
     */
    public void releaseLock() {
        synchronized (lock) {
            if (engine != null) engine.releaseLock();
        }
    }

    /**
     * Whether the 1-player camera is locked onto a person.
     * @return NONE (test mode or 2 players), SEARCHING or LOCKED
     */
    public PersonLock.State lockState() {
        synchronized (lock) {
            if (testActive() || engine == null) return PersonLock.State.NONE;
            return engine.lockState();
        }
    }

    /**
     * How many poses were dropped because they looked like another person.
     * @return the count since the app started (0 in test mode)
     */
    public int lockRejections() {
        synchronized (lock) {
            if (testActive() || engine == null) return 0;
            return engine.lockRejections();
        }
    }

    /**
     * Sets who hears about loading and error messages from the camera engine.
     * @param l the listener, or null for none
     */
    public void setStatusListener(PoseEngine.StatusListener l) {
        synchronized (lock) {
            statusListener = l;
            if (engine != null) engine.setStatusListener(l);
        }
    }

    /** Turns the camera's light boost (brightens dark rooms for the AI) on or off. */
    public void toggleLightBoost() {
        synchronized (lock) {
            if (engine != null) engine.toggleLightBoost();
        }
    }

    /**
     * One line of numbers for the debug bar.
     * @return camera stats, or a test-mode help line
     */
    public String statsText() {
        synchronized (lock) {
            if (testActive() || engine == null) {
                return status() + " · AI 12 fps · mouse = right hand · W jump · S duck · A/D lean · SPACE arms out"
                        + " · 1-9 poses · TAB player · T camera";
            }
            return engine.statsText();
        }
    }

    /**
     * Gives a key event to the hub. T toggles test mode; in test mode the test keys drive the body.
     * @param e the key event
     * @return true if the hub used the key
     */
    public boolean handleKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_T) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) toggleTestMode();
            return true;
        }
        synchronized (lock) {
            if (!testActive()) return false;
        }
        return testInput.onKey(e);
    }

    /**
     * Gives a touch or mouse event to the hub. Only used in test mode.
     * @param e the event
     * @param viewW width of the view in pixels
     * @param viewH height of the view in pixels
     * @return true if the hub used the event
     */
    public boolean handleTouch(MotionEvent e, int viewW, int viewH) {
        synchronized (lock) {
            if (!testActive()) return false;
        }
        return testInput.onTouch(e, viewW, viewH);
    }

    /** Switches between camera and test mode (what key T and the long-press of the menu key do). */
    public void toggleTestMode() {
        boolean nowTest;
        synchronized (lock) {
            nowTest = testActive();
        }
        useTestMode(!nowTest);
    }

    // ---- internals ----

    /** Test mode is on by hand, or the camera cannot be used (no permission, no camera, camera failed). */
    private boolean testActive() {
        if (forcedTest || !permissionOk) return true;
        if (engine == null || !engineRunning) return false;
        return engine.cameraState() == PoseEngine.CAM_NONE;
    }

    private void onCameraFrame(PoseFrame f) {
        synchronized (lock) {
            f.copyInto(camLatest);
            camHasFrame = true;
        }
    }
}
