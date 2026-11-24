package com.wiggleplay.app.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.wiggleplay.app.core.ProfileLogic;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.GameInfo;
import com.wiggleplay.app.games.GameRegistry;
import com.wiggleplay.app.games.race.RaceArt;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the level of the chosen game: Easy (sprout), Medium (tree) or Hard (mountain), each showing the stars
 * earned so far. Selecting one starts the game's scene with {@code {gameId, level 1|2|3, players 1|2}}.
 * Turbo Trucks also shows a row with two small tiles under the levels, Wheel and Lean (T-23 Part B2): picking one
 * ticks it and saves it in the profile; the race starts with {@code steer} set to the ticked one.
 */
public final class LevelSelectScene implements Scene {

    private static final String[] IDS = {"level1", "level2", "level3"};
    private static final String[] NAMES = {"Easy", "Medium", "Hard"};
    private static final String[] STEER_IDS = {"steer_wheel", "steer_lean"};
    private static final String[] STEER_NAMES = {"Steer: Wheel", "Steer: Lean"};

    private final TileMenu.Env env;
    private final TileMenu menu;
    private Bitmap camera;
    private float time;
    private String gameId = "";
    private GameInfo game;
    private String title = "";
    private int players = 1;
    private boolean racing;
    private final TileMenu.Tile[] steerTiles = new TileMenu.Tile[2];

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
        title = game != null ? game.title + ": pick a level" : "Pick a level";
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        tiles.add(RoundButton.settings());
        racing = "race".equals(gameId);
        float w = 480f, h = racing ? 460f : 560f, gap = 64f, x0 = (Stage.W - 3f * w - 2f * gap) / 2f;
        int[] colours = {Theme.MUSTARD, Theme.ORANGE, Theme.PINK};
        com.wiggleplay.app.core.SpriteCache.Painter[] icons = {Theme::paintSprout, Theme::paintTree, Theme::paintMountain};
        for (int i = 0; i < 3; i++) {
            TileMenu.Tile t = new TileMenu.Tile(IDS[i], NAMES[i], null, colours[i], icons[i],
                    x0 + i * (w + gap), 340f, w, h);
            t.badge = Theme.starsBadge(env.profile != null ? env.profile.stars(gameId, i + 1) : 0);
            tiles.add(t);
        }
        if (racing) {
            String saved = env.profile != null ? env.profile.raceSteer() : ProfileLogic.DEFAULT_RACE_STEER;
            float sw = 480f, sh = 190f, sgap = 64f, sx = (Stage.W - 2f * sw - sgap) / 2f;
            for (int i = 0; i < 2; i++) {
                final int mode = i == 0 ? RaceArt.MODE_WHEEL : RaceArt.MODE_LEAN;
                SpriteCache.Painter icon = (c, bw, bh) -> RaceArt.paintSteerTile(c, bw, bh, mode);
                TileMenu.Tile t = new TileMenu.Tile(STEER_IDS[i], STEER_NAMES[i], null,
                        i == 0 ? Theme.ORANGE : Theme.SAGE, icon, sx + i * (sw + sgap), 830f, sw, sh);
                steerTiles[i] = t;
                tiles.add(t);
            }
            tickSteer(saved);
        }
        menu.setTiles(tiles, IDS[0]);
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
        if (sel == null) return;
        if (racing) {
            for (int i = 0; i < 2; i++) {
                if (!STEER_IDS[i].equals(sel)) continue;
                String id = i == 0 ? ProfileLogic.STEER_WHEEL : ProfileLogic.STEER_LEAN;
                if (env.profile != null) env.profile.setRaceSteer(id);
                tickSteer(id);
                return;
            }
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

    /** Puts the tick on the chosen steering tile only. */
    private void tickSteer(String chosen) {
        steerTiles[0].cornerBadge = ProfileLogic.STEER_WHEEL.equals(chosen) ? Theme.checkBadge() : null;
        steerTiles[1].cornerBadge = ProfileLogic.STEER_LEAN.equals(chosen) ? Theme.checkBadge() : null;
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, title, Stage.W / 2f, 120f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
    }
}
