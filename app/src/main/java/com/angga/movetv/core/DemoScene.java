package com.angga.movetv.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.KeyEvent;

import com.angga.movetv.audio.Music;
import com.angga.movetv.audio.Sfx;
import com.angga.movetv.audio.Synth;
import com.angga.movetv.audio.Tracks;
import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.PlayerPose;
import com.angga.movetv.pose.PoseFrame;

import java.util.Random;

/**
 * A stress test and showcase for the framework: the camera picture (or a paper background), your
 * skeleton, and 200 floating bubbles that move with parallax, drawn in all three art styles (a third
 * each: Flat storybook, Crayon doodle, Neon arcade). The bubbles are painted once into the
 * {@link SpriteCache}, so each frame only copies bitmaps. The performance overlay is shown.
 *
 * It also holds the sound test: Up/Down choose an effect, OK plays it, Right (or M) cycles the music,
 * and on a keyboard F1-F12 and Z X C V play the 16 effects directly.
 */
public final class DemoScene implements Scene {

    private static final int BUBBLES = 200;
    private static final int LOW_BUBBLES = 100;
    private static final int LAYERS = 3;
    private static final int STYLES = 3;
    private static final int COLOURS = 4;
    private static final int[] SIZES = {56, 96, 150};            // far, middle, near
    private static final float[] RISE = {42f, 72f, 118f};          // pixels per second
    private static final float[] PARALLAX = {0.06f, 0.16f, 0.34f}; // how far the player's position shifts a layer

    private static final int[][] PALETTE = {
            // Flat storybook
            {0xFF5B8DB8, 0xFF9CB89A, 0xFFE89A4C, 0xFFE8A598},
            // Crayon doodle
            {0xFFE8453C, 0xFF2D7DD2, 0xFF3BB273, 0xFFF2B705},
            // Neon arcade
            {0xFF7FF4FF, 0xFFFF4FD8, 0xFFFFC27A, 0xFF6BFF8E}};

    private static final int[][] BONES = {
            {Kp.L_SHOULDER, Kp.R_SHOULDER}, {Kp.L_SHOULDER, Kp.L_ELBOW}, {Kp.L_ELBOW, Kp.L_WRIST},
            {Kp.R_SHOULDER, Kp.R_ELBOW}, {Kp.R_ELBOW, Kp.R_WRIST},
            {Kp.L_SHOULDER, Kp.L_HIP}, {Kp.R_SHOULDER, Kp.R_HIP}, {Kp.L_HIP, Kp.R_HIP},
            {Kp.L_HIP, Kp.L_KNEE}, {Kp.L_KNEE, Kp.L_ANKLE}, {Kp.R_HIP, Kp.R_KNEE}, {Kp.R_KNEE, Kp.R_ANKLE}};
    private static final int[] PLAYER_COLOURS = {0xFF29B6F6, 0xFFFF6EC7};
    private static final float GLIDE = 0.65f;
    private static final float MIN_DRAW = 0.2f;
    private static final int PAPER = 0xFFF3EBDD;
    private static final String HELP =
            "REMOTE   Up/Down: effect   OK: play   Right: song   Menu \u2261: 1 or 2 players   Left: light   hold Left: fps";

    /** One floating bubble (pooled). */
    private static final class Bubble {
        float x, y, phase;
        int layer, style, colour;
    }

    private final PerfOverlay perf;
    private final Sfx sfx;
    private final Music music;

    private final Pool<Bubble> pool = new Pool<>(BUBBLES, Bubble::new);
    private final Bitmap[][][] sprites = new Bitmap[STYLES][COLOURS][LAYERS];
    private final Random rnd = new Random(42);

    private final PoseFrame target = new PoseFrame();
    private final PoseFrame shown = new PoseFrame();
    private boolean haveShown;
    private Bitmap camera;
    private float shiftNose = 0f; // smoothed horizontal offset of the player's nose from the stage centre
    private float time;

    private final Paint paperPaint = new Paint();
    private final Paint bonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint();
    private final Paint beatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // sound test
    private int fxIndex = 0;
    private int trackIndex = -1; // -1 = music off
    private int playersShown = 1;
    private String statusLine = ""; // "Effect 5/16: pop     Music: off"; rebuilt only when it changes
    private volatile long beatFlashUntil = 0;
    private volatile int lastBeat = 0;
    private long now;

    /**
     * Creates the scene.
     * @param perf the performance overlay, switched on while this scene is shown
     * @param sfx sound effects for the sound test
     * @param music music for the sound test
     */
    public DemoScene(PerfOverlay perf, Sfx sfx, Music music) {
        this.perf = perf;
        this.sfx = sfx;
        this.music = music;
        bonePaint.setStrokeWidth(10f);
        bonePaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        panelPaint.setColor(Color.argb(170, 0, 0, 0));
        paperPaint.setColor(PAPER);
        dotPaint.setColor(Color.WHITE);
        updateLabels();
    }

