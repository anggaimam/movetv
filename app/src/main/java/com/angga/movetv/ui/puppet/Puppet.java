package com.angga.movetv.ui.puppet;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

/**
 * One of Angga's two kids as a cut-out puppet: about 20 pre-rendered PNG parts (arms split at the elbow)
 * drawn with a pre-allocated matrix per part, moved by a {@link PuppetPose} from a {@link PuppetAnim}.
 * Kid A has dark hair and a green striped shirt; kid B is blond with a red striped shirt. The two kids are
 * independent characters: each {@code Puppet} has its own timers. The picture parts are loaded once for the
 * whole app by {@link #init(Context)} and shared. If they were never loaded, drawing does nothing.
 * Nothing is allocated per frame. Main thread only.
 */
public final class Puppet {

    /** Kid A (dark hair, green striped shirt). */
    public static final int KID_A = 0;
    /** Kid B (blond hair, red striped shirt). */
    public static final int KID_B = 1;
    /** Height of a standing kid in rig pixels (hair tuft to sole). */
    public static final float RIG_HEIGHT = 614f * 1.5f;

    private static final int MAX_PARTS = 24;
    private static final float PUPIL_MAX = 5f * 1.5f;   // rig pixels

    /** The shared pictures of one kid. */
    private static final class Rig {
        final int n;
        final String[] id = new String[MAX_PARTS];
        final Bitmap[] bmp = new Bitmap[MAX_PARTS];
        final int[] parent = new int[MAX_PARTS];      // index, or -1 for the root
        final float[] px = new float[MAX_PARTS], py = new float[MAX_PARTS];
        final float[] ox = new float[MAX_PARTS], oy = new float[MAX_PARTS];
        final int[] drawOrder = new int[MAX_PARTS];
        int shadow = -1, torso = -1, head = -1, pupils = -1, eyes = -1, lids = -1, closed = -1;
        int mSmile = -1, mOpen = -1, mClosed = -1;
        int upperL = -1, upperR = -1, foreL = -1, foreR = -1;

        Rig(int n) {
            this.n = n;
        }
    }

    private static final Rig[] RIGS = new Rig[2];
    private static boolean loaded;

    private final int kid;
    private final PuppetAnim anim;
    private final PuppetPose pose = new PuppetPose();
    private final Matrix base = new Matrix();
    private final Matrix[] world = new Matrix[MAX_PARTS];
    private final boolean[] done = new boolean[MAX_PARTS];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    /**
     * Loads the puppet pictures from the app's assets (call once at start; later calls do nothing).
     * @param ctx any context
     */
    public static synchronized void init(Context ctx) {
        if (loaded) return;
        try {
            AssetManager am = ctx.getAssets();
            JSONObject root = new JSONObject(readAll(am.open("mascots/rig.json")));
            JSONObject kids = root.getJSONObject("kids");
            String[] keys = {"a", "b"};
            for (int k = 0; k < 2; k++) {
                JSONArray parts = kids.getJSONObject(keys[k]).getJSONArray("parts");
                Rig r = new Rig(Math.min(MAX_PARTS, parts.length()));
                for (int i = 0; i < r.n; i++) {
                    JSONObject p = parts.getJSONObject(i);
                    r.id[i] = p.getString("id");
                    try (InputStream is = am.open(p.getString("file"))) {
                        r.bmp[i] = BitmapFactory.decodeStream(is);
                    }
                    r.px[i] = (float) p.getJSONArray("pivot").getDouble(0);
                    r.py[i] = (float) p.getJSONArray("pivot").getDouble(1);
                    r.ox[i] = (float) p.getJSONArray("offset").getDouble(0);
                    r.oy[i] = (float) p.getJSONArray("offset").getDouble(1);
                    r.drawOrder[p.getInt("order")] = i;
                }
                for (int i = 0; i < r.n; i++) {
                    String par = parts.getJSONObject(i).getString("parent");
                    r.parent[i] = -1;
                    for (int j = 0; j < r.n && !"root".equals(par); j++) if (r.id[j].equals(par)) r.parent[i] = j;
                    switch (r.id[i]) {
                        case "shadow": r.shadow = i; break;
                        case "torso": r.torso = i; break;
                        case "head": r.head = i; break;
                        case "pupils": r.pupils = i; break;
                        case "eyes": r.eyes = i; break;
                        case "lids": r.lids = i; break;
                        case "eyes_closed": r.closed = i; break;
                        case "mouth_smile": r.mSmile = i; break;
                        case "mouth_open": r.mOpen = i; break;
                        case "mouth_closed": r.mClosed = i; break;
                        case "upper_arm_l": r.upperL = i; break;
                        case "upper_arm_r": r.upperR = i; break;
                        case "fore_arm_l": r.foreL = i; break;
                        case "fore_arm_r": r.foreR = i; break;
                        default: break;
                    }
                }
                RIGS[k] = r;
            }
            loaded = true;
        } catch (Exception e) {
            Log.w("MoveTV-Puppet", "puppet pictures not loaded", e);
        }
    }

