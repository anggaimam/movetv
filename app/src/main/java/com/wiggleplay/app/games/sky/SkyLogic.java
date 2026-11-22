package com.wiggleplay.app.games.sky;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.pose.PoseFrame;

import java.util.Random;

/**
 * The rules of Sky Glider for one plane (plain Java, no Android): how the body steers, how the world is laid out,
 * what the plane touches. A pseudo-3D world: the plane flies forward along z at a constant speed; things come
 * toward it. World x is sideways, y is height (0 = low, up is positive), both in "pixels at the plane's depth".
 *
 * Nothing is ever lost or crashed: obstacles only wobble the plane, clouds only fog the view, rings that are
 * missed just float past. Two players get the same course (same seed) and their own score.
 */
public final class SkyLogic {

    /** Item kinds. */
    public static final int RING = 0, COIN = 1, STAR = 2, BALLOON = 3, BIRD = 4, CLOUD = 5, MAGNET = 6;
    /** Event kinds sent to the {@link Listener}. */
    public static final int EV_RING = 0, EV_RING_MISS = 1, EV_COIN = 2, EV_STAR = 3, EV_BUMP = 4, EV_FOG = 5, EV_MAGNET = 6;

    /** Distance between the camera and the plane; things at this distance are drawn at scale 1. */
    public static final float FOCAL = 600f;
    /** How far ahead of the plane the course is built. */
    public static final float SPAWN_DIST = 7000f;
    /** The plane stays within +-X_MAX sideways. */
    public static final float X_MAX = 900f;
    /** The plane stays between 0 and Y_MAX in height. */
    public static final float Y_MAX = 700f;
    /** Where the plane starts. */
    public static final float START_Y = 250f;

    /** Sideways speed of the plane at full steering (px/s). */
    static final float LAT_V = 850f;
    /** Climb / dive speed at full climb (px/s). */
    static final float ALT_V = 620f;
    /** Steering: no turn inside this many degrees, full turn at FULL_DEG. */
    static final float DEAD_DEG = 4f, FULL_DEG = 22f;
    /** Climb: no change inside this many px, full climb / dive at FULL_PX. */
    static final float DEAD_PX = 25f, FULL_PX = 80f;
    /** Smoothing time of steering and climbing (seconds). */
    static final float TAU = 0.15f;
    /** Wrist keypoints at least this confident are used. */
    static final float MIN_CONF = 0.3f;

    /** Points. */
    static final int RING_POINTS = 3, COIN_POINTS = 1, STAR_POINTS = 5;

    private static final float[] SPEED = {900f, 1150f, 1400f};
    private static final float[] INTERVAL = {2.2f, 1.9f, 1.6f};
    private static final float[] RING_R = {260f, 200f, 150f};
    private static final float[] LATERAL = {0.35f, 0.45f, 0.55f};
    private static final float[] OBSTACLE_P = {0.15f, 0.35f, 0.55f};
    private static final float[] CLOUD_P = {0.20f, 0.30f, 0.30f};
    private static final float[] STAR_P = {0.12f, 0.16f, 0.20f};
    private static final int CAP = 96;
    private static final float KEEP_BEHIND = 300f;
    /** Seconds the magnet pulls coins in after it is picked up. */
    public static final float MAGNET_TIME = 6f;
    private static final float PULL_Z = 3000f;

    /** One thing in the world. Objects are reused; do not keep references past a frame. */
    public static final class Item {
        /** Kind, see the constants. */
        public int kind;
        /** World position. */
        public float x, y, z;
        /** Radius that counts as a touch (clouds with 0 are only decoration). */
        public float r;
        /** True once the plane has passed it. */
        public boolean done;
        /** True if it was collected or touched. */
        public boolean hit;
        /** A random number 0..1 for small visual differences. */
        public float seed;
    }

    /** Hears what happens to the plane. */
    public interface Listener {
        /**
         * Something happened.
         * @param type one of the EV_ constants
         * @param it the item involved (reused afterwards: read it now)
         * @param points points earned (0 for none)
         */
        void onEvent(int type, Item it, int points);
    }

