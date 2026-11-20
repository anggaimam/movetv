package com.angga.movetv.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * The fixed 1920x1080 drawing stage and helpers for drawing on it. Everything in the app draws in these
 * coordinates; {@link GameView} scales them to the real screen. Use from the main thread only.
 */
public final class Stage {

    /** Stage width in pixels. */
    public static final float W = 1920f;
    /** Stage height in pixels. */
    public static final float H = 1080f;

    private static final Paint CAMERA_PAINT = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final Paint DIM_PAINT = new Paint();
    private static final RectF FULL = new RectF(0f, 0f, W, H);

    private Stage() { }

    /**
     * Draws the camera picture over the whole stage, mirrored like a mirror and stretched the same way
     * pose keypoints are mapped, then dims it with black.
     * @param c canvas in stage coordinates
     * @param camera the camera picture (not yet mirrored); nothing is drawn if null
     * @param dim 0 (no dimming) to 1 (black)
     */
    public static void drawCamera(Canvas c, Bitmap camera, float dim) {
        if (camera == null) return;
        c.save();
        c.scale(-1f, 1f, W / 2f, H / 2f);
        c.drawBitmap(camera, null, FULL, CAMERA_PAINT);
        c.restore();
        if (dim > 0f) {
            DIM_PAINT.setColor((int) (Math.min(1f, dim) * 255f + 0.5f) << 24);
            c.drawRect(0f, 0f, W, H, DIM_PAINT);
        }
    }
}
