package com.wiggleplay.app.games.bubbles;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.wiggleplay.app.core.GameView;
import com.wiggleplay.app.core.Pool;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.games.fruit.HandTrack;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

/**
 * Bubble Burst: the camera picture shows through blue-green water; bubbles float up near the child and each hand
 * is a little fish that pops them. One or two players (two split the screen in halves). Nothing is ever lost.
 * Medium: blue bubbles for the left hand, red for the right, golden worth 5. Hard: purple needs both hands,
 * spiky decoys are skipped.
 */
public final class BubbleScene implements Scene {

    private static final String GAME_ID = "bubbles";
    private static final float ROUND_SECONDS = 60f;
    private static final String[] POPUP = {"+0", "+1", "+2", "+3", "+4", "+5"};
    private static boolean rulesSeen;

    private static final class Ring {
        float x, y, r, life;
        int kind;
    }

    private static final class Bit {
        float x, y, vx, vy, life, size;
    }

    private static final class Popup {
        float x, y, life;
        int colour;
        String text;
    }

    private final TileMenu.Env env;
    private final Hud hud;
    private final Pool<Ring> rings = new Pool<>(12, Ring::new);
    private final Pool<Bit> bits = new Pool<>(120, Bit::new);
    private final Pool<Popup> popups = new Pool<>(10, Popup::new);
    private final HandTrack[][] hands = new HandTrack[2][2];
    private final boolean[][] faceRight = new boolean[2][2];
    private final BubbleLogic[] logic = new BubbleLogic[2];
    private BubbleArt.Scenery scenery = new BubbleArt.Scenery(1L);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BubbleRules rules;
    private android.graphics.Bitmap camera;
    private int level = 1, players = 1;
    private float time;

    /**
     * Tells the game whether the rules screen has been shown already (tests use this).
     * @param seen true to skip the rules screen on the next entry
     */
    public static void setRulesSeen(boolean seen) {
        rulesSeen = seen;
    }

    /**
     * Creates the game.
     * @param env what the screens share
     */
    public BubbleScene(TileMenu.Env env) {
        this.env = env;
        this.hud = new Hud(env, ROUND_SECONDS, "sky");
        for (int p = 0; p < 2; p++) for (int h = 0; h < 2; h++) {
            hands[p][h] = new HandTrack(14f);
            faceRight[p][h] = h == 1;
        }
    }

    @Override
    public void enter(SceneArgs args) {
        env.leaveMenus();
        level = Math.max(1, Math.min(3, args.getInt("level", 1)));
        players = args.getInt("players", env.players) == 2 ? 2 : 1;
        scenery = new BubbleArt.Scenery(System.nanoTime());   // a different sea every visit
        rules = new BubbleRules(level);
        hud.setRules(rules);
        beginRound(!rulesSeen);
        rulesSeen = true;
    }

    private void beginRound(boolean showRules) {
        for (int i = rings.size() - 1; i >= 0; i--) rings.freeAt(i);
        for (int i = bits.size() - 1; i >= 0; i--) bits.freeAt(i);
        for (int i = popups.size() - 1; i >= 0; i--) popups.freeAt(i);
        long seed = System.nanoTime();
        for (int p = 0; p < 2; p++) {
            if (p >= players) {
                logic[p] = null;
                continue;
            }
            float x0 = players == 2 ? p * Stage.W / 2f : 0f;
            float x1 = players == 2 ? (p + 1) * Stage.W / 2f : Stage.W;
            final int pl = p;
            logic[p] = new BubbleLogic(level, seed + p * 7919L, x0, x1, new BubbleLogic.Listener() {
                @Override
                public void onPop(BubbleLogic.Bubble b, int points) {
                    popped(pl, b, points);
                }

                @Override
                public void onWrongHand(BubbleLogic.Bubble b) {
                    env.sfx("bonk", 1.4f);
                }

                @Override
                public void onDecoy(BubbleLogic.Bubble b, int lost) {
                    env.sfx("bonk", 0.8f);
                    ring(b, 0.8f);
                    Popup pu = popups.obtain();
                    if (pu != null) {
                        pu.x = b.x;
                        pu.y = b.y - b.r;
                        pu.life = 1.1f;
                        pu.text = lost > 0 ? "-" + lost : "Oops!";
                        pu.colour = 0xFFFF7A70;
                    }
                }

                @Override
                public void onFade(BubbleLogic.Bubble b) { }
            });
        }
        hud.start(players, showRules);
    }

    @Override
    public void exit() {
        BubbleArt.unload();
        if (env.music != null) env.music.stop(300);
    }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        scenery.update(dt);
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
        if (hud.phase() == Hud.END) {
            animateEffects(dt);
            return;
        }
        for (int p = 0; p < players; p++) {
            PlayerPose pp = f.pose.players[p];
            for (int h = 0; h < 2; h++) {
                Keypoint k = pp.kps[h == 0 ? Kp.L_WRIST : Kp.R_WRIST];
                hands[p][h].update(dt, pp.present && k.s >= 0.3f, k.x, k.y, f.nowMs);
                float dx = hands[p][h].dirX();
                if (hands[p][h].valid() && Math.abs(dx) > 0.5f) faceRight[p][h] = dx > 0f;
            }
            Keypoint ls = pp.kps[Kp.L_SHOULDER], rs = pp.kps[Kp.R_SHOULDER], nose = pp.kps[Kp.NOSE];
            if (pp.present && ls.s >= 0.3f && rs.s >= 0.3f) {
                float sw = (float) Math.hypot(ls.x - rs.x, ls.y - rs.y);
                boolean nz = nose.s >= 0.3f;
                logic[p].setReach((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f, sw, nz ? nose.x : Float.NaN, nose.y);
            }
        }
        if (hud.running()) {
            for (int p = 0; p < players; p++) logic[p].update(dt, f.nowMs, hands[p][0], hands[p][1], hud.spawning());
            animateEffects(dt);
            if (hud.readyToFinish(allEmpty())) {
                int s0 = BubbleLogic.stars(logic[0].score(), logic[0].offered());
                int s1 = players == 2 ? BubbleLogic.stars(logic[1].score(), logic[1].offered()) : 0;
                if (env.profile != null) env.profile.recordStars(GAME_ID, level, Math.max(s0, s1));
                hud.finish(s0, s1, logic[0].score(), players == 2 ? logic[1].score() : 0);
            }
        }
    }

