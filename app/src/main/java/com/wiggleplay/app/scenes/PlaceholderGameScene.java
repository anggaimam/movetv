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
 * Stands in for every game that is not built yet: shows the chosen game, level and player count, and a Home
 * tile. Back goes to the level screen, like Back during a real round will.
 */
public final class PlaceholderGameScene implements Scene {

    private static final String[] LEVELS = {"Easy", "Medium", "Hard"};

    private final TileMenu.Env env;
    private final TileMenu menu;
    private Bitmap camera;
    private float time;
    private String gameId = "";
    private String name = "", detail = "";

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public PlaceholderGameScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
    }

    @Override
    public void enter(SceneArgs args) {
        env.leaveMenus();
        gameId = args.getString("gameId", "");
        int level = Math.max(1, Math.min(3, args.getInt("level", 1)));
        int players = args.getInt("players", 1) == 2 ? 2 : 1;
        GameInfo g = GameRegistry.get(gameId);
        name = g != null ? g.title : "Game";
        detail = LEVELS[level - 1] + "  -  " + players + (players == 1 ? " player" : " players");
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.home());
        menu.setTiles(tiles, "home");
    }

    @Override
    public void exit() { }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        String sel = menu.update(dt, f);
        if (f.input.pressed(KeyEvent.KEYCODE_BACK)) {
            env.sfx("back");
            env.go("levelselect", SceneArgs.of("gameId", gameId));
        } else if ("home".equals(sel)) {
            env.go("gameselect", SceneArgs.EMPTY);
        }
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, name, Stage.W / 2f, 420f, 96f, 0xE6F3EBDD, Theme.INK);
        Theme.drawBanner(c, detail, Stage.W / 2f, 600f, 56f, 0xE6F3EBDD, Theme.INK);
        Theme.drawBanner(c, "This game is coming soon!", Stage.W / 2f, 780f, 48f, 0xCCF3EBDD, Theme.INK);
        menu.draw(c);
    }
}
