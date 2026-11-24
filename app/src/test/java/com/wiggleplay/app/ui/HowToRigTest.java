package com.wiggleplay.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HowToRigTest {

    private static HowToRig rig(HowToPose p) {
        HowToRig r = new HowToRig();
        r.compute(p);
        return r;
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    private static final HowToPose[] ALL = {HowToPose.STAND, HowToPose.WHEEL, HowToPose.ARMS_UP, HowToPose.T_POSE,
            HowToPose.LEAN_LEFT, HowToPose.LEAN_RIGHT, HowToPose.JUMP, HowToPose.SQUAT};

    @Test
    public void standingFigureIsAboutOneUnitTallAndSymmetric() {
        HowToRig r = rig(HowToPose.STAND);
        assertEquals(1f, r.headY + HowToRig.HEAD_R, 0.03f);
        assertEquals(0f, r.headX, 1e-5f);
        assertEquals(-r.shLx, r.shRx, 1e-5f);
        assertEquals(-r.haLx, r.haRx, 1e-5f);
        assertEquals(-r.knLx, r.knRx, 1e-5f);
        assertEquals(HowToRig.ANKLE_Y, r.anLy, 1e-5f);
    }

    @Test
    public void everyBoneKeepsItsLengthInEveryPose() {
        for (int i = 0; i < ALL.length; i++) {
            HowToRig r = rig(ALL[i]);
            String m = "pose " + i;
            float kl = 1f - 0.55f * ALL[i].reachL, kr = 1f - 0.55f * ALL[i].reachR;
            if (ALL[i].wheel == 0f) {
                assertEquals(m + " upper arm L", HowToRig.UPPER * kl, dist(r.shLx, r.shLy, r.elLx, r.elLy), 1e-4f);
                assertEquals(m + " forearm L", HowToRig.FORE * kl, dist(r.elLx, r.elLy, r.haLx, r.haLy), 1e-4f);
                assertEquals(m + " upper arm R", HowToRig.UPPER * kr, dist(r.shRx, r.shRy, r.elRx, r.elRy), 1e-4f);
                assertEquals(m + " forearm R", HowToRig.FORE * kr, dist(r.elRx, r.elRy, r.haRx, r.haRy), 1e-4f);
            }
            assertEquals(m + " thigh L", HowToRig.THIGH, dist(r.hpLx, r.hpLy, r.knLx, r.knLy), 1e-3f);
            assertEquals(m + " shin L", HowToRig.SHIN, dist(r.knLx, r.knLy, r.anLx, r.anLy), 1e-3f);
            assertEquals(m + " thigh R", HowToRig.THIGH, dist(r.hpRx, r.hpRy, r.knRx, r.knRy), 1e-3f);
            assertEquals(m + " shin R", HowToRig.SHIN, dist(r.knRx, r.knRy, r.anRx, r.anRy), 1e-3f);
            for (float v : new float[] {r.headX, r.headY, r.elLx, r.elRy, r.haLx, r.haRy, r.knLx, r.knRy}) {
                assertFalse(m + " is finite", Float.isNaN(v) || Float.isInfinite(v));
            }
        }
    }

    @Test
    public void tPoseHasTheHandsOutLevelWithTheShoulders() {
        HowToRig r = rig(HowToPose.T_POSE);
        assertEquals(r.shLy, r.haLy, 0.01f);
        assertEquals(r.shRy, r.haRy, 0.01f);
        assertTrue(r.haLx < -0.3f && r.haRx > 0.3f);
    }

    @Test
    public void armsUpPutsTheHandsAboveTheHead() {
        HowToRig r = rig(HowToPose.ARMS_UP);
        assertTrue(r.haLy > r.headY && r.haRy > r.headY);
        assertTrue("a V: the hands are wider than the shoulders", r.haRx > r.shRx && r.haLx < r.shLx);
    }

    @Test
    public void leaningMovesTheHeadSideways() {
        float left = rig(HowToPose.LEAN_LEFT).headX, right = rig(HowToPose.LEAN_RIGHT).headX;
        assertTrue(left < -0.05f && right > 0.05f);
        assertEquals(-left, right, 1e-5f);
        // the feet stay planted
        assertEquals(rig(HowToPose.STAND).anLx, rig(HowToPose.LEAN_RIGHT).anLx, 0.01f);
    }

    @Test
    public void squatLowersTheHipsAndThrowsTheKneesOut() {
        HowToRig stand = rig(HowToPose.STAND), squat = rig(HowToPose.SQUAT);
        assertTrue(squat.hipY < stand.hipY - 0.15f);
        assertTrue(squat.headY < stand.headY - 0.15f);
        assertTrue("knees out wider than the hips", squat.knLx < squat.hpLx - 0.05f);
        assertTrue("the arms point at the viewer, so they are drawn short",
                dist(squat.shLx, squat.shLy, squat.haLx, squat.haLy) < 0.25f);
    }

    @Test
    public void jumpLiftsTheBodyAndAFootAboveTheGround() {
        HowToRig stand = rig(HowToPose.STAND), jump = rig(HowToPose.JUMP);
        assertTrue(jump.headY > stand.headY - 0.1f + 0.0f && jump.hipY > rig(HowToPose.SQUAT).hipY);
        assertTrue(jump.anLy > 0.1f && jump.anRy > 0.2f);
        assertTrue(jump.haLy > jump.headY);
    }

    @Test
    public void wheelPoseHasBothHandsOnTheRimAndTurnsWithTheWheel() {
        HowToRig r = rig(HowToPose.WHEEL);
        assertEquals(HowToRig.WHEEL_R, dist(r.wheelX, r.wheelY, r.haLx, r.haLy), 1e-4f);
        assertEquals(HowToRig.WHEEL_R, dist(r.wheelX, r.wheelY, r.haRx, r.haRy), 1e-4f);
        assertEquals(r.haLy, r.haRy, 1e-4f);
        assertEquals(HowToRig.UPPER, dist(r.shLx, r.shLy, r.elLx, r.elLy), 1e-3f);
        assertEquals(HowToRig.FORE, dist(r.elLx, r.elLy, r.haLx, r.haLy), 1e-3f);
        assertTrue("the hands are in front of the chest, below the head", r.haLy < r.headY && r.haLy > r.hipY);
        // turn the wheel counter-clockwise by 60 degrees: the screen-left hand goes down, the right hand up
        HowToPose turned = new HowToPose().set(HowToPose.WHEEL);
        turned.wheelDeg = 60f;
        HowToRig t = rig(turned);
        assertTrue(t.haLy < r.haLy - 0.08f && t.haRy > r.haRy + 0.08f);
        assertEquals(HowToRig.WHEEL_R, dist(t.wheelX, t.wheelY, t.haLx, t.haLy), 1e-4f);
        assertEquals(HowToRig.UPPER, dist(t.shLx, t.shLy, t.elLx, t.elLy), 1e-3f);
        assertEquals(HowToRig.FORE, dist(t.elLx, t.elLy, t.haLx, t.haLy), 1e-3f);
    }

    @Test
    public void blendingGlidesTheJointsAndDoesNotJumpBetweenPoses() {
        HowToPose mid = new HowToPose();
        HowToRig prev = null;
        for (int k = 0; k <= 20; k++) {
            mid.lerp(HowToPose.STAND, HowToPose.WHEEL, k / 20f);
            HowToRig r = rig(mid);
            if (prev != null) {
                assertTrue("elbow L step " + k, dist(prev.elLx, prev.elLy, r.elLx, r.elLy) < 0.06f);
                assertTrue("hand R step " + k, dist(prev.haRx, prev.haRy, r.haRx, r.haRy) < 0.06f);
            }
            prev = new HowToRig();
            prev.compute(mid);
        }
        mid.lerp(HowToPose.T_POSE, HowToPose.ARMS_UP, 0.5f);
        assertEquals(120f, mid.shL, 1e-4f);
        assertEquals(0f, new HowToPose().lerp(HowToPose.STAND, HowToPose.JUMP, -3f).lift, 0f);
        assertEquals(0.12f, new HowToPose().lerp(HowToPose.STAND, HowToPose.JUMP, 9f).lift, 1e-6f);
    }

    @Test
    public void swingGoesZeroToOneAndBack() {
        assertEquals(0f, HowToPose.swing(0f, 2f), 1e-6f);
        assertEquals(1f, HowToPose.swing(1f, 2f), 1e-6f);
        assertEquals(0f, HowToPose.swing(2f, 2f), 1e-5f);
    }
}
