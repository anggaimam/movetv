package com.wiggleplay.app.games.bubbles;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.ui.TileMenu;

/**
 * All the pictures of Bubble Burst, drawn in code in a flat underwater style (no outlines, soft shapes, no neon):
 * bubbles, the little fish that follow the wrists, the water tint over the camera picture, and the swimming
 * scenery (light rays, drifting fish and jellyfish, seaweed, tiny rising bubbles). The scenery stays behind and
 * stays faint so it never looks like something to pop. Nothing is allocated per frame.
 */
public final class BubbleArt {

    /** Fin colours of the little fish: blue for the left hand, red for the right, neutral otherwise. */
    public static final int FIN_BLUE = 0xFF3F7DF0, FIN_RED = 0xFFF0584F, FIN_NEUTRAL = 0xFFFFFFFF;
    private static final int[] KIND_COLOUR = {0xFF8EE6F2, 0xFF4F8DF5, 0xFFF26B6B, 0xFFFFD04D, 0xFFB486FF, 0xFF7D8A8F};

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SPRITE = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Path P = new Path();
    private static final RectF R = new RectF();
    private static Paint tint;

    static {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
    }

    private BubbleArt() { }

    /** Lets go of every baked picture (called when the game is left). */
    static void unload() {
        SpriteCache.shared().removePrefix("bubbles_");
        SpriteCache.shared().removePrefix("tile_");
        SpriteCache.shared().removePrefix("tileicon_");
        tint = null;
    }

    // ---- water over the camera ----

    /**
     * Draws the water: the camera picture (if any) with a blue-green tint, or a plain flat gradient without a camera.
     * @param c canvas
     * @param camera camera picture or null
     */
    static void water(Canvas c, Bitmap camera) {
        if (tint == null) {
            tint = new Paint();
            tint.setShader(new LinearGradient(0f, 0f, 0f, Stage.H, 0x7A5FD0E8, 0x9A1C6E9E, Shader.TileMode.CLAMP));
        }
        if (camera != null) {
            Stage.drawCamera(c, camera, 0.12f);
            c.drawRect(0f, 0f, Stage.W, Stage.H, tint);
        } else {
            FILL.setColor(0xFF3AB0D0);
            c.drawRect(0f, 0f, Stage.W, Stage.H, FILL);
            c.drawRect(0f, 0f, Stage.W, Stage.H, tint);
        }
    }

    // ---- fish ----

    /**
     * Gets a baked little fish that faces right.
     * @param body body colour
     * @param fin fin colour
     * @return the picture, 160 x 100
     */
    static Bitmap fish(int body, int fin) {
        return fish(body, fin, 0);
    }

    /**
     * Gets a baked fish of one of four shapes: 0 plain, 1 striped, 2 spotted, 3 tall angelfish.
     * @param body body colour
     * @param fin fin colour
     * @param variant 0..3
     * @return the picture, 160 x 100
     */
    static Bitmap fish(int body, int fin, int variant) {
        return SpriteCache.shared().get("bubbles_fish_" + Integer.toHexString(body) + "_" + Integer.toHexString(fin) + "_" + variant,
                160, 100, (c, w, h) -> paintFish(c, w, h, body, fin, variant));
    }

