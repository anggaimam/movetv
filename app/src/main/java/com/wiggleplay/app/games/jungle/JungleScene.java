package com.wiggleplay.app.games.jungle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.wiggleplay.app.core.GameView;
import com.wiggleplay.app.core.Pool;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.Random;

/**
 * Jungle Dash: a cheeky monkey runs through the jungle, and the child's own body controls it. Hop (a real jump) to leap
 * over logs, crouch to duck under vines, and grab bananas on the way. Crayon doodle art. One or two players (two get a
 * half of the screen each, the same course, their own score). Hitting something is a comic stumble and a short
 * slowdown; nothing is lost and the run never ends early. Round 70 s, then a calm "take a breath" moment, then stars.
 * The camera picture is not shown: the jungle is the picture, and the body is read from the head and shoulders only.
 */
public final class JungleScene implements Scene {

    private static final String GAME_ID = "jungle";
    private static final float ROUND_SECONDS = 70f;
    private static final float APEX_PX = 300f;
    private static final float SPRITE = 300f;
    private static final float MONKEY_X_1P = 340f, MONKEY_X_2P = 230f;
    private static final float DIST_2P = 0.5f;
    private static final float BANANA_Y = JungleArt.GROUND_Y - 70f;
    private static final float HIGH_Y = JungleArt.GROUND_Y - 250f;
    private static final float GOLD_Y = JungleArt.GROUND_Y - 255f;
    private static final String[] POPUP = {"+0", "+1", "+2", "+3", "+4", "+5"};
    private static final boolean[] RULES_SEEN = new boolean[4];

    // the row of rainforest trees behind the path (drawn art only)
    private static final int SLOTS = 40;
    private static final float TREE_PARALLAX = 0.7f, TREE_GROUND = 818f;
    private static final int MAX_BIRDS = 2;

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
    private final JungleRules rules = new JungleRules();
    private final JungleLogic[] logic = new JungleLogic[2];
    private final JumpDetect[] detect = {new JumpDetect(), new JumpDetect()};
    private final float[] runPhase = new float[2];
    private final Pool<Spark> sparks = new Pool<>(120, Spark::new);
    private final Pool<Popup> popups = new Pool<>(12, Popup::new);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Rect src = new Rect();

    private final int[] slotTree = new int[SLOTS], slotThing = new int[SLOTS];
    private final float[] slotX = new float[SLOTS], slotScale = new float[SLOTS];
    private float stripLen = 1f;
    private final float[] birdX = new float[MAX_BIRDS], birdY = new float[MAX_BIRDS], birdV = new float[MAX_BIRDS];
    private final boolean[] birdOn = new boolean[MAX_BIRDS];
    private float birdWait = 3f;
    private final Random rnd = new Random();

    private int level = 1, players = 1;
    private float time;
    private int sparkCap = 120;

    // per-viewport values, set at the start of drawing one viewport
    private float vx, vw, mx, dist;

    /**
     * Tells the game whether the rules screen has been shown already this session (tests use this).
     * @param seen true to skip the rules screen on the next entry
     */
    public static void setRulesSeen(boolean seen) {
        for (int i = 0; i < RULES_SEEN.length; i++) RULES_SEEN[i] = seen;
    }

    /**
     * Gives the drawn jungle art access to the app's assets (call once at start, before the game is played).
     * @param context any context
     */
    public static void initArt(android.content.Context context) {
        JungleArt.init(context);
    }

