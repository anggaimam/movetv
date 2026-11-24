package com.wiggleplay.app.ui;

/**
 * The skeleton of the "how to play" figure (T-27): turns a {@link HowToPose} into joint positions. Plain Java, no
 * allocation after construction. Coordinates are in units of the standing height, x to the screen's right, y up, the soles
 * of the feet at y = 0 and the figure centred on x = 0.
 */
public final class HowToRig {

    /** Hip height when standing. */
    public static final float HIP_Y = 0.46f;
    /** Hip centre to the shoulder line when standing. */
    public static final float TORSO = 0.315f;
    /** Half the distance between the shoulders. */
    public static final float SHOULDER_HALF = 0.10f;
    /** Half the distance between the hip joints when standing. */
    public static final float HIP_HALF = 0.06f;
    /** Upper arm, forearm, thigh and shin lengths. */
    public static final float UPPER = 0.17f, FORE = 0.16f, THIGH = 0.225f, SHIN = 0.225f;
    /** Head radius, and the distance from the shoulder line to the head centre. */
    public static final float HEAD_R = 0.10f, HEAD_UP = 0.115f;
    /** Height of an ankle above the ground when the foot is down. */
    public static final float ANKLE_Y = 0.03f;
    /** Steering wheel radius, and its height above the hips. */
    public static final float WHEEL_R = 0.115f, WHEEL_UP = 0.20f;

    /** Head centre. */
    public float headX, headY;
    /** Hip centre. */
    public float hipX, hipY;
    /** Shoulders, left = on the screen's left. */
    public float shLx, shLy, shRx, shRy;
    /** Elbows. */
    public float elLx, elLy, elRx, elRy;
    /** Hands. */
    public float haLx, haLy, haRx, haRy;
    /** Hip joints. */
    public float hpLx, hpLy, hpRx, hpRy;
    /** Knees. */
    public float knLx, knLy, knRx, knRy;
    /** Ankles. */
    public float anLx, anLy, anRx, anRy;
    /** Steering wheel centre (valid when {@link #wheel} &gt; 0). */
    public float wheelX, wheelY;
    /** The pose's wheel amount, 0..1. */
    public float wheel;
    /** Arm foreshortening 0..1 of the two arms (for drawing the hands a little bigger). */
    public float reachL, reachR;

    private float ex, ey; // scratch result of two-bone solves