    private static String readAll(InputStream is) throws java.io.IOException {
        try (InputStream in = is) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        }
    }

    /**
     * Whether the pictures are loaded.
     * @return true if puppets can be drawn
     */
    public static boolean ready() {
        return loaded;
    }

    /**
     * Creates a puppet.
     * @param kid {@link #KID_A} or {@link #KID_B}
     * @param seed random seed for its blinks and idle motion (use a different one per puppet)
     */
    public Puppet(int kid, long seed) {
        this.kid = kid == KID_B ? KID_B : KID_A;
        this.anim = new PuppetAnim(seed);
        for (int i = 0; i < MAX_PARTS; i++) world[i] = new Matrix();
    }

    /** @return this puppet's animation (start waves, cheers, points; set the look target) */
    public PuppetAnim anim() {
        return anim;
    }

    /** @return which kid this is, {@link #KID_A} or {@link #KID_B} */
    public int kid() {
        return kid;
    }

    /**
     * Advances the animation.
     * @param dt seconds since the last frame
     */
    public void update(float dt) {
        anim.update(dt);
    }

    /**
     * Draws the kid standing on a ground point. Drawing below the bottom of the screen shows only head and
     * shoulders (the "peek" look).
     * @param c canvas in stage coordinates
     * @param footX ground point x (between the feet)
     * @param footY ground point y (the floor)
     * @param height height in stage pixels of the whole kid (hair tuft to sole)
     */
    public void draw(Canvas c, float footX, float footY, float height) {
        Rig r = RIGS[kid];
        if (r == null) return;
        anim.fill(pose);
        float s = height / RIG_HEIGHT;
        base.setTranslate(footX, footY);
        base.preScale(s, s);
        for (int i = 0; i < r.n; i++) done[i] = false;
        for (int i = 0; i < r.n; i++) compute(r, i);
        for (int k = 0; k < r.n; k++) {
            int i = r.drawOrder[k];
            if (i == r.lids) {
                if (pose.eyes == PuppetPose.EYES_OPEN) continue;
                drawPart(c, r, i, pose.eyes == PuppetPose.EYES_HALF);
            } else if (i == r.closed) {
                if (pose.eyes != PuppetPose.EYES_CLOSED) continue;
                drawPart(c, r, i, false);
            } else if (i == r.mSmile) {
                if (pose.mouth == PuppetPose.MOUTH_SMILE) drawPart(c, r, i, false);
            } else if (i == r.mOpen) {
                if (pose.mouth == PuppetPose.MOUTH_OPEN) drawPart(c, r, i, false);
            } else if (i == r.mClosed) {
                if (pose.mouth == PuppetPose.MOUTH_CLOSED) drawPart(c, r, i, false);
            } else {
                drawPart(c, r, i, false);
            }
        }
    }

    private void compute(Rig r, int i) {
        if (done[i]) return;
        Matrix m = world[i];
        int p = r.parent[i];
        if (p >= 0) {
            compute(r, p);
            m.set(world[p]);
        } else {
            m.set(base);
            if (i == r.shadow) {
                m.preTranslate(pose.rootDx * 1.5f, 0f);
            } else {
                m.preTranslate(pose.rootDx * 1.5f, pose.rootDy * 1.5f);
            }
        }
        m.preTranslate(r.ox[i], r.oy[i]);
        if (i == r.shadow) {
            m.preScale(pose.shadowScale, pose.shadowScale);
        } else if (i == r.torso) {
            m.preRotate(pose.torsoRot);
            m.preScale(1f, 1f + pose.breath);
        } else if (i == r.head) {
            m.preTranslate(0f, pose.headDy * 1.5f);
            m.preRotate(pose.headRot);
        } else if (i == r.upperL) {
            m.preRotate(pose.armLift[PuppetPose.LEFT]);
        } else if (i == r.upperR) {
            m.preRotate(-pose.armLift[PuppetPose.RIGHT]);
        } else if (i == r.foreL) {
            m.preRotate(pose.foreBend[PuppetPose.LEFT]);
        } else if (i == r.foreR) {
            m.preRotate(-pose.foreBend[PuppetPose.RIGHT]);
        } else if (i == r.pupils) {
            m.preTranslate(pose.pupilX * PUPIL_MAX, pose.pupilY * PUPIL_MAX);
        }
        done[i] = true;
    }

    private void drawPart(Canvas c, Rig r, int i, boolean topHalf) {
        Bitmap b = r.bmp[i];
        if (b == null) return;
        c.save();
        c.concat(world[i]);
        if (topHalf) {
            src.set(0, 0, b.getWidth(), b.getHeight() / 2);
            dst.set(-r.px[i], -r.py[i], -r.px[i] + b.getWidth(), -r.py[i] + b.getHeight() / 2f);
            c.drawBitmap(b, src, dst, paint);
        } else {
            c.drawBitmap(b, -r.px[i], -r.py[i], paint);
        }
        c.restore();
    }
}
