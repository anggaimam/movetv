package com.wiggleplay.app.games.dance;

import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

import java.util.Random;

/**
 * The poses of Animal Dance Party and the matcher that scores a body against one. Plain Java, no Android.
 *
 * A pose is made of up to four arm segments (left upper arm, left forearm, right upper arm, right forearm), each with
 * a target direction in degrees measured against the shoulder line (0 = down, 90 = out to the side, 180 = up), an
 * optional torso tilt, and an optional "wrist near a point" rule. Every segment scores {@code 1 - |error| / 45} (0..1),
 * the pose quality is the weighted average. Missing keypoints are skipped while at least two scored parts remain.
 * Nothing is allocated while matching.
 */
public final class Poses {

    /** Pose numbers. */
    public static final int STAR = 0, AIRPLANE = 1, ROCKET = 2, ROBOT = 3, CRAB = 4, FLAMINGO = 5, PENGUIN = 6,
            MONKEY = 7, LEAN_LEFT = 8, LEAN_RIGHT = 9, GIRAFFE = 10, FROG = 11, SCARECROW = 12, REACH = 13,
            SURFER = 14, BUNNY = 15, SUPERHERO = 16, KOALA = 17, CHICKEN = 18, PALM = 19, EAGLE = 20, BIRD = 21,
            COUNT = 22;

    /** Names shown on the cards. */
    public static final String[] NAMES = {"Star", "Airplane", "Rocket", "Robot", "Crab", "Flamingo", "Penguin",
            "Monkey", "Lean left", "Lean right", "Giraffe", "Frog", "Scarecrow", "Reach up", "Surfer", "Bunny", "Superhero",
            "Koala", "Chicken", "Palm tree", "Eagle", "Bird"};
    /** What the host says. */
    public static final String[] SAY = {"Be a star!", "Be an airplane!", "Be a rocket!", "Be a robot!", "Be a crab!",
            "Be a flamingo!", "Be a penguin!", "Be a monkey!", "Lean to the left!", "Lean to the right!", "Be a giraffe!",
            "Be a frog!", "Be a scarecrow!", "Reach up high!", "Be a surfer!", "Be a bunny!", "Be a superhero!",
            "Hug like a koala!", "Be a chicken!", "Be a palm tree!", "Be an eagle!", "Be a bird!"};

    /** An arm angle error of this many degrees scores zero. */
    public static final float TOLERANCE_DEG = 45f;
    /** A torso tilt error of this many degrees scores zero. */
    public static final float TILT_TOLERANCE_DEG = 30f;
    /** A wrist this many shoulder widths from its point scores zero. */
    public static final float NEAR_RANGE = 1.5f;
    /** Quality at or above which the result is Perfect. */
    public static final float PERFECT = 0.75f;
    /** Quality at or above which the result is Great. */
    public static final float GREAT = 0.55f;
    /** Quality at or above which the result is Good. */
    public static final float GOOD = 0.35f;
    /** Keypoints below this confidence count as missing. */
    public static final float MIN_CONFIDENCE = 0.3f;

    private static final float W_NEAR = 3f, W_TILT = 4f;
    private static final float NAN = Float.NaN;
    private static final int NOSE_POINT = 0, HIP_POINT = 1;

    private static final class Def {
        final float[] arm;       // LU, LF, RU, RF in degrees, NaN = free
        final float tilt;        // degrees (negative = towards the screen's left), NaN = none
        final int nearSide;      // 0 left wrist, 1 right wrist, -1 none
        final int nearPoint;
        final boolean mirrorOk;  // the same pose with the other arm counts too
        final boolean symmetric; // both arms do the same (used on the Easy level)

        Def(float[] arm, float tilt, int nearSide, int nearPoint, boolean mirrorOk, boolean symmetric) {
            this.arm = arm;
            this.tilt = tilt;
            this.nearSide = nearSide;
            this.nearPoint = nearPoint;
            this.mirrorOk = mirrorOk;
            this.symmetric = symmetric;
        }
    }

    private static final Def[] DEFS = new Def[COUNT];

