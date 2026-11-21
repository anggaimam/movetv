package com.wiggleplay.app.games.sky;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.ui.Theme;

import java.util.Random;

/**
 * The pictures of Sky Glider, Flat storybook style: flat fills, a darker flat offset for shadows, no outlines and
 * no gradients. Everything is painted once into {@link SpriteCache} bitmaps; the scene only copies them. The three
 * levels have their own sky: sunny meadow, sunset ocean with islands, starry night. Main thread only.
 */
final class SkyArt {

    /** Where the horizon is on the stage (the sky bands end here). */
    static final float HORIZON = 400f;
    /** Half-size of the background picture: it is painted at half resolution and stretched, flat colours do not mind. */
    private static final int BG_W = 960, BG_H = 540;
    private static final int LAYER_W = 1440;

    /** Number of bank pictures of the plane (-2..2). */
    static final int BANKS = 5;
    /** Plane picture size. */
    static final int PLANE_W = 360, PLANE_H = 220;

    /** Stripe colours of the ground (near, far) per level; the stripes slide toward the plane to show speed. */
    static final int[][] STRIPE = {
            {0xFFA9C4A6, 0xFF9CB89A},
            {0xFF5A8DB8, 0xFF4F80AB},
            {0xFF2B5266, 0xFF224558}};

    private static final Paint F = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint L = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    private static Bitmap ring, coin, star, balloon;
    private static final Bitmap[] BIRD = new Bitmap[2];
    private static final Bitmap[] CLOUD = new Bitmap[3];
    private static final Bitmap[] PLANE = new Bitmap[BANKS];
    private static final Bitmap[] BG = new Bitmap[3];
    private static final Bitmap[] FAR = new Bitmap[3];
    private static final Bitmap[] NEAR = new Bitmap[3];
    private static Bitmap shadow;

    static {
        L.setStyle(Paint.Style.STROKE);
        L.setStrokeCap(Paint.Cap.ROUND);
        L.setStrokeJoin(Paint.Join.ROUND);
    }

    private SkyArt() { }

    /**
     * Paints (once) everything the game needs for a level. Call from the scene's {@code enter}.
     * @param level 1..3
     */
    static void prepare(int level) {
        SpriteCache sc = SpriteCache.shared();
        int i = level - 1;
        if (ring == null) {
            ring = sc.get("sky_ring", 512, 512, SkyArt::paintRing);
            coin = sc.get("sky_coin", 128, 128, SkyArt::paintCoin);
            star = sc.get("sky_star", 192, 192, SkyArt::paintStar);
            balloon = sc.get("sky_balloon", 256, 340, SkyArt::paintBalloon);
            shadow = sc.get("sky_shadow", 256, 96, (c, w, h) -> {
                F.setColor(0x55000000);
                R.set(0, 0, w, h);
                c.drawOval(R, F);
            });
            BIRD[0] = sc.get("sky_bird0", 200, 140, (c, w, h) -> paintBird(c, w, h, false));
            BIRD[1] = sc.get("sky_bird1", 200, 140, (c, w, h) -> paintBird(c, w, h, true));
            CLOUD[0] = sc.get("sky_cloud0", 400, 200, (c, w, h) -> paintCloud(c, w, h, 0));
            CLOUD[1] = sc.get("sky_cloud1", 400, 200, (c, w, h) -> paintCloud(c, w, h, 1));
            CLOUD[2] = sc.get("sky_cloud2", 400, 200, (c, w, h) -> paintCloud(c, w, h, 2));
            for (int b = 0; b < BANKS; b++) {
                final int bank = b - 2;
                PLANE[b] = sc.get("sky_plane" + b, PLANE_W, PLANE_H, (c, w, h) -> paintPlane(c, w, h, bank));
            }
        }
        if (BG[i] == null) {
            final int lv = level;
            BG[i] = sc.get("sky_bg" + level, BG_W, BG_H, (c, w, h) -> paintBackground(c, w, h, lv));
            FAR[i] = sc.get("sky_far" + level, LAYER_W, 150, (c, w, h) -> paintFar(c, w, h, lv));
            NEAR[i] = sc.get("sky_near" + level, LAYER_W, 130, (c, w, h) -> paintNear(c, w, h, lv));
        }
    }

