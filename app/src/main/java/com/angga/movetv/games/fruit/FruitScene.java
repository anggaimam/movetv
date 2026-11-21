package com.angga.movetv.games.fruit;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.angga.movetv.core.GameView;
import com.angga.movetv.core.Pool;
import com.angga.movetv.core.Scene;
import com.angga.movetv.core.Stage;
import com.angga.movetv.games.common.BodyShadow;
import com.angga.movetv.games.common.Hud;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.PlayerPose;
import com.angga.movetv.pose.PoseFrame;
import com.angga.movetv.ui.Theme;
import com.angga.movetv.ui.TileMenu;

/**
 * Fruit Splash: fruit fly up, wave your hands through them to slice them. Flat storybook art. One or two
 * players (two split the screen in halves). No lives, nothing is lost: missed fruit just falls, the grumpy
 * coconut scores nothing, and after a few misses the fruit get a little bigger until you slice a couple.
 */
public final class FruitScene implements Scene {

    private static final String GAME_ID = "fruit";
    private static final float ROUND_SECONDS = 75f;
    private static final String[] POPUP = {"+0", "+1", "+2", "+3", "+4", "+5", "+6", "+7", "+8", "+9", "+10"};

    private static final class Half {
        int kind, side;
        float x, y, vx, vy, rot, rotSpeed, r, life;
    }

    private static final class Drop {
        float x, y, vx, vy, life, size;
        int colour;
    }

    private static final class Splat {
        float x, y, size, life;
        int colour;
    }

    private static final class Slash {
        float x, y, dx, dy, life;
    }

    private static final float[] SPL_X = {0f, 0.95f, -0.85f, 0.45f, -0.4f, 1.15f, -1.1f, 0.1f};
    private static final float[] SPL_Y = {0f, 0.3f, -0.4f, -0.95f, 0.9f, -0.5f, 0.55f, 1.2f};
    private static final float[] SPL_R = {0.9f, 0.45f, 0.5f, 0.4f, 0.42f, 0.3f, 0.28f, 0.25f};
    private static final String[] NEG = {"Oops!", "-1", "-2", "-3", "-4", "-5"};
    private static boolean rulesSeenOverride, rulesSeenFallback;

    private static final class Popup {
        float x, y, life;
        int idx, colour;
        String text;
    }

    private final TileMenu.Env env;
    private final Hud hud;
    private final Pool<Half> halves = new Pool<>(24, Half::new);
    private final Pool<Drop> drops = new Pool<>(300, Drop::new);
    private final Pool<Popup> popups = new Pool<>(12, Popup::new);
    private final Pool<Splat> splats = new Pool<>(14, Splat::new);
    private final Pool<Slash> slashes = new Pool<>(16, Slash::new);
    private float flash;
    private final HandTrack[][] hands = new HandTrack[2][2];
    private final FruitLogic[] logic = new FruitLogic[2];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    private int level = 1, players = 1;
    private FruitRules rules;
    private Bitmap camera;
    private PoseFrame pose;
    private final AuroraBackdrop aurora = new AuroraBackdrop();
    private final BodyShadow shadow = new BodyShadow();
    private float time;
    private float lastDt = 0.016f;
    private int dropCap = 300;

    /**
     * Lets tests skip (true) or show (false) the rules screen on the next entry, whatever the saved profile says.
     * @param seen true to skip the rules screen
     */
    public static void setRulesSeen(boolean seen) {
        rulesSeenOverride = seen;
        rulesSeenFallback = false;
    }

    /**
     * Gives the fruit pictures access to the app's assets (call once at start, before the game is played).
     * @param context any context
     */
    public static void initArt(android.content.Context context) {
        FruitArt.init(context);
    }

    /**
     * Creates the game.
     * @param env what the screens share
     */
    public FruitScene(TileMenu.Env env) {
        this.env = env;
        this.hud = new Hud(env, ROUND_SECONDS, "fruit");
        paint.setStyle(Paint.Style.FILL);
        for (int p = 0; p < 2; p++) for (int h = 0; h < 2; h++) hands[p][h] = new HandTrack(14f);
    }

