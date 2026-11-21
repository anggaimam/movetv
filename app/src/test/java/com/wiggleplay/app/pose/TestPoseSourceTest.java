package com.wiggleplay.app.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestPoseSourceTest {

    private static final float EPS = 0.01f;

    private static Keypoint kp(PoseFrame f, int player, int k) {
        return f.players[player].kps[k];
    }

    private static PoseFrame at(TestPoseSource s, long t) {
        return s.sample(t, new PoseFrame());
    }

    @Test
    public void standingChildHas17ConfidentKeypointsAndShoulderWidth180() {
        TestPoseSource s = new TestPoseSource();
        PoseFrame f = at(s, 1000);
        assertEquals("test", f.source);
        assertEquals(12f, f.aiFps, 0f);
        assertEquals(1, f.mode);
        assertEquals(1000, f.timeMs);
        assertTrue(f.players[0].present);
        assertFalse(f.players[1].present);
        for (int k = 0; k < Kp.COUNT; k++) {
            Keypoint p = kp(f, 0, k);
            assertEquals(0.9f, p.s, 0f);
            assertTrue("x in stage " + k, p.x >= 0 && p.x <= PoseFrame.STAGE_W);
            assertTrue("y in stage " + k, p.y >= 0 && p.y <= PoseFrame.STAGE_H);
        }
        assertEquals(180f, PoseFrame.shoulderWidth(f.players[0]), EPS);
        // centred on the stage
        assertEquals(960f, (kp(f, 0, Kp.L_SHOULDER).x + kp(f, 0, Kp.R_SHOULDER).x) / 2f, EPS);
        // plausible proportions: head above shoulders above hips above knees above ankles
        assertTrue(kp(f, 0, Kp.NOSE).y < kp(f, 0, Kp.L_SHOULDER).y);
        assertTrue(kp(f, 0, Kp.L_SHOULDER).y < kp(f, 0, Kp.L_HIP).y);
        assertTrue(kp(f, 0, Kp.L_HIP).y < kp(f, 0, Kp.L_KNEE).y);
        assertTrue(kp(f, 0, Kp.L_KNEE).y < kp(f, 0, Kp.L_ANKLE).y);
        // mirrored like a mirror: the right side of the body is on the right of the screen
        assertTrue(kp(f, 0, Kp.R_SHOULDER).x > kp(f, 0, Kp.L_SHOULDER).x);
        assertTrue(kp(f, 0, Kp.R_WRIST).x > kp(f, 0, Kp.L_WRIST).x);
        // arms hang: wrists below elbows
        assertTrue(kp(f, 0, Kp.R_WRIST).y > kp(f, 0, Kp.R_ELBOW).y);
        assertTrue(kp(f, 0, Kp.L_WRIST).y > kp(f, 0, Kp.L_ELBOW).y);
    }

    @Test
    public void twoPlayersAreCentredInTheirHalves() {
        TestPoseSource s = new TestPoseSource();
        s.setPlayers(2);
        PoseFrame f = at(s, 0);
        assertEquals(2, f.mode);
        assertTrue(f.players[0].present);
        assertTrue(f.players[1].present);
        float c0 = (kp(f, 0, Kp.L_SHOULDER).x + kp(f, 0, Kp.R_SHOULDER).x) / 2f;
        float c1 = (kp(f, 1, Kp.L_SHOULDER).x + kp(f, 1, Kp.R_SHOULDER).x) / 2f;
        assertEquals(480f, c0, EPS);
        assertEquals(1440f, c1, EPS);
        for (int k = 0; k < Kp.COUNT; k++) {
            assertTrue("player 0 stays in the left half", kp(f, 0, k).x < 960f);
            assertTrue("player 1 stays in the right half", kp(f, 1, k).x > 960f);
        }
        assertEquals(180f, PoseFrame.shoulderWidth(f.players[1]), EPS);
    }

    @Test
    public void backToOnePlayerHidesPlayerTwo() {
        TestPoseSource s = new TestPoseSource();
        s.setPlayers(2);
        s.setActivePlayer(1);
        s.setPlayers(1);
        assertEquals(0, s.activePlayer());
        PoseFrame f = at(s, 100);
        assertTrue(f.players[0].present);
        assertFalse(f.players[1].present);
        assertEquals(0f, kp(f, 1, Kp.NOSE).s, 0f);
    }

    @Test
    public void pointerRaisesRightWristAboveElbowAndMovesIt() {
        TestPoseSource s = new TestPoseSource();
        float[][] spots = {{1500, 200}, {300, 900}, {960, 540}, {1900, 1070}, {10, 10}};
        long t = 0;
        for (float[] sp : spots) {
            s.pointerMove(sp[0], sp[1]);
            t += 100;
            PoseFrame f = at(s, t);
            Keypoint w = kp(f, 0, Kp.R_WRIST), e = kp(f, 0, Kp.R_ELBOW);
            assertEquals(sp[0], w.x, EPS);
            assertEquals(sp[1], w.y, EPS);
            assertTrue("wrist above elbow at " + sp[0] + "," + sp[1], w.y < e.y);
            assertTrue(kp(f, 0, Kp.L_WRIST).y > kp(f, 0, Kp.L_ELBOW).y); // left arm still hangs
        }
    }

    @Test
    public void elbowSitsBetweenShoulderAndWristWhenHandIsHigh() {
        TestPoseSource s = new TestPoseSource();
        s.pointerMove(1300, 150);
        PoseFrame f = at(s, 100);
        Keypoint sh = kp(f, 0, Kp.R_SHOULDER), e = kp(f, 0, Kp.R_ELBOW), w = kp(f, 0, Kp.R_WRIST);
        assertEquals((sh.x + w.x) / 2f, e.x, EPS);
        assertEquals((sh.y + w.y) / 2f, e.y, EPS);
    }

    @Test
    public void armStaysRaisedTwoSecondsAfterReleaseThenDrops() {
        TestPoseSource s = new TestPoseSource();
        s.pointerMove(1400, 250);
        at(s, 1000);
        s.pointerUp(1000);
        PoseFrame f = at(s, 1100);
        assertTrue(kp(f, 0, Kp.R_WRIST).y < kp(f, 0, Kp.R_ELBOW).y);
        f = at(s, 2900); // 1.9 s after release
        assertTrue("still raised at 1.9 s", kp(f, 0, Kp.R_WRIST).y < kp(f, 0, Kp.R_ELBOW).y);
        assertEquals(1400f, kp(f, 0, Kp.R_WRIST).x, EPS);
        f = at(s, 3050); // 2.05 s after release
        assertTrue("dropped after 2 s", kp(f, 0, Kp.R_WRIST).y > kp(f, 0, Kp.R_ELBOW).y);
    }

    @Test
    public void jumpRisesAlongAnArcAndLandsAfter500ms() {
        TestPoseSource s = new TestPoseSource();
        float rest = kp(at(s, 0), 0, Kp.L_SHOULDER).y;
        float prev = 0, peak = 0;
        // exact curve checks with fresh sources, each sampled once at a chosen time
        float[] at = {0, 125, 250, 375, 499};
        for (float ms : at) {
            TestPoseSource j = new TestPoseSource();
            j.jump(5000);
            PoseFrame f = j.sample(5000 + (long) ms, new PoseFrame());
            float up = rest - kp(f, 0, Kp.L_SHOULDER).y;
            float expected = 120f * (float) Math.sin(Math.PI * ms / 500.0);
            assertEquals("jump height at " + ms, expected, up, 0.5f);
            peak = Math.max(peak, up);
            assertTrue(up >= prev - 0.5f || ms > 250);
            prev = up;
        }
        assertEquals(120f, peak, 1.5f);
        TestPoseSource j = new TestPoseSource();
        j.jump(5000);
        PoseFrame f = j.sample(5600, new PoseFrame());
        assertEquals(rest, kp(f, 0, Kp.L_SHOULDER).y, EPS);
        // the whole skeleton rises together (ankles too)
        TestPoseSource k = new TestPoseSource();
        float ankle = kp(at(k, 0), 0, Kp.L_ANKLE).y;
        k.jump(100);
        PoseFrame mid = k.sample(350, new PoseFrame());
        assertEquals(120f, ankle - kp(mid, 0, Kp.L_ANKLE).y, 1.5f);
    }

    @Test
    public void jumpKeyRepeatDoesNotRestartAJumpInProgress() {
        TestPoseSource s = new TestPoseSource();
        s.jump(1000);
        s.jump(1200); // ignored: still going
        PoseFrame f = s.sample(1250, new PoseFrame());
        float rest = kp(at(new TestPoseSource(), 0), 0, Kp.L_SHOULDER).y;
        assertEquals(120f, rest - kp(f, 0, Kp.L_SHOULDER).y, 0.5f); // peak of the FIRST jump
    }

    @Test
    public void duckLowersHeadAndShouldersBy160WhileHeldAndReturns() {
        TestPoseSource s = new TestPoseSource();
        PoseFrame rest = at(s, 0);
        s.setHeld(TestPoseSource.CTRL_DUCK, true);
        PoseFrame f = rest;
        long t = 0;
        for (int i = 0; i < 12; i++) { t += 100; f = s.sample(t, new PoseFrame()); }
        assertEquals(160f, kp(f, 0, Kp.NOSE).y - kp(rest, 0, Kp.NOSE).y, 0.5f);
        assertEquals(160f, kp(f, 0, Kp.L_SHOULDER).y - kp(rest, 0, Kp.L_SHOULDER).y, 0.5f);
        assertEquals(kp(rest, 0, Kp.L_HIP).y, kp(f, 0, Kp.L_HIP).y, EPS); // hips stay
        s.setHeld(TestPoseSource.CTRL_DUCK, false);
        for (int i = 0; i < 12; i++) { t += 100; f = s.sample(t, new PoseFrame()); }
        assertEquals(kp(rest, 0, Kp.NOSE).y, kp(f, 0, Kp.NOSE).y, 0.5f);
    }

    @Test
    public void leanTiltsShoulderLineTwentyDegreesTowardsTheLeanedSide() {
        TestPoseSource s = new TestPoseSource();
        s.setHeld(TestPoseSource.CTRL_LEAN_LEFT, true);
        PoseFrame f = null;
        long t = 0;
        for (int i = 0; i < 12; i++) { t += 100; f = s.sample(t, new PoseFrame()); }
        double angle = shoulderAngleDeg(f, 0);
        assertEquals(-20.0, angle, 0.3);
        assertTrue("left shoulder is lower when leaning left", kp(f, 0, Kp.L_SHOULDER).y > kp(f, 0, Kp.R_SHOULDER).y);
        assertTrue("head moves to the left", kp(f, 0, Kp.NOSE).x < 960f);

        TestPoseSource r = new TestPoseSource();
        r.setHeld(TestPoseSource.CTRL_LEAN_RIGHT, true);
        t = 0;
        for (int i = 0; i < 12; i++) { t += 100; f = r.sample(t, new PoseFrame()); }
        assertEquals(20.0, shoulderAngleDeg(f, 0), 0.3);
        assertTrue(kp(f, 0, Kp.NOSE).x > 960f);
    }

    private static double shoulderAngleDeg(PoseFrame f, int player) {
        Keypoint l = kp(f, player, Kp.L_SHOULDER), r = kp(f, player, Kp.R_SHOULDER);
        return Math.toDegrees(Math.atan2(r.y - l.y, r.x - l.x));
    }

    @Test
    public void armsOutPutsBothWristsLevelWithTheShoulders() {
        TestPoseSource s = new TestPoseSource();
        s.setHeld(TestPoseSource.CTRL_ARMS_OUT, true);
        PoseFrame f = at(s, 0);
        for (int side = 0; side < 2; side++) {
            int sh = side == 0 ? Kp.L_SHOULDER : Kp.R_SHOULDER;
            int wr = side == 0 ? Kp.L_WRIST : Kp.R_WRIST;
            assertEquals(kp(f, 0, sh).y, kp(f, 0, wr).y, EPS);
            assertTrue(Math.abs(kp(f, 0, wr).x - kp(f, 0, sh).x) > 200f);
        }
        assertTrue(kp(f, 0, Kp.R_WRIST).x > kp(f, 0, Kp.R_SHOULDER).x);
        assertTrue(kp(f, 0, Kp.L_WRIST).x < kp(f, 0, Kp.L_SHOULDER).x);
    }

    @Test
    public void armsOutWithPointerFollowsPointerHeightWithBothWrists() {
        TestPoseSource s = new TestPoseSource();
        s.setHeld(TestPoseSource.CTRL_ARMS_OUT, true);
        s.pointerMove(900, 250);
        PoseFrame f = at(s, 100);
        assertEquals(250f, kp(f, 0, Kp.L_WRIST).y, EPS);
        assertEquals(250f, kp(f, 0, Kp.R_WRIST).y, EPS);
        s.pointerMove(900, 700);
        f = at(s, 300);
        assertEquals(700f, kp(f, 0, Kp.L_WRIST).y, EPS);
        assertEquals(700f, kp(f, 0, Kp.R_WRIST).y, EPS);
    }

    @Test
    public void everyPresetGivesADifferentPoseOfTheArmsOrTorso() {
        float[][] sigs = new float[10][];
        for (int n = 1; n <= 9; n++) {
            TestPoseSource s = new TestPoseSource();
            s.setPreset(n, true);
            PoseFrame f = null;
            long t = 0;
            for (int i = 0; i < 12; i++) { t += 100; f = s.sample(t, new PoseFrame()); }
            PlayerPose p = f.players[0];
            assertTrue(p.present);
            sigs[n] = new float[]{kp(f, 0, Kp.L_WRIST).x, kp(f, 0, Kp.L_WRIST).y,
                    kp(f, 0, Kp.R_WRIST).x, kp(f, 0, Kp.R_WRIST).y,
                    kp(f, 0, Kp.R_ELBOW).x, kp(f, 0, Kp.R_ELBOW).y, kp(f, 0, Kp.NOSE).x};
        }
        for (int a = 1; a <= 9; a++) {
            for (int b = a + 1; b <= 9; b++) {
                float d = 0;
                for (int i = 0; i < sigs[a].length; i++) d += Math.abs(sigs[a][i] - sigs[b][i]);
                assertTrue("presets " + a + " and " + b + " differ", d > 20f);
            }
        }
    }

    @Test
    public void presetRocketPutsBothWristsAboveTheHead_andStarSpreadsThemUp() {
        TestPoseSource s = new TestPoseSource();
        s.setPreset(3, true);
        PoseFrame f = at(s, 0);
        assertTrue(kp(f, 0, Kp.L_WRIST).y < kp(f, 0, Kp.NOSE).y);
        assertTrue(kp(f, 0, Kp.R_WRIST).y < kp(f, 0, Kp.NOSE).y);
        s.setPreset(3, false);
        s.setPreset(1, true);
        f = at(s, 200);
        assertTrue(kp(f, 0, Kp.L_WRIST).y < kp(f, 0, Kp.L_SHOULDER).y);
        assertTrue(kp(f, 0, Kp.R_WRIST).x - kp(f, 0, Kp.L_WRIST).x > 400f);
    }

    @Test
    public void presetMonkeyAndFlamingoUseNoseAndHipRules() {
        TestPoseSource s = new TestPoseSource();
        s.setPreset(8, true);
        PoseFrame f = at(s, 0);
        assertTrue(PoseFrame.dist(kp(f, 0, Kp.R_WRIST), kp(f, 0, Kp.NOSE)) < 0.5f * 180f);
        s.setPreset(8, false);
        s.setPreset(6, true);
        f = at(s, 200);
        assertTrue(PoseFrame.dist(kp(f, 0, Kp.R_WRIST), kp(f, 0, Kp.R_HIP)) < 0.3f * 180f);
    }

    @Test
    public void releasingAPresetReturnsToStanding() {
        TestPoseSource s = new TestPoseSource();
        PoseFrame rest = at(s, 0);
        s.setPreset(3, true);
        at(s, 200);
        s.setPreset(3, false);
        PoseFrame f = at(s, 400);
        assertEquals(kp(rest, 0, Kp.R_WRIST).y, kp(f, 0, Kp.R_WRIST).y, EPS);
    }

    @Test
    public void tabSwitchesWhichPlayerTheInputControls() {
        TestPoseSource s = new TestPoseSource();
        s.setPlayers(2);
        assertEquals(0, s.activePlayer());
        s.pointerMove(300, 200);
        PoseFrame f = at(s, 100);
        assertTrue(kp(f, 0, Kp.R_WRIST).y < kp(f, 0, Kp.R_ELBOW).y);
        assertTrue(kp(f, 1, Kp.R_WRIST).y > kp(f, 1, Kp.R_ELBOW).y);
        s.pointerUp(100);
        s.switchPlayer();
        assertEquals(1, s.activePlayer());
        s.jump(1000);
        float rest = kp(at(new TestPoseSource(), 0), 0, Kp.L_SHOULDER).y;
        f = s.sample(1250, new PoseFrame());
        assertEquals(120f, rest - kp(f, 1, Kp.L_SHOULDER).y, 0.5f); // player 1 jumped
        assertEquals(rest, kp(f, 0, Kp.L_SHOULDER).y, EPS); // player 0 did not jump
        s.switchPlayer();
        assertEquals(0, s.activePlayer());
    }

    @Test
    public void sampleRecomputesAtTwelveHzAndCopiesInBetween() {
        TestPoseSource s = new TestPoseSource();
        PoseFrame a = s.sample(1000, new PoseFrame());
        assertEquals(1000, a.timeMs);
        PoseFrame b = s.sample(1040, new PoseFrame()); // 40 ms later: same frame again
        assertEquals(1000, b.timeMs);
        PoseFrame c = s.sample(1090, new PoseFrame()); // 90 ms later: new frame
        assertEquals(1090, c.timeMs);
        // 12 frames per second over one second
        int frames = 0;
        long last = -1;
        for (long t = 2000; t < 3000; t += 5) {
            PoseFrame f = s.sample(t, new PoseFrame());
            if (f.timeMs != last) { frames++; last = f.timeMs; }
        }
        assertTrue("about 12 fps, got " + frames, frames >= 11 && frames <= 13);
    }

    @Test
    public void sampleWritesIntoTheGivenFrameWithoutAllocatingANewOne() {
        TestPoseSource s = new TestPoseSource();
        PoseFrame dst = new PoseFrame();
        assertSame(dst, s.sample(0, dst));
        assertNotNull(s.sample(100, null));
    }

    @Test
    public void releaseAllClearsHeldKeysAndPointer() {
        TestPoseSource s = new TestPoseSource();
        PoseFrame rest = at(s, 0);
        s.setHeld(TestPoseSource.CTRL_DUCK, true);
        s.setHeld(TestPoseSource.CTRL_ARMS_OUT, true);
        s.setPreset(2, true);
        s.pointerMove(1000, 300);
        s.releaseAll();
        PoseFrame f = null;
        long t = 0;
        for (int i = 0; i < 12; i++) { t += 100; f = s.sample(t, new PoseFrame()); }
        assertEquals(kp(rest, 0, Kp.NOSE).y, kp(f, 0, Kp.NOSE).y, 0.5f);
        assertTrue(kp(f, 0, Kp.R_WRIST).y > kp(f, 0, Kp.R_ELBOW).y);
        assertEquals(rest.players[0].kps[Kp.R_WRIST].x, f.players[0].kps[Kp.R_WRIST].x, 1f);
    }
}