    static Bitmap bg(int level) { return BG[level - 1]; }
    static Bitmap far(int level) { return FAR[level - 1]; }
    static Bitmap near(int level) { return NEAR[level - 1]; }
    static Bitmap ring() { return ring; }
    static Bitmap coin() { return coin; }
    static Bitmap star() { return star; }
    static Bitmap balloon() { return balloon; }
    static Bitmap bird(int frame) { return BIRD[frame & 1]; }
    static Bitmap cloud(int variant) { return CLOUD[variant % 3]; }
    static Bitmap plane(int bank) { return PLANE[Math.max(-2, Math.min(2, bank)) + 2]; }
    static Bitmap shadow() { return shadow; }

    // ---- sprites ----

    /** A hollow ring: the visual radius of the middle of the stroke is 230 of 256. */
    private static void paintRing(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        L.setStrokeWidth(46f);
        L.setColor(0xFFC77C2E);
        c.drawCircle(cx + 6f, cy + 8f, 224f, L);
        L.setColor(Theme.MUSTARD);
        c.drawCircle(cx, cy, 224f, L);
        // orange dashes around the ring
        L.setColor(Theme.ORANGE);
        L.setStrokeWidth(30f);
        R.set(cx - 224f, cy - 224f, cx + 224f, cy + 224f);
        for (int k = 0; k < 12; k++) c.drawArc(R, k * 30f + 4f, 12f, false, L);
        L.setColor(0x66FFFFFF);
        L.setStrokeWidth(10f);
        R.set(cx - 205f, cy - 205f, cx + 205f, cy + 205f);
        c.drawArc(R, 200f, 70f, false, L);
    }

    private static void paintCoin(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        F.setColor(0xFFC99A2E);
        c.drawCircle(cx + 3f, cy + 4f, 58f, F);
        F.setColor(Theme.MUSTARD);
        c.drawCircle(cx, cy, 58f, F);
        F.setColor(0xFFE6B04A);
        c.drawCircle(cx, cy, 40f, F);
        F.setColor(Theme.MUSTARD);
        c.drawCircle(cx, cy, 33f, F);
        F.setColor(0x88FFFFFF);
        c.drawCircle(cx - 20f, cy - 22f, 9f, F);
    }

    private static void starPath(Path p, float cx, float cy, float r) {
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? r : r * 0.48f;
            float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr;
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.close();
    }

    private static void paintStar(Canvas c, int w, int h) {
        Path p = new Path();
        starPath(p, w / 2f + 4f, h / 2f + 8f, 88f);
        F.setColor(0xFFC77C2E);
        c.drawPath(p, F);
        p.rewind();
        starPath(p, w / 2f, h / 2f + 2f, 88f);
        F.setColor(Theme.ORANGE);
        c.drawPath(p, F);
        p.rewind();
        starPath(p, w / 2f, h / 2f + 2f, 62f);
        F.setColor(Theme.MUSTARD);
        c.drawPath(p, F);
        F.setColor(0xAAFFFFFF);
        c.drawCircle(w / 2f - 22f, h / 2f - 28f, 8f, F);
    }