    private final int level;
    private final float speed, interval, ringR;
    private final Random course;
    private final Random wind;
    private final Item[] items = new Item[CAP];
    private int count;
    private final Listener listener;

    private float planeX, planeY, planeZ;
    private float steerTarget, climbTarget, steer, climb;
    private float wobble, fog;
    private int score, offered, missStreak;

    private float courseZ, pathX, pathY;
    private float magnetLeft;
    private int stepNo, magnetA, magnetB, magnetsMade;
    private float nextGust, gustLeft, gustDir, gustV;

    /**
     * Creates the rules for one plane and one round.
     * @param level 1 Easy, 2 Medium, 3 Hard
     * @param courseSeed seed of the course; give both players the same one
     * @param windSeed seed of the gusts (Hard only); give both players the same one for the same wind
     * @param listener hears the events, may be null
     */
    public SkyLogic(int level, long courseSeed, long windSeed, Listener listener) {
        this.level = Math.max(1, Math.min(3, level));
        int i = this.level - 1;
        this.speed = SPEED[i];
        this.interval = INTERVAL[i];
        this.ringR = RING_R[i];
        this.course = new Random(courseSeed);
        this.wind = new Random(windSeed);
        this.listener = listener;
        for (int k = 0; k < CAP; k++) items[k] = new Item();
        // one magnet early in the round and, half of the time, a second one later (own random numbers: the course stays the same)
        Random mr = new Random(courseSeed * 31L + 7L);
        magnetA = 8 + mr.nextInt(6);
        magnetB = mr.nextBoolean() ? 26 + mr.nextInt(7) : -1;
        planeY = START_Y;
        pathY = START_Y;
        nextGust = 5f + wind.nextFloat() * 4f;
        buildAhead();
    }

    // ---- reading the body ----

