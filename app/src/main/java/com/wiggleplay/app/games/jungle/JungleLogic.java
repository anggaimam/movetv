package com.wiggleplay.app.games.jungle;

import java.util.Random;

/**
 * The rules of Jungle Dash for one runner (plain Java, no Android). The monkey runs in place; the jungle comes
 * toward it. The course is a list of things with an arrival time on the "course clock", which runs at normal speed
 * except for one second after a stumble, when it runs at 60% (a slight slowdown that moves everything later, so the
 * timing windows stay fair). Two players get the same course (same seed) and their own score.
 *
 * <ul>
 *   <li>Log: cleared when the monkey is in a jump (it lasts 0.9 s whatever the player does) at the moment the log
 *       arrives, so a jump that started anywhere from 0.9 s before to the moment of arrival works: a window of
 *       +-0.45 s around the best moment.</li>
 *   <li>Double log (Hard): the same, but the jump must also be a big one (shoulders up at least 0.5 widths).</li>
 *   <li>Vine: cleared when the monkey is ducking at the moment the vine arrives. A duck is shown for at least 0.6 s.</li>
 *   <li>A hit is a comic stumble, a one-second slowdown and no points. Nothing is ever lost and nothing ends the run.</li>
 *   <li>Bananas: on the ground (collected when the monkey is not high) or high in the air (when it is), golden coconut
 *       +5 high up.</li>
 * </ul>
 * Points: banana +1, golden coconut +5, obstacle cleared +2. Stars are fair: 1 always, 2 for 40% and 3 for 70% of
 * the points that came past the monkey.
 */
public final class JungleLogic {

    /** Obstacle kinds. */
    public static final int LOG = 0, VINE = 1, DOUBLE_LOG = 2;
    /** Item kinds. */
    public static final int GROUND_BANANA = 0, HIGH_BANANA = 1, GOLDEN = 2;
    /** Obstacle states. */
    public static final int PENDING = 0, CLEARED = 1, HIT = 2;
    /** Event kinds sent to the {@link Listener}. */
    public static final int EV_JUMP = 0, EV_LAND = 1, EV_DUCK = 2, EV_CLEAR = 3, EV_HIT = 4, EV_BANANA = 5, EV_GOLDEN = 6;

    /** Length of a jump in course seconds. */
    public static final float JUMP_SECONDS = 0.9f;
    /** A duck is shown at least this long. */
    public static final float DUCK_HOLD = 0.6f;
    /** Half the width of the time window around the best jump moment. */
    public static final float WINDOW = 0.45f;
    /** Length of the stumble animation (real seconds). */
    public static final float STUMBLE_SECONDS = 0.8f;
    /** Slowdown after a hit. */
    public static final float SLOW_SECONDS = 1.0f, SLOW_RATE = 0.6f;
    /** Course time of the first obstacle (the 2 s "Stand tall!" calibration is done before). */
    public static final float FIRST_AT = 4.5f;
    /** No obstacle or item arrives after this course time (the round is 70 s). */
    public static final float LAST_AT = 64f;
    /** Points. */
    public static final int CLEAR_POINTS = 2, BANANA_POINTS = 1, GOLDEN_POINTS = 5;
    /** The monkey needs to be at least this high (0..1 of a normal jump) to take a high banana or the coconut. */
    public static final float HIGH_REACH = 0.5f;
    /** The monkey must be lower than this to take a ground banana. */
    public static final float GROUND_REACH = 0.35f;
    /** Golden coconut can be taken this long before and after its time. */
    public static final float GOLDEN_WINDOW = 0.2f;

    private static final float[] SPEED = {640f, 780f, 940f};
    private static final float[] INTERVAL = {4.0f, 3.0f, 2.4f};
    private static final float[] WARN = {2.0f, 1.6f, 1.3f};
    private static final float[] GOLDEN_P = {0.30f, 0.35f, 0.40f};

    /** Receives what happens to the monkey. */
    public interface Listener {
        /**
         * Something happened.
         * @param type one of the EV_ constants
         * @param index obstacle or item number (or -1)
         * @param points points gained (0 if none)
         */
        void onEvent(int type, int index, int points);
    }

