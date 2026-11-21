package com.angga.movetv.games.common;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.angga.movetv.core.Ease;
import com.angga.movetv.core.Pool;
import com.angga.movetv.core.SpriteCache;
import com.angga.movetv.core.Stage;
import com.angga.movetv.ui.Theme;
import com.angga.movetv.ui.TileMenu;

import java.util.Random;

/**
 * The animated stars of the end screen. The score counts up (with ticks), then every earned star flies from the
 * score into its slot (spinning and growing), lands with a burst of sparkles and a rising "star" sound, and then
 * bobs and twinkles for as long as the screen stays. Empty slots are soft paper-coloured outlines. A 3-star round
 * also rains confetti. Everything comes from pools made once, so nothing is allocated per frame. Main thread only.
 */
public final class StarBurst {

    /** Seconds the score takes to count up. */
    static final float COUNT_SECONDS = 1.2f;
    /** When the first star leaves. */
    static final float FIRST_STAR_AT = 1.5f;
    /** A star's flight, and the pause between one landing and the next star leaving. */
    static final float FLIGHT = 0.45f, PAUSE = 0.25f;
    private static final float STEP = FLIGHT + PAUSE;
    private static final float[] PITCH = {1.0f, 1.2f, 1.4f};
    private static final int[] SPARK_COLOURS = {0xFFF2B705, 0xFFFFE08A, 0xFFFFFFFF, 0xFFE89A4C};
    private static final int[] CONFETTI_COLOURS = {Theme.P1, Theme.P2, Theme.ORANGE, Theme.MUSTARD, Theme.SAGE, Theme.PINK,
            Theme.BLUE};
    private static final int SPARKS_PER_STAR = 12;
    private static final float CONFETTI_SECONDS = 2.8f;
    private static final float BMP = 136f;

    private static final class Spark {
        float x, y, vx, vy, life, size;
        int colour;
    }

    private static final class Piece {
        float x, y, vy, swayPhase, swaySpeed, rot, rotSpeed, len;
        int colour;
    }

    private final TileMenu.Env env;
    private final Pool<Spark> sparks = new Pool<>(96, Spark::new);
    private final Pool<Piece> confetti = new Pool<>(120, Piece::new);
    private final Random rnd = new Random(7L);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final int[] earned = new int[2];
    private final int[] score = new int[2];
    private final boolean[][] landed = new boolean[2][3];
    private final boolean[] soundDone = new boolean[3];
    private final boolean[] playerDone = new boolean[2];
    private EndLayout layout;
    private int players = 1;
    private float t, ticks, confettiClock, spawnDebt;
    private boolean confettiOn, cheered;
    private float finishAt;

    /**
     * Creates the animation.
     * @param env the shared environment (for the sounds)
     */
    public StarBurst(TileMenu.Env env) {
        this.env = env;
    }

    /**
     * Starts the animation for a finished round.
     * @param layout where everything goes
     * @param players 1 or 2
     * @param stars1 stars of player 1 (1..3)
     * @param stars2 stars of player 2 (1..3, ignored for one player)
     * @param score1 score of player 1
     * @param score2 score of player 2
     */
    public void begin(EndLayout layout, int players, int stars1, int stars2, int score1, int score2) {
        this.layout = layout;
        this.players = players == 2 ? 2 : 1;
        earned[0] = clampStars(stars1);
        earned[1] = this.players == 2 ? clampStars(stars2) : 0;
        score[0] = Math.max(0, score1);
        score[1] = Math.max(0, score2);
        t = 0f;
        ticks = 0f;
        confettiClock = 0f;
        spawnDebt = 0f;
        confettiOn = false;
        cheered = false;
        for (int i = sparks.size() - 1; i >= 0; i--) sparks.freeAt(i);
        for (int i = confetti.size() - 1; i >= 0; i--) confetti.freeAt(i);
        for (int p = 0; p < 2; p++) {
            playerDone[p] = p >= this.players;
            for (int i = 0; i < 3; i++) landed[p][i] = false;
        }
        for (int i = 0; i < 3; i++) soundDone[i] = false;
        int most = Math.max(earned[0], earned[1]);
        finishAt = FIRST_STAR_AT + (most - 1) * STEP + FLIGHT;
    }

    private static int clampStars(int n) {
        return Math.max(1, Math.min(3, n));
    }

    /**
     * Jumps to the end of the animation: every star sits in its slot, no more sounds are played except one cheer
     * after a 3-star round.
     */
    public void skip() {
        for (int p = 0; p < players; p++) {
            for (int i = 0; i < earned[p]; i++) landed[p][i] = true;
            playerDone[p] = true;
        }
        for (int i = 0; i < 3; i++) soundDone[i] = true;
        t = Math.max(t, finishAt + 0.3f);
        startConfettiIfDeserved();
    }

