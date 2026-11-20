package com.angga.movetv.input;

import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.PlayerPose;
import com.angga.movetv.pose.PoseFrame;

/**
 * Points where the hand is: {@code cursor = anchor + gain * (wrist - anchor)}, the anchor being the shoulder on
 * the same side as the hand. Gain 1.0 puts the cursor exactly on the wrist the skeleton is drawn at; a bigger
 * gain ("easy reach") reaches further than the hand does.
 * <p>
 * A small, bounded look-ahead hides the camera delay: the speed comes from the last two pose updates, the lead is
 * speed x 80 ms, capped at half a shoulder width, and it eases away over 100 ms when the hand is slow or the
 * newest pose is older than 150 ms. Plain Java; nothing is allocated after construction.
 */
public final class ReachMapping implements CursorMapping {
    static final float MIN_S = 0.3f;
    static final float LEAD_S = 0.080f;
    static final float LEAD_CAP = 0.5f;          // x shoulder width
    static final float SLOW = 0.5f;              // x shoulder width per second
    static final long STALE_MS = 150, EASE_MS = 100, GAP_MS = 400;

    private float gain;
    private long poseMs, nowMs, lastNowMs = -1;
    private long prevPoseMs = -1;
    private float prevX, prevY;
    private float vx, vy;                        // px/s from the last two poses
    private float factor;                        // 0 = no lead, 1 = full lead
    private int lastHand = -1;

    /**
     * Creates the mapping.
     * @param gain 1.0 for "on my hand", 1.6 for "easy reach"
     */
    public ReachMapping(float gain) {
        this.gain = gain;
    }

    /**
     * Changes the gain.
     * @param g the new gain
     */
    public void setGain(float g) {
        gain = g;
    }

    /**
     * @return the gain
     */
    public float gain() {
        return gain;
    }

    @Override
    public void timing(long poseTimeMs, long now) {
        poseMs = poseTimeMs;
        nowMs = now;
    }

    /** An arm held out sideways at least this many shoulder widths from its own shoulder counts as pointing. */
    static final float OUT = 0.9f;

    @Override
    public boolean reaching(PlayerPose p, int hand) {
        Keypoint wr = p.kps[hand == 0 ? Kp.L_WRIST : Kp.R_WRIST];
        Keypoint own = p.kps[hand == 0 ? Kp.L_SHOULDER : Kp.R_SHOULDER];
        float sw = PoseFrame.shoulderWidth(p);
        if (sw <= 0f || wr.s < MIN_S || own.s < MIN_S) return false;
        return Math.abs(wr.x - own.x) >= OUT * sw;
    }

    @Override
    public void reset() {
        prevPoseMs = -1;
        vx = 0f;
        vy = 0f;
        factor = 0f;
        lastHand = -1;
        lastNowMs = -1;
    }

    @Override
    public boolean map(PlayerPose p, int hand, float[] out) {
        Keypoint wr = p.kps[hand == 0 ? Kp.L_WRIST : Kp.R_WRIST];
        Keypoint own = p.kps[hand == 0 ? Kp.L_SHOULDER : Kp.R_SHOULDER];
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        float ax, ay;
        if (own.s >= MIN_S) {
            ax = own.x;
            ay = own.y;
        } else if (ls.s >= MIN_S && rs.s >= MIN_S) {
            ax = (ls.x + rs.x) * 0.5f;
            ay = (ls.y + rs.y) * 0.5f;
        } else {
            return false;
        }
        float tx = ax + gain * (wr.x - ax), ty = ay + gain * (wr.y - ay);

        float sw = PoseFrame.shoulderWidth(p);
        long dtFrame = lastNowMs < 0 ? 0 : Math.max(0, nowMs - lastNowMs);
        lastNowMs = nowMs;
        if (hand != lastHand) {
            prevPoseMs = -1;
            vx = 0f;
            vy = 0f;
            factor = 0f;
            lastHand = hand;
        }
        // a new pose update: take the speed from the two newest ones
        if (poseMs != prevPoseMs) {
            if (prevPoseMs >= 0 && poseMs > prevPoseMs && poseMs - prevPoseMs <= GAP_MS) {
                float dt = (poseMs - prevPoseMs) / 1000f;
                vx = gain * (wr.x - prevX) / dt;
                vy = gain * (wr.y - prevY) / dt;
            } else {
                vx = 0f;
                vy = 0f;
            }
            prevPoseMs = poseMs;
            prevX = wr.x;
            prevY = wr.y;
        }
        float speed = (float) Math.hypot(vx, vy);
        boolean go = sw > 0f && speed >= SLOW * sw && nowMs - poseMs <= STALE_MS;
        float step = dtFrame / (float) EASE_MS;
        factor = go ? Math.min(1f, factor + step) : Math.max(0f, factor - step);

        float lx = vx * LEAD_S, ly = vy * LEAD_S;
        float cap = LEAD_CAP * sw, len = (float) Math.hypot(lx, ly);
        if (len > cap && len > 0f) {
            lx *= cap / len;
            ly *= cap / len;
        }
        tx += lx * factor;
        ty += ly * factor;
        out[0] = clamp(tx, PoseFrame.STAGE_W);
        out[1] = clamp(ty, PoseFrame.STAGE_H);
        return true;
    }

    private static float clamp(float v, float max) {
        return v < 0f ? 0f : (v > max ? max : v);
    }
}
