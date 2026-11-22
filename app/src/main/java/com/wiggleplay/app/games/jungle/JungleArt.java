package com.wiggleplay.app.games.jungle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.Brushes;
import com.wiggleplay.app.core.SpriteCache;
import com.wiggleplay.app.ui.Theme;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * The pictures of Jungle Dash, Crayon doodle style. The finished art (the monkey, the forest layers, 10 tree species,
 * rainforest critters and a macaw) is drawn by {@code tools/jungle/export.py} into {@code assets/jungle} and loaded
 * once; the code-drawn pictures below stay as the fallback when a file is missing. Original fallback notes: wobbly hand-drawn outlines (made once from a seed) and fills made of
 * diagonal crayon strokes over a light wash. Everything is painted once into {@link SpriteCache} bitmaps when the game
 * is entered; the scene only copies bitmaps (no gradients, no shaders, nothing is drawn per frame). The three levels
 * have their own sky and leaf colours: sunny morning, golden afternoon, dusk. Main thread only.
 */
final class JungleArt {

    /** Where the ground begins on the stage (the monkey's feet touch this line). */
    static final float GROUND_Y = 800f;
    /** Animation frame counts. */
    static final int RUN = 6, JUMP = 3, DUCK = 2, STUMBLE = 3;
    /** Size of one monkey frame in pixels (square); the feet are at {@link #FEET} of the height. */
    static final int FRAME = 320;
    static final float FEET = 238f / 256f;
    /** Width of a scrolling layer picture; it is drawn at twice that width on the stage. */
    static final int TILE_W = 960;

    private static final int BG_W = 960, BG_H = 540;
    private static final int FAR_H = 200, MID_H = 300, GROUND_H = 140, TUFT_H = 56;

    static final int BROWN = 0xFFB5651D, DARK = 0xFF7A4210, CREAM = 0xFFF2C572, LOG = 0xFF9B6B43;

    private static final Paint F = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint S = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path P = new Path();
    private static final Path OUT = new Path();
    private static final RectF R = new RectF();

    private static Bitmap log, doubleLog, vine, banana, golden, markUp, markDown, shadow;
    private static final Bitmap[] RUN_F = new Bitmap[RUN];
    private static final Bitmap[] JUMP_F = new Bitmap[JUMP];
    private static final Bitmap[] DUCK_F = new Bitmap[DUCK];
    private static final Bitmap[] STUMBLE_F = new Bitmap[STUMBLE];
    private static final Bitmap[] BG = new Bitmap[3];
    private static final Bitmap[] FAR = new Bitmap[3];
    private static final Bitmap[] MID = new Bitmap[3];
    private static final Bitmap[] GROUND = new Bitmap[3];
    private static Bitmap tufts;

    /** Sky wash, sun, far hills, far trees, mid trunks, mid leaves, grass, dirt (per level). */
    private static final int[][] PAL = {
            {0xFFBEE3F2, 0xFFFFD84D, 0xFFA9D6A0, 0xFF6FB36B, 0xFF9B6B43, 0xFF4CAF50, 0xFF6FBF4F, 0xFFC08A52},
            {0xFFF6D9A0, 0xFFFF9F43, 0xFFC9C78A, 0xFF8FA84C, 0xFF8A5A33, 0xFF7CAA3A, 0xFF8DB544, 0xFFB7793F},
            {0xFFE8A98F, 0xFFFFE9B0, 0xFF9C8FB0, 0xFF5E8A78, 0xFF6E4A33, 0xFF3E8C6A, 0xFF4F9A5C, 0xFF9A6540}};

    static {
        S.setStyle(Paint.Style.STROKE);
        S.setStrokeCap(Paint.Cap.ROUND);
        S.setStrokeJoin(Paint.Join.ROUND);
    }

    private JungleArt() { }

    // ---- the drawn art from assets/jungle ----
    /** Number of tree species in {@code assets/jungle/tree0..9}. */
    static final int TREES = 10;
    /** Tree pictures: size and where the trunk meets the ground. */
    static final int TREE_W = 440, TREE_H = 680, TREE_BASE_X = 220, TREE_BASE_Y = 672;
    /** Critters drawn next to the trees. */
    static final int TOUCAN = 0, FROG = 1, MORPHO = 2, FERN = 3, MUSHROOMS = 4, THINGS = 5;
    private static final String[] THING_FILES = {"toucan", "frog", "morpho0", "fern", "mushrooms"};

    private static Context ctx;
    private static boolean fancy, tried;
    private static final Bitmap[] WALK_A = new Bitmap[4], DUCK_A = new Bitmap[4], JUMP_A = new Bitmap[4],
            FALL_A = new Bitmap[4], SKY_A = new Bitmap[3], TREE_A = new Bitmap[TREES], THING_A = new Bitmap[THINGS],
            BIRD_A = new Bitmap[3], MORPHO_A = new Bitmap[2];
    private static Bitmap farA, midA, groundA;

    /**
     * Gives the pictures access to the app's assets (call once at start).
     * @param context any context
     */
    static void init(Context context) {
        ctx = context.getApplicationContext();
    }

