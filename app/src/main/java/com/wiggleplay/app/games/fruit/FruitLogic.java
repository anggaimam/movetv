package com.wiggleplay.app.games.fruit;

import com.wiggleplay.app.core.Pool;

import java.util.Random;

/**
 * The rules of Fruit Splash for one player (or one half of the screen): spawning, flight, slicing, scoring,
 * the gentle help after misses, and the stars. Plain Java so it is unit-tested on the JVM. Nothing is allocated
 * after construction. Nobody loses anything: a missed fruit just falls, the grumpy coconut scores 0.
 */
public final class FruitLogic {

    /** Number of ordinary fruit kinds: 0 apple, banana, orange, pear, strawberry, grapes, watermelon, pineapple, lemon, peach, cherries, kiwi, mango, blueberries, plum, 15 papaya. */
    public static final int FRUIT_KINDS = 16;
    /** Golden bonus star fruit (+5). */
    public static final int STAR = 16;
    /** The grumpy coconut (takes points away on Hard, 0 on Medium; the score never goes below 0). */
    public static final int COCONUT = 17;
    /** Number of kinds in all. */
    public static final int KINDS = 18;

    static final float STAGE_H = 1080f;
    static final int STAR_POINTS = 5, COMBO_POINTS = 3;
    static final int HELP_AFTER_MISSES = 4, HELP_SLICES = 2;
    static final float HELP_RADIUS = 1.2f;
    /** Time to slice three fruit for the combo bonus: 1.0 s on Medium, 1.5 s on Hard. */
    static final long COMBO_WINDOW_MS = 1000, COMBO_WINDOW_HARD_MS = 1500;

    /** One fruit in flight. */
    public static final class Fruit {
        /** Kind (0..7). */
        public int kind;
        /** Centre in stage px. */
        public float x, y;
        /** Velocity in px/s. */
        public float vx, vy;
        /** Hit radius in px (already includes any help). */
        public float r;
        /** Seconds since it appeared. */
        public float age;
        /** Seconds it takes to reach the top of its flight. */
        public float rise;
        /** Height of the flight in px (how far up it goes). */
        public float apex;
        /** Spin angle in degrees (drawing only). */
        public float spin;
        /** Spin speed in degrees per second (drawing only). */
        public float spinSpeed;
    }

    /** Hears what happens. Called from {@link #update}; do not allocate in it. */
    public interface Listener {
        /**
         * A fruit was sliced.
         * @param f the fruit (still holds its position)
         * @param dirX slice direction x (unit)
         * @param dirY slice direction y (unit)
         * @param points points just earned (includes any combo bonus)
         * @param combo true if this slice completed a combo of three
         */
        void onSlice(Fruit f, float dirX, float dirY, int points, boolean combo);

        /**
         * The grumpy coconut was hit.
         * @param f the coconut
         * @param dirX slice direction x
         * @param dirY slice direction y
         * @param lost points taken away (0 when the score was already 0 or the level takes none)
         */
        void onCoconut(Fruit f, float dirX, float dirY, int lost);

        /**
         * A fruit fell away unsliced (no penalty).
         * @param f the fruit
         */
        void onMiss(Fruit f);
    }

    /** What one level asks for. */
    public static final class Params {
        /** Seconds to rise to the top. */
        public final float rise;
        /** Most fruit in the air at once. */
        public final int maxAtOnce;
        /** Hit radius in px. */
        public final float radius;
        /** Chance a spawn is the golden star fruit. */
        public final float golden;
        /** Chance a spawn is the grumpy coconut. */
        public final float coconut;
        /** Chance a fruit drifts sideways. */
        public final float sideways;
        /** Whether three quick slices earn a bonus. */
        public final boolean combo;
        /** Time allowed for those three slices, in ms (1 s on Medium, 1.5 s on Hard). */
        public long comboWindowMs = COMBO_WINDOW_MS;
        /** Chance that a spawn launches two or three fruit together. */
        public final float burst;
        /** Points the grumpy coconut takes away. */
        public final int coconutPenalty;
        /** The hand must travel this many px within 200 ms to slice (a resting hand cuts nothing). */
        public final float minPath;

        Params(float rise, int maxAtOnce, float radius, float golden, float coconut, float sideways, boolean combo,
               float burst, int coconutPenalty, float minPath) {
            this.burst = burst;
            this.coconutPenalty = coconutPenalty;
            this.minPath = minPath;
            this.rise = rise;
            this.maxAtOnce = maxAtOnce;
            this.radius = radius;
            this.golden = golden;
            this.coconut = coconut;
            this.sideways = sideways;
            this.combo = combo;
        }
    }

