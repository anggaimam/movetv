package com.wiggleplay.app.games.bowling;

/**
 * The ball and the ten pins, seen from above. Plain Java, no Android: the same inputs always give the same result.
 *
 * <p>Units: the lane is {@link #LANE_W} wide (x from -0.5 to 0.5, 0 is the middle) and {@link #LANE_L} long (y from 0 at
 * the foul line to 18 at the back of the pin deck). Everything moves in fixed {@link #STEP} second steps (120 per
 * second). Pins and the ball are circles that bounce off each other; a pin counts as down once it has been pushed
 * more than {@link #DOWN_DIST} from where it stood.
 *
 * <p>Levels: Easy has bumpers (the ball bounces off the sides, no gutters), a slower lane and 30% aim help toward the
 * head pin; Medium has gutters and 10% help; Hard has gutters, no help, a stronger curve and lighter pins.
 */
public final class BowlingPhysics {

    /** Lane width and length. */
    public static final float LANE_W = 1.0f, LANE_L = 18.0f;
    /** Ball and pin radius. */
    public static final float BALL_R = 0.11f, PIN_R = 0.06f;
    /** Pins in the triangle. */
    public static final int PINS = 10;
    /** Fixed simulation step in seconds (120 Hz). */
    public static final float STEP = 1f / 120f;
    /** A pin that has moved farther than this from its spot is down. */
    public static final float DOWN_DIST = 0.05f;
    /** Slowest and fastest ball speed in units per second. */
    public static final float MIN_SPEED = 6f, MAX_SPEED = 14f;
    /** Where the head pin stands, and the gaps between pins. */
    public static final float HEAD_Y = 16.2f, ROW_GAP = 0.17f, PIN_GAP = 0.19f;
    /** Outside this distance from the middle the ball is in the gutter. */
    public static final float GUTTER_X = 0.5f;
    /** Walls that stop pins from leaving the pin deck, and the back wall. */
    private static final float DECK_X = 0.62f, BACK_Y = 18.3f;

    private static final float BALL_MASS = 3f;
    private static final float REST = 0.85f;           // bounciness of collisions
    private static final float PIN_DRAG = 1.6f;        // per second
    private static final float STOP_SPEED = 0.05f;

    private final int level;
    private final float pinMass;
    private final float speedScale, spinScale, assist;
    private final boolean bumpers;

    // ball
    private float bx, by, bvx, bvy, spinAccel;
    private boolean ballOn, ballInGutter, ballGone;
    // pins
    private final float[] px = new float[PINS], py = new float[PINS], pvx = new float[PINS], pvy = new float[PINS];
    private final float[] homeX = new float[PINS], homeY = new float[PINS];
    private final boolean[] standing = new boolean[PINS];
    private final boolean[] down = new boolean[PINS];
    private final boolean[] hit = new boolean[PINS];
    private final float[] fall = new float[PINS];      // time since the pin went down (for tumbling)
    private float time;
    private int touches;                                // ball-pin and pin-pin knocks, for the sound

    /**
     * Creates a lane with all ten pins up.
     * @param level 1 Easy, 2 Medium, 3 Hard
     */
    public BowlingPhysics(int level) {
        this.level = Math.max(1, Math.min(3, level));
        bumpers = this.level == 1;
        speedScale = this.level == 1 ? 0.8f : 1f;
        spinScale = this.level == 3 ? 1.6f : 1f;
        assist = this.level == 1 ? 0.3f : this.level == 2 ? 0.1f : 0f;
        pinMass = this.level == 3 ? 0.85f : 1f;
        int k = 0;
        for (int row = 0; row < 4; row++) {
            for (int i = 0; i <= row; i++) {
                homeX[k] = (i - row / 2f) * PIN_GAP;
                homeY[k] = HEAD_Y + row * ROW_GAP;
                k++;
            }
        }
        resetPins();
    }

    /** Stands all ten pins up again (a new frame). */
    public void resetPins() {
        for (int i = 0; i < PINS; i++) {
            standing[i] = true;
            setPin(i);
        }
        ballOn = false;
        ballGone = false;
        time = 0f;
    }

    /**
     * Takes away the pins that are down and puts the others back on their spots (the second roll of a frame).
     */
    public void sweep() {
        for (int i = 0; i < PINS; i++) {
            if (standing[i] && !down[i]) {
                standing[i] = true;
            } else {
                standing[i] = false;
            }
            setPin(i);
        }
        ballOn = false;
        ballGone = false;
        time = 0f;
    }

    private void setPin(int i) {
        px[i] = homeX[i];
        py[i] = homeY[i];
        pvx[i] = pvy[i] = 0f;
        down[i] = false;
        hit[i] = false;
        fall[i] = 0f;
    }