    static {
        DEFS[STAR] = new Def(new float[]{135, 135, 135, 135}, NAN, -1, 0, false, true);
        DEFS[AIRPLANE] = new Def(new float[]{90, 90, 90, 90}, NAN, -1, 0, false, true);
        DEFS[ROCKET] = new Def(new float[]{190, 190, 190, 190}, NAN, -1, 0, false, true);
        DEFS[ROBOT] = new Def(new float[]{90, 180, 90, 180}, NAN, -1, 0, false, true);
        DEFS[CRAB] = new Def(new float[]{90, 205, 90, 205}, NAN, -1, 0, false, true);
        // Flamingo: right hand on the right hip, left arm up and out (or the other way round)
        DEFS[FLAMINGO] = new Def(new float[]{135, 135, NAN, NAN}, NAN, 1, HIP_POINT, true, false);
        DEFS[PENGUIN] = new Def(new float[]{20, 30, 20, 30}, NAN, -1, 0, false, true);
        // Monkey: one hand at the nose, the other arm hangs and swings
        DEFS[MONKEY] = new Def(new float[]{30, 20, NAN, NAN}, NAN, 1, NOSE_POINT, true, false);
        DEFS[LEAN_LEFT] = new Def(new float[]{NAN, NAN, NAN, NAN}, -20f, -1, 0, false, false);
        DEFS[LEAN_RIGHT] = new Def(new float[]{NAN, NAN, NAN, NAN}, 20f, -1, 0, false, false);
        DEFS[GIRAFFE] = new Def(new float[]{160, 160, 160, 160}, NAN, -1, 0, false, true);
        DEFS[FROG] = new Def(new float[]{40, 175, 40, 175}, NAN, -1, 0, false, true);
        DEFS[SCARECROW] = new Def(new float[]{90, 0, 90, 0}, NAN, -1, 0, false, true);
        DEFS[REACH] = new Def(new float[]{10, 5, 180, 180}, NAN, -1, 0, true, false);
        DEFS[SURFER] = new Def(new float[]{45, 45, 135, 135}, NAN, -1, 0, true, false);
        DEFS[BUNNY] = new Def(new float[]{120, 240, 120, 240}, NAN, -1, 0, false, true);
        DEFS[SUPERHERO] = new Def(new float[]{65, 330, 65, 330}, NAN, -1, 0, false, true);
        DEFS[KOALA] = new Def(new float[]{30, 285, 30, 285}, NAN, -1, 0, false, true);
        DEFS[CHICKEN] = new Def(new float[]{90, 270, 90, 270}, NAN, -1, 0, false, true);
        DEFS[PALM] = new Def(new float[]{NAN, NAN, 180, 180}, -20f, -1, 0, true, false);
        DEFS[EAGLE] = new Def(new float[]{60, 60, 60, 60}, NAN, -1, 0, false, true);
        DEFS[BIRD] = new Def(new float[]{10, 5, 90, 90}, NAN, -1, 0, true, false);
    }

    private Poses() { }

    /** What a match found: the quality and how well each part fitted (for the colours of the outline). */
    public static final class Result {
        /** 0..1 */
        public float quality;
        /** Per segment (LU, LF, RU, RF): 0..1, or -1 when the pose does not ask for it or it cannot be seen. */
        public final float[] seg = new float[4];
        /** Torso tilt fit 0..1, or -1. */
        public float torso = -1f;
        /** How many parts were scored. */
        public int parts;
    }

    /**
     * Whether a pose uses both arms the same way.
     * @param pose pose number
     * @return true for symmetric arm poses
     */
    public static boolean symmetric(int pose) {
        return DEFS[pose].symmetric;
    }

    /**
     * Scores a body against a pose.
     * @param pose pose number
     * @param p the body (stage pixels)
     * @param out receives the details (may be null)
     * @return quality 0..1 (0 when too little of the body is visible)
     */
    public static float match(int pose, PlayerPose p, Result out) {
        return match(pose, p, out, false);
    }

    /**
     * Scores a body against a pose, optionally only against the opposite side ("do the opposite" rounds).
     * @param pose pose number
     * @param p the body (stage pixels)
     * @param out receives the details (may be null)
     * @param opposite true to score only the mirror image of the pose
     * @return quality 0..1
     */
    public static float match(int pose, PlayerPose p, Result out, boolean opposite) {
        Def d = DEFS[pose];
        Result a = out != null ? out : TMP_A;
        if (opposite) {
            // "Do the opposite": the mirror image earns full marks; copying the card as shown still earns about half
            float qm = score(d, true, p, a);
            float qc = score(d, false, p, TMP_B) * COPY_FACTOR;
            if (qc > qm) {
                copy(TMP_B, a);
                a.quality = qc;
                return qc;
            }
            return qm;
        }
        float q = score(d, false, p, a);
        if (d.mirrorOk) {
            float q2 = score(d, true, p, TMP_B);
            if (q2 > q) {
                q = q2;
                copy(TMP_B, a);
            }
        }
        return q;
    }

