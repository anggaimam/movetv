package com.wiggleplay.app.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.wiggleplay.app.core.ProfileLogic;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.GameInfo;
import com.wiggleplay.app.games.GameRegistry;
import com.wiggleplay.app.games.race.RaceArt;
import com.wiggleplay.app.games.race.TrackDef;
import com.wiggleplay.app.games.race.TruckSwatchArt;
import com.wiggleplay.app.games.race.TrackDefs;
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
    private TileMenu.Tile steerTile, truckTile;
    private int truckIdx;
    private int steerMode = RaceArt.MODE_WHEEL;
    private String trackName = "";

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
        if (racing) {   // T-32: the track chosen on the track screen, small at the top
            TrackDef def = TrackDefs.byId(env.profile != null ? env.profile.raceTrack() : null);
            trackName = def.name + (env.profile != null && env.profile.raceReversed() ? " (other way)" : "");
        }
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
            // T-32 Part B: the Truck button, just left of the Steering button; each press cycles yellow, red, green, purple
            truckIdx = ProfileLogic.raceTruckIndex(env.profile != null ? env.profile.raceTruck() : null);
            truckTile = new TileMenu.Tile("truck", "Truck", null, Theme.ORANGE, null, 950f, 20f, 300f, 300f);
            tiles.add(truckTile);
        }
        menu.setTiles(tiles, IDS[0]);
        if (racing) {
            showSteerIcon();
            showTruckIcon();
        }
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
        if (racing && "steer".equals(sel)) {
            steerMode = steerMode == RaceArt.MODE_WHEEL ? RaceArt.MODE_LEAN : RaceArt.MODE_WHEEL;
            if (env.profile != null) env.profile.setRaceSteer(steerMode == RaceArt.MODE_LEAN ? ProfileLogic.STEER_LEAN : ProfileLogic.STEER_WHEEL);
            env.sfx("select");
            showSteerIcon();
            return;
        }
        if (racing && "truck".equals(sel)) {
            truckIdx = (truckIdx + 1) % ProfileLogic.RACE_TRUCKS.length;
            if (env.profile != null) env.profile.setRaceTruck(ProfileLogic.RACE_TRUCKS[truckIdx]);
            env.sfx("select");
            showTruckIcon();
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

    /** Shows the side-view truck in the chosen colour on the Truck button. */
    private void showTruckIcon() {
        final int idx = truckIdx;
        truckTile.setIcon("truck_" + TruckSwatchArt.NAMES[idx], (c, bw, bh) -> TruckSwatchArt.paint(c, bw, bh, idx));
    }

    /** Shows the picture of the chosen steering mode on the button. */
    private void showSteerIcon() {
        final int mode = steerMode;
        steerTile.setIcon(mode == RaceArt.MODE_LEAN ? "lean" : "wheel", (c, bw, bh) -> RaceArt.paintSteerButton(c, bw, bh, mode));
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        // Turbo Trucks: the Truck and Steering buttons take the middle of the top row, so the title sits between Back and the Truck button
        float titleX = racing ? 610f : Stage.W / 2f;
        Theme.drawBanner(c, title, titleX, 120f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
        if (racing) Theme.drawBanner(c, trackName, titleX, 235f, 44f, 0xE6F3EBDD, Theme.INK);   // T-32: the chosen track
    }
}
