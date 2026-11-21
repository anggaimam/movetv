package com.angga.movetv.shots;

import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import com.angga.movetv.audio.Music;
import com.angga.movetv.core.DemoScene;
import com.angga.movetv.core.Input;
import com.angga.movetv.core.PerfOverlay;
import com.angga.movetv.core.Scene;
import com.angga.movetv.games.fruit.FruitScene;
import com.angga.movetv.scenes.AttractScene;
import com.angga.movetv.scenes.GameSelectScene;
import com.angga.movetv.scenes.LevelSelectScene;
import com.angga.movetv.scenes.PlaceholderGameScene;
import com.angga.movetv.scenes.PlayersScene;
import com.angga.movetv.scenes.SettingsOverlay;
import com.angga.movetv.scenes.SetupScene;
import com.angga.movetv.ui.TileMenu;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;

/**
 * Takes one picture per scene. To add a scene, add a test method that calls {@link ShotRig#shoot}.
 * Run only with {@code gradle testReleaseUnitTest -Pshots} (CI does this in its own step).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class ScreenshotTest {

    @Before
    public void loadPuppets() {
        com.angga.movetv.ui.puppet.Puppet.init(org.robolectric.RuntimeEnvironment.getApplication());
    }

    /** A bare scene that shows one or two kid puppets on paper, to look at poses. */
    private static final class PuppetScene implements Scene {
        private final int mode;   // 0 rest, 1 wave, 2 cheer, 3 point
        private final com.angga.movetv.ui.puppet.Puppet a = new com.angga.movetv.ui.puppet.Puppet(0, 3);
        private final com.angga.movetv.ui.puppet.Puppet b = new com.angga.movetv.ui.puppet.Puppet(1, 4);

        PuppetScene(int mode) {
            this.mode = mode;
        }

        @Override public void enter(SceneArgs args) {
            if (mode == 1) {
                a.anim().wave(com.angga.movetv.ui.puppet.PuppetPose.RIGHT);
                b.anim().wave(com.angga.movetv.ui.puppet.PuppetPose.LEFT);
            } else if (mode == 2) {
                a.anim().cheer();
                b.anim().cheer();
            } else if (mode == 3) {
                a.anim().point(com.angga.movetv.ui.puppet.PuppetPose.RIGHT, 100f);
                b.anim().point(com.angga.movetv.ui.puppet.PuppetPose.LEFT, 120f);
            }
        }

        @Override public void exit() { }

        @Override public void update(float dt, Frame f) {
            a.update(dt);
            b.update(dt);
        }

        @Override public void draw(android.graphics.Canvas c) {
            c.drawColor(0xFFFBF5E9);
            a.draw(c, 560f, 1020f, 900f);
            b.draw(c, 1360f, 1020f, 900f);
        }
    }

    @Test
    public void puppetsAtRest() throws Exception {
        check(ShotRig.shoot("puppet_rest", new PuppetScene(0), Scene.SceneArgs.EMPTY, 1, 1, null));
    }

    @Test
    public void puppetWaveSequence() throws Exception {
        int[] frames = {10, 24, 40, 56, 76, 96};
        for (int i = 0; i < frames.length; i++) {
            check(ShotRig.shoot("puppet_wave_" + (i + 1), new PuppetScene(1), Scene.SceneArgs.EMPTY, 1, frames[i], null));
        }
    }

    @Test
    public void puppetsCheerAndPoint() throws Exception {
        check(ShotRig.shoot("puppet_cheer", new PuppetScene(2), Scene.SceneArgs.EMPTY, 1, 28, null));
        check(ShotRig.shoot("puppet_point", new PuppetScene(3), Scene.SceneArgs.EMPTY, 1, 40, null));
    }

    private static void check(File f) {
        assertTrue(f + " should be a real picture", f.length() > 5000);
    }

    @Test
    public void demoOnePlayerHandRaised() throws Exception {
        DemoScene scene = new DemoScene(new PerfOverlay(true), null, new Music());
        File f = ShotRig.shoot("demo_1p_hand_up", scene, Scene.SceneArgs.EMPTY, 1, 90,
                (frame, now, body, input) -> {
                    if (frame == 0) body.pointerDown(1300, 380);
                    body.pointerMove(1300, 380);
                });
        check(f);
    }

    @Test
    public void demoTwoPlayers() throws Exception {
        DemoScene scene = new DemoScene(new PerfOverlay(true), null, new Music());
        File f = ShotRig.shoot("demo_2p", scene, Scene.SceneArgs.EMPTY, 2, 60, null);
        check(f);
    }

    @Test
    public void demoSoundTestSelection() throws Exception {
        DemoScene scene = new DemoScene(new PerfOverlay(true), new com.angga.movetv.audio.Sfx(
                org.robolectric.RuntimeEnvironment.getApplication()), new Music());
        File f = ShotRig.shoot("demo_sound_test", scene, Scene.SceneArgs.EMPTY, 1, 60,
                (frame, now, body, input) -> {
                    if (frame == 10 || frame == 12) input.keyDown(KeyEvent.KEYCODE_DPAD_DOWN);
                    if (frame == 11 || frame == 13) input.keyUp(KeyEvent.KEYCODE_DPAD_DOWN);
                });
        check(f);
    }

    // ---- T-06: the home flow (menus use the test body: the pointer is the cursor) ----

    private static TileMenu.Env env() {
        return new TileMenu.Env(null, null, null, null, null, new PerfOverlay(false));
    }

    @Test
    public void menuAttract() throws Exception {
        File f = ShotRig.shoot("menu_attract", new AttractScene(env()), Scene.SceneArgs.EMPTY, 1, 40, null);
        check(f);
    }

    @Test
    public void menuSetup() throws Exception {
        File f = ShotRig.shoot("menu_setup", new SetupScene(env()), Scene.SceneArgs.of("back", 1), 1, 30, null);
        check(f);
    }

    @Test
    public void menuPlayersCursorFilling() throws Exception {
        File f = ShotRig.shoot("menu_players_cursor", new PlayersScene(env()), Scene.SceneArgs.EMPTY, 1, 45,
                (frame, now, body, input) -> {
                    if (frame == 0) input.touch(Input.TOUCH_DOWN, 640f, 520f);
                });
        check(f);
    }

    @Test
    public void menuGameSelectRemoteFocus() throws Exception {
        File f = ShotRig.shoot("menu_gameselect_focus", new GameSelectScene(env()), Scene.SceneArgs.EMPTY, 1, 40,
                (frame, now, body, input) -> {
                    if (frame == 5 || frame == 9) input.keyDown(KeyEvent.KEYCODE_DPAD_RIGHT);
                    if (frame == 6 || frame == 10) input.keyUp(KeyEvent.KEYCODE_DPAD_RIGHT);
                });
        check(f);
    }

    @Test
    public void menuLevelSelect() throws Exception {
        File f = ShotRig.shoot("menu_levelselect", new LevelSelectScene(env()), Scene.SceneArgs.of("gameId", "fruit"),
                1, 40, null);
        check(f);
    }

    @Test
    public void menuPlaceholderGame() throws Exception {
        File f = ShotRig.shoot("menu_placeholder", new PlaceholderGameScene(env()),
                Scene.SceneArgs.of("gameId", "jungle", "level", 2, "players", 2), 2, 30, null);
        check(f);
    }

    @Test
    public void menuSettingsOverlay() throws Exception {
        TileMenu.Env e = env();
        SettingsOverlay overlay = new SettingsOverlay(e);
        e.settings = overlay;
        File f = ShotRig.shoot("menu_settings", overlay.host(new PlayersScene(e)), Scene.SceneArgs.EMPTY, 1, 40,
                (frame, now, body, input) -> {
                    if (frame == 3) input.keyDown(KeyEvent.KEYCODE_MENU);
                    if (frame == 4) input.keyUp(KeyEvent.KEYCODE_MENU);
                });
        check(f);
    }

    @Test
    public void fruitRules() throws Exception {
        FruitScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        FruitScene.setRulesSeen(false);
        File f = ShotRig.shoot("fruit_rules", new FruitScene(env()), Scene.SceneArgs.of("level", 3, "players", 1), 1, 75,
                null);
        check(f);
    }

    @Test
    public void fruitPlaying() throws Exception {
        FruitScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        FruitScene.setRulesSeen(true);
        File f = ShotRig.shoot("fruit_play", new FruitScene(env()), Scene.SceneArgs.of("level", 2, "players", 1), 1, 700,
                (frame, now, body, input) -> {
                    if (frame == 0) body.pointerDown(300, 700);
                    float t = frame / 60f;
                    body.pointerMove(960 + (float) Math.sin(t * 5f) * 700f, 500 + (float) Math.cos(t * 7f) * 300f);
                });
        check(f);
    }

    /** A bare scene that shows the shared end screen of the Hud, to check the layout and the star animation. */
    private static final class EndScene implements Scene {
        private final TileMenu.Env env;
        private final com.angga.movetv.games.common.Hud hud;
        private final int players, s0, s1, score0, score1;

        EndScene(TileMenu.Env env, int players, int s0, int s1, int score0, int score1) {
            this.env = env;
            this.hud = new com.angga.movetv.games.common.Hud(env, 75f, "fruit");
            this.players = players;
            this.s0 = s0;
            this.s1 = s1;
            this.score0 = score0;
            this.score1 = score1;
        }

        @Override public void enter(SceneArgs args) {
            hud.start(players, false);
            hud.finish(s0, s1, score0, score1);
        }

        @Override public void exit() { }

        @Override public void update(float dt, Frame f) {
            hud.update(dt, f);
        }

        @Override public void draw(android.graphics.Canvas c) {
            com.angga.movetv.ui.Theme.drawBackdrop(c, null, 0f);
            hud.draw(c, 0, 0);
        }
    }

    @Test
    public void endScreenOnePlayer() throws Exception {
        int[] frames = {45, 100, 140, 260};
        for (int i = 0; i < frames.length; i++) {
            check(ShotRig.shoot("end_1p_" + (i + 1), new EndScene(env(), 1, 3, 0, 102, 0), Scene.SceneArgs.EMPTY, 1,
                    frames[i], null));
        }
    }

    @Test
    public void endScreenTwoPlayersFourDigits() throws Exception {
        int[] frames = {45, 130, 260};
        for (int i = 0; i < frames.length; i++) {
            check(ShotRig.shoot("end_2p_" + (i + 1), new EndScene(env(), 2, 3, 2, 1234, 87), Scene.SceneArgs.EMPTY, 2,
                    frames[i], null));
        }
    }

    @Test
    public void endScreenOneStar() throws Exception {
        check(ShotRig.shoot("end_1star", new EndScene(env(), 1, 1, 0, 3, 0), Scene.SceneArgs.EMPTY, 1, 200, null));
    }

    @Test
    public void fruitCameraOff() throws Exception {
        FruitScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        FruitScene.setRulesSeen(true);
        TileMenu.Env e = env();
        e.toggleCamView();
        File f = ShotRig.shoot("fruit_camera_off", new FruitScene(e), Scene.SceneArgs.of("level", 2, "players", 1), 1, 700,
                (frame, now, body, input) -> {
                    if (frame == 0) body.pointerDown(300, 700);
                    float t = frame / 60f;
                    body.pointerMove(960 + (float) Math.sin(t * 5f) * 700f, 500 + (float) Math.cos(t * 7f) * 300f);
                });
        check(f);
    }

    @Test
    public void fruitCameraOffTwoPlayersRules() throws Exception {
        FruitScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        FruitScene.setRulesSeen(false);
        TileMenu.Env e = env();
        e.toggleCamView();
        check(ShotRig.shoot("fruit_rules_camera_off", new FruitScene(e), Scene.SceneArgs.of("level", 2, "players", 2), 2, 60,
                null));
    }

    @Test
    public void settingsShowsCameraTile() throws Exception {
        TileMenu.Env e = env();
        e.toggleCamView(); // off: the camera on the tile is crossed out
        SettingsOverlay overlay = new SettingsOverlay(e);
        e.settings = overlay;
        File f = ShotRig.shoot("menu_settings_cam_off", overlay.host(new PlayersScene(e)), Scene.SceneArgs.EMPTY, 1, 40,
                (frame, now, body, input) -> {
                    if (frame == 3) input.keyDown(KeyEvent.KEYCODE_MENU);
                    if (frame == 4) input.keyUp(KeyEvent.KEYCODE_MENU);
                });
        check(f);
    }
}
