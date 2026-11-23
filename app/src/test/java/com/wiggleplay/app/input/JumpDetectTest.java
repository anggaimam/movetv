package com.wiggleplay.app.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

import org.junit.Test;

import java.util.Random;

public class JumpDetectTest {

    private static final float W = 200f;          // shoulder width in px
    private static final float SH = 500f;         // shoulder line when standing
    private static final float NOSE = 380f;       // nose when standing

    private final JumpDetect d = new JumpDetect();
    private final PlayerPose p = new PlayerPose(0);
    private long t = 1000;

    private void pose(float shY, float noseY, float width) {
        p.present = true;
        for (int i = 0; i < Kp.COUNT; i++) {
            p.kps[i].x = 960f;
            p.kps[i].y = shY + 300f;
            p.kps[i].s = 0.9f;
        }
        p.kps[Kp.L_SHOULDER].x = 960f - width / 2f;
        p.kps[Kp.R_SHOULDER].x = 960f + width / 2f;
        p.kps[Kp.L_SHOULDER].y = shY;
        p.kps[Kp.R_SHOULDER].y = shY;
        p.kps[Kp.NOSE].y = noseY;
    }

    /** Feeds poses at 12 Hz for the given time with the body at the given heights; returns true if a jump event came. */
    private boolean hold(float seconds, float shY, float noseY) {
        boolean jumped = false;
        int n = Math.round(seconds * 12f);
        for (int i = 0; i < n; i++) {
            t += 83;
            pose(shY, noseY, W);
            d.update(p, t);
            if (d.consumeJump()) jumped = true;
        }
        return jumped;
    }

    private void calibrate() {
        hold(2.3f, SH, NOSE);
        assertTrue(d.calibrated());
    }

    /** A hop: shoulders (and head) rise by riseWidths shoulder widths over about 250 ms, then come back down. */
    private int hop(float riseWidths) {
        int events = 0;
        float[] k = {0.3f, 0.8f, 1f, 0.7f, 0.2f, 0f};
        for (float f : k) {
            t += 83;
            pose(SH - riseWidths * W * f, NOSE - riseWidths * W * f, W);
            d.update(p, t);
            if (d.consumeJump()) events++;
        }
        hold(0.6f, SH, NOSE);
        return events;
    }

    @Test
    public void nothingIsReportedBeforeCalibration() {
        assertFalse(d.calibrated());
        assertEquals(0f, d.scale(), 0f);
        hold(1.0f, SH, NOSE);
        assertFalse(d.calibrated());
        assertEquals(0.5f, d.calibrationProgress(), 0.1f);
        hold(0.5f, SH - 150f, NOSE - 150f);   // a jump during calibration is not reported
        assertFalse(d.consumeJump());
    }

    @Test
    public void calibratesAfterTwoStillSeconds() {
        calibrate();
        assertEquals(W, d.scale(), 1f);
        assertEquals(1f, d.calibrationProgress(), 0f);
    }

    @Test
    public void movingAboutRestartsTheTwoSeconds() {
        // swaying up and down by a whole shoulder width never counts as standing still
        for (int i = 0; i < 40; i++) {
            t += 83;
            pose(SH - (i % 2 == 0 ? 120f : 0f), NOSE, W);
            d.update(p, t);
        }
        assertFalse(d.calibrated());
        hold(5f, SH, NOSE);
        assertTrue(d.calibrated());
    }

    @Test
    public void aHopIsOneJumpAndTwoHopsAreTwo() {
        calibrate();
        assertEquals(1, hop(0.55f));
        assertTrue(d.peakRise() >= JumpDetect.JUMP_RISE);
        assertFalse(d.jumping());
        assertEquals(1, hop(0.55f));
    }

    @Test
    public void aSmallBounceBelowTheThresholdIsNotAJump() {
        calibrate();
        assertEquals(0, hop(0.25f));
    }

    @Test
    public void bigJumpHasAHighPeak() {
        calibrate();
        assertEquals(1, hop(0.8f));
        assertTrue(d.peakRise() >= JumpDetect.BIG_RISE);
    }

    @Test
    public void aMediumHopIsNotBig() {
        calibrate();
        assertEquals(1, hop(0.42f));
        assertTrue(d.peakRise() < JumpDetect.BIG_RISE);
    }

    @Test
    public void crouchingIsADuckWhileHeldOnly() {
        calibrate();
        assertFalse(d.ducking());
        hold(0.6f, SH + 0.5f * W, NOSE + 0.8f * W);
        assertTrue(d.ducking());
        assertFalse(d.consumeJump());
        hold(0.6f, SH, NOSE);
        assertFalse(d.ducking());
        assertFalse(d.consumeJump());          // standing up again is not a jump
    }

