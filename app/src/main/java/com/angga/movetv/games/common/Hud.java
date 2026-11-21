package com.angga.movetv.games.common;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;

import com.angga.movetv.core.Scene;
import com.angga.movetv.core.Stage;
import com.angga.movetv.ui.RoundButton;
import com.angga.movetv.ui.Theme;
import com.angga.movetv.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared game frame: 3-2-1-Go countdown, a time bar, score bubbles, pause, and the end screen with
 * animated stars and the "Play again / Home / Back" tiles. Games own the playing field and feed their scores
 * in. Nothing is allocated per frame (score text comes from a table made once). Main thread only.
 */
public final class Hud {

    /** The phases of a round. */
    public static final int COUNTDOWN = 0, PLAYING = 1, ENDING = 2, END = 3;

    private static final float COUNT_SECONDS = 3.2f;
    private static final int MAX_NUM = 400;
    private static final String[] NUM = new String[MAX_NUM + 1];
    private static final String[] COUNT_TEXT = {"3", "2", "1", "Go!"};

    static {
        for (int i = 0; i < NUM.length; i++) NUM[i] = i == MAX_NUM ? MAX_NUM + "+" : Integer.toString(i);
    }

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    private final TileMenu.Env env;
    private final TileMenu menu;
    private final float playSeconds;
    private final String track;

    private int phase = COUNTDOWN;
    private float t, clock, endAnim;
    private int players = 1;
    private int shownStars = -1;
    private int lastCountShown = -1;
    private boolean paused;
    private final int[] stars = new int[2];
    private final int[] finalScore = new int[2];
    private boolean menuReady;
    private float time;

    /**
     * Creates the frame.
     * @param env what the menu screens share
     * @param playSeconds length of a round in seconds
     * @param track music track name played during the round
     */
    public Hud(TileMenu.Env env, float playSeconds, String track) {
        this.env = env;
        this.menu = new TileMenu(env);
        this.playSeconds = playSeconds;
        this.track = track;
    }

    /**
     * Starts a new round: countdown first.
     * @param playerCount 1 or 2
     */
    public void start(int playerCount) {
        players = playerCount == 2 ? 2 : 1;
        phase = COUNTDOWN;
        t = 0f;
        clock = 0f;
        endAnim = 0f;
        shownStars = -1;
        lastCountShown = -1;
        paused = false;
        menuReady = false;
        if (env.music != null) env.music.stop(200);
    }

    /** @return the current phase ({@link #COUNTDOWN}, {@link #PLAYING}, {@link #ENDING} or {@link #END}) */
    public int phase() { return phase; }

    /** @return true while the game should move (playing or the last fruit falling) and not paused */
    public boolean running() { return !paused && (phase == PLAYING || phase == ENDING); }

    /** @return true while new things may appear in the game */
    public boolean spawning() { return !paused && phase == PLAYING; }

    /** @return seconds of the round used up */
    public float elapsed() { return clock; }

    /** @return true if paused */
    public boolean paused() { return paused; }

    /**
     * Ends the round: the game says its final scores and stars, and the end screen begins.
     * @param s1 stars of player 1 (1..3)
     * @param s2 stars of player 2 (1..3), ignored with one player
     * @param score1 score of player 1
     * @param score2 score of player 2
     */
    public void finish(int s1, int s2, int score1, int score2) {
        stars[0] = s1;
        stars[1] = s2;
        finalScore[0] = score1;
        finalScore[1] = score2;
        phase = END;
        endAnim = 0f;
        shownStars = -1;
        if (env.music != null) env.music.stop(400);
        env.sfx("cheer");
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        TileMenu.Tile again = new TileMenu.Tile("again", "Play again", null, Theme.ORANGE, Theme::paintSprout,
                460f, 640f, 400f, 380f);
        TileMenu.Tile home = new TileMenu.Tile("home", "Home", null, Theme.BLUE, Theme::paintHome,
                1060f, 640f, 400f, 380f);
        again.holdMs = 1000;
        home.holdMs = 1000;
        tiles.add(again);
        tiles.add(home);
        menu.setTiles(tiles, "again");
        menuReady = true;
    }

