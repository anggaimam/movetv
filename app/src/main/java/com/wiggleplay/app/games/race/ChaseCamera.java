package com.wiggleplay.app.games.race;

/**
 * The chase camera of the race (plain Java): 6.5 units behind and 3.2 above the truck, looking at a point 4 units
 * ahead of it at height 0.8. The position eases after the truck (rate 5 per second) and the camera heading eases
 * after the truck heading (rate 4 per second), so a sharp turn swings the picture round instead of jumping. The
 * field of view grows from 60 to 68 degrees during a boost, in 0.3 s. No shake, no allocation.
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
    private float yaw;
    private float fov = FOV;

    /**
     * Puts the camera right behind the truck at once (at the start of a round).
     * @param x truck x
     * @param z truck z
     * @param truckYaw truck heading (0 = +Z)
     */
    public void snap(float x, float z, float truckYaw) {
        yaw = truckYaw;
        fov = FOV;
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
        yaw += angleDiff(truckYaw, yaw) * Math.min(1f, dt * 4f);
        place(x, z, Math.min(1f, dt * 5f));
        float goal = boosting ? FOV_BOOST : FOV;
        float step = (FOV_BOOST - FOV) / 0.3f * dt;
        if (fov < goal) fov = Math.min(goal, fov + step);
        else if (fov > goal) fov = Math.max(goal, fov - step);
    }

    private void place(float x, float z, float k) {
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        eye[0] += (x - fx * BEHIND - eye[0]) * k;
        eye[1] += (HEIGHT - eye[1]) * k;
        eye[2] += (z - fz * BEHIND - eye[2]) * k;
        target[0] += (x + fx * AHEAD - target[0]) * k;
        target[1] += (LOOK_HEIGHT - target[1]) * k;
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