    /** @return true once every star has landed (the tiles may appear) */
    public boolean allLanded() {
        return t >= finishAt;
    }

    /**
     * Whether a player's last star has landed (the kid cheers then).
     * @param p player 0 or 1
     * @return true when the player's last earned star has landed
     */
    public boolean playerDone(int p) {
        return playerDone[p];
    }

    /**
     * The score to show while it counts up.
     * @param p player 0 or 1
     * @return the number to draw
     */
    public int shownScore(int p) {
        float u = Math.min(1f, t / COUNT_SECONDS);
        return (int) (score[p] * Ease.outCubic(u) + 0.5f);
    }

    /**
     * Advances the animation.
     * @param dt seconds
     */
    public void update(float dt) {
        float before = t;
        t += dt;
        if (before < COUNT_SECONDS && score[0] + score[1] > 0) {
            ticks += dt;
            if (ticks >= 0.1f) {
                ticks -= 0.1f;
                env.sfx("tick", 0.8f + 0.8f * Math.min(1f, t / COUNT_SECONDS));
            }
        }
        for (int p = 0; p < players; p++) {
            for (int i = 0; i < earned[p]; i++) {
                if (!landed[p][i] && t >= FIRST_STAR_AT + i * STEP + FLIGHT) {
                    landed[p][i] = true;
                    if (!soundDone[i]) {
                        soundDone[i] = true;
                        env.sfx("star", PITCH[i]);
                    }
                    sparkle(layout.slotX(p, i), layout.slotY(p));
                    if (i == earned[p] - 1) playerDone[p] = true;
                }
            }
        }
        startConfettiIfDeserved();
        updateSparks(dt);
        updateConfetti(dt);
    }

    private void startConfettiIfDeserved() {
        if (confettiOn) return;
        for (int p = 0; p < players; p++) {
            if (earned[p] == 3 && landed[p][2]) {
                confettiOn = true;
                if (!cheered) {
                    cheered = true;
                    env.sfx("cheer");
                }
                return;
            }
        }
    }

    private void sparkle(float x, float y) {
        for (int k = 0; k < SPARKS_PER_STAR; k++) {
            Spark s = sparks.obtain();
            if (s == null) return;
            float a = (float) (k * (Math.PI * 2 / SPARKS_PER_STAR) + rnd.nextFloat() * 0.4f);
            float sp = 220f + rnd.nextFloat() * 260f;
            s.x = x;
            s.y = y;
            s.vx = (float) Math.cos(a) * sp;
            s.vy = (float) Math.sin(a) * sp - 60f;
            s.life = 0.55f + rnd.nextFloat() * 0.3f;
            s.size = 7f + rnd.nextFloat() * 8f;
            s.colour = SPARK_COLOURS[k % SPARK_COLOURS.length];
        }
    }

