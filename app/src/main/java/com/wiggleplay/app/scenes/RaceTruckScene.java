package com.wiggleplay.app.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.KeyEvent;

import com.wiggleplay.app.core.ProfileLogic;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.games.race.RaceArt;
import com.wiggleplay.app.games.race.TruckPaints;
import com.wiggleplay.app.games.race.TruckSwatchArt;
import com.wiggleplay.app.gl.Mat4;
import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.ProcMesh;
import com.wiggleplay.app.gl.RaceGlView;
import com.wiggleplay.app.gl.RenderList;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Stage;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Pick Your Truck (T-34): the last screen before a Turbo Trucks race. The real in-game truck stands on a lit round platform
 * in a dark garage, turning slowly with its wheels rolling. Arrows at the sides cycle the four trucks (the old one slides
 * out, the new one slides in), the paint can cycles preset paint jobs for the body and accessories (never the wheels),
 * and the green Go button starts the race. The Steering choice (Wheel or Lean) lives here too. With two players the
 * screen splits in two: each half has its own arrows, paint can and Go button; the race starts when both have pressed Go,
 * or after 20 seconds.
 *
 * <p>The truck is drawn by the 3D view under this screen; the garage is drawn here on the canvas with holes where the
 * trucks show through. Without a 3D view (the screenshot rig) the holes are filled with a flat picture of the truck.
 */
public final class RaceTruckScene implements Scene {

    private static final int WALL = 0xFF262B33, FLOOR = 0xFF1B1F25, FLOOR_RGB = 0x1B1F25;
    private static final float SLIDE_SECONDS = 0.5f, AUTO_START_SECONDS = 20f;
    private static final String[] PART_NAMES = {"underside", "body", "wheel-front-left", "wheel-front-right",
            "wheel-back-left", "wheel-back-right"};
    private static final String PICS = "truckpick_";

    private final TileMenu.Env env;
    private final RaceGlView gl;
    private final TileMenu menu;
    private Bitmap camera;
    private float time, waited;
    private int level = 1, np = 1;
    private int steerMode = RaceArt.MODE_WHEEL;
    private TileMenu.Tile steerTile;
    private final TileMenu.Tile[] paintTile = new TileMenu.Tile[2];

    private final int[] truck = new int[2], paint = new int[2], oldTruck = new int[2], oldPaint = new int[2];
    private final float[] slide = {1f, 1f};       // 0..1; 1 = settled
    private final float[] slideDir = new float[2];
    private final boolean[] ready = new boolean[2];
    private boolean leaving;

    // 3D
    private final RenderList list = new RenderList(200);
    private final int[] meshId = new int[4];
    private final MeshData[] meshData = new MeshData[4];
    private final int[][] partIdx = new int[4][6];
    private int ringId = -1, padId = -1, dotId = -1, shadowId = -1;
    private final float[] mT = new float[16], mR = new float[16], mA = new float[16], mB = new float[16], mC = new float[16],
            mBase = new float[16];
    private final float[] eye = {0f, 2.0f, 6.6f}, target = {0f, 1.75f, 0f};

    private final Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /**
     * Creates the screen.
     * @param env what the menu screens share
     * @param gl the 3D view, or null in tests
     */
    public RaceTruckScene(TileMenu.Env env, RaceGlView gl) {
        this.env = env;
        this.gl = gl;
        this.menu = new TileMenu(env);
    }

    @Override
    public boolean world3d() {
        return gl != null;
    }

