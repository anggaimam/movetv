package com.wiggleplay.app.games.sky;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.KeyEvent;

import com.wiggleplay.app.core.GameView;
import com.wiggleplay.app.core.Pool;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

/**
 * Sky Glider: fly a little plane in a behind-the-plane view. Hold your arms out like wings, tilt your shoulders to
 * steer, raise or lower your arms to climb or dive, fly through the rings and pick up coins. Flat storybook art.
 * One or two players (two get a viewport each, the same course, their own score). Nothing ever crashes the plane
 * and the round never ends early. The camera picture is not shown: the sky is the picture.
 */
public final class SkyScene implements Scene {

    private static final String GAME_ID = "sky";
    private static final float ROUND_SECONDS = 80f;
    private static final float CAM_H = 260f;
    private static final float CAM_H_2P = 420f;
    private static final float GROUND_Y = -300f;
    private static final float STRIPE = 500f;
    private static final float FAR = SkyLogic.FOCAL + SkyLogic.SPAWN_DIST + 400f;
    private static final float NEAR_CUT = SkyLogic.FOCAL * 0.45f;
    private static final float ZOOM_2P = 0.55f;
    private static final String[] POPUP = {"+0", "+1", "+2", "+3", "+4", "+5"};
    private static final boolean[] RULES_SEEN = new boolean[4];

    private static final class Spark {
        float x, y, vx, vy, life, size;
        int colour;
    }

    private static final class Popup {
        float x, y, life;
        int idx, colour;
    }

    private final TileMenu.Env env;
    private final Hud hud;
    private final SkyRules rules = new SkyRules();
    private final SkyLogic[] logic = new SkyLogic[2];
    private final Pool<Spark> sparks = new Pool<>(120, Spark::new);
    private final Pool<Popup> popups = new Pool<>(12, Popup::new);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Rect src = new Rect();

    /** The plane picked last in the menu (used when the game starts without the plane menu). */
    static int lastPlane;

    private final int[] planeType = new int[2];
    private final int[] planeTheme = new int[2];
    private int level = 1, players = 1;
    private float time, demoT;
    private int sparkCap = 120;

    // per-viewport values, set at the start of drawing one viewport
    private float vx, vw, zoom, cx, camX, camY, camZ, camH;

    /**
     * Tells the game whether the rules screen has been shown already this session (tests use this).
     * @param seen true to skip the rules screen on the next entry
     */
    public static void setRulesSeen(boolean seen) {
        for (int i = 0; i < RULES_SEEN.length; i++) RULES_SEEN[i] = seen;
    }

