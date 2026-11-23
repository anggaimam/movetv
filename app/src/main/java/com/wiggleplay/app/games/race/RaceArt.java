package com.wiggleplay.app.games.race;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.ui.TileMenu;

/**
 * The 2D pictures of Turbo Trucks, drawn in code in the flat storybook style: the tile on the game menu (a little
 * truck seen from the side that bounces along a road, its wheels turning, speed lines streaming past, like the
 * plane of Sky Glider), the kid figure and rocket used by the rules pictures and the HUD. The 3D world itself is
 * OpenGL; nothing here is used per frame except these cheap shape draws, and nothing is allocated while drawing.
 */
public final class RaceArt {

    /** Palette (flat storybook). */
    public static final int INK = 0xFF2F3A45, PAPER = 0xFFF3EBDD, BLUE = 0xFF5B8DB8, SAGE = 0xFF9CB89A,
            ORANGE = 0xFFE89A4C, MUSTARD = 0xFFF2C572, PINK = 0xFFE8A598, SKY = 0xFFA8DDF2;

    private static final int TRUCK_BODY = 0xFFE0605A, TRUCK_DARK = 0xFFB9423E, ROAD = 0xFF3F4A56;

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();
    private static final Path ROCKET = new Path();

    static {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setStrokeJoin(Paint.Join.ROUND);
        // a rocket pointing up, in a box x -50..50, y -100..100 (built once)
        ROCKET.moveTo(0f, -100f);
        ROCKET.cubicTo(38f, -64f, 40f, -10f, 34f, 46f);
        ROCKET.lineTo(34f, 64f);
        ROCKET.lineTo(-34f, 64f);
        ROCKET.lineTo(-34f, 46f);
        ROCKET.cubicTo(-40f, -10f, -38f, -64f, 0f, -100f);
        ROCKET.close();
    }

    private RaceArt() { }

    /** Lets go of every baked picture (called when the game is left). */
    static void unload() {
        SpriteCache.shared().removePrefix("race_");
        SpriteCache.shared().removePrefix("tile_");
        SpriteCache.shared().removePrefix("tileicon_");
    }

    // ---- menu tile ----

    /**
     * The still picture on the menu tile.
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void cover(Canvas c, int w, int h) {
        scene(c, w / 2f, h / 2f, Math.min(w, h), 0.6f, 0f);
    }

    /** The moving tile picture (tile highlighted): the truck bounces along, wheels turn, speed lines stream past. */
    public static final TileMenu.LiveIcon LIVE = (c, cx, cy, size, hover, time) -> scene(c, cx, cy, size, time, hover);

    private static void scene(Canvas c, float cx, float cy, float size, float time, float hover) {
        float u = size / 100f;
        float roadY = cy + 31f * u;
        // speed lines streaming backwards
        LINE.setColor(0xFFFFFFFF);
        LINE.setStrokeWidth(1.4f * u);
        for (int i = 0; i < 4; i++) {
            float f = (time * 0.9f + i * 0.27f) % 1f;
            float y = cy - 22f * u + i * 13f * u;
            float x = cx + 44f * u - f * 92f * u;
            LINE.setAlpha((int) (170 * Math.sin(Math.PI * f) * (0.35f + 0.65f * hover)));
            c.drawLine(x, y, x + 14f * u, y, LINE);
        }
        LINE.setAlpha(255);
        // the road with dashes moving backwards
        FILL.setColor(ROAD);
        R.set(cx - 48f * u, roadY - 2f * u, cx + 48f * u, roadY + 9f * u);
        c.drawRoundRect(R, 4f * u, 4f * u, FILL);
        FILL.setColor(PAPER);
        float shift = (time * 36f * u) % (20f * u);
        for (int i = 0; i < 5; i++) {
            float x = cx + 44f * u - i * 20f * u - shift;
            if (x < cx - 46f * u) x += 100f * u;
            R.set(x - 5f * u, roadY + 3f * u, x + 5f * u, roadY + 5f * u);
            c.drawRect(R, FILL);
        }
        float bob = (float) Math.sin(time * 9f) * 1.1f * u * (0.4f + 0.6f * hover);
        truckSide(c, cx, roadY - 20f * u + bob, u, time * 9f);
    }

