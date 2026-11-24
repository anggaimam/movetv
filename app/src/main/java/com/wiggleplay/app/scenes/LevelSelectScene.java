package com.wiggleplay.app.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;

import com.wiggleplay.app.core.ProfileLogic;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.GameInfo;
import com.wiggleplay.app.games.GameRegistry;
import com.wiggleplay.app.games.race.RaceArt;
import com.wiggleplay.app.games.race.TrackChoice;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the level of the chosen game: Easy (sprout), Medium (tree) or Hard (mountain), each showing the stars
 * earned so far. Selecting one starts the game's scene with {@code {gameId, level 1|2|3, players 1|2}}.
 * Turbo Trucks also shows one steering button at the top, beside the settings gear (T-23 Part B2): each press switches
 * between a steering wheel and a body (lean), saves it in the profile, and the race starts with that {@code steer}.
 */
public final class LevelSelectScene implements Scene {

    private static final String[] IDS = {"level1", "level2", "level3"};
    private static final String[] NAMES = {"Easy", "Medium", "Hard"};

    private final TileMenu.Env env;
    private final TileMenu menu;
    private Bitmap camera;
    private float time;
    private String gameId = "";
    private GameInfo game;
    private String title = "";
    private int players = 1;
    private boolean racing;
    private TileMenu.Tile steerTile;
    private int steerMode = RaceArt.MODE_WHEEL;

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public LevelSelectScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
    }

    @Override
    public void enter(SceneArgs args) {
        env.menuMusic();
        gameId = args.getString("gameId", "");
        game = GameRegistry.get(gameId);
        int asked = args.getInt("players", -1);   // coming back from a game: the count last picked for it
        int max = game != null ? game.maxPlayers : 2;
        players = asked > 0 ? asked : env.profile != null ? env.profile.playersFor(gameId, max, env.players) : env.players;
        title = game != null && !"race".equals(gameId) ? game.title + ": pick a level" : "Pick a level";   // Turbo Trucks: the steering button sits where a long title would run
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        tiles.add(RoundButton.settings());
        racing = "race".equals(gameId);
        float w = 480f, h = 560f, gap = 64f, x0 = (Stage.W - 3f * w - 2f * gap) / 2f;
        int[] colours = {Theme.MUSTARD, Theme.ORANGE, Theme.PINK};
        com.wiggleplay.app.core.SpriteCache.Painter[] icons = {Theme::paintSprout, Theme::paintTree, Theme::paintMountain};
        for (int i = 0; i < 3; i++) {
            TileMenu.Tile t = new TileMenu.Tile(IDS[i], NAMES[i], null, colours[i], icons[i],
                    x0 + i * (w + gap), 340f, w, h);
            t.badge = Theme.starsBadge(env.profile != null ? env.profile.stars(gameId, i + 1) : 0);
            tiles.add(t);
        }
        if (racing) {
            // T-23 B2 (second try): ONE button at the top, beside the settings gear, that cycles wheel -> body -> wheel
            steerMode = ProfileLogic.STEER_LEAN.equals(env.profile != null ? env.profile.raceSteer() : ProfileLogic.DEFAULT_RACE_STEER)
                    ? RaceArt.MODE_LEAN : RaceArt.MODE_WHEEL;
            steerTile = new TileMenu.Tile("steer", "Steering", null, Theme.ORANGE, null, 1290f, 20f, 300f, 300f);
            tiles.add(steerTile);
        }
        menu.setTiles(tiles, IDS[0]);
        if (racing) showSteerIcon();
    }

    @Override
    public void exit() { }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        String sel = menu.update(dt, f);
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) || "back".equals(sel)) {
            env.sfx("back");
            env.go("players", SceneArgs.of("gameId", gameId));
            return;
        }
        if ("settings".equals(sel)) {
            env.sfx("select");
            if (env.settings != null) env.settings.open();
            return;
        }
        if (racing && f.input.pressed(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)) {
            TrackChoice.next();   // T-28 Part D: temporary, until T-32 brings the track cards
            env.sfx("select");
        }
        if (sel == null) return;
        if (racing && "steer".equals(sel)) {
            steerMode = steerMode == RaceArt.MODE_WHEEL ? RaceArt.MODE_LEAN : RaceArt.MODE_WHEEL;
            if (env.profile != null) env.profile.setRaceSteer(steerMode == RaceArt.MODE_LEAN ? ProfileLogic.STEER_LEAN : ProfileLogic.STEER_WHEEL);
            env.sfx("select");
            showSteerIcon();
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (IDS[i].equals(sel) && game != null) {
                if (racing) {
                    String steer = env.profile != null ? env.profile.raceSteer() : ProfileLogic.DEFAULT_RACE_STEER;
                    env.go(game.sceneName, SceneArgs.of("gameId", gameId, "level", i + 1, "players", players,
                            "steer", steer));
                } else {
                    env.go(game.sceneName, SceneArgs.of("gameId", gameId, "level", i + 1, "players", players));
                }
                return;
            }
        }
    }

    /** Shows the picture of the chosen steering mode on the button. */
    private void showSteerIcon() {
        final int mode = steerMode;
        steerTile.setIcon(mode == RaceArt.MODE_LEAN ? "lean" : "wheel", (c, bw, bh) -> RaceArt.paintSteerButton(c, bw, bh, mode));
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, title, Stage.W / 2f, 120f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
        if (racing) drawTrackChip(c);
    }

    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chipRect = new RectF();

    /**
     * Temporary track chip under the level tiles (T-28 Part D): the chosen track and its direction. The remote's
     * fast-forward key cycles the 8 tracks. The direction is drawn as a round arrow (counter-clockwise for the track as
     * designed, clockwise reversed) because the font has no such arrow characters.
     */
    private void drawTrackChip(Canvas c) {
        float cx = Stage.W / 2f, cy = 985f;
        Theme.drawBanner(c, "Track: " + TrackChoice.name(), cx, cy, 52f, 0xE6F3EBDD, Theme.INK);
        float ax = cx + 400f, r = 30f;
        chipPaint.setStyle(Paint.Style.FILL);
        chipPaint.setColor(0xE6F3EBDD);
        c.drawCircle(ax, cy, r + 14f, chipPaint);
        chipPaint.setStyle(Paint.Style.STROKE);
        chipPaint.setStrokeWidth(8f);
        chipPaint.setStrokeCap(Paint.Cap.ROUND);
        chipPaint.setColor(Theme.INK);
        chipRect.set(ax - r, cy - r, ax + r, cy + r);
        boolean reversed = TrackChoice.reversed();
        // 270 degree arc with an arrow head at its end; reversed drives clockwise
        c.drawArc(chipRect, reversed ? -60f : 240f, reversed ? 270f : -270f, false, chipPaint);
        double a = Math.toRadians(reversed ? 210f : -30f);   // end of the arc
        float ex = ax + r * (float) Math.cos(a), ey = cy + r * (float) Math.sin(a);
        float dir = reversed ? 1f : -1f;
        double t = a + dir * Math.PI / 2.0;                   // direction of travel at the end
        float tx = (float) Math.cos(t), ty = (float) Math.sin(t);
        float nx = -ty, ny = tx;
        chipPaint.setStyle(Paint.Style.FILL);
        android.graphics.Path head = chipHead;
        head.rewind();
        head.moveTo(ex + tx * 16f, ey + ty * 16f);
        head.lineTo(ex - tx * 6f + nx * 14f, ey - ty * 6f + ny * 14f);
        head.lineTo(ex - tx * 6f - nx * 14f, ey - ty * 6f - ny * 14f);
        head.close();
        c.drawPath(head, chipPaint);
    }

    private final android.graphics.Path chipHead = new android.graphics.Path();
}
