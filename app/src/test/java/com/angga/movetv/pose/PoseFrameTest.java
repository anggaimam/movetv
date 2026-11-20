package com.angga.movetv.pose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PoseFrameTest {

    private static PoseFrame sample() {
        PoseFrame f = new PoseFrame();
        f.timeMs = 1234;
        f.mode = 2;
        f.aiFps = 7.5f;
        f.source = "camera";
        for (int n = 0; n < 2; n++) {
            f.players[n].present = n == 0;
            for (int k = 0; k < Kp.COUNT; k++) {
                Keypoint kp = f.players[n].kps[k];
                kp.x = 100 * n + k;
                kp.y = 10 * k;
                kp.s = 0.05f * k;
            }
        }
        return f;
    }

    @Test
    public void kpIndexesFollowCocoOrder() {
        assertEquals(0, Kp.NOSE);
        assertEquals(5, Kp.L_SHOULDER);
        assertEquals(10, Kp.R_WRIST);
        assertEquals(16, Kp.R_ANKLE);
        assertEquals(17, Kp.COUNT);
    }

    @Test
    public void newFrameHasTwoPlayersWithAllKeypoints() {
        PoseFrame f = new PoseFrame();
        assertEquals(2, f.players.length);
        assertEquals(0, f.players[0].id);
        assertEquals(1, f.players[1].id);
        for (PlayerPose p : f.players) {
            assertEquals(Kp.COUNT, p.kps.length);
            for (Keypoint k : p.kps) assertNotNull(k);
        }
    }

    @Test
    public void copyIntoCopiesEverythingIntoGivenFrame() {
        PoseFrame a = sample();
        PoseFrame b = new PoseFrame();
        assertSame(b, a.copyInto(b));
        assertEquals(1234, b.timeMs);
        assertEquals(2, b.mode);
        assertEquals(7.5f, b.aiFps, 0f);
        assertEquals("camera", b.source);
        assertTrue(b.players[0].present);
        assertFalse(b.players[1].present);
        for (int n = 0; n < 2; n++) {
            for (int k = 0; k < Kp.COUNT; k++) {
                assertEquals(a.players[n].kps[k].x, b.players[n].kps[k].x, 0f);
                assertEquals(a.players[n].kps[k].y, b.players[n].kps[k].y, 0f);
                assertEquals(a.players[n].kps[k].s, b.players[n].kps[k].s, 0f);
            }
        }
    }

    @Test
    public void copyIntoIsDeepAndKeepsKeypointObjects() {
        PoseFrame a = sample();
        PoseFrame b = new PoseFrame();
        Keypoint before = b.players[0].kps[3];
        a.copyInto(b);
        assertSame(before, b.players[0].kps[3]);
        assertNotSame(a.players[0].kps[3], b.players[0].kps[3]);
        a.players[0].kps[3].x = -999f;
        assertEquals(3f, b.players[0].kps[3].x, 0f);
    }

    @Test
    public void copyIntoNullCreatesNewFrame() {
        PoseFrame a = sample();
        PoseFrame b = a.copyInto(null);
        assertNotNull(b);
        assertNotSame(a, b);
        assertEquals(a.players[1].kps[16].x, b.players[1].kps[16].x, 0f);
    }

    @Test
    public void okComparesConfidence() {
        Keypoint k = new Keypoint();
        k.s = 0.3f;
        assertTrue(PoseFrame.ok(k, 0.3f));
        assertFalse(PoseFrame.ok(k, 0.31f));
    }

    @Test
    public void distIsStraightLine() {
        Keypoint a = new Keypoint(), b = new Keypoint();
        b.x = 3f;
        b.y = 4f;
        assertEquals(5f, PoseFrame.dist(a, b), 1e-4f);
    }

    @Test
    public void shoulderWidthIsDistanceWhenBothShouldersConfident() {
        PlayerPose p = new PlayerPose(0);
        p.kps[Kp.L_SHOULDER].x = 900f;
        p.kps[Kp.R_SHOULDER].x = 1080f;
        p.kps[Kp.L_SHOULDER].s = 0.9f;
        p.kps[Kp.R_SHOULDER].s = 0.3f;
        assertEquals(180f, PoseFrame.shoulderWidth(p), 1e-3f);
    }

    @Test
    public void shoulderWidthIsZeroWhenEitherShoulderUnsure() {
        PlayerPose p = new PlayerPose(0);
        p.kps[Kp.L_SHOULDER].x = 900f;
        p.kps[Kp.R_SHOULDER].x = 1080f;
        p.kps[Kp.L_SHOULDER].s = 0.9f;
        p.kps[Kp.R_SHOULDER].s = 0.29f;
        assertEquals(0f, PoseFrame.shoulderWidth(p), 0f);
        p.kps[Kp.R_SHOULDER].s = 0.9f;
        p.kps[Kp.L_SHOULDER].s = 0.1f;
        assertEquals(0f, PoseFrame.shoulderWidth(p), 0f);
    }
}