    /**
     * Draws the red truck seen from the side, facing right.
     * @param c canvas
     * @param cx centre x
     * @param baseY y of the wheel centres' level (ground contact is about 12 units lower)
     * @param u size of one unit (the truck is about 90 units long)
     * @param wheelAngle wheel turn in radians
     */
    public static void truckSide(Canvas c, float cx, float baseY, float u, float wheelAngle) {
        // shadow
        FILL.setColor(0x33000000);
        R.set(cx - 44f * u, baseY + 30f * u, cx + 44f * u, baseY + 34f * u);
        c.drawOval(R, FILL);
        // cargo bed, chassis, cab
        FILL.setColor(TRUCK_DARK);
        R.set(cx - 45f * u, baseY - 18f * u, cx - 4f * u, baseY + 2f * u);
        c.drawRoundRect(R, 4f * u, 4f * u, FILL);
        FILL.setColor(TRUCK_BODY);
        R.set(cx - 45f * u, baseY - 2f * u, cx + 45f * u, baseY + 17f * u);
        c.drawRoundRect(R, 7f * u, 7f * u, FILL);
        R.set(cx - 2f * u, baseY - 26f * u, cx + 33f * u, baseY + 3f * u);
        c.drawRoundRect(R, 8f * u, 8f * u, FILL);
        FILL.setColor(SKY);
        R.set(cx + 8f * u, baseY - 21f * u, cx + 29f * u, baseY - 7f * u);
        c.drawRoundRect(R, 4f * u, 4f * u, FILL);
        FILL.setColor(MUSTARD);
        c.drawCircle(cx + 42f * u, baseY + 7f * u, 3.2f * u, FILL);
        FILL.setColor(PAPER);
        R.set(cx - 45f * u, baseY + 9f * u, cx + 45f * u, baseY + 11.5f * u);
        c.drawRect(R, FILL);
        wheel(c, cx - 26f * u, baseY + 17f * u, 12f * u, wheelAngle);
        wheel(c, cx + 24f * u, baseY + 17f * u, 12f * u, wheelAngle);
    }

    private static void wheel(Canvas c, float x, float y, float r, float angle) {
        FILL.setColor(INK);
        c.drawCircle(x, y, r, FILL);
        FILL.setColor(PAPER);
        c.drawCircle(x, y, r * 0.42f, FILL);
        LINE.setColor(PAPER);
        LINE.setStrokeWidth(r * 0.2f);
        for (int i = 0; i < 4; i++) {
            float a = angle + i * (float) (Math.PI / 2.0);
            c.drawLine(x + (float) Math.cos(a) * r * 0.42f, y + (float) Math.sin(a) * r * 0.42f,
                    x + (float) Math.cos(a) * r * 0.86f, y + (float) Math.sin(a) * r * 0.86f, LINE);
        }
        FILL.setColor(INK);
        c.drawCircle(x, y, r * 0.16f, FILL);
    }

    // ---- shared little pictures ----

    /**
     * Draws a rocket pointing up.
     * @param c canvas
     * @param cx centre x
     * @param cy centre y
     * @param h height of the rocket body (the box is 200 units tall, so this scales it)
     * @param body body colour
     * @param flame 0 for no flame, up to 1 for a full flame (it flickers by itself with {@code time})
     * @param time seconds, for the flame flicker
     */
    public static void rocket(Canvas c, float cx, float cy, float h, int body, float flame, float time) {
        float s = h / 200f;
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        if (flame > 0.01f) {
            float flick = 1f + 0.18f * (float) Math.sin(time * 40f);
            FILL.setColor(ORANGE);
            tri(c, 0f, 64f + 120f * flame * flick, 26f);
            FILL.setColor(MUSTARD);
            tri(c, 0f, 64f + 66f * flame * flick, 14f);
        }
        FILL.setColor(body);
        c.drawPath(ROCKET, FILL);
        FILL.setColor(SKY);
        c.drawCircle(0f, -30f, 17f, FILL);
        FILL.setColor(INK);
        LINE.setColor(INK);
        LINE.setStrokeWidth(5f);
        LINE.setAlpha(255);
        FILL.setColor(PINK);
        R.set(-62f, 22f, -30f, 70f);
        c.drawRoundRect(R, 10f, 10f, FILL);
        R.set(30f, 22f, 62f, 70f);
        c.drawRoundRect(R, 10f, 10f, FILL);
        c.restore();
    }