    @Override
    public void enter(SceneArgs args) {
        perf.setVisible(true);
        SpriteCache cache = SpriteCache.shared();
        for (int s = 0; s < STYLES; s++) {
            for (int c = 0; c < COLOURS; c++) {
                for (int l = 0; l < LAYERS; l++) {
                    final int style = s, colour = c, size = SIZES[l];
                    sprites[s][c][l] = cache.get("demo_bubble_" + s + "_" + c + "_" + l, size, size,
                            (cv, w, h) -> paintBubble(cv, style, PALETTE[style][colour], w));
                }
            }
        }
        pool.clear();
        for (int i = 0; i < BUBBLES; i++) {
            Bubble b = pool.obtain();
            b.layer = i < 90 ? 0 : (i < 160 ? 1 : 2);
            b.style = i % STYLES;
            b.colour = rnd.nextInt(COLOURS);
            b.x = rnd.nextFloat() * Stage.W;
            b.y = rnd.nextFloat() * (Stage.H + 200f) - 100f;
            b.phase = rnd.nextFloat() * 6.2832f;
        }
        haveShown = false;
        music.setBeatListener((beat, bar) -> {
            lastBeat = beat;
            beatFlashUntil = android.os.SystemClock.uptimeMillis() + 120;
        });
    }

    @Override
    public void exit() {
        music.setBeatListener(null);
        music.stop(200);
        trackIndex = -1;
        updateLabels();
        pool.clear();
    }

    @Override
    public void update(float dt, Frame f) {
        now = f.nowMs;
        camera = f.camera;
        time += dt;
        f.pose.copyInto(target);
        if (target.mode != playersShown && (target.mode == 1 || target.mode == 2)) {
            playersShown = target.mode;
            updateLabels();
        }
        handleInput(f.input);

        // glide the shown skeleton toward the newest result (the AI updates only 7-14 times a second)
        if (!haveShown) {
            target.copyInto(shown);
            haveShown = true;
        } else {
            shown.mode = target.mode;
            for (int n = 0; n < shown.players.length; n++) {
                PlayerPose sp = shown.players[n], tp = target.players[n];
                boolean appeared = !sp.present && tp.present;
                sp.present = tp.present;
                for (int k = 0; k < Kp.COUNT; k++) {
                    Keypoint a = sp.kps[k], b = tp.kps[k];
                    if (appeared) { a.x = b.x; a.y = b.y; }
                    else { a.x += (b.x - a.x) * GLIDE; a.y += (b.y - a.y) * GLIDE; }
                    a.s = b.s;
                }
            }
        }

        // the player's position shifts the bubble layers (parallax); LOW quality has no parallax
        float goal = 0f;
        if (GameView.quality == GameView.HIGH) {
            for (PlayerPose p : shown.players) {
                if (p.present && p.kps[Kp.NOSE].s >= MIN_DRAW) { goal = p.kps[Kp.NOSE].x - Stage.W / 2f; break; }
            }
        }
        shiftNose += (goal - shiftNose) * Math.min(1f, dt * 4f);

        // LOW quality: half the bubbles
        while (GameView.quality == GameView.LOW && pool.size() > LOW_BUBBLES) pool.freeAt(pool.size() - 1);

        for (int i = 0; i < pool.size(); i++) {
            Bubble b = pool.at(i);
            b.y -= RISE[b.layer] * dt;
            if (b.y < -SIZES[b.layer]) {
                b.y = Stage.H + SIZES[b.layer];
                b.x = rnd.nextFloat() * Stage.W;
            }
        }
    }

    private void handleInput(Input in) {
        int n = Synth.EFFECTS.length;
        if (in.pressed(KeyEvent.KEYCODE_DPAD_UP)) { fxIndex = (fxIndex + n - 1) % n; updateLabels(); }
        if (in.pressed(KeyEvent.KEYCODE_DPAD_DOWN)) { fxIndex = (fxIndex + 1) % n; updateLabels(); }
        if (in.pressed(KeyEvent.KEYCODE_DPAD_CENTER) || in.pressed(KeyEvent.KEYCODE_ENTER)) {
            sfx.play(Synth.EFFECTS[fxIndex]);
        }
        if (in.pressed(KeyEvent.KEYCODE_DPAD_RIGHT) || in.pressed(KeyEvent.KEYCODE_M)) nextTrack();
        for (int i = 0; i < 12 && i < n; i++) {
            if (in.pressed(KeyEvent.KEYCODE_F1 + i)) sfx.play(Synth.EFFECTS[i]);
        }
        if (n > 12 && in.pressed(KeyEvent.KEYCODE_Z)) sfx.play(Synth.EFFECTS[12]);
        if (n > 13 && in.pressed(KeyEvent.KEYCODE_X)) sfx.play(Synth.EFFECTS[13]);
        if (n > 14 && in.pressed(KeyEvent.KEYCODE_C)) sfx.play(Synth.EFFECTS[14]);
        if (n > 15 && in.pressed(KeyEvent.KEYCODE_V)) sfx.play(Synth.EFFECTS[15]);
    }

