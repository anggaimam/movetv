package com.wiggleplay.app.games.bubbles;

import com.wiggleplay.app.games.fruit.HandTrack;

import java.util.Random;

/**
 * The rules of Bubble Burst for one player (or one half of the screen). Plain Java, no android imports, so it is
 * tested on a laptop. Bubbles appear inside the player's reach (around the shoulders, never on top of the face),
 * live a few seconds and fade away; nothing is ever taken away from a child. Medium and Hard add colours (blue is
 * for the left hand, red for the right), golden bubbles, spiky decoys that take 1 (Medium) or 2 (Hard) points
 * away (never below zero), and on Hard two-hand bubbles.
 */
public final class BubbleLogic {

    /** Bubble kinds. */
    public static final int ANY = 0, BLUE = 1, RED = 2, GOLD = 3, BOTH = 4, DECOY = 5;
    /** Points per kind (decoys give nothing). */
    public static final int[] POINTS = {1, 1, 1, 5, 3, 0};

    /** Tells the scene what happened. */
    public interface Listener {
        /**
         * A bubble was popped.
         * @param b the bubble (still holds its position)
         * @param points points scored
         */
        void onPop(Bubble b, int points);

        /**
         * A hand of the wrong side touched a coloured bubble (it only wobbles).
         * @param b the bubble
         */
        void onWrongHand(Bubble b);

        /**
         * A spiky decoy was touched: it takes a few points away (the score never goes below zero).
         * @param b the decoy
         * @param lost points taken off (positive number, 0 when the score was already 0)
         */
        void onDecoy(Bubble b, int lost);

        /**
         * A bubble faded away on its own.
         * @param b the bubble
         */
        void onFade(Bubble b);
    }

    /** One bubble. */
    public static final class Bubble {
        public int kind, serial, look;
        public float x, y, r, age, life, wobble, born;
        boolean live;
    }

    static final int MAX_BUBBLES = 3;
    static final float FADE = 0.5f;
    static final float DRIFT = 14f;
    static final float HIT_FACTOR = 1.4f;
    static final float MARGIN = 120f, TOP = 230f, BOTTOM = 880f;
    static final float STAGE_W = 1920f;

    private final int level;
    private final Random rnd;
    private final float x0, x1;
    private final Listener listener;
    private final Bubble[] bubbles = new Bubble[MAX_BUBBLES];
    private final float maxCount, radius, lifetime;
    private float spawnIn = 0.6f, clock;
    private int score, offered, popped, serial;
    private float reachX = Float.NaN, reachY, reachR, noseX, noseY, noseAvoid;
    private boolean hasNose;

    /**
     * Creates the rules.
     * @param level 1..3
     * @param seed random seed (tests use a fixed one)
     * @param x0 left edge of this player's part of the screen (px)
     * @param x1 right edge
     * @param listener receives events
     */
    public BubbleLogic(int level, long seed, float x0, float x1, Listener listener) {
        this.level = Math.max(1, Math.min(3, level));
        this.rnd = new Random(seed);
        this.x0 = x0;
        this.x1 = x1;
        this.listener = listener;
        for (int i = 0; i < bubbles.length; i++) bubbles[i] = new Bubble();
        this.maxCount = this.level == 1 ? 1 : (this.level == 2 ? 2 : 3);
        this.radius = this.level == 1 ? 85f : (this.level == 2 ? 75f : 65f);
        this.lifetime = this.level == 1 ? 3.0f : (this.level == 2 ? 2.4f : 1.9f);
    }

    /**
     * Tells where the player can reach (call every frame the body is seen).
     * @param cx centre (shoulder midpoint) x
     * @param cy centre y
     * @param shoulderWidth distance between the shoulders in px
     * @param faceX nose x, or NaN when unknown
     * @param faceY nose y
     */
    public void setReach(float cx, float cy, float shoulderWidth, float faceX, float faceY) {
        float sw = Math.max(80f, shoulderWidth);
        reachX = cx;
        reachY = cy;
        reachR = Math.max(300f, Math.min(560f, 2.2f * sw));
        hasNose = !Float.isNaN(faceX);
        noseX = faceX;
        noseY = faceY;
        noseAvoid = 0.6f * sw + radius;
    }

    /** @return score so far */
    public int score() { return score; }

    /** @return points that were on offer so far (decoys excluded) */
    public int offered() { return offered; }

    /** @return bubbles popped */
    public int popped() { return popped; }

    /** @return how many bubbles are on screen */
    public int activeCount() {
        int n = 0;
        for (Bubble b : bubbles) if (b.live) n++;
        return n;
    }

    /**
     * A bubble on screen.
     * @param i 0..activeCount()-1
     * @return the bubble
     */
    public Bubble at(int i) {
        int n = 0;
        for (Bubble b : bubbles) if (b.live) {
            if (n == i) return b;
            n++;
        }
        return null;
    }

