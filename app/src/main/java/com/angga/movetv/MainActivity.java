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
 *   Menu (≡)      1 or 2 players
 *   Rewind        Lite or Full tracking model
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
            case KeyEvent.KEYCODE_MENU:
                engine.setPlayers(engine.players == 1 ? 2 : 1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                engine.setFullModel(!engine.fullModel);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                engine.setGpu(!engine.gpu);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }
}

/** Camera + body tracking, all on one background thread. */
class PoseEngine {

    private final Context ctx;
    private final PoseView view;

    volatile int players = 1;
    volatile boolean fullModel = false;
    volatile boolean gpu = false;

    private HandlerThread thread;
    private Handler bg;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private PoseLandmarker landmarker;
    private volatile int gen = 0;

    private int width, height;
    private int[] argb;
    private byte[] yBuf, uBuf, vBuf;
    private final Bitmap[] bitmaps = new Bitmap[3];
    private int bmpIndex = 0;
    private long lastTs = 0;

    // stats
    private int camFrames = 0, doneFrames = 0;
    private long statStart = 0, convertMs = 0, detectMs = 0;
    private float camFps = 0, poseFps = 0;

    PoseEngine(Context ctx, PoseView view) {
        this.ctx = ctx.getApplicationContext();
        this.view = view;
    }

    private CameraManager manager() {
        return (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
    }

    synchronized void start() {
        if (thread != null) return;
        thread = new HandlerThread("movetv");
        thread.start();
        bg = new Handler(thread.getLooper());
        final int myGen = ++gen;
        bg.post(() -> {
            buildLandmarker();
            openCamera(myGen);
        });
    }

    synchronized void stop() {
        gen++;
        final HandlerThread th = thread;
        final Handler h = bg;
        thread = null;
        bg = null;
        if (h == null) return;
        h.post(() -> {
            try { if (session != null) session.close(); } catch (Throwable ignored) { }
            try { if (device != null) device.close(); } catch (Throwable ignored) { }
            try { if (reader != null) reader.close(); } catch (Throwable ignored) { }
            try { if (landmarker != null) landmarker.close(); } catch (Throwable ignored) { }
            session = null; device = null; reader = null; landmarker = null;
            th.quitSafely();
        });
    }

    void setPlayers(int n) { players = n; rebuild(); }
    void setFullModel(boolean f) { fullModel = f; rebuild(); }
    void setGpu(boolean g) { gpu = g; rebuild(); }

    private synchronized void rebuild() {
        if (bg == null) return;
        bg.post(() -> {
            try { if (landmarker != null) landmarker.close(); } catch (Throwable ignored) { }
            landmarker = null;
            buildLandmarker();
        });
    }

    private String settingsText() {
        return (fullModel ? "Full" : "Lite") + " model · " + (gpu ? "GPU" : "CPU") + " · "
                + players + (players == 1 ? " player" : " players");
    }

    private void buildLandmarker() {
        String model = fullModel ? "pose_landmarker_full.task" : "pose_landmarker_lite.task";
        view.setStatus("Loading body tracking (" + settingsText() + ")…");
        try {
            BaseOptions base = BaseOptions.builder()
                    .setModelAssetPath(model)
                    .setDelegate(gpu ? Delegate.GPU : Delegate.CPU)
                    .build();
            PoseLandmarker.PoseLandmarkerOptions opts = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumPoses(players)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build();
            landmarker = PoseLandmarker.createFromOptions(ctx, opts);
            view.setStatus(null);
        } catch (Throwable t) {
            landmarker = null;
            view.setStatus("Body tracking failed to load (" + settingsText() + "): " + t.getMessage()
                    + (gpu ? "  → press Fast-forward to go back to CPU" : ""));
        }
    }

    private String pickCamera() throws Exception {
        String[] ids = manager().getCameraIdList();
        for (String id : ids) {
            Integer f = manager().getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (f != null && f == CameraCharacteristics.LENS_FACING_EXTERNAL) return id;
        }
        return ids.length > 0 ? ids[0] : null;
    }

    @SuppressLint("MissingPermission")
    private void openCamera(final int myGen) {
        try {
            String id = pickCamera();
            if (id == null) {
                view.setStatus("No camera found. Plug in the USB webcam (close Zoom), then reopen MoveTV.");
                return;
            }
            StreamConfigurationMap map = manager().getCameraCharacteristics(id)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size best = new Size(640, 480);
            if (map != null) {
                Size b = null;
                for (Size s : map.getOutputSizes(ImageFormat.YUV_420_888)) {
                    int score = Math.abs(s.getWidth() - 640) + Math.abs(s.getHeight() - 480);
                    if (b == null || score < Math.abs(b.getWidth() - 640) + Math.abs(b.getHeight() - 480)) b = s;
                }
                if (b != null) best = b;
            }
            width = best.getWidth();
            height = best.getHeight();
            argb = new int[width * height];
            for (int i = 0; i < bitmaps.length; i++) {
                bitmaps[i] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            }
            reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2);
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

    private void onImage(ImageReader r) {
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null || r != reader) return;
            camFrames++;
            long t0 = SystemClock.uptimeMillis();
            yuvToArgb(img);
            img.close();
            img = null;
            Bitmap bmp = bitmaps[bmpIndex];
            bmpIndex = (bmpIndex + 1) % bitmaps.length;
            bmp.setPixels(argb, 0, width, 0, 0, width, height);
            long t1 = SystemClock.uptimeMillis();

            List<float[]> poses = new ArrayList<>();
            if (landmarker != null) {
                long ts = Math.max(t1, lastTs + 1);
                lastTs = ts;
                MPImage mp = new BitmapImageBuilder(bmp).build();
                PoseLandmarkerResult res = landmarker.detectForVideo(mp, ts);
                for (List<NormalizedLandmark> lm : res.landmarks()) {
                    float[] p = new float[lm.size() * 3];
                    for (int i = 0; i < lm.size(); i++) {
                        NormalizedLandmark l = lm.get(i);
                        p[i * 3] = l.x();
                        p[i * 3 + 1] = l.y();
                        p[i * 3 + 2] = l.visibility().orElse(1f);
                    }
                    poses.add(p);
                }
            }
            long t2 = SystemClock.uptimeMillis();
            doneFrames++;
            convertMs = (convertMs * 7 + (t1 - t0)) / 8;
            detectMs = (detectMs * 7 + (t2 - t1)) / 8;
            if (statStart == 0) statStart = t2;
            if (t2 - statStart >= 1000) {
                float secs = (t2 - statStart) / 1000f;
                camFps = camFrames / secs;
                poseFps = doneFrames / secs;
                camFrames = 0; doneFrames = 0; statStart = t2;
            }
            String stats = String.format(java.util.Locale.US,
                    "Tracking %.0f fps · camera %.0f fps · AI %d ms · convert %d ms · %dx%d · %s",
                    poseFps, camFps, detectMs, convertMs, width, height, settingsText());
            view.update(bmp, poses, stats);
        } catch (Throwable t) {
            view.setStatus("Tracking error: " + t.getMessage());
        } finally {
            if (img != null) img.close();
        }
    }