    private boolean allEmpty() {
        for (int p = 0; p < players; p++) if (logic[p].activeCount() > 0) return false;
        return true;
    }

    private void ring(BubbleLogic.Bubble b, float life) {
        Ring r = rings.obtain();
        if (r == null) return;
        r.x = b.x;
        r.y = b.y;
        r.r = b.r;
        r.kind = b.kind;
        r.life = life;
    }

    private void popped(int player, BubbleLogic.Bubble b, int points) {
        env.sfx(b.kind == BubbleLogic.GOLD ? "star" : "splat", 1.5f + (b.serial % 4) * 0.1f);
        ring(b, 0.45f);
        int n = GameView.quality == GameView.LOW ? 5 : 9;
        for (int i = 0; i < n; i++) {
            Bit s = bits.obtain();
            if (s == null) break;
            float a = (float) (Math.random() * Math.PI * 2), sp = 120f + (float) Math.random() * 220f;
            s.x = b.x + (float) Math.cos(a) * b.r * 0.8f;
            s.y = b.y + (float) Math.sin(a) * b.r * 0.8f;
            s.vx = (float) Math.cos(a) * sp;
            s.vy = (float) Math.sin(a) * sp - 40f;
            s.life = 0.45f + (float) Math.random() * 0.3f;
            s.size = 5f + (float) Math.random() * 7f;
        }
        Popup pu = popups.obtain();
        if (pu != null) {
            pu.x = b.x;
            pu.y = b.y - b.r;
            pu.life = 0.9f;
            pu.text = POPUP[Math.max(0, Math.min(POPUP.length - 1, points))];
            pu.colour = points >= 3 ? 0xFFFFE066 : 0xFFFFFFFF;
        }
    }

    private void animateEffects(float dt) {
        for (int i = rings.size() - 1; i >= 0; i--) {
            Ring r = rings.at(i);
            r.life -= dt;
            if (r.life <= 0f) rings.freeAt(i);
        }
        for (int i = bits.size() - 1; i >= 0; i--) {
            Bit s = bits.at(i);
            s.life -= dt;
            s.vx *= 0.96f;
            s.vy = s.vy * 0.96f - 60f * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            if (s.life <= 0f) bits.freeAt(i);
        }
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup p = popups.at(i);
            p.life -= dt;
            p.y -= 60f * dt;
            if (p.life <= 0f) popups.freeAt(i);
        }
    }

    @Override
    public void draw(Canvas c) {
        BubbleArt.water(c, camera);
        scenery.draw(c, GameView.quality == GameView.LOW);
        if (hud.phase() == Hud.RULES) {
            hud.draw(c, 0, 0);
            return;
        }
        if (hud.phase() != Hud.END) {
            for (int p = 0; p < players; p++) drawBubbles(c, logic[p]);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < bits.size(); i++) {
            Bit s = bits.at(i);
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha((int) (200 * Math.min(1f, s.life * 2.5f)));
            c.drawCircle(s.x, s.y, s.size, paint);
        }
        paint.setAlpha(255);
        for (int i = 0; i < rings.size(); i++) {
            Ring r = rings.at(i);
            float total = r.kind == BubbleLogic.DECOY ? 0.8f : 0.45f;
            BubbleArt.drawPop(c, r.x, r.y, r.r, 1f - r.life / total, r.kind);
        }
        if (hud.phase() != Hud.END) {
            for (int p = 0; p < players; p++) for (int h = 0; h < 2; h++) drawFish(c, p, h);
        }
        for (int i = 0; i < popups.size(); i++) {
            Popup pu = popups.at(i);
            Theme.drawCentered(c, pu.text, pu.x + 3f, pu.y + 3f, 64f, 0x66000000);
            Theme.drawCentered(c, pu.text, pu.x, pu.y, 64f, pu.colour);
        }
        hud.draw(c, logic[0] != null ? logic[0].score() : 0, logic[1] != null ? logic[1].score() : 0);
    }

    private void drawBubbles(Canvas c, BubbleLogic l) {
        for (int i = 0; i < l.activeCount(); i++) {
            BubbleLogic.Bubble b = l.at(i);
            float grow = Math.min(1f, b.age / 0.25f);
            float scale = 0.3f + 0.7f * (1f - (1f - grow) * (1f - grow));
            float left = b.life - b.age;
            float alpha = left < 0.5f ? Math.max(0f, left / 0.5f) : 1f;
            float wob = b.wobble > 0f ? (float) Math.sin(b.wobble * 50f) * 14f * (b.wobble / 0.5f) : 0f;
            BubbleArt.drawBubble(c, b.kind, b.x + wob, b.y, b.r * scale, alpha, time + b.serial, b.look);
        }
    }

    private void drawFish(Canvas c, int player, int hand) {
        HandTrack t = hands[player][hand];
        if (!t.valid()) return;
        int fin = level == 1 ? BubbleArt.FIN_NEUTRAL : (hand == 0 ? BubbleArt.FIN_BLUE : BubbleArt.FIN_RED);
        BubbleArt.drawFish(c, t.x(), t.y(), faceRight[player][hand], Theme.playerColour(player), fin, time + hand, 1f);
    }
}
