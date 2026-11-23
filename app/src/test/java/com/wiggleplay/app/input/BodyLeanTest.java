package com.wiggleplay.app.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

import org.junit.Test;

import java.util.Random;

public class BodyLeanTest {

    private static final float SW = 200f, SY = 500f, CX = 960f, DT = 1f / 60f;

    private final BodyLean b = new BodyLean();
    private final PlayerPose p = new PlayerPose(0);
    private long t = 1000;

    /** Standing, arms down; the shoulder line tilted by deg (positive = screen-left shoulder lower), dy = crouch. */
    private void stand(float deg, float dy) {
        p.present = true;
        for (int i = 0; i < Kp.COUNT; i++) {
            p.kps[i].x = CX;
            p.kps[i].y = SY + dy;
            p.kps[i].s = 0.9f;
        }
        float rise = (float) Math.tan(Math.toRadians(deg)) * SW / 2f;
        p.kps[Kp.L_SHOULDER].x = CX - SW / 2f;
        p.kps[Kp.L_SHOULDER].y = SY + dy + rise;
        p.kps[Kp.R_SHOULDER].x = CX + SW / 2f;
        p.kps[Kp.R_SHOULDER].y = SY + dy - rise;
        p.kps[Kp.NOSE].y = SY + dy - 0.8f * SW;
        p.kps[Kp.L_WRIST].x = CX - SW / 2f;
        p.kps[Kp.L_WRIST].y = SY + dy + 1.5f * SW;
        p.kps[Kp.R_WRIST].x = CX + SW / 2f;
        p.kps[Kp.R_WRIST].y = SY + dy + 1.5f * SW;
    }

    private void run(float seconds) {
        int n = Math.round(seconds * 60f);
        for (int i = 0; i < n; i++) {
            t += 17;
            b.update(p, t, DT);
        }
    }

    @Test
    public void uprightIsStraightAndActive() {
        stand(0f, 0f);
        run(1f);
        assertEquals(SteerInput.State.ACTIVE, b.steerState());
        assertEquals(0f, b.steer(), 1e-4f);
    }

    @Test
    public void leanLeftSteersLeftLeanRightSteersRight() {
        stand(12f, 0f);
        run(1f);
        assertEquals(-(12f - 4f) / 16f, b.steer(), 0.02f); // -0.5
        assertEquals(12f, b.iconAngleDeg(), 0.3f);
        stand(-25f, 0f);
        run(1f);
        assertEquals(1f, b.steer(), 0.01f);
    }

    @Test
    public void insideTheDeadZoneIsStraight() {
        stand(3.5f, 0f);
        run(1f);
        assertEquals(0f, b.steer(), 1e-4f);
    }

    @Test
    public void crouchDoesNotSteer() {
        stand(0f, 0f);
        run(0.5f);
        float worst = 0f;
        for (int i = 0; i <= 30; i++) { // down 0.6 shoulder widths and back up, both shoulders together
            stand(0f, 0.6f * SW * (float) Math.sin(Math.PI * i / 30.0));
            run(1f / 60f);
            worst = Math.max(worst, Math.abs(b.steer()));
        }
        assertEquals(0f, worst, 1e-4f);
        // a crouch while leaning keeps the same steer
        stand(12f, 0f);
        run(1f);
        float s0 = b.steer();
        stand(12f, 0.6f * SW);
        run(0.5f);
        assertEquals(s0, b.steer(), 0.01f);
    }

    @Test
    public void shouldersLostEaseToStraight() {
        stand(-25f, 0f);
        run(1f);
        p.kps[Kp.L_SHOULDER].s = 0.1f;
        run(0.2f);
        assertEquals(SteerInput.State.NONE, b.steerState());
        assertTrue(b.steer() > 0.4f);
        run(0.3f);
        assertEquals(0f, b.steer(), 1e-4f);
    }

    @Test
    public void jitterOfEightPixelsMovesSteerLittle() {
        Random r = new Random(7);
        float worst = 0f;
        for (int i = 0; i < 240; i++) {
            stand(0f, 0f);
            for (int k = 0; k < Kp.COUNT; k++) {
                p.kps[k].x += (r.nextFloat() * 2f - 1f) * 8f;
                p.kps[k].y += (r.nextFloat() * 2f - 1f) * 8f;
            }
            run(1f / 60f);
            worst = Math.max(worst, Math.abs(b.steer()));
        }
        System.out.println("BodyLean: jitter +-8 px, shoulder width 200: worst steer " + worst);
        assertTrue("jitter steer " + worst, worst <= 0.08f);
    }
}
