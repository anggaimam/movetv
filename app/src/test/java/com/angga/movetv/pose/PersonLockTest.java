package com.angga.movetv.pose;

import static org.junit.Assert.*;

import org.junit.Test;

public class PersonLockTest {

    /** A standing person with shoulders sw apart, shoulder midpoint at (mx, my). */
    static float[][] person(float mx, float my, float sw) {
        float[] xs = new float[17], ys = new float[17], sc = new float[17];
        for (int k = 0; k < 17; k++) {
            xs[k] = mx;
            ys[k] = my + 100;
            sc[k] = 0.9f;
        }
        xs[Kp.L_SHOULDER] = mx - sw / 2f;
        xs[Kp.R_SHOULDER] = mx + sw / 2f;
        ys[Kp.L_SHOULDER] = my;
        ys[Kp.R_SHOULDER] = my;
        return new float[][] {xs, ys, sc};
    }

    static boolean offer(PersonLock l, float[][] p, long t) {
        return l.offer(p[0], p[1], p[2], t);
    }

    private static PersonLock fresh() {
        PersonLock l = new PersonLock();
        l.setEnabled(true);
        return l;
    }

    @Test
    public void offStateAcceptsEverything() {
        PersonLock l = new PersonLock();
        assertEquals(PersonLock.State.NONE, l.state());
        assertTrue(offer(l, person(300, 200, 80), 0));
        assertEquals(PersonLock.State.NONE, l.state());
    }

    @Test
    public void firstGoodPoseLocks() {
        PersonLock l = fresh();
        assertEquals(PersonLock.State.SEARCHING, l.state());
        assertTrue(offer(l, person(300, 200, 80), 0));
        assertEquals(PersonLock.State.LOCKED, l.state());
    }

    @Test
    public void poseWithTooFewPointsDoesNotLock() {
        PersonLock l = fresh();
        float[][] p = person(300, 200, 80);
        for (int k = 0; k < 17; k++) if (k != Kp.L_SHOULDER && k != Kp.R_SHOULDER && k > 3) p[2][k] = 0f;
        assertFalse(offer(l, p, 0));
        assertEquals(PersonLock.State.SEARCHING, l.state());
        float[][] q = person(300, 200, 80);
        q[2][Kp.L_SHOULDER] = 0.1f;   // no shoulder width
        assertFalse(offer(l, q, 10));
        assertEquals(PersonLock.State.SEARCHING, l.state());
    }

    @Test
    public void keepsThroughNormalMovesAndAFastArmWave() {
        PersonLock l = fresh();
        offer(l, person(300, 200, 80), 0);
        // a jump: shoulders rise 0.9 shoulder widths in one update
        assertTrue(offer(l, person(300, 128, 80), 80));
        // sway to the side by 0.8 shoulder widths
        assertTrue(offer(l, person(364, 128, 80), 160));
        // a fast arm wave moves wrists only; shoulders stay
        float[][] wave = person(364, 128, 80);
        wave[0][Kp.R_WRIST] += 300;
        wave[1][Kp.R_WRIST] -= 200;
        assertTrue(offer(l, wave, 240));
        assertEquals(0, l.rejections());
        assertEquals(PersonLock.State.LOCKED, l.state());
    }

    @Test
    public void rejectsASwitchToSomeoneOneAndAHalfShoulderWidthsAway() {
        PersonLock l = fresh();
        offer(l, person(300, 200, 80), 0);
        assertFalse(offer(l, person(300 + 1.5f * 80, 200, 80), 100));
        assertEquals(1, l.rejections());
        // the real person is still there afterwards
        assertTrue(offer(l, person(305, 200, 80), 200));
        assertEquals(PersonLock.State.LOCKED, l.state());
    }

    @Test
    public void rejectsASizeJumpWithAMove() {
        PersonLock l = fresh();
        offer(l, person(300, 200, 80), 0);
        // twice as big (someone close to the camera) and 0.7 shoulder widths away
        assertFalse(offer(l, person(300 + 0.7f * 80, 200, 160), 100));
        assertEquals(1, l.rejections());
        // a size change on its own (stepping closer) is fine
        assertTrue(offer(l, person(300, 200, 130), 200));
    }

    @Test
    public void releasesAfterTwoSecondsWithoutAnAcceptedPose() {
        PersonLock l = fresh();
        offer(l, person(300, 200, 80), 0);
        l.tick(1900);
        assertEquals(PersonLock.State.LOCKED, l.state());
        l.tick(2000);
        assertEquals(PersonLock.State.SEARCHING, l.state());
        // someone else now takes over
        assertTrue(offer(l, person(900, 300, 100), 2100));
        assertEquals(PersonLock.State.LOCKED, l.state());
    }

    @Test
    public void keepsRejectingUntilTheTimeoutThenLocksTheNewPerson() {
        PersonLock l = fresh();
        offer(l, person(300, 200, 80), 0);
        long t = 0;
        boolean takenOver = false;
        for (int i = 0; i < 40; i++) {
            t += 100;
            if (offer(l, person(900, 200, 80), t)) {
                takenOver = true;
                break;
            }
        }
        assertTrue(takenOver);
        assertTrue("took over at " + t, t >= 2000 && t <= 2100);
        assertTrue(l.rejections() >= 19);
    }

    @Test
    public void releaseLockHandsOverAtOnce() {
        PersonLock l = fresh();
        offer(l, person(300, 200, 80), 0);
        l.release();
        assertEquals(PersonLock.State.SEARCHING, l.state());
        assertTrue(offer(l, person(900, 300, 100), 10));
        assertEquals(PersonLock.State.LOCKED, l.state());
    }

    @Test
    public void twoPlayersSwitchesItOff() {
        PersonLock l = fresh();
        offer(l, person(300, 200, 80), 0);
        l.setEnabled(false);
        assertEquals(PersonLock.State.NONE, l.state());
        l.release();
        assertEquals(PersonLock.State.NONE, l.state());
        l.setEnabled(true);
        assertEquals(PersonLock.State.SEARCHING, l.state());
    }
}
