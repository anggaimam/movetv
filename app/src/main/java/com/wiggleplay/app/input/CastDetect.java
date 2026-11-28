package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.PlayerPose;

/**
 * The cast gesture: hold a hand up (rod back) while the power charges, point sideways to aim, then drop the hand.
 * Lag-proof by design: hold-based, no timing window. Plain Java, no Android, no allocation per frame.
 *
 * <ul>
 *   <li><b>Rod back:</b> the active wrist at least 0.35 sw above the shoulder midpoint for 250 ms and at least 2
 *       pictures starts CHARGING. {@link #charge()} then rises 0..1 over 1.5 s. A rise and fall inside 250 ms is
 *       ignored. A wrist hovering around the line is not enough: it must stay above 0.28 sw to keep the timer.</li>
 *   <li><b>Aim:</b> {@code aimX} = mean of (wrist.x - shoulderMid.x) / (0.9 sw), clamped to -1..+1, over the 0.5 s of
 *       pictures before the release in which the hand was still near its peak, using pictures older than 150 ms
 *       (lag compensation). The game maps it to +/-35 degrees.</li>
 *   <li><b>Release:</b> the wrist falls at least 0.5 sw from its peak (any speed) or below the shoulder line. Fires
 *       {@link Listener#onCast} with {@code power} = charge at (release time - 150 ms) (at least 0.05) and
 *       {@code jerky} = the drop between the last two pictures was faster than 4 sw/s.</li>
 *   <li>Cooldown 0.6 s; the hand must go back below 0.2 sw before the next charge.</li>
 * </ul>
 * A cheer (both arms up with no drop) may enter CHARGING but never fires. See {@link ActiveWrist} for the picture rules.
 */
public final class CastDetect {

    /** Receives casts. */
    public interface Listener {
        /**
         * A cast was released.
         * @param power 0.05 to 1
         * @param aimX -1 (left on the screen) to +1
         * @param jerky true when the arm dropped faster than 4 sw/s
         */
        void onCast(float power, float aimX, boolean jerky);
    }

    /** What the detector is doing. */
    public enum State { IDLE, CHARGING }

    private static final float RISE = 0.35f, RISE_KEEP = 0.28f, REARM = 0.2f;
    private static final float HOLD_S = 0.25f, CHARGE_S = 1.5f, COOL_S = 0.6f, LAG_S = 0.15f, AIM_S = 0.5f;
    private static final float FALL = 0.5f, AIM_DIV = 0.9f, JERKY_SW_S = 4f, NEAR_PEAK = 0.3f, MIN_POWER = 0.05f;
    /** Charging starts counting this long after the wrist first crossed the line (tuned with {@code LagSim}). */
    static final float CHARGE_DELAY_S = 0.15f;
    /** The release is seen late (the fall takes a few pictures), so the aim window starts this much earlier. */
    private static final float AIM_GRACE_S = 0.25f;
    private static final int CAP = 32;

    private final ActiveWrist aw;
    private final boolean ownsWrist;
    private Listener listener;
    private State state = State.IDLE;
    private double aboveSince = -1;
    private int picsAbove;
    private double chargeStart;
    private float peak = -9f, prevRelY, prevT;
    private float cooldown;
    private boolean needLow;
    private int lastSwitch = -1;
    private final float[] bufT = new float[CAP], bufX = new float[CAP], bufY = new float[CAP];
    private int bufHead, bufN;
    private float lastPower, lastAim, aimNow;
    private int casts;
    private float charge;

    /** Creates a detector with its own {@link ActiveWrist}. */
    public CastDetect() {
        this(new ActiveWrist(), true);
    }

    /**
     * Creates a detector that reads a shared {@link ActiveWrist}; the owner calls {@code aw.update} before this one.
     * @param shared the shared wrist tracker
     */
    public CastDetect(ActiveWrist shared) {
        this(shared, false);
    }

    private CastDetect(ActiveWrist aw, boolean owns) {
        this.aw = aw;
        this.ownsWrist = owns;
    }

    /**
     * Sets who hears the casts.
     * @param l the listener, or null
     */
    public void setListener(Listener l) {
        listener = l;
    }

    /** Back to IDLE; forgets the pictures. */
    public void reset() {
        state = State.IDLE;
        aboveSince = -1;
        picsAbove = 0;
        cooldown = 0;
        needLow = false;
        bufN = 0;
        charge = 0;
        peak = -9f;
    }

