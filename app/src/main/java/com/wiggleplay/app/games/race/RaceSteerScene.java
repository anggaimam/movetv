package com.wiggleplay.app.games.race;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.wiggleplay.app.core.ProfileLogic;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * T-23: "How do you want to steer?" before every Turbo Trucks race while Angga tries the three steering gestures:
 * Plane arms, Lean or Wheel, each a code-drawn kid picture; the last choice is ticked and has the remote focus.
 * Hold-to-select or remote arrows + OK. The choice is saved in the profile and the race starts with
 * {@code {gameId, level, players, steer}}. When {@link ProfileLogic#RACE_STEER_ASK} is turned off, this screen goes
 * straight to the race with the saved mode.
 */
public final class RaceSteerScene implements Scene {

    private static final String[] IDS = {ProfileLogic.STEER_PLANE, ProfileLogic.STEER_LEAN, ProfileLogic.STEER_WHEEL};
    private static final String[] TITLES = {"Plane arms", "Lean", "Wheel"};
    private static final String[] SUBTITLES = {"Arms out, tilt", "Arms down, lean", "Hands in front, turn"};
    private static final int[] COLOURS = {Theme.BLUE, Theme.SAGE, Theme.ORANGE};

    private final TileMenu.Env env;
    private final TileMenu menu;
    private final List<TileMenu.Tile> picks = new ArrayList<>();
    private Bitmap camera;
    private float time;
    private String gameId = "race";
    private int level = 1, players = 1;
    private boolean skip;

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public RaceSteerScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
    }

    @Override
    public void enter(SceneArgs args) {
        gameId = args.getString("gameId", "race");
        level = args.getInt("level", 1);
        players = args.getInt("players", 1);
        skip = !ProfileLogic.RACE_STEER_ASK;
        if (skip) return;
        env.menuMusic();
        String last = env.profile != null ? env.profile.raceSteer() : ProfileLogic.DEFAULT_RACE_STEER;
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        tiles.add(RoundButton.settings());
        picks.clear();
        float w = 460f, h = 580f, gap = 60f, x0 = (Stage.W - 3f * w - 2f * gap) / 2f;
        for (int i = 0; i < 3; i++) {
            final int m = i;
            SpriteCache.Painter icon = (c, bw, bh) -> RaceArt.paintSteerTile(c, bw, bh, m);
            TileMenu.Tile t = new TileMenu.Tile(IDS[i], TITLES[i], SUBTITLES[i], COLOURS[i], icon,
                    x0 + i * (w + gap), 330f, w, h);
            if (IDS[i].equals(last)) t.cornerBadge = Theme.checkBadge();
            tiles.add(t);
            picks.add(t);
        }
        menu.setTiles(tiles, last);
    }

    @Override
    public void exit() { }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        if (skip) {
            startRace(env.profile != null ? env.profile.raceSteer() : ProfileLogic.DEFAULT_RACE_STEER);
            return;
        }
        String sel = menu.update(dt, f);
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) || "back".equals(sel)) {
            env.sfx("back");
            env.go("levelselect", SceneArgs.of("gameId", gameId));
            return;
        }
        if ("settings".equals(sel)) {
            env.sfx("select");
            if (env.settings != null) env.settings.open();
            return;
        }
        if (sel == null) return;
        for (String id : IDS) {
            if (!id.equals(sel)) continue;
            env.sfx("select");
            if (env.profile != null) env.profile.setRaceSteer(id);
            startRace(id);
            return;
        }
    }

    private void startRace(String steer) {
        env.go("race", SceneArgs.of("gameId", gameId, "level", level, "players", players,
                "steer", ProfileLogic.cleanRaceSteer(steer)));
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        if (skip) return;
        Theme.drawBanner(c, "How do you want to steer?", Stage.W / 2f, 120f, 72f, 0xE6F3EBDD, Theme.INK);
        Theme.drawCentered(c, "Try all three!", Stage.W / 2f, 250f, 44f, Theme.INK);
        menu.draw(c);
    }
}
