package com.wiggleplay.app.games.bowling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;

import com.wiggleplay.app.core.Input;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Bowling, part 1: one to four bowlers take turns on a neon lane (five frames, two rolls each). The ball is rolled
 * with the remote for now: hold OK and let go (the longer the hold, the harder the ball); the ball's place on the lane
 * follows where the player stands (or a marker that sweeps left and right when nobody is seen, or the left and right
 * keys). If nobody rolls for 12 seconds a gentle ball rolls by itself, so nobody is ever stuck. Swinging an arm to roll
 * comes in part 2. Gutter balls are funny, never sad.
 */
public final class BowlingScene implements Scene {

    private static final String GAME_ID = "bowling";
    private static final String[] NAMES = {"Bunny", "Fox", "Bear", "Cat"};
    private static final String[] PIN_TEXT = {"Gutter ball!", "1 pin!", "2 pins!", "3 pins!", "4 pins!", "5 pins!",
            "6 pins!", "7 pins!", "8 pins!", "9 pins!", "10 pins!"};
    private static final String[] TURN_TEXT = {"Bunny's turn!", "Fox's turn!", "Bear's turn!", "Cat's turn!"};
    private static final String[] FRAME_TEXT = {"Frame 1", "Frame 2", "Frame 3", "Frame 4", "Frame 5"};

    private static final int PICK = 0, INTRO = 1, AIM = 2, ROLL = 3, RESULT = 4, END = 5;
    private static final float INTRO_SECONDS = 1.4f, RESULT_SECONDS = 2.0f;
    private static final float CHARGE_SECONDS = 1.2f, MIN_POWER = 0.3f, HINT_AFTER = 6f, AUTO_ROLL_AFTER = 12f;

    private final TileMenu.Env env;
    private final TileMenu menu;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    private BowlingPhysics physics;
    private BowlingScore score;
    private int level = 1, players = 1, phase = PICK;
    private Bitmap camera;
    private float time, phaseT, acc;

    // aiming and charging
    private float aimX, sweepT, manualUntil, idle, chargeT;
    private boolean charging;
    // rolling
    private int rollPlayer, lastKnocks, resultKind, resultPins;
    private float rollSoundT;
    private boolean pinsHeard;
    private float flash;

    // scoreboard texts, rebuilt after every roll
    private final String[][] cellText = new String[4][BowlingScore.FRAMES];
    private final String[] totalText = new String[4];

    /**
     * Creates the game.
     * @param env what the screens share
     */
    public BowlingScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void enter(SceneArgs args) {
        level = Math.max(1, Math.min(3, args.getInt("level", 1)));
        // bowling tracks one person at a time, whatever the home screen chose
        if (env.hub != null) env.hub.setMode(1);
        env.menuMusic();   // the calm menu music keeps playing between turns
        showPicker();
    }

    @Override
    public void exit() {
        BowlingArt.unload();
        if (env.hub != null) env.hub.setMode(env.players);
    }

    // ---- who is playing ----

    private void showPicker() {
        phase = PICK;
        phaseT = 0f;
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        tiles.add(RoundButton.settings());
        float w = 360f, h = 420f, gap = 40f, x0 = (Stage.W - 4f * w - 3f * gap) / 2f;
        int[] colours = {Theme.MUSTARD, Theme.ORANGE, Theme.PINK, Theme.BLUE};
        for (int i = 0; i < 4; i++) {
            TileMenu.Tile t = new TileMenu.Tile("p" + (i + 1), String.valueOf(i + 1), i == 0 ? "player" : "players",
                    colours[i], null, x0 + i * (w + gap), 380f, w, h);
            tiles.add(t);
        }
        menu.setTiles(tiles, "p1");
    }

    private void startGame(int n) {
        players = n;
        score = new BowlingScore(n);
        physics = new BowlingPhysics(level);
        for (int p = 0; p < 4; p++) buildScoreText(p);
        beginTurn();
    }

    private void beginTurn() {
        phase = INTRO;
        phaseT = 0f;
        charging = false;
        chargeT = 0f;
        idle = 0f;
        aimX = 0f;
        sweepT = 0f;
        flash = 0f;
        if (env.hub != null) env.hub.releaseLock();
        env.say(TURN_TEXT[score.player()]);
    }

