package com.wiggleplay.app.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;

import com.wiggleplay.app.audio.Music;
import com.wiggleplay.app.audio.Sfx;
import com.wiggleplay.app.audio.Voice;
import com.wiggleplay.app.core.Input;
import com.wiggleplay.app.core.PerfOverlay;
import com.wiggleplay.app.core.Profile;
import com.wiggleplay.app.core.ProfileLogic;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.SceneManager;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.input.DwellButtons;
import com.wiggleplay.app.input.DwellEvent;
import com.wiggleplay.app.input.HandCursor;
import com.wiggleplay.app.input.ReachMapping;
import com.wiggleplay.app.input.Target;
import com.wiggleplay.app.pose.PoseHub;
import com.wiggleplay.app.scenes.SettingsOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * A reusable menu of big tiles: layout is given by the caller; this class does the hand cursors (a real
 * hand with the camera, the pointer in test mode), hover-and-hold selection ({@link DwellButtons}), the
 * remote focus highlight, the hover animation, sounds and the spoken name on hover. In 2 players the menu
 * is controlled by whichever player raises a hand first (that cursor uses the whole screen).
 * Nothing is allocated per frame.
 */
public final class TileMenu {

    /**
     * What the menu screens share with the rest of the app: sounds, the camera hub, saved settings, the
     * scene manager and the settings overlay. The activity creates one and hands it to every scene.
     * Every sound call is silently ignored when the sound object is null (used by the screenshot tests).
     * A game scene should call {@link #leaveMenus()} when it starts its own music; menu screens call
     * {@link #menuMusic()} and the menu music only restarts if it is not already playing.
     */
    public static final class Env {
        /** Camera and test-mode hub; may be null in tests. */
        public final PoseHub hub;
        /** Sound effects; may be null. */
        public final Sfx sfx;
        /** Music; may be null. */
        public final Music music;
        /** Spoken names; may be null. */
        public final Voice voice;
        /** Saved settings and stars; may be null in tests. */
        public final Profile profile;
        /** Performance overlay. */
        public final PerfOverlay perf;
        /** The scene manager; set by the activity after it is created. */
        public SceneManager scenes;
        /** The settings overlay shared by all screens; set by the activity. */
        public SettingsOverlay settings;
        /** The chosen player count (1 or 2). */
        public int players = 1;

        private boolean menuMusicOn;
        private boolean lightBoost = true; // the engine starts with light boost on
        private String pointer = ProfileLogic.DEFAULT_POINTER;

        /**
         * Creates the environment.
         * @param hub camera hub (may be null)
         * @param sfx sound effects (may be null)
         * @param music music (may be null)
         * @param voice spoken names (may be null)
         * @param profile saved settings (may be null)
         * @param perf performance overlay
         */
        public Env(PoseHub hub, Sfx sfx, Music music, Voice voice, Profile profile, PerfOverlay perf) {
            this.hub = hub;
            this.sfx = sfx;
            this.music = music;
            this.voice = voice;
            this.profile = profile;
            this.perf = perf;
            if (profile != null) players = profile.players();
        }

        /**
         * Goes to another scene.
         * @param name registered scene name
         * @param args scene arguments, or null
         */
        public void go(String name, Scene.SceneArgs args) {
            if (scenes != null) scenes.go(name, args);
        }

        /**
         * Plays a sound effect.
         * @param name effect name
         */
        public void sfx(String name) {
            if (sfx != null) sfx.play(name);
        }

        /**
         * Plays a sound effect at a pitch.
         * @param name effect name
         * @param pitch playback rate
         */
        public void sfx(String name, float pitch) {
            if (sfx != null) sfx.play(name, pitch);
        }

        /**
         * Speaks a word or phrase.
         * @param text what to say
         */
        public void say(String text) {
            if (voice != null) voice.say(text);
        }

