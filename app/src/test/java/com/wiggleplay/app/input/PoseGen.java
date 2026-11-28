package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

/**
 * Synthetic gesture generator for the fishing detector tests: an ideal (noise-free, lag-free) pose as a function of the
 * true time. Shoulder width 200 px, shoulders at y 500, centre x 960. {@link LagSim} turns these into pictures.
 */
final class PoseGen {

    static final float SW = 200f, SY = 500f, CX = 960f;

    /** An ideal pose at time t (seconds). */
    interface Fn {
        void at(float t, PlayerPose p);
    }

    private PoseGen() { }

    /** Standing body: shoulders, hips, nose; wrists hanging at the sides (rest). */
    static void body(PlayerPose p, float dy) {
        p.present = true;
        for (int i = 0; i < Kp.COUNT; i++) {
            p.kps[i].x = CX;
            p.kps[i].y = SY + dy;
            p.kps[i].s = 0.9f;
        }
        p.kps[Kp.NOSE].y = SY - 0.8f * SW + dy;
        p.kps[Kp.L_SHOULDER].x = CX - SW / 2f;
        p.kps[Kp.R_SHOULDER].x = CX + SW / 2f;
        p.kps[Kp.L_HIP].x = CX - SW * 0.3f;
        p.kps[Kp.R_HIP].x = CX + SW * 0.3f;
        p.kps[Kp.L_HIP].y = p.kps[Kp.R_HIP].y = SY + 1.6f * SW + dy;
        p.kps[Kp.L_WRIST].x = CX - 0.55f * SW;
        p.kps[Kp.R_WRIST].x = CX + 0.55f * SW;
        p.kps[Kp.L_WRIST].y = p.kps[Kp.R_WRIST].y = SY + 1.2f * SW + dy;
    }

    private static int wrist(boolean right) { return right ? Kp.R_WRIST : Kp.L_WRIST; }

    private static float smooth(float u) {
        u = Math.max(0f, Math.min(1f, u));
        return u * u * (3f - 2f * u);
    }

    /** Standing still, both hands at the sides. */
    static Fn stand() {
        return (t, p) -> body(p, 0f);
    }

    /** Standing with a hand held at chest height (a resting hand that is not at the sides). */
    static Fn restAtChest(boolean right) {
        return (t, p) -> {
            body(p, 0f);
            p.kps[wrist(right)].y = SY + 0.5f * SW;
        };
    }

    /**
     * A cast: the hand rises over {@code riseS} to 0.9 sw above the shoulders, is held {@code holdS} at the sideways
     * offset {@code aimX} (range units, 0.9 sw = 1), then drops over 0.35 s.
     */
    static Fn cast(boolean right, float startS, float riseS, float holdS, float aimX) {
        return (t, p) -> {
            body(p, 0f);
            int w = wrist(right);
            float restX = p.kps[w].x, restY = p.kps[w].y;
            float topY = SY - 0.9f * SW, holdX = CX + aimX * 0.9f * SW;
            float u;
            if (t < startS) return;
            if (t < startS + riseS) {
                u = smooth((t - startS) / riseS);
            } else if (t < startS + riseS + holdS) {
                u = 1f;
            } else {
                u = 1f - smooth((t - startS - riseS - holdS) / 0.35f);
            }
            p.kps[w].y = restY + (topY - restY) * u;
            p.kps[w].x = restX + (holdX - restX) * u;
        };
    }

    /** Both arms raised and held after rising (a cheer): wrists 1.5 sw above the shoulders, 1 sw out. */
    static Fn cheer(float startS) {
        return (t, p) -> {
            body(p, 0f);
            if (t < startS) return;
            float u = smooth((t - startS) / 0.5f);
            for (int w : new int[] {Kp.L_WRIST, Kp.R_WRIST}) {
                p.kps[w].y += (SY - 1.5f * SW - p.kps[w].y) * u;
            }
            p.kps[Kp.L_WRIST].x = CX - SW * (0.55f + 0.45f * u);
            p.kps[Kp.R_WRIST].x = CX + SW * (0.55f + 0.45f * u);
        };
    }

    /** A wiggle: the hand at chest height moves up and down by +/- {@code ampSw} at {@code hz}. */
    static Fn wiggle(boolean right, float ampSw, float hz, float startS) {
        return (t, p) -> {
            body(p, 0f);
            if (t < startS) return;
            int w = wrist(right);
            p.kps[w].y = SY + 0.5f * SW + ampSw * SW * (float) Math.sin(2 * Math.PI * hz * (t - startS));
        };
    }

    /** A slow lift: the hand rises from rest to 1 sw above the shoulders over {@code durS}. */
    static Fn slowLift(boolean right, float durS) {
        return (t, p) -> {
            body(p, 0f);
            int w = wrist(right);
            float u = Math.max(0f, Math.min(1f, t / durS));
            p.kps[w].y = SY + 1.2f * SW + (SY - SW - (SY + 1.2f * SW)) * u;
        };
    }

    /** A pull: the hand at chest height rises {@code riseSw} over 0.35 s starting at {@code startS}. */
    static Fn pull(boolean right, float startS, float riseSw) {
        return (t, p) -> {
            body(p, 0f);
            int w = wrist(right);
            float u = smooth((t - startS) / 0.35f);
            p.kps[w].y = SY + 0.5f * SW - riseSw * SW * u;
        };
    }

    /** Circles of radius {@code radiusSw} at {@code turnsPerS} around a centre at x offset {@code centreAim} (range units). */
    static Fn circles(boolean right, float turnsPerS, float radiusSw, float centreAim, float startS) {
        return circlesMoving(right, turnsPerS, radiusSw, t -> centreAim, startS);
    }

    /** Circles around a centre that follows {@code aimOfT} (range units, a function of true time). */
    static Fn circlesMoving(boolean right, float turnsPerS, float radiusSw, java.util.function.DoubleUnaryOperator aimOfT, float startS) {
        return (t, p) -> {
            body(p, 0f);
            if (t < startS) return;
            int w = wrist(right);
            double th = 2 * Math.PI * turnsPerS * (t - startS);
            p.kps[w].x = CX + (float) aimOfT.applyAsDouble(t) * 0.9f * SW + radiusSw * SW * (float) Math.cos(th);
            p.kps[w].y = SY + 0.5f * SW + radiusSw * SW * (float) Math.sin(th);
        };
    }

    /** Pumping: the hand goes up and down by +/- {@code ampSw} at {@code hz}, with no sideways movement. */
    static Fn pump(boolean right, float ampSw, float hz) {
        return wiggle(right, ampSw, hz, 0f);
    }

    /** Jumping: the whole body bounces 0.4 sw up and down at 2 Hz, the hands swinging loosely. */
    static Fn jump() {
        return (t, p) -> {
            float dy = -0.4f * SW * Math.max(0f, (float) Math.sin(2 * Math.PI * 2 * t));
            body(p, dy);
            p.kps[Kp.L_WRIST].y += 0.3f * SW * (float) Math.sin(2 * Math.PI * 2 * t + 1f);
            p.kps[Kp.R_WRIST].y += 0.3f * SW * (float) Math.sin(2 * Math.PI * 2 * t + 2f);
        };
    }
}