    /**
     * Creates the game.
     * @param env what the screens share
     */
    public SkyScene(TileMenu.Env env) {
        this.env = env;
        this.hud = new Hud(env, ROUND_SECONDS, "sky");
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void enter(SceneArgs args) {
        env.leaveMenus();
        level = Math.max(1, Math.min(3, args.getInt("level", 1)));
        players = args.getInt("players", env.players) == 2 ? 2 : 1;
        SkyArt.prepare(level);
        planeType[0] = Math.floorMod(args.getInt("plane", lastPlane), PlaneModel.COUNT);
        planeType[1] = Math.floorMod(args.getInt("plane2", planeType[0] + 1), PlaneModel.COUNT);
        hud.setRules(rules);
        beginRound(!RULES_SEEN[level]);
        RULES_SEEN[level] = true;
    }

    private void beginRound(boolean showRules) {
        for (int i = sparks.size() - 1; i >= 0; i--) sparks.freeAt(i);
        for (int i = popups.size() - 1; i >= 0; i--) popups.freeAt(i);
        demoT = 0f;
        long seed = System.nanoTime();
        // a new colour theme for each plane every round
        int t0 = (int) (Math.abs(seed >> 7) % PlaneModel.themes(planeType[0]));
        planeTheme[0] = t0;
        planeTheme[1] = (int) (Math.abs(seed >> 13) % PlaneModel.themes(planeType[1]));
        if (planeType[0] == planeType[1] && planeTheme[0] == planeTheme[1]) planeTheme[1]++;
        rules.setPlane(planeType[0], planeTheme[0]);
        for (int p = 0; p < 2; p++) {
            if (p >= players) {
                logic[p] = null;
                continue;
            }
            final int pl = p;
            logic[p] = new SkyLogic(level, seed, seed ^ 0x5DEECE66DL, (type, it, points) -> happened(pl, type, it, points));
        }
        hud.start(players, showRules);
    }

    @Override
    public void exit() {
        if (env.music != null) env.music.stop(300);
    }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        sparkCap = GameView.quality == GameView.LOW ? 60 : 120;
        String sel = hud.update(dt, f);
        if (sel != null) {
            if ("again".equals(sel) || "go".equals(sel)) {
                beginRound(false);
            } else if ("rules".equals(sel)) {
                hud.openRules();
            } else if ("home".equals(sel)) {
                env.go("gameselect", SceneArgs.EMPTY);
            } else {
                env.go("levelselect", SceneArgs.of("gameId", GAME_ID));
            }
            return;
        }
        if (hud.phase() == Hud.RULES) {
            rules.update(dt);
            return;
        }
        if (hud.phase() == Hud.COUNTDOWN && !hud.paused()) demoT += dt;
        if (hud.running()) {
            boolean test = "test".equals(f.pose.source);
            for (int p = 0; p < players; p++) {
                SkyLogic l = logic[p];
                l.control(f.pose.players[p]);
                if (test) {
                    // the Fire TV remote has no mouse: up and down keys climb and dive in test mode
                    boolean up = f.input.held(KeyEvent.KEYCODE_DPAD_UP) || f.input.held(KeyEvent.KEYCODE_W);
                    boolean down = f.input.held(KeyEvent.KEYCODE_DPAD_DOWN) || f.input.held(KeyEvent.KEYCODE_S);
                    if (up != down) l.setClimbTarget(up ? 1f : -1f);
                }
                l.update(dt);
            }
            if (hud.readyToFinish(true)) {
                int s0 = SkyLogic.stars(logic[0].score(), logic[0].offered());
                int s1 = players == 2 ? SkyLogic.stars(logic[1].score(), logic[1].offered()) : 0;
                if (env.profile != null) env.profile.recordStars(GAME_ID, level, Math.max(s0, s1));
                hud.finish(s0, s1, logic[0].score(), players == 2 ? logic[1].score() : 0);
            }
        }
        animateEffects(dt);
    }

    // ---- what happens to the plane ----

    private void happened(int player, int type, SkyLogic.Item it, int points) {
        switch (type) {
            case SkyLogic.EV_RING:
                env.sfx("star", 1f);
                burst(player, 14, Theme.MUSTARD, 22f);
                popup(player, points, Theme.ORANGE);
                break;
            case SkyLogic.EV_COIN:
                env.sfx("coin");
                burst(player, 6, Theme.MUSTARD, 14f);
                popup(player, points, Theme.INK);
                break;
            case SkyLogic.EV_STAR:
                env.sfx("star", 1.35f);
                burst(player, 18, Theme.ORANGE, 24f);
                popup(player, points, Theme.ORANGE);
                break;
            case SkyLogic.EV_MAGNET:
                env.sfx("star", 1.2f);
                burst(player, 16, 0xFFE8504A, 22f);
                break;
            case SkyLogic.EV_BUMP:
                env.sfx("boing");
                burst(player, 8, 0xFFFFFFFF, 16f);
                break;
            case SkyLogic.EV_FOG:
                env.sfx("whoosh");
                break;
            default:
                break;
        }
    }

    private void setViewport(int p) {
        vw = players == 2 ? Stage.W / 2f : Stage.W;
        vx = players == 2 ? p * vw : 0f;
        zoom = players == 2 ? ZOOM_2P : 1f;
        camH = players == 2 ? CAM_H_2P : CAM_H;
        cx = vx + vw / 2f;
        SkyLogic l = logic[p];
        camX = l.planeX() * 0.85f;
        camY = l.planeY() * 0.85f;
        camZ = l.planeZ() - SkyLogic.FOCAL;
    }

    private float planeSX(SkyLogic l) {
        return cx + (l.planeX() - camX) * zoom;
    }

    private float planeSY(SkyLogic l) {
        return SkyArt.HORIZON - (l.planeY() - camY - camH) * zoom;
    }

