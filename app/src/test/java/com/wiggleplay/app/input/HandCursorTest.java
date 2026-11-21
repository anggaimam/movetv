package com.wiggleplay.app.input;

import static org.junit.Assert.*;

import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;
import org.junit.Test;

public class HandCursorTest {
    static final float DT = 1f / 60f;

    /** Person facing the screen: shoulders 160 px apart around x=960, arms hanging. */
    static PlayerPose standing() {
        PlayerPose p = new PlayerPose(0);
        p.present = true;
        set(p, Kp.NOSE, 960, 300);
        set(p, Kp.L_SHOULDER, 880, 400);
        set(p, Kp.R_SHOULDER, 1040, 400);
        set(p, Kp.L_ELBOW, 860, 520);
        set(p, Kp.R_ELBOW, 1060, 520);
        set(p, Kp.L_WRIST, 850, 640);
        set(p, Kp.R_WRIST, 1070, 640);
        set(p, Kp.L_HIP, 900, 700);
        set(p, Kp.R_HIP, 1020, 700);
        return p;
    }

    static void set(PlayerPose p, int k, float x, float y) {
        Keypoint kp = p.kps[k];
        kp.x = x;
        kp.y = y;
        kp.s = 0.9f;
    }

    /** Runs frames; returns the final time. */
    static long run(HandCursor c, PlayerPose p, long t, int ms) {
        int frames = Math.round(ms / (DT * 1000f));
        for (int i = 0; i < frames; i++) {
            t += Math.round(DT * 1000f);
            c.update(DT, p, t);
        }
        return t;
    }

    @Test
    public void hangingArmsNeverShowCursor() {
        HandCursor c = new HandCursor(0);
        PlayerPose p = standing();
        long t = 0;
        for (int i = 0; i < 300; i++) {
            t += 17;
            c.update(DT, p, t);
            assertFalse(c.visible());
        }
        assertEquals(-1, c.hand());
    }

    @Test
    public void lowConfidenceWristIsNotRaised() {
        HandCursor c = new HandCursor(0);
        PlayerPose p = standing();
        set(p, Kp.L_WRIST, 850, 380);
        p.kps[Kp.L_WRIST].s = 0.2f;
        run(c, p, 0, 500);
        assertFalse(c.visible());
    }

    @Test
    public void raisedLeftWristShowsLeftHand() {
        HandCursor c = new HandCursor(0);
        PlayerPose p = standing();
        set(p, Kp.L_WRIST, 850, 380);
        run(c, p, 0, 100);
        assertTrue(c.visible());
        assertEquals(0, c.hand());
    }

    @Test
    public void cursorDisappearsAfter400msWithoutRaisedHand() {
        HandCursor c = new HandCursor(0);
        PlayerPose up = standing();
        set(up, Kp.R_WRIST, 1070, 380);
        long t = run(c, up, 0, 300);
        assertTrue(c.visible());
        assertEquals(1, c.hand());
        PlayerPose down = standing();
        t = run(c, down, t, 300);
        assertTrue("still visible at 300 ms", c.visible());
        run(c, down, t, 200);
        assertFalse(c.visible());
        assertEquals(-1, c.hand());
    }

    @Test
    public void mappingCornersAndClamp() {
        PlayerPose p = standing();
        // right hand: box centre x = 960 + 0.35*160 = 1016, width 416 -> 808..1224; top = 300-192 = 108; bottom = 700
        set(p, Kp.R_WRIST, 1016, 404);           // centre of box (108+700)/2 = 404
        HandCursor c = new HandCursor(0);
        run(c, p, 0, 1500);
        assertEquals(960f, c.x(), 2f);
        assertEquals(540f, c.y(), 2f);

        set(p, Kp.R_WRIST, 5000, -50);           // way outside -> clamped to top-right corner
        run(c, p, 2000, 3000);
        assertEquals(1920f, c.x(), 2f);
        assertEquals(0f, c.y(), 2f);
    }

    @Test
    public void regionScalesOutput() {
        PlayerPose p = standing();
        set(p, Kp.R_WRIST, 1016, 404);
        HandCursor c = new HandCursor(0);
        c.setRegion(960, 0, 960, 1080);
        run(c, p, 0, 1500);
        assertEquals(1440f, c.x(), 2f);
        assertEquals(540f, c.y(), 2f);
    }

    @Test
    public void leftHandBoxIsShiftedLeft() {
        PlayerPose p = standing();
        // left hand: centre x = 960 - 56 = 904. wrist at 904 -> middle of the screen.
        set(p, Kp.L_WRIST, 904, 404);
        HandCursor c = new HandCursor(0);
        run(c, p, 0, 1500);
        assertEquals(0, c.hand());
        assertEquals(960f, c.x(), 2f);
    }

    @Test
    public void hipsMissingUseShoulderBasedBottom() {
        PlayerPose p = standing();
        p.kps[Kp.L_HIP].s = 0f;
        p.kps[Kp.R_HIP].s = 0f;
        // bottom = 400 + 2*160 = 720; box 108..720 -> centre y 414
        set(p, Kp.R_WRIST, 1016, 414);
        HandCursor c = new HandCursor(0);
        run(c, p, 0, 1500);
        assertEquals(540f, c.y(), 2f);
    }