    /**
     * The angle of the shoulder line in degrees: positive when the shoulder on the right of the screen is lower.
     * @param p the player
     * @return degrees, or NaN if a shoulder is not seen
     */
    public static float shoulderAngle(PlayerPose p) {
        Keypoint l = p.kps[Kp.L_SHOULDER], r = p.kps[Kp.R_SHOULDER];
        if (!PoseFrame.ok(l, MIN_CONF) || !PoseFrame.ok(r, MIN_CONF)) return Float.NaN;
        float dx = r.x - l.x, dy = r.y - l.y;
        if (dx < 0f) {
            dx = -dx;
            dy = -dy;
        }
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    /**
     * Maps the shoulder angle to steering: dead zone +-4 degrees, full turn at +-22.
     * @param deg shoulder angle in degrees (NaN means straight)
     * @return -1 (full left) to 1 (full right)
     */
    public static float steerFor(float deg) {
        if (Float.isNaN(deg)) return 0f;
        float a = Math.abs(deg);
        if (a <= DEAD_DEG) return 0f;
        float v = Math.min(1f, (a - DEAD_DEG) / (FULL_DEG - DEAD_DEG));
        return deg < 0f ? -v : v;
    }

    /**
     * How far the wings are above the shoulders: the shoulder middle minus the average height of the wrists
     * that are held out (a wrist hanging lower than one shoulder width below the shoulders is not a wing).
     * @param p the player
     * @return pixels, positive when the wrists are above the shoulders; NaN if no wing is seen (level flight)
     */
    public static float wingHeight(PlayerPose p) {
        Keypoint l = p.kps[Kp.L_SHOULDER], r = p.kps[Kp.R_SHOULDER];
        float sw = PoseFrame.shoulderWidth(p);
        if (sw <= 0f || !PoseFrame.ok(l, MIN_CONF) || !PoseFrame.ok(r, MIN_CONF)) return Float.NaN;
        float sy = (l.y + r.y) * 0.5f, sum = 0f;
        int n = 0;
        for (int w = 0; w < 2; w++) {
            Keypoint k = p.kps[w == 0 ? Kp.L_WRIST : Kp.R_WRIST];
            if (PoseFrame.ok(k, MIN_CONF) && k.y < sy + sw) {
                sum += k.y;
                n++;
            }
        }
        return n == 0 ? Float.NaN : sy - sum / n;
    }

    /**
     * Maps the wing height to climbing: dead zone +-25 px, full climb at +80 px above, full dive at 80 px below.
     * @param px wing height in pixels (NaN means level)
     * @return -1 (dive) to 1 (climb)
     */
    public static float climbFor(float px) {
        if (Float.isNaN(px)) return 0f;
        float a = Math.abs(px);
        if (a <= DEAD_PX) return 0f;
        float v = Math.min(1f, (a - DEAD_PX) / (FULL_PX - DEAD_PX));
        return px < 0f ? -v : v;
    }

    /**
     * Reads the player's body: steering and climbing aim for what the pose says. A player who is not seen flies
     * straight and level.
     * @param p the player (may be null)
     */
    public void control(PlayerPose p) {
        if (p == null || !p.present) {
            steerTarget = 0f;
            climbTarget = 0f;
            return;
        }
        steerTarget = steerFor(shoulderAngle(p));
        climbTarget = climbFor(wingHeight(p));
    }

    /**
     * Sets the steering and climbing targets directly (remote and tests).
     * @param steerNow -1..1
     * @param climbNow -1..1
     */
    public void setTargets(float steerNow, float climbNow) {
        steerTarget = Math.max(-1f, Math.min(1f, steerNow));
        climbTarget = Math.max(-1f, Math.min(1f, climbNow));
    }

    /**
     * Overrides only the climbing target (the remote's up and down keys in test mode).
     * @param climbNow -1..1
     */
    public void setClimbTarget(float climbNow) {
        climbTarget = Math.max(-1f, Math.min(1f, climbNow));
    }

    // ---- stars ----

    /**
     * Fair stars: 1 always, 2 for 40% or more of the points that were offered, 3 for 70% or more.
     * @param scored points collected
     * @param offered points of everything that flew past the plane (obstacles give none)
     * @return 1..3
     */
    public static int stars(int scored, int offered) {
        if (offered <= 0) return 1;
        float f = (float) scored / offered;
        return f >= 0.70f ? 3 : f >= 0.40f ? 2 : 1;
    }

    // ---- moving ----

    /**
     * Advances the plane and the world.
     * @param dt seconds, at most 0.05
     */
    public void update(float dt) {
        float a = 1f - (float) Math.exp(-dt / TAU);
        steer += (steerTarget - steer) * a;
        climb += (climbTarget - climb) * a;
        if (level == 3) updateGust(dt);
        planeX = Math.max(-X_MAX, Math.min(X_MAX, planeX + (steer * LAT_V + gustV) * dt));
        planeY = Math.max(0f, Math.min(Y_MAX, planeY + climb * ALT_V * dt));
        planeZ += speed * dt;
        wobble = Math.max(0f, wobble - dt);
        fog = Math.max(0f, fog - dt);
        buildAhead();
        if (magnetLeft > 0f) {
            magnetLeft = Math.max(0f, magnetLeft - dt);
            float pull = 1f - (float) Math.exp(-dt / 0.28f);
            for (int i = 0; i < count; i++) {
                Item it = items[i];
                if (it.done) continue;
                if (it.z > planeZ + PULL_Z) break;
                if (it.kind == COIN) {
                    it.x += (planeX - it.x) * pull;
                    it.y += (planeY - it.y) * pull;
                }
            }
        }
        int keepFrom = 0;
        for (int i = 0; i < count; i++) {
            Item it = items[i];
            if (it.z > planeZ) break;
            if (!it.done) pass(it);
            if (it.z < planeZ - KEEP_BEHIND) keepFrom = i + 1;
        }
        if (keepFrom > 0) dropFront(keepFrom);
    }

    private void updateGust(float dt) {
        if (gustLeft > 0f) {
            gustLeft -= dt;
            float t = 1f - Math.max(0f, gustLeft) / 1.8f;
            gustV = gustDir * 320f * (float) Math.sin(Math.PI * t);
            if (gustLeft <= 0f) {
                gustV = 0f;
                nextGust = 5f + wind.nextFloat() * 4f;
            }
        } else {
            nextGust -= dt;
            if (nextGust <= 0f) {
                gustLeft = 1.8f;
                gustDir = wind.nextBoolean() ? 1f : -1f;
            }
        }
    }

    private void pass(Item it) {
        it.done = true;
        float d = (float) Math.hypot(planeX - it.x, planeY - it.y);
        switch (it.kind) {
            case RING: {
                offered += RING_POINTS;
                boolean got = d <= it.r;
                it.hit = got;
                if (got) score += RING_POINTS;
                ringResult(got);
                send(got ? EV_RING : EV_RING_MISS, it, got ? RING_POINTS : 0);
                break;
            }
            case COIN:
                offered += COIN_POINTS;
                if (d <= it.r) {
                    it.hit = true;
                    score += COIN_POINTS;
                    send(EV_COIN, it, COIN_POINTS);
                }
                break;
            case STAR:
                offered += STAR_POINTS;
                if (d <= it.r) {
                    it.hit = true;
                    score += STAR_POINTS;
                    send(EV_STAR, it, STAR_POINTS);
                }
                break;
            case MAGNET:
                if (d <= it.r) {
                    it.hit = true;
                    magnetLeft = MAGNET_TIME;
                    send(EV_MAGNET, it, 0);
                }
                break;
            case BALLOON:
            case BIRD:
                if (d <= it.r) {
                    it.hit = true;
                    wobble = 0.7f;
                    send(EV_BUMP, it, 0);
                }
                break;
            default: // clouds
                if (it.r > 0f && d <= it.r) {
                    it.hit = true;
                    fog = 0.7f;
                    send(EV_FOG, it, 0);
                }
                break;
        }
    }

    private void send(int type, Item it, int points) {
        if (listener != null) listener.onEvent(type, it, points);
    }

    /**
     * Records that a ring was collected or missed. Three missed rings in a row make the next ring ahead drift 30%
     * toward the plane.
     * @param got true if collected
     */
    void ringResult(boolean got) {
        if (got) {
            missStreak = 0;
            return;
        }
        missStreak++;
        if (missStreak >= 3) {
            missStreak = 0;
            for (int i = 0; i < count; i++) {
                Item it = items[i];
                if (it.kind == RING && !it.done && it.z > planeZ + 1500f) {
                    it.x += (planeX - it.x) * 0.30f;
                    it.y += (planeY - it.y) * 0.30f;
                    break;
                }
            }
        }
    }

    // ---- building the course ----

    private void buildAhead() {
        while (courseZ < planeZ + SPAWN_DIST) step();
    }

    private void step() {
        float spacing = interval * speed;
        float zPrev = courseZ, zNew = courseZ + spacing;
        float px0 = pathX, py0 = pathY;
        float maxDx = LATERAL[level - 1] * interval * LAT_V, maxDy = 0.45f * interval * ALT_V;
        float nx = clamp(px0 + (course.nextFloat() * 2f - 1f) * maxDx, -X_MAX, X_MAX);
        float ny = clamp(py0 + (course.nextFloat() * 2f - 1f) * maxDy, 0f, Y_MAX);
        float midX = (px0 + nx) * 0.5f, midY = (py0 + ny) * 0.5f;
        add(RING, nx, ny, zNew, ringR, course.nextFloat());
        boolean magnetStep = stepNo == magnetA || stepNo == magnetB;
        stepNo++;
        if (magnetStep) {
            magnetsMade++;
            add(MAGNET, px0 + (nx - px0) * 0.5f, py0 + (ny - py0) * 0.5f, zPrev + spacing * 0.5f, 140f, course.nextFloat());
        }
        for (int k = 1; k <= 3; k++) {
            if (magnetStep && k == 2) continue;
            float f = k * 0.25f;
            add(COIN, px0 + (nx - px0) * f, py0 + (ny - py0) * f, zPrev + spacing * f, 110f, course.nextFloat());
        }
        if (course.nextFloat() < STAR_P[level - 1]) {
            float side = course.nextBoolean() ? 1f : -1f;
            float sx = midX + side * (ringR * 1.1f + 80f);
            if (Math.abs(sx) > X_MAX) sx = midX - side * (ringR * 1.1f + 80f);
            add(STAR, clamp(sx, -X_MAX, X_MAX), midY, zPrev + spacing * 0.5f, 130f, course.nextFloat());
        }
        if (course.nextFloat() < OBSTACLE_P[level - 1]) {
            float side = course.nextBoolean() ? 1f : -1f;
            float off = ringR + 420f + course.nextFloat() * 350f;
            float ox = midX + side * off;
            if (Math.abs(ox) > X_MAX) ox = midX - side * off;
            if (Math.abs(ox) <= X_MAX + 200f && Math.abs(ox - midX) >= 500f) {
                boolean balloon = course.nextBoolean();
                add(balloon ? BALLOON : BIRD, ox, course.nextFloat() * Y_MAX,
                        zPrev + spacing * (0.15f + 0.7f * course.nextFloat()), balloon ? 130f : 90f, course.nextFloat());
            }
        }
        if (course.nextFloat() < CLOUD_P[level - 1]) {
            add(CLOUD, midX + (course.nextFloat() - 0.5f) * 300f, midY, zPrev + spacing * (0.3f + 0.4f * course.nextFloat()),
                    260f, course.nextFloat());
        }
        if (course.nextFloat() < 0.6f) {
            float side = course.nextBoolean() ? 1f : -1f;
            add(CLOUD, side * (900f + course.nextFloat() * 600f), 300f + course.nextFloat() * 600f,
                    zPrev + spacing * course.nextFloat(), 0f, course.nextFloat());
        }
        pathX = nx;
        pathY = ny;
        courseZ = zNew;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Puts an item into the world, keeping the list sorted by distance.
     * @return the item, or null if the world is full
     */
    Item add(int kind, float x, float y, float z, float r, float seed) {
        if (count >= CAP) return null;
        Item it = items[count];
        it.kind = kind;
        it.x = x;
        it.y = y;
        it.z = z;
        it.r = r;
        it.done = false;
        it.hit = false;
        it.seed = seed;
        int i = count++;
        while (i > 0 && items[i - 1].z > items[i].z) {
            Item t = items[i - 1];
            items[i - 1] = items[i];
            items[i] = t;
            i--;
        }
        return it;
    }

    private void dropFront(int n) {
        // the removed objects go to the end of the array: every slot at or after `count` is a spare object
        for (int i = 0; i < n; i++) {
            Item gone = items[0];
            System.arraycopy(items, 1, items, 0, CAP - 1);
            items[CAP - 1] = gone;
            count--;
        }
    }

    // ---- reading the state ----

    /** @return sideways position of the plane */
    public float planeX() { return planeX; }
    /** @return height of the plane */
    public float planeY() { return planeY; }
    /** @return distance flown */
    public float planeZ() { return planeZ; }
    /** @return smoothed steering -1..1 (also the bank of the plane) */
    public float steer() { return steer; }
    /** @return smoothed climb -1..1 */
    public float climb() { return climb; }
    /** @return seconds left of the wobble after touching a balloon or bird */
    public float wobble() { return wobble; }
    /** @return sideways push of the wind right now (px/s), 0 outside gusts and on Easy and Medium */
    public float gust() { return gustV; }
    /** @return seconds of magnet left (0 when it is not on) */
    public float magnet() { return magnetLeft; }
    /** @return how many magnets the course has made so far (1 or 2 per round) */
    int magnetsMade() { return magnetsMade; }
    /** Starts the magnet (tests). */
    void startMagnet() { magnetLeft = MAGNET_TIME; }
    /** @return seconds left of fog after flying through a cloud */
    public float fog() { return fog; }
    /** @return points collected */
    public int score() { return score; }
    /** @return points of everything that has flown past the plane */
    public int offered() { return offered; }
    /** @return rings missed in a row */
    public int missStreak() { return missStreak; }
    /** @return radius of the rings of this level */
    public float ringRadius() { return ringR; }
    /** @return how many items are in the world */
    public int itemCount() { return count; }

    /**
     * An item by distance order (0 is the nearest or just passed).
     * @param i index below {@link #itemCount()}
     * @return the item
     */
    public Item item(int i) { return items[i]; }

    /** Sets the plane position (tests). */
    void setPlane(float x, float y) {
        planeX = x;
        planeY = y;
    }
}