    /**
     * What a level asks for.
     * @param level 1 (Easy), 2 (Medium) or 3 (Hard); anything else counts as 1
     * @param players 1 or 2: in 2 players radii are 25% bigger and speeds 0.85 times
     * @return the parameters
     */
    public static Params params(int level, int players) {
        float rise, radius, golden = 0f, coconut = 0f, side, burst = 0f, minPath;
        int max, penalty = 0;
        boolean combo = false;
        switch (level) {
            case 2:
                rise = 3.0f; max = 3; radius = 75f; golden = 0.10f; coconut = 0.12f; side = 0.30f; burst = 0.25f;
                combo = true; minPath = 90f;
                break;
            case 3:
                rise = 2.4f; max = 5; radius = 65f; golden = 0.08f; coconut = 0.18f; side = 0.50f; combo = true;
                burst = 0.40f; penalty = 3; minPath = 100f;
                break;
            default:
                rise = 3.5f; max = 1; radius = 90f; side = 0.15f; minPath = 70f;
                break;
        }
        if (players == 2) {
            radius *= 1.25f;
            rise /= 0.85f;
        }
        Params out = new Params(rise, max, radius, golden, coconut, side, combo, burst, penalty, minPath);
        out.comboWindowMs = level >= 3 ? COMBO_WINDOW_HARD_MS : COMBO_WINDOW_MS;
        return out;
    }

    /**
     * Stars for a score: 3 stars at 45, 2 at 25, 1 at 10 on Easy (Medium thresholds times 1.5, Hard times 2),
     * and everyone gets at least 1.
     * @param level 1..3
     * @param score points
     * @return 1, 2 or 3
     */
    public static int stars(int level, int score) {
        float m = level == 3 ? 2f : (level == 2 ? 1.5f : 1f);
        if (score >= Math.round(45f * m)) return 3;
        if (score >= Math.round(25f * m)) return 2;
        return 1;
    }

    private final Params p;
    private final Random rnd;
    private final float x0, x1;
    private final Listener listener;
    private final Pool<Fruit> pool = new Pool<>(12, Fruit::new);
    private int lastSpawnKind = -1;
    private final long[] sliceTimes = new long[3];
    private int sliceCount;
    private float spawnWait = 0.6f;
    private int score, missStreak, helpSlices;

    /**
     * Creates the logic for one player.
     * @param level 1..3
     * @param players 1 or 2
     * @param seed random seed (the same seed gives the same game)
     * @param regionX0 left edge of this player's area in stage px
     * @param regionX1 right edge of this player's area
     * @param listener hears slices and misses
     */
    public FruitLogic(int level, int players, long seed, float regionX0, float regionX1, Listener listener) {
        this.p = params(level, players);
        this.rnd = new Random(seed);
        this.x0 = regionX0;
        this.x1 = regionX1;
        this.listener = listener;
    }

    /** @return points so far */
    public int score() { return score; }

    /** @return number of fruit in the air */
    public int activeCount() { return pool.size(); }

    /**
     * A fruit in the air.
     * @param i 0..activeCount()-1
     * @return the fruit
     */
    public Fruit at(int i) { return pool.at(i); }

    /** @return the slices still needed before the extra help ends (0 = no help now) */
    public int helpLeft() { return helpSlices; }

    /** @return misses in a row */
    public int missStreak() { return missStreak; }

    /**
     * Advances the game by one frame: spawns, moves, slices and drops fruit.
     * @param dt seconds since the last frame
     * @param nowMs clock in ms
     * @param hands the wrists of this player (null entries are skipped)
     * @param spawning false stops new fruit (the round is ending)
     */
    public void update(float dt, long nowMs, HandTrack[] hands, boolean spawning) {
        if (spawning) {
            spawnWait -= dt;
            if (spawnWait <= 0f && pool.size() < p.maxAtOnce && (p.maxAtOnce > 1 || pool.size() == 0)) spawn(nowMs);
        }
        for (int i = pool.size() - 1; i >= 0; i--) {
            Fruit f = pool.at(i);
            f.age += dt;
            f.vy += gravity(f) * dt;
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            f.spin += f.spinSpeed * dt;
            boolean sliced = false;
            for (int h = 0; h < hands.length && !sliced; h++) {
                HandTrack t = hands[h];
                if (t != null && t.hits(f.x, f.y, f.r, nowMs, p.minPath)) {
                    slice(f, t.dirX(), t.dirY(), nowMs);
                    sliced = true;
                }
            }
            if (sliced) {
                pool.freeAt(i);
            } else if (f.age > f.rise && f.y > STAGE_H + f.r + 20f) {
                if (f.kind != COCONUT) {
                    missStreak++;
                    if (missStreak >= HELP_AFTER_MISSES && helpSlices == 0) {
                        helpSlices = HELP_SLICES;
                        missStreak = 0;
                    }
                }
                listener.onMiss(f);
                pool.freeAt(i);
            }
        }
    }

