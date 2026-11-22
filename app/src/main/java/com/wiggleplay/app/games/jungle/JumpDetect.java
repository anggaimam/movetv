package com.wiggleplay.app.games.jungle;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.pose.PoseFrame;

/**
 * Finds jumps and ducks from the head and shoulders only (knees and ankles are too unreliable on this camera).
 * Plain Java, no Android, no allocation after construction.
 *
 * <p>For the first 2 seconds of calm standing ("Stand tall!") it measures a baseline: the middle value of the
 * nose height and of the shoulder-midpoint height, and the shoulder width as the unit of size. After that, the
 * baseline follows slow changes with a time constant of 8 seconds (so stepping toward or away from the camera
 * is tolerated) but never while the player is jumping, ducking or moving fast.
 *
 * <ul>
 *   <li><b>Jump</b>: the shoulder midpoint is at least 0.35 shoulder widths above the baseline and it got there
 *       within the last 400 ms (at least 0.2 widths of that rise happened inside the window). One event per jump.</li>
 *   <li><b>Duck</b>: the nose is at least 0.6 shoulder widths below the baseline; it stays a duck until the nose is
 *       back above 0.4 widths.</li>
 * </ul>
 * Poses arrive 7 to 14 times a second; call {@link #update} every display frame with the pose and its time, repeated
 * poses (same time) are ignored. Coordinates are stage pixels, y grows downward.
 */
public final class JumpDetect {

    /** Shoulders must rise this far above the baseline (in shoulder widths) for a jump. */
    public static final float JUMP_RISE = 0.35f;
    /** A rise this big counts as a "big jump" (needed to clear a double log). */
    public static final float BIG_RISE = 0.5f;
    /** The nose must drop this far below the baseline to start a duck. */
    public static final float DUCK_DROP = 0.6f;

    private static final float MIN_CONF = 0.3f;
    private static final long CAL_MS = 2000;
    private static final long CAL_GIVE_UP_MS = 8000;
    private static final long WINDOW_MS = 400;
    private static final float QUICK_RISE = 0.2f;
    private static final float JUMP_END_RISE = 0.12f;
    private static final float DUCK_END_DROP = 0.4f;
    private static final float TAU_S = 8f;
    private static final long LOST_MS = 3000;
    private static final int CAP = 256;
    private static final int HIST = 32;

    // calibration samples
    private final float[] calNose = new float[CAP], calSh = new float[CAP], calW = new float[CAP];
    private final float[] scratch = new float[CAP];
    private int calN;
    private long calStartMs = -1, calFirstTryMs = -1;

    // short history of shoulder heights for the 400 ms window
    private final long[] histT = new long[HIST];
    private final float[] histY = new float[HIST];
    private int histHead, histN;

    private boolean calibrated;
    private float baseNose, baseSh, scale;
    private long lastTimeMs = Long.MIN_VALUE, lastValidMs = Long.MIN_VALUE;
    private boolean jumping, ducking, jumpEvent;
    private static final long LONG_AGO = -1_000_000L;
    private long jumpStartMs = LONG_AGO, jumpEndMs = LONG_AGO;
    private float peak, rise, drop;

    /** Starts empty: nothing is calibrated. */
    public JumpDetect() {
        reset();
    }

    /** Forgets everything, so the next 2 calm seconds become the new baseline. */
    public void reset() {
        calN = 0;
        calStartMs = -1;
        calFirstTryMs = -1;
        histHead = 0;
        histN = 0;
        calibrated = false;
        jumping = false;
        ducking = false;
        jumpEvent = false;
        peak = 0f;
        rise = 0f;
        drop = 0f;
        lastTimeMs = Long.MIN_VALUE;
        lastValidMs = Long.MIN_VALUE;
        jumpStartMs = LONG_AGO;
        jumpEndMs = LONG_AGO;
    }

    /** @return true once the baseline is measured; no jump or duck is reported before that */
    public boolean calibrated() { return calibrated; }

    /** @return how far the calibration has got, 0..1 (1 when done) */
    public float calibrationProgress() {
        if (calibrated) return 1f;
        if (calStartMs < 0 || calN == 0) return 0f;
        return Math.min(1f, (lastTimeMs - calStartMs) / (float) CAL_MS);
    }

    /**
     * Feeds the newest pose. Repeated poses (same time) are ignored.
     * @param p the player's pose (may be null)
     * @param timeMs when the pose was measured
     */
    public void update(PlayerPose p, long timeMs) {
        if (timeMs == lastTimeMs) return;
        long prev = lastTimeMs;
        lastTimeMs = timeMs;
        if (p == null || !p.present) {
            checkLost(timeMs);
            return;
        }
        Keypoint nose = p.kps[Kp.NOSE];
        float w = PoseFrame.shoulderWidth(p);
        if (w <= 0f || !PoseFrame.ok(nose, MIN_CONF)) {
            checkLost(timeMs);
            return;
        }
        float sh = (p.kps[Kp.L_SHOULDER].y + p.kps[Kp.R_SHOULDER].y) * 0.5f;
        lastValidMs = timeMs;
        if (!calibrated) {
            calibrate(nose.y, sh, w, timeMs);
            return;
        }
        float dtS = prev == Long.MIN_VALUE ? 0f : Math.min(0.5f, Math.max(0f, (timeMs - prev) / 1000f));
        step(nose.y, sh, w, timeMs, dtS);
    }