    /**
     * Works out every joint of a pose.
     * @param p the pose
     */
    public void compute(HowToPose p) {
        float crouch = clamp(p.crouch, 0f, 1f);
        hipX = 0f;
        hipY = HIP_Y - 0.20f * crouch + p.lift;
        float hipHalf = HIP_HALF * (1f + 0.35f * crouch);
        hpLx = -hipHalf;
        hpRx = hipHalf;
        hpLy = hipY;
        hpRy = hipY;

        // the upper body turns around the hip centre
        float la = (float) Math.toRadians(p.lean), ls = (float) Math.sin(la), lc = (float) Math.cos(la);
        float torso = TORSO - 0.04f * crouch;
        shLx = hipX + (-SHOULDER_HALF) * lc + torso * ls;
        shLy = hipY - (-SHOULDER_HALF) * ls + torso * lc;
        shRx = hipX + SHOULDER_HALF * lc + torso * ls;
        shRy = hipY - SHOULDER_HALF * ls + torso * lc;
        headX = hipX + (torso + HEAD_UP) * ls;
        headY = hipY + (torso + HEAD_UP) * lc;
        wheel = clamp(p.wheel, 0f, 1f);
        float wl = torso - 0.12f;
        wheelX = hipX + wl * ls;
        wheelY = hipY + wl * lc;
        reachL = clamp(p.reachL, 0f, 1f);
        reachR = clamp(p.reachR, 0f, 1f);

        // arms: angles first (forward kinematics), then blended toward the wheel (two-bone solve)
        arm(-1, p.shL, p.elL, reachL, la, shLx, shLy);
        float fkElLx = ex, fkElLy = ey, fkHaLx = ex2, fkHaLy = ey2;
        arm(1, p.shR, p.elR, reachR, la, shRx, shRy);
        float fkElRx = ex, fkElRy = ey, fkHaRx = ex2, fkHaRy = ey2;
        if (wheel > 0f) {
            float wa = (float) Math.toRadians(p.wheelDeg), wc = (float) Math.cos(wa), wsn = (float) Math.sin(wa);
            // hand targets: the wheel's left and right grips; they turn with the wheel (counter-clockwise for positive
            // degrees) and lean with the body
            float glx = -WHEEL_R * wc, gly = -WHEEL_R * wsn;
            float grx = WHEEL_R * wc, gry = WHEEL_R * wsn;
            float lxh = wheelX + glx * lc + gly * ls;
            float lyh = wheelY - glx * ls + gly * lc;
            float rxh = wheelX + grx * lc + gry * ls, ryh = wheelY - grx * ls + gry * lc;
            twoBone(shLx, shLy, lxh, lyh, UPPER, FORE, -1f, -0.4f);
            float ikElLx = ex, ikElLy = ey;
            twoBone(shRx, shRy, rxh, ryh, UPPER, FORE, 1f, -0.4f);
            float ikElRx = ex, ikElRy = ey;
            elLx = fkElLx + (ikElLx - fkElLx) * wheel;
            elLy = fkElLy + (ikElLy - fkElLy) * wheel;
            elRx = fkElRx + (ikElRx - fkElRx) * wheel;
            elRy = fkElRy + (ikElRy - fkElRy) * wheel;
            haLx = fkHaLx + (lxh - fkHaLx) * wheel;
            haLy = fkHaLy + (lyh - fkHaLy) * wheel;
            haRx = fkHaRx + (rxh - fkHaRx) * wheel;
            haRy = fkHaRy + (ryh - fkHaRy) * wheel;
        } else {
            elLx = fkElLx;
            elLy = fkElLy;
            haLx = fkHaLx;
            haLy = fkHaLy;
            elRx = fkElRx;
            elRy = fkElRy;
            haRx = fkHaRx;
            haRy = fkHaRy;
        }

        // legs: ankles fixed under the feet, knees found by a two-bone solve that bends outward
        float spread = p.spread + 0.08f * crouch;
        anLx = -spread;
        anRx = spread;
        anLy = ANKLE_Y + Math.max(0f, p.footL);
        anRy = ANKLE_Y + Math.max(0f, p.footR);
        twoBone(hpLx, hpLy, anLx, anLy, THIGH, SHIN, -1f, 0.0f);
        knLx = ex;
        knLy = ey;
        twoBone(hpRx, hpRy, anRx, anRy, THIGH, SHIN, 1f, 0.0f);
        knRx = ex;
        knRy = ey;
    }

    private float ex2, ey2;

    /** Forward kinematics of one arm; the elbow goes to (ex, ey), the hand to (ex2, ey2). */
    private void arm(int side, float shoulderDeg, float elbowDeg, float reach, float lean, float sx, float sy) {
        float k = 1f - 0.55f * reach;
        float a1 = (float) Math.toRadians(shoulderDeg);
        float a2 = (float) Math.toRadians(shoulderDeg + elbowDeg);
        // direction in the body's frame: (side * sin a, -cos a); then turned with the lean
        float d1x = side * (float) Math.sin(a1), d1y = -(float) Math.cos(a1);
        float d2x = side * (float) Math.sin(a2), d2y = -(float) Math.cos(a2);
        float lc = (float) Math.cos(lean), ls = (float) Math.sin(lean);
        float r1x = d1x * lc + d1y * ls, r1y = -d1x * ls + d1y * lc;
        float r2x = d2x * lc + d2y * ls, r2y = -d2x * ls + d2y * lc;
        ex = sx + r1x * UPPER * k;
        ey = sy + r1y * UPPER * k;
        ex2 = ex + r2x * FORE * k;
        ey2 = ey + r2y * FORE * k;
    }

    /**
     * Solves a two-bone limb: the middle joint for a start and an end point, bending toward the side the preferred
     * direction points to. The result is left in (ex, ey).
     */
    private void twoBone(float x0, float y0, float x1, float y1, float l1, float l2, float prefX, float prefY) {
        float dx = x1 - x0, dy = y1 - y0;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float max = l1 + l2 - 1e-4f;
        if (d > max) {
            dx *= max / d;
            dy *= max / d;
            d = max;
        }
        if (d < 1e-4f) {
            ex = x0;
            ey = y0 - l1;
            return;
        }
        float a = (l1 * l1 - l2 * l2 + d * d) / (2f * d);
        float h2 = l1 * l1 - a * a;
        float h = h2 > 0f ? (float) Math.sqrt(h2) : 0f;
        float ux = dx / d, uy = dy / d;
        float nx = -uy, ny = ux;
        if (nx * prefX + ny * prefY < 0f) {
            nx = -nx;
            ny = -ny;
        }
        ex = x0 + ux * a + nx * h;
        ey = y0 + uy * a + ny * h;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
