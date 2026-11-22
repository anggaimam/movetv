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
 * Neon arcade art for Animal Dance Party: a dark stage with a glowing floor, four animal hosts (penguin, giraffe,
 * monkey, flamingo) drawn as glowing outlines, sweeping stage lights, pose cards with a stick figure, and the glowing
 * outline of the players' own bodies. Glow is a wide low-opacity line under a thin bright one: no blur, no gradients.
 * Everything big is baked once into {@link SpriteCache}; per frame only a few lines and one bitmap per host are drawn.
 */
final class DanceArt {

    static final int BG = 0xFF140B2E, PANEL = 0xFF1E1245, PANEL_LIGHT = 0xFF2A1A5E;
    static final int CYAN = 0xFF7FF4FF, PINK = 0xFFFF4FD8, ORANGE = 0xFFFFC27A, GREEN = 0xFF6BFF8E, GOLD = 0xFFFFD84A,
            WHITE = 0xFFFFFFFF;

    static final int HOSTS = 4;
    static final String[] HOST_NAMES = {"Penguin", "Giraffe", "Monkey", "Flamingo"};
    private static final String[] HOST_KEYS = {"dance_host_0", "dance_host_1", "dance_host_2", "dance_host_3"};
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

    // where each host's arms start and what they reach, in the host picture's own pixels (440 x 620)
    // {left shoulder x, right shoulder x, shoulder y, nose x, nose y, left hip x, right hip x, hip y, arm length, arm width}
    private static final float[][] ANCHOR = {
            {118f, 322f, 335f, 220f, 270f, 170f, 270f, 520f, 0.5f, 34f},
            {150f, 310f, 405f, 385f, 114f, 150f, 310f, 480f, 0.7f, 22f},
            {135f, 305f, 385f, 220f, 262f, 175f, 265f, 530f, 0.65f, 28f},
            {140f, 310f, 345f, 360f, 105f, 190f, 260f, 430f, 0.55f, 20f}};
    private static final int[] HOST_COLOUR = {CYAN, ORANGE, ORANGE, PINK};
    private static final Path ARM = new Path();

