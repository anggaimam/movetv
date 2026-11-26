package com.wiggleplay.app.games.race;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.wiggleplay.app.core.GameView;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.gl.DetailTexture;
import com.wiggleplay.app.gl.Mat4;
import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import com.wiggleplay.app.gl.ProcMesh;
import com.wiggleplay.app.gl.RaceGlView;
import com.wiggleplay.app.gl.RenderList;
import com.wiggleplay.app.gl.StaticBatcher;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.core.ProfileLogic;
import com.wiggleplay.app.input.AirplaneArms;
import com.wiggleplay.app.input.BodyLean;
import com.wiggleplay.app.input.SteerInput;
import com.wiggleplay.app.input.SteeringWheel;
import com.wiggleplay.app.input.JumpDetect;
import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.Keypoint;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Turbo Trucks, one player: a 3D arcade race on one of three tracks. The truck always drives forward; the child
 * steers with the gesture picked on the "How do you want to steer?" screen (T-23: airplane arms, body lean or an
 * invisible wheel, scene arg {@code steer}), jumps to start a 1.6 s rocket boost, and crouches to
 * slow down. Three laps, coins and a golden star, then the shared end screen. There is never a game over.
 * The world is drawn by OpenGL under this transparent screen; this class only builds the list of things to draw,
 * and draws the HUD on the Canvas. Remote keys: Left/Right steer, Up or OK boost, Down brake; in test mode the
 * test body's arms-out/lean/jump/duck keys do the same through the pose (a lean steers when the arms are not out).
 * Nothing is allocated per frame.
 */
public final class RaceScene implements Scene {

    private static final String GAME_ID = "race";
    private static final String[] TRUCK_MESH = {"vehicle-truck-yellow", "vehicle-truck-red",
            "vehicle-truck-green", "vehicle-truck-purple"};
    private static final String[] PART_NAMES = {"underside", "body", "wheel-front-left", "wheel-front-right",
            "wheel-back-left", "wheel-back-right"};
    private static final int P_FL = 2, P_FR = 3;
    /** Ground grid: 140 units (past the far plane 60 in every direction), 14 x 14 cells of 10 (392 triangles). */
    private static final float GROUND_SIZE = 140f;
    private static final int GROUND_CELLS = 14;
    private static final float GROUND_STEP = GROUND_SIZE / GROUND_CELLS;
    private static final int SHADOW = 0x59000000;      // alpha 0.35, black
    private static final int COIN_COLOUR = 0xFFD04D, STAR_COLOUR = 0xFFC933, GLOW_COLOUR = 0x4DFFE066; // glow alpha 0.3
    private static final float WHEEL_R = 0.3f, TWO_PI = 6.2831855f;
    private static final float INTRO_SECONDS = 2f;
    private static final long KEY_HOLD_MS = 350;
    /** Cell size of the static batcher (m) and the block size of a road chunk in cells (6 x 10 m = 60 m). */
    private static final float CELL = 10f;
    private static final int CHUNK_CELLS = 6;
    /** Scenery truck lanes as multiples of laneFrac x halfWidth: left, right, centre. */
    private static final float[] SCENERY_LANE = {-1f, 1f, 0f};
    /** Seconds the speed-meter note shows the build time after loading. */
    private static final float BUILD_NOTE_SECONDS = 5f;
    private static final boolean[] RULES_SEEN = new boolean[4];

    private final TileMenu.Env env;
    private final RaceGlView gl;                    // null in the screenshot tests (no OpenGL there)
    private final Hud hud;
    private final RaceHud raceHud = new RaceHud();
    private final RaceRules rules = new RaceRules();
    private final ChaseCamera camera = new ChaseCamera();
    // T-24: the second player's own camera, jump detector, steering inputs (one of each mode), HUD and controls
    private final ChaseCamera camera2 = new ChaseCamera();
    private final ChaseCamera[] cams = {camera, camera2};
    private final JumpDetect jump = new JumpDetect();
    private final JumpDetect jump2 = new JumpDetect();
    private final SteerInput[] steerModes2 = {new AirplaneArms(), new BodyLean(), new SteeringWheel()};
    private SteerInput steerIn2 = steerModes2[2];
    private final RaceHud raceHud2 = new RaceHud();
    private int np = 1;
    private final int[] pickTruck = new int[2], pickPaint = new int[2];   // T-34
    private float noneT2;
    private float finalT2 = 99f;
    private PlayerPose introPose2;
    private int coinLowId = -1, arrowId = -1;
    private static final int P1_RGB = 0x29B6F6, P2_RGB = 0xFF6EC7;
    private final AirplaneArms arms = new AirplaneArms();
    private final BodyLean lean = new BodyLean();
    private final SteeringWheel wheel = new SteeringWheel();
    private final SteerInput[] steerModes = {arms, lean, wheel};
    private SteerInput steerIn = wheel;
    private int mode = RaceArt.MODE_WHEEL;
    private float noteT;
    private final StringBuilder noteSb = new StringBuilder(64);
    private static final String[] INTRO_TEXT = RaceHud.HINT;
    private final RenderList list = new RenderList(420);

    // scratch
    private final float[] mTruck = new float[16], mT = new float[16], mR = new float[16], mA = new float[16],
            mB = new float[16], mC = new float[16];
    private final float[] smp = new float[3], smp2 = new float[3];

    private final int[] truckId = new int[4];
    private final int[][] partIdx = new int[4][6];
    private final MeshData[] truckData = new MeshData[4];
    private int groundId, shadowId, coinId, starId, nitroId, magnetId, sparkleId = -1;
    /** T-30b: the lakes of the track and the sparkles on them (8 per lake: x, z, phase); empty on LOW. */
    private Lakes lakes = Lakes.NONE;
    private float[] sparkle = new float[0];
    /** The world's look (T-29). Written in full because {@code Theme} here means the menu theme in com.wiggleplay.app.ui. */
    private com.wiggleplay.app.games.race.Theme theme = Themes.MEADOW;
    private final int[] ringId = new int[4];
    private int ringCount;
    /** The world is built on a worker thread; {@link #update} takes the result over, and the intro waits for it (R1). */
    private volatile WorldBuild pendingWorld;
    private volatile boolean buildFailed;
    private volatile int buildGen;
    private boolean worldReady = true;
    private float loadingT;

    // scenery trucks
    private final float[] scS = new float[3], scSpin = new float[3], scWheelYaw = new float[3];

    private RaceLogic logic;
    private TrackSpline layout;
    /** T-31: pooled dust discs (x, z, age, unused) at a launch or landing, and the mesh. */
    private final float[] dustPool = new float[8 * 4];
    /** T-31 Part B: 6 pooled spray quads (x, z, age, unused) behind the wheels on ice or water, and when the next one is due. */
    private final float[] sprayPool = new float[6 * 4];
    private int sprayNext;
    private float sprayTimer;
    private static final float SPRAY_SECONDS = 0.4f;
    private int dustId = -1, dustNext;
    private static final float DUST_SECONDS = 0.5f;
    private int buildMs;
    private float buildNoteT;
    private RaceLevel raceLevel = RaceLevel.EASY;
    private int level = 1;
    private boolean intro, introAfterRules;
    private float introT, time, noneT, finalT, wheelYawPlayer;
    private long leftUntil, rightUntil;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.Bitmap camBitmap;
    private PlayerPose introPose;

    /**
     * Tells the game whether the rules screen has been shown already (tests use this).
     * @param seen true to skip the rules screen on the next entry
     */
    public static void setRulesSeen(boolean seen) {
        for (int i = 0; i < RULES_SEEN.length; i++) RULES_SEEN[i] = seen;
    }

