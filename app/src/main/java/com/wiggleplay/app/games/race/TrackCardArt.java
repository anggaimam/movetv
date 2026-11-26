package com.wiggleplay.app.games.race;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import com.wiggleplay.app.ui.Theme;

/**
 * Paints one track card of the track screen (T-32 Part A): a 400 x 600 rounded card with a picture of the track's world on top (sky,
 * far and mid backdrop silhouettes taken from the theme's own rings, a ground band and a few flat props), the track's minimap in the
 * middle with a start arrow showing the direction, and the name and the best stars on a paper area at the bottom. It draws with the
 * Android canvas only (no GL), so the screenshot rig shows it. Called only when a card is baked into the sprite cache, so it may allocate.
 */
public final class TrackCardArt {
    /** Card size, and the heights of the picture, the minimap and (the rest) the name area. */
    public static final int W = 400, H = 600, PIC_H = 220, MAP_H = 220;
    private static final int PAPER = 0xFFF3EBDD, INK = 0xFF2F3A45, GREY = 0xFFD9CDB8, GREY_LINE = 0xFFBFB29A;
    private static final int[] LEVEL_COLOURS = {Theme.MUSTARD, Theme.ORANGE, Theme.PINK};
    private static final int MAP_POINTS = 96;
    private static final float GROUND_TOP = 170f;

    private TrackCardArt() { }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /**
     * Paints a card.
     * @param c canvas on a 400 x 600 bitmap
     * @param def the track
     * @param reversed true when the card shows the other way round (the start arrow and the minimap follow it)
     * @param best best stars on Easy, Medium and Hard in that direction, 0 = never played (grey dots)
     */
    public static void paint(Canvas c, TrackDef def, boolean reversed, int[] best) {
        com.wiggleplay.app.games.race.Theme theme = Themes.byId(def.themeId);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF card = new RectF(3f, 3f, W - 3f, H - 3f);
        Path clip = new Path();
        clip.addRoundRect(card, 28f, 28f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        paintPicture(c, theme, p);
        paintMap(c, def, theme, reversed, p);
        // the name area
        p.setStyle(Paint.Style.FILL);
        p.setColor(PAPER);
        c.drawRect(0f, PIC_H + MAP_H, W, H, p);
        String[] lines = splitName(def.name);
        for (int i = 0; i < lines.length; i++) {
            Theme.drawCentered(c, lines[i], W / 2f, PIC_H + MAP_H + 48f + i * 52f, 52f, INK);
        }
        paintStars(c, best, p);
        c.restore();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setColor(INK);
        c.drawRoundRect(card, 28f, 28f, p);
    }

    private static String[] splitName(String name) {
        int i = name.indexOf(' ');
        return i < 0 ? new String[] {name} : new String[] {name.substring(0, i), name.substring(i + 1)};
    }

    // ---- the picture of the world ----

    private static void paintPicture(Canvas c, com.wiggleplay.app.games.race.Theme theme, Paint p) {
        int top = opaque(theme.look.zenith != 0 ? theme.look.zenith : theme.sky), bottom = opaque(theme.horizon);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0f, 0f, 0f, GROUND_TOP, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, W, GROUND_TOP + 4f, p);
        p.setShader(null);
        // stars of a STARS ring (space)
        for (com.wiggleplay.app.games.race.Theme.Ring r : theme.rings) {
            if (r.profile != com.wiggleplay.app.games.race.Theme.STARS) continue;
            p.setColor(0xFFFFFFFF);
            java.util.Random rnd = new java.util.Random(r.seed);
            for (int i = 0; i < 28; i++) c.drawCircle(rnd.nextFloat() * W, rnd.nextFloat() * (GROUND_TOP - 20f), 1.5f + rnd.nextFloat() * 1.5f, p);
        }
        // the far ring (columns 0 to 31) and the mid ring (columns 10 to 41), the two largest band rings
        com.wiggleplay.app.games.race.Theme.Ring far = null, mid = null;
        for (com.wiggleplay.app.games.race.Theme.Ring r : theme.rings) {
            if (r.profile == com.wiggleplay.app.games.race.Theme.STARS) continue;
            if (far == null || r.radius > far.radius) {
                mid = far;
                far = r;
            } else if (mid == null || r.radius > mid.radius) {
                mid = r;
            }
        }
        float scale = 1f;
        if (far != null) {
            float m = 1f;
            for (com.wiggleplay.app.games.race.Theme.Ring r : new com.wiggleplay.app.games.race.Theme.Ring[] {far, mid}) {
                if (r != null) m = Math.max(m, r.maxH);
            }
            scale = 105f / m;
        }
        if (far != null) silhouette(c, far, 0, 32, scale, p);
        if (mid != null) silhouette(c, mid, 10, 32, scale, p);
        // the ground band, and the props on it
        com.wiggleplay.app.games.race.Terrain.Profile t = theme.look.terrain;
        p.setColor(opaque(t != null ? t.groundLight : theme.ground));
        c.drawRect(0f, GROUND_TOP, W, PIC_H, p);
        props(c, theme.id, p);
    }

