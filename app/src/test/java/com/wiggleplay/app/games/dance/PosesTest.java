package com.wiggleplay.app.games.dance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.pose.PoseFrame;
import com.wiggleplay.app.pose.TestPoseSource;

import org.junit.Test;

public class PosesTest {

    // preset key (1..9) -> pose number
    private static final int[] PRESET_POSE = {-1, Poses.STAR, Poses.AIRPLANE, Poses.ROCKET, Poses.ROBOT, Poses.CRAB,
            Poses.FLAMINGO, Poses.PENGUIN, Poses.MONKEY, Poses.LEAN_LEFT};

    private static PlayerPose body(int preset, int control) {
        TestPoseSource src = new TestPoseSource();
        if (preset > 0) src.setPreset(preset, true);
        if (control >= 0) src.setHeld(control, true);
        PoseFrame f = null;
        long t = 1000;
        for (int i = 0; i < 40; i++) {
            f = src.sample(t, f);
            t += 90;
        }
        return f.players[0];
    }

    @Test
    public void everyPresetScoresHighOnItsOwnPose() {
        for (int k = 1; k <= 9; k++) {
            float q = Poses.match(PRESET_POSE[k], body(k, -1), null);
            assertTrue("preset " + k + " scored " + q, q >= 0.85f);
        }
        float q = Poses.match(Poses.LEAN_RIGHT, body(0, TestPoseSource.CTRL_LEAN_RIGHT), null);
        assertTrue("lean right scored " + q, q >= 0.85f);
    }

    @Test
    public void aClearlyDifferentPoseScoresLow() {
        int[][] pairs = {
                {1, Poses.AIRPLANE}, {1, Poses.PENGUIN}, {2, Poses.STAR}, {2, Poses.PENGUIN}, {3, Poses.AIRPLANE},
                {3, Poses.PENGUIN}, {4, Poses.PENGUIN}, {5, Poses.PENGUIN}, {6, Poses.AIRPLANE}, {6, Poses.PENGUIN},
                {7, Poses.STAR}, {7, Poses.ROCKET}, {8, Poses.STAR}, {8, Poses.AIRPLANE}, {9, Poses.STAR},
                {9, Poses.LEAN_RIGHT}};
        for (int[] pr : pairs) {
            float q = Poses.match(pr[1], body(pr[0], -1), null);
            assertTrue("preset " + pr[0] + " on " + Poses.NAMES[pr[1]] + " scored " + q, q < 0.5f);
        }
    }

    @Test
    public void leaningTheOtherWayScoresZero() {
        assertEquals(0f, Poses.match(Poses.LEAN_RIGHT, body(9, -1), null), 0.001f);
        assertEquals(0f, Poses.match(Poses.LEAN_LEFT, body(0, TestPoseSource.CTRL_LEAN_RIGHT), null), 0.001f);
    }

    @Test
    public void flamingoAndMonkeyWorkOnEitherSide() {
        PlayerPose p = body(6, -1);
        mirrorBody(p);
        assertTrue(Poses.match(Poses.FLAMINGO, p, null) >= 0.85f);
        p = body(8, -1);
        mirrorBody(p);
        assertTrue(Poses.match(Poses.MONKEY, p, null) >= 0.85f);
    }

    /** Swaps left and right around the middle of the shoulders. */
    private static void mirrorBody(PlayerPose p) {
        float cx = (p.kps[Kp.L_SHOULDER].x + p.kps[Kp.R_SHOULDER].x) / 2f;
        for (int k = 0; k < Kp.COUNT; k++) p.kps[k].x = 2 * cx - p.kps[k].x;
        for (int k = 1; k < Kp.COUNT; k += 2) {
            if (k + 1 >= Kp.COUNT) break;
            float x = p.kps[k].x, y = p.kps[k].y, s = p.kps[k].s;
            p.kps[k].x = p.kps[k + 1].x; p.kps[k].y = p.kps[k + 1].y; p.kps[k].s = p.kps[k + 1].s;
            p.kps[k + 1].x = x; p.kps[k + 1].y = y; p.kps[k + 1].s = s;
        }
    }

