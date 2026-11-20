package com.angga.movetv.pose;

/**
 * Keeps the 1-player AI on one person. MoveNet SinglePose only ever reports one person, so it can jump to
 * someone else who walks by; this class decides whether a new pose still belongs to the person we locked onto.
 * <p>
 * States: {@link State#NONE} (switched off, for example in 2 players), {@link State#SEARCHING} (nobody locked;
 * the engine looks at the centre of the picture) and {@link State#LOCKED}. The first good pose locks. A later
 * pose is rejected as "someone else" when, compared with the last accepted pose, the shoulder midpoint moved
 * more than 1.2 shoulder widths in one update, or the shoulder width changed outside 0.65 to 1.5 times and the
 * midpoint moved more than 0.6 shoulder widths. After 2 s without an accepted pose the lock is released.
 * Plain Java (no android imports); methods are synchronized because the camera thread and the main thread both use it.
 */
public final class PersonLock {

    /** Lock state. */
    public enum State { NONE, SEARCHING, LOCKED }

    static final float MIN_S = 0.3f;
    static final int MIN_POINTS = 5;
    static final float JUMP = 1.2f;
    static final float RATIO_LO = 0.65f, RATIO_HI = 1.5f, RATIO_MOVE = 0.6f;
    static final long RELEASE_MS = 2000;

    private State state = State.NONE;
    private float lastMx, lastMy, lastSw;
    private long lastAcceptMs;
    private int rejections;

    /**
     * Switches the lock on (1 player) or off (2 players). Switching on starts in SEARCHING.
     * @param on true for 1-player mode
     */
    public synchronized void setEnabled(boolean on) {
        if (on) {
            if (state == State.NONE) state = State.SEARCHING;
        } else {
            state = State.NONE;
        }
    }

    /** Forgets the locked person at once; the next good pose locks. Does nothing while switched off. */
    public synchronized void release() {
        if (state != State.NONE) state = State.SEARCHING;
    }

    /**
     * Current state.
     * @return NONE, SEARCHING or LOCKED
     */
    public synchronized State state() {
        return state;
    }

    /**
     * Number of poses rejected as "someone else" so far.
     * @return the count
     */
    public synchronized int rejections() {
        return rejections;
    }

    /**
     * Checks a new pose from the AI.
     * @param xs keypoint x (any units, one value per keypoint, COCO order)
     * @param ys keypoint y (same units)
     * @param sc keypoint confidence 0..1
     * @param nowMs the current time in ms
     * @return true if the pose belongs to the locked person (or locks now); false if it must be dropped
     *         (someone else, or not a good pose). Also releases the lock after 2 s without an accepted pose.
     */
    public synchronized boolean offer(float[] xs, float[] ys, float[] sc, long nowMs) {
        if (state == State.NONE) return true;
        int good = 0;
        for (int k = 0; k < Kp.COUNT; k++) if (sc[k] >= MIN_S) good++;
        boolean haveSw = sc[Kp.L_SHOULDER] >= MIN_S && sc[Kp.R_SHOULDER] >= MIN_S;
        float sw = haveSw ? (float) Math.hypot(xs[Kp.L_SHOULDER] - xs[Kp.R_SHOULDER], ys[Kp.L_SHOULDER] - ys[Kp.R_SHOULDER]) : 0f;
        if (good < MIN_POINTS || sw <= 0f) {
            timeout(nowMs);
            return false;
        }
        float mx = (xs[Kp.L_SHOULDER] + xs[Kp.R_SHOULDER]) * 0.5f;
        float my = (ys[Kp.L_SHOULDER] + ys[Kp.R_SHOULDER]) * 0.5f;
        if (state == State.SEARCHING) {
            accept(mx, my, sw, nowMs);
            state = State.LOCKED;
            return true;
        }
        float move = (float) Math.hypot(mx - lastMx, my - lastMy);
        float ratio = sw / lastSw;
        boolean other = move > JUMP * lastSw
                || ((ratio < RATIO_LO || ratio > RATIO_HI) && move > RATIO_MOVE * lastSw);
        if (other) {
            rejections++;
            timeout(nowMs);
            return false;
        }
        accept(mx, my, sw, nowMs);
        return true;
    }

    /**
     * Call when the AI found nobody at all, so the 2 s release timer keeps running.
     * @param nowMs the current time in ms
     */
    public synchronized void tick(long nowMs) {
        timeout(nowMs);
    }

    private void accept(float mx, float my, float sw, long nowMs) {
        lastMx = mx;
        lastMy = my;
        lastSw = sw;
        lastAcceptMs = nowMs;
    }

    private void timeout(long nowMs) {
        if (state == State.LOCKED && nowMs - lastAcceptMs >= RELEASE_MS) state = State.SEARCHING;
    }
}
