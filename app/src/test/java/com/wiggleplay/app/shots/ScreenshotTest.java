package com.wiggleplay.app.shots;

import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import com.wiggleplay.app.audio.Music;
import com.wiggleplay.app.core.DemoScene;
import com.wiggleplay.app.core.Input;
import com.wiggleplay.app.core.PerfOverlay;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.games.bowling.BowlingScene;
import com.wiggleplay.app.games.fruit.FruitScene;
import com.wiggleplay.app.games.dance.DanceScene;
import com.wiggleplay.app.games.jungle.JungleScene;
import com.wiggleplay.app.games.sky.SkyPlaneScene;
import com.wiggleplay.app.games.sky.SkyScene;
import com.wiggleplay.app.pose.TestPoseSource;
import com.wiggleplay.app.scenes.AttractScene;
import com.wiggleplay.app.scenes.GameSelectScene;
import com.wiggleplay.app.scenes.LevelSelectScene;
import com.wiggleplay.app.scenes.PlaceholderGameScene;
import com.wiggleplay.app.scenes.PlayersScene;
import com.wiggleplay.app.scenes.SettingsOverlay;
import com.wiggleplay.app.scenes.SetupScene;
import com.wiggleplay.app.ui.TileMenu;

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
        DemoScene scene = new DemoScene(new PerfOverlay(true), new com.wiggleplay.app.audio.Sfx(
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
        com.wiggleplay.app.ui.Theme.init(org.robolectric.RuntimeEnvironment.getApplication());
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
        File f = ShotRig.shoot("menu_players_cursor", new PlayersScene(env()), Scene.SceneArgs.of("gameId", "fruit"), 1, 45,
                (frame, now, body, input) -> {
                    if (frame == 0) input.touch(Input.TOUCH_DOWN, 640f, 520f);
                });
        check(f);
    }

    @Test
    public void menuGameSelectRemoteFocus() throws Exception {
        GameSelectScene.setPageForTests(0);
        File f = ShotRig.shoot("menu_gameselect_focus", new GameSelectScene(env()), Scene.SceneArgs.EMPTY, 1, 40,
                (frame, now, body, input) -> {
                    if (frame == 5 || frame == 9) input.keyDown(KeyEvent.KEYCODE_DPAD_RIGHT);
                    if (frame == 6 || frame == 10) input.keyUp(KeyEvent.KEYCODE_DPAD_RIGHT);
                });
        check(f);
    }

    /** Five games: the first four with an arrow on the right, and dots. */
    @Test
    public void menuGameSelectPageOne() throws Exception {
        GameSelectScene.setPageForTests(0);
        check(ShotRig.shoot("menu_gameselect_page1", new GameSelectScene(env()), Scene.SceneArgs.EMPTY, 1, 30, null));
    }

    /** Hold a hand on the right arrow: the second page shows (Bowling), with the left arrow. */
    @Test
    public void menuGameSelectPageTwo() throws Exception {
        GameSelectScene.setPageForTests(0);
        check(ShotRig.shoot("menu_gameselect_page2", new GameSelectScene(env()), Scene.SceneArgs.EMPTY, 1, 80,
                (frame, now, body, input) -> {
                    if (frame == 0) input.touch(Input.TOUCH_DOWN, 1800f, 600f);
                    if (frame == 70) input.touch(Input.TOUCH_UP, 1800f, 600f);
                }));
    }

    /** On page two the Bowling tile is highlighted and the rocket is part-way round the planet. */
    @Test
    public void menuGameSelectBowlingLap() throws Exception {
        GameSelectScene.setPageForTests(0);
        check(ShotRig.shoot("menu_gameselect_bowling_lap", new GameSelectScene(env()), Scene.SceneArgs.EMPTY, 1, 165,
                (frame, now, body, input) -> {
                    if (frame == 0) input.touch(Input.TOUCH_DOWN, 1800f, 600f);
                    if (frame == 62) input.touch(Input.TOUCH_UP, 1800f, 600f);
                    if (frame == 70) input.touch(Input.TOUCH_DOWN, 960f, 560f);
                    if (frame == 71) input.touch(Input.TOUCH_MOVE, 960f, 560f);
                }, true));
    }

    /** Page one with a finger on the Animal Dance Party tile: the flamingo waves its wings. */
    @Test
    public void menuGameSelectDanceFlamingo() throws Exception {
        GameSelectScene.setPageForTests(0);
        check(ShotRig.shoot("menu_gameselect_dance_flamingo", new GameSelectScene(env()), Scene.SceneArgs.EMPTY, 1, 75,
                (frame, now, body, input) -> {
                    if (frame == 20) input.touch(Input.TOUCH_DOWN, 400f, 560f);
                    if (frame == 21) input.touch(Input.TOUCH_MOVE, 400f, 560f);
                }, true));
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
        File f = ShotRig.shoot("menu_settings", overlay.host(new PlayersScene(e)), Scene.SceneArgs.of("gameId", "fruit"), 1, 40,
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

    private void fruitEnd(String name, int players) throws Exception {
        FruitScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        FruitScene.setRulesSeen(true);
        File f = ShotRig.shoot(name, new FruitScene(env()), Scene.SceneArgs.of("level", 2, "players", players), players,
                5000, (frame, now, body, input) -> {
                    if (frame == 0) body.pointerDown(300, 700);
                    float t = frame / 60f;
                    body.pointerMove(960 + (float) Math.sin(t * 5f) * 700f, 500 + (float) Math.cos(t * 7f) * 300f);
                });
        check(f);
    }

    @Test
    public void fruitEndOnePlayer() throws Exception {
        fruitEnd("fruit_end_1p", 1);
    }

    @Test
    public void fruitEndTwoPlayers() throws Exception {
        fruitEnd("fruit_end_2p", 2);
    }

    // ---- T-08: Sky Glider ----

    @Test
    public void skyRules() throws Exception {
        SkyScene.setRulesSeen(false);
        File f = ShotRig.shoot("sky_rules", new SkyScene(env()), Scene.SceneArgs.of("level", 1, "players", 1), 1, 75, null);
        check(f);
    }

    @Test
    public void skyCountdownDemo() throws Exception {
        SkyScene.setRulesSeen(true);
        File f = ShotRig.shoot("sky_countdown", new SkyScene(env()), Scene.SceneArgs.of("level", 1, "players", 1), 1, 100,
                null);
        check(f);
    }

    private void skyPlay(String name, int level, int players, int frames) throws Exception {
        skyPlay(name, level, players, frames, 0);
    }

    private void skyPlay(String name, int level, int players, int frames, int plane) throws Exception {
        SkyScene.setRulesSeen(true);
        File f = ShotRig.shoot(name, new SkyScene(env()),
                Scene.SceneArgs.of("level", level, "players", players, "plane", plane, "plane2", plane + 1), players,
                frames, (frame, now, body, input) -> {
                    int phase = (frame / 70) % 4;
                    body.setHeld(TestPoseSource.CTRL_LEAN_LEFT, phase == 0);
                    body.setHeld(TestPoseSource.CTRL_LEAN_RIGHT, phase == 2);
                    if (frame == 0) body.pointerDown(1300, 250);
                    body.pointerMove(1300, phase < 2 ? 250 : 520);
                });
        check(f);
    }

    @Test
    public void skyPlayEasy() throws Exception {
        skyPlay("sky_play_easy", 1, 1, 900);
    }

    @Test
    public void skyPlayMedium() throws Exception {
        skyPlay("sky_play_medium", 2, 1, 900);
    }

    @Test
    public void skyPlayHard() throws Exception {
        skyPlay("sky_play_hard", 3, 1, 900);
    }

    @Test
    public void skyPlayTwoPlayers() throws Exception {
        skyPlay("sky_play_2p", 1, 2, 900);
    }

    @Test
    public void skyEndOnePlayer() throws Exception {
        skyPlay("sky_end_1p", 1, 1, 6000);
    }

    @Test
    public void skyPlaneMenu() throws Exception {
        File f = ShotRig.shoot("sky_plane_menu", new SkyPlaneScene(env()),
                Scene.SceneArgs.of("gameId", "sky", "level", 1, "players", 1), 1, 40, null);
        check(f);
    }

    @Test
    public void skyPlayAirliner() throws Exception {
        skyPlay("sky_play_airliner", 1, 1, 300, 1);
    }

    @Test
    public void skyPlayJet() throws Exception {
        skyPlay("sky_play_jet", 2, 1, 300, 2);
    }

    @Test
    public void skyPlaySpaceship() throws Exception {
        skyPlay("sky_play_spaceship", 3, 1, 300, 3);
    }

    @Test
    public void skyPlayTwoPlanes() throws Exception {
        skyPlay("sky_play_2p_planes", 1, 2, 300, 1);
    }

    // ---- T-09: Animal Dance Party ----

    @Test
    public void danceRules() throws Exception {
        DanceScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        DanceScene.setRulesSeen(false);
        check(ShotRig.shoot("dance_rules", new DanceScene(env()), Scene.SceneArgs.of("level", 1, "players", 1), 1, 75, null));
    }

    @Test
    public void danceCountdown() throws Exception {
        DanceScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        DanceScene.setRulesSeen(true);
        check(ShotRig.shoot("dance_countdown", new DanceScene(env()), Scene.SceneArgs.of("level", 1, "players", 1), 1, 100, null));
    }

    private void dancePlay(String name, int level, int players, int frames, int preset) throws Exception {
        DanceScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        DanceScene.setRulesSeen(true);
        check(ShotRig.shoot(name, new DanceScene(env()), Scene.SceneArgs.of("level", level, "players", players), players,
                frames, (frame, now, body, input) -> {
                    int k = preset > 0 ? preset : 1 + (frame / 90) % 9;
                    for (int i = 1; i <= 9; i++) body.setPreset(i, i == k);
                }));
    }

    @Test
    public void dancePlayEasy() throws Exception {
        dancePlay("dance_play_easy", 1, 1, 460, 1);
    }

    @Test
    public void dancePlayResult() throws Exception {
        dancePlay("dance_result_easy", 1, 1, 590, 0);
    }

    @Test
    public void dancePlayHard() throws Exception {
        dancePlay("dance_play_hard", 3, 1, 1500, 4);
    }

    @Test
    public void dancePlayTwoPlayers() throws Exception {
        dancePlay("dance_play_2p", 2, 2, 500, 2);
    }

    @Test
    public void danceEnd() throws Exception {
        dancePlay("dance_end", 1, 1, 4000, 0);
    }

    private void danceAt(String name, int level, int players, int frames, boolean unlockAll) throws Exception {
        DanceScene.setUnlockAll(unlockAll);
        try {
            dancePlay(name, level, players, frames, 0);
        } finally {
            DanceScene.setUnlockAll(false);
        }
    }

    @Test
    public void danceFreeze() throws Exception {
        danceAt("dance_freeze", 1, 1, 1010, false);
    }

    @Test
    public void danceMove() throws Exception {
        danceAt("dance_move", 1, 1, 2240, false);
    }

    @Test
    public void danceMirrorRound() throws Exception {
        danceAt("dance_mirror", 3, 1, 495, false);
    }

    @Test
    public void danceMirrorFlip() throws Exception {
        danceAt("dance_mirror_flip", 3, 1, 530, false);
    }

    @Test
    public void danceMirrorFlipped() throws Exception {
        danceAt("dance_mirror_flipped", 3, 1, 580, false);
    }

    // ---- T-13 part 1: Bowling ----

    private BowlingScene newBowling() {
        BowlingScene.setRulesSeen(true);
        BowlingScene.setGoldenForTests(4);        // a known golden pin (back row, in the middle) for the pictures
        return new BowlingScene(env());
    }

    @Test
    public void bowlingRules() throws Exception {
        BowlingScene b = new BowlingScene(env());
        BowlingScene.setRulesSeen(false);
        check(ShotRig.shoot("bowling_rules", b, Scene.SceneArgs.of("level", 1, "players", 2), 1, 70,
                null));
        BowlingScene.setRulesSeen(true);
    }

    @Test
    public void bowlingPlayers() throws Exception {
        check(ShotRig.shoot("bowling_players", new PlayersScene(env()), Scene.SceneArgs.of("gameId", "bowling"), 1, 30,
                null));
    }

    @Test
    public void bowlingTurnFourPlayers() throws Exception {
        check(ShotRig.shoot("bowling_turn_4p", newBowling(), Scene.SceneArgs.of("level", 1, "players", 4), 1,
                40, null));
    }

    /** Four players, every roll by the 12 s auto-roll: ends on the end screen. */
    @Test
    public void bowlingEndFourPlayers() throws Exception {
        check(ShotRig.shoot("bowling_end_4p", newBowling(), Scene.SceneArgs.of("level", 2, "players", 4), 1,
                60 * 60 * 18, null));
    }

    /** Picks "1 player" with the remote, waits out the intro, holds OK from frame 150 to 190, then lets go. */
    private void bowlingAt(String name, int level, int frames) throws Exception {
        check(ShotRig.shoot(name, newBowling(), Scene.SceneArgs.of("level", level), 1, frames,
                (frame, now, body, input) -> {
                    int[] taps = {10, 25};
                    for (int t : taps) {
                        if (frame == t) input.keyDown(KeyEvent.KEYCODE_DPAD_CENTER);
                        if (frame == t + 1) input.keyUp(KeyEvent.KEYCODE_DPAD_CENTER);
                    }
                    if (frame == 150) input.keyDown(KeyEvent.KEYCODE_DPAD_CENTER);
                    if (frame == 190) input.keyUp(KeyEvent.KEYCODE_DPAD_CENTER);
                }));
    }

    /** Nobody rolls for 7 seconds: the swing demo grows and the hint shows. */
    @Test
    public void bowlingSwingHint() throws Exception {
        check(ShotRig.shoot("bowling_swing_hint", newBowling(), Scene.SceneArgs.of("level", 1), 1, 560,
                (frame, now, body, input) -> {
                    if (frame == 10) input.keyDown(KeyEvent.KEYCODE_DPAD_CENTER);
                    if (frame == 11) input.keyUp(KeyEvent.KEYCODE_DPAD_CENTER);
                }));
    }

    @Test
    public void bowlingAim() throws Exception {
        bowlingAt("bowling_aim", 1, 120);
    }

    @Test
    public void bowlingCharge() throws Exception {
        bowlingAt("bowling_charge", 1, 175);
    }

    @Test
    public void bowlingRolling() throws Exception {
        bowlingAt("bowling_rolling", 1, 255);
    }

    @Test
    public void bowlingResult() throws Exception {
        bowlingAt("bowling_result", 1, 420);
    }

    @Test
    public void bowlingHardResult() throws Exception {
        bowlingAt("bowling_result_hard", 3, 420);
    }

    /** Medium: the planet beside the lane and its pull arrows. */
    @Test
    public void bowlingMediumPlanet() throws Exception {
        bowlingAt("bowling_aim_medium", 2, 120);
    }

    /** Hard: the pin platform, a little way along its slide. */
    @Test
    public void bowlingHardPlatform() throws Exception {
        bowlingAt("bowling_aim_hard", 3, 200);
    }

    /** The golden pin falls: the banner says +5. */
    @Test
    public void bowlingGoldenResult() throws Exception {
        bowlingAt("bowling_result_gold", 1, 420);
    }

    @Test
    public void danceParty() throws Exception {
        danceAt("dance_party", 1, 1, 1800, true);
    }

    @Test
    public void danceParty2p() throws Exception {
        danceAt("dance_party_2p", 3, 2, 1500, true);
    }

    @Test
    public void jungleRules() throws Exception {
        JungleScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        JungleScene.setRulesSeen(false);
        check(ShotRig.shoot("jungle_rules", new JungleScene(env()),
                Scene.SceneArgs.of("level", 1, "players", 1), 1, 40, null));
    }

    @Test
    public void jungleCountdown() throws Exception {
        JungleScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        JungleScene.setRulesSeen(true);
        check(ShotRig.shoot("jungle_countdown", new JungleScene(env()),
                Scene.SceneArgs.of("level", 1, "players", 1), 1, 60, null));
    }

    private void junglePlay(String name, int level, int players, int frames) throws Exception {
        JungleScene.initArt(org.robolectric.RuntimeEnvironment.getApplication());
        JungleScene.setRulesSeen(true);
        check(ShotRig.shoot(name, new JungleScene(env()),
                Scene.SceneArgs.of("level", level, "players", players), players, frames,
                (frame, now, body, input) -> {
                    if (frame > 400 && frame % 90 == 0) body.jump(now);
                    body.setHeld(TestPoseSource.CTRL_DUCK, frame > 400 && (frame / 45) % 5 == 3);
                }));
    }

    @Test
    public void junglePlayEasy() throws Exception {
        junglePlay("jungle_play_easy", 1, 1, 900);
    }

    @Test
    public void junglePlayMedium() throws Exception {
        junglePlay("jungle_play_medium", 2, 1, 900);
    }

    @Test
    public void junglePlayHard() throws Exception {
        junglePlay("jungle_play_hard", 3, 1, 900);
    }

    @Test
    public void junglePlayTwoPlayers() throws Exception {
        junglePlay("jungle_play_2p", 2, 2, 900);
    }

    @Test
    public void jungleEnd() throws Exception {
        junglePlay("jungle_end_1p", 1, 1, 6000);
    }
}