    private void burst(int player, int n, int colour, float size) {
        setViewport(player);
        SkyLogic l = logic[player];
        float x = planeSX(l), y = planeSY(l);
        if (GameView.quality == GameView.LOW) n = (n + 1) / 2;
        for (int i = 0; i < n; i++) {
            if (sparks.size() >= sparkCap) return;
            Spark s = sparks.obtain();
            if (s == null) return;
            float a = (float) (Math.random() * Math.PI * 2), sp = 140f + (float) Math.random() * 330f;
            s.x = x;
            s.y = y;
            s.vx = (float) Math.cos(a) * sp;
            s.vy = (float) Math.sin(a) * sp - 80f;
            s.life = 0.45f + (float) Math.random() * 0.35f;
            s.size = 5f + (float) Math.random() * size * 0.35f;
            s.colour = (i & 1) == 0 ? colour : 0xFFFFFFFF;
        }
    }

    private void popup(int player, int points, int colour) {
        setViewport(player);
        SkyLogic l = logic[player];
        Popup pu = popups.obtain();
        if (pu == null) return;
        pu.x = planeSX(l);
        pu.y = planeSY(l) - 120f * zoom;
        pu.life = 0.9f;
        pu.idx = Math.max(0, Math.min(POPUP.length - 1, points));
        pu.colour = colour;
    }

