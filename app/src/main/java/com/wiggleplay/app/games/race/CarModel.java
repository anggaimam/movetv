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
 *   <li>T-31: ramps. On a ramp the truck rises (y up to 0.8 m, pitch +9 degrees); at the lip it leaves the ground with a vertical
 *       speed of {@code clamp(0.5 x speed, 4, 9)} under gravity {@link #GRAVITY}. In the air steering is 40 % of the ground rate, the
 *       assist, the heading limit and the wall stay on, the speed is held, and the nose follows the climb and fall (at most -10 to +12
 *       degrees). Landing keeps the speed and eases the nose with a small dip. The truck never flips.</li>
 * </ul>
 */
public final class CarModel {

    /** Gravity in the air (units per second squared), the share of the yaw rate kept in the air, and the pitch limits (radians). */
    public static final float GRAVITY = 20f, AIR_STEER = 0.4f;
    public static final float PITCH_MIN = (float) Math.toRadians(-10.0), PITCH_MAX = (float) Math.toRadians(12.0);
    private static final float RAMP_PITCH = (float) Math.toRadians(9.0), LAND_DIP = (float) Math.toRadians(3.0), LAND_SECONDS = 0.25f;

    /** Pothole: speed factor, pitch kick (radians) and its duration. Patch: steering and grip easing, travel easing, slide cap, time after. */
    public static final float POTHOLE_SPEED = 0.8f, SLICK_STEER = 0.5f, SLICK_AFTER = 0.4f;
    private static final float BUMP_KICK = (float) Math.toRadians(4.0), BUMP_SECONDS = 0.2f;
    private static final float SLICK_GRIP = 1.5f, SLICK_TRAVEL = 3f, SLICK_CAP = (float) Math.toRadians(20.0);

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
    // T-31: height, vertical speed, air state, ramp under the wheels, extra pitch from ramp and air, events
    private float y, vy, airPitch, landT = LAND_SECONDS, landFrom, impact;
    private boolean air, launched, landed;
    private int onRamp = -1;
    // T-31 Part B: pothole bump (pitch kick), slick (travel direction, time left after the patch), the pothole last hit
    private float travel, bumpT = BUMP_SECONDS, slickLeft;
    private boolean bumped, slick;
    private int lastPothole = -1;

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
        y = 0f;
        vy = 0f;
        airPitch = 0f;
        landT = LAND_SECONDS;
        air = false;
        launched = false;
        landed = false;
        onRamp = -1;
        travel = yaw;
        bumpT = BUMP_SECONDS;
        slickLeft = 0f;
        bumped = false;
        slick = false;
        lastPothole = -1;
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
        if (!air) speed += (target - speed) * Math.min(1f, dt * (target > speed ? 6f : 8f));   // in the air the speed is held
        float gripTarget = Math.max(GRIP_MIN, Math.min(1f, speed / base));
        grip += (gripTarget - grip) * Math.min(1f, dt * (slick ? SLICK_GRIP : 4f));

        float rate = -steer * maxYaw * grip * (air ? AIR_STEER : 1f) * (slick ? SLICK_STEER : 1f)
                + assist * (2f * angleDiff(roadYaw, yaw) + 0.5f * tp.offset / half);
        yaw += rate * dt;
        float err = angleDiff(yaw, roadYaw);
        if (err > MAX_HEADING_ERROR) yaw = roadYaw + MAX_HEADING_ERROR;
        else if (err < -MAX_HEADING_ERROR) yaw = roadYaw - MAX_HEADING_ERROR;
        yaw = wrap(yaw);

        if (slick) {
            // sliding: the travel direction lags the heading by at most 20 degrees
            travel += angleDiff(yaw, travel) * Math.min(1f, dt * SLICK_TRAVEL);
            float gap = angleDiff(travel, yaw);
            if (gap > SLICK_CAP) travel = yaw + SLICK_CAP;
            else if (gap < -SLICK_CAP) travel = yaw - SLICK_CAP;
            travel = wrap(travel);
        } else {
            travel = yaw;
        }
        x += (float) Math.sin(travel) * speed * dt;
        z += (float) Math.cos(travel) * speed * dt;

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
        RoadObjects ro = t.roadObjects();
        vertical(dt, ro);
        hazards(dt, ro);
        if (!slick) travel = yaw;   // the wall may have turned the heading this frame

        lean = -steer / 5f * Math.min(1f, speed / base);
        float accel = (speed - before) / dt;
        float pitchTarget = Math.max(-0.12f, Math.min(0.12f, accel * 0.01f));
        pitch += (pitchTarget - pitch) * Math.min(1f, dt * 8f);
        spin += speed * dt / WHEEL_RADIUS;
        if (spin > TWO_PI) spin -= TWO_PI;
        frontYaw = -steer * 0.6f;
    }

    /** Potholes and slick patches (T-31 Part B): runs after the new s, offset and height are known. */
    private void hazards(float dt, RoadObjects ro) {
        // pothole: once per pothole per pass, on the ground only
        int key = y < 0.3f ? ro.potholeAt(s, offset) : -1;
        if (key >= 0 && key != lastPothole) {
            lastPothole = key;
            speed = Math.max(speed * POTHOLE_SPEED, MIN_SPEED_FRACTION * base);
            bumped = true;
            bumpT = 0f;
        } else if (key < 0 && lastPothole >= 0) {
            // free again once the truck is clear of the last one (the group of two re-arms between its two potholes)
            lastPothole = -1;
        }
        if (bumpT < BUMP_SECONDS) bumpT += dt;
        // slick: on the patch and for 0.4 s after; then the travel direction is the heading again
        if (ro.onPatch(s, offset) && y < 0.3f) slickLeft = SLICK_AFTER;
        else slickLeft = Math.max(0f, slickLeft - dt);
        boolean was = slick;
        slick = slickLeft > 0f;
        if (was && !slick) travel = yaw;
    }

    /** Ramp, flight and landing (T-31): runs after the new s and offset are known. */
    private void vertical(float dt, RoadObjects ro) {
        if (air) {
            y += vy * dt;
            vy -= GRAVITY * dt;
            if (y <= 0f) {
                impact = Math.abs(vy);
                y = 0f;
                vy = 0f;
                air = false;
                landed = true;
                landT = 0f;
                landFrom = airPitch;
            } else {
                float p = 0.6f * (float) Math.atan2(vy, speed);
                airPitch = Math.max(PITCH_MIN, Math.min(PITCH_MAX, p));
            }
            return;
        }
        int r = ro.rampAt(s, offset);
        if (r >= 0) {
            onRamp = r;
            y = RoadObjects.RAMP_HEIGHT * Math.max(0f, Math.min(1f, (s - (ro.s(r) - RoadObjects.RAMP_LENGTH)) / RoadObjects.RAMP_LENGTH));
            airPitch = RAMP_PITCH;
            return;
        }
        if (onRamp >= 0) {
            // left the ramp: over the lip (a launch) or off its side (a drop from where it is)
            boolean lip = s >= ro.s(onRamp) && Math.abs(offset - ro.lateral(onRamp)) <= RoadObjects.RAMP_HIT;
            onRamp = -1;
            air = true;
            if (lip) {
                y = RoadObjects.RAMP_HEIGHT;
                vy = Math.max(4f, Math.min(9f, 0.5f * speed));
                launched = true;
            } else {
                vy = 0f;
            }
            return;
        }
        y = 0f;
        if (landT < LAND_SECONDS) {
            landT += dt;
            float u = Math.min(1f, landT / LAND_SECONDS);
            airPitch = landFrom * (1f - u) - LAND_DIP * (float) Math.sin(Math.PI * u);
        } else {
            airPitch += (0f - airPitch) * Math.min(1f, dt * 8f);
        }
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

    /**
     * Whether the truck left a ramp's lip since the last call.
     * @return true once per jump
     */
    public boolean consumeLaunch() {
        boolean e = launched;
        launched = false;
        return e;
    }

    /**
     * Whether the truck touched down since the last call.
     * @return true once per landing; {@link #landingImpact} says how hard
     */
    public boolean consumeLanding() {
        boolean e = landed;
        landed = false;
        return e;
    }

    /**
     * Whether the truck hit a pothole since the last call.
     * @return true once per pothole
     */
    public boolean consumeBump() {
        boolean e = bumped;
        bumped = false;
        return e;
    }

    /** @return true on a patch of ice or water and for 0.4 s after it */
    public boolean slick() { return slick; }

    /** @return the direction the truck moves in (differs from {@link #yaw} by at most 20 degrees while sliding) */
    public float travelYaw() { return travel; }

    /** @return the vertical speed at the last touch-down, units per second */
    public float landingImpact() { return impact; }

    /** @return height above the road in metres (0 on the ground, up to 0.8 on a ramp, more in the air) */
    public float y() { return y; }

    /** @return vertical speed in units per second */
    public float verticalSpeed() { return vy; }

    /** @return true while the truck is in the air */
    public boolean airborne() { return air; }

    /** @return the pitch from ramp, flight and landing alone, radians (nose up positive) */
    public float airPitch() { return airPitch; }

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
    public float bodyPitch() { return pitch + airPitch + (bumpT < BUMP_SECONDS ? BUMP_KICK * (1f - bumpT / BUMP_SECONDS) : 0f); }
    /** @return how far the wheels have turned, radians (0 to 2 pi) */
    public float wheelSpin() { return spin; }
    /** @return front wheel angle in radians (same sign as yaw) */
    public float frontWheelYaw() { return frontYaw; }
    /** @return the track search hint to pass to {@link TrackPath#project} */
    public int trackHint() { return hint; }
}
