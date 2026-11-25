package com.wiggleplay.app.games.race;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The side-view toy truck on the Truck button of the level screen (T-32 Part B), drawn in code in the colour of the chosen truck. The four
 * body colours were sampled from {@code colormap.png} at the UV of each truck mesh's {@code body} part (the most common saturated colour
 * of its body vertices): yellow {@code #FFCF60}, red {@code #FA6B41}, green {@code #399C71}, purple {@code #A072E3}. Plain painting only.
 */
public final class TruckSwatchArt {
    /** Body colours in the order of {@code ProfileLogic.RACE_TRUCKS}: yellow, red, green, purple (RGB). */
    public static final int[] BODY = {0xFFCF60, 0xFA6B41, 0x399C71, 0xA072E3};
    /** Names in the same order. */
    public static final String[] NAMES = {"yellow", "red", "green", "purple"};

    private static final int INK = 0xFF2F3A45, GLASS = 0xFFBDC6EE, TYRE = 0xFF3D3D44, HUB = 0xFFC1C1D8;

    private TruckSwatchArt() { }

    /**
     * Paints the truck facing right, filling the picture.
     * @param c canvas
     * @param w width
     * @param h height
     * @param colour index into {@link #BODY}
     */
    public static void paint(Canvas c, int w, int h, int colour) {
        int rgb = 0xFF000000 | BODY[Math.max(0, Math.min(BODY.length - 1, colour))];
        int dark = darker(rgb);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float u = w / 100f;
        c.save();
        c.translate(0f, -8f * u);
        p.setStrokeJoin(Paint.Join.ROUND);
        // the bed behind the cab (a darker box), the chassis and the cab
        fill(c, p, new RectF(6f * u, 40f * u, 58f * u, 66f * u), 4f * u, dark, u);
        fill(c, p, new RectF(6f * u, 58f * u, 94f * u, 72f * u), 3f * u, rgb, u);
        Path cab = new Path();
        cab.moveTo(56f * u, 66f * u);
        cab.lineTo(56f * u, 36f * u);
        cab.lineTo(72f * u, 36f * u);
        cab.lineTo(86f * u, 52f * u);
        cab.lineTo(94f * u, 56f * u);
        cab.lineTo(94f * u, 66f * u);
        cab.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(rgb);
        c.drawPath(cab, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * u);
        p.setColor(INK);
        c.drawPath(cab, p);
        // the window
        Path win = new Path();
        win.moveTo(61f * u, 41f * u);
        win.lineTo(71f * u, 41f * u);
        win.lineTo(81f * u, 53f * u);
        win.lineTo(61f * u, 53f * u);
        win.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(GLASS);
        c.drawPath(win, p);
        // headlight and bumper
        p.setColor(0xFFFFF3C4);
        c.drawCircle(91f * u, 60f * u, 2.6f * u, p);
        // wheels
        for (float wx : new float[] {26f * u, 74f * u}) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(TYRE);
            c.drawCircle(wx, 72f * u, 11f * u, p);
            p.setColor(HUB);
            c.drawCircle(wx, 72f * u, 5f * u, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f * u);
            p.setColor(INK);
            c.drawCircle(wx, 72f * u, 11f * u, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private static void fill(Canvas c, Paint p, RectF r, float radius, int colour, float u) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(colour);
        c.drawRoundRect(r, radius, radius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * u);
        p.setColor(INK);
        c.drawRoundRect(r, radius, radius, p);
    }

    private static int darker(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return 0xFF000000 | ((int) (r * 0.8f) << 16) | ((int) (g * 0.8f) << 8) | (int) (b * 0.8f);
    }
}
