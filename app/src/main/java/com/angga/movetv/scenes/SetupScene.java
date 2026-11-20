package com.angga.movetv.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.KeyEvent;

import com.angga.movetv.core.SpriteCache;
import com.angga.movetv.core.Scene;
import com.angga.movetv.core.Stage;
import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.PlayerPose;
import com.angga.movetv.pose.PoseFrame;
import com.angga.movetv.ui.Theme;
import com.angga.movetv.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Set-up: a big camera picture with a body-outline guide. The distance is judged by shoulder width:
 * 110 to 260 px is good (green outline, "Great! You're ready!"), below 110 "Come a bit closer", above 260
 * "Step back a little". If the average confidence stays under 0.4 for 2 s it says it is dark. After 1.5 s of
 * "good" it continues by itself. The "Skip" tile (hold, or OK on the remote) continues at once.
 */
public final class SetupScene implements Scene {

    static final float NEAR_PX = 110f, FAR_PX = 260f;
    private static final long GOOD_MS = 1500, DARK_MS = 2000;
    private static final float DARK_CONF = 0.4f;

    private static final String T_TITLE = "Let's get ready!";
    private static final String T_NOBODY = "Stand where the camera can see you";
    private static final String T_CLOSER = "Come a bit closer";
    private static final String T_BACK = "Step back a little";
    private static final String T_DARK = "It's a bit dark - turn on a light in front of you";
    private static final String T_GOOD = "Great! You're ready!";
    private static final int NOBODY = 0, CLOSER = 1, BACK = 2, DARK = 3, GOOD = 4;

    private final TileMenu.Env env;
    private final TileMenu menu;
    private Bitmap camera;
    private float time;
    private boolean fromBack;
    private float goodMs, lowConfMs;
    private int state = NOBODY;
    private boolean showSkeleton;
    private final PoseFrame pose = new PoseFrame();
    private final Paint bone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap outlineWhite, outlineGreen;

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public SetupScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
        bone.setStrokeWidth(12f);
        bone.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Judges how the player stands.
     * @param present whether a body is tracked
     * @param shoulderWidth shoulder width in stage pixels (0 if unknown)
     * @param lowConfidenceMs how long the average confidence has been under 0.4
     * @return one of the screen's states (0 nobody, 1 closer, 2 back, 3 dark, 4 good)
     */
    static int judge(boolean present, float shoulderWidth, float lowConfidenceMs) {
        if (lowConfidenceMs >= DARK_MS) return DARK;
        if (!present || shoulderWidth <= 0f) return NOBODY;
        if (shoulderWidth < NEAR_PX) return CLOSER;
        if (shoulderWidth > FAR_PX) return BACK;
        return GOOD;
    }

    @Override
    public void enter(SceneArgs args) {
        env.menuMusic();
        if (env.hub != null) env.hub.releaseLock();   // "stand in the middle" hands over control
        fromBack = args.getInt("back", 0) == 1;
        goodMs = 0f;
        lowConfMs = 0f;
        state = NOBODY;
        List<TileMenu.Tile> tiles = new ArrayList<>();
        tiles.add(new TileMenu.Tile("skip", "Skip", null, Theme.MUSTARD, Theme::paintSkip,
                Stage.W - 48f - 380f, Stage.H - 48f - 260f, 380f, 260f));
        menu.setTiles(tiles, "skip");
        outlineWhite = outline("setup_outline_w", 0xE6FFFFFF, 10f);
        outlineGreen = outline("setup_outline_g", 0xFF6BDB7A, 16f);
    }

    private static Bitmap outline(String key, final int colour, final float width) {
        return SpriteCache.shared().get(key, 560, 820, (c, w, h) -> {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(colour);
            p.setStrokeWidth(width);
            Path head = new Path();
            head.addCircle(w / 2f, 150f, 70f, Path.Direction.CW);
            c.drawPath(head, p);
            Path torso = new Path();
            RectF r = new RectF(w / 2f - 95f, 250f, w / 2f + 95f, 640f);
            torso.addRoundRect(r, new float[] {90f, 90f, 90f, 90f, 20f, 20f, 20f, 20f}, Path.Direction.CW);
            c.drawPath(torso, p);
            c.drawLine(w / 2f - 95f, 300f, w / 2f - 180f, 560f, p);
            c.drawLine(w / 2f + 95f, 300f, w / 2f + 180f, 560f, p);
        });
    }

    @Override
    public void exit() { }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        f.pose.copyInto(pose);
        showSkeleton = true;
        PlayerPose p = pose.players[0];
        float sum = 0f;
        for (int k = 0; k < Kp.COUNT; k++) sum += p.kps[k].s;
        float avg = sum / Kp.COUNT;
        lowConfMs = avg < DARK_CONF ? lowConfMs + dt * 1000f : 0f;
        state = judge(p.present, PoseFrame.shoulderWidth(p), lowConfMs);
        goodMs = state == GOOD ? goodMs + dt * 1000f : 0f;

        String sel = menu.update(dt, f);
        if (f.input.pressed(KeyEvent.KEYCODE_BACK)) {
            env.sfx("back");
            env.go("attract", SceneArgs.EMPTY);
            return;
        }
        if ("skip".equals(sel) || (goodMs >= GOOD_MS && !fromBack)) {
            env.go("players", SceneArgs.EMPTY);
        }
    }

    @Override
    public void draw(Canvas c) {
        if (camera != null) {
            Stage.drawCamera(c, camera, 0.1f);
        } else {
            Theme.drawBackdrop(c, null, time);
        }
        Bitmap o = state == GOOD ? outlineGreen : outlineWhite;
        c.drawBitmap(o, (Stage.W - o.getWidth()) / 2f, 150f, null);
        if (showSkeleton) drawSkeleton(c, pose.players[0]);
        Theme.drawBanner(c, T_TITLE, Stage.W / 2f, 90f, 64f, 0xE6F3EBDD, Theme.INK);
        String msg;
        int bg = 0xE6F3EBDD;
        switch (state) {
            case CLOSER: msg = T_CLOSER; break;
            case BACK: msg = T_BACK; break;
            case DARK: msg = T_DARK; break;
            case GOOD: msg = T_GOOD; bg = 0xF2B9E3B0; break;
            default: msg = T_NOBODY; break;
        }
        Theme.drawBanner(c, msg, Stage.W / 2f - 150f, 960f, 56f, bg, Theme.INK);
        menu.draw(c);
    }

    private void drawSkeleton(Canvas c, PlayerPose p) {
        if (!p.present) return;
        bone.setColor(Theme.P1);
        Keypoint ls = p.kps[Kp.L_SHOULDER], rs = p.kps[Kp.R_SHOULDER];
        if (ls.s >= 0.3f && rs.s >= 0.3f) c.drawLine(ls.x, ls.y, rs.x, rs.y, bone);
        line(c, p, Kp.L_SHOULDER, Kp.L_ELBOW);
        line(c, p, Kp.L_ELBOW, Kp.L_WRIST);
        line(c, p, Kp.R_SHOULDER, Kp.R_ELBOW);
        line(c, p, Kp.R_ELBOW, Kp.R_WRIST);
        Keypoint nose = p.kps[Kp.NOSE];
        if (nose.s >= 0.3f) c.drawCircle(nose.x, nose.y, 30f, bone);
    }

    private void line(Canvas c, PlayerPose p, int a, int b) {
        Keypoint ka = p.kps[a], kb = p.kps[b];
        if (ka.s >= 0.3f && kb.s >= 0.3f) c.drawLine(ka.x, ka.y, kb.x, kb.y, bone);
    }
}
