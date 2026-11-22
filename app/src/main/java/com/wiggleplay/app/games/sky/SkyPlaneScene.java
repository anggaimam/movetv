package com.wiggleplay.app.games.sky;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the plane for Sky Glider after the level: propeller, airliner, jet or spaceship. With two players each one
 * picks in turn. The colours of the plane change by themselves every round. Then the game starts with
 * {@code {gameId, level, players, plane, plane2}}.
 */
public final class SkyPlaneScene implements Scene {

    private static final String[] IDS = {"plane0", "plane1", "plane2", "plane3"};
    private static final int[] COLOURS = {Theme.MUSTARD, Theme.BLUE, Theme.SAGE, Theme.PINK};

    private final TileMenu.Env env;
    private final TileMenu menu;
    private Bitmap camera;
    private float time;
    private String gameId = "sky";
    private int level = 1, players = 1, turn;
    private int first;

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public SkyPlaneScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
    }

    @Override
    public void enter(SceneArgs args) {
        env.menuMusic();
        gameId = args.getString("gameId", "sky");
        level = args.getInt("level", 1);
        players = args.getInt("players", env.players) == 2 ? 2 : 1;
        turn = 0;
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        tiles.add(RoundButton.settings());
        float w = 400f, h = 560f, gap = 48f, x0 = (Stage.W - 4f * w - 3f * gap) / 2f;
        for (int i = 0; i < PlaneModel.COUNT; i++) {
            final int type = i;
            SpriteCache.Painter icon = (c, bw, bh) -> paintIcon(c, bw, bh, type);
            tiles.add(new TileMenu.Tile(IDS[i], PlaneModel.NAMES[i], null, COLOURS[i], icon,
                    x0 + i * (w + gap), 340f, w, h));
        }
        menu.setTiles(tiles, IDS[SkyScene.lastPlane % PlaneModel.COUNT]);
    }

    /** The picture on a tile: the plane a little turned, in its first colours. */
    static void paintIcon(Canvas c, int w, int h, int type) {
        float scale = 0.85f * w / (4.9f * PlaneModel.SIZE[type]);
        PlaneDraw.shared().draw(c, type, 0, 14f, 18f, 30f, w / 2f, h * 0.5f, scale);
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
            if (turn == 1) {
                turn = 0;
            } else {
                env.go("levelselect", SceneArgs.of("gameId", gameId));
            }
            return;
        }
        if ("settings".equals(sel)) {
            env.sfx("select");
            if (env.settings != null) env.settings.open();
            return;
        }
        if (sel == null) return;
        for (int i = 0; i < PlaneModel.COUNT; i++) {
            if (!IDS[i].equals(sel)) continue;
            if (players == 2 && turn == 0) {
                first = i;
                turn = 1;
                return;
            }
            SkyScene.lastPlane = players == 2 ? first : i;
            int second = players == 2 ? i : (i + 1) % PlaneModel.COUNT;
            env.go("sky", SceneArgs.of("gameId", gameId, "level", level, "players", players,
                    "plane", players == 2 ? first : i, "plane2", second));
            return;
        }
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        String title = players == 2 ? "Player " + (turn + 1) + ": pick a plane" : "Pick your plane";
        Theme.drawBanner(c, title, Stage.W / 2f, 120f, 72f, 0xE6F3EBDD, Theme.INK);
        Theme.drawCentered(c, "The colours change every flight", Stage.W / 2f, 250f, 44f, Theme.INK);
        menu.draw(c);
    }
}
