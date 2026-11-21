package com.angga.movetv.games.fruit;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.angga.movetv.core.SpriteCache;
import com.angga.movetv.core.Stage;

import java.util.Random;

/**
 * The "aurora orchard": the themed background of Fruit Splash when the camera picture is switched off. A flat deep
 * blue night sky with twinkling stars and a moon, three soft aurora ribbons (teal, green, violet) that drift
 * sideways and slowly breathe, and dark rolling hills with round fruit trees and a few glowing fruit. The ribbons,
 * the moon and the landscape are painted once into bitmaps (soft edges and gradients are fine there); each frame
 * draws only five bitmaps and the star twinkles. Nothing is allocated per frame.
 */
final class AuroraBackdrop {

    private static final int SKY = 0xFF0B1530;
    private static final int[] RIBBON = {0xFF4FE3C1, 0xFF7CF29A, 0xFFB58CFF};
    private static final float[] DRIFT_PERIOD = {11f, 9f, 13f};
    private static final float[] BREATH_PERIOD = {8f, 12f, 14f};
    private static final float[] RIBBON_Y = {70f, 140f, 40f};
    private static final float RIBBON_SCALE = 2f; // baked at half size, drawn twice as big
    private static final int RIBBON_W = 1020, RIBBON_H = 220;
    private static final int LAND_W = 960, LAND_H = 540; // baked at half size too
    private static final int STARS = 70;

    private final float[] sx = new float[STARS], sy = new float[STARS], sPhase = new float[STARS],
            sSize = new float[STARS], sSpeed = new float[STARS];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Bitmap moon, landscape;
    private final Bitmap[] ribbon = new Bitmap[3];

    /** Creates the backdrop (the pictures are painted the first time it is drawn). */
    AuroraBackdrop() {
        Random r = new Random(11L);
        for (int i = 0; i < STARS; i++) {
            sx[i] = r.nextFloat() * Stage.W;
            sy[i] = r.nextFloat() * 520f;
            sPhase[i] = r.nextFloat() * 6.28f;
            sSize[i] = 2f + r.nextFloat() * 3.2f;
            sSpeed[i] = 1.2f + r.nextFloat() * 2.2f;
        }
    }

