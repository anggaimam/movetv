package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.pose.PoseFrame;

/**
 * Chooses which wrist is "the rod hand" so either hand works at any time (Gone Fishing). Plain Java, no Android, no
 * allocation per frame.
 *
 * <p>Call {@link #update} every display frame with the newest pose. A new <em>picture</em> is detected when {@code tMs}
 * (the time the pose was measured) changes; between pictures the last values are held. Wrists are told apart by
 * <b>screen position</b>, not by their left/right label: when both are confident the one further left on the screen is
 * the {@link #LEFT} slot. When the hands cross, each keeps its slot (the pairing that moved least since the last picture wins). The active wrist is the one whose vertical path over the last 1.0 s is longer. To stop two
 * moving hands from flip-flopping the other wrist must lead for 0.4 s; a resting hand is replaced at once (the other
 * wrist has moved at least 0.2 sw while the active one moved under 0.05 sw; steps under 0.15 sw are camera noise and do not count as movement). One lost wrist hands over to the other.
 * Shoulder width 0 (a lost shoulder) holds the last body reference.
 *
 * <p>Pose coordinates: stage pixels (1920x1080), mirrored like a mirror, y grows downward. All distances in the
 * {@code rel*} accessors are in shoulder widths (sw) relative to the shoulder midpoint.
 */
public final class ActiveWrist {

    /** Slot of the wrist that is further left on the screen. */
    public static final int LEFT = 0;
    /** Slot of the wrist that is further right on the screen. */
    public static final int RIGHT = 1;

    private static final float MIN_CONF = 0.3f;
    private static final float WINDOW_S = 1.0f, HYST_S = 0.4f;
    private static final float IDLE_MOVE = 0.05f, BUSY_MOVE = 0.2f, LEAD_MARGIN = 0.1f;
    /** Picture-to-picture steps smaller than this (sw) are camera noise and add nothing to a wrist's movement (+/-8 px noise on the wrist and on both shoulders gives steps up to 0.14). */
    private static final float NOISE_STEP = 0.15f;
    private static final float SW_ALPHA = 0.15f;   // per picture: a time constant of about 0.8 s at 8 Hz
    private static final int CAP = 24;

    private final OneEuro fx = new OneEuro(1.2f, 0.02f, 1.0f), fy = new OneEuro(1.2f, 0.02f, 1.0f);
    private final float[][] histT = new float[2][CAP], histY = new float[2][CAP];
    private final int[] histHead = new int[2], histN = new int[2];
    private final float[] sx = new float[2], sy = new float[2];
    private final boolean[] present = new boolean[2];

    private double time;
    private boolean hasPic, hasBody, newPic, hasValue, prevBoth;
    private long lastTMs, pictureMs;
    private float midX, midY, sw = 1f, noseY;
    private int active = RIGHT, switches;
    private double leadSince = -1;
    private boolean locked;
    private float rawX, rawY, outX, outY;

    /** Starts with no hand chosen. */
    public ActiveWrist() { }

    /** Forgets everything. */
    public void reset() {
        time = 0;
        hasPic = hasBody = newPic = hasValue = prevBoth = false;
        lastTMs = pictureMs = 0;
        histHead[0] = histHead[1] = histN[0] = histN[1] = 0;
        present[0] = present[1] = false;
        active = RIGHT;
        leadSince = -1;
        fx.reset();
        fy.reset();
        switches++;
    }

    /**
     * Feeds the newest pose. Call once per display frame.
     * @param p the player's pose (may be null)
     * @param tMs when the pose was measured, in milliseconds; a different value means a new picture
     * @param dt seconds since the last call
     */
    public void update(PlayerPose p, long tMs, float dt) {
        time += dt;
        newPic = false;
        if (p != null && p.present) {
            float w = PoseFrame.shoulderWidth(p);
            Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
            if (w > 1f) {
                // the shoulder width is smoothed over several pictures: its +/-8 px noise would otherwise move a far-away wrist by 0.1 sw
                if (!hasBody) sw = w;
                else if (!hasPic || tMs != lastTMs) sw += (w - sw) * SW_ALPHA;
                midX = (ls.x + rs.x) * 0.5f;
                midY = (ls.y + rs.y) * 0.5f;
                hasBody = true;
            }
            Keypoint nose = p.kps[Kp.NOSE];
            if (PoseFrame.ok(nose, MIN_CONF)) noseY = nose.y;
            if (hasBody && (!hasPic || tMs != lastTMs)) picture(p, tMs);
        }
        if (hasValue) {
            outX = fx.filter(rawX, dt);
            outY = fy.filter(rawY, dt);
        }
    }

