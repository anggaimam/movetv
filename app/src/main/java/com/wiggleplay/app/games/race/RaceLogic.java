package com.wiggleplay.app.games.race;

import com.wiggleplay.app.games.common.StarRules;

/**
 * The rules of a Turbo Trucks race for one or two players (plain Java, no Android). Owns one {@link CarModel} per
 * player; the scene passes the steering and brake in with {@link #setControls}, the jump with {@link #onJump}, and
 * calls {@link #update} once per frame. Nothing is allocated after construction.
 *
 * <ul>
 *   <li><b>Laps</b>: 3. A lap counts when the distance along the lap wraps past the finish line and the player has
 *       covered at least 0.9 laps since the last count. A race ends when everybody has finished, or 10 s after the
 *       first finisher in a two-player race, or after 150 s. There is never a game over.</li>
 *   <li><b>Coins</b>: each player has their own coins (from {@link TrackLayout#coinSlots}) and one golden star per lap
 *       at the same place. A coin is picked up within 1.4 units: +1 (the star +5). Coins come back every lap.
 *       "Offered" is the sum of every coin and star that was collected or that the player drove past.</li>
 *   <li><b>Boost</b>: a meter 0..1 that starts full, fills 0.2 per second and 0.15 per coin. A jump starts a boost only
 *       when the meter is full and no boost runs; the boost lasts exactly 1.6 s whatever happens next, the meter
 *       goes to 0, jumps during a boost do nothing and braking is ignored during it.</li>
 *   <li><b>Two players</b>: trucks are circles of radius 1.0; overlapping trucks are pushed apart equally, and lose
 *       5% speed once per bump (no spin).</li>
 * </ul>
 */
public final class RaceLogic {

    /** Laps in a race. */
    public static final int LAPS = 3;
    /** Seconds a boost lasts. */
    public static final float BOOST_SECONDS = 1.6f;
    /** Longest race in seconds. */
    public static final float SAFETY_SECONDS = 150f;
    /** Seconds the second player has after the first one finishes (two players). */
    public static final float GRACE_SECONDS = 10f;
    /** Coin pickup distance. */
    public static final float PICKUP_RADIUS = 1.4f;
    /** Value of a coin and of the golden star. */
    public static final int COIN_VALUE = 1, STAR_VALUE = 5;

    private static final float TRUCK_RADIUS = 1.0f;
    private static final float PASSED_BEHIND = 2.5f;
    private static final float LOOK_AHEAD = 3f;

    private final TrackLayout track;
    private final int players;
    private final float lap;
    private final CarModel[] car = new CarModel[2];

    private final float[] steer = new float[2];
    private final boolean[] braking = new boolean[2];
    private final float[] meter = {1f, 1f};
    private final float[] boostLeft = new float[2];
    private final boolean[] boosting = new boolean[2];
    private final int[] score = new int[2], offered = new int[2], laps = new int[2], finishPlace = new int[2];
    private final float[] lastS = new float[2], progress = new float[2], sinceCount = new float[2], finishTime = new float[2];
    private final boolean[] finished = new boolean[2];
    private final int[] coinEvents = new int[2];
    private final boolean[] starEvent = new boolean[2], lapEvent = new boolean[2];

    // coin slots per player (sorted by s), with their world positions
    private final float[][] slotS = new float[2][], slotX = new float[2][], slotZ = new float[2][];
    private final int[][] slotValue = new int[2][];
    private final boolean[][] resolved = new boolean[2][];
    private final boolean[][] slotCorner = new boolean[2][];
    private final int[] scan = new int[2];

    private float time;
    private int finishedCount;
    private float firstFinishTime = -1f;
    private boolean touching;

