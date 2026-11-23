package com.wiggleplay.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.wiggleplay.app.audio.Music;
import com.wiggleplay.app.audio.Sfx;
import com.wiggleplay.app.audio.Voice;
import com.wiggleplay.app.core.DemoScene;
import com.wiggleplay.app.core.GameView;
import com.wiggleplay.app.core.PerfOverlay;
import com.wiggleplay.app.core.Profile;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.SceneManager;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.debug.DebugPoseView;
import com.wiggleplay.app.games.GameRegistry;
import com.wiggleplay.app.games.fruit.FruitScene;
import com.wiggleplay.app.gl.RaceGlView;
import com.wiggleplay.app.pose.PoseFrame;
import com.wiggleplay.app.pose.PoseHub;
import com.wiggleplay.app.scenes.AttractScene;
import com.wiggleplay.app.scenes.GameSelectScene;
import com.wiggleplay.app.scenes.LevelSelectScene;
import com.wiggleplay.app.scenes.PlaceholderGameScene;
import com.wiggleplay.app.scenes.PlayersScene;
import com.wiggleplay.app.scenes.SettingsOverlay;
import com.wiggleplay.app.scenes.SetupScene;
import com.wiggleplay.app.ui.TileMenu;

/**
 * Wiggle Play. USB webcam (or the no-camera test body) -> PoseHub -> the screens. Nothing leaves the TV: the app
 * has no internet permission. The app starts at the attract screen.
 *
 * Fire TV remote (every screen works with these keys only):
 *   Arrows        menus: move the focus highlight; games in test mode: jump / duck / lean (test body)
 *   OK            select; hold 2 s = switch between the screens and the old debug view
 *   Play/Pause    menus: select; games: pause music (camera) or arms out on/off (test mode)
 *   Back          back one screen (attract: nothing); during a round: end it, go to level select
 *   Menu (≡)      short press = settings (a game pauses); hold 2 s = test mode on/off
 *   Left (◀)      hold 2 s = performance overlay on/off
 *   Rewind (⏪)    menus: hold 2 s = sound and speed test screen (the old DemoScene; Back leaves it)
 *   Rewind/Fast-forward  games in test mode: previous/next preset dance pose
 * DemoScene keeps its old quick keys: Left = light boost, Menu = 1 or 2 players.
 *
 * Keyboard (emulator extras): Enter = OK, Esc = Back, P overlay, T test mode; DemoScene: M song, F1-F12 and
 * Z X C V sound effects. In test mode: mouse drag moves the right hand, W jump, S duck, A/D lean, Space arms
 * out, 1-9 poses, Tab switches player.
 */