    /**
     * Creates the game.
     * @param env what the screens share
     */
    public JungleScene(TileMenu.Env env) {
        this.env = env;
        this.hud = new Hud(env, ROUND_SECONDS, "jungle");
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void enter(SceneArgs args) {
        env.leaveMenus();
        level = Math.max(1, Math.min(3, args.getInt("level", 1)));
        players = args.getInt("players", env.players) == 2 ? 2 : 1;
        JungleArt.prepare(level);
        hud.setRules(rules);
        beginRound(!RULES_SEEN[level]);
        RULES_SEEN[level] = true;
    }

    private void beginRound(boolean showRules) {
        for (int i = sparks.size() - 1; i >= 0; i--) sparks.freeAt(i);
        for (int i = popups.size() - 1; i >= 0; i--) popups.freeAt(i);
        long seed = System.nanoTime();
        for (int p = 0; p < 2; p++) {
            detect[p].reset();
            runPhase[p] = 0f;
            if (p >= players) {
                logic[p] = null;
                continue;
            }
            final int pl = p;
            logic[p] = new JungleLogic(level, seed, (type, index, points) -> happened(pl, type, index, points));
        }
        buildTreeRow(seed);
        for (int b = 0; b < MAX_BIRDS; b++) birdOn[b] = false;
        birdWait = 2f + rnd.nextFloat() * 4f;
        hud.start(players, showRules);
    }

    /** A random row of the 10 species, packed close, with a critter by some of them (the same in both halves). */
    private void buildTreeRow(long seed) {
        Random r = new Random(seed ^ 0x5DEECE66DL);
        float x = 0f;
        // deal the species like a shuffled deck, so all 10 appear before any repeats
        for (int k = 0; k < SLOTS; k++) slotTree[k] = k % JungleArt.TREES;
        for (int k0 = 0; k0 < SLOTS; k0 += JungleArt.TREES) {
            int n = Math.min(JungleArt.TREES, SLOTS - k0);
            for (int i = n - 1; i > 0; i--) {
                int j = r.nextInt(i + 1), t = slotTree[k0 + i];
                slotTree[k0 + i] = slotTree[k0 + j];
                slotTree[k0 + j] = t;
            }
            if (k0 > 0 && slotTree[k0] == slotTree[k0 - 1]) {   // no twin across the seam
                int t = slotTree[k0]; slotTree[k0] = slotTree[k0 + 1]; slotTree[k0 + 1] = t;
            }
        }
        for (int k = 0; k < SLOTS; k++) {
            slotScale[k] = 0.95f + r.nextFloat() * 0.25f;
            slotX[k] = x;
            slotThing[k] = r.nextFloat() < 0.4f ? r.nextInt(JungleArt.THINGS) : -1;
            x += 200f + r.nextFloat() * 70f;
        }
        stripLen = x;
    }

    /** Now and then a macaw flaps across the top of the screen. */
    private void animateBirds(float dt) {
        if (!JungleArt.fancy()) return;
        birdWait -= dt;
        if (birdWait <= 0f) {
            birdWait = 5f + rnd.nextFloat() * 9f;
            for (int b = 0; b < MAX_BIRDS; b++) {
                if (birdOn[b]) continue;
                birdOn[b] = true;
                birdX[b] = -170f;
                birdY[b] = 70f + rnd.nextFloat() * 150f;
                birdV[b] = 260f + rnd.nextFloat() * 160f;
                break;
            }
        }
        for (int b = 0; b < MAX_BIRDS; b++) {
            if (!birdOn[b]) continue;
            birdX[b] += birdV[b] * dt;
            birdY[b] += (float) Math.sin(time * 3f + b) * 12f * dt;
            if (birdX[b] > Stage.W + 170f) birdOn[b] = false;
        }
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
        // the detectors always listen, so the 2 calm seconds ("Stand tall!") are measured during the countdown
        for (int p = 0; p < players; p++) detect[p].update(f.pose.players[p], f.pose.timeMs);
        if (hud.running()) {
            for (int p = 0; p < players; p++) {
                JumpDetect d = detect[p];
                JungleLogic l = logic[p];
                if (d.consumeJump()) l.jump();
                if (d.jumping()) l.setJumpRise(d.peakRise());
                l.setDuck(d.ducking());
                l.update(dt);
                runPhase[p] += dt * 9f * (l.slow() ? JungleLogic.SLOW_RATE : 1f);
            }
            if (hud.readyToFinish(false)) {
                int s0 = JungleLogic.stars(logic[0].score(), logic[0].offered());
                int s1 = players == 2 ? JungleLogic.stars(logic[1].score(), logic[1].offered()) : 0;
                if (env.profile != null) env.profile.recordStars(GAME_ID, level, Math.max(s0, s1));
                hud.finish(s0, s1, logic[0].score(), players == 2 ? logic[1].score() : 0);
            }
        } else {
            for (int p = 0; p < players; p++) detect[p].consumeJump();
        }
        animateEffects(dt);
        animateBirds(dt);
    }

    // ---- what happens to the monkey ----

    private void happened(int player, int type, int index, int points) {
        switch (type) {
            case JungleLogic.EV_JUMP:
                env.sfx("jump");
                break;
            case JungleLogic.EV_DUCK:
                env.sfx("duck");
                break;
            case JungleLogic.EV_CLEAR:
                env.sfx("star", 1f);
                burst(player, 12, Theme.LEAF, 22f, 0f);
                popup(player, points, Theme.LEAF, -150f);
                break;
            case JungleLogic.EV_HIT:
                env.sfx("boing");
                burst(player, 10, 0xFFFFC107, 18f, -110f);
                break;
            case JungleLogic.EV_BANANA:
                env.sfx("coin");
                burst(player, 5, 0xFFFFD84D, 12f, itemY(logic[player].itemKind(index)) - JungleArt.GROUND_Y);
                popup(player, points, Theme.INK, itemY(logic[player].itemKind(index)) - JungleArt.GROUND_Y - 40f);
                break;
            case JungleLogic.EV_GOLDEN:
                env.sfx("star", 1.4f);
                burst(player, 18, 0xFFFFC107, 24f, GOLD_Y - JungleArt.GROUND_Y);
                popup(player, points, Theme.ORANGE, GOLD_Y - JungleArt.GROUND_Y - 50f);
                break;
            default:
                break;
        }
    }

    private static float itemY(int kind) {
        return kind == JungleLogic.GROUND_BANANA ? BANANA_Y : kind == JungleLogic.HIGH_BANANA ? HIGH_Y : GOLD_Y;
    }

    private void setViewport(int p) {
        vw = players == 2 ? Stage.W / 2f : Stage.W;
        vx = players == 2 ? p * vw : 0f;
        mx = vx + (players == 2 ? MONKEY_X_2P : MONKEY_X_1P);
        dist = players == 2 ? DIST_2P : 1f;
    }

    private void burst(int player, int n, int colour, float size, float dy) {
        setViewport(player);
        float x = mx + 40f, y = JungleArt.GROUND_Y + dy - 90f;
        if (GameView.quality == GameView.LOW) n = (n + 1) / 2;
        for (int i = 0; i < n; i++) {
            if (sparks.size() >= sparkCap) return;
            Spark s = sparks.obtain();
            if (s == null) return;
            float a = (float) (Math.random() * Math.PI * 2), sp = 120f + (float) Math.random() * 320f;
            s.x = x;
            s.y = y;
            s.vx = (float) Math.cos(a) * sp;
            s.vy = (float) Math.sin(a) * sp - 100f;
            s.life = 0.45f + (float) Math.random() * 0.35f;
            s.size = 5f + (float) Math.random() * size * 0.35f;
            s.colour = (i & 1) == 0 ? colour : 0xFFFFFFFF;
        }
    }

    private void popup(int player, int points, int colour, float dy) {
        setViewport(player);
        Popup pu = popups.obtain();
        if (pu == null) return;
        pu.x = mx + 40f;
        pu.y = JungleArt.GROUND_Y + dy - 110f;
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
            src.set(0, 0, 960, 540);
            rect.set(0f, 0f, Stage.W, Stage.H);
            c.drawBitmap(JungleArt.bg(level), src, rect, sprite);
            hud.draw(c, 0, 0);
            return;
        }
        for (int p = 0; p < players; p++) drawViewport(c, p);
        if (JungleArt.fancy()) {
            for (int b = 0; b < MAX_BIRDS; b++) {
                if (!birdOn[b]) continue;
                Bitmap bb = JungleArt.bird((int) (time * 10f) + b);
                rect.set(birdX[b] - 80f, birdY[b] - 55f, birdX[b] + 80f, birdY[b] + 55f);
                c.drawBitmap(bb, null, rect, sprite);
            }
        }
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
        for (int p = 0; p < players; p++) drawStandTall(c, p);
        if (hud.phase() == Hud.ENDING) {
            Theme.drawBanner(c, "Great running! Take a breath", Stage.W / 2f, 330f, 72f, 0xE6F3EBDD, Theme.INK);
        }
        hud.draw(c, logic[0] != null ? logic[0].score() : 0, logic[1] != null ? logic[1].score() : 0);
    }

