package com.wiggleplay.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.Ease;
import com.wiggleplay.app.core.SpriteCache;

/**
 * The round corner buttons: Back (an arrow) and Home (a layered flat house). Each is drawn as a 200 px ink
 * circle with a paper-coloured icon and a layered flat shadow, but its hit area is 260 x 260 px so it is easy
 * to hit (the one allowed exception to the "targets at least 240 px tall" rule). Hold time 1500 ms. They are
 * ordinary {@link TileMenu.Tile}s with {@code round} set, so hand, remote focus and sounds work like any tile.
 * Pre-rendered; nothing is allocated per frame. Main thread only.
 */
public final class RoundButton {

    /** Drawn diameter in px. */
    public static final float DIAMETER = 200f;
    /** Side of the square hit area in px. */
    public static final float HIT = 260f;
    /** Hold time in ms. */
    public static final int HOLD_MS = 1500;
    /** Page arrows: a little smaller hit area and a quicker hold, since they are used again and again. */
    public static final float PAGE_HIT = 220f;
    public static final int PAGE_HOLD_MS = 800;
    /** Centre of the corner button used on every screen. */
    public static final float CORNER_X = 160f, CORNER_Y = 150f;

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RING = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();
    private static final int PAPER_DARK = 0xFFD9CDB8;

    static {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setStrokeJoin(Paint.Join.ROUND);
        RING.setStyle(Paint.Style.STROKE);
        RING.setStrokeCap(Paint.Cap.ROUND);
    }

    private RoundButton() { }

    /**
     * A Back button at the usual corner spot.
     * @return a tile with id "back"
     */
    public static TileMenu.Tile back() {
        return make("back", "Back", "back", CORNER_X, CORNER_Y);
    }

    /**
     * A Home button at the usual corner spot.
     * @return a tile with id "home"
     */
    public static TileMenu.Tile home() {
        return make("home", "Home", "home", CORNER_X, CORNER_Y);
    }

    /**
     * A Settings (gear) button at the top-right corner, for children and adults who cannot hold both hands up.
     * @return a tile with id "settings"
     */
    public static TileMenu.Tile settings() {
        return make("settings", "Settings", "settings", 1920f - CORNER_X, CORNER_Y);
    }

    /**
     * A round arrow pointing left, to show the previous page of tiles (shorter hold than the corner buttons).
     * @param cx centre x
     * @param cy centre y
     * @param title the spoken name
     * @return a tile with id "prev"
     */
    public static TileMenu.Tile pagePrev(float cx, float cy, String title) {
        return pageArrow("prev", title, cx, cy);
    }

    /**
     * A round arrow pointing right, to show the next page of tiles.
     * @param cx centre x
     * @param cy centre y
     * @param title the spoken name
     * @return a tile with id "next"
     */
    public static TileMenu.Tile pageNext(float cx, float cy, String title) {
        return pageArrow("next", title, cx, cy);
    }

    private static TileMenu.Tile pageArrow(String id, String title, float cx, float cy) {
        TileMenu.Tile t = new TileMenu.Tile(id, title, null, Theme.INK, null, cx - PAGE_HIT / 2f, cy - PAGE_HIT / 2f,
                PAGE_HIT, PAGE_HIT);
        t.round = true;
        t.holdMs = PAGE_HOLD_MS;
        return t;
    }

    private static TileMenu.Tile make(String id, String title, String kind, float cx, float cy) {
        TileMenu.Tile t = new TileMenu.Tile(id, title, null, Theme.INK, null, cx - HIT / 2f, cy - HIT / 2f, HIT, HIT);
        t.round = true;
        t.holdMs = HOLD_MS;
        return t;
    }

    /** Builds the cached pictures of a round tile (called by the menu). */
    static void bake(TileMenu.Tile t) {
        final boolean home = "home".equals(t.id);
        final boolean gear = "settings".equals(t.id);
        final boolean next = "next".equals(t.id);
        t.body = SpriteCache.shared().get("round_body_" + (home ? "home" : gear ? "settings" : next ? "next" : "back"),
                (int) DIAMETER, (int) DIAMETER,
                (c, w, h) -> {
                    FILL.setColor(Theme.INK);
                    c.drawCircle(w / 2f, h / 2f, w / 2f - 3f, FILL);
                    LINE.setColor(0x33FFFFFF);
                    LINE.setStrokeWidth(5f);
                    c.drawCircle(w / 2f, h / 2f, w / 2f - 12f, LINE);
                    if (home) paintHouse(c, w, h);
                    else if (gear) paintGear(c, w, h);
                    else if (next) {
                        c.save();
                        c.scale(-1f, 1f, w / 2f, h / 2f);       // the back arrow, turned to point right
                        paintArrow(c, w, h);
                        c.restore();
                    } else paintArrow(c, w, h);
                });
        t.shadow = SpriteCache.shared().get("round_shadow", (int) DIAMETER, (int) DIAMETER, (c, w, h) -> {
            FILL.setColor(0xCC1B232B);
            c.drawCircle(w / 2f, h / 2f, w / 2f - 3f, FILL);
        });
    }

