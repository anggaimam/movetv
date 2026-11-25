package com.wiggleplay.app.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Debug;

import com.wiggleplay.app.gl.GlStats;
import com.wiggleplay.app.gl.RenderToggles;

/**
 * The little performance readout in the top-left corner: draw fps (1 second average), worst frame time
 * (5 second window), AI fps and the quality level. the owner photographs it on the TV.
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

    // pointer debug line (player 0): wrist vs cursor
    private float dbgWx, dbgWy, dbgCx, dbgCy;
    private long dbgNs = 0;
    private String pointerText = "";

    // person lock line: state (0 none, 1 searching, 2 locked) and rejected switches
    private int lockState = 0, lockRejected = 0;
    private String lockText = "";

    // a short line a game can set for tuning (e.g. the bowling swing); set only when it changes
    private String note = "";

    // the 3D view's speed line, shown only while the GL view is visible
    private GlStats glStats;
    private boolean glVisible = false;
    private String glText = "";
    // T-30 Gate 0: the debug switches (keys 1 to 5), and one-second timing of the scene (canvas draw, update) and frame spread
    private RenderToggles toggles;
    private String spreadText = "";
    private String toggleText = "";
    private long gateWindowNs = 0;
    private float gateUpdSum, gateDrawSum, gateUpdMax, gateDrawMax;
    private int gateFrames = 0;
    private float gateUpdAvg, gateDrawAvg, gateUpdPeak, gateDrawPeak;
    private float secBestMs = Float.MAX_VALUE;
    private float frameMinMs, frameMaxMs;

    /**
     * Gives the overlay the GL view's speed numbers (shown as a line while the GL view is visible).
     * @param stats the GL stats, or null
     */
    public void setGlStats(GlStats stats) {
        glStats = stats;
    }

    /**
     * Gives the overlay the debug switches, whose state it shows while the GL view is visible.
     * @param t the switches, or null
     */
    public void setToggles(RenderToggles t) {
        toggles = t;
    }

    /**
     * Tells the overlay whether the GL view is showing (the {@link GameView} calls this).
     * @param visible true while a 3D scene is current
     */
    public void setGlVisible(boolean visible) {
        glVisible = visible;
    }

    private String qualityMode = "auto";
    private int autoLowAt = -1;

    /**
     * Sets the words after the quality level on the first line.
     * @param mode "auto" or "forced"
     * @param autoLowAtS the second of this scene at which the automatic drop to LOW happened, or -1
     */
    public void setQualityMode(String mode, int autoLowAtS) {
        qualityMode = mode == null ? "" : mode;
        autoLowAt = autoLowAtS;
    }

    /**
     * Shows a short line under the others (pass "" to remove it). Build the text only when something changes.
     * @param text the line
     */
    public void setNote(String text) {
        note = text == null ? "" : text;
    }

    // CPU time per frame in the scene (T-11): average and worst over the last text period
    private String sceneName = "";
    private float updSum, drawSum, updMax, drawMax;
    private int timingFrames = 0;
    private String timingText = "";

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
     * Reports where player 0's wrist and cursor are, for the debug line (call every frame while a cursor shows).
     * @param wx wrist x in stage px
     * @param wy wrist y
     * @param cx cursor x
     * @param cy cursor y
     */
    public void setPointerDebug(float wx, float wy, float cx, float cy) {
        dbgWx = wx;
        dbgWy = wy;
        dbgCx = cx;
        dbgCy = cy;
        dbgNs = System.nanoTime();
    }

    /**
     * Reports the person lock for the debug line (call every frame).
     * @param state 0 off, 1 searching, 2 locked
     * @param rejected how many poses were dropped as "someone else"
     */
    public void setLock(int state, int rejected) {
        lockState = state;
        lockRejected = rejected;
    }

    /**
     * Reports how long this frame's scene update and scene draw took on the CPU (call every frame).
     * The GPU time is not included: a draw that is quick here but a low fps overall means the GPU is the limit.
     * @param scene the current scene name (may be null)
     * @param updateMs milliseconds spent in the scene update
     * @param drawMs milliseconds spent issuing the scene draw calls
     */
    public void setTiming(String scene, float updateMs, float drawMs) {
        sceneName = scene == null ? "" : scene;
        updSum += updateMs;
        drawSum += drawMs;
        if (updateMs > updMax) updMax = updateMs;
        if (drawMs > drawMax) drawMax = drawMs;
        timingFrames++;
        long now = System.nanoTime();
        if (gateWindowNs == 0) gateWindowNs = now;
        gateUpdSum += updateMs;
        gateDrawSum += drawMs;
        if (updateMs > gateUpdMax) gateUpdMax = updateMs;
        if (drawMs > gateDrawMax) gateDrawMax = drawMs;
        gateFrames++;
        if (now - gateWindowNs >= 1_000_000_000L) {
            gateUpdAvg = gateUpdSum / gateFrames;
            gateDrawAvg = gateDrawSum / gateFrames;
            gateUpdPeak = gateUpdMax;
            gateDrawPeak = gateDrawMax;
            gateUpdSum = gateDrawSum = gateUpdMax = gateDrawMax = 0f;
            gateFrames = 0;
            gateWindowNs = now;
        }
    }

    /** Removes the pointer debug line (no hand is showing). */
    public void clearPointerDebug() {
        dbgNs = 0;
    }

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
        secBestMs = Float.MAX_VALUE;
        frameMinMs = 0f;
        frameMaxMs = 0f;
        gateWindowNs = 0;
        gateUpdSum = gateDrawSum = gateUpdMax = gateDrawMax = 0f;
        gateFrames = 0;
        gateUpdAvg = gateDrawAvg = gateUpdPeak = gateDrawPeak = 0f;
        fps = 0f;
        worstMs = 0f;
        java.util.Arrays.fill(worstBuckets, 0f);
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
            if (ms < secBestMs) secBestMs = ms;
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
            frameMinMs = secBestMs == Float.MAX_VALUE ? 0f : secBestMs;
            frameMaxMs = secWorstMs;
            secBestMs = Float.MAX_VALUE;
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
                    .append("quality ").append(quality == GameView.LOW ? "LOW" : "HIGH")
                    .append(" (").append(qualityMode).append(')');
            if (autoLowAt >= 0) sb.append("  auto LOW at ").append(autoLowAt).append(" s");
            text = sb.toString();
            sb.setLength(0);
            int n = Math.max(1, timingFrames);
            Runtime rt = Runtime.getRuntime();
            sb.append(sceneName).append("  upd ").append(one(updSum / n)).append('/').append(one(updMax))
                    .append("  draw ").append(one(drawSum / n)).append('/').append(one(drawMax))
                    .append(" ms  heap ").append((rt.totalMemory() - rt.freeMemory()) >> 20)
                    .append(" MB  native ").append(Debug.getNativeHeapAllocatedSize() >> 20).append(" MB  cache ")
                    .append(SpriteCache.shared().bytes() >> 20).append(" MB (")
                    .append(SpriteCache.shared().summary()).append(')');
            timingText = sb.toString();
            updSum = drawSum = updMax = drawMax = 0f;
            timingFrames = 0;
            sb.setLength(0);
            if (lockState == 1) sb.append("lock SEARCHING");
            else if (lockState == 2) sb.append("lock LOCKED");
            else sb.append("lock off");
            sb.append("  rejected ").append(lockRejected);
            lockText = sb.toString();
            if (glVisible && glStats != null) {
                sb.setLength(0);
                sb.append("gl ").append((int) (glStats.fps() + 0.5f)).append(" fps, ")
                        .append((int) (glStats.ms() + 0.5f)).append(" ms, tris ")
                        .append((glStats.triangles() + 500) / 1000).append("k, draws ").append(glStats.draws());
                String cfg = glStats.config();
                if (cfg.length() > 0) sb.append("  egl ").append(cfg);
                glText = sb.toString();
                // Gate 0: canvas ms = the scene's 2D draw on the main thread, update ms = the scene update, both averaged
                // over one second (the biggest in brackets); frame spread = shortest / average / longest time between frames
                // over one second, for the canvas (the screen's frames) and for the GL view (rendered frames)
                sb.setLength(0);
                sb.append("canvas ").append(one(gateDrawAvg)).append(" (").append(one(gateDrawPeak)).append(") ms  update ")
                        .append(one(gateUpdAvg)).append(" (").append(one(gateUpdPeak)).append(") ms  frame ms min/avg/max: canvas ")
                        .append(one(frameMinMs)).append('/').append(one(fps > 0f ? 1000f / fps : 0f)).append('/')
                        .append(one(frameMaxMs)).append("  gl ").append(one(glStats.frameMinMs())).append('/')
                        .append(one(glStats.fps() > 0f ? 1000f / glStats.fps() : 0f)).append('/').append(one(glStats.frameMaxMs()));
                spreadText = sb.toString();
                sb.setLength(0);
                if (toggles != null) toggles.describe(sb);
                toggleText = sb.toString();
            }
            boolean fresh = dbgNs != 0 && System.nanoTime() - dbgNs < 300_000_000L;
            if (fresh) {
                sb.setLength(0);
                sb.append("wrist (").append((int) (dbgWx + 0.5f)).append(',').append((int) (dbgWy + 0.5f))
                        .append(") cursor (").append((int) (dbgCx + 0.5f)).append(',').append((int) (dbgCy + 0.5f))
                        .append(") \u0394 ").append((int) (Math.hypot(dbgCx - dbgWx, dbgCy - dbgWy) + 0.5)).append(" px");
                pointerText = sb.toString();
            } else {
                pointerText = "";
            }
        }
        boolean line2 = pointerText.length() > 0;
        boolean line3 = note.length() > 0;
        boolean line4 = glVisible && glStats != null && glText.length() > 0;
        boolean line5 = line4 && spreadText.length() > 0;
        boolean line6 = line4 && toggleText.length() > 0;
        float bottom = 140f + (line2 ? 44f : 0f) + (line3 ? 44f : 0f) + (line4 ? 44f : 0f) + (line5 ? 44f : 0f) + (line6 ? 44f : 0f);
        c.drawRect(0f, 0f, 1900f, bottom, panelPaint);
        c.drawText(text, 16f, 38f, textPaint);
        c.drawText(timingText, 16f, 82f, textPaint);
        c.drawText(lockText, 16f, 126f, textPaint);
        float y = 170f;
        if (line2) {
            c.drawText(pointerText, 16f, y, textPaint);
            y += 44f;
        }
        if (line3) {
            c.drawText(note, 16f, y, textPaint);
            y += 44f;
        }
        if (line4) {
            c.drawText(glText, 16f, y, textPaint);
            y += 44f;
        }
        if (line5) {
            c.drawText(spreadText, 16f, y, textPaint);
            y += 44f;
        }
        if (line6) c.drawText(toggleText, 16f, y, textPaint);
    }

    /** One decimal place without allocating a formatter (appends into the shared builder via a tiny string). */
    private static String one(float v) {
        int t = (int) (v * 10f + 0.5f);
        return (t / 10) + "." + (t % 10);
    }
}
