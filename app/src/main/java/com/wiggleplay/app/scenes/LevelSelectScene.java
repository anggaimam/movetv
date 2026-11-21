package com.wiggleplay.app.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.GameInfo;
import com.wiggleplay.app.games.GameRegistry;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the level of the chosen game: Easy (sprout), Medium (tree) or Hard (mountain), each showing the stars
 * earned so far. Selecting one starts the game's scene with {@code {gameId, level 1|2|3, players 1|2}}.
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
        title = game != null ? game.title + ": pick a level" : "Pick a level";
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        float w = 480f, h = 560f, gap = 64f, x0 = (Stage.W - 3f * w - 2f * gap) / 2f;
        int[] colours = {Theme.MUSTARD, Theme.ORANGE, Theme.PINK};
        com.wiggleplay.app.core.SpriteCache.Painter[] icons = {Theme::paintSprout, Theme::paintTree, Theme::paintMountain};
        for (int i = 0; i < 3; i++) {
            TileMenu.Tile t = new TileMenu.Tile(IDS[i], NAMES[i], null, colours[i], icons[i],
                    x0 + i * (w + gap), 340f, w, h);
            t.badge = Theme.starsBadge(env.profile != null ? env.profile.stars(gameId, i + 1) : 0);
            tiles.add(t);
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
            env.go("gameselect", SceneArgs.EMPTY);
            return;
        }
        if (sel == null) return;
        for (int i = 0; i < 3; i++) {
            if (IDS[i].equals(sel) && game != null) {
                env.go(game.sceneName, SceneArgs.of("gameId", gameId, "level", i + 1, "players", env.players));
                return;
            }
        }
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, title, Stage.W / 2f, 120f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
    }
}
