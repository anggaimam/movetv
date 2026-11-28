package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.PlayerPose;

/**
 * The reel gesture: the hand draws circles (a crank). Counts turns, measures the rate, and gives a smooth crank angle
 * for the reel animation and the centre of the circles (the aim while fighting a fish). Plain Java, no Android, no
 * allocation per frame.
 *
 * <ul>
 *   <li>The running centre of the active wrist is an exponential average with a 1.5 s time constant (a plain mean for
 *       the first picture so it settles at once). The angle is measured about that centre and only while the radius
 *       is at least 0.25 sw; it is unwrapped from picture to picture, and a step over 150 degrees is dropped (it is a
 *       jump or a wiggle, not a turn). Either direction counts; 360 degrees of net turning is 1 cycle
 *       ({@link Listener#onCycle}); jitter and wiggling cancel out.</li>
 *   <li><b>Pump fallback</b> (for children who pump the arm up and down): two consecutive vertical legs of at least
 *       {@value #PUMP_LEG} sw with under 0.3 sw of sideways travel count as 1 cycle. The ticket says 0.5 sw; 0.7 is
 *       used so that a gentle wiggle (0.25 sw either way) or the swing of a jump is never a pump.</li>
 *   <li>{@link #rate()}: cycles per second over the last 1.5 s. {@link #crankAngle()}: the unwrapped angle in radians,
 *       extrapolated between pictures and eased toward the measured angle (0.15 s), never moving backwards while the
 *       child keeps turning one way. {@link #centreX()}: the centre of the circles from pairs of pictures half a turn apart (not the slow
 *       centre, which would lag a moving aim), normalised like the cast aim ((x - shoulderMid) / (0.9 sw), -1..+1).</li>
 * </ul>
 */
public final class CycleDetect {

    /** Receives completed cycles. */
    public interface Listener {
        /** One full turn (or one pump) was counted. */
        void onCycle();
    }

    static final float PUMP_LEG = 0.7f;
    private static final float TAU_C = 0.35f, MIN_RADIUS = 0.25f, MAX_STEP = (float) Math.toRadians(150.0);
    private static final float TWO_PI = (float) (2.0 * Math.PI), AIM_DIV = 0.9f;
    private static final float PUMP_BAND = 0.15f, PUMP_SIDE = 0.3f;
    private static final float RATE_S = 1.5f, ANGLE_TAU = 0.15f;
    private static final int CAP = 40;

    private final ActiveWrist aw;
    private final boolean ownsWrist;
    private Listener listener;
    private int lastSwitch = -1;
    // centre and angle
    private boolean haveCentre, haveAng;
    private float cx, cy, prevAng, accum, lastPicT;
    private int centreCount;
    private float measured;          // signed unwrapped angle, radians (turns plus pump cycles)
    private int cycles;
    // pump
    private float pRef, pExt, pLegXMin, pLegXMax;
    private int pDir, pLegs;
    private boolean pInit;
    // progress history for the rate: (time, cycles + |accum| / 2 pi)
    private final float[] progT = new float[CAP], progV = new float[CAP];
    private int progHead, progN;
    // fast centre history
    private final float[] cT = new float[CAP], cX = new float[CAP], cA = new float[CAP];
    private int cHead, cN;
    // animation
    private float visual, omega, lastMeasured, lastPicTime;
    private float rate, centreXv;
    private float dirSign;

    /** Creates a detector with its own {@link ActiveWrist}. */
    public CycleDetect() {
        aw = new ActiveWrist();
        ownsWrist = true;
    }

    /**
     * Creates a detector that reads a shared {@link ActiveWrist}.
     * @param shared the tracker; its owner calls {@code update} first
     */
    public CycleDetect(ActiveWrist shared) {
        aw = shared;
        ownsWrist = false;
    }

    /**
     * Sets who hears the cycles.
     * @param l the listener, or null
     */
    public void setListener(Listener l) {
        listener = l;
    }

    /** Forgets everything including the counters. */
    public void reset() {
        clearTrack();
        cycles = 0;
        measured = 0f;
        visual = 0f;
        omega = 0f;
        lastMeasured = 0f;
        rate = 0f;
        centreXv = 0f;
        progN = 0;
    }

