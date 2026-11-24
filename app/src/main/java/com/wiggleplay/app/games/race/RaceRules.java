package com.wiggleplay.app.games.race;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.ui.HowToFigure;
import com.wiggleplay.app.ui.HowToPose;
import com.wiggleplay.app.ui.Theme;

/**
 * The "how to play" pictures of Turbo Trucks, readable without words, five animated panels:
 * the chosen steering gesture (arms out like a plane and tilted, a body lean, or turning a wheel, T-23) = the truck
 * turns; a jump = rocket flames (boost, filled by blue nitro cans); a crouch = the truck slows; the red magnet pulls
 * coins in; and a coin (+1) and a golden star (+5). No allocation per frame.
 */
final class RaceRules implements Hud.RulesPainter {

    private static final float PW = 330f, GAP = 24f, TOP = 470f, PH = 340f, LOOP = 3.2f;
    private static final int KID = 0xFF29B6F6;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path arrow = new Path();
    private final HowToFigure figure = new HowToFigure();
    private final HowToPose pose = new HowToPose();
    private static final float[] MSX = {-120f, 120f, -110f, 115f}, MSY = {55f, 85f, 190f, 175f};
    private float t;
    private int mode = RaceArt.MODE_PLANE;
    private static final String[] SPEECH = {
        "Hold your arms out like a plane and tilt to steer. Jump for a rocket boost, crouch to slow down, grab blue nitro cans to fill the rocket, and the red magnet to pull in coins!",
        "Lean your body left and right to steer. Jump for a rocket boost, crouch to slow down, grab blue nitro cans to fill the rocket, and the red magnet to pull in coins!",
        "Hold an invisible steering wheel in front of you and turn it. Jump for a rocket boost, crouch to slow down, grab blue nitro cans to fill the rocket, and the red magnet to pull in coins!"};

    /**
     * Picks the steering picture of panel 1.
     * @param m RaceArt.MODE_PLANE, MODE_LEAN or MODE_WHEEL
     */
    void setMode(int m) {
        mode = Math.max(0, Math.min(2, m));
    }