    /** YUV_420_888 -> ARGB, integer math. */
    private void yuvToArgb(Image img) {
        Image.Plane[] p = img.getPlanes();
        ByteBuffer yb = p[0].getBuffer(), ub = p[1].getBuffer(), vb = p[2].getBuffer();
        if (yBuf == null || yBuf.length < yb.remaining()) yBuf = new byte[yb.remaining()];
        if (uBuf == null || uBuf.length < ub.remaining()) uBuf = new byte[ub.remaining()];
        if (vBuf == null || vBuf.length < vb.remaining()) vBuf = new byte[vb.remaining()];
        yb.get(yBuf, 0, yb.remaining());
        ub.get(uBuf, 0, ub.remaining());
        vb.get(vBuf, 0, vb.remaining());
        int yRow = p[0].getRowStride(), yPix = p[0].getPixelStride();
        int uvRow = p[1].getRowStride(), uvPix = p[1].getPixelStride();
        int w = width, h = height;
        for (int j = 0; j < h; j++) {
            int yo = j * yRow, uvo = (j >> 1) * uvRow, o = j * w;
            for (int i = 0; i < w; i++) {
                int y = (yBuf[yo + i * yPix] & 0xff) - 16;
                if (y < 0) y = 0;
                int uvi = uvo + (i >> 1) * uvPix;
                int u = (uBuf[uvi] & 0xff) - 128;
                int v = (vBuf[uvi] & 0xff) - 128;
                int y1192 = 1192 * y;
                int r = y1192 + 1634 * v;
                int g = y1192 - 833 * v - 400 * u;
                int b = y1192 + 2066 * u;
                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;
                argb[o + i] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }
}

/** Draws the mirrored camera picture, the skeletons, a balloon to pop and the stats. */
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

    private volatile Bitmap frame;
    private volatile List<float[]> poses = new ArrayList<>();
    private volatile String stats = "";
    private volatile String status = "Starting…";
    private boolean showCamera = true;

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

    PoseView(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
        bone.setStrokeWidth(10f);
        bone.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(Color.WHITE);
        text.setTextSize(28f);
        panel.setColor(Color.argb(170, 0, 0, 0));
        setFocusable(true);
    }

    void update(Bitmap bmp, List<float[]> p, String s) {
        frame = bmp;
        poses = p;
        stats = s;
        postInvalidate();
    }

    void setStatus(String s) {
        status = s;
        postInvalidate();
    }

    void toggleCamera() {
        showCamera = !showCamera;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
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

        // balloon
        long now = SystemClock.uptimeMillis();
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
        List<float[]> ps = poses;
        boolean hit = false;
        for (int n = 0; n < ps.size(); n++) {
            float[] p = ps.get(n);
            int col = COLORS[n % COLORS.length];
            bone.setColor(col);
            joint.setColor(Color.WHITE);
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
        c.drawText(stats, 16, 36, text);
        text.setTextSize(44f);
        c.drawText("🎈 " + score, vw - 180, 110, text);
        text.setTextSize(28f);
        String help = "OK: camera picture on/off   ≡: 1/2 players   ⏪: Lite/Full model   ⏩: CPU/GPU";
        c.drawRect(0, vh - 52, vw, vh, panel);
        c.drawText(help, 16, vh - 16, text);
        String s = status;
        if (s != null) {
            c.drawRect(0, vh / 2f - 40, vw, vh / 2f + 30, panel);
            c.drawText(s, 24, vh / 2f + 8, text);
        } else if (ps.isEmpty() && f != null) {
            c.drawText("Step back so your whole body is in view", 24, 100, text);
        }
        if (!hit && now < popFlashUntil) postInvalidateDelayed(30);
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
