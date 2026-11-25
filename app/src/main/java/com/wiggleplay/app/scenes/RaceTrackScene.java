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
    private static final float CARD_Y = 220f, DIR_Y = 850f;
    private static final float[] CARD_X = {90f, 530f, 970f, 1410f};

    private final TileMenu.Env env;
    private final TileMenu menu;
    private Bitmap camera;
    private float time;
    private boolean reversed;
    private final TileMenu.Tile[] cards = new TileMenu.Tile[4];
    private TileMenu.Tile thisWay, otherWay;

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
        thisWay = new TileMenu.Tile("dir_this", "This way", null, Theme.PAPER, null, 630f, DIR_Y, 300f, 110f);
        otherWay = new TileMenu.Tile("dir_other", "Other way", null, Theme.PAPER, null, 990f, DIR_Y, 300f, 110f);
        thisWay.setCustom(directionBitmap(false));
        otherWay.setCustom(directionBitmap(true));
        tiles.add(thisWay);
        tiles.add(otherWay);
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

    private Bitmap directionBitmap(final boolean other) {
        final boolean ticked = other == reversed;
        return SpriteCache.shared().get(CARD_PREFIX + "dir_" + (other ? "o" : "t") + (ticked ? "1" : "0"), 300, 110,
                (c, w, h) -> TrackCardArt.paintDirection(c, other, ticked));
    }

    private void showDirection() {
        for (int i = 0; i < cards.length; i++) cards[i].setCustom(cardBitmap(TrackDefs.ALL[i]));
        thisWay.setCustom(directionBitmap(false));
        otherWay.setCustom(directionBitmap(true));
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
            env.go("gameselect", SceneArgs.EMPTY);
            return;
        }
        if ("settings".equals(sel)) {
            env.sfx("select");
            if (env.settings != null) env.settings.open();
            return;
        }
        if (sel == null) return;
        if ("dir_this".equals(sel) || "dir_other".equals(sel)) {
            boolean want = "dir_other".equals(sel);
            if (want != reversed) {
                reversed = want;
                if (env.profile != null) env.profile.setRaceReversed(reversed);
                showDirection();
            }
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
                env.go("players", SceneArgs.of("gameId", "race"));
                return;
            }
        }
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, "Pick a track", Stage.W / 2f, 110f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
        Theme.drawBanner(c, "Hold your hand on a track to pick it", Stage.W / 2f, 1036f, 40f, 0xE6F3EBDD, Theme.INK);
    }
}