    /**
     * Creates the game.
     * @param env what the screens share
     * @param gl the 3D view under the game screen (null in the screenshot tests: a flat picture is drawn instead)
     */
    public RaceScene(TileMenu.Env env, RaceGlView gl) {
        this.env = env;
        this.gl = gl;
        this.hud = new Hud(env, RaceLogic.SAFETY_SECONDS, "race");
        hud.setEndTitle("Great driving!");
        jump.setRequireHeadRise(true);
    }

    @Override
    public boolean world3d() {
        return true;
    }

    // ---- entering and leaving ----

    @Override
    public void enter(SceneArgs args) {
        env.leaveMenus();
        level = Math.max(1, Math.min(3, args.getInt("level", 1)));
        raceLevel = RaceLevel.fromNumber(level);
        np = args.getInt("players", env.players) == 2 ? 2 : 1;   // T-24
        int own = env.profile != null ? com.wiggleplay.app.core.ProfileLogic.raceTruckIndex(env.profile.raceTruck()) : 0;
        pickTruck[0] = Math.floorMod(args.getInt("truck0", own), 4);
        pickTruck[1] = Math.floorMod(args.getInt("truck1", own + 1), 4);
        pickPaint[0] = TruckPaints.clean(args.getInt("paint0", env.profile != null ? env.profile.racePaint() : 0));
        pickPaint[1] = TruckPaints.clean(args.getInt("paint1", 0));
        long built0 = SystemClock.uptimeMillis();
        worldReady = false;
        pendingWorld = null;
        final int gen = ++buildGen;
        if (env.profile != null) TrackChoice.load(env.profile.raceTrack(), env.profile.raceReversed());   // T-32: the saved track screen choice
        layout = TrackSpline.build(TrackChoice.def(), TrackChoice.reversed(), raceLevel);
        if (gl != null) {
            gl.loadMeshes(TRUCK_MESH[0], TRUCK_MESH[1], TRUCK_MESH[2], TRUCK_MESH[3]);
            groundId = -1;
            shadowId = gl.addMesh("shadow", ProcMesh.disc(1.2f));
            dustId = gl.addMesh("dust", ProcMesh.disc(1f));
            for (int i = 0; i < 8; i++) dustPool[i * 4 + 2] = DUST_SECONDS;
            for (int i = 0; i < 6; i++) sprayPool[i * 4 + 2] = SPRAY_SECONDS;
            coinId = gl.addMesh("coin", ProcMesh.coinReeded());
            coinLowId = gl.addMesh("coinLow", ProcMesh.coinLow());
            arrowId = gl.addMesh("playerArrow", ProcMesh.playerArrow());
            starId = gl.addMesh("star", ProcMesh.starBevelled(0.9f, 0.4f, 0.2f));
            nitroId = gl.addMesh("nitro", ProcMesh.nitroCan());
            magnetId = gl.addMesh("magnet", ProcMesh.magnet());
            // T-32 Part B: the player drives the saved colour, the three scenery trucks the next three in the order yellow, red, green, purple
            // T-34: the Pick Your Truck screen passes each player's truck and paint; the scenery trucks follow player 1's model
            int mine = pickTruck[0];
            for (int t = 0; t < 4; t++) {
                truckId[t] = gl.meshId(TRUCK_MESH[t < np ? pickTruck[t] : (mine + t) % 4]);
                truckData[t] = gl.meshData(truckId[t]);
                for (int p = 0; p < 6; p++) partIdx[t][p] = truckData[t].partIndex(PART_NAMES[p]);
            }
            // T-29: the theme decides sky, fog, sun, ground, road colours, ground patches and the backdrop rings
            theme = Themes.byId(TrackChoice.def().themeId);
            // a theme with terrain has its own ground; the others keep the flat ground grid
            if (theme.look.terrain == null) groundId = gl.addMesh("ground", ProcMesh.groundGrid(GROUND_SIZE, GROUND_CELLS));
            // T-24: two players use the LOW world (no filler props, far rings only, fewer patches)
            final boolean low = GameView.quality == GameView.LOW || np == 2;
            // T-30 Gate 0: with the speed meter showing, the static world is merged per group so keys 1 and 2 can switch terrain
            // and props off (a few more draw calls than the normal build); the other switches work either way
            final boolean split = env.perf != null && env.perf.isVisible();
            gl.toggles().reset();
            gl.setSky(theme.sky);   // also puts the plain light back
            gl.setFogColor(theme.horizon);
            gl.setFog(theme.fogNear, theme.fogFar);
            gl.setSunDir(theme.sunDir[0], theme.sunDir[1], theme.sunDir[2]);
            gl.setViewports(np);
            gl.setChunkCullDistance(np == 2 ? 45f : 0f);   // T-24: 45 m per viewport
            // R1: the rest of the world (road, patches, props, rings, sky dome, detail texture) is built on a worker thread
            // while the rules / loading screen shows; update() commits it, and the intro waits for it
            final com.wiggleplay.app.games.race.Theme th = theme;
            final TrackSpline lay = layout;
            final TrackDef def = TrackChoice.def();
            final boolean reversed = TrackChoice.reversed();
            final RaceLevel lvl = raceLevel;
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        WorldBuild w = buildWorld(th, lay, def, reversed, lvl, low, split, built0);
                        if (gen == buildGen) pendingWorld = w;
                    } catch (Throwable t) {
                        android.util.Log.e("WigglePlayRace", "World build failed: " + t, t);
                        if (gen == buildGen) buildFailed = true;
                    }
                }
            }, "WorldBuild");
            buildFailed = false;
            worker.start();
        } else {
            worldReady = true;
        }
        if (env.sfx != null) {
            env.sfx.loadAsset("engine", "audio/race/engine.ogg");
            env.sfx.loadAsset("skid", "audio/race/skid.ogg");
            env.sfx.loadAsset("impact", "audio/race/impact.ogg");
        }
        raceHud.setTrack(layout, TrackChoice.key());
        String steerName = args.getString("steer", env.profile != null ? env.profile.raceSteer()
                : ProfileLogic.DEFAULT_RACE_STEER);
        mode = RaceArt.modeOf(ProfileLogic.cleanRaceSteer(steerName));
        steerIn = steerModes[mode];
        steerIn2 = steerModes2[mode];
        raceHud.setMode(mode);
        raceHud2.playerColour = 0xFFFF6EC7;
        raceHud2.setMode(mode);
        rules.setMode(mode);
        hud.setRules(rules);
        jump.reset();
        for (SteerInput si : steerModes) si.reset();
        for (SteerInput si : steerModes2) si.reset();
        jump2.reset();
        jump2.setRequireHeadRise(true);
        newLogic();
        raceHud.reset();
        raceHud2.reset();
        boolean showRules = !RULES_SEEN[level];
        RULES_SEEN[level] = true;
        intro = false;
        introAfterRules = false;
        if (showRules) {
            introAfterRules = true;
            hud.start(np, true);
        } else {
            startIntro();
        }
    }

    @Override
    public void exit() {
        buildGen++;
        pendingWorld = null;
        stopLoops();
        if (env.perf != null) env.perf.setNote("");
        if (gl != null) gl.release();
        if (env.sfx != null) {
            env.sfx.unloadAsset("engine");
            env.sfx.unloadAsset("skid");
            env.sfx.unloadAsset("impact");
        }
        if (env.music != null) env.music.stop(300);
        RaceArt.unload();
    }

    private void newLogic() {
        logic = new RaceLogic(layout, raceLevel, np, System.nanoTime());
        for (int p = 0; p < np; p++) cams[p].snap(logic.car(p).x(), logic.car(p).z(), logic.car(p).yaw());
        for (int i = 0; i < 3; i++) {
            scS[i] = layout.lapLength() * (i + 1) / (float) (scenery() + 1);
            scSpin[i] = 0f;
            scWheelYaw[i] = 0f;
        }
        wheelYawPlayer = 0f;
        leftUntil = 0;
        rightUntil = 0;
        noneT = 0f;
        finalT = 99f;
        noneT2 = 0f;
        finalT2 = 99f;
    }

    /** Scenery trucks: 3 for one player, 2 for two (T-24). */
    private int scenery() { return 4 - np; }

    private void startIntro() {
        intro = true;
        introT = 0f;
        raceHud.reset();
    }

    private void beginRound() {
        newLogic();
        raceHud.reset();
        raceHud2.reset();
        steerIn.reset();
        steerIn2.reset();
        hud.start(np, false);
    }

    // ---- per frame ----

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        PlayerPose pose = f.pose.players[0];
        PlayerPose pose2 = np == 2 ? f.pose.players[1] : null;
        camBitmap = f.camera;
        if (!worldReady) {
            loadingT += dt;
            commitWorld();
        }
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) && intro) {
            env.sfx("back");
            env.go("levelselect", SceneArgs.of("gameId", GAME_ID));
            return;
        }
        if (intro) {
            updateIntro(dt, f, pose, pose2);
            return;
        }
        String sel = hud.update(dt, f);
        if (sel != null) {
            stopLoops();
            if ("again".equals(sel)) {
                beginRound();
            } else if ("go".equals(sel)) {
                if (introAfterRules) {
                    introAfterRules = false;
                    startIntro();
                } else {
                    beginRound();
                }
            } else if ("rules".equals(sel)) {
                hud.openRules();
            } else if ("home".equals(sel)) {
                env.go("gameselect", SceneArgs.EMPTY);
            } else {
                env.go("levelselect", SceneArgs.of("gameId", GAME_ID));
            }
            return;
        }
        if (hud.phase() == Hud.RULES) {
            rules.update(dt);
            stopLoops();
            return;
        }
        // gestures run every frame so their baselines settle, but only count while racing
        steerIn.update(pose, f.nowMs, dt);
        jump.update(pose, f.nowMs);
        boolean jumpEvent = jump.consumeJump();
        boolean jumpEvent2 = false;
        if (np == 2) {
            steerIn2.update(pose2, f.nowMs, dt);
            jump2.update(pose2, f.nowMs);
            jumpEvent2 = jump2.consumeJump();
        }
        speedMeterNote(dt);
        if (hud.phase() != Hud.END) {
            Controls in = readInput(f, pose, 0);
            jumpEvent |= in.jump;
            Controls in2 = np == 2 ? readInput(f, pose2, 1) : null;
            boolean racing = hud.running();
            if (racing) {
                logic.setControls(0, in.steer, in.brake);
                if (jumpEvent) {
                    boolean was = logic.boosting(0);
                    logic.onJump(0);
                    if (!was && logic.boosting(0)) env.sfx("whoosh");
                }
                if (np == 2) {
                    logic.setControls(1, in2.steer, in2.brake);
                    if (jumpEvent2 || in2.jump) {
                        boolean was = logic.boosting(1);
                        logic.onJump(1);
                        if (!was && logic.boosting(1)) env.sfx("whoosh");
                    }
                }
                logic.update(dt);
                for (int p = 0; p < np; p++) raceEvents(p);
                if (logic.over() || hud.phase() == Hud.ENDING) finishRace();
            }
            updateHud(0, dt, racing, in);
            if (np == 2) updateHud(1, dt, racing, in2);
        } else {
            // behind the end screen the trucks keep driving by themselves
            for (int p = 0; p < np; p++) logic.setControls(p, autoSteer(logic.car(p)), false);
            logic.update(dt);
            raceHud.update(dt, false);
            if (np == 2) raceHud2.update(dt, false);
        }
        if (!hud.paused() && hud.phase() != Hud.RULES) moveScenery(dt);
        followCamera(dt);
        updateSound();
        submitWorld();
    }

    private static final class Controls {
        float steer;
        boolean jump, brake;
    }

    private final Controls input = new Controls();
    private final Controls input2 = new Controls();

    /** Player 0 also takes the remote's keys; player 1 steers by body only (in test mode the hub moves the active test body). */
    private Controls readInput(Frame f, PlayerPose pose, int p) {
        if (p == 1) {
            boolean test = "test".equals(f.pose.source);
            input2.steer = test && steerIn2.steerState() == SteerInput.State.NONE ? leanSteer(pose) : steerIn2.steer();
            input2.jump = false;
            input2.brake = jump2.crouching();
            return input2;
        }
        long now = f.nowMs;
        if (f.input.pressed(KeyEvent.KEYCODE_DPAD_LEFT) || f.input.held(KeyEvent.KEYCODE_DPAD_LEFT)
                || f.input.pressed(KeyEvent.KEYCODE_A) || f.input.held(KeyEvent.KEYCODE_A)) leftUntil = now + KEY_HOLD_MS;
        if (f.input.pressed(KeyEvent.KEYCODE_DPAD_RIGHT) || f.input.held(KeyEvent.KEYCODE_DPAD_RIGHT)
                || f.input.pressed(KeyEvent.KEYCODE_D) || f.input.held(KeyEvent.KEYCODE_D)) rightUntil = now + KEY_HOLD_MS;
        float keySteer = (now < rightUntil ? 1f : 0f) - (now < leftUntil ? 1f : 0f);
        boolean test = "test".equals(f.pose.source);
        float steer;
        if (keySteer != 0f) steer = keySteer;
        else if (test && steerIn.steerState() == SteerInput.State.NONE) steer = leanSteer(pose);
        else steer = steerIn.steer();
        input.steer = steer;
        input.jump = f.input.pressed(KeyEvent.KEYCODE_DPAD_UP) || f.input.pressed(KeyEvent.KEYCODE_DPAD_CENTER)
                || f.input.pressed(KeyEvent.KEYCODE_ENTER) || f.input.pressed(KeyEvent.KEYCODE_W)
                || f.input.pressed(KeyEvent.KEYCODE_SPACE);
        input.brake = jump.crouching() || f.input.held(KeyEvent.KEYCODE_DPAD_DOWN) || f.input.held(KeyEvent.KEYCODE_S);
        return input;
    }

    /** Test mode only: with the arms not out, a lean of the upper body steers (tilted shoulder line, same curve). */
    private static float leanSteer(PlayerPose p) {
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        if (!p.present || ls.s < 0.3f || rs.s < 0.3f || rs.x - ls.x < 20f) return 0f;
        float a = (float) Math.toDegrees(Math.atan2(ls.y - rs.y, rs.x - ls.x));
        float m = Math.max(0f, Math.min(1f, (Math.abs(a) - 6f) / 22f));
        return a > 0f ? -m : m;
    }

    private void raceEvents(int p) {
        CarModel car = logic.car(p);
        ChaseCamera cam = cams[p];
        if (logic.consumeNitro(p) > 0) env.sfx("star", 1.5f);   // T-26: a nitro can
        if (logic.consumeMagnet(p) > 0) env.sfx("star", 1.2f);   // the magnet
        int coins = logic.consumeCoins(p);
        if (coins > 0) {
            env.sfx("coin", 1f + 0.06f * Math.min(coins, 4));
            raceHud.popup(1);
        }
        if (logic.consumeStar(p)) {
            env.sfx("star");
            raceHud.popup(5);
        }
        if (logic.consumeLap(p)) {
            if (logic.lapsDone(p) < logic.lapsTotal()) env.sfx("go");
            if (logic.currentLap(p) == logic.lapsTotal() && !logic.finished(p)) { if (p == 0) finalT = 0f; else finalT2 = 0f; }
        }
        if (logic.consumeLaunch(p) > 0) {   // T-31: off the lip of a ramp
            env.sfx("whoosh", 1.2f);
            spawnDust(car.x(), car.z());
        }
        if (logic.consumeLanding(p) > 0) {
            if (env.sfx != null) env.sfx.play("impact", 1.3f, Math.min(0.7f, 0.3f + 0.05f * logic.landingImpact(p)));
            spawnDust(car.x(), car.z());
            cam.onLanding();
        }
        if (logic.consumeBump(p) > 0) {   // T-31 Part B: a pothole
            if (env.sfx != null) env.sfx.play("impact", 0.75f, 0.45f);
            cam.onBump();
        }
        if (car.consumeHitWall() && env.sfx != null) {
            env.sfx.play("impact", 1f, Math.max(0.25f, Math.min(1f, car.speed() / (raceLevel.baseSpeed * 1.2f))));
        }
    }

    private void finishRace() {
        stopLoops();
        int stars = logic.stars(0);
        if (np == 2) {   // T-24: both players' stars; only player 1's go to the profile
            if (env.profile != null) {
                env.profile.recordStars(GAME_ID, level, stars);
                env.profile.recordRaceBest(TrackChoice.key(), level, stars);
            }
            hud.finish(stars, logic.stars(1), logic.score(0), logic.score(1));
            return;
        }
        if (env.profile != null) {
            env.profile.recordStars(GAME_ID, level, stars);
            env.profile.recordRaceBest(TrackChoice.key(), level, stars);   // T-32 Part B: shown on the track card next visit
        }
        hud.finish(stars, 0, logic.score(0), 0);
    }

    private void updateHud(int p, float dt, boolean racing, Controls in) {
        CarModel car = logic.car(p);
        RaceHud h = p == 0 ? raceHud : raceHud2;
        SteerInput si = p == 0 ? steerIn : steerIn2;
        h.lap = logic.currentLap(p);
        h.lapTotal = logic.lapsTotal();
        h.meter = logic.boostMeter(p);
        h.boosting = logic.boosting(p);
        h.boostLeft = logic.boostLeft(p);
        h.magnetK = logic.magnetLeft(p) / RaceLogic.MAGNET_SECONDS;
        h.braking = racing && in.brake && !logic.boosting(p);
        boolean active = si.steerState() == SteerInput.State.ACTIVE;
        h.iconDeg = si.iconAngleDeg();
        h.steerActive = active;
        if (p == 0) {
            if (racing) noneT = active ? 0f : noneT + dt;
            h.noneSeconds = noneT;
            if (finalT < 90f && racing) finalT += dt;
            h.finalLapSeconds = finalT;
        } else {
            if (racing) noneT2 = active ? 0f : noneT2 + dt;
            h.noneSeconds = noneT2;
            if (finalT2 < 90f && racing) finalT2 += dt;
            h.finalLapSeconds = finalT2;
        }
        h.place = np == 2 ? logic.place(p) : 0;
        h.playerS = car.s();
        h.player2S = p == 0 && np == 2 ? logic.car(1).s() : -1f;
        h.sceneryCount = scenery();
        System.arraycopy(scS, 0, h.sceneryS, 0, 3);
        h.camera = p == 0 && np == 1 ? camBitmap : null;
        h.update(dt, racing);
    }

    private void updateIntro(float dt, Frame f, PlayerPose pose, PlayerPose pose2) {
        if (!worldReady) {
            introT = 0f;   // the countdown starts when the track is ready
            introPose = pose;
            return;
        }
        introT += dt;
        introPose = pose;
        introPose2 = pose2;
        steerIn.update(pose, f.nowMs, dt);
        if (np == 2) {
            steerIn2.update(pose2, f.nowMs, dt);
            jump2.update(pose2, f.nowMs);
            jump2.consumeJump();
        }
        speedMeterNote(dt);
        jump.update(pose, f.nowMs);
        jump.consumeJump();
        boolean skip = f.input.pressed(KeyEvent.KEYCODE_DPAD_CENTER) || f.input.pressed(KeyEvent.KEYCODE_ENTER)
                || f.input.pressed(KeyEvent.KEYCODE_SPACE);
        moveScenery(dt);
        followCamera(dt);
        raceHud.iconDeg = steerIn.iconAngleDeg();
        raceHud.steerActive = steerIn.steerState() == SteerInput.State.ACTIVE;
        if (np == 2) {
            raceHud2.iconDeg = steerIn2.iconAngleDeg();
            raceHud2.steerActive = steerIn2.steerState() == SteerInput.State.ACTIVE;
        }
        submitWorld();
        if (introT >= INTRO_SECONDS || skip) {
            intro = false;
            beginRound();
        }
    }

    // ---- the background build (R1) ----

    /** Everything the worker thread makes; the main thread takes it over in {@link #commitWorld}. */
    private static final class WorldBuild {
        PaletteSwatches pal;
        final LinkedHashMap<String, MeshData> library = new LinkedHashMap<>();
        final List<StaticBatcher.Placement> placements = new ArrayList<>();
        final List<String> ringNames = new ArrayList<>();
        final List<MeshData> rings = new ArrayList<>();
        MeshData sky;
        byte[] detail;
        RaceGlView.PreparedStatic chunks;
        int ms;
        Lakes lakes = Lakes.NONE;
        boolean low;
    }

    /** Adds a world-space mesh to the library and puts it in the chunk of the middle of its bounds. */
    private static void addStatic(WorldBuild w, String name, MeshData mesh, float[] ident) {
        w.library.put(name, mesh);
        MeshData.Part part = mesh.parts[0];
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int v = 0; v < part.vertexCount; v++) {
            float x = part.vertices[v * 8], z = part.vertices[v * 8 + 2];
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        w.placements.add(new StaticBatcher.Placement(name, ident, (int) Math.floor((minX + maxX) / 2f / CELL),
                (int) Math.floor((minZ + maxZ) / 2f / CELL)));
    }

    /** Builds the road, patches, props, rings, sky dome, detail texture and the merged chunks (worker thread, no GL, no view). */
    private static WorldBuild buildWorld(com.wiggleplay.app.games.race.Theme theme, TrackSpline layout, TrackDef def,
            boolean reversed, RaceLevel raceLevel, boolean low, boolean split, long t0) {
        WorldBuild w = new WorldBuild();
        PaletteSwatches pal = new PaletteSwatches();
        w.pal = pal;
        theme.fillPalette(pal);
        TrackSpline forward = reversed ? TrackSpline.build(def, false, raceLevel) : layout;
        long seed = def.id.hashCode();
        w.lakes = Lakes.of(def);
        w.low = low;
        Terrain terrain = theme.look.terrain != null ? Terrain.create(forward, theme, seed, low, w.lakes) : null;
        List<MeshData> pieces = TrackMeshBuilder.build(layout, theme.road, pal, terrain != null ? theme.terrainSwatch(1) : -1);
        float[] ident = new float[16];
        Mat4.identity(ident);
        for (int i = 0; i < pieces.size(); i++) {
            String name = "trk_road_" + i;
            w.library.put(name, pieces.get(i));
            MeshData.Part part = pieces.get(i).parts[0];
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int v = 0; v < part.vertexCount; v++) {
                float x = part.vertices[v * 8], z = part.vertices[v * 8 + 2];
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            w.placements.add(new StaticBatcher.Placement(name, ident, (int) Math.floor((minX + maxX) / 2f / CELL),
                    (int) Math.floor((minZ + maxZ) / 2f / CELL)));
        }
        if (terrain != null) {
            // R2: the ground is a height field cut into 60 m tiles
            boolean extras = theme.look.terrain.kind == Terrain.HILLS;   // T-30b: water, lake edge, shore and rock ramps
            List<Terrain.Tile> tiles = terrain.build(pal, theme.terrainSwatch(0), theme.terrainSwatch(1),
                    theme.look.terrain.parkDark != 0 ? theme.terrainSwatch(2) : -1, extras ? theme.extraSwatch(2) : -1,
                    extras ? theme.extraSwatch(3) : -1);
            if (extras) {
                for (int i = 0; i < w.lakes.count(); i++) {
                    String name = "lake_" + i;
                    w.library.put(name, LakeMesh.build(w.lakes, i, pal, theme.extraSwatch(0), theme.extraSwatch(1)));
                    w.placements.add(new StaticBatcher.Placement(name, ident, (int) Math.floor(w.lakes.centreX(i) / CELL),
                            (int) Math.floor(w.lakes.centreZ(i) / CELL)));
                }
            }
            tiles.addAll(terrain.sidewalk(pal, theme.terrainSwatch(1)));
            for (int i = 0; i < tiles.size(); i++) {
                String name = "terrain_" + i;
                w.library.put(name, tiles.get(i).mesh);
                w.placements.add(new StaticBatcher.Placement(name, ident, tiles.get(i).cellX, tiles.get(i).cellZ));
            }
        } else {
            List<GroundPatches.Patch> patches = GroundPatches.generate(layout, theme, seed, low);
            for (int i = 0; i < patches.size(); i++) {
                GroundPatches.Patch pt = patches.get(i);
                String name = "patch_" + i;
                w.library.put(name, GroundPatches.mesh(pt, pal));
                w.placements.add(new StaticBatcher.Placement(name, ident, (int) Math.floor(pt.x / CELL), (int) Math.floor(pt.z / CELL)));
            }
        }
        // T-31: the ramps, the chevrons painted before them and the warning signs (static, in the chunks)
        RoadObjects ro = layout.roadObjects();
        for (int i = 0; i < ro.count(); i++) {
            if (ro.kind(i) == RoadObjects.RAMP) {
                addStatic(w, "ramp_" + i, ObjectMeshes.ramp(ro, i, layout, pal), ident);
                addStatic(w, "marks_" + i, ObjectMeshes.rampMarks(ro, i, layout, pal), ident);
            }
            else if (ro.kind(i) == RoadObjects.POTHOLE) addStatic(w, "pit_" + i, ObjectMeshes.pothole(ro, i, layout, pal), ident);
            else addStatic(w, "slick_" + i, ObjectMeshes.patch(ro, i, layout, pal, theme.slick), ident);
            addStatic(w, "sign_" + i, ObjectMeshes.sign(ro, i, layout, pal, theme.slick), ident);
        }
        // T-29 Part B: props scattered by fixed rules on the forward track (same layout in both directions); the filler tier is
        // left out on LOW (and, with T-24, in 2P)
        boolean tierB = !low;
        for (int r = 0; r < theme.props.length; r++) {
            com.wiggleplay.app.games.race.Theme.PropRule rule = theme.props[r];
            if (rule.tierB && !tierB) continue;
            for (int v = 0; v < PropMeshes.variants(rule.kind); v++) {
                w.library.put(PropMeshes.name(rule.kind, v), PropMeshes.build(rule.kind, v, pal, theme.propSwatches(r)));
            }
        }
        List<PropScatter.Instance> instances = new ArrayList<>();
        PropScatter.scatter(forward, theme, seed, tierB, w.placements, terrain, instances);
        if (terrain != null) PropShadows.build(terrain, pal, theme.terrainSwatch(0), instances, w.library, w.placements);
        int n = 0;
        for (int i = 0; i < theme.rings.length && n < 4; i++) {
            if (low && !theme.rings[i].low) continue;
            w.ringNames.add("ring_" + i);
            w.rings.add(Backdrop.build(theme, i, pal, low));
            n++;
        }
        if (theme.hasSky()) w.sky = SkyDome.build(theme, pal);
        if (theme.look.detail != null) w.detail = DetailTexture.atlas(theme.look.detail, def.id.hashCode());
        w.chunks = RaceGlView.prepareStatic(w.placements, w.library, CHUNK_CELLS, split);
        w.ms = (int) (SystemClock.uptimeMillis() - t0);
        return w;
    }

    /** Takes the finished world over (main thread): palette, rings, sky, detail, light and chunks; the intro starts after this. */
    private void commitWorld() {
        WorldBuild w = pendingWorld;
        if (w == null) {
            if (buildFailed) worldReady = false;   // stays on the loading screen with its message
            return;
        }
        pendingWorld = null;
        gl.setColormapSwatches(w.pal);
        ringCount = 0;
        for (int i = 0; i < w.rings.size() && ringCount < ringId.length; i++) {
            ringId[ringCount++] = gl.addMesh(w.ringNames.get(i), w.rings.get(i));
        }
        lakes = w.lakes;
        sparkleId = -1;
        sparkle = new float[0];
        if (lakes.count() > 0 && !w.low) {
            // T-30b: 8 sparkles per lake, seeded inside the water
            sparkleId = gl.addMesh("sparkle", ProcMesh.disc(0.45f, 8));
            sparkle = new float[lakes.count() * 8 * 3];
            float[] pt = new float[2];
            java.util.Random rnd = new java.util.Random(77L);
            for (int i = 0; i < lakes.count(); i++) {
                for (int q = 0; q < 8; q++) {
                    lakes.point(i, rnd.nextFloat() * 6.2831855f, 0.15f + 0.7f * rnd.nextFloat(), pt);
                    int o = (i * 8 + q) * 3;
                    sparkle[o] = pt[0];
                    sparkle[o + 1] = pt[1];
                    sparkle[o + 2] = rnd.nextFloat() * 6.2831855f;
                }
            }
        }
        gl.setSkyDome(w.sky);
        gl.setDetailTexture(w.detail);
        gl.setSky(theme.look.zenith != 0 ? theme.look.zenith : theme.sky);   // the colour behind the dome (and the plain look...)
        gl.setLight(theme.look.skyAmb, theme.look.groundAmb, theme.look.sunColor);
        gl.setFogColor(theme.horizon);
        gl.commitStatic(w.chunks);
        buildMs = w.ms;
        buildNoteT = BUILD_NOTE_SECONDS;
        worldReady = true;
    }

    // ---- the world ----

    private float autoSteer(CarModel car) {
        layout.sample(car.s() + 3.5f, smp);
        float d = (float) Math.atan2(smp[0] - car.x(), smp[1] - car.z()) - car.yaw();
        while (d > Math.PI) d -= TWO_PI;
        while (d < -Math.PI) d += TWO_PI;
        return Math.max(-1f, Math.min(1f, -d * 2.5f));
    }

    private void moveScenery(float dt) {
        float v = raceLevel.baseSpeed * 0.8f;
        for (int i = 0; i < scenery(); i++) {
            scS[i] += v * dt;
            if (scS[i] > layout.lapLength()) scS[i] -= layout.lapLength();
            scSpin[i] += v * dt / WHEEL_R;
            if (scSpin[i] > TWO_PI) scSpin[i] -= TWO_PI;
            layout.sample(scS[i], smp);
            layout.sample(scS[i] + 1f, smp2);
            float rate = angleDiff(smp2[2], smp[2]) * v;
            float want = Math.max(-0.6f, Math.min(0.6f, rate * 0.2f));
            scWheelYaw[i] += (want - scWheelYaw[i]) * Math.min(1f, dt * 10f);
        }
    }

    private void followCamera(float dt) {
        for (int p = 0; p < np; p++) {
            CarModel car = logic.car(p);
            cams[p].update(dt, car.x(), car.z(), car.yaw(), logic.boosting(p), car.y());
        }
        for (int i = 0; i < 8; i++) dustPool[i * 4 + 2] += dt;
        for (int i = 0; i < 6; i++) sprayPool[i * 4 + 2] += dt;
        for (int p = 0; p < np; p++) {
            CarModel car = logic.car(p);
            if (logic.slick(p) && car.speed() > 1f) {
                sprayTimer -= dt;
                if (sprayTimer <= 0f) {   // a quad behind a rear wheel, the two sides in turn
                    sprayTimer = 0.07f;
                    int i = sprayNext;
                    sprayNext = (sprayNext + 1) % 6;
                    float fx = (float) Math.sin(car.yaw()), fz = (float) Math.cos(car.yaw());
                    float side = (i & 1) == 0 ? -0.7f : 0.7f;
                    sprayPool[i * 4] = car.x() - fx * 1.4f - fz * side;
                    sprayPool[i * 4 + 1] = car.z() - fz * 1.4f + fx * side;
                    sprayPool[i * 4 + 2] = 0f;
                }
            }
        }
    }

    /** Puts 4 dust discs round a place (a launch or a landing); the pool holds 8. */
    private void spawnDust(float x, float z) {
        for (int q = 0; q < 4; q++) {
            int i = dustNext;
            dustNext = (dustNext + 1) % 8;
            double a = q * Math.PI / 2.0 + i;
            dustPool[i * 4] = x + (float) Math.cos(a) * 0.6f;
            dustPool[i * 4 + 1] = z + (float) Math.sin(a) * 0.6f;
            dustPool[i * 4 + 2] = 0f;
        }
    }

    private void submitWorld() {
        if (gl == null || !worldReady) return;
        buildList();
        gl.setViewports(np);
        for (int p = 0; p < np; p++) gl.setCamera(p, cams[p].eye(), cams[p].target(), cams[p].fov());
        gl.submit(list);
    }

    private void buildList() {
        list.begin();
        final int vm = np == 2 ? 3 : 1;   // the viewports that show a shared thing
        for (int v = 0; v < np; v++) {
            final int bit = 1 << v;
            // T-23: the ground grid follows the camera, snapped to whole cells so its vertices don't swim
            float[] eye = cams[v].eye();
            float gx = GROUND_STEP * (float) Math.floor(eye[0] / GROUND_STEP + 0.5f);
            float gz = GROUND_STEP * (float) Math.floor(eye[2] / GROUND_STEP + 0.5f);
            Mat4.translate(mT, gx, 0f, gz);
            if (groundId >= 0) list.add(groundId, 0, mT, theme.ground, true, bit);
            // T-29: the backdrop rings follow the camera's position (not its turning) and are drawn without fog
            for (int i = 0; i < ringCount; i++) {
                Mat4.translate(mT, eye[0], 0f, eye[2]);
                list.add(ringId[i], 0, mT, 0, false, bit, true);
            }
            // T-30b: sparkles on the lakes (within 50 m of the camera); none in two players (the world is built LOW)
            for (int i = 0; sparkleId >= 0 && i < sparkle.length; i += 3) {
                float dx = sparkle[i] - eye[0], dz = sparkle[i + 1] - eye[2];
                if (dx * dx + dz * dz > 50f * 50f) continue;
                float a = 0.15f + 0.55f * Math.max(0f, (float) Math.sin(2.5f * time + sparkle[i + 2]));
                Mat4.translate(mT, sparkle[i], 0.02f, sparkle[i + 1]);
                list.add(sparkleId, 0, mT, (Math.round(a * 255f) << 24) | 0xFFFFFF, true, bit);
            }
            addCoins(v, bit, eye);
        }
        // the trucks: the players' first (their own colours), then the friendly scenery trucks
        for (int p = 0; p < np; p++) {
            CarModel car = logic.car(p);
            addTruck(p, car.x(), car.y(), car.z(), car.yaw(), car.bodyLean(), car.bodyPitch(), car.wheelSpin(), car.frontWheelYaw(), vm);
            if (np == 2 && arrowId >= 0) {   // T-24: a floating arrow in the player colour, bobbing a little
                Mat4.translate(mA, car.x(), car.y() + 2.4f + 0.12f * (float) Math.sin(time * 4f + p * 2f), car.z());
                Mat4.rotY(mR, car.yaw());
                Mat4.multiply(mB, mA, mR);
                list.add(arrowId, 0, mB, p == 0 ? P1_RGB : P2_RGB, true, vm);
            }
        }
        // T-31: dust at the launch and the landing (alpha 0.6 to 0, radius 0.3 to 1.0 over 0.5 s)
        for (int i = 0; dustId >= 0 && i < 8; i++) {
            float age = dustPool[i * 4 + 2];
            if (age >= DUST_SECONDS) continue;
            float k = age / DUST_SECONDS, rad = 0.3f + 0.7f * k;
            Mat4.translate(mT, dustPool[i * 4], 0.05f, dustPool[i * 4 + 1]);
            Mat4.scale(mB, rad, 1f, rad);
            Mat4.multiply(mA, mT, mB);
            int rgb = theme.dust != 0 ? theme.dust : 0xCCCCCC;
            list.add(dustId, 0, mA, (Math.round(0.6f * (1f - k) * 255f) << 24) | (rgb & 0xFFFFFF), true, vm);
        }
        // T-31 Part B: spray behind the wheels on ice (white) or water (blue): alpha 0.7 to 0, radius 0.25 to 0.6 over 0.4 s
        for (int i = 0; dustId >= 0 && i < 6; i++) {
            float age = sprayPool[i * 4 + 2];
            if (age >= SPRAY_SECONDS) continue;
            float k = age / SPRAY_SECONDS, rad = 0.25f + 0.35f * k;
            Mat4.translate(mT, sprayPool[i * 4], 0.12f, sprayPool[i * 4 + 1]);
            Mat4.scale(mB, rad, 1f, rad);
            Mat4.multiply(mA, mT, mB);
            int rgb = theme.slick == com.wiggleplay.app.games.race.Theme.SLICK_WATER ? ObjectMeshes.WATER : 0xFFFFFF;
            list.add(dustId, 0, mA, (Math.round(0.7f * (1f - k) * 255f) << 24) | rgb, true, vm);
        }
        for (int i = 0; i < scenery(); i++) {
            layout.sample(scS[i], smp);
            float off = SCENERY_LANE[i] * raceLevel.laneFrac * layout.roadHalfWidth();
            float x = smp[0] - (float) Math.cos(smp[2]) * off, z = smp[1] + (float) Math.sin(smp[2]) * off;
            addTruck(i + np, x, 0f, z, smp[2], 0f, 0f, scSpin[i], scWheelYaw[i], vm);
        }
    }

    /** Player p's coins, golden star, magnet and nitro cans: only those near the truck and not behind it, in its viewport only. */
    private void addCoins(int p, int bit, float[] eye) {
        CarModel car = logic.car(p);
        float fx = (float) Math.sin(car.yaw()), fz = (float) Math.cos(car.yaw());
        float spin = time * TWO_PI * 2f;
        for (int i = 0; i < logic.slotCount(p); i++) {
            if (logic.slotGone(p, i)) continue;
            float dx = logic.slotX(p, i) - car.x(), dz = logic.slotZ(p, i) - car.z();
            if (dx * dx + dz * dz > 55f * 55f || dx * fx + dz * fz < -8f) continue;
            float y = (logic.slotHeight(p, i) > 0f ? logic.slotHeight(p, i) : 1.0f) + 0.15f * (float) Math.sin(time * 4f + i);
            Mat4.translate(mT, logic.slotX(p, i), y, logic.slotZ(p, i));
            Mat4.rotY(mR, spin);
            Mat4.multiply(mA, mT, mR);
            if (logic.slotIsStar(p, i)) {
                list.add(starId, 0, mA, STAR_COLOUR, false, bit);   // T-23 Part C: palette swatches, not one flat colour
                Mat4.scale(mB, 1.6f, 1.6f, 1.6f);
                Mat4.multiply(mC, mA, mB);
                list.add(starId, 0, mC, GLOW_COLOUR, true, bit);
            } else if (logic.slotIsMagnet(p, i)) {
                // the magnet: bigger than a coin, hovering a little higher
                Mat4.scale(mB, 1.6f, 1.6f, 1.6f);
                Mat4.multiply(mC, mA, mB);
                list.add(magnetId, 0, mC, 0, false, bit);
            } else if (logic.slotIsNitro(p, i)) {
                // T-26: a nitro can, a little bigger than a coin so it stands out among them
                Mat4.scale(mB, 1.3f, 1.3f, 1.3f);
                Mat4.multiply(mC, mA, mB);
                list.add(nitroId, 0, mC, 0, false, bit);
            } else {
                // T-24: in two players, coins farther than 15 m from this viewport's eye use the 48-triangle coin
                float ex = logic.slotX(p, i) - eye[0], ez = logic.slotZ(p, i) - eye[2];
                boolean far = np == 2 && ex * ex + ez * ez > 15f * 15f && coinLowId >= 0;
                list.add(far ? coinLowId : coinId, 0, mA, COIN_COLOUR, false, bit);
            }
        }
    }

    /** One truck: parts as {@code truck x T(part) [x rotY(front wheel)] x rotX(spin)}, plus its shadow. */
    private void addTruck(int t, float x, float y, float z, float yaw, float lean, float pitch, float spin, float frontYaw, int vm) {
        Mat4.translate(mT, x, y, z);
        Mat4.rotY(mR, yaw);
        Mat4.multiply(mA, mT, mR);
        Mat4.rotZ(mR, lean);
        Mat4.multiply(mB, mA, mR);
        Mat4.rotX(mR, -pitch);
        Mat4.multiply(mTruck, mB, mR);
        MeshData md = truckData[t];
        for (int p = 0; p < 6; p++) {
            MeshData.Part part = md.parts[partIdx[t][p]];
            Mat4.translate(mA, part.translation[0], part.translation[1], part.translation[2]);
            Mat4.multiply(mB, mTruck, mA);
            if (p == P_FL || p == P_FR) {
                Mat4.rotY(mR, frontYaw);
                Mat4.multiply(mC, mB, mR);
                System.arraycopy(mC, 0, mB, 0, 16);
            }
            if (p >= P_FL) {
                Mat4.rotX(mR, spin);
                Mat4.multiply(mC, mB, mR);
                list.add(truckId[t], partIdx[t][p], mC, 0, false, vm);
            } else {
                list.add(truckId[t], partIdx[t][p], mB, t < np && p == 1 ? TruckPaints.renderColour(pickPaint[t], pickTruck[t]) : 0, false, vm);   // T-34: paint
            }
        }
        Mat4.translate(mA, x, 0.06f, z);
        list.add(shadowId, 0, mA, SHADOW, true, vm);
    }

    private static float angleDiff(float to, float from) {
        float d = to - from;
        while (d > (float) Math.PI) d -= TWO_PI;
        while (d <= -(float) Math.PI) d += TWO_PI;
        return d;
    }

    // ---- sound ----

    private void updateSound() {
        if (env.sfx == null) return;
        boolean on = !hud.paused() && (hud.phase() == Hud.COUNTDOWN || hud.running());
        if (!on) {
            stopLoops();
            return;
        }
        CarModel car = logic.car(0);
        float sp = car.speed();
        boolean slick = logic.slick(0), sharp = Math.abs(input.steer) > 0.75f && car.speed() > 0.8f * raceLevel.baseSpeed;
        if (np == 2) {   // two trucks share one engine and one skid loop: the faster truck sets the pitch
            sp = Math.max(sp, logic.car(1).speed());
            slick |= logic.slick(1);
            sharp |= Math.abs(input2.steer) > 0.75f && logic.car(1).speed() > 0.8f * raceLevel.baseSpeed;
        }
        float norm = Math.min(1f, sp / (raceLevel.baseSpeed * 1.5f));
        env.sfx.loop("engine", 0.6f + 1.2f * norm, 0.35f);
        boolean skid = (slick || sharp) && hud.running();
        if (skid) env.sfx.loop("skid", 1f, 0.3f); else env.sfx.stopLoop("skid");
    }

    private void stopLoops() {
        if (env.sfx == null) return;
        env.sfx.stopLoop("engine");
        env.sfx.stopLoop("skid");
    }

    // ---- drawing ----

    @Override
    public void draw(Canvas c) {
        if (gl == null) drawFlatWorld(c);
        if (intro) {
            drawIntro(c);
            return;
        }
        if (hud.phase() == Hud.RULES) {
            c.drawColor(0xFFF3EBDD);
            hud.draw(c, 0, 0);
            return;
        }
        if (gl != null && gl.toggles().isOff(com.wiggleplay.app.gl.RenderToggles.HUD)) return;   // Gate 0: key 5, HUD off
        if (hud.phase() != Hud.END) {
            if (np == 2) {   // T-24: a HUD in each half and one shared minimap
                raceHud.drawHalf(c, 0);
                raceHud2.drawHalf(c, 1);
                raceHud.drawSharedMinimap(c);
                paint.setColor(RaceArt.INK);
                c.drawRect(Stage.W / 2f - 3f, 0f, Stage.W / 2f + 3f, Stage.H, paint);   // the 6 px ink divider
            } else {
                raceHud.draw(c);
            }
        }
        hud.draw(c, logic.score(0), np == 2 ? logic.score(1) : 0);
        if (np == 2 && hud.phase() == Hud.END) drawPlaces(c);
    }

    /** T-24 Part B: a trophy beside the winner's name and a medal beside the other player's, on the shared end screen. */
    private void drawPlaces(Canvas c) {
        for (int p = 0; p < 2; p++) {
            float cx = p == 0 ? 600f : 1320f, x = cx - 190f, y = 282f;
            if (logic.place(p) == 1) {
                paint.setColor(0xFFF2C14E);   // the cup
                android.graphics.Path cup = FLAT_ROAD;
                cup.rewind();
                cup.moveTo(x - 24f, y - 30f);
                cup.lineTo(x + 24f, y - 30f);
                cup.lineTo(x + 16f, y + 6f);
                cup.lineTo(x - 16f, y + 6f);
                cup.close();
                c.drawPath(cup, paint);
                c.drawRect(x - 5f, y + 6f, x + 5f, y + 18f, paint);
                c.drawRoundRect(x - 20f, y + 16f, x + 20f, y + 26f, 5f, 5f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(6f);
                c.drawCircle(x - 28f, y - 20f, 10f, paint);
                c.drawCircle(x + 28f, y - 20f, 10f, paint);
                paint.setStyle(Paint.Style.FILL);
            } else {
                paint.setColor(0xFF5BB8D8);   // the ribbon, then a silver disc
                c.drawRect(x - 14f, y - 34f, x - 2f, y - 4f, paint);
                paint.setColor(0xFFE0605A);
                c.drawRect(x + 2f, y - 34f, x + 14f, y - 4f, paint);
                paint.setColor(0xFFB8C3CC);
                c.drawCircle(x, y + 8f, 22f, paint);
                paint.setColor(0xFFE8EDF0);
                c.drawCircle(x, y + 8f, 14f, paint);
            }
        }
    }

    /** Stand-in for the 3D world when there is no GL view (screenshot tests): sky, grass and a road. */
    private void drawFlatWorld(Canvas c) {
        paint.setColor(0xFFA8DDF2);
        c.drawRect(0f, 0f, Stage.W, Stage.H, paint);
        paint.setColor(0xFF37966D);
        c.drawRect(0f, 520f, Stage.W, Stage.H, paint);
        paint.setColor(0xFF8A939B);
        android.graphics.Path p = FLAT_ROAD;
        p.rewind();
        p.moveTo(880f, 520f);
        p.lineTo(1040f, 520f);
        p.lineTo(1500f, Stage.H);
        p.lineTo(420f, Stage.H);
        p.close();
        c.drawPath(p, paint);
        paint.setColor(0xFFF2C572);
        c.drawRoundRect(860f, 790f, 1060f, 900f, 24f, 24f, paint);
    }

    private static final android.graphics.Path FLAT_ROAD = new android.graphics.Path();
    private final android.graphics.RectF introRect = new android.graphics.RectF();
    private final Paint introPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    /**
     * The speed meter line {@code steer MODE raw ANGLE° -> STEER STATE}, rebuilt twice a second while the overlay
     * shows, so Angga can photograph what the camera sees.
     */
    private void speedMeterNote(float dt) {
        if (buildNoteT > 0f) buildNoteT -= dt;
        if (env.perf == null || !env.perf.isVisible()) return;
        noteT -= dt;
        if (noteT > 0f) return;
        noteT = 0.5f;
        noteSb.setLength(0);
        noteSb.append("steer ").append(steerIn.modeName()).append("  raw ");
        float raw = steerIn.rawAngleDeg();
        if (Float.isNaN(raw)) noteSb.append("--"); else noteSb.append(Math.round(raw));
        noteSb.append("\u00B0 \u2192 ");
        int st = Math.round(steerIn.steer() * 100f);
        if (st < 0) noteSb.append('-');
        st = Math.abs(st);
        noteSb.append(st / 100).append('.').append(st % 100 < 10 ? "0" : "").append(st % 100);
        noteSb.append("  ").append(steerIn.steerState() == SteerInput.State.ACTIVE ? "ACTIVE" : "NONE");
        if (buildNoteT > 0f) noteSb.append("  build ").append(buildMs).append(" ms");
        env.perf.setNote(noteSb.toString());
    }

    /** The player's shoulders and arms as thick rounded lines in the player colour, where they are on the screen. */
    private void drawArmsOnScreen(Canvas c, PlayerPose p, int colour) {
        if (p == null || !p.present) return;
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        if (ls.s < 0.3f || rs.s < 0.3f) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (int pass = 0; pass < 2; pass++) {
            paint.setStrokeWidth(pass == 0 ? 44f : 30f);
            paint.setColor(pass == 0 ? 0xFFFFFFFF : colour);
            c.drawLine(ls.x, ls.y, rs.x, rs.y, paint);
            arm(c, ls, p.kps[Kp.L_ELBOW], p.kps[Kp.L_WRIST]);
            arm(c, rs, p.kps[Kp.R_ELBOW], p.kps[Kp.R_WRIST]);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void arm(Canvas c, Keypoint s, Keypoint e, Keypoint w) {
        if (e.s >= 0.3f) {
            c.drawLine(s.x, s.y, e.x, e.y, paint);
            if (w.s >= 0.3f) c.drawLine(e.x, e.y, w.x, w.y, paint);
        } else if (w.s >= 0.3f) {
            c.drawLine(s.x, s.y, w.x, w.y, paint);
        }
    }

    private void drawIntro(Canvas c) {
        if (!worldReady) {
            paint.setColor(0xFFF3EBDD);
            c.drawRect(0f, 0f, Stage.W, Stage.H, paint);
            paint.setColor(0x33000000);
            float cx = Stage.W / 2f, cy = 500f;
            for (int i = 0; i < 8; i++) {
                float a = (float) (i * Math.PI / 4.0);
                float on = ((loadingT * 8f) % 8f);
                float d = (i - on + 8f) % 8f;
                paint.setColor(((int) (255f * (1f - d / 8f)) << 24) | 0x3A3A3A);
                c.drawCircle(cx + (float) Math.cos(a) * 70f, cy + (float) Math.sin(a) * 70f, 16f, paint);
            }
            Theme.drawCentered(c, buildFailed ? "Could not get the track ready" : "Getting the track ready…", cx, 720f, 64f, Theme.INK);
            return;
        }
        paint.setColor(0xB0F3EBDD);
        c.drawRect(0f, 0f, Stage.W, Stage.H, paint);
        Theme.drawBanner(c, INTRO_TEXT[mode], Stage.W / 2f, 190f, 96f, 0xE6F3EBDD, Theme.INK);
        drawArmsOnScreen(c, introPose, Theme.P1);
        if (np == 2) drawArmsOnScreen(c, introPose2, Theme.P2);
        // the steering icon (plane, leaning kid or wheel), tilting with the gesture; one for each player in two players
        for (int p = 0; p < np; p++) {
            SteerInput si = p == 0 ? steerIn : steerIn2;
            android.graphics.Bitmap icon = (p == 0 ? raceHud : raceHud2).icon(si.steerState() == SteerInput.State.ACTIVE);
            if (icon == null) continue;
            float r = np == 2 ? 130f : 190f;
            c.save();
            c.translate(np == 2 ? (p == 0 ? 480f : 1440f) : Stage.W / 2f, 420f);
            c.rotate(-si.iconAngleDeg());
            introRect.set(-r, -r, r, r);
            c.drawBitmap(icon, null, introRect, introPaint);
            c.restore();
        }
        // progress dots: the calibration fills them
        float prog = Math.min(1f, introT / INTRO_SECONDS);
        paint.setColor(0x33000000);
        c.drawRoundRect(Stage.W / 2f - 200f, 700f, Stage.W / 2f + 200f, 730f, 15f, 15f, paint);
        paint.setColor(Theme.P1);
        c.drawRoundRect(Stage.W / 2f - 200f, 700f, Stage.W / 2f - 200f + 400f * prog, 730f, 15f, 15f, paint);
        Theme.drawCentered(c, "Ready…", Stage.W / 2f, 820f, 64f, Theme.INK);
    }
}
