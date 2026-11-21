package com.angga.movetv.scenes;

import android.graphics.Canvas;
import android.view.KeyEvent;

import com.angga.movetv.core.Ease;
import com.angga.movetv.core.Scene;
import com.angga.movetv.core.Stage;
import com.angga.movetv.ui.RoundButton;
import com.angga.movetv.ui.Theme;
import com.angga.movetv.ui.TileMenu;
import com.angga.movetv.ui.puppet.Mascots;
import com.angga.movetv.ui.puppet.Puppet;
import com.angga.movetv.ui.puppet.PuppetPose;

import java.util.ArrayList;
import java.util.List;

/**
 * "How many are playing?": two huge tiles, 1 Player (one kid) and 2 Players (both kids side by side). The
 * kids wave now and then, and at once when their tile is hovered. Settings open with the round gear button
 * top right (or the menu key on the remote).
 */
public final class PlayersScene implements Scene {

    private static final String T_TITLE = "How many are playing?";

    private final TileMenu.Env env;
    private final TileMenu menu;
    private android.graphics.Bitmap camera;
    private float time;
    private TileMenu.Tile tile1, tile2;
    private Puppet one, twoA, twoB;
    private float nextWave1, nextWave2;
    private boolean hover1, hover2;
    private final float[] cur = new float[2];

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
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        tiles.add(RoundButton.settings());
        float w = 560f, h = 620f, gap = 80f, x0 = (Stage.W - 2f * w - gap) / 2f, y = 200f;
        tile1 = new TileMenu.Tile("p1", "1 Player", null, Theme.SAGE, null, x0, y, w, h);
        tile2 = new TileMenu.Tile("p2", "2 Players", null, Theme.PINK, null, x0 + w + gap, y, w, h);
        tiles.add(tile1);
        tiles.add(tile2);
        menu.setTiles(tiles, env.players == 2 ? "p2" : "p1");
        one = Mascots.create(Mascots.nextKid());
        twoA = Mascots.create(Puppet.KID_A);
        twoB = Mascots.create(Puppet.KID_B);
        nextWave1 = 1.0f;
        nextWave2 = 2.2f;
        hover1 = false;
        hover2 = false;
    }

    @Override
    public void exit() { }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        String sel = menu.update(dt, f);
        one.update(dt);
        twoA.update(dt);
        twoB.update(dt);
        if (env.speaking()) {
            if (tile1.hoverAmount() > 0.3f) one.anim().talk(0.1f);
            if (tile2.hoverAmount() > 0.3f) {
                twoA.anim().talk(0.1f);
                twoB.anim().talk(0.1f);
            }
        }
        boolean has = menu.cursorPosition(cur);
        Mascots.look(one, tile1.x + tile1.w / 2f, tile1.y + 200f, has, cur[0], cur[1]);
        Mascots.look(twoA, tile2.x + tile2.w / 2f - 130f, tile2.y + 200f, has, cur[0], cur[1]);
        Mascots.look(twoB, tile2.x + tile2.w / 2f + 130f, tile2.y + 200f, has, cur[0], cur[1]);
        // hovering a tile makes its kid(s) wave at once; otherwise they wave every few seconds
        boolean h1 = tile1.hoverAmount() > 0.5f, h2 = tile2.hoverAmount() > 0.5f;
        if (h1 && !hover1) one.anim().wave(PuppetPose.LEFT);
        if (h2 && !hover2) {
            twoA.anim().wave(PuppetPose.LEFT);
            twoB.anim().wave(PuppetPose.RIGHT);
        }
        hover1 = h1;
        hover2 = h2;
        nextWave1 -= dt;
        nextWave2 -= dt;
        if (nextWave1 <= 0f) {
            if (!one.anim().busy()) one.anim().wave(PuppetPose.LEFT);
            nextWave1 = 4f + (time * 13f % 2f);
        }
        if (nextWave2 <= 0f) {
            if (!twoA.anim().busy() && !twoB.anim().busy()) {
                // alternately: A waves, then B
                if (((int) (time * 3f)) % 2 == 0) twoA.anim().wave(PuppetPose.LEFT);
                else twoB.anim().wave(PuppetPose.RIGHT);
            }
            nextWave2 = 2.6f + (time * 11f % 1.5f);
        }
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) || "back".equals(sel)) {
            env.sfx("back");
            env.go("setup", SceneArgs.of("back", 1));
            return;
        }
        if ("settings".equals(sel)) {
            if (env.settings != null) env.settings.open();
            return;
        }
        if (sel != null) {
            env.setPlayers("p2".equals(sel) ? 2 : 1);
            env.go("gameselect", SceneArgs.EMPTY);
        }
    }

    private void drawKids(Canvas c, TileMenu.Tile t, Puppet a, Puppet b) {
        float s = 1f + 0.08f * Ease.outBack(t.hoverAmount());
        float cx = t.x + t.w / 2f, cy = t.y + t.h / 2f;
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        c.translate(-cx, -cy);
        float foot = t.y + t.h * 0.74f;
        if (b == null) {
            a.draw(c, cx, foot, 400f);
        } else {
            a.draw(c, cx - 125f, foot, 370f);
            b.draw(c, cx + 125f, foot, 370f);
        }
        c.restore();
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, T_TITLE, Stage.W / 2f, 100f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
        drawKids(c, tile1, one, null);
        drawKids(c, tile2, twoA, twoB);
        menu.drawCursors(c);
    }
}