    /** A filled flame triangle hanging down from y=top-ish: tip at (x, tipY), half-width hw, base at y=64. */
    private static void tri(Canvas c, float x, float tipY, float hw) {
        Path p = TRI;
        p.rewind();
        p.moveTo(x - hw, 64f);
        p.lineTo(x + hw, 64f);
        p.lineTo(x, tipY);
        p.close();
        c.drawPath(p, FILL);
    }

    private static final Path TRI = new Path();

    /**
     * Draws a stick-figure kid (flat colours): round head, body, legs, and arms either hanging or held out.
     * @param c canvas
     * @param cx centre x
     * @param footY y of the feet
     * @param h height of the standing figure
     * @param armsOut 0 = arms hanging, 1 = held straight out sideways
     * @param tiltDeg wing tilt in degrees (positive = screen-left hand lower)
     * @param crouch 0 = standing, 1 = fully crouched
     * @param colour clothes colour
     */
    public static void kid(Canvas c, float cx, float footY, float h, float armsOut, float tiltDeg, float crouch, int colour) {
        float lw = h * 0.085f;
        float hip = footY - h * (0.40f - 0.10f * crouch);
        float shoulder = hip - h * (0.34f - 0.08f * crouch);
        float headR = h * 0.12f;
        float headY = shoulder - h * 0.15f;
        LINE.setStrokeWidth(lw);
        LINE.setAlpha(255);
        LINE.setColor(INK);
        // legs: bent when crouching
        float knee = h * 0.10f * crouch;
        c.drawLine(cx, hip, cx - h * 0.12f - knee, footY - h * 0.2f * (1f - crouch) - 0.5f * lw, LINE);
        c.drawLine(cx, hip, cx + h * 0.12f + knee, footY - h * 0.2f * (1f - crouch) - 0.5f * lw, LINE);
        c.drawLine(cx - h * 0.12f - knee, footY - h * 0.2f * (1f - crouch) - 0.5f * lw, cx - h * 0.12f, footY - 0.5f * lw, LINE);
        c.drawLine(cx + h * 0.12f + knee, footY - h * 0.2f * (1f - crouch) - 0.5f * lw, cx + h * 0.12f, footY - 0.5f * lw, LINE);
        // body
        LINE.setColor(colour);
        LINE.setStrokeWidth(h * 0.16f);
        c.drawLine(cx, shoulder + h * 0.04f, cx, hip, LINE);
        // arms
        LINE.setStrokeWidth(lw);
        float armLen = h * 0.40f;
        float a = (float) Math.toRadians(tiltDeg);
        float out = armsOut;
        float lx = cx - (armLen * (float) Math.cos(a)) * out - h * 0.10f * (1f - out);
        float ly = shoulder + (armLen * (float) Math.sin(a)) * out + h * 0.30f * (1f - out);
        float rx = cx + (armLen * (float) Math.cos(a)) * out + h * 0.10f * (1f - out);
        float ry = shoulder - (armLen * (float) Math.sin(a)) * out + h * 0.30f * (1f - out);
        c.drawLine(cx - h * 0.07f, shoulder, lx, ly, LINE);
        c.drawLine(cx + h * 0.07f, shoulder, rx, ry, LINE);
        // head
        FILL.setColor(0xFFF0C8A0);
        c.drawCircle(cx, headY, headR, FILL);
        FILL.setColor(INK);
        c.drawCircle(cx - headR * 0.35f, headY - headR * 0.1f, headR * 0.11f, FILL);
        c.drawCircle(cx + headR * 0.35f, headY - headR * 0.1f, headR * 0.11f, FILL);
    }

    /**
     * Draws a coin (gold disc with a ring).
     * @param c canvas
     * @param cx centre x
     * @param cy centre y
     * @param r radius
     */
    public static void coin(Canvas c, float cx, float cy, float r) {
        FILL.setColor(0xFFE0A100);
        c.drawCircle(cx, cy, r, FILL);
        FILL.setColor(0xFFFFD04D);
        c.drawCircle(cx, cy, r * 0.82f, FILL);
        LINE.setColor(0xFFE0A100);
        LINE.setStrokeWidth(r * 0.14f);
        LINE.setAlpha(255);
        c.drawCircle(cx, cy, r * 0.52f, LINE);
    }
}
