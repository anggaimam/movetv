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
public final class SkyArt {

    /** Where the horizon is on the stage (the sky bands end here). */
    static final float HORIZON = 400f;
    /** Half-size of the background picture: it is painted at half resolution and stretched, flat colours do not mind. */
    private static final int BG_W = 960, BG_H = 540;
    private static final int LAYER_W = 1440;

    /** Stripe colours of the ground (near, far) per level; the stripes slide toward the plane to show speed. */
    static final int[][] STRIPE = {
            {0xFFA9C4A6, 0xFF9CB89A},
            {0xFF5A8DB8, 0xFF4F80AB},
            {0xFF2B5266, 0xFF224558}};

    private static final Paint F = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint L = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    private static Bitmap ring, coin, star, balloon, magnet;
    private static final Bitmap[] BIRD = new Bitmap[2];
    private static final Bitmap[] CLOUD = new Bitmap[3];
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

    /** Lets go of every sky picture (called when the game is left); {@link #prepare} paints them again. */
    static void unload() {
        for (Bitmap[] a : new Bitmap[][]{BIRD, CLOUD, BG, FAR, NEAR}) java.util.Arrays.fill(a, null);
        ring = coin = star = balloon = magnet = shadow = null;
        SpriteCache.shared().removePrefix("sky_");
        SpriteCache.shared().removePrefix("tile_");
        SpriteCache.shared().removePrefix("tileicon_");
    }

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
            magnet = sc.get("sky_magnet", 192, 192, SkyArt::paintMagnet);
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
    static Bitmap magnet() { return magnet; }
    static Bitmap balloon() { return balloon; }
    static Bitmap bird(int frame) { return BIRD[frame & 1]; }
    static Bitmap cloud(int variant) { return CLOUD[variant % 3]; }
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