    private final int level;
    private final Listener listener;
    private final int obstacles;
    private final int[] oKind;
    private final float[] oTime;
    private final int[] oState;
    private final int items;
    private final int[] iKind;
    private final float[] iTime;
    private final boolean[] iDone, iTaken;

    private float tau, slowLeft, stumbleLeft;
    private boolean jumping, big;
    private float jumpT, apex = 1f;
    private boolean duckInput;
    private float duckHold;
    private int score, offered;
    private int nextObstacle, nextItem;
    private int clears, hits;

    /**
     * Builds a course.
     * @param level 1 (Easy), 2 (Medium) or 3 (Hard)
     * @param seed random seed; the same seed gives the same course
     * @param listener receives events (may be null)
     */
    public JungleLogic(int level, long seed, Listener listener) {
        this.level = Math.max(1, Math.min(3, level));
        this.listener = listener;
        int li = this.level - 1;
        Random rnd = new Random(seed);
        // ---- obstacles ----
        float[] ot = new float[64];
        int[] ok = new int[64];
        int n = 0;
        float t = FIRST_AT;
        int lastPattern = -1, sameRun = 0;
        while (t <= LAST_AT - 1.5f && n < 60) {
            int pattern; // 0 log, 1 vine, 2 log then vine, 3 double log
            if (this.level == 1) {
                pattern = 0;
            } else if (this.level == 2) {
                pattern = rnd.nextBoolean() ? 0 : 1;
                if (pattern == lastPattern && sameRun >= 2) pattern = 1 - pattern;
            } else {
                float r = rnd.nextFloat();
                pattern = n == 0 ? 0 : r < 0.28f ? 0 : r < 0.48f ? 1 : r < 0.80f ? 2 : 3;
                if (pattern == lastPattern && pattern >= 2) pattern = pattern == 2 ? 1 : 0;
            }
            sameRun = pattern == lastPattern ? sameRun + 1 : 1;
            lastPattern = pattern;
            float gapAfter = 0f;
            switch (pattern) {
                case 0:
                    ok[n] = LOG;
                    ot[n++] = t;
                    break;
                case 1:
                    ok[n] = VINE;
                    ot[n++] = t;
                    break;
                case 2:
                    ok[n] = LOG;
                    ot[n++] = t;
                    ok[n] = VINE;
                    ot[n++] = t + 1.2f;
                    gapAfter = 1.2f;
                    break;
                default:
                    ok[n] = DOUBLE_LOG;
                    ot[n++] = t;
                    break;
            }
            t += gapAfter + INTERVAL[li] + (rnd.nextFloat() * 0.8f - 0.4f);
        }
        obstacles = n;
        oKind = new int[n];
        oTime = new float[n];
        oState = new int[n];
        System.arraycopy(ok, 0, oKind, 0, n);
        System.arraycopy(ot, 0, oTime, 0, n);
        // ---- bananas and coconuts ----
        float[] it = new float[400];
        int[] ik = new int[400];
        int m = 0;
        boolean goldenGiven = false;
        for (int i = 0; i < n; i++) {
            int cnt = oKind[i] == DOUBLE_LOG ? 4 : oKind[i] == LOG ? 3 : 0;
            for (int k = 0; k < cnt; k++) {
                ik[m] = HIGH_BANANA;
                it[m++] = oTime[i] + (k - (cnt - 1) / 2f) * 0.22f;
            }
        }
        float prevT = 1.0f;
        for (int i = 0; i <= n; i++) {
            float nextT = i < n ? oTime[i] : LAST_AT;
            float from = i == 0 ? FIRST_AT - 2.4f : prevT;
            float gap = nextT - from;
            if (gap >= 2.0f && m < 392) {
                float mid = (from + nextT) / 2f;
                boolean golden = rnd.nextFloat() < GOLDEN_P[li] || (!goldenGiven && i == 2);
                if (golden) {
                    ik[m] = GOLDEN;
                    it[m++] = mid;
                    goldenGiven = true;
                } else {
                    for (int k = -1; k <= 1; k++) {
                        ik[m] = GROUND_BANANA;
                        it[m++] = mid + k * 0.35f;
                    }
                }
            }
            if (i < n) prevT = oTime[i] + 0.3f;
        }
        // sort items by time (insertion sort, built once)
        for (int a = 1; a < m; a++) {
            float tv = it[a];
            int kv = ik[a];
            int b = a - 1;
            while (b >= 0 && it[b] > tv) {
                it[b + 1] = it[b];
                ik[b + 1] = ik[b];
                b--;
            }
            it[b + 1] = tv;
            ik[b + 1] = kv;
        }
        items = m;
        iKind = new int[m];
        iTime = new float[m];
        iDone = new boolean[m];
        iTaken = new boolean[m];
        System.arraycopy(ik, 0, iKind, 0, m);
        System.arraycopy(it, 0, iTime, 0, m);
    }

