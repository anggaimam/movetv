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
    /** The bowlers' colours, matching their avatars (astronaut cyan, alien green, robot orange, star pink). */
    static final int[] COLOURS = {0xFF29D3FF, 0xFF9BE22B, 0xFFFF9A1F, 0xFFFF6FAE};

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
        lane = pin = space = rocket = null;
        java.util.Arrays.fill(BALLS, null);
        java.util.Arrays.fill(AVATAR, null);
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
        // quiet space things on the dark floor beside the lane
        faintPlanet(c, 1700f, 900f, 95f);
        faintPlanet(c, 880f, 120f, 30f);
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

    /** The tile picture for the game menu: a bowling-ball planet with a ring, and a pin-shaped rocket orbiting it. */
    public static void cover(Canvas c, int w, int h) {
        float cx = w * 0.46f, cy = h * 0.54f, r = w * 0.27f;
        // stars
        Random rnd = new Random(5);
        fill(0xAAFFFFFF);
        for (int i = 0; i < 14; i++) c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, 1f + rnd.nextFloat() * 2f, F);
        // back half of the ring
        L.setStrokeCap(Paint.Cap.ROUND);
        L.setColor(0xFFFFC21F);
        L.setStrokeWidth(r * 0.12f);
        c.save();
        c.rotate(-20f, cx, cy);
        R.set(cx - r * 1.65f, cy - r * 0.42f, cx + r * 1.65f, cy + r * 0.42f);
        c.drawOval(R, L);
        c.restore();
        // the bowling ball planet
        circ(c, cx, cy, r, 0xFF6A3FD6);
        circ(c, cx + r * 0.18f, cy + r * 0.2f, r * 0.78f, 0xFF5A32C4);
        oval(c, cx - r * 0.35f, cy - r * 0.55f, r * 0.3f, r * 0.13f, 0xFF9B7BF0);
        circ(c, cx - r * 0.42f, cy - r * 0.12f, r * 0.13f, 0xFF2A1773);
        circ(c, cx - r * 0.12f, cy - r * 0.3f, r * 0.13f, 0xFF2A1773);
        circ(c, cx - r * 0.2f, cy + r * 0.08f, r * 0.15f, 0xFF2A1773);
        // front half of the ring
        c.save();
        c.clipRect(0f, cy, w, h);
        c.rotate(-20f, cx, cy);
        R.set(cx - r * 1.65f, cy - r * 0.42f, cx + r * 1.65f, cy + r * 0.42f);
        c.drawOval(R, L);
        c.restore();
        // the pin rocket on its orbit, front right, climbing
        paintPinRocket(c, cx + r * 1.3f, cy - r * 0.55f, r / 95f, 55f);
    }

    // ---- space avatars (flat, drawn from circles, curves and rounded boxes; design units, about -95..95) ----

    /** Player names, shown with the avatars. */
    static final String[] PLAYER = {"Player 1", "Player 2", "Player 3", "Player 4"};
    /** Avatar kinds: astronaut, alien, robot, star. */
    static final int ASTRONAUT = 0, ALIEN = 1, ROBOT = 2, STAR = 3;
    private static final int AV = 200;
    private static final Bitmap[] AVATAR = new Bitmap[4];
    private static final int INK = 0xFF1D2440;

    /**
     * An avatar as a cached 200 x 200 picture (draw it scaled for small sizes).
     * @param kind 0..3
     * @return the picture
     */
    static Bitmap avatar(int kind) {
        int k = Math.max(0, Math.min(3, kind));
        if (AVATAR[k] == null) {
            AVATAR[k] = SpriteCache.shared().get("bowl_av_" + k, AV, AV,
                    (c, w, h) -> drawAvatar(c, k, w / 2f, h / 2f + 4f, w / 200f));
        }
        return AVATAR[k];
    }

    private static void fill(int col) {
        F.setStyle(Paint.Style.FILL);
        F.setColor(col);
    }

    private static void circ(Canvas c, float x, float y, float r, int col) {
        fill(col);
        c.drawCircle(x, y, r, F);
    }

    private static void oval(Canvas c, float cx, float cy, float rx, float ry, int col) {
        fill(col);
        R.set(cx - rx, cy - ry, cx + rx, cy + ry);
        c.drawOval(R, F);
    }

    private static void box(Canvas c, float x, float y, float w, float h, float r, int col) {
        fill(col);
        R.set(x, y, x + w, y + h);
        c.drawRoundRect(R, r, r, F);
    }

    private static void arc(Canvas c, float x0, float y0, float qx, float qy, float x1, float y1, float width, int col) {
        P.reset();
        P.moveTo(x0, y0);
        P.quadTo(qx, qy, x1, y1);
        L.setColor(col);
        L.setStrokeWidth(width);
        c.drawPath(P, L);
    }

    /** An open smiling mouth with a tongue, centred at (0, y). */
    private static void mouth(Canvas c, float y, float half, int dark) {
        P.reset();
        P.moveTo(-half, y);
        P.quadTo(0f, y + half * 1.45f, half, y);
        P.close();
        fill(dark);
        c.drawPath(P, F);
        P.reset();
        P.moveTo(-half * 0.55f, y + half * 0.62f);
        P.quadTo(0f, y + half * 1.2f, half * 0.55f, y + half * 0.62f);
        P.quadTo(0f, y + half * 0.4f, -half * 0.55f, y + half * 0.62f);
        fill(0xFFFF7B8F);
        c.drawPath(P, F);
    }

    /** Big round eyes with a white shine. */
    private static void eyes(Canvas c, float dx, float y, float r, float shine, boolean white, float whiteR) {
        for (int s = -1; s <= 1; s += 2) {
            if (white) circ(c, s * dx, y - 2f, whiteR, 0xFFFFFFFF);
            circ(c, s * dx + 1f, y, r, INK);
            circ(c, s * dx + 1f + r * 0.35f, y - r * 0.35f, shine, 0xFFFFFFFF);
        }
    }

    /**
     * Draws a space avatar.
     * @param kind {@link #ASTRONAUT}, {@link #ALIEN}, {@link #ROBOT} or {@link #STAR}
     * @param cx centre x
     * @param cy centre y
     * @param s scale (1 = about 190 px across)
     */
    static void drawAvatar(Canvas c, int kind, float cx, float cy, float s) {
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        L.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case ASTRONAUT: {
                circ(c, -78f, 0f, 16f, 0xFF5AA9F2);
                circ(c, 78f, 0f, 16f, 0xFF5AA9F2);
                box(c, -86f, -10f, 9f, 20f, 4f, 0xFFFFD23F);
                box(c, 77f, -10f, 9f, 20f, 4f, 0xFFFFD23F);
                box(c, -58f, 60f, 116f, 22f, 10f, 0xFF6FB4F5);       // collar
                circ(c, 0f, 0f, 74f, 0xFFF4F7FB);                   // helmet
                box(c, -14f, -78f, 28f, 12f, 4f, 0xFF6FB4F5);
                circ(c, 0f, 4f, 58f, 0xFF2E6FC8);                   // visor
                circ(c, 0f, 12f, 40f, 0xFFF6B98A);                  // face
                circ(c, -40f, 14f, 7f, 0xFFF6B98A);
                circ(c, 40f, 14f, 7f, 0xFFF6B98A);
                // hair, coming well down over the forehead with a few bangs
                P.reset();
                P.moveTo(-43f, 8f);
                P.cubicTo(-48f, -30f, -20f, -44f, 4f, -40f);
                P.cubicTo(24f, -46f, 48f, -30f, 43f, 8f);
                P.cubicTo(38f, 0f, 32f, -4f, 24f, -3f);
                P.quadTo(18f, -9f, 10f, -4f);
                P.quadTo(2f, -12f, -6f, -4f);
                P.quadTo(-14f, -10f, -22f, -2f);
                P.quadTo(-34f, -4f, -43f, 8f);
                P.close();
                fill(INK);
                c.drawPath(P, F);
                eyes(c, 15f, 14f, 7f, 2.5f, false, 0f);
                circ(c, -27f, 26f, 6f, 0xFFF48A8A);
                circ(c, 27f, 26f, 6f, 0xFFF48A8A);
                mouth(c, 27f, 11f, 0xFF8A2A3A);
                L.setStrokeCap(Paint.Cap.ROUND);
                arc(c, -40f, -18f, -34f, -34f, -18f, -40f, 7f, 0xFF9CC8F7);  // visor shine
                box(c, -12f, 66f, 24f, 9f, 4f, 0xFFFFD23F);
                break;
            }
            case ALIEN: {
                L.setStrokeCap(Paint.Cap.ROUND);
                L.setColor(0xFFA3D321);
                L.setStrokeWidth(7f);
                c.drawLine(-26f, -50f, -38f, -82f, L);
                c.drawLine(26f, -50f, 38f, -82f, L);
                circ(c, -38f, -86f, 12f, 0xFFB8E026);
                circ(c, 38f, -86f, 12f, 0xFFB8E026);
                for (int sd = -1; sd <= 1; sd += 2) {                // pointed ears
                    P.reset();
                    P.moveTo(sd * 56f, -8f);
                    P.lineTo(sd * 88f, -24f);
                    P.lineTo(sd * 72f, 22f);
                    P.close();
                    fill(0xFF8FC21B);
                    c.drawPath(P, F);
                }
                oval(c, 0f, 4f, 66f, 60f, 0xFFB8E026);
                oval(c, -14f, -40f, 20f, 8f, 0xFFD4F05E);
                arc(c, -36f, -24f, -26f, -32f, -14f, -26f, 4f, 0xFF3D5A0A);
                arc(c, 14f, -26f, 26f, -32f, 36f, -24f, 4f, 0xFF3D5A0A);
                eyes(c, 23f, 0f, 12f, 4f, true, 18f);
                oval(c, -40f, 24f, 10f, 7f, 0xFF9FCB1C);
                oval(c, 40f, 24f, 10f, 7f, 0xFF9FCB1C);
                mouth(c, 22f, 13f, 0xFF6B1F2A);
                break;
            }
            case ROBOT: {
                box(c, -4f, -80f, 8f, 18f, 0f, 0xFF8FA3BF);
                circ(c, 0f, -84f, 11f, 0xFFFF9A1F);
                P.reset();
                P.moveTo(-22f, -60f);
                P.quadTo(0f, -72f, 22f, -60f);
                P.close();
                fill(0xFF3A8EE8);
                c.drawPath(P, F);
                box(c, -82f, -24f, 20f, 42f, 10f, 0xFF3A8EE8);
                box(c, 62f, -24f, 20f, 42f, 10f, 0xFF3A8EE8);
                box(c, -88f, -14f, 10f, 22f, 5f, 0xFFFF9A1F);
                box(c, 78f, -14f, 10f, 22f, 5f, 0xFFFF9A1F);
                box(c, -66f, -60f, 132f, 118f, 34f, 0xFFDFE5EF);    // head
                box(c, -52f, -44f, 104f, 70f, 20f, 0xFF2F74D0);     // screen
                eyes(c, 22f, -10f, 10f, 3.5f, true, 15f);
                L.setStrokeCap(Paint.Cap.ROUND);
                arc(c, -10f, 12f, 0f, 20f, 10f, 12f, 4f, INK);
                box(c, -28f, 34f, 56f, 16f, 8f, 0xFFF4F7FB);
                for (int i = -1; i <= 1; i++) circ(c, i * 12f, 42f, 4.5f, 0xFFFF9A1F);
                break;
            }
            default: { // STAR
                P.reset();
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + i * 36);
                    float r = i % 2 == 0 ? 66f : 30f;
                    float x = (float) Math.cos(a) * r, y = (float) Math.sin(a) * r + 4f;
                    if (i == 0) P.moveTo(x, y);
                    else P.lineTo(x, y);
                }
                P.close();
                F.setStyle(Paint.Style.FILL_AND_STROKE);
                F.setStrokeJoin(Paint.Join.ROUND);
                F.setStrokeWidth(22f);
                F.setColor(0xFFFF6FAE);
                c.drawPath(P, F);
                F.setStyle(Paint.Style.FILL);
                c.save();
                c.rotate(20f, -8f, -48f);
                oval(c, -8f, -48f, 7f, 11f, 0xFFFFA8CF);
                c.restore();
                L.setStrokeCap(Paint.Cap.ROUND);
                arc(c, -28f, -4f, -20f, -14f, -12f, -4f, 5f, INK);    // happy closed eyes
                arc(c, 12f, -4f, 20f, -14f, 28f, -4f, 5f, INK);
                oval(c, -34f, 10f, 9f, 6f, 0xFFFF9AA6);
                oval(c, 34f, 10f, 9f, 6f, 0xFFFF9AA6);
                mouth(c, 8f, 13f, 0xFF8A1F3A);
                break;
            }
        }
        L.setStrokeCap(Paint.Cap.ROUND);
        c.restore();
    }

    /**
     * A tile picture for the "How many players?" screen: that many avatars.
     * @param n 1..4
     */
    public static void paintGroup(Canvas c, int w, int h, int n) {
        float s = w / 200f;
        if (n == 1) {
            drawAvatar(c, 0, w * 0.5f, h * 0.52f, s * 0.95f);
        } else if (n == 2) {
            drawAvatar(c, 0, w * 0.28f, h * 0.52f, s * 0.5f);
            drawAvatar(c, 1, w * 0.72f, h * 0.52f, s * 0.5f);
        } else {
            float[][] at = {{0.28f, 0.28f}, {0.72f, 0.28f}, {0.28f, 0.74f}, {0.72f, 0.74f}};
            if (n == 3) at[2][0] = 0.5f;
            for (int i = 0; i < n; i++) drawAvatar(c, i, w * at[i][0], h * at[i][1], s * 0.45f);
        }
    }

    // ---- the quiet space around the lane ----

    private static Bitmap space, rocket;

    /** A small rocket (the drifting one in the background, and the tile picture's). */
    static Bitmap rocket() {
        if (rocket == null) rocket = SpriteCache.shared().get("bowl_rocket", 120, 120, (c, w, h) -> paintPinRocket(c, w / 2f, h / 2f, w / 150f, 30f));
        return rocket;
    }

    /** A dark starry backdrop with a faint planet (menus and the end screen of this game). */
    public static Bitmap space() {
        if (space == null) space = SpriteCache.shared().get("bowl_space", 1920, 1080, BowlingArt::paintSpace);
        return space;
    }

    private static void paintSpace(Canvas c, int w, int h) {
        fill(0xFF0E0725);
        c.drawRect(0f, 0f, w, h, F);
        Random r = new Random(11);
        for (int i = 0; i < 120; i++) {
            F.setColor(r.nextInt(5) == 0 ? 0x99FFD84D : 0x88FFFFFF);
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, 1f + r.nextFloat() * 2.2f, F);
        }
        faintPlanet(c, 1700f, 880f, 120f);
        faintPlanet(c, 180f, 170f, 46f);
    }

    /** A ringed planet at low brightness (sits quietly behind the play). */
    private static void faintPlanet(Canvas c, float x, float y, float r) {
        L.setStrokeCap(Paint.Cap.ROUND);
        L.setColor(0x33FFD84D);
        L.setStrokeWidth(r * 0.09f);
        c.save();
        c.rotate(-18f, x, y);
        R.set(x - r * 1.7f, y - r * 0.38f, x + r * 1.7f, y + r * 0.38f);
        c.drawOval(R, L);
        c.restore();
        circ(c, x, y, r, 0x406A3FD6);
        circ(c, x - r * 0.3f, y - r * 0.35f, r * 0.22f, 0x30B9A2FF);
        circ(c, x + r * 0.25f, y + r * 0.2f, r * 0.14f, 0x302A1A70);
        // the front half of the ring over the planet
        c.save();
        c.clipRect(x - r * 2f, y, x + r * 2f, y + r * 2f);
        c.rotate(-18f, x, y);
        R.set(x - r * 1.7f, y - r * 0.38f, x + r * 1.7f, y + r * 0.38f);
        c.drawOval(R, L);
        c.restore();
    }

    /**
     * A bowling-pin-shaped rocket: white body, red nose, red fins, a blue window and a flame.
     * @param s scale (1 = about 150 px tall)
     * @param tilt degrees, clockwise from pointing up
     */
    static void paintPinRocket(Canvas c, float cx, float cy, float s, float tilt) {
        c.save();
        c.translate(cx, cy);
        c.rotate(tilt);
        c.scale(s, s);
        // flame
        P.reset();
        P.moveTo(-16f, 60f);
        P.quadTo(0f, 110f, 16f, 60f);
        P.close();
        fill(0xFFFF9A1F);
        c.drawPath(P, F);
        P.reset();
        P.moveTo(-8f, 60f);
        P.quadTo(0f, 88f, 8f, 60f);
        P.close();
        fill(0xFFFFE15C);
        c.drawPath(P, F);
        // fins
        for (int sd = -1; sd <= 1; sd += 2) {
            P.reset();
            P.moveTo(sd * 16f, 18f);
            P.lineTo(sd * 40f, 52f);
            P.lineTo(sd * 36f, 66f);
            P.lineTo(sd * 14f, 56f);
            P.close();
            fill(0xFFE83A3A);
            c.drawPath(P, F);
        }
        // the pin-shaped body
        P.reset();
        P.moveTo(-17f, 62f);
        P.cubicTo(-30f, 36f, -28f, 6f, -12f, -16f);
        P.cubicTo(-6f, -26f, -8f, -38f, -8f, -46f);
        P.cubicTo(-18f, -52f, -18f, -70f, -10f, -78f);
        P.cubicTo(-5f, -84f, 5f, -84f, 10f, -78f);
        P.cubicTo(18f, -70f, 18f, -52f, 8f, -46f);
        P.cubicTo(8f, -38f, 6f, -26f, 12f, -16f);
        P.cubicTo(28f, 6f, 30f, 36f, 17f, 62f);
        P.close();
        fill(0xFFF4F7FB);
        c.drawPath(P, F);
        // red nose (the pin's head) and two neck stripes
        c.save();
        c.clipPath(P);
        fill(0xFFE83A3A);
        c.drawRect(-30f, -90f, 30f, -56f, F);
        c.drawRect(-30f, -44f, 30f, -38f, F);
        c.drawRect(-30f, -32f, 30f, -26f, F);
        c.restore();
        // window
        circ(c, 0f, 18f, 13f, 0xFF3A8EE8);
        circ(c, 0f, 18f, 8f, 0xFF9CD4FF);
        circ(c, -3f, 14f, 3f, 0xFFFFFFFF);
        c.restore();
    }

}