    private static void paintBalloon(Canvas c, int w, int h) {
        float cx = w / 2f;
        L.setStrokeWidth(5f);
        L.setColor(0xFF7A5A3A);
        c.drawLine(cx - 52f, 232f, cx - 26f, 276f, L);
        c.drawLine(cx + 52f, 232f, cx + 26f, 276f, L);
        F.setColor(Theme.BROWN);
        R.set(cx - 32f, 272f, cx + 32f, 322f);
        c.drawRoundRect(R, 10f, 10f, F);
        F.setColor(0xFFC9857A);
        R.set(cx - 124f + 6f, 12f + 8f, cx + 124f + 6f, 252f + 8f);
        c.drawOval(R, F);
        F.setColor(Theme.PINK);
        R.set(cx - 124f, 12f, cx + 124f, 252f);
        c.drawOval(R, F);
        // two flat stripes
        F.setColor(Theme.MUSTARD);
        R.set(cx - 56f, 14f, cx + 56f, 250f);
        c.drawOval(R, F);
        F.setColor(Theme.PINK);
        R.set(cx - 22f, 14f, cx + 22f, 250f);
        c.drawOval(R, F);
        F.setColor(0x77FFFFFF);
        R.set(cx - 96f, 52f, cx - 70f, 112f);
        c.drawOval(R, F);
    }

    private static void paintBird(Canvas c, int w, int h, boolean up) {
        float cx = w / 2f, cy = h / 2f + 14f;
        Path wing = new Path();
        float dy = up ? -62f : 22f;
        wing.moveTo(cx - 18f, cy - 6f);
        wing.lineTo(cx - 92f, cy + dy);
        wing.lineTo(cx - 56f, cy + 8f);
        wing.close();
        wing.moveTo(cx + 18f, cy - 6f);
        wing.lineTo(cx + 92f, cy + dy);
        wing.lineTo(cx + 56f, cy + 8f);
        wing.close();
        F.setColor(Theme.darker(Theme.BLUE));
        c.drawPath(wing, F);
        F.setColor(0xFF4F7FA8);
        R.set(cx - 38f, cy - 26f, cx + 38f, cy + 34f);
        c.drawOval(R, F);
        F.setColor(Theme.BLUE);
        R.set(cx - 38f, cy - 30f, cx + 38f, cy + 26f);
        c.drawOval(R, F);
        F.setColor(Theme.ORANGE);
        Path beak = new Path();
        beak.moveTo(cx - 10f, cy - 16f);
        beak.lineTo(cx + 10f, cy - 16f);
        beak.lineTo(cx, cy - 2f);
        beak.close();
        c.drawPath(beak, F);
        F.setColor(Theme.INK);
        c.drawCircle(cx - 16f, cy - 22f, 5f, F);
        c.drawCircle(cx + 16f, cy - 22f, 5f, F);
    }

    private static final float[][] PUFFS = {
            {0.30f, 0.62f, 0.20f}, {0.45f, 0.45f, 0.26f}, {0.62f, 0.55f, 0.22f}, {0.78f, 0.66f, 0.15f},
            {0.18f, 0.72f, 0.13f}, {0.52f, 0.70f, 0.20f}};

    private static void paintCloud(Canvas c, int w, int h, int variant) {
        float[] flip = {1f, -1f, 1f};
        float sx = flip[variant];
        F.setColor(0xFFCFE1EE);
        for (float[] p : PUFFS) {
            float px = sx > 0 ? p[0] : 1f - p[0];
            c.drawCircle(px * w, p[1] * h + 10f, p[2] * w * (0.9f + 0.1f * variant), F);
        }
        F.setColor(0xFFFFFFFF);
        for (float[] p : PUFFS) {
            float px = sx > 0 ? p[0] : 1f - p[0];
            c.drawCircle(px * w, p[1] * h, p[2] * w * (0.9f + 0.1f * variant), F);
        }
    }

