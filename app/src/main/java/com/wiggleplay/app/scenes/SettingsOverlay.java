package com.wiggleplay.app.scenes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;

import com.wiggleplay.app.core.ProfileLogic;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings panel, drawn over any screen. It holds the hold time (one tile that cycles 0.7 / 1.0 / 1.4 s), the pointer (on my hand / easy reach), the camera's light
 * boost and the performance overlay, all as big tiles that work with a hand or with the remote. The menu key
 * (or Back, or the Done tile) closes it. While it is open the screen underneath is not updated, so a game
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
    private TileMenu.Tile tHold, tBoost, tPerf, tPointer;

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

    private static final int[] HOLD_MS = {700, 1000, 1400};

    /**
     * Opens the panel. Selections are ignored for the first 1.5 s, so a raised hand does not pick a tile by accident.
     * Layout: OK at the top right (always the highest thing, easy to reach), then a grid of 5 columns by 2 rows;
     * only the top row is used so nothing needs a low reach, the bottom row is kept free for future settings.
     */
    public void open() {
        if (open) return;
        List<TileMenu.Tile> tiles = new ArrayList<>();
        float w = 320f, h = 370f, gap = 20f, x0 = (Stage.W - 5f * w - 4f * gap) / 2f, y = 320f;
        tHold = new TileMenu.Tile("hold", "Hold time", null, Theme.MUSTARD, Theme.clockPainter(2), x0, y, w, h);
        tPointer = new TileMenu.Tile("pointer", "Pointer", null, Theme.PINK, Theme::paintPointer,
                x0 + (w + gap), y, w, h);
        tBoost = new TileMenu.Tile("boost", "Light boost", null, Theme.ORANGE, Theme::paintSun,
                x0 + 2f * (w + gap), y, w, h);
        tPerf = new TileMenu.Tile("perf", "Speed meter", null, Theme.BLUE, Theme::paintGauge,
                x0 + 3f * (w + gap), y, w, h);
        TileMenu.Tile done = new TileMenu.Tile("done", "OK", null, Theme.SAGE, Theme::paintDone, 1370f, 40f, 380f, 250f);
        tiles.add(done);
        tiles.add(tHold);
        tiles.add(tPointer);
        tiles.add(tBoost);
        tiles.add(tPerf);
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
        tHold.badge = Theme.holdBadge(currentHold());
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
            case "hold": {
                int cur = currentHold(), next = HOLD_MS[0];
                for (int i = 0; i < HOLD_MS.length; i++) if (HOLD_MS[i] == cur) next = HOLD_MS[(i + 1) % HOLD_MS.length];
                if (env.profile != null) env.profile.setHoldMs(next);
                break;
            }
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
        Theme.drawBanner(c, T_TITLE, 760f, 150f, 72f, 0xFFF3EBDD, Theme.INK);
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