    private void animateEffects(float dt) {
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.at(i);
            s.life -= dt;
            s.vy += 900f * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            if (s.life <= 0f) sparks.freeAt(i);
        }
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.at(i);
            p.life -= dt;
            p.y -= 80f * dt;
            if (p.life <= 0f) popups.freeAt(i);
        }
    }

    // ---- drawing ----

    @Override
    public void draw(Canvas c) {
        if (hud.phase() == Hud.RULES) {
            drawBackdropOnly(c);
            hud.draw(c, 0, 0);
            return;
        }
        for (int p = 0; p < players; p++) drawViewport(c, p);
        for (int i = 0; i < sparks.size(); i++) {
            Spark s = sparks.at(i);
            paint.setColor(s.colour);
            paint.setAlpha((int) (255 * Math.min(1f, s.life * 3f)));
            c.drawCircle(s.x, s.y, s.size, paint);
        }
        paint.setAlpha(255);
        for (int i = 0; i < popups.size(); i++) {
            Popup pu = popups.at(i);
            Theme.drawCentered(c, POPUP[pu.idx], pu.x + 3f, pu.y + 3f, 64f, 0x66000000);
            Theme.drawCentered(c, POPUP[pu.idx], pu.x, pu.y, 64f, pu.colour);
        }
        if (hud.phase() == Hud.COUNTDOWN) {
            if (players == 2) rules.drawDemo(c, Stage.W / 2f, 815f, demoT);
            else rules.drawDemo(c, 440f, 720f, demoT);
        }
        hud.draw(c, logic[0] != null ? logic[0].score() : 0, logic[1] != null ? logic[1].score() : 0);
    }

    private void drawBackdropOnly(Canvas c) {
        src.set(0, 0, 960, 540);
        rect.set(0f, 0f, Stage.W, Stage.H);
        c.drawBitmap(SkyArt.bg(level), src, rect, sprite);
    }

    private void blit(Canvas c, Bitmap b, float x, float y, float hw, float hh) {
        if (x + hw < vx || x - hw > vx + vw) return;
        rect.set(x - hw, y - hh, x + hw, y + hh);
        c.drawBitmap(b, null, rect, sprite);
    }

    private float groundY(float dz) {
        return SkyArt.HORIZON + (camY + camH - GROUND_Y) * SkyLogic.FOCAL / dz * zoom;
    }

    private void drawViewport(Canvas c, int p) {
        setViewport(p);
        SkyLogic l = logic[p];
        c.save();
        c.clipRect(vx, 0f, vx + vw, Stage.H);
        // sky and sea or meadow
        if (players == 2) src.set(240, 0, 720, 540); else src.set(0, 0, 960, 540);
        rect.set(vx, 0f, vx + vw, Stage.H);
        c.drawBitmap(SkyArt.bg(level), src, rect, sprite);
        boolean low = GameView.quality == GameView.LOW;
        drawLayer(c, SkyArt.far(level), 0.10f, 0.10f, 30f);
        drawStripes(c);
        if (!low) drawLayer(c, SkyArt.near(level), 0.22f, 0.24f, 60f);
        // the shadow of the plane on the ground, small when high
        float shrink = 1f - 0.5f * l.planeY() / SkyLogic.Y_MAX;
        float shy = groundY(SkyLogic.FOCAL);
        blit(c, SkyArt.shadow(), planeSX(l), shy, 120f * zoom * shrink, 45f * zoom * shrink);
        boolean planeDone = false;
        for (int i = l.itemCount() - 1; i >= 0; i--) {
            SkyLogic.Item it = l.item(i);
            float dz = it.z - camZ;
            if (!planeDone && dz <= SkyLogic.FOCAL) {
                drawPlane(c, l, p);
                planeDone = true;
            }
            if (dz < NEAR_CUT || dz > FAR) continue;
            drawItem(c, it, dz);
        }
        if (!planeDone) drawPlane(c, l, p);
        drawSpeedLines(c, l);
        if (l.magnet() > 0f) drawMagnetAura(c, l);
        if (l.fog() > 0f) {
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha((int) (170 * l.fog() / 0.7f));
            c.drawRect(vx, 0f, vx + vw, Stage.H, paint);
            paint.setAlpha(255);
        }
        if (Math.abs(l.gust()) > 25f) drawGust(c, l.gust());
        c.restore();
    }

    private void drawLayer(Canvas c, Bitmap b, float parallaxX, float parallaxY, float below) {
        float w = b.getWidth() * 2f * zoom, h = b.getHeight() * 2f * zoom;
        float x = cx - w / 2f - (GameView.quality == GameView.LOW ? 0f : camX * parallaxX * zoom);
        float bottom = SkyArt.HORIZON + below * zoom + (GameView.quality == GameView.LOW ? 0f : camY * parallaxY * zoom);
        rect.set(x, bottom - h, x + w, bottom);
        c.drawBitmap(b, null, rect, sprite);
    }

    /** Flat stripes on the ground that slide toward the plane: they are what makes the speed visible. */
    private void drawStripes(Canvas c) {
        paint.setColor(SkyArt.STRIPE[level - 1][0]);
        long first = (long) Math.floor((camZ + NEAR_CUT) / STRIPE);
        for (long k = first; ; k++) {
            float z0 = k * STRIPE;
            if ((k & 1) != 0) continue;
            float d0 = Math.max(z0 - camZ, NEAR_CUT), d1 = z0 + STRIPE * 0.5f - camZ;
            if (d0 > FAR) break;
            if (d1 <= NEAR_CUT) continue;
            float yNear = groundY(d0), yFar = groundY(d1);
            if (yNear > Stage.H) yNear = Stage.H;
            if (yFar < SkyArt.HORIZON) yFar = SkyArt.HORIZON;
            if (yNear > yFar) c.drawRect(vx, yFar, vx + vw, yNear, paint);
        }
    }

    private void drawItem(Canvas c, SkyLogic.Item it, float dz) {
        float scale = SkyLogic.FOCAL / dz * zoom;
        float x = cx + (it.x - camX) * scale;
        float y = SkyArt.HORIZON - (it.y - camY - camH) * scale;
        switch (it.kind) {
            case SkyLogic.RING: {
                float h = it.r * scale * 256f / 230f;
                blit(c, SkyArt.ring(), x, y, h, h);
                break;
            }
            case SkyLogic.COIN:
                if (it.hit) break;
                blit(c, SkyArt.coin(), x, y, 64f * scale, 64f * scale);
                break;
            case SkyLogic.STAR:
                if (it.hit) break;
                blit(c, SkyArt.star(), x, y, 96f * scale * (1f + 0.06f * (float) Math.sin(time * 5f + it.seed * 6f)),
                        96f * scale);
                break;
            case SkyLogic.MAGNET:
                if (it.hit) break;
                blit(c, SkyArt.magnet(), x, y + 6f * scale * (float) Math.sin(time * 4f + it.seed * 6f), 80f * scale, 80f * scale);
                break;
            case SkyLogic.BALLOON:
                blit(c, SkyArt.balloon(), x, y - 30f * scale, 140f * scale, 186f * scale);
                break;
            case SkyLogic.BIRD:
                blit(c, SkyArt.bird(((int) (time * 6f + it.seed * 10f)) & 1), x, y, 100f * scale, 70f * scale);
                break;
            default: {
                int v = (int) (it.seed * 3f);
                float hw = it.r > 0f ? 300f : 200f * (0.7f + 0.8f * it.seed);
                blit(c, SkyArt.cloud(v), x, y, hw * scale, hw * 0.5f * scale);
                break;
            }
        }
    }

    private void drawPlane(Canvas c, SkyLogic l, int p) {
        float x = planeSX(l), y = planeSY(l);
        float steer = l.steer();
        float roll = PlaneDraw.rollFor(steer) + (l.wobble() > 0f ? (float) Math.sin(time * 38f) * 12f * l.wobble() / 0.7f : 0f);
        PlaneDraw.shared().setProp(time * 31f + p);
        PlaneDraw.shared().draw(c, planeType[p], planeTheme[p], roll, PlaneDraw.yawFor(steer), PlaneDraw.elevFor(l.climb()),
                x, y, 88f * zoom);
    }

    /** Thin streaks that rush past when the plane turns, climbs or dives; they go the other way from the move. */
    private void drawSpeedLines(Canvas c, SkyLogic l) {
        float sx = l.steer(), cl = l.climb();
        float mag = Math.max(Math.abs(sx), Math.abs(cl));
        if (mag < 0.3f) return;
        float k = Math.min(1f, (mag - 0.3f) / 0.5f);
        float dx = -sx, dy = cl * 0.9f;
        float n = (float) Math.sqrt(dx * dx + dy * dy);
        if (n < 0.01f) return;
        dx /= n;
        dy /= n;
        float len = (60f + 120f * k) * zoom, speed = 1500f * zoom;
        float px = planeSX(l), py = planeSY(l), keep = 150f * zoom;
        int lines = GameView.quality == GameView.LOW ? 8 : 16;
        paint.setStrokeWidth(Math.max(2f, 6f * zoom));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha((int) (190 * k));
        for (int i = 0; i < lines; i++) {
            float fx = (i * 0.6180339f) % 1f, fy = (i * 0.3819660f + 0.13f) % 1f;
            float run = time * speed * (0.7f + 0.6f * ((i * 0.37f) % 1f)) + i * 131f;
            float x = vx + (((fx * vw + dx * run) % vw) + vw) % vw;
            float y = (((fy * Stage.H + dy * run) % Stage.H) + Stage.H) % Stage.H;
            float ex = x - px, ey = y - py;
            if (ex * ex + ey * ey < keep * keep) continue;
            c.drawLine(x, y, x - dx * len, y - dy * len, paint);
        }
        paint.setAlpha(255);
    }

    /** A soft golden glow round the plane with the magnet and a bar that shrinks as the time runs out. */
    private void drawMagnetAura(Canvas c, SkyLogic l) {
        float x = planeSX(l), y = planeSY(l);
        float left = l.magnet() / SkyLogic.MAGNET_TIME;
        float pulse = 1f + 0.07f * (float) Math.sin(time * 9f);
        paint.setColor(0xFFFFE08A);
        paint.setAlpha(left < 0.25f && ((int) (time * 8f) & 1) == 0 ? 25 : 60);
        c.drawCircle(x, y, 190f * zoom * pulse, paint);
        paint.setAlpha(255);
        float by = y - 190f * zoom, bw = 90f * zoom;
        blit(c, SkyArt.magnet(), x, by - 26f * zoom, 30f * zoom, 30f * zoom);
        paint.setColor(0x55000000);
        rect.set(x - bw / 2f, by + 12f * zoom, x + bw / 2f, by + 22f * zoom);
        c.drawRoundRect(rect, 5f, 5f, paint);
        paint.setColor(0xFFE8504A);
        rect.set(x - bw / 2f, by + 12f * zoom, x - bw / 2f + bw * left, by + 22f * zoom);
        c.drawRoundRect(rect, 5f, 5f, paint);
    }

    private void drawGust(Canvas c, float gust) {
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(150);
        float dir = gust > 0f ? 1f : -1f;
        for (int k = 0; k < 6; k++) {
            float y = 220f + k * 130f + (k & 1) * 40f;
            float x = vx + ((time * 1100f * dir + k * 331f) % vw + vw) % vw;
            c.drawRect(x, y, x + 160f * zoom, y + 8f, paint);
        }
        paint.setAlpha(255);
    }
}
