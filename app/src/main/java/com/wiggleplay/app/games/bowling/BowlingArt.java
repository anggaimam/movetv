package com.wiggleplay.app.games.bowling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.Brushes;
import com.wiggleplay.app.core.SpriteCache;

import java.util.Random;

/**
 * Neon arcade look for the bowling alley ("cosmic bowling"): a dark hall with a glowing lane, pins and balls as neon
 * outlines. Everything is painted once into {@link SpriteCache} bitmaps (keys start with "bowl_"); each frame only
 * copies them. The lane is drawn with a simple perspective: {@link #sx}, {@link #sy} and {@link #scale} turn a place on
 * the lane (x across, y down the lane in lane units) into stage pixels.
 */
public final class BowlingArt {

    /** Stage pixels across the lane (1 lane unit) at the foul line, and the lane's middle on the stage. */
    static final float S0 = 1000f, CX = 1180f;
    /** Perspective: screen y of the far end, of the foul line, and how fast things shrink with distance. */
    private static final float FOUL_Y = 1010f, FAR_Y = 330f, SHRINK = 0.16f;
    private static final float FAR_S = 1f / (1f + SHRINK * BowlingPhysics.LANE_L);
    private static final float K = (FOUL_Y - FAR_Y) / (1f - FAR_S);
    private static final float HORIZON = FOUL_Y - K;

    static final int PIN_W = 72, PIN_H = 180, BALL = 128;
    /** The bowlers' colours (cyan, pink, gold, mint). */
    static final int[] COLOURS = {0xFF29D3FF, 0xFFFF6EC7, 0xFFFFD84D, 0xFF7CFFB2};

    private static Bitmap lane, pin;
    private static final Bitmap[] BALLS = new Bitmap[4];

    private static final Paint F = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint L = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path P = new Path();
    private static final RectF R = new RectF();

    static {
        L.setStyle(Paint.Style.STROKE);
        L.setStrokeCap(Paint.Cap.ROUND);
    }

    private BowlingArt() { }

    /**
     * Perspective scale at a distance down the lane.
     * @param y lane y (0 at the foul line)
     * @return 1 at the foul line, smaller further away
     */
    static float scale(float y) {
        return 1f / (1f + SHRINK * y);
    }

    /**
     * Stage x of a place on the lane.
     * @param x lane x (-0.5..0.5)
     * @param y lane y
     * @return stage pixels
     */
    static float sx(float x, float y) {
        return CX + x * S0 * scale(y);
    }

    /**
     * Stage y of the floor at a distance down the lane.
     * @param y lane y
     * @return stage pixels
     */
    static float sy(float y) {
        return HORIZON + K * scale(y);
    }

    /** Lets go of every bowling picture (called when the game is left); they are painted again on the next visit. */
    static void unload() {
        lane = pin = null;
        java.util.Arrays.fill(BALLS, null);
        SpriteCache.shared().removePrefix("bowl_");
    }

    static Bitmap lane() {
        if (lane == null) lane = SpriteCache.shared().get("bowl_lane", 1920, 1080, BowlingArt::paintLane);
        return lane;
    }

    static Bitmap pin() {
        if (pin == null) pin = SpriteCache.shared().get("bowl_pin", PIN_W, PIN_H, BowlingArt::paintPin);
        return pin;
    }

    static Bitmap ball(int player) {
        int i = Math.max(0, Math.min(3, player));
        if (BALLS[i] == null) {
            final int col = COLOURS[i];
            BALLS[i] = SpriteCache.shared().get("bowl_ball_" + i, BALL, BALL, (c, w, h) -> paintBall(c, w, h, col));
        }
        return BALLS[i];
    }

    // ---- painting ----

    private static void glow(Canvas c, float x1, float y1, float x2, float y2, int col, float w) {
        L.setColor((col & 0x00FFFFFF) | 0x40000000);
        L.setStrokeWidth(w * 3.5f);
        c.drawLine(x1, y1, x2, y2, L);
        L.setColor(col);
        L.setStrokeWidth(w);
        c.drawLine(x1, y1, x2, y2, L);
    }

    private static void quad(Canvas c, float xl0, float y0, float xr0, float xl1, float y1, float xr1, int col) {
        P.reset();
        P.moveTo(xl0, y0);
        P.lineTo(xr0, y0);
        P.lineTo(xr1, y1);
        P.lineTo(xl1, y1);
        P.close();
        F.setStyle(Paint.Style.FILL);
        F.setColor(col);
        c.drawPath(P, F);
    }