    /** "Stand tall!" with a bar that fills while the body is measured; shown until the measuring is done. */
    private void drawStandTall(Canvas c, int p) {
        if (detect[p].calibrated()) return;
        int ph = hud.phase();
        if (ph != Hud.COUNTDOWN && ph != Hud.PLAYING) return;
        setViewport(p);
        float cx = vx + vw / 2f;
        Theme.drawBanner(c, "Stand tall!", cx, 250f, 64f, 0xE6F3EBDD, Theme.INK);
        paint.setColor(0x88F3EBDD);
        rect.set(cx - 200f, 330f, cx + 200f, 354f);
        c.drawRoundRect(rect, 12f, 12f, paint);
        paint.setColor(Theme.SAGE);
        rect.set(cx - 200f, 330f, cx - 200f + 400f * detect[p].calibrationProgress(), 354f);
        c.drawRoundRect(rect, 12f, 12f, paint);
    }

    private void drawTile(Canvas c, Bitmap b, float scroll, float parallax, float bottomY) {
        float tw = JungleArt.TILE_W * 2f, th = b.getHeight() * 2f;
        float x = -((scroll * parallax) % tw);
        while (x + tw < vx) x += tw;
        for (; x < vx + vw; x += tw) {
            rect.set(x, bottomY - th, x + tw + 1f, bottomY);
            c.drawBitmap(b, null, rect, sprite);
        }
    }

