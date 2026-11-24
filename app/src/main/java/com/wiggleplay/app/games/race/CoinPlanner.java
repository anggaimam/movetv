package com.wiggleplay.app.games.race;

import java.util.Random;

/**
 * Lays out the coins, the golden star, the nitro cans and the magnet of one lap on a {@link TrackSpline} (T-28 Part B).
 * Plain Java, build-time only.
 *
 * <p>Coins come in runs of 5, 1.6 m apart, a run every 15 m from s = 25 while the run ends before L - 15 (nothing in
 * the start area). A run on a bend (any sample in its span with |radius| under 30) sits {@code bendFrac x halfWidth} toward the
 * outside of the bend and its coins are corner coins; a run on a straight sits in the centre lane (chance
 * {@code centreWeight}) or in a side lane at {@code laneFrac x halfWidth}, never in the same side lane as the straight
 * run before it. One golden star sits at the apex of a seeded-random bend (apex radius under 25) and replaces the run that
 * contains it. Nitro cans replace the middle coin of a run, the magnet the middle coin of a straight run without a can, at least 25 m from every can (else the farthest one).
 * Lanes, star, cans and magnet each use their own random numbers, so adding cans or a magnet never moves a lane.
 */
public final class CoinPlanner {

    /** Coins in a run and the gap between them. */
    static final int RUN_COINS = 5;
    static final float COIN_GAP = 1.6f;
    static final float RUN_SPAN = (RUN_COINS - 1) * COIN_GAP;
    static final float FIRST_RUN = 25f, RUN_EVERY = 15f, END_MARGIN = 15f;
    /** A run is a bend run when any sample in its span has |radius| below this. */
    static final float BEND_RUN_RADIUS = 30f;
    /** Bends for the star: |radius| under this in a region that turns more than 20 degrees; apex under STAR_APEX. */
    static final float BEND_REGION_RADIUS = 40f, STAR_APEX = 25f;
    static final float CAN_SPACING = 50f, CAN_REACH = 45f, MAGNET_FROM_START = 60f, MAGNET_FROM_CAN = 25f;
    private static final long STAR_SEED = 0x51A7C0DE5EEDL, MAGNET_SEED = 0x2545F4914F6CDD1DL;

    private CoinPlanner() { }

    /**
     * Plans a lap with the level's nitro cans and one magnet (what {@code RaceLogic} asks for).
     * @param t the track
     * @param lv the level
     * @param seed layout seed
     * @return the slots, sorted by s
     */
    public static Slots plan(TrackSpline t, RaceLevel lv, long seed) {
        return plan(t, lv, seed, lv.nitroPerLap, 1);
    }

