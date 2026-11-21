package com.angga.movetv.games.fruit;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.angga.movetv.core.SpriteCache;
import com.angga.movetv.ui.Theme;

/**
 * The flat storybook pictures of Fruit Splash, painted once into the shared sprite cache. Every sprite is
 * {@value #SIZE} px square; the fruit body is a circle of {@value #BODY_R} px radius at the centre, so the
 * scene scales it to the fruit's hit radius.
 */
final class FruitArt {

    /** Side of every sprite in px. */
    static final int SIZE = 240;
    /** Radius of the fruit body inside the sprite, in px. */
    static final float BODY_R = 100f;

    /** Colour of the inside of each kind (used for the cut face and the splash). */
    static final int[] FLESH = {
            0xFFE0605A, 0xFFF5B041, 0xFFF2A83B, 0xFF9CCB5E, 0xFFEE6B6E, 0xFFF7DC6F, 0xFFFFE08A, 0xFFF3EBDD};
    private static final int[] SKIN = {
            0xFF6FA06B, 0xFFE89A4C, 0xFFE8884C, 0xFF9B6B43, 0xFFE0605A, 0xFFD9A441, 0xFFF2B705, 0xFF7A5230};

    private static final Bitmap[] SPRITES = new Bitmap[8];
    private static final Paint F = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint L = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    static {
        L.setStyle(Paint.Style.STROKE);
        L.setStrokeCap(Paint.Cap.ROUND);
        L.setStrokeJoin(Paint.Join.ROUND);
    }

    private FruitArt() { }

    /**
     * The picture of a fruit kind.
     * @param kind 0..7 (see {@link FruitLogic})
     * @return a cached {@link #SIZE} x {@link #SIZE} bitmap
     */
    static Bitmap sprite(int kind) {
        if (SPRITES[kind] == null) {
            final int k = kind;
            SPRITES[kind] = SpriteCache.shared().get("fruit_sprite_" + kind, SIZE, SIZE, (c, w, h) -> paint(c, k));
        }
        return SPRITES[kind];
    }

    private static void body(Canvas c, int colour) {
        float cx = SIZE / 2f, cy = SIZE / 2f;
        F.setColor(Theme.darker(colour));
        c.drawCircle(cx + 7f, cy + 8f, BODY_R, F);
        F.setColor(colour);
        c.drawCircle(cx, cy, BODY_R, F);
    }

    private static void shine(Canvas c) {
        F.setColor(0x55FFFFFF);
        R.set(SIZE / 2f - 62f, SIZE / 2f - 66f, SIZE / 2f - 30f, SIZE / 2f - 20f);
        c.save();
        c.rotate(-30f, SIZE / 2f - 46f, SIZE / 2f - 44f);
        c.drawOval(R, F);
        c.restore();
    }

    private static void leaf(Canvas c, float x, float y, float rot, float len, int colour) {
        F.setColor(colour);
        c.save();
        c.rotate(rot, x, y);
        R.set(x - len * 0.22f, y - len, x + len * 0.22f, y);
        c.drawOval(R, F);
        c.restore();
    }

