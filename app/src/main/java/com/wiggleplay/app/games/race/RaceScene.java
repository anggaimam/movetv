package com.wiggleplay.app.games.race;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
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
    private static final int GRASS = 0x37966D, SKY = 0xA8DDF2;
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
    private final JumpDetect jump = new JumpDetect();
    private final AirplaneArms arms = new AirplaneArms();
    private final BodyLean lean = new BodyLean();
    private final SteeringWheel wheel = new SteeringWheel();
    private final SteerInput[] steerModes = {arms, lean, wheel};
    private SteerInput steerIn = wheel;
    private int mode = RaceArt.MODE_WHEEL;
    private float noteT;
    private final StringBuilder noteSb = new StringBuilder(64);
    private static final String[] INTRO_TEXT = RaceHud.HINT;
    private final RenderList list = new RenderList(192);

    // scratch
    private final float[] mTruck = new float[16], mT = new float[16], mR = new float[16], mA = new float[16],
            mB = new float[16], mC = new float[16];
    private final float[] smp = new float[3], smp2 = new float[3];

    private final int[] truckId = new int[4];
    private final int[][] partIdx = new int[4][6];
    private final MeshData[] truckData = new MeshData[4];
    private int groundId, shadowId, coinId, starId, nitroId, magnetId;

    // scenery trucks
    private final float[] scS = new float[3], scSpin = new float[3], scWheelYaw = new float[3];

    private RaceLogic logic;
    private TrackSpline layout;
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
        long built0 = SystemClock.uptimeMillis();
        layout = TrackSpline.build(TrackChoice.def(), TrackChoice.reversed(), raceLevel);
        if (gl != null) {
            gl.loadMeshes(TRUCK_MESH[0], TRUCK_MESH[1], TRUCK_MESH[2], TRUCK_MESH[3]);
            groundId = gl.addMesh("ground", ProcMesh.groundGrid(GROUND_SIZE, GROUND_CELLS));
            shadowId = gl.addMesh("shadow", ProcMesh.disc(1.2f));
            coinId = gl.addMesh("coin", ProcMesh.coinReeded());
            starId = gl.addMesh("star", ProcMesh.starBevelled(0.9f, 0.4f, 0.2f));
            nitroId = gl.addMesh("nitro", ProcMesh.nitroCan());
            magnetId = gl.addMesh("magnet", ProcMesh.magnet());
            for (int t = 0; t < 4; t++) {
                truckId[t] = gl.meshId(TRUCK_MESH[t]);
                truckData[t] = gl.meshData(truckId[t]);
                for (int p = 0; p < 6; p++) partIdx[t][p] = truckData[t].partIndex(PART_NAMES[p]);
            }
            gl.setSky(SKY);
            gl.setFog(25f, 55f);
            gl.setViewports(1);
            // T-28 Part D: the road is built once from the spline, coloured through swatches painted into the colormap
            PaletteSwatches pal = new PaletteSwatches();
            List<MeshData> pieces = TrackMeshBuilder.build(layout, RoadStyle.MEADOW, pal);
            gl.setColormapSwatches(pal);
            List<StaticBatcher.Placement> all = new ArrayList<>(pieces.size());
            float[] ident = new float[16];
            Mat4.identity(ident);
            for (int i = 0; i < pieces.size(); i++) {
                String name = "trk_road_" + i;
                gl.addMesh(name, pieces.get(i));
                MeshData.Part part = pieces.get(i).parts[0];
                float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
                for (int v = 0; v < part.vertexCount; v++) {
                    float x = part.vertices[v * 8], z = part.vertices[v * 8 + 2];
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
                all.add(new StaticBatcher.Placement(name, ident, (int) Math.floor((minX + maxX) / 2f / CELL),
                        (int) Math.floor((minZ + maxZ) / 2f / CELL)));
            }
            gl.setStatic(all, CHUNK_CELLS);
        }
        buildMs = (int) (SystemClock.uptimeMillis() - built0);
        buildNoteT = BUILD_NOTE_SECONDS;
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
        raceHud.setMode(mode);
        rules.setMode(mode);
        hud.setRules(rules);
        jump.reset();
        for (SteerInput si : steerModes) si.reset();
        newLogic();
        raceHud.reset();
        boolean showRules = !RULES_SEEN[level];
        RULES_SEEN[level] = true;
        intro = false;
        introAfterRules = false;
        if (showRules) {
            introAfterRules = true;
            hud.start(1, true);
        } else {
            startIntro();
        }
    }

    @Override
    public void exit() {
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
        logic = new RaceLogic(layout, raceLevel, 1, System.nanoTime());
        CarModel car = logic.car(0);
        camera.snap(car.x(), car.z(), car.yaw());
        for (int i = 0; i < 3; i++) {
            scS[i] = layout.lapLength() * (i + 1) / 4f;
            scSpin[i] = 0f;
            scWheelYaw[i] = 0f;
        }
        wheelYawPlayer = 0f;
        leftUntil = 0;
        rightUntil = 0;
        noneT = 0f;
        finalT = 99f;
    }

    private void startIntro() {
        intro = true;
        introT = 0f;
        raceHud.reset();
    }

    private void beginRound() {
        newLogic();
        raceHud.reset();
        steerIn.reset();
        hud.start(1, false);
    }

    // ---- per frame ----

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        PlayerPose pose = f.pose.players[0];
        camBitmap = f.camera;
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) && intro) {
            env.sfx("back");
            env.go("levelselect", SceneArgs.of("gameId", GAME_ID));
            return;
        }
        if (intro) {
            updateIntro(dt, f, pose);
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
        speedMeterNote(dt);
        jump.update(pose, f.nowMs);
        boolean jumpEvent = jump.consumeJump();
        if (hud.phase() != Hud.END) {
            Controls in = readInput(f, pose);
            jumpEvent |= in.jump;
            boolean racing = hud.running();
            CarModel car = logic.car(0);
            if (racing) {
                logic.setControls(0, in.steer, in.brake);
                if (jumpEvent) {
                    boolean was = logic.boosting(0);
                    logic.onJump(0);
                    if (!was && logic.boosting(0)) env.sfx("whoosh");
                }
                logic.update(dt);
                raceEvents(car);
                if (logic.over() || hud.phase() == Hud.ENDING) finishRace();
            }
            updateHud(dt, racing, in);
        } else {
            // behind the end screen the truck keeps driving by itself
            logic.setControls(0, autoSteer(logic.car(0)), false);
            logic.update(dt);
            raceHud.update(dt, false);
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

    private Controls readInput(Frame f, PlayerPose pose) {
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

    private void raceEvents(CarModel car) {
        if (logic.consumeNitro(0) > 0) env.sfx("star", 1.5f);   // T-26: a nitro can
        if (logic.consumeMagnet(0) > 0) env.sfx("star", 1.2f);   // the magnet
        int coins = logic.consumeCoins(0);
        if (coins > 0) {
            env.sfx("coin", 1f + 0.06f * Math.min(coins, 4));
            raceHud.popup(1);
        }
        if (logic.consumeStar(0)) {
            env.sfx("star");
            raceHud.popup(5);
        }
        if (logic.consumeLap(0)) {
            if (logic.lapsDone(0) < logic.lapsTotal()) env.sfx("go");
            if (logic.currentLap(0) == logic.lapsTotal() && !logic.finished(0)) finalT = 0f;
        }
        if (car.consumeHitWall() && env.sfx != null) {
            env.sfx.play("impact", 1f, Math.max(0.25f, Math.min(1f, car.speed() / (raceLevel.baseSpeed * 1.2f))));
        }
    }

    private void finishRace() {
        stopLoops();
        int stars = logic.stars(0);
        if (env.profile != null) env.profile.recordStars(GAME_ID, level, stars);
        hud.finish(stars, 0, logic.score(0), 0);
    }

    private void updateHud(float dt, boolean racing, Controls in) {
        CarModel car = logic.car(0);
        raceHud.lap = logic.currentLap(0);
        raceHud.lapTotal = logic.lapsTotal();
        raceHud.meter = logic.boostMeter(0);
        raceHud.boosting = logic.boosting(0);
        raceHud.boostLeft = logic.boostLeft(0);
        raceHud.magnetK = logic.magnetLeft(0) / RaceLogic.MAGNET_SECONDS;
        raceHud.braking = racing && in.brake && !logic.boosting(0);
        boolean active = steerIn.steerState() == SteerInput.State.ACTIVE;
        raceHud.iconDeg = steerIn.iconAngleDeg();
        raceHud.steerActive = active;
        if (racing) noneT = active ? 0f : noneT + dt;
        raceHud.noneSeconds = noneT;
        if (finalT < 90f && racing) finalT += dt;
        raceHud.finalLapSeconds = finalT;
        raceHud.playerS = car.s();
        raceHud.sceneryCount = 3;
        System.arraycopy(scS, 0, raceHud.sceneryS, 0, 3);
        raceHud.camera = camBitmap;
        raceHud.update(dt, racing);
    }

    private void updateIntro(float dt, Frame f, PlayerPose pose) {
        introT += dt;
        introPose = pose;
        steerIn.update(pose, f.nowMs, dt);
        speedMeterNote(dt);
        jump.update(pose, f.nowMs);
        jump.consumeJump();
        boolean skip = f.input.pressed(KeyEvent.KEYCODE_DPAD_CENTER) || f.input.pressed(KeyEvent.KEYCODE_ENTER)
                || f.input.pressed(KeyEvent.KEYCODE_SPACE);
        moveScenery(dt);
        followCamera(dt);
        raceHud.iconDeg = steerIn.iconAngleDeg();
        raceHud.steerActive = steerIn.steerState() == SteerInput.State.ACTIVE;
        submitWorld();
        if (introT >= INTRO_SECONDS || skip) {
            intro = false;
            beginRound();
        }
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
        for (int i = 0; i < 3; i++) {
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
        CarModel car = logic.car(0);
        camera.update(dt, car.x(), car.z(), car.yaw(), logic.boosting(0));
    }

    private void submitWorld() {
        if (gl == null) return;
        buildList();
        gl.setViewports(1);
        gl.setCamera(0, camera.eye(), camera.target(), camera.fov());
        gl.submit(list);
    }

    private void buildList() {
        list.begin();
        // T-23: the ground grid follows the camera, snapped to whole cells so its vertices don't swim
        float[] eye = camera.eye();
        float gx = GROUND_STEP * (float) Math.floor(eye[0] / GROUND_STEP + 0.5f);
        float gz = GROUND_STEP * (float) Math.floor(eye[2] / GROUND_STEP + 0.5f);
        Mat4.translate(mT, gx, 0f, gz);
        list.add(groundId, 0, mT, GRASS, true, 1);
        // coins and the golden star (only those near the truck and not behind the camera)
        CarModel car = logic.car(0);
        float fx = (float) Math.sin(car.yaw()), fz = (float) Math.cos(car.yaw());
        float spin = time * TWO_PI * 2f;
        for (int i = 0; i < logic.slotCount(0); i++) {
            if (logic.slotGone(0, i)) continue;
            float dx = logic.slotX(0, i) - car.x(), dz = logic.slotZ(0, i) - car.z();
            if (dx * dx + dz * dz > 55f * 55f || dx * fx + dz * fz < -8f) continue;
            float y = 1.0f + 0.15f * (float) Math.sin(time * 4f + i);
            Mat4.translate(mT, logic.slotX(0, i), y, logic.slotZ(0, i));
            Mat4.rotY(mR, spin);
            Mat4.multiply(mA, mT, mR);
            if (logic.slotIsStar(0, i)) {
                list.add(starId, 0, mA, STAR_COLOUR, false, 1);   // T-23 Part C: palette swatches, not one flat colour
                Mat4.scale(mB, 1.6f, 1.6f, 1.6f);
                Mat4.multiply(mC, mA, mB);
                list.add(starId, 0, mC, GLOW_COLOUR, true, 1);
            } else if (logic.slotIsMagnet(0, i)) {
                // the magnet: bigger than a coin, hovering a little higher
                Mat4.scale(mB, 1.6f, 1.6f, 1.6f);
                Mat4.multiply(mC, mA, mB);
                list.add(magnetId, 0, mC, 0, false, 1);
            } else if (logic.slotIsNitro(0, i)) {
                // T-26: a nitro can, a little bigger than a coin so it stands out among them
                Mat4.scale(mB, 1.3f, 1.3f, 1.3f);
                Mat4.multiply(mC, mA, mB);
                list.add(nitroId, 0, mC, 0, false, 1);
            } else {
                list.add(coinId, 0, mA, COIN_COLOUR, false, 1);   // T-23 Part C: one draw call, swatches from the palette
            }
        }
        // the player's truck, then the three friendly scenery trucks
        addTruck(0, car.x(), car.z(), car.yaw(), car.bodyLean(), car.bodyPitch(), car.wheelSpin(), car.frontWheelYaw());
        for (int i = 0; i < 3; i++) {
            layout.sample(scS[i], smp);
            float off = SCENERY_LANE[i] * raceLevel.laneFrac * layout.roadHalfWidth();
            float x = smp[0] - (float) Math.cos(smp[2]) * off, z = smp[1] + (float) Math.sin(smp[2]) * off;
            addTruck(i + 1, x, z, smp[2], 0f, 0f, scSpin[i], scWheelYaw[i]);
        }
    }

    /** One truck: parts as {@code truck x T(part) [x rotY(front wheel)] x rotX(spin)}, plus its shadow. */
    private void addTruck(int t, float x, float z, float yaw, float lean, float pitch, float spin, float frontYaw) {
        Mat4.translate(mT, x, 0f, z);
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
                list.add(truckId[t], partIdx[t][p], mC, 0, false, 1);
            } else {
                list.add(truckId[t], partIdx[t][p], mB, 0, false, 1);
            }
        }
        Mat4.translate(mA, x, 0.06f, z);
        list.add(shadowId, 0, mA, SHADOW, true, 1);
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
        float norm = Math.min(1f, car.speed() / (raceLevel.baseSpeed * 1.5f));
        env.sfx.loop("engine", 0.6f + 1.2f * norm, 0.35f);
        boolean skid = Math.abs(input.steer) > 0.75f && car.speed() > 0.8f * raceLevel.baseSpeed && hud.running();
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
        if (hud.phase() != Hud.END) raceHud.draw(c);
        hud.draw(c, logic.score(0), 0);
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
    private void drawArmsOnScreen(Canvas c) {
        PlayerPose p = introPose;
        if (p == null || !p.present) return;
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        if (ls.s < 0.3f || rs.s < 0.3f) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (int pass = 0; pass < 2; pass++) {
            paint.setStrokeWidth(pass == 0 ? 44f : 30f);
            paint.setColor(pass == 0 ? 0xFFFFFFFF : Theme.P1);
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
        paint.setColor(0xB0F3EBDD);
        c.drawRect(0f, 0f, Stage.W, Stage.H, paint);
        Theme.drawBanner(c, INTRO_TEXT[mode], Stage.W / 2f, 190f, 96f, 0xE6F3EBDD, Theme.INK);
        drawArmsOnScreen(c);
        // the steering icon (plane, leaning kid or wheel), tilting with the gesture
        boolean active = steerIn.steerState() == SteerInput.State.ACTIVE;
        android.graphics.Bitmap icon = raceHud.icon(active);
        if (icon != null) {
            c.save();
            c.translate(Stage.W / 2f, 420f);
            c.rotate(-steerIn.iconAngleDeg());
            introRect.set(-190f, -190f, 190f, 190f);
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
