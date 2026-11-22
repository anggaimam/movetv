package com.wiggleplay.app.games.sky;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.ui.Theme;

/**
 * The "how to fly" pictures of Sky Glider, made so a child can follow them without reading: lean your shoulders to
 * steer, raise or lower your arms to climb or dive, fly through the rings. Three animated panels in a row.
 * {@link #drawDemo} is the same little kid, shown for three seconds before each round. Nothing is allocated per frame.
 */
final class SkyRules implements Hud.RulesPainter {

    private static final float PW = 540f, GAP = 40f, TOP = 470f, PH = 340f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private float t;

    SkyRules() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * Advances the animation.
     * @param dt seconds
     */
    void update(float dt) {
        t += dt;
    }

    @Override
    public String speech() {
        return "Hold your arms out like wings. Lean to turn, and lift your arms to fly up!";
    }

    @Override
    public void draw(Canvas c) {
        float x0 = (Stage.W - (3 * PW + 2 * GAP)) / 2f;
        for (int i = 0; i < 3; i++) {
            float px = x0 + i * (PW + GAP), cx = px + PW / 2f;
            panel(c, px);
            switch (i) {
                case 0: steer(c, cx); break;
                case 1: climb(c, cx); break;
                default: rings(c, cx); break;
            }
        }
    }

    private void panel(Canvas c, float px) {
        fill.setColor(0xFFD9CDB8);
        r.set(px + 6f, TOP + 8f, px + PW + 6f, TOP + PH + 8f);
        c.drawRoundRect(r, 36f, 36f, fill);
        fill.setColor(0xF2FFFDF7);
        r.set(px, TOP, px + PW, TOP + PH);
        c.drawRoundRect(r, 36f, 36f, fill);
    }

