package com.wiggleplay.app.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Draws the shared "how to play" person (T-27): a chunky blue figure with a dark outline, seen from the front, in the
 * style of Angga's pose sheet. It has a real skeleton ({@link HowToRig}), so shoulders, elbows, knees, hips and the lean
 * move like joints when a {@link HowToPose} is blended with another. One instance owns its paints and scratch objects;
 * nothing is allocated per frame.
 */
public final class HowToFigure {

    /** The kid blue used for the figure. */
    public static final int BLUE = 0xFF29B6F6;
    /** The dark outline colour. */
    public static final int INK = 0xFF2B3A4A;

    private static final float LIMB = 0.075f, LEG = 0.09f, TORSO_W = 0.09f, OUTLINE = 0.026f, HAND_R = 0.042f;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path torso = new Path();
    private final HowToRig rig = new HowToRig();
    private final HowToPose scratch = new HowToPose();
    private float cx, foot, h;

    /** Creates a figure. */
    public HowToFigure() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    /**
     * Draws a pose that is a blend of two poses.
     * @param c canvas
     * @param a the pose at k = 0
     * @param b the pose at k = 1
     * @param k 0..1
     * @param cxPx x of the figure's centre
     * @param footY y of the soles of the feet when standing
     * @param height height of the standing figure in pixels
     * @param colour fill colour (usually {@link #BLUE} or a player colour)
     */
    public void drawBetween(Canvas c, HowToPose a, HowToPose b, float k, float cxPx, float footY, float height, int colour) {
        scratch.lerp(a, b, k);
        draw(c, scratch, cxPx, footY, height, colour);
    }

    /**
     * Draws one pose.
     * @param c canvas
     * @param p the pose
     * @param cxPx x of the figure's centre
     * @param footY y of the soles of the feet when standing
     * @param height height of the standing figure in pixels
     * @param colour fill colour
     */
    public void draw(Canvas c, HowToPose p, float cxPx, float footY, float height, int colour) {
        rig.compute(p);
        cx = cxPx;
        foot = footY;
        h = height;
        pass(c, INK, OUTLINE * 2f);                 // the dark silhouette, a little fatter than the body
        pass(c, colour, 0f);                          // the coloured body
        if (rig.wheel > 0.01f) {
            wheel(c, p.wheelDeg);
            hand(c, rig.haLx, rig.haLy, rig.reachL, colour);
            hand(c, rig.haRx, rig.haRy, rig.reachR, colour);
        }
    }

    /** The whole figure in one colour; {@code grow} (fraction of the height) makes every shape fatter for the outline. */
    private void pass(Canvas c, int colour, float grow) {
        stroke.setColor(colour);
        fill.setColor(colour);
        // legs
        stroke.setStrokeWidth((LEG + grow) * h);
        line(c, rig.hpLx, rig.hpLy, rig.knLx, rig.knLy);
        line(c, rig.knLx, rig.knLy, rig.anLx, rig.anLy);
        line(c, rig.hpRx, rig.hpRy, rig.knRx, rig.knRy);
        line(c, rig.knRx, rig.knRy, rig.anRx, rig.anRy);
        // feet point outward
        stroke.setStrokeWidth((0.07f + grow) * h);
        line(c, rig.anLx, rig.anLy - 0.012f, rig.anLx - 0.05f, rig.anLy - 0.012f);
        line(c, rig.anRx, rig.anRy - 0.012f, rig.anRx + 0.05f, rig.anRy - 0.012f);
        // torso: the four corners joined, thick and round
        torso.rewind();
        torso.moveTo(sx(rig.shLx), sy(rig.shLy));
        torso.lineTo(sx(rig.shRx), sy(rig.shRy));
        torso.lineTo(sx(rig.hpRx), sy(rig.hpRy));
        torso.lineTo(sx(rig.hpLx), sy(rig.hpLy));
        torso.close();
        fill.setStyle(Paint.Style.FILL);
        c.drawPath(torso, fill);
        stroke.setStrokeWidth((TORSO_W + grow) * h);
        c.drawPath(torso, stroke);
        // arms
        stroke.setStrokeWidth((LIMB + grow) * h);
        line(c, rig.shLx, rig.shLy, rig.elLx, rig.elLy);
        line(c, rig.elLx, rig.elLy, rig.haLx, rig.haLy);
        line(c, rig.shRx, rig.shRy, rig.elRx, rig.elRy);
        line(c, rig.elRx, rig.elRy, rig.haRx, rig.haRy);
        // hands, a little bigger than the arm
        float hl = HAND_R * (1f + 0.5f * rig.reachL) + grow * 0.5f, hr = HAND_R * (1f + 0.5f * rig.reachR) + grow * 0.5f;
        c.drawCircle(sx(rig.haLx), sy(rig.haLy), hl * h, fill);
        c.drawCircle(sx(rig.haRx), sy(rig.haRy), hr * h, fill);
        // head
        c.drawCircle(sx(rig.headX), sy(rig.headY), (HowToRig.HEAD_R + grow * 0.5f) * h, fill);
    }

    /** A steering wheel: dark rim, three spokes and a hub, turned by {@code deg} (counter-clockwise on screen). */
    private void wheel(Canvas c, float deg) {
        float wx = sx(rig.wheelX), wy = sy(rig.wheelY), r = HowToRig.WHEEL_R * h;
        c.save();
        c.rotate(-deg, wx, wy);
        stroke.setColor(INK);
        stroke.setStrokeWidth(0.034f * h);
        c.drawCircle(wx, wy, r, stroke);
        stroke.setStrokeWidth(0.026f * h);
        c.drawLine(wx - r, wy, wx + r, wy, stroke);
        c.drawLine(wx, wy, wx, wy + r, stroke);
        fill.setColor(INK);
        c.drawCircle(wx, wy, 0.03f * h, fill);
        c.restore();
    }

    private void hand(Canvas c, float x, float y, float reach, int colour) {
        float r = HAND_R * (1f + 0.5f * reach) * h;
        fill.setColor(INK);
        c.drawCircle(sx(x), sy(y), r + OUTLINE * h, fill);
        fill.setColor(colour);
        c.drawCircle(sx(x), sy(y), r, fill);
    }

    private void line(Canvas c, float x0, float y0, float x1, float y1) {
        c.drawLine(sx(x0), sy(y0), sx(x1), sy(y1), stroke);
    }

    private float sx(float x) {
        return cx + x * h;
    }

    private float sy(float y) {
        return foot - y * h;
    }
}
