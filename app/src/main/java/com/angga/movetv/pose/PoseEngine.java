package com.angga.movetv.pose;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;

import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;

/**
 * Camera + body tracking (MoveNet Lightning).
 *  - The camera thread keeps only the newest frame. The moment the AI finishes, it starts on
 *    that frame (no waiting for the next camera frame, no queue of stale frames).
 *  - 1 player: the AI looks at a square crop that follows the person, so they appear big.
 *  - 2 players: the left and right halves of the picture are checked separately (one player each).
 *  - The webcam is asked for 30 frames per second.
 */
public final class PoseEngine {

    /** Receives a PoseFrame after every AI check. */
    public interface Listener {
        /**
         * Called on the AI thread. The engine reuses two frame objects alternately, so listeners must copy.
         * @param frame the newest result
         */
        void onPose(PoseFrame frame);
    }

    /** Receives status messages (loading, errors); null means "all fine, hide the message". */
    public interface StatusListener {
        /**
         * Called on whichever thread raised the message.
         * @param status the message, or null to clear it
         */
        void onStatus(String status);
    }

    /** Width of the camera picture kept for the screen. */
    private static final int PICTURE_W = 320;
    private static final int MN = 192;
    private static final float MIN_SCORE = 0.3f;

    private final Context ctx;
    private final Listener listener;
    private volatile StatusListener statusListener;

    private volatile int players = 1;

    private HandlerThread thread, aiThread;
    private Handler bg, ai;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private volatile Interpreter movenet;
    private final Object mnLock = new Object();
    private volatile int gen = 0;
    private String fpsText = "";
    private android.util.Range<Integer> fpsRange;

    // newest camera frame (camera thread only)
    private int camW, camH;
    private byte[] yBuf, uBuf, vBuf;
    private int sYRow, sYPix, sUvRow, sUvPix;
    private boolean fresh = false;

    // picture shown on screen
    private int outW, outH;
    private int[] argb;
    private final Bitmap[] bitmaps = new Bitmap[3];
    private int bmpIndex = 0;
    private volatile Bitmap picture;

    // AI state
    private volatile boolean inFlight = false;
    private volatile long inFlightSince = 0;
    private final ByteBuffer[] mnInputs = {
            ByteBuffer.allocateDirect(MN * MN * 3).order(ByteOrder.nativeOrder()),
            ByteBuffer.allocateDirect(MN * MN * 3).order(ByteOrder.nativeOrder())};
    private final byte[] mnBytes = new byte[MN * MN * 3];
    private final float[][][][] mnOutput = new float[1][1][17][3];

    // published results: two frames used alternately
    private final PoseFrame[] frames = {new PoseFrame(), new PoseFrame()};
    private int frameIdx = 0;

    // 1-player follow crop, in camera pixels (square: left, top, side)
    private volatile float cropX, cropY, cropS;
    private volatile boolean following = false;

    // Light boost: stretches dark and bright parts of the picture (helps dark rooms and backlight).
    private volatile boolean lightBoost = true;
    private final int[] yLut = new int[256];
    private int boostLo = 16, boostHi = 235;
    // Steadier joints, per player and point: smoothed position (0..1 of the camera picture),
    // its confidence, and when it was last seen.
    private final float[][] jx = new float[2][17], jy = new float[2][17], jScore = new float[2][17];
    private final long[][] jSeen = new long[2][17];
    private static final long HOLD_MS = 400;
    private static final float LEG_SCORE = 0.2f;

    // stats
    private int camFrames = 0, aiFrames = 0, dropped = 0;
    private long statStart = 0, convertMs = 0, aiMs = 0;
    private float camFps = 0, aiFps = 0, dropPct = 0;