    /** In an opposite round, doing the pose the shown way instead of mirrored scores this share of its quality. */
    public static final float COPY_FACTOR = 0.6f;

    private static final Result TMP_A = new Result(), TMP_B = new Result();

    private static void copy(Result from, Result to) {
        to.quality = from.quality;
        to.torso = from.torso;
        to.parts = from.parts;
        System.arraycopy(from.seg, 0, to.seg, 0, 4);
    }

    private static boolean ok(Keypoint k) {
        return k.s >= MIN_CONFIDENCE;
    }

    private static float score(Def d, boolean mirror, PlayerPose p, Result out) {
        out.quality = 0f;
        out.torso = -1f;
        out.parts = 0;
        for (int i = 0; i < 4; i++) out.seg[i] = -1f;
        if (p == null || !p.present) return 0f;
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        boolean haveLine = ok(ls) && ok(rs);
        float ux = 1f, uy = 0f, sw = 0f;
        if (haveLine) {
            float dx = rs.x - ls.x, dy = rs.y - ls.y;
            sw = (float) Math.sqrt(dx * dx + dy * dy);
            if (sw < 1f) haveLine = false;
            else {
                ux = dx / sw;
                uy = dy / sw;
            }
        }
        float vx = -uy, vy = ux; // "down" of the body

        float sum = 0f, weight = 0f;
        int defined = 0, used = 0;
        boolean anyArm = false;
        for (int i = 0; i < 4; i++) if (!Float.isNaN(d.arm[i])) anyArm = true;
        float tiltWeight = anyArm ? 2f : W_TILT;

        // arms: index 0..3 in the pose, mirrored poses swap left and right
        for (int i = 0; i < 4; i++) {
            int di = mirror ? (i + 2) % 4 : i;
            float target = d.arm[di];
            if (Float.isNaN(target)) continue;
            defined++;
            if (!haveLine) continue;
            boolean left = i < 2, upper = (i & 1) == 0;
            Keypoint a = p.kps[left ? (upper ? Kp.L_SHOULDER : Kp.L_ELBOW) : (upper ? Kp.R_SHOULDER : Kp.R_ELBOW)];
            Keypoint b = p.kps[left ? (upper ? Kp.L_ELBOW : Kp.L_WRIST) : (upper ? Kp.R_ELBOW : Kp.R_WRIST)];
            if (!ok(a) || !ok(b)) continue;
            float ex = b.x - a.x, ey = b.y - a.y;
            float outward = (ex * ux + ey * uy) * (left ? -1f : 1f);
            float down = ex * vx + ey * vy;
            float angle = (float) Math.toDegrees(Math.atan2(outward, down));
            float s = clamp01(1f - Math.abs(wrap(target - angle)) / TOLERANCE_DEG);
            out.seg[i] = s;
            sum += s;
            weight += 1f;
            used++;
        }

        // wrist near a point
        if (d.nearSide >= 0) {
            defined++;
            int side = mirror ? 1 - d.nearSide : d.nearSide; // 0 left, 1 right
            Keypoint w = p.kps[side == 0 ? Kp.L_WRIST : Kp.R_WRIST];
            Keypoint target = d.nearPoint == NOSE_POINT ? p.kps[Kp.NOSE] : p.kps[side == 0 ? Kp.L_HIP : Kp.R_HIP];
            if (haveLine && ok(w) && ok(target)) {
                float dx = w.x - target.x, dy = w.y - target.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy) / sw;
                float s = clamp01(1f - dist / NEAR_RANGE);
                sum += s * W_NEAR;
                weight += W_NEAR;
                used++;
                int base = side == 0 ? 0 : 2;
                out.seg[base] = s;
                out.seg[base + 1] = s;
            }
        }

        // torso tilt
        if (!Float.isNaN(d.tilt)) {
            defined++;
            if (haveLine) {
                float tilt;
                Keypoint lh = p.kps[Kp.L_HIP], rh = p.kps[Kp.R_HIP];
                if (ok(lh) && ok(rh)) {
                    float smx = (ls.x + rs.x) / 2f, smy = (ls.y + rs.y) / 2f;
                    float hmx = (lh.x + rh.x) / 2f, hmy = (lh.y + rh.y) / 2f;
                    tilt = (float) Math.toDegrees(Math.atan2(smx - hmx, hmy - smy));
                } else {
                    tilt = (float) Math.toDegrees(Math.atan2(uy, ux));
                }
                float target = mirror ? -d.tilt : d.tilt;
                float s = clamp01(1f - Math.abs(target - tilt) / TILT_TOLERANCE_DEG);
                out.torso = s;
                sum += s * tiltWeight;
                weight += tiltWeight;
                used++;
            }
        }

