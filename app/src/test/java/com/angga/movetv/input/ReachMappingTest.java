package com.angga.movetv.input;

import static org.junit.Assert.*;

import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.PlayerPose;
import org.junit.Test;

public class ReachMappingTest {
    static final float DT = 1f / 60f;

    static void set(PlayerPose p, int k, float x, float y, float s) {
        Keypoint kp = p.kps[k];
        kp.x = x;
        kp.y = y;
        kp.s = s;
    }

    /** Shoulders 160 px apart, right (screen) wrist raised at (wx, wy). */
    static PlayerPose pose(float wx, float wy) {
        PlayerPose p = new PlayerPose(0);
        p.present = true;
        set(p, Kp.NOSE, 960, 300, 0.9f);
        set(p, Kp.L_SHOULDER, 880, 400, 0.9f);
        set(p, Kp.R_SHOULDER, 1040, 400, 0.9f);
        set(p, Kp.L_ELBOW, 860, 520, 0.9f);
        set(p, Kp.R_ELBOW, 1060, 520, 0.9f);
        set(p, Kp.L_WRIST, 850, 640, 0.9f);
        set(p, Kp.R_WRIST, wx, wy, 0.9f);
        set(p, Kp.L_HIP, 900, 700, 0.9f);
        set(p, Kp.R_HIP, 1020, 700, 0.9f);
        return p;
    }

    @Test
    public void gainOneSitsOnTheWrist() {
        ReachMapping m = new ReachMapping(1f);
        float[] o = new float[2];
        m.timing(0, 0);
        assertTrue(m.map(pose(1200, 250), 1, o));
        assertEquals(1200f, o[0], 0.01f);
        assertEquals(250f, o[1], 0.01f);
    }

    @Test
    public void gainReachesFurtherFromTheShoulder() {
        ReachMapping m = new ReachMapping(1.6f);
        float[] o = new float[2];
        m.timing(0, 0);
        assertTrue(m.map(pose(1200, 250), 1, o));
        assertEquals(1040f + 1.6f * 160f, o[0], 0.01f);   // 1296
        assertEquals(400f + 1.6f * -150f, o[1], 0.01f);   // 160
    }

    @Test
    public void clampedToTheStage() {
        ReachMapping m = new ReachMapping(1.6f);
        float[] o = new float[2];
        m.timing(0, 0);
        assertTrue(m.map(pose(1900, 100), 1, o));
        assertEquals(1920f, o[0], 0.01f);
        assertEquals(0f, o[1], 0.01f);
    }

    @Test
    public void leftHandUsesTheLeftShoulder() {
        ReachMapping m = new ReachMapping(2f);
        PlayerPose p = pose(1100, 250);
        set(p, Kp.L_WRIST, 800, 300, 0.9f);
        float[] o = new float[2];
        m.timing(0, 0);
        assertTrue(m.map(p, 0, o));
        assertEquals(880f + 2f * (800f - 880f), o[0], 0.01f);
        assertEquals(400f + 2f * (300f - 400f), o[1], 0.01f);
    }

    @Test
    public void weakOwnShoulderFallsBackToTheMidpoint() {
        ReachMapping m = new ReachMapping(2f);
        PlayerPose p = pose(1200, 250);
        p.kps[Kp.R_SHOULDER].s = 0.1f;
        float[] o = new float[2];
        m.timing(0, 0);
        // only one shoulder is good: neither the own shoulder nor a midpoint exists
        assertFalse(m.map(p, 1, o));
        // own shoulder weak but both... cannot happen; own good, other weak still works
        PlayerPose q = pose(1200, 250);
        q.kps[Kp.L_SHOULDER].s = 0.1f;
        assertTrue(m.map(q, 1, o));
        assertEquals(1040f + 2f * 160f, o[0], 0.01f);
    }

    @Test
    public void noShouldersCannotMap() {
        ReachMapping m = new ReachMapping(1f);
        PlayerPose p = pose(1200, 250);
        p.kps[Kp.L_SHOULDER].s = 0f;
        p.kps[Kp.R_SHOULDER].s = 0f;
        assertFalse(m.map(p, 1, new float[2]));
    }

    /** Moves the wrist along x at vx px/s, a new pose every 100 ms, frames at 60 Hz; returns final time. */
    private static long drive(HandCursor c, float[] wx, long t, float vx, int ms, float wy) {
        long nextPose = t;
        PlayerPose p = pose(wx[0], wy);
        long poseT = t;
        for (long end = t + ms; t < end; t += 17) {
            if (t >= nextPose) {
                wx[0] += vx * 0.1f;
                p = pose(wx[0], wy);
                poseT = t;
                nextPose += 100;
            }
            c.update(DT, p, t, poseT);
        }
        return t;
    }