    /**
     * Sets up a race with the trucks on the start line.
     * @param track the track
     * @param level speed and assist
     * @param players 1 or 2 (2: lateral start offsets -2 and +2, more assist, trucks bump)
     * @param seed coin layout seed (a new one per round); each player gets a different layout from it
     */
    public RaceLogic(TrackLayout track, RaceLevel level, int players, long seed) {
        if (players < 1 || players > 2) throw new IllegalArgumentException("players must be 1 or 2: " + players);
        this.track = track;
        this.players = players;
        this.lap = track.lapLength();
        for (int p = 0; p < players; p++) {
            car[p] = new CarModel(level, players == 2);
            car[p].reset(track, 0f, players == 2 ? (p == 0 ? -2f : 2f) : 0f);
            lastS[p] = car[p].s();
            buildSlots(p, seed + p * 7919L);
        }
    }

    private void buildSlots(int p, long seed) {
        TrackLayout.Slots sl = track.coinSlots(seed);
        int n = sl.count + 1;
        float[] s = new float[n], off = new float[n];
        int[] val = new int[n];
        boolean[] cor = new boolean[n];
        int k = 0;
        boolean starIn = false;
        for (int i = 0; i <= sl.count; i++) {
            if (!starIn && (i == sl.count || sl.starS < sl.s[i])) {
                s[k] = sl.starS;
                off[k] = sl.starOffset;
                val[k] = STAR_VALUE;
                k++;
                starIn = true;
            }
            if (i < sl.count) {
                s[k] = sl.s[i];
                off[k] = sl.offset[i];
                cor[k] = sl.corner[i];
                val[k] = COIN_VALUE;
                k++;
            }
        }
        slotS[p] = s;
        slotValue[p] = val;
        slotCorner[p] = cor;
        slotX[p] = new float[n];
        slotZ[p] = new float[n];
        resolved[p] = new boolean[n];
        float[] smp = new float[3];
        for (int i = 0; i < n; i++) {
            track.sample(s[i], smp);
            slotX[p][i] = smp[0] - (float) Math.cos(smp[2]) * off[i];
            slotZ[p][i] = smp[1] + (float) Math.sin(smp[2]) * off[i];
        }
    }

    /**
     * Sets what a player is doing this frame.
     * @param p player 0 or 1
     * @param steerValue -1 (left on screen) to +1
     * @param brake true while braking
     */
    public void setControls(int p, float steerValue, boolean brake) {
        steer[p] = steerValue;
        braking[p] = brake;
    }

    /**
     * A jump happened: starts a boost if the meter is full and no boost is running.
     * @param p player 0 or 1
     */
    public void onJump(int p) {
        if (finished[p] || boosting[p] || meter[p] < 0.9999f) return;
        boosting[p] = true;
        boostLeft[p] = BOOST_SECONDS;
        meter[p] = 0f;
    }

    /**
     * Advances the race.
     * @param dt seconds (at most 0.05)
     */
    public void update(float dt) {
        if (dt <= 0f) return;
        boolean wasOver = over();
        if (!wasOver) time += dt;
        for (int p = 0; p < players; p++) {
            if (boosting[p]) {
                boostLeft[p] -= dt;
                if (boostLeft[p] <= 1e-4f) {
                    boosting[p] = false;
                    boostLeft[p] = 0f;
                }
            }
            if (!finished[p]) meter[p] = Math.min(1f, meter[p] + 0.2f * dt);
            car[p].update(dt, steer[p], boosting[p], braking[p] && !boosting[p], track);
        }
        if (players == 2) bump();
        if (wasOver) return;
        for (int p = 0; p < players; p++) {
            if (finished[p]) continue;
            lapsAndCoins(p);
        }
    }

