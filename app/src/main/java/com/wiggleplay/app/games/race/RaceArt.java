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
        kid(c, cx, footY, h, armsOut, tiltDeg, crouch, colour, true);
    }

    private static void kid(Canvas c, float cx, float footY, float h, float armsOut, float tiltDeg, float crouch,
                            int colour, boolean drawArms) {
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
        if (drawArms) {
            c.drawLine(cx - h * 0.07f, shoulder, lx, ly, LINE);
            c.drawLine(cx + h * 0.07f, shoulder, rx, ry, LINE);
        }
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

    // ---- T-23: the three steering modes ----

    /** Steering modes: airplane arms (no longer chosen in the race), body lean, steering wheel. */
    public static final int MODE_PLANE = 0, MODE_LEAN = 1, MODE_WHEEL = 2;

    /**
     * The mode number of a saved steering name.
     * @param name "lean" or "wheel" (anything else is "wheel"; plane arms are no longer offered in the race)
     * @return MODE_PLANE, MODE_LEAN or MODE_WHEEL
     */
    public static int modeOf(String name) {
        if ("lean".equals(name)) return MODE_LEAN;
        return MODE_WHEEL;
    }

    /**
     * A kid with the arms down, the whole body leaning around the feet.
     * @param leanDeg degrees, positive = leans to the screen-left (the screen-left shoulder lower)
     */
    public static void kidLean(Canvas c, float cx, float footY, float h, float leanDeg, int colour) {
        c.save();
        c.rotate(-leanDeg, cx, footY);
        kid(c, cx, footY, h, 0f, 0f, 0f, colour, true);
        c.restore();
    }

    /**
     * A kid holding a steering wheel in front of the chest with both hands.
     * @param wheelDeg how far the wheel is turned, positive = the screen-left hand lower (turning left)
     */
    public static void kidWheel(Canvas c, float cx, float footY, float h, float wheelDeg, int colour) {
        kid(c, cx, footY, h, 0f, 0f, 0f, colour, false);
        float hip = footY - h * 0.40f, shoulder = hip - h * 0.34f;
        float wy = shoulder + h * 0.15f, wr = h * 0.17f;
        float a = (float) Math.toRadians(wheelDeg);
        float hx = wr * (float) Math.cos(a), hy = wr * (float) Math.sin(a);
        wheelShape(c, cx, wy, wr, wheelDeg, h * 0.05f);
        LINE.setColor(colour);
        LINE.setStrokeWidth(h * 0.085f);
        c.drawLine(cx - h * 0.07f, shoulder, cx - hx, wy + hy, LINE);
        c.drawLine(cx + h * 0.07f, shoulder, cx + hx, wy - hy, LINE);
        FILL.setColor(0xFFF0C8A0);
        c.drawCircle(cx - hx, wy + hy, h * 0.05f, FILL);
        c.drawCircle(cx + hx, wy - hy, h * 0.05f, FILL);
    }

    /** A steering wheel: rim, three spokes and a hub, turned by deg (positive = counter-clockwise on screen). */
    private static void wheelShape(Canvas c, float cx, float cy, float r, float deg, float lw) {
        c.save();
        c.rotate(-deg, cx, cy);
        LINE.setColor(INK);
        LINE.setStrokeWidth(lw);
        c.drawCircle(cx, cy, r, LINE);
        LINE.setStrokeWidth(lw * 0.8f);
        c.drawLine(cx - r, cy, cx + r, cy, LINE);
        c.drawLine(cx, cy, cx, cy + r, LINE);
        FILL.setColor(INK);
        c.drawCircle(cx, cy, lw * 1.1f, FILL);
        c.restore();
    }

    /** Picture for a steering choice tile: the kid making that gesture. */
    public static void paintSteerTile(Canvas c, int w, int h, int mode) {
        float cx = w / 2f, foot = h * 0.97f, kh = h * 0.92f;
        com.wiggleplay.app.ui.HowToFigure f = new com.wiggleplay.app.ui.HowToFigure();
        if (mode == MODE_LEAN) f.draw(c, com.wiggleplay.app.ui.HowToPose.LEAN_RIGHT, cx, foot, kh, com.wiggleplay.app.ui.HowToFigure.BLUE);
        else if (mode == MODE_WHEEL) f.draw(c, com.wiggleplay.app.ui.HowToPose.WHEEL, cx, foot, kh, com.wiggleplay.app.ui.HowToFigure.BLUE);
        else kid(c, cx, foot, kh, 1f, 14f, 0f, 0xFF29B6F6);
    }

    /** The Sky Glider's red horseshoe magnet, drawn centred at (cx, cy) with the given size. */
    public static void magnet(Canvas c, float cx, float cy, float size) {
        c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(size / 192f, size / 192f);
        com.wiggleplay.app.games.sky.SkyArt.paintMagnet(c, 192, 192);
        c.restore();
    }

    private static final Path CAN_BOLT = new Path();

    /** A nitro can (blue, white band, orange cap, yellow bolt), centred at (cx, cy), about size tall. */
    public static void can(Canvas c, float cx, float cy, float size) {
        float u = size / 100f;
        FILL.setColor(0xFF2E9BE8);
        R.set(cx - 30f * u, cy - 26f * u, cx + 30f * u, cy + 46f * u);
        c.drawRoundRect(R, 9f * u, 9f * u, FILL);
        FILL.setColor(0xFFF5F5F5);
        c.drawRect(cx - 30f * u, cy - 6f * u, cx + 30f * u, cy + 14f * u, FILL);
        FILL.setColor(0xFFFF7E44);
        R.set(cx - 12f * u, cy - 44f * u, cx + 12f * u, cy - 22f * u);
        c.drawRoundRect(R, 5f * u, 5f * u, FILL);
        FILL.setColor(0xFF2E9BE8);
        c.drawRect(cx - 18f * u, cy - 30f * u, cx + 18f * u, cy - 20f * u, FILL);
        FILL.setColor(0xFFFFE14D);
        Path p = CAN_BOLT;
        p.rewind();
        p.moveTo(cx + 8f * u, cy - 18f * u);
        p.lineTo(cx - 14f * u, cy + 14f * u);
        p.lineTo(cx + 2f * u, cy + 14f * u);
        p.lineTo(cx - 8f * u, cy + 42f * u);
        p.lineTo(cx + 16f * u, cy + 6f * u);
        p.lineTo(cx, cy + 6f * u);
        p.close();
        c.drawPath(p, FILL);
    }

    /** Picture for the steering button on the level screen: a steering wheel (wheel mode) or the leaning body (lean mode). */
    public static void paintSteerButton(Canvas c, int w, int h, int mode) {
        if (mode == MODE_LEAN) paintSteerTile(c, w, h, MODE_LEAN);
        else paintIcon(c, w, h, MODE_WHEEL, 0xFF2B3A4A);
    }

    /** Size of the baked steering icons. */
    static final int ICON = 240;

    /**
     * The HUD / intro icon of a steering mode, baked once (240 x 240, centred, drawn upright; rotate it to tilt).
     * @param mode MODE_PLANE, MODE_LEAN or MODE_WHEEL
     * @param active true for the player colour, false for grey (the gesture is not being made)
     * @param colour the player colour
     * @return the bitmap
     */
    static android.graphics.Bitmap steerIcon(int mode, boolean active, int colour) {
        final int col = active ? colour : 0xFF9AA5AD;
        final int m = mode;
        return SpriteCache.shared().get("race_steer_" + mode + (active ? "_on" : "_off"), ICON, ICON,
                (c, w, h) -> paintIcon(c, w, h, m, col));
    }

    private static void paintIcon(Canvas c, int w, int h, int mode, int col) {
        float cx = w / 2f, cy = h / 2f, u = w / 240f;
        if (mode == MODE_LEAN) {
            // a leaning stick kid seen from the front, arms down
            float lw = 22f * u;
            LINE.setStrokeWidth(lw + 12f * u);
            LINE.setColor(0xFFFFFFFF);
            leanFigure(c, cx, cy, u);
            LINE.setStrokeWidth(lw);
            LINE.setColor(col);
            leanFigure(c, cx, cy, u);
            FILL.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy - 62f * u, 34f * u, FILL);
            FILL.setColor(col);
            c.drawCircle(cx, cy - 62f * u, 26f * u, FILL);
        } else if (mode == MODE_WHEEL) {
            for (int pass = 0; pass < 2; pass++) {
                LINE.setColor(pass == 0 ? 0xFFFFFFFF : col);
                LINE.setStrokeWidth((pass == 0 ? 34f : 22f) * u);
                c.drawCircle(cx, cy, 82f * u, LINE);
                LINE.setStrokeWidth((pass == 0 ? 28f : 16f) * u);
                c.drawLine(cx - 82f * u, cy, cx + 82f * u, cy, LINE);
                c.drawLine(cx, cy, cx, cy + 82f * u, LINE);
                FILL.setColor(pass == 0 ? 0xFFFFFFFF : col);
                c.drawCircle(cx, cy, (pass == 0 ? 30f : 22f) * u, FILL);
            }
            // a mark at the top so the turn is easy to see
            FILL.setColor(ORANGE);
            c.drawCircle(cx, cy - 82f * u, 14f * u, FILL);
        } else {
            // the plane: wings, body and tail (as in T-22)
            LINE.setStrokeWidth(40f * u);
            LINE.setColor(0xFFFFFFFF);
            c.drawLine(cx - 96f * u, cy, cx + 96f * u, cy, LINE);
            LINE.setStrokeWidth(28f * u);
            LINE.setColor(col);
            c.drawLine(cx - 96f * u, cy, cx + 96f * u, cy, LINE);
            FILL.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, 33f * u, FILL);
            FILL.setColor(col);
            c.drawCircle(cx, cy, 24f * u, FILL);
            LINE.setStrokeWidth(16f * u);
            c.drawLine(cx, cy - 3f * u, cx, cy - 54f * u, LINE);
        }
    }

    private static void leanFigure(Canvas c, float cx, float cy, float u) {
        c.drawLine(cx, cy - 30f * u, cx, cy + 40f * u, LINE);            // body
        c.drawLine(cx, cy + 40f * u, cx - 26f * u, cy + 96f * u, LINE);  // legs
        c.drawLine(cx, cy + 40f * u, cx + 26f * u, cy + 96f * u, LINE);
        c.drawLine(cx - 4f * u, cy - 22f * u, cx - 34f * u, cy + 40f * u, LINE); // arms down
        c.drawLine(cx + 4f * u, cy - 22f * u, cx + 34f * u, cy + 40f * u, LINE);
    }
}