    @Test
    public void missingKeypointsAreSkippedWhileTwoPartsRemain() {
        PlayerPose p = body(1, -1);
        p.kps[Kp.L_WRIST].s = 0f; // left forearm gone, three parts remain
        assertTrue(Poses.match(Poses.STAR, p, null) >= 0.85f);
        p.kps[Kp.R_WRIST].s = 0f; // two parts remain
        assertTrue(Poses.match(Poses.STAR, p, null) >= 0.85f);
        p.kps[Kp.L_ELBOW].s = 0f; // only the right upper arm is left
        assertEquals(0f, Poses.match(Poses.STAR, p, null), 0f);
    }

    @Test
    public void noBodyOrNoShouldersGivesZero() {
        PlayerPose p = body(1, -1);
        p.present = false;
        assertEquals(0f, Poses.match(Poses.STAR, p, null), 0f);
        p.present = true;
        p.kps[Kp.L_SHOULDER].s = 0f;
        assertEquals(0f, Poses.match(Poses.STAR, p, null), 0f);
        assertEquals(0f, Poses.match(Poses.STAR, null, null), 0f);
    }

    @Test
    public void detailsTellWhichArmFits() {
        PlayerPose p = body(1, -1); // star
        Poses.Result r = new Poses.Result();
        Poses.match(Poses.AIRPLANE, p, r);
        // airplane wants 90, star arms are at 135: error 45 -> 0
        for (int i = 0; i < 4; i++) assertEquals(0f, r.seg[i], 0.02f);
        Poses.match(Poses.STAR, p, r);
        for (int i = 0; i < 4; i++) assertEquals(1f, r.seg[i], 0.02f);
        assertEquals(1f, r.quality, 0.02f);
    }

    @Test
    public void armsAreMeasuredAgainstTheShoulderLine() {
        // a leaning body keeps the same arm angles
        PlayerPose p = body(2, TestPoseSource.CTRL_LEAN_LEFT);
        assertTrue(Poses.match(Poses.AIRPLANE, p, null) >= 0.85f);
    }

    @Test
    public void pointsNeverBelowOne() {
        assertEquals(3, Poses.points(0.9f));
        assertEquals(3, Poses.points(0.75f));
        assertEquals(2, Poses.points(0.6f));
        assertEquals(1, Poses.points(0.4f));
        assertEquals(1, Poses.points(0f));
        assertEquals("Perfect!", Poses.label(0.8f));
        assertEquals("Great!", Poses.label(0.6f));
        assertEquals("Good!", Poses.label(0.4f));
        assertEquals("Keep trying!", Poses.label(0.1f));
    }

    private static PlayerPose skeleton(int pose) {
        float[] x = new float[Kp.COUNT], y = new float[Kp.COUNT];
        Poses.skeleton(pose, 960f, 400f, 180f, x, y);
        PlayerPose p = new PlayerPose(0);
        p.present = true;
        for (int k = 0; k < Kp.COUNT; k++) {
            p.kps[k].x = x[k];
            p.kps[k].y = y[k];
            p.kps[k].s = 0.9f;
        }
        return p;
    }

    @Test
    public void everyPoseScoresHighOnItsOwnSkeleton() {
        for (int i = 0; i < Poses.COUNT; i++) {
            float q = Poses.match(i, skeleton(i), null);
            assertTrue(Poses.NAMES[i] + " scored " + q, q >= 0.9f);
        }
    }

    @Test
    public void noPoseIsPerfectOnAnotherPose() {
        StringBuilder bad = new StringBuilder();
        for (int a = 0; a < Poses.COUNT; a++) {
            for (int b = 0; b < Poses.COUNT; b++) {
                if (a == b) continue;
                float q = Poses.match(b, skeleton(a), null);
                // a pose that asks for less is satisfied by one that asks for more
                boolean subset = a == Poses.PALM && (b == Poses.LEAN_LEFT || b == Poses.REACH);
                if (q >= Poses.PERFECT && !subset) bad.append(Poses.NAMES[a]).append(" on ").append(Poses.NAMES[b]).append(' ').append(q).append("; ");
            }
        }
        assertEquals("", bad.toString());
    }