    /**
     * Plans a lap.
     * @param t the track
     * @param lv the level (lanes, bend offset, how many cans sit on bends)
     * @param seed layout seed; the same seed gives the same plan
     * @param nitroCans nitro cans per lap
     * @param magnets 0 or 1 magnet per lap
     * @return the slots, sorted by s
     */
    public static Slots plan(TrackSpline t, RaceLevel lv, long seed, int nitroCans, int magnets) {
        final float lap = t.lapLength(), hw = t.roadHalfWidth();
        final int n = t.sampleCount();
        final float ds = lap / n;
        int runs = 0;
        while (FIRST_RUN + RUN_EVERY * runs + RUN_SPAN < lap - END_MARGIN) runs++;

        // 1. every run: bend or straight, and its offset
        float[] runS = new float[runs], runOff = new float[runs];
        boolean[] bend = new boolean[runs];
        int[] lane = new int[runs];   // -1, 0, +1 for a straight run; 0 for a bend run
        Random rnd = new Random(seed);
        int prevLane = 0;
        for (int r = 0; r < runs; r++) {
            float s0 = FIRST_RUN + RUN_EVERY * r;
            runS[r] = s0;
            int i0 = (int) Math.floor(s0 / ds), i1 = (int) Math.ceil((s0 + RUN_SPAN) / ds);
            float tight = Float.MAX_VALUE, sign = 1f;
            for (int i = i0; i <= i1; i++) {
                float rad = t.radiusAt(i % n);
                if (Math.abs(rad) < tight) {
                    tight = Math.abs(rad);
                    sign = rad > 0f ? 1f : -1f;
                }
            }
            if (tight < BEND_RUN_RADIUS) {
                bend[r] = true;
                runOff[r] = sign * lv.bendFrac * hw;   // left bend (positive radius): the outside is the right, offset > 0
            } else {
                float centre = rnd.nextFloat();
                int side = rnd.nextBoolean() ? 1 : -1;
                int l;
                if (centre < lv.centreWeight) l = 0;
                else l = prevLane != 0 ? -prevLane : side;
                lane[r] = l;
                prevLane = l;
                runOff[r] = l * lv.laneFrac * hw;
            }
        }

        // 2. the golden star at the apex of a seeded-random bend; it replaces the run that contains it
        float[] apexS = new float[64];
        float[] apexR = new float[64];
        int apexes = bendApexes(t, apexS, apexR);
        float starS = 0f;
        if (apexes > 0) {
            int[] ok = new int[apexes];
            int oks = 0;
            for (int a = 0; a < apexes; a++) if (apexR[a] < STAR_APEX) ok[oks++] = a;
            int pick;
            if (oks > 0) {
                pick = ok[new Random(seed ^ STAR_SEED).nextInt(oks)];
            } else {
                pick = 0;
                for (int a = 1; a < apexes; a++) if (apexR[a] < apexR[pick]) pick = a;
            }
            starS = apexS[pick];
        }
        boolean[] gone = new boolean[runs];
        for (int r = 0; r < runs; r++) gone[r] = starS >= runS[r] && starS <= runS[r] + RUN_SPAN;

        // 3. nitro cans: targets spread over the lap; the first nitroBendCans (spread evenly) go to bend runs
        int cans = Math.max(0, nitroCans);
        int[] canRun = new int[cans];
        java.util.Arrays.fill(canRun, -1);
        int bendCans = Math.min(lv.nitroBendCans, cans);
        boolean[] wantBend = new boolean[cans];
        for (int j = 0; j < bendCans; j++) wantBend[(int) Math.floor((j + 0.5) * cans / bendCans)] = true;
        for (int k = 0; k < cans; k++) {
            float target = (k + 0.5f) * lap / cans;
            int best = -1;
            float bd = Float.MAX_VALUE;
            for (int pass = 0; pass < 2 && best < 0; pass++) {
                for (int r = 0; r < runs; r++) {
                    if (gone[r] || !spacedFromCans(runS, canRun, k, r, lap)) continue;
                    boolean kindOk = wantBend[k] ? bend[r] : (!bend[r] && lane[r] != 0);
                    float d = gap(runS[r] + RUN_SPAN / 2f, target, lap);
                    if (pass == 0 && (!kindOk || d > CAN_REACH)) continue;
                    if (d < bd) {
                        bd = d;
                        best = r;
                    }
                }
                if (best < 0 && pass == 0) bd = Float.MAX_VALUE;
            }
            if (best >= 0 && !(wantBend[k] ? bend[best] : (!bend[best] && lane[best] != 0))) {
                System.out.println("CoinPlanner: can " + k + " wanted a " + (wantBend[k] ? "bend run" : "side-lane run")
                        + " within " + CAN_REACH + " m of s=" + target + "; took the run at s=" + runS[best]);
            }
            if (best >= 0 && !bend[best] && wantBend[k]) {
                // a bend can that fell back to a straight run: that run moves to the centre lane
                lane[best] = 0;
                runOff[best] = 0f;
            }
            canRun[k] = best;
        }

        // 4. the magnet: a straight run without a can, not in the first 60 m, at least 25 m from every can (own Random);
        //    when none is that far, the one farthest from its nearest can. Always exactly one per lap.
        int magRun = -1;
        if (magnets > 0 && runs > 0) {
            int start = new Random(seed ^ MAGNET_SEED).nextInt(runs);
            float bestFar = -1f;
            for (int q = 0; q < runs; q++) {
                int r = (start + q) % runs;
                if (gone[r] || bend[r] || hasCan(canRun, r)) continue;
                float mid = runS[r] + RUN_SPAN / 2f;
                if (mid < MAGNET_FROM_START) continue;
                float nearest = Float.MAX_VALUE;
                for (int c = 0; c < cans; c++) {
                    if (canRun[c] >= 0) nearest = Math.min(nearest, gap(mid, runS[canRun[c]] + RUN_SPAN / 2f, lap));
                }
                if (nearest >= MAGNET_FROM_CAN) {
                    magRun = r;
                    break;
                }
                if (nearest > bestFar) {
                    bestFar = nearest;
                    magRun = r;
                }
            }
        }

        // 5. write the slots, sorted by s
        int kept = 0;
        for (int r = 0; r < runs; r++) if (!gone[r]) kept++;
        int count = kept * RUN_COINS;
        float[] cs = new float[count], co = new float[count];
        boolean[] cor = new boolean[count], nit = new boolean[count], mag = new boolean[count];
        int k = 0;
        for (int r = 0; r < runs; r++) {
            if (gone[r]) continue;
            boolean can = hasCan(canRun, r);
            for (int j = 0; j < RUN_COINS; j++) {
                cs[k] = runS[r] + j * COIN_GAP;
                co[k] = runOff[r];
                cor[k] = bend[r];
                nit[k] = can && j == 2;
                mag[k] = r == magRun && j == 2;
                k++;
            }
        }
        return new Slots(cs, co, cor, nit, mag, count, starS, 0f);
    }