    @Override
    public void enter(SceneArgs args) {
        env.menuMusic();
        level = args.getInt("level", 1);
        np = args.getInt("players", 1) == 2 ? 2 : 1;
        time = 0f;
        waited = 0f;
        leaving = false;
        int own = env.profile != null ? ProfileLogic.raceTruckIndex(env.profile.raceTruck()) : 0;
        truck[0] = own;
        paint[0] = env.profile != null ? env.profile.racePaint() : 0;
        truck[1] = (own + 1) % 4;
        paint[1] = 0;
        for (int p = 0; p < 2; p++) {
            oldTruck[p] = truck[p];
            oldPaint[p] = paint[p];
            slide[p] = 1f;
            ready[p] = false;
        }
        steerMode = ProfileLogic.STEER_LEAN.equals(env.profile != null ? env.profile.raceSteer() : ProfileLogic.DEFAULT_RACE_STEER)
                ? RaceArt.MODE_LEAN : RaceArt.MODE_WHEEL;

        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(RoundButton.back());
        tiles.add(RoundButton.settings());
        steerTile = new TileMenu.Tile("steer", "Steering", null, Theme.ORANGE, null, 1290f, 20f, 300f, 300f);
        tiles.add(steerTile);
        for (int p = 0; p < np; p++) {
            float x0 = np == 2 ? p * 960f : 0f;
            float pw = np == 2 ? 960f : 1920f;
            float mid = x0 + pw / 2f;
            tiles.add(RoundButton.arrow("prev" + p, "Earlier truck", x0 + (np == 2 ? 110f : 120f), 800f));
            tiles.add(RoundButton.arrow("next" + p, "Next truck", x0 + pw - (np == 2 ? 110f : 120f), 800f));
            paintTile[p] = new TileMenu.Tile("paint" + p, "Paint", null, Theme.INK, null, mid - 250f, 330f, 230f, 210f);
            tiles.add(paintTile[p]);
            TileMenu.Tile go = new TileMenu.Tile("go" + p, "Go", null, Theme.SAGE, Theme::paintDone, mid + 20f, 320f, 300f, 230f);
            tiles.add(go);
        }
        menu.setTiles(tiles, "go0");
        showSteerIcon();
        for (int p = 0; p < np; p++) showPaintIcon(p);
        env.say("Pick your truck");

        if (gl != null) {
            gl.loadMeshes(TruckPaints.MESH[0], TruckPaints.MESH[1], TruckPaints.MESH[2], TruckPaints.MESH[3]);
            for (int m = 0; m < 4; m++) {
                meshId[m] = gl.meshId(TruckPaints.MESH[m]);
                meshData[m] = gl.meshData(meshId[m]);
                for (int k = 0; k < 6; k++) partIdx[m][k] = meshData[m].partIndex(PART_NAMES[k]);
            }
            ringId = gl.addMesh("padRing", ProcMesh.disc(2.35f));
            padId = gl.addMesh("pad", ProcMesh.disc(2.2f));
            dotId = gl.addMesh("padDot", ProcMesh.disc(0.14f, 10));
            shadowId = gl.addMesh("padShadow", ProcMesh.disc(1.5f));
            gl.toggles().reset();
            gl.setSky(FLOOR_RGB);
            gl.setFogColor(FLOOR_RGB);
            gl.setFog(40f, 90f);
            gl.setSunDir(0.35f, 0.85f, 0.4f);
            gl.setViewports(np);
            gl.setChunkCullDistance(0f);
        }
    }

