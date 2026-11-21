package com.wiggleplay.app.core;

/**
 * Easing curves: pure functions that turn progress {@code t} (0..1) into an eased value (0..1, some
 * overshoot). Input outside 0..1 is clamped. Plain Java, no android imports.
 */
public final class Ease {

    private Ease() { }

    private static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    /**
     * Fast start, gentle stop.
     * @param t progress 0..1
     * @return eased value 0..1
     */
    public static float outCubic(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /**
     * Gentle start and stop.
     * @param t progress 0..1
     * @return eased value 0..1
     */
    public static float inOutCubic(float t) {
        t = clamp01(t);
        if (t < 0.5f) return 4f * t * t * t;
        float u = -2f * t + 2f;
        return 1f - u * u * u / 2f;
    }

    /**
     * Overshoots the target a little, then settles (pop-in effects).
     * @param t progress 0..1
     * @return eased value; goes above 1 on the way and ends at exactly 1
     */
    public static float outBack(float t) {
        t = clamp01(t);
        final float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /**
     * Springy wobble around the target (bouncy tiles).
     * @param t progress 0..1
     * @return eased value; wobbles around 1 and ends at exactly 1
     */
    public static float outElastic(float t) {
        t = clamp01(t);
        if (t == 0f) return 0f;
        if (t == 1f) return 1f;
        final float c4 = (float) (2.0 * Math.PI / 3.0);
        return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0);
    }
}
