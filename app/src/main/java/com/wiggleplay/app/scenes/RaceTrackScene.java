package com.wiggleplay.app.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.race.TrackCardArt;
import com.wiggleplay.app.games.race.TrackChoice;
import com.wiggleplay.app.games.race.TrackDef;
import com.wiggleplay.app.games.race.TrackDefs;
import com.wiggleplay.app.games.race.TrackSpline;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * The Turbo Trucks track screen (T-32 Part A): four cards, one per track, each with a picture of its world, a minimap with the start arrow and
 * the best stars on Easy, Medium and Hard; under them a "This way" / "Other way" pair that applies to all four cards. Choosing a card saves
 * the track and the direction in the profile and goes on to the players and level screens. The cards are baked once per visit into the
 * sprite cache ({@code race_card_} keys) and freed when the screen is left.
 */
public final class RaceTrackScene implements Scene {

    private static final String CARD_PREFIX = "race_card_";
    private static final float CARD_Y = 350f;
    private static final float[] CARD_X = {90f, 530f, 970f, 1410f};

    private final TileMenu.Env env;
    private final TileMenu menu;
    private Bitmap camera;
    private float time;
    private boolean reversed;
    private final TileMenu.Tile[] cards = new TileMenu.Tile[4];
    private TileMenu.Tile dirTile;
    private int level = 1, players = 1;

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public RaceTrackScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
    }

    @Override
    public void enter(SceneArgs args) {
        env.menuMusic();
        level = args.getInt("level", 1);
        players = args.getInt("players", 1);
        reversed = env.profile != null && env.profile.raceReversed();
        String last = env.profile != null ? env.profile.raceTrack() : TrackChoice.def().id;
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        tiles.add(RoundButton.settings());
        for (int i = 0; i < cards.length; i++) {
            TrackDef def = TrackDefs.ALL[i];
            cards[i] = new TileMenu.Tile("t_" + def.id, def.name, null, Theme.PAPER, null, CARD_X[i], CARD_Y, TrackCardArt.W, TrackCardArt.H);
            cards[i].setCustom(cardBitmap(def));
            tiles.add(cards[i]);
        }
        // T-34: one on/off switch for the direction, beside the settings gear
        dirTile = new TileMenu.Tile("dir", "Other way", null, Theme.PAPER, null, 1290f, 20f, 300f, 300f);
        dirTile.setCustom(directionBitmap());
        tiles.add(dirTile);
        menu.setTiles(tiles, "t_" + last);
        env.say("Pick a track");
    }

    /** The card of a track in the ticked direction, with the best stars saved for that direction. */
    private Bitmap cardBitmap(final TrackDef def) {
        final boolean rev = reversed;
        final int[] best = new int[3];
        String key = TrackSpline.key(def, rev);
        for (int l = 0; l < 3; l++) best[l] = env.profile != null ? env.profile.raceBest(key, l + 1) : 0;
        return SpriteCache.shared().get(CARD_PREFIX + key + "_" + best[0] + best[1] + best[2], TrackCardArt.W, TrackCardArt.H,
                (c, w, h) -> TrackCardArt.paint(c, def, rev, best));
    }

    private Bitmap directionBitmap() {
        final boolean rev = reversed;
        return SpriteCache.shared().get(CARD_PREFIX + "dir_" + (rev ? "1" : "0"), 300, 300,
                (c, w, h) -> TrackCardArt.paintDirectionToggle(c, w, h, rev));
    }

    private void showDirection() {
        for (int i = 0; i < cards.length; i++) cards[i].setCustom(cardBitmap(TrackDefs.ALL[i]));
        dirTile.setCustom(directionBitmap());
    }

    @Override
    public void exit() {
        SpriteCache.shared().removePrefix(CARD_PREFIX);
    }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        String sel = menu.update(dt, f);
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) || "back".equals(sel)) {
            env.sfx("back");
            env.go("levelselect", SceneArgs.of("gameId", "race", "players", players));
            return;
        }
        if ("settings".equals(sel)) {
            env.sfx("select");
            if (env.settings != null) env.settings.open();
            return;
        }
        if (sel == null) return;
        if ("dir".equals(sel)) {
            reversed = !reversed;
            if (env.profile != null) env.profile.setRaceReversed(reversed);
            showDirection();
            env.sfx("select");
            return;
        }
        for (int i = 0; i < TrackDefs.ALL.length; i++) {
            if (("t_" + TrackDefs.ALL[i].id).equals(sel)) {
                if (env.profile != null) {
                    env.profile.setRaceTrack(TrackDefs.ALL[i].id);
                    env.profile.setRaceReversed(reversed);
                }
                TrackChoice.set(i * 2 + (reversed ? 1 : 0));
                env.sfx("select");
                env.go("racetruck", SceneArgs.of("gameId", "race", "level", level, "players", players));
                return;
            }
        }
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, "Pick a track", 700f, 110f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
        Theme.drawBanner(c, "Hold your hand on a track to pick it", Stage.W / 2f, 1036f, 40f, 0xE6F3EBDD, Theme.INK);
    }
}