    @Test
    public void overshootIsSmallAndSettlesAfterTheHandStops() {
        HandCursor c = new HandCursor(0);
        c.setMapping(new ReachMapping(1f));
        float[] wx = {1000f};
        long t = drive(c, wx, 0, 0f, 600, 300);               // hand still, cursor settles
        t = drive(c, wx, t, 400f, 1000, 300);                 // moves right at 400 px/s
        float stopX = wx[0];
        float maxOver = -1e9f;
        long end = t + 700;
        // hand stops: poses keep arriving at the same place
        PlayerPose p = pose(stopX, 300);
        long poseT = t;
        long nextPose = t;
        float lastDelta = 0f;
        long settledAt = -1;
        for (long tt = t; tt < end; tt += 17) {
            if (tt >= nextPose) {
                poseT = tt;
                nextPose += 100;
            }
            c.update(DT, p, tt, poseT);
            maxOver = Math.max(maxOver, c.x() - stopX);
            lastDelta = Math.abs(c.x() - stopX);
            if (lastDelta <= 6f && settledAt < 0) settledAt = tt - t;
            if (lastDelta > 6f) settledAt = -1;
        }
        assertTrue("overshoot " + maxOver, maxOver <= 40f);
        assertTrue("settled at " + settledAt, settledAt >= 0 && settledAt <= 150);
    }

    @Test
    public void staleOrSlowPoseGivesNoLead() {
        ReachMapping m = new ReachMapping(1f);
        float[] o = new float[2];
        // two quick poses 100 ms apart, hand moved 60 px (600 px/s)
        m.timing(0, 0);
        m.map(pose(1000, 300), 1, o);
        for (long t = 1; t <= 100; t += 17) {
            m.timing(100, t);
            m.map(pose(1060, 300), 1, o);
        }
        assertTrue("lead while fast", o[0] > 1060f + 5f);
        assertTrue("lead is capped", o[0] <= 1060f + 0.5f * 160f + 0.01f);
        // the pose is now older than 150 ms: the lead eases out within 100 ms
        for (long t = 101; t <= 400; t += 17) {
            m.timing(100, t);
            m.map(pose(1060, 300), 1, o);
        }
        assertEquals(1060f, o[0], 0.01f);
    }

    @Test
    public void handCursorUsesTheMappingOnTheWristAtGainOne() {
        HandCursor c = new HandCursor(0);
        c.setMapping(new ReachMapping(1f));
        PlayerPose p = pose(1200, 250);
        long t = 0;
        for (int i = 0; i < 120; i++) {
            t += 17;
            c.update(DT, p, t);
        }
        assertTrue(c.visible());
        assertEquals(1200f, c.x(), 2f);
        assertEquals(250f, c.y(), 2f);
        assertEquals(1200f, c.wristX(), 0.01f);
    }

    @Test
    public void defaultMappingIsTheComfortBox() {
        HandCursor a = new HandCursor(0), b = new HandCursor(0);
        b.setMapping(null);
        PlayerPose p = pose(1200, 250);
        long t = 0;
        for (int i = 0; i < 60; i++) {
            t += 17;
            a.update(DT, p, t);
            b.update(DT, p, t);
        }
        assertEquals(a.x(), b.x(), 0.001f);
        assertNotEquals(1200f, a.x(), 50f);
    }

    @Test
    public void armHeldOutLowCountsAsPointingButHangingArmsDoNot() {
        HandCursor c = new HandCursor(0);
        c.setMapping(new ReachMapping(1f));
        // right arm out to the side, wrist below the elbow
        PlayerPose out = pose(1300, 650);
        set(out, Kp.R_ELBOW, 1170, 520, 0.9f);
        long t = 0;
        for (int i = 0; i < 60; i++) {
            t += 17;
            c.update(DT, out, t);
        }
        assertTrue(c.visible());
        assertEquals(650f, c.y(), 3f);

        HandCursor h = new HandCursor(0);
        h.setMapping(new ReachMapping(1f));
        PlayerPose hang = pose(1070, 640);
        for (int i = 0; i < 120; i++) {
            t += 17;
            h.update(DT, hang, t);
            assertFalse(h.visible());
        }
    }
}
