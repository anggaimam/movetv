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
        return SpriteCache.shared().get("bubbles_fish_" + Integer.toHexString(body) + "_" + Integer.toHexString(fin), 160, 100,
                (c, w, h) -> paintFish(c, w, h, body, fin));
    }

    private static void paintFish(Canvas c, int w, int h, int body, int fin) {
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
        R.set(w * 0.22f, h * 0.16f, w * 0.96f, h * 0.84f);
        c.drawOval(R, FILL);
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

    /** The swimming scenery behind the bubbles. */
    static final class Scenery {
        private static final int FISH = 6, JELLY = 2, BITS = 22, WEED = 9;
        private final float[] fx = new float[FISH], fy = new float[FISH], fs = new float[FISH], fv = new float[FISH], fp = new float[FISH];
        private final int[] fc = {0xFFFFB454, 0xFFFFE066, 0xFFB38CFF, 0xFFFF8FA3, 0xFFFFB454, 0xFF7FE3B0};
        private final float[] jx = new float[JELLY], jy = new float[JELLY], jp = new float[JELLY];
        private final float[] bx = new float[BITS], by = new float[BITS], br = new float[BITS], bv = new float[BITS];
        private final float[] wx = new float[WEED], wh = new float[WEED], wp = new float[WEED];
        private float t;

        Scenery() {
            for (int i = 0; i < FISH; i++) {
                fx[i] = (i * 397f) % Stage.W;
                fy[i] = 260f + (i * 131f) % 480f;
                fs[i] = 0.42f + (i % 3) * 0.12f;
                fv[i] = (i % 2 == 0 ? 1f : -1f) * (36f + i * 9f);
                fp[i] = i * 1.7f;
            }
            for (int i = 0; i < JELLY; i++) {
                jx[i] = 400f + i * 1000f;
                jy[i] = 380f + i * 140f;
                jp[i] = i * 2.2f;
            }
            for (int i = 0; i < BITS; i++) {
                bx[i] = (i * 211f) % Stage.W;
                by[i] = (i * 97f) % Stage.H;
                br[i] = 5f + (i % 4) * 3f;
                bv[i] = 38f + (i % 5) * 14f;
            }
            for (int i = 0; i < WEED; i++) {
                wx[i] = 70f + i * 225f + (i % 2) * 40f;
                wh[i] = 140f + (i % 3) * 50f;
                wp[i] = i * 0.9f;
            }
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
                if (by[i] < -20f) {
                    by[i] = Stage.H + 20f;
                }
            }
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
            // drifting fish
            for (int i = 0; i < (low ? 3 : FISH); i++) {
                Bitmap b = fish(fc[i], 0xFFFFFFFF);
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
            // jellyfish
            if (!low) {
                for (int i = 0; i < JELLY; i++) {
                    float x = jx[i] + (float) Math.sin(t * 0.25f + jp[i]) * 120f;
                    float y = jy[i] + (float) Math.sin(t * 0.6f + jp[i]) * 40f;
                    float pulse = 1f + 0.06f * (float) Math.sin(t * 2.2f + jp[i]);
                    FILL.setColor(0x55FFC4E8);
                    R.set(x - 55f * pulse, y - 45f, x + 55f * pulse, y + 35f);
                    c.drawArc(R, 180f, 180f, true, FILL);
                    LINE.setColor(0x55FFC4E8);
                    LINE.setStrokeWidth(6f);
                    for (int k = -2; k <= 2; k++) {
                        float sx = x + k * 20f;
                        P.reset();
                        P.moveTo(sx, y);
                        P.quadTo(sx + (float) Math.sin(t * 2f + k + i) * 12f, y + 40f, sx, y + 80f);
                        c.drawPath(P, LINE);
                    }
                }
            }
            // seabed with swaying seaweed
            seabed(c);
            LINE.setStrokeWidth(18f);
            for (int i = 0; i < WEED; i++) {
                LINE.setColor(i % 2 == 0 ? 0xFF3FAE7A : 0xFF2F9B8A);
                float sway = (float) Math.sin(t * 1.1f + wp[i]) * 26f;
                P.reset();
                P.moveTo(wx[i], Stage.H - 40f);
                P.quadTo(wx[i] + sway, Stage.H - 40f - wh[i] * 0.5f, wx[i] + sway * 0.6f, Stage.H - 40f - wh[i]);
                c.drawPath(P, LINE);
            }
        }

        private static void seabed(Canvas c) {
            Bitmap b = SpriteCache.shared().get("bubbles_seabed", (int) Stage.W, 150, (cv, w, h) -> {
                FILL.setStyle(Paint.Style.FILL);
                FILL.setColor(0xFFE9D3A0);
                P.reset();
                P.moveTo(0f, h * 0.55f);
                P.quadTo(w * 0.25f, h * 0.25f, w * 0.5f, h * 0.5f);
                P.quadTo(w * 0.75f, h * 0.75f, w, h * 0.35f);
                P.lineTo(w, h);
                P.lineTo(0f, h);
                P.close();
                cv.drawPath(P, FILL);
                FILL.setColor(0xFFD9BC82);
                R.set(w * 0.05f, h * 0.78f, w * 0.30f, h * 1.2f);
                cv.drawOval(R, FILL);
                R.set(w * 0.60f, h * 0.8f, w * 0.95f, h * 1.25f);
                cv.drawOval(R, FILL);
                // rocks and coral
                FILL.setColor(0xFF9AA7B0);
                R.set(w * 0.16f, h * 0.45f, w * 0.21f, h * 0.7f);
                cv.drawOval(R, FILL);
                FILL.setColor(0xFFFF8E9E);
                for (int i = 0; i < 3; i++) {
                    R.set(w * (0.82f + i * 0.02f), h * (0.32f - i * 0.04f), w * (0.84f + i * 0.02f), h * 0.6f);
                    cv.drawRoundRect(R, 8f, 8f, FILL);
                }
                FILL.setColor(0xFFFFB454);
                for (int i = 0; i < 3; i++) {
                    R.set(w * (0.44f + i * 0.015f), h * (0.42f - i * 0.03f), w * (0.455f + i * 0.015f), h * 0.62f);
                    cv.drawRoundRect(R, 8f, 8f, FILL);
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