    /**
     * Where a host's elbows and wrists are when it does a pose.
     * @param which host 0..3
     * @param pose pose number
     * @param o receives left elbow x, y, left wrist x, y, right elbow x, y, right wrist x, y (host picture pixels)
     */
    static void hostArmsPose(int which, int pose, float[] o) {
        float[] a = ANCHOR[which];
        Poses.arms(pose, a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], o);
    }

    /**
     * Where a host's elbows and wrists are for four arm directions.
     * @param which host 0..3
     * @param ang four angles LU, LF, RU, RF (0 = down, 90 = out, 180 = up)
     * @param o receives the eight coordinates (see {@link #hostArmsPose})
     */
    static void hostArmsAngles(int which, float[] ang, float[] o) {
        float[] a = ANCHOR[which];
        Poses.armsFromAngles(a[0], a[1], a[2], a[8], ang, o);
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
     * @param which host 0..3
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

    /** The body picture and the two arms, drawn in the host picture's pixels. */
    private static void hostBody(Canvas c, int which, float[] o) {
        Bitmap b = SpriteCache.shared().get(HOST_KEYS[which], HOST_W, HOST_H, PAINTERS[which]);
        c.drawBitmap(b, 0f, 0f, SPRITE);
        float[] a = ANCHOR[which];
        limb(c, a[0], a[2], o[0], o[1], o[2], o[3], HOST_COLOUR[which], a[9]);
        limb(c, a[1], a[2], o[4], o[5], o[6], o[7], HOST_COLOUR[which], a[9]);
    }

    private static void limb(Canvas c, float sx, float sy, float ex, float ey, float wx, float wy, int colour, float w) {
        ARM.reset();
        ARM.moveTo(sx, sy);
        ARM.lineTo(ex, ey);
        ARM.lineTo(wx, wy);
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setColor((colour & 0x00FFFFFF) | (70 << 24));
        LINE.setStrokeWidth(w * 1.9f);
        c.drawPath(ARM, LINE);
        LINE.setColor(colour);
        LINE.setStrokeWidth(w);
        c.drawPath(ARM, LINE);
        LINE.setColor(PANEL);
        LINE.setStrokeWidth(Math.max(4f, w - 10f));
        c.drawPath(ARM, LINE);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(PANEL);
        c.drawCircle(wx, wy, w * 0.62f, FILL);
        LINE.setColor(colour);
        LINE.setStrokeWidth(Math.max(4f, w * 0.22f));
        c.drawCircle(wx, wy, w * 0.62f, LINE);
    }

    /**
     * Draws a dancing host: it hops on every beat, squashes a little and sways.
     * @param c canvas
     * @param which 0..3 penguin, giraffe, monkey, flamingo
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

    private static final SpriteCache.Painter[] PAINTERS = {
            DanceArt::paintPenguin, DanceArt::paintGiraffe, DanceArt::paintMonkey, DanceArt::paintFlamingo};

    private static Path oval(float l, float t, float r, float b) {
        Path p = new Path();
        R.set(l, t, r, b);
        p.addOval(R, Path.Direction.CW);
        return p;
    }

    private static Path rotOval(float cx, float cy, float rx, float ry, float deg) {
        Path p = oval(cx - rx, cy - ry, cx + rx, cy + ry);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setRotate(deg, cx, cy);
        p.transform(m);
        return p;
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

    private static void shape(Canvas c, Path p, int fill, int colour, float w) {
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(fill);
        c.drawPath(p, f);
        Brushes.neonStroke(c, p, colour, w);
    }

    /** A glowing tube along a path: a wide coloured stroke with a dark one on top. */
    private static void tube(Canvas c, Path p, int colour, float width) {
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeCap(Paint.Cap.ROUND);
        s.setStrokeJoin(Paint.Join.ROUND);
        s.setColor((colour & 0x00FFFFFF) | (70 << 24));
        s.setStrokeWidth(width * 1.9f);
        c.drawPath(p, s);
        s.setColor(colour | 0xFF000000);
        s.setStrokeWidth(width);
        c.drawPath(p, s);
        s.setColor(PANEL);
        s.setStrokeWidth(width - 10f);
        c.drawPath(p, s);
    }

    private static void eye(Canvas c, float x, float y, float r) {
        shape(c, circle(x, y, r), PANEL, WHITE, 5f);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(WHITE);
        c.drawCircle(x + r * 0.15f, y + r * 0.1f, r * 0.42f, f);
    }

    private static void paintPenguin(Canvas c, int w, int h) {
        shape(c, oval(150f, 548f, 218f, 594f), PANEL, ORANGE, 6f);
        shape(c, oval(222f, 548f, 290f, 594f), PANEL, ORANGE, 6f);
        shape(c, oval(100f, 150f, 340f, 570f), PANEL, CYAN, 8f);
        shape(c, oval(150f, 285f, 290f, 545f), PANEL_LIGHT, WHITE, 5f);
        Path tuft = new Path();
        tuft.moveTo(200f, 152f);
        tuft.lineTo(190f, 110f);
        tuft.moveTo(220f, 150f);
        tuft.lineTo(222f, 96f);
        tuft.moveTo(240f, 152f);
        tuft.lineTo(256f, 112f);
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeCap(Paint.Cap.ROUND);
        Brushes.neonStroke(c, tuft, PINK, 7f);
        eye(c, 182f, 228f, 24f);
        eye(c, 258f, 228f, 24f);
        shape(c, poly(196f, 266f, 244f, 266f, 220f, 308f), PANEL, ORANGE, 6f);
        shape(c, circle(150f, 272f, 14f), PANEL, PINK, 4f);
        shape(c, circle(290f, 272f, 14f), PANEL, PINK, 4f);
    }

    private static void paintGiraffe(Canvas c, int w, int h) {
        int body = ORANGE;
        Path tail = new Path();
        tail.moveTo(112f, 410f);
        tail.cubicTo(70f, 430f, 70f, 480f, 90f, 520f);
        Brushes.neonStroke(c, tail, body, 7f);
        shape(c, rotOval(90f, 530f, 12f, 18f, 0f), PANEL, PINK, 5f);
        float[] legX = {140f, 188f, 262f, 310f};
        for (float lx : legX) shape(c, poly(lx, 470f, lx + 30f, 470f, lx + 30f, 592f, lx, 592f), PANEL, body, 7f);
        shape(c, poly(235f, 395f, 290f, 120f, 336f, 132f, 302f, 400f), PANEL, body, 8f);
        shape(c, oval(105f, 350f, 355f, 500f), PANEL, body, 8f);
        shape(c, rotOval(338f, 108f, 64f, 38f, -18f), PANEL, body, 8f);
        Path horns = new Path();
        horns.moveTo(312f, 76f);
        horns.lineTo(306f, 44f);
        horns.moveTo(342f, 78f);
        horns.lineTo(352f, 48f);
        Brushes.neonStroke(c, horns, PINK, 6f);
        shape(c, circle(306f, 38f, 9f), PANEL, PINK, 4f);
        shape(c, circle(353f, 42f, 9f), PANEL, PINK, 4f);
        eye(c, 336f, 98f, 11f);
        shape(c, circle(388f, 114f, 5f), PANEL, PINK, 3f);
        float[][] spots = {{165f, 410f, 20f}, {228f, 445f, 17f}, {290f, 410f, 19f}, {272f, 250f, 15f}, {288f, 320f, 13f},
                {196f, 380f, 11f}, {320f, 450f, 12f}};
        for (float[] s : spots) shape(c, circle(s[0], s[1], s[2]), PANEL_LIGHT, PINK, 4f);
        Path mane = new Path();
        mane.moveTo(236f, 380f);
        mane.lineTo(290f, 130f);
        Brushes.neonStroke(c, mane, GREEN, 4f);
    }

    private static void paintMonkey(Canvas c, int w, int h) {
        Path tail = new Path();
        tail.moveTo(312f, 505f);
        tail.cubicTo(420f, 500f, 430f, 400f, 380f, 330f);
        tube(c, tail, ORANGE, 24f);
        shape(c, oval(130f, 548f, 208f, 596f), PANEL, ORANGE, 6f);
        shape(c, oval(232f, 548f, 310f, 596f), PANEL, ORANGE, 6f);
        shape(c, oval(125f, 340f, 315f, 565f), PANEL, ORANGE, 8f);
        shape(c, oval(160f, 400f, 280f, 540f), PANEL_LIGHT, PINK, 5f);
        shape(c, circle(98f, 215f, 46f), PANEL, ORANGE, 7f);
        shape(c, circle(342f, 215f, 46f), PANEL, ORANGE, 7f);
        shape(c, circle(98f, 215f, 24f), PANEL_LIGHT, PINK, 4f);
        shape(c, circle(342f, 215f, 24f), PANEL_LIGHT, PINK, 4f);
        shape(c, circle(220f, 210f, 122f), PANEL, ORANGE, 8f);
        shape(c, oval(140f, 195f, 300f, 330f), PANEL_LIGHT, PINK, 5f);
        eye(c, 185f, 232f, 17f);
        eye(c, 255f, 232f, 17f);
        Path mouth = new Path();
        R.set(180f, 262f, 260f, 312f);
        mouth.addArc(R, 10f, 160f);
        Brushes.neonStroke(c, mouth, WHITE, 6f);
        Path nose = new Path();
        nose.addCircle(210f, 262f, 4f, Path.Direction.CW);
        nose.addCircle(230f, 262f, 4f, Path.Direction.CW);
        Brushes.neonStroke(c, nose, PINK, 4f);
        Path hair = new Path();
        hair.moveTo(190f, 100f);
        hair.quadTo(220f, 60f, 250f, 100f);
        Brushes.neonStroke(c, hair, GREEN, 6f);
    }

    private static void paintFlamingo(Canvas c, int w, int h) {
        Path leg1 = new Path();
        leg1.moveTo(200f, 430f);
        leg1.lineTo(200f, 590f);
        Path foot1 = new Path();
        foot1.moveTo(160f, 592f);
        foot1.lineTo(240f, 592f);
        Path leg2 = new Path();
        leg2.moveTo(250f, 430f);
        leg2.lineTo(292f, 505f);
        leg2.lineTo(215f, 520f);
        tube(c, leg2, PINK, 18f);
        tube(c, leg1, PINK, 18f);
        Brushes.neonStroke(c, foot1, ORANGE, 8f);
        shape(c, rotOval(110f, 345f, 70f, 28f, -30f), PANEL, PINK, 6f);
        Path neck = new Path();
        neck.moveTo(305f, 340f);
        neck.cubicTo(400f, 290f, 230f, 190f, 330f, 112f);
        tube(c, neck, PINK, 34f);
        shape(c, oval(115f, 295f, 335f, 440f), PANEL, PINK, 8f);
        shape(c, rotOval(205f, 355f, 62f, 38f, -12f), PANEL_LIGHT, WHITE, 5f);
        shape(c, circle(335f, 100f, 36f), PANEL, PINK, 7f);
        shape(c, poly(358f, 88f, 420f, 108f, 372f, 128f), PANEL, ORANGE, 6f);
        eye(c, 340f, 90f, 11f);
        Path crest = new Path();
        crest.moveTo(318f, 70f);
        crest.lineTo(306f, 44f);
        crest.moveTo(334f, 66f);
        crest.lineTo(336f, 36f);
        Brushes.neonStroke(c, crest, CYAN, 5f);
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
        float sc = Math.min(w / (HOST_W * 1.15f), h * 0.9f / HOST_H);
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