    private void nextTrack() {
        String[] names = Tracks.names();
        trackIndex++;
        if (trackIndex >= names.length) {
            trackIndex = -1;
            music.stop(400);
        } else {
            music.play(names[trackIndex]);
        }
        updateLabels();
    }

    private void updateLabels() { // only when something changes, so draw() never builds text
        statusLine = "Effect " + (fxIndex + 1) + "/" + Synth.EFFECTS.length + ": " + Synth.EFFECTS[fxIndex]
                + "     Music: " + (trackIndex < 0 ? "off" : Tracks.names()[trackIndex])
                + "     Players: " + playersShown;
    }

    @Override
    public void draw(Canvas c) {
        if (camera != null) {
            Stage.drawCamera(c, camera, 0.35f);
        } else {
            c.drawRect(0f, 0f, Stage.W, Stage.H, paperPaint);
        }

        // bubbles, far layers first
        boolean parallax = GameView.quality == GameView.HIGH;
        for (int layer = 0; layer < LAYERS; layer++) {
            float shift = parallax ? -shiftNose * PARALLAX[layer] : 0f;
            for (int i = 0; i < pool.size(); i++) {
                Bubble b = pool.at(i);
                if (b.layer != layer) continue;
                float sway = (float) Math.sin(time * 0.9f + b.phase) * (8f + 6f * layer);
                c.drawBitmap(sprites[b.style][b.colour][layer], b.x + shift + sway, b.y, null);
            }
        }

        // skeleton
        for (int n = 0; n < shown.players.length; n++) {
            PlayerPose p = shown.players[n];
            if (!haveShown || !p.present) continue;
            bonePaint.setColor(PLAYER_COLOURS[n % PLAYER_COLOURS.length]);
            for (int[] bone : BONES) {
                Keypoint a = p.kps[bone[0]], b = p.kps[bone[1]];
                if (a.s >= MIN_DRAW && b.s >= MIN_DRAW) c.drawLine(a.x, a.y, b.x, b.y, bonePaint);
            }
            Keypoint nose = p.kps[Kp.NOSE];
            if (nose.s >= MIN_DRAW) {
                dotPaint.setColor(PLAYER_COLOURS[n % PLAYER_COLOURS.length]);
                c.drawCircle(nose.x, nose.y, 46f, dotPaint);
            }
            dotPaint.setColor(Color.WHITE);
            Keypoint lw = p.kps[Kp.L_WRIST], rw = p.kps[Kp.R_WRIST];
            if (lw.s >= MIN_DRAW) c.drawCircle(lw.x, lw.y, 14f, dotPaint);
            if (rw.s >= MIN_DRAW) c.drawCircle(rw.x, rw.y, 14f, dotPaint);
        }

        // sound test panel
        c.drawRect(0f, Stage.H - 130f, Stage.W, Stage.H, panelPaint);
        c.drawText(HELP, 24f, Stage.H - 86f, textPaint);
        c.drawText(statusLine, 24f, Stage.H - 36f, textPaint);
        beatPaint.setColor(now < beatFlashUntil ? 0xFFFFD54F : 0x55FFFFFF);
        c.drawCircle(Stage.W - 70f, Stage.H - 65f, now < beatFlashUntil ? 34f : 26f, beatPaint);
    }

    // ---- bubble art, painted once into the SpriteCache ----

    private static int darker(int colour) {
        int r = (int) (((colour >> 16) & 0xff) * 0.7f), g = (int) (((colour >> 8) & 0xff) * 0.7f),
                b = (int) ((colour & 0xff) * 0.7f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void paintBubble(Canvas c, int style, int colour, int size) {
        float margin = size * 0.14f, r = size / 2f - margin, mid = size / 2f;
        Path circle = new Path();
        circle.addCircle(mid, mid, r, Path.Direction.CW);
        if (style == 0) { // Flat storybook: flat shadow, flat fill, flat highlight
            Brushes.flatShadow(c, circle, size * 0.05f, darker(colour));
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(colour);
            c.drawPath(circle, p);
            p.setColor(0x99FFFFFF);
            c.drawOval(new RectF(mid - r * 0.6f, mid - r * 0.62f, mid - r * 0.15f, mid - r * 0.3f), p);
        } else if (style == 1) { // Crayon doodle: hatched fill, wobbly outline
            Brushes.crayonFill(c, circle, colour, 40f, Math.max(4f, size * 0.09f));
            Brushes.drawCrayonOutline(c, circle, 1000L + colour, size * 0.03f, darker(colour), Math.max(2f, size * 0.045f));
        } else { // Neon arcade: glow under a thin bright line
            Brushes.neonStroke(c, circle, colour, Math.max(2f, size * 0.04f));
            Path arc = new Path();
            arc.addArc(new RectF(mid - r * 0.62f, mid - r * 0.62f, mid + r * 0.62f, mid + r * 0.62f), 200f, 60f);
            Brushes.neonStroke(c, arc, 0xFFFFFFFF, Math.max(2f, size * 0.03f));
        }
    }
}