        /** Starts the calm menu music unless it is already playing. */
        public void menuMusic() {
            if (music != null && !menuMusicOn) {
                music.play("menu");
                menuMusicOn = true;
            }
        }

        /** Stops the menu music; call when a game starts its own music or when a game screen is entered. */
        public void leaveMenus() {
            if (music != null && menuMusicOn) music.stop(300);
            menuMusicOn = false;
        }

        /**
         * Sets the player count for the camera, the test body and the saved profile.
         * @param n 1 or 2
         */
        public void setPlayers(int n) {
            players = n == 2 ? 2 : 1;
            if (hub != null) hub.setMode(players);
            if (profile != null) profile.setPlayers(players);
        }

        /**
         * Whether the camera light boost is on.
         * @return true if on
         */
        public boolean lightBoost() {
            return lightBoost;
        }

        /**
         * The pointer setting in use (kept here so the menus never read the saved file every frame).
         * @return "hand" or "reach"
         */
        public String pointer() {
            return pointer;
        }

        /** Switches the pointer between "on my hand" and "easy reach" and saves the choice. */
        public void togglePointer() {
            pointer = ProfileLogic.POINTER_REACH.equals(pointer) ? ProfileLogic.POINTER_HAND : ProfileLogic.POINTER_REACH;
            if (profile != null) profile.setPointer(pointer);
        }

        /** Switches the camera light boost and saves the choice. */
        public void toggleLightBoost() {
            lightBoost = !lightBoost;
            if (hub != null) hub.toggleLightBoost();
            if (profile != null) profile.setLightBoost(lightBoost);
        }

        /**
         * Applies the saved settings once at start (the light boost).
         * Call after the camera hub has started, because the light boost lives in the camera engine.
         */
        public void applySaved() {
            if (profile == null) return;
            pointer = profile.pointer();
            if (!profile.lightBoost() && lightBoost) {
                lightBoost = false;
                if (hub != null) hub.toggleLightBoost();
            }
        }

        /** Switches the performance overlay on or off (not saved: it is a tool for checking the TV). */
        public void togglePerf() {
            perf.toggle();
        }
    }

    /** One big tile. Create it, place it ({@code x y w h}) and give it to {@link TileMenu#setTiles}. */
    /** A tile picture that moves while the tile is highlighted. Must not allocate. */
    public interface LiveIcon {
        /**
         * Draws the picture.
         * @param c canvas
         * @param cx centre x
         * @param cy centre y
         * @param size width and height of the picture area
         * @param hover how highlighted the tile is, 0..1
         * @param time seconds, for animation
         */
        void draw(Canvas c, float cx, float cy, float size, float hover, float time);
    }

    public static final class Tile {
        /** Unique id inside the menu; the id of the selected tile is what {@link TileMenu#update} returns. */
        public final String id;
        /** Name shown on the tile and spoken on hover. */
        public final String title;
        /** Small text under the title, or null. */
        public final String subtitle;
        /** Flat fill colour. */
        public final int colour;
        /** Paints the picture on the tile (painted once), or null. */
        public final SpriteCache.Painter icon;
        /** Left, top, width and height in stage pixels. */
        public float x, y, w, h;
        /** Hold time in ms, or 0 for the default (Home tiles use 1500). */
        public int holdMs;
        /** Extra picture near the bottom of the icon area (stars, ON/OFF, tick), or null. May change any time. */
        public Bitmap badge;
        /** Small picture in the top-right corner (a tick for the chosen option), or null. May change any time. */
        public Bitmap cornerBadge;
        /** True for the round corner buttons (Back, Home) made by {@link RoundButton}. */
        public boolean round;
        /** Draws the picture live while the tile is highlighted (the baked picture is used otherwise), or null. */
        public LiveIcon live;

        Bitmap body, shadow, iconBmp;
        float hover;

