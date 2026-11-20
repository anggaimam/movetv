package com.angga.movetv;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.angga.movetv.debug.DebugPoseView;
import com.angga.movetv.pose.PoseEngine;

/**
 * MoveTV proof of concept.
 * USB webcam -> on-device body tracking (MoveNet) -> skeleton + a balloon to pop.
 * Nothing leaves the TV: the app has no internet permission.
 *
 * Remote:
 *   OK            camera picture on/off (skeleton only)
 *   Left          light boost on/off (brightens dark rooms for the AI)
 *   Menu (≡)      1 player (AI follows you) or 2 players (one per half of the screen)
 */
public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 7;
    private DebugPoseView view;
    private PoseEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        view = new DebugPoseView(this);
        setContentView(view);
        engine = new PoseEngine(this, frame -> view.update(engine.cameraPicture(), frame, engine.statsText()));
        engine.setStatusListener(view::setStatus);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            view.setStatus("Waiting for camera permission…");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            engine.start();
        }
    }

    @Override
    protected void onPause() {
        engine.stop(); // releases the webcam so Zoom etc. can use it
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                engine.start();
            } else {
                view.setStatus("Camera permission denied. Allow it in Settings > Applications > MoveTV.");
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                view.toggleCamera();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                engine.toggleLightBoost();
                return true;
            case KeyEvent.KEYCODE_MENU:
                engine.setPlayers(engine.players() == 1 ? 2 : 1);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}
