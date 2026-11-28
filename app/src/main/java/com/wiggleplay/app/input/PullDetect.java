package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.PlayerPose;

/**
 * The pull-up at the bite ("!"). Plain Java, no Android, no allocation per frame.
 *
 * <p>Call {@link #arm} when the bite window opens. A pull is the active wrist rising at least 0.5 sw (0.35 sw in easy
 * mode, where crossing up through the nose line also counts) within 600 ms, over at least 3 pictures (two rising
 * steps), so one wild picture is never a pull. The pull counts when it <em>started</em> (the lowest picture of the
 * rise) inside the window, {@code windowStart <= start <= windowEnd + 250 ms} (the camera is 150 to 250 ms late).
 * All times are the pose measuring times ({@code tMs}), the same clock as {@link ActiveWrist#pictureMs}.
 * Pulls that start outside the window, or when not armed, fire nothing and are counted in {@link #earlyPulls()}.
 *
 * <p>Because the pictures arrive late, the game logic must keep listening until about 600 ms after the window ended
 * before it declares the bite missed. After any pull the history is cleared, so one motion is counted once.
 */
public final class PullDetect {

    /** Receives pulls. */
    public interface Listener {
        /** A pull started inside the armed window. */
        void onPull();
    }

    private static final float RISE = 0.5f, RISE_EASY = 0.35f, SPAN_MS = 600f, GRACE_MS = 250f, NOSE_MARGIN = 0.1f;
    private static final float RISE_WIGGLE = 0.75f, WIGGLE_STEP = 0.1f;
    private static final long WIGGLE_LOOK_MS = 1200;
    private static final int CAP = 16;

    private final ActiveWrist aw;
    private final boolean ownsWrist;
    private Listener listener;
    private boolean easy, armed;
    private long winStart, winEnd;
    private final long[] pt = new long[CAP];
    private final float[] py = new float[CAP];
    private int head, n, early, pulls, lastSwitch = -1;

    /** Creates a detector with its own {@link ActiveWrist}. */
    public PullDetect() {
        aw = new ActiveWrist();
        ownsWrist = true;
    }

    /**
     * Creates a detector that reads a shared {@link ActiveWrist}.
     * @param shared the tracker; its owner calls {@code update} first
     */
    public PullDetect(ActiveWrist shared) {
        aw = shared;
        ownsWrist = false;
    }

    /**
     * Sets who hears the pulls.
     * @param l the listener, or null
     */
    public void setListener(Listener l) {
        listener = l;
    }

    /**
     * Easy mode: a 0.35 sw rise, or crossing up through the nose line, is a pull.
     * @param on true for easy
     */
    public void setEasy(boolean on) {
        easy = on;
    }

    /**
     * Opens the bite window.
     * @param windowStartMs start of the window (pose clock, ms)
     * @param windowEndMs end of the window (pose clock, ms)
     */
    public void arm(long windowStartMs, long windowEndMs) {
        armed = true;
        winStart = windowStartMs;
        winEnd = windowEndMs;
    }

    /** Closes the window; pulls are then counted as early. */
    public void disarm() {
        armed = false;
    }

    /** Forgets the history and the counters. */
    public void reset() {
        n = 0;
        armed = false;
        early = 0;
        pulls = 0;
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
        if (!aw.newPicture()) return;
        long now = aw.pictureMs();
        pt[head] = now;
        py[head] = aw.relY();
        head = (head + 1) % CAP;
        if (n < CAP) n++;
        float cur = aw.relY();
        // lowest picture within the last 600 ms (excluding the current one)
        float low = 1e9f;
        long lowMs = 0;
        int count = 0;
        for (int k = 2; k <= n; k++) {
            int i = (head - k + CAP * 2) % CAP;
            if (now - pt[i] > SPAN_MS) break;
            count++;
            if (py[i] < low) {
                low = py[i];
                lowMs = pt[i];
            }
        }
        if (count < 2) return;
        float need = easy ? RISE_EASY : RISE;
        if (wiggling(lowMs)) need = Math.max(need, RISE_WIGGLE);
        boolean rose = cur - low >= need;
        if (easy && !rose) {
            // crossing up through the nose line: some picture in the span was clearly below it, now above it
            float noseLine = aw.noseRelY();
            rose = cur >= noseLine && low <= noseLine - NOSE_MARGIN;
        }
        if (!rose) return;
        head = 0;   // one motion is counted once
        n = 0;
        if (armed && lowMs >= winStart && lowMs <= winEnd + (long) GRACE_MS) {
            armed = false;
            pulls++;
            if (listener != null) listener.onPull();
        } else {
            early++;
        }
    }

    /** True when the hand was already going up and down before the rise started (a wiggle): then only a rise bigger than any wiggle counts. */
    private boolean wiggling(long lowMs) {
        int turns = 0;
        float prevStep = 0f;
        float prevY = Float.NaN;
        for (int k = n; k >= 1; k--) {
            int i = (head - k + CAP * 2) % CAP;
            if (pt[i] > lowMs) break;
            if (aw.pictureMs() - pt[i] > WIGGLE_LOOK_MS) continue;
            if (!Float.isNaN(prevY)) {
                float step = py[i] - prevY;
                if (Math.abs(step) >= WIGGLE_STEP) {
                    if (prevStep != 0f && Math.signum(step) != Math.signum(prevStep)) turns++;
                    prevStep = step;
                }
            }
            prevY = py[i];
        }
        return turns >= 2;
    }

    /** @return pulls that started outside the window or while not armed */
    public int earlyPulls() { return early; }
    /** @return pulls that fired */
    public int pullCount() { return pulls; }
}
