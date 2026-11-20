package com.angga.movetv;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.angga.movetv.audio.Music;
import com.angga.movetv.audio.Sfx;
import com.angga.movetv.core.DemoScene;
import com.angga.movetv.core.GameView;
import com.angga.movetv.core.PerfOverlay;
import com.angga.movetv.core.Scene;
import com.angga.movetv.core.SceneManager;
import com.angga.movetv.core.SpriteCache;
import com.angga.movetv.debug.DebugPoseView;
import com.angga.movetv.pose.PoseFrame;
import com.angga.movetv.pose.PoseHub;

/**
 * MoveTV. USB webcam (or the no-camera test body) -> PoseHub -> the game screen.
 * Nothing leaves the TV: the app has no internet permission.
 *
 * Remote:
 *   OK, hold 2 s  switch between the game screen (DemoScene) and the old debug view
 *   OK            DemoScene: play the chosen sound effect (debug view: camera picture on/off)
 *   Up/Down       DemoScene: choose a sound effect
 *   Right         DemoScene: next song
 *   Left          light boost on/off (brightens dark rooms for the AI);  hold 2 s = performance overlay on/off
 *   Menu (≡)      1 player (AI follows you) or 2 players (one per half of the screen);
 *                 hold it for 2 seconds to switch test mode on or off
 *
 * Keyboard (emulator): P overlay, M song, F1-F12 and Z X C V sound effects, T test mode. In test mode:
 * mouse drag moves the right hand, W jump, S duck, A/D lean, Space arms out, 1-9 poses, Tab switches player.
 */
public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 7;
    private static final long LONG_PRESS_MS = 2000;

    private GameView gameView;
    private DebugPoseView debugView;
    private boolean debugMode = false;
    private PoseHub hub;
    private PerfOverlay perf;
    private Sfx sfx;
    private Music music;

    // debug view feed
    private final PoseFrame frame = new PoseFrame();
    private String statsText = "";
    private long statsAt = 0;
    private boolean ticking = false;

    private final Hold menuKey = new Hold(), okKey = new Hold(), leftKey = new Hold();

    /** Tells a short press of a key from a 2-second hold. */
    private static final class Hold {
        private long downTime = -1;
        private boolean longDone = false;

        /** @return true at the moment a hold reaches 2 seconds */
        boolean down(KeyEvent e) {
            if (e.getRepeatCount() == 0) {
                downTime = e.getDownTime();
                longDone = false;
                return false;
            }
            if (!longDone && downTime >= 0 && e.getEventTime() - downTime >= LONG_PRESS_MS) {
                longDone = true;
                return true;
            }
            return false;
        }

        /** @return true if the key was released before the 2 seconds were up */
        boolean up() {
            boolean shortPress = downTime >= 0 && !longDone;
            downTime = -1;
            return shortPress;
        }
    }

    private final Choreographer.FrameCallback ticker = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!ticking) return;
            hub.latest(frame);
            long now = SystemClock.uptimeMillis();
            if (now - statsAt >= 250) { // the text changes slowly: do not rebuild it 60 times a second
                statsText = hub.statsText();
                statsAt = now;
            }
            debugView.update(hub.cameraPicture(), frame, statsText);
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hub = new PoseHub();
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        perf = new PerfOverlay(debuggable);
        sfx = new Sfx(this);
        music = new Music();
        SceneManager scenes = new SceneManager();
        scenes.register("demo", new DemoScene(perf, sfx, music));
        gameView = new GameView(this, hub, scenes, perf);
        debugView = new DebugPoseView(this);
        hub.setStatusListener(debugView::setStatus);
        setContentView(gameView);
        scenes.go("demo", Scene.SceneArgs.EMPTY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always start the hub: without the camera permission it runs in test mode until the permission is granted.
        hub.start(this);
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            debugView.setStatus("Waiting for camera permission…");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
        if (debugMode) startTicker();
    }

    @Override
    protected void onPause() {
        ticking = false;
        music.stop(0);
        hub.stop(); // releases the webcam so Zoom etc. can use it
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        sfx.release();
        music.release();
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        SpriteCache.shared().clear();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                debugView.setStatus(null);
                hub.start(this);
            } else {
                debugView.setStatus("Camera permission denied: test mode. Allow it in Settings > Applications > MoveTV.");
                debugView.postDelayed(() -> debugView.setStatus(null), 6000);
            }
        }
    }

    private void startTicker() {
        ticking = true;
        Choreographer.getInstance().postFrameCallback(ticker);
    }

    /** Switches between the game screen and the old debug view. */
    private void setDebugMode(boolean on) {
        debugMode = on;
        if (on) {
            music.stop(200);
            setContentView(debugView);
            startTicker();
        } else {
            ticking = false;
            setContentView(gameView);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (hub.handleKey(event)) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                if (menuKey.down(event)) hub.toggleTestMode();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (okKey.down(event)) setDebugMode(!debugMode);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (leftKey.down(event)) gameView.togglePerf();
                return true;
            case KeyEvent.KEYCODE_P:
                if (event.getRepeatCount() == 0) gameView.togglePerf();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (!debugMode && event.getRepeatCount() == 0) gameView.input().keyDown(keyCode);
                return true;
            default:
                if (!debugMode) gameView.input().keyDown(keyCode);
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (hub.handleKey(event)) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                if (menuKey.up()) hub.setMode(hub.players() == 1 ? 2 : 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (okKey.up()) {
                    if (debugMode) {
                        debugView.toggleCamera();
                    } else { // a short press reaches the scene as one press
                        gameView.input().keyDown(keyCode);
                        gameView.input().keyUp(keyCode);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (leftKey.up()) hub.toggleLightBoost();
                return true;
            case KeyEvent.KEYCODE_P:
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (!debugMode) gameView.input().keyUp(keyCode);
                return true;
            default:
                if (!debugMode) gameView.input().keyUp(keyCode);
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // the game screen handles touches itself; this is for the debug view
        if (hub.handleTouch(event, debugView.getWidth(), debugView.getHeight())) return true;
        return super.onTouchEvent(event);
    }
}