    @Test
    public void oppositeModeWantsTheOtherSide() {
        int[] asym = {Poses.FLAMINGO, Poses.MONKEY, Poses.LEAN_LEFT, Poses.LEAN_RIGHT, Poses.REACH, Poses.SURFER,
                Poses.PALM, Poses.BIRD};
        for (int pose : asym) {
            PlayerPose same = skeleton(pose);
            PlayerPose other = skeleton(pose);
            mirrorBody(other);
            assertTrue(Poses.NAMES[pose] + " opposite on the mirror image", Poses.match(pose, other, null, true) >= 0.9f);
            assertTrue(Poses.NAMES[pose] + " opposite on the same side", Poses.match(pose, same, null, true) < 0.6f);
        }
    }

    @Test
    public void measureReadsTheArmDirections() {
        float[] ang = new float[4];
        assertTrue(Poses.measure(skeleton(Poses.AIRPLANE), ang));
        for (int i = 0; i < 4; i++) assertEquals(90f, ang[i], 1f);
        assertTrue(Poses.measure(skeleton(Poses.ROBOT), ang));
        assertEquals(90f, ang[0], 1f);
        assertEquals(180f, Math.abs(ang[1]), 1f);
        PlayerPose p = skeleton(Poses.STAR);
        p.kps[Kp.L_WRIST].s = 0f;
        assertTrue(Poses.measure(p, ang));
        assertTrue(Float.isNaN(ang[1]));
        p.kps[Kp.R_SHOULDER].s = 0f;
        assertFalse(Poses.measure(p, ang));
        assertEquals(-20f, Poses.tiltOf(skeleton(Poses.LEAN_LEFT)), 0.5f);
        assertEquals(0f, Poses.tiltOf(skeleton(Poses.STAR)), 0.5f);
    }

    @Test
    public void armsReachTheirPointsAndKeepTheirLengths() {
        float[] o = new float[8];
        Poses.arms(Poses.MONKEY, 870f, 1050f, 400f, 960f, 256f, 897f, 1023f, 670f, 1f, o);
        // right wrist near the nose, arm lengths unchanged
        assertEquals(960f + 0.15f * 180f, o[6], 3f);
        assertEquals(256f + 0.15f * 180f, o[7], 3f);
        float upper = (float) Math.hypot(o[4] - 1050f, o[5] - 400f), fore = (float) Math.hypot(o[6] - o[4], o[7] - o[5]);
        assertEquals(0.9f * 180f, upper, 1f);
        assertEquals(0.8f * 180f, fore, 1f);
        // a short-armed host cannot reach: the arm just stretches towards the point
        Poses.arms(Poses.MONKEY, 870f, 1050f, 400f, 960f, 100f, 897f, 1023f, 670f, 0.5f, o);
        assertTrue(Math.hypot(o[6] - 1050f, o[7] - 400f) <= 0.85f * 180f * 0.5f * 2f);
    }

    @Test
    public void bestWindowFindsTheBestSecond() {
        // 3 seconds at 10 samples a second: bad, then a good second, then bad
        float[] t = new float[30], q = new float[30];
        for (int i = 0; i < 30; i++) {
            t[i] = i / 10f;
            q[i] = i >= 10 && i < 21 ? 1f : 0.1f;
        }
        assertEquals(1f, Poses.bestWindow(t, q, 0, 30, 1f), 0.001f);
        // a one second stretch cannot be filled by a 0.5 second spike
        for (int i = 0; i < 30; i++) q[i] = i >= 12 && i < 17 ? 1f : 0.1f;
        assertTrue(Poses.bestWindow(t, q, 0, 30, 1f) < 0.6f);
        // shorter than the span: the average of everything
        assertEquals(0.55f, Poses.bestWindow(new float[]{0f, 0.1f}, new float[]{0.1f, 1f}, 0, 2, 1f), 0.001f);
        assertEquals(0f, Poses.bestWindow(t, q, 5, 5, 1f), 0f);
    }

