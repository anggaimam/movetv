package com.wiggleplay.app.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

import org.junit.Test;

import java.util.Random;

public class AirplaneArmsTest {

    private static final float SW = 200f, SY = 500f, CX = 960f;
    private static final float DT = 1f / 60f;

    private final AirplaneArms a = new AirplaneArms();
    private final PlayerPose p = new PlayerPose(0);
    private long t = 1000;

    private void body() {
        p.present = true;
        for (int i = 0; i < Kp.COUNT; i++) {
            p.kps[i].x = CX;
            p.kps[i].y = SY;
            p.kps[i].s = 0.9f;
        }
        p.kps[Kp.L_SHOULDER].x = CX - SW / 2f;
        p.kps[Kp.R_SHOULDER].x = CX + SW / 2f;
    }

    /** Both wrists out: half = half the wrist spread, deg = wing tilt (left hand lower is positive), dy = both up (negative) or down. */
    private void wings(float half, float deg, float dy) {
        body();
        float rise = (float) Math.tan(Math.toRadians(deg)) * half;
        p.kps[Kp.L_WRIST].x = CX - half;
        p.kps[Kp.L_WRIST].y = SY + rise + dy;
        p.kps[Kp.R_WRIST].x = CX + half;
        p.kps[Kp.R_WRIST].y = SY - rise + dy;
    }

    private void run(float seconds) {
        int n = Math.round(seconds * 60f);
        for (int i = 0; i < n; i++) {
            t += 17;
            a.update(p, t, DT);
        }
    }

    @Test
    public void levelTPoseSteersStraight() {
        wings(500f, 0f, 0f);
        run(1f);
        assertEquals(AirplaneArms.State.BOTH, a.state());
        assertEquals(0f, a.steer(), 0.001f);
        assertEquals(0f, a.pitch(), 0.001f);
        assertEquals(0f, a.wingAngleDeg(), 0.01f);
    }

    @Test
    public void leftHandLowerTurnsLeft() {
        wings(500f, 20f, 0f);
        run(1f);
        assertEquals(-14f / 22f, a.steer(), 0.02f); // about -0.64
        assertEquals(20f, a.wingAngleDeg(), 0.5f);
    }

    @Test
    public void rightHandLowerTurnsRightAtFullStrength() {
        wings(500f, -30f, 0f);
        run(1f);
        assertEquals(1f, a.steer(), 0.01f);
    }

    @Test
    public void insideTheDeadZoneIsStraight() {
        wings(500f, 5f, 0f);
        run(1f);
        assertEquals(0f, a.steer(), 0.001f);
    }

    @Test
    public void armsHangingIsNone() {
        wings(150f, 0f, 400f);          // wrists only 1.5 shoulder widths apart, well below the shoulders
        run(1f);
        assertEquals(AirplaneArms.State.NONE, a.state());
        assertEquals(0f, a.steer(), 0.001f);
    }

    @Test
    public void oneArmOutAndTiltedIsOneWingAtEightyPercent() {
        body();
        float x = CX - SW / 2f - 400f;
        p.kps[Kp.L_WRIST].x = x;
        p.kps[Kp.L_WRIST].y = SY + (float) Math.tan(Math.toRadians(20)) * 400f; // 20 degrees below the left shoulder
        p.kps[Kp.R_WRIST].s = 0.1f;
        run(1f);
        assertEquals(AirplaneArms.State.ONE_WING, a.state());
        assertEquals(-0.8f * 14f / 22f, a.steer(), 0.02f);
        assertEquals(20f, a.wingAngleDeg(), 0.5f);
        // the right arm alone, lower: turns right
        body();
        p.kps[Kp.R_WRIST].x = CX + SW / 2f + 400f;
        p.kps[Kp.R_WRIST].y = SY + (float) Math.tan(Math.toRadians(30)) * 400f;
        p.kps[Kp.L_WRIST].s = 0.1f;
        run(1f);
        assertEquals(AirplaneArms.State.ONE_WING, a.state());
        assertEquals(0.8f, a.steer(), 0.02f);
        assertEquals(-30f, a.wingAngleDeg(), 0.5f);
    }

