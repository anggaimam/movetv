package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.pose.PoseFrame;

/**
 * An invisible go-kart wheel (T-23): both hands together in front of the chest, turned like a steering wheel.
 * Plain Java, no allocation. A multi-turn wheel can't be tracked by the camera (the hands cross after about half a
 * turn), so a short turn is enough: full steer at 60 degrees.
 *
 * <ul>
 *   <li><b>Held</b> (a candidate frame): both wrists s >= 0.3, the wrists 0.5 to 2.0 shoulder widths apart (straight
 *       distance, so turning doesn't shrink it), both wrists between nose height and hip height (hips missing: the
 *       shoulder middle + 2.0 shoulder widths; nose missing: the shoulder middle - 1.0 shoulder width), and the wheel
 *       angle within +-75 degrees (steeper means the hands are above each other or crossed, and which hand is on
 *       which side can't be told any more).</li>
 *   <li>ACTIVE after 150 ms of held frames; it leaves after 300 ms without. In that 300 ms gap the steering is held
 *       (hands crossed or too close: the last steer is kept), then NONE eases it to straight over 0.4 s.</li>
 *   <li>Wheel angle {@code a = atan2(wl.y - wr.y, wr.x - wl.x)} in degrees from the screen-left wrist to the screen-right
 *       wrist (screen positions, not the labels), positive when the screen-left hand is lower. Raw steer
 *       {@code -sign(a) * clamp((|a| - 8) / (60 - 8), 0, 1)}, smoothed with a time constant of 0.18 s.</li>
 * </ul>
 */
public final class SteeringWheel implements SteerInput {

    /** Guesses to tune on the TV. */
    static final float MIN_CONF = 0.3f, MIN_SPREAD = 0.5f, MAX_SPREAD = 2.0f, HIP_FALLBACK = 2.0f,
            NOSE_FALLBACK = 1.0f, MAX_ANGLE = 75f, DEAD_DEG = 8f, FULL_DEG = 60f, TAU_S = 0.18f,
            ON_S = 0.15f, OFF_S = 0.3f, EASE_OUT_S = 0.4f;
    private static final float RAD2DEG = (float) (180.0 / Math.PI);

    private boolean active;
    private float onTimer, offTimer;
    private float steer, iconDeg;
    private float rawDeg = Float.NaN;

    @Override
    public void reset() {
        active = false;
        onTimer = 0f;
        offTimer = 0f;
        steer = 0f;
        iconDeg = 0f;
        rawDeg = Float.NaN;
    }

    @Override
    public void update(PlayerPose p, long timeMs, float dt) {
        if (dt <= 0f) return;
        boolean held = false;
        float deg = 0f;
        float sw = p != null && p.present ? PoseFrame.shoulderWidth(p) : 0f;
        if (sw > 0f) {
            Keypoint a = p.kps[Kp.L_WRIST], b = p.kps[Kp.R_WRIST];
            if (PoseFrame.ok(a, MIN_CONF) && PoseFrame.ok(b, MIN_CONF)) {
                Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
                float midY = (ls.y + rs.y) * 0.5f;
                Keypoint lh = p.kps[Kp.L_HIP], rh = p.kps[Kp.R_HIP], nose = p.kps[Kp.NOSE];
                float hipY = PoseFrame.ok(lh, MIN_CONF) && PoseFrame.ok(rh, MIN_CONF)
                        ? (lh.y + rh.y) * 0.5f : midY + HIP_FALLBACK * sw;
                float noseY = PoseFrame.ok(nose, MIN_CONF) ? nose.y : midY - NOSE_FALLBACK * sw;
                float spread = PoseFrame.dist(a, b);
                if (spread >= MIN_SPREAD * sw && spread <= MAX_SPREAD * sw
                        && a.y >= noseY && a.y <= hipY && b.y >= noseY && b.y <= hipY) {
                    Keypoint wl = a.x <= b.x ? a : b, wr = a.x <= b.x ? b : a;
                    deg = (float) Math.atan2(wl.y - wr.y, wr.x - wl.x) * RAD2DEG;
                    held = Math.abs(deg) <= MAX_ANGLE;
                }
            }
        }
        rawDeg = held ? deg : Float.NaN;
        if (held) {
            onTimer += dt;
            offTimer = 0f;
        } else {
            offTimer += dt;
            onTimer = 0f;
        }
        if (!active && onTimer >= ON_S) active = true;
        else if (active && offTimer >= OFF_S) active = false;

        if (active && !held) return; // the gap: keep the last steer
        if (active) {
            float target = -Math.signum(deg) * curve(Math.abs(deg));
            float k = 1f - (float) Math.exp(-dt / TAU_S);
            steer += (target - steer) * k;
            iconDeg += (deg - iconDeg) * k;
        } else {
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
    public State steerState() { return active ? State.ACTIVE : State.NONE; }

    @Override
    public float iconAngleDeg() { return iconDeg; }

    @Override
    public float rawAngleDeg() { return rawDeg; }

    @Override
    public String modeName() { return "wheel"; }
}