    private static Bitmap file(String name) {
        if (ctx == null) return null;
        try (InputStream in = ctx.getAssets().open("jungle/" + name + ".png")) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Loads the drawn art once; it is used only when every file is there. */
    private static void loadFiles() {
        if (tried || ctx == null) return;
        tried = true;
        boolean ok = true;
        for (int i = 0; i < 4; i++) {
            ok &= (WALK_A[i] = file("walk" + i)) != null;
            ok &= (DUCK_A[i] = file("duck" + i)) != null;
            ok &= (JUMP_A[i] = file("jump" + i)) != null;
            ok &= (FALL_A[i] = file("fall" + i)) != null;
        }
        for (int i = 0; i < 3; i++) {
            ok &= (SKY_A[i] = file("sky" + (i + 1))) != null;
            ok &= (BIRD_A[i] = file("bird" + i)) != null;
        }
        for (int i = 0; i < TREES; i++) ok &= (TREE_A[i] = file("tree" + i)) != null;
        for (int i = 0; i < THINGS; i++) ok &= (THING_A[i] = file(THING_FILES[i])) != null;
        MORPHO_A[0] = THING_A[MORPHO];
        ok &= (MORPHO_A[1] = file("morpho1")) != null;
        ok &= (farA = file("far")) != null;
        ok &= (midA = file("mid")) != null;
        ok &= (groundA = file("ground")) != null;
        fancy = ok;
    }

    /** @return true when the drawn art from assets is in use (trees, critters and birds are shown) */
    static boolean fancy() { return fancy; }
    static Bitmap tree(int i) { return TREE_A[Math.floorMod(i, TREES)]; }
    static Bitmap thing(int kind) { return THING_A[kind]; }
    static Bitmap morpho(int f) { return MORPHO_A[f & 1]; }
    /** @param f 0..3, flapping: up, middle, down, middle */
    static Bitmap bird(int f) { int k = Math.floorMod(f, 4); return BIRD_A[k == 3 ? 1 : k]; }
    /** @param i dizzy frame, alternates */
    static Bitmap dizzy(int i) { return fancy ? FALL_A[2 + (i & 1)] : stumble(2); }

    /**
     * Paints (once) everything the game needs for a level. Call from the scene's {@code enter}.
     * @param level 1..3
     */
    static void prepare(int level) {
        loadFiles();
        SpriteCache sc = SpriteCache.shared();
        int li = level - 1;
        if (log == null) {
            log = sc.get("jg_log", 300, 80, JungleArt::paintLog);
            doubleLog = sc.get("jg_dlog", 400, 150, JungleArt::paintDoubleLog);
            vine = sc.get("jg_vine", 200, 640, JungleArt::paintVine);
            banana = sc.get("jg_banana", 96, 96, JungleArt::paintBanana);
            golden = sc.get("jg_golden", 128, 128, JungleArt::paintGolden);
            markUp = sc.get("jg_mark_up", 150, 150, (c, w, h) -> paintMarker(c, w, h, true));
            markDown = sc.get("jg_mark_down", 150, 150, (c, w, h) -> paintMarker(c, w, h, false));
            shadow = sc.get("jg_shadow", 256, 64, (c, w, h) -> {
                F.setStyle(Paint.Style.FILL);
                F.setColor(0x44000000);
                R.set(0, 0, w, h);
                c.drawOval(R, F);
            });
            int k = 0;
            for (int i = 0; i < RUN; i++, k++) {
                final int fi = i;
                RUN_F[i] = sc.get("jg_run" + i, FRAME, FRAME, (c, w, h) -> monkey(c, w, h, runPose(fi)));
            }
            for (int i = 0; i < JUMP; i++) {
                final int fi = i;
                JUMP_F[i] = sc.get("jg_jump" + i, FRAME, FRAME, (c, w, h) -> monkey(c, w, h, jumpPose(fi)));
            }
            for (int i = 0; i < DUCK; i++) {
                final int fi = i;
                DUCK_F[i] = sc.get("jg_duck" + i, FRAME, FRAME, (c, w, h) -> monkey(c, w, h, duckPose(fi)));
            }
            for (int i = 0; i < STUMBLE; i++) {
                final int fi = i;
                STUMBLE_F[i] = sc.get("jg_stumble" + i, FRAME, FRAME, (c, w, h) -> monkey(c, w, h, stumblePose(fi)));
            }
            tufts = sc.get("jg_tufts", TILE_W, TUFT_H, JungleArt::paintTufts);
        }
        if (BG[li] == null) {
            final int lv = li;
            BG[li] = sc.get("jg_bg" + level, BG_W, BG_H, (c, w, h) -> paintSky(c, w, h, lv));
            FAR[li] = sc.get("jg_far" + level, TILE_W, FAR_H, (c, w, h) -> paintFar(c, w, h, lv));
            MID[li] = sc.get("jg_mid" + level, TILE_W, MID_H, (c, w, h) -> paintMid(c, w, h, lv));
            GROUND[li] = sc.get("jg_ground" + level, TILE_W, GROUND_H, (c, w, h) -> paintGround(c, w, h, lv));
        }
    }

    static Bitmap bg(int level) { return fancy ? SKY_A[level - 1] : BG[level - 1]; }
    static Bitmap far(int level) { return fancy ? farA : FAR[level - 1]; }
    static Bitmap mid(int level) { return fancy ? midA : MID[level - 1]; }
    static Bitmap ground(int level) { return fancy ? groundA : GROUND[level - 1]; }
    /** @return the front grass tufts, or null when the drawn art is in use (it has its own ground edge) */
    static Bitmap tufts() { return fancy ? null : tufts; }
    static Bitmap log() { return log; }
    static Bitmap doubleLog() { return doubleLog; }
    static Bitmap vine() { return vine; }
    static Bitmap banana() { return banana; }
    static Bitmap golden() { return golden; }
    static Bitmap markUp() { return markUp; }
    static Bitmap markDown() { return markDown; }
    static Bitmap shadow() { return shadow; }
    /** Running: the upright walk cycle (4 frames) when the drawn art is in use. */
    static Bitmap run(int i) { return fancy ? WALK_A[Math.floorMod(i, 4)] : RUN_F[Math.floorMod(i, RUN)]; }
    /** Jump: take-off, tuck, landing (the drawn art's first frame, the squat, is not used in the air). */
    static Bitmap jump(int i) {
        int k = Math.max(0, Math.min(JUMP - 1, i));
        return fancy ? JUMP_A[k + 1] : JUMP_F[k];
    }
    /** Ducking: the four-legged walk (4 frames) when the drawn art is in use. */
    static Bitmap duck(int i) { return fancy ? DUCK_A[Math.floorMod(i, 4)] : DUCK_F[Math.floorMod(i, DUCK)]; }
    /** Bump: 0 trip, 1 sit down, 2 dizzy (see {@link #dizzy} for the wobble). */
    static Bitmap stumble(int i) {
        int k = Math.max(0, Math.min(STUMBLE - 1, i));
        return fancy ? FALL_A[k] : STUMBLE_F[k];
    }

    // ---- crayon helpers (used only while painting) ----

    /** A light wash under the hatching so the shape reads on any background, then crayon strokes and a wobbly outline. */
    private static void crayon(Canvas c, Path p, int fill, int line, long seed, float angle, float spacing, float jitter,
                               float width) {
        F.setStyle(Paint.Style.FILL);
        F.setColor((fill & 0x00FFFFFF) | (0x99 << 24));
        c.drawPath(p, F);
        Brushes.crayonFill(c, p, fill, angle, spacing);
        Brushes.drawCrayonOutline(c, p, seed, jitter, line, width);
    }

    /** A thick curved line (an arm, a leg, a tail, a vine) with crayon fill and outline. */
    private static void limb(Canvas c, float x0, float y0, float x1, float y1, float bend, float width, int fill, int line,
                             long seed) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) len = 1f;
        float nx = -dy / len, ny = dx / len;
        P.reset();
        P.moveTo(x0, y0);
        P.quadTo((x0 + x1) / 2f + nx * bend, (y0 + y1) / 2f + ny * bend, x1, y1);
        S.setStrokeWidth(width);
        OUT.reset();
        S.getFillPath(P, OUT);
        crayon(c, OUT, fill, line, seed, 50f, Math.max(4f, width * 0.35f), 1.2f, 3.2f);
    }

    private static void blob(Canvas c, float cx, float cy, float rx, float ry, int fill, int line, long seed, float angle) {
        P.reset();
        R.set(cx - rx, cy - ry, cx + rx, cy + ry);
        P.addOval(R, Path.Direction.CW);
        crayon(c, P, fill, line, seed, angle, Math.max(4f, Math.min(rx, ry) * 0.28f), Math.min(rx, ry) * 0.05f + 0.6f, 3.4f);
    }

    // ---- the monkey ----

    /** Where the parts of the monkey are in one frame (a 256 x 256 box, facing right, feet on y = 238). */
    private static final class Pose {
        float bx = 118f, by = 140f, tilt;
        float hdx, hdy;                       // head offset from its normal place
        float bfx, bfy, ffx, ffy;             // back foot, front foot
        float bhx, bhy, fhx, fhy;             // back hand, front hand
        float bendLeg = 14f, bendArm = 10f;
        int eyes;                             // 0 normal, 1 wide, 2 dizzy
        float mouth;                          // 0 smile, 1 open, 2 wavy
        boolean stars;
        float whole;                          // rotate the whole monkey (tumble)
    }

    private static Pose runPose(int i) {
        Pose p = new Pose();
        double a = Math.PI * 2.0 * i / RUN_COUNT;
        float bob = 5f * (float) Math.abs(Math.cos(a));
        p.by = 142f - bob;
        p.tilt = 7f;
        float hipBx = p.bx - 20f, hipFx = p.bx + 8f, hipY = p.by + 30f;
        p.bfx = hipBx + 40f * (float) Math.sin(a);
        p.bfy = 238f - 30f * Math.max(0f, (float) Math.cos(a));
        p.ffx = hipFx + 40f * (float) Math.sin(a + Math.PI);
        p.ffy = 238f - 30f * Math.max(0f, (float) Math.cos(a + Math.PI));
        float shBx = p.bx + 20f, shFx = p.bx + 30f, shY = p.by - 12f;
        p.bhx = shBx + 36f * (float) Math.sin(a + Math.PI);
        p.bhy = shY + 40f - 14f * Math.max(0f, (float) Math.cos(a + Math.PI));
        p.fhx = shFx + 36f * (float) Math.sin(a);
        p.fhy = shY + 40f - 14f * Math.max(0f, (float) Math.cos(a));
        p.hdy = -bob * 0.5f;
        p.mouth = i % 3 == 0 ? 0.6f : 0f;
        return p;
    }

    private static final int RUN_COUNT = RUN;

    private static Pose jumpPose(int i) {
        Pose p = new Pose();
        if (i == 0) {            // crouch to launch
            p.by = 172f;
            p.tilt = 10f;
            p.bfx = p.bx - 34f;
            p.bfy = 238f;
            p.ffx = p.bx + 22f;
            p.ffy = 238f;
            p.bendLeg = 26f;
            p.bhx = p.bx - 44f;
            p.bhy = p.by + 8f;
            p.fhx = p.bx - 30f;
            p.fhy = p.by + 24f;
            p.hdy = 8f;
        } else if (i == 1) {     // the top: legs tucked, arms up, "wheee"
            p.by = 126f;
            p.tilt = -8f;
            p.bfx = p.bx - 6f;
            p.bfy = p.by + 70f;
            p.ffx = p.bx + 30f;
            p.ffy = p.by + 74f;
            p.bendLeg = 28f;
            p.bhx = p.bx + 52f;
            p.bhy = p.by - 76f;
            p.fhx = p.bx + 72f;
            p.fhy = p.by - 62f;
            p.mouth = 1f;
            p.eyes = 1;
        } else {                 // landing
            p.by = 168f;
            p.tilt = 4f;
            p.bfx = p.bx - 44f;
            p.bfy = 238f;
            p.ffx = p.bx + 42f;
            p.ffy = 238f;
            p.bendLeg = 24f;
            p.bhx = p.bx - 56f;
            p.bhy = p.by + 26f;
            p.fhx = p.bx + 72f;
            p.fhy = p.by + 30f;
            p.mouth = 0.6f;
        }
        return p;
    }

    private static Pose duckPose(int i) {
        Pose p = new Pose();
        p.by = 188f;
        p.tilt = 24f;
        p.hdx = -2f;
        p.hdy = 14f;
        float s = i == 0 ? 1f : -1f;
        p.bfx = p.bx - 28f + 10f * s;
        p.bfy = 238f - (i == 0 ? 0f : 10f);
        p.ffx = p.bx + 10f - 10f * s;
        p.ffy = 238f - (i == 0 ? 10f : 0f);
        p.bendLeg = 30f;
        p.bhx = p.bx + 56f + 6f * s;
        p.bhy = p.by + 40f;
        p.fhx = p.bx + 70f - 6f * s;
        p.fhy = p.by + 44f;
        p.mouth = 0.6f;
        return p;
    }

    private static Pose stumblePose(int i) {
        Pose p = new Pose();
        if (i == 0) {            // tripping forward
            p.by = 150f;
            p.tilt = 30f;
            p.bfx = p.bx - 56f;
            p.bfy = p.by + 50f;
            p.ffx = p.bx + 24f;
            p.ffy = 238f;
            p.bendLeg = 18f;
            p.bhx = p.bx + 84f;
            p.bhy = p.by - 10f;
            p.fhx = p.bx + 70f;
            p.fhy = p.by - 36f;
            p.mouth = 1f;
            p.eyes = 1;
            p.hdx = 8f;
        } else if (i == 1) {     // tumbling over
            p.by = 160f;
            p.tilt = 0f;
            p.whole = 84f;
            p.bfx = p.bx - 30f;
            p.bfy = p.by + 60f;
            p.ffx = p.bx + 20f;
            p.ffy = p.by + 64f;
            p.bhx = p.bx + 50f;
            p.bhy = p.by + 40f;
            p.fhx = p.bx + 66f;
            p.fhy = p.by + 24f;
            p.mouth = 1f;
            p.eyes = 2;
        } else {                 // sitting, dizzy, stars
            p.by = 176f;
            p.tilt = -4f;
            p.hdx = 4f;
            p.hdy = 6f;
            p.bfx = p.bx - 36f;
            p.bfy = 238f;
            p.ffx = p.bx + 30f;
            p.ffy = 238f;
            p.bendLeg = 24f;
            p.bhx = p.bx + 14f;
            p.bhy = p.by + 50f;
            p.fhx = p.bx + 40f;
            p.fhy = p.by + 52f;
            p.mouth = 2f;
            p.eyes = 2;
            p.stars = true;
        }
        return p;
    }

    private static void monkey(Canvas c, int w, int h, Pose p) {
        float sc = w / 256f;
        c.save();
        c.scale(sc, sc);
        if (p.whole != 0f) c.rotate(p.whole, p.bx, p.by);
        final int fur = BROWN, ink = DARK;
        // tail
        limb(c, p.bx - 38f, p.by + 4f, p.bx - 84f, p.by - 62f, -34f, 13f, fur, ink, 11L);
        // back leg and arm (a little darker)
        int dark = 0xFF9A5418;
        limb(c, p.bx - 20f, p.by + 30f, p.bfx, p.bfy - 6f, p.bendLeg, 24f, dark, ink, 21L);
        blob(c, p.bfx + 6f, p.bfy - 3f, 17f, 9f, dark, ink, 22L, 0f);
        limb(c, p.bx + 20f, p.by - 12f, p.bhx, p.bhy, p.bendArm, 18f, dark, ink, 23L);
        // body
        c.save();
        c.rotate(p.tilt, p.bx, p.by);
        blob(c, p.bx, p.by, 48f, 40f, fur, ink, 31L, 40f);
        blob(c, p.bx + 8f, p.by + 8f, 28f, 26f, CREAM, 0x00000000, 32L, 130f);
        c.restore();
        // front leg
        limb(c, p.bx + 8f, p.by + 32f, p.ffx, p.ffy - 6f, p.bendLeg, 26f, fur, ink, 41L);
        blob(c, p.ffx + 6f, p.ffy - 3f, 18f, 9f, fur, ink, 42L, 0f);
        // head
        float tr = (float) Math.toRadians(p.tilt);
        float hx = p.bx + 40f * (float) Math.cos(tr) + 6f + p.hdx, hy = p.by - 44f - 6f * (float) Math.cos(tr) + p.hdy;
        blob(c, hx - 20f, hy - 6f, 14f, 14f, CREAM, ink, 51L, 60f);          // ear (back)
        blob(c, hx, hy, 35f, 33f, fur, ink, 52L, 40f);
        blob(c, hx + 12f, hy + 8f, 22f, 19f, CREAM, ink, 53L, 130f);          // face patch
        face(c, hx, hy, p);
        // front arm
        limb(c, p.bx + 30f, p.by - 14f, p.fhx, p.fhy, p.bendArm, 20f, fur, ink, 61L);
        blob(c, p.fhx, p.fhy, 11f, 11f, fur, ink, 62L, 30f);
        if (p.stars) dizzyStars(c, hx, hy - 48f);
        c.restore();
    }

    private static void face(Canvas c, float hx, float hy, Pose p) {
        F.setStyle(Paint.Style.FILL);
        F.setColor(Theme.INK);
        float ex = hx + 14f, ey = hy - 8f;
        if (p.eyes == 1) {
            F.setColor(0xFFFFFFFF);
            c.drawCircle(ex, ey, 9f, F);
            F.setColor(Theme.INK);
            c.drawCircle(ex + 2f, ey, 5f, F);
        } else if (p.eyes == 2) {
            S.setColor(Theme.INK);
            S.setStrokeWidth(3.4f);
            c.drawLine(ex - 6f, ey - 6f, ex + 6f, ey + 6f, S);
            c.drawLine(ex - 6f, ey + 6f, ex + 6f, ey - 6f, S);
        } else {
            c.drawCircle(ex, ey, 5.5f, F);
            F.setColor(0xFFFFFFFF);
            c.drawCircle(ex + 1.8f, ey - 2f, 1.8f, F);
        }
        // nose dots and mouth
        F.setColor(Theme.INK);
        c.drawCircle(hx + 28f, hy + 4f, 2.2f, F);
        S.setColor(Theme.INK);
        S.setStrokeWidth(3.2f);
        if (p.mouth >= 0.9f && p.mouth < 1.5f) {
            F.setColor(0xFF8B2E2E);
            R.set(hx + 12f, hy + 12f, hx + 32f, hy + 28f);
            c.drawOval(R, F);
        } else if (p.mouth > 1.5f) {
            P.reset();
            P.moveTo(hx + 10f, hy + 20f);
            P.quadTo(hx + 16f, hy + 14f, hx + 22f, hy + 20f);
            P.quadTo(hx + 28f, hy + 26f, hx + 34f, hy + 18f);
            c.drawPath(P, S);
        } else {
            P.reset();
            float open = p.mouth > 0.3f ? 5f : 0f;
            P.moveTo(hx + 10f, hy + 16f);
            P.quadTo(hx + 22f, hy + 26f + open, hx + 34f, hy + 14f);
            c.drawPath(P, S);
        }
    }

    private static void dizzyStars(Canvas c, float cx, float cy) {
        F.setStyle(Paint.Style.FILL);
        F.setColor(0xFFFFC107);
        for (int i = 0; i < 3; i++) {
            float a = (float) (i * Math.PI * 2 / 3);
            float x = cx + (float) Math.cos(a) * 44f, y = cy + (float) Math.sin(a) * 12f;
            P.reset();
            for (int k = 0; k < 8; k++) {
                float r = (k % 2 == 0) ? 11f : 4f;
                double an = -Math.PI / 2 + k * Math.PI / 4;
                float px = x + (float) Math.cos(an) * r, py = y + (float) Math.sin(an) * r;
                if (k == 0) P.moveTo(px, py); else P.lineTo(px, py);
            }
            P.close();
            c.drawPath(P, F);
        }
    }

    // ---- things in the jungle ----

    /** A low log lying across the path: a round end with rings on the left, bark hatching. */
    private static void paintLog(Canvas c, int w, int h) {
        drawLog(c, 4f, 4f, w - 4f, h - 4f);
    }

    private static void drawLog(Canvas c, float x0, float y0, float x1, float y1) {
        float hh = y1 - y0;
        P.reset();
        R.set(x0, y0, x1, y1);
        P.addRoundRect(R, hh / 2f, hh / 2f, Path.Direction.CW);
        crayon(c, P, LOG, 0xFF5E3A1E, (long) (x0 * 7 + y0), 15f, 7f, 1.6f, 4f);
        // end rings
        float cx = x0 + hh / 2f, cy = (y0 + y1) / 2f;
        blob(c, cx, cy, hh * 0.38f, hh * 0.38f, 0xFFD9A86C, 0xFF5E3A1E, (long) (x0 * 3 + y1), 70f);
        S.setColor(0xFF8A5A33);
        S.setStrokeWidth(2.6f);
        c.drawCircle(cx, cy, hh * 0.22f, S);
        c.drawCircle(cx, cy, hh * 0.08f, S);
        // two bark lines
        S.setColor(0xFF5E3A1E);
        S.setStrokeWidth(2.4f);
        c.drawLine(x0 + hh, cy - hh * 0.18f, x1 - hh * 0.5f, cy - hh * 0.22f, S);
        c.drawLine(x0 + hh * 1.2f, cy + hh * 0.2f, x1 - hh * 0.8f, cy + hh * 0.16f, S);
    }

    /** Three logs in a pile: two below, one on top. Needs a big jump. */
    private static void paintDoubleLog(Canvas c, int w, int h) {
        float lh = 64f, bw = (w - 16f) / 2f + 20f;
        drawLog(c, 6f, h - 4f - lh, 6f + bw, h - 4f);
        drawLog(c, w - 6f - bw, h - 4f - lh, w - 6f, h - 4f);
        drawLog(c, w / 2f - bw / 2f, 4f, w / 2f + bw / 2f, 4f + lh);
    }

    /** A hanging jungle vine with leaves, from the top of the stage to just above a ducking monkey. */
    private static void paintVine(Canvas c, int w, int h) {
        float cx = w / 2f;
        limb(c, cx, -10f, cx + 8f, h - 60f, 22f, 22f, 0xFF4E9B3E, 0xFF2E5E26, 77L);
        S.setColor(0xFF2E5E26);
        S.setStrokeWidth(2.4f);
        // leaves along the vine
        for (int i = 0; i < 6; i++) {
            float y = 60f + i * 90f, side = (i % 2 == 0) ? -1f : 1f;
            float x = cx + 8f * (y / h) + 6f * (float) Math.sin(i);
            leaf(c, x, y, side * 46f, -26f, 0xFF66BB6A, 80L + i);
        }
        // a bushy tuft at the bottom
        float bx = cx + 8f, by = h - 52f;
        leaf(c, bx, by, -52f, 20f, 0xFF66BB6A, 91L);
        leaf(c, bx, by, 52f, 20f, 0xFF66BB6A, 92L);
        leaf(c, bx, by, 0f, 56f, 0xFF4E9B3E, 93L);
        leaf(c, bx, by, -30f, 44f, 0xFF81C784, 94L);
        leaf(c, bx, by, 30f, 44f, 0xFF81C784, 95L);
    }

    /** One leaf: a pointed oval from (x, y) to (x + dx, y + dy). */
    private static void leaf(Canvas c, float x, float y, float dx, float dy, int colour, long seed) {
        float ex = x + dx, ey = y + dy;
        float nx = -dy * 0.35f, ny = dx * 0.35f;
        P.reset();
        P.moveTo(x, y);
        P.quadTo((x + ex) / 2f + nx, (y + ey) / 2f + ny, ex, ey);
        P.quadTo((x + ex) / 2f - nx, (y + ey) / 2f - ny, x, y);
        P.close();
        crayon(c, P, colour, 0xFF2E5E26, seed, 60f, 5f, 0.9f, 2.8f);
    }

    private static void paintBanana(Canvas c, int w, int h) {
        P.reset();
        P.moveTo(w * 0.16f, h * 0.30f);
        P.quadTo(w * 0.34f, h * 0.98f, w * 0.88f, h * 0.60f);
        P.quadTo(w * 0.52f, h * 0.76f, w * 0.30f, h * 0.18f);
        P.close();
        crayon(c, P, 0xFFFFD84D, 0xFF8A6A00, 101L, 70f, 5f, 0.9f, 3.4f);
        F.setStyle(Paint.Style.FILL);
        F.setColor(0xFF6B4A12);
        c.drawCircle(w * 0.22f, h * 0.22f, 4f, F);
        c.drawCircle(w * 0.86f, h * 0.60f, 3.4f, F);
    }

    private static void paintGolden(Canvas c, int w, int h) {
        F.setStyle(Paint.Style.FILL);
        F.setColor(0x55FFE08A);
        c.drawCircle(w / 2f, h / 2f, w * 0.48f, F);
        blob(c, w / 2f, h / 2f + 4f, w * 0.34f, w * 0.34f, 0xFFFFC107, 0xFF8A5A00, 111L, 40f);
        F.setColor(0xFF8A5A00);
        c.drawCircle(w * 0.42f, h * 0.50f, 4.2f, F);
        c.drawCircle(w * 0.58f, h * 0.50f, 4.2f, F);
        c.drawCircle(w * 0.50f, h * 0.62f, 4.2f, F);
        F.setColor(0xCCFFFFFF);
        c.drawCircle(w * 0.40f, h * 0.36f, 5f, F);
        // sparkle
        S.setColor(0xFFFFFFFF);
        S.setStrokeWidth(4f);
        c.drawLine(w * 0.82f, h * 0.10f, w * 0.82f, h * 0.30f, S);
        c.drawLine(w * 0.72f, h * 0.20f, w * 0.92f, h * 0.20f, S);
    }

    /** The warning above an obstacle: a round bubble with an arrow up (jump) or down (duck). */
    private static void paintMarker(Canvas c, int w, int h, boolean up) {
        float cx = w / 2f, cy = h / 2f;
        F.setStyle(Paint.Style.FILL);
        F.setColor(0xFFFFFDF7);
        c.drawCircle(cx, cy, w * 0.44f, F);
        P.reset();
        P.addCircle(cx, cy, w * 0.44f, Path.Direction.CW);
        Brushes.drawCrayonOutline(c, P, up ? 5L : 6L, 1.6f, up ? Theme.ORANGE : Theme.BLUE, 7f);
        S.setColor(up ? Theme.ORANGE : Theme.BLUE);
        S.setStrokeWidth(13f);
        float d = up ? -1f : 1f;
        c.drawLine(cx, cy + d * 26f * -1f, cx, cy + d * 26f, S);
        c.drawLine(cx, cy + d * 26f, cx - 22f, cy + d * 4f, S);
        c.drawLine(cx, cy + d * 26f, cx + 22f, cy + d * 4f, S);
    }

    // ---- the scrolling world ----

    private interface Piece {
        void draw(Canvas c, float x);
    }

    /** Draws a piece at x and one tile to each side, so layers join up when they scroll. */
    private static void wrap(Canvas c, int w, float x, Piece p) {
        p.draw(c, x);
        p.draw(c, x - w);
        p.draw(c, x + w);
    }

    private static void paintSky(Canvas c, int w, int h, int lv) {
        int[] pal = PAL[lv];
        F.setStyle(Paint.Style.FILL);
        F.setColor(pal[0]);
        c.drawRect(0, 0, w, h, F);
        P.reset();
        P.addRect(0, 0, w, h, Path.Direction.CW);
        Brushes.crayonFill(c, P, Theme.darker(pal[0]), 35f, 16f);
        Brushes.crayonFill(c, P, 0xFFFFFFFF, 125f, 40f);
        // sun (or moon at dusk)
        float sx = w * (lv == 2 ? 0.74f : 0.80f), sy = h * (lv == 2 ? 0.34f : 0.20f), sr = 44f;
        F.setColor((pal[1] & 0x00FFFFFF) | (0x55 << 24));
        c.drawCircle(sx, sy, sr * 1.6f, F);
        blob(c, sx, sy, sr, sr, pal[1], Theme.darker(pal[1]), 201L, 30f);
        if (lv != 2) {   // sun rays
            S.setColor(pal[1]);
            S.setStrokeWidth(5f);
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                c.drawLine(sx + (float) Math.cos(a) * sr * 1.3f, sy + (float) Math.sin(a) * sr * 1.3f,
                        sx + (float) Math.cos(a) * sr * 1.75f, sy + (float) Math.sin(a) * sr * 1.75f, S);
            }
        }
        // clouds
        for (int i = 0; i < 4; i++) {
            float cx = w * (0.10f + 0.24f * i) + (i % 2) * 20f, cy = 60f + (i % 3) * 36f;
            cloud(c, cx, cy, 0.8f + 0.2f * (i % 2), 210L + i, lv == 2 ? 0xFFF7D6C6 : 0xFFFFFFFF);
        }
    }

    private static void cloud(Canvas c, float cx, float cy, float k, long seed, int colour) {
        P.reset();
        R.set(cx - 70f * k, cy - 14f * k, cx + 70f * k, cy + 28f * k);
        P.addRoundRect(R, 22f * k, 22f * k, Path.Direction.CW);
        R.set(cx - 44f * k, cy - 40f * k, cx + 8f * k, cy + 6f * k);
        P.addOval(R, Path.Direction.CW);
        R.set(cx - 12f * k, cy - 52f * k, cx + 46f * k, cy + 4f * k);
        P.addOval(R, Path.Direction.CW);
        crayon(c, P, colour, Theme.darker(Theme.darker(colour)), seed, 40f, 9f, 1.4f, 3f);
    }

    /** Distant rolling hills with round trees; every shape repeats after one tile width so it scrolls without a seam. */
    private static void paintFar(Canvas c, int w, int h, int lv) {
        int[] pal = PAL[lv];
        P.reset();
        P.moveTo(0f, h);
        for (int x = 0; x <= w; x += 20) {
            double u = x * Math.PI * 2.0 / w;
            float y = h * 0.52f + 22f * (float) Math.sin(u + 0.6) + 12f * (float) Math.sin(u * 3 + 1.9);
            P.lineTo(x, y);
        }
        P.lineTo(w, h);
        P.close();
        crayon(c, P, pal[2], Theme.darker(pal[2]), 301L, 30f, 12f, 2.4f, 4f);
        Random r = new Random(40 + lv);
        for (int i = 0; i < 7; i++) {
            final float x = w * (i + 0.5f) / 7f + (r.nextFloat() - 0.5f) * 40f;
            final float rr = 20f + r.nextFloat() * 14f;
            final long seed = 310L + i;
            double u = x * Math.PI * 2.0 / w;
            final float y = h * 0.52f + 22f * (float) Math.sin(u + 0.6) + 12f * (float) Math.sin(u * 3 + 1.9);
            final int col = pal[3];
            wrap(c, w, x, (cv, px) -> {
                limb(cv, px, y + 6f, px, y - rr * 1.2f, 0f, 9f, LOG, DARK, seed);
                blob(cv, px, y - rr * 1.8f, rr * 1.2f, rr * 1.05f, col, Theme.darker(col), seed + 1, 50f);
            });
        }
    }

    /** Big trunks with leafy tops, hanging vines and bushes. */
    private static void paintMid(Canvas c, int w, int h, int lv) {
        int[] pal = PAL[lv];
        Random r = new Random(70 + lv);
        int n = 4;
        for (int i = 0; i < n; i++) {
            final float x = w * (i + 0.5f) / n + (r.nextFloat() - 0.5f) * 50f;
            final float tw = 26f + r.nextFloat() * 10f;
            final long seed = 400L + i * 10L;
            final float top = 30f + r.nextFloat() * 40f;
            final int trunk = pal[4], leaf = pal[5];
            wrap(c, w, x, (cv, px) -> {
                P.reset();
                P.moveTo(px - tw, h);
                P.quadTo(px - tw * 0.4f, h * 0.55f, px - tw * 0.55f, top + 40f);
                P.lineTo(px + tw * 0.55f, top + 40f);
                P.quadTo(px + tw * 0.4f, h * 0.55f, px + tw, h);
                P.close();
                crayon(cv, P, trunk, Theme.darker(trunk), seed, 80f, 7f, 1.8f, 4f);
                blob(cv, px - 34f, top + 28f, 54f, 38f, leaf, Theme.darker(leaf), seed + 1, 40f);
                blob(cv, px + 32f, top + 22f, 58f, 42f, leaf, Theme.darker(leaf), seed + 2, 130f);
                blob(cv, px, top, 52f, 40f, Theme.darker(leaf), Theme.darker(Theme.darker(leaf)), seed + 3, 60f);
                // a hanging vine
                limb(cv, px + 44f, top + 52f, px + 52f, top + 150f + (seed % 3) * 14f, 14f, 8f, 0xFF4E9B3E, 0xFF2E5E26, seed + 4);
            });
        }
        // bushes at the bottom
        for (int i = 0; i < 6; i++) {
            final float x = w * (i + 0.3f) / 6f + r.nextFloat() * 30f;
            final int leaf = pal[5];
            final long seed = 480L + i;
            wrap(c, w, x, (cv, px) -> {
                blob(cv, px, h - 16f, 46f, 26f, leaf, Theme.darker(leaf), seed, 50f);
                blob(cv, px + 30f, h - 12f, 34f, 20f, Theme.darker(leaf), Theme.darker(Theme.darker(leaf)), seed + 1, 120f);
            });
        }
    }

    /** The path the monkey runs on: grass on top (a wavy edge), dirt below with pebbles and flowers. */
    private static void paintGround(Canvas c, int w, int h, int lv) {
        int[] pal = PAL[lv];
        P.reset();
        P.moveTo(0f, h);
        P.lineTo(0f, 10f);
        for (int x = 0; x <= w; x += 20) {
            double u = x * Math.PI * 2.0 / w;
            P.lineTo(x, 8f + 4f * (float) Math.sin(u * 8));
        }
        P.lineTo(w, h);
        P.close();
        crayon(c, P, pal[7], Theme.darker(pal[7]), 501L, 20f, 11f, 1.2f, 3.4f);
        // grass band
        P.reset();
        P.moveTo(0f, 0f);
        for (int x = 0; x <= w; x += 20) {
            double u = x * Math.PI * 2.0 / w;
            P.lineTo(x, 8f + 4f * (float) Math.sin(u * 8));
        }
        P.lineTo(w, 0f);
        P.close();
        P.addRect(0f, 0f, w, 6f, Path.Direction.CW);
        crayon(c, P, pal[6], Theme.darker(pal[6]), 502L, 60f, 6f, 1.0f, 3.4f);
        Random r = new Random(90 + lv);
        for (int i = 0; i < 18; i++) {
            float x = r.nextFloat() * w, y = 40f + r.nextFloat() * (h - 56f);
            float rx = 7f + r.nextFloat() * 8f;
            blob(c, x, y, rx, rx * 0.6f, 0xFFD9C3A0, Theme.darker(pal[7]), 510L + i, 40f);
        }
        // a few fallen leaves
        for (int i = 0; i < 6; i++) {
            float x = (i + 0.5f) * w / 6f + r.nextFloat() * 30f, y = 80f + r.nextFloat() * 40f;
            leaf(c, x, y, 26f, 6f, i % 2 == 0 ? 0xFFE8A64C : 0xFFD9604C, 530L + i);
        }
    }

    /** Foreground grass blades and tiny flowers along the bottom edge. */
    private static void paintTufts(Canvas c, int w, int h) {
        Random r = new Random(5);
        for (int i = 0; i < 16; i++) {
            final float x = (i + 0.5f) * w / 16f + (r.nextFloat() - 0.5f) * 30f;
            final int col = (i % 3 == 0) ? 0xFF3E8C3A : 0xFF5FB34F;
            final long seed = 600L + i;
            wrap(c, w, x, (cv, px) -> {
                leaf(cv, px, h, -14f, -(26f + (seed % 4) * 6f), col, seed);
                leaf(cv, px, h, 2f, -(36f + (seed % 3) * 6f), col, seed + 20);
                leaf(cv, px, h, 16f, -(24f + (seed % 5) * 4f), col, seed + 40);
            });
        }
        for (int i = 0; i < 5; i++) {
            final float x = (i + 0.3f) * w / 5f + r.nextFloat() * 40f;
            final int col = i % 2 == 0 ? 0xFFFF7A8A : 0xFFFFD84D;
            wrap(c, w, x, (cv, px) -> {
                S.setColor(0xFF3E8C3A);
                S.setStrokeWidth(3f);
                cv.drawLine(px, h, px, h - 30f, S);
                F.setStyle(Paint.Style.FILL);
                F.setColor(col);
                cv.drawCircle(px, h - 34f, 8f, F);
                F.setColor(0xFFFFFFFF);
                cv.drawCircle(px, h - 34f, 3f, F);
            });
        }
    }
}