    // ---- control ----

    /**
     * The player jumped: the monkey jumps (ignored while it is already in the air).
     */
    public void jump() {
        if (jumping) return;
        jumping = true;
        big = false;
        jumpT = 0f;
        apex = 1f;
        stumbleLeft = 0f;
        send(EV_JUMP, -1, 0);
    }

    /**
     * Tells the logic how high the player's shoulders have got in the current jump, so a big jump shows higher and
     * can clear a double log. Call every frame while the detector reports a jump.
     * @param rise highest rise so far, in shoulder widths
     */
    public void setJumpRise(float rise) {
        if (!jumping) return;
        if (rise >= JumpDetect.BIG_RISE) big = true;
        float k = Math.max(0f, Math.min(1f, (rise - JumpDetect.JUMP_RISE) / (JumpDetect.BIG_RISE - JumpDetect.JUMP_RISE)));
        float a = 1f + 0.3f * k;
        if (a > apex) apex = a;
    }

    /**
     * Sets whether the player is ducking now.
     * @param ducking true while the player's head is low
     */
    public void setDuck(boolean ducking) {
        if (ducking && !duckInput && !jumping) {
            duckHold = DUCK_HOLD;
            send(EV_DUCK, -1, 0);
        }
        duckInput = ducking && !jumping;
    }

    /**
     * Advances the game.
     * @param dt real seconds since the last frame
     */
    public void update(float dt) {
        float rate = slowLeft > 0f ? SLOW_RATE : 1f;
        slowLeft = Math.max(0f, slowLeft - dt);
        stumbleLeft = Math.max(0f, stumbleLeft - dt);
        float cdt = dt * rate;
        tau += cdt;
        if (duckHold > 0f) duckHold = Math.max(0f, duckHold - cdt);
        if (jumping) {
            jumpT += cdt;
            if (jumpT >= JUMP_SECONDS) {
                jumping = false;
                jumpT = 0f;
                send(EV_LAND, -1, 0);
            }
        }
        while (nextObstacle < obstacles && oTime[nextObstacle] <= tau) {
            resolveObstacle(nextObstacle);
            nextObstacle++;
        }
        resolveItems();
    }

    private void resolveObstacle(int i) {
        boolean ok;
        switch (oKind[i]) {
            case VINE:
                ok = ducking();
                break;
            case DOUBLE_LOG:
                ok = jumping && big && Math.abs(jumpT - JUMP_SECONDS / 2f) <= WINDOW - 0.1f;
                break;
            default:
                ok = jumping && Math.abs(jumpT - JUMP_SECONDS / 2f) <= WINDOW;
                break;
        }
        offered += CLEAR_POINTS;
        if (ok) {
            oState[i] = CLEARED;
            score += CLEAR_POINTS;
            clears++;
            send(EV_CLEAR, i, CLEAR_POINTS);
        } else {
            oState[i] = HIT;
            hits++;
            stumbleLeft = STUMBLE_SECONDS;
            slowLeft = SLOW_SECONDS;
            send(EV_HIT, i, 0);
        }
    }

    private void resolveItems() {
        float h = height();
        for (int i = nextItem; i < items; i++) {
            float t = iTime[i];
            if (iDone[i]) {
                if (i == nextItem) nextItem++;
                continue;
            }
            if (iKind[i] == GOLDEN) {
                if (tau < t - GOLDEN_WINDOW) break;
                if (h >= HIGH_REACH && tau <= t + GOLDEN_WINDOW) {
                    take(i, GOLDEN_POINTS, EV_GOLDEN);
                } else if (tau > t + GOLDEN_WINDOW) {
                    offered += GOLDEN_POINTS;
                    iDone[i] = true;
                } else {
                    break; // still waiting for the window to end; later items come later too
                }
            } else {
                if (tau < t) break;
                boolean got = iKind[i] == HIGH_BANANA ? h >= HIGH_REACH : h <= GROUND_REACH;
                if (got) take(i, BANANA_POINTS, EV_BANANA);
                else {
                    offered += BANANA_POINTS;
                    iDone[i] = true;
                }
            }
            if (i == nextItem) nextItem++;
        }
    }