    /**
     * The plane seen from behind: tail fin, body, two wings. Banking tilts the wing tips (the left wing of the
     * screen drops when bank is negative).
     */
    private static void paintPlane(Canvas c, int w, int h, int bank) {
        float cx = w / 2f, cy = h * 0.55f;
        float tipDy = bank * 26f;
        Path wings = new Path();
        wings.moveTo(cx - 20f, cy - 8f);
        wings.lineTo(cx - 168f, cy - 6f - tipDy);
        wings.lineTo(cx - 150f, cy + 22f - tipDy);
        wings.lineTo(cx - 20f, cy + 22f);
        wings.close();
        wings.moveTo(cx + 20f, cy - 8f);
        wings.lineTo(cx + 168f, cy - 6f + tipDy);
        wings.lineTo(cx + 150f, cy + 22f + tipDy);
        wings.lineTo(cx + 20f, cy + 22f);
        wings.close();
        F.setColor(0x33000000);
        c.save();
        c.translate(5f, 8f);
        c.drawPath(wings, F);
        c.restore();
        F.setColor(Theme.MUSTARD);
        c.drawPath(wings, F);
        F.setColor(Theme.ORANGE);
        // wing tips
        Path tips = new Path();
        tips.moveTo(cx - 168f, cy - 6f - tipDy);
        tips.lineTo(cx - 150f, cy + 22f - tipDy);
        tips.lineTo(cx - 128f, cy + 21f - tipDy * 0.85f);
        tips.lineTo(cx - 144f, cy - 6f - tipDy * 0.85f);
        tips.close();
        tips.moveTo(cx + 168f, cy - 6f + tipDy);
        tips.lineTo(cx + 150f, cy + 22f + tipDy);
        tips.lineTo(cx + 128f, cy + 21f + tipDy * 0.85f);
        tips.lineTo(cx + 144f, cy - 6f + tipDy * 0.85f);
        tips.close();
        c.drawPath(tips, F);
        // tail fin
        Path fin = new Path();
        fin.moveTo(cx - 14f, cy - 10f);
        fin.lineTo(cx, cy - 78f);
        fin.lineTo(cx + 14f, cy - 10f);
        fin.close();
        F.setColor(Theme.PINK);
        c.drawPath(fin, F);
        // body
        F.setColor(0xFFC77C2E);
        R.set(cx - 38f + 4f, cy - 44f + 6f, cx + 38f + 4f, cy + 64f + 6f);
        c.drawOval(R, F);
        F.setColor(Theme.ORANGE);
        R.set(cx - 38f, cy - 44f, cx + 38f, cy + 64f);
        c.drawOval(R, F);
        // little pilot head in the canopy
        F.setColor(0xFFF1D3B3);
        c.drawCircle(cx, cy - 18f, 18f, F);
        F.setColor(Theme.BLUE);
        R.set(cx - 22f, cy - 38f, cx + 22f, cy - 14f);
        c.drawArc(R, 180f, 180f, true, F);
        F.setColor(0x88FFFFFF);
        R.set(cx - 22f, cy + 8f, cx - 8f, cy + 40f);
        c.drawOval(R, F);
    }

    // ---- backgrounds ----

    private static final int[][] SKY = {
            {0xFF8EC1E6, 0xFFA7D2EE, 0xFFC0DFF3, 0xFFD9ECF7},
            {0xFF5B5F97, 0xFF8E6FA6, 0xFFD88B8F, 0xFFF4B27A},
            {0xFF121A3A, 0xFF1B2552, 0xFF263470, 0xFF33458A}};
    private static final int[] GROUND = {0xFF9CB89A, 0xFF4F80AB, 0xFF224558};

