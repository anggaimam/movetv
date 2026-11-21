package com.angga.movetv.ui.puppet;

import com.angga.movetv.core.Ease;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The life of one kid puppet: time in, {@link PuppetPose} out. Always running underneath: breathing, a small
 * head bob and tilt, a tiny arm sway, a weight shift every 4 to 6 s, random blinks (every 2.5 to 5.5 s,
 * 15% double) and pupils that follow a look target. On top of that one action at a time: wave, cheer or point;
 * talking moves the mouth. Each kid has its own random numbers and phases, so two kids are never in sync.
 * Plain Java (no Android), so it is unit-tested. Nothing is allocated while running except when a blink
 * starts (one list entry for the tests).
 */
public final class PuppetAnim {

    /** Rest angle of a hanging arm from straight down, in degrees (the drawing has the arms held out a little). */
    public static final float REST_ARM_DEG = 42f;
    /** Upper arm lift of a wave (the arm ends about 110 degrees from hanging). */
    public static final float WAVE_LIFT = 68f;
    /** Forearm bend while waving. */
    public static final float WAVE_BEND = 55f;
    /** Upper arm lift of a cheer (arms up in a V). */
    public static final float CHEER_LIFT = 84f;

    private static final int NONE = 0, WAVE = 1, CHEER = 2, POINT = 3;
    private static final float WAVE_UP = 0.4f, WAVE_DOWN = 0.5f, WAVE_HZ = 1.8f, WAVE_SWINGS = 3.5f;
    private static final float CHEER_LEN = 0.9f, POINT_LEN = 1.2f;
    private static final float BLINK_LEN = 0.2f;

    private final Random rnd;
    private final float p1, p2, p3, p4;
    private double t;

    private int action = NONE;
    private double aStart;
    private float aLen;
    private int aSide;
    private float aAngle;

    private double nextBlink;
    private double blinkStart = -10;
    private boolean doubleDue;
    private final List<Float> blinkStarts = new ArrayList<>();

    private double talkUntil;
    private double mouthToggleAt;
    private boolean mouthOpenNow;

    private double shiftAt;
    private float shiftTarget, shiftNow;
    private float lookX, lookY, lookTx, lookTy;

    /**
     * Creates a kid with its own random timers.
     * @param seed random seed (different for each kid)
     */
    public PuppetAnim(long seed) {
        rnd = new Random(seed);
        p1 = rnd.nextFloat() * 6.283f;
        p2 = rnd.nextFloat() * 6.283f;
        p3 = rnd.nextFloat() * 6.283f;
        p4 = rnd.nextFloat() * 6.283f;
        nextBlink = 1.0 + rnd.nextFloat() * 2.5;
        shiftAt = 2.0 + rnd.nextFloat() * 3.0;
    }

    /** Starts a wave. @param side the arm to wave with: {@link PuppetPose#LEFT} or {@link PuppetPose#RIGHT} (screen side) */
    public void wave(int side) {
        start(WAVE, WAVE_UP + WAVE_SWINGS / WAVE_HZ + WAVE_DOWN, side, 0f);
    }

    /** Starts a cheer: both arms up in a V and a small hop. */
    public void cheer() {
        start(CHEER, CHEER_LEN, 0, 0f);
    }

    /**
     * Starts pointing with one straight arm.
     * @param side the arm to use
     * @param angleFromDown direction of the finger in degrees from straight down (90 = sideways, 180 = straight up)
     */
    public void point(int side, float angleFromDown) {
        start(POINT, POINT_LEN, side, Math.max(REST_ARM_DEG, Math.min(175f, angleFromDown)));
    }

    /**
     * The angle from straight down toward a target, for {@link #point}.
     * @param dx target x minus kid x
     * @param dy target y minus kid y (positive = below)
     * @return degrees 0..180
     */
    public static float angleTo(float dx, float dy) {
        return (float) Math.toDegrees(Math.atan2(Math.abs(dx), dy));
    }

    /** Whether a wave, cheer or point is playing. @return true while an action runs */
    public boolean busy() {
        return action != NONE;
    }

    private void start(int a, float len, int side, float angle) {
        action = a;
        aStart = t;
        aLen = len;
        aSide = side == PuppetPose.RIGHT ? PuppetPose.RIGHT : PuppetPose.LEFT;
        aAngle = angle;
    }

    /**
     * Keeps the mouth moving as if talking.
     * @param seconds how long from now (call again every frame while a voice is speaking)
     */
    public void talk(float seconds) {
        talkUntil = Math.max(talkUntil, t + seconds);
    }

    /**
     * Where the pupils should look.
     * @param x -1 (screen left) .. 1 (screen right)
     * @param y -1 (up) .. 1 (down)
     */
    public void lookAt(float x, float y) {
        lookTx = Math.max(-1f, Math.min(1f, x));
        lookTy = Math.max(-1f, Math.min(1f, y));
    }