        /**
         * Creates a tile.
         * @param id unique id
         * @param title shown and spoken name
         * @param subtitle small text, or null
         * @param colour fill colour
         * @param icon paints the picture, or null
         * @param x left
         * @param y top
         * @param w width
         * @param h height
         */
        public Tile(String id, String title, String subtitle, int colour, SpriteCache.Painter icon,
                    float x, float y, float w, float h) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.colour = colour;
            this.icon = icon;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        void bake() {
            if (round) {
                RoundButton.bake(this);
                return;
            }
            int bw = (int) w, bh = (int) h;
            String k = "tile_" + id + "_" + title + "_" + bw + "x" + bh + "_" + colour;
            body = Theme.tileBody(k + "_b", bw, bh, colour, title, subtitle);
            shadow = Theme.tileShadow(k + "_s", bw, bh, colour);
            if (icon != null) {
                int isz = (int) Theme.iconSize(w, h);
                iconBmp = Theme.icon("tileicon_" + id + "_" + title + "_" + isz, isz, icon);
            }
        }

        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    private static final long LONG_TICK_MS = 140;

    private final Env env;
    private List<Tile> tiles = new ArrayList<>();
    private DwellButtons dwell = new DwellButtons();
    private HandCursor[] hand = {new HandCursor(0), new HandCursor(1)};
    private final ReachMapping[] reach = {new ReachMapping(1f), new ReachMapping(1f)};
    private final PointerCursor pointer = new PointerCursor(0);
    private final HandCursor[] active = new HandCursor[2];
    private final HandCursor[] forDwell = new HandCursor[2];
    private final boolean[] wasShown = new boolean[2];
    private final String[] blockedTile = new String[2];
    private final List<DwellEvent> events = new ArrayList<>();
    private int owner = -1;
    private boolean focusShown;
    private boolean cursorVisible;
    private String defaultFocus;
    private float time;
    private float tickAcc;

    /**
     * Creates a menu.
     * @param env what the screens share
     */
    public TileMenu(Env env) {
        this.env = env;
    }

    /**
     * Gives the menu its tiles and forgets the old hover, focus and cursor state (call from a scene's enter).
     * @param list the tiles, already placed
     * @param defaultFocusId the tile the remote highlight starts on, or null for the first tile
     */
    public void setTiles(List<Tile> list, String defaultFocusId) {
        tiles = new ArrayList<>(list);
        dwell = new DwellButtons();
        hand = new HandCursor[] {new HandCursor(0), new HandCursor(1)};
        owner = -1;
        focusShown = false;
        cursorVisible = false;
        wasShown[0] = false;
        wasShown[1] = false;
        blockedTile[0] = null;
        blockedTile[1] = null;
        defaultFocus = defaultFocusId != null ? defaultFocusId : (tiles.isEmpty() ? null : tiles.get(0).id);
        List<Target> targets = new ArrayList<>();
        for (Tile t : tiles) {
            t.bake();
            t.hover = 0f;
            targets.add(new Target(t.id, t.x, t.y, t.w, t.h, t.holdMs));
        }
        dwell.setTargets(targets);
    }

    /**
     * Whether a hand (or the test pointer) is showing a cursor right now.
     * @return true if a cursor is visible
     */
    public boolean cursorVisible() {
        return cursorVisible;
    }