    /**
     * Stars for a finished round: 1 always, 2 from 40% of the offered points, 3 from 70%.
     * @param scored points scored
     * @param offeredPoints points that were offered
     * @return 1..3
     */
    public static int stars(int scored, int offeredPoints) {
        if (offeredPoints <= 0) return 1;
        float f = scored / (float) offeredPoints;
        return f >= 0.70f ? 3 : (f >= 0.40f ? 2 : 1);
    }

    /**
     * Advances the rules.
     * @param dt seconds
     * @param nowMs clock in ms
     * @param left tracker of the left wrist
     * @param right tracker of the right wrist
     * @param spawning false while the round is ending: no new bubbles
     */
    public void update(float dt, long nowMs, HandTrack left, HandTrack right, boolean spawning) {
        clock += dt;
        for (Bubble b : bubbles) {
            if (!b.live) continue;
            b.age += dt;
            b.y -= DRIFT * dt;
            if (b.wobble > 0f) b.wobble = Math.max(0f, b.wobble - dt);
            if (b.age >= b.life) {
                b.live = false;
                listener.onFade(b);
                continue;
            }
            if (b.age < 0.15f) continue; // just born: give the eye a moment
            check(b, nowMs, left, right);
        }
        if (spawning && activeCount() < maxCount) {
            spawnIn -= dt;
            if (spawnIn <= 0f) {
                if (spawn()) spawnIn = 0.35f + rnd.nextFloat() * 0.45f; else spawnIn = 0.1f;
            }
        }
    }

    private boolean near(HandTrack h, Bubble b, long nowMs) {
        if (h == null || !h.valid()) return false;
        if (Math.hypot(h.x() - b.x, h.y() - b.y) <= b.r * HIT_FACTOR) return true;
        for (int i = 0; i < h.samples(); i++) {
            if (nowMs - h.sampleMs(i) > HandTrack.LOOK_BACK_MS) break;
            if (Math.hypot(h.sampleX(i) - b.x, h.sampleY(i) - b.y) <= b.r) return true;
        }
        return false;
    }

    private void check(Bubble b, long nowMs, HandTrack left, HandTrack right) {
        boolean l = near(left, b, nowMs), r = near(right, b, nowMs);
        if (!l && !r) return;
        switch (b.kind) {
            case BLUE:
                if (l) pop(b); else if (b.wobble <= 0f) { b.wobble = 0.5f; listener.onWrongHand(b); }
                break;
            case RED:
                if (r) pop(b); else if (b.wobble <= 0f) { b.wobble = 0.5f; listener.onWrongHand(b); }
                break;
            case BOTH:
                if (l && r) pop(b); else if (b.wobble <= 0f) { b.wobble = 0.5f; listener.onWrongHand(b); }
                break;
            case DECOY:
                b.live = false;
                int lost = Math.min(score, penalty());
                score -= lost;
                listener.onDecoy(b, lost);
                break;
            default:
                pop(b);
        }
    }

    /** @return points a spiky decoy takes away: Medium 1, Hard 2 */
    public int penalty() { return level == 2 ? 1 : 2; }

    private void pop(Bubble b) {
        b.live = false;
        int p = POINTS[b.kind];
        score += p;
        popped++;
        listener.onPop(b, p);
    }

    private int pickKind() {
        float v = rnd.nextFloat();
        if (level == 1) return ANY;
        if (level == 2) return v < 0.08f ? GOLD : (v < 0.20f ? DECOY : (v < 0.60f ? BLUE : RED));
        if (v < 0.07f) return GOLD;
        if (v < 0.19f) return BOTH;
        if (v < 0.37f) return DECOY;
        return v < 0.68f ? BLUE : RED;
    }

    private boolean spawn() {
        float cx = Float.isNaN(reachX) ? (x0 + x1) / 2f : reachX;
        float cy = Float.isNaN(reachX) ? 560f : reachY;
        float rr = Float.isNaN(reachX) ? 420f : reachR;
        float lo = x0 + MARGIN, hi = x1 - MARGIN;
        int kind = pickKind();
        for (int tries = 0; tries < 14; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            float d = (float) Math.sqrt(rnd.nextDouble()) * rr;
            float x = cx + (float) Math.cos(a) * d, y = cy + (float) Math.sin(a) * d;
            x = Math.max(lo, Math.min(hi, x));
            y = Math.max(TOP, Math.min(BOTTOM, y));
            if (hasNose && Math.hypot(x - noseX, y - noseY) < noseAvoid) continue;
            boolean clear = true;
            for (Bubble o : bubbles) {
                if (o.live && Math.hypot(x - o.x, y - o.y) < o.r + radius + 30f) clear = false;
            }
            if (!clear) continue;
            for (Bubble b : bubbles) {
                if (b.live) continue;
                b.live = true;
                b.kind = kind;
                b.serial = ++serial;
                b.x = x;
                b.y = y;
                b.r = radius * (0.9f + rnd.nextFloat() * 0.25f);
                b.look = rnd.nextInt(3);
                b.age = 0f;
                b.life = lifetime;
                b.wobble = 0f;
                b.born = clock;
                offered += POINTS[kind];
                return true;
            }
            return false;
        }
        return false;
    }
}