    RaceRules() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
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
        return SPEECH[mode];
    }

    @Override
    public void draw(Canvas c) {
        float total = 5 * PW + 4 * GAP;
        float x0 = (Stage.W - total) / 2f;
        for (int i = 0; i < 5; i++) {
            float px = x0 + i * (PW + GAP);
            fill.setColor(0x33000000);
            r.set(px + 6f, TOP + 8f, px + PW + 6f, TOP + PH + 8f);
            c.drawRoundRect(r, 36f, 36f, fill);
            fill.setColor(0xFFFFFFFF);
            r.set(px, TOP, px + PW, TOP + PH);
            c.drawRoundRect(r, 36f, 36f, fill);
            fill.setColor(i == 4 ? 0xFFFFF2C9 : 0xFFE3F3FA);
            r.set(px + 12f, TOP + 12f, px + PW - 12f, TOP + PH - 12f);
            c.drawRoundRect(r, 28f, 28f, fill);
            float cx = px + PW / 2f;
            switch (i) {
                case 0: steer(c, cx); break;
                case 1: jump(c, cx); break;
                case 2: crouch(c, cx); break;
                case 3: magnet(c, cx); break;
                default: coins(c, cx); break;
            }
        }
    }

    /** The figure steers (leans, or turns a wheel), right then left; the truck below swings the same way (T-27). */
    private void steer(Canvas c, float cx) {
        float lt = t % LOOP;
        float s = (float) Math.sin(lt / LOOP * 2f * Math.PI);   // +1 = to the screen's right
        if (mode == RaceArt.MODE_WHEEL) {
            pose.set(HowToPose.WHEEL);
            pose.wheelDeg = -55f * s;                          // clockwise turns right
            figure.draw(c, pose, cx, TOP + 205f, 190f, KID);
        } else if (mode == RaceArt.MODE_LEAN) {
            figure.drawBetween(c, HowToPose.LEAN_LEFT, HowToPose.LEAN_RIGHT, 0.5f + 0.5f * s, cx, TOP + 205f, 190f, KID);
        } else {
            RaceArt.kid(c, cx, TOP + 190f, 190f, 1f, -24f * s, 0f, KID);
        }
        RaceArt.truckSide(c, cx + 40f * s, TOP + 262f, 0.9f, lt * 6f);
        line.setColor(RaceArt.ORANGE);
        line.setStrokeWidth(10f);
        float dir = s > 0f ? 1f : -1f;
        float a = Math.min(1f, Math.abs(s) * 2.4f);
        line.setAlpha((int) (255 * a));
        float ax = cx + dir * 120f;
        c.drawLine(ax - dir * 28f, TOP + 308f, ax + dir * 28f, TOP + 308f, line);
        c.drawLine(ax + dir * 28f, TOP + 308f, ax + dir * 8f, TOP + 292f, line);
        c.drawLine(ax + dir * 28f, TOP + 308f, ax + dir * 8f, TOP + 324f, line);
        line.setAlpha(255);
    }

    /** Kid jumps; a rocket with a flame appears when the kid is in the air. */
    private void jump(Canvas c, float cx) {
        float lt = t % LOOP;
        float p = lt < 1.6f ? lt / 1.6f : 0f;
        float lift = (float) Math.sin(p * Math.PI);
        if (lift < 0f) lift = 0f;
        figure.drawBetween(c, HowToPose.STAND, HowToPose.JUMP, lift, cx - 60f, TOP + 300f - 40f * lift, 170f, KID);
        boolean up = lift > 0.25f;
        // two blue nitro cans fill the rocket (Medium and Hard): shown top left
        RaceArt.can(c, cx - 100f, TOP + 62f, 58f);
        RaceArt.can(c, cx - 58f, TOP + 62f, 58f);
        RaceArt.rocket(c, cx + 85f, TOP + 150f, 130f, up ? 0xFFE0605A : 0xFFB8C3CC, up ? 0.6f + 0.4f * lift : 0f, t);
        if (up) {
            line.setColor(0xFFFFFFFF);
            line.setStrokeWidth(7f);
            for (int i = 0; i < 3; i++) {
                float y = TOP + 120f + i * 38f, x = cx + 10f - ((t * 3f + i * 0.3f) % 1f) * 30f;
                c.drawLine(x, y, x + 22f, y, line);
            }
        }
    }

    /** Kid crouches; the truck's speed lines get short. */
    private void crouch(Canvas c, float cx) {
        float lt = t % LOOP;
        float k = lt < 0.6f ? lt / 0.6f : (lt < 2.2f ? 1f : Math.max(0f, 1f - (lt - 2.2f) / 0.6f));
        figure.drawBetween(c, HowToPose.STAND, HowToPose.SQUAT, k, cx - 60f, TOP + 290f, 170f, KID);
        RaceArt.truckSide(c, cx + 75f, TOP + 215f, 0.62f, lt * (k > 0.5f ? 2f : 9f));
        line.setColor(0xFF9AA5AD);
        line.setStrokeWidth(7f);
        float len = 46f * (1f - 0.75f * k);
        for (int i = 0; i < 3; i++) c.drawLine(cx + 5f - len, TOP + 150f + i * 26f, cx + 5f, TOP + 150f + i * 26f, line);
        // a down arrow for "slow"
        arrow.rewind();
        arrow.moveTo(cx + 50f, TOP + 70f);
        arrow.lineTo(cx + 100f, TOP + 70f);
        arrow.lineTo(cx + 75f, TOP + 100f);
        arrow.close();
        fill.setColor(k > 0.5f ? RaceArt.ORANGE : 0xFFC7CFD6);
        c.drawPath(arrow, fill);
    }

    /** The magnet hovers over the truck and coins from the sides slide up to it. */
    private void magnet(Canvas c, float cx) {
        float bob = (float) Math.sin(t * 3f) * 5f;
        RaceArt.truckSide(c, cx, TOP + 285f, 0.8f, t * 6f);
        final float my = TOP + 130f + bob;
        for (int i = 0; i < 4; i++) {
            float k = (t * 0.7f + i * 0.25f) % 1f;
            float e = k * k;                                   // starts slowly, then slides in
            float x = cx + MSX[i] * (1f - e), y = TOP + MSY[i] + (my - TOP - MSY[i]) * e;
            if (k < 0.92f) RaceArt.coin(c, x, y, 24f * (1f - 0.35f * e));
        }
        RaceArt.magnet(c, cx, my, 110f);
    }

    /** A coin is worth 1, the golden star 5. */
    private void coins(Canvas c, float cx) {
        float bob = (float) Math.sin(t * 3f) * 6f;
        RaceArt.coin(c, cx - 70f, TOP + 150f + bob, 50f);
        Theme.drawCentered(c, "+1", cx + 62f, TOP + 168f + bob, 74f, 0xFFB57F00);
        android.graphics.Bitmap star = Theme.bigStar(true);
        float d = 56f + 4f * (float) Math.sin(t * 4f);
        r.set(cx - 70f - d, TOP + 255f - d, cx - 70f + d, TOP + 255f + d);
        c.drawBitmap(star, null, r, null);
        Theme.drawCentered(c, "+5", cx + 62f, TOP + 275f, 74f, 0xFFB57F00);
    }
}
