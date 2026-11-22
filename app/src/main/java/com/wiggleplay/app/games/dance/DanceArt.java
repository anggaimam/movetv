package com.wiggleplay.app.games.dance;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.Brushes;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.PlayerPose;

import java.util.Random;

/**
 * Neon arcade art for Animal Dance Party: a dark stage with a glowing floor, eight animal hosts (monkey, sheep, bear,
 * dog, cat, flamingo, fox, gecko) drawn as neon outlines, sweeping stage lights, pose cards with a stick figure, and the glowing
 * outline of the players' own bodies. Glow is a wide low-opacity line under a thin bright one: no blur, no gradients.
 * Everything big is baked once into {@link SpriteCache}; per frame only a few lines and one bitmap per host are drawn.
 */
final class DanceArt {

    static final int BG = 0xFF140B2E, PANEL = 0xFF1E1245, PANEL_LIGHT = 0xFF2A1A5E;
    static final int CYAN = 0xFF7FF4FF, PINK = 0xFFFF4FD8, ORANGE = 0xFFFFC27A, GREEN = 0xFF6BFF8E, GOLD = 0xFFFFD84A,
            WHITE = 0xFFFFFFFF;

    static final int HOST_W = 440, HOST_H = 620;

    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SPRITE = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final RectF R = new RectF();
    private static final Path BEAM = new Path();
    private static final int[] BEAM_COLOURS = {CYAN, PINK, ORANGE, GREEN};
    private static final float[] ANG = new float[4];

    static {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setStrokeJoin(Paint.Join.ROUND);
    }

    private DanceArt() { }

    // ---- the stage ----

    /**
     * Draws the dark stage (floor grid, stars, speakers).
     * @param c canvas in stage coordinates
     */
    static void stage(Canvas c, int theme) {
        int t = Math.max(0, Math.min(THEMES - 1, theme));
        Bitmap b = SpriteCache.shared().get(STAGE_KEYS[t], 1920, 1080, STAGE_PAINTERS[t]);
        c.drawBitmap(b, 0f, 0f, null);
    }

    /** Number of stage colour schemes, and their names (a new one is unlocked with each level finished with 3 stars). */
    static final int THEMES = 4;
    static final String[] THEME_NAMES = {"Disco", "Aurora", "Sunset", "Starlight"};
    private static final String[] STAGE_KEYS = {"dance_stage_0", "dance_stage_1", "dance_stage_2", "dance_stage_3"};
    // floor lines across, floor lines into the distance, horizon glow
    private static final int[][] THEME_COLOURS = {
            {CYAN, PINK, PINK}, {GREEN, CYAN, CYAN}, {ORANGE, PINK, ORANGE}, {GOLD, 0xFFB07CFF, 0xFFB07CFF}};
    private static final SpriteCache.Painter[] STAGE_PAINTERS = {
            (c, w, h) -> paintStage(c, w, h, 0), (c, w, h) -> paintStage(c, w, h, 1),
            (c, w, h) -> paintStage(c, w, h, 2), (c, w, h) -> paintStage(c, w, h, 3)};

    private static void line(Canvas c, float x0, float y0, float x1, float y1, int colour, float w) {
        LINE.setColor((colour & 0x00FFFFFF) | (70 << 24));
        LINE.setStrokeWidth(w * 3.2f);
        c.drawLine(x0, y0, x1, y1, LINE);
        LINE.setColor(colour | 0xFF000000);
        LINE.setStrokeWidth(w);
        c.drawLine(x0, y0, x1, y1, LINE);
    }

