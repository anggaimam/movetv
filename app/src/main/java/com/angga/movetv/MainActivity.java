package com.angga.movetv;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.angga.movetv.debug.DebugPoseView;
import com.angga.movetv.pose.PoseFrame;
import com.angga.movetv.pose.PoseHub;

/**
 * MoveTV proof of concept.
 * USB webcam (or the no-camera test body) -> PoseHub -> skeleton + a balloon to pop.
 * Nothing leaves the TV: the app has no internet permission.
 *
 * Remote:
 *   OK            camera picture on/off (skeleton only)
 *   Left          light boost on/off (brightens dark rooms for the AI)
 *   Menu (≡)      1 player (AI follows you) or 2 players (one per half of the screen);
 *                 hold it for 2 seconds to switch test mode on or off
 *
 * Keyboard (emulator): T toggles test mode. In test mode: mouse drag moves the right hand,
 * W jump, S duck, A/D lean, Space arms out, 1-9 poses, Tab switches player.
 */
public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 7;
    private static final long LONG_PRESS_MS = 2000;

    private DebugPoseView view;
    private PoseHub hub;
    private final PoseFrame frame = new PoseFrame();
    private String statsText = "";
    private long statsAt = 0;
    private boolean ticking = false;

    private long menuDownTime = -1;
    private boolean menuLongDone = false;

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
            view.update(hub.cameraPicture(), frame, statsText);
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        view = new DebugPoseView(this);
        setContentView(view);
        hub = new PoseHub();
        hub.setStatusListener(view::setStatus);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always start the hub: without the camera permission it runs in test mode until the permission is granted.
        hub.start(this);
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            view.setStatus("Waiting for camera permission…");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
        ticking = true;
        Choreographer.getInstance().postFrameCallback(ticker);
    }

    @Override
    protected void onPause() {
        ticking = false;
        hub.stop(); // releases the webcam so Zoom etc. can use it
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                view.setStatus(null);
                hub.start(this);
            } else {
                view.setStatus("Camera permission denied: test mode. Allow it in Settings > Applications > MoveTV.");
                view.postDelayed(() -> view.setStatus(null), 6000);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (hub.handleKey(event)) return true;
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getRepeatCount() == 0) {
                menuDownTime = event.getDownTime();
                menuLongDone = false;
            } else if (!menuLongDone && menuDownTime >= 0
                    && event.getEventTime() - menuDownTime >= LONG_PRESS_MS) {
                menuLongDone = true;
                hub.toggleTestMode();
            }
            return true;
        }
        if (event.getRepeatCount() > 0) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                view.toggleCamera();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                hub.toggleLightBoost();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (hub.handleKey(event)) return true;
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!menuLongDone && menuDownTime >= 0) hub.setMode(hub.players() == 1 ? 2 : 1);
            menuDownTime = -1;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (hub.handleTouch(event, view.getWidth(), view.getHeight())) return true;
        return super.onTouchEvent(event);
    }
}
