package com.angga.movetv.games.common;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.angga.movetv.core.Stage;
import com.angga.movetv.pose.Kp;
import com.angga.movetv.pose.Keypoint;
import com.angga.movetv.pose.PlayerPose;

/**
 * A soft picture of a player made of rounded limbs between the body points (torso, upper and lower arms, a head
 * circle, and legs when they are seen well), shown instead of the camera picture so a child can still see where
 * their body is. The whole figure is one colour at 30% with a lighter 6 px edge; it is drawn into one layer so
 * overlaps at the shoulders and elbows do not get darker. The hands are bright circles. Nothing is allocated per frame.
 */
public final class BodyShadow {

    private static final float MIN_S = 0.3f, LEG_S = 0.4f;
    private static final int FIGURE_ALPHA = 77; // 30%

    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Creates the painter. */
    public BodyShadow() {
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeCap(Paint.Cap.ROUND);
        body.setStyle(Paint.Style.STROKE);
        body.setStrokeCap(Paint.Cap.ROUND);
        hand.setStyle(Paint.Style.FILL);
    }

    private static boolean ok(Keypoint k, float min) {
        return k.s >= min;
    }

    /**
     * Lighter version of a colour for the edge.
     * @param colour a colour
     * @return the colour mixed 55% with white
     */
    static int lighter(int colour) {
        int r = (colour >> 16) & 0xff, g = (colour >> 8) & 0xff, b = colour & 0xff;
        r += (255 - r) * 55 / 100;
        g += (255 - g) * 55 / 100;
        b += (255 - b) * 55 / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Draws one player's shadow.
     * @param c canvas in stage coordinates
     * @param pp the player's pose (nothing is drawn unless the player is present)
     * @param colour the player's colour (opaque ARGB)
     */
    public void draw(Canvas c, PlayerPose pp, int colour) {
        if (pp == null || !pp.present) return;
        Keypoint[] k = pp.kps;
        Keypoint ls = k[Kp.L_SHOULDER], rs = k[Kp.R_SHOULDER];
        if (!ok(ls, MIN_S) || !ok(rs, MIN_S)) {
            drawHands(c, k, colour);
            return;
        }
        float sd = (float) Math.hypot(ls.x - rs.x, ls.y - rs.y);
        sd = Math.max(80f, Math.min(420f, sd));
        float smx = (ls.x + rs.x) / 2f, smy = (ls.y + rs.y) / 2f;
        Keypoint lh = k[Kp.L_HIP], rh = k[Kp.R_HIP];
        float hmx, hmy;
        if (ok(lh, MIN_S) && ok(rh, MIN_S)) {
            hmx = (lh.x + rh.x) / 2f;
            hmy = (lh.y + rh.y) / 2f;
        } else {
            hmx = smx;
            hmy = smy + sd * 1.5f;
        }
        float top = Math.min(smy, k[Kp.NOSE].s >= MIN_S ? k[Kp.NOSE].y : smy) - sd * 0.7f;
        float minX = Math.min(ls.x, rs.x) - sd, maxX = Math.max(ls.x, rs.x) + sd;
        float minY = Math.max(0f, top), maxY = Math.max(hmy, smy) + sd * 1.2f;
        for (int i = Kp.L_ELBOW; i <= Kp.R_WRIST; i++) {
            if (!ok(k[i], MIN_S)) continue;
            minX = Math.min(minX, k[i].x - sd * 0.5f);
            maxX = Math.max(maxX, k[i].x + sd * 0.5f);
            minY = Math.min(minY, k[i].y - sd * 0.5f);
            maxY = Math.max(maxY, k[i].y + sd * 0.5f);
        }
        boolean legs = ok(k[Kp.L_KNEE], LEG_S) || ok(k[Kp.R_KNEE], LEG_S);
        if (legs) maxY = Math.max(maxY, Stage.H);
        minX = Math.max(0f, minX);
        maxX = Math.min(Stage.W, maxX);
        maxY = Math.min(Stage.H, maxY);
        if (maxX <= minX || maxY <= minY) return;

        int light = lighter(colour);
        edge.setColor(light);
        body.setColor(colour);
        int saved = c.saveLayerAlpha(minX, minY, maxX, maxY, FIGURE_ALPHA);
        // edges first, then the fill, so the 6 px outline only shows on the outside of the whole figure
        figure(c, k, sd, smx, smy, hmx, hmy, legs, true);
        figure(c, k, sd, smx, smy, hmx, hmy, legs, false);
        c.restoreToCount(saved);
        drawHands(c, k, colour);
    }

    private void figure(Canvas c, Keypoint[] k, float sd, float smx, float smy, float hmx, float hmy,
                        boolean legs, boolean outline) {
        Paint p = outline ? edge : body;
        float add = outline ? 12f : 0f;
        // torso
        p.setStrokeWidth(sd * 0.85f + add);
        c.drawLine(smx, smy + sd * 0.1f, hmx, hmy, p);
        // head
        Keypoint nose = k[Kp.NOSE];
        if (nose.s >= MIN_S) {
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(nose.x, nose.y, sd * 0.38f + add / 2f, p);
            p.setStyle(Paint.Style.STROKE);
        }
        limb(c, p, k[Kp.L_SHOULDER], k[Kp.L_ELBOW], sd * 0.30f + add, MIN_S);
        limb(c, p, k[Kp.L_ELBOW], k[Kp.L_WRIST], sd * 0.25f + add, MIN_S);
        limb(c, p, k[Kp.R_SHOULDER], k[Kp.R_ELBOW], sd * 0.30f + add, MIN_S);
        limb(c, p, k[Kp.R_ELBOW], k[Kp.R_WRIST], sd * 0.25f + add, MIN_S);
        if (legs) {
            limb(c, p, k[Kp.L_HIP], k[Kp.L_KNEE], sd * 0.36f + add, LEG_S);
            limb(c, p, k[Kp.L_KNEE], k[Kp.L_ANKLE], sd * 0.28f + add, LEG_S);
            limb(c, p, k[Kp.R_HIP], k[Kp.R_KNEE], sd * 0.36f + add, LEG_S);
            limb(c, p, k[Kp.R_KNEE], k[Kp.R_ANKLE], sd * 0.28f + add, LEG_S);
        }
    }

    private static void limb(Canvas c, Paint p, Keypoint a, Keypoint b, float width, float min) {
        if (a.s < min || b.s < min) return;
        p.setStrokeWidth(width);
        c.drawLine(a.x, a.y, b.x, b.y, p);
    }

    private void drawHands(Canvas c, Keypoint[] k, int colour) {
        for (int i = 0; i < 2; i++) {
            Keypoint w = k[i == 0 ? Kp.L_WRIST : Kp.R_WRIST];
            if (w.s < MIN_S) continue;
            hand.setColor(0xFFFFFFFF);
            c.drawCircle(w.x, w.y, 30f, hand);
            hand.setColor(colour);
            c.drawCircle(w.x, w.y, 20f, hand);
        }
    }
}
