package com.angga.movetv.input;

import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.PlayerPose;
import com.angga.movetv.pose.PoseFrame;

/**
 * The T-04 mapping: the wrist inside a comfort box around the body is stretched over a screen region.
 * Plain Java. It is the default of {@link HandCursor}.
 */
public final class ComfortBoxMapping implements CursorMapping {
    private float rx = 0f, ry = 0f, rw = PoseFrame.STAGE_W, rh = PoseFrame.STAGE_H;

    /**
     * Sets the screen rectangle the comfort box is stretched over.
     * @param x left
     * @param y top
     * @param w width
     * @param h height
     */
    public void setRegion(float x, float y, float w, float h) {
        rx = x;
        ry = y;
        rw = w;
        rh = h;
    }

    @Override
    public boolean map(PlayerPose p, int hand, float[] out) {
        float sw = PoseFrame.shoulderWidth(p);
        if (sw <= 0f) return false;
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
        Keypoint wr = p.kps[hand == 0 ? Kp.L_WRIST : Kp.R_WRIST];
        float u = clamp01((wr.x - (cx - bw * 0.5f)) / bw);
        float v = clamp01((wr.y - top) / bh);
        out[0] = rx + u * rw;
        out[1] = ry + v * rh;
        return true;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