    private static void paintBackground(Canvas c, int w, int h, int level) {
        float hz = HORIZON / 2f;
        int[] sky = SKY[level - 1];
        float band = hz / 4f;
        for (int i = 0; i < 4; i++) {
            F.setColor(sky[i]);
            c.drawRect(0f, i * band, w, (i + 1) * band + 1f, F);
        }
        F.setColor(GROUND[level - 1]);
        c.drawRect(0f, hz, w, h, F);
        if (level == 1) {
            F.setColor(0xFFFFF0B8);
            c.drawCircle(760f, 100f, 62f, F);
            F.setColor(0xFFFFE08A);
            c.drawCircle(760f, 100f, 46f, F);
        } else if (level == 2) {
            F.setColor(0xFFFFD596);
            c.drawCircle(480f, hz, 120f, F);
            F.setColor(0xFFFFE9A8);
            c.drawCircle(480f, hz, 84f, F);
            F.setColor(GROUND[1]);
            c.drawRect(0f, hz, w, h, F);
        } else {
            Random r = new Random(7);
            F.setColor(0xFFFFFFFF);
            for (int i = 0; i < 70; i++) {
                float sx = r.nextFloat() * w, sy = r.nextFloat() * (hz - 14f);
                F.setAlpha(120 + r.nextInt(135));
                c.drawCircle(sx, sy, 1.5f + r.nextFloat() * 2.5f, F);
            }
            F.setAlpha(255);
            F.setColor(0xFFF6EBC8);
            c.drawCircle(160f, 90f, 40f, F);
            F.setColor(SKY[2][2]);
            c.drawCircle(176f, 80f, 36f, F);
        }
    }

    private static void paintFar(Canvas c, int w, int h, int level) {
        Random r = new Random(level * 11L);
        if (level == 1) {
            F.setColor(0xFFC5D6BE);
            Path p = new Path();
            p.moveTo(0f, h);
            float x = 0f;
            while (x < w) {
                float bw = 160f + r.nextFloat() * 160f, bh = 40f + r.nextFloat() * 60f;
                p.quadTo(x + bw / 2f, h - bh * 2f, x + bw, h - 20f);
                x += bw;
            }
            p.lineTo(w, h);
            p.close();
            c.drawPath(p, F);
        } else if (level == 2) {
            // islands with palm trees
            for (int i = 0; i < 6; i++) {
                float cx = 120f + i * 240f + r.nextFloat() * 60f, wd = 90f + r.nextFloat() * 60f;
                F.setColor(0xFF2F5A5A);
                R.set(cx - wd, h - 44f, cx + wd, h + 40f);
                c.drawOval(R, F);
                F.setColor(0xFF8F6B43);
                c.drawRect(cx - 4f, h - 90f, cx + 4f, h - 30f, F);
                F.setColor(0xFF3F7F5A);
                R.set(cx - 38f, h - 118f, cx + 38f, h - 80f);
                c.drawOval(R, F);
            }
        } else {
            F.setColor(0xFF1A2650);
            Path p = new Path();
            p.moveTo(0f, h);
            float x = 0f;
            while (x < w) {
                float bw = 130f + r.nextFloat() * 130f, bh = 50f + r.nextFloat() * 80f;
                p.lineTo(x + bw / 2f, h - bh);
                p.lineTo(x + bw, h - 10f);
                x += bw;
            }
            p.lineTo(w, h);
            p.close();
            c.drawPath(p, F);
        }
    }

    private static void paintNear(Canvas c, int w, int h, int level) {
        Random r = new Random(level * 29L);
        int col = level == 1 ? 0xFF8DAE8B : level == 2 ? 0xFF3E7BA8 : 0xFF18354A;
        F.setColor(col);
        Path p = new Path();
        p.moveTo(0f, h);
        float x = 0f;
        while (x < w) {
            float bw = 220f + r.nextFloat() * 220f, bh = level == 2 ? 14f : 40f + r.nextFloat() * 50f;
            p.quadTo(x + bw / 2f, h - bh * 2f, x + bw, h - 10f);
            x += bw;
        }
        p.lineTo(w, h);
        p.close();
        c.drawPath(p, F);
        if (level == 1) {
            // a few round flat trees on the hills
            F.setColor(0xFF6FA06B);
            for (int i = 0; i < 10; i++) {
                float tx = 80f + i * 140f + r.nextFloat() * 50f;
                c.drawCircle(tx, h - 34f - r.nextFloat() * 24f, 18f + r.nextFloat() * 8f, F);
            }
        }
    }
}