    private void lapsAndCoins(int p) {
        float s = car[p].s();
        float raw = s - lastS[p];
        float ds = raw;
        boolean forwardWrap = raw < -lap * 0.5f;
        if (forwardWrap) ds += lap;
        else if (raw > lap * 0.5f) ds -= lap;
        lastS[p] = s;
        progress[p] += ds;
        sinceCount[p] += ds;

        if (forwardWrap && sinceCount[p] >= 0.9f * lap) {
            for (int i = 0; i < slotS[p].length; i++) { // whatever was not collected counts as offered
                if (!resolved[p][i]) offered[p] += slotValue[p][i];
                resolved[p][i] = false;
            }
            scan[p] = 0;
            sinceCount[p] = s;
            laps[p]++;
            lapEvent[p] = true;
            if (laps[p] >= LAPS) {
                finished[p] = true;
                finishTime[p] = time;
                finishPlace[p] = ++finishedCount;
                if (firstFinishTime < 0f) firstFinishTime = time;
            }
            return;
        }
        float[] ss = slotS[p];
        boolean[] done = resolved[p];
        int n = ss.length;
        float cx = car[p].x(), cz = car[p].z();
        float r2 = PICKUP_RADIUS * PICKUP_RADIUS;
        for (int i = scan[p]; i < n && ss[i] <= s + LOOK_AHEAD; i++) {
            if (done[i]) continue;
            float dx = slotX[p][i] - cx, dz = slotZ[p][i] - cz;
            if (dx * dx + dz * dz <= r2) {
                done[i] = true;
                int v = slotValue[p][i];
                score[p] += v;
                offered[p] += v;
                if (v == STAR_VALUE) starEvent[p] = true;
                else coinEvents[p]++;
                meter[p] = Math.min(1f, meter[p] + 0.15f * (v == STAR_VALUE ? 1 : v));
            }
        }
        while (scan[p] < n && ss[scan[p]] + PASSED_BEHIND < s) {
            if (!done[scan[p]]) {
                done[scan[p]] = true;
                offered[p] += slotValue[p][scan[p]];
            }
            scan[p]++;
        }
    }

    private void bump() {
        CarModel a = car[0], b = car[1];
        float dx = b.x() - a.x(), dz = b.z() - a.z();
        float d2 = dx * dx + dz * dz;
        float min = 2f * TRUCK_RADIUS;
        if (d2 >= min * min) {
            touching = false;
            return;
        }
        float d = (float) Math.sqrt(d2);
        float nx, nz;
        if (d < 1e-4f) {
            nx = 1f;
            nz = 0f;
        } else {
            nx = dx / d;
            nz = dz / d;
        }
        float push = (min - d) * 0.5f;
        a.nudge(-nx * push, -nz * push);
        b.nudge(nx * push, nz * push);
        if (!touching) {
            touching = true;
            a.scaleSpeed(0.95f);
            b.scaleSpeed(0.95f);
        }
    }

    // ---- state ----

    /** @return number of players */
    public int players() { return players; }

    /**
     * A player's truck.
     * @param p player 0 or 1
     * @return the car model
     */
    public CarModel car(int p) { return car[p]; }

    /** @return seconds since the start (stops when the race is over) */
    public float time() { return time; }

    /**
     * Whether the race has ended.
     * @return true when everybody finished, 10 s after the first finisher in a two-player race, or after 150 s
     */
    public boolean over() {
        if (time >= SAFETY_SECONDS) return true;
        if (finishedCount >= players) return true;
        return players == 2 && firstFinishTime >= 0f && time - firstFinishTime >= GRACE_SECONDS;
    }

    /**
     * Laps completed.
     * @param p player 0 or 1
     * @return 0 to 3
     */
    public int lapsDone(int p) { return laps[p]; }

    /**
     * The lap being driven, for the HUD ("Lap 2/3").
     * @param p player 0 or 1
     * @return 1 to 3
     */
    public int currentLap(int p) { return Math.min(LAPS, laps[p] + 1); }

    /**
     * Whether a player has done all laps.
     * @param p player 0 or 1
     * @return true when finished
     */
    public boolean finished(int p) { return finished[p]; }

    /**
     * Seconds from the start to the finish.
     * @param p player 0 or 1
     * @return the time, or 0 if not finished
     */
    public float finishTime(int p) { return finished[p] ? finishTime[p] : 0f; }