    private void bake() {
        SpriteCache sc = SpriteCache.shared();
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            ribbon[i] = sc.get("aurora_ribbon_" + i, RIBBON_W, RIBBON_H, (c, w, h) -> paintRibbon(c, w, h, idx));
        }
        moon = sc.get("aurora_moon", 220, 220, AuroraBackdrop::paintMoon);
        landscape = sc.get("aurora_orchard", LAND_W, LAND_H, AuroraBackdrop::paintLandscape);
    }

    private static void paintRibbon(Canvas c, int w, int h, int idx) {
        Random r = new Random(100 + idx);
        Path p = new Path();
        float phase = r.nextFloat() * 6.28f;
        float amp = 22f + r.nextFloat() * 14f;
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            float x = i * (w / (float) steps);
            float y = h * 0.35f + (float) Math.sin(phase + i * 0.38f) * amp + (float) Math.sin(phase * 2f + i * 0.17f) * amp * 0.6f;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        for (int i = steps; i >= 0; i--) {
            float x = i * (w / (float) steps);
            float y = h * 0.62f + (float) Math.sin(phase + i * 0.38f + 0.7f) * amp * 1.2f
                    + (float) Math.sin(phase * 2f + i * 0.17f) * amp * 0.5f;
            p.lineTo(x, y);
        }
        p.close();
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setStyle(Paint.Style.FILL);
        int col = RIBBON[idx];
        f.setShader(new LinearGradient(0f, 0f, 0f, h, new int[] {col & 0x00FFFFFF, col, col & 0x00FFFFFF},
                new float[] {0.1f, 0.5f, 0.95f}, Shader.TileMode.CLAMP));
        f.setMaskFilter(new BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(p, f);
        // soft side fade so the ribbon has no hard end
        Paint fade = new Paint();
        fade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        fade.setShader(new LinearGradient(0f, 0f, w, 0f, new int[] {0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                new float[] {0f, 0.12f, 0.88f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, fade);
    }

    private static void paintMoon(Canvas c, int w, int h) {
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setShader(new RadialGradient(w / 2f, h / 2f, w / 2f, new int[] {0x66FFF6D8, 0x00FFF6D8},
                new float[] {0.3f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(w / 2f, h / 2f, w / 2f, f);
        f.setShader(null);
        f.setColor(0xFFFFF3CF);
        c.drawCircle(w / 2f, h / 2f, 44f, f);
        f.setColor(0xFFF1DFAE);
        c.drawCircle(w / 2f + 14f, h / 2f - 12f, 9f, f);
        c.drawCircle(w / 2f - 16f, h / 2f + 14f, 12f, f);
    }

    private static void paintLandscape(Canvas c, int w, int h) {
        // drawn in a 1920x1080 space and shrunk by half
        c.scale(w / Stage.W, h / Stage.H);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setStyle(Paint.Style.FILL);
        hills(c, f, 0xFF16264A, 690f, 70f, 0.0045f, 1f);
        hills(c, f, 0xFF101D3B, 790f, 60f, 0.0062f, 2.4f);
        Random r = new Random(5L);
        // far trees on the middle hill
        for (int i = 0; i < 9; i++) {
            float x = 120f + i * 215f + r.nextFloat() * 60f;
            tree(c, f, x, 800f + r.nextFloat() * 20f, 0.55f + r.nextFloat() * 0.15f, 0xFF0D1830, false, r);
        }
        hills(c, f, 0xFF0A1327, 900f, 55f, 0.0075f, 4.1f);
        // near trees with glowing fruit
        float[] tx = {170f, 520f, 900f, 1330f, 1730f};
        float[] ts = {1.0f, 0.85f, 1.1f, 0.9f, 1.05f};
        for (int i = 0; i < tx.length; i++) tree(c, f, tx[i], 960f, ts[i], 0xFF070D1C, true, r);
        f.setColor(0xFF050A16);
        c.drawRect(0f, 1010f, Stage.W, Stage.H, f);
    }

    private static void hills(Canvas c, Paint f, int colour, float base, float amp, float freq, float phase) {
        Path p = new Path();
        p.moveTo(0f, Stage.H);
        for (int x = 0; x <= 1920; x += 40) {
            float y = base + (float) Math.sin(x * freq + phase) * amp + (float) Math.sin(x * freq * 2.3f + phase * 1.7f) * amp * 0.35f;
            p.lineTo(x, y);
        }
        p.lineTo(Stage.W, Stage.H);
        p.close();
        f.setShader(null);
        f.setColor(colour);
        c.drawPath(p, f);
    }

    private static void tree(Canvas c, Paint f, float x, float baseY, float s, int colour, boolean fruit, Random r) {
        f.setShader(null);
        f.setColor(colour);
        c.drawRect(x - 10f * s, baseY - 120f * s, x + 10f * s, baseY + 20f, f);
        c.drawCircle(x, baseY - 190f * s, 100f * s, f);
        c.drawCircle(x - 70f * s, baseY - 150f * s, 66f * s, f);
        c.drawCircle(x + 72f * s, baseY - 155f * s, 70f * s, f);
        if (!fruit) return;
        int[] glow = {0xFFFFB347, 0xFFFF7A59, 0xFFFFE066};
        for (int i = 0; i < 6; i++) {
            float fx = x + (r.nextFloat() - 0.5f) * 220f * s;
            float fy = baseY - 190f * s + (r.nextFloat() - 0.4f) * 150f * s;
            int col = glow[i % glow.length];
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setShader(new RadialGradient(fx, fy, 34f, new int[] {(col & 0x00FFFFFF) | 0x88000000, col & 0x00FFFFFF},
                    new float[] {0.2f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(fx, fy, 34f, g);
            f.setShader(null);
            f.setColor(col);
            c.drawCircle(fx, fy, 11f, f);
        }
    }

    /**
     * Draws the backdrop.
     * @param c canvas in stage coordinates
     * @param time seconds, drives the drifting and the twinkling
     * @param low true to draw fewer stars (the slow-TV mode)
     */
    void draw(Canvas c, float time, boolean low) {
        if (landscape == null) bake();
        starPaint.setStyle(Paint.Style.FILL);
        starPaint.setColor(SKY);
        c.drawRect(0f, 0f, Stage.W, Stage.H, starPaint);
        int n = low ? STARS / 2 : STARS;
        starPaint.setColor(0xFFFFFFFF);
        for (int i = 0; i < n; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(time * sSpeed[i] + sPhase[i]);
            starPaint.setAlpha((int) (70 + 185 * tw));
            c.drawCircle(sx[i], sy[i], sSize[i] * (0.7f + 0.3f * tw), starPaint);
        }
        paint.setAlpha(255);
        c.drawBitmap(moon, 1500f, 70f, paint);
        for (int i = 0; i < 3; i++) {
            float drift = (float) Math.sin(time * 6.2832f / DRIFT_PERIOD[i] + i * 2f) * 60f;
            float breath = 0.5f + 0.5f * (float) Math.sin(time * 6.2832f / BREATH_PERIOD[i] + i * 1.3f);
            paint.setAlpha((int) (255 * (0.40f + 0.30f * breath)));
            float w = RIBBON_W * RIBBON_SCALE, h = RIBBON_H * RIBBON_SCALE;
            rect.set(drift - 60f, RIBBON_Y[i], drift - 60f + w, RIBBON_Y[i] + h);
            c.drawBitmap(ribbon[i], null, rect, paint);
        }
        paint.setAlpha(255);
        rect.set(0f, 0f, Stage.W, Stage.H);
        c.drawBitmap(landscape, null, rect, paint);
    }
}
