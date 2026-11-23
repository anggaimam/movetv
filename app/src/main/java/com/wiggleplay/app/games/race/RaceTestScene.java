package com.wiggleplay.app.games.race;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;

import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.Mat4;
import com.wiggleplay.app.gl.ProcMesh;
import com.wiggleplay.app.gl.RaceGlView;
import com.wiggleplay.app.gl.RenderList;
import com.wiggleplay.app.gl.StaticBatcher;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * T-19, temporary (removed in T-22): the speed test for the 3D race. Four trucks drive the centre line of the
 * Hard (or Easy) map by themselves while the camera follows truck 1, and a small label says which settings are
 * on so a photo of the TV explains itself. Remote keys: Up/Down cycle the scenery (100% / LOW / 0%), Left/Right
 * switch between one and two viewports, OK cycles the render size, Rewind/Fast-forward pick Easy/Hard, Back goes
 * to the level screen. (Letters work too, for the emulator's test mode: W/S, A/D, Space, Q/E.)
 */
public final class RaceTestScene implements Scene {

    private static final String GAME_ID = "racetest";
    private static final String[] TRUCK_MESH = {"vehicle-truck-yellow", "vehicle-truck-red",
            "vehicle-truck-green", "vehicle-truck-purple"};
    private static final String[] PART_NAMES = {"underside", "body", "wheel-front-left", "wheel-front-right",
            "wheel-back-left", "wheel-back-right"};
    private static final int P_UNDER = 0, P_BODY = 1, P_FL = 2, P_FR = 3, P_BL = 4, P_BR = 5;
    private static final String[] DECOR_NAME = {"0%", "LOW", "100%"};

    /** Grass green of the colormap (sampled where decoration-empty draws its grass); written here on purpose. */
    private static final int GRASS = 0x37966D;
    private static final int SKY = 0xA8DDF2;
    private static final int SHADOW = 0x59000000; // alpha 0.35, black
    private static final float SPEED = 15f, SPACING = 25f, WHEEL_R = 0.3f;
    private static final long KEY_GAP_MS = 250;

    private final TileMenu.Env env;
    private final RaceGlView gl;
    private final RenderList list = new RenderList(96);

    // scratch matrices (no allocation per frame)
    private final float[] mTruck = new float[16], mT = new float[16], mR = new float[16], mA = new float[16],
            mB = new float[16], mC = new float[16];
    private final float[] smp = new float[3], smp2 = new float[3];
    private final float[][] eye = new float[2][3], target = new float[2][3];
    private final float[] camYaw = new float[2];

    private final int[] truckId = new int[4];
    private final int[][] partIdx = new int[4][6];
    private final MeshData[] truckData = new MeshData[4];
    private int groundId, shadowId;

    private final float[] truckS = new float[4], wheelSpin = new float[4], wheelYaw = new float[4];

    private TrackLayout layout;
    private boolean hard = true;
    private int decor = 2;          // 0 = 0%, 1 = LOW, 2 = 100%
    private int viewports = 1;
    private int sizeIdx;            // 0 = 1280x720, 1 = 960x540
    private float groundX, groundZ;
    private long lastKeyMs;
    private boolean perfWasVisible;
    private String label = "";
    private final StringBuilder sb = new StringBuilder(96);
    private final Paint panel = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Creates the scene.
     * @param env what the screens share
     * @param gl the 3D view under the game screen
     */
    public RaceTestScene(TileMenu.Env env, RaceGlView gl) {
        this.env = env;
        this.gl = gl;
        panel.setColor(0xCC2F3A45);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(34f);
    }

    @Override
    public boolean world3d() {
        return true;
    }

    @Override
    public void enter(SceneArgs args) {
        env.leaveMenus();
        perfWasVisible = env.perf.isVisible();
        env.perf.setVisible(true);
        hard = true;
        decor = 2;
        viewports = 1;
        sizeIdx = com.wiggleplay.app.core.GameView.quality == com.wiggleplay.app.core.GameView.LOW ? 1 : 0;
        gl.loadMeshes("track-straight", "track-corner", "track-finish", "decoration-forest", "decoration-tents",
                "decoration-empty", TRUCK_MESH[0], TRUCK_MESH[1], TRUCK_MESH[2], TRUCK_MESH[3]);
        groundId = gl.addMesh("ground", ProcMesh.ground(400f));
        shadowId = gl.addMesh("shadow", ProcMesh.disc(1.2f));
        for (int t = 0; t < 4; t++) {
            truckId[t] = gl.meshId(TRUCK_MESH[t]);
            truckData[t] = gl.meshData(truckId[t]);
            for (int p = 0; p < 6; p++) partIdx[t][p] = truckData[t].partIndex(PART_NAMES[p]);
        }
        gl.setSky(SKY);
        gl.setFog(25f, 55f);
        gl.setViewports(1);
        buildWorld();
        refreshLabel();
    }

    @Override
    public void exit() {
        gl.release();
        env.perf.setVisible(perfWasVisible);
        SpriteCache.shared().removePrefix("race_");
        SpriteCache.shared().removePrefix("tile_");
        SpriteCache.shared().removePrefix("tileicon_");
    }

    /** Builds the track, scenery and trucks for the current map and decoration setting. */
    private void buildWorld() {
        layout = TrackMaps.forLevel(hard ? 2 : 0);
        groundX = (layout.width() - 1) * TrackLayout.CELL / 2f;
        groundZ = (layout.height() - 1) * TrackLayout.CELL / 2f;
        rebuildStatic();
        for (int t = 0; t < 4; t++) {
            truckS[t] = t * SPACING;
            wheelSpin[t] = 0f;
            wheelYaw[t] = 0f;
        }
        for (int v = 0; v < 2; v++) snapCamera(v);
    }

    private void rebuildStatic() {
        List<StaticBatcher.Placement> all = new ArrayList<>(layout.tilePlacements());
        if (decor == 1) all.addAll(layout.decor(1234L, true));
        else if (decor == 2) all.addAll(layout.decor(1234L, false));
        gl.setStatic(all);
    }

    private void refreshLabel() {
        sb.setLength(0);
        sb.append("Map ").append(hard ? "Hard" : "Easy").append("   Decor ").append(DECOR_NAME[decor])
                .append("   Viewports ").append(viewports).append("   Size ").append(sizeIdx == 0 ? "1280x720" : "960x540");
        label = sb.toString();
    }

    @Override
    public void update(float dt, Frame f) {
        handleKeys(f);
        for (int t = 0; t < 4; t++) {
            truckS[t] += SPEED * dt;
            if (truckS[t] > layout.lapLength()) truckS[t] -= layout.lapLength();
            wheelSpin[t] += SPEED * dt / WHEEL_R;
            if (wheelSpin[t] > 6.2831855f) wheelSpin[t] -= 6.2831855f;
            // the front wheels turn with the curve: yaw change over the next unit of road, scaled to a wheel angle
            layout.sample(truckS[t], smp);
            layout.sample(truckS[t] + 1f, smp2);
            float rate = angleDiff(smp2[2], smp[2]) * SPEED;
            float want = Math.max(-0.6f, Math.min(0.6f, rate * 0.2f));
            wheelYaw[t] += (want - wheelYaw[t]) * Math.min(1f, dt * 10f);
        }
        followCamera(0, 0, dt);
        followCamera(1, 1, dt);
        gl.setViewports(viewports);
        gl.setCamera(0, eye[0], target[0], 60f);
        gl.setCamera(1, eye[1], target[1], 60f);
        buildList();
        gl.submit(list);
    }

    private void handleKeys(Frame f) {
        if (f.input.pressed(KeyEvent.KEYCODE_BACK)) {
            env.sfx("back");
            env.go("levelselect", SceneArgs.of("gameId", GAME_ID));
            return;
        }
        if (f.nowMs - lastKeyMs < KEY_GAP_MS) return;
        boolean up = f.input.pressed(KeyEvent.KEYCODE_DPAD_UP) || f.input.pressed(KeyEvent.KEYCODE_W);
        boolean down = f.input.pressed(KeyEvent.KEYCODE_DPAD_DOWN) || f.input.pressed(KeyEvent.KEYCODE_S);
        boolean lr = f.input.pressed(KeyEvent.KEYCODE_DPAD_LEFT) || f.input.pressed(KeyEvent.KEYCODE_DPAD_RIGHT)
                || f.input.pressed(KeyEvent.KEYCODE_A) || f.input.pressed(KeyEvent.KEYCODE_D);
        boolean ok = f.input.pressed(KeyEvent.KEYCODE_DPAD_CENTER) || f.input.pressed(KeyEvent.KEYCODE_ENTER)
                || f.input.pressed(KeyEvent.KEYCODE_SPACE);
        boolean easy = f.input.pressed(KeyEvent.KEYCODE_MEDIA_REWIND) || f.input.pressed(KeyEvent.KEYCODE_Q);
        boolean hardKey = f.input.pressed(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) || f.input.pressed(KeyEvent.KEYCODE_E);
        if (!(up || down || lr || ok || easy || hardKey)) return;
        lastKeyMs = f.nowMs;
        if (up) {
            decor = (decor + 1) % 3;
            rebuildStatic();
        } else if (down) {
            decor = (decor + 2) % 3;
            rebuildStatic();
        } else if (lr) {
            viewports = viewports == 1 ? 2 : 1;
        } else if (ok) {
            sizeIdx = 1 - sizeIdx;
            if (sizeIdx == 0) gl.setRenderSize(RaceGlView.HIGH_W, RaceGlView.HIGH_H);
            else gl.setRenderSize(RaceGlView.LOW_W, RaceGlView.LOW_H);
        } else if ((easy && hard) || (hardKey && !hard)) {
            hard = !hard;
            buildWorld();
        }
        refreshLabel();
    }

    // ---- camera: 6.5 behind, 3.2 up; looks 4 ahead at height 0.8; eased ----

    private void snapCamera(int v) {
        layout.sample(truckS[v], smp);
        camYaw[v] = smp[2];
        placeCamera(v, smp[0], smp[1], 1f);
    }

    private void followCamera(int v, int truck, float dt) {
        layout.sample(truckS[truck], smp);
        camYaw[v] += angleDiff(smp[2], camYaw[v]) * Math.min(1f, dt * 4f);
        placeCamera(v, smp[0], smp[1], Math.min(1f, dt * 5f));
    }

    private void placeCamera(int v, float x, float z, float k) {
        float fx = (float) Math.sin(camYaw[v]), fz = (float) Math.cos(camYaw[v]);
        float[] e = eye[v], tg = target[v];
        e[0] += (x - fx * 6.5f - e[0]) * k;
        e[1] += (3.2f - e[1]) * k;
        e[2] += (z - fz * 6.5f - e[2]) * k;
        tg[0] += (x + fx * 4f - tg[0]) * k;
        tg[1] += (0.8f - tg[1]) * k;
        tg[2] += (z + fz * 4f - tg[2]) * k;
    }

    private static float angleDiff(float to, float from) {
        float d = to - from;
        while (d > (float) Math.PI) d -= 6.2831855f;
        while (d <= -(float) Math.PI) d += 6.2831855f;
        return d;
    }

    // ---- the dynamic things: ground, trucks, shadows ----

    private void buildList() {
        list.begin();
        Mat4.translate(mT, groundX, 0f, groundZ);
        list.add(groundId, 0, mT, GRASS, true, 3);
        for (int t = 0; t < 4; t++) {
            layout.sample(truckS[t], smp);
            Mat4.translate(mT, smp[0], 0f, smp[1]);
            Mat4.rotY(mR, smp[2]);
            Mat4.multiply(mTruck, mT, mR);
            MeshData md = truckData[t];
            for (int p = 0; p < 6; p++) {
                MeshData.Part part = md.parts[partIdx[t][p]];
                Mat4.translate(mA, part.translation[0], part.translation[1], part.translation[2]);
                Mat4.multiply(mB, mTruck, mA);
                if (p == P_FL || p == P_FR) {
                    Mat4.rotY(mR, wheelYaw[t]);
                    Mat4.multiply(mC, mB, mR);
                    System.arraycopy(mC, 0, mB, 0, 16);
                }
                if (p >= P_FL) {
                    Mat4.rotX(mR, wheelSpin[t]);
                    Mat4.multiply(mC, mB, mR);
                    list.add(truckId[t], partIdx[t][p], mC, 0, false, 3);
                } else {
                    list.add(truckId[t], partIdx[t][p], mB, 0, false, 3);
                }
            }
            Mat4.translate(mA, smp[0], 0.06f, smp[1]);
            list.add(shadowId, 0, mA, SHADOW, true, 3);
        }
    }

    @Override
    public void draw(Canvas c) {
        // the 3D world is under this transparent screen; only the label is drawn here (the speed overlay is on top)
        float y = 1080f - 120f;
        c.drawRect(0f, y, 1920f, 1080f, panel);
        c.drawText(label, 24f, y + 46f, text);
        c.drawText("Up/Down decor   Left/Right 1-2 views   OK size   Rew/FF map   Back = levels", 24f, y + 98f, text);
    }

    // ---- tile picture for the game menu: a little yellow truck on a green hill ----

    /**
     * Paints the menu tile of the race test: a small yellow truck on grass.
     * @param c canvas on the tile bitmap
     * @param w tile width
     * @param h tile height
     */
    public static void cover(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF r = new RectF();
        p.setColor(0xFFA8DDF2);
        c.drawRect(0f, 0f, w, h, p);
        p.setColor(0xFF6FA06B);
        c.drawRect(0f, h * 0.68f, w, h, p);
        p.setColor(0xFF3A3A40);
        c.drawRect(0f, h * 0.74f, w, h * 0.92f, p);
        p.setColor(0xFFF2C572);
        r.set(w * 0.22f, h * 0.42f, w * 0.78f, h * 0.74f);
        c.drawRoundRect(r, w * 0.04f, w * 0.04f, p);
        r.set(w * 0.34f, h * 0.26f, w * 0.64f, h * 0.46f);
        c.drawRoundRect(r, w * 0.04f, w * 0.04f, p);
        p.setColor(0xFFA8DDF2);
        r.set(w * 0.38f, h * 0.30f, w * 0.60f, h * 0.43f);
        c.drawRoundRect(r, w * 0.02f, w * 0.02f, p);
        p.setColor(0xFF2F3A45);
        c.drawCircle(w * 0.34f, h * 0.76f, w * 0.07f, p);
        c.drawCircle(w * 0.66f, h * 0.76f, w * 0.07f, p);
        p.setColor(0xFFF3EBDD);
        c.drawCircle(w * 0.34f, h * 0.76f, w * 0.03f, p);
        c.drawCircle(w * 0.66f, h * 0.76f, w * 0.03f, p);
    }
}