    @Override
    public void exit() {
        SpriteCache.shared().removePrefix(PICS);
        if (gl != null) gl.release();
    }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        waited += dt;
        camera = f.camera;
        for (int p = 0; p < 2; p++) if (slide[p] < 1f) slide[p] = Math.min(1f, slide[p] + dt / SLIDE_SECONDS);
        String sel = menu.update(dt, f);
        if (leaving) return;
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) || "back".equals(sel)) {
            env.sfx("back");
            env.go("racetrack", SceneArgs.of("gameId", "race", "level", level, "players", np));
            return;
        }
        if ("settings".equals(sel)) {
            env.sfx("select");
            if (env.settings != null) env.settings.open();
            return;
        }
        if ("steer".equals(sel)) {
            steerMode = steerMode == RaceArt.MODE_WHEEL ? RaceArt.MODE_LEAN : RaceArt.MODE_WHEEL;
            if (env.profile != null) env.profile.setRaceSteer(steerMode == RaceArt.MODE_LEAN ? ProfileLogic.STEER_LEAN : ProfileLogic.STEER_WHEEL);
            env.sfx("select");
            showSteerIcon();
        } else if (sel != null) {
            for (int p = 0; p < np; p++) {
                if (("go" + p).equals(sel)) {
                    ready[p] = !ready[p];
                    env.sfx("select");
                } else if (!ready[p] && ("prev" + p).equals(sel)) {
                    cycleTruck(p, -1);
                } else if (!ready[p] && ("next" + p).equals(sel)) {
                    cycleTruck(p, 1);
                } else if (!ready[p] && ("paint" + p).equals(sel)) {
                    paint[p] = (paint[p] + 1) % TruckPaints.RGB.length;
                    showPaintIcon(p);
                    env.sfx("select");
                }
            }
        }
        boolean all = ready[0] && (np == 1 || ready[1]);
        if (all || (np == 2 && waited >= AUTO_START_SECONDS)) start();
    }

    private void cycleTruck(int p, int dir) {
        if (slide[p] < 0.6f) return;   // let the last slide finish a little before the next
        oldTruck[p] = truck[p];
        oldPaint[p] = paint[p];
        truck[p] = Math.floorMod(truck[p] + dir, 4);
        slide[p] = 0f;
        slideDir[p] = dir;
        env.sfx("select");
    }

    private void start() {
        leaving = true;
        if (env.profile != null) {
            env.profile.setRaceTruck(ProfileLogic.RACE_TRUCKS[truck[0]]);
            env.profile.setRacePaint(paint[0]);
        }
        String steer = env.profile != null ? env.profile.raceSteer() : ProfileLogic.DEFAULT_RACE_STEER;
        env.go("race", SceneArgs.of("gameId", "race", "level", level, "players", np, "steer", steer,
                "truck0", truck[0], "paint0", paint[0], "truck1", truck[1], "paint1", paint[1]));
    }

    private void showSteerIcon() {
        final int mode = steerMode;
        steerTile.setIcon(mode == RaceArt.MODE_LEAN ? "lean" : "wheel", (c, bw, bh) -> RaceArt.paintSteerButton(c, bw, bh, mode));
    }

    /** The white-outline paint can on a dim round plate, with a blob of the current paint on it. */
    private void showPaintIcon(final int p) {
        final int rgb = TruckPaints.RGB[paint[p]];
        Bitmap b = SpriteCache.shared().get(PICS + "can_" + paint[p], 230, 210, (c, w, h) -> paintCan(c, w, h, rgb));
        paintTile[p].setCustom(b);
    }

    private static void paintCan(Canvas c, int w, int h, int rgb) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x80000000);
        c.drawRoundRect(new RectF(4f, 4f, w - 4f, h - 4f), 52f, 52f, p);
        float cx = w / 2f;
        RectF body = new RectF(cx - 48f, 70f, cx + 48f, 160f);
        if (rgb != 0) {   // the paint inside, showing at the front of the can
            p.setColor(0xFF000000 | rgb);
            c.drawRoundRect(new RectF(body.left + 8f, 100f, body.right - 8f, 150f), 8f, 8f, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFFFFFFFF);
        c.drawRoundRect(body, 12f, 12f, p);
        c.drawOval(new RectF(cx - 56f, 52f, cx + 56f, 86f), p);       // the lid
        Path handle = new Path();
        handle.moveTo(cx - 52f, 66f);
        handle.quadTo(cx, 6f, cx + 52f, 66f);
        c.drawPath(handle, p);
        c.drawLine(body.left, 118f, body.right, 118f, p);              // the label band
    }

    // ---- drawing ----

    private float windowLeft(int p) { return np == 2 ? p * 960f + 130f : 300f; }

    private float windowRight(int p) { return np == 2 ? p * 960f + 830f : 1620f; }

    @Override
    public void draw(Canvas c) {
        drawGarage(c);
        if (np == 1) {
            Theme.drawBanner(c, "Pick Your Truck", 700f, 110f, 72f, 0xE6F3EBDD, Theme.INK);
            Theme.drawCentered(c, name(0), 700f, 226f, 38f, 0xFFE9EEF2);
        } else {
            Theme.drawBanner(c, "Pick Your Truck", 700f, 110f, 72f, 0xE6F3EBDD, Theme.INK);
            for (int p = 0; p < 2; p++) {
                Theme.drawCentered(c, name(p), p * 960f + 480f, 606f, 38f, 0xFFE9EEF2);
            }
        }
        menu.draw(c);
        for (int p = 0; p < np; p++) {
            if (!ready[p]) continue;
            float mid = (np == 2 ? p * 960f : 0f) + (np == 2 ? 480f : 960f);
            Theme.drawBanner(c, "Ready!", mid + 170f, 590f - (np == 2 ? 0f : 30f), 44f, 0xE6BFE0B8, Theme.INK);
        }
        if (np == 2) {
            int left = Math.max(0, (int) Math.ceil(AUTO_START_SECONDS - waited));
            if (!(ready[0] && ready[1]) && (ready[0] || ready[1])) {
                Theme.drawBanner(c, "Waiting for the other player", Stage.W / 2f, 1036f, 40f, 0xE6F3EBDD, Theme.INK);
            }
            paintFill.setColor(Theme.INK);
            c.drawRect(Stage.W / 2f - 3f, 330f, Stage.W / 2f + 3f, Stage.H, paintFill);   // the divider between the two halves
            if (left <= 5 && left > 0) Theme.drawCentered(c, String.valueOf(left), Stage.W / 2f, 420f, 90f, 0xFFF2C572);
        }
    }

    private String name(int p) {
        return TruckPaints.TRUCK_NAMES[truck[p]] + " truck  -  " + TruckPaints.NAMES[paint[p]];
    }

    /** The dark garage: back wall with slats and a hazard stripe, a floor, ceiling lamps; holes where the 3D trucks show. */
    private void drawGarage(Canvas c) {
        c.save();
        if (gl != null) {
            for (int p = 0; p < np; p++) {
                rect.set(windowLeft(p), np == 2 ? 640f : 580f, windowRight(p), 1060f);
                c.clipRect(rect, Region.Op.DIFFERENCE);
            }
        }
        paintFill.setStyle(Paint.Style.FILL);
        paintFill.setColor(FLOOR);
        c.drawRect(0f, 0f, Stage.W, Stage.H, paintFill);
        paintFill.setColor(WALL);
        c.drawRect(0f, 0f, Stage.W, 540f, paintFill);
        paintFill.setColor(0x14FFFFFF);
        for (int x = 0; x < 1920; x += 160) c.drawRect(x, 0f, x + 6f, 540f, paintFill);   // wall panel seams
        paintFill.setColor(0xFFE0B03A);
        for (int x = -40; x < 1960; x += 80) {   // hazard stripe along the foot of the wall
            Path s = new Path();
            s.moveTo(x, 548f);
            s.lineTo(x + 40f, 548f);
            s.lineTo(x + 20f, 524f);
            s.lineTo(x - 20f, 524f);
            s.close();
            c.drawPath(s, paintFill);
        }
        for (int i = 0; i < 3; i++) {   // lamps with a faint cone of light
            float lx = 480f + i * 480f;
            paintFill.setColor(0x10FFFFFF);
            Path cone = new Path();
            cone.moveTo(lx - 40f, 40f);
            cone.lineTo(lx + 40f, 40f);
            cone.lineTo(lx + 300f, 540f);
            cone.lineTo(lx - 300f, 540f);
            cone.close();
            c.drawPath(cone, paintFill);
            paintFill.setColor(0xFFF2C572);
            c.drawRoundRect(new RectF(lx - 60f, 20f, lx + 60f, 48f), 12f, 12f, paintFill);
        }
        if (gl == null) {   // no 3D view: a flat picture of each truck stands in
            for (int p = 0; p < np; p++) {
                float x = windowLeft(p), w = windowRight(p) - x;
                float top = np == 2 ? 640f : 580f;
                c.save();
                c.translate(x, top);
                TruckSwatchArt.paint(c, (int) w, (int) (1060f - top), truck[p]);
                c.restore();
            }
        }
        c.restore();
        if (gl != null) submitWorld();
    }

    // ---- 3D ----

    private void submitWorld() {
        list.begin();
        float spin = time * 7f;
        for (int p = 0; p < np; p++) {
            int bit = 1 << p;
            float yaw = 0.6f + time * 0.5f;
            Mat4.translate(mT, 0f, 0.004f, 0f);
            list.add(ringId, 0, mT, 0x2FA8C4, true, bit);
            Mat4.translate(mT, 0f, 0.012f, 0f);
            list.add(padId, 0, mT, 0x143641, true, bit);
            for (int i = 0; i < 12; i++) {   // marks round the rim turn with the platform
                float a = yaw + i * 0.5235988f;
                Mat4.translate(mT, 1.95f * (float) Math.sin(a), 0.02f, 1.95f * (float) Math.cos(a));
                list.add(dotId, 0, mT, i % 3 == 0 ? 0xF2C572 : 0x5ED0E8, true, bit);
            }
            float k = slide[p] >= 1f ? 1f : 1f - (float) Math.pow(1f - slide[p], 3);
            if (slide[p] < 1f) {
                addTruck(oldTruck[p], oldPaint[p], -slideDir[p] * 6.5f * k, yaw, spin, bit);
                addTruck(truck[p], paint[p], slideDir[p] * 6.5f * (1f - k), yaw, spin, bit);
            } else {
                addTruck(truck[p], paint[p], 0f, yaw, spin, bit);
            }
            float[] ey = eye;
            gl.setCamera(p, ey, target, 40f);
        }
        gl.setViewports(np);
        gl.submit(list);
    }

    private void addTruck(int m, int paintJob, float ox, float yaw, float spin, int bit) {
        Mat4.translate(mT, ox, 0.03f, 0f);
        Mat4.rotY(mR, yaw);
        Mat4.multiply(mBase, mT, mR);
        MeshData md = meshData[m];
        int tint = TruckPaints.renderColour(paintJob);
        for (int k = 0; k < 6; k++) {
            MeshData.Part part = md.parts[partIdx[m][k]];
            Mat4.translate(mA, part.translation[0], part.translation[1], part.translation[2]);
            Mat4.multiply(mB, mBase, mA);
            if (k >= 2) {   // wheels roll and are never painted
                Mat4.rotX(mR, spin);
                Mat4.multiply(mC, mB, mR);
                list.add(meshId[m], partIdx[m][k], mC, 0, false, bit);
            } else {
                list.add(meshId[m], partIdx[m][k], mB, tint, false, bit);
            }
        }
        Mat4.translate(mA, ox, 0.05f, 0f);
        list.add(shadowId, 0, mA, 0x59000000, true, bit);
    }
}