    private void picture(PlayerPose p, long tMs) {
        hasPic = true;
        lastTMs = tMs;
        pictureMs = tMs;
        Keypoint a = p.kps[Kp.L_WRIST], b = p.kps[Kp.R_WRIST];
        boolean oa = PoseFrame.ok(a, MIN_CONF), ob = PoseFrame.ok(b, MIN_CONF);
        prevBoth = present[0] && present[1];
        present[0] = present[1] = false;
        if (oa && ob) {
            Keypoint l = a.x <= b.x ? a : b, r = a.x <= b.x ? b : a;
            if (prevBoth) {
                // keep each hand in its slot when they cross: take the pairing that moved least since the last picture
                float keep = Math.abs(l.x - sx[LEFT]) + Math.abs(l.y - sy[LEFT]) + Math.abs(r.x - sx[RIGHT]) + Math.abs(r.y - sy[RIGHT]);
                float swap = Math.abs(r.x - sx[LEFT]) + Math.abs(r.y - sy[LEFT]) + Math.abs(l.x - sx[RIGHT]) + Math.abs(l.y - sy[RIGHT]);
                if (swap < keep) {
                    Keypoint t = l;
                    l = r;
                    r = t;
                }
            }
            set(LEFT, l);
            set(RIGHT, r);
        } else if (oa || ob) {
            Keypoint k = oa ? a : b;
            set(k.x < midX ? LEFT : RIGHT, k);
        } else {
            return;     // no wrist: hold everything
        }
        newPic = true;
        for (int s = 0; s < 2; s++) if (present[s]) push(s, (midY - sy[s]) / sw);
        int other = 1 - active;
        if (!hasValue) {
            active = present[LEFT] && present[RIGHT] ? (sy[LEFT] < sy[RIGHT] ? LEFT : RIGHT) : (present[LEFT] ? LEFT : RIGHT);
            hasValue = true;
            switches++;
        } else if (!present[active]) {
            active = other;
            switches++;
            fx.reset();
            fy.reset();
            leadSince = -1;
        } else if (present[other] && !locked) {
            float ma = movement(active), mo = movement(other);
            if (mo > ma + LEAD_MARGIN) {
                if (ma < IDLE_MOVE && mo > BUSY_MOVE) {
                    switchTo(other);
                } else {
                    if (leadSince < 0) leadSince = time;
                    if (time - leadSince >= HYST_S) switchTo(other);
                }
            } else {
                leadSince = -1;
            }
        }
        rawX = sx[active];
        rawY = sy[active];
    }

    private void switchTo(int s) {
        active = s;
        switches++;
        leadSince = -1;
        fx.reset();
        fy.reset();
    }

    private void set(int slot, Keypoint k) {
        present[slot] = true;
        sx[slot] = k.x;
        sy[slot] = k.y;
    }

    private void push(int s, float relY) {
        int h = histHead[s];
        histT[s][h] = (float) time;
        histY[s][h] = relY;
        histHead[s] = (h + 1) % CAP;
        if (histN[s] < CAP) histN[s]++;
    }

    /** Vertical path length (sw) of one slot over the last {@link #WINDOW_S} seconds. */
    private float movement(int s) {
        float path = 0f, prev = Float.NaN;
        int n = histN[s];
        for (int k = n; k >= 1; k--) {
            int i = (histHead[s] - k + CAP * 2) % CAP;
            if (time - histT[s][i] > WINDOW_S) continue;
            if (!Float.isNaN(prev)) path += Math.max(0f, Math.abs(histY[s][i] - prev) - NOISE_STEP);
            prev = histY[s][i];
        }
        return path;
    }

    /**
     * Keeps the current wrist while a gesture is in progress (a cast being charged): the other hand cannot take over,
     * unless the active wrist is lost.
     * @param on true to lock
     */
    public void lock(boolean on) {
        locked = on;
    }

    /** @return true during the {@link #update} call in which a new picture with a usable wrist arrived */
    public boolean newPicture() { return newPic; }
    /** @return seconds since the last {@link #reset}, advanced by {@code dt} (the clock of all the detectors) */
    public float time() { return (float) time; }
    /** @return the {@code tMs} of the newest picture */
    public long pictureMs() { return pictureMs; }
    /** @return true once a wrist has been seen */
    public boolean valid() { return hasValue; }
    /** @return {@link #LEFT} or {@link #RIGHT}: the screen side of the active wrist */
    public int side() { return active; }
    /** @return counts up every time the active wrist changes or the tracker is reset; detectors clear their history on a change */
    public int switchCount() { return switches; }
    /** @return the One-Euro filtered x of the active wrist in stage px (minCutoff 1.2 Hz, beta 0.02) */
    public float x() { return outX; }
    /** @return the One-Euro filtered y of the active wrist in stage px */
    public float y() { return outY; }
    /** @return x of the active wrist in the newest picture (stage px) */
    public float rawX() { return rawX; }
    /** @return y of the active wrist in the newest picture (stage px) */
    public float rawY() { return rawY; }
    /** @return the active wrist's height above the shoulder midpoint in sw in the newest picture (up is positive) */
    public float relY() { return (midY - rawY) / sw; }
    /** @return the active wrist's x relative to the shoulder midpoint in sw (right on the screen is positive) */
    public float relX() { return (rawX - midX) / sw; }
    /** @return shoulder midpoint x in stage px */
    public float shoulderMidX() { return midX; }
    /** @return shoulder midpoint y in stage px */
    public float shoulderMidY() { return midY; }
    /** @return shoulder width in stage px (the last good value) */
    public float sw() { return sw; }
    /** @return the nose height above the shoulder midpoint in sw (up is positive) */
    public float noseRelY() { return (midY - noseY) / sw; }
}