    private void updateSparks(float dt) {
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.at(i);
            s.life -= dt;
            s.vy += 500f * dt;
            s.vx *= 1f - Math.min(1f, 2.2f * dt);
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            if (s.life <= 0f) sparks.freeAt(i);
        }
    }

    private void updateConfetti(float dt) {
        if (confettiOn && confettiClock < CONFETTI_SECONDS) {
            confettiClock += dt;
            spawnDebt += dt * 55f;
            while (spawnDebt >= 1f) {
                spawnDebt -= 1f;
                Piece c = confetti.obtain();
                if (c == null) break;
                c.x = rnd.nextFloat() * Stage.W;
                c.y = -20f - rnd.nextFloat() * 60f;
                c.vy = 260f + rnd.nextFloat() * 220f;
                c.swayPhase = rnd.nextFloat() * 6.28f;
                c.swaySpeed = 1.5f + rnd.nextFloat() * 2f;
                c.rot = rnd.nextFloat() * 6.28f;
                c.rotSpeed = (rnd.nextFloat() - 0.5f) * 10f;
                c.len = 10f + rnd.nextFloat() * 8f;
                c.colour = CONFETTI_COLOURS[rnd.nextInt(CONFETTI_COLOURS.length)];
            }
        }
        for (int i = confetti.size() - 1; i >= 0; i--) {
            Piece c = confetti.at(i);
            c.y += c.vy * dt;
            c.x += (float) Math.sin(c.swayPhase + t * c.swaySpeed) * 60f * dt;
            c.rot += c.rotSpeed * dt;
            if (c.y > Stage.H + 30f) confetti.freeAt(i);
        }
    }

    /**
     * Draws the confetti (call before the texts, so it falls behind them).
     * @param c canvas in stage coordinates
     */
    public void drawConfetti(Canvas c) {
        if (confetti.size() == 0) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(9f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < confetti.size(); i++) {
            Piece p = confetti.at(i);
            paint.setColor(p.colour);
            float dx = (float) Math.cos(p.rot) * p.len;
            float dy = (float) Math.sin(p.rot * 0.5f) * p.len * 0.6f;
            c.drawLine(p.x - dx, p.y - dy, p.x + dx, p.y + dy, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * Draws the star slots, the flying and resting stars, and the sparkles.
     * @param c canvas in stage coordinates
     */
    public void drawStars(Canvas c) {
        Bitmap off = outline();
        Bitmap on = filled();
        for (int p = 0; p < players; p++) {
            for (int i = 0; i < 3; i++) {
                float cx = layout.slotX(p, i), cy = layout.slotY(p);
                float since = t - (1.1f + i * 0.12f);
                if (since > 0f) {
                    float u = Math.min(1f, since / 0.35f);
                    float k = 0.6f + 0.4f * Ease.outCubic(u);
                    paint.setAlpha((int) (255 * u));
                    float half = BMP / 2f * k;
                    rect.set(cx - half, cy - half, cx + half, cy + half);
                    c.drawBitmap(off, null, rect, paint);
                }
                if (i < earned[p]) drawEarned(c, on, p, i, cx, cy);
            }
        }
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < sparks.size(); i++) {
            Spark s = sparks.at(i);
            paint.setColor(s.colour);
            paint.setAlpha((int) (255 * Math.min(1f, s.life * 2.5f)));
            c.drawCircle(s.x, s.y, s.size * Math.min(1f, 0.4f + s.life), paint);
        }
        paint.setAlpha(255);
    }

    private void drawEarned(Canvas c, Bitmap on, int p, int i, float cx, float cy) {
        float start = FIRST_STAR_AT + i * STEP;
        if (t < start) return;
        float x, y, scale, rot;
        if (!landed[p][i] && t < start + FLIGHT) {
            float u = (t - start) / FLIGHT;
            float e = Ease.outCubic(u);
            x = layout.colX[p] + (cx - layout.colX[p]) * e;
            float y0 = layout.score[p].cy();
            y = y0 + (cy - y0) * e - 150f * (float) Math.sin(Math.PI * u);
            scale = 0.2f + 0.8f * Ease.outBack(u);
            rot = 360f * (1f - e);
        } else {
            float landT = Math.max(0f, t - (start + FLIGHT));
            float pulse = landT < 0.25f ? 0.22f * (1f - landT / 0.25f) : 0f;
            float phase = i * 0.9f + p * 1.7f;
            x = cx;
            y = cy + 5f * (float) Math.sin(t * 2.6f + phase);
            scale = 1f + pulse + 0.03f * (1f + (float) Math.sin(t * 5f + phase));
            rot = 0f;
        }
        float half = BMP / 2f * scale;
        paint.setAlpha(255);
        c.save();
        c.translate(x, y);
        c.rotate(rot);
        rect.set(-half, -half, half, half);
        c.drawBitmap(on, null, rect, paint);
        c.restore();
    }

    // ---- baked star pictures ----

    private static void starPath(Path p, float cx, float cy, float r) {
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? r : r * 0.46f;
            float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr;
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.close();
    }

    private static Bitmap filled() {
        return SpriteCache.shared().get("burst_star_on", (int) BMP, (int) BMP, (c, w, h) -> {
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            Path p = new Path();
            starPath(p, w / 2f, h / 2f + 3f, 60f);
            f.setStyle(Paint.Style.FILL);
            f.setColor(0xFFF2B705);
            c.drawPath(p, f);
            Path inner = new Path();
            starPath(inner, w / 2f, h / 2f + 3f, 38f);
            f.setColor(0xFFFFD84D);
            c.drawPath(inner, f);
            f.setStyle(Paint.Style.STROKE);
            f.setStrokeWidth(6f);
            f.setStrokeJoin(Paint.Join.ROUND);
            f.setColor(0xFFC98F00);
            c.drawPath(p, f);
        });
    }

    private static Bitmap outline() {
        return SpriteCache.shared().get("burst_star_off", (int) BMP, (int) BMP, (c, w, h) -> {
            Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            Path p = new Path();
            starPath(p, w / 2f, h / 2f + 3f, 60f);
            f.setStyle(Paint.Style.FILL);
            f.setColor(0x66FFFFFF);
            c.drawPath(p, f);
            f.setStyle(Paint.Style.STROKE);
            f.setStrokeWidth(6f);
            f.setStrokeJoin(Paint.Join.ROUND);
            f.setColor(0xFFD9CDB8);
            c.drawPath(p, f);
        });
    }
}