    /**
     * Advances time.
     * @param dt seconds since the last call (0 to 0.1)
     */
    public void update(float dt) {
        if (dt <= 0f) return;
        t += Math.min(dt, 0.1f);
        if (action != NONE && t - aStart >= aLen) action = NONE;
        // blinking
        if (t >= nextBlink) {
            blinkStart = t;
            blinkStarts.add((float) t);
            if (doubleDue) {
                doubleDue = false;
                nextBlink = t + 2.5 + rnd.nextFloat() * 3.0;
            } else if (rnd.nextFloat() < 0.15f) {
                doubleDue = true;
                nextBlink = t + 0.28;
            } else {
                nextBlink = t + 2.5 + rnd.nextFloat() * 3.0;
            }
        }
        // weight shift
        if (t >= shiftAt) {
            shiftTarget = (rnd.nextFloat() * 2f - 1f) * 3f;
            shiftAt = t + 4.0 + rnd.nextFloat() * 2.0;
        }
        shiftNow += (shiftTarget - shiftNow) * (1f - (float) Math.exp(-dt * 1.5f));
        float k = 1f - (float) Math.exp(-dt / 0.08f);
        lookX += (lookTx - lookX) * k;
        lookY += (lookTy - lookY) * k;
        // talking mouth
        if (t < talkUntil && t >= mouthToggleAt) {
            mouthOpenNow = !mouthOpenNow;
            float hz = 7f + rnd.nextFloat() * 2f;
            mouthToggleAt = t + 0.5f / hz * (0.8f + rnd.nextFloat() * 0.4f);
        }
    }

    /** Seconds of life so far. @return the clock */
    public double time() {
        return t;
    }

    /** Times (seconds) at which blinks started, oldest first. For tests. @return the list (do not modify) */
    public List<Float> blinkStarts() {
        return blinkStarts;
    }

    /**
     * Writes the current pose.
     * @param o the pose to fill
     */
    public void fill(PuppetPose o) {
        o.reset();
        // idle
        float breathe = 0.5f + 0.5f * (float) Math.sin(6.2832 * 0.25 * t + p1);
        o.breath = 0.012f * breathe;
        o.headDy = 2f * (float) Math.sin(6.2832 * 0.25 * t + p2);
        o.headRot = 1.5f * (float) Math.sin(6.2832 * 0.2 * t + p3);
        o.rootDx = shiftNow;
        o.armLift[0] = 2f * (float) Math.sin(6.2832 * 0.3 * t + p4);
        o.armLift[1] = 2f * (float) Math.sin(6.2832 * 0.3 * t + p4 + 1.7);
        o.pupilX = lookX;
        o.pupilY = lookY;
        // blink
        float b = (float) (t - blinkStart);
        if (b >= 0f && b < BLINK_LEN) {
            if (b < 0.03f) o.eyes = PuppetPose.EYES_HALF;
            else if (b < 0.11f) o.eyes = PuppetPose.EYES_CLOSED;
            else if (b < 0.155f) o.eyes = PuppetPose.EYES_HALF;
        }
        // actions
        if (action != NONE) {
            float a = (float) (t - aStart);
            int s = aSide;
            float dir = s == PuppetPose.LEFT ? -1f : 1f;   // toward the side of the screen of the used arm
            if (action == WAVE) {
                float up = Ease.outCubic(Math.min(1f, a / WAVE_UP));
                float down = a > aLen - WAVE_DOWN ? Ease.outCubic((a - (aLen - WAVE_DOWN)) / WAVE_DOWN) : 0f;
                float env = up * (1f - down);
                float ph = (float) (6.2832 * WAVE_HZ * Math.max(0f, a - WAVE_UP));
                float swing = a > WAVE_UP && down < 1f ? (float) Math.sin(ph) : 0f;
                float swingEnv = Math.min(1f, Math.max(0f, (a - WAVE_UP) / 0.15f)) * (1f - down);
                float follow = a > WAVE_UP ? (float) Math.sin(ph - 3.1416f) : 0f;
                o.armLift[s] = lerp(o.armLift[s], WAVE_LIFT + 5f * follow * swingEnv, env);
                o.foreBend[s] = env * WAVE_BEND + 22f * swing * swingEnv;
                o.headRot += dir * 4f * env;
                o.torsoRot += dir * 2f * env;
                if (swing > 0.2f && swingEnv > 0.5f) o.mouth = PuppetPose.MOUTH_OPEN;
            } else if (action == CHEER) {
                float up = Ease.outCubic(Math.min(1f, a / 0.25f));
                float down = a > aLen - 0.25f ? Ease.outCubic((a - (aLen - 0.25f)) / 0.25f) : 0f;
                float env = up * (1f - down);
                for (int i = 0; i < 2; i++) {
                    o.armLift[i] = lerp(o.armLift[i], CHEER_LIFT, env);
                    o.foreBend[i] = 12f * env;
                }
                float u = a / aLen;
                float hop = u < 0.4f ? Ease.outBack(u / 0.4f) : 1f - Ease.outCubic((u - 0.4f) / 0.6f);
                o.rootDy = -24f * Math.max(0f, hop);
                o.shadowScale = 1f - 0.15f * Math.max(0f, hop);
                o.mouth = PuppetPose.MOUTH_OPEN;
            } else {
                float up = Ease.outCubic(Math.min(1f, a / 0.3f));
                float down = a > aLen - 0.3f ? Ease.outCubic((a - (aLen - 0.3f)) / 0.3f) : 0f;
                float env = up * (1f - down);
                o.armLift[s] = lerp(o.armLift[s], aAngle - REST_ARM_DEG, env);
                o.foreBend[s] = 0f;
                o.torsoRot += dir * 3f * env;
                o.headRot += dir * 2f * env;
            }
        }
        // talking wins the mouth unless the action opened it
        if (t < talkUntil && o.mouth == PuppetPose.MOUTH_SMILE) {
            o.mouth = mouthOpenNow ? PuppetPose.MOUTH_OPEN : PuppetPose.MOUTH_CLOSED;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
