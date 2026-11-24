package com.wiggleplay.app.ui;

/**
 * One pose of the shared "how to play" figure (T-27): plain numbers, no Android classes. Angles are in degrees, lengths are
 * fractions of the standing height. {@link HowToRig} turns a pose into joint positions; {@link HowToFigure} draws it.
 * The eight named poses come from Angga's pose sheet. Poses can be blended with {@link #lerp}, so the joints glide.
 *
 * <p>The figure is seen from the front, as it appears on the (mirrored) TV: {@code lean > 0} puts the head toward the
 * screen's right, the "left" arm is the one on the screen's left.
 */
public final class HowToPose {

    /** Lean of the body around the hips, degrees; positive = the head moves to the screen's right. */
    public float lean;
    /** 0 = standing, 1 = deep squat (hips down, knees out). */
    public float crouch;
    /** The body raised off the ground (a jump), as a fraction of the height. */
    public float lift;
    /** Upper-arm angle of the screen-left / screen-right arm: 0 = hanging, 90 = out sideways, 180 = straight up. */
    public float shL, shR;
    /** Elbow bend of the screen-left / screen-right arm: the forearm turns this many degrees further up (and inward). */
    public float elL, elR;
    /** 0 = the arm is in the picture plane, 1 = it points at the viewer (drawn short, the hand a little bigger). */
    public float reachL, reachR;
    /** Half the distance between the ankles, fraction of the height. */
    public float spread = 0.07f;
    /** Extra height of the screen-left / screen-right ankle above the ground, fraction of the height. */
    public float footL, footR;
    /** 0 = no steering wheel; 1 = both hands hold a steering wheel in front of the chest. */
    public float wheel;
    /** How far the wheel is turned, degrees; positive = counter-clockwise on screen (the screen-left hand goes down). */
    public float wheelDeg;

    /** Standing, arms hanging a little away from the body. */
    public static final HowToPose STAND = make(0f, 0f, 0f, 14f, 14f, 6f, 6f, 0f, 0f, 0.07f, 0f, 0f, 0f);
    /** Holding a steering wheel with both hands. */
    public static final HowToPose WHEEL = make(0f, 0f, 0f, 35f, 35f, 40f, 40f, 0f, 0f, 0.07f, 0f, 0f, 1f);
    /** Both arms up in a V. */
    public static final HowToPose ARMS_UP = make(0f, 0f, 0f, 150f, 150f, 12f, 12f, 0f, 0f, 0.08f, 0f, 0f, 0f);
    /** Arms straight out to the sides, like a plane. */
    public static final HowToPose T_POSE = make(0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f, 0f, 0.08f, 0f, 0f, 0f);
    /** The body leans, the head toward the screen's left; the screen-right arm swings out. */
    public static final HowToPose LEAN_LEFT = make(-13f, 0f, 0f, 10f, 40f, 6f, 6f, 0f, 0f, 0.07f, 0f, 0f, 0f);
    /** The body leans, the head toward the screen's right; the screen-left arm swings out. */
    public static final HowToPose LEAN_RIGHT = make(13f, 0f, 0f, 40f, 10f, 6f, 6f, 0f, 0f, 0.07f, 0f, 0f, 0f);
    /** A jump: off the ground, arms up, knees bent, one foot higher. */
    public static final HowToPose JUMP = make(0f, 0.25f, 0.12f, 145f, 145f, 20f, 20f, 0f, 0f, 0.12f, 0.10f, 0.20f, 0f);
    /** A deep squat with both arms pointing forward, at the viewer. */
    public static final HowToPose SQUAT = make(0f, 0.8f, 0f, 20f, 20f, 0f, 0f, 0.85f, 0.85f, 0.13f, 0f, 0f, 0f);

    private static HowToPose make(float lean, float crouch, float lift, float shL, float shR, float elL, float elR,
                                  float reachL, float reachR, float spread, float footL, float footR, float wheel) {
        HowToPose p = new HowToPose();
        p.lean = lean;
        p.crouch = crouch;
        p.lift = lift;
        p.shL = shL;
        p.shR = shR;
        p.elL = elL;
        p.elR = elR;
        p.reachL = reachL;
        p.reachR = reachR;
        p.spread = spread;
        p.footL = footL;
        p.footR = footR;
        p.wheel = wheel;
        return p;
    }

    /**
     * Copies another pose into this one.
     * @param o the pose to copy
     * @return this
     */
    public HowToPose set(HowToPose o) {
        return lerp(o, o, 0f);
    }

    /**
     * Sets this pose to a blend of two poses.
     * @param a the pose at k = 0
     * @param b the pose at k = 1
     * @param k 0..1 (clamped)
     * @return this
     */
    public HowToPose lerp(HowToPose a, HowToPose b, float k) {
        float t = Math.max(0f, Math.min(1f, k));
        lean = a.lean + (b.lean - a.lean) * t;
        crouch = a.crouch + (b.crouch - a.crouch) * t;
        lift = a.lift + (b.lift - a.lift) * t;
        shL = a.shL + (b.shL - a.shL) * t;
        shR = a.shR + (b.shR - a.shR) * t;
        elL = a.elL + (b.elL - a.elL) * t;
        elR = a.elR + (b.elR - a.elR) * t;
        reachL = a.reachL + (b.reachL - a.reachL) * t;
        reachR = a.reachR + (b.reachR - a.reachR) * t;
        spread = a.spread + (b.spread - a.spread) * t;
        footL = a.footL + (b.footL - a.footL) * t;
        footR = a.footR + (b.footR - a.footR) * t;
        wheel = a.wheel + (b.wheel - a.wheel) * t;
        wheelDeg = a.wheelDeg + (b.wheelDeg - a.wheelDeg) * t;
        return this;
    }

    /**
     * A smooth 0..1..0 swing for animating between two poses (a cosine, so the joints slow at both ends).
     * @param seconds time
     * @param period seconds for the whole swing there and back
     * @return 0..1
     */
    public static float swing(float seconds, float period) {
        return 0.5f - 0.5f * (float) Math.cos(2.0 * Math.PI * seconds / period);
    }
}