    private static void paintFish(Canvas c, int w, int h, int body, int fin, int variant) {
        FILL.setStyle(Paint.Style.FILL);
        // tail
        FILL.setColor(fin);
        P.reset();
        P.moveTo(w * 0.30f, h * 0.50f);
        P.lineTo(w * 0.02f, h * 0.14f);
        P.quadTo(w * 0.10f, h * 0.50f, w * 0.02f, h * 0.86f);
        P.close();
        c.drawPath(P, FILL);
        // top fin
        P.reset();
        P.moveTo(w * 0.40f, h * 0.22f);
        P.quadTo(w * 0.52f, h * -0.02f, w * 0.70f, h * 0.20f);
        P.close();
        c.drawPath(P, FILL);
        // body
        FILL.setColor(body);
        R.set(w * 0.22f, h * (variant == 3 ? 0.06f : 0.16f), w * 0.96f, h * (variant == 3 ? 0.94f : 0.84f));
        c.drawOval(R, FILL);
        if (variant == 1) {
            FILL.setColor(0x44000000);
            for (int i = 0; i < 3; i++) {
                R.set(w * (0.40f + i * 0.14f), h * 0.20f, w * (0.45f + i * 0.14f), h * 0.80f);
                c.drawRoundRect(R, 6f, 6f, FILL);
            }
        } else if (variant == 2) {
            FILL.setColor(0x44FFFFFF);
            c.drawCircle(w * 0.46f, h * 0.38f, h * 0.07f, FILL);
            c.drawCircle(w * 0.60f, h * 0.30f, h * 0.06f, FILL);
            c.drawCircle(w * 0.58f, h * 0.60f, h * 0.075f, FILL);
            c.drawCircle(w * 0.72f, h * 0.50f, h * 0.05f, FILL);
        }
        FILL.setColor(body);
        // belly
        FILL.setColor(0x55FFFFFF);
        R.set(w * 0.34f, h * 0.52f, w * 0.88f, h * 0.80f);
        c.drawOval(R, FILL);
        // side fin
        FILL.setColor(fin);
        P.reset();
        P.moveTo(w * 0.52f, h * 0.55f);
        P.quadTo(w * 0.46f, h * 0.78f, w * 0.64f, h * 0.76f);
        P.quadTo(w * 0.66f, h * 0.62f, w * 0.52f, h * 0.55f);
        c.drawPath(P, FILL);
        // eye
        FILL.setColor(0xFFFFFFFF);
        c.drawCircle(w * 0.80f, h * 0.38f, h * 0.13f, FILL);
        FILL.setColor(0xFF2F3A45);
        c.drawCircle(w * 0.83f, h * 0.38f, h * 0.07f, FILL);
        // smile
        LINE.setColor(0xFF2F3A45);
        LINE.setStrokeWidth(h * 0.035f);
        R.set(w * 0.84f, h * 0.44f, w * 0.95f, h * 0.62f);
        c.drawArc(R, 20f, 90f, false, LINE);
    }

    /**
     * Draws one little fish guide on a wrist.
     * @param c canvas
     * @param x wrist x
     * @param y wrist y
     * @param right true to face right
     * @param body body colour
     * @param fin fin colour
     * @param t time in seconds (tail wiggle)
     * @param scale 1 = normal size
     */
    static void drawFish(Canvas c, float x, float y, boolean right, int body, int fin, float t, float scale) {
        Bitmap b = fish(body, fin);
        float w = 120f * scale, h = w * 100f / 160f;
        c.save();
        c.translate(x, y + (float) Math.sin(t * 7f) * 3f);
        c.rotate((float) Math.sin(t * 9f) * 5f * (right ? 1f : -1f));
        if (!right) c.scale(-1f, 1f);
        R.set(-w / 2f, -h / 2f, w / 2f, h / 2f);
        c.drawBitmap(b, null, R, SPRITE);
        c.restore();
    }

    // ---- bubbles ----

    /**
     * Draws one bubble (or a spiky decoy).
     * @param c canvas
     * @param kind {@link BubbleLogic#ANY} .. {@link BubbleLogic#DECOY}
     * @param x centre x
     * @param y centre y
     * @param r radius
     * @param alpha 0..1
     * @param t time in seconds (gentle shimmer)
     */
    static void drawBubble(Canvas c, int kind, float x, float y, float r, float alpha, float t) {
        drawBubble(c, kind, x, y, r, alpha, t, 0);
    }

