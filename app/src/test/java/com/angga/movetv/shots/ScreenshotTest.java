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
}
