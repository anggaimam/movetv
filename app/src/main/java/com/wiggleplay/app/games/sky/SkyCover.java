package com.wiggleplay.app.games.sky;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.wiggleplay.app.ui.TileMenu;

/**
 * The Sky Glider cover on the game menu: the propeller plane seen from behind and a little to the side, drawn straight
 * on the tile (no inner picture). While the tile is highlighted the plane banks and bobs, its propeller spins and wind streaks fly past.
 */
public final class SkyCover {

    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setColor(0xFFFFFFFF);
    }

    private SkyCover() { }

    /** The moving picture used while the tile is highlighted. */
    public static final TileMenu.LiveIcon LIVE = (c, cx, cy, size, hover, time) -> {
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
        PlaneDraw.shared().draw(c, PlaneModel.PROP, 0, roll, yaw, elev, cx, cy + size * 0.04f + bob, size * 0.95f / 4.9f);
    };

    /**
     * Paints the still cover (used when the tile is not highlighted).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paint(Canvas c, int w, int h) {
        PlaneDraw.shared().setProp(0.5f);
        PlaneDraw.shared().draw(c, PlaneModel.PROP, 0, 0f, 30f, 28f, w / 2f, h / 2f + h * 0.04f, w * 0.95f / 4.9f);
    }
}
