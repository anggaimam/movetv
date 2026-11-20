package com.angga.movetv.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * The little performance readout in the top-left corner: draw fps (1 second average), worst frame time
 * (5 second window), AI fps and the quality level. Angga photographs it on the TV.
 * Toggle with remote key P or by holding the left arrow for 2 s; it is on by default in debuggable builds.
 * The text is rebuilt twice a second, not every frame.
 */
public final class PerfOverlay {

    private static final long TEXT_EVERY_MS = 500;

    private boolean visible;
    private final StringBuilder sb = new StringBuilder(96);
    private String text = "";
    private long textAt = 0;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint();

    // frame timing
    private long lastNanos = 0;
    private long secStartNanos = 0;
    private int secFrames = 0;
    private float secWorstMs = 0f;
    private float fps = 0f;
    private final float[] worstBuckets = new float[5]; // worst frame of each of the last 5 seconds
    private int bucketIdx = 0;
    private float worstMs = 0f;

    /**
     * Creates the overlay.
     * @param visible whether it starts visible
     */
    public PerfOverlay(boolean visible) {
        this.visible = visible;
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(34f);
        panelPaint.setColor(Color.argb(170, 0, 0, 0));
    }

    /**
     * Shows or hides the overlay.
     * @param v true to show
     */
    public void setVisible(boolean v) { visible = v; }

    /** Shows the overlay if hidden, hides it if shown. */
    public void toggle() { visible = !visible; }

    /**
     * Whether the overlay is showing.
     * @return true if visible
     */
    public boolean isVisible() { return visible; }

    /**
     * Draw frames per second, averaged over the last full second.
     * @return fps (0 until one second has passed)
     */
    public float fps() { return fps; }

    /**
     * The slowest frame of the last 5 seconds.
     * @return milliseconds
     */
    public float worstMs() { return worstMs; }

    /** Forgets the timing so far (after the app was paused, so the pause does not count as slow frames). */
    public void reset() {
        lastNanos = 0;
        secStartNanos = 0;
        secFrames = 0;
        secWorstMs = 0f;
    }

    /**
     * Call once at the start of every drawn frame.
     * @param nowNanos a monotonic time in nanoseconds (SystemClock.elapsedRealtimeNanos())
     * @return true when a new 1-second average has just been computed (about once a second)
     */
    public boolean onFrame(long nowNanos) {
        boolean newSecond = false;
        if (lastNanos != 0) {
            float ms = (nowNanos - lastNanos) / 1_000_000f;
            if (ms > secWorstMs) secWorstMs = ms;
        }
        lastNanos = nowNanos;
        if (secStartNanos == 0) secStartNanos = nowNanos;
        secFrames++;
        long elapsed = nowNanos - secStartNanos;
        if (elapsed >= 1_000_000_000L) {
            fps = secFrames * 1_000_000_000f / elapsed;
            worstBuckets[bucketIdx] = secWorstMs;
            bucketIdx = (bucketIdx + 1) % worstBuckets.length;
            float w = 0f;
            for (float b : worstBuckets) if (b > w) w = b;
            worstMs = w;
            secFrames = 0;
            secWorstMs = 0f;
            secStartNanos = nowNanos;
            newSecond = true;
        }
        return newSecond;
    }

    /**
     * Draws the overlay in stage coordinates (does nothing when hidden).
     * @param c canvas in stage coordinates
     * @param nowMs current time (SystemClock.uptimeMillis()), used to refresh the text twice a second
     * @param aiFps the AI's checks per second, from the pose frame
     * @param quality GameView.HIGH or GameView.LOW
     */
    public void draw(Canvas c, long nowMs, float aiFps, int quality) {
        if (!visible) return;
        if (nowMs - textAt >= TEXT_EVERY_MS) {
            textAt = nowMs;
            sb.setLength(0);
            sb.append((int) (fps + 0.5f)).append(" fps  worst ").append((int) (worstMs + 0.5f))
                    .append(" ms  AI ").append((int) (aiFps + 0.5f)).append(" fps  ")
                    .append(quality == GameView.LOW ? "LOW" : "HIGH");
            text = sb.toString();
        }
        c.drawRect(0f, 0f, 760f, 52f, panelPaint);
        c.drawText(text, 16f, 38f, textPaint);
    }
}
