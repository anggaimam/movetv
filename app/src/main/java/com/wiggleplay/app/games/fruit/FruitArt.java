package com.wiggleplay.app.games.fruit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.Log;

import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.ui.Theme;

import java.io.IOException;
import java.io.InputStream;

/**
 * The pictures of Fruit Splash. The 16 fruits are drawn pictures (from {@code art-src/fruit/fruits-sheet.svg},
 * turned into 256 px PNGs in {@code assets/fruit} by {@code tools/split_fruits.py}); the golden star and the grumpy
 * coconut are painted in code. Every sprite is {@value #SIZE} px square and the fruit fills it, so the scene scales
 * it to the fruit's hit radius. Cut halves are baked once from the whole sprite with a flesh-coloured cut face.
 * Nothing is allocated while the game runs. Main thread only.
 */
final class FruitArt {

    /** Side of every sprite in px. */
    static final int SIZE = 256;
    /** The hit radius corresponds to this many sprite px (a little less than half the sprite, so the picture is bigger than the hit area). */
    static final float BODY_R = 118f;

    /** Colour of the inside of each kind (cut face and splash). */
    static final int[] FLESH = {
            0xFFF6E7B8, 0xFFFBEFB0, 0xFFFFA32B, 0xFFEFE9B0, 0xFFFF5A5F, 0xFFA655C0, 0xFFEF3B42, 0xFFFFE15A,
            0xFFFFEF7A, 0xFFFFC07A, 0xFFE8283C, 0xFF9CD02F, 0xFFFFB02B, 0xFF5A78CF, 0xFFE8C45A, 0xFFF58327,
            0xFFFFE08A, 0xFFF3EBDD};

    private static final String[] FILES = {
            "01_apple", "02_banana", "03_orange", "04_pear", "05_strawberry", "06_grapes", "07_watermelon",
            "08_pineapple", "09_lemon", "10_peach", "11_cherries", "12_kiwi", "13_mango", "14_blueberries",
            "15_plum", "16_papaya"};

    private static final Bitmap[] SPRITES = new Bitmap[FruitLogic.KINDS];
    private static final Bitmap[][] HALVES = new Bitmap[FruitLogic.KINDS][2];
    private static final Paint F = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint L = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint ATOP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();
    private static Context ctx;

    static {
        L.setStyle(Paint.Style.STROKE);
        L.setStrokeCap(Paint.Cap.ROUND);
        L.setStrokeJoin(Paint.Join.ROUND);
        ATOP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
    }

    private FruitArt() { }

    /**
     * Gives the art access to the app's assets (call once at start).
     * @param context any context
     */
    static void init(Context context) {
        ctx = context.getApplicationContext();
    }

    /**
     * The picture of a fruit kind.
     * @param kind 0..17 (see {@link FruitLogic})
     * @return a cached {@link #SIZE} x {@link #SIZE} bitmap
     */
    static Bitmap sprite(int kind) {
        if (SPRITES[kind] == null) {
            Bitmap loaded = kind < FILES.length ? load(kind) : null;
            if (loaded != null) {
                SPRITES[kind] = loaded;
            } else {
                final int k = kind;
                SPRITES[kind] = SpriteCache.shared().get("fruit_sprite_" + kind, SIZE, SIZE, (c, w, h) -> paint(c, k));
            }
        }
        return SPRITES[kind];
    }

    /** Lets go of every fruit picture (called when the game is left); they load again on the next visit. */
    static void unload() {
        java.util.Arrays.fill(SPRITES, null);
        for (Bitmap[] h : HALVES) java.util.Arrays.fill(h, null);
        SpriteCache.shared().removePrefix("fruit_");
    }