    @Test
    public void levelsHaveTheRightShape() {
        assertEquals(100, Poses.bpm(1));
        assertEquals(112, Poses.bpm(2));
        assertEquals(124, Poses.bpm(3));
        assertEquals(6, Poses.windowBeats(1));
        assertEquals(4, Poses.windowBeats(3));
        int[] slots = {8, 12, 16};
        for (int level = 1; level <= 3; level++) {
            boolean sawFreeze = false, sawSpeed = false, sawMove = false, sawMirror = false;
            for (long seed = 0; seed < 100; seed++) {
                Poses.Plan pl = Poses.plan(level, seed);
                assertEquals(slots[level - 1], pl.slots);
                int beat = 0, prev = -1;
                for (int s = 0; s < pl.slots; s++) {
                    assertEquals(beat, pl.start[s]);
                    beat += pl.length(s);
                    assertTrue(pl.parts[s] >= 1 && pl.parts[s] <= 3);
                    assertTrue(pl.title[s] != null && pl.say[s] != null);
                    if (pl.type[s] == Poses.T_MOVE) {
                        sawMove = true;
                        assertEquals(pl.parts[s] * 2, pl.hold[s]);
                        assertTrue(pl.parts[s] >= 2);
                    } else {
                        assertEquals(1, pl.parts[s]);
                    }
                    if (pl.type[s] == Poses.T_FREEZE) sawFreeze = true;
                    if (pl.type[s] == Poses.T_SPEED) {
                        sawSpeed = true;
                        assertTrue(pl.hold[s] < Poses.windowBeats(level) + 1);
                    }
                    if (pl.type[s] == Poses.T_MIRROR) {
                        sawMirror = true;
                        assertFalse(Poses.symmetric(pl.pose[s][0]));
                    }
                    assertTrue(pl.pose[s][0] != prev);
                    for (int k = 0; k < pl.parts[s]; k++) {
                        int p = pl.pose[s][k];
                        assertTrue(p >= 0 && p < Poses.COUNT);
                        if (level == 1) assertTrue(Poses.NAMES[p] + " on Easy", Poses.symmetric(p));
                        if (k > 0) assertTrue(p != pl.pose[s][k - 1]);
                    }
                    prev = pl.pose[s][pl.parts[s] - 1];
                }
                assertEquals(beat, pl.totalBeats);
            }
            assertTrue(sawFreeze && sawSpeed && sawMove);
            assertEquals(level > 1, sawMirror);
        }
    }

    @Test
    public void poseAtFollowsTheMove() {
        Poses.Plan pl = null;
        for (long seed = 0; pl == null; seed++) {
            Poses.Plan t = Poses.plan(2, seed);
            for (int s = 0; s < t.slots; s++) if (t.type[s] == Poses.T_MOVE && t.parts[s] == 2) pl = t;
        }
        int s = 0;
        while (pl.type[s] != Poses.T_MOVE || pl.parts[s] != 2) s++;
        assertEquals(pl.pose[s][0], pl.poseAt(s, -1f));
        assertEquals(pl.pose[s][0], pl.poseAt(s, 1f));
        assertEquals(pl.pose[s][1], pl.poseAt(s, pl.hold[s] - 0.5f));
        assertEquals(pl.pose[s][1], pl.poseAt(s, pl.hold[s] + 5f));
    }

    @Test
    public void streakBonusAndMaxPoints() {
        assertEquals(0, Poses.streakBonus(2));
        assertEquals(1, Poses.streakBonus(3));
        assertEquals(2, Poses.streakBonus(6));
        assertEquals(3, Poses.streakBonus(9));
        assertEquals(3, Poses.streakBonus(20));
        for (int level = 1; level <= 3; level++) {
            Poses.Plan pl = Poses.plan(level, 5);
            int max = Poses.maxPoints(pl);
            int base = 0;
            for (int s = 0; s < pl.slots; s++) base += 3 * pl.parts[s];
            assertTrue(max > base);
            assertTrue(max <= base + 3 * pl.slots);
        }
    }

    @Test
    public void starsFollowTheScore() {
        assertEquals(1, Poses.stars(0, 24));
        assertEquals(1, Poses.stars(7, 24));
        assertEquals(2, Poses.stars(14, 24));
        assertEquals(3, Poses.stars(20, 24));
        assertEquals(1, Poses.stars(5, 0));
    }
}
