package com.angga.movetv.input;

import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.PlayerPose;
import com.angga.movetv.pose.PoseFrame;

/**
 * A pointer driven by a raised wrist. A {@link CursorMapping} turns the wrist into a screen position (the
 * comfort-box mapping unless another is set), then a One-Euro filter smooths it. Plain Java; not final so
 * tests can fake it.
 */
public class HandCursor {
    static final float MIN_WRIST_S = 0.3f, MIN_ELBOW_S = 0.2f;
    static final long LOSE_MS = 400, SWITCH_MS = 300;
    static final float SWITCH_PX = 60f;

    private final int playerId;
    private float rx = 0f, ry = 0f, rw = PoseFrame.STAGE_W, rh = PoseFrame.STAGE_H;
    private final ComfortBoxMapping comfort = new ComfortBoxMapping();
    private CursorMapping mapping = comfort;
    private final float[] target = new float[2];
    private float wristX, wristY;
    private final OneEuro fx = new OneEuro(1.2f, 0.02f, 1.0f), fy = new OneEuro(1.2f, 0.02f, 1.0f);
    private boolean visible;
    private int hand = -1;
    private float x, y, speed;
    private long lastRaisedMs;
    private long switchSinceMs = -1;

    public HandCursor(int playerId) {
        this.playerId = playerId;
        x = rx + rw * 0.5f;
        y = ry + rh * 0.5f;
    }

    /** @return the player this cursor belongs to */
    public int playerId() { return playerId; }

    /** Sets the screen rectangle the comfort zone is mapped onto (stage px). Only the comfort-box mapping uses it. */
    public void setRegion(float x, float y, float w, float h) {
        rx = x;
        ry = y;
        rw = w;
        rh = h;
        comfort.setRegion(x, y, w, h);
    }

    /**
     * Chooses how the wrist is turned into a screen position.
     * @param m the mapping (null goes back to the comfort box)
     */
    public void setMapping(CursorMapping m) {
        CursorMapping n = m != null ? m : comfort;
        if (n == mapping) return;
        mapping = n;
        mapping.reset();
    }

    /** @return wrist x in stage px as last mapped (for the debug line) */
    public float wristX() { return wristX; }

    /** @return wrist y in stage px as last mapped (for the debug line) */
    public float wristY() { return wristY; }

    /** @return true while a raised hand is tracked */
    public boolean visible() { return visible; }

    /** @return cursor x in stage px */
    public float x() { return x; }

    /** @return cursor y in stage px */
    public float y() { return y; }

    /** @return -1 none, 0 left hand, 1 right hand */
    public int hand() { return hand; }

    /** @return cursor speed in px/s */
    public float speed() { return speed; }

    private boolean raised(PlayerPose p, int h) {
        Keypoint w = p.kps[h == 0 ? Kp.L_WRIST : Kp.R_WRIST];
        Keypoint e = p.kps[h == 0 ? Kp.L_ELBOW : Kp.R_ELBOW];
        if (w.s >= MIN_WRIST_S && e.s >= MIN_ELBOW_S && w.y < e.y) return true;
        return mapping.reaching(p, h);
    }

    /**
     * Advances the cursor by one display frame.
     * @param dt seconds since the last call
     * @param p newest pose of this cursor's player (may be null)
     * @param nowMs clock in ms
     */
    public void update(float dt, PlayerPose p, long nowMs) {
        update(dt, p, nowMs, nowMs);
    }

    /**
     * Advances the cursor by one display frame, telling the mapping when the pose was measured.
     * @param dt seconds since the last call
     * @param p newest pose of this cursor's player (may be null)
     * @param nowMs clock in ms
     * @param poseTimeMs when that pose was measured (same clock as nowMs)
     */
    public void update(float dt, PlayerPose p, long nowMs, long poseTimeMs) {
        if (dt <= 0f) dt = 1f / 60f;
        boolean rl = p != null && p.present && raised(p, 0);
        boolean rr = p != null && p.present && raised(p, 1);
        if (!rl && !rr) {
            switchSinceMs = -1;
            speed = 0f;
            if (visible && nowMs - lastRaisedMs > LOSE_MS) {
                visible = false;
                hand = -1;
                fx.reset();
                fy.reset();
                mapping.reset();
            }
            return;
        }
        lastRaisedMs = nowMs;
        Keypoint lw = p.kps[Kp.L_WRIST], rwk = p.kps[Kp.R_WRIST];
        if (rl && rr) {
            if (hand == -1) {
                hand = lw.y <= rwk.y ? 0 : 1;
                switchSinceMs = -1;
            } else {
                Keypoint cur = hand == 0 ? lw : rwk, oth = hand == 0 ? rwk : lw;
                if (oth.y <= cur.y - SWITCH_PX) {
                    if (switchSinceMs < 0) switchSinceMs = nowMs;
                    if (nowMs - switchSinceMs >= SWITCH_MS) {
                        hand = 1 - hand;
                        switchSinceMs = -1;
                    }
                } else {
                    switchSinceMs = -1;
                }
            }
        } else {
            hand = rl ? 0 : 1;
            switchSinceMs = -1;
        }
        boolean wasVisible = visible;
        visible = true;

        Keypoint wr = hand == 0 ? lw : rwk;
        wristX = wr.x;
        wristY = wr.y;
        mapping.timing(poseTimeMs, nowMs);
        if (!mapping.map(p, hand, target)) {   // cannot map: freeze where we are
            speed = 0f;
            return;
        }
        float tx = target[0], ty = target[1];

        if (!wasVisible) {
            fx.reset();
            fy.reset();
        }
        float ox = x, oy = y;
        x = fx.filter(tx, dt);
        y = fy.filter(ty, dt);
        speed = wasVisible ? (float) Math.hypot(x - ox, y - oy) / dt : 0f;
    }
}
