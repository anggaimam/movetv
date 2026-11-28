package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.pose.PoseFrame;

/**
 * The aim while fighting a fish: where the child points the rod while cranking. Plain Java, no Android, no allocation
 * per frame.
 *
 * <p>{@link #aim()} = the centre of the cranking circles ({@link CycleDetect#centreX()}), smoothed over 0.2 s and
 * lead-compensated against the camera delay: smoothed + {@value #LEAD_S} s x the smoothed velocity, the correction clamped
 * to +/-0.25. In <b>two-hand mode</b> ({@link #setTwoHandMode}) the x of the hand that is <em>not</em> cranking is
 * used when both wrists are above the hips (a two-handed rod hold); otherwise the circle centre is used. Both modes
 * are implemented; the gesture lab (F-02) picks the default.
 */
public final class AimFollow {

    static final float LEAD_S = 0.8f;
    /** The directly measured other hand needs only the camera-delay lead (a bigger one amplifies its noise). */
    static final float LEAD_TWO_S = 0.15f;
    static final float SMOOTH_S = 0.2f;
    private static final float MAX_LEAD = 0.25f, AIM_DIV = 0.9f, MIN_CONF = 0.3f;

    private final ActiveWrist aw;
    private final CycleDetect cycle;
    private final boolean owns;
    private boolean twoHand;
    private float smooth, vel, aim, other;
    private boolean haveOther, init;

    /** Creates a detector with its own {@link ActiveWrist} and {@link CycleDetect}. */
    public AimFollow() {
        aw = new ActiveWrist();
        cycle = new CycleDetect(aw);
        owns = true;
    }

    /**
     * Creates a detector that reads shared trackers; their owner updates them first.
     * @param shared the wrist tracker
     * @param cycleDetect the crank detector built on that tracker
     */
    public AimFollow(ActiveWrist shared, CycleDetect cycleDetect) {
        aw = shared;
        cycle = cycleDetect;
        owns = false;
    }

    /**
     * Chooses the aim source.
     * @param on true to aim with the hand that is not cranking
     */
    public void setTwoHandMode(boolean on) {
        twoHand = on;
    }

    /** Forgets the smoothing. */
    public void reset() {
        init = false;
        smooth = vel = aim = 0f;
        haveOther = false;
    }

    /**
     * Feeds the newest pose.
     * @param p the pose
     * @param tMs when the pose was measured
     * @param dt seconds since the last call
     */
    public void update(PlayerPose p, long tMs, float dt) {
        if (owns) {
            aw.update(p, tMs, dt);
            cycle.update(p, tMs, dt);
        }
        if (twoHand && aw.newPicture() && p != null && p.present) pickOther(p);
        float raw = cycle.centreX();
        if (twoHand && haveOther) raw = other;
        if (!init) {
            init = true;
            smooth = raw;
            vel = 0f;
        } else {
            float a = 1f - (float) Math.exp(-dt / SMOOTH_S);
            float prev = smooth;
            smooth += (raw - smooth) * a;
            vel += ((smooth - prev) / Math.max(dt, 1e-4f) - vel) * (1f - (float) Math.exp(-dt / 0.25f));
        }
        float lead = Math.max(-MAX_LEAD, Math.min(MAX_LEAD, (twoHand ? LEAD_TWO_S : LEAD_S) * vel));
        aim = Math.max(-1f, Math.min(1f, smooth + lead));
    }

    private void pickOther(PlayerPose p) {
        Keypoint a = p.kps[Kp.L_WRIST], b = p.kps[Kp.R_WRIST];
        haveOther = false;
        if (!PoseFrame.ok(a, MIN_CONF) || !PoseFrame.ok(b, MIN_CONF)) return;
        float hipY = (p.kps[Kp.L_HIP].y + p.kps[Kp.R_HIP].y) * 0.5f;
        if (a.y > hipY || b.y > hipY) return;
        Keypoint l = a.x <= b.x ? a : b, r = a.x <= b.x ? b : a;
        Keypoint o = aw.side() == ActiveWrist.LEFT ? r : l;     // the wrist that is not cranking
        float v = (o.x - aw.shoulderMidX()) / aw.sw() / AIM_DIV;
        other = Math.max(-1f, Math.min(1f, v));
        haveOther = true;
    }

    /** @return the aim, -1 (left on the screen) to +1, ready to compare with the fish position on the same scale */
    public float aim() { return aim; }
    /** @return the crank detector */
    public CycleDetect cycle() { return cycle; }
}