    /**
     * Advances the menu by one frame.
     * @param dt seconds since the last frame
     * @param f this frame's data
     * @return the id of the tile selected this frame (by hand or remote), or null
     */
    public String update(float dt, Scene.Frame f) {
        time += dt;
        dwell.setHoldMs(env.profile != null ? env.profile.holdMs() : 1000);
        updateCursors(dt, f);
        if (cursorVisible) focusShown = false;
        if (!tiles.isEmpty()) handleRemote(f.input);

        events.clear();
        gateDwell(!"test".equals(f.pose.source));
        dwell.update(dt, forDwell, f.nowMs, events);
        String selected = null;
        for (int i = 0; i < events.size(); i++) {
            DwellEvent e = events.get(i);
            if (e.type == DwellEvent.HOVER) {
                env.sfx("hover");
                env.say(titleOf(e.targetId));
            } else {
                env.sfx("select");
                selected = e.targetId;
            }
        }

        // tick sound follows the progress of the tile being filled
        float prog = 0f;
        for (int i = 0; i < tiles.size(); i++) {
            float p = dwell.progress(tiles.get(i).id);
            if (p > prog && dwell.progressPlayer(tiles.get(i).id) >= 0) prog = p;
        }
        if (prog > 0f) {
            tickAcc += dt * 1000f;
            if (tickAcc >= LONG_TICK_MS) {
                tickAcc = 0f;
                env.sfx("tick", 0.8f + 0.8f * prog);
            }
        } else {
            tickAcc = LONG_TICK_MS;
        }

        // hover animation
        String focusId = focusShown ? dwell.focusId() : null;
        float k = Math.min(1f, dt * 12f);
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            boolean on = t.id.equals(focusId) || dwell.progress(t.id) > 0f;
            for (int c = 0; !on && c < active.length; c++) {
                HandCursor cur = active[c];
                if (cur != null && cur.visible() && t.contains(cur.x(), cur.y())) on = true;
            }
            t.hover += ((on ? 1f : 0f) - t.hover) * k;
            if (t.hover < 0.002f) t.hover = 0f;
        }
        return selected;
    }

    /**
     * A hand that is already resting on a tile when it first shows (for example the hand was still up from
     * the last screen) may not select that tile until it has left it once.
     */
    private void gateDwell(boolean camera) {
        for (int i = 0; i < 2; i++) {
            HandCursor cur = active[i];
            boolean shown = cur != null && cur.visible();
            if (!camera) {
                forDwell[i] = cur;
                continue;
            }
            if (shown && !wasShown[i]) {
                blockedTile[i] = null;
                for (int t = 0; t < tiles.size(); t++) {
                    if (tiles.get(t).contains(cur.x(), cur.y())) {
                        blockedTile[i] = tiles.get(t).id;
                        break;
                    }
                }
            }
            wasShown[i] = shown;
            if (blockedTile[i] != null) {
                boolean still = false;
                for (int t = 0; shown && t < tiles.size(); t++) {
                    Tile tl = tiles.get(t);
                    if (tl.id.equals(blockedTile[i]) && tl.contains(cur.x(), cur.y())) still = true;
                }
                if (!still) blockedTile[i] = null;
            }
            forDwell[i] = blockedTile[i] == null ? cur : null;
        }
    }

    private void updateCursors(float dt, Scene.Frame f) {
        active[0] = null;
        active[1] = null;
        boolean test = "test".equals(f.pose.source);
        if (test) {
            pointer.updatePointer(dt, f.input, f.nowMs);
            if (pointer.visible()) active[0] = pointer;
            cursorVisible = pointer.visible();
            return;
        }
        for (int i = 0; i < 2; i++) {
            reach[i].setGain(ProfileLogic.pointerGain(env.pointer()));
            hand[i].setMapping(reach[i]);
            hand[i].update(dt, i < f.pose.mode ? f.pose.players[i] : null, f.nowMs, f.pose.timeMs);
        }
        if (env.perf != null) {
            if (hand[0].visible()) env.perf.setPointerDebug(hand[0].wristX(), hand[0].wristY(), hand[0].x(), hand[0].y());
            else env.perf.clearPointerDebug();
        }
        if (owner >= 0 && !hand[owner].visible()) owner = -1;
        if (owner < 0) {
            for (int i = 0; i < 2; i++) {
                if (hand[i].visible()) {
                    owner = i;
                    break;
                }
            }
        }
        if (owner >= 0) active[owner] = hand[owner];
        cursorVisible = owner >= 0;
    }

