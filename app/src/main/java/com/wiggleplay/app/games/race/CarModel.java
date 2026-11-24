package com.wiggleplay.app.games.race;

/**
 * The truck's driving feel, ported from Kenney's arcade model plus kid-friendly assists. Plain Java, no allocation in
 * {@link #update}.
 *
 * <p>Coordinates: +Y up, yaw 0 = +Z, {@code x += sin(yaw) v dt}, {@code z += cos(yaw) v dt}. From a chase camera
 * looking along +Z, screen-right is -X, so turning right lowers the yaw. Steering input is -1 (left on screen) to +1.
 *
 * <ul>
 *   <li>Speed eases toward the target (factor dt x 6 speeding up, dt x 8 slowing down). Target = base x (boost 1.5)
 *       x (brake and not boosting 0.4), never below 0.35 x base.</li>
 *   <li>Yaw rate = -steer x maxYaw x grip, with grip = clamp(speed / base, 0.2, 1) eased at dt x 4, plus the road assist
 *       {@code assist x (2 x angleDiff(roadYaw, yaw) + 0.5 x offset / roadHalf)} which turns the truck back toward the
 *       road direction and toward the centre line.</li>
 *   <li>The heading can never be more than 75 degrees away from the road direction.</li>
 *   <li>T-23: the barrier wall. |offset| can never pass {@link #wallLimit} (barrier inner edge = half-width + 0.6, minus
 *       half the truck width 0.75). At the wall the truck is put back along the road's normal and its heading turns toward the
 *       road direction by half the difference; once per 0.5 s its speed is multiplied by 0.85 and
 *       {@link #consumeHitWall()} reports the hit. There is no off-road slowdown any more.</li>
 * </ul>
 */
public final class CarModel {

    private static final float GRIP_MIN = 0.2f;
    private static final float MIN_SPEED_FRACTION = 0.35f;
    private static final float MAX_HEADING_ERROR = (float) Math.toRadians(75.0);
    private static final float WALL_SPEED = 0.85f;
    private static final float WALL_GAP_S = 0.5f;
    private static final float WHEEL_RADIUS = 0.3f;
    private static final float TWO_PI = 6.2831855f;

    private final float base, maxYaw, assist;
    private final TrackPoint tp = new TrackPoint();

    private float x, z, yaw, speed, grip = 1f;
    private float s, offset, lean, pitch, spin, frontYaw;
    private boolean hitWall;
    private float sinceWall = 10f;
    private int hint = -1;

    /**
     * Largest |offset| the truck can reach on a road: the barrier's inner face (half-width + 0.6) minus half the truck
     * width (0.75).
     * @param halfWidth half the width of the drivable road
     * @return the wall limit (3.85 for the tile road of half-width 4.0)
     */
    public static float wallLimit(float halfWidth) {
        return halfWidth - 0.15f;
    }

    /**
     * Creates a truck for a level.
     * @param level speed, turn rate and assist
     * @param twoPlayers true in a two-player race (more assist)
     */
    public CarModel(RaceLevel level, boolean twoPlayers) {
        this.base = level.baseSpeed;
        this.maxYaw = level.maxYaw;
        this.assist = level.assistFor(twoPlayers);
    }

    /**
     * Puts the truck on the road, standing still, pointing along the road.
     * @param t the track
     * @param startS distance along the lap
     * @param lateral offset from the centre line (positive = right as seen from behind)
     */
    public void reset(TrackPath t, float startS, float lateral) {
        float[] p = new float[3];
        t.sample(startS, p);
        yaw = p[2];
        x = p[0] + -(float) Math.cos(yaw) * lateral;
        z = p[1] + (float) Math.sin(yaw) * lateral;
        speed = 0f;
        grip = GRIP_MIN;
        lean = 0f;
        pitch = 0f;
        spin = 0f;
        frontYaw = 0f;
        hitWall = false;
        sinceWall = 10f;
        hint = t.project(x, z, -1, tp);
        s = tp.s;
        offset = tp.offset;
    }