    /**
     * Draws one bubble with one of three shine styles, so no two look quite the same.
     * @param c canvas
     * @param kind kind
     * @param x centre x
     * @param y centre y
     * @param r radius
     * @param alpha 0..1
     * @param t time in seconds
     * @param look 0..2 shine style
     */
    static void drawBubble(Canvas c, int kind, float x, float y, float r, float alpha, float t, int look) {
        int a = (int) (255 * Math.max(0f, Math.min(1f, alpha)));
        FILL.setStyle(Paint.Style.FILL);
        if (kind == BubbleLogic.DECOY) {
            drawUrchin(c, x, y, r, a, t);
            return;
        }
        int col = KIND_COLOUR[kind];
        FILL.setColor(col);
        FILL.setAlpha((int) (a * 0.45f));
        c.drawCircle(x, y, r, FILL);
        LINE.setColor(col);
        LINE.setAlpha(a);
        LINE.setStrokeWidth(Math.max(6f, r * 0.10f));
        c.drawCircle(x, y, r - LINE.getStrokeWidth() / 2f, LINE);
        // shine
        LINE.setColor(0xFFFFFFFF);
        LINE.setAlpha((int) (a * 0.85f));
        LINE.setStrokeWidth(r * 0.09f);
        R.set(x - r * 0.62f, y - r * 0.62f, x + r * 0.62f, y + r * 0.62f);
        c.drawArc(R, 200f, 55f, false, LINE);
        FILL.setColor(0xFFFFFFFF);
        FILL.setAlpha((int) (a * 0.85f));
        c.drawCircle(x - r * 0.52f, y - r * 0.08f - (float) Math.sin(t * 3f) * 1.5f, r * 0.06f, FILL);
        if (look == 1) {
            c.drawCircle(x + r * 0.42f, y + r * 0.38f, r * 0.07f, FILL);
            c.drawCircle(x + r * 0.30f, y + r * 0.50f, r * 0.04f, FILL);
        } else if (look == 2) {
            LINE.setColor(0xFFFFFFFF);
            LINE.setAlpha((int) (a * 0.5f));
            LINE.setStrokeWidth(r * 0.07f);
            R.set(x - r * 0.65f, y - r * 0.65f, x + r * 0.65f, y + r * 0.65f);
            c.drawArc(R, 20f, 45f, false, LINE);
        }
        if (kind == BubbleLogic.GOLD) {
            FILL.setColor(0xFFFFFFFF);
            FILL.setAlpha(a);
            star(c, x, y + r * 0.04f, r * 0.42f);
        } else if (kind == BubbleLogic.BOTH) {
            FILL.setColor(0xFFFFFFFF);
            FILL.setAlpha(a);
            c.drawCircle(x - r * 0.26f, y + r * 0.02f, r * 0.19f, FILL);
            c.drawCircle(x + r * 0.26f, y + r * 0.02f, r * 0.19f, FILL);
        } else if (kind == BubbleLogic.BLUE || kind == BubbleLogic.RED) {
            // one inner dot: a quiet colour cue that does not depend on telling blue from red apart
            FILL.setColor(0xFFFFFFFF);
            FILL.setAlpha((int) (a * 0.8f));
            c.drawCircle(x + (kind == BubbleLogic.BLUE ? -1f : 1f) * r * 0.28f, y + r * 0.1f, r * 0.15f, FILL);
        }
        FILL.setAlpha(255);
        LINE.setAlpha(255);
    }

    private static void drawUrchin(Canvas c, float x, float y, float r, int a, float t) {
        FILL.setColor(0xFF6B5B7B);
        FILL.setAlpha(a);
        P.reset();
        int spikes = 12;
        for (int i = 0; i < spikes * 2; i++) {
            double ang = i * Math.PI / spikes + Math.sin(t * 2f) * 0.05;
            float rad = (i % 2 == 0 ? 1.05f : 0.72f) * r;
            float px = x + (float) Math.cos(ang) * rad, py = y + (float) Math.sin(ang) * rad;
            if (i == 0) P.moveTo(px, py); else P.lineTo(px, py);
        }
        P.close();
        c.drawPath(P, FILL);
        FILL.setColor(0xFF8E7AA3);
        FILL.setAlpha(a);
        c.drawCircle(x, y, r * 0.55f, FILL);
        FILL.setColor(0xFFFFFFFF);
        FILL.setAlpha(a);
        c.drawCircle(x - r * 0.2f, y - r * 0.05f, r * 0.12f, FILL);
        c.drawCircle(x + r * 0.2f, y - r * 0.05f, r * 0.12f, FILL);
        FILL.setColor(0xFF2F3A45);
        FILL.setAlpha(a);
        c.drawCircle(x - r * 0.18f, y - r * 0.03f, r * 0.06f, FILL);
        c.drawCircle(x + r * 0.22f, y - r * 0.03f, r * 0.06f, FILL);
        LINE.setColor(0xFF2F3A45);
        LINE.setAlpha(a);
        LINE.setStrokeWidth(r * 0.05f);
        R.set(x - r * 0.2f, y + r * 0.12f, x + r * 0.2f, y + r * 0.34f);
        c.drawArc(R, 200f, 140f, false, LINE);
        FILL.setAlpha(255);
        LINE.setAlpha(255);
    }

