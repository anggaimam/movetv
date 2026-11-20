package com.angga.movetv.input;

/** One-Euro low-pass filter for a single axis (Casiez et al. 2012). Plain Java. */
public final class OneEuro {
    private final float minCutoff, beta, dCutoff;
    private boolean init;
    private float xPrev, dxPrev;

    /**
     * @param minCutoff cutoff in Hz when the signal is slow
     * @param beta how much the cutoff grows with speed
     * @param dCutoff cutoff in Hz of the speed estimate
     */
    public OneEuro(float minCutoff, float beta, float dCutoff) {
        this.minCutoff = minCutoff;
        this.beta = beta;
        this.dCutoff = dCutoff;
    }

    private static float alpha(float cutoff, float dt) {
        float tau = 1f / (2f * (float) Math.PI * cutoff);
        return 1f / (1f + tau / dt);
    }

    /**
     * Filters one sample.
     * @param x new raw value
     * @param dt seconds since the previous sample (&gt; 0)
     * @return the smoothed value
     */
    public float filter(float x, float dt) {
        if (dt <= 0f) dt = 1f / 60f;
        if (!init) {
            init = true;
            xPrev = x;
            dxPrev = 0f;
            return x;
        }
        float dx = (x - xPrev) / dt;
        float ad = alpha(dCutoff, dt);
        dxPrev = dxPrev + ad * (dx - dxPrev);
        float cutoff = minCutoff + beta * Math.abs(dxPrev);
        float a = alpha(cutoff, dt);
        xPrev = xPrev + a * (x - xPrev);
        return xPrev;
    }

    /** Forgets all history; the next sample passes through unchanged. */
    public void reset() {
        init = false;
    }

    /** @return the last output (0 before the first sample) */
    public float value() {
        return xPrev;
    }
}