    @Test
    public void aLeanOfHalfAShoulderWidthIsNotADuck() {
        calibrate();
        hold(1f, SH, NOSE + 0.45f * W);
        assertFalse(d.ducking());
    }

    @Test
    public void jitterOfTenPixelsNeverTriggersAnythingInAMinute() {
        calibrate();
        Random r = new Random(7);
        boolean jumped = false, ducked = false;
        for (int i = 0; i < 12 * 60; i++) {
            t += 83;
            float n1 = (r.nextFloat() * 2f - 1f) * 10f, n2 = (r.nextFloat() * 2f - 1f) * 10f;
            pose(SH + n1, NOSE + n2, W + (r.nextFloat() * 2f - 1f) * 10f);
            d.update(p, t);
            jumped |= d.consumeJump();
            ducked |= d.ducking();
        }
        assertFalse(jumped);
        assertFalse(ducked);
    }

    @Test
    public void slowWalkTowardTheCameraIsTolerated() {
        calibrate();
        boolean jumped = false, ducked = false;
        // 30 s: shoulders drift up 90 px, the nose drifts 60 px down, the child gets 40% bigger
        int n = 12 * 30;
        for (int i = 0; i < n; i++) {
            t += 83;
            float f = i / (float) n;
            pose(SH - 90f * f, NOSE + 60f * f, W * (1f + 0.4f * f));
            d.update(p, t);
            jumped |= d.consumeJump();
            ducked |= d.ducking();
        }
        assertFalse(jumped);
        assertFalse(ducked);
        // and a real hop from the new place still works
        assertTrue(d.scale() > W * 1.2f);
    }

    @Test
    public void aRepeatedPoseWithTheSameTimeIsIgnored() {
        calibrate();
        pose(SH - 0.6f * W, NOSE - 0.6f * W, W);
        t += 83;
        d.update(p, t);
        d.update(p, t);
        d.update(p, t);
        assertTrue(d.consumeJump());
        assertFalse(d.consumeJump());
    }

    @Test
    public void whenThePersonLeavesForThreeSecondsItMeasuresAgain() {
        calibrate();
        p.present = false;
        for (int i = 0; i < 12 * 4; i++) {
            t += 83;
            d.update(p, t);
        }
        assertFalse(d.calibrated());
        calibrate();
    }

    @Test
    public void lowConfidenceNoseNeverDucks() {
        calibrate();
        t += 83;
        pose(SH, NOSE + W, W);
        p.kps[Kp.NOSE].s = 0.1f;
        d.update(p, t);
        assertFalse(d.ducking());
    }

    // ---- T-20: the race options ----

    /** Both arms going up: the shoulders rise a little (riseWidths shoulder widths over about 250 ms), the nose stays still. */
    private int armsUp(float riseWidths) {
        int events = 0;
        float[] k = {0.3f, 0.8f, 1f, 0.7f, 0.2f, 0f};
        for (float f : k) {
            t += 83;
            pose(SH - riseWidths * W * f, NOSE, W);
            d.update(p, t);
            if (d.consumeJump()) events++;
        }
        hold(0.6f, SH, NOSE);
        return events;
    }

    @Test
    public void liftingTheArmsIsAJumpOnlyWithoutTheHeadRiseOption() {
        calibrate();
        assertEquals(1, armsUp(0.4f));          // Jungle Dash behaviour is unchanged (option off by default)
        d.setRequireHeadRise(true);
        assertEquals(0, armsUp(0.4f));          // shoulders rise 0.4 widths, nose still: no jump
    }

    @Test
    public void aRealHopStillJumpsWithTheHeadRiseOption() {
        calibrate();
        d.setRequireHeadRise(true);
        assertEquals(1, hop(0.55f));
        assertEquals(1, hop(0.55f));
    }

    @Test
    public void crouchingKeepsHoldingForAShortWhileAfterStandingUp() {
        calibrate();
        assertFalse(d.crouching());
        hold(0.6f, SH + 0.5f * W, NOSE + 0.8f * W);
        assertTrue(d.crouching());
        t += 83;
        pose(SH, NOSE, W);
        d.update(p, t);
        assertFalse(d.ducking());
        assertTrue(d.crouching());              // 0 ms after standing up
        t += 83;
        d.update(pose2(), t);
        assertTrue(d.crouching());              // 83 ms
        t += 83;
        d.update(pose2(), t);
        assertFalse(d.crouching());             // 166 ms
    }

    private PlayerPose pose2() {
        pose(SH, NOSE, W);
        return p;
    }
}