    /**
     * A little kid from the front: head, body, two arms.
     * @param tilt lean in degrees (positive leans to the right of the picture)
     * @param armDeg both arms' angle above horizontal (negative = down); the arms go out to the sides
     */
    private void kid(Canvas c, float cx, float cy, float scale, float tilt, float armDeg, int colour) {
        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);
        c.rotate(tilt, 0f, 130f);
        line.setColor(colour);
        line.setStrokeWidth(24f);
        double a = Math.toRadians(armDeg);
        float ax = (float) Math.cos(a) * 118f, ay = -(float) Math.sin(a) * 118f;
        c.drawLine(-48f, 4f, -48f - ax, 4f + ay, line);
        c.drawLine(48f, 4f, 48f + ax, 4f + ay, line);
        fill.setColor(colour);
        r.set(-50f, -8f, 50f, 150f);
        c.drawRoundRect(r, 40f, 40f, fill);
        fill.setColor(0xFFF1D3B3);
        c.drawCircle(0f, -52f, 38f, fill);
        fill.setColor(Theme.INK);
        c.drawCircle(-13f, -56f, 5f, fill);
        c.drawCircle(13f, -56f, 5f, fill);
        c.restore();
    }

    private int planeType, planeTheme;

    /**
     * Chooses the plane shown in the pictures.
     * @param type PlaneModel plane kind
     * @param theme colour theme number
     */
    void setPlane(int type, int theme) {
        planeType = type;
        planeTheme = theme;
    }

    private void plane(Canvas c, float cx, float cy, float scale, float steer, float climb) {
        PlaneDraw.shared().draw(c, planeType, planeTheme, PlaneDraw.rollFor(steer), PlaneDraw.yawFor(steer),
                PlaneDraw.elevFor(climb), cx, cy, 88f * scale);
    }

    private void arrow(Canvas c, float cx, float cy, float dir, float len, int colour) {
        line.setColor(colour);
        line.setStrokeWidth(14f);
        float hx = cx + dir * len, h = 22f;
        c.drawLine(cx, cy, hx, cy, line);
        c.drawLine(hx, cy, hx - dir * h, cy - h, line);
        c.drawLine(hx, cy, hx - dir * h, cy + h, line);
    }

    private void arrowV(Canvas c, float cx, float cy, float dir, float len, int colour) {
        line.setColor(colour);
        line.setStrokeWidth(14f);
        float hy = cy - dir * len, h = 22f;
        c.drawLine(cx, cy, cx, hy, line);
        c.drawLine(cx, hy, cx - h, hy + dir * h, line);
        c.drawLine(cx, hy, cx + h, hy + dir * h, line);
    }

    /** Panel 1: leaning to the right turns the plane right. */
    private void steer(Canvas c, float cx) {
        float s = (float) Math.sin(t * 1.5f);
        kid(c, cx - 130f, TOP + 110f, 0.78f, s * 16f, 0f, Theme.P1);
        plane(c, cx + 140f, TOP + 100f, 0.55f, s, 0f);
        arrow(c, cx + 140f, TOP + 190f, s >= 0 ? 1f : -1f, 60f, Theme.ORANGE);
        Theme.drawCentered(c, "Lean to steer", cx, TOP + PH - 36f, 44f, Theme.INK);
    }

    /** Panel 2: arms up climbs, arms down dives. */
    private void climb(Canvas c, float cx) {
        float s = (float) Math.sin(t * 1.5f);
        kid(c, cx - 130f, TOP + 110f, 0.78f, 0f, s * 45f, Theme.P1);
        float py = TOP + 110f - s * 36f;
        plane(c, cx + 140f, py, 0.55f, 0f, s);
        arrowV(c, cx + 140f, TOP + 220f, s >= 0 ? 1f : -1f, 50f, Theme.ORANGE);
        Theme.drawCentered(c, "Arms up or down", cx, TOP + PH - 36f, 44f, Theme.INK);
    }

    /** Panel 3: fly through rings and pick up coins. */
    private void rings(Canvas c, float cx) {
        float k = (t % 3f) / 3f;
        float rs = 100f;
        r.set(cx - rs, TOP + 30f, cx + rs, TOP + 30f + rs * 2f);
        c.drawBitmap(SkyArt.ring(), null, r, sprite);
        float py = TOP + 200f - k * 70f, sc = 0.42f - 0.12f * k;
        plane(c, cx, py, sc, 0f, 0f);
        if (k > 0.55f) Theme.drawCentered(c, "+3", cx + 120f, TOP + 70f, 56f, Theme.ORANGE);
        r.set(cx - 215f, TOP + 110f, cx - 155f, TOP + 170f);
        c.drawBitmap(SkyArt.coin(), null, r, sprite);
        Theme.drawCentered(c, "+1", cx - 185f, TOP + 215f, 40f, Theme.INK);
        r.set(cx + 150f, TOP + 120f, cx + 220f, TOP + 190f);
        c.drawBitmap(SkyArt.star(), null, r, sprite);
        Theme.drawCentered(c, "+5", cx + 185f, TOP + 232f, 40f, Theme.ORANGE);
        Theme.drawCentered(c, "Fly through rings", cx, TOP + PH - 36f, 44f, Theme.INK);
    }

    /**
     * The three-second demo shown beside the countdown before a round: the kid steers, then climbs.
     * @param c canvas
     * @param cx centre x of the demo card
     * @param cy centre y of the demo card
     * @param time seconds since the countdown began
     */
    void drawDemo(Canvas c, float cx, float cy, float time) {
        fill.setColor(0xCCF3EBDD);
        r.set(cx - 230f, cy - 230f, cx + 230f, cy + 230f);
        c.drawRoundRect(r, 40f, 40f, fill);
        float tilt = 0f, arm = 0f;
        String s;
        if (time < 1.1f) {
            tilt = (float) Math.sin(time / 1.1f * Math.PI * 2f) * 17f;
            s = "Lean to turn";
        } else if (time < 2.4f) {
            arm = (float) Math.sin((time - 1.1f) / 1.3f * Math.PI * 2f) * 45f;
            s = "Arms up: climb";
        } else {
            s = "Fly through rings!";
        }
        kid(c, cx, cy - 20f, 1.15f, tilt, arm, Theme.P1);
        Theme.drawCentered(c, s, cx, cy + 190f, 44f, Theme.INK);
    }
}
