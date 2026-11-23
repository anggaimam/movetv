package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.pose.PoseFrame;

/**
 * Body lean (T-23): arms down, tilt the shoulders like leaning on a bike. Plain Java, no allocation.
 *
 * <p>The angle of the shoulder line (shoulders told apart by screen position, both s >= 0.3) is
 * {@code a = atan2(sl.y - sr.y, sr.x - sl.x)} in degrees, positive when the screen-left shoulder is lower (the stage is
 * mirrored, so that is a lean to the left). Raw steer is {@code -sign(a) * clamp((|a| - 4) / (20 - 4), 0, 1)}, smoothed
 * with a time constant of 0.15 s. ACTIVE whenever both shoulders are seen; otherwise the steering eases to straight
 * over 0.4 s. A crouch lowers both shoulders by the same amount, so it does not change the angle.
 *
 * <p>Sky Glider's shoulder steering ({@code SkyLogic.shoulderAngle/steerFor}) uses the same angle with +-4/+-22 and sits
 * in the game's package, so this class has its own copy with the T-23 numbers (+-4/+-20).
 */
public final class BodyLean implements SteerInput {

    /** Guesses to tune on the TV. */
    static final float MIN_CONF = 0.3f, DEAD_DEG = 4f, FULL_DEG = 20f, TAU_S = 0.15f, EASE_OUT_S = 0.4f;
    private static final float RAD2DEG = (float) (180.0 / Math.PI);

    private State state = State.NONE;
    private float steer, iconDeg;
    private float rawDeg = Float.NaN;

    @Override
    public void reset() {
        state = State.NONE;
        steer = 0f;
        iconDeg = 0f;
        rawDeg = Float.NaN;
    }

    @Override
    public void update(PlayerPose p, long timeMs, float dt) {
        if (dt <= 0f) return;
        boolean seen = false;
        float deg = 0f;
        if (p != null && p.present) {
            Keypoint a = p.kps[Kp.L_SHOULDER], b = p.kps[Kp.R_SHOULDER];
            if (PoseFrame.ok(a, MIN_CONF) && PoseFrame.ok(b, MIN_CONF) && Math.abs(a.x - b.x) > 1f) {
                Keypoint sl = a.x <= b.x ? a : b, sr = a.x <= b.x ? b : a;
                deg = (float) Math.atan2(sl.y - sr.y, sr.x - sl.x) * RAD2DEG;
                seen = true;
            }
        }
        if (seen) {
            state = State.ACTIVE;
            rawDeg = deg;
            float target = -Math.signum(deg) * curve(Math.abs(deg));
            float k = 1f - (float) Math.exp(-dt / TAU_S);
            steer += (target - steer) * k;
            iconDeg += (deg - iconDeg) * k;
        } else {
            state = State.NONE;
            rawDeg = Float.NaN;
            float step = dt / EASE_OUT_S;
            steer = toward(steer, 0f, step);
            iconDeg = toward(iconDeg, 0f, step * FULL_DEG);
        }
    }

    static float curve(float absDeg) {
        return Math.max(0f, Math.min(1f, (absDeg - DEAD_DEG) / (FULL_DEG - DEAD_DEG)));
    }

    private static float toward(float v, float target, float step) {
        if (v < target) return Math.min(target, v + step);
        return Math.max(target, v - step);
    }

    @Override
    public float steer() { return steer; }

    @Override
    public State steerState() { return state; }

    @Override
    public float iconAngleDeg() { return iconDeg; }

    @Override
    public float rawAngleDeg() { return rawDeg; }

    @Override
    public String modeName() { return "lean"; }
}