    @Override
    public void enter(SceneArgs args) {
        env.leaveMenus();
        level = Math.max(1, Math.min(3, args.getInt("level", 1)));
        players = args.getInt("players", env.players) == 2 ? 2 : 1;
        rules = new FruitRules(level);
        hud.setRules(rules);
        // the rules show the first time each level is played, ever (saved in the profile)
        boolean seen = rulesSeenOverride;
        if (env.profile != null) {
            seen |= env.profile.rulesSeen(GAME_ID, level);
            env.profile.setRulesSeen(GAME_ID, level);
        } else {
            seen |= rulesSeenFallback;
            rulesSeenFallback = true;
        }
        beginRound(!seen);
    }

    private void beginRound(boolean showRules) {
        for (int i = halves.size() - 1; i >= 0; i--) halves.freeAt(i);
        for (int i = drops.size() - 1; i >= 0; i--) drops.freeAt(i);
        for (int i = popups.size() - 1; i >= 0; i--) popups.freeAt(i);
        for (int i = splats.size() - 1; i >= 0; i--) splats.freeAt(i);
        for (int i = slashes.size() - 1; i >= 0; i--) slashes.freeAt(i);
        flash = 0f;
        long seed = System.nanoTime();
        for (int p = 0; p < 2; p++) {
            if (p >= players) {
                logic[p] = null;
                continue;
            }
            float x0 = players == 2 ? p * Stage.W / 2f : 0f;
            float x1 = players == 2 ? (p + 1) * Stage.W / 2f : Stage.W;
            final int pl = p;
            logic[p] = new FruitLogic(level, players, seed + p * 7919L, x0, x1, new FruitLogic.Listener() {
                @Override
                public void onSlice(FruitLogic.Fruit f, float dx, float dy, int points, boolean combo) {
                    sliced(pl, f, dx, dy, points, combo);
                }

                @Override
                public void onCoconut(FruitLogic.Fruit f, float dx, float dy, int lost) {
                    bonk(f, lost);
                }

                @Override
                public void onMiss(FruitLogic.Fruit f) { }
            });
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
        lastDt = dt;
        camera = f.camera;
        pose = f.pose;
        dropCap = GameView.quality == GameView.LOW ? 150 : 300;
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
        if (hud.phase() == Hud.END) {
            animateEffects(dt);
            return;
        }
        if (hud.phase() == Hud.RULES) {
            rules.update(dt);
            return;
        }
        // hands always follow, so the glide is ready when the round starts
        for (int p = 0; p < players; p++) {
            PlayerPose pp = f.pose.players[p];
            for (int h = 0; h < 2; h++) {
                Keypoint k = pp.kps[h == 0 ? Kp.L_WRIST : Kp.R_WRIST];
                boolean ok = pp.present && k.s >= 0.3f;
                hands[p][h].update(dt, ok, k.x, k.y, f.nowMs);
            }
        }
        if (hud.running()) {
            for (int p = 0; p < players; p++) logic[p].update(dt, f.nowMs, hands[p], hud.spawning());
            animateEffects(dt);
            if (hud.readyToFinish(allEmpty())) {
                int s0 = FruitLogic.stars(logic[0].score(), logic[0].offered());
                int s1 = players == 2 ? FruitLogic.stars(logic[1].score(), logic[1].offered()) : 0;
                if (env.profile != null) env.profile.recordStars(GAME_ID, level, Math.max(s0, s1));
                hud.finish(s0, s1, logic[0].score(), players == 2 ? logic[1].score() : 0);
            }
        }
    }

    private boolean allEmpty() {
        for (int p = 0; p < players; p++) if (logic[p].activeCount() > 0) return false;
        return true;
    }

    private void sliced(int player, FruitLogic.Fruit f, float dx, float dy, int points, boolean combo) {
        env.sfx(f.kind == FruitLogic.STAR ? "star" : "splat", 0.9f + (f.kind % 4) * 0.08f);
        if (combo) env.sfx("coin");
        Slash sl = slashes.obtain();
        if (sl != null) {
            sl.x = f.x;
            sl.y = f.y;
            sl.dx = dx;
            sl.dy = dy;
            sl.life = 0.28f;
        }
        Splat sp = splats.obtain();
        if (sp != null) {
            sp.x = f.x;
            sp.y = f.y;
            sp.size = f.r * 0.9f;
            sp.life = 2.2f;
            sp.colour = FruitArt.FLESH[f.kind];
        }
        float nx = -dy, ny = dx;
        for (int s = -1; s <= 1; s += 2) {
            Half h = halves.obtain();
            if (h == null) break;
            h.kind = f.kind;
            h.side = s;
            h.x = f.x;
            h.y = f.y;
            h.r = f.r;
            h.vx = f.vx * 0.4f + nx * s * 220f;
            h.vy = Math.min(f.vy, 0f) * 0.3f + ny * s * 220f - 140f;
            h.rot = f.spin;
            h.rotSpeed = s * 180f;
            h.life = 1.2f;
        }
        int n = GameView.quality == GameView.LOW ? 8 : 16;
        burst(f.x, f.y, FruitArt.FLESH[f.kind], n, f.r);
        Popup pu = popups.obtain();
        if (pu != null) {
            pu.x = f.x;
            pu.y = f.y - f.r;
            pu.life = 0.9f;
            pu.text = POPUP[Math.max(0, Math.min(POPUP.length - 1, points))];
            pu.colour = combo ? Theme.ORANGE : Theme.playerColour(player);
        }
    }

    private void bonk(FruitLogic.Fruit f, int lost) {
        env.sfx("bonk");
        burst(f.x, f.y, 0xFF9B6B43, 10, f.r);
        if (lost > 0) flash = 0.35f;
        Popup pu = popups.obtain();
        if (pu != null) {
            pu.x = f.x;
            pu.y = f.y - f.r;
            pu.life = 1.1f;
            pu.text = NEG[Math.max(0, Math.min(NEG.length - 1, lost))];
            pu.colour = lost > 0 ? 0xFFD64545 : Theme.INK;
        }
    }

    private void burst(float x, float y, int colour, int n, float r) {
        for (int i = 0; i < n; i++) {
            if (drops.size() >= dropCap) return;
            Drop d = drops.obtain();
            if (d == null) return;
            float a = (float) (Math.random() * Math.PI * 2);
            float sp = 160f + (float) Math.random() * 380f;
            d.x = x;
            d.y = y;
            d.vx = (float) Math.cos(a) * sp;
            d.vy = (float) Math.sin(a) * sp - 120f;
            d.life = 0.5f + (float) Math.random() * 0.4f;
            d.size = 6f + (float) Math.random() * (r * 0.12f);
            d.colour = colour;
        }
    }

    private void animateEffects(float dt) {
        for (int i = halves.size() - 1; i >= 0; i--) {
            Half h = halves.at(i);
            h.life -= dt;
            h.vy += 1800f * dt;
            h.x += h.vx * dt;
            h.y += h.vy * dt;
            h.rot += h.rotSpeed * dt;
            if (h.life <= 0f || h.y > Stage.H + 200f) halves.freeAt(i);
        }
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.at(i);
            d.life -= dt;
            d.vy += 1400f * dt;
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            if (d.life <= 0f) drops.freeAt(i);
        }
        flash = Math.max(0f, flash - dt);
        for (int i = splats.size() - 1; i >= 0; i--) {
            Splat sp = splats.at(i);
            sp.life -= dt;
            if (sp.life <= 0f) splats.freeAt(i);
        }
        for (int i = slashes.size() - 1; i >= 0; i--) {
            Slash sl = slashes.at(i);
            sl.life -= dt;
            if (sl.life <= 0f) slashes.freeAt(i);
        }
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.at(i);
            p.life -= dt;
            p.y -= 70f * dt;
            if (p.life <= 0f) popups.freeAt(i);
        }
    }

    @Override
    public void draw(Canvas c) {
        boolean camOff = !env.camView();
        if (camOff) {
            aurora.draw(c, time, GameView.quality == GameView.LOW);
            if (hud.phase() != Hud.END && hud.phase() != Hud.RULES && pose != null) {
                for (int p = 0; p < players; p++) shadow.draw(c, pose.players[p], Theme.playerColour(p));
            }
        } else {
            Theme.drawBackdrop(c, camera, time);
        }
        if (hud.phase() == Hud.RULES) {
            hud.draw(c, 0, 0);
            return;
        }
        drawSplats(c);
        if (hud.phase() != Hud.END) {
            for (int p = 0; p < players; p++) drawFruit(c, logic[p]);
        }
        drawSlashes(c);
        for (int i = 0; i < halves.size(); i++) drawHalf(c, halves.at(i));
        for (int i = 0; i < drops.size(); i++) {
            Drop d = drops.at(i);
            paint.setColor(d.colour);
            paint.setAlpha((int) (255 * Math.min(1f, d.life * 2.5f)));
            c.drawCircle(d.x, d.y, d.size, paint);
        }
        if (hud.phase() != Hud.END) {
            for (int p = 0; p < players; p++) for (int h = 0; h < 2; h++) drawHand(c, hands[p][h], p);
        }
        for (int i = 0; i < popups.size(); i++) {
            Popup pu = popups.at(i);
            Theme.drawCentered(c, pu.text, pu.x + 3f, pu.y + 3f, 64f, 0x66000000);
            Theme.drawCentered(c, pu.text, pu.x, pu.y, 64f, pu.colour);
        }
        if (flash > 0f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFD64545);
            paint.setAlpha((int) (110 * flash / 0.35f));
            c.drawRect(0f, 0f, Stage.W, Stage.H, paint);
        }
        hud.draw(c, logic[0] != null ? logic[0].score() : 0, logic[1] != null ? logic[1].score() : 0);
    }

    private void drawFruit(Canvas c, FruitLogic l) {
        float k = 1f;
        for (int i = 0; i < l.activeCount(); i++) {
            FruitLogic.Fruit f = l.at(i);
            k = f.r / FruitArt.BODY_R;
            float half = FruitArt.SIZE / 2f * k;
            c.save();
            c.translate(f.x, f.y);
            c.rotate(f.spin);
            rect.set(-half, -half, half, half);
            c.drawBitmap(FruitArt.sprite(f.kind), null, rect, spritePaint);
            c.restore();
        }
    }

    private void drawHalf(Canvas c, Half h) {
        float k = h.r / FruitArt.BODY_R;
        float half = FruitArt.SIZE / 2f * k;
        c.save();
        c.translate(h.x, h.y);
        c.rotate(h.rot);
        rect.set(-half, -half, half, half);
        c.drawBitmap(FruitArt.half(h.kind, h.side), null, rect, spritePaint);
        c.restore();
    }

    private void drawSplats(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < splats.size(); i++) {
            Splat sp = splats.at(i);
            paint.setColor(sp.colour);
            paint.setAlpha((int) (150 * Math.min(1f, sp.life / 1.2f)));
            for (int j = 0; j < SPL_X.length; j++) {
                c.drawCircle(sp.x + SPL_X[j] * sp.size, sp.y + SPL_Y[j] * sp.size, SPL_R[j] * sp.size, paint);
            }
        }
        paint.setAlpha(255);
    }

    private void drawSlashes(Canvas c) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < slashes.size(); i++) {
            Slash sl = slashes.at(i);
            float a = sl.life / 0.28f, len = 190f * (1.2f - 0.4f * a);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha((int) (230 * a));
            paint.setStrokeWidth(6f + 22f * a);
            c.drawLine(sl.x - sl.dx * len, sl.y - sl.dy * len, sl.x + sl.dx * len, sl.y + sl.dy * len, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private void drawHand(Canvas c, HandTrack t, int player) {
        if (!t.valid()) return;
        int col = Theme.playerColour(player);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        int n = t.samples();
        long newest = t.sampleMs(0);
        for (int i = 0; i + 1 < n; i++) {
            long age = newest - t.sampleMs(i + 1);
            if (age > 300) break;
            float a = 1f - age / 300f;
            paint.setColor(col);
            paint.setAlpha((int) (200 * a));
            paint.setStrokeWidth(8f + 22f * a);
            c.drawLine(t.sampleX(i), t.sampleY(i), t.sampleX(i + 1), t.sampleY(i + 1), paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(t.x(), t.y(), 24f, paint);
        paint.setColor(col);
        c.drawCircle(t.x(), t.y(), 14f, paint);
    }
}