public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 7;
    private static final long LONG_PRESS_MS = 2000;

    private GameView gameView;
    private RaceGlView raceGl;
    private FrameLayout root;
    private DebugPoseView debugView;
    private boolean debugMode = false;
    private PoseHub hub;
    private PerfOverlay perf;
    private Sfx sfx;
    private Music music;
    private Voice voice;
    private Profile profile;
    private SceneManager scenes;
    private TileMenu.Env env;
    private SettingsOverlay settings;
    private boolean savedApplied;

    // debug view feed
    private final PoseFrame frame = new PoseFrame();
    private String statsText = "";
    private long statsAt = 0;
    private boolean ticking = false;

    private final Hold menuKey = new Hold(), okKey = new Hold(), leftKey = new Hold(), rewindKey = new Hold();

    /** Tells a short press of a key from a 2-second hold. */
    private static final class Hold {
        private long downTime = -1;
        private boolean longDone = false;

        /** @return true at the moment a hold reaches 2 seconds */
        boolean down(KeyEvent e) {
            if (e.getRepeatCount() == 0) {
                downTime = e.getDownTime();
                longDone = false;
                return false;
            }
            if (!longDone && downTime >= 0 && e.getEventTime() - downTime >= LONG_PRESS_MS) {
                longDone = true;
                return true;
            }
            return false;
        }

        /** @return true if the key was released before the 2 seconds were up */
        boolean up() {
            boolean shortPress = downTime >= 0 && !longDone;
            downTime = -1;
            return shortPress;
        }
    }

    private final Choreographer.FrameCallback ticker = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!ticking) return;
            hub.latest(frame);
            long now = SystemClock.uptimeMillis();
            if (now - statsAt >= 250) { // the text changes slowly: do not rebuild it 60 times a second
                statsText = hub.statsText();
                statsAt = now;
            }
            debugView.update(hub.cameraPicture(), frame, statsText);
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        com.wiggleplay.app.ui.Theme.init(this);
        com.wiggleplay.app.games.fruit.FruitScene.initArt(this);
        com.wiggleplay.app.games.jungle.JungleScene.initArt(this);
        com.wiggleplay.app.games.dance.DanceScene.initArt(this);
        hub = new PoseHub();
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        perf = new PerfOverlay(debuggable);
        sfx = new Sfx(this);
        music = new Music();
        voice = new Voice(this);
        profile = new Profile(this);
        scenes = new SceneManager();
        env = new TileMenu.Env(hub, sfx, music, voice, profile, perf);
        env.scenes = scenes;
        settings = new SettingsOverlay(env);
        env.settings = settings;
        hub.setMode(env.players);
        // every home-flow screen is wrapped so the menu key opens the settings over it
        scenes.register("attract", settings.host(new AttractScene(env)));
        scenes.register("setup", settings.host(new SetupScene(env)));
        scenes.register("players", settings.host(new PlayersScene(env)));
        scenes.register("gameselect", settings.host(new GameSelectScene(env)));
        scenes.register("levelselect", settings.host(new LevelSelectScene(env)));
        scenes.register(GameRegistry.PLACEHOLDER_SCENE, settings.host(new PlaceholderGameScene(env)));
        scenes.register("fruit", settings.host(new FruitScene(env)));
        scenes.register("dance", settings.host(new com.wiggleplay.app.games.dance.DanceScene(env)));
        scenes.register("skyplane", settings.host(new com.wiggleplay.app.games.sky.SkyPlaneScene(env)));
        scenes.register("sky", settings.host(new com.wiggleplay.app.games.sky.SkyScene(env)));
        scenes.register("jungle", settings.host(new com.wiggleplay.app.games.jungle.JungleScene(env)));
        scenes.register("bubbles", settings.host(new com.wiggleplay.app.games.bubbles.BubbleScene(env)));
        scenes.register("bowling", settings.host(new com.wiggleplay.app.games.bowling.BowlingScene(env)));
        // the sound and speed test keeps its own quick keys, so it is not wrapped
        scenes.register("demo", new DemoScene(perf, sfx, music));
        gameView = new GameView(this, hub, scenes, perf);
        // The 3D view sits under the game screen (hidden until a 3D scene is current); the root is black, which
        // makes the letterbox bars.
        raceGl = new RaceGlView(this);
        perf.setGlStats(raceGl.stats());
        gameView.setGlView(raceGl);
        // the race needs the GL view, which is made after the other scenes
        scenes.register("racesteer", settings.host(new com.wiggleplay.app.games.race.RaceSteerScene(env)));
        scenes.register("race", settings.host(new com.wiggleplay.app.games.race.RaceScene(env, raceGl)));
        root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.BLACK);
        root.addView(raceGl, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        root.addView(gameView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        debugView = new DebugPoseView(this);
        hub.setStatusListener(debugView::setStatus);
        setContentView(root);
        scenes.go("attract", Scene.SceneArgs.EMPTY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        raceGl.onResume();
        // Always start the hub: without the camera permission it runs in test mode until the permission is granted.
        hub.start(this);
        if (!savedApplied) { // the light boost lives in the camera engine, which exists once the hub has started
            savedApplied = true;
            env.applySaved();
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            debugView.setStatus("Waiting for camera permission…");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
        if (debugMode) startTicker();
    }

    @Override
    protected void onPause() {
        ticking = false;
        music.stop(0);
        hub.stop(); // releases the webcam so Zoom etc. can use it
        raceGl.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        sfx.release();
        music.release();
        voice.release();
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        SpriteCache.shared().noteTrim(level);
        // Mild hints (5, 10) arrive while a game is running; the scenes still hold their pictures, so emptying the
        // cache then frees nothing and only makes every sprite repaint (a hitch). Act on the serious levels only.
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) SpriteCache.shared().clear();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                debugView.setStatus(null);
                hub.start(this);
            } else {
                debugView.setStatus("Camera permission denied: test mode. Allow it in Settings > Applications > Wiggle Play.");
                debugView.postDelayed(() -> debugView.setStatus(null), 6000);
            }
        }
    }

    private void startTicker() {
        ticking = true;
        Choreographer.getInstance().postFrameCallback(ticker);
    }

    /** Switches between the game screen and the old debug view. */
    private void setDebugMode(boolean on) {
        debugMode = on;
        if (on) {
            music.stop(200);
            setContentView(debugView);
            startTicker();
        } else {
            ticking = false;
            setContentView(root);
        }
    }

    private static boolean isRemoteKey(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return true;
            default:
                return false;
        }
    }

    private boolean inDemo() {
        return "demo".equals(scenes.currentName());
    }

    /**
     * True while the arrows and media keys belong to the game (in test mode they drive the test body there):
     * in game screens and the sound test, but never while the settings panel is open and never in menus,
     * where the arrows always move the focus.
     */
    private boolean gameKeys() {
        if (settings.isOpen()) return false;
        return inDemo() || GameRegistry.isGameScene(scenes.currentName());
    }

    /** Gives the test body the keys it owns right now. */
    private boolean testBodyTakes(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_T || !isRemoteKey(keyCode) || gameKeys()) return hub.handleKey(event);
        return false;
    }

    /** One short press reaches the scene as a press and a release. */
    private void tap(int keyCode) {
        if (debugMode) return;
        gameView.input().keyDown(keyCode);
        gameView.input().keyUp(keyCode);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (testBodyTakes(keyCode, event)) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (event.getRepeatCount() == 0) {
                    if (debugMode) {
                        setDebugMode(false);
                    } else if (inDemo()) {
                        scenes.go("attract", Scene.SceneArgs.EMPTY);
                    } else {
                        gameView.input().keyDown(KeyEvent.KEYCODE_BACK);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (menuKey.down(event)) hub.toggleTestMode();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (okKey.down(event)) setDebugMode(!debugMode);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (leftKey.down(event)) gameView.togglePerf();
                return true;
            case KeyEvent.KEYCODE_P:
                if (event.getRepeatCount() == 0) gameView.togglePerf();
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (debugMode) return true;
                if (gameKeys()) {
                    if (event.getRepeatCount() == 0) gameView.input().keyDown(keyCode);
                } else if (rewindKey.down(event)) { // menus: hold 2 s opens the sound and speed test
                    env.leaveMenus();
                    scenes.go("demo", Scene.SceneArgs.EMPTY);
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!debugMode && event.getRepeatCount() == 0) gameView.input().keyDown(keyCode);
                return true;
            default:
                if (!debugMode) gameView.input().keyDown(keyCode);
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (testBodyTakes(keyCode, event)) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (!debugMode) gameView.input().keyUp(KeyEvent.KEYCODE_BACK);
                return true; // never let Back close the app
            case KeyEvent.KEYCODE_MENU:
                if (menuKey.up()) {
                    if (debugMode || inDemo()) hub.setMode(hub.players() == 1 ? 2 : 1); // old quick key
                    else tap(KeyEvent.KEYCODE_MENU);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (okKey.up()) {
                    if (debugMode) {
                        debugView.toggleCamera();
                    } else { // a short press reaches the scene as one press
                        tap(keyCode);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (leftKey.up()) {
                    if (debugMode || inDemo()) env.toggleLightBoost(); // old quick key
                    else tap(KeyEvent.KEYCODE_DPAD_LEFT);              // menus: move the focus
                }
                return true;
            case KeyEvent.KEYCODE_P:
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (debugMode) return true;
                if (gameKeys()) gameView.input().keyUp(keyCode);
                else if (rewindKey.up()) tap(keyCode);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!debugMode) gameView.input().keyUp(keyCode);
                return true;
            default:
                if (!debugMode) gameView.input().keyUp(keyCode);
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // the game screen handles touches itself; this is for the debug view
        if (hub.handleTouch(event, debugView.getWidth(), debugView.getHeight())) return true;
        return super.onTouchEvent(event);
    }
}
