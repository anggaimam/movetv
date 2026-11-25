package com.wiggleplay.app.games.race;

/**
 * The chase camera of the race (plain Java): 6.5 units behind and 3.2 above the truck, looking at a point 4 units
 * ahead of it at height 0.8. The position eases after the truck (rate 5 per second) and the camera heading eases
 * after the truck heading (rate 4 per second), so a sharp turn swings the picture round instead of jumping. The
 * field of view grows from 60 to 68 degrees during a boost, in 0.3 s. No shake, no allocation.
 *
 * <p>T-31: with the truck above the road the eye rises by 0.6 x the truck's height and the look-at point by 0.5 x it (both eased like
 * everything else); a landing dips the eye by 0.2 m, easing back over 0.3 s.
 */
public final class ChaseCamera {

    /** Distance behind the truck. */
    public static final float BEHIND = 6.5f;
    /** Height above the ground. */
    public static final float HEIGHT = 3.2f;
    /** How far ahead of the truck the camera looks. */
    public static final float AHEAD = 4f;
    /** Height of the point it looks at. */
    public static final float LOOK_HEIGHT = 0.8f;
    /** Field of view in degrees: normal and while boosting. */
    public static final float FOV = 60f, FOV_BOOST = 68f;

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final float[] eye = new float[3];
    private final float[] target = new float[3];
    /** Share of the truck's height the eye and the look-at point follow, and the landing dip (m) and its length (s). */
    public static final float EYE_RISE = 0.6f, TARGET_RISE = 0.5f, LAND_DIP = 0.2f, LAND_SECONDS = 0.3f;

    private float yaw;
    private float fov = FOV;
    private float truckY, dipLeft, shakeLeft, shakeApplied;
    /** Pothole shake (T-31 Part B): vertical amplitude (m), frequency (Hz) and length (s), fading out. */
    public static final float SHAKE_AMP = 0.08f, SHAKE_HZ = 12f, SHAKE_SECONDS = 0.25f;

    /**
     * Puts the camera right behind the truck at once (at the start of a round).
     * @param x truck x
     * @param z truck z
     * @param truckYaw truck heading (0 = +Z)
     */
    public void snap(float x, float z, float truckYaw) {
        yaw = truckYaw;
        fov = FOV;
        truckY = 0f;
        dipLeft = 0f;
        shakeLeft = 0f;
        shakeApplied = 0f;
        place(x, z, 1f);
    }

    /**
     * Follows the truck.
     * @param dt seconds
     * @param x truck x
     * @param z truck z
     * @param truckYaw truck heading
     * @param boosting true during a boost
     */
    public void update(float dt, float x, float z, float truckYaw, boolean boosting) {
        update(dt, x, z, truckYaw, boosting, 0f);
    }

    /**
     * Follows the truck, which may be above the road (T-31).
     * @param truckHeight the truck's height above the road in metres
     */
    public void update(float dt, float x, float z, float truckYaw, boolean boosting, float truckHeight) {
        truckY = truckHeight;
        dipLeft = Math.max(0f, dipLeft - dt);
        yaw += angleDiff(truckYaw, yaw) * Math.min(1f, dt * 4f);
        place(x, z, Math.min(1f, dt * 5f));
        shakeLeft = Math.max(0f, shakeLeft - dt);
        float shake = shakeLeft > 0f
                ? SHAKE_AMP * (float) Math.sin(2.0 * Math.PI * SHAKE_HZ * (SHAKE_SECONDS - shakeLeft)) * (shakeLeft / SHAKE_SECONDS) : 0f;
        eye[1] += shake - shakeApplied;   // only the change, so the shake never builds up in the eased height
        shakeApplied = shake;
        float goal = boosting ? FOV_BOOST : FOV;
        float step = (FOV_BOOST - FOV) / 0.3f * dt;
        if (fov < goal) fov = Math.min(goal, fov + step);
        else if (fov > goal) fov = Math.max(goal, fov - step);
    }

    /** The truck has landed: the eye dips by {@link #LAND_DIP} and eases back over {@link #LAND_SECONDS}. */
    public void onLanding() {
        dipLeft = LAND_SECONDS;
    }

    /** The truck hit a pothole: the eye shakes vertically ({@link #SHAKE_AMP} m, {@link #SHAKE_HZ} Hz, {@link #SHAKE_SECONDS} s, fading). */
    public void onBump() {
        shakeLeft = SHAKE_SECONDS;
    }

    private void place(float x, float z, float k) {
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        eye[0] += (x - fx * BEHIND - eye[0]) * k;
        float dip = LAND_DIP * dipLeft / LAND_SECONDS;
        eye[1] += (HEIGHT + EYE_RISE * truckY - dip - eye[1]) * k;
        eye[2] += (z - fz * BEHIND - eye[2]) * k;
        target[0] += (x + fx * AHEAD - target[0]) * k;
        target[1] += (LOOK_HEIGHT + TARGET_RISE * truckY - target[1]) * k;
        target[2] += (z + fz * AHEAD - target[2]) * k;
    }

    /** @return the eye position (x, y, z); the array is live, do not change it */
    public float[] eye() { return eye; }

    /** @return the point looked at (x, y, z); the array is live, do not change it */
    public float[] target() { return target; }

    /** @return vertical field of view in degrees */
    public float fov() { return fov; }

    /** @return the camera's heading (0 = +Z) */
    public float yaw() { return yaw; }

    private static float angleDiff(float to, float from) {
        float d = to - from;
        while (d > (float) Math.PI) d -= TWO_PI;
        while (d <= -(float) Math.PI) d += TWO_PI;
        return d;
    }
}
