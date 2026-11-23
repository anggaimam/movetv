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
    private volatile String config = "";

    // GL thread only
    private long windowStart;
    private int windowFrames;
    private long windowBusy;

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
     * The EGL surface configuration in use, for example {@code r5g6b5 d16}; when the wanted RGB565 plus
     * 16-bit depth configuration was not offered, it starts with {@code FALLBACK} and lists what was offered.
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
        long elapsed = nowNanos - windowStart;
        if (elapsed >= 1_000_000_000L) {
            fps = windowFrames * 1_000_000_000f / elapsed;
            ms = windowBusy / 1_000_000f / windowFrames;
            windowStart = nowNanos;
            windowFrames = 0;
            windowBusy = 0;
        }
    }

    /** Forgets the running window (the view was hidden for a while). */
    void reset() {
        windowStart = 0;
        windowFrames = 0;
        windowBusy = 0;
        fps = 0f;
        ms = 0f;
        triangles = 0;
        draws = 0;
    }
}