    private static void paint(Canvas c, int kind) {
        float cx = SIZE / 2f, cy = SIZE / 2f;
        switch (kind) {
            case FruitLogic.WATERMELON: {
                body(c, SKIN[kind]);
                L.setColor(0xFF4F7F4B);
                L.setStrokeWidth(12f);
                for (int i = -2; i <= 2; i++) {
                    Path p = new Path();
                    p.moveTo(cx + i * 32f, cy - 86f + Math.abs(i) * 10f);
                    p.quadTo(cx + i * 44f, cy, cx + i * 32f, cy + 86f - Math.abs(i) * 10f);
                    c.drawPath(p, L);
                }
                shine(c);
                break;
            }
            case FruitLogic.ORANGE: {
                body(c, SKIN[kind]);
                leaf(c, cx, cy - BODY_R + 6f, 25f, 40f, Theme.LEAF);
                F.setColor(0xFFC96F2C);
                c.drawCircle(cx, cy - BODY_R + 8f, 7f, F);
                shine(c);
                break;
            }
            case FruitLogic.MANGO: {
                body(c, SKIN[kind]);
                F.setColor(0xFFF2C572);
                c.save();
                c.rotate(35f, cx, cy);
                R.set(cx - 70f, cy - 30f, cx + 80f, cy + 90f);
                c.drawOval(R, F);
                c.restore();
                leaf(c, cx + 8f, cy - BODY_R + 6f, 20f, 38f, Theme.LEAF);
                shine(c);
                break;
            }
            case FruitLogic.KIWI: {
                body(c, SKIN[kind]);
                F.setColor(0xFFB8DD7B);
                c.drawCircle(cx, cy, 52f, F);
                F.setColor(0xFFF3EBDD);
                c.drawCircle(cx, cy, 16f, F);
                F.setColor(Theme.INK);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawCircle(cx + (float) Math.cos(a) * 34f, cy + (float) Math.sin(a) * 34f, 4f, F);
                }
                shine(c);
                break;
            }
            case FruitLogic.STRAWBERRY: {
                body(c, SKIN[kind]);
                F.setColor(Theme.INK);
                for (int i = 0; i < 7; i++) {
                    float sx = cx + ((i % 3) - 1) * 40f + (i / 3) * 12f - 12f, sy = cy - 24f + (i / 3) * 40f;
                    R.set(sx - 5f, sy - 8f, sx + 5f, sy + 8f);
                    c.drawOval(R, F);
                }
                leaf(c, cx, cy - BODY_R + 12f, -40f, 44f, Theme.LEAF);
                leaf(c, cx, cy - BODY_R + 12f, 0f, 50f, Theme.LEAF);
                leaf(c, cx, cy - BODY_R + 12f, 40f, 44f, Theme.LEAF);
                shine(c);
                break;
            }
            case FruitLogic.PINEAPPLE: {
                body(c, SKIN[kind]);
                L.setColor(0xFFA87B26);
                L.setStrokeWidth(7f);
                for (int i = -2; i <= 2; i++) {
                    c.drawLine(cx + i * 30f - 50f, cy - 60f, cx + i * 30f + 50f, cy + 60f, L);
                    c.drawLine(cx + i * 30f + 50f, cy - 60f, cx + i * 30f - 50f, cy + 60f, L);
                }
                leaf(c, cx, cy - BODY_R + 14f, -30f, 46f, Theme.LEAF);
                leaf(c, cx, cy - BODY_R + 14f, 0f, 56f, 0xFF4F7F4B);
                leaf(c, cx, cy - BODY_R + 14f, 30f, 46f, Theme.LEAF);
                shine(c);
                break;
            }
            case FruitLogic.STAR: {
                Path p = new Path();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = (i % 2 == 0) ? BODY_R + 8f : BODY_R * 0.48f;
                    float px = cx + (float) Math.cos(a) * rr, py = cy + 4f + (float) Math.sin(a) * rr;
                    if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
                }
                p.close();
                F.setColor(0xFFC98F00);
                c.save();
                c.translate(7f, 8f);
                c.drawPath(p, F);
                c.restore();
                F.setColor(SKIN[kind]);
                c.drawPath(p, F);
                L.setColor(0xFFFFF3B0);
                L.setStrokeWidth(8f);
                c.drawPath(p, L);
                F.setColor(Theme.INK);
                c.drawCircle(cx - 20f, cy + 6f, 7f, F);
                c.drawCircle(cx + 20f, cy + 6f, 7f, F);
                break;
            }
            default: { // coconut: grumpy face
                body(c, SKIN[FruitLogic.COCONUT]);
                F.setColor(0xFFA87B4F);
                R.set(cx - 70f, cy - 74f, cx + 20f, cy - 20f);
                c.drawOval(R, F);
                F.setColor(0xFFF3EBDD);
                c.drawCircle(cx - 32f, cy - 6f, 20f, F);
                c.drawCircle(cx + 32f, cy - 6f, 20f, F);
                F.setColor(Theme.INK);
                c.drawCircle(cx - 28f, cy - 2f, 9f, F);
                c.drawCircle(cx + 28f, cy - 2f, 9f, F);
                L.setColor(Theme.INK);
                L.setStrokeWidth(10f);
                c.drawLine(cx - 56f, cy - 36f, cx - 14f, cy - 22f, L);
                c.drawLine(cx + 56f, cy - 36f, cx + 14f, cy - 22f, L);
                Path m = new Path();
                m.moveTo(cx - 28f, cy + 52f);
                m.quadTo(cx, cy + 28f, cx + 28f, cy + 52f);
                c.drawPath(m, L);
                break;
            }
        }
    }
}