    /** One backdrop ring as a filled silhouette: {@code count} columns from {@code first}, left and right top heights per column. */
    private static void silhouette(Canvas c, com.wiggleplay.app.games.race.Theme.Ring r, int first, int count, float scale, Paint p) {
        Backdrop.Profile pr = Backdrop.profile(r);
        int n = pr.left.length;
        Path path = new Path();
        path.moveTo(0f, GROUND_TOP + 2f);
        float dx = (float) W / count;
        for (int k = 0; k < count; k++) {
            int col = (first + k) % n;
            path.lineTo(k * dx, GROUND_TOP - Math.max(0f, pr.left[col]) * scale);
            path.lineTo((k + 1) * dx, GROUND_TOP - Math.max(0f, pr.right[col]) * scale);
        }
        path.lineTo(W, GROUND_TOP + 2f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(opaque(r.color));
        c.drawPath(path, p);
    }

    private static void props(Canvas c, String id, Paint p) {
        p.setStyle(Paint.Style.FILL);
        float g = GROUND_TOP + 14f;
        switch (id) {
            case "desert": {
                p.setColor(0xFF4E8B5A);
                float[][] cacti = {{110f, 74f}, {290f, 56f}};
                for (float[] k : cacti) {
                    float x = k[0], h = k[1];
                    c.drawRoundRect(new RectF(x - 7f, g - h, x + 7f, g), 7f, 7f, p);
                    c.drawRoundRect(new RectF(x - 25f, g - h * 0.62f, x - 15f, g - h * 0.3f), 5f, 5f, p);
                    c.drawRect(x - 25f, g - h * 0.34f, x - 4f, g - h * 0.26f, p);
                    c.drawRoundRect(new RectF(x + 15f, g - h * 0.78f, x + 25f, g - h * 0.42f), 5f, 5f, p);
                    c.drawRect(x + 4f, g - h * 0.46f, x + 25f, g - h * 0.38f, p);
                }
                break;
            }
            case "city": {
                int[] cols = {0xFF6B7A8F, 0xFF8794A8, 0xFF5C6B80};
                float[][] b = {{60f, 60f, 70f}, {170f, 90f, 90f}, {270f, 50f, 66f}};   // left, height, width
                for (int i = 0; i < 3; i++) {
                    p.setColor(cols[i]);
                    c.drawRect(b[i][0], g - b[i][1], b[i][0] + b[i][2], g, p);
                    p.setColor(0xFFF2C572);
                    for (float y = g - b[i][1] + 10f; y < g - 8f; y += 16f) c.drawRect(b[i][0] + 8f, y, b[i][0] + b[i][2] - 8f, y + 6f, p);
                }
                break;
            }
            case "rockies": {
                p.setColor(0xFF6CC3E6);
                c.drawOval(new RectF(210f, GROUND_TOP + 6f, 380f, PIC_H - 4f), p);
                p.setColor(0xFF2F6B4F);
                float[] xs = {40f, 80f, 120f, 160f};
                for (float x : xs) {
                    for (int t = 0; t < 3; t++) {
                        float w = 30f - t * 7f, y = g - 12f - t * 20f;
                        Path tri = new Path();
                        tri.moveTo(x, y - 26f);
                        tri.lineTo(x - w / 2f, y);
                        tri.lineTo(x + w / 2f, y);
                        tri.close();
                        c.drawPath(tri, p);
                    }
                    c.drawRect(x - 2f, g - 12f, x + 2f, g, p);
                }
                break;
            }
            default: {   // space: a ringed planet and a few stars
                p.setColor(0xFFC9A0DC);
                c.drawCircle(290f, 96f, 36f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(7f);
                p.setColor(0xFFF2E6C8);
                c.drawOval(new RectF(238f, 80f, 342f, 112f), p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                float[][] stars = {{60f, 40f}, {120f, 110f}, {180f, 30f}, {350f, 150f}};
                for (float[] s : stars) c.drawCircle(s[0], s[1], 3f, p);
                break;
            }
        }
    }

    // ---- the minimap ----

    private static void paintMap(Canvas c, TrackDef def, com.wiggleplay.app.games.race.Theme theme, boolean reversed, Paint p) {
        float top = PIC_H;
        com.wiggleplay.app.games.race.Terrain.Profile t = theme.look.terrain;
        p.setStyle(Paint.Style.FILL);
        p.setColor(opaque(t != null ? t.groundDark : theme.ground));
        c.drawRect(0f, top, W, top + MAP_H, p);
        TrackSpline spline = TrackSpline.build(def, reversed, RaceLevel.MEDIUM, 0);
        float[] pts = new float[MAP_POINTS * 2];
        spline.minimap(MAP_POINTS, pts);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < MAP_POINTS; i++) {
            minX = Math.min(minX, pts[i * 2]);
            maxX = Math.max(maxX, pts[i * 2]);
            minZ = Math.min(minZ, pts[i * 2 + 1]);
            maxZ = Math.max(maxZ, pts[i * 2 + 1]);
        }
        float margin = 24f, road = 9f;   // world road width in the same units as the points (about 9 m with the kerbs)
        float sc = Math.min((W - 2f * margin - 18f) / (maxX - minX), (MAP_H - 2f * margin - 18f) / (maxZ - minZ));
        float ox = (W - (maxX - minX) * sc) / 2f - minX * sc, oz = top + (MAP_H - (maxZ - minZ) * sc) / 2f - minZ * sc;
        // lakes (Rocky Lakes) under the road
        Lakes lakes = Lakes.of(def);
        float[] q = new float[2];
        p.setColor(0xFF6CC3E6);
        for (int i = 0; i < lakes.count(); i++) {
            Path lp = new Path();
            for (int k = 0; k < 20; k++) {
                lakes.point(i, (float) (2.0 * Math.PI * k / 20), 1f, q);
                float x = ox + q[0] * sc, y = oz + q[1] * sc;
                if (k == 0) lp.moveTo(x, y); else lp.lineTo(x, y);
            }
            lp.close();
            c.drawPath(lp, p);
        }
        Path line = new Path();
        for (int i = 0; i < MAP_POINTS; i++) {
            float x = ox + pts[i * 2] * sc, y = oz + pts[i * 2 + 1] * sc;
            if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
        }
        line.close();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(26f);
        p.setColor(opaque(theme.road.kerbA));
        c.drawPath(line, p);
        p.setStrokeWidth(18f);
        p.setColor(opaque(theme.road.road));
        c.drawPath(line, p);
        // the start: a small chequer across the road, and an arrow in the direction of the lap
        float sx = ox + pts[0] * sc, sy = oz + pts[1] * sc;
        float dx = pts[6] - pts[0], dz = pts[7] - pts[1];
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        dx /= len;
        dz /= len;
        float nx = -dz, ny = dx;
        p.setStyle(Paint.Style.FILL);
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 2; b++) {
                p.setColor(((a + b) & 1) == 0 ? 0xFFFFFFFF : INK);
                float cx = sx + nx * (a - 1.5f) * 5f + dx * (b - 0.5f) * 5f, cy = sy + ny * (a - 1.5f) * 5f + dz * (b - 0.5f) * 5f;
                c.drawRect(cx - 2.5f, cy - 2.5f, cx + 2.5f, cy + 2.5f, p);
            }
        }
        float ax = sx + dx * 38f, ay = sy + dz * 38f;
        Path arrow = new Path();
        arrow.moveTo(ax + dx * 16f, ay + dz * 16f);
        arrow.lineTo(ax - dx * 8f + nx * 13f, ay - dz * 8f + ny * 13f);
        arrow.lineTo(ax - dx * 8f - nx * 13f, ay - dz * 8f - ny * 13f);
        arrow.close();
        p.setColor(INK);
        c.drawPath(arrow, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(PAPER);
        c.drawPath(arrow, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ---- best stars ----

    private static void paintStars(Canvas c, int[] best, Paint p) {
        float cy = H - 30f;
        for (int g = 0; g < 3; g++) {
            float cx = 70f + g * 130f;
            int n = best != null && g < best.length ? Math.max(0, Math.min(3, best[g])) : 0;
            if (n == 0) {   // never played: three grey dots
                p.setStyle(Paint.Style.FILL);
                p.setColor(GREY);
                for (int i = 0; i < 3; i++) c.drawCircle(cx + (i - 1) * 28f, cy, 6f, p);
                continue;
            }
            for (int i = 0; i < 3; i++) {
                Path s = new Path();
                star(s, cx + (i - 1) * 28f, cy, 13f);
                boolean on = i < n;
                p.setStyle(Paint.Style.FILL);
                p.setColor(on ? LEVEL_COLOURS[g] : GREY);
                c.drawPath(s, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f);
                p.setColor(on ? INK : GREY_LINE);
                c.drawPath(s, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void star(Path p, float cx, float cy, float r) {
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? r : r * 0.45f;
            float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr;
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.close();
    }

    /**
     * Paints a direction tile of the track screen: a paper-coloured pill (sage when it is the ticked direction) with a round arrow
     * and the words "This way" or "Other way".
     * @param c canvas on a 300 x 110 bitmap
     * @param reversed false for "This way", true for "Other way"
     * @param ticked true for the chosen direction
     */
    public static void paintDirectionToggle(Canvas c, int w, int h, boolean reversed) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF r = new RectF(6f, 6f, w - 6f, h - 6f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(PAPER);
        c.drawRoundRect(r, 44f, 44f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setColor(INK);
        c.drawRoundRect(r, 44f, 44f, p);
        Theme.drawCentered(c, "Other way", w / 2f, 84f, 44f, INK);
        // the on/off switch: green with the knob on the right when the track runs the other way
        float sw = 150f, sh = 76f, sx = (w - sw) / 2f, sy = 120f;
        RectF track = new RectF(sx, sy, sx + sw, sy + sh);
        p.setStyle(Paint.Style.FILL);
        p.setColor(reversed ? Theme.SAGE : 0xFFBFB6A6);
        c.drawRoundRect(track, sh / 2f, sh / 2f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f);
        p.setColor(INK);
        c.drawRoundRect(track, sh / 2f, sh / 2f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(reversed ? sx + sw - sh / 2f : sx + sh / 2f, sy + sh / 2f, sh / 2f - 8f, p);
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(reversed ? sx + sw - sh / 2f : sx + sh / 2f, sy + sh / 2f, sh / 2f - 8f, p);
        Theme.drawCentered(c, reversed ? "On" : "Off", w / 2f, 250f, 40f, INK);
    }

    /**
     * Older two-tile direction picture (kept for the screenshot rig).
     * @param c canvas
     * @param reversed true for the "Other way" tile
     * @param ticked true for the chosen direction
     */
    public static void paintDirection(Canvas c, boolean reversed, boolean ticked) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF r = new RectF(3f, 3f, 297f, 107f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ticked ? Theme.SAGE : PAPER);
        c.drawRoundRect(r, 36f, 36f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(ticked ? 7f : 5f);
        p.setColor(INK);
        c.drawRoundRect(r, 36f, 36f, p);
        // the round arrow: counter-clockwise for the track as designed, clockwise for the other way
        float ax = 62f, ay = 55f, rad = 21f;
        p.setStrokeWidth(7f);
        p.setStrokeCap(Paint.Cap.ROUND);
        RectF arc = new RectF(ax - rad, ay - rad, ax + rad, ay + rad);
        c.drawArc(arc, reversed ? -60f : 240f, reversed ? 270f : -270f, false, p);
        double a = Math.toRadians(reversed ? 210f : -30f);
        float ex = ax + rad * (float) Math.cos(a), ey = ay + rad * (float) Math.sin(a);
        double t = a + (reversed ? 1f : -1f) * Math.PI / 2.0;
        float tx = (float) Math.cos(t), ty = (float) Math.sin(t), nx = -ty, ny = tx;
        Path head = new Path();
        head.moveTo(ex + tx * 14f, ey + ty * 14f);
        head.lineTo(ex - tx * 5f + nx * 12f, ey - ty * 5f + ny * 12f);
        head.lineTo(ex - tx * 5f - nx * 12f, ey - ty * 5f - ny * 12f);
        head.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(head, p);
        Theme.drawCentered(c, reversed ? "Other way" : "This way", 190f, 68f, 40f, INK);
    }
}
