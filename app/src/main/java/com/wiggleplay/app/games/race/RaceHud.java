package com.wiggleplay.app.games.race;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import com.wiggleplay.app.core.Pool;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.ui.Theme;

/**
 * The race-only parts of the screen, drawn over the 3D world on the Canvas: the lap pill, the steering icon (plane,
 * leaning kid or wheel, T-23) that tilts with the gesture, the boost rocket gauge, the brake icon, the minimap (the track outline is baked once), the
 * "Final lap!" banner, the camera inset, speed lines while boosting and small "+1" popups. The scene fills in the
 * public fields each frame and calls {@link #draw}. Nothing is allocated while playing (pools, preset paints and
 * text tables).
 */
final class RaceHud {

    private static final int INK = RaceArt.INK, PAPER = RaceArt.PAPER;
    private static final String[] LAP = {"Lap 1/3", "Lap 2/3", "Lap 3/3"};
    private static final String[] LAP_OF_2 = {"Lap 1/2", "Lap 2/2"};
    private static final String[] PLUS = {"+0", "+1", "+2", "+3", "+4", "+5"};
    private static final float MAP_X = 1560f, MAP_Y = 24f, MAP_W = 330f, MAP_H = 230f, PAD = 18f;
    private static final float CAM_X = 40f, CAM_Y = 1080f - 40f - 180f, CAM_W = 320f, CAM_H = 180f;
    private static final float SPEED_LIFE = 0.35f;
    private static final int MAP_POINTS = 96;

    private static final class SpeedLine {
        float ang, life;
    }

    private static final class Popup {
        float life;
        int points;
    }

    // set by the scene each frame
    int lap = 1;
    /** Laps in this race (2 or 3). */
    int lapTotal = 3;
    float meter, boostLeft;
    /** Magnet time left, 0..1 of {@link RaceLogic#MAGNET_SECONDS}. */
    float magnetK;
    boolean boosting, braking;
    /** Steering icon tilt in degrees (positive = the icon's left side lower). */
    float iconDeg;
    /** True while the steering gesture is made (the icon is in the player colour, otherwise grey). */
    boolean steerActive;
    /** RaceArt.MODE_PLANE, MODE_LEAN or MODE_WHEEL. */
    int mode = RaceArt.MODE_PLANE;
    float noneSeconds;
    float finalLapSeconds = 99f;
    float playerS;
    /** Second player's distance on the minimap (two players), or -1 for none. */
    float player2S = -1f;
    /** Race position badge in two players: 1 or 2, or 0 for none. */
    int place;
    final float[] sceneryS = new float[3];
    int sceneryCount;
    Bitmap camera;
    int playerColour = 0xFF29B6F6;

    private Bitmap iconOn, iconOff;
    private TrackSpline layout;
    private float mapScale, mapOx, mapOz;
    private Bitmap mapBmp;
    private float time;
    private final float[] tmp = new float[3];
    private final Pool<SpeedLine> lines = new Pool<>(40, SpeedLine::new);
    private final Pool<Popup> popups = new Pool<>(6, Popup::new);
    private float spawnT;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final Path arrow = new Path();

    RaceHud() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    /**
     * Gives the HUD the track for its minimap (the outline is baked once into the sprite cache from 96 points of the spline).
     * @param l the track
     * @param key the track's cache key (for example {@code city-r})
     */
    void setTrack(TrackSpline l, String key) {
        layout = l;
        final float[] pts = new float[MAP_POINTS * 2];
        l.minimap(MAP_POINTS, pts);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < MAP_POINTS; i++) {
            minX = Math.min(minX, pts[i * 2]);
            maxX = Math.max(maxX, pts[i * 2]);
            minZ = Math.min(minZ, pts[i * 2 + 1]);
            maxZ = Math.max(maxZ, pts[i * 2 + 1]);
        }
        float worldW = maxX - minX + 12f, worldH = maxZ - minZ + 12f;   // the road is drawn about 12 m wide
        mapScale = Math.min((MAP_W - 2f * PAD) / worldW, (MAP_H - 2f * PAD) / worldH);
        // centre the outline in the panel
        mapOx = (MAP_W - (maxX - minX) * mapScale) / 2f - minX * mapScale;
        mapOz = (MAP_H - (maxZ - minZ) * mapScale) / 2f - minZ * mapScale;
        final float sc = mapScale, ox = mapOx, oz = mapOz;
        mapBmp = SpriteCache.shared().get("race_map_" + key, (int) MAP_W, (int) MAP_H, (c, w, h) -> {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            RectF r = new RectF(0f, 0f, w, h);
            p.setColor(0xCCF3EBDD);
            c.drawRoundRect(r, 28f, 28f, p);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            Path path = new Path();
            for (int i = 0; i < MAP_POINTS; i++) {
                float x = ox + pts[i * 2] * sc, y = oz + pts[i * 2 + 1] * sc;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            line.setColor(0xFF5B6B78);
            line.setStrokeWidth(Math.max(9f, 8f * sc * 2f));
            c.drawPath(path, line);
            line.setColor(0xFFC9D2D9);
            line.setStrokeWidth(Math.max(5f, 8f * sc * 2f - 5f));
            c.drawPath(path, line);
            float fx = ox + pts[0] * sc, fy = oz + pts[1] * sc;
            p.setColor(0xFF2F3A45);
            c.drawRect(fx - 3f, fy - 10f, fx + 3f, fy + 10f, p);
        });
    }