    /**
     * Advances the truck.
     * @param dt seconds (at most 0.05)
     * @param steer -1 (left on screen) to +1
     * @param boosting true while a boost runs
     * @param braking true while braking (ignored while boosting)
     * @param t the track
     */
    public void update(float dt, float steer, boolean boosting, boolean braking, TrackPath t) {
        if (dt <= 0f) return;
        float half = t.roadHalfWidth();
        float wall = wallLimit(half);
        // where we are now, for the assist and the heading limit
        hint = t.project(x, z, hint, tp);
        float roadYaw = tp.tangentYaw;

        float target = base * (boosting ? 1.5f : 1f) * (braking && !boosting ? 0.4f : 1f);
        target = Math.max(target, MIN_SPEED_FRACTION * base);
        float before = speed;
        speed += (target - speed) * Math.min(1f, dt * (target > speed ? 6f : 8f));
        float gripTarget = Math.max(GRIP_MIN, Math.min(1f, speed / base));
        grip += (gripTarget - grip) * Math.min(1f, dt * 4f);

        float rate = -steer * maxYaw * grip
                + assist * (2f * angleDiff(roadYaw, yaw) + 0.5f * tp.offset / half);
        yaw += rate * dt;
        float err = angleDiff(yaw, roadYaw);
        if (err > MAX_HEADING_ERROR) yaw = roadYaw + MAX_HEADING_ERROR;
        else if (err < -MAX_HEADING_ERROR) yaw = roadYaw - MAX_HEADING_ERROR;
        yaw = wrap(yaw);

        x += (float) Math.sin(yaw) * speed * dt;
        z += (float) Math.cos(yaw) * speed * dt;

        // the barrier wall: put the truck back along the road's normal, steer it along the road, scrape
        hint = t.project(x, z, hint, tp);
        sinceWall += dt;
        if (Math.abs(tp.offset) > wall) {
            float excess = tp.offset - Math.signum(tp.offset) * wall;
            float rx = -(float) Math.cos(tp.tangentYaw), rz = (float) Math.sin(tp.tangentYaw);
            x -= rx * excess;
            z -= rz * excess;
            tp.offset -= excess;
            yaw = wrap(yaw + 0.5f * angleDiff(tp.tangentYaw, yaw));
            if (sinceWall >= WALL_GAP_S) {
                hitWall = true;
                sinceWall = 0f;
                speed = Math.max(speed * WALL_SPEED, MIN_SPEED_FRACTION * base);
            }
        }
        s = tp.s;
        offset = tp.offset;

        lean = -steer / 5f * Math.min(1f, speed / base);
        float accel = (speed - before) / dt;
        float pitchTarget = Math.max(-0.12f, Math.min(0.12f, accel * 0.01f));
        pitch += (pitchTarget - pitch) * Math.min(1f, dt * 8f);
        spin += speed * dt / WHEEL_RADIUS;
        if (spin > TWO_PI) spin -= TWO_PI;
        frontYaw = -steer * 0.6f;
    }

    /**
     * Moves the truck by a small amount (used to push two trucks apart).
     * @param dx change of x
     * @param dz change of z
     */
    public void nudge(float dx, float dz) {
        x += dx;
        z += dz;
    }

    /**
     * Changes the speed by a factor (used when two trucks bump).
     * @param factor 1 = no change
     */
    public void scaleSpeed(float factor) {
        speed *= factor;
    }

    /**
     * Whether the truck hit the soft wall since the last call (at most once per 0.5 s).
     * @return true once per hit
     */
    public boolean consumeHitWall() {
        boolean h = hitWall;
        hitWall = false;
        return h;
    }

    private static float angleDiff(float to, float from) {
        float d = to - from;
        while (d > (float) Math.PI) d -= TWO_PI;
        while (d <= -(float) Math.PI) d += TWO_PI;
        return d;
    }

    private static float wrap(float a) {
        while (a > (float) Math.PI) a -= TWO_PI;
        while (a <= -(float) Math.PI) a += TWO_PI;
        return a;
    }

    /** @return world x */
    public float x() { return x; }
    /** @return world z */
    public float z() { return z; }
    /** @return heading (0 = +Z, turning right lowers it) */
    public float yaw() { return yaw; }
    /** @return speed in units per second */
    public float speed() { return speed; }
    /** @return distance along the lap */
    public float s() { return s; }
    /** @return offset from the centre line (positive = right) */
    public float offset() { return offset; }
    /** @return body lean in radians (roll; leans away from the turn) */
    public float bodyLean() { return lean; }
    /** @return body pitch in radians (positive = nose up while speeding up) */
    public float bodyPitch() { return pitch; }
    /** @return how far the wheels have turned, radians (0 to 2 pi) */
    public float wheelSpin() { return spin; }
    /** @return front wheel angle in radians (same sign as yaw) */
    public float frontWheelYaw() { return frontYaw; }
    /** @return the track search hint to pass to {@link TrackPath#project} */
    public int trackHint() { return hint; }
}