        out.parts = used;
        if (used < Math.min(2, defined) || used == 0) return 0f;
        out.quality = sum / weight;
        return out.quality;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    private static float wrap(float deg) {
        float r = (deg + 540f) % 360f;
        if (r < 0f) r += 360f;
        return r - 180f;
    }

    // ---- results ----

    /**
     * Points for a quality: 3 / 2 / 1, and never less than 1.
     * @param quality 0..1
     * @return points
     */
    public static int points(float quality) {
        return quality >= PERFECT ? 3 : quality >= GREAT ? 2 : 1;
    }

    /**
     * The word for a quality.
     * @param quality 0..1
     * @return "Perfect!", "Great!", "Good!" or "Keep trying!"
     */
    public static String label(float quality) {
        return quality >= PERFECT ? "Perfect!" : quality >= GREAT ? "Great!" : quality >= GOOD ? "Good!" : "Keep trying!";
    }

    /**
     * The best quality held for a whole time span: the highest average of the samples over any stretch of at least
     * {@code span} seconds (so a late camera picture does not matter). If the samples cover less than that, the average
     * of all of them.
     * @param t sample times in seconds, rising
     * @param q sample qualities 0..1
     * @param from first sample (inclusive)
     * @param to last sample (exclusive)
     * @param span seconds that must be held
     * @return 0..1 (0 when there are no samples)
     */
    public static float bestWindow(float[] t, float[] q, int from, int to, float span) {
        if (to <= from) return 0f;
        float best = -1f;
        int hi = from;
        float run = 0f; // sum of q[lo..hi)
        for (int lo = from; lo < to; lo++) {
            if (hi < lo) {
                hi = lo;
                run = 0f;
            }
            while (hi < to && (hi == lo || t[hi - 1] - t[lo] < span)) {
                run += q[hi];
                hi++;
            }
            if (t[hi - 1] - t[lo] >= span * 0.98f) {
                float avg = run / (hi - lo);
                if (avg > best) best = avg;
            }
            run -= q[lo];
        }
        if (best < 0f) {
            float s = 0f;
            for (int i = from; i < to; i++) s += q[i];
            best = s / (to - from);
        }
        return best;
    }

    // ---- measuring a body ----

    /**
     * Measures the four arm directions of a body, in the same convention as the poses (0 = down, 90 = out, 180 = up).
     * @param p the body
     * @param out four angles LU, LF, RU, RF; NaN where a keypoint is missing
     * @return true if the shoulders are visible (otherwise all four are NaN)
     */
    public static boolean measure(PlayerPose p, float[] out) {
        for (int i = 0; i < 4; i++) out[i] = NAN;
        if (p == null || !p.present) return false;
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        if (!ok(ls) || !ok(rs)) return false;
        float dx = rs.x - ls.x, dy = rs.y - ls.y;
        float sw = (float) Math.sqrt(dx * dx + dy * dy);
        if (sw < 1f) return false;
        float ux = dx / sw, uy = dy / sw, vx = -uy, vy = ux;
        for (int i = 0; i < 4; i++) {
            boolean left = i < 2, upper = (i & 1) == 0;
            Keypoint a = p.kps[left ? (upper ? Kp.L_SHOULDER : Kp.L_ELBOW) : (upper ? Kp.R_SHOULDER : Kp.R_ELBOW)];
            Keypoint b = p.kps[left ? (upper ? Kp.L_ELBOW : Kp.L_WRIST) : (upper ? Kp.R_ELBOW : Kp.R_WRIST)];
            if (!ok(a) || !ok(b)) continue;
            float ex = b.x - a.x, ey = b.y - a.y;
            float outward = (ex * ux + ey * uy) * (left ? -1f : 1f);
            float down = ex * vx + ey * vy;
            out[i] = (float) Math.toDegrees(Math.atan2(outward, down));
        }
        return true;
    }

