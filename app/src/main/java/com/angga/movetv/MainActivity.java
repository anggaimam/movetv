package com.angga.movetv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.tensorflow.lite.Interpreter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * MoveTV proof of concept.
 * USB webcam -> on-device body tracking (MediaPipe Pose) -> skeleton + a balloon to pop.
 * Nothing leaves the TV: the app has no internet permission.
 *
 * Remote:
 *   OK            camera picture on/off (skeleton only)
 *   Up / Down     bigger / smaller image for the AI (speed vs accuracy)
 *   Menu (≡)      1 player (AI follows you) or 2 players (one per half of the screen)
 *   Rewind        AI engine: MoveNet (fast) / MediaPipe Lite / MediaPipe Full
 *   Fast-forward  CPU or GPU
 */
public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 7;
    private PoseView view;
    private PoseEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        view = new PoseView(this);
        setContentView(view);
        engine = new PoseEngine(this, view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            view.setStatus("Waiting for camera permission…");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            engine.start();
        }
    }

    @Override
    protected void onPause() {
        engine.stop(); // releases the webcam so Zoom etc. can use it
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                engine.start();
            } else {
                view.setStatus("Camera permission denied. Allow it in Settings > Applications > MoveTV.");
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                view.toggleCamera();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                engine.changeSize(+1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                engine.changeSize(-1);
                return true;
            case KeyEvent.KEYCODE_MENU:
                engine.setPlayers(engine.players == 1 ? 2 : 1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                engine.cycleEngine();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                engine.setGpu(!engine.gpu);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}

/**
 * Camera + body tracking.
 *  - The camera thread keeps only the newest frame. The moment the AI finishes, it starts on
 *    that frame (no waiting for the next camera frame, no queue of stale frames).
 *  - 1 player: the AI looks at a square crop that follows the person, so they appear big.
 *  - 2 players: the left and right halves of the picture are checked separately (one player each).
 *  - The webcam is asked for 30 frames per second.
 */
class PoseEngine {

    static final int[] INPUT_WIDTHS = {192, 256, 320, 480};
    private static final int MN = 192;
    /** MoveNet's 17 points -> the MediaPipe numbering the drawing code uses */
    private static final int[] COCO_TO_MP = {0, 2, 5, 7, 8, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28};
    private static final float MIN_SCORE = 0.3f;

    private final Context ctx;
    private final PoseView view;

    volatile int players = 1;
    /** 0 = MoveNet Lightning (fast), 1 = MediaPipe Lite, 2 = MediaPipe Full */
    volatile int engineType = 0;
    volatile boolean gpu = false;
    volatile int sizeIndex = 2; // camera picture shown on screen: 320 wide

    private HandlerThread thread, aiThread;
    private Handler bg, ai;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private volatile PoseLandmarker landmarker;
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

    // AI state
    private volatile boolean inFlight = false;
    private volatile long inFlightSince = 0;
    private long lastTs = 0;
    private final ByteBuffer[] mnInputs = {
            ByteBuffer.allocateDirect(MN * MN * 3).order(ByteOrder.nativeOrder()),
            ByteBuffer.allocateDirect(MN * MN * 3).order(ByteOrder.nativeOrder())};
    private final byte[] mnBytes = new byte[MN * MN * 3];
    private final float[][][][] mnOutput = new float[1][1][17][3];
    private final java.util.concurrent.ConcurrentHashMap<Long, Bitmap> pending = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Long> sentAt = new java.util.concurrent.ConcurrentHashMap<>();

    // 1-player follow crop, in camera pixels (square: left, top, side)
    private volatile float cropX, cropY, cropS;
    private volatile boolean following = false;

    // stats
    private int camFrames = 0, aiFrames = 0, dropped = 0;
    private long statStart = 0, convertMs = 0, aiMs = 0;
    private float camFps = 0, aiFps = 0, dropPct = 0;

    PoseEngine(Context ctx, PoseView view) {
        this.ctx = ctx.getApplicationContext();
        this.view = view;
    }

    private CameraManager manager() {
        return (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
    }

    synchronized void start() {
        if (thread != null) return;
        thread = new HandlerThread("movetv-cam");
        thread.start();
        bg = new Handler(thread.getLooper());
        aiThread = new HandlerThread("movetv-ai");
        aiThread.start();
        ai = new Handler(aiThread.getLooper());
        final int myGen = ++gen;
        bg.post(() -> {
            buildEngine();
            openCamera(myGen);
        });
    }

    synchronized void stop() {
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
            try { if (landmarker != null) landmarker.close(); } catch (Throwable ignored) { }
            session = null; device = null; reader = null; landmarker = null;
            inFlight = false;
            fresh = false;
            pending.clear(); sentAt.clear();
            th.quitSafely();
        });
    }

    void setPlayers(int n) { players = n; resetCrop(); rebuild(); }
    void cycleEngine() { engineType = (engineType + 1) % 3; rebuild(); }
    void setGpu(boolean g) { gpu = g; rebuild(); }
    boolean isMoveNet() { return engineType == 0; }

    synchronized void changeSize(int delta) {
        sizeIndex = Math.max(0, Math.min(INPUT_WIDTHS.length - 1, sizeIndex + delta));
        if (bg == null) return;
        bg.post(this::allocBuffers);
    }

    private synchronized void rebuild() {
        if (bg == null) return;
        bg.post(() -> {
            inFlight = false;
            pending.clear(); sentAt.clear();
            buildEngine();
        });
    }

    private void closeMoveNet() {
        synchronized (mnLock) {
            try { if (movenet != null) movenet.close(); } catch (Throwable ignored) { }
            movenet = null;
        }
    }

    private void buildEngine() {
        try { if (landmarker != null) landmarker.close(); } catch (Throwable ignored) { }
        landmarker = null;
        closeMoveNet();
        if (engineType == 0) buildMoveNet(); else buildLandmarker();
    }

    private void buildMoveNet() {
        view.setStatus("Loading MoveNet…");
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
            view.setStatus(null);
        } catch (Throwable t) {
            view.setStatus("MoveNet failed to load: " + t.getMessage());
        }
    }

    private void buildLandmarker() {
        boolean full = engineType == 2;
        view.setStatus("Loading MediaPipe (" + (full ? "Full" : "Lite") + ", " + (gpu ? "GPU" : "CPU") + ")…");
        try {
            BaseOptions base = BaseOptions.builder()
                    .setModelAssetPath(full ? "pose_landmarker_full.task" : "pose_landmarker_lite.task")
                    .setDelegate(gpu ? Delegate.GPU : Delegate.CPU)
                    .build();
            PoseLandmarker.PoseLandmarkerOptions opts = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(players)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener(this::onMediaPipeResult)
                    .setErrorListener(e -> { aiDone(); view.setStatus("AI error: " + e.getMessage()); })
                    .build();
            landmarker = PoseLandmarker.createFromOptions(ctx, opts);
            view.setStatus(null);
        } catch (Throwable t) {
            landmarker = null;
            view.setStatus("MediaPipe failed to load: " + t.getMessage()
                    + (gpu ? "  → press Fast-forward to go back to CPU" : ""));
        }
    }

    private String settingsText() {
        String eng = engineType == 0 ? "MoveNet" : engineType == 1 ? "MediaPipe Lite" : "MediaPipe Full";
        String mode = engineType == 0
                ? (players == 2 ? "2P split" : (following ? "1P follow" : "1P searching"))
                : (gpu ? "GPU" : "CPU") + " · " + players + "P";
        return eng + " · " + mode;
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
        outW = Math.min(INPUT_WIDTHS[sizeIndex], camW);
        outH = Math.round(outW * (float) camH / camW);
        argb = new int[outW * outH];
        for (int i = 0; i < bitmaps.length; i++) {
            bitmaps[i] = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        }
        inFlight = false;
        pending.clear(); sentAt.clear();
    }

    @SuppressLint("MissingPermission")
    private void openCamera(final int myGen) {
        try {
            String id = pickCamera();
            if (id == null) {
                view.setStatus("No camera found. Plug in the USB webcam (close Zoom), then reopen MoveTV.");
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
                                        } catch (Throwable t) { view.setStatus("Camera start failed: " + t.getMessage()); }
                                    }
                                    @Override public void onConfigureFailed(CameraCaptureSession s) {
                                        view.setStatus("Camera session failed");
                                    }
                                }, bg);
                    } catch (Throwable t) { view.setStatus("Camera error: " + t.getMessage()); }
                }
                @Override public void onDisconnected(CameraDevice d) { d.close(); view.setStatus("Webcam disconnected"); }
                @Override public void onError(CameraDevice d, int e) {
                    d.close();
                    view.setStatus("Webcam error " + e + " — is Zoom or another app using it?");
                }
            }, bg);
        } catch (Throwable t) {
            view.setStatus("Camera error: " + t.getMessage());
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
            img.close();
            img = null;
            fresh = true;
            if (inFlight && SystemClock.uptimeMillis() - inFlightSince > 2000) inFlight = false; // safety
            tryLaunch();
            updateStats(SystemClock.uptimeMillis());
        } catch (Throwable t) {
            view.setStatus("Camera frame error: " + t.getMessage());
        } finally {
            if (img != null) img.close();
        }
    }

    /** Camera thread: start the AI on the newest frame, if there is one and the AI is free. */
    private void tryLaunch() {
        if (!fresh || inFlight || camW == 0 || argb == null) return;
        Handler a = ai;
        boolean mn = engineType == 0;
        if (mn ? (movenet == null || a == null) : landmarker == null) return;
        fresh = false;

        long t0 = SystemClock.uptimeMillis();
        final Bitmap bmp = bitmaps[bmpIndex];
        bmpIndex = (bmpIndex + 1) % bitmaps.length;
        convertShrink(bmp);

        if (mn) {
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
        } else {
            long t1 = SystemClock.uptimeMillis();
            convertMs = (convertMs * 7 + (t1 - t0)) / 8;
            long ts = Math.max(t1, lastTs + 1);
            lastTs = ts;
            pending.put(ts, bmp);
            sentAt.put(ts, t1);
            inFlight = true;
            inFlightSince = t1;
            try {
                landmarker.detectAsync(new BitmapImageBuilder(bmp).build(), ts);
            } catch (Throwable t) {
                inFlight = false;
                view.setStatus("MediaPipe error: " + t.getMessage());
            }
        }
    }

    /** Called when the AI finishes: start on the newest frame straight away. */
    private void aiDone() {
        inFlight = false;
        Handler h = bg;
        if (h != null) h.post(this::tryLaunch);
    }

    private void runMoveNet(Bitmap bmp, float[][] crops, int np, long sent) {
        try {
            List<float[]> poses = new ArrayList<>();
            float[] xs = new float[17], ys = new float[17], sc = new float[17];
            for (int n = 0; n < np; n++) {
                synchronized (mnLock) {
                    if (movenet == null) return;
                    movenet.run(mnInputs[n], mnOutput);
                }
                float[] p = new float[33 * 3];
                int good = 0;
                for (int k = 0; k < 17; k++) {
                    float y = mnOutput[0][0][k][0], x = mnOutput[0][0][k][1], s = mnOutput[0][0][k][2];
                    float px = crops[n][0] + x * crops[n][2];
                    float py = crops[n][1] + y * crops[n][2];
                    xs[k] = px; ys[k] = py; sc[k] = s;
                    int m = COCO_TO_MP[k];
                    p[m * 3] = px / camW;
                    p[m * 3 + 1] = py / camH;
                    p[m * 3 + 2] = s > MIN_SCORE ? 1f : 0f;
                    if (s > MIN_SCORE) good++;
                }
                if (np == 1) {
                    if (good >= 5) { poses.add(p); updateCrop(xs, ys, sc); } else resetCrop();
                } else {
                    poses.add(good >= 5 ? p : new float[33 * 3]);
                }
            }
            long now = SystemClock.uptimeMillis();
            aiMs = (aiMs * 7 + (now - sent)) / 8;
            aiFrames++;
            view.update(bmp, poses, statsText(), np == 2);
        } catch (Throwable t) {
            view.setStatus("MoveNet error: " + t.getMessage());
        } finally {
            aiDone();
        }
    }

    private void onMediaPipeResult(PoseLandmarkerResult res, MPImage input) {
        long now = SystemClock.uptimeMillis();
        long ts = res.timestampMs();
        Bitmap bmp = pending.remove(ts);
        Long sent = sentAt.remove(ts);
        if (sent != null) aiMs = (aiMs * 7 + (now - sent)) / 8;
        aiFrames++;
        List<float[]> poses = new ArrayList<>();
        for (List<NormalizedLandmark> lmk : res.landmarks()) {
            float[] p = new float[lmk.size() * 3];
            for (int i = 0; i < lmk.size(); i++) {
                NormalizedLandmark l = lmk.get(i);
                p[i * 3] = l.x();
                p[i * 3 + 1] = l.y();
                p[i * 3 + 2] = l.visibility().orElse(1f);
            }
            poses.add(p);
        }
        view.update(bmp, poses, statsText(), false);
        aiDone();
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
        int[] torso = {5, 6, 11, 12};
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
        for (int k = 0; k < 17; k++) if (sc[k] > MIN_SCORE) {
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

    /** Builds MoveNet's 192x192 RGB input from a square crop of the newest frame (outside = black). */
    private void fillMoveNetInput(float[] crop, ByteBuffer dst) {
        byte[] out = mnBytes;
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
                int y = (yBuf[yo + srcX * sYPix] & 0xff) - 16;
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

    private String statsText() {
        return String.format(java.util.Locale.US,
                "AI %.0f fps (%d ms) · camera %.0f fps%s (%dx%d) · convert %d ms · unused %.0f%% · %s",
                aiFps, aiMs, camFps, fpsText, camW, camH, convertMs, dropPct, settingsText());
    }

    /** YUV (newest frame) -> ARGB picture for the screen, shrunk to outW x outH. */
    private void convertShrink(Bitmap out) {
        int ow = outW, oh = outH;
        int sx = (camW << 16) / ow, sy = (camH << 16) / oh; // 16.16 fixed-point step
        int[] dst = argb;
        for (int j = 0; j < oh; j++) {
            int srcY = (j * sy) >> 16;
            int yo = srcY * sYRow, uvo = (srcY >> 1) * sUvRow, o = j * ow;
            int fx = 0;
            for (int i = 0; i < ow; i++, fx += sx) {
                int srcX = fx >> 16;
                int y = (yBuf[yo + srcX * sYPix] & 0xff) - 16;
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

/**
 * Draws at the TV's refresh rate. Skeleton points glide toward the latest AI result,
 * so motion looks smooth even when the AI updates less often.
 */
class PoseView extends View {

    // MediaPipe pose landmark indexes
    private static final int[][] BONES = {
            {11, 12}, {11, 13}, {13, 15}, {12, 14}, {14, 16},      // arms
            {15, 19}, {16, 20},                                     // hands
            {11, 23}, {12, 24}, {23, 24},                           // body
            {23, 25}, {25, 27}, {24, 26}, {26, 28},                 // legs
            {27, 31}, {28, 32}                                      // feet
    };
    private static final int[] HANDS = {15, 16, 19, 20};
    private static final int[] COLORS = {Color.rgb(0, 200, 255), Color.rgb(255, 80, 200)};
    private static final float GLIDE = 0.5f; // 0..1, higher = snappier, lower = smoother

    private volatile Bitmap frame;
    private volatile List<float[]> target = new ArrayList<>();
    private final List<float[]> shown = new ArrayList<>();
    private volatile String stats = "";
    private volatile String status = "Starting…";
    private boolean showCamera = true;
    private volatile boolean split = false;
    private final Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint bone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint joint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panel = new Paint();
    private final Paint balloonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint camPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final Random rnd = new Random();

    // balloon game (normalized, already mirrored, coordinates)
    private float bx = 0.5f, by = 0.3f;
    private int balloonColor = Color.rgb(255, 70, 70);
    private int score = 0;
    private long popFlashUntil = 0;

    // screen fps
    private int drawFrames = 0;
    private long drawStart = 0;
    private float screenFps = 0;

    PoseView(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
        bone.setStrokeWidth(10f);
        bone.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(Color.WHITE);
        text.setTextSize(28f);
        panel.setColor(Color.argb(170, 0, 0, 0));
        divider.setColor(Color.argb(200, 255, 255, 255));
        divider.setStrokeWidth(6f);
        setFocusable(true);
    }

    void update(Bitmap bmp, List<float[]> p, String s, boolean splitScreen) {
        if (bmp != null) frame = bmp;
        split = splitScreen;
        target = p;
        stats = s;
    }

    void setStatus(String s) {
        status = s;
        postInvalidate();
    }

    void toggleCamera() {
        showCamera = !showCamera;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        drawFrames++;
        if (drawStart == 0) drawStart = now;
        if (now - drawStart >= 1000) {
            screenFps = drawFrames * 1000f / (now - drawStart);
            drawFrames = 0;
            drawStart = now;
        }

        int vw = getWidth(), vh = getHeight();
        Bitmap f = frame;
        float fw = f != null ? f.getWidth() : 4, fh = f != null ? f.getHeight() : 3;
        float scale = Math.min(vw / fw, vh / fh);
        float dw = fw * scale, dh = fh * scale;
        float left = (vw - dw) / 2f, top = (vh - dh) / 2f;
        dst.set(left, top, left + dw, top + dh);

        if (f != null && showCamera) {
            c.save();
            c.scale(-1f, 1f, vw / 2f, vh / 2f); // mirror, like looking in a mirror
            c.drawBitmap(f, null, dst, camPaint);
            c.restore();
        }

        // 2 players: each owns one half of the screen
        if (split) {
            float mid = left + dw / 2f;
            c.drawLine(mid, top, mid, top + dh, divider);
            text.setTextSize(40f);
            text.setColor(COLORS[0]);
            c.drawText("P1", left + 24, top + 100, text);
            text.setColor(COLORS[1]);
            c.drawText("P2", mid + 24, top + 100, text);
            text.setColor(Color.WHITE);
            text.setTextSize(28f);
        }

        // glide shown skeletons toward the latest AI result
        List<float[]> tg = target;
        while (shown.size() > tg.size()) shown.remove(shown.size() - 1);
        for (int n = 0; n < tg.size(); n++) {
            float[] t = tg.get(n);
            if (n >= shown.size() || shown.get(n).length != t.length) {
                if (n < shown.size()) shown.set(n, t.clone()); else shown.add(t.clone());
                continue;
            }
            float[] s = shown.get(n);
            for (int i = 0; i < t.length; i++) s[i] += (t[i] - s[i]) * GLIDE;
        }

        // balloon
        float br = dh * 0.07f;
        float bpx = left + bx * dw, bpy = top + by * dh;
        if (now < popFlashUntil) {
            balloonPaint.setColor(Color.YELLOW);
            c.drawCircle(bpx, bpy, br * 1.6f, balloonPaint);
        } else {
            balloonPaint.setColor(balloonColor);
            c.drawOval(bpx - br, bpy - br * 1.2f, bpx + br, bpy + br * 1.2f, balloonPaint);
            balloonPaint.setColor(Color.WHITE);
            balloonPaint.setStrokeWidth(3f);
            c.drawLine(bpx, bpy + br * 1.2f, bpx, bpy + br * 2.6f, balloonPaint);
        }

        // skeletons
        boolean hit = false;
        for (int n = 0; n < shown.size(); n++) {
            float[] p = shown.get(n);
            int col = COLORS[n % COLORS.length];
            bone.setColor(col);
            for (int[] b : BONES) {
                if (vis(p, b[0]) && vis(p, b[1])) {
                    c.drawLine(px(p, b[0], left, dw), py(p, b[0], top, dh),
                            px(p, b[1], left, dw), py(p, b[1], top, dh), bone);
                }
            }
            if (vis(p, 0)) {
                joint.setColor(col);
                c.drawCircle(px(p, 0, left, dw), py(p, 0, top, dh), dh * 0.045f, joint);
            }
            for (int h : HANDS) {
                if (!vis(p, h)) continue;
                float hx = px(p, h, left, dw), hy = py(p, h, top, dh);
                joint.setColor(Color.WHITE);
                c.drawCircle(hx, hy, 12f, joint);
                if (now >= popFlashUntil && Math.hypot(hx - bpx, hy - bpy) < br * 1.3f) hit = true;
            }
        }
        if (hit) {
            score++;
            popFlashUntil = now + 250;
            bx = 0.15f + rnd.nextFloat() * 0.7f;
            by = 0.12f + rnd.nextFloat() * 0.45f;
            balloonColor = Color.HSVToColor(new float[]{rnd.nextInt(360), 0.8f, 1f});
        }

        // text
        c.drawRect(0, 0, vw, 52, panel);
        c.drawText(String.format(java.util.Locale.US, "Screen %.0f fps · ", screenFps) + stats, 16, 36, text);
        text.setTextSize(44f);
        c.drawText("🎈 " + score, vw - 180, 110, text);
        text.setTextSize(28f);
        String help = "≡: 1/2 players   OK: camera on/off   ⏪: AI engine   ▲▼: picture size   ⏩: CPU/GPU (MediaPipe)";
        c.drawRect(0, vh - 52, vw, vh, panel);
        c.drawText(help, 16, vh - 16, text);
        String s = status;
        if (s != null) {
            c.drawRect(0, vh / 2f - 40, vw, vh / 2f + 30, panel);
            c.drawText(s, 24, vh / 2f + 8, text);
        } else if (tg.isEmpty() && f != null) {
            c.drawText("Step back so your whole body is in view", 24, 100, text);
        }
        postInvalidateOnAnimation(); // keep drawing at the TV's refresh rate
    }

    private static boolean vis(float[] p, int i) {
        return i * 3 + 2 < p.length && p[i * 3 + 2] > 0.5f;
    }

    private static float px(float[] p, int i, float left, float dw) {
        return left + (1f - p[i * 3]) * dw; // mirrored
    }

    private static float py(float[] p, int i, float top, float dh) {
        return top + p[i * 3 + 1] * dh;
    }
}