    private void drawViewport(Canvas c, int p) {
        setViewport(p);
        JungleLogic l = logic[p];
        boolean low = GameView.quality == GameView.LOW;
        float scroll = l.clock() * l.speed() * dist;
        c.save();
        c.clipRect(vx, 0f, vx + vw, Stage.H);
        if (players == 2) src.set(240, 0, 720, 540); else src.set(0, 0, 960, 540);
        rect.set(vx, 0f, vx + vw, Stage.H);
        c.drawBitmap(JungleArt.bg(level), src, rect, sprite);
        drawTile(c, JungleArt.far(level), scroll, 0.12f, 770f);
        if (!low) drawTile(c, JungleArt.mid(level), scroll, 0.40f, 840f);
        if (JungleArt.fancy()) drawTreeRow(c, scroll * TREE_PARALLAX);
        drawTile(c, JungleArt.ground(level), scroll, 1f, Stage.H);
        drawThings(c, l);
        drawMonkey(c, l, p);
        if (!low && JungleArt.tufts() != null) drawTile(c, JungleArt.tufts(), scroll, 1.25f, Stage.H);
        c.restore();
    }

    private void drawTreeRow(Canvas c, float scroll) {
        float off = scroll % stripLen;
        for (int k = 0; k < SLOTS; k++) {
            float x = vx + slotX[k] - off;
            if (x < vx - 400f) x += stripLen;
            if (x < vx - 400f || x > vx + vw + 400f) continue;
            float sc = slotScale[k];
            rect.set(x - JungleArt.TREE_BASE_X * sc, TREE_GROUND - JungleArt.TREE_BASE_Y * sc,
                    x + (JungleArt.TREE_W - JungleArt.TREE_BASE_X) * sc, TREE_GROUND + (JungleArt.TREE_H - JungleArt.TREE_BASE_Y) * sc);
            c.drawBitmap(JungleArt.tree(slotTree[k]), null, rect, sprite);
        }
        for (int k = 0; k < SLOTS; k++) {
            int kind = slotThing[k];
            if (kind < 0) continue;
            float x = vx + slotX[k] - off;
            if (x < vx - 400f) x += stripLen;
            if (x < vx - 400f || x > vx + vw + 400f) continue;
            float sc = slotScale[k];
            switch (kind) {
                case JungleArt.TOUCAN:
                    rect.set(x - 40f, TREE_GROUND - 420f * sc - 190f, x + 194f, TREE_GROUND - 420f * sc + 8f);
                    c.drawBitmap(JungleArt.thing(kind), null, rect, sprite);
                    break;
                case JungleArt.MORPHO: {
                    float fx = x + 70f + (float) Math.sin(time * 1.3f + k) * 40f;
                    float fy = TREE_GROUND - 360f * sc + (float) Math.cos(time * 2.1f + k) * 25f;
                    rect.set(fx - 60f, fy - 48f, fx + 60f, fy + 48f);
                    c.drawBitmap(JungleArt.morpho((int) (time * 8f) + k), null, rect, sprite);
                    break;
                }
                case JungleArt.FROG:
                    rect.set(x + 40f, 600f, x + 300f, 800f);
                    c.drawBitmap(JungleArt.thing(kind), null, rect, sprite);
                    break;
                default:   // fern, mushrooms: at the foot of the tree
                    rect.set(x - 150f, 600f, x + 150f, 800f);
                    c.drawBitmap(JungleArt.thing(kind), null, rect, sprite);
                    break;
            }
        }
    }

    private void blit(Canvas c, Bitmap b, float x, float y, float hw, float hh) {
        if (x + hw < vx || x - hw > vx + vw) return;
        rect.set(x - hw, y - hh, x + hw, y + hh);
        c.drawBitmap(b, null, rect, sprite);
    }

