package com.wiggleplay.app.games.sky;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Paints a {@link PlaneModel} plane on a canvas. One shared painter is enough: the game draws on one thread.
 * Nothing is allocated while drawing.
 */
final class PlaneDraw implements PlaneModel.Sink {

    private static final PlaneDraw SHARED = new PlaneDraw();

    private final PlaneModel model = new PlaneModel();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Canvas canvas;

    private PlaneDraw() {
        fill.setStrokeJoin(Paint.Join.ROUND);
        fill.setStrokeCap(Paint.Cap.ROUND);
    }

    /** @return the shared painter */
    static PlaneDraw shared() {
        return SHARED;
    }

    /** The roll of a plane for a steering value of -1..1. */
    static float rollFor(float steer) {
        return steer * 20f;
    }

    /** The yaw of a plane for a steering value of -1..1. */
    static float yawFor(float steer) {
        return steer * 16f;
    }

    /** The camera height angle for a climb value of -1..1 (0 is level flight, seen a little from above). */
    static float elevFor(float climb) {
        return 30f + climb * (climb > 0f ? 26f : 40f);
    }

    /**
     * Draws a plane.
     * @param c the canvas
     * @param type PlaneModel.PROP, AIRLINER, JET or SHIP
     * @param theme colour theme number
     * @param roll roll in degrees
     * @param yaw yaw in degrees
     * @param elev camera height angle in degrees
     * @param cx screen x of the plane's middle
     * @param cy screen y of the plane's middle
     * @param scale pixels per model unit (about 88 gives a 360 px wide propeller plane)
     */
    void draw(Canvas c, int type, int theme, float roll, float yaw, float elev, float cx, float cy, float scale) {
        canvas = c;
        model.draw(this, type, theme, roll, yaw, elev, cx, cy, scale);
        canvas = null;
    }

    private final android.graphics.RectF oval = new android.graphics.RectF();

    @Override
    public void ellipse(float cx, float cy, float rx, float ry, float deg, int argb) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(argb);
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry);
        if (Math.abs(deg) < 0.5f) {
            canvas.drawOval(oval, fill);
        } else {
            canvas.save();
            canvas.rotate(deg, cx, cy);
            canvas.drawOval(oval, fill);
            canvas.restore();
        }
    }

    @Override
    public void poly(float[] xy, int n, int argb, float stroke) {
        path.reset();
        path.moveTo(xy[0], xy[1]);
        for (int i = 1; i < n; i++) path.lineTo(xy[2 * i], xy[2 * i + 1]);
        path.close();
        fill.setColor(argb);
        if (stroke > 0.5f) {
            fill.setStyle(Paint.Style.FILL_AND_STROKE);
            fill.setStrokeWidth(stroke);
        } else {
            fill.setStyle(Paint.Style.FILL);
        }
        canvas.drawPath(path, fill);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, int argb, float width) {
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(width);
        fill.setColor(argb);
        canvas.drawLine(x1, y1, x2, y2, fill);
    }
}
