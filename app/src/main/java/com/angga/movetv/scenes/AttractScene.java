package com.angga.movetv.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.angga.movetv.core.Scene;
import com.angga.movetv.core.SpriteCache;
import com.angga.movetv.core.Stage;
import com.angga.movetv.ui.Theme;
import com.angga.movetv.ui.TileMenu;
import com.angga.movetv.ui.puppet.Mascots;
import com.angga.movetv.ui.puppet.Puppet;
import com.angga.movetv.ui.puppet.PuppetPose;

import java.util.Collections;

/**
 * The first screen: the MoveTV logo, a waving kid (Angga's drawing, a puppet) and a pulsing "Raise your hand to start!". Any raised
 * hand for half a second (or OK / Play on the remote) goes to the set-up screen. Back does nothing here.
 */
public final class AttractScene implements Scene {

    private static final long SHOW_MS = 500;
    private static final String HINT = "Raise your hand to start!";
    private static final String HINT_TEST = "Test mode: drag the mouse, or press OK";

    private final TileMenu.Env env;
    private final TileMenu menu;
    private Bitmap camera;
    private boolean test;
    private float time;
    private float visibleMs;
    private Bitmap logo;
    private Puppet kid;
    private float nextWave;
    private float leaveIn = -1f;
    private final float[] cur = new float[2];
    private static final float KID_X = 1560f, KID_FOOT = 880f, KID_H = 600f;

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public AttractScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
    }

    @Override
    public void enter(SceneArgs args) {
        env.menuMusic();
        menu.setTiles(Collections.<TileMenu.Tile>emptyList(), null);
        visibleMs = 0f;
        leaveIn = -1f;
        kid = Mascots.create(Mascots.nextKid());
        nextWave = 0.6f;
        logo = SpriteCache.shared().get("attract_logo", 1000, 280, (c, w, h) -> {
            Theme.drawCentered(c, "MoveTV", w / 2f + 10f, h * 0.80f + 10f, 230f, Theme.darker(Theme.ORANGE));
            Theme.drawCentered(c, "MoveTV", w / 2f, h * 0.80f, 230f, Theme.ORANGE);
            Theme.drawCentered(c, "MoveTV", w / 2f - 4f, h * 0.80f - 4f, 230f, 0x33FFFFFF);
        });
    }

    @Override
    public void exit() { }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        test = "test".equals(f.pose.source);
        menu.update(dt, f);
        kid.update(dt);
        if (env.speaking()) kid.anim().talk(0.1f);
        boolean has = menu.cursorPosition(cur);
        Mascots.look(kid, KID_X, KID_FOOT - KID_H * 0.8f, has, cur[0], cur[1]);
        if (leaveIn >= 0f) {
            leaveIn -= dt;
            if (leaveIn <= 0f) env.go("setup", SceneArgs.EMPTY);
            return;
        }
        nextWave -= dt;
        if (nextWave <= 0f && !kid.anim().busy()) {
            kid.anim().wave(PuppetPose.RIGHT);   // the arm on the outer side of the screen
            nextWave = 3f + (time * 7f % 1f);
        }
        visibleMs = menu.cursorVisible() ? visibleMs + dt * 1000f : 0f;
        boolean ok = f.input.pressed(KeyEvent.KEYCODE_DPAD_CENTER) || f.input.pressed(KeyEvent.KEYCODE_ENTER)
                || f.input.pressed(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) || f.input.pressed(KeyEvent.KEYCODE_MEDIA_PLAY)
                || f.input.pressed(KeyEvent.KEYCODE_MEDIA_PAUSE);
        if (visibleMs >= SHOW_MS || ok) {
            env.sfx("select");
            kid.anim().cheer();
            leaveIn = 0.9f;
        }
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        c.drawBitmap(logo, (Stage.W - logo.getWidth()) / 2f, 70f, null);
        kid.draw(c, KID_X, KID_FOOT, KID_H);
        float pulse = 1f + 0.05f * (float) Math.sin(time * 4f);
        c.save();
        c.scale(pulse, pulse, Stage.W / 2f, 930f);
        Theme.drawBanner(c, test ? HINT_TEST : HINT, Stage.W / 2f, 930f, 48f, 0xE6F3EBDD, Theme.INK);
        c.restore();
        menu.draw(c);
    }
}