    private void take(int i, int points, int ev) {
        iDone[i] = true;
        iTaken[i] = true;
        offered += points;
        score += points;
        send(ev, i, points);
    }

    private void send(int type, int index, int points) {
        if (listener != null) listener.onEvent(type, index, points);
    }

    // ---- stars ----

    /**
     * Fair stars: 1 always, 2 for 40% or more of the points that came past the monkey, 3 for 70% or more.
     * @param scored points the player got
     * @param offered points of everything that came past (obstacles, bananas, coconuts)
     * @return 1..3
     */
    public static int stars(int scored, int offered) {
        if (offered <= 0) return 1;
        float f = (float) scored / offered;
        if (f >= 0.70f) return 3;
        if (f >= 0.40f) return 2;
        return 1;
    }

    // ---- what the scene reads ----

    /** @return the level, 1..3 */
    public int level() { return level; }
    /** @return the course clock in seconds (runs slower for a second after a hit) */
    public float clock() { return tau; }
    /** @return how fast the jungle scrolls in px per course second */
    public float speed() { return SPEED[level - 1]; }
    /** @return seconds before arrival that the marker shows */
    public float warn() { return WARN[level - 1]; }
    /** @return the score */
    public int score() { return score; }
    /** @return points of everything that has come past the monkey */
    public int offered() { return offered; }
    /** @return obstacles cleared */
    public int clears() { return clears; }
    /** @return obstacles hit */
    public int hits() { return hits; }
    /** @return number of obstacles in the course */
    public int obstacleCount() { return obstacles; }
    /**
     * @param i obstacle number
     * @return LOG, VINE or DOUBLE_LOG
     */
    public int obstacleKind(int i) { return oKind[i]; }
    /**
     * @param i obstacle number
     * @return arrival time on the course clock
     */
    public float obstacleTime(int i) { return oTime[i]; }
    /**
     * @param i obstacle number
     * @return PENDING, CLEARED or HIT
     */
    public int obstacleState(int i) { return oState[i]; }
    /** @return number of bananas and coconuts */
    public int itemCount() { return items; }
    /**
     * @param i item number
     * @return GROUND_BANANA, HIGH_BANANA or GOLDEN
     */
    public int itemKind(int i) { return iKind[i]; }
    /**
     * @param i item number
     * @return arrival time on the course clock
     */
    public float itemTime(int i) { return iTime[i]; }
    /**
     * @param i item number
     * @return true once taken or passed
     */
    public boolean itemDone(int i) { return iDone[i]; }
    /**
     * @param i item number
     * @return true if it was taken (not just passed)
     */
    public boolean itemTaken(int i) { return iTaken[i]; }

    /** @return true while the monkey is in the air */
    public boolean airborne() { return jumping; }
    /** @return progress of the jump 0..1 (0 when on the ground) */
    public float jumpProgress() { return jumping ? jumpT / JUMP_SECONDS : 0f; }
    /** @return true if this jump counts as a big one */
    public boolean bigJump() { return jumping && big; }
    /** @return how high the monkey is: 0 on the ground, 1 at the top of a normal jump, up to 1.3 for a big one */
    public float height() {
        if (!jumping) return 0f;
        return (float) Math.sin(Math.PI * jumpT / JUMP_SECONDS) * apex;
    }
    /** @return true while the duck is shown (the player ducks, or the 0.6 s minimum has not run out) */
    public boolean ducking() { return duckInput || duckHold > 0f; }
    /** @return 0 when not stumbling, up to 1 at the start of a stumble, falling to 0 */
    public float stumble() { return stumbleLeft / STUMBLE_SECONDS; }
    /** @return true during the slowdown after a hit */
    public boolean slow() { return slowLeft > 0f; }
}
