package com.angga.movetv.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.angga.movetv.core.Scene;
import com.angga.movetv.core.Stage;
import com.angga.movetv.games.GameInfo;
import com.angga.movetv.games.GameRegistry;
import com.angga.movetv.ui.RoundButton;
import com.angga.movetv.ui.Theme;
import com.angga.movetv.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a game. Up to 4 games stand in one row of big tiles (400 x 520); 5 to 8 games use two rows of 4
 * (360 x 340, still well over 240 px tall, no scrolling). Games that cannot take the chosen number of
 * players are hidden. A Home tile (hold 1.5 s) at the top left goes back to the players screen.
 */
public final class GameSelectScene implements Scene {

    static final float HOME_X = 48f, HOME_Y = 40f, HOME_W = 300f, HOME_H = 250f;
    private static final String T_TITLE = "Pick a game!";

    private final TileMenu.Env env;
    private final TileMenu menu;
    private Bitmap camera;
    private float time;

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public GameSelectScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
    }

    /**
     * Places the game tiles.
     * @param games the games to show
     * @return one tile per game, placed (no home tile)
     */
    static List<TileMenu.Tile> layout(List<GameInfo> games) {
        List<TileMenu.Tile> out = new ArrayList<>();
        int n = games.size();
        if (n <= 4) {
            float w = 400f, h = 520f, gap = 48f;
            float x0 = (Stage.W - (n * w + (n - 1) * gap)) / 2f;
            for (int i = 0; i < n; i++) out.add(tile(games.get(i), x0 + i * (w + gap), 340f, w, h));
        } else {
            float w = 360f, h = 340f, gap = 48f, rowGap = 40f;
            for (int i = 0; i < n && i < 8; i++) {
                int row = i / 4, inRow = Math.min(4, n - row * 4);
                float x0 = (Stage.W - (inRow * w + (inRow - 1) * gap)) / 2f;
                out.add(tile(games.get(i), x0 + (i % 4) * (w + gap), 320f + row * (h + rowGap), w, h));
            }
        }
        return out;
    }

    private static TileMenu.Tile tile(GameInfo g, float x, float y, float w, float h) {
        return new TileMenu.Tile(g.id, g.title, null, g.color, g.icon, x, y, w, h);
    }

    @Override
    public void enter(SceneArgs args) {
        env.menuMusic();
        List<GameInfo> games = GameRegistry.forPlayers(env.players);
        List<TileMenu.Tile> tiles = new ArrayList<>(layout(games));
        tiles.add(0, RoundButton.back());
        menu.setTiles(tiles, games.isEmpty() ? "back" : games.get(0).id);
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
            env.go("players", SceneArgs.EMPTY);
            return;
        }
        if (sel != null) env.go("levelselect", SceneArgs.of("gameId", sel));
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, T_TITLE, Stage.W / 2f, 120f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
    }
}