    /**
     * Rolls a ball.
     * @param startX where it leaves the foul line, -0.4..0.4 (clamped)
     * @param angle direction in radians, 0 straight down the lane, positive toward +x (clamped to +-0.12)
     * @param power 0..1, how hard (6..14 units per second)
     * @param spin -1..1, curves the ball toward the side of the sign (0 for a straight ball)
     */
    public void roll(float startX, float angle, float power, float spin) {
        float sx = Math.max(-0.4f, Math.min(0.4f, startX));
        float toHead = (float) Math.atan2(-sx, HEAD_Y);
        float a = angle * (1f - assist) + toHead * assist;
        a = Math.max(-0.12f, Math.min(0.12f, a));
        float sp = (MIN_SPEED + (MAX_SPEED - MIN_SPEED) * Math.max(0f, Math.min(1f, power))) * speedScale;
        bx = sx;
        by = 0f;
        bvx = (float) Math.sin(a) * sp;
        bvy = (float) Math.cos(a) * sp;
        spinAccel = Math.max(-1f, Math.min(1f, spin)) * 0.5f * spinScale;
        ballOn = true;
        ballInGutter = false;
        ballGone = false;
        touches = 0;
    }

    /** Advances one fixed step. */
    public void step() {
        final float dt = STEP;
        time += dt;
        if (ballOn) moveBall(dt);
        for (int i = 0; i < PINS; i++) {
            if (!standing[i]) continue;
            if (down[i]) fall[i] += dt;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            float drag = (float) Math.exp(-PIN_DRAG * (down[i] ? 2f : 1f) * dt);
            pvx[i] *= drag;
            pvy[i] *= drag;
            if (px[i] < -DECK_X + PIN_R) { px[i] = -DECK_X + PIN_R; pvx[i] = -pvx[i] * 0.4f; }
            if (px[i] > DECK_X - PIN_R) { px[i] = DECK_X - PIN_R; pvx[i] = -pvx[i] * 0.4f; }
            if (py[i] > BACK_Y - PIN_R) { py[i] = BACK_Y - PIN_R; pvy[i] = -pvy[i] * 0.3f; }
            if (!down[i]) {
                float dx = px[i] - homeX[i], dy = py[i] - homeY[i];
                if (dx * dx + dy * dy > DOWN_DIST * DOWN_DIST) down[i] = true;
            }
        }
        for (int i = 0; i < PINS; i++) {
            if (!standing[i]) continue;
            if (ballOn && !ballInGutter) collide(i);
            for (int j = i + 1; j < PINS; j++) if (standing[j]) pinPin(i, j);
        }
    }

    private void moveBall(float dt) {
        if (!ballInGutter) bvx += spinAccel * dt;
        bx += bvx * dt;
        by += bvy * dt;
        if (!ballInGutter) {
            float limit = GUTTER_X - BALL_R * 0.5f;
            if (Math.abs(bx) > limit) {
                if (bumpers) {
                    bx = Math.signum(bx) * limit;
                    bvx = -bvx * 0.6f;
                    spinAccel = 0f;
                } else if (Math.abs(bx) > GUTTER_X + 0.02f) {
                    ballInGutter = true;
                    bx = Math.signum(bx) * (GUTTER_X + 0.06f);
                    bvx = 0f;
                    spinAccel = 0f;
                }
            }
        }
        // a ball that has run out of speed among the pins is taken away (the pit), so the roll can end
        if (by > BACK_Y + 0.5f || (by > HEAD_Y - 0.5f && bvx * bvx + bvy * bvy < 0.3f * 0.3f)) {
            ballOn = false;
            ballGone = true;
        }
    }

    private void collide(int i) {
        float dx = px[i] - bx, dy = py[i] - by, r = BALL_R + PIN_R, d2 = dx * dx + dy * dy;
        if (d2 >= r * r || d2 < 1e-9f) return;
        float d = (float) Math.sqrt(d2), nx = dx / d, ny = dy / d;
        // push apart
        float over = r - d;
        float wb = pinMass / (BALL_MASS + pinMass), wp = BALL_MASS / (BALL_MASS + pinMass);
        px[i] += nx * over * wp;
        py[i] += ny * over * wp;
        bx -= nx * over * wb;
        by -= ny * over * wb;
        float rv = (pvx[i] - bvx) * nx + (pvy[i] - bvy) * ny;
        if (rv >= 0f) return;
        float jmp = -(1f + REST) * rv / (1f / BALL_MASS + 1f / pinMass);
        bvx -= jmp / BALL_MASS * nx;
        bvy -= jmp / BALL_MASS * ny;
        pvx[i] += jmp / pinMass * nx;
        pvy[i] += jmp / pinMass * ny;
        if (!hit[i]) { hit[i] = true; touches++; }
    }

