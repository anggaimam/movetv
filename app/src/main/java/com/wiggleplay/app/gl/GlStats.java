package com.wiggleplay.app.gl;

/**
 * Speed numbers of the GL view. The GL thread writes them once per rendered frame; any thread may read
 * them (the values are volatile). The numbers are CPU time on the GL thread (the time spent issuing the
 * draw calls); the GPU time is not included, so a small time with a low fps means the GPU is the limit.
 */
public final class GlStats {

    private volatile float fps;
    private volatile float ms;
    private volatile int triangles;
    private volatile int draws;
    private volatile float frameMinMs;
    private volatile float frameMaxMs;
    private volatile String config = "";

    // GL thread only
    private long windowStart;
    private int windowFrames;
    private long windowBusy;
    private long lastFrameNanos;
    private long windowMinGap = Long.MAX_VALUE;
    private long windowMaxGap;

    /**
     * Frames rendered per second, averaged over the last full second.
     * @return fps (0 until one second has passed)
     */
    public float fps() { return fps; }

    /**
     * Average time the GL thread spent per frame issuing draw calls, over the last full second.
     * @return milliseconds
     */
    public float ms() { return ms; }

    /**
     * The shortest time between two rendered frames in the last full second (frame pacing).
     * @return milliseconds
     */
    public float frameMinMs() { return frameMinMs; }

    /**
     * The longest time between two rendered frames in the last full second (frame pacing).
     * @return milliseconds
     */
    public float frameMaxMs() { return frameMaxMs; }

    /**
     * Triangles drawn in the last frame (all viewports).
     * @return triangle count
     */
    public int triangles() { return triangles; }

    /**
     * Draw calls issued in the last frame (all viewports).
     * @return draw call count
     */
    public int draws() { return draws; }

    /**
     * The EGL surface configuration in use, for example {@code 888 d24 msaa4} (HIGH) or {@code r5g6b5 d16}; when no wanted
     * configuration was offered, it contains {@code FALLBACK} and lists what was offered.
     * @return a short text, or "" before the surface exists
     */
    public String config() { return config; }

    void setConfig(String text) {
        config = text;
    }

    /**
     * Called by the GL thread at the end of every frame.
     * @param nowNanos a monotonic time in nanoseconds
     * @param busyNanos how long this frame's draw code took
     * @param tris triangles drawn this frame
     * @param drawCalls draw calls issued this frame
     */
    void frameDone(long nowNanos, long busyNanos, int tris, int drawCalls) {
        triangles = tris;
        draws = drawCalls;
        if (windowStart == 0) windowStart = nowNanos;
        windowFrames++;
        windowBusy += busyNanos;
        if (lastFrameNanos != 0) {
            long gap = nowNanos - lastFrameNanos;
            if (gap < windowMinGap) windowMinGap = gap;
            if (gap > windowMaxGap) windowMaxGap = gap;
        }
        lastFrameNanos = nowNanos;
        long elapsed = nowNanos - windowStart;
        if (elapsed >= 1_000_000_000L) {
            fps = windowFrames * 1_000_000_000f / elapsed;
            ms = windowBusy / 1_000_000f / windowFrames;
            frameMinMs = windowMinGap == Long.MAX_VALUE ? 0f : windowMinGap / 1_000_000f;
            frameMaxMs = windowMaxGap / 1_000_000f;
            windowStart = nowNanos;
            windowFrames = 0;
            windowBusy = 0;
            windowMinGap = Long.MAX_VALUE;
            windowMaxGap = 0;
        }
    }

    /** Forgets the running window (the view was hidden for a while). */
    void reset() {
        windowStart = 0;
        windowFrames = 0;
        windowBusy = 0;
        lastFrameNanos = 0;
        windowMinGap = Long.MAX_VALUE;
        windowMaxGap = 0;
        frameMinMs = 0f;
        frameMaxMs = 0f;
        fps = 0f;
        ms = 0f;
        triangles = 0;
        draws = 0;
    }
}
