package com.wiggleplay.app.games.bowling;

import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

/**
 * Finds an underhand bowling swing in the body points. Plain Java, no Android. Either arm can bowl.
 *
 * <p>Everything is measured relative to the shoulders and in shoulder widths (sw), so it works for small and big
 * players, near to and far from the camera. Stage y grows downward. For each arm:
 * <ol>
 * <li><b>Down:</b> the wrist is below the hip (or, with no hip seen, more than 1.2 sw below the shoulder).</li>
 * <li><b>Armed:</b> it has stayed down for {@link #ARM_MS} ms (a swing back).</li>
 * <li><b>Rising:</b> the wrist moves up at {@link #MIN_UP_SPEED} sw per second or faster.</li>
 * <li><b>Release:</b> once it has risen at least {@link #MIN_RISE} sw, the ball goes when the wrist passes chest height
 * ({@link #RELEASE_BELOW_SHOULDER} sw below the shoulder) or when the upward speed drops off after its peak, whichever
 * comes first.</li>
 * </ol>
 * An arm hanging still, a slow lift or waving above the head never releases. Power comes from the fastest upward speed
 * (1.5 to 5 sw/s gives 0 to 1); spin from the wrist's sideways speed at the release.
 */
public final class SwingDetect {

    /** Time the wrist must stay down before a swing can start. */
    public static final long ARM_MS = 150;
    /** Slowest upward wrist speed that counts as a swing, and the speed that gives full power (sw per second). */
    public static final float MIN_UP_SPEED = 1.5f, FULL_UP_SPEED = 5f;
    /** The wrist must rise this far (sw) from its lowest point before the ball can go. */
    public static final float MIN_RISE = 0.5f;
    /** Release height: this far below the shoulder (sw). */
    public static final float RELEASE_BELOW_SHOULDER = 0.6f;
    /** With no hip seen, the wrist counts as down this far below the shoulder (sw). */
    public static final float DOWN_BELOW_SHOULDER = 1.2f;
    /** A rise that has not released within this time is dropped. */
    public static final long RISE_TIMEOUT_MS = 1200;
    /** A gap in the points longer than this starts the arm over. */
    private static final long GAP_MS = 450;
    private static final float MIN_CONF = 0.3f;

    /** Arm states, for the debug line. */
    public static final int IDLE = 0, DOWN = 1, ARMED = 2, RISING = 3;

    private static final class Arm {
        int state = IDLE;
        boolean have;
        long lastT, since;
        float lastX, lastY, lowY, peakV;
    }

    private final Arm[] arms = {new Arm(), new Arm()};
    private float power, spin, peak;
    private int side = -1;

    /** Forgets both arms (call at the start of each turn). */
    public void reset() {
        for (Arm a : arms) {
            a.state = IDLE;
            a.have = false;
        }
        side = -1;
    }

    /**
     * Feeds one new set of body points.
     * @param p the player (stage pixels, mirrored)
     * @param tMs when the points were measured (milliseconds); a repeat of the last time is ignored
     * @return true when the ball is let go on this update
     */
    public boolean update(PlayerPose p, long tMs) {
        if (p == null || !p.present) {
            for (Arm a : arms) a.have = false;
            return false;
        }
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        if (ls.s < MIN_CONF || rs.s < MIN_CONF) {
            for (Arm a : arms) a.have = false;
            return false;
        }
        float sw = (float) Math.hypot(rs.x - ls.x, rs.y - ls.y);
        if (sw < 20f) return false;
        for (int s = 0; s < 2; s++) {
            Keypoint sh = s == 0 ? ls : rs;
            Keypoint wr = p.kps[s == 0 ? Kp.L_WRIST : Kp.R_WRIST];
            Keypoint hip = p.kps[s == 0 ? Kp.L_HIP : Kp.R_HIP];
            if (step(arms[s], sh, wr, hip, sw, tMs)) {
                side = s;
                for (Arm a : arms) a.state = IDLE;
                return true;
            }
        }
        return false;
    }