    @Test
    public void zeroShoulderWidthFreezesCursor() {
        PlayerPose p = standing();
        set(p, Kp.R_WRIST, 1016, 404);
        HandCursor c = new HandCursor(0);
        long t = run(c, p, 0, 1000);
        float x = c.x(), y = c.y();
        p.kps[Kp.L_SHOULDER].s = 0f;
        set(p, Kp.R_WRIST, 1200, 200);
        run(c, p, t, 200);
        assertEquals(x, c.x(), 0.001f);
        assertEquals(y, c.y(), 0.001f);
    }

    @Test
    public void keepsCurrentHandUnlessOtherIs60pxHigherFor300ms() {
        PlayerPose p = standing();
        set(p, Kp.L_WRIST, 850, 400);
        set(p, Kp.R_WRIST, 1070, 380);    // only 20 px higher
        HandCursor c = new HandCursor(0);
        // start with only the left hand up so it becomes the current hand
        PlayerPose first = standing();
        set(first, Kp.L_WRIST, 850, 400);
        long t = run(c, first, 0, 100);
        assertEquals(0, c.hand());
        t = run(c, p, t, 1000);
        assertEquals("20 px higher is not enough", 0, c.hand());

        set(p, Kp.R_WRIST, 1070, 330);    // 70 px higher
        t = run(c, p, t, 200);
        assertEquals("not yet 300 ms", 0, c.hand());
        run(c, p, t, 200);
        assertEquals(1, c.hand());
    }

    @Test
    public void switchTimerResetsWhenRightHandDropsBack() {
        PlayerPose first = standing();
        set(first, Kp.L_WRIST, 850, 400);
        HandCursor c = new HandCursor(0);
        long t = run(c, first, 0, 100);
        PlayerPose both = standing();
        set(both, Kp.L_WRIST, 850, 400);
        set(both, Kp.R_WRIST, 1070, 320);
        t = run(c, both, t, 200);
        set(both, Kp.R_WRIST, 1070, 380);
        t = run(c, both, t, 100);
        set(both, Kp.R_WRIST, 1070, 320);
        t = run(c, both, t, 200);
        assertEquals("timer restarted, only 200 ms again", 0, c.hand());
    }

    @Test
    public void smoothingStepResponseIsFast() {
        // Step the wrist across the comfort zone; measure how long the filtered cursor needs to cover 90%.
        PlayerPose p = standing();
        set(p, Kp.R_WRIST, 1016, 404);
        HandCursor c = new HandCursor(0);
        long t = run(c, p, 0, 1000);
        float x0 = c.x();
        set(p, Kp.R_WRIST, 1016 + 80, 404);     // 80 px sideways = about 370 px on screen
        float target = 960 + 80f / 416f * 1920f;
        int frames = 0;
        while (Math.abs(c.x() - x0) < 0.9f * (target - x0) && frames < 120) {
            t += 17;
            c.update(DT, p, t);
            frames++;
        }
        float ms = frames * DT * 1000f;
        System.out.println("One-Euro 90% step response: " + ms + " ms (" + frames + " frames)");
        assertTrue("took " + ms + " ms", ms <= 150f);
    }

    @Test
    public void jitterIsSmoothedWhenSlow() {
        PlayerPose p = standing();
        set(p, Kp.R_WRIST, 1016, 404);
        HandCursor c = new HandCursor(0);
        long t = run(c, p, 0, 1000);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < 120; i++) {
            set(p, Kp.R_WRIST, 1016 + (i % 2 == 0 ? 1.5f : -1.5f), 404);
            t += 17;
            c.update(DT, p, t);
            min = Math.min(min, c.x());
            max = Math.max(max, c.x());
        }
        // raw jitter would be about 13 px peak-to-peak on screen
        assertTrue("jitter range " + (max - min), max - min < 6f);
    }

    @Test
    public void pointerStaysOnTheHandThatReachesDownWhileTheOtherArmIsBent() {
        HandCursor c = new HandCursor(0);
        PlayerPose p = standing();
        set(p, Kp.R_WRIST, 1070, 380);           // right hand up: it becomes the pointer
        long t = run(c, p, 0, 300);
        assertEquals(1, c.hand());
        set(p, Kp.R_WRIST, 1180, 700);           // the right hand goes down and out to a low button
        set(p, Kp.L_WRIST, 850, 500);            // the left forearm is bent, wrist above its elbow but below the shoulder
        t = run(c, p, t, 1500);
        assertTrue(c.visible());
        assertEquals("pointer must stay on the hand that is pointing", 1, c.hand());
    }

    @Test
    public void anotherHandTakesOverOnlyWhenClearlyRaisedAboveItsShoulder() {
        HandCursor c = new HandCursor(0);
        PlayerPose p = standing();
        set(p, Kp.R_WRIST, 1180, 700);
        set(p, Kp.R_ELBOW, 1100, 560);
        set(p, Kp.R_WRIST, 1070, 380);
        long t = run(c, p, 0, 300);
        assertEquals(1, c.hand());
        set(p, Kp.L_WRIST, 850, 250);            // left hand high above its shoulder, 130 px above the right
        t = run(c, p, t, 500);
        assertEquals(0, c.hand());
    }

    @Test
    public void aRestingArmStillNeverCountsEvenAfterTheHandWasTracked() {
        HandCursor c = new HandCursor(0);
        PlayerPose up = standing();
        set(up, Kp.R_WRIST, 1070, 380);
        long t = run(c, up, 0, 300);
        assertEquals(1, c.hand());
        run(c, standing(), t, 600);              // arm hangs next to the body again
        assertFalse(c.visible());
    }
}
