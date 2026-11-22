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
            MONKEY = 7, LEAN_LEFT = 8, LEAN_RIGHT = 9, COUNT = 10;

    /** Names shown on the cards. */
    public static final String[] NAMES = {"Star", "Airplane", "Rocket", "Robot", "Crab", "Flamingo", "Penguin",
            "Monkey", "Lean left", "Lean right"};
    /** What the host says. */
    public static final String[] SAY = {"Be a star!", "Be an airplane!", "Be a rocket!", "Be a robot!", "Be a crab!",
            "Be a flamingo!", "Be a penguin!", "Be a monkey!", "Lean to the left!", "Lean to the right!"};

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
        Def d = DEFS[pose];
        Result a = out != null ? out : TMP_A;
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
                sum += s * W_TILT;
                weight += W_TILT;
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
     * Number of poses (slots) in a round.
     * @param level 1..3
     * @return 8, 12 or 16
     */
    public static int slots(int level) {
        return level == 1 ? 8 : level == 2 ? 12 : 16;
    }

    /**
     * Length of the hold window in beats.
     * @param level 1..3
     * @return 6, 5 or 4
     */
    public static int windowBeats(int level) {
        return level == 1 ? 6 : level == 2 ? 5 : 4;
    }

    /**
     * Whether a slot of a level is a two-pose combo ("Rocket then Robot!").
     * @param level 1..3
     * @param slot slot number
     * @return true for combos (Hard level, every third pose after the first two)
     */
    public static boolean isCombo(int level, int slot) {
        return level == 3 && slot >= 2 && slot % 3 == 2;
    }

    /**
     * Picks the poses of a round: never the same pose twice in a row, only symmetric arm poses on Easy.
     * @param level 1..3
     * @param seed random seed
     * @param first receives the first pose of each slot (length = slots)
     * @param second receives the second pose of combo slots, otherwise -1
     */
    public static void plan(int level, long seed, int[] first, int[] second) {
        Random r = new Random(seed);
        int n = slots(level);
        int[] pool = new int[COUNT];
        int m = 0;
        for (int i = 0; i < COUNT; i++) if (level != 1 || DEFS[i].symmetric) pool[m++] = i;
        int prev = -1;
        for (int s = 0; s < n; s++) {
            int a;
            do {
                a = pool[r.nextInt(m)];
            } while (a == prev);
            first[s] = a;
            prev = a;
            int b = -1;
            if (isCombo(level, s)) {
                do {
                    b = pool[r.nextInt(m)];
                } while (b == a);
                prev = b;
            }
            second[s] = b;
        }
    }

    /**
     * Most points a round can give.
     * @param level 1..3
     * @return 3 per pose, 6 per combo
     */
    public static int maxPoints(int level) {
        int n = slots(level), total = 0;
        for (int s = 0; s < n; s++) total += isCombo(level, s) ? 6 : 3;
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

    // ---- for drawing the pose cards ----

    /**
     * The arm directions to draw for a pose (the same convention as the matcher). Arms the pose leaves free get a
     * natural direction; the arm with a hand-on-hip or hand-on-nose rule is drawn with {@link #nearSide}.
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
}
