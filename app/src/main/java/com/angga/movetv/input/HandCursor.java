package com.angga.movetv.input;

import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.PlayerPose;
import com.angga.movetv.pose.PoseFrame;

/**
 * A pointer driven by a raised wrist. The wrist is mapped from a comfort zone anchored to the body
 * into a screen region, then smoothed with a One-Euro filter. Plain Java; not final so tests can fake it.
 */
public class HandCursor {
    static final float MIN_WRIST_S = 0.3f, MIN_ELBOW_S = 0.2f;
    static final long LOSE_MS = 400, SWITCH_MS = 300;
    static final float SWITCH_PX = 60f;

    private final int playerId;
    private float rx = 0f, ry = 0f, rw = PoseFrame.STAGE_W, rh = PoseFrame.STAGE_H;
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

    /** Sets the screen rectangle the comfort zone is mapped onto (stage px). */
    public void setRegion(float x, float y, float w, float h) {
        rx = x;
        ry = y;
        rw = w;
        rh = h;
    }

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

    private static boolean raised(PlayerPose p, int h) {
        Keypoint w = p.kps[h == 0 ? Kp.L_WRIST : Kp.R_WRIST];
        Keypoint e = p.kps[h == 0 ? Kp.L_ELBOW : Kp.R_ELBOW];
        return w.s >= MIN_WRIST_S && e.s >= MIN_ELBOW_S && w.y < e.y;
    }

    /**
     * Advances the cursor by one display frame.
     * @param dt seconds since the last call
     * @param p newest pose of this cursor's player (may be null)
     * @param nowMs clock in ms
     */
    public void update(float dt, PlayerPose p, long nowMs) {
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

        float sw = PoseFrame.shoulderWidth(p);
        if (sw <= 0f) {            // no usable body size: freeze where we are
            speed = 0f;
            return;
        }
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        float midX = (ls.x + rs.x) * 0.5f, midY = (ls.y + rs.y) * 0.5f;
        // Screen is mirrored: the person's left hand is on the screen's left (smaller x).
        float cx = midX + (hand == 0 ? -0.35f : 0.35f) * sw;
        float bw = 2.6f * sw;
        Keypoint nose = p.kps[Kp.NOSE];
        float noseY = nose.s >= 0.3f ? nose.y : midY - 0.8f * sw;  // local choice: nose missing
        float top = noseY - 1.2f * sw;
        Keypoint lh = p.kps[Kp.L_HIP], rh2 = p.kps[Kp.R_HIP];
        float bottom = (lh.s >= 0.3f && rh2.s >= 0.3f) ? (lh.y + rh2.y) * 0.5f : midY + 2.0f * sw;
        float bh = Math.max(bottom - top, 1f);
        Keypoint wr = hand == 0 ? lw : rwk;
        float u = clamp01((wr.x - (cx - bw * 0.5f)) / bw);
        float v = clamp01((wr.y - top) / bh);
        float tx = rx + u * rw, ty = ry + v * rh;

        if (!wasVisible) {
            fx.reset();
            fy.reset();
        }
        float ox = x, oy = y;
        x = fx.filter(tx, dt);
        y = fy.filter(ty, dt);
        speed = wasVisible ? (float) Math.hypot(x - ox, y - oy) / dt : 0f;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
