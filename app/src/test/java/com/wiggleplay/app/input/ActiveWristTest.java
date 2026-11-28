package com.wiggleplay.app.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;
import org.junit.Test;

public class ActiveWristTest {
    private static int run(boolean rightMoves) {
        PlayerPose p = new PlayerPose(0);
        ActiveWrist a = new ActiveWrist();
        int kp = rightMoves ? Kp.R_WRIST : Kp.L_WRIST;
        for (int i = 0; i < 240; i++) {
            float t = i / 60f;
            PoseGen.body(p, 0f);
            p.kps[kp].y = PoseGen.SY + 1.2f * PoseGen.SW - 0.8f * PoseGen.SW * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * 0.7 * t));
            a.update(p, (long) (t * 1000), 1f / 60f);
        }
        return a.switchCount();
    }

    @Test
    public void picksMovingHandWithoutThrashing() {
        assertTrue("right switches " + run(true), run(true) <= 2);
        assertTrue("left switches " + run(false), run(false) <= 2);
    }

    @Test
    public void lockHoldsSlot() {
        PlayerPose p = new PlayerPose(0);
        ActiveWrist a = new ActiveWrist();
        a.lock(true);
        PoseGen.body(p, 0f);
        a.update(p, 0L, 1f / 60f);
        int side = a.side(), n = a.switchCount();
        for (int i = 1; i < 60; i++) {
            PoseGen.body(p, 0f);
            a.update(p, i * 16L, 1f / 60f);
        }
        assertEquals(side, a.side());
        assertEquals(n, a.switchCount());
    }
}
