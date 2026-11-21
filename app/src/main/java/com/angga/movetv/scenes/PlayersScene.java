package com.angga.movetv.scenes;

import android.graphics.Canvas;
import android.view.KeyEvent;

import com.angga.movetv.core.Scene;
import com.angga.movetv.core.Stage;
import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.PlayerPose;
import com.angga.movetv.ui.RoundButton;
import com.angga.movetv.ui.Theme;
import com.angga.movetv.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * "How many are playing?": two huge tiles, 1 Player and 2 Players. Holding both hands up for 3 seconds opens
 * the settings (on the remote, the menu key does that on any screen).
 */
public final class PlayersScene implements Scene {

    static final long SETTINGS_MS = 3000;
    private static final String T_TITLE = "How many are playing?";
    private static final String T_HINT = "Both hands up for 3 seconds: settings";

    private final TileMenu.Env env;
    private final TileMenu menu;
    private android.graphics.Bitmap camera;
    private float time;
    private float bothUpMs;

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
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        float w = 560f, h = 620f, gap = 80f, x0 = (Stage.W - 2f * w - gap) / 2f, y = 200f;
        tiles.add(new TileMenu.Tile("p1", "1 Player", null, Theme.SAGE, Theme::paintOneMascot, x0, y, w, h));
        tiles.add(new TileMenu.Tile("p2", "2 Players", null, Theme.PINK, Theme::paintTwoMascots, x0 + w + gap, y, w, h));
        menu.setTiles(tiles, env.players == 2 ? "p2" : "p1");
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
        String sel = menu.update(dt, f);
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) || "back".equals(sel)) {
            env.sfx("back");
            env.go("setup", SceneArgs.of("back", 1));
            return;
        }
        if (sel != null) {
            env.setPlayers("p2".equals(sel) ? 2 : 1);
            env.go("gameselect", SceneArgs.EMPTY);
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
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, T_TITLE, Stage.W / 2f, 100f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
        Theme.drawBanner(c, T_HINT, Stage.W / 2f, 960f, 40f, 0xCCF3EBDD, Theme.INK);
    }
}