    private void clearTrack() {
        haveCentre = false;
        haveAng = false;
        centreCount = 0;
        accum = 0f;
        pInit = false;
        pLegs = 0;
        pDir = 0;
        cN = 0;
    }

    /**
     * Feeds the newest pose.
     * @param p the pose
     * @param tMs when the pose was measured
     * @param dt seconds since the last call
     */
    public void update(PlayerPose p, long tMs, float dt) {
        if (ownsWrist) aw.update(p, tMs, dt);
        float now = aw.time();
        if (aw.switchCount() != lastSwitch) {
            lastSwitch = aw.switchCount();
            clearTrack();
        }
        if (aw.newPicture()) picture(now);
        animate(now, dt);
        rate = computeRate(now);
        centreXv = computeCentreX(now);
    }

    private void picture(float now) {
        float x = aw.relX(), y = -aw.relY();       // y down, like the screen
        float dtPic = Math.max(0.001f, now - lastPicT);
        lastPicT = now;
        // fast centre history
        cT[cHead] = now;
        cX[cHead] = x;
        cA[cHead] = Float.NaN;       // filled below once this picture's angle step is known
        int myIndex = cHead;
        cHead = (cHead + 1) % CAP;
        if (cN < CAP) cN++;
        // slow centre
        if (!haveCentre) {
            cx = x;
            cy = y;
            centreCount = 1;
            haveCentre = true;
        } else {
            centreCount++;
            float a = Math.max(dtPic / (TAU_C + dtPic), 1f / centreCount);
            cx += (x - cx) * a;
            cy += (y - cy) * a;
        }
        float dx = x - cx, dy = y - cy;
        float r = (float) Math.sqrt(dx * dx + dy * dy);
        if (r >= MIN_RADIUS) {
            float ang = (float) Math.atan2(dy, dx);
            if (haveAng) {
                float d = ang - prevAng;
                while (d > Math.PI) d -= TWO_PI;
                while (d < -Math.PI) d += TWO_PI;
                if (Math.abs(d) <= MAX_STEP) {
                    accum += d;
                    measured += d;
                    while (Math.abs(accum) >= TWO_PI) {
                        accum -= Math.signum(accum) * TWO_PI;
                        count();
                    }
                }
            }
            prevAng = ang;
            haveAng = true;
        } else {
            haveAng = false;
        }
        cA[myIndex] = measured;
        pump(x, y);
        progT[progHead] = now;
        progV[progHead] = cycles + Math.abs(accum) / TWO_PI;
        progHead = (progHead + 1) % CAP;
        if (progN < CAP) progN++;
        // angle speed for the animation
        float step = measured - lastMeasured;
        float w = step / dtPic;
        omega += (w - omega) * 0.3f;
        lastMeasured = measured;
        lastPicTime = now;
        if (Math.abs(step) > 1e-4f) dirSign = Math.signum(step);
    }

    private void count() {
        cycles++;
        if (listener != null) listener.onCycle();
    }

    /** Zig-zag on the height; two long vertical legs with little sideways travel are one pumped cycle. */
    private void pump(float x, float y) {
        if (!pInit) {
            pInit = true;
            pRef = y;
            pExt = y;
            pDir = 0;
            pLegXMin = pLegXMax = x;
            pLegs = 0;
            return;
        }
        pLegXMin = Math.min(pLegXMin, x);
        pLegXMax = Math.max(pLegXMax, x);
        boolean turned = false;
        if (pDir == 0) {
            if (Math.abs(y - pRef) >= PUMP_BAND) {
                pDir = y > pRef ? 1 : -1;
                pExt = y;
            }
        } else if (pDir > 0) {
            if (y > pExt) pExt = y;
            else if (pExt - y >= PUMP_BAND) turned = true;
        } else {
            if (y < pExt) pExt = y;
            else if (y - pExt >= PUMP_BAND) turned = true;
        }
        if (turned) {
            float leg = Math.abs(pExt - pRef);
            boolean good = leg >= PUMP_LEG && (pLegXMax - pLegXMin) < PUMP_SIDE;
            pLegs = good ? pLegs + 1 : 0;
            if (pLegs >= 2) {
                pLegs = 0;
                measured += (dirSign == 0f ? 1f : dirSign) * TWO_PI;
                count();
            }
            pRef = pExt;
            pExt = y;
            pDir = -pDir;
            pLegXMin = pLegXMax = x;
        }
    }

