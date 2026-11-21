package com.angga.movetv.ui.puppet;

/**
 * What one kid puppet looks like right now: small angles and offsets per part, plus the eye and mouth
 * state. Plain data, filled by {@link PuppetAnim#fill}. Angles are in degrees; for both arms a positive
 * "lift" raises the arm up and out from hanging, and a positive "bend" folds the forearm up toward the head.
 */
public final class PuppetPose {
    /** Eye states. */
    public static final int EYES_OPEN = 0, EYES_HALF = 1, EYES_CLOSED = 2;
    /** Mouth states. */
    public static final int MOUTH_SMILE = 0, MOUTH_OPEN = 1, MOUTH_CLOSED = 2;
    /** Index of the screen-left and screen-right arm. */
    public static final int LEFT = 0, RIGHT = 1;

    /** Whole body sideways and up/down (hop is negative) in drawing units. */
    public float rootDx, rootDy;
    /** Torso lean in degrees (positive = clockwise on screen) and breathing (0.012 = 1.2% taller). */
    public float torsoRot, breath;
    /** Head tilt in degrees and head bob in drawing units. */
    public float headRot, headDy;
    /** Upper arm lift per arm (degrees from hanging). */
    public final float[] armLift = new float[2];
    /** Forearm bend per arm (degrees, relative to the upper arm). */
    public final float[] foreBend = new float[2];
    /** Where the pupils look, each -1..1 (the puppet turns it into at most 5 drawing units). */
    public float pupilX, pupilY;
    /** One of the EYES_ constants. */
    public int eyes;
    /** One of the MOUTH_ constants. */
    public int mouth;
    /** Floor shadow scale (shrinks while the kid hops). */
    public float shadowScale = 1f;

    /** Sets everything to the plain standing pose. */
    public void reset() {
        rootDx = rootDy = torsoRot = breath = headRot = headDy = 0f;
        armLift[0] = armLift[1] = 0f;
        foreBend[0] = foreBend[1] = 0f;
        pupilX = pupilY = 0f;
        eyes = EYES_OPEN;
        mouth = MOUTH_SMILE;
        shadowScale = 1f;
    }

    /**
     * Whether every number is finite.
     * @return true if no NaN or infinity is present
     */
    public boolean finite() {
        float[] v = {rootDx, rootDy, torsoRot, breath, headRot, headDy, armLift[0], armLift[1], foreBend[0],
                foreBend[1], pupilX, pupilY, shadowScale};
        for (float f : v) if (Float.isNaN(f) || Float.isInfinite(f)) return false;
        return true;
    }
}
