package com.wiggleplay.app.games.bowling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

import org.junit.Test;

import java.util.Random;

/** Synthetic swings at the camera's real rate (about 13 updates a second). */
public class SwingDetectTest {

    private static final float SW = 200f;                 // shoulder width in stage pixels
    private static final float SH_Y = 400f, HIP_Y = 700f, CX = 960f;
    private static final long STEP = 77;                  // ms between pose updates

    private final Random rnd = new Random(1);

    /** A standing body with the right wrist at (dx, y) from the right shoulder; the left arm hangs. */
    private PlayerPose body(float wristDx, float wristY, float jitter, boolean hips) {
        PlayerPose p = new PlayerPose(0);
        p.present = true;
        set(p, Kp.L_SHOULDER, CX - SW / 2f, SH_Y, jitter);
        set(p, Kp.R_SHOULDER, CX + SW / 2f, SH_Y, jitter);
        set(p, Kp.NOSE, CX, SH_Y - 150f, jitter);
        if (hips) {
            set(p, Kp.L_HIP, CX - SW * 0.4f, HIP_Y, jitter);
            set(p, Kp.R_HIP, CX + SW * 0.4f, HIP_Y, jitter);
        }
        set(p, Kp.L_WRIST, CX - SW / 2f - 20f, HIP_Y + 60f, jitter);   // hanging, still
        set(p, Kp.R_WRIST, CX + SW / 2f + wristDx, wristY, jitter);
        return p;
    }

    private void set(PlayerPose p, int k, float x, float y, float jitter) {
        p.kps[k].x = x + (rnd.nextFloat() - 0.5f) * 2f * jitter;
        p.kps[k].y = y + (rnd.nextFloat() - 0.5f) * 2f * jitter;
        p.kps[k].s = 0.9f;
    }

    /**
     * Holds the arm back and down for a while, then swings it forward and up to above the shoulder.
     * @param riseSeconds how long the swing up takes (shorter is faster)
     * @return the time of the release (ms from the start of the swing up), or -1 for none
     */
    private long swing(SwingDetect d, float riseSeconds, boolean hips, float sideways) {
        long t = 0;
        for (int i = 0; i < 6; i++, t += STEP) {   // swung back, low
            if (d.update(body(-40f, HIP_Y + 80f, 3f, hips), t)) return -2;
        }
        float fromY = HIP_Y + 80f, toY = SH_Y - 0.3f * SW;
        long start = t;
        int steps = Math.max(1, Math.round(riseSeconds * 1000f / STEP));
        for (int i = 1; i <= steps + 4; i++, t += STEP) {
            float k = Math.min(1f, i / (float) steps);
            float y = fromY + (toY - fromY) * k;
            if (d.update(body(-40f + sideways * k, y, 3f, hips), t)) return t - start;
        }
        return -1;
    }

    @Test
    public void aNormalSwingReleases() {
        SwingDetect d = new SwingDetect();
        long at = swing(d, 0.35f, true, 0f);
        assertTrue("released at " + at, at > 0 && at <= 400);
        assertEquals(1, d.side());
        assertTrue(d.power() > 0f);
    }

    @Test
    public void aFasterSwingIsStronger() {
        SwingDetect slow = new SwingDetect(), fast = new SwingDetect();
        assertTrue(swing(slow, 0.6f, true, 0f) > 0);
        assertTrue(swing(fast, 0.25f, true, 0f) > 0);
        assertTrue(fast.power() > slow.power());
    }

    @Test
    public void itWorksWithoutHips() {
        SwingDetect d = new SwingDetect();
        assertTrue(swing(d, 0.35f, false, 0f) > 0);
    }

    @Test
    public void armsHangingStillNeverRelease() {
        SwingDetect d = new SwingDetect();
        for (long t = 0; t < 10_000; t += STEP) {
            assertFalse(d.update(body(10f, HIP_Y + 60f, 8f, true), t));
        }
    }

    @Test
    public void wavingOverheadNeverReleases() {
        SwingDetect d = new SwingDetect();
        for (long t = 0; t < 6_000; t += STEP) {
            float y = SH_Y - 1.2f * SW + (float) Math.sin(t / 120.0) * 0.5f * SW;
            assertFalse(d.update(body((float) Math.cos(t / 120.0) * 100f, y, 5f, true), t));
        }
    }

    @Test
    public void aSlowLiftDoesNotRelease() {
        SwingDetect d = new SwingDetect();
        long t = 0;
        for (int i = 0; i < 6; i++, t += STEP) d.update(body(0f, HIP_Y + 80f, 3f, true), t);
        float y = HIP_Y + 80f;
        while (y > SH_Y - SW) {                        // about 0.8 shoulder widths a second
            y -= 0.8f * SW * STEP / 1000f;
            t += STEP;
            assertFalse(d.update(body(0f, y, 3f, true), t));
        }
    }