    /**
     * Advances the frame.
     * @param dt seconds since the last frame
     * @param f this frame's data
     * @return "again", "home" or "back" when the player chose on the end screen or pressed Back; otherwise null
     */
    public String update(float dt, Scene.Frame f) {
        time += dt;
        boolean test = "test".equals(f.pose.source);
        if (f.input.pressed(KeyEvent.KEYCODE_BACK)) {
            env.sfx("back");
            return "back";
        }
        if (phase == END) {
            endAnim += dt;
            int want = Math.min(3, (int) (endAnim / 0.55f)) ;
            int best = Math.max(stars[0], players == 2 ? stars[1] : 0);
            int show = Math.min(want, best);
            if (show != shownStars) {
                if (show > 0) env.sfx("star");
                shownStars = show;
            }
            if (!menuReady) return null;
            String sel = menu.update(dt, f);
            if (sel == null) return null;
            if ("again".equals(sel)) env.sfx("select");
            return sel;
        }
        if (!test && f.input.pressed(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            paused = !paused;
            if (env.music != null && phase != COUNTDOWN) {
                if (paused) env.music.stop(150); else env.music.play(track);
            }
        }
        if (paused) return null;
        if (phase == COUNTDOWN) {
            t += dt;
            int idx = Math.min(3, (int) (t / 0.8f));
            if (idx != lastCountShown) {
                lastCountShown = idx;
                env.sfx(idx == 3 ? "go" : "count");
            }
            if (t >= COUNT_SECONDS) {
                phase = PLAYING;
                clock = 0f;
                if (env.music != null) env.music.play(track);
            }
        } else if (phase == PLAYING) {
            clock += dt;
            if (clock >= playSeconds) {
                phase = ENDING;
                t = 0f;
            }
        } else if (phase == ENDING) {
            t += dt;
        }
        return null;
    }

    /**
     * While ENDING, whether the game may finish now (a short pause after time is up so the last fruit can fall).
     * @param fieldEmpty true when nothing is left in the air
     * @return true when the game should call {@link #finish}
     */
    public boolean readyToFinish(boolean fieldEmpty) {
        return phase == ENDING && (fieldEmpty || t > 3f);
    }

    /**
     * Draws the frame on top of the game: time bar, scores, countdown, pause, or the end screen.
     * @param c canvas
     * @param score1 live score of player 1
     * @param score2 live score of player 2
     */
    public void draw(Canvas c, int score1, int score2) {
        if (phase == END) {
            drawEnd(c);
            return;
        }
        drawBar(c);
        drawScore(c, score1, 40f, Theme.P1);
        if (players == 2) {
            drawScore(c, score2, Stage.W - 40f - 220f, Theme.P2);
            FILL.setColor(0x66FFFFFF);
            for (int y = 130; y < 1060; y += 60) c.drawRect(Stage.W / 2f - 3f, y, Stage.W / 2f + 3f, y + 34f, FILL);
        }
        if (phase == COUNTDOWN) {
            int idx = Math.max(0, Math.min(3, lastCountShown));
            float local = (t - idx * 0.8f) / 0.8f;
            float size = 300f * (1f + 0.25f * (1f - Math.min(1f, Math.max(0f, local * 3f))));
            Theme.drawCentered(c, COUNT_TEXT[idx], Stage.W / 2f + 6f, 600f + 8f, size, 0x66000000);
            Theme.drawCentered(c, COUNT_TEXT[idx], Stage.W / 2f, 600f, size,
                    idx == 3 ? Theme.ORANGE : 0xFFFFFFFF);
        }
        if (paused) Theme.drawBanner(c, "Paused", Stage.W / 2f, 540f, 96f, 0xE6F3EBDD, Theme.INK);
    }

    private void drawBar(Canvas c) {
        float x0 = 420f, x1 = 1500f, y = 56f, h = 28f;
        R.set(x0, y, x1, y + h);
        FILL.setColor(0x88F3EBDD);
        c.drawRoundRect(R, h / 2f, h / 2f, FILL);
        float left = phase == COUNTDOWN ? 1f : Math.max(0f, 1f - clock / playSeconds);
        if (phase == ENDING) left = 0f;
        if (left > 0.01f) {
            R.set(x0, y, x0 + (x1 - x0) * left, y + h);
            FILL.setColor(left < 0.2f ? Theme.PINK : Theme.SAGE);
            c.drawRoundRect(R, h / 2f, h / 2f, FILL);
        }
    }

    private void drawScore(Canvas c, int score, float x, int colour) {
        R.set(x, 24f, x + 220f, 140f);
        FILL.setColor(colour);
        c.drawRoundRect(R, 58f, 58f, FILL);
        FILL.setColor(0xFFF3EBDD);
        R.set(x + 8f, 32f, x + 212f, 132f);
        c.drawRoundRect(R, 50f, 50f, FILL);
        Theme.drawCentered(c, NUM[Math.max(0, Math.min(MAX_NUM, score))], x + 110f, 108f, 72f, Theme.INK);
    }

    private void drawEnd(Canvas c) {
        FILL.setColor(0x99F3EBDD);
        c.drawRect(0f, 0f, Stage.W, Stage.H, FILL);
        Theme.drawBanner(c, "Great job!", Stage.W / 2f, 190f, 96f, 0xE6F3EBDD, Theme.INK);
        for (int p = 0; p < players; p++) {
            float cx = players == 1 ? Stage.W / 2f : (p == 0 ? 600f : 1320f);
            int n = Math.min(shownStars, stars[p]);
            float pop = (endAnim % 0.55f) / 0.55f;
            float sc = 2.1f + (n > 0 && n == shownStars && pop < 0.3f ? 0.25f * (1f - pop / 0.3f) : 0f);
            android.graphics.Bitmap b = Theme.starsBadge(Math.max(0, n));
            float w = b.getWidth() * sc, h = b.getHeight() * sc;
            R.set(cx - w / 2f, 330f - h / 2f, cx + w / 2f, 330f + h / 2f);
            c.drawBitmap(b, null, R, null);
            if (players == 2) {
                Theme.drawCentered(c, p == 0 ? "Player 1" : "Player 2", cx, 480f, 48f, Theme.playerColour(p));
            }
            Theme.drawCentered(c, NUM[Math.max(0, Math.min(MAX_NUM, finalScore[p]))], cx, players == 2 ? 570f : 500f,
                    players == 2 ? 80f : 96f, Theme.INK);
        }
        if (menuReady) menu.draw(c);
    }
}