    private boolean step(Arm a, Keypoint sh, Keypoint wr, Keypoint hip, float sw, long t) {
        if (wr.s < MIN_CONF) {
            a.have = false;
            a.state = IDLE;
            return false;
        }
        if (a.have && t <= a.lastT) return false;  // the same points again
        boolean fresh = !a.have || t - a.lastT > GAP_MS;
        float dtS = fresh ? 0f : (t - a.lastT) / 1000f;
        float v = fresh ? 0f : (a.lastY - wr.y) / sw / dtS;   // + is upward, sw per second
        float vx = fresh ? 0f : (wr.x - a.lastX) / sw / dtS;
        a.lastX = wr.x;
        a.lastY = wr.y;
        a.lastT = t;
        a.have = true;
        if (fresh) a.state = IDLE;

        float downLine = hip.s >= MIN_CONF ? hip.y : sh.y + DOWN_BELOW_SHOULDER * sw;
        boolean down = wr.y >= downLine;
        switch (a.state) {
            case IDLE:
                if (down) {
                    a.state = DOWN;
                    a.since = t;
                }
                return false;
            case DOWN:
                if (!down) {
                    a.state = IDLE;
                } else if (t - a.since >= ARM_MS) {
                    a.state = ARMED;
                    a.lowY = wr.y;
                }
                return false;
            case ARMED:
                if (down) a.lowY = Math.max(a.lowY, wr.y);
                if (v >= MIN_UP_SPEED) {
                    a.state = RISING;
                    a.since = t;
                    a.peakV = v;
                    return checkRelease(a, sh, wr, sw, v, vx);   // a fast swing can pass chest height in one update
                }
                if (wr.y < sh.y + DOWN_BELOW_SHOULDER * sw * 0.5f) a.state = IDLE;   // lifted slowly: not a swing
                return false;
            default: // RISING
                if (t - a.since > RISE_TIMEOUT_MS) {
                    a.state = IDLE;
                    return false;
                }
                if (down && v < MIN_UP_SPEED) {           // fell back down: armed again
                    a.state = ARMED;
                    a.lowY = wr.y;
                    return false;
                }
                a.peakV = Math.max(a.peakV, v);
                return checkRelease(a, sh, wr, sw, v, vx);
        }
    }

    private boolean checkRelease(Arm a, Keypoint sh, Keypoint wr, float sw, float v, float vx) {
        float rise = (a.lowY - wr.y) / sw;
        if (rise < MIN_RISE) return false;
        boolean chest = wr.y <= sh.y + RELEASE_BELOW_SHOULDER * sw;
        boolean slowing = v < 0.6f * a.peakV;
        if (!chest && !slowing) return false;
        peak = a.peakV;
        power = clamp((a.peakV - MIN_UP_SPEED) / (FULL_UP_SPEED - MIN_UP_SPEED), 0f, 1f);
        float sp = clamp(vx / 4f, -0.6f, 0.6f);
        spin = Math.abs(sp) < 0.15f ? 0f : sp;   // wobbly arms roll straight
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    /** @return power of the last release, 0..1 */
    public float power() { return power; }

    /** @return spin of the last release, -0.6..0.6 (+ curves toward the screen's right) */
    public float spin() { return spin; }

    /** @return the fastest upward wrist speed of the last release, in shoulder widths per second */
    public float peakSpeed() { return peak; }

    /** @return the arm that bowled last (0 left, 1 right), or -1 */
    public int side() { return side; }

    /**
     * The state of an arm, for the screen and the debug line.
     * @param s 0 left, 1 right
     * @return IDLE, DOWN, ARMED or RISING
     */
    public int state(int s) { return arms[s].state; }

    /** @return true when either arm is swung back and ready */
    public boolean ready() {
        return arms[0].state >= ARMED || arms[1].state >= ARMED;
    }
}