    private static void paintLane(Canvas c, int w, int h) {
        F.setStyle(Paint.Style.FILL);
        F.setColor(0xFF0E0725);
        c.drawRect(0f, 0f, w, h, F);
        // a few stars in the dark hall
        Random r = new Random(7);
        F.setColor(0x88FFFFFF);
        for (int i = 0; i < 70; i++) c.drawCircle(r.nextFloat() * w, r.nextFloat() * (HORIZON + 260f), 1f + r.nextFloat() * 2f, F);
        // floor of the hall, a pink glow band at the horizon
        F.setColor(0xFF170B33);
        c.drawRect(0f, FAR_Y - 40f, w, h, F);
        glow(c, 0f, FAR_Y - 40f, w, FAR_Y - 40f, 0xFFFF4FD8, 3f);
        float y0 = -0.9f, y1 = BowlingPhysics.LANE_L + 0.4f;
        float gut = 0.62f;
        // gutters
        quad(c, sx(-gut, y0), sy(y0), sx(-0.5f, y0), sx(-gut, y1), sy(y1), sx(-0.5f, y1), 0xFF0A0420);
        quad(c, sx(0.5f, y0), sy(y0), sx(gut, y0), sx(0.5f, y1), sy(y1), sx(gut, y1), 0xFF0A0420);
        // the lane
        quad(c, sx(-0.5f, y0), sy(y0), sx(0.5f, y0), sx(-0.5f, y1), sy(y1), sx(0.5f, y1), 0xFF24165A);
        // boards: faint lines down the lane
        for (int i = -3; i <= 3; i++) {
            float bx = i * 0.125f;
            L.setColor(0x22FFFFFF);
            L.setStrokeWidth(2f);
            c.drawLine(sx(bx, y0), sy(y0), sx(bx, y1), sy(y1), L);
        }
        // pin deck, a lighter patch at the end
        float d0 = BowlingPhysics.HEAD_Y - 0.7f;
        quad(c, sx(-0.5f, d0), sy(d0), sx(0.5f, d0), sx(-0.5f, y1), sy(y1), sx(0.5f, y1), 0xFF2E1D70);
        // arrows (lane markers) in a V
        for (int i = -3; i <= 3; i++) {
            float ay = 4.2f + (3 - Math.abs(i)) * 0.55f, ax = i * 0.12f;
            P.reset();
            P.moveTo(sx(ax, ay + 0.55f), sy(ay + 0.55f));
            P.lineTo(sx(ax - 0.035f, ay), sy(ay));
            P.lineTo(sx(ax + 0.035f, ay), sy(ay));
            P.close();
            F.setColor(0xFFFFD84D);
            c.drawPath(P, F);
        }
        // foul line and the glowing edges
        glow(c, sx(-0.5f, 0f), sy(0f), sx(0.5f, 0f), sy(0f), 0xFFFF4FD8, 5f);
        glow(c, sx(-0.5f, y0), sy(y0), sx(-0.5f, y1), sy(y1), 0xFF29D3FF, 5f);
        glow(c, sx(0.5f, y0), sy(y0), sx(0.5f, y1), sy(y1), 0xFF29D3FF, 5f);
        glow(c, sx(-gut, y0), sy(y0), sx(-gut, y1), sy(y1), 0xFFFF4FD8, 3f);
        glow(c, sx(gut, y0), sy(y0), sx(gut, y1), sy(y1), 0xFFFF4FD8, 3f);
        glow(c, sx(-gut, y1), sy(y1), sx(gut, y1), sy(y1), 0xFFFF4FD8, 4f);
    }

    private static void paintPin(Canvas c, int w, int h) {
        Path p = new Path();
        p.moveTo(19f, 176f);
        p.cubicTo(6f, 150f, 8f, 118f, 24f, 94f);
        p.cubicTo(31f, 82f, 29f, 70f, 29f, 54f);
        p.cubicTo(19f, 48f, 17f, 28f, 26f, 14f);
        p.cubicTo(30f, 8f, 42f, 8f, 46f, 14f);
        p.cubicTo(55f, 28f, 53f, 48f, 43f, 54f);
        p.cubicTo(43f, 70f, 41f, 82f, 48f, 94f);
        p.cubicTo(64f, 118f, 66f, 150f, 53f, 176f);
        p.close();
        F.setStyle(Paint.Style.FILL);
        F.setColor(0xFF3A2A7A);
        c.drawPath(p, F);
        // two stripes on the neck
        L.setColor(0xFFFF4FD8);
        L.setStrokeWidth(5f);
        c.drawLine(30f, 62f, 42f, 62f, L);
        c.drawLine(29f, 72f, 43f, 72f, L);
        Brushes.neonStroke(c, p, 0xFFE8F8FF, 4f);
    }

    private static void paintBall(Canvas c, int w, int h, int col) {
        float cx = w / 2f, cy = h / 2f, r = w * 0.40f;
        F.setStyle(Paint.Style.FILL);
        F.setColor(darker(col));
        c.drawCircle(cx, cy, r, F);
        Path ring = new Path();
        ring.addCircle(cx, cy, r, Path.Direction.CW);
        Brushes.neonStroke(c, ring, col, 5f);
        // a shine and three finger holes
        F.setColor(0x66FFFFFF);
        c.drawCircle(cx - r * 0.38f, cy - r * 0.42f, r * 0.16f, F);
        F.setColor(0xFF0E0725);
        c.drawCircle(cx + r * 0.05f, cy - r * 0.35f, r * 0.10f, F);
        c.drawCircle(cx + r * 0.32f, cy - r * 0.22f, r * 0.10f, F);
        c.drawCircle(cx + r * 0.12f, cy - r * 0.05f, r * 0.12f, F);
    }

    private static int darker(int col) {
        int r = (col >> 16 & 0xFF) / 3, g = (col >> 8 & 0xFF) / 3, b = (col & 0xFF) / 3;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The tile picture for the game menu: three neon pins and a ball, on the tile's dark panel. */
    public static void cover(Canvas c, int w, int h) {
        Bitmap pn = pin();
        float ph = h * 0.50f, pw = ph * PIN_W / PIN_H;
        float[][] at = {{0.50f, 0.18f}, {0.30f, 0.34f}, {0.70f, 0.34f}};
        for (float[] a : at) {
            R.set(w * a[0] - pw / 2f, h * a[1], w * a[0] + pw / 2f, h * a[1] + ph);
            c.drawBitmap(pn, null, R, null);
        }
        Bitmap bl = ball(1);
        float bs = w * 0.34f;
        R.set(w * 0.50f - bs / 2f, h * 0.66f, w * 0.50f + bs / 2f, h * 0.66f + bs);
        c.drawBitmap(bl, null, R, null);
    }
}