    /** A red horseshoe magnet with silver tips, opening downward. */
    public static void paintMagnet(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h * 0.5f, r = 48f;
        for (int pass = 0; pass < 2; pass++) {
            float o = pass == 0 ? 5f : 0f;
            L.setStrokeCap(Paint.Cap.BUTT);
            L.setStrokeWidth(34f);
            L.setColor(pass == 0 ? 0xFF9C2F2A : 0xFFE8504A);
            R.set(cx - r + o, cy - r - 10f + o, cx + r + o, cy + r - 10f + o);
            c.drawArc(R, 180f, 180f, false, L);
            c.drawLine(cx - r + o, cy - 10f + o, cx - r + o, cy + 40f + o, L);
            c.drawLine(cx + r + o, cy - 10f + o, cx + r + o, cy + 40f + o, L);
        }
        L.setColor(0xFFE3E8EE);
        L.setStrokeWidth(34f);
        c.drawLine(cx - r, cy + 40f, cx - r, cy + 70f, L);
        c.drawLine(cx + r, cy + 40f, cx + r, cy + 70f, L);
        L.setStrokeCap(Paint.Cap.ROUND);
        L.setColor(0x66FFFFFF);
        L.setStrokeWidth(8f);
        R.set(cx - r, cy - r - 10f, cx + r, cy + r - 10f);
        c.drawArc(R, 200f, 60f, false, L);
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
        Random r = new Random(variant * 13L + 3L);
        float k = 0.75f * (0.9f + 0.1f * variant);
        // shadow under the cloud, the cloud, then a few small bright puffs on the upper left of each ball
        F.setColor(0xFFC3D7E6);
        for (float[] p : PUFFS) {
            float px = sx > 0 ? p[0] : 1f - p[0];
            c.drawCircle(px * w, p[1] * h * 0.8f + 10f, p[2] * w * k, F);
        }
        F.setColor(0xFFF2F7FB);
        for (float[] p : PUFFS) {
            float px = sx > 0 ? p[0] : 1f - p[0];
            c.drawCircle(px * w, p[1] * h * 0.8f, p[2] * w * k, F);
        }
        F.setColor(0xFFFFFFFF);
        for (float[] p : PUFFS) {
            float px = sx > 0 ? p[0] : 1f - p[0];
            float rr = p[2] * w * k;
            c.drawCircle(px * w - rr * (0.15f + 0.1f * r.nextFloat()), p[1] * h * 0.8f - rr * 0.18f, rr * 0.78f, F);
        }
    }

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
        Random r = new Random(level * 101L);
        if (level == 1) {
            // a soft glow round the sun, then the sun
            F.setColor(0x33FFF6D0);
            c.drawCircle(760f, 100f, 110f, F);
            F.setColor(0x44FFF0B8);
            c.drawCircle(760f, 100f, 84f, F);
            F.setColor(0xFFFFF0B8);
            c.drawCircle(760f, 100f, 62f, F);
            F.setColor(0xFFFFE08A);
            c.drawCircle(760f, 100f, 46f, F);
            // far thin cloud streaks near the horizon
            for (int i = 0; i < 7; i++) {
                float y = hz - 18f - r.nextFloat() * 60f, x = r.nextFloat() * w, len = 60f + r.nextFloat() * 120f;
                F.setColor(0x66FFFFFF);
                R.set(x, y, x + len, y + 8f + r.nextFloat() * 6f);
                c.drawRoundRect(R, 8f, 8f, F);
            }
            for (int i = 0; i < 3; i++) softCloud(c, 80f + i * 300f + r.nextFloat() * 120f, 40f + r.nextFloat() * 50f,
                    0.5f + r.nextFloat() * 0.4f, r);
        } else if (level == 2) {
            F.setColor(0x33FFE0A0);
            c.drawCircle(480f, hz, 170f, F);
            F.setColor(0xFFFFD596);
            c.drawCircle(480f, hz, 120f, F);
            F.setColor(0xFFFFE9A8);
            c.drawCircle(480f, hz, 84f, F);
            F.setColor(GROUND[1]);
            c.drawRect(0f, hz, w, h, F);
            // long sunset clouds lit from below
            for (int i = 0; i < 6; i++) {
                float y = 30f + r.nextFloat() * (hz - 80f), x = r.nextFloat() * w, len = 90f + r.nextFloat() * 160f;
                F.setColor(0x88B5729A);
                R.set(x, y, x + len, y + 12f);
                c.drawRoundRect(R, 6f, 6f, F);
                F.setColor(0x99FFC59A);
                R.set(x + 10f, y + 8f, x + len - 6f, y + 13f);
                c.drawRoundRect(R, 3f, 3f, F);
            }
            // a few far birds
            L.setColor(0xFF5A4060);
            L.setStrokeWidth(2.5f);
            for (int i = 0; i < 5; i++) {
                float x = 560f + r.nextFloat() * 300f, y = 40f + r.nextFloat() * 90f, s = 5f + r.nextFloat() * 4f;
                c.drawLine(x - s, y - s * 0.5f, x, y, L);
                c.drawLine(x, y, x + s, y - s * 0.5f, L);
            }
        } else {
            for (int i = 0; i < 150; i++) {
                float sx = r.nextFloat() * w, sy = r.nextFloat() * (hz - 14f);
                F.setColor(i % 9 == 0 ? 0xFFFFE6B0 : i % 7 == 0 ? 0xFFB8D0FF : 0xFFFFFFFF);
                F.setAlpha(90 + r.nextInt(165));
                float rad = 0.8f + r.nextFloat() * r.nextFloat() * 3.2f;
                c.drawCircle(sx, sy, rad, F);
                if (rad > 2.6f) {
                    // a twinkle cross on the brightest stars
                    L.setColor(0x88FFFFFF);
                    L.setStrokeWidth(1.2f);
                    c.drawLine(sx - rad * 2.6f, sy, sx + rad * 2.6f, sy, L);
                    c.drawLine(sx, sy - rad * 2.6f, sx, sy + rad * 2.6f, L);
                }
            }
            F.setAlpha(255);
            F.setColor(0x22F6EBC8);
            c.drawCircle(160f, 90f, 70f, F);
            F.setColor(0xFFF6EBC8);
            c.drawCircle(160f, 90f, 40f, F);
            F.setColor(0xFFE3D6AE);
            c.drawCircle(146f, 98f, 7f, F);
            c.drawCircle(158f, 112f, 4f, F);
            c.drawCircle(140f, 78f, 4f, F);
            F.setColor(SKY[2][2]);
            c.drawCircle(176f, 80f, 36f, F);
        }
    }

    private static void softCloud(Canvas c, float x, float y, float s, Random r) {
        F.setColor(0x55FFFFFF);
        for (int i = 0; i < 4; i++) c.drawCircle(x + i * 26f * s, y - (i == 1 || i == 2 ? 10f : 0f) * s, (20f + r.nextFloat() * 10f) * s, F);
        F.setColor(0x88FFFFFF);
        R.set(x - 10f * s, y, x + 90f * s, y + 18f * s);
        c.drawRoundRect(R, 9f * s, 9f * s, F);
    }

    // ---- layered trees, palms and pines (shared with the menu cover) ----

    /** Tree colours: trunk, dark, middle, light. */
    static final int[] TREE_FAR1 = {0xFF9A8A70, 0xFF8DAE88, 0xFF9FBF98, 0xFFB5D0AC};
    static final int[] TREE_NEAR1 = {0xFF7A5534, 0xFF4F7F4E, 0xFF6FA06B, 0xFF93C185};
    private static final int[] PINE_FAR1 = {0xFF9A8A70, 0xFF86A884, 0xFF98B994, 0xFFAECBA8};
    private static final int[] PINE_NEAR1 = {0xFF6A4A2E, 0xFF3F6E4A, 0xFF5A8E60, 0xFF7AAE78};
    private static final int[] PINE_NIGHT = {0xFF0B1722, 0xFF0D2233, 0xFF163A50, 0xFF20506A};
    private static final int[] PINE_NIGHT_FAR = {0xFF141E40, 0xFF172552, 0xFF1D2E62, 0xFF263A76};
    private static final int[] PALM = {0xFF8F6B43, 0xFF2F6A4A, 0xFF3F7F5A, 0xFF5A9A6A};

    /**
     * A round tree made of overlapping balls in three shades (dark at the bottom right, light at the top left).
     * @param c canvas
     * @param x middle of the trunk
     * @param gy ground under the tree
     * @param s size (about the canopy radius)
     * @param r random numbers, so no two trees look the same
     * @param pal trunk, dark, middle, light
     */
    static void tree(Canvas c, float x, float gy, float s, Random r, int[] pal) {
        float cy = gy - s * 1.55f;
        F.setColor(pal[0]);
        R.set(x - s * 0.13f, cy, x + s * 0.13f, gy);
        c.drawRect(R, F);
        F.setColor(pal[1]);
        for (int i = 0; i < 4; i++) {
            c.drawCircle(x + (r.nextFloat() - 0.4f) * s * 0.8f, cy + (r.nextFloat() - 0.3f) * s * 0.6f,
                    s * (0.55f + r.nextFloat() * 0.2f), F);
        }
        F.setColor(pal[2]);
        for (int i = 0; i < 3; i++) {
            c.drawCircle(x + (r.nextFloat() - 0.65f) * s * 0.7f, cy + (r.nextFloat() - 0.8f) * s * 0.5f,
                    s * (0.45f + r.nextFloat() * 0.15f), F);
        }
        F.setColor(pal[3]);
        for (int i = 0; i < 2; i++) {
            c.drawCircle(x - s * (0.2f + r.nextFloat() * 0.25f), cy - s * (0.3f + r.nextFloat() * 0.2f),
                    s * (0.18f + r.nextFloat() * 0.12f), F);
        }
    }

    /** A pine: three stacked triangles, the left half of each a shade lighter. */
    static void pine(Canvas c, float x, float gy, float s, Random r, int[] pal) {
        F.setColor(pal[0]);
        R.set(x - s * 0.1f, gy - s * 0.5f, x + s * 0.1f, gy);
        c.drawRect(R, F);
        float tall = s * (2.6f + r.nextFloat() * 0.8f);
        Path p = new Path();
        for (int k = 0; k < 3; k++) {
            float top = gy - s * 0.35f - tall * (0.45f + 0.27f * k) - s * 0.2f;
            float bot = gy - s * 0.35f - tall * 0.27f * k;
            float hw = s * (0.85f - 0.2f * k);
            p.reset();
            p.moveTo(x, top);
            p.lineTo(x + hw, bot);
            p.lineTo(x - hw, bot);
            p.close();
            F.setColor(pal[1]);
            c.drawPath(p, F);
            p.reset();
            p.moveTo(x, top);
            p.lineTo(x - hw, bot);
            p.lineTo(x - hw * 0.1f, bot);
            p.close();
            F.setColor(pal[2]);
            c.drawPath(p, F);
        }
        F.setColor(pal[3]);
        c.drawCircle(x - s * 0.1f, gy - s * 0.35f - tall * 1.0f, s * 0.12f, F);
    }

    /** A low bush of a few balls. */
    private static void bush(Canvas c, float x, float gy, float s, Random r, int[] pal) {
        F.setColor(pal[1]);
        for (int i = 0; i < 3; i++) c.drawCircle(x + (i - 1) * s * 0.6f, gy - s * 0.35f, s * (0.5f + r.nextFloat() * 0.15f), F);
        F.setColor(pal[2]);
        c.drawCircle(x - s * 0.35f, gy - s * 0.6f, s * 0.4f, F);
        c.drawCircle(x + s * 0.2f, gy - s * 0.65f, s * 0.32f, F);
        F.setColor(pal[3]);
        c.drawCircle(x - s * 0.45f, gy - s * 0.78f, s * 0.15f, F);
    }

    /** A palm: a bent trunk of rings and drooping leaves in two greens. */
    private static void palm(Canvas c, float x, float gy, float s, Random r) {
        float lean = (r.nextFloat() - 0.5f) * s * 1.2f, tall = s * (2.6f + r.nextFloat());
        float tx = x + lean, ty = gy - tall;
        for (int i = 0; i <= 10; i++) {
            float t = i / 10f;
            float px = x + lean * t * t, py = gy - tall * t;
            F.setColor((i & 1) == 0 ? PALM[0] : 0xFF7A5A36);
            c.drawCircle(px, py, s * (0.16f - 0.05f * t), F);
        }
        Path leaf = new Path();
        for (int pass = 0; pass < 2; pass++) {
            F.setColor(pass == 0 ? PALM[1] : PALM[2]);
            for (int k = 0; k < 6; k++) {
                float a = (float) (Math.PI * (0.05f + k * 0.18f)) + pass * 0.12f;
                float dx = (float) Math.cos(a) * s * 1.3f * (pass == 0 ? 1f : 0.85f), dy = -(float) Math.sin(a) * s * 0.5f;
                leaf.reset();
                leaf.moveTo(tx, ty);
                leaf.quadTo(tx + dx * 0.5f, ty + dy - s * 0.35f, tx + dx, ty + dy + s * 0.45f);
                leaf.quadTo(tx + dx * 0.55f, ty + dy - s * 0.05f, tx, ty + s * 0.08f);
                c.drawPath(leaf, F);
            }
        }
        F.setColor(0xFF6B4A2A);
        c.drawCircle(tx - s * 0.1f, ty + s * 0.12f, s * 0.11f, F);
        c.drawCircle(tx + s * 0.1f, ty + s * 0.14f, s * 0.11f, F);
    }

    // ---- rolling hills: the bumps are kept so things can stand on them ----

    private static final float[] BX = new float[64], BW = new float[64], BH = new float[64];
    private static int bumps;
    private static float bumpBase;

    private static Path hills(int w, int h, float base, float minW, float maxW, float minH, float maxH, Random r) {
        Path p = new Path();
        p.moveTo(0f, h);
        p.lineTo(0f, base);
        bumps = 0;
        bumpBase = base;
        float x = -40f * r.nextFloat();
        p.lineTo(x, base);
        while (x < w && bumps < 64) {
            float bw = minW + r.nextFloat() * (maxW - minW), bh = minH + r.nextFloat() * (maxH - minH);
            BX[bumps] = x;
            BW[bumps] = bw;
            BH[bumps] = bh;
            bumps++;
            p.quadTo(x + bw / 2f, base - bh * 2f, x + bw, base);
            x += bw;
        }
        p.lineTo(w, h);
        p.close();
        return p;
    }

    private static float hillY(float x) {
        for (int i = 0; i < bumps; i++) {
            if (x >= BX[i] && x <= BX[i] + BW[i]) {
                float t = (x - BX[i]) / BW[i];
                return bumpBase - 4f * BH[i] * t * (1f - t);
            }
        }
        return bumpBase;
    }

    /** Fills hills with a lighter rim along their tops (the light comes from the upper left). */
    private static void fillHills(Canvas c, Path p, int col, int light, float rim) {
        F.setColor(light);
        c.drawPath(p, F);
        c.save();
        c.clipPath(p);
        c.translate(rim * 0.6f, rim);
        F.setColor(col);
        c.drawPath(p, F);
        c.restore();
    }

    private static void paintFar(Canvas c, int w, int h, int level) {
        Random r = new Random(level * 11L);
        if (level == 1) {
            Path back = hills(w, h, h - 58f, 140f, 260f, 14f, 34f, r);
            fillHills(c, back, 0xFFD3E0CB, 0xFFDDE8D6, 4f);
            for (float x = 10f; x < w; x += 8f + r.nextFloat() * 30f) {
                if (r.nextFloat() < 0.35f) continue;
                float y = hillY(x) + 2f, s = 3f + r.nextFloat() * 3f;
                F.setColor(0xFFB9CDB2);
                c.drawCircle(x, y - s, s, F);
                F.setColor(0xFFC6D8BF);
                c.drawCircle(x - s * 0.3f, y - s * 1.3f, s * 0.6f, F);
            }
            Path front = hills(w, h, h - 12f, 160f, 320f, 22f, 52f, r);
            fillHills(c, front, 0xFFC0D3B9, 0xFFCCDDC5, 5f);
            float x = 20f;
            while (x < w) {
                int group = 1 + r.nextInt(4);
                for (int k = 0; k < group && x < w; k++) {
                    float s = 8f + r.nextFloat() * 7f;
                    float y = hillY(x) + 3f;
                    if (r.nextFloat() < 0.3f) pine(c, x, y, s * 0.75f, r, PINE_FAR1);
                    else tree(c, x, y, s, r, TREE_FAR1);
                    x += s * (1.1f + r.nextFloat());
                }
                x += 40f + r.nextFloat() * 120f;
            }
        } else if (level == 2) {
            // hazy far islands, then near islands with sand, palms and bushes, and a couple of sailboats
            for (int i = 0; i < 5; i++) {
                float cx = r.nextFloat() * w, wd = 60f + r.nextFloat() * 90f;
                F.setColor(0xFF6A6F98);
                R.set(cx - wd, h - 26f - r.nextFloat() * 10f, cx + wd, h + 30f);
                c.drawOval(R, F);
            }
            for (int i = 0; i < 6; i++) {
                float cx = 100f + i * 240f + r.nextFloat() * 80f, wd = 80f + r.nextFloat() * 70f, ht = 34f + r.nextFloat() * 22f;
                Path isl = new Path();
                R.set(cx - wd, h - ht, cx + wd, h + ht);
                isl.addOval(R, Path.Direction.CW);
                fillHills(c, isl, 0xFF2F5A5A, 0xFF3F6E66, 5f);
                F.setColor(0xFFE8C48E);
                R.set(cx - wd * 1.02f, h - 8f, cx + wd * 1.02f, h + 6f);
                c.drawOval(R, F);
                int palms = 1 + r.nextInt(3);
                for (int k = 0; k < palms; k++) palm(c, cx + (r.nextFloat() - 0.5f) * wd, h - ht * 0.6f, 14f + r.nextFloat() * 8f, r);
                bush(c, cx - wd * 0.4f, h - ht * 0.35f, 9f, r, PALM);
                bush(c, cx + wd * 0.35f, h - ht * 0.3f, 8f, r, PALM);
            }
            for (int i = 0; i < 2; i++) {
                float bx = 300f + i * 600f + r.nextFloat() * 200f, by = h - 6f;
                F.setColor(0xFF6B4A3A);
                R.set(bx - 16f, by - 6f, bx + 16f, by + 2f);
                c.drawRoundRect(R, 4f, 4f, F);
                Path sail = new Path();
                sail.moveTo(bx, by - 40f);
                sail.lineTo(bx + 14f, by - 8f);
                sail.lineTo(bx, by - 8f);
                sail.close();
                F.setColor(0xFFF6E8D0);
                c.drawPath(sail, F);
                sail.reset();
                sail.moveTo(bx - 2f, by - 34f);
                sail.lineTo(bx - 2f, by - 8f);
                sail.lineTo(bx - 14f, by - 8f);
                sail.close();
                F.setColor(0xFFE8B6A0);
                c.drawPath(sail, F);
            }
        } else {
            // two moonlit mountain ranges: the faces toward the moon (left) are lighter, the high peaks have snow
            for (int range = 0; range < 2; range++) {
                int body = range == 0 ? 0xFF233066 : 0xFF1A2650;
                int lit = range == 0 ? 0xFF2D3D7A : 0xFF22306A;
                float base = range == 0 ? h - 30f : h - 6f;
                float x = -60f * r.nextFloat();
                while (x < w) {
                    float bw = 110f + r.nextFloat() * 150f, bh = (range == 0 ? 70f : 45f) + r.nextFloat() * 60f;
                    float px = x + bw * (0.4f + r.nextFloat() * 0.2f), py = base - bh;
                    Path m = new Path();
                    m.moveTo(x, base + 10f);
                    m.lineTo(px, py);
                    m.lineTo(x + bw, base + 10f);
                    m.close();
                    F.setColor(body);
                    c.drawPath(m, F);
                    m.reset();
                    m.moveTo(x, base + 10f);
                    m.lineTo(px, py);
                    m.lineTo(px - bw * 0.05f, base + 10f);
                    m.close();
                    F.setColor(lit);
                    c.drawPath(m, F);
                    if (bh > 80f) {
                        float k = 0.22f;
                        m.reset();
                        m.moveTo(px, py);
                        m.lineTo(px + (x + bw - px) * k, py + bh * k);
                        m.lineTo(px + (x + bw - px) * k * 0.4f, py + bh * k * 0.75f);
                        m.lineTo(px, py + bh * k * 1.05f);
                        m.lineTo(px - (px - x) * k * 0.5f, py + bh * k * 0.7f);
                        m.lineTo(px - (px - x) * k, py + bh * k);
                        m.close();
                        F.setColor(range == 0 ? 0xFF9AA8D2 : 0xFF8090C0);
                        c.drawPath(m, F);
                    }
                    x += bw * (0.55f + r.nextFloat() * 0.3f);
                }
            }
            for (float x = 5f; x < w; x += 6f + r.nextFloat() * 22f) {
                if (r.nextFloat() < 0.4f) continue;
                pine(c, x, h + 2f, 4f + r.nextFloat() * 3f, r, PINE_NIGHT_FAR);
            }
        }
    }

    private static void paintNear(Canvas c, int w, int h, int level) {
        Random r = new Random(level * 29L);
        if (level == 1) {
            Path p = hills(w, h, h - 10f, 220f, 440f, 22f, 48f, r);
            fillHills(c, p, 0xFF8DAE8B, 0xFF9EBF98, 6f);
            float x = 30f;
            while (x < w) {
                float pick = r.nextFloat();
                float y = hillY(x) + 4f;
                if (pick < 0.45f) {
                    tree(c, x, y, 14f + r.nextFloat() * 10f, r, TREE_NEAR1);
                    x += 30f + r.nextFloat() * 40f;
                } else if (pick < 0.65f) {
                    pine(c, x, y, 11f + r.nextFloat() * 6f, r, PINE_NEAR1);
                    x += 24f + r.nextFloat() * 30f;
                } else if (pick < 0.85f) {
                    bush(c, x, y, 9f + r.nextFloat() * 6f, r, TREE_NEAR1);
                    x += 20f + r.nextFloat() * 30f;
                } else {
                    x += 60f + r.nextFloat() * 120f;
                }
            }
            // tiny flowers in the grass
            int[] flowers = {0xFFFFFFFF, 0xFFF6C0CF, 0xFFFFE08A};
            for (int i = 0; i < 160; i++) {
                float fx = r.nextFloat() * w, fy = hillY(fx) + 8f + r.nextFloat() * (h - hillY(fx) - 8f);
                F.setColor(flowers[i % 3]);
                c.drawCircle(fx, fy, 1.5f + r.nextFloat() * 1.5f, F);
            }
        } else if (level == 2) {
            // a wave band with lighter crests and foam
            Path p = hills(w, h, h - 10f, 50f, 110f, 3f, 8f, r);
            fillHills(c, p, 0xFF3E7BA8, 0xFF5A93BE, 4f);
            L.setStrokeWidth(3f);
            for (int i = 0; i < 70; i++) {
                float x = r.nextFloat() * w, y = h - 40f + r.nextFloat() * 36f, len = 12f + r.nextFloat() * 28f;
                L.setColor(r.nextFloat() < 0.3f ? 0xCCFFFFFF : 0xFF7FB0D2);
                R.set(x, y, x + len, y + 10f);
                c.drawArc(R, 200f, 140f, false, L);
            }
        } else {
            Path p = hills(w, h, h - 8f, 200f, 380f, 20f, 46f, r);
            fillHills(c, p, 0xFF18354A, 0xFF1F4258, 5f);
            float x = 10f;
            while (x < w) {
                float y = hillY(x) + 4f;
                if (r.nextFloat() < 0.8f) pine(c, x, y, 8f + r.nextFloat() * 8f, r, PINE_NIGHT);
                x += 10f + r.nextFloat() * (r.nextFloat() < 0.15f ? 160f : 26f);
            }
            // a little cabin with a warm window
            float cx = 380f + r.nextFloat() * 500f, cy = hillY(cx) + 2f;
            F.setColor(0xFF2A2230);
            R.set(cx - 18f, cy - 22f, cx + 18f, cy);
            c.drawRect(R, F);
            Path roof = new Path();
            roof.moveTo(cx - 24f, cy - 20f);
            roof.lineTo(cx, cy - 38f);
            roof.lineTo(cx + 24f, cy - 20f);
            roof.close();
            F.setColor(0xFF3A2E40);
            c.drawPath(roof, F);
            F.setColor(0x55FFD27A);
            c.drawCircle(cx - 6f, cy - 11f, 12f, F);
            F.setColor(0xFFFFD27A);
            R.set(cx - 10f, cy - 15f, cx - 2f, cy - 7f);
            c.drawRect(R, F);
        }
    }
}