    @Test
    public void aWristCloseToTheBodyIsNotAWing() {
        body();
        p.kps[Kp.L_WRIST].x = CX - 120f; // 0.6 shoulder widths from the middle
        p.kps[Kp.L_WRIST].y = SY + 200f;
        p.kps[Kp.R_WRIST].s = 0.1f;
        run(1f);
        assertEquals(AirplaneArms.State.NONE, a.state());
    }

    @Test
    public void bothNeedsAShortHoldAndLeavesLate() {
        wings(500f, 0f, 0f);
        run(0.1f);
        assertEquals(AirplaneArms.State.NONE, a.state());
        run(0.12f);
        assertEquals(AirplaneArms.State.BOTH, a.state());
        wings(150f, 0f, 400f);
        run(0.2f);
        assertEquals(AirplaneArms.State.BOTH, a.state()); // still inside the 300 ms
        run(0.2f);
        assertEquals(AirplaneArms.State.NONE, a.state());
    }

    @Test
    public void steeringEasesToStraightOverAboutFourTenthsOfASecond() {
        wings(500f, -30f, 0f);
        run(1f);
        assertEquals(1f, a.steer(), 0.01f);
        wings(150f, 0f, 400f);
        run(0.3f + 0.2f);       // 0.3 s until BOTH is dropped, then the ease starts
        assertTrue(a.steer() > 0.4f);
        run(0.5f);
        assertEquals(0f, a.steer(), 0.001f);
    }

    @Test
    public void armsUpAndDownGivePitch() {
        wings(500f, 0f, -160f);   // wrists 0.8 shoulder widths above the shoulders
        run(1f);
        assertEquals(1f, a.pitch(), 0.02f);
        wings(500f, 0f, 160f);
        run(1f);
        assertEquals(-1f, a.pitch(), 0.02f);
        wings(500f, 0f, -20f);    // a little up is inside the dead zone
        run(1f);
        assertEquals(0f, a.pitch(), 0.001f);
    }

    @Test
    public void holdsTheLastValuesWhenAShoulderIsLost() {
        wings(500f, 20f, 0f);
        run(1f);
        float s = a.steer();
        p.kps[Kp.R_SHOULDER].s = 0.1f;
        run(1f);
        assertEquals(s, a.steer(), 0f);
        assertEquals(AirplaneArms.State.BOTH, a.state());
    }

    @Test
    public void twoPlayersCanStandCloserTogether() {
        wings(0.8f * SW, 0f, 0f);       // spread 1.6 shoulder widths
        run(1f);
        assertEquals(AirplaneArms.State.NONE, a.state());
        a.setMinSpread(1.5f);
        run(1f);
        assertEquals(AirplaneArms.State.BOTH, a.state());
    }

    @Test
    public void jitterOfEightPixelsChangesNothing() {
        Random r = new Random(7);
        wings(500f, 0f, 0f);
        float[][] base = new float[Kp.COUNT][2];
        for (int i = 0; i < Kp.COUNT; i++) {
            base[i][0] = p.kps[i].x;
            base[i][1] = p.kps[i].y;
        }
        run(0.5f);
        float worst = 0f;
        for (int f = 0; f < 240; f++) {
            for (int i = 0; i < Kp.COUNT; i++) {
                p.kps[i].x = base[i][0] + (r.nextFloat() * 2f - 1f) * 8f;
                p.kps[i].y = base[i][1] + (r.nextFloat() * 2f - 1f) * 8f;
            }
            t += 17;
            a.update(p, t, DT);
            assertEquals(AirplaneArms.State.BOTH, a.state());
            worst = Math.max(worst, Math.abs(a.steer()));
        }
        assertTrue("steer moved " + worst, worst <= 0.05f);
    }

    @Test
    public void noPersonEasesToStraight() {
        wings(500f, 20f, 0f);
        run(1f);
        p.present = false;
        run(1f);
        assertEquals(AirplaneArms.State.NONE, a.state());
        assertEquals(0f, a.steer(), 0.001f);
    }
}