    /**
     * How far a body leans.
     * @param p the body
     * @return degrees (negative = towards the left of the screen), 0 if the shoulders are not visible
     */
    public static float tiltOf(PlayerPose p) {
        if (p == null || !p.present) return 0f;
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        if (!ok(ls) || !ok(rs)) return 0f;
        Keypoint lh = p.kps[Kp.L_HIP], rh = p.kps[Kp.R_HIP];
        if (ok(lh) && ok(rh)) {
            float smx = (ls.x + rs.x) / 2f, smy = (ls.y + rs.y) / 2f;
            float hmx = (lh.x + rh.x) / 2f, hmy = (lh.y + rh.y) / 2f;
            return (float) Math.toDegrees(Math.atan2(smx - hmx, hmy - smy));
        }
        return (float) Math.toDegrees(Math.atan2(rs.y - ls.y, rs.x - ls.x));
    }

    // ---- levels ----

    /**
     * Tempo of a level.
     * @param level 1..3
     * @return beats per minute
     */
    public static int bpm(int level) {
        return level == 1 ? 100 : level == 2 ? 112 : 124;
    }

    /**
     * Length of the hold window of an ordinary pose in beats.
     * @param level 1..3
     * @return 6, 5 or 4
     */
    public static int windowBeats(int level) {
        return level == 1 ? 6 : level == 2 ? 5 : 4;
    }

    // ---- round types and plans ----

    /** Ordinary pose, opposite side, freeze (the music stops), quick (short hold), move (several poses in a row). */
    public static final int T_NORMAL = 0, T_MIRROR = 1, T_FREEZE = 2, T_SPEED = 3, T_MOVE = 4;
    /** Beats to get ready (quick poses get one), and beats to show the result. */
    public static final int READY_BEATS = 2, QUICK_READY_BEATS = 1, REST_BEATS = 1;

    private static final int[] SYMMETRIC_POOL;
    private static final int[] ASYMMETRIC_POOL;
    private static final int[] ALL_POOL;

    /** Named moves: a few poses in a row, two beats each. */
    public static final String[] MOVE_NAMES = {"Jumping jacks", "Wing flaps", "Pump it up", "Rocket launch", "Sway",
            "Sunrise"};
    private static final int[][] MOVES = {
            {PENGUIN, STAR}, {AIRPLANE, ROBOT}, {ROBOT, ROCKET}, {PENGUIN, GIRAFFE, ROCKET}, {LEAN_LEFT, LEAN_RIGHT},
            {PENGUIN, EAGLE, AIRPLANE}};

    static {
        int sym = 0, asym = 0;
        for (int i = 0; i < COUNT; i++) if (DEFS[i].symmetric) sym++; else asym++;
        SYMMETRIC_POOL = new int[sym];
        ASYMMETRIC_POOL = new int[asym];
        ALL_POOL = new int[COUNT];
        sym = 0;
        asym = 0;
        for (int i = 0; i < COUNT; i++) {
            ALL_POOL[i] = i;
            if (DEFS[i].symmetric) SYMMETRIC_POOL[sym++] = i; else ASYMMETRIC_POOL[asym++] = i;
        }
    }

    private static final int N = T_NORMAL, MI = T_MIRROR, F = T_FREEZE, S = T_SPEED, M = T_MOVE;
    private static final int[][] LEVEL_TYPES = {
            {N, N, F, N, S, N, M, N},
            {N, N, N, F, N, M, S, N, N, F, M, N},
            {N, MI, M, F, S, M, MI, N, F, M, S, MI, M, F, S, M}};

    /** The poses of a round: for each slot its type, poses and timing in beats. */
    public static final class Plan {
        /** Number of slots. */
        public final int slots;
        /** Type of each slot (T_ constants). */
        public final int[] type = new int[16];
        /** Number of poses in each slot (1 for everything but moves). */
        public final int[] parts = new int[16];
        /** The poses of each slot. */
        public final int[][] pose = new int[16][3];
        /** Beats to get ready, to hold, and the beat the slot starts on (the first slot starts on 0). */
        public final int[] ready = new int[16], hold = new int[16], start = new int[16];
        /** Big title (e.g. "Flamingo" or "Penguin then Star!"). */
        public final String[] title = new String[16];
        /** Small line under the title, or null. */
        public final String[] tag = new String[16];
        /** What the host says. */
        public final String[] say = new String[16];
        /** Beats of the whole round after the intro. */
        public int totalBeats;

        Plan(int slots) {
            this.slots = slots;
        }

        /**
         * Beats in a slot from the start of get-ready to the end of the result.
         * @param s slot
         * @return beats
         */
        public int length(int s) {
            return ready[s] + hold[s] + REST_BEATS;
        }

