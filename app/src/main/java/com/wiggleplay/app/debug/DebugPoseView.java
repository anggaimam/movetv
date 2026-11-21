package com.wiggleplay.app.debug;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.pose.PoseFrame;

import java.util.Random;

/**
 * Draws at the TV's refresh rate. Skeleton points glide toward the latest AI result,
 * so motion looks smooth even when the AI updates less often.
 * Poses arrive as PoseFrames in stage coordinates (1920x1080, already mirrored) and are scaled to the view.
 */
public class DebugPoseView extends View {

    // COCO keypoint indexes (see Kp)
    private static final int[][] BONES = {
            {Kp.L_SHOULDER, Kp.R_SHOULDER}, {Kp.L_SHOULDER, Kp.L_ELBOW}, {Kp.L_ELBOW, Kp.L_WRIST},
            {Kp.R_SHOULDER, Kp.R_ELBOW}, {Kp.R_ELBOW, Kp.R_WRIST},                          // arms
            {Kp.L_SHOULDER, Kp.L_HIP}, {Kp.R_SHOULDER, Kp.R_HIP}, {Kp.L_HIP, Kp.R_HIP},     // body
            {Kp.L_HIP, Kp.L_KNEE}, {Kp.L_KNEE, Kp.L_ANKLE},
            {Kp.R_HIP, Kp.R_KNEE}, {Kp.R_KNEE, Kp.R_ANKLE}                                  // legs
    };
    private static final int[] HANDS = {Kp.L_WRIST, Kp.R_WRIST};
    private static final int[] COLORS = {Color.rgb(0, 200, 255), Color.rgb(255, 80, 200)};
    private static final float GLIDE = 0.65f; // 0..1, higher = snappier, lower = smoother
    /** Lowest confidence a joint can have and still be drawn (the engine keeps legs from 0.2). */
    private static final float MIN_DRAW = 0.2f;

    private volatile Bitmap frame;
    // two buffers used alternately, so the AI thread never writes the one being read
    private final PoseFrame[] inbox = {new PoseFrame(), new PoseFrame()};
    private int inboxIdx = 0;
    private volatile PoseFrame target;
    private final PoseFrame shown = new PoseFrame();
    private boolean haveShown = false;
    private volatile String stats = "";
    private volatile String status = "Starting…";
    private boolean showCamera = true;
    private final Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint bone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint joint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panel = new Paint();
    private final Paint balloonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint camPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final Random rnd = new Random();

    // balloon game (normalized to the stage, already mirrored, coordinates)
    private float bx = 0.5f, by = 0.3f;
    private int balloonColor = Color.rgb(255, 70, 70);
    private int score = 0;
    private long popFlashUntil = 0;

    // screen fps
    private int drawFrames = 0;
    private long drawStart = 0;
    private float screenFps = 0;

    /**
     * Creates the view.
     * @param c any context
     */
    public DebugPoseView(Context c) {
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

    /**
     * Gives the view the newest AI result. Copies the frame, so the caller may reuse it.
     * @param bmp newest camera picture (may be null)
     * @param pose the newest PoseFrame
     * @param s text for the stats bar
     */
    public void update(Bitmap bmp, PoseFrame pose, String s) {
        if (bmp != null) frame = bmp;
        inboxIdx ^= 1;
        PoseFrame d = inbox[inboxIdx];
        pose.copyInto(d);
        target = d;
        stats = s;
    }

    /**
     * Shows a message in the middle of the screen.
     * @param s the message, or null to hide it
     */
    public void setStatus(String s) {
        status = s;
        postInvalidate();
    }

    /** Camera picture on/off (skeleton only). */
    public void toggleCamera() {
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

        // the 1920x1080 stage, letterboxed into the view
        int vw = getWidth(), vh = getHeight();
        float scale = Math.min(vw / PoseFrame.STAGE_W, vh / PoseFrame.STAGE_H);
        float dw = PoseFrame.STAGE_W * scale, dh = PoseFrame.STAGE_H * scale;
        float left = (vw - dw) / 2f, top = (vh - dh) / 2f;
        dst.set(left, top, left + dw, top + dh);

        Bitmap f = frame;
        if (f != null && showCamera) {
            c.save();
            c.scale(-1f, 1f, vw / 2f, vh / 2f); // mirror, like looking in a mirror
            c.drawBitmap(f, null, dst, camPaint);
            c.restore();
        }

        // glide shown skeletons toward the latest AI result
        PoseFrame tg = target;
        boolean split = false;
        if (tg != null) {
            split = tg.mode == 2;
            if (!haveShown) {
                tg.copyInto(shown);
                haveShown = true;
            } else {
                shown.mode = tg.mode;
                for (int n = 0; n < shown.players.length; n++) {
                    PlayerPose sp = shown.players[n], tp = tg.players[n];
                    boolean appeared = !sp.present && tp.present; // snap instead of gliding in from old spots
                    sp.present = tp.present;
                    for (int k = 0; k < Kp.COUNT; k++) {
                        Keypoint a = sp.kps[k], b = tp.kps[k];
                        if (appeared) {
                            a.x = b.x;
                            a.y = b.y;
                        } else {
                            a.x += (b.x - a.x) * GLIDE;
                            a.y += (b.y - a.y) * GLIDE;
                        }
                        a.s = b.s;
                    }
                }
            }
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
        boolean anyPresent = false;
        for (int n = 0; n < shown.players.length; n++) {
            PlayerPose p = shown.players[n];
            if (!haveShown || !p.present) continue;
            anyPresent = true;
            int col = COLORS[n % COLORS.length];
            bone.setColor(col);
            for (int[] b : BONES) {
                if (vis(p, b[0]) && vis(p, b[1])) {
                    c.drawLine(px(p, b[0], left, scale), py(p, b[0], top, scale),
                            px(p, b[1], left, scale), py(p, b[1], top, scale), bone);
                }
            }
            if (vis(p, Kp.NOSE)) {
                joint.setColor(col);
                c.drawCircle(px(p, Kp.NOSE, left, scale), py(p, Kp.NOSE, top, scale), dh * 0.045f, joint);
            }
            for (int h : HANDS) {
                if (!vis(p, h)) continue;
                float hx = px(p, h, left, scale), hy = py(p, h, top, scale);
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
        String help = "≡: 1/2 players   ◀: light boost   OK: camera on/off";
        c.drawRect(0, vh - 52, vw, vh, panel);
        c.drawText(help, 16, vh - 16, text);
        String s = status;
        if (s != null) {
            c.drawRect(0, vh / 2f - 40, vw, vh / 2f + 30, panel);
            c.drawText(s, 24, vh / 2f + 8, text);
        } else if (!anyPresent && f != null) {
            c.drawText("Step back so your whole body is in view", 24, 100, text);
        }
        postInvalidateOnAnimation(); // keep drawing at the TV's refresh rate
    }

    private static boolean vis(PlayerPose p, int i) {
        return p.kps[i].s >= MIN_DRAW;
    }

    private static float px(PlayerPose p, int i, float left, float scale) {
        return left + p.kps[i].x * scale; // x is already mirrored
    }

    private static float py(PlayerPose p, int i, float top, float scale) {
        return top + p.kps[i].y * scale;
    }
}