    /**
     * Creates the engine. Nothing runs until {@link #start()}.
     * @param ctx any context
     * @param listener receives a PoseFrame after every AI check
     */
    public PoseEngine(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    /**
     * Sets who hears about loading and error messages.
     * @param l the listener, or null for none
     */
    public void setStatusListener(StatusListener l) {
        statusListener = l;
    }

    private void status(String s) {
        StatusListener l = statusListener;
        if (l != null) l.onStatus(s);
    }

    private CameraManager manager() {
        return (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
    }

    /** Starts the camera and the AI. Does nothing if already started. */
    public synchronized void start() {
        if (thread != null) return;
        thread = new HandlerThread("movetv-cam");
        thread.start();
        bg = new Handler(thread.getLooper());
        aiThread = new HandlerThread("movetv-ai");
        aiThread.start();
        ai = new Handler(aiThread.getLooper());
        final int myGen = ++gen;
        bg.post(() -> {
            buildMoveNet();
            openCamera(myGen);
        });
    }

    /** Stops the AI and releases the webcam so other apps (Zoom etc.) can use it. */
    public synchronized void stop() {
        gen++;
        final HandlerThread th = thread, at = aiThread;
        final Handler h = bg, ah = ai;
        thread = null; bg = null; aiThread = null; ai = null;
        if (ah != null) ah.post(() -> {
            closeMoveNet();
            at.quitSafely();
        });
        if (h == null) return;
        h.post(() -> {
            try { if (session != null) session.close(); } catch (Throwable ignored) { }
            try { if (device != null) device.close(); } catch (Throwable ignored) { }
            try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
            session = null; device = null; reader = null;
            inFlight = false;
            fresh = false;
            th.quitSafely();
        });
    }

    /**
     * Switches between 1 player (AI follows you) and 2 players (one per half of the screen).
     * @param n 1 or 2
     */
    public void setPlayers(int n) { players = n; resetCrop(); rebuild(); }

    /**
     * Current number of players.
     * @return 1 or 2
     */
    public int players() { return players; }

    /** Turns the light boost (brightens dark rooms for the AI) on or off. */
    public void toggleLightBoost() { lightBoost = !lightBoost; }

    /**
     * Newest camera picture (already shrunk), or null before the first frame.
     * @return a bitmap owned by the engine; do not modify
     */
    public Bitmap cameraPicture() { return picture; }

    private synchronized void rebuild() {
        if (bg == null) return;
        bg.post(() -> {
            inFlight = false;
            closeMoveNet();
            buildMoveNet();
        });
    }

    private void closeMoveNet() {
        synchronized (mnLock) {
            try { if (movenet != null) movenet.close(); } catch (Throwable ignored) { }
            movenet = null;
        }
    }

    private void buildMoveNet() {
        status("Loading MoveNet…");
        try {
            android.content.res.AssetFileDescriptor fd = ctx.getAssets().openFd("movenet_lightning.tflite");
            MappedByteBuffer model;
            try (java.io.FileInputStream in = new java.io.FileInputStream(fd.getFileDescriptor())) {
                model = in.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
            }
            Interpreter.Options o = new Interpreter.Options();
            o.setNumThreads(4);
            synchronized (mnLock) {
                movenet = new Interpreter(model, o);
            }
            status(null);
        } catch (Throwable t) {
            status("MoveNet failed to load: " + t.getMessage());
        }
    }

    private String settingsText() {
        String mode = players == 2 ? "2P split" : (following ? "1P follow" : "1P searching");
        return "MoveNet · " + mode + (lightBoost ? " · light boost" : "");
    }

    private String pickCamera() throws Exception {
        String[] ids = manager().getCameraIdList();
        for (String id : ids) {
            Integer f = manager().getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (f != null && f == CameraCharacteristics.LENS_FACING_EXTERNAL) return id;
        }
        return ids.length > 0 ? ids[0] : null;
    }

    /** (Re)creates the on-screen picture buffers. Runs on the camera thread. */
    private void allocBuffers() {
        if (camW == 0) return;
        outW = Math.min(PICTURE_W, camW);
        outH = Math.round(outW * (float) camH / camW);
        argb = new int[outW * outH];
        for (int i = 0; i < bitmaps.length; i++) {
            bitmaps[i] = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        }
        inFlight = false;
    }

    @SuppressLint("MissingPermission")
    private void openCamera(final int myGen) {
        try {
            String id = pickCamera();
            if (id == null) {
                status("No camera found. Plug in the USB webcam (close Zoom), then reopen MoveTV.");
                return;
            }
            CameraCharacteristics cc = manager().getCameraCharacteristics(id);
            StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // Smallest supported size that is at least 480 wide (fast for the webcam, enough detail).
            Size best = null;
            if (map != null) {
                for (Size s : map.getOutputSizes(ImageFormat.YUV_420_888)) {
                    if (s.getWidth() < 480) continue;
                    if (best == null || s.getWidth() * s.getHeight() < best.getWidth() * best.getHeight()) best = s;
                }
            }
            if (best == null) best = new Size(640, 480);
            camW = best.getWidth();
            camH = best.getHeight();
            resetCrop();

            // Ask for 30 fps: prefer a fixed 30-30 range, otherwise the range reaching highest.
            fpsRange = null;
            android.util.Range<Integer>[] ranges = cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (ranges != null) {
                for (android.util.Range<Integer> r : ranges) {
                    if (r.getUpper() > 30) continue;
                    if (fpsRange == null || r.getUpper() > fpsRange.getUpper()
                            || (r.getUpper().equals(fpsRange.getUpper()) && r.getLower() > fpsRange.getLower())) {
                        fpsRange = r;
                    }
                }
            }
            fpsText = fpsRange == null ? "" : " asked " + fpsRange.getLower() + "-" + fpsRange.getUpper();

            allocBuffers();
            reader = ImageReader.newInstance(camW, camH, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(this::onImage, bg);
            final ImageReader myReader = reader;

            manager().openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    if (myGen != gen) { d.close(); return; }
                    device = d;
                    try {
                        d.createCaptureSession(Collections.singletonList(myReader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override public void onConfigured(CameraCaptureSession s) {
                                        session = s;
                                        try {
                                            CaptureRequest.Builder rb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                            rb.addTarget(myReader.getSurface());
                                            if (fpsRange != null) rb.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
                                            s.setRepeatingRequest(rb.build(), null, bg);
                                        } catch (Throwable t) { status("Camera start failed: " + t.getMessage()); }
                                    }
                                    @Override public void onConfigureFailed(CameraCaptureSession s) {
                                        status("Camera session failed");
                                    }
                                }, bg);
                    } catch (Throwable t) { status("Camera error: " + t.getMessage()); }
                }
                @Override public void onDisconnected(CameraDevice d) { d.close(); status("Webcam disconnected"); }
                @Override public void onError(CameraDevice d, int e) {
                    d.close();
                    status("Webcam error " + e + " — is Zoom or another app using it?");
                }
            }, bg);
        } catch (Throwable t) {
            status("Camera error: " + t.getMessage());
        }
    }

