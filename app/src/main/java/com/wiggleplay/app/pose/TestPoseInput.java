package com.wiggleplay.app.pose;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Turns keyboard and touch/mouse events into calls on a {@link TestPoseSource}.
 *
 * Keys: W or Up = jump, S or Down = duck (held), A or Left = lean left (held), D or Right = lean right
 * (held), Space = arms out (held), 1-9 = preset poses (held), Tab = switch player in 2 players.
 * Remote only (T-06): Play/Pause switches arms out (T-pose) on and off; Rewind and Fast-forward step
 * through the preset poses (none, 1 Star ... 9 Lean-left, then round again).
 * Touch or mouse: the position moves the right wrist.
 */
public final class TestPoseInput {

    private final TestPoseSource source;
    private boolean armsOutLatched;
    private int stepPreset; // preset chosen with Rewind / Fast-forward: 0 = none, 1..9

    /**
     * Creates the adapter.
     * @param source the test source to drive
     */
    public TestPoseInput(TestPoseSource source) {
        this.source = source;
    }

    /**
     * Handles a key event.
     * @param e the event
     * @return true if the key belongs to the test pose (the caller should not handle it further)
     */
    public boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = e.getAction() == KeyEvent.ACTION_UP;
        if (!down && !up) return false;
        boolean first = down && e.getRepeatCount() == 0;
        switch (code) {
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:
                if (first) source.jump(SystemClock.uptimeMillis());
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                source.setHeld(TestPoseSource.CTRL_DUCK, down);
                return true;
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                source.setHeld(TestPoseSource.CTRL_LEAN_LEFT, down);
                return true;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                source.setHeld(TestPoseSource.CTRL_LEAN_RIGHT, down);
                return true;
            case KeyEvent.KEYCODE_SPACE:
                source.setHeld(TestPoseSource.CTRL_ARMS_OUT, down);
                return true;
            case KeyEvent.KEYCODE_TAB:
                if (first) source.switchPlayer();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (first) {
                    armsOutLatched = !armsOutLatched;
                    source.setHeld(TestPoseSource.CTRL_ARMS_OUT, armsOutLatched);
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (first) stepPreset(code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? 1 : -1);
                return true;
            default:
                if (code >= KeyEvent.KEYCODE_1 && code <= KeyEvent.KEYCODE_9) {
                    source.setPreset(code - KeyEvent.KEYCODE_1 + 1, down);
                    return true;
                }
                return false;
        }
    }

    /** Steps the remote's preset pose: 0 (none), 1 Star ... 9 Lean-left, then round again. */
    private void stepPreset(int dir) {
        if (stepPreset > 0) source.setPreset(stepPreset, false);
        stepPreset = (stepPreset + dir + 10) % 10;
        if (stepPreset > 0) source.setPreset(stepPreset, true);
    }

    /**
     * Handles a touch or mouse event: the pointer position drives the right wrist.
     * @param e the event
     * @param viewW width of the view the event came from, in pixels
     * @param viewH height of the view the event came from, in pixels
     * @return true if the event was used
     */
    public boolean onTouch(MotionEvent e, int viewW, int viewH) {
        if (viewW <= 0 || viewH <= 0) return false;
        // same letterbox as the view: the 1920x1080 stage is centred and scaled to fit
        float scale = Math.min(viewW / PoseFrame.STAGE_W, viewH / PoseFrame.STAGE_H);
        float left = (viewW - PoseFrame.STAGE_W * scale) / 2f;
        float top = (viewH - PoseFrame.STAGE_H * scale) / 2f;
        float x = clamp((e.getX() - left) / scale, 0f, PoseFrame.STAGE_W);
        float y = clamp((e.getY() - top) / scale, 0f, PoseFrame.STAGE_H);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                source.pointerDown(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                source.pointerMove(x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                source.pointerUp(SystemClock.uptimeMillis());
                return true;
            default:
                return false;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