        /**
         * The pose asked for at a time inside a slot.
         * @param s slot
         * @param beatsIntoHold beats since the hold window began (negative while getting ready)
         * @return pose number
         */
        public int poseAt(int s, float beatsIntoHold) {
            if (parts[s] == 1 || beatsIntoHold <= 0f) return pose[s][0];
            int idx = (int) (beatsIntoHold / hold[s] * parts[s]);
            return pose[s][Math.min(parts[s] - 1, idx)];
        }
    }

    /**
     * Picks the poses of a round: no pose twice in a row, only symmetric arm poses on Easy, move rounds from Medium on, and opposite-side rounds on Hard only.
     * @param level 1..3
     * @param seed random seed
     * @return the plan
     */
    public static Plan plan(int level, long seed) {
        Random r = new Random(seed);
        int[] types = LEVEL_TYPES[Math.max(1, Math.min(3, level)) - 1];
        Plan pl = new Plan(types.length);
        int[] pool = level == 1 ? SYMMETRIC_POOL : ALL_POOL;
        int prev = -1, beat = 0;
        for (int s = 0; s < pl.slots; s++) {
            int t = types[s];
            pl.type[s] = t;
            pl.ready[s] = t == T_SPEED ? QUICK_READY_BEATS : READY_BEATS;
            pl.hold[s] = t == T_SPEED ? 3 : windowBeats(level);
            pl.parts[s] = 1;
            if (t == T_MOVE) {
                int[] mv;
                int moveIdx;
                if (level == 3 && r.nextInt(3) == 0) {
                    moveIdx = -1; // a made-up sequence
                    mv = new int[3];
                    for (int k = 0; k < 3; k++) {
                        int a;
                        do {
                            a = pool[r.nextInt(pool.length)];
                        } while ((k > 0 && a == mv[k - 1]) || (k == 0 && a == prev));
                        mv[k] = a;
                    }
                } else {
                    int[] okMoves = level == 1 ? new int[]{0, 1, 2} : level == 2 ? new int[]{0, 1, 2, 3, 4} :
                            new int[]{0, 1, 2, 3, 4, 5};
                    do {
                        moveIdx = okMoves[r.nextInt(okMoves.length)];
                        mv = MOVES[moveIdx];
                    } while (mv[0] == prev);
                }
                pl.parts[s] = mv.length;
                System.arraycopy(mv, 0, pl.pose[s], 0, mv.length);
                pl.hold[s] = mv.length * 2;
                if (moveIdx >= 0) {
                    pl.title[s] = MOVE_NAMES[moveIdx];
                    pl.say[s] = MOVE_NAMES[moveIdx] + "! Follow me!";
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < mv.length; k++) sb.append(k > 0 ? " then " : "").append(NAMES[mv[k]]);
                    pl.title[s] = sb.append('!').toString();
                    pl.say[s] = pl.title[s];
                }
                pl.tag[s] = "Follow me!";
            } else {
                int a;
                int[] from = t == T_MIRROR ? ASYMMETRIC_POOL : pool;
                do {
                    a = from[r.nextInt(from.length)];
                } while (a == prev);
                pl.pose[s][0] = a;
                pl.title[s] = NAMES[a];
                switch (t) {
                    case T_MIRROR:
                        pl.tag[s] = "Mirror!";
                        pl.say[s] = "Flip it! " + SAY[a];
                        break;
                    case T_FREEZE:
                        pl.tag[s] = "Music stops!";
                        pl.say[s] = "Freeze! " + SAY[a];
                        break;
                    case T_SPEED:
                        pl.tag[s] = "Quick!";
                        pl.say[s] = "Quick! " + SAY[a];
                        break;
                    default:
                        pl.tag[s] = null;
                        pl.say[s] = SAY[a];
                        break;
                }
            }
            prev = pl.pose[s][pl.parts[s] - 1];
            pl.start[s] = beat;
            beat += pl.ready[s] + pl.hold[s] + REST_BEATS;
        }
        pl.totalBeats = beat;
        return pl;
    }

    /**
     * Extra points for a run of Great or better results: +1 at 3 in a row, +2 at 6, +3 from 9.
     * @param streak results in a row at Great or better, counting this one
     * @return bonus points
     */
    public static int streakBonus(int streak) {
        return Math.min(3, streak / 3);
    }

    /**
     * Most points a round can give: 3 for each pose of each slot, plus the streak bonus of an unbroken run.
     * @param plan the plan
     * @return points
     */
    public static int maxPoints(Plan plan) {
        int total = 0;
        for (int s = 0; s < plan.slots; s++) total += 3 * plan.parts[s] + streakBonus(s + 1);
        return total;
    }

    /**
     * Stars for a score: one from 30% of the best possible, two from 55%, three from 80%; everyone gets one.
     * @param score points
     * @param max best possible
     * @return 1..3
     */
    public static int stars(int score, int max) {
        if (max <= 0) return 1;
        float f = (float) score / max;
        return f >= 0.80f ? 3 : f >= 0.55f ? 2 : 1;
    }

    // ---- poses as arm positions (for drawing and for tests) ----

    /**
     * The arm directions that describe a pose (the same convention as the matcher). Arms the pose leaves free get a
     * natural direction; the arm with a hand-on-hip or hand-on-nose rule is placed by {@link #arms}.
     * @param pose pose number
     * @param out four angles LU, LF, RU, RF
     */
    public static void drawAngles(int pose, float[] out) {
        Def d = DEFS[pose];
        for (int i = 0; i < 4; i++) out[i] = Float.isNaN(d.arm[i]) ? ((i & 1) == 0 ? 15f : 5f) : d.arm[i];
    }

    /**
     * Which wrist a pose puts on a point.
     * @param pose pose number
     * @return 0 left, 1 right, -1 none
     */
    public static int nearSide(int pose) {
        return DEFS[pose].nearSide;
    }

    /**
     * Whether the near point is the nose (otherwise the hip).
     * @param pose pose number
     * @return true for the nose
     */
    public static boolean nearNose(int pose) {
        return DEFS[pose].nearPoint == NOSE_POINT;
    }

    /**
     * Torso tilt of a pose.
     * @param pose pose number
     * @return degrees, 0 when the pose has none
     */
    public static float tilt(int pose) {
        return Float.isNaN(DEFS[pose].tilt) ? 0f : DEFS[pose].tilt;
    }

    /**
     * Puts the elbows and wrists of both arms in place from four directions.
     * @param lsx x of the left shoulder
     * @param rsx x of the right shoulder
     * @param sy y of the shoulders
     * @param k arm length as a part of the normal child's arm (1 = upper arm 0.9 and forearm 0.8 shoulder widths)
     * @param ang four angles LU, LF, RU, RF in degrees
     * @param o receives left elbow x, y, left wrist x, y, right elbow x, y, right wrist x, y
     */
    public static void armsFromAngles(float lsx, float rsx, float sy, float k, float[] ang, float[] o) {
        float sw = rsx - lsx, upper = 0.9f * k * sw, fore = 0.8f * k * sw;
        for (int side = 0; side < 2; side++) {
            float dir = side == 0 ? -1f : 1f, sx = side == 0 ? lsx : rsx;
            double a = Math.toRadians(ang[side * 2]), b = Math.toRadians(ang[side * 2 + 1]);
            float ex = sx + dir * upper * (float) Math.sin(a), ey = sy + upper * (float) Math.cos(a);
            o[side * 4] = ex;
            o[side * 4 + 1] = ey;
            o[side * 4 + 2] = ex + dir * fore * (float) Math.sin(b);
            o[side * 4 + 3] = ey + fore * (float) Math.cos(b);
        }
    }

    /**
     * Puts the elbows and wrists of both arms in place for a pose; the arm with a hand-on-nose or hand-on-hip rule
     * reaches its point with the elbow out to the side.
     * @param pose pose number
     * @param lsx x of the left shoulder
     * @param rsx x of the right shoulder
     * @param sy y of the shoulders
     * @param noseX x of the nose
     * @param noseY y of the nose
     * @param hipLx x of the left hip
     * @param hipRx x of the right hip
     * @param hipY y of the hips
     * @param k arm length factor (see {@link #armsFromAngles})
     * @param o receives left elbow x, y, left wrist x, y, right elbow x, y, right wrist x, y
     */
    public static void arms(int pose, float lsx, float rsx, float sy, float noseX, float noseY, float hipLx, float hipRx,
                            float hipY, float k, float[] o) {
        float[] ang = TMP_ANG;
        drawAngles(pose, ang);
        armsFromAngles(lsx, rsx, sy, k, ang, o);
        int near = DEFS[pose].nearSide;
        if (near < 0) return;
        float sw = rsx - lsx, dir = near == 0 ? -1f : 1f, sx = near == 0 ? lsx : rsx;
        float tx, ty;
        if (DEFS[pose].nearPoint == NOSE_POINT) {
            tx = noseX + dir * 0.15f * sw;
            ty = noseY + 0.15f * sw;
        } else {
            tx = (near == 0 ? hipLx : hipRx) + dir * 0.07f * sw;
            ty = hipY;
        }
        float l1 = 0.9f * k * sw, l2 = 0.8f * k * sw;
        float dx = tx - sx, dy = ty - sy, d = (float) Math.sqrt(dx * dx + dy * dy);
        d = Math.max(1f, Math.min(d, l1 + l2 - 1f));
        float a = (l1 * l1 - l2 * l2 + d * d) / (2f * d);
        float h = (float) Math.sqrt(Math.max(0f, l1 * l1 - a * a));
        float ux = dx / Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy)), uy = dy / Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
        float px = sx + ux * a, py = sy + uy * a, ox = -uy, oy = ux;
        if (ox * dir < 0f) {
            ox = -ox;
            oy = -oy;
        }
        int b = near * 4;
        o[b] = px + ox * h;
        o[b + 1] = py + oy * h;
        o[b + 2] = sx + ux * d;
        o[b + 3] = sy + uy * d;
    }

    private static final float[] TMP_ANG = new float[4];

    /**
     * Builds the 17 keypoints of a child standing in a pose (for tests and for drawing): head, shoulders, elbows, wrists,
     * hips, knees and ankles; the upper body leans for poses with a tilt.
     * @param pose pose number
     * @param cx centre x
     * @param sy y of the shoulders
     * @param sw shoulder width
     * @param x receives 17 x values
     * @param y receives 17 y values
     */
    public static void skeleton(int pose, float cx, float sy, float sw, float[] x, float[] y) {
        float hipY = sy + 1.5f * sw, hipHalf = 0.35f * sw;
        x[Kp.NOSE] = cx;
        y[Kp.NOSE] = sy - 0.8f * sw;
        x[Kp.L_EYE] = cx - 0.17f * sw;
        x[Kp.R_EYE] = cx + 0.17f * sw;
        y[Kp.L_EYE] = y[Kp.R_EYE] = y[Kp.NOSE] - 0.15f * sw;
        x[Kp.L_EAR] = cx - 0.33f * sw;
        x[Kp.R_EAR] = cx + 0.33f * sw;
        y[Kp.L_EAR] = y[Kp.R_EAR] = y[Kp.NOSE] - 0.08f * sw;
        x[Kp.L_SHOULDER] = cx - 0.5f * sw;
        x[Kp.R_SHOULDER] = cx + 0.5f * sw;
        y[Kp.L_SHOULDER] = y[Kp.R_SHOULDER] = sy;
        x[Kp.L_HIP] = cx - hipHalf;
        x[Kp.R_HIP] = cx + hipHalf;
        y[Kp.L_HIP] = y[Kp.R_HIP] = hipY;
        x[Kp.L_KNEE] = x[Kp.L_ANKLE] = cx - hipHalf;
        x[Kp.R_KNEE] = x[Kp.R_ANKLE] = cx + hipHalf;
        y[Kp.L_KNEE] = y[Kp.R_KNEE] = hipY + sw;
        y[Kp.L_ANKLE] = y[Kp.R_ANKLE] = hipY + 2f * sw;
        float[] o = new float[8];
        arms(pose, x[Kp.L_SHOULDER], x[Kp.R_SHOULDER], sy, x[Kp.NOSE], y[Kp.NOSE], x[Kp.L_HIP], x[Kp.R_HIP], hipY, 1f, o);
        x[Kp.L_ELBOW] = o[0];
        y[Kp.L_ELBOW] = o[1];
        x[Kp.L_WRIST] = o[2];
        y[Kp.L_WRIST] = o[3];
        x[Kp.R_ELBOW] = o[4];
        y[Kp.R_ELBOW] = o[5];
        x[Kp.R_WRIST] = o[6];
        y[Kp.R_WRIST] = o[7];
        float lean = tilt(pose);
        if (Math.abs(lean) > 0.001f) {
            double a = Math.toRadians(lean), cos = Math.cos(a), sin = Math.sin(a);
            int[] upper = {Kp.NOSE, Kp.L_EYE, Kp.R_EYE, Kp.L_EAR, Kp.R_EAR, Kp.L_SHOULDER, Kp.R_SHOULDER, Kp.L_ELBOW,
                    Kp.R_ELBOW, Kp.L_WRIST, Kp.R_WRIST};
            for (int kp : upper) {
                float dx = x[kp] - cx, dy = y[kp] - hipY;
                x[kp] = (float) (cx + dx * cos - dy * sin);
                y[kp] = (float) (hipY + dx * sin + dy * cos);
            }
        }
    }
}