    /** Obstacles, warnings, bananas and coconuts, from the far ones to the near ones. */
    private void drawThings(Canvas c, JungleLogic l) {
        float tau = l.clock(), speed = l.speed() * dist;
        for (int i = l.itemCount() - 1; i >= 0; i--) {
            if (l.itemTaken(i)) continue;
            float x = mx + (l.itemTime(i) - tau) * speed;
            if (x < vx - 120f || x > vx + vw + 120f) continue;
            int k = l.itemKind(i);
            float bob = (float) Math.sin(time * 4f + l.itemTime(i) * 3f) * 7f;
            if (k == JungleLogic.GOLDEN) {
                float pulse = 1f + 0.06f * (float) Math.sin(time * 6f);
                blit(c, JungleArt.golden(), x, GOLD_Y + bob, 62f * pulse, 62f * pulse);
            } else {
                blit(c, JungleArt.banana(), x, (k == JungleLogic.HIGH_BANANA ? HIGH_Y : BANANA_Y) + bob, 38f, 38f);
            }
        }
        for (int i = l.obstacleCount() - 1; i >= 0; i--) {
            float dt = l.obstacleTime(i) - tau;
            float x = mx + dt * speed;
            if (x < vx - 260f || x > vx + vw + 260f) continue;
            int kind = l.obstacleKind(i);
            boolean pending = l.obstacleState(i) == JungleLogic.PENDING;
            if (pending && dt <= l.warn()) drawWarning(c, x, kind, dt);
            switch (kind) {
                case JungleLogic.VINE: {
                    float sway = (float) Math.sin(time * 1.7f + i) * 2.4f;
                    c.save();
                    c.rotate(sway, x, -30f);
                    rect.set(x - 100f, -30f, x + 100f, 610f);
                    if (x + 100f >= vx && x - 100f <= vx + vw) c.drawBitmap(JungleArt.vine(), null, rect, sprite);
                    c.restore();
                    break;
                }
                case JungleLogic.DOUBLE_LOG:
                    blit(c, JungleArt.doubleLog(), x, JungleArt.GROUND_Y + 10f - 75f, 200f, 75f);
                    break;
                default:
                    blit(c, JungleArt.log(), x, JungleArt.GROUND_Y + 10f - 40f, 150f, 40f);
                    break;
            }
        }
    }

    /** A red patch on the ground and a bubble with an arrow: up for logs, down for vines. It bounces faster as it nears. */
    private void drawWarning(Canvas c, float x, int kind, float timeLeft) {
        paint.setColor(0x55E8504A);
        rect.set(x - 150f, JungleArt.GROUND_Y + 14f, x + 150f, JungleArt.GROUND_Y + 62f);
        if (x + 150f >= vx && x - 150f <= vx + vw) c.drawOval(rect, paint);
        float near = timeLeft < 0.6f ? 1f : 0f;
        float bounce = (float) Math.sin(time * (8f + 6f * near)) * (8f + 6f * near);
        float grow = 1f + 0.12f * near;
        boolean up = kind != JungleLogic.VINE;
        float y = (up ? JungleArt.GROUND_Y - 230f : JungleArt.GROUND_Y - 120f) + bounce;
        blit(c, up ? JungleArt.markUp() : JungleArt.markDown(), x, y, 62f * grow, 62f * grow);
    }

    private void drawMonkey(Canvas c, JungleLogic l, int p) {
        float height = l.height() * APEX_PX;
        Bitmap b;
        if (l.stumble() > 0f) {
            float s = l.stumble();
            b = s > 0.80f ? JungleArt.stumble(0) : s > 0.60f ? JungleArt.stumble(1) : JungleArt.dizzy((int) (time * 8f));
        } else if (l.airborne()) {
            float jp = l.jumpProgress();
            b = JungleArt.jump(jp < 0.12f ? 0 : jp > 0.88f ? 2 : 1);
        } else if (l.ducking()) {
            b = JungleArt.duck((int) (time * 8f));
        } else if (hud.phase() == Hud.PLAYING || hud.phase() == Hud.ENDING) {
            b = JungleArt.run((int) runPhase[p]);
        } else {
            b = JungleArt.run(0);
        }
        // the shadow stays on the ground and shrinks as the monkey rises
        float sh = 1f - 0.4f * Math.min(1f, l.height());
        blit(c, JungleArt.shadow(), mx + 10f, JungleArt.GROUND_Y + 20f, 120f * sh, 28f * sh);
        float top = JungleArt.GROUND_Y - height - JungleArt.FEET * SPRITE;
        float tilt = l.airborne() ? (l.jumpProgress() - 0.5f) * 16f : 0f;
        if (tilt != 0f) {
            c.save();
            c.rotate(tilt, mx, top + SPRITE * 0.55f);
        }
        rect.set(mx - SPRITE / 2f, top, mx + SPRITE / 2f, top + SPRITE);
        c.drawBitmap(b, null, rect, sprite);
        if (tilt != 0f) c.restore();
    }
}