    private void animate(float now, float dt) {
        // no picture for a while: the extrapolation fades out
        float silent = now - lastPicTime;
        float w = silent > 0.5f ? omega * Math.max(0f, 1f - (silent - 0.5f) / 0.3f) : omega;
        float prev = visual;
        float next = visual + w * dt;
        next += (measured - next) * (1f - (float) Math.exp(-dt / ANGLE_TAU));
        float step = next - prev;
        float cap = Math.max(2f * Math.abs(w) * dt, 0.03f);
        if (w > 1e-3f) step = Math.max(0f, Math.min(step, cap));
        else if (w < -1e-3f) step = Math.min(0f, Math.max(step, -cap));
        next = prev + step;
        visual = next;
    }

    private float computeRate(float now) {
        if (progN == 0) return 0f;
        int newest = (progHead - 1 + CAP) % CAP;
        float vNow = progV[newest];
        float vOld = vNow;
        float tOld = progT[newest];
        for (int k = 1; k <= progN; k++) {
            int i = (progHead - k + CAP) % CAP;
            if (now - progT[i] > RATE_S) break;
            vOld = progV[i];
            tOld = progT[i];
        }
        float span = Math.max(0.6f, Math.min(RATE_S, now - tOld));
        float r = (vNow - vOld) / span;
        float idle = now - progT[newest];
        if (idle > 0.5f) r *= Math.max(0f, 1f - (idle - 0.5f) / 1.0f);
        return Math.max(0f, r);
    }

    /**
     * The centre of the circles with as little delay as possible. While turning (rate over 0.7 per second) it averages
     * pairs of pictures half a turn apart in <em>angle</em> (their mean is the centre, and it is delayed by only a
     * quarter turn instead of the half turn of a plain mean over one turn); when the hand is slower it is the mean of
     * the last 0.8 s.
     */
    private float computeCentreX(float now) {
        if (cN == 0) return 0f;
        if (rate > 0.7f && cN >= 6) {
            float sum = 0f;
            int pairs = 0;
            for (int k = 1; k <= 3 && k <= cN; k++) {
                int i = (cHead - k + CAP) % CAP;
                float a1 = cA[i];
                if (Float.isNaN(a1)) continue;
                float x0 = Float.NaN;
                for (int m = k + 1; m <= cN; m++) {
                    int j = (cHead - m + CAP) % CAP;
                    float aj = cA[j];
                    if (Float.isNaN(aj)) break;
                    if (Math.abs(a1 - aj) >= (float) Math.PI) {
                        int jn = (cHead - m + 1 + CAP) % CAP;
                        float an = cA[jn];
                        float want = a1 - (float) Math.PI * Math.signum(a1 - aj);
                        float f = Math.abs(an - aj) > 1e-5f ? (want - aj) / (an - aj) : 0f;
                        f = Math.max(0f, Math.min(1f, f));
                        x0 = cX[j] + (cX[jn] - cX[j]) * f;
                        break;
                    }
                }
                if (Float.isNaN(x0)) break;
                sum += (cX[i] + x0) * 0.5f;
                pairs++;
            }
            if (pairs > 0) return clampAim(sum / pairs / AIM_DIV);
        }
        float sum = 0f;
        int n = 0;
        for (int k = 1; k <= cN; k++) {
            int i = (cHead - k + CAP) % CAP;
            if (now - cT[i] > 0.8f && n >= 3) break;
            sum += cX[i];
            n++;
        }
        return clampAim(sum / n / AIM_DIV);
    }

    private static float clampAim(float v) {
        return v < -1f ? -1f : (v > 1f ? 1f : v);
    }

    /** @return total cycles counted since the last {@link #reset} */
    public int cycleCount() { return cycles; }
    /** @return cycles per second over the last 1.5 s (0 when not turning) */
    public float rate() { return rate; }
    /** @return the crank angle in radians for the reel animation (unwrapped, smooth, monotonic while turning one way) */
    public float crankAngle() { return visual; }
    /** @return the centre of the circles, -1 (left on the screen) to +1, for the aim */
    public float centreX() { return centreXv; }
    /** @return the underlying wrist tracker */
    public ActiveWrist wrist() { return aw; }
}