    private void handleRemote(Input in) {
        int dx = 0, dy = 0;
        if (in.pressed(KeyEvent.KEYCODE_DPAD_LEFT)) dx = -1;
        else if (in.pressed(KeyEvent.KEYCODE_DPAD_RIGHT)) dx = 1;
        if (in.pressed(KeyEvent.KEYCODE_DPAD_UP)) dy = -1;
        else if (in.pressed(KeyEvent.KEYCODE_DPAD_DOWN)) dy = 1;
        boolean ok = in.pressed(KeyEvent.KEYCODE_DPAD_CENTER) || in.pressed(KeyEvent.KEYCODE_ENTER)
                || in.pressed(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) || in.pressed(KeyEvent.KEYCODE_MEDIA_PLAY)
                || in.pressed(KeyEvent.KEYCODE_MEDIA_PAUSE);
        if (dx != 0 || dy != 0) {
            String before = dwell.focusId();
            if (!focusShown) {
                focusShown = true;
                if (before == null) dwell.setFocus(defaultFocus);
            } else {
                dwell.moveFocus(dx, dy);
            }
            String after = dwell.focusId();
            if (after != null && !after.equals(before) || before == null) {
                env.sfx("hover");
                env.say(titleOf(after));
            }
        } else if (ok) {
            if (!focusShown && tiles.size() > 1) {
                focusShown = true;
                if (dwell.focusId() == null) dwell.setFocus(defaultFocus);
                env.say(titleOf(dwell.focusId()));
            } else {
                focusShown = true;
                if (dwell.focusId() == null) dwell.setFocus(defaultFocus);
                dwell.selectFocused(0);
            }
        }
    }

    private String titleOf(String id) {
        if (id == null) return null;
        for (int i = 0; i < tiles.size(); i++) if (tiles.get(i).id.equals(id)) return tiles.get(i).title;
        return null;
    }

    /**
     * Draws the tiles, the remote focus highlight and the cursors.
     * @param c canvas in stage coordinates
     */
    public void draw(Canvas c) {
        for (int i = 0; i < tiles.size(); i++) {
            Tile t = tiles.get(i);
            if (t.round) {
                int pl = dwell.progressPlayer(t.id);
                RoundButton.draw(c, t, dwell.progress(t.id), Theme.playerColour(pl < 0 ? 0 : pl));
            } else {
                Theme.drawTile(c, t.shadow, t.body, t.iconBmp, t.badge, t.cornerBadge, t.x, t.y, t.hover, time, t.live);
            }
        }
        if (focusShown) {
            String fid = dwell.focusId();
            for (int i = 0; i < tiles.size() && fid != null; i++) {
                Tile t = tiles.get(i);
                if (!t.id.equals(fid)) continue;
                if (t.round) RoundButton.drawFocus(c, t, time);
                else Theme.drawFocus(c, t.x, t.y, t.w, t.h, t.hover, time);
            }
        }
        drawCursors(c);
    }

    /**
     * Draws only the cursors (for screens that draw their own content over the tiles).
     * @param c canvas in stage coordinates
     */
    public void drawCursors(Canvas c) {
        float pulse = (float) Math.sin(time * 5f) * 0.5f + 0.5f;
        for (int p = 0; p < active.length; p++) {
            HandCursor cur = active[p];
            if (cur == null || !cur.visible()) continue;
            float cx = cur.x() + dwell.magnetDx(p), cy = cur.y() + dwell.magnetDy(p);
            float prog = 0f;
            for (int i = 0; i < tiles.size(); i++) {
                Tile t = tiles.get(i);
                if (dwell.progressPlayer(t.id) == p) prog = Math.max(prog, dwell.progress(t.id));
            }
            int id = cur.playerId() == 1 ? 1 : 0;
            Theme.drawRing(c, cx, cy, 70f, prog, Theme.playerColour(id));
            Theme.drawCursor(c, cx, cy, id, pulse);
        }
    }
}
