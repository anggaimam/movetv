package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChaseCameraTest {
    private static final float DT = 1f / 60f;

    @Test public void snapPutsTheCameraBehindAndAbove() {
        ChaseCamera c = new ChaseCamera();
        c.snap(10f, 20f, 0f);
        assertEquals(10f, c.eye()[0], 1e-4f);
        assertEquals(3.2f, c.eye()[1], 1e-4f);
        assertEquals(20f - 6.5f, c.eye()[2], 1e-4f);
        assertEquals(20f + 4f, c.target()[2], 1e-4f);
        assertEquals(0.8f, c.target()[1], 1e-4f);
        assertEquals(60f, c.fov(), 1e-4f);
    }

    @Test public void followsATruckDrivingStraightAndSettlesBehindIt() {
        ChaseCamera c = new ChaseCamera();
        c.snap(0f, 0f, 0f);
        float z = 0f;
        for (int i = 0; i < 600; i++) {
            z += 11f * DT;
            c.update(DT, 0f, z, 0f, false);
        }
        // eased: it trails the ideal spot by speed / rate (about 2.2 units at 11 u/s), never ahead of it
        float lag = (z - 6.5f) - c.eye()[2];
        assertTrue("lag " + lag, lag > 1.5f && lag < 3f);
        assertEquals(0f, c.eye()[0], 1e-3f);
    }

    @Test public void easesInsteadOfJumpingWhenTheTruckTurns() {
        ChaseCamera c = new ChaseCamera();
        c.snap(0f, 0f, 0f);
        c.update(DT, 0f, 0f, (float) Math.PI / 2f, false); // the truck suddenly points along +X
        assertTrue("after one frame the camera heading has hardly moved: " + c.yaw(), c.yaw() > 0f && c.yaw() < 0.12f);
        for (int i = 0; i < 300; i++) c.update(DT, 0f, 0f, (float) Math.PI / 2f, false);
        assertEquals((float) Math.PI / 2f, c.yaw(), 0.01f);
        assertEquals(-6.5f, c.eye()[0], 0.05f); // now behind the truck along -X
    }

    @Test public void takesTheShortWayRoundAtTheWrapPoint() {
        ChaseCamera c = new ChaseCamera();
        c.snap(0f, 0f, 3.0f);
        c.update(DT, 0f, 0f, -3.0f, false); // 3.0 and -3.0 are 0.28 rad apart through pi
        assertTrue("short way: " + c.yaw(), Math.abs(c.yaw() - 3.0f) < 0.05f);
    }

    @Test public void fovGrowsInThreeTenthsOfASecondDuringABoostAndComesBack() {
        ChaseCamera c = new ChaseCamera();
        c.snap(0f, 0f, 0f);
        for (int i = 0; i < 9; i++) c.update(DT, 0f, 0f, 0f, true); // 0.15 s
        assertTrue("halfway " + c.fov(), c.fov() > 62f && c.fov() < 66f);
        for (int i = 0; i < 12; i++) c.update(DT, 0f, 0f, 0f, true); // 0.35 s in all
        assertEquals(68f, c.fov(), 1e-4f);
        for (int i = 0; i < 30; i++) c.update(DT, 0f, 0f, 0f, false);
        assertEquals(60f, c.fov(), 1e-4f);
    }
}
