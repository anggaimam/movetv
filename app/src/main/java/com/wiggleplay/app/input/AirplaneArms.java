package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.pose.PoseFrame;

/**
 * Airplane arms: both arms out to the sides like wings. The tilt of the line between the wrists steers
 * ({@link #steer()}, -1 = left on the screen), and both arms going up or down gives {@link #pitch()} (used by
 * Sky Glider later; the race ignores it). Plain Java, no Android, no allocation.
 *
 * <p>The pose is mirrored like a mirror and arrives 7 to 14 times a second; call {@link #update} every display
 * frame with the newest pose and the frame time {@code dt}. Wrists are told apart by screen position (the left and
 * right labels are not trusted). Timers run on {@code dt}, so a pose that is repeated for several frames does not
 * matter. y grows downward.
 *
 * <ul>
 *   <li><b>BOTH</b>: both wrists are confident, at least {@code minSpread} shoulder widths apart sideways (1.8, or 1.5
 *       for two players) and within 1.5 shoulder widths above or below the shoulder middle; true for 150 ms, and it
 *       stays until false for 300 ms.</li>
 *   <li><b>ONE_WING</b>: exactly one confident wrist, at least 0.9 shoulder widths out to the side. It steers alone
 *       (80% strength) from its angle against the nearest shoulder.</li>
 *   <li><b>NONE</b>: anything else; the steering eases to straight over 0.4 s.</li>
 * </ul>
 * Wing angle {@code a = atan2(wl.y - wr.y, wr.x - wl.x)} in degrees (positive when the screen-left hand is lower).
 * Raw steer is {@code -sign(a) * clamp((|a| - 6) / (28 - 6), 0, 1)}: a dead zone of 6 degrees and a full turn at 28,
 * smoothed with a time constant of 0.12 s.
 */
public final class AirplaneArms {

    /** What the arms are doing. */
    public enum State { NONE, ONE_WING, BOTH }

    private static final float MIN_CONF = 0.3f;
    private static final float DEAD_DEG = 6f, FULL_DEG = 28f;
    private static final float TAU_S = 0.12f;
    private static final float ON_S = 0.15f, OFF_S = 0.3f, EASE_OUT_S = 0.4f;
    private static final float ONE_WING_SIDE = 0.9f, ONE_WING_STRENGTH = 0.8f;
    private static final float MAX_VERTICAL = 1.5f;
    private static final float PITCH_RANGE = 0.8f, PITCH_DEAD = 0.15f;
    private static final float RAD2DEG = (float) (180.0 / Math.PI);

    private float minSpread = 1.8f;
    private State state = State.NONE;
    private boolean isBoth;
    private float onTimer, offTimer;
    private float steer, pitch, wingDeg;

    /** Starts with straight steering and no arms. */
    public AirplaneArms() { }

    /**
     * Sets how far apart the wrists must be to count as wings.
     * @param shoulderWidths 1.8 for one player (the default), 1.5 for two players standing close
     */
    public void setMinSpread(float shoulderWidths) {
        minSpread = shoulderWidths;
    }

    /** Forgets everything: straight steering, no arms. */
    public void reset() {
        state = State.NONE;
        isBoth = false;
        onTimer = 0f;
        offTimer = 0f;
        steer = 0f;
        pitch = 0f;
        wingDeg = 0f;
    }