    private static void star(Canvas c, float cx, float cy, float r) {
        P.reset();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? r : r * 0.45f;
            float px = cx + (float) Math.cos(ang) * rr, py = cy + (float) Math.sin(ang) * rr;
            if (i == 0) P.moveTo(px, py); else P.lineTo(px, py);
        }
        P.close();
        c.drawPath(P, FILL);
    }

    /**
     * Draws the ring that grows when a bubble pops.
     * @param c canvas
     * @param x centre x
     * @param y centre y
     * @param r bubble radius
     * @param k progress 0..1
     * @param kind kind, for the colour
     */
    static void drawPop(Canvas c, float x, float y, float r, float k, int kind) {
        LINE.setColor(KIND_COLOUR[Math.max(0, Math.min(KIND_COLOUR.length - 1, kind))]);
        LINE.setAlpha((int) (230 * (1f - k)));
        LINE.setStrokeWidth(r * 0.16f * (1f - k) + 2f);
        c.drawCircle(x, y, r * (1f + 0.55f * k), LINE);
        LINE.setAlpha(255);
    }

    // ---- scenery ----

    // ---- sea creatures ----

    private static Bitmap turtle() {
        return SpriteCache.shared().get("bubbles_turtle", 220, 140, (c, w, h) -> {
            FILL.setStyle(Paint.Style.FILL);
            FILL.setColor(0xFF6BBF8A);
            R.set(w * 0.62f, h * 0.30f, w * 0.80f, h * 0.60f);       // back flipper
            c.save(); c.rotate(-20f, w * 0.7f, h * 0.45f); c.drawOval(R, FILL); c.restore();
            R.set(w * 0.30f, h * 0.55f, w * 0.52f, h * 0.88f);       // front flipper
            c.save(); c.rotate(25f, w * 0.4f, h * 0.6f); c.drawOval(R, FILL); c.restore();
            R.set(w * 0.05f, h * 0.38f, w * 0.20f, h * 0.55f);       // tail
            c.drawOval(R, FILL);
            FILL.setColor(0xFFA8DDB4);                                 // head
            R.set(w * 0.72f, h * 0.26f, w * 0.97f, h * 0.58f);
            c.drawOval(R, FILL);
            FILL.setColor(0xFF2F3A45);
            c.drawCircle(w * 0.90f, h * 0.38f, h * 0.035f, FILL);
            FILL.setColor(0xFF3E8F63);                                 // shell
            R.set(w * 0.14f, h * 0.10f, w * 0.76f, h * 0.76f);
            c.drawOval(R, FILL);
            FILL.setColor(0xFF58AE7C);
            R.set(w * 0.26f, h * 0.20f, w * 0.64f, h * 0.60f);
            c.drawOval(R, FILL);
            FILL.setColor(0xFF3E8F63);
            c.drawCircle(w * 0.45f, h * 0.40f, h * 0.07f, FILL);
        });
    }

    private static Bitmap crab() {
        return SpriteCache.shared().get("bubbles_crab", 140, 100, (c, w, h) -> {
            FILL.setStyle(Paint.Style.FILL);
            FILL.setColor(0xFFE9654F);
            LINE.setColor(0xFFE9654F);
            LINE.setStrokeWidth(7f);
            for (int i = 0; i < 3; i++) {
                c.drawLine(w * (0.30f + i * 0.06f), h * 0.66f, w * (0.10f + i * 0.04f), h * (0.82f + i * 0.04f), LINE);
                c.drawLine(w * (0.70f - i * 0.06f), h * 0.66f, w * (0.90f - i * 0.04f), h * (0.82f + i * 0.04f), LINE);
            }
            R.set(w * 0.04f, h * 0.10f, w * 0.30f, h * 0.40f);       // claws
            c.drawOval(R, FILL);
            R.set(w * 0.70f, h * 0.10f, w * 0.96f, h * 0.40f);
            c.drawOval(R, FILL);
            R.set(w * 0.22f, h * 0.34f, w * 0.78f, h * 0.84f);       // body
            c.drawOval(R, FILL);
            FILL.setColor(0xFFFFFFFF);
            c.drawCircle(w * 0.40f, h * 0.30f, h * 0.08f, FILL);
            c.drawCircle(w * 0.60f, h * 0.30f, h * 0.08f, FILL);
            FILL.setColor(0xFF2F3A45);
            c.drawCircle(w * 0.41f, h * 0.31f, h * 0.04f, FILL);
            c.drawCircle(w * 0.61f, h * 0.31f, h * 0.04f, FILL);
        });
    }

    private static Bitmap starfish(int colour) {
        return SpriteCache.shared().get("bubbles_star_" + Integer.toHexString(colour), 90, 90, (c, w, h) -> {
            FILL.setStyle(Paint.Style.FILL);
            FILL.setColor(colour);
            P.reset();
            for (int i = 0; i < 10; i++) {
                double ang = -Math.PI / 2 + i * Math.PI / 5;
                float rr = (i % 2 == 0 ? 0.46f : 0.20f) * w;
                float px = w / 2f + (float) Math.cos(ang) * rr, py = h / 2f + (float) Math.sin(ang) * rr;
                if (i == 0) P.moveTo(px, py); else P.lineTo(px, py);
            }
            P.close();
            c.drawPath(P, FILL);
            FILL.setColor(0x55FFFFFF);
            for (int i = 0; i < 5; i++) {
                double ang = -Math.PI / 2 + i * Math.PI * 2 / 5;
                c.drawCircle(w / 2f + (float) Math.cos(ang) * w * 0.20f, h / 2f + (float) Math.sin(ang) * w * 0.20f, 3.5f, FILL);
            }
        });
    }

    /** The swimming scenery behind the bubbles; every visit (seed) picks a different mix and layout. */
    static final class Scenery {
        private static final int FISH = 7, JELLY = 2, BITS = 22, WEED = 9, STARS = 3;
        private static final int[] BODY = {0xFFFFB454, 0xFFFFE066, 0xFFB38CFF, 0xFFFF8FA3, 0xFF7FE3B0, 0xFF8EC5FF, 0xFFFF9E6B};
        private static final int[] FIN = {0xFFFFFFFF, 0xFFFFF3C4, 0xFFE9DDFF};
        private static final int[] JELLY_COL = {0x55FFC4E8, 0x55C9B8FF, 0x55B8F0FF};
        private static final int[] STAR_COL = {0xFFFF8E6B, 0xFFFFC857, 0xFFE87AA8, 0xFFB98CFF};
        private final float[] fx = new float[FISH], fy = new float[FISH], fs = new float[FISH], fv = new float[FISH], fp = new float[FISH];
        private final int[] fc = new int[FISH], ff = new int[FISH], fvar = new int[FISH];
        private final float[] jx = new float[JELLY], jy = new float[JELLY], jp = new float[JELLY];
        private final int[] jc = new int[JELLY];
        private final float[] bx = new float[BITS], by = new float[BITS], br = new float[BITS], bv = new float[BITS];
        private final float[] wx = new float[WEED], wh = new float[WEED], wp = new float[WEED];
        private final int[] wcol = new int[WEED];
        private final float[] sx = new float[STARS];
        private final int[] scol = new int[STARS];
        private final int seabedVariant;
        private float t, turtleX, turtleY, turtleV, turtleWait, crabX, crabV;

        Scenery(long seed) {
            java.util.Random r = new java.util.Random(seed);
            seabedVariant = r.nextInt(3);
            for (int i = 0; i < FISH; i++) {
                fx[i] = r.nextFloat() * Stage.W;
                fy[i] = 250f + r.nextFloat() * 520f;
                fs[i] = 0.38f + r.nextFloat() * 0.34f;
                fv[i] = (r.nextBoolean() ? 1f : -1f) * (28f + r.nextFloat() * 50f);
                fp[i] = r.nextFloat() * 6f;
                fc[i] = BODY[r.nextInt(BODY.length)];
                ff[i] = FIN[r.nextInt(FIN.length)];
                fvar[i] = r.nextInt(4);
            }
            for (int i = 0; i < JELLY; i++) {
                jx[i] = 300f + i * 1000f + r.nextFloat() * 300f;
                jy[i] = 330f + r.nextFloat() * 260f;
                jp[i] = r.nextFloat() * 6f;
                jc[i] = JELLY_COL[r.nextInt(JELLY_COL.length)];
            }
            for (int i = 0; i < BITS; i++) {
                bx[i] = r.nextFloat() * Stage.W;
                by[i] = r.nextFloat() * Stage.H;
                br[i] = 4f + r.nextFloat() * 11f;
                bv[i] = 30f + r.nextFloat() * 70f;
            }
            for (int i = 0; i < WEED; i++) {
                wx[i] = 50f + i * 215f + r.nextFloat() * 80f;
                wh[i] = 110f + r.nextFloat() * 130f;
                wp[i] = r.nextFloat() * 6f;
                wcol[i] = r.nextBoolean() ? 0xFF3FAE7A : (r.nextBoolean() ? 0xFF2F9B8A : 0xFF58B86B);
            }
            for (int i = 0; i < STARS; i++) {
                sx[i] = 200f + r.nextFloat() * 1500f;
                scol[i] = STAR_COL[r.nextInt(STAR_COL.length)];
            }
            turtleX = -300f;
            turtleY = 380f + r.nextFloat() * 260f;
            turtleV = 0f;
            turtleWait = 3f + r.nextFloat() * 6f;
            crabX = 300f + r.nextFloat() * 1300f;
            crabV = 40f;
        }

        void update(float dt) {
            t += dt;
            for (int i = 0; i < FISH; i++) {
                fx[i] += fv[i] * dt;
                if (fv[i] > 0f && fx[i] > Stage.W + 120f) fx[i] = -120f;
                if (fv[i] < 0f && fx[i] < -120f) fx[i] = Stage.W + 120f;
            }
            for (int i = 0; i < BITS; i++) {
                by[i] -= bv[i] * dt;
                if (by[i] < -20f) by[i] = Stage.H + 20f;
            }
            // the turtle waits off screen, then glides across, then waits again
            if (turtleV == 0f) {
                turtleWait -= dt;
                if (turtleWait <= 0f) {
                    turtleV = (t * 7f % 2f < 1f ? 1f : -1f) * 70f;
                    turtleX = turtleV > 0f ? -260f : Stage.W + 260f;
                    turtleY = 340f + (t * 131f) % 300f;
                }
            } else {
                turtleX += turtleV * dt;
                if (turtleX < -300f || turtleX > Stage.W + 300f) {
                    turtleV = 0f;
                    turtleWait = 8f + (t * 17f) % 8f;
                }
            }
            crabX += crabV * dt;
            if (crabX > Stage.W - 200f) crabV = -Math.abs(crabV);
            if (crabX < 200f) crabV = Math.abs(crabV);
        }

        void draw(Canvas c, boolean low) {
            // light rays
            FILL.setStyle(Paint.Style.FILL);
            FILL.setColor(0x18FFFFFF);
            for (int i = 0; i < 4; i++) {
                float x = 250f + i * 480f + (float) Math.sin(t * 0.35f + i) * 60f;
                P.reset();
                P.moveTo(x - 40f, 0f);
                P.lineTo(x + 40f, 0f);
                P.lineTo(x + 230f, Stage.H);
                P.lineTo(x - 90f, Stage.H);
                P.close();
                c.drawPath(P, FILL);
            }
            // tiny rising bubbles
            LINE.setColor(0x66FFFFFF);
            for (int i = 0; i < (low ? BITS / 2 : BITS); i++) {
                LINE.setStrokeWidth(2.5f);
                c.drawCircle(bx[i] + (float) Math.sin(t * 1.3f + i) * 14f, by[i], br[i], LINE);
            }
            // drifting fish of several shapes and colours
            for (int i = 0; i < (low ? 3 : FISH); i++) {
                Bitmap b = fish(fc[i], ff[i], fvar[i]);
                float w = 120f * fs[i], h = w * 100f / 160f;
                float y = fy[i] + (float) Math.sin(t * 0.9f + fp[i]) * 18f;
                c.save();
                c.translate(fx[i], y);
                if (fv[i] < 0f) c.scale(-1f, 1f);
                SPRITE.setAlpha(120);
                R.set(-w / 2f, -h / 2f, w / 2f, h / 2f);
                c.drawBitmap(b, null, R, SPRITE);
                SPRITE.setAlpha(255);
                c.restore();
            }
            // the turtle
            if (turtleV != 0f) {
                Bitmap b = turtle();
                float y = turtleY + (float) Math.sin(t * 0.8f) * 14f;
                c.save();
                c.translate(turtleX, y);
                if (turtleV < 0f) c.scale(-1f, 1f);
                SPRITE.setAlpha(150);
                c.drawBitmap(b, -b.getWidth() / 2f * 0.9f, -b.getHeight() / 2f * 0.9f, SPRITE);
                SPRITE.setAlpha(255);
                c.restore();
            }
            // jellyfish
            if (!low) {
                for (int i = 0; i < JELLY; i++) {
                    float x = jx[i] + (float) Math.sin(t * 0.25f + jp[i]) * 120f;
                    float y = jy[i] + (float) Math.sin(t * 0.6f + jp[i]) * 40f;
                    float pulse = 1f + 0.06f * (float) Math.sin(t * 2.2f + jp[i]);
                    FILL.setColor(jc[i]);
                    R.set(x - 55f * pulse, y - 45f, x + 55f * pulse, y + 35f);
                    c.drawArc(R, 180f, 180f, true, FILL);
                    LINE.setColor(jc[i]);
                    LINE.setStrokeWidth(6f);
                    for (int k = -2; k <= 2; k++) {
                        float sx0 = x + k * 20f;
                        P.reset();
                        P.moveTo(sx0, y);
                        P.quadTo(sx0 + (float) Math.sin(t * 2f + k + i) * 12f, y + 40f, sx0, y + 80f);
                        c.drawPath(P, LINE);
                    }
                }
            }
            // seabed, starfish, crab and swaying seaweed
            seabed(c, seabedVariant);
            for (int i = 0; i < STARS; i++) {
                Bitmap b = starfish(scol[i]);
                c.drawBitmap(b, sx[i], Stage.H - 90f + (i % 2) * 18f, null);
            }
            Bitmap cr = crab();
            float scuttle = (float) Math.sin(t * 9f) * 2.5f;
            c.save();
            c.translate(crabX, Stage.H - 112f + scuttle);
            if (crabV < 0f) c.scale(-1f, 1f);
            c.drawBitmap(cr, -cr.getWidth() / 2f, 0f, null);
            c.restore();
            LINE.setStrokeWidth(18f);
            for (int i = 0; i < WEED; i++) {
                LINE.setColor(wcol[i]);
                float sway = (float) Math.sin(t * 1.1f + wp[i]) * 26f;
                P.reset();
                P.moveTo(wx[i], Stage.H - 40f);
                P.quadTo(wx[i] + sway, Stage.H - 40f - wh[i] * 0.5f, wx[i] + sway * 0.6f, Stage.H - 40f - wh[i]);
                c.drawPath(P, LINE);
            }
        }

        private static void seabed(Canvas c, int variant) {
            Bitmap b = SpriteCache.shared().get("bubbles_seabed_" + variant, (int) Stage.W, 150, (cv, w, h) -> {
                java.util.Random r = new java.util.Random(77L + variant);
                FILL.setStyle(Paint.Style.FILL);
                FILL.setColor(0xFFE9D3A0);
                P.reset();
                P.moveTo(0f, h * (0.45f + 0.2f * r.nextFloat()));
                P.quadTo(w * 0.25f, h * (0.15f + 0.2f * r.nextFloat()), w * 0.5f, h * 0.5f);
                P.quadTo(w * 0.75f, h * (0.6f + 0.2f * r.nextFloat()), w, h * (0.25f + 0.2f * r.nextFloat()));
                P.lineTo(w, h);
                P.lineTo(0f, h);
                P.close();
                cv.drawPath(P, FILL);
                FILL.setColor(0xFFD9BC82);
                for (int i = 0; i < 3; i++) {
                    float x = r.nextFloat() * w * 0.8f;
                    R.set(x, h * 0.8f, x + w * (0.12f + 0.15f * r.nextFloat()), h * 1.25f);
                    cv.drawOval(R, FILL);
                }
                int[] colours = {0xFFFF8E9E, 0xFFFFB454, 0xFFB98CFF, 0xFF7FD6C2};
                for (int g = 0; g < 4; g++) {
                    float x = 120f + r.nextFloat() * (w - 300f);
                    FILL.setColor(colours[r.nextInt(colours.length)]);
                    int n = 2 + r.nextInt(3);
                    for (int i = 0; i < n; i++) {
                        R.set(x + i * 22f, h * (0.42f - 0.05f * i), x + i * 22f + 16f, h * 0.65f);
                        cv.drawRoundRect(R, 8f, 8f, FILL);
                    }
                }
                FILL.setColor(0xFF9AA7B0);
                for (int i = 0; i < 2; i++) {
                    float x = r.nextFloat() * (w - 150f);
                    R.set(x, h * 0.50f, x + 40f + r.nextFloat() * 50f, h * 0.74f);
                    cv.drawOval(R, FILL);
                }
            });
            c.drawBitmap(b, 0f, Stage.H - 150f, null);
        }
    }

    // ---- tile picture ----

    /**
     * The picture on the game's menu tile.
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void cover(Canvas c, int w, int h) {
        coverAt(c, w / 2f, h / 2f, Math.min(w, h), 0f);
    }

    /** The tile picture while the tile is highlighted: bubbles bob and the fish swims. */
    public static final TileMenu.LiveIcon LIVE = (c, cx, cy, size, hover, time) -> coverAt(c, cx, cy, size, time);

    private static void coverAt(Canvas c, float cx, float cy, float s, float t) {
        float x0 = cx - s / 2f, y0 = cy - s / 2f;
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(0xFF2A93B8);
        R.set(x0 + s * 0.04f, y0 + s * 0.10f, x0 + s * 0.96f, y0 + s * 0.94f);
        c.drawRoundRect(R, s * 0.12f, s * 0.12f, FILL);
        FILL.setColor(0xFFE9D3A0);
        R.set(x0 + s * 0.04f, y0 + s * 0.80f, x0 + s * 0.96f, y0 + s * 0.94f);
        c.drawRoundRect(R, s * 0.07f, s * 0.07f, FILL);
        float bob = (float) Math.sin(t * 2.4f);
        drawBubble(c, BubbleLogic.BLUE, x0 + s * 0.30f, y0 + s * (0.34f + 0.02f * bob), s * 0.17f, 1f, t);
        drawBubble(c, BubbleLogic.RED, x0 + s * 0.68f, y0 + s * (0.30f - 0.02f * bob), s * 0.15f, 1f, t);
        drawBubble(c, BubbleLogic.GOLD, x0 + s * 0.52f, y0 + s * (0.56f + 0.02f * bob), s * 0.12f, 1f, t);
        float fx = x0 + s * (0.30f + 0.40f * (0.5f + 0.5f * (float) Math.sin(t * 1.2f)));
        drawFish(c, fx, y0 + s * 0.74f, Math.cos(t * 1.2f) >= 0, 0xFF29B6F6, FIN_NEUTRAL, t, s / 330f);
    }
}
