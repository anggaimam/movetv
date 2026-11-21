package com.angga.movetv.scenes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;

import com.angga.movetv.core.ProfileLogic;
import com.angga.movetv.core.Scene;
import com.angga.movetv.core.Stage;
import com.angga.movetv.ui.Theme;
import com.angga.movetv.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings panel, drawn over any screen. It holds the hold time (0.7 / 1.0 / 1.4 s), the pointer (on my hand / easy reach), the camera's light
 * boost and the performance overlay, all as big tiles that work with a hand or with the remote. The menu key
 * (or Back, or the Done tile) closes it. It opens with the menu key or the round gear button (top right on Players, Game select and Level select). While it is open the screen underneath is not updated, so a game
 * pauses. Wrap each scene with {@link #host(Scene)} to give it the overlay.
 */
public final class SettingsOverlay {

    private static final long IGNORE_MS = 1500;
    private static final String T_TITLE = "Settings";

    private final TileMenu.Env env;
    private final TileMenu menu;
    private final Paint dim = new Paint();
    private boolean open;
    private float ignoreMs;
    private TileMenu.Tile tBoost, tPerf, tPointer;
    private final List<TileMenu.Tile> holdTiles = new ArrayList<>();

    /**
     * Creates the overlay.
     * @param env what the menu screens share
     */
    public SettingsOverlay(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
        dim.setColor(0xF52F3A45);
    }

    /**
     * Whether the panel is showing.
     * @return true if open
     */
    public boolean isOpen() {
        return open;
    }

    /** Opens the panel. Selections are ignored for the first 1.5 s, so a raised hand does not pick a tile by accident. */
    public void open() {
        if (open) return;
        List<TileMenu.Tile> tiles = new ArrayList<>();
        float w = 400f, h = 380f, gap = 40f, x0 = (Stage.W - 4f * w - 3f * gap) / 2f;
        float x1 = (Stage.W - 3f * w - 2f * gap) / 2f;
        int[] ms = {700, 1000, 1400};
        String[] names = {"0.7 s", "1.0 s", "1.4 s"};
        String[] subs = {"Quick", "Normal", "Slow"};
        holdTiles.clear();
        for (int i = 0; i < 3; i++) {
            TileMenu.Tile t = new TileMenu.Tile("hold" + ms[i], names[i], subs[i], Theme.MUSTARD,
                    Theme.clockPainter(i + 1), x0 + i * (w + gap), 170f, w, h);
            holdTiles.add(t);
            tiles.add(t);
        }
        tPointer = new TileMenu.Tile("pointer", "Pointer", null, Theme.PINK, Theme::paintPointer,
                x0 + 3f * (w + gap), 170f, w, h);
        tiles.add(tPointer);
        tBoost = new TileMenu.Tile("boost", "Light boost", null, Theme.ORANGE, Theme::paintSun, x1, 600f, w, h);
        tPerf = new TileMenu.Tile("perf", "Speed meter", null, Theme.BLUE, Theme::paintGauge, x1 + w + gap, 600f, w, h);
        TileMenu.Tile done = new TileMenu.Tile("done", "Done", null, Theme.SAGE, Theme::paintDone,
                x1 + 2f * (w + gap), 600f, w, h);
        tiles.add(tBoost);
        tiles.add(tPerf);
        tiles.add(done);
        refresh();
        menu.setTiles(tiles, "done");
        ignoreMs = IGNORE_MS;
        open = true;
    }

    /** Closes the panel. */
    public void close() {
        open = false;
    }

    private int currentHold() {
        return env.profile != null ? env.profile.holdMs() : 1000;
    }

    private void refresh() {
        int cur = currentHold();
        int[] ms = {700, 1000, 1400};
        for (int i = 0; i < holdTiles.size(); i++) holdTiles.get(i).cornerBadge = ms[i] == cur ? Theme.checkBadge() : null;
        tPointer.badge = Theme.pointerBadge(ProfileLogic.POINTER_REACH.equals(env.pointer()));
        tBoost.badge = Theme.onOffBadge(env.lightBoost());
        tPerf.badge = Theme.onOffBadge(env.perf.isVisible());
    }

    /**
     * Advances the panel by one frame (only call while it is open).
     * @param dt seconds since the last frame
     * @param f this frame's data
     */
    public void update(float dt, Scene.Frame f) {
        if (!open) return;
        String sel = menu.update(dt, f);
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) || f.input.pressed(KeyEvent.KEYCODE_MENU)) {
            env.sfx("back");
            close();
            return;
        }
        if (ignoreMs > 0f) {
            ignoreMs -= dt * 1000f;
            return;
        }
        if (sel == null) return;
        switch (sel) {
            case "hold700":
                if (env.profile != null) env.profile.setHoldMs(700);
                break;
            case "hold1000":
                if (env.profile != null) env.profile.setHoldMs(1000);
                break;
            case "hold1400":
                if (env.profile != null) env.profile.setHoldMs(1400);
                break;
            case "pointer":
                env.togglePointer();
                break;
            case "boost":
                env.toggleLightBoost();
                break;
            case "perf":
                env.togglePerf();
                break;
            case "done":
                close();
                return;
            default:
                break;
        }
        refresh();
    }

    /**
     * Draws the panel over whatever is already on the canvas.
     * @param c canvas in stage coordinates
     */
    public void draw(Canvas c) {
        if (!open) return;
        c.drawRect(0f, 0f, Stage.W, Stage.H, dim);
        Theme.drawBanner(c, T_TITLE, Stage.W / 2f, 90f, 72f, 0xFFF3EBDD, Theme.INK);
        menu.draw(c);
    }

    /**
     * Wraps a scene so this overlay can open over it: the menu key opens it, the scene is paused while it is
     * open, and it is drawn on top.
     * @param inner the scene to wrap
     * @return the wrapped scene (register this one with the scene manager)
     */
    public Scene host(Scene inner) {
        return new Host(inner);
    }

    private final class Host implements Scene {
        private final Scene inner;

        Host(Scene inner) {
            this.inner = inner;
        }

        @Override
        public void enter(SceneArgs args) {
            close();
            inner.enter(args);
        }

        @Override
        public void exit() {
            close();
            inner.exit();
        }

        @Override
        public void update(float dt, Frame f) {
            if (open) {
                SettingsOverlay.this.update(dt, f);
            } else if (f.input.pressed(KeyEvent.KEYCODE_MENU)) {
                open();
            } else {
                inner.update(dt, f);
            }
        }

        @Override
        public void draw(Canvas c) {
            inner.draw(c);
            SettingsOverlay.this.draw(c);
        }
    }
}