    /**
     * Feeds the newest pose. Call once per display frame.
     * @param p the player's pose (may be null)
     * @param timeMs when the pose was measured (not needed for the timers, kept for the shared update signature)
     * @param dt seconds since the last call
     */
    public void update(PlayerPose p, long timeMs, float dt) {
        if (dt <= 0f) return;
        float sw = p != null && p.present ? PoseFrame.shoulderWidth(p) : -1f;
        if (p != null && p.present && sw <= 0f) return; // a shoulder is not seen: hold the last values

        boolean candBoth = false, candOne = false;
        float targetSteer = 0f, targetPitch = 0f, targetAngle = 0f;
        if (sw > 0f) {
            Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
            float mx = (ls.x + rs.x) * 0.5f, my = (ls.y + rs.y) * 0.5f;
            Keypoint a = p.kps[Kp.L_WRIST], b = p.kps[Kp.R_WRIST];
            boolean okA = PoseFrame.ok(a, MIN_CONF), okB = PoseFrame.ok(b, MIN_CONF);
            if (okA && okB) {
                Keypoint wl = a.x <= b.x ? a : b, wr = a.x <= b.x ? b : a;
                if (wr.x - wl.x >= minSpread * sw
                        && Math.abs(wl.y - my) <= MAX_VERTICAL * sw && Math.abs(wr.y - my) <= MAX_VERTICAL * sw) {
                    candBoth = true;
                    float deg = (float) Math.atan2(wl.y - wr.y, wr.x - wl.x) * RAD2DEG;
                    targetAngle = deg;
                    targetSteer = -Math.signum(deg) * curve(Math.abs(deg));
                    float avgY = (wl.y + wr.y) * 0.5f;
                    float v = Math.max(-1f, Math.min(1f, (my - avgY) / (PITCH_RANGE * sw)));
                    float av = Math.abs(v);
                    targetPitch = av < PITCH_DEAD ? 0f : Math.signum(v) * (av - PITCH_DEAD) / (1f - PITCH_DEAD);
                }
            } else if (okA != okB) {
                Keypoint w = okA ? a : b;
                if (Math.abs(w.x - mx) >= ONE_WING_SIDE * sw) {
                    candOne = true;
                    Keypoint sh = Math.abs(w.x - ls.x) <= Math.abs(w.x - rs.x) ? ls : rs;
                    float side = w.x < mx ? -1f : 1f;
                    float deg = (float) Math.atan2(w.y - sh.y, Math.abs(w.x - sh.x)) * RAD2DEG; // positive = wrist lower
                    targetSteer = side * Math.signum(deg) * curve(Math.abs(deg)) * ONE_WING_STRENGTH;
                    targetAngle = -side * deg; // same sign as the two-wing angle: left hand lower = positive
                }
            }
        }

        // BOTH needs 150 ms of "yes" and leaves after 300 ms of "no"
        if (candBoth) {
            onTimer += dt;
            offTimer = 0f;
        } else {
            offTimer += dt;
            onTimer = 0f;
        }
        if (!isBoth && onTimer >= ON_S) isBoth = true;
        else if (isBoth && offTimer >= OFF_S) isBoth = false;
        State next = isBoth ? State.BOTH : candOne ? State.ONE_WING : State.NONE;
        state = next;

        if (next == State.BOTH && !candBoth) {
            // a short gap inside BOTH: keep what we had
            return;
        }
        if (next == State.NONE) {
            float step = dt / EASE_OUT_S;
            steer = toward(steer, 0f, step);
            pitch = toward(pitch, 0f, step);
            wingDeg = toward(wingDeg, 0f, step * FULL_DEG);
        } else {
            float k = 1f - (float) Math.exp(-dt / TAU_S);
            steer += (targetSteer - steer) * k;
            pitch += (targetPitch - pitch) * k;
            wingDeg += (targetAngle - wingDeg) * k;
        }
    }

    private static float curve(float absDeg) {
        return Math.max(0f, Math.min(1f, (absDeg - DEAD_DEG) / (FULL_DEG - DEAD_DEG)));
    }

    private static float toward(float v, float target, float step) {
        if (v < target) return Math.min(target, v + step);
        return Math.max(target, v - step);
    }

    /** @return NONE, ONE_WING or BOTH */
    public State state() { return state; }

    /** @return steering -1 (left on the screen) to +1 (right), smoothed */
    public float steer() { return steer; }

    /** @return -1 (both arms down) to +1 (both arms up); only meaningful while the state is BOTH */
    public float pitch() { return pitch; }

    /** @return the wing tilt in degrees for the plane icon (positive = the screen-left hand is lower), smoothed */
    public float wingAngleDeg() { return wingDeg; }
}