    /**
     * Slices one fruit right now as if a hand hit it (used by tests).
     * @param index position in the active list
     * @param nowMs clock in ms
     */
    void forceSlice(int index, long nowMs) {
        Fruit f = pool.at(index);
        slice(f, 0f, -1f, nowMs);
        pool.freeAt(index);
    }

    private float gravity(Fruit f) {
        return 2f * f.apex / (f.rise * f.rise);
    }

    private void spawn(long nowMs) {
        int n = 1;
        if (rnd.nextFloat() < p.burst) n = 2 + rnd.nextInt(2);
        for (int k = 0; k < n && pool.size() < p.maxAtOnce; k++) spawnOne();
        spawnWait = 0.45f + rnd.nextFloat() * 0.85f + (n > 1 ? 0.6f : 0f);
        if (rnd.nextFloat() < 0.12f) spawnWait += 0.8f; // now and then a breather, so the rhythm is not guessable
    }

    private void spawnOne() {
        Fruit f = pool.obtain();
        if (f == null) return;
        float roll = rnd.nextFloat();
        if (roll < p.coconut) f.kind = COCONUT;
        else if (roll < p.coconut + p.golden) f.kind = STAR;
        else {
            f.kind = rnd.nextInt(FRUIT_KINDS);
            if (f.kind == lastSpawnKind) f.kind = (f.kind + 1 + rnd.nextInt(FRUIT_KINDS - 1)) % FRUIT_KINDS;
        }
        lastSpawnKind = f.kind;
        f.r = p.radius * (helpSlices > 0 ? HELP_RADIUS : 1f);
        f.rise = p.rise * (0.85f + rnd.nextFloat() * 0.30f);
        f.apex = (0.50f + rnd.nextFloat() * 0.35f) * STAGE_H;
        float margin = f.r + 60f;
        float lo = x0 + margin, hi = x1 - margin;
        f.x = lo + rnd.nextFloat() * Math.max(1f, hi - lo);
        f.y = STAGE_H + f.r;
        f.vy = -2f * f.apex / f.rise;
        f.vx = 0f;
        if (rnd.nextFloat() < p.sideways) {
            // aim for a landing spot at least 250 px away, so the fruit travels across and stays in its area
            float target = lo + rnd.nextFloat() * Math.max(1f, hi - lo);
            if (Math.abs(target - f.x) < 250f) target = f.x < (lo + hi) / 2f ? Math.min(hi, f.x + 300f) : Math.max(lo, f.x - 300f);
            f.vx = (target - f.x) / (2f * f.rise);
        }
        f.age = 0f;
        f.spin = rnd.nextFloat() * 360f;
        f.spinSpeed = (rnd.nextFloat() - 0.5f) * 220f;
    }

    private void slice(Fruit f, float dx, float dy, long nowMs) {
        if (f.kind == COCONUT) {
            int before = score;
            score = Math.max(0, score - p.coconutPenalty);
            sliceCount = 0;
            listener.onCoconut(f, dx, dy, before - score);
            return;
        }
        int pts = f.kind == STAR ? STAR_POINTS : 1;
        boolean combo = false;
        if (p.combo) {
            sliceTimes[sliceCount % 3] = nowMs;
            sliceCount++;
            if (sliceCount >= 3) {
                long oldest = Long.MAX_VALUE;
                for (long t : sliceTimes) oldest = Math.min(oldest, t);
                if (nowMs - oldest <= p.comboWindowMs) {
                    combo = true;
                    pts += COMBO_POINTS;
                    sliceCount = 0;
                }
            }
        }
        score += pts;
        missStreak = 0;
        if (helpSlices > 0) helpSlices--;
        listener.onSlice(f, dx, dy, pts, combo);
    }
}
