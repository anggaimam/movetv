package com.angga.movetv.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;

import java.util.Random;

/**
 * Helpers for the three art styles (Flat storybook, Crayon doodle, Neon arcade). They are meant to draw
 * into a cached bitmap through {@link SpriteCache}, once, NOT every frame: they allocate. There are no
 * gradients and no blur anywhere; depth comes from flat shapes, hatching and layered strokes.
 */
public final class Brushes {

    private Brushes() { }

    /**
     * Flat storybook: draws a darker copy of a shape shifted down-right, to go under the real shape.
     * @param c canvas (a cached bitmap's canvas)
     * @param path the shape
     * @param offset how far to shift the shadow, in pixels (x and y)
     * @param colour the shadow colour (ARGB), usually a darker shade of the fill
     */
    public static void flatShadow(Canvas c, Path path, float offset, int colour) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(colour);
        c.save();
        c.translate(offset, offset);
        c.drawPath(path, p);
        c.restore();
    }

    /**
     * Crayon doodle: a hand-wobbled copy of a path. The wobble comes from a seed, so the same seed always
     * gives the same wobble; make it once and keep it.
     * @param path the clean shape
     * @param seed any number; the same seed gives the same wobble
     * @param jitterPx largest sideways wobble in pixels
     * @return a new, wobbly path
     */
    public static Path crayonOutline(Path path, long seed, float jitterPx) {
        PathMeasure pm = new PathMeasure(path, false);
        Path out = new Path();
        Random rnd = new Random(seed);
        float[] pos = new float[2], tan = new float[2];
        boolean first = true;
        do {
            float len = pm.getLength();
            if (len < 1f) continue;
            int steps = Math.max(8, (int) (len / 12f));
            boolean closed = pm.isClosed();
            float[] ox = new float[steps + 1], oy = new float[steps + 1];
            float off = 0f;
            for (int i = 0; i <= steps; i++) {
                pm.getPosTan(len * i / steps, pos, tan);
                off = off * 0.55f + (rnd.nextFloat() * 2f - 1f) * jitterPx * 0.85f; // smooth random walk
                if (closed && i == steps) off = 0f; // the loop closes on its starting point
                ox[i] = pos[0] - tan[1] * off;
                oy[i] = pos[1] + tan[0] * off;
            }
            if (closed) { ox[steps] = ox[0]; oy[steps] = oy[0]; }
            out.moveTo(ox[0], oy[0]);
            for (int i = 1; i <= steps; i++) {
                float mx = (ox[i - 1] + ox[i]) / 2f, my = (oy[i - 1] + oy[i]) / 2f;
                out.quadTo(ox[i - 1], oy[i - 1], mx, my);
            }
            out.lineTo(ox[steps], oy[steps]);
            if (closed) out.close();
            first = false;
        } while (pm.nextContour());
        if (first) out.addPath(path);
        return out;
    }

    /**
     * Crayon doodle: draws a path as a wobbly crayon line (two slightly different passes, like going over
     * the line twice).
     * @param c canvas (a cached bitmap's canvas)
     * @param path the clean shape
     * @param seed wobble seed
     * @param jitterPx largest sideways wobble in pixels
     * @param colour line colour (ARGB)
     * @param widthPx line width in pixels
     */
    public static void drawCrayonOutline(Canvas c, Path path, long seed, float jitterPx, int colour, float widthPx) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(colour);
        p.setStrokeWidth(widthPx);
        c.drawPath(crayonOutline(path, seed, jitterPx), p);
        p.setStrokeWidth(widthPx * 0.55f);
        p.setAlpha(170);
        c.drawPath(crayonOutline(path, seed + 7919L, jitterPx), p);
    }

    /**
     * Crayon doodle: fills a shape with parallel crayon strokes (hatching) at about 70% opacity, clipped to
     * the shape.
     * @param c canvas (a cached bitmap's canvas)
     * @param path the shape to fill
     * @param colour stroke colour (its alpha is replaced by 70%)
     * @param angleDeg direction of the strokes in degrees: 0 = horizontal, 45 = sloping down to the right
     *                 (the y axis points down on screen)
     * @param spacingPx distance between strokes in pixels
     */
    public static void crayonFill(Canvas c, Path path, int colour, float angleDeg, float spacingPx) {
        RectF b = new RectF();
        path.computeBounds(b, true);
        if (b.isEmpty() || spacingPx < 1f) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor((colour & 0x00FFFFFF) | (178 << 24)); // 70% opacity
        p.setStrokeWidth(spacingPx * 0.8f);
        double a = Math.toRadians(angleDeg);
        float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
        float nx = -dy, ny = dx;
        float cx = b.centerX(), cy = b.centerY();
        float half = (float) Math.hypot(b.width(), b.height()) / 2f + spacingPx;
        Random rnd = new Random(colour * 31L + (long) angleDeg);
        c.save();
        c.clipPath(path);
        for (float t = -half; t <= half; t += spacingPx) {
            float j = (rnd.nextFloat() - 0.5f) * spacingPx * 0.35f; // each stroke sits a little off
            float w = (rnd.nextFloat() - 0.5f) * spacingPx * 0.25f; // and a little tilted
            float sx = cx + nx * (t + j) - dx * half, sy = cy + ny * (t + j) - dy * half;
            float ex = cx + nx * (t + j + w) + dx * half, ey = cy + ny * (t + j + w) + dy * half;
            c.drawLine(sx, sy, ex, ey, p);
        }
        c.restore();
    }

    /**
     * Neon arcade: a glowing outline made without any blur: a wide 30%-opacity stroke under a thin full-colour
     * stroke.
     * @param c canvas (a cached bitmap's canvas)
     * @param path the shape
     * @param colour line colour (ARGB, alpha ignored)
     * @param widthPx width of the thin bright line; the glow is 3.5 times wider
     */
    public static void neonStroke(Canvas c, Path path, int colour, float widthPx) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor((colour & 0x00FFFFFF) | (77 << 24)); // 30% opacity
        p.setStrokeWidth(widthPx * 3.5f);
        c.drawPath(path, p);
        p.setColor(colour | 0xFF000000);
        p.setStrokeWidth(widthPx);
        c.drawPath(path, p);
    }
}