    private void pinPin(int i, int j) {
        float dx = px[j] - px[i], dy = py[j] - py[i], r = 2f * PIN_R, d2 = dx * dx + dy * dy;
        if (d2 >= r * r || d2 < 1e-9f) return;
        float d = (float) Math.sqrt(d2), nx = dx / d, ny = dy / d, over = (r - d) * 0.5f;
        px[i] -= nx * over;
        py[i] -= ny * over;
        px[j] += nx * over;
        py[j] += ny * over;
        float rv = (pvx[j] - pvx[i]) * nx + (pvy[j] - pvy[i]) * ny;
        if (rv >= 0f) return;
        float jmp = -(1f + REST) * rv / 2f;
        pvx[i] -= jmp * nx;
        pvy[i] -= jmp * ny;
        pvx[j] += jmp * nx;
        pvy[j] += jmp * ny;
        if (!hit[i] || !hit[j]) touches++;
        hit[i] = hit[j] = true;
    }

    /**
     * Whether everything has stopped (the ball is gone or slow and no pin is moving).
     * @return true when nothing moves any more
     */
    public boolean settled() {
        if (ballOn && !ballGone) return false;
        for (int i = 0; i < PINS; i++) {
            if (standing[i] && pvx[i] * pvx[i] + pvy[i] * pvy[i] > STOP_SPEED * STOP_SPEED) return false;
        }
        return true;
    }

    /**
     * Runs the simulation until things have settled (or a time limit), for tests and quick results.
     * @param maxSeconds the most simulated time
     * @return pins knocked down in this roll
     */
    public int runToEnd(float maxSeconds) {
        int steps = (int) (maxSeconds / STEP);
        for (int i = 0; i < steps && !(time > 0.5f && settled()); i++) step();
        return pinsDown();
    }

    /**
     * Pins knocked down since the last {@link #resetPins} or {@link #sweep}.
     * @return 0..10
     */
    public int pinsDown() {
        int n = 0;
        for (int i = 0; i < PINS; i++) if (standing[i] && down[i]) n++;
        return n;
    }

    /**
     * Pins still standing (not down and not swept away).
     * @return 0..10
     */
    public int pinsUp() {
        int n = 0;
        for (int i = 0; i < PINS; i++) if (standing[i] && !down[i]) n++;
        return n;
    }

    /**
     * Pins that were on the lane when the roll began (standing or fallen but not yet swept).
     * @return 0..10
     */
    public int pinsOnLane() {
        int n = 0;
        for (int i = 0; i < PINS; i++) if (standing[i]) n++;
        return n;
    }

    /** @return knocks (ball hits pin or pin hits pin) so far in this roll, for the clatter sound */
    public int knocks() { return touches; }

    /** @return time since the roll began, in seconds */
    public float time() { return time; }

    /** @return true while the ball is on the lane */
    public boolean ballOnLane() { return ballOn; }

    /** @return true when the ball is in a gutter */
    public boolean inGutter() { return ballInGutter; }

    /** @return the ball's x */
    public float ballX() { return bx; }

    /** @return the ball's y (0 at the foul line) */
    public float ballY() { return by; }

    /**
     * A pin's place.
     * @param i 0..9 (0 is the head pin)
     * @return x
     */
    public float pinX(int i) { return px[i]; }

    /**
     * A pin's place.
     * @param i 0..9
     * @return y
     */
    public float pinY(int i) { return py[i]; }

    /**
     * Whether a pin is on the lane (not swept away).
     * @param i 0..9
     * @return true if it is on the lane
     */
    public boolean onLane(int i) { return standing[i]; }

    /**
     * Whether a pin has been knocked down.
     * @param i 0..9
     * @return true if down
     */
    public boolean isDown(int i) { return standing[i] && down[i]; }

    /**
     * Seconds since a pin went down (0 while it stands), for the tumble.
     * @param i 0..9
     * @return seconds
     */
    public float fallTime(int i) { return fall[i]; }

    /**
     * Direction a fallen pin lies, from the way it was pushed: -1 (to the left) .. 1 (to the right).
     * @param i 0..9
     * @return -1..1
     */
    public float pushSide(int i) {
        float dx = px[i] - homeX[i];
        return Math.max(-1f, Math.min(1f, dx * 6f));
    }

    /** @return the level (1..3) */
    public int level() { return level; }

    /** @return true on Easy, where the sides bounce the ball back */
    public boolean hasBumpers() { return bumpers; }
}
