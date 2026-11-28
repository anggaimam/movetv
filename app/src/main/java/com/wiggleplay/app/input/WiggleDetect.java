package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.PlayerPose;

/**
 * The wiggle gesture (twitching the rod tip): small up-and-down hand movements. Plain Java, no Android, no allocation
 * per frame.
 *
 * <p>A sliding 1.0 s window of the active wrist's height (one value per picture). The height path is measured as a
 * zig-zag: a leg only counts once the hand has moved at least the dead band from the last turning point (so camera
 * jitter of +/-8 px never counts), and a single leg is capped at 0.5 sw. {@link #wiggle()} =
 * clamp(path / 1.2 sw, 0, 1) when there are at least 2 direction reversals in the window; a window whose
 * peak-to-peak height exceeds 0.7 sw is a pull or a lift, not a wiggle, and gives 0. The window empties when the
 * active hand changes.
 */
public final class WiggleDetect {

    /** Legs shorter than this (sw) are noise. The ticket says 0.04; 0.15 is used because +/-8 px of noise on the wrist and both shoulders at a shoulder width of 200 px makes legs of 0.04 to 0.1 (tuned with {@code LagSim}). */
    static final float DEAD_BAND = 0.15f;
    private static final float WINDOW_S = 1.0f, LEG_CAP = 0.5f, FULL_PATH = 1.2f, MAX_P2P = 0.7f;
    private static final int CAP = 24;

    private final ActiveWrist aw;
    private final boolean ownsWrist;
    private final float[] t = new float[CAP], y = new float[CAP];
    private int head, n, lastSwitch = -1;
    private float wiggle, smooth;

    /** Creates a detector with its own {@link ActiveWrist}. */
    public WiggleDetect() {
        aw = new ActiveWrist();
        ownsWrist = true;
    }

    /**
     * Creates a detector that reads a shared {@link ActiveWrist}.
     * @param shared the tracker; its owner calls {@code update} first
     */
    public WiggleDetect(ActiveWrist shared) {
        aw = shared;
        ownsWrist = false;
    }

    /** Forgets the window. */
    public void reset() {
        n = 0;
        wiggle = 0f;
        smooth = 0f;
    }

    /**
     * Feeds the newest pose.
     * @param p the pose
     * @param tMs when the pose was measured
     * @param dt seconds since the last call
     */
    public void update(PlayerPose p, long tMs, float dt) {
        if (ownsWrist) aw.update(p, tMs, dt);
        if (aw.switchCount() != lastSwitch) {
            lastSwitch = aw.switchCount();
            n = 0;
        }
        float now = aw.time();
        if (aw.newPicture()) {
            t[head] = now;
            y[head] = aw.relY();
            head = (head + 1) % CAP;
            if (n < CAP) n++;
            wiggle = compute(now);
        } else if (n > 0) {
            wiggle = compute(now);      // the window slides on without pictures
        }
        // a short release so one lucky picture does not flicker the bar
        smooth = wiggle > smooth ? wiggle : smooth + (wiggle - smooth) * Math.min(1f, dt * 6f);
    }

    private float compute(float now) {
        float ref = Float.NaN, ext = 0f, path = 0f, lo = 1e9f, hi = -1e9f;
        int dir = 0, reversals = 0;
        for (int k = n; k >= 1; k--) {
            int i = (head - k + CAP * 2) % CAP;
            if (now - t[i] > WINDOW_S) continue;
            float v = y[i];
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
            if (Float.isNaN(ref)) {
                ref = v;
                ext = v;
                continue;
            }
            if (dir == 0) {
                if (Math.abs(v - ref) >= DEAD_BAND) {
                    dir = v > ref ? 1 : -1;
                    ext = v;
                }
            } else if (dir > 0) {
                if (v > ext) ext = v;
                else if (ext - v >= DEAD_BAND) {
                    path += Math.min(Math.abs(ext - ref), LEG_CAP);
                    ref = ext;
                    ext = v;
                    dir = -1;
                    reversals++;
                }
            } else {
                if (v < ext) ext = v;
                else if (v - ext >= DEAD_BAND) {
                    path += Math.min(Math.abs(ext - ref), LEG_CAP);
                    ref = ext;
                    ext = v;
                    dir = 1;
                    reversals++;
                }
            }
        }
        if (Float.isNaN(ref) || reversals < 2 || hi - lo > MAX_P2P) return 0f;
        path += Math.min(Math.abs(ext - ref), LEG_CAP);
        return Math.max(0f, Math.min(1f, path / FULL_PATH));
    }

    /** @return the wiggle strength 0..1 (the lure attraction input) */
    public float wiggle() { return smooth; }
}