    /** Camera thread: copy the newest frame, then start the AI on it if the AI is free. */
    private void onImage(ImageReader r) {
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null || r != reader) return;
            camFrames++;
            if (fresh) dropped++; // previous frame was never used
            Image.Plane[] p = img.getPlanes();
            ByteBuffer yb = p[0].getBuffer(), ub = p[1].getBuffer(), vb = p[2].getBuffer();
            if (yBuf == null || yBuf.length < yb.remaining()) yBuf = new byte[yb.remaining()];
            if (uBuf == null || uBuf.length < ub.remaining()) uBuf = new byte[ub.remaining()];
            if (vBuf == null || vBuf.length < vb.remaining()) vBuf = new byte[vb.remaining()];
            yb.get(yBuf, 0, yb.remaining());
            ub.get(uBuf, 0, ub.remaining());
            vb.get(vBuf, 0, vb.remaining());
            sYRow = p[0].getRowStride(); sYPix = p[0].getPixelStride();
            sUvRow = p[1].getRowStride(); sUvPix = p[1].getPixelStride();
            if (lightBoost) updateLut();
            img.close();
            img = null;
            fresh = true;
            if (inFlight && SystemClock.uptimeMillis() - inFlightSince > 2000) inFlight = false; // safety
            tryLaunch();
            updateStats(SystemClock.uptimeMillis());
        } catch (Throwable t) {
            status("Camera frame error: " + t.getMessage());
        } finally {
            if (img != null) img.close();
        }
    }

    /** Camera thread: start the AI on the newest frame, if there is one and the AI is free. */
    private void tryLaunch() {
        if (!fresh || inFlight || camW == 0 || argb == null) return;
        Handler a = ai;
        if (movenet == null || a == null) return;
        fresh = false;

        long t0 = SystemClock.uptimeMillis();
        final Bitmap bmp = bitmaps[bmpIndex];
        bmpIndex = (bmpIndex + 1) % bitmaps.length;
        convertShrink(bmp);

        final int np = players;
        final float[][] crops = new float[np][];
        if (np == 1) {
            crops[0] = new float[]{cropX, cropY, cropS};
        } else {
            float s = Math.max(camW / 2f, camH);
            // The screen is a mirror: the camera's right half is the player on the screen's left.
            crops[0] = new float[]{camW * 0.75f - s / 2f, camH / 2f - s / 2f, s};
            crops[1] = new float[]{camW * 0.25f - s / 2f, camH / 2f - s / 2f, s};
        }
        for (int i = 0; i < np; i++) fillMoveNetInput(crops[i], mnInputs[i]);
        final long sent = SystemClock.uptimeMillis();
        convertMs = (convertMs * 7 + (sent - t0)) / 8;
        inFlight = true;
        inFlightSince = sent;
        a.post(() -> runMoveNet(bmp, crops, np, sent));
    }

    /** Called when the AI finishes: start on the newest frame straight away. */
    private void aiDone() {
        inFlight = false;
        Handler h = bg;
        if (h != null) h.post(this::tryLaunch);
    }

    private void runMoveNet(Bitmap bmp, float[][] crops, int np, long sent) {
        try {
            float[] xs = new float[17], ys = new float[17], sc = new float[17];
            PoseFrame f = frames[frameIdx];
            frameIdx ^= 1;
            for (int n = 0; n < np; n++) {
                synchronized (mnLock) {
                    if (movenet == null) return;
                    movenet.run(mnInputs[n], mnOutput);
                }
                PlayerPose pp = f.players[n];
                int good = 0;
                for (int k = 0; k < Kp.COUNT; k++) {
                    float y = mnOutput[0][0][k][0], x = mnOutput[0][0][k][1], s = mnOutput[0][0][k][2];
                    float px = crops[n][0] + x * crops[n][2];
                    float py = crops[n][1] + y * crops[n][2];
                    xs[k] = px; ys[k] = py; sc[k] = s;
                    float need = k >= Kp.L_KNEE ? LEG_SCORE : MIN_SCORE; // knees and ankles are harder to see
                    float nx = px / camW, ny = py / camH;
                    long now0 = SystemClock.uptimeMillis();
                    if (s > need) {
                        if (now0 - jSeen[n][k] > HOLD_MS) {
                            jx[n][k] = nx; jy[n][k] = ny;
                        } else {
                            // small moves are smoothed (less jitter), big moves follow at once (no lag)
                            float d = Math.abs(nx - jx[n][k]) + Math.abs(ny - jy[n][k]);
                            float a = Math.min(1f, 0.35f + d * 8f);
                            jx[n][k] += (nx - jx[n][k]) * a;
                            jy[n][k] += (ny - jy[n][k]) * a;
                        }
                        jScore[n][k] = Math.min(1f, s);
                        jSeen[n][k] = now0;
                        good++;
                    }
                    // a briefly lost point stays where it was for a moment instead of flickering
                    boolean visible = now0 - jSeen[n][k] <= HOLD_MS;
                    Keypoint kp = pp.kps[k];
                    kp.x = (1f - jx[n][k]) * PoseFrame.STAGE_W; // mirrored, like a mirror
                    kp.y = jy[n][k] * PoseFrame.STAGE_H;
                    kp.s = visible ? jScore[n][k] : 0f;
                }
                if (good < 5) {
                    for (int k = 0; k < Kp.COUNT; k++) pp.kps[k].s = 0f;
                }
                int strong = 0;
                for (int k = 0; k < Kp.COUNT; k++) if (pp.kps[k].s >= MIN_SCORE) strong++;
                pp.present = good >= 5 && strong >= 5;
                if (np == 1) {
                    if (good >= 5) updateCrop(xs, ys, sc); else resetCrop();
                }
            }
            for (int n = np; n < f.players.length; n++) {
                PlayerPose pp = f.players[n];
                pp.present = false;
                for (int k = 0; k < Kp.COUNT; k++) pp.kps[k].s = 0f;
            }
            long now = SystemClock.uptimeMillis();
            aiMs = (aiMs * 7 + (now - sent)) / 8;
            aiFrames++;
            f.timeMs = now;
            f.mode = np;
            f.aiFps = aiFps;
            f.source = "camera";
            picture = bmp;
            listener.onPose(f);
        } catch (Throwable t) {
            status("MoveNet error: " + t.getMessage());
        } finally {
            aiDone();
        }
    }

    /** Whole picture, letterboxed into a square. */
    private void resetCrop() {
        following = false;
        if (camW == 0) return;
        float s = Math.max(camW, camH);
        cropX = (camW - s) / 2f;
        cropY = (camH - s) / 2f;
        cropS = s;
    }

    /** Next crop: a square around the person (torso x1.9, whole body x1.2), gliding to avoid jitter. */
    private void updateCrop(float[] xs, float[] ys, float[] sc) {
        int[] torso = {Kp.L_SHOULDER, Kp.R_SHOULDER, Kp.L_HIP, Kp.R_HIP};
        float cx = 0, cy = 0;
        int nt = 0;
        for (int k : torso) if (sc[k] > 0.2f) { cx += xs[k]; cy += ys[k]; nt++; }
        if (nt < 2) { resetCrop(); return; }
        cx /= nt;
        cy /= nt;
        float torsoRange = 0, bodyRange = 0;
        for (int k : torso) if (sc[k] > 0.2f) {
            torsoRange = Math.max(torsoRange, Math.max(Math.abs(xs[k] - cx), Math.abs(ys[k] - cy)));
        }
        for (int k = 0; k < Kp.COUNT; k++) if (sc[k] > MIN_SCORE) {
            bodyRange = Math.max(bodyRange, Math.max(Math.abs(xs[k] - cx), Math.abs(ys[k] - cy)));
        }
        float half = Math.max(torsoRange * 1.9f, bodyRange * 1.2f);
        half = Math.max(half, camH * 0.2f);
        half = Math.min(half, Math.max(camW, camH) / 2f);
        float tx = cx - half, ty = cy - half, ts = half * 2f;
        if (!following) {
            cropX = tx; cropY = ty; cropS = ts;
        } else {
            cropX += (tx - cropX) * 0.6f;
            cropY += (ty - cropY) * 0.6f;
            cropS += (ts - cropS) * 0.6f;
        }
        following = true;
    }

    /** Light boost: measures the frame's darkest and brightest 2% and stretches them apart, lifting shadows. */
    private void updateLut() {
        int[] hist = new int[256];
        int n = 0;
        for (int j = 0; j < camH; j += 4) {
            int yo = j * sYRow;
            for (int i = 0; i < camW; i += 4) { hist[yBuf[yo + i * sYPix] & 0xff]++; n++; }
        }
        int lo = 0, hi = 255, acc = 0;
        for (int v = 0; v < 256; v++) { acc += hist[v]; if (acc >= n * 0.02f) { lo = v; break; } }
        acc = 0;
        for (int v = 255; v >= 0; v--) { acc += hist[v]; if (acc >= n * 0.02f) { hi = v; break; } }
        if (hi - lo < 40) hi = Math.min(255, lo + 40);
        boostLo += (lo - boostLo) / 4; // glide, so brightness doesn't flicker
        boostHi += (hi - boostHi) / 4;
        float range = Math.max(40, boostHi - boostLo);
        for (int v = 0; v < 256; v++) {
            float t = (v - boostLo) / range;
            if (t < 0) t = 0; else if (t > 1) t = 1;
            t = (float) Math.pow(t, 0.75); // lift the shadows
            yLut[v] = 16 + (int) (t * 219);
        }
    }

    /** Builds MoveNet's 192x192 RGB input from a square crop of the newest frame (outside = black). */
    private void fillMoveNetInput(float[] crop, ByteBuffer dst) {
        byte[] out = mnBytes;
        boolean boost = lightBoost;
        int[] lut = yLut;
        int step = (int) (crop[2] / MN * 65536f);
        int bx = (int) (crop[0] * 65536f), by = (int) (crop[1] * 65536f);
        int o = 0;
        for (int j = 0; j < MN; j++) {
            int srcY = (by + j * step) >> 16;
            if (srcY < 0 || srcY >= camH) {
                java.util.Arrays.fill(out, o, o + MN * 3, (byte) 0);
                o += MN * 3;
                continue;
            }
            int yo = srcY * sYRow, uvo = (srcY >> 1) * sUvRow;
            int fx = bx;
            for (int i = 0; i < MN; i++, fx += step) {
                int srcX = fx >> 16;
                if (srcX < 0 || srcX >= camW) {
                    out[o++] = 0; out[o++] = 0; out[o++] = 0;
                    continue;
                }
                int y = (boost ? lut[yBuf[yo + srcX * sYPix] & 0xff] : (yBuf[yo + srcX * sYPix] & 0xff)) - 16;
                if (y < 0) y = 0;
                int uvi = uvo + (srcX >> 1) * sUvPix;
                int u = (uBuf[uvi] & 0xff) - 128;
                int v = (vBuf[uvi] & 0xff) - 128;
                int y1192 = 1192 * y;
                int r = y1192 + 1634 * v;
                int g = y1192 - 833 * v - 400 * u;
                int b = y1192 + 2066 * u;
                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;
                out[o++] = (byte) (r >> 10);
                out[o++] = (byte) (g >> 10);
                out[o++] = (byte) (b >> 10);
            }
        }
        dst.rewind();
        dst.put(out);
        dst.rewind();
    }

    private synchronized void updateStats(long now) {
        if (statStart == 0) statStart = now;
        if (now - statStart >= 1000) {
            float secs = (now - statStart) / 1000f;
            camFps = camFrames / secs;
            aiFps = aiFrames / secs;
            dropPct = camFrames == 0 ? 0 : 100f * dropped / camFrames;
            camFrames = 0; aiFrames = 0; dropped = 0; statStart = now;
        }
    }

    /**
     * One-line stats for the debug bar (AI speed, camera speed, settings).
     * @return the text
     */
    public String statsText() {
        return String.format(java.util.Locale.US,
                "AI %.0f fps (%d ms) · camera %.0f fps%s (%dx%d) · convert %d ms · unused %.0f%% · %s",
                aiFps, aiMs, camFps, fpsText, camW, camH, convertMs, dropPct, settingsText());
    }

    /** YUV (newest frame) -> ARGB picture for the screen, shrunk to outW x outH. */
    private void convertShrink(Bitmap out) {
        int ow = outW, oh = outH;
        boolean boost = lightBoost;
        int[] lut = yLut;
        int sx = (camW << 16) / ow, sy = (camH << 16) / oh; // 16.16 fixed-point step
        int[] dst = argb;
        for (int j = 0; j < oh; j++) {
            int srcY = (j * sy) >> 16;
            int yo = srcY * sYRow, uvo = (srcY >> 1) * sUvRow, o = j * ow;
            int fx = 0;
            for (int i = 0; i < ow; i++, fx += sx) {
                int srcX = fx >> 16;
                int y = (boost ? lut[yBuf[yo + srcX * sYPix] & 0xff] : (yBuf[yo + srcX * sYPix] & 0xff)) - 16;
                if (y < 0) y = 0;
                int uvi = uvo + (srcX >> 1) * sUvPix;
                int u = (uBuf[uvi] & 0xff) - 128;
                int v = (vBuf[uvi] & 0xff) - 128;
                int y1192 = 1192 * y;
                int r = y1192 + 1634 * v;
                int g = y1192 - 833 * v - 400 * u;
                int b = y1192 + 2066 * u;
                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;
                dst[o + i] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        out.setPixels(dst, 0, ow, 0, 0, ow, oh);
    }
}
