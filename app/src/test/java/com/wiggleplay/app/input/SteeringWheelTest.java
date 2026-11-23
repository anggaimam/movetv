package com.wiggleplay.app.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

import org.junit.Test;

import java.util.Random;

public class SteeringWheelTest {

    private static final float SW = 200f, SY = 500f, CX = 960f, DT = 1f / 60f;
    private static final float CHEST = SY + 0.6f * SW; // between the nose (SY - 0.8 SW) and the hips (SY + 1.6 SW)

    private final SteeringWheel w = new SteeringWheel();
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
        p.kps[Kp.NOSE].y = SY - 0.8f * SW;
        p.kps[Kp.L_HIP].x = CX - SW / 3f;
        p.kps[Kp.L_HIP].y = SY + 1.6f * SW;
        p.kps[Kp.R_HIP].x = CX + SW / 3f;
        p.kps[Kp.R_HIP].y = SY + 1.6f * SW;
    }

    /** Hands on the wheel at the chest: spread in shoulder widths, deg = turn (positive = screen-left hand lower). */
    private void wheel(float spread, float deg) {
        body();
        float half = spread * SW / 2f;
        float c = (float) Math.cos(Math.toRadians(deg)), s = (float) Math.sin(Math.toRadians(deg));
        p.kps[Kp.L_WRIST].x = CX - half * c;
        p.kps[Kp.L_WRIST].y = CHEST + half * s;
        p.kps[Kp.R_WRIST].x = CX + half * c;
        p.kps[Kp.R_WRIST].y = CHEST - half * s;
    }

    private void run(float seconds) {
        int n = Math.round(seconds * 60f);
        for (int i = 0; i < n; i++) {
            t += 17;
            w.update(p, t, DT);
        }
    }

    @Test
    public void levelHandsSteerStraight() {
        wheel(1f, 0f);
        run(1f);
        assertEquals(SteerInput.State.ACTIVE, w.steerState());
        assertEquals(0f, w.steer(), 1e-4f);
    }

    @Test
    public void thirtyDegreesEachWay() {
        float want = (30f - 8f) / (60f - 8f); // 0.42
        wheel(1f, 30f);
        run(1.5f);
        assertEquals(-want, w.steer(), 0.02f);
        assertEquals(30f, w.iconAngleDeg(), 0.5f);
        wheel(1f, -30f);
        run(1.5f);
        assertEquals(want, w.steer(), 0.02f);
        wheel(1f, -60f);
        run(1.5f);
        assertEquals(1f, w.steer(), 0.01f);
    }

    @Test
    public void needsHundredFiftyMsToStart() {
        wheel(1f, 40f);
        run(0.1f);
        assertEquals(SteerInput.State.NONE, w.steerState());
        assertEquals(0f, w.steer(), 1e-4f);
        run(0.1f);
        assertEquals(SteerInput.State.ACTIVE, w.steerState());
    }

    @Test
    public void jitterOfEightPixelsWithOneShoulderWidthSpread() {
        Random r = new Random(11);
        float worst = 0f;
        for (int i = 0; i < 300; i++) {
            wheel(1f, 0f);
            for (int k = 0; k < Kp.COUNT; k++) {
                p.kps[k].x += (r.nextFloat() * 2f - 1f) * 8f;
                p.kps[k].y += (r.nextFloat() * 2f - 1f) * 8f;
            }
            run(1f / 60f);
            if (i > 15) worst = Math.max(worst, Math.abs(w.steer()));
        }
        System.out.println("SteeringWheel: jitter +-8 px, spread 1 shoulder width: worst steer " + worst);
        assertEquals(SteerInput.State.ACTIVE, w.steerState());
        assertTrue("jitter steer " + worst, worst <= 0.08f);
    }

    @Test
    public void armsHangingAreNone() {
        body();
        p.kps[Kp.L_WRIST].x = CX - SW / 2f;
        p.kps[Kp.L_WRIST].y = SY + 1.9f * SW; // below the hips
        p.kps[Kp.R_WRIST].x = CX + SW / 2f;
        p.kps[Kp.R_WRIST].y = SY + 1.9f * SW;
        run(1f);
        assertEquals(SteerInput.State.NONE, w.steerState());
        assertEquals(0f, w.steer(), 1e-4f);
    }

    @Test
    public void airplaneWideArmsAreNone() {
        body();
        p.kps[Kp.L_WRIST].x = CX - 2.5f * SW / 2f * 1.0f - 60f; // spread > 2.0 shoulder widths
        p.kps[Kp.L_WRIST].y = SY + 10f;
        p.kps[Kp.R_WRIST].x = CX + 2.5f * SW / 2f + 60f;
        p.kps[Kp.R_WRIST].y = SY + 10f;
        run(1f);
        assertEquals(SteerInput.State.NONE, w.steerState());
    }

    @Test
    public void handsTooCloseHoldThenEaseToStraight() {
        wheel(1f, -60f);
        run(1.5f);
        assertEquals(1f, w.steer(), 0.01f);
        wheel(0.3f, -60f); // the hands come together: the angle is no longer reliable
        run(0.25f);
        assertEquals(SteerInput.State.ACTIVE, w.steerState());
        assertEquals("held", 1f, w.steer(), 0.01f);
        run(0.1f);
        assertEquals(SteerInput.State.NONE, w.steerState());
        run(0.45f);
        assertEquals(0f, w.steer(), 1e-4f);
    }

    @Test
    public void handsCrossedOrAboveEachOtherHold() {
        wheel(1f, 50f);
        run(1.5f);
        float s0 = w.steer();
        assertTrue(s0 < -0.7f);
        wheel(1f, 85f); // nearly vertical: which hand is which can't be told
        run(0.2f);
        assertEquals("held, not flipped", s0, w.steer(), 0.01f);
    }
}