    /**
     * Picks the steering mode and bakes its two icons (once; nothing is looked up per frame).
     * @param m RaceArt.MODE_PLANE, MODE_LEAN or MODE_WHEEL
     */
    void setMode(int m) {
        mode = Math.max(0, Math.min(2, m));
        iconOn = RaceArt.steerIcon(mode, true, playerColour);
        iconOff = RaceArt.steerIcon(mode, false, playerColour);
    }

    /** @return the baked icon of the current mode (for the intro) */
    Bitmap icon(boolean active) {
        return active ? iconOn : iconOff;
    }

    /** Clears popups, speed lines and timers for a new round. */
    void reset() {
        for (int i = lines.size() - 1; i >= 0; i--) lines.freeAt(i);
        for (int i = popups.size() - 1; i >= 0; i--) popups.freeAt(i);
        finalLapSeconds = 99f;
        noneSeconds = 0f;
        boosting = false;
        braking = false;
        meter = 1f;
    }

    /**
     * A small "+n" rises from the score bubble.
     * @param points 1..5
     */
    void popup(int points) {
        Popup p = popups.obtain();
        if (p == null) return;
        p.life = 0.9f;
        p.points = Math.max(0, Math.min(PLUS.length - 1, points));
    }

    /**
     * Advances the little animations.
     * @param dt seconds
     * @param running true while the race runs (speed lines only then)
     */
    void update(float dt, boolean running) {
        time += dt;
        for (int i = lines.size() - 1; i >= 0; i--) {
            SpeedLine s = lines.at(i);
            s.life -= dt;
            if (s.life <= 0f) lines.freeAt(i);
        }
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.at(i);
            p.life -= dt;
            if (p.life <= 0f) popups.freeAt(i);
        }
        if (running && boosting) {
            spawnT -= dt;
            while (spawnT <= 0f) {
                spawnT += 0.022f;
                SpeedLine s = lines.obtain();
                if (s == null) break;
                s.ang = (float) (Math.random() * Math.PI * 2.0);
                s.life = SPEED_LIFE;
            }
        }
    }

    /**
     * Draws everything over the 3D world.
     * @param c canvas (stage coordinates)
     */
    void draw(Canvas c) {
        drawSpeedLines(c);
        drawMinimap(c);
        drawLap(c);
        drawPopups(c);
        drawSteerIcon(c);
        drawRocket(c);
        drawMagnet(c);
        if (braking) drawBrake(c);
        if (lap >= lapTotal && finalLapSeconds < 2.6f) {
            Theme.drawBanner(c, "Final lap!", Stage.W / 2f, 260f, 84f, 0xE6F3EBDD, RaceArt.ORANGE);
        }
        drawCamera(c);
    }


    private static final String[] PLACE = {"", "1st", "2nd"};

    /**
     * T-24: this player's half of the two-player screen: lap pill, steering icon, boost rocket, brake, magnet, popups, the
     * position badge and the final-lap banner, laid out in a 960 x 1080 half (the shared minimap and divider are drawn by
     * the scene).
     * @param c canvas (stage coordinates)
     * @param side 0 for the left half, 1 for the right half
     */
    void drawHalf(Canvas c, int side) {
        float ox = side * Stage.W / 2f;
        c.save();
        c.clipRect(ox, 0f, ox + Stage.W / 2f, Stage.H);
        c.translate(ox, 0f);
        // the lap pill and the position badge sit under the score bubble
        float x = 40f, y = 156f;
        rect.set(x, y, x + 220f, y + 72f);
        fill.setColor(playerColour);
        c.drawRoundRect(rect, 36f, 36f, fill);
        rect.set(x + 6f, y + 6f, x + 214f, y + 66f);
        fill.setColor(PAPER);
        c.drawRoundRect(rect, 30f, 30f, fill);
        String[] names = lapTotal == 2 ? LAP_OF_2 : LAP;
        Theme.drawCentered(c, names[Math.max(1, Math.min(names.length, lap)) - 1], x + 110f, y + 50f, 44f, INK);
        if (place > 0) {
            rect.set(x, y + 90f, x + 140f, y + 90f + 72f);
            fill.setColor(playerColour);
            c.drawRoundRect(rect, 36f, 36f, fill);
            Theme.drawCentered(c, PLACE[Math.min(2, place)], x + 70f, y + 90f + 52f, 48f, 0xFFFFFFFF);
        }
        for (int i = 0; i < popups.size(); i++) {
            Popup p = popups.at(i);
            float k = 1f - p.life / 0.9f;
            float py = 330f + 20f * k * 4f;
            Theme.drawCentered(c, PLUS[p.points], 150f, py + 3f, 48f, 0x66000000);
            Theme.drawCentered(c, PLUS[p.points], 150f, py, 48f, p.points >= 3 ? 0xFFFFE066 : 0xFFFFFFFF);
        }
        // the steering icon, smaller, bottom centre of the half
        float cx = 480f, cy = 960f;
        boolean hint = !steerActive && noneSeconds > 2f;
        float pulse = hint ? 1f + 0.08f * (float) Math.sin(time * 7f) : 1f;
        c.save();
        c.translate(cx, cy);
        c.scale(pulse * 0.8f, pulse * 0.8f);
        fill.setColor(0xCCF3EBDD);
        c.drawCircle(0f, 0f, 74f, fill);
        c.rotate(-iconDeg);
        Bitmap icon = steerActive ? iconOn : iconOff;
        if (icon != null) {
            rect.set(-66f, -66f, 66f, 66f);
            c.drawBitmap(icon, null, rect, bmp);
        }
        c.restore();
        if (hint) Theme.drawBanner(c, HINT[mode], cx, cy - 110f, 44f, 0xE6F3EBDD, INK);
        // the boost rocket on the outer-right of the half, small
        c.save();
        c.translate(860f, 905f);
        c.scale(0.62f, 0.62f);
        float rx = 0f, ry = 0f, h = 200f;
        boolean full = !boosting && meter >= 0.999f;
        float rp = full ? 1f + 0.07f * (float) Math.sin(time * 9f) : 1f;
        fill.setColor(0xCCF3EBDD);
        rect.set(rx - 85f, ry - 120f, rx + 85f, ry + 150f);
        c.drawRoundRect(rect, 40f, 40f, fill);
        float fillK = boosting ? Math.max(0f, boostLeft / RaceLogic.BOOST_SECONDS) : meter;
        RaceArt.rocket(c, rx, ry - 6f, h * rp, 0xFFB8C3CC, boosting ? 1f : 0f, time);
        if (fillK > 0.01f) {
            float sc = h * rp / 200f;
            float bottom = ry - 6f + 100f * sc, top = ry - 6f - 100f * sc;
            float yy = bottom - (bottom - top) * fillK;
            c.save();
            c.clipRect(rx - 80f, yy, rx + 80f, bottom + 4f);
            RaceArt.rocket(c, rx, ry - 6f, h * rp, boosting ? RaceArt.ORANGE : (full ? 0xFFE0605A : 0xFF5BB8D8), 0f, time);
            c.restore();
        }
        c.restore();
        if (full) Theme.drawCentered(c, "Jump!", 860f, 760f, 40f, RaceArt.ORANGE);
        if (braking) {
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(300f, 960f, 46f, fill);
            fill.setColor(RaceArt.PINK);
            c.drawCircle(300f, 960f, 40f, fill);
        }
        if (lap >= lapTotal && finalLapSeconds < 2.6f) {
            Theme.drawBanner(c, "Final lap!", 480f, 300f, 64f, 0xE6F3EBDD, RaceArt.ORANGE);
        }
        c.restore();
    }

    /** T-24: the shared minimap at the top centre of the two-player screen (both dots). */
    void drawSharedMinimap(Canvas c) {
        if (mapBmp == null || layout == null) return;
        float x = (Stage.W - MAP_W) / 2f, y = MAP_Y + 100f;
        c.save();
        c.translate(x - MAP_X, y - MAP_Y);
        drawMinimap(c);
        c.restore();
    }

    private void drawSpeedLines(Canvas c) {
        if (lines.size() == 0) return;
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(7f);
        float cx = Stage.W / 2f, cy = Stage.H / 2f - 40f;
        for (int i = 0; i < lines.size(); i++) {
            SpeedLine s = lines.at(i);
            float k = 1f - s.life / SPEED_LIFE;
            float r0 = 380f + k * 560f, r1 = r0 + 70f + 80f * k;
            float cs = (float) Math.cos(s.ang), sn = (float) Math.sin(s.ang);
            stroke.setAlpha((int) (200f * Math.min(1f, s.life / SPEED_LIFE * 1.6f)));
            c.drawLine(cx + cs * r0 * 1.25f, cy + sn * r0 * 0.8f, cx + cs * r1 * 1.25f, cy + sn * r1 * 0.8f, stroke);
        }
        stroke.setAlpha(255);
    }

    private void drawMinimap(Canvas c) {
        if (mapBmp == null || layout == null) return;
        c.drawBitmap(mapBmp, MAP_X, MAP_Y, null);
        for (int i = 0; i < sceneryCount; i++) dot(c, sceneryS[i], 7f, 0xFF8A97A3, 0f);
        dot(c, playerS, 11f, playerColour, 4f);
        if (player2S >= 0f) dot(c, player2S, 11f, 0xFFFF6EC7, 4f);
    }

    private void dot(Canvas c, float s, float r, int colour, float ring) {
        layout.sample(s, tmp);
        float x = MAP_X + mapOx + tmp[0] * mapScale, y = MAP_Y + mapOz + tmp[1] * mapScale;
        if (ring > 0f) {
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(x, y, r + ring, fill);
        }
        fill.setColor(colour);
        c.drawCircle(x, y, r, fill);
    }

    private void drawLap(Canvas c) {
        float x = 40f, y = 156f;
        rect.set(x, y, x + 220f, y + 72f);
        fill.setColor(playerColour);
        c.drawRoundRect(rect, 36f, 36f, fill);
        rect.set(x + 6f, y + 6f, x + 214f, y + 66f);
        fill.setColor(PAPER);
        c.drawRoundRect(rect, 30f, 30f, fill);
        String[] names = lapTotal == 2 ? LAP_OF_2 : LAP;
        Theme.drawCentered(c, names[Math.max(1, Math.min(names.length, lap)) - 1], x + 110f, y + 50f, 44f, INK);
    }

    private void drawPopups(Canvas c) {
        for (int i = 0; i < popups.size(); i++) {
            Popup p = popups.at(i);
            float k = 1f - p.life / 0.9f;
            float y = 250f + 20f * k * 4f;
            Theme.drawCentered(c, PLUS[p.points], 150f, y + 3f, 56f, 0x66000000);
            Theme.drawCentered(c, PLUS[p.points], 150f, y, 56f, p.points >= 3 ? 0xFFFFE066 : 0xFFFFFFFF);
        }
    }

    /** The hint shown when the gesture has not been made for 2 s, per mode. */
    static final String[] HINT = {"Arms out like a plane!", "Lean to steer!", "Hold the wheel!"};

    private void drawSteerIcon(Canvas c) {
        float cx = Stage.W / 2f, cy = 960f;
        boolean hint = !steerActive && noneSeconds > 2f;
        float pulse = hint ? 1f + 0.08f * (float) Math.sin(time * 7f) : 1f;
        c.save();
        c.translate(cx, cy);
        c.scale(pulse, pulse);
        fill.setColor(0xCCF3EBDD);
        c.drawCircle(0f, 0f, 74f, fill);
        c.rotate(-iconDeg);
        Bitmap icon = steerActive ? iconOn : iconOff;
        if (icon == null) {
            c.restore();
            return;
        }
        rect.set(-66f, -66f, 66f, 66f);
        c.drawBitmap(icon, null, rect, bmp);
        c.restore();
        if (hint) Theme.drawBanner(c, HINT[mode], cx, cy - 120f, 52f, 0xE6F3EBDD, INK);
    }

    /** The magnet icon with a bar that shrinks as its time runs out, above the rocket. */
    private void drawMagnet(Canvas c) {
        if (magnetK <= 0f) return;
        float cx = 1760f, cy = 730f;
        fill.setColor(0xCCF3EBDD);
        rect.set(cx - 85f, cy - 60f, cx + 85f, cy + 52f);
        c.drawRoundRect(rect, 30f, 30f, fill);
        RaceArt.magnet(c, cx, cy - 14f, 64f);
        fill.setColor(0x55000000);
        rect.set(cx - 60f, cy + 22f, cx + 60f, cy + 36f);
        c.drawRoundRect(rect, 7f, 7f, fill);
        fill.setColor(magnetK < 0.25f && ((int) (time * 8f) & 1) == 0 ? 0xFFF3A19C : 0xFFE8504A);
        rect.set(cx - 60f, cy + 22f, cx - 60f + 120f * magnetK, cy + 36f);
        c.drawRoundRect(rect, 7f, 7f, fill);
    }

    private void drawRocket(Canvas c) {
        float cx = 1760f, cy = 905f, h = 200f;
        boolean full = !boosting && meter >= 0.999f;
        float pulse = full ? 1f + 0.07f * (float) Math.sin(time * 9f) : 1f;
        fill.setColor(0xCCF3EBDD);
        rect.set(cx - 85f, cy - 120f, cx + 85f, cy + 150f);
        c.drawRoundRect(rect, 40f, 40f, fill);
        float fillK = boosting ? Math.max(0f, boostLeft / RaceLogic.BOOST_SECONDS) : meter;
        RaceArt.rocket(c, cx, cy - 6f, h * pulse, 0xFFB8C3CC, boosting ? 1f : 0f, time);
        if (fillK > 0.01f) {
            float s = h * pulse / 200f;
            float bottom = cy - 6f + 100f * s, top = cy - 6f - 100f * s;
            float y = bottom - (bottom - top) * fillK;
            c.save();
            c.clipRect(cx - 80f, y, cx + 80f, bottom + 4f);
            RaceArt.rocket(c, cx, cy - 6f, h * pulse, boosting ? RaceArt.ORANGE : (full ? 0xFFE0605A : 0xFF5BB8D8), 0f, time);
            c.restore();
        }
        if (full) Theme.drawCentered(c, "Jump!", cx, cy - 140f, 56f, RaceArt.ORANGE);
    }

    private void drawBrake(Canvas c) {
        float cx = 760f, cy = 960f;
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, 58f, fill);
        fill.setColor(RaceArt.PINK);
        c.drawCircle(cx, cy, 50f, fill);
        arrow.rewind();
        arrow.moveTo(cx - 24f, cy - 12f);
        arrow.lineTo(cx + 24f, cy - 12f);
        arrow.lineTo(cx, cy + 22f);
        arrow.close();
        fill.setColor(0xFFFFFFFF);
        c.drawPath(arrow, fill);
    }

    private void drawCamera(Canvas c) {
        Bitmap b = camera;
        if (b == null) return;
        int bw = b.getWidth(), bh = b.getHeight();
        float want = CAM_W / CAM_H;
        int sw = bw, sh = bh;
        if ((float) bw / bh > want) sw = Math.round(bh * want); else sh = Math.round(bw / want);
        int sx = (bw - sw) / 2, sy = (bh - sh) / 2;
        src.set(sx, sy, sx + sw, sy + sh);
        rect.set(0f, 0f, CAM_W, CAM_H);
        c.save();
        c.translate(CAM_X + CAM_W, CAM_Y);
        c.scale(-1f, 1f); // a mirror: the player's left hand is on the left
        c.drawBitmap(b, src, rect, bmp);
        c.restore();
        stroke.setColor(INK);
        stroke.setStrokeWidth(10f);
        rect.set(CAM_X - 3f, CAM_Y - 3f, CAM_X + CAM_W + 3f, CAM_Y + CAM_H + 3f);
        c.drawRoundRect(rect, 12f, 12f, stroke);
    }
}