    @Test
    public void theSamePointsTwiceCountOnce() {
        SwingDetect d = new SwingDetect();
        PlayerPose low = body(0f, HIP_Y + 80f, 0f, true);
        for (int i = 0; i < 50; i++) assertFalse(d.update(low, 0));
        assertEquals(SwingDetect.DOWN, d.state(1));
    }

    @Test
    public void sidewaysWristGivesSpin() {
        SwingDetect straight = new SwingDetect(), curved = new SwingDetect();
        assertTrue(swing(straight, 0.35f, true, 0f) > 0);
        assertTrue(swing(curved, 0.35f, true, 400f) > 0);
        assertEquals(0f, straight.spin(), 0f);
        assertTrue(curved.spin() > 0f);
    }

    @Test
    public void nothingWhenNobodyIsThere() {
        SwingDetect d = new SwingDetect();
        PlayerPose p = new PlayerPose(0);
        assertFalse(d.update(p, 0));
        assertFalse(d.update(null, 77));
    }

    /** One picture at the bottom, then a quick swing up (what a fast, fluid kid swing looks like at 13 pictures a second). */
    private long fluid(SwingDetect d, int steps, boolean lostOne) {
        long t = 0;
        for (int i = 0; i < 6; i++, t += STEP) d.update(body(0f, HIP_Y + 60f, 3f, true), t);   // hanging
        // swing back: one picture low behind the hip
        if (d.update(body(-40f, HIP_Y + 110f, 3f, true), t)) return -2;
        t += STEP;
        float fromY = HIP_Y + 110f, toY = SH_Y - 0.3f * SW;
        long start = t;
        for (int i = 1; i <= steps + 3; i++, t += STEP) {
            float k = Math.min(1f, i / (float) steps);
            PlayerPose p = body(-40f, fromY + (toY - fromY) * k, 3f, true);
            if (lostOne && i == 2) p.kps[Kp.R_WRIST].s = 0.1f;       // the arm blurs for one picture
            if (d.update(p, t)) return t - start;
        }
        return -1;
    }

    @Test
    public void aFastFluidSwingWithOneDownPictureStillReleases() {
        for (int steps = 2; steps <= 4; steps++) {
            SwingDetect d = new SwingDetect();
            long at = fluid(d, steps, false);
            assertTrue("steps " + steps + " released at " + at, at > 0 && at <= 500);
        }
    }

    @Test
    public void aBlurredPictureInTheMiddleOfAFastSwingIsForgiven() {
        SwingDetect d = new SwingDetect();
        long at = fluid(d, 4, true);
        assertTrue("released at " + at, at > 0 && at <= 600);
    }

    @Test
    public void oneWildPointDoesNotReleaseABall() {
        // arms hanging, and the tracker throws the wrist high for a single picture, even above the shoulder
        for (float spikeY : new float[] {HIP_Y - 100f, SH_Y + 60f, SH_Y - 100f}) {
            SwingDetect d = new SwingDetect();
            long t = 0;
            for (int i = 0; i < 10; i++, t += STEP) assertFalse(d.update(body(0f, HIP_Y + 80f, 3f, true), t));
            assertFalse(d.update(body(0f, spikeY, 3f, true), t));
            t += STEP;
            for (int i = 0; i < 10; i++, t += STEP) assertFalse("spike " + spikeY, d.update(body(0f, HIP_Y + 80f, 3f, true), t));
        }
    }

    @Test
    public void aLittleLiftOfTheHandDoesNotReleaseABall() {
        // the hand comes up a little and stays near the hip (a gesture, or adjusting): nothing rolls
        SwingDetect d = new SwingDetect();
        long t = 0;
        for (int i = 0; i < 8; i++, t += STEP) assertFalse(d.update(body(0f, HIP_Y + 80f, 3f, true), t));
        float[] ys = {HIP_Y + 30f, HIP_Y - 10f, HIP_Y - 35f, HIP_Y - 40f, HIP_Y - 40f, HIP_Y - 30f};
        for (float y : ys) {
            assertFalse("y " + y, d.update(body(0f, y, 3f, true), t));
            t += STEP;
        }
    }

    @Test
    public void theReasonAndRiseAreReported() {
        SwingDetect d = new SwingDetect();
        assertTrue(swing(d, 0.35f, true, 0f) > 0);
        assertTrue(d.reason() == 'C' || d.reason() == 'S');
        assertTrue(d.riseAtRelease() >= SwingDetect.MIN_RISE);
    }
}