    private static boolean hasCan(int[] canRun, int r) {
        for (int c : canRun) if (c == r) return true;
        return false;
    }

    /** True when run r is at least 50 m from the cans already placed (cans 0..k-1). */
    private static boolean spacedFromCans(float[] runS, int[] canRun, int k, int r, float lap) {
        float mid = runS[r] + RUN_SPAN / 2f;
        for (int c = 0; c < k; c++) {
            if (canRun[c] < 0) continue;
            if (gap(mid, runS[canRun[c]] + RUN_SPAN / 2f, lap) < CAN_SPACING) return false;
        }
        return true;
    }

    /** Distance between two places on the lap, the short way round. */
    static float gap(float a, float b, float lap) {
        float d = Math.abs(a - b) % lap;
        return Math.min(d, lap - d);
    }

    /**
     * Finds the bends: regions with |radius| under 40 m that turn more than 20 degrees in total. For each, the distance
     * along the lap of its apex (the smallest |radius|) and that radius.
     * @param t the track
     * @param outS receives the apex s of each bend, in lap order starting after the first straight sample
     * @param outRadius receives the apex |radius|
     * @return the number of bends found (at most the length of the arrays)
     */
    static int bendApexes(TrackSpline t, float[] outS, float[] outRadius) {
        int m = t.sampleCount();
        float ds = t.lapLength() / m;
        int st = -1;
        for (int i = 0; i < m && st < 0; i++) if (Math.abs(t.radiusAt(i)) >= BEND_REGION_RADIUS) st = i;
        if (st < 0) return 0;
        int found = 0, i0 = -1;
        for (int j = 0; j <= m; j++) {
            int i = (st + j) % m;
            boolean inBend = j < m && Math.abs(t.radiusAt(i)) < BEND_REGION_RADIUS;
            if (inBend && i0 < 0) i0 = j;
            if (!inBend && i0 >= 0) {
                double turn = 0;
                int apex = (st + i0) % m;
                for (int q = i0; q < j; q++) {
                    int a = (st + q) % m, b = (a + 1) % m;
                    double d = t.sampleYaw(b) - t.sampleYaw(a);
                    while (d > Math.PI) d -= 2 * Math.PI;
                    while (d <= -Math.PI) d += 2 * Math.PI;
                    turn += d;
                    if (Math.abs(t.radiusAt(a)) < Math.abs(t.radiusAt(apex))) apex = a;
                }
                if (Math.abs(turn) > Math.toRadians(20) && found < outS.length) {
                    outS[found] = apex * ds;
                    outRadius[found] = Math.abs(t.radiusAt(apex));
                    found++;
                }
                i0 = -1;
            }
        }
        return found;
    }
}