    private static void paintGear(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h / 2f;
        FILL.setColor(Theme.PAPER);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45f, cx, cy);
            R.set(cx - w * 0.06f, cy - w * 0.31f, cx + w * 0.06f, cy - w * 0.19f);
            c.drawRoundRect(R, w * 0.02f, w * 0.02f, FILL);
            c.restore();
        }
        c.drawCircle(cx, cy, w * 0.23f, FILL);
        FILL.setColor(Theme.INK);
        c.drawCircle(cx, cy, w * 0.09f, FILL);
    }

    private static void paintArrow(Canvas c, int w, int h) {
        LINE.setColor(Theme.PAPER);
        LINE.setStrokeWidth(w * 0.09f);
        c.drawLine(w * 0.72f, h * 0.50f, w * 0.30f, h * 0.50f, LINE);
        Path p = new Path();
        p.moveTo(w * 0.47f, h * 0.28f);
        p.lineTo(w * 0.28f, h * 0.50f);
        p.lineTo(w * 0.47f, h * 0.72f);
        c.drawPath(p, LINE);
    }

    private static void paintHouse(Canvas c, int w, int h) {
        // body (back layer, a shade darker), body (front), roof (two layers), door and window
        R.set(w * 0.29f, h * 0.46f, w * 0.71f, h * 0.76f);
        FILL.setColor(PAPER_DARK);
        c.drawRoundRect(R, w * 0.04f, w * 0.04f, FILL);
        R.set(w * 0.27f, h * 0.44f, w * 0.69f, h * 0.74f);
        FILL.setColor(Theme.PAPER);
        c.drawRoundRect(R, w * 0.04f, w * 0.04f, FILL);
        Path roof = new Path();
        roof.moveTo(w * 0.50f, h * 0.20f);
        roof.lineTo(w * 0.80f, h * 0.48f);
        roof.lineTo(w * 0.20f, h * 0.48f);
        roof.close();
        c.save();
        c.translate(w * 0.015f, h * 0.02f);
        FILL.setColor(PAPER_DARK);
        c.drawPath(roof, FILL);
        c.restore();
        FILL.setColor(Theme.ORANGE);
        c.drawPath(roof, FILL);
        LINE.setColor(Theme.ORANGE);
        LINE.setStrokeWidth(w * 0.04f);
        c.drawPath(roof, LINE);
        R.set(w * 0.455f, h * 0.54f, w * 0.545f, h * 0.74f);
        FILL.setColor(Theme.INK);
        c.drawRoundRect(R, w * 0.03f, w * 0.03f, FILL);
        R.set(w * 0.33f, h * 0.53f, w * 0.41f, h * 0.61f);
        FILL.setColor(Theme.MUSTARD);
        c.drawRoundRect(R, w * 0.015f, w * 0.015f, FILL);
        R.set(w * 0.59f, h * 0.53f, w * 0.67f, h * 0.61f);
        c.drawRoundRect(R, w * 0.015f, w * 0.015f, FILL);
    }

    /**
     * Draws a round tile: shadow, body, hover lift and the hold ring.
     * @param c canvas
     * @param t the round tile
     * @param progress hold progress 0..1
     * @param ringColour the colour of the player filling it
     */
    static void draw(Canvas c, TileMenu.Tile t, float progress, int ringColour) {
        float cx = t.x + t.w / 2f, cy = t.y + t.h / 2f;
        float s = 1f + 0.08f * Ease.outBack(t.hover);
        float d = 8f + 12f * t.hover;
        float r = DIAMETER / 2f;
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        c.drawBitmap(t.shadow, -r + d * 0.6f, -r + d, null);
        c.drawBitmap(t.body, -r, -r, null);
        if (t.hover > 0f) {
            FILL.setColor(((int) (t.hover * 40f) << 24) | 0x00FFFFFF);
            c.drawCircle(0f, 0f, r - 3f, FILL);
        }
        if (progress > 0f) {
            R.set(-r - 14f, -r - 14f, r + 14f, r + 14f);
            RING.setStrokeWidth(14f);
            RING.setColor(0x55FFFFFF);
            c.drawArc(R, 0f, 360f, false, RING);
            RING.setColor(ringColour | 0xFF000000);
            c.drawArc(R, -90f, 360f * Math.min(1f, progress), false, RING);
        }
        c.restore();
    }

    /**
     * Draws the remote focus ring around a round tile.
     * @param c canvas
     * @param t the round tile
     * @param time seconds, for a gentle pulse
     */
    static void drawFocus(Canvas c, TileMenu.Tile t, float time) {
        float cx = t.x + t.w / 2f, cy = t.y + t.h / 2f;
        float s = 1f + 0.08f * Ease.outBack(t.hover);
        float grow = 16f + 4f * (float) Math.sin(time * 6f);
        float r = DIAMETER / 2f * s + grow;
        LINE.setStrokeWidth(12f);
        LINE.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, r, LINE);
        LINE.setStrokeWidth(6f);
        LINE.setColor(Theme.INK);
        c.drawCircle(cx, cy, r + 8f, LINE);
    }
}