    // ---- update ----

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        phaseT += dt;
        camera = f.camera;
        if (flash > 0f) flash = Math.max(0f, flash - dt * 1.6f);
        Input in = f.input;
        if (in.pressed(KeyEvent.KEYCODE_BACK)) {
            env.sfx("back");
            leave();
            return;
        }
        switch (phase) {
            case PICK:
                updatePicker(dt, f);
                break;
            case INTRO:
                if (phaseT >= INTRO_SECONDS) {
                    phase = AIM;
                    phaseT = 0f;
                    idle = 0f;
                }
                break;
            case AIM:
                updateAim(dt, f);
                break;
            case ROLL:
                updateRoll(dt);
                break;
            case RESULT:
                if (phaseT >= RESULT_SECONDS) nextAfterResult();
                break;
            default:
                updateEnd(dt, f);
                break;
        }
    }

    private void leave() {
        env.go("levelselect", SceneArgs.of("gameId", GAME_ID));
    }

    private void updatePicker(float dt, Frame f) {
        String sel = menu.update(dt, f);
        if (sel == null) return;
        if ("back".equals(sel)) {
            env.sfx("back");
            leave();
        } else if ("settings".equals(sel)) {
            env.sfx("select");
            if (env.settings != null) env.settings.open();
        } else if (sel.length() == 2 && sel.charAt(0) == 'p') {
            env.sfx("select");
            startGame(sel.charAt(1) - '0');
        }
    }

    private static boolean okPressed(Input in) {
        return in.pressed(KeyEvent.KEYCODE_DPAD_CENTER) || in.pressed(KeyEvent.KEYCODE_ENTER) || in.touchPressed();
    }

    private static boolean okHeld(Input in) {
        return in.held(KeyEvent.KEYCODE_DPAD_CENTER) || in.held(KeyEvent.KEYCODE_ENTER) || in.touchHeld();
    }

    private void updateAim(float dt, Frame f) {
        Input in = f.input;
        idle += dt;
        // where the ball starts: the player's place on the stage, or the remote's left and right, or a sweeping marker
        float body = bodyAim(f.pose.players[0]);
        if (!Float.isNaN(body)) {
            aimX += (body - aimX) * Math.min(1f, dt * 8f);
            manualUntil = 0f;
        } else if (in.held(KeyEvent.KEYCODE_DPAD_LEFT) || in.held(KeyEvent.KEYCODE_DPAD_RIGHT)) {
            float dir = in.held(KeyEvent.KEYCODE_DPAD_RIGHT) ? 1f : -1f;
            aimX = Math.max(-0.4f, Math.min(0.4f, aimX + dir * 0.5f * dt));
            manualUntil = time + 2.5f;
            sweepT = (float) Math.asin(aimX / 0.4f) / 1.3f;
        } else if (time >= manualUntil) {
            sweepT += dt;
            aimX = 0.4f * (float) Math.sin(sweepT * 1.3f);
        }
        if (okPressed(in) && !charging) {
            charging = true;
            chargeT = 0f;
            idle = 0f;
        }
        if (charging) {
            chargeT += dt;
            idle = 0f;
            boolean stillHeld = okHeld(in);
            if (!stillHeld || in.released(KeyEvent.KEYCODE_DPAD_CENTER) || in.released(KeyEvent.KEYCODE_ENTER)
                    || in.touchReleased()) {
                throwBall(Math.max(MIN_POWER, Math.min(1f, chargeT / CHARGE_SECONDS)));
                return;
            }
        } else if (idle >= AUTO_ROLL_AFTER) {
            throwBall(0.35f); // nobody rolled: a gentle ball goes by itself
        }
    }

    /** Where the player stands across the middle third of the stage, as a place on the lane (-0.4..0.4), or NaN. */
    private static float bodyAim(PlayerPose p) {
        if (p == null || !p.present) return Float.NaN;
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER], nose = p.kps[Kp.NOSE];
        float x;
        if (ls.s >= 0.3f && rs.s >= 0.3f) x = (ls.x + rs.x) / 2f;
        else if (nose.s >= 0.3f) x = nose.x;
        else return Float.NaN;
        return Math.max(-0.4f, Math.min(0.4f, (x - Stage.W / 2f) / 800f));
    }

    private void throwBall(float power) {
        charging = false;
        rollPlayer = score.player();
        physics.roll(aimX, 0f, power, 0f);
        acc = 0f;
        lastKnocks = 0;
        pinsHeard = false;
        rollSoundT = 0f;
        phase = ROLL;
        phaseT = 0f;
        env.sfx("whoosh", 0.8f);
    }

    private void updateRoll(float dt) {
        acc += Math.min(dt, 0.05f);
        int guard = 0;
        while (acc >= BowlingPhysics.STEP && guard++ < 12) {
            physics.step();
            acc -= BowlingPhysics.STEP;
        }
        if (physics.ballOnLane() && !physics.inGutter()) {
            rollSoundT -= dt;
            if (rollSoundT <= 0f) {
                rollSoundT = 0.55f;
                env.sfx("roll");
            }
        }
        if (!pinsHeard && physics.knocks() > 0) {
            pinsHeard = true;
            env.sfx("pins");
        }
        boolean over = (physics.time() > 0.5f && physics.settled()) || physics.time() > 9f;
        if (over) finishRoll();
    }

    private void finishRoll() {
        resultPins = physics.pinsDown();
        resultKind = score.record(resultPins);
        for (int p = 0; p < players; p++) buildScoreText(p);
        phase = RESULT;
        phaseT = 0f;
        switch (resultKind) {
            case BowlingScore.STRIKE:
                env.sfx("strike");
                env.say("Strike!");
                flash = 1f;
                break;
            case BowlingScore.SPARE:
                env.sfx("star");
                env.say("Spare!");
                break;
            case BowlingScore.GUTTER:
                env.sfx("boing");
                break;
            default:
                if (resultPins >= 7) env.sfx("coin");
                break;
        }
    }

    private void nextAfterResult() {
        if (score.finished()) {
            finishGame();
            return;
        }
        if (score.roll() == 1) physics.sweep();   // the same bowler's second roll: the fallen pins are swept away
        else physics.resetPins();                  // a new frame or a new bowler
        beginTurn();
    }

    // ---- the end ----

    private int bestStars;
    private String endTitle = "";

    private void finishGame() {
        phase = END;
        phaseT = 0f;
        int best = 0;
        for (int p = 0; p < players; p++) best = Math.max(best, score.totalPins(p));
        bestStars = BowlingScore.stars(best, level);
        if (env.profile != null) env.profile.recordStars(GAME_ID, level, bestStars);
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.home());
        float w = 420f, h = 190f, gap = 50f, x0 = (Stage.W - 2f * w - gap) / 2f;
        tiles.add(new TileMenu.Tile("again", "Play again", null, Theme.MUSTARD, null, x0, 800f, w, h));
        tiles.add(new TileMenu.Tile("who", "Who's playing?", null, Theme.BLUE, null, x0 + w + gap, 800f, w, h));
        menu.setTiles(tiles, "again");
        endTitle = players == 1 ? "Great bowling!" : NAMES[score.leader()] + " wins!";
        env.sfx("cheer");
        env.say(endTitle);
    }

    private void updateEnd(float dt, Frame f) {
        String sel = menu.update(dt, f);
        if (sel == null) return;
        env.sfx("select");
        if ("again".equals(sel)) startGame(players);
        else if ("who".equals(sel)) showPicker();
        else env.go("gameselect", SceneArgs.EMPTY);
    }

    // ---- scoreboard texts ----

    private void buildScoreText(int p) {
        for (int fr = 0; fr < BowlingScore.FRAMES; fr++) {
            if (score == null || p >= players) {
                cellText[p][fr] = "";
                continue;
            }
            int pts = score.framePoints(p, fr);
            int kind = score.frameKind(p, fr);
            cellText[p][fr] = pts < 0 ? "" : kind == BowlingScore.STRIKE ? "X" : kind == BowlingScore.SPARE ? "/"
                    : String.valueOf(pts);
        }
        totalText[p] = score == null || p >= players ? "" : String.valueOf(score.total(p));
    }

    // ---- drawing ----

    @Override
    public void draw(Canvas c) {
        if (phase == PICK) {
            Theme.drawBackdrop(c, camera, time);
            Theme.drawBanner(c, "Who's playing?", Stage.W / 2f, 160f, 80f, 0xE6F3EBDD, Theme.INK);
            menu.draw(c);
            return;
        }
        if (phase == END) {
            Theme.drawBackdrop(c, camera, time);
            drawEnd(c);
            return;
        }
        c.drawBitmap(BowlingArt.lane(), 0f, 0f, null);
        drawPinsAndBall(c);
        drawAimUi(c);
        drawScoreboard(c);
        drawBanners(c);
        if (flash > 0f) {
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha((int) (110 * flash));
            c.drawRect(0f, 0f, Stage.W, Stage.H, paint);
            paint.setAlpha(255);
        }
    }

    private void drawPinsAndBall(Canvas c) {
        boolean rolling = phase == ROLL || (phase == RESULT && physics.ballOnLane());
        float by = physics.ballY();
        // pins behind the ball first, then the ball, then pins in front of it
        for (int i = BowlingPhysics.PINS - 1; i >= 0; i--) {
            if (!rolling || physics.pinY(i) > by) drawPin(c, i);
        }
        if (phase == AIM || phase == INTRO || phase == ROLL) drawBall(c, phase == ROLL);
        if (rolling) {
            for (int i = BowlingPhysics.PINS - 1; i >= 0; i--) if (physics.pinY(i) <= by) drawPin(c, i);
        }
    }

    private void drawPin(Canvas c, int i) {
        if (!physics.onLane(i)) return;
        float py = physics.pinY(i);
        float s = BowlingArt.scale(py);
        float hgt = 0.42f * BowlingArt.S0 * s, wid = hgt * BowlingArt.PIN_W / BowlingArt.PIN_H;
        float cx = BowlingArt.sx(physics.pinX(i), py), base = BowlingArt.sy(py) + hgt * 0.04f;
        boolean down = physics.isDown(i);
        int alpha = 255;
        c.save();
        if (down) {
            float t = Math.min(1f, physics.fallTime(i) * 4f);
            float side = physics.pushSide(i);
            if (Math.abs(side) < 0.15f) side = (i & 1) == 0 ? 0.4f : -0.4f;
            c.rotate(85f * t * Math.signum(side), cx, base);
            if (phase == RESULT) alpha = (int) (255 * Math.max(0f, 1f - phaseT / 1.0f));
            else alpha = 200;
        }
        rect.set(cx - wid / 2f, base - hgt, cx + wid / 2f, base);
        sprite.setAlpha(alpha);
        if (alpha > 0) c.drawBitmap(BowlingArt.pin(), null, rect, sprite);
        sprite.setAlpha(255);
        c.restore();
    }

    private void drawBall(Canvas c, boolean rolling) {
        float x = rolling ? physics.ballX() : aimX, y = rolling ? physics.ballY() : 0f;
        float s = BowlingArt.scale(y);
        float r = BowlingPhysics.BALL_R * BowlingArt.S0 * s;
        float cx = BowlingArt.sx(x, y), cy = BowlingArt.sy(y) - r * 0.9f;
        // a soft shadow on the lane
        paint.setColor(0x66000000);
        rect.set(cx - r, BowlingArt.sy(y) - r * 0.25f, cx + r, BowlingArt.sy(y) + r * 0.2f);
        c.drawOval(rect, paint);
        rect.set(cx - r, cy - r, cx + r, cy + r);
        c.drawBitmap(BowlingArt.ball(rolling ? rollPlayer : score.player()), null, rect, sprite);
    }

    private void drawAimUi(Canvas c) {
        if (phase != AIM) return;
        // arrow on the foul line where the ball will start
        float ax = BowlingArt.sx(aimX, 0f), ay = BowlingArt.sy(0f) + 10f;
        paint.setColor(BowlingArt.COLOURS[score.player()]);
        float pulse = 1f + 0.12f * (float) Math.sin(time * 8f);
        c.save();
        c.translate(ax, ay);
        c.scale(pulse, pulse);
        paint.setStyle(Paint.Style.FILL);
        android.graphics.Path p = tri;
        p.reset();
        p.moveTo(0f, -26f);
        p.lineTo(-30f, 22f);
        p.lineTo(30f, 22f);
        p.close();
        c.drawPath(p, paint);
        c.restore();
        // power bar
        if (charging) {
            float pw = Math.min(1f, chargeT / CHARGE_SECONDS);
            float w = 560f, x0 = BowlingArt.CX - w / 2f, y0 = 1020f;
            paint.setColor(0xB0140B2E);
            rect.set(x0 - 8f, y0 - 8f, x0 + w + 8f, y0 + 40f);
            c.drawRoundRect(rect, 24f, 24f, paint);
            paint.setColor(pw >= 1f ? 0xFFFF6EC7 : 0xFFFFD84D);
            rect.set(x0, y0, x0 + w * pw, y0 + 32f);
            c.drawRoundRect(rect, 16f, 16f, paint);
        } else if (idle >= HINT_AFTER) {
            Theme.drawBanner(c, "Hold OK, then let go!", BowlingArt.CX, 1000f, 56f, 0xE0140B2E, 0xFFFFD84D);
        }
    }

    private final android.graphics.Path tri = new android.graphics.Path();

    private void drawBanners(Canvas c) {
        if (phase == INTRO) {
            Theme.drawBanner(c, TURN_TEXT[score.player()], BowlingArt.CX, 200f, 84f, 0xE0140B2E,
                    BowlingArt.COLOURS[score.player()]);
        } else if (phase == RESULT) {
            float pop = 1f + 0.3f * Math.max(0f, 1f - phaseT * 4f);
            String s = resultKind == BowlingScore.STRIKE ? "Strike!" : resultKind == BowlingScore.SPARE ? "Spare!"
                    : PIN_TEXT[Math.max(0, Math.min(10, resultPins))];
            int col = resultKind == BowlingScore.STRIKE ? 0xFFFFD84D : resultKind == BowlingScore.SPARE ? 0xFF7CFFB2
                    : resultKind == BowlingScore.GUTTER ? 0xFFFFB38A : 0xFFFFFFFF;
            Theme.drawCentered(c, s, BowlingArt.CX, 300f, 130f * pop, col);
        }
    }

    private void drawScoreboard(Canvas c) {
        float x = 24f, y = 24f, rowH = 92f, w = 720f;
        paint.setColor(0xC0140B2E);
        rect.set(x, y, x + w, y + 54f + players * rowH);
        c.drawRoundRect(rect, 30f, 30f, paint);
        int fr = Math.min(BowlingScore.FRAMES - 1, score.frame());
        Theme.drawCentered(c, FRAME_TEXT[fr], x + 150f, y + 44f, 40f, 0xFFE8F8FF);
        for (int p = 0; p < players; p++) {
            float ry = y + 54f + p * rowH;
            boolean mine = p == score.player() && !score.finished();
            if (mine) {
                paint.setColor((BowlingArt.COLOURS[p] & 0x00FFFFFF) | 0x40000000);
                rect.set(x + 8f, ry, x + w - 8f, ry + rowH - 6f);
                c.drawRoundRect(rect, 22f, 22f, paint);
            }
            paint.setColor(BowlingArt.COLOURS[p]);
            c.drawCircle(x + 46f, ry + 42f, 22f, paint);
            Theme.drawCentered(c, NAMES[p], x + 160f, ry + 56f, 34f, 0xFFFFFFFF);
            for (int k = 0; k < BowlingScore.FRAMES; k++) {
                float bx = x + 260f + k * 66f;
                paint.setColor(0x40FFFFFF);
                rect.set(bx, ry + 14f, bx + 58f, ry + 68f);
                c.drawRoundRect(rect, 10f, 10f, paint);
                if (cellText[p][k].length() > 0) {
                    int kind = score.frameKind(p, k);
                    Theme.drawCentered(c, cellText[p][k], bx + 29f, ry + 54f, 32f,
                            kind == BowlingScore.NORMAL ? 0xFFFFFFFF : 0xFFFFD84D);
                }
            }
            Theme.drawCentered(c, totalText[p], x + w - 56f, ry + 58f, 44f, BowlingArt.COLOURS[p]);
        }
    }

    private void drawEnd(Canvas c) {
        Theme.drawBanner(c, endTitle, Stage.W / 2f, 130f, 84f, 0xE6F3EBDD, Theme.INK);
        // the scoreboard in the middle
        float w = 720f, rowH = 92f, x = (Stage.W - w) / 2f, y = 225f;
        paint.setColor(0xD0140B2E);
        rect.set(x, y, x + w, y + 20f + players * rowH);
        c.drawRoundRect(rect, 30f, 30f, paint);
        for (int p = 0; p < players; p++) {
            float ry = y + 10f + p * rowH;
            paint.setColor(BowlingArt.COLOURS[p]);
            c.drawCircle(x + 46f, ry + 42f, 22f, paint);
            Theme.drawCentered(c, NAMES[p], x + 160f, ry + 56f, 34f, 0xFFFFFFFF);
            for (int k = 0; k < BowlingScore.FRAMES; k++) {
                float bx = x + 260f + k * 66f;
                paint.setColor(0x40FFFFFF);
                rect.set(bx, ry + 14f, bx + 58f, ry + 68f);
                c.drawRoundRect(rect, 10f, 10f, paint);
                if (cellText[p][k].length() > 0) {
                    Theme.drawCentered(c, cellText[p][k], bx + 29f, ry + 54f, 32f, 0xFFFFFFFF);
                }
            }
            Theme.drawCentered(c, totalText[p], x + w - 56f, ry + 58f, 44f, BowlingArt.COLOURS[p]);
        }
        Bitmap badge = Theme.starsBadge(bestStars);
        if (badge != null) {
            float by = y + 30f + players * rowH;
            rect.set(Stage.W / 2f - 162f, by, Stage.W / 2f + 162f, by + 108f);
            c.drawBitmap(badge, null, rect, sprite);
        }
        if (players > 1) Theme.drawCentered(c, "Everybody gets a medal!", Stage.W / 2f, 782f, 44f, Theme.INK);
        menu.draw(c);
    }
}
