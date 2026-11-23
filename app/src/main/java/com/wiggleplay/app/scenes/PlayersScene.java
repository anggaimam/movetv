package com.wiggleplay.app.scenes;

import android.graphics.Canvas;
import android.view.KeyEvent;

import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.GameInfo;
import com.wiggleplay.app.games.GameRegistry;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * "How many players?", asked for one game after it is picked (before its levels). The side-by-side games offer
 * 1 and 2; turn-based bowling offers 1 to 4 with a space avatar per player, on a dark space backdrop. Each game
 * remembers what was picked last and starts on it.
 * Two huge tiles, 1 Player and 2 Players. Holding both hands up for 3 seconds opens
 * the settings (on the remote, the menu key does that on any screen).
 */
public final class PlayersScene implements Scene {

    static final long SETTINGS_MS = 3000;
    private static final String T_TITLE = "How many players?";
    private static final String T_TURNS = "Everyone takes turns";
    private static final String[] WORD = {"player", "players", "players", "players"};
    private static final String T_HINT = "Both hands up for 3 seconds, or the gear: settings";

    private final TileMenu.Env env;
    private final TileMenu menu;
    private android.graphics.Bitmap camera;
    private float time;
    private float bothUpMs;
    private String gameId = "";
    private boolean turns, redirect;

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public PlayersScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
    }

    @Override
    public void enter(SceneArgs args) {
        env.menuMusic();
        bothUpMs = 0f;
        gameId = args.getString("gameId", "");
        GameInfo game = GameRegistry.get(gameId);
        redirect = game == null;                   // nothing picked yet: the game menu comes first (in update)
        if (redirect) return;
        turns = game.turnBased;
        int max = turns ? Math.min(4, game.maxPlayers) : Math.min(2, game.maxPlayers);
        int last = env.profile != null ? env.profile.playersFor(gameId, max, Math.min(max, env.players)) : 1;
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        tiles.add(RoundButton.settings());
        if (turns) {
            // one tile per count, each showing that many avatars, so "4" plainly means four players
            float w = 380f, h = 470f, gap = 40f, x0 = (Stage.W - max * w - (max - 1) * gap) / 2f;
            for (int i = 0; i < max; i++) {
                final int n = i + 1;
                tiles.add(new TileMenu.Tile("p" + n, String.valueOf(n), WORD[i], 0xFF26215C,
                        (c, bw, bh) -> com.wiggleplay.app.games.bowling.BowlingArt.paintGroup(c, bw, bh, n),
                        x0 + i * (w + gap), 250f, w, h));
            }
        } else {
            float w = 560f, h = 600f, gap = 80f, x0 = (Stage.W - 2f * w - gap) / 2f, y = 200f;
            tiles.add(new TileMenu.Tile("p1", "1 Player", null, Theme.SAGE, Theme::paintOneMascot, x0, y, w, h));
            if (max >= 2) {
                tiles.add(new TileMenu.Tile("p2", "2 Players", null, Theme.PINK, Theme::paintTwoMascots, x0 + w + gap,
                        y, w, h));
            }
        }
        menu.setTiles(tiles, "p" + last);
        env.say(T_TITLE);
    }

    @Override
    public void exit() { }

    /** Whether both wrists are above the shoulder line (both hands up). */
    static boolean bothHandsUp(PlayerPose p) {
        if (p == null || !p.present) return false;
        Keypoint lw = p.kps[Kp.L_WRIST], rw = p.kps[Kp.R_WRIST];
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        if (lw.s < 0.3f || rw.s < 0.3f || ls.s < 0.3f || rs.s < 0.3f) return false;
        float line = Math.min(ls.y, rs.y);
        return lw.y < line && rw.y < line;
    }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        if (redirect) {
            redirect = false;
            env.go("gameselect", SceneArgs.EMPTY);
            return;
        }
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
        if (sel != null && sel.length() == 2 && sel.charAt(0) == 'p') {
            int n = sel.charAt(1) - '0';
            if (env.profile != null) env.profile.setPlayersFor(gameId, n);
            if (!turns) env.setPlayers(n);          // side by side: the camera follows 1 or 2 people
            env.go("levelselect", SceneArgs.of("gameId", gameId, "players", n));
            return;
        }
        boolean up = false;
        for (int i = 0; i < 2 && i < f.pose.mode; i++) if (bothHandsUp(f.pose.players[i])) up = true;
        bothUpMs = up ? bothUpMs + dt * 1000f : 0f;
        if (bothUpMs >= SETTINGS_MS) {
            bothUpMs = 0f;
            if (env.settings != null) env.settings.open();
        }
    }

    @Override
    public void draw(Canvas c) {
        if (turns) {
            c.drawBitmap(com.wiggleplay.app.games.bowling.BowlingArt.space(), 0f, 0f, null);
            Theme.drawCentered(c, T_TITLE, Stage.W / 2f, 130f, 84f, 0xFFFFFFFF);
            Theme.drawCentered(c, T_TURNS, Stage.W / 2f, 205f, 46f, 0xFFCECBF6);
            menu.draw(c);
            return;
        }
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, T_TITLE, Stage.W / 2f, 100f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
        Theme.drawBanner(c, T_HINT, Stage.W / 2f, 960f, 40f, 0xCCF3EBDD, Theme.INK);
    }
}