    private static void paintStage(Canvas c, int w, int h, int theme) {
        int[] col = THEME_COLOURS[theme];
        c.drawColor(BG);
        // far glow bands at the horizon
        FILL.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 5; i++) {
            FILL.setColor(((col[2] & 0x00FFFFFF) | ((14 + i * 6) << 24)));
            c.drawRect(0f, 560f + i * 22f, w, 640f, FILL);
        }
        // stars
        Random r = new Random(7);
        FILL.setColor(0xFFFFFFFF);
        for (int i = 0; i < 70; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * 520f, s = 2f + r.nextFloat() * 3f;
            FILL.setAlpha(80 + r.nextInt(150));
            c.drawCircle(x, y, s, FILL);
        }
        // floor grid in perspective: horizon at y = 640, vanishing point in the middle
        float horizon = 640f, vx = w / 2f;
        for (int i = 0; i < 9; i++) {
            float t = i / 8f;
            float y = horizon + (h - horizon) * t * t + 4f;
            line(c, 0f, y, w, y, col[0], 3f);
        }
        for (int i = -10; i <= 10; i++) {
            float bx = vx + i * 260f;
            line(c, vx + i * 40f, horizon, bx, h + 40f, col[1], 3f);
        }
        line(c, 0f, horizon, w, horizon, col[1], 5f);
        // speakers on both sides
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? 20f : w - 200f;
            R.set(x, 380f, x + 180f, 700f);
            FILL.setColor(PANEL);
            FILL.setAlpha(255);
            c.drawRoundRect(R, 22f, 22f, FILL);
            Path p = new Path();
            p.addRoundRect(R, 22f, 22f, Path.Direction.CW);
            Brushes.neonStroke(c, p, ORANGE, 5f);
            Path cone = new Path();
            cone.addCircle(x + 90f, 470f, 36f, Path.Direction.CW);
            cone.addCircle(x + 90f, 600f, 52f, Path.Direction.CW);
            Brushes.neonStroke(c, cone, side == 0 ? CYAN : PINK, 5f);
        }
    }

    /**
     * Draws the sweeping stage lights (four translucent beams from the top).
     * @param c canvas
     * @param beatPos time in beats, drives the sweep
     * @param boost 0..1 how bright to make them (the players are doing well)
     * @param beams how many beams, 2..4 (the party grows)
     */
    static void lights(Canvas c, float beatPos, float boost, int beams) {
        int[] cols = BEAM_COLOURS;
        int alpha = (int) (28 + 40 * boost);
        for (int i = 0; i < beams; i++) {
            float baseX = 330f + i * (beams > 1 ? 1260f / (beams - 1) : 0f);
            float sweep = (float) Math.sin(beatPos * 0.5f + i * 1.7f) * 220f;
            float tx = baseX + sweep;
            BEAM.reset();
            BEAM.moveTo(baseX - 26f, -10f);
            BEAM.lineTo(baseX + 26f, -10f);
            BEAM.lineTo(tx + 170f, 760f);
            BEAM.lineTo(tx - 170f, 760f);
            BEAM.close();
            FILL.setStyle(Paint.Style.FILL);
            FILL.setColor((cols[i] & 0x00FFFFFF) | (alpha << 24));
            c.drawPath(BEAM, FILL);
        }
    }

    /**
     * Draws a hanging disco ball with sparkles.
     * @param c canvas
     * @param x centre x
     * @param y centre y
     * @param r radius
     * @param beatPos time in beats
     */
    static void discoBall(Canvas c, float x, float y, float r, float beatPos) {
        line(c, x, -10f, x, y - r, WHITE, 3f);
        BEAM.reset();
        BEAM.addCircle(x, y, r, Path.Direction.CW);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(PANEL_LIGHT);
        c.drawPath(BEAM, FILL);
        Brushes.neonStroke(c, BEAM, WHITE, 4f);
        BEAM.reset();
        for (int i = -1; i <= 1; i++) {
            R.set(x - r * 0.45f * Math.abs(i == 0 ? 2 : 1) / 1f, y - r, x + r * 0.45f * Math.abs(i == 0 ? 2 : 1) / 1f, y + r);
            BEAM.addOval(R, Path.Direction.CW);
            BEAM.moveTo(x - r, y + i * r * 0.5f);
            BEAM.lineTo(x + r, y + i * r * 0.5f);
        }
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setColor(0x66FFFFFF);
        LINE.setStrokeWidth(2f);
        c.save();
        c.clipPath(circlePath(x, y, r));
        c.drawPath(BEAM, LINE);
        c.restore();
        for (int i = 0; i < 6; i++) {
            float a = beatPos * 0.9f + i * 1.05f;
            float d = r * (1.5f + 0.5f * (float) Math.sin(a * 1.7f + i));
            FILL.setColor(i % 2 == 0 ? CYAN : PINK);
            FILL.setAlpha(190);
            c.drawCircle(x + (float) Math.cos(a) * d, y + (float) Math.sin(a) * d, 5f + (i % 3) * 2f, FILL);
        }
        FILL.setAlpha(255);
    }

    private static final Path CLIP = new Path();

    private static Path circlePath(float x, float y, float r) {
        CLIP.reset();
        CLIP.addCircle(x, y, r, Path.Direction.CW);
        return CLIP;
    }

    // ---- hosts ----
    //
    // Each host is two pictures drawn ahead of time from the approved sketches (assets/dance/<name>_body.png and
    // <name>_head.png, made at 440 x 620 and cropped) plus arms and hands drawn every frame. Drawing order makes the
    // outline look like one line: arm outlines, then the body (its inside is filled with the stage colour, so it hides
    // the arm outline under it), then the arm insides (they wipe the body outline where an arm leaves the body), then
    // the head (arms pass behind it), then the hands on top of everything. The flamingo's wings sit behind its body.
    //
    // Arms and hands use the sketches' own units ("rig" units): the floor is at y = 352, the middle at x = 200,
    // and one rig unit is S host-picture pixels.

    private static final float S = 1.7f, T = 4.5f;
    private static final int RIG_CYAN = 0xFF22E3F0, RIG_PINK = 0xFFFF4DA6, RIG_FLAMINGO = 0xFFFF5CB8;

    static final int HOSTS = 8;
    static final String[] HOST_NAMES = {"Monkey", "Sheep", "Bear", "Dog", "Cat", "Flamingo", "Fox", "Gecko"};
    private static final String[] HOST_FILES = {"monkey", "sheep", "bear", "dog", "cat", "flamingo", "fox", "gecko"};
    // top-left corner of each cropped picture inside the 440 x 620 host picture
    private static final int[][] BODY_OFF = {{115, 270}, {127, 255}, {94, 253}, {118, 258}, {123, 256}, {145, 170},
            {125, 263}, {110, 236}};
    private static final int[][] HEAD_OFF = {{36, 32}, {38, 56}, {60, 42}, {18, 61}, {82, 34}, {135, 18}, {65, 32},
            {76, 39}};
    private static final int HAND_MONKEY = 0, HAND_HOOF = 1, HAND_PAW = 2, HAND_GECKO = 3, HAND_WING = 4;
    private static final int[] HAND = {HAND_MONKEY, HAND_HOOF, HAND_PAW, HAND_PAW, HAND_PAW, HAND_WING, HAND_PAW,
            HAND_GECKO};
    // rig: hip y, shoulder half-width, shoulder y (from the hip), upper arm, forearm, upper arm width, forearm width,
    // nose y (from the hip), hip half-width (where a hand on the hip goes)
    private static final float[][] RIG = {
            {268f, 28f, -84f, 50f, 44f, 15f, 13f, -134f, 34f},
            {272f, 28f, -96f, 40f, 36f, 10f, 10f, -134f, 40f},
            {262f, 38f, -84f, 44f, 38f, 11f, 11f, -138f, 48f},
            {257f, 36f, -78f, 46f, 40f, 11f, 11f, -133f, 46f},
            {262f, 31f, -84f, 44f, 40f, 11f, 11f, -135f, 42f},
            {270f, 26f, -74f, 48f, 72f, 0f, 0f, -164f, 38f},
            {272f, 31f, -88f, 44f, 40f, 11f, 11f, -138f, 42f},
            {252f, 28f, -84f, 46f, 38f, 9f, 9f, -135f, 36f}};

    private static android.content.Context ctx;
    private static final Bitmap[] BODY_BMP = new Bitmap[HOSTS], HEAD_BMP = new Bitmap[HOSTS];
    private static final boolean[] TRIED = new boolean[HOSTS];

    private static final Paint ARM = new Paint(Paint.ANTI_ALIAS_FLAG), ARM_FILL = new Paint(Paint.ANTI_ALIAS_FLAG),
            WING = new Paint(Paint.ANTI_ALIAS_FLAG), DOT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path WING_UPPER, WING_FORE, WING_UPPER_LINE, WING_FORE_LINE, WING_FORE_BLUE, MITTEN;
    private static final float[] GECKO_FINGERS = {-50f, 18f, -17f, 20f, 16f, 20f, 48f, 17f};

    static {
        ARM.setStyle(Paint.Style.STROKE);
        ARM.setStrokeJoin(Paint.Join.ROUND);
        ARM_FILL.setStyle(Paint.Style.FILL_AND_STROKE);
        ARM_FILL.setStrokeJoin(Paint.Join.ROUND);
        WING.setStyle(Paint.Style.FILL_AND_STROKE);
        WING.setStrokeJoin(Paint.Join.ROUND);
        WING.setShader(new android.graphics.LinearGradient(-20f, 0f, 30f, 0f,
                new int[]{RIG_FLAMINGO, RIG_FLAMINGO, RIG_CYAN, RIG_CYAN}, new float[]{0f, 0.5f, 0.5f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        DOT.setStyle(Paint.Style.FILL);
        WING_UPPER = union(circle(1f, 0f, 12f), poly(-10f, 0f, -11f, 48f, 14f, 48f, 12f, 0f), circle(13f, 9f, 8f),
                circle(14f, 24f, 8f), circle(14f, 38f, 8f));
        Path fore = new Path();
        fore.moveTo(-11f, 0f);
        fore.quadTo(-9f, 45f, 0f, 80f);
        fore.lineTo(6f, 72f);
        fore.lineTo(14f, 50f);
        fore.lineTo(18f, 20f);
        fore.lineTo(16f, 0f);
        fore.close();
        WING_FORE = union(circle(2f, 0f, 13f), fore, circle(19f, 8f, 9f), circle(21f, 23f, 9f), circle(19f, 38f, 9f),
                circle(15f, 52f, 8f), circle(9f, 65f, 7f), circle(3f, 76f, 5f));
        WING_UPPER_LINE = scallops(-1f, 6f, -1f, 21f, -1f, 36f);
        WING_FORE_LINE = scallops(-2f, 6f, -1f, 20f, 0f, 34f);
        WING_FORE_BLUE = scallops(8f, 8f, 9f, 22f, 8f, 36f, 5f, 50f);
        MITTEN = new Path();
        MITTEN.moveTo(-4.6f, 17f);
        R.set(-11f, -4f, 11f, 18f);
        MITTEN.arcTo(R, 114.7f, 310.6f, false);
        MITTEN.lineTo(0f, 10f);
        MITTEN.close();
    }

    private static Path union(Path... parts) {
        Path p = new Path(parts[0]);
        for (int i = 1; i < parts.length; i++) p.op(parts[i], Path.Op.UNION);
        return p;
    }

    /** Half-circle bumps through the given points, bulging to the right of the direction of travel. */
    private static Path scallops(float... xy) {
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            float x0 = xy[i - 2], y0 = xy[i - 1], x1 = xy[i], y1 = xy[i + 1];
            float mx = (x0 + x1) / 2f, my = (y0 + y1) / 2f;
            float r = (float) Math.hypot(x1 - x0, y1 - y0) / 2f;
            R.set(mx - r, my - r, mx + r, my + r);
            p.arcTo(R, (float) Math.toDegrees(Math.atan2(y0 - my, x0 - mx)), 180f, false);
        }
        return p;
    }

    /**
     * Gives the host pictures access to the app's assets (call once at start).
     * @param context any context
     */
    static void init(android.content.Context context) {
        ctx = context.getApplicationContext();
    }

    /**
     * Loads a host's two pictures if they are not loaded yet (do it before the host is first drawn).
     * @param which host 0..HOSTS-1
     */
    static void preload(int which) {
        if (which < 0 || which >= HOSTS || TRIED[which] || ctx == null) return;
        TRIED[which] = true;
        BODY_BMP[which] = file(HOST_FILES[which] + "_body");
        HEAD_BMP[which] = file(HOST_FILES[which] + "_head");
    }

    /** Lets go of the host pictures and the stage (called when the game is left); they load again on the next visit. */
    static void unload() {
        java.util.Arrays.fill(BODY_BMP, null);
        java.util.Arrays.fill(HEAD_BMP, null);
        java.util.Arrays.fill(TRIED, false);
        SpriteCache.shared().removePrefix("dance_");
    }

    private static Bitmap file(String name) {
        try (java.io.InputStream in = ctx.getAssets().open("dance/" + name + ".png")) {
            return android.graphics.BitmapFactory.decodeStream(in);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static final float[] POSE_ANG = new float[4];

    /**
     * Where a host's elbows and wrists are when it does a pose.
     * @param which host 0..HOSTS-1
     * @param pose pose number
     * @param o receives left elbow x, y, left wrist x, y, right elbow x, y, right wrist x, y (rig units)
     */
    static void hostArmsPose(int which, int pose, float[] o) {
        Poses.drawAngles(pose, POSE_ANG);
        hostArmsAngles(which, POSE_ANG, o);
        int near = Poses.nearSide(pose);
        if (near < 0) return;
        float[] r = RIG[which];
        float dir = near == 0 ? -1f : 1f, sx = 200f + dir * r[1], sy = r[0] + r[2], sw = 2f * r[1];
        float tx, ty;
        if (Poses.nearNose(pose)) {
            tx = 200f + dir * 0.15f * sw;
            ty = r[0] + r[7] + 0.15f * sw;
        } else {
            tx = 200f + dir * (r[8] + 4f);
            ty = r[0] - 24f;
        }
        float l1 = r[3], l2 = r[4];
        float dx = tx - sx, dy = ty - sy, full = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
        float d = Math.max(1f, Math.min(full, l1 + l2 - 1f));
        float a = (l1 * l1 - l2 * l2 + d * d) / (2f * d);
        float h = (float) Math.sqrt(Math.max(0f, l1 * l1 - a * a));
        float ux = dx / full, uy = dy / full, px = sx + ux * a, py = sy + uy * a, ox = -uy, oy = ux;
        if (ox * dir < 0f) {
            ox = -ox;
            oy = -oy;
        }
        int b = near * 4;
        o[b] = px + ox * h;
        o[b + 1] = py + oy * h;
        o[b + 2] = sx + ux * d;
        o[b + 3] = sy + uy * d;
    }

    /**
     * Where a host's elbows and wrists are for four arm directions.
     * @param which host 0..HOSTS-1
     * @param ang four angles LU, LF, RU, RF (0 = down, 90 = out, 180 = up)
     * @param o receives the eight coordinates (see {@link #hostArmsPose})
     */
    static void hostArmsAngles(int which, float[] ang, float[] o) {
        float[] r = RIG[which];
        for (int side = 0; side < 2; side++) {
            float dir = side == 0 ? -1f : 1f, sx = 200f + dir * r[1], sy = r[0] + r[2];
            double a = Math.toRadians(ang[side * 2]), b = Math.toRadians(ang[side * 2 + 1]);
            float ex = sx + dir * r[3] * (float) Math.sin(a), ey = sy + r[3] * (float) Math.cos(a);
            o[side * 4] = ex;
            o[side * 4 + 1] = ey;
            o[side * 4 + 2] = ex + dir * r[4] * (float) Math.sin(b);
            o[side * 4 + 3] = ey + r[4] * (float) Math.cos(b);
        }
    }

    /**
     * Swinging arms for a host that has nothing to copy: a dance on the beat.
     * @param beatPos time in beats
     * @param ang receives four angles
     */
    static void danceAngles(float beatPos, float[] ang) {
        float s = (float) Math.sin(Math.PI * beatPos);
        ang[0] = 70f + 50f * s;
        ang[1] = 70f + 70f * s;
        ang[2] = 70f - 50f * s;
        ang[3] = 70f - 70f * s;
    }

    private static final float[] DANCE_ANG = new float[4], DANCE_ARMS = new float[8];

    /**
     * Draws a host that just dances: arms swinging on the beat.
     * @param c canvas
     * @param which host 0..HOSTS-1
     * @param cx centre x
     * @param bottom floor y
     * @param scale 1 = full size
     * @param beatPos time in beats
     * @param excite 0..1 how big the dance is
     */
    static void hostDance(Canvas c, int which, float cx, float bottom, float scale, float beatPos, float excite) {
        danceAngles(beatPos, DANCE_ANG);
        hostArmsAngles(which, DANCE_ANG, DANCE_ARMS);
        host(c, which, cx, bottom, scale, beatPos, excite, DANCE_ARMS, 0f, false);
    }

    /** Switches the canvas from host-picture pixels to rig units (pair with {@code c.restore()}). */
    private static void rig(Canvas c) {
        c.save();
        c.translate(220f - 200f * S, 620f - 372f * S);
        c.scale(S, S);
    }

    /** The body, the head and the arms, drawn in the host picture's pixels. */
    private static void hostBody(Canvas c, int which, float[] o) {
        preload(which);
        boolean wing = HAND[which] == HAND_WING;
        rig(c);
        if (wing) wings(c, o);
        else arms(c, which, o, true);
        c.restore();
        Bitmap b = BODY_BMP[which];
        if (b != null) c.drawBitmap(b, BODY_OFF[which][0], BODY_OFF[which][1], SPRITE);
        if (!wing) {
            rig(c);
            arms(c, which, o, false);
            c.restore();
        }
        b = HEAD_BMP[which];
        if (b != null) c.drawBitmap(b, HEAD_OFF[which][0], HEAD_OFF[which][1], SPRITE);
        if (!wing) {
            rig(c);
            for (int side = 0; side < 2; side++) hand(c, which, o, side);
            c.restore();
        }
    }

    /** Both arms as tubes: the outline pass (wide, cyan) or the inside pass (narrow, stage colour). */
    private static void arms(Canvas c, int which, float[] o, boolean outline) {
        float[] r = RIG[which];
        boolean hoof = HAND[which] == HAND_HOOF;
        ARM.setColor(outline ? RIG_CYAN : BG);
        for (int side = 0; side < 2; side++) {
            float sx = 200f + (side == 0 ? -1f : 1f) * r[1], sy = r[0] + r[2];
            int b = side * 4;
            ARM.setStrokeCap(Paint.Cap.ROUND);
            ARM.setStrokeWidth(r[5] + (outline ? 2f * T : 0f));
            c.drawLine(sx, sy, o[b], o[b + 1], ARM);
            float wx = o[b + 2], wy = o[b + 3];
            if (hoof) {
                ARM.setStrokeCap(Paint.Cap.BUTT);
                if (!outline) {
                    float dx = wx - o[b], dy = wy - o[b + 1], d = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
                    wx += dx / d * 4f;
                    wy += dy / d * 4f;
                }
            }
            ARM.setStrokeWidth(r[6] + (outline ? 2f * T : 0f));
            c.drawLine(o[b], o[b + 1], wx, wy, ARM);
        }
    }

    /**
     * Turns the canvas so the wrist is at (0, 0) and the forearm points along +y (the right hand is mirrored, as in
     * the sketches). Pair with {@code c.restore()}.
     */
    private static void segment(Canvas c, float px, float py, float qx, float qy, boolean mirror, boolean atEnd) {
        c.save();
        float dx = qx - px, dy = qy - py;
        if (atEnd) c.translate(qx, qy);
        else c.translate(px, py);
        if (mirror) {
            c.scale(-1f, 1f);
            dx = -dx;
        }
        c.rotate((float) Math.toDegrees(Math.atan2(-dx, dy)));
    }

    /** One hand on the top layer: the last bit of the forearm, the hand itself, then its pads. */
    private static void hand(Canvas c, int which, float[] o, int side) {
        int b = side * 4, kind = HAND[which];
        float w = RIG[which][6];
        segment(c, o[b], o[b + 1], o[b + 2], o[b + 3], side == 1, true);
        for (int pass = 0; pass < 2; pass++) {
            boolean outline = pass == 0;
            int col = outline ? RIG_CYAN : BG;
            float grow = outline ? 2f * T : 0f;
            ARM.setColor(col);
            ARM.setStrokeCap(Paint.Cap.BUTT);
            ARM.setStrokeWidth(w + grow);
            c.drawLine(0f, outline ? -14f : -20f, 0f, kind == HAND_HOOF && !outline ? 4f : 0f, ARM);
            ARM_FILL.setColor(col);
            ARM_FILL.setStrokeWidth(grow);
            switch (kind) {
                case HAND_MONKEY:
                    c.drawCircle(0f, 6f, 10f, ARM_FILL);
                    ARM.setStrokeCap(Paint.Cap.ROUND);
                    ARM.setStrokeWidth(5f + grow);
                    for (int k = -1; k <= 1; k++) {
                        double a = Math.toRadians(38 * k);
                        float sn = (float) Math.sin(a), cs = (float) Math.cos(a);
                        c.drawLine(-sn * 6f, 2f + cs * 6f, -sn * 19f, 2f + cs * 19f, ARM);
                    }
                    break;
                case HAND_PAW:
                    c.drawCircle(0f, 0f, 15f, ARM_FILL);
                    break;
                case HAND_GECKO:
                    c.drawCircle(0f, 2f, 7f, ARM_FILL);
                    ARM.setStrokeCap(Paint.Cap.ROUND);
                    ARM.setStrokeWidth(3.2f + grow);
                    for (int k = 0; k < 8; k += 2) {
                        double a = Math.toRadians(GECKO_FINGERS[k]);
                        float l = GECKO_FINGERS[k + 1];
                        c.drawLine(0f, 2f, -(float) Math.sin(a) * l, 2f + (float) Math.cos(a) * l, ARM);
                    }
                    break;
                default:
                    break;
            }
        }
        DOT.setStyle(Paint.Style.FILL);
        switch (kind) {
            case HAND_HOOF:
                DOT.setColor(BG);
                c.drawPath(MITTEN, DOT);
                ARM.setColor(RIG_PINK);
                ARM.setStrokeWidth(5f);
                c.drawPath(MITTEN, ARM);
                break;
            case HAND_PAW:
                DOT.setColor(RIG_PINK);
                R.set(-6f, -6f, 6f, 4f);
                c.drawOval(R, DOT);
                c.drawCircle(-7f, 7.5f, 2.8f, DOT);
                c.drawCircle(0f, 10f, 2.8f, DOT);
                c.drawCircle(7f, 7.5f, 2.8f, DOT);
                break;
            case HAND_GECKO:
                for (int k = 0; k < 8; k += 2) {
                    double a = Math.toRadians(GECKO_FINGERS[k]);
                    float l = GECKO_FINGERS[k + 1] + 3f;
                    float x = -(float) Math.sin(a) * l, y = 2f + (float) Math.cos(a) * l;
                    DOT.setColor(0x59FF4DA6);
                    c.drawCircle(x, y, 5.5f, DOT);
                    ARM.setColor(RIG_PINK);
                    ARM.setStrokeWidth(3.5f);
                    c.drawCircle(x, y, 5.5f, ARM);
                }
                break;
            default:
                break;
        }
        c.restore();
    }

    /** The flamingo's wings, behind its body: outline pass, inside pass, then the feather lines. */
    private static void wings(Canvas c, float[] o) {
        float[] r = RIG[5];
        for (int pass = 0; pass < 3; pass++) {
            for (int side = 0; side < 2; side++) {
                int b = side * 4;
                float sx = 200f + (side == 0 ? -1f : 1f) * r[1], sy = r[0] + r[2];
                for (int part = 0; part < 2; part++) {
                    if (part == 0) segment(c, sx, sy, o[b], o[b + 1], side == 1, false);
                    else segment(c, o[b], o[b + 1], o[b + 2], o[b + 3], side == 1, false);
                    Path shape = part == 0 ? WING_UPPER : WING_FORE;
                    if (pass == 0) {
                        WING.setStrokeWidth(2f * T);
                        c.drawPath(shape, WING);
                    } else if (pass == 1) {
                        DOT.setColor(BG);
                        c.drawPath(shape, DOT);
                    } else {
                        ARM.setStrokeCap(Paint.Cap.ROUND);
                        ARM.setStrokeWidth(3.5f);
                        ARM.setColor(RIG_FLAMINGO);
                        c.drawPath(part == 0 ? WING_UPPER_LINE : WING_FORE_LINE, ARM);
                        if (part == 1) {
                            ARM.setColor(RIG_CYAN);
                            c.drawPath(WING_FORE_BLUE, ARM);
                        }
                    }
                    c.restore();
                }
            }
        }
    }

    /**
     * Draws a dancing host: it hops on every beat, squashes a little and sways.
     * @param c canvas
     * @param which host 0..HOSTS-1 (see {@link #HOST_NAMES})
     * @param cx centre x
     * @param bottom y of the floor under the host
     * @param scale 1 = full size (440 x 620)
     * @param beatPos time in beats
     * @param excite 0..1 how big the dance is
     * @param arms eight arm coordinates from {@link #hostArmsPose} or {@link #hostArmsAngles}
     * @param lean how far the whole host leans, in degrees
     * @param still true to stand still (no hop) as in a freeze round
     */
    static void host(Canvas c, int which, float cx, float bottom, float scale, float beatPos, float excite, float[] arms,
                     float lean, boolean still) {
        float phase = beatPos - (float) Math.floor(beatPos);
        float hop = still ? 0f : (float) Math.abs(Math.sin(Math.PI * phase));
        float amp = 18f + 22f * excite;
        float sway = still ? 0f : (float) Math.sin(beatPos * Math.PI * 0.5f) * (4f + 5f * excite);
        float sy = still ? 1f : 1f - 0.05f * (1f - hop) * (0.5f + excite);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(0x66000000);
        float sw = HOST_W * scale * (0.34f - 0.06f * hop);
        R.set(cx - sw, bottom - 14f * scale, cx + sw, bottom + 14f * scale);
        c.drawOval(R, FILL);
        c.save();
        c.translate(cx, bottom - hop * amp * scale);
        c.rotate(sway + lean);
        c.scale(scale, scale * sy);
        c.translate(-HOST_W / 2f, -HOST_H);
        hostBody(c, which, arms);
        c.restore();
    }

    private static Path circle(float cx, float cy, float r) {
        Path p = new Path();
        p.addCircle(cx, cy, r, Path.Direction.CW);
        return p;
    }

    private static Path poly(float... xy) {
        Path p = new Path();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.close();
        return p;
    }

    // ---- stick figure ----

    private static int mix(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /**
     * Draws a pose as a glowing stick figure.
     * @param c canvas
     * @param pose pose number
     * @param cx centre x between the shoulders
     * @param cy y of the shoulder line
     * @param sw shoulder width in pixels
     * @param colour line colour
     * @param width thin line width
     */
    static void figure(Canvas c, int pose, float cx, float cy, float sw, int colour, float width) {
        Poses.drawAngles(pose, ANG);
        float tilt = Poses.tilt(pose);
        float hipY = cy + 1.5f * sw;
        // legs
        line(c, cx - 0.35f * sw, hipY, cx - 0.4f * sw, hipY + 1.3f * sw, colour, width);
        line(c, cx + 0.35f * sw, hipY, cx + 0.4f * sw, hipY + 1.3f * sw, colour, width);
        c.save();
        c.rotate(tilt, cx, hipY);
        line(c, cx, cy, cx, hipY, colour, width);
        line(c, cx - 0.5f * sw, cy, cx + 0.5f * sw, cy, colour, width);
        float headY = cy - 0.8f * sw;
        FILL.setStyle(Paint.Style.FILL);
        LINE.setColor((colour & 0x00FFFFFF) | (70 << 24));
        LINE.setStrokeWidth(width * 3.2f);
        c.drawCircle(cx, headY, 0.38f * sw, LINE);
        LINE.setColor(colour);
        LINE.setStrokeWidth(width);
        c.drawCircle(cx, headY, 0.38f * sw, LINE);
        int near = Poses.nearSide(pose);
        for (int side = 0; side < 2; side++) { // 0 left, 1 right
            float dir = side == 0 ? -1f : 1f;
            float sx = cx + dir * 0.5f * sw;
            float ex, ey, wx, wy;
            if (side == near) {
                if (Poses.nearNose(pose)) {
                    wx = cx + dir * 0.18f * sw;
                    wy = headY + 0.3f * sw;
                } else {
                    wx = cx + dir * 0.45f * sw;
                    wy = hipY;
                }
                // two-bone reach, elbow out to the side
                float dx = wx - sx, dy = wy - cy, d = (float) Math.sqrt(dx * dx + dy * dy);
                float l1 = 0.9f * sw, l2 = 0.8f * sw;
                d = Math.min(d, l1 + l2 - 1f);
                float a = (l1 * l1 - l2 * l2 + d * d) / (2f * d);
                float hh = (float) Math.sqrt(Math.max(0f, l1 * l1 - a * a));
                float ux = dx / Math.max(1f, d), uy = dy / Math.max(1f, d);
                float px = sx + ux * a, py = cy + uy * a;
                float ox = -uy, oy = ux;
                if (ox * dir < 0f) {
                    ox = -ox;
                    oy = -oy;
                }
                ex = px + ox * hh;
                ey = py + oy * hh;
            } else {
                double a = Math.toRadians(ANG[side * 2]), b = Math.toRadians(ANG[side * 2 + 1]);
                ex = sx + dir * 0.9f * sw * (float) Math.sin(a);
                ey = cy + 0.9f * sw * (float) Math.cos(a);
                wx = ex + dir * 0.8f * sw * (float) Math.sin(b);
                wy = ey + 0.8f * sw * (float) Math.cos(b);
            }
            line(c, sx, cy, ex, ey, colour, width);
            line(c, ex, ey, wx, wy, colour, width);
        }
        c.restore();
    }

    /**
     * Draws a pose card: a dark panel with a neon border and an animal doing the pose.
     * @param c canvas
     * @param pose pose number
     * @param which the animal 0..3
     * @param x left
     * @param y top
     * @param w width
     * @param h height
     * @param colour border colour
     * @param pulse 0..1 extra glow on the beat
     */
    static void card(Canvas c, int pose, int which, float x, float y, float w, float h, int colour, float pulse) {
        R.set(x, y, x + w, y + h);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(0xF01E1245);
        c.drawRoundRect(R, 36f, 36f, FILL);
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setColor((colour & 0x00FFFFFF) | ((int) (60 + 60 * pulse) << 24));
        LINE.setStrokeWidth(18f);
        c.drawRoundRect(R, 36f, 36f, LINE);
        LINE.setColor(colour);
        LINE.setStrokeWidth(5f);
        c.drawRoundRect(R, 36f, 36f, LINE);
        float sc = Math.min(w / (HOST_W * 1.3f), h * 0.9f / HOST_H);
        hostArmsPose(which, pose, CARD_ARMS);
        c.save();
        c.translate(x + w / 2f, y + h * 0.95f);
        c.rotate(Poses.tilt(pose));
        c.scale(sc, sc);
        c.translate(-HOST_W / 2f, -HOST_H);
        hostBody(c, which, CARD_ARMS);
        c.restore();
    }

    private static final float[] CARD_ARMS = new float[8];

    // ---- the players' own outline ----

    /**
     * Draws a player's body as a glowing outline. Parts the pose asks for are white and turn gold as they match; parts
     * it does not ask for stay in the player's colour.
     * @param c canvas
     * @param p the body
     * @param res how well each part matches now (null = plain colour)
     * @param base player colour
     * @param width thin line width
     */
    static void body(Canvas c, PlayerPose p, Poses.Result res, int base, float width) {
        if (p == null || !p.present) return;
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        boolean shoulders = ls.s >= Poses.MIN_CONFIDENCE && rs.s >= Poses.MIN_CONFIDENCE;
        if (shoulders) line(c, ls.x, ls.y, rs.x, rs.y, base, width);
        for (int i = 0; i < 4; i++) {
            boolean left = i < 2, upper = (i & 1) == 0;
            Keypoint a = p.kps[left ? (upper ? Kp.L_SHOULDER : Kp.L_ELBOW) : (upper ? Kp.R_SHOULDER : Kp.R_ELBOW)];
            Keypoint b = p.kps[left ? (upper ? Kp.L_ELBOW : Kp.L_WRIST) : (upper ? Kp.R_ELBOW : Kp.R_WRIST)];
            if (a.s < Poses.MIN_CONFIDENCE || b.s < Poses.MIN_CONFIDENCE) continue;
            int col = base;
            if (res != null && res.seg[i] >= 0f) col = mix(WHITE, GOLD, res.seg[i]);
            line(c, a.x, a.y, b.x, b.y, col, width * 1.15f);
        }
        Keypoint lh = p.kps[Kp.L_HIP], rh = p.kps[Kp.R_HIP];
        if (shoulders && lh.s >= Poses.MIN_CONFIDENCE && rh.s >= Poses.MIN_CONFIDENCE) {
            int col = res != null && res.torso >= 0f ? mix(WHITE, GOLD, res.torso) : base;
            line(c, (ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f, (lh.x + rh.x) / 2f, (lh.y + rh.y) / 2f, col, width);
        }
        Keypoint nose = p.kps[Kp.NOSE];
        if (shoulders && nose.s >= Poses.MIN_CONFIDENCE) {
            float sw = Math.abs(rs.x - ls.x);
            LINE.setStyle(Paint.Style.STROKE);
            LINE.setColor((base & 0x00FFFFFF) | (70 << 24));
            LINE.setStrokeWidth(width * 3.2f);
            c.drawCircle(nose.x, nose.y - sw * 0.12f, sw * 0.32f, LINE);
            LINE.setColor(base);
            LINE.setStrokeWidth(width);
            c.drawCircle(nose.x, nose.y - sw * 0.12f, sw * 0.32f, LINE);
        }
    }

    /**
     * Draws a small star burst used for the team bonus and the rules.
     * @param c canvas
     * @param cx centre x
     * @param cy centre y
     * @param r radius
     * @param colour colour
     */
    static void star(Canvas c, float cx, float cy, float r, int colour) {
        BEAM.reset();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i & 1) == 0 ? r : r * 0.45f;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) BEAM.moveTo(x, y); else BEAM.lineTo(x, y);
        }
        BEAM.close();
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor((colour & 0x00FFFFFF) | (255 << 24));
        c.drawPath(BEAM, FILL);
    }

    /**
     * Draws the camera picture dimmed if there is one (the stage is drawn separately).
     * @param c canvas
     * @param camera camera picture or null
     */
    static void camera(Canvas c, Bitmap camera) {
        if (camera != null) Stage.drawCamera(c, camera, 0.55f);
    }

    /**
     * Draws the camera picture in a small box with a neon frame, mirrored like a mirror.
     * @param c canvas
     * @param camera camera picture (nothing is drawn if null)
     * @param x left
     * @param y top
     * @param w width
     * @param h height
     * @param colour frame colour
     */
    static void cameraBox(Canvas c, Bitmap camera, float x, float y, float w, float h, int colour) {
        if (camera == null) return;
        R.set(x, y, x + w, y + h);
        c.save();
        c.clipRect(R);
        c.scale(-1f, 1f, x + w / 2f, y);
        c.drawBitmap(camera, null, R, SPRITE);
        c.restore();
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setColor((colour & 0x00FFFFFF) | (70 << 24));
        LINE.setStrokeWidth(16f);
        c.drawRoundRect(R, 18f, 18f, LINE);
        LINE.setColor(colour);
        LINE.setStrokeWidth(5f);
        c.drawRoundRect(R, 18f, 18f, LINE);
    }
}