    /**
     * Total distance driven along the lap (counts up across laps).
     * @param p player 0 or 1
     * @return distance in world units
     */
    public float progress(int p) { return progress[p]; }

    /**
     * Race position: finishers first in finishing order, then the others by distance driven.
     * @param p player 0 or 1
     * @return 1 or 2 (always 1 in a one-player race)
     */
    public int place(int p) {
        int ahead = 0;
        for (int q = 0; q < players; q++) {
            if (q == p) continue;
            if (finished[q] && !finished[p]) ahead++;
            else if (finished[q] && finished[p] && finishPlace[q] < finishPlace[p]) ahead++;
            else if (!finished[q] && !finished[p] && progress[q] > progress[p]) ahead++;
        }
        return 1 + ahead;
    }

    /**
     * Points scored.
     * @param p player 0 or 1
     * @return coins plus 5 per star, never negative
     */
    public int score(int p) { return score[p]; }

    /**
     * Points offered to this player so far (collected, or driven past).
     * @param p player 0 or 1
     * @return the points
     */
    public int offered(int p) { return offered[p]; }

    /**
     * Stars for the round.
     * @param p player 0 or 1
     * @return 1 to 3, never less than 1
     */
    public int stars(int p) { return StarRules.stars(score[p], offered[p]); }

    /**
     * The boost meter.
     * @param p player 0 or 1
     * @return 0 to 1
     */
    public float boostMeter(int p) { return meter[p]; }

    /**
     * Whether a boost runs.
     * @param p player 0 or 1
     * @return true during the 1.6 s
     */
    public boolean boosting(int p) { return boosting[p]; }

    /**
     * Seconds of boost left.
     * @param p player 0 or 1
     * @return 0 to 1.6
     */
    public float boostLeft(int p) { return boostLeft[p]; }

    /**
     * Coins picked up since the last call (for the sound and the score burst).
     * @param p player 0 or 1
     * @return the number of coins
     */
    public int consumeCoins(int p) {
        int c = coinEvents[p];
        coinEvents[p] = 0;
        return c;
    }

    /**
     * Whether the golden star was picked up since the last call.
     * @param p player 0 or 1
     * @return true once per star
     */
    public boolean consumeStar(int p) {
        boolean e = starEvent[p];
        starEvent[p] = false;
        return e;
    }

    /**
     * Whether a lap was completed since the last call.
     * @param p player 0 or 1
     * @return true once per lap
     */
    public boolean consumeLap(int p) {
        boolean e = lapEvent[p];
        lapEvent[p] = false;
        return e;
    }

    /**
     * Number of coin and star slots one player has per lap.
     * @param p player 0 or 1
     * @return the count (the star is one of them)
     */
    public int slotCount(int p) { return slotS[p].length; }

    /**
     * Where a slot is (for drawing).
     * @param p player 0 or 1
     * @param i slot index, sorted by distance along the lap
     * @return its world x
     */
    public float slotX(int p, int i) { return slotX[p][i]; }

    /**
     * Where a slot is (for drawing).
     * @param p player 0 or 1
     * @param i slot index
     * @return its world z
     */
    public float slotZ(int p, int i) { return slotZ[p][i]; }

    /**
     * Whether a slot is a golden star.
     * @param p player 0 or 1
     * @param i slot index
     * @return true for the star
     */
    public boolean slotIsStar(int p, int i) { return slotValue[p][i] == STAR_VALUE; }

    /**
     * Whether a slot is a coin on a corner (tests use this).
     * @param p player 0 or 1
     * @param i slot index
     * @return true for a corner coin
     */
    public boolean slotIsCorner(int p, int i) { return slotCorner[p][i]; }

    /**
     * Whether a slot is already collected or passed this lap (so it is not drawn).
     * @param p player 0 or 1
     * @param i slot index
     * @return true when gone
     */
    public boolean slotGone(int p, int i) { return resolved[p][i]; }
}