    /**
     * Feeds the newest pose.
     * @param p the pose
     * @param tMs when the pose was measured
     * @param dt seconds since the last call
     */
    public void update(PlayerPose p, long tMs, float dt) {
        if (ownsWrist) aw.update(p, tMs, dt);
        double now = aw.time();
        if (cooldown > 0) cooldown -= dt;
        if (aw.switchCount() != lastSwitch) {
            lastSwitch = aw.switchCount();
            if (state == State.CHARGING || aboveSince >= 0) {
                state = State.IDLE;
                aboveSince = -1;
                picsAbove = 0;
                bufN = 0;
            }
        }
        aw.lock(state == State.CHARGING);
        if (state == State.CHARGING) charge = clamp01((float) ((now - chargeStart) / CHARGE_S));
        else charge = 0f;
        if (!aw.newPicture()) return;
        float ry = aw.relY(), rx = aw.relX();
        if (state == State.IDLE) {
            if (needLow) {
                if (ry < REARM) needLow = false;
                prevRelY = ry;
                prevT = (float) now;
                return;
            }
            if (cooldown > 0) {
                prevRelY = ry;
                prevT = (float) now;
                return;
            }
            if (ry >= RISE) {
                if (aboveSince < 0) {
                    aboveSince = now;
                    picsAbove = 0;
                    bufN = 0;
                    peak = ry;
                }
                picsAbove++;
                push((float) now, rx, ry);
                if (ry > peak) peak = ry;
                if (now - aboveSince >= HOLD_S && picsAbove >= 2) {
                    state = State.CHARGING;
                    chargeStart = aboveSince + CHARGE_DELAY_S;
                }
            } else if (ry < RISE_KEEP) {
                aboveSince = -1;
                picsAbove = 0;
                bufN = 0;
            } else if (aboveSince >= 0) {
                push((float) now, rx, ry);
            }
        } else {
            push((float) now, rx, ry);
            if (ry > peak) peak = ry;
            aimNow = clampPm1(rx / AIM_DIV);
            if (peak - ry >= FALL || ry < 0f) release((float) now, ry);
        }
        prevRelY = ry;
        prevT = (float) now;
    }

    private void release(float now, float ry) {
        float jerkSpeed = (prevRelY - ry) / Math.max(0.02f, now - prevT);
        float power = Math.max(MIN_POWER, clamp01((float) ((now - LAG_S - chargeStart) / CHARGE_S)));
        // aim: pictures of the last 0.5 s before the fall, older than 150 ms, while the hand was near its peak
        float sum = 0f;
        int n = 0;
        float lastNearX = 0f;
        boolean haveNear = false;
        for (int k = bufN; k >= 1; k--) {
            int i = (bufHead - k + CAP * 2) % CAP;
            if (bufY[i] < peak - NEAR_PEAK) continue;
            lastNearX = bufX[i];
            haveNear = true;
            if (bufT[i] <= now - LAG_S && bufT[i] >= now - LAG_S - AIM_S - AIM_GRACE_S) {
                sum += bufX[i];
                n++;
            }
        }
        float aimRel = n > 0 ? sum / n : (haveNear ? lastNearX : 0f);
        float aim = clampPm1(aimRel / AIM_DIV);
        lastPower = power;
        lastAim = aim;
        casts++;
        state = State.IDLE;
        aboveSince = -1;
        picsAbove = 0;
        bufN = 0;
        cooldown = COOL_S;
        needLow = true;
        charge = 0f;
        peak = -9f;
        if (listener != null) listener.onCast(power, aim, jerkSpeed > JERKY_SW_S);
    }

    private void push(float t, float x, float y) {
        bufT[bufHead] = t;
        bufX[bufHead] = x;
        bufY[bufHead] = y;
        bufHead = (bufHead + 1) % CAP;
        if (bufN < CAP) bufN++;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static float clampPm1(float v) { return v < -1f ? -1f : (v > 1f ? 1f : v); }

    /** @return the current state */
    public State state() { return state; }
    /** @return charge 0..1 while CHARGING (0 otherwise) */
    public float charge() { return charge; }
    /** @return the live aim -1..+1 while CHARGING, for the landing ring */
    public float aimNow() { return aimNow; }
    /** @return power of the last cast */
    public float lastPower() { return lastPower; }
    /** @return aim of the last cast */
    public float lastAimX() { return lastAim; }
    /** @return the underlying wrist tracker */
    public ActiveWrist wrist() { return aw; }
    /** @return number of casts since creation */
    public int castCount() { return casts; }
}
