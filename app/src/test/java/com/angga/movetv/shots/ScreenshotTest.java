package com.angga.movetv.shots;

import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import com.angga.movetv.audio.Music;
import com.angga.movetv.core.DemoScene;
import com.angga.movetv.core.PerfOverlay;
import com.angga.movetv.core.Scene;

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
}
