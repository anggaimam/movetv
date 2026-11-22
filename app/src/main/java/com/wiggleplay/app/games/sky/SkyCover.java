package com.wiggleplay.app.games.sky;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.ui.TileMenu;

import java.util.Random;

/**
 * The Sky Glider cover on the game menu: the propeller plane seen from behind and a little to the side, over a sky
 * with hills. While the tile is highlighted the plane banks and bobs, its propeller spins and wind streaks fly past.
 */
public final class SkyCover {

    private static final Paint F = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BMP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    static {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setColor(0xFFFFFFFF);
    }

    private SkyCover() { }

    /** The moving picture used while the tile is highlighted. */
    public static final TileMenu.LiveIcon LIVE = (c, cx, cy, size, hover, time) -> {
        int s = Math.round(size);
        Bitmap bg = SpriteCache.shared().get("skycover_bg_" + s, s, s, SkyCover::paintBg);
        R.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        c.drawBitmap(bg, null, R, BMP);
        // wind streaks running backwards past the plane
        LINE.setStrokeWidth(size * 0.012f);
        for (int i = 0; i < 4; i++) {
            float f = ((time * 0.9f + i * 0.27f) % 1f);
            float y = cy - size * 0.28f + i * size * 0.17f;
            float x = cx + size * 0.42f - f * size * 0.9f;
            LINE.setAlpha((int) (150 * hover * Math.sin(Math.PI * f)));
            c.drawLine(x, y, x + size * 0.14f, y, LINE);
        }
        float roll = (float) Math.sin(time * 2.2f) * 14f * hover;
        float yaw = 30f + (float) Math.sin(time * 1.3f) * 8f * hover;
        float elev = 28f + (float) Math.sin(time * 1.7f) * 5f * hover;
        float bob = (float) Math.sin(time * 3f) * size * 0.025f * hover;
        PlaneDraw.shared().setProp(time * 31f);
        PlaneDraw.shared().draw(c, PlaneModel.PROP, 0, roll, yaw, elev, cx, cy + size * 0.04f + bob, size * 0.82f / 4.9f);
    };

    /**
     * Paints the still cover (used when the tile is not highlighted).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paint(Canvas c, int w, int h) {
        paintBg(c, w, h);
        PlaneDraw.shared().setProp(0.5f);
        PlaneDraw.shared().draw(c, PlaneModel.PROP, 0, 0f, 30f, 28f, w / 2f, h / 2f + h * 0.04f, w * 0.82f / 4.9f);
    }

    /** Sky bands, a sun, a cloud and two rows of hills with little layered trees, inside a rounded panel. */
    private static void paintBg(Canvas c, int w, int h) {
        Path panel = new Path();
        R.set(w * 0.03f, h * 0.03f, w * 0.97f, h * 0.97f);
        panel.addRoundRect(R, w * 0.1f, w * 0.1f, Path.Direction.CW);
        c.save();
        c.clipPath(panel);
        int[] sky = {0xFF8EC1E6, 0xFFA7D2EE, 0xFFC0DFF3, 0xFFD9ECF7};
        for (int i = 0; i < 4; i++) {
            F.setColor(sky[i]);
            c.drawRect(0f, i * h * 0.16f, w, (i + 1) * h * 0.16f + 1f, F);
        }
        F.setColor(0xFFFFF0B8);
        c.drawCircle(w * 0.8f, h * 0.18f, w * 0.1f, F);
        F.setColor(0xFFFFE08A);
        c.drawCircle(w * 0.8f, h * 0.18f, w * 0.075f, F);
        F.setColor(0xFFFFFFFF);
        c.drawCircle(w * 0.18f, h * 0.2f, w * 0.06f, F);
        c.drawCircle(w * 0.25f, h * 0.17f, w * 0.075f, F);
        c.drawCircle(w * 0.32f, h * 0.21f, w * 0.05f, F);
        R.set(w * 0.12f, h * 0.2f, w * 0.38f, h * 0.26f);
        c.drawRoundRect(R, h * 0.03f, h * 0.03f, F);
        Random r = new Random(5);
        hills(c, w, h, h * 0.66f, 0xFFB9D0B1, 0xFFC9DDC2, r);
        for (int i = 0; i < 6; i++) {
            float x = w * (0.05f + i * 0.18f + r.nextFloat() * 0.05f);
            SkyArt.tree(c, x, h * 0.70f, w * 0.03f, r, SkyArt.TREE_FAR1);
        }
        hills(c, w, h, h * 0.8f, 0xFF9CB89A, 0xFFAECBA9, r);
        for (int i = 0; i < 4; i++) {
            float x = w * (0.08f + i * 0.28f + r.nextFloat() * 0.06f);
            SkyArt.tree(c, x, h * 0.86f, w * 0.05f, r, SkyArt.TREE_NEAR1);
        }
        c.restore();
    }

    private static void hills(Canvas c, int w, int h, float top, int col, int light, Random r) {
        Path p = new Path();
        p.moveTo(0f, h);
        p.lineTo(0f, top + h * 0.04f);
        float x = 0f;
        while (x < w) {
            float bw = w * (0.3f + r.nextFloat() * 0.25f);
            p.quadTo(x + bw / 2f, top - h * (0.04f + r.nextFloat() * 0.06f), x + bw, top + h * 0.03f);
            x += bw;
        }
        p.lineTo(w, h);
        p.close();
        F.setColor(col);
        c.drawPath(p, F);
        c.save();
        c.clipPath(p);
        F.setColor(light);
        c.translate(-w * 0.03f, -h * 0.015f);
        c.drawPath(p, F);
        c.restore();
        F.setColor(col);
        c.save();
        c.clipPath(p);
        c.translate(w * 0.02f, h * 0.025f);
        c.drawPath(p, F);
        c.restore();
    }
}