    private void checkLost(long timeMs) {
        if (lastValidMs != Long.MIN_VALUE && timeMs - lastValidMs > LOST_MS) {
            // somebody else may be standing there now: measure again
            boolean wasCal = calibrated;
            reset();
            lastTimeMs = timeMs;
            if (wasCal) lastValidMs = Long.MIN_VALUE;
        }
    }

    private void calibrate(float noseY, float shY, float w, long t) {
        if (calFirstTryMs < 0) calFirstTryMs = t;
        if (calStartMs < 0) calStartMs = t;
        if (calN < CAP) {
            calNose[calN] = noseY;
            calSh[calN] = shY;
            calW[calN] = w;
            calN++;
        }
        boolean enough = t - calStartMs >= CAL_MS && calN >= 4;
        boolean full = calN >= CAP;
        if (!enough && !full) return;
        float medW = median(calW, calN);
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int i = 0; i < calN; i++) {
            lo = Math.min(lo, calSh[i]);
            hi = Math.max(hi, calSh[i]);
        }
        boolean still = hi - lo <= 0.5f * medW;
        boolean giveUp = t - calFirstTryMs >= CAL_GIVE_UP_MS;
        if (!still && !giveUp && !full) {
            calN = 0;          // moving about: start the two seconds again
            calStartMs = t;
            return;
        }
        baseNose = median(calNose, calN);
        baseSh = median(calSh, calN);
        scale = medW;
        calibrated = true;
        histN = 0;
        histHead = 0;
    }

    private float median(float[] a, int n) {
        System.arraycopy(a, 0, scratch, 0, n);
        for (int i = 1; i < n; i++) {           // insertion sort: runs once per calibration
            float v = scratch[i];
            int j = i - 1;
            while (j >= 0 && scratch[j] > v) {
                scratch[j + 1] = scratch[j];
                j--;
            }
            scratch[j + 1] = v;
        }
        return scratch[n / 2];
    }

    private void step(float noseY, float shY, float w, long t, float dtS) {
        rise = (baseSh - shY) / scale;
        drop = (noseY - baseNose) / scale;
        // how much of the rise happened inside the last 400 ms
        float lowest = shY;
        for (int i = 0; i < histN; i++) {
            int k = (histHead - 1 - i + HIST) % HIST;
            if (t - histT[k] > WINDOW_MS) break;
            if (histY[k] > lowest) lowest = histY[k];
        }
        float quick = (lowest - shY) / scale;
        histT[histHead] = t;
        histY[histHead] = shY;
        histHead = (histHead + 1) % HIST;
        if (histN < HIST) histN++;

        if (jumping) {
            if (rise > peak) peak = rise;
            if (rise < JUMP_END_RISE || t - jumpStartMs > 1500) {
                jumping = false;
                jumpEndMs = t;
            }
        } else if (!ducking && rise >= JUMP_RISE && quick >= QUICK_RISE && t - jumpEndMs > 250) {
            jumping = true;
            jumpEvent = true;
            jumpStartMs = t;
            peak = rise;
        }

        if (ducking) {
            if (drop < DUCK_END_DROP) ducking = false;
        } else if (!jumping && drop >= DUCK_DROP) {
            ducking = true;
        }

        // slow baseline: only while standing calmly
        if (!jumping && !ducking && rise < 0.2f && drop < 0.2f && quick < 0.1f && dtS > 0f) {
            float a = 1f - (float) Math.exp(-dtS / TAU_S);
            baseSh += (shY - baseSh) * a;
            baseNose += (noseY - baseNose) * a;
            scale += (w - scale) * a;
        }
    }

    /**
     * Whether a new jump started since the last call (true once per jump).
     * @return true for a new jump
     */
    public boolean consumeJump() {
        boolean j = jumpEvent;
        jumpEvent = false;
        return j;
    }

    /** @return true while the nose is low (a duck) */
    public boolean ducking() { return ducking; }

    /** @return true while the shoulders are up in a jump */
    public boolean jumping() { return jumping; }

    /** @return the highest rise of the current or last jump, in shoulder widths */
    public float peakRise() { return peak; }

    /** @return the shoulders' height above the baseline now, in shoulder widths (negative = below) */
    public float rise() { return rise; }

    /** @return the nose's depth below the baseline now, in shoulder widths (negative = above) */
    public float drop() { return drop; }

    /** @return the shoulder width the baseline is measured in (0 before calibration) */
    public float scale() { return calibrated ? scale : 0f; }
}
