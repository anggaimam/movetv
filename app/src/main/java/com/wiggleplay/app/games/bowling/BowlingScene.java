package com.wiggleplay.app.games.bowling;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;

import com.wiggleplay.app.core.GameView;
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
    private static final String[] NAMES = BowlingArt.PLAYER;
    private static final String[] PIN_TEXT = {"Gutter ball!", "1 pin!", "2 pins!", "3 pins!", "4 pins!", "5 pins!",
            "6 pins!", "7 pins!", "8 pins!", "9 pins!", "10 pins!"};
    private static final String[] TURN_TEXT = {"Player 1's turn!", "Player 2's turn!", "Player 3's turn!",
            "Player 4's turn!"};
    private static final String[] FRAME_TEXT = {"Frame 1", "Frame 2", "Frame 3", "Frame 4", "Frame 5"};

    private static final int INTRO = 1, AIM = 2, ROLL = 3, RESULT = 4, END = 5;
    private static final float INTRO_SECONDS = 1.4f, RESULT_SECONDS = 2.0f;
    private static final float CHARGE_SECONDS = 1.2f, MIN_POWER = 0.3f, HINT_AFTER = 6f, AUTO_ROLL_AFTER = 12f;

    private final TileMenu.Env env;
    private final TileMenu menu;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    private BowlingPhysics physics;
    private BowlingScore score;
    private int level = 1, players = 1, phase = INTRO;
    private Bitmap camera;
    private float time, phaseT, acc;

    // aiming and charging
    private float aimX, sweepT, manualUntil, idle, chargeT;
    private boolean charging;
    // rolling
    private int rollPlayer, lastKnocks, resultKind, resultPins;
    private float rollSoundT;
    private boolean pinsHeard;
    private float flash, resultLength = 2f, cheerAt = -1f, knockWait, shake, wave = -1f;
    private boolean gutterRoll;

    // arm swing (part 2)
    private final SwingDetect swing = new SwingDetect();
    private long lastPoseMs = -1;
    private int lastSwingState = -1;
    private Bitmap cameraPic;

    // confetti and sparks (one fixed pool, nothing allocated while playing)
    private static final int BITS = 140;
    private final float[] bx = new float[BITS], by = new float[BITS], bvx = new float[BITS], bvy = new float[BITS],
            blife = new float[BITS], bsize = new float[BITS];
    private final int[] bcol = new int[BITS];
    private static final int[] PARTY = {0xFF29D3FF, 0xFFFF6EC7, 0xFFFFD84D, 0xFF7CFFB2, 0xFFFFFFFF, 0xFFB388FF};

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
        // how many bowl was picked on the "How many players?" screen (1 to 4), everyone takes turns
        startGame(Math.max(1, Math.min(4, args.getInt("players", 1))));
    }

    @Override
    public void exit() {
        BowlingArt.unload();
        if (env.hub != null) env.hub.setMode(env.players);
    }

    private void startGame(int n) {
        players = n;
        score = new BowlingScore(n);
        physics = new BowlingPhysics(level);
        for (int p = 0; p < 4; p++) buildScoreText(p);
        java.util.Arrays.fill(blife, 0f);
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
        swing.reset();
        lastSwingState = -1;
        env.say(TURN_TEXT[score.player()]);
    }

    // ---- update ----

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        phaseT += dt;
        camera = f.camera;
        cameraPic = f.camera;
        if (flash > 0f) flash = Math.max(0f, flash - dt * 1.6f);
        if (shake > 0f) shake = Math.max(0f, shake - dt * 2.5f);
        if (wave >= 0f) wave = wave + dt > 1.2f ? -1f : wave + dt;
        animateBits(dt);
        Input in = f.input;
        if (in.pressed(KeyEvent.KEYCODE_BACK)) {
            env.sfx("back");
            leave();
            return;
        }
        switch (phase) {
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
                if (cheerAt >= 0f && phaseT >= cheerAt) {
                    cheerAt = -1f;
                    env.sfx("cheer");
                }
                if (phaseT >= resultLength) nextAfterResult();
                break;
            default:
                updateEnd(dt, f);
                break;
        }
    }

    private void leave() {
        env.go("levelselect", SceneArgs.of("gameId", GAME_ID));
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
        // the arm swing: fed only when the camera has new points
        PlayerPose me = f.pose.players[0];
        if (!charging && f.pose.timeMs != lastPoseMs) {
            lastPoseMs = f.pose.timeMs;
            boolean go = swing.update(me, f.pose.timeMs);
            swingDebug(go);
            if (go) {
                env.sfx("whoosh", 1.1f);
                throwBall(Math.max(0.1f, swing.power()), swing.spin());
                return;
            }
            // an arm on its way up counts as trying (just standing with arms down does not)
            if (swing.state(0) == SwingDetect.RISING || swing.state(1) == SwingDetect.RISING) {
                idle = Math.min(idle, HINT_AFTER - 0.01f);
            }
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
                throwBall(Math.max(MIN_POWER, Math.min(1f, chargeT / CHARGE_SECONDS)), 0f);
                return;
            }
        } else if (idle >= AUTO_ROLL_AFTER) {
            throwBall(0.35f, 0f); // nobody rolled: a gentle ball goes by itself
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

    /** Writes the swing state to the speed meter's tuning line (only when it changes, so nothing is built per frame). */
    private void swingDebug(boolean released) {
        if (env.perf == null || !env.perf.isVisible()) return;
        if (released) {
            env.perf.setNote("swing " + (swing.side() == 0 ? "L" : "R") + " RELEASE peak "
                    + Math.round(swing.peakSpeed() * 10f) / 10f + " sw/s  power " + Math.round(swing.power() * 100f)
                    + "%  spin " + Math.round(swing.spin() * 100f) + "%");
            return;
        }
        int st = Math.max(swing.state(0), swing.state(1));
        if (st == lastSwingState) return;
        lastSwingState = st;
        env.perf.setNote("swing " + (st == SwingDetect.ARMED ? "READY" : st == SwingDetect.RISING ? "RISING"
                : st == SwingDetect.DOWN ? "down" : "idle"));
    }

    private void throwBall(float power, float spin) {
        charging = false;
        rollPlayer = score.player();
        physics.roll(aimX, 0f, power, spin);
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
        // the ball meets the pins: a big crash and sparks, then a wooden tock for each further pin that is knocked
        int k = physics.knocks();
        knockWait -= dt;
        if (!pinsHeard && k > 0) {
            pinsHeard = true;
            lastKnocks = k;
            env.sfx("pins");
            knockWait = 0.12f;
            sparks(BowlingArt.sx(physics.ballX(), physics.ballY()), BowlingArt.sy(physics.ballY()) - 30f, 14,
                    0xFFFFFFFF);
            shake = 0.35f;
        } else if (k > lastKnocks && knockWait <= 0f) {
            lastKnocks = k;
            env.sfx("knock", 0.85f + (float) Math.random() * 0.4f);
            knockWait = 0.07f;
        }
        boolean over = (physics.time() > 0.5f && physics.settled()) || physics.time() > 9f;
        if (over) finishRoll();
    }

    private void finishRoll() {
        resultPins = physics.pinsDown();
        gutterRoll = physics.inGutter();
        resultKind = score.record(resultPins);
        for (int p = 0; p < players; p++) buildScoreText(p);
        phase = RESULT;
        phaseT = 0f;
        resultLength = RESULT_SECONDS;
        cheerAt = -1f;
        float deckX = BowlingArt.sx(0f, BowlingPhysics.HEAD_Y), deckY = BowlingArt.sy(BowlingPhysics.HEAD_Y) - 40f;
        switch (resultKind) {
            case BowlingScore.STRIKE:
                env.sfx("strike");
                env.say("Strike!");
                cheerAt = 0.45f;
                resultLength = 2.8f;
                flash = 1f;
                shake = 1f;
                wave = 0f;
                confetti(deckX, deckY, 110);
                confetti(240f, 1080f, 30);
                confetti(1680f, 1080f, 30);
                break;
            case BowlingScore.SPARE:
                env.sfx("star");
                env.say("Spare!");
                cheerAt = 0.35f;
                resultLength = 2.5f;
                flash = 0.5f;
                wave = 0f;
                confetti(deckX, deckY, 60);
                break;
            case BowlingScore.GUTTER:
                env.sfx("wahwah");
                env.say(gutterRoll ? "Gutter ball! Try again!" : "So close!");
                break;
            default:
                if (resultPins >= 7) {
                    env.sfx("coin", 1.2f);
                    cheerAt = 0.25f;
                    confetti(deckX, deckY, 30);
                } else {
                    env.sfx("coin");
                    sparks(deckX, deckY, 12, 0xFFFFD84D);
                }
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
        // picture buttons, high on the screen (the lower half is hard to reach with a hand)
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.home());
        tiles.add(new TileMenu.Tile("again", "Play again", null, Theme.MUSTARD, BowlingScene::paintReplay,
                1130f, 210f, 320f, 360f));
        tiles.add(new TileMenu.Tile("who", "Players", null, 0xFF26215C, (c, w, h) -> BowlingArt.paintGroup(c, w, h, 4),
                1490f, 210f, 320f, 360f));
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
        else if ("who".equals(sel)) env.go("players", SceneArgs.of("gameId", GAME_ID));
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
        if (phase == END) {
            c.drawBitmap(BowlingArt.space(), 0f, 0f, null);
            drawEnd(c);
            return;
        }
        c.save();
        if (shake > 0f) c.translate((float) Math.sin(time * 70f) * 14f * shake, (float) Math.cos(time * 55f) * 8f * shake);
        c.drawBitmap(BowlingArt.lane(), 0f, 0f, null);
        drawDrifters(c);
        if (wave >= 0f) {
            // a ring of light spreading from the pins (strike and spare)
            float r = 60f + wave * 700f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(14f * (1.2f - wave));
            paint.setColor(resultKind == BowlingScore.STRIKE ? 0xFFFFD84D : 0xFF7CFFB2);
            paint.setAlpha((int) (220 * Math.max(0f, 1f - wave / 1.2f)));
            c.drawCircle(BowlingArt.sx(0f, BowlingPhysics.HEAD_Y), BowlingArt.sy(BowlingPhysics.HEAD_Y) - 40f, r, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
        }
        drawPinsAndBall(c);
        c.restore();
        drawAimUi(c);
        drawScoreboard(c);
        drawBanners(c);
        drawBits(c);
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
            Theme.drawBanner(c, "Swing your arm, or hold OK!", BowlingArt.CX, 1000f, 56f, 0xE0140B2E, 0xFFFFD84D);
        }
        drawSwingDemo(c, idle >= HINT_AFTER ? 1.35f : 1f);
        drawBooth(c);
    }

    private final android.graphics.Path tri = new android.graphics.Path();

    /**
     * A neon stick figure on the left doing an underhand bowl over and over, so nobody needs to read. It glows gold
     * while the player's own arm is swung back and ready.
     */
    private void drawSwingDemo(Canvas c, float size) {
        float cx = 250f, floor = 1010f, k = 1.7f * size;
        boolean ready = swing.ready();
        int col = ready ? 0xFFFFD84D : 0xFF29D3FF;
        // the arm goes back (behind, low) then forward and up, 1.6 s a loop
        float ph = (time % 1.6f) / 1.6f;
        float ang = ph < 0.55f ? -40f - 30f * (float) Math.sin(ph / 0.55f * Math.PI)       // back and forth behind
                : -40f + 150f * (float) Math.sin((ph - 0.55f) / 0.45f * Math.PI / 2f);      // forward and up
        float hipY = floor - 90f * k, shY = hipY - 70f * k, headY = shY - 30f * k;
        Theme.drawCentered(c, ready ? "Now swing!" : "Swing!", cx, headY - 22f * k - 34f, 64f * size, col);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(9f * size);
        paint.setColor(col);
        c.drawCircle(cx, headY, 22f * k, paint);
        c.drawLine(cx, shY - 8f * k, cx, hipY, paint);                       // body
        c.drawLine(cx, hipY, cx - 30f * k, floor, paint);                     // legs, one step forward
        c.drawLine(cx, hipY, cx + 34f * k, floor, paint);
        c.drawLine(cx, shY, cx - 20f * k, shY + 60f * k, paint);              // the other arm
        double r = Math.toRadians(ang);                                       // 0 = straight down, + = forward (right)
        float hx = cx + (float) Math.sin(r) * 80f * k, hy = shY + (float) Math.cos(r) * 80f * k;
        c.drawLine(cx, shY, hx, hy, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
        if (ph < 0.92f) {                                                     // the ball in the hand
            paint.setColor(BowlingArt.COLOURS[score.player()]);
            c.drawCircle(hx, hy + 10f * k, 16f * k, paint);
        }
    }

    /** The player's own camera picture in a small neon "TV booth" in the top-right corner, mirrored like a mirror. */
    private void drawBooth(Canvas c) {
        Bitmap cam = cameraPic;
        if (cam == null) return;
        float w = 384f, h = 216f, x = Stage.W - w - 30f, y = 30f;
        c.save();
        c.translate(x + w, y);
        c.scale(-1f, 1f);
        rect.set(0f, 0f, w, h);
        c.drawBitmap(cam, null, rect, sprite);
        c.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setColor(swing.ready() ? 0xFFFFD84D : BowlingArt.COLOURS[score.player()]);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 18f, 18f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ---- confetti and sparks ----

    private int freeBit() {
        for (int i = 0; i < BITS; i++) if (blife[i] <= 0f) return i;
        return -1;
    }

    /** Confetti thrown up from a point; on LOW quality half as much. */
    private void confetti(float x, float y, int n) {
        if (GameView.quality == GameView.LOW) n = (n + 1) / 2;
        for (int k = 0; k < n; k++) {
            int i = freeBit();
            if (i < 0) return;
            double a = -Math.PI / 2 + (Math.random() - 0.5) * 1.8;
            float sp = 500f + (float) Math.random() * 700f;
            bx[i] = x + ((float) Math.random() - 0.5f) * 120f;
            by[i] = y;
            bvx[i] = (float) Math.cos(a) * sp;
            bvy[i] = (float) Math.sin(a) * sp;
            blife[i] = 1.6f + (float) Math.random() * 1.0f;
            bsize[i] = 10f + (float) Math.random() * 12f;
            bcol[i] = PARTY[k % PARTY.length];
        }
    }

    /** A small quick burst of round sparks (a hit). */
    private void sparks(float x, float y, int n, int colour) {
        for (int k = 0; k < n; k++) {
            int i = freeBit();
            if (i < 0) return;
            double a = Math.random() * Math.PI * 2;
            float sp = 200f + (float) Math.random() * 380f;
            bx[i] = x;
            by[i] = y;
            bvx[i] = (float) Math.cos(a) * sp;
            bvy[i] = (float) Math.sin(a) * sp - 150f;
            blife[i] = 0.4f + (float) Math.random() * 0.3f;
            bsize[i] = -(4f + (float) Math.random() * 5f); // negative size = a round spark
            bcol[i] = colour;
        }
    }

    private void animateBits(float dt) {
        for (int i = 0; i < BITS; i++) {
            if (blife[i] <= 0f) continue;
            blife[i] -= dt;
            bvy[i] += 900f * dt;
            bvx[i] *= 1f - dt * 0.8f;
            bx[i] += bvx[i] * dt;
            by[i] += bvy[i] * dt;
        }
    }

    private void drawBits(Canvas c) {
        for (int i = 0; i < BITS; i++) {
            if (blife[i] <= 0f) continue;
            paint.setColor(bcol[i]);
            paint.setAlpha((int) (255 * Math.min(1f, blife[i] * 2.5f)));
            float sz = bsize[i];
            if (sz < 0f) {
                c.drawCircle(bx[i], by[i], -sz, paint);
            } else {
                float w = sz * Math.abs((float) Math.cos(blife[i] * 9f + i));
                rect.set(bx[i] - w / 2f, by[i] - sz / 2f, bx[i] + w / 2f, by[i] + sz / 2f);
                c.drawRect(rect, paint);
            }
        }
        paint.setAlpha(255);
    }

    private void drawBanners(Canvas c) {
        if (phase == INTRO) {
            Theme.drawBanner(c, TURN_TEXT[score.player()], BowlingArt.CX, 200f, 84f, 0xE0140B2E,
                    BowlingArt.COLOURS[score.player()]);
            float pop = Math.min(1f, phaseT * 4f);
            float sz = 230f * (0.6f + 0.4f * pop);
            rect.set(BowlingArt.CX - sz / 2f, 300f, BowlingArt.CX + sz / 2f, 300f + sz);
            c.drawBitmap(BowlingArt.avatar(score.player()), null, rect, sprite);
        } else if (phase == RESULT) {
            float pop = 1f + 0.3f * Math.max(0f, 1f - phaseT * 4f);
            String s = resultKind == BowlingScore.STRIKE ? "Strike!" : resultKind == BowlingScore.SPARE ? "Spare!"
                    : resultPins == 0 ? (gutterRoll ? "Gutter ball!" : "So close!")
                    : PIN_TEXT[Math.max(0, Math.min(10, resultPins))];
            int col = resultKind == BowlingScore.STRIKE ? PARTY[((int) (phaseT * 8f)) % 4]
                    : resultKind == BowlingScore.SPARE ? 0xFF7CFFB2
                    : resultKind == BowlingScore.GUTTER ? 0xFFFFB38A : 0xFFFFFFFF;
            float size = resultKind == BowlingScore.STRIKE ? 170f : 130f;
            float y = 300f;
            c.save();
            if (resultKind == BowlingScore.STRIKE || resultKind == BowlingScore.SPARE) {
                y -= Math.abs((float) Math.sin(phaseT * 7f)) * 30f * Math.max(0f, 1f - phaseT / 2f); // bounces
            } else if (resultKind == BowlingScore.GUTTER) {
                c.rotate((float) Math.sin(phaseT * 10f) * 8f * Math.max(0f, 1f - phaseT / 1.5f), BowlingArt.CX, y);
            }
            Theme.drawCentered(c, s, BowlingArt.CX, y, size * pop, col);
            c.restore();
            if (resultKind == BowlingScore.GUTTER) {
                Theme.drawCentered(c, "Try again!", BowlingArt.CX, y + 80f, 56f, 0xFFFFFFFF);
            }
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
            rect.set(x + 14f, ry + 6f, x + 80f, ry + 72f);
            c.drawBitmap(BowlingArt.avatar(p), null, rect, sprite);
            Theme.drawCentered(c, NAMES[p], x + 165f, ry + 54f, 32f, BowlingArt.COLOURS[p]);
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
        Theme.drawCentered(c, endTitle, 640f, 140f, 84f, 0xFFFFD84D);
        // the scoreboard on the left, buttons on the right (both in the top half)
        float w = 760f, rowH = 92f, x = 260f, y = 200f;
        paint.setColor(0xD026215C);
        rect.set(x, y, x + w, y + 20f + players * rowH);
        c.drawRoundRect(rect, 30f, 30f, paint);
        int leader = score.leader();
        for (int p = 0; p < players; p++) {
            float ry = y + 10f + p * rowH;
            rect.set(x + 14f, ry + 6f, x + 80f, ry + 72f);
            c.drawBitmap(BowlingArt.avatar(p), null, rect, sprite);
            Theme.drawCentered(c, NAMES[p], x + 170f, ry + 54f, 32f, BowlingArt.COLOURS[p]);
            for (int k = 0; k < BowlingScore.FRAMES; k++) {
                float bx = x + 270f + k * 66f;
                paint.setColor(0x40FFFFFF);
                rect.set(bx, ry + 14f, bx + 58f, ry + 68f);
                c.drawRoundRect(rect, 10f, 10f, paint);
                if (cellText[p][k].length() > 0) {
                    Theme.drawCentered(c, cellText[p][k], bx + 29f, ry + 54f, 32f, 0xFFFFFFFF);
                }
            }
            Theme.drawCentered(c, totalText[p], x + w - 60f, ry + 58f, 44f, BowlingArt.COLOURS[p]);
            if (players > 1 && p == leader) {                    // a little crown on the winner's avatar
                paint.setColor(0xFFFFD84D);
                tri.reset();
                float cx = x + 47f, cy = ry + 4f;
                tri.moveTo(cx - 20f, cy + 4f);
                tri.lineTo(cx - 20f, cy - 14f);
                tri.lineTo(cx - 8f, cy - 4f);
                tri.lineTo(cx, cy - 18f);
                tri.lineTo(cx + 8f, cy - 4f);
                tri.lineTo(cx + 20f, cy - 14f);
                tri.lineTo(cx + 20f, cy + 4f);
                tri.close();
                c.drawPath(tri, paint);
            }
        }
        Bitmap badge = Theme.starsBadge(bestStars);
        float by = y + 40f + players * rowH;
        if (badge != null) {
            rect.set(x + w / 2f - 162f, by, x + w / 2f + 162f, by + 108f);
            c.drawBitmap(badge, null, rect, sprite);
        }
        if (players > 1) Theme.drawCentered(c, "Everybody gets a medal!", x + w / 2f, by + 160f, 44f, 0xFFE8F8FF);
        menu.draw(c);
    }

    /** "Play again": a round arrow, drawn on the tile's picture. */
    private static void paintReplay(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w * 0.11f);
        p.setColor(0xFF2C2340);
        float r = w * 0.3f, cx = w / 2f, cy = h / 2f;
        RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(o, -60f, 290f, false, p);
        // arrow head at the start of the arc
        double a = Math.toRadians(-60f);
        float ax = cx + (float) Math.cos(a) * r, ay = cy + (float) Math.sin(a) * r;
        p.setStyle(Paint.Style.FILL);
        android.graphics.Path head = new android.graphics.Path();
        head.moveTo(ax + w * 0.16f, ay - w * 0.02f);
        head.lineTo(ax - w * 0.08f, ay - w * 0.13f);
        head.lineTo(ax - w * 0.04f, ay + w * 0.15f);
        head.close();
        c.drawPath(head, p);
    }

    /** A small rocket crossing the dark sky and an astronaut bobbing on the right, both faint. */
    private void drawDrifters(Canvas c) {
        float t = time % 40f;
        float rx = -150f + t / 40f * 2220f, ry = 110f + (float) Math.sin(time * 0.4f) * 30f;
        sprite.setAlpha(70);
        rect.set(rx - 45f, ry - 45f, rx + 45f, ry + 45f);
        c.save();
        c.rotate(60f, rx, ry);
        c.drawBitmap(BowlingArt.rocket(), null, rect, sprite);
        c.restore();
        float ax = 1780f, ay = 640f + (float) Math.sin(time * 0.7f) * 18f;
        c.save();
        c.rotate((float) Math.sin(time * 0.3f) * 14f, ax, ay);
        rect.set(ax - 70f, ay - 70f, ax + 70f, ay + 70f);
        c.drawBitmap(BowlingArt.avatar(BowlingArt.ASTRONAUT), null, rect, sprite);
        c.restore();
        sprite.setAlpha(255);
    }

}
