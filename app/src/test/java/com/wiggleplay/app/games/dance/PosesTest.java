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
        assertEquals(8, Poses.slots(1));
        assertEquals(12, Poses.slots(2));
        assertEquals(16, Poses.slots(3));
        assertEquals(6, Poses.windowBeats(1));
        assertEquals(4, Poses.windowBeats(3));
        for (int level = 1; level <= 3; level++) {
            for (long seed = 0; seed < 50; seed++) {
                int n = Poses.slots(level);
                int[] a = new int[n], b = new int[n];
                Poses.plan(level, seed, a, b);
                int prev = -1;
                for (int s = 0; s < n; s++) {
                    assertTrue(a[s] >= 0 && a[s] < Poses.COUNT);
                    assertTrue(a[s] != prev);
                    if (level == 1) assertTrue(Poses.symmetric(a[s]));
                    assertEquals(Poses.isCombo(level, s), b[s] >= 0);
                    if (b[s] >= 0) {
                        assertTrue(b[s] != a[s]);
                        prev = b[s];
                    } else {
                        prev = a[s];
                    }
                }
            }
        }
        assertFalse(Poses.isCombo(1, 5));
        assertFalse(Poses.isCombo(2, 5));
        assertEquals(24, Poses.maxPoints(1));
        assertEquals(36, Poses.maxPoints(2));
        assertEquals(48 + 5 * 3, Poses.maxPoints(3));
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
