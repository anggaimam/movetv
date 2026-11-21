package com.wiggleplay.app.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.Collections;

/**
 * The first screen: the Wiggle Play logo, a waving mascot and a pulsing "Raise your hand to start!". Any raised
 * hand for half a second (or OK / Play on the remote) goes to the set-up screen. Back does nothing here.
 */
public final class AttractScene implements Scene {

    private static final long SHOW_MS = 500;
    private static final String HINT = "Raise your hand to start!";
    private static final String HINT_TEST = "Test mode: drag the mouse, or press OK";

    private final TileMenu.Env env;
    private final TileMenu menu;
    private boolean test;
    private float time;
    private float visibleMs;
    private Bitmap logo;

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
        logo = SpriteCache.shared().get("attract_logo", 1500, 280, (c, w, h) -> {
            Theme.drawCentered(c, "Wiggle Play", w / 2f + 10f, h * 0.80f + 10f, 200f, Theme.darker(Theme.ORANGE));
            Theme.drawCentered(c, "Wiggle Play", w / 2f, h * 0.80f, 200f, Theme.ORANGE);
            Theme.drawCentered(c, "Wiggle Play", w / 2f - 4f, h * 0.80f - 4f, 200f, 0x33FFFFFF);
        });
    }

    @Override
    public void exit() { }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        test = "test".equals(f.pose.source);
        menu.update(dt, f);
        visibleMs = menu.cursorVisible() ? visibleMs + dt * 1000f : 0f;
        boolean ok = f.input.pressed(KeyEvent.KEYCODE_DPAD_CENTER) || f.input.pressed(KeyEvent.KEYCODE_ENTER)
                || f.input.pressed(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) || f.input.pressed(KeyEvent.KEYCODE_MEDIA_PLAY)
                || f.input.pressed(KeyEvent.KEYCODE_MEDIA_PAUSE);
        if (visibleMs >= SHOW_MS || ok) {
            env.sfx("select");
            env.go("setup", SceneArgs.EMPTY);
        }
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, null, time); // no camera picture on the home screen
        c.drawBitmap(logo, (Stage.W - logo.getWidth()) / 2f, 70f, null);
        Theme.drawBlinkingBoy(c, Stage.W / 2f, 865f, 480f, 360f, time, 0.5f + 0.5f * (float) Math.sin(time * 4f));
        float pulse = 1f + 0.05f * (float) Math.sin(time * 4f);
        c.save();
        c.scale(pulse, pulse, Stage.W / 2f, 930f);
        Theme.drawBanner(c, test ? HINT_TEST : HINT, Stage.W / 2f, 930f, 48f, 0xE6F3EBDD, Theme.INK);
        c.restore();
        menu.draw(c);
    }
}