    private static Bitmap load(int kind) {
        if (ctx == null) return null;
        try (InputStream in = ctx.getAssets().open("fruit/" + FILES[kind] + ".png")) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            Log.w("Wiggle Play-Fruit", "missing fruit picture " + FILES[kind]);
            return null;
        }
    }

    /**
     * One half of a cut fruit, in the same {@link #SIZE} square as the whole sprite (so both halves line up),
     * with a flat flesh-coloured cut face along the cut.
     * @param kind 0..17
     * @param side -1 for the left half, 1 for the right half
     * @return a cached bitmap
     */
    static Bitmap half(int kind, int side) {
        int si = side < 0 ? 0 : 1;
        if (HALVES[kind][si] == null) {
            final Bitmap whole = sprite(kind);
            final int sd = side < 0 ? -1 : 1, k = kind;
            HALVES[kind][si] = SpriteCache.shared().get("fruit_half_" + kind + "_" + si, SIZE, SIZE, (c, w, h) -> {
                c.save();
                if (sd < 0) c.clipRect(0f, 0f, w / 2f, h); else c.clipRect(w / 2f, 0f, w, h);
                c.drawBitmap(whole, 0f, 0f, null);
                c.restore();
                float mid = w / 2f;
                float edge = sd < 0 ? mid : mid;
                ATOP.setColor(Theme.darker(FLESH[k]));
                R.set(sd < 0 ? edge - 22f : edge, 0f, sd < 0 ? edge : edge + 22f, h);
                c.drawRect(R, ATOP);
                ATOP.setColor(FLESH[k]);
                R.set(sd < 0 ? edge - 15f : edge, 0f, sd < 0 ? edge : edge + 15f, h);
                c.drawRect(R, ATOP);
                ATOP.setColor(0x66FFFFFF);
                R.set(sd < 0 ? edge - 8f : edge + 3f, 0f, sd < 0 ? edge - 3f : edge + 8f, h);
                c.drawRect(R, ATOP);
            });
        }
        return HALVES[kind][si];
    }

    private static void paint(Canvas c, int kind) {
        float cx = SIZE / 2f, cy = SIZE / 2f;
        if (kind == FruitLogic.STAR) {
            Path p = new Path();
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float rr = (i % 2 == 0) ? 118f : 56f;
                float px = cx + (float) Math.cos(a) * rr, py = cy + 6f + (float) Math.sin(a) * rr;
                if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
            p.close();
            F.setColor(0xFFC98F00);
            c.save();
            c.translate(8f, 9f);
            c.drawPath(p, F);
            c.restore();
            F.setColor(0xFFF2B705);
            c.drawPath(p, F);
            L.setColor(0xFFFFF3B0);
            L.setStrokeWidth(9f);
            c.drawPath(p, L);
            F.setColor(Theme.INK);
            c.drawCircle(cx - 24f, cy + 8f, 8f, F);
            c.drawCircle(cx + 24f, cy + 8f, 8f, F);
            L.setColor(Theme.INK);
            L.setStrokeWidth(7f);
            Path m = new Path();
            m.moveTo(cx - 18f, cy + 34f);
            m.quadTo(cx, cy + 50f, cx + 18f, cy + 34f);
            c.drawPath(m, L);
        } else if (kind == FruitLogic.COCONUT) {
            F.setColor(0xFF4F3720);
            c.drawCircle(cx + 8f, cy + 9f, 112f, F);
            F.setColor(0xFF7A5230);
            c.drawCircle(cx, cy, 112f, F);
            F.setColor(0xFFA87B4F);
            R.set(cx - 80f, cy - 86f, cx + 24f, cy - 24f);
            c.drawOval(R, F);
            F.setColor(0xFFF3EBDD);
            c.drawCircle(cx - 38f, cy - 8f, 24f, F);
            c.drawCircle(cx + 38f, cy - 8f, 24f, F);
            F.setColor(Theme.INK);
            c.drawCircle(cx - 33f, cy - 2f, 11f, F);
            c.drawCircle(cx + 33f, cy - 2f, 11f, F);
            L.setColor(Theme.INK);
            L.setStrokeWidth(12f);
            c.drawLine(cx - 66f, cy - 42f, cx - 16f, cy - 26f, L);
            c.drawLine(cx + 66f, cy - 42f, cx + 16f, cy - 26f, L);
            Path m = new Path();
            m.moveTo(cx - 34f, cy + 62f);
            m.quadTo(cx, cy + 34f, cx + 34f, cy + 62f);
            c.drawPath(m, L);
        } else { // fallback if a picture is missing: a plain coloured ball
            F.setColor(FLESH[kind]);
            c.drawCircle(cx, cy, 110f, F);
        }
    }
}
