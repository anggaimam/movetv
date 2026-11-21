package com.wiggleplay.app.games.fruit;

/**
 * Follows one wrist for slicing games. The camera's pose arrives only 7 to 14 times a second, so the hand glides
 * toward each new point every display frame; each frame's small step is a segment that is tested against fruit
 * (a "swept path"), so a fast swipe cannot jump over a fruit. It also remembers the last 250 ms of positions, so a
 * hand that crossed a fruit just before the fruit was drawn still counts. Plain Java, no allocation after
 * construction.
 */
public final class HandTrack {

    static final int N = 32;
    /** A hit is also counted for a hand that was inside the fruit at any moment in this many ms. */
    public static final long LOOK_BACK_MS = 250;
    /** A swipe counts when it passes within this many radii of the fruit centre. */
    public static final float SWEEP = 1.5f;
    /** Window used to decide whether the hand is moving. */
    public static final long MOVE_WINDOW_MS = 200;

    private final float glideRate;
    private final float[] hx = new float[N], hy = new float[N];
    private final long[] ht = new long[N];
    private int head, count;
    private float x, y, prevX, prevY, dirX = 0f, dirY = -1f;
    private boolean valid, hadPrev;

    /**
     * Creates a tracker.
     * @param glideRate how fast the hand glides to a new pose point (per second, around 14); 0 or less means jump at once
     */
    public HandTrack(float glideRate) {
        this.glideRate = glideRate;
    }

    /**
     * Advances by one display frame.
     * @param dt seconds since the last frame
     * @param isValid whether the wrist is trusted this frame (confidence and presence)
     * @param tx newest wrist x (stage px)
     * @param ty newest wrist y
     * @param nowMs clock in ms
     */
    public void update(float dt, boolean isValid, float tx, float ty, long nowMs) {
        if (!isValid) {
            valid = false;
            hadPrev = false;
            count = 0;
            return;
        }
        if (!valid) {
            x = tx;
            y = ty;
            hadPrev = false;
            count = 0;
        } else {
            prevX = x;
            prevY = y;
            hadPrev = true;
            if (glideRate <= 0f) {
                x = tx;
                y = ty;
            } else {
                float k = 1f - (float) Math.exp(-Math.max(dt, 0.001f) * glideRate);
                x += (tx - x) * k;
                y += (ty - y) * k;
            }
            float dx = x - prevX, dy = y - prevY;
            float len = (float) Math.hypot(dx, dy);
            if (len > 3f) {
                dirX = dx / len;
                dirY = dy / len;
            }
        }
        valid = true;
        hx[head] = x;
        hy[head] = y;
        ht[head] = nowMs;
        head = (head + 1) % N;
        if (count < N) count++;
    }

    /** @return true while the wrist is trusted */
    public boolean valid() { return valid; }

    /** @return current x */
    public float x() { return x; }

    /** @return current y */
    public float y() { return y; }

    /** @return unit vector of the latest movement, x part (default 0) */
    public float dirX() { return dirX; }

    /** @return unit vector of the latest movement, y part (default straight up, -1) */
    public float dirY() { return dirY; }

    /** @return how many positions are remembered */
    public int samples() { return count; }

    /**
     * A remembered position, newest first.
     * @param i 0 (newest) to samples()-1
     * @return x
     */
    public float sampleX(int i) { return hx[(head - 1 - i + 2 * N) % N]; }

    /**
     * A remembered position, newest first.
     * @param i 0 (newest) to samples()-1
     * @return y
     */
    public float sampleY(int i) { return hy[(head - 1 - i + 2 * N) % N]; }

    /**
     * When a remembered position was taken.
     * @param i 0 (newest) to samples()-1
     * @return time in ms
     */
    public long sampleMs(int i) { return ht[(head - 1 - i + 2 * N) % N]; }

    /**
     * Whether the hand slices a circle.
     * @param cx circle centre x
     * @param cy circle centre y
     * @param r the circle's radius
     * @param nowMs clock in ms
     * @param minPath how far the hand must have travelled in the last 200 ms (a resting hand slices nothing)
     * @return true if this frame's swept segment passes within 1.5 r, or the hand was inside r in the last 250 ms
     */
    public boolean hits(float cx, float cy, float r, long nowMs, float minPath) {
        if (!valid || !moving(nowMs, minPath)) return false;
        float lim = SWEEP * r;
        if (hadPrev) {
            if (segDist(prevX, prevY, x, y, cx, cy) <= lim) return true;
        } else if (Math.hypot(x - cx, y - cy) <= lim) {
            return true;
        }
        for (int i = 0; i < count; i++) {
            if (nowMs - sampleMs(i) > LOOK_BACK_MS) break;
            if (Math.hypot(sampleX(i) - cx, sampleY(i) - cy) <= r) return true;
        }
        return false;
    }

    /**
     * Whether the hand is really moving.
     * @param nowMs clock in ms
     * @param minPath least distance in px travelled during the last {@value #MOVE_WINDOW_MS} ms
     * @return true if it travelled at least that far
     */
    public boolean moving(long nowMs, float minPath) {
        float path = 0f;
        for (int i = 0; i + 1 < count; i++) {
            if (nowMs - sampleMs(i) > MOVE_WINDOW_MS) break;
            path += (float) Math.hypot(sampleX(i) - sampleX(i + 1), sampleY(i) - sampleY(i + 1));
        }
        return path >= minPath;
    }

    /**
     * Distance from a point to a segment.
     * @param ax segment start x
     * @param ay segment start y
     * @param bx segment end x
     * @param by segment end y
     * @param px point x
     * @param py point y
     * @return the shortest distance
     */
    public static float segDist(float ax, float ay, float bx, float by, float px, float py) {
        float dx = bx - ax, dy = by - ay;
        float len2 = dx * dx + dy * dy;
        float t = len2 <= 0f ? 0f : ((px - ax) * dx + (py - ay) * dy) / len2;
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        float qx = ax + t * dx, qy = ay + t * dy;
        return (float) Math.hypot(px - qx, py - qy);
    }
}
