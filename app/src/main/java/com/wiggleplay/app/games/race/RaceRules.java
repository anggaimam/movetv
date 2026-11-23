package com.wiggleplay.app.games.race;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.ui.Theme;

/**
 * The "how to play" pictures of Turbo Trucks, readable without words, four animated panels:
 * the chosen steering gesture (arms out like a plane and tilted, a body lean, or turning a wheel, T-23) = the truck
 * turns; a jump = rocket flames (boost); a crouch = the truck slows;
 * and a coin (+1) and a golden star (+5). No allocation per frame.
 */
final class RaceRules implements Hud.RulesPainter {

    private static final float PW = 400f, GAP = 30f, TOP = 470f, PH = 340f, LOOP = 3.2f;
    private static final int KID = 0xFF29B6F6;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path arrow = new Path();
    private float t;
    private int mode = RaceArt.MODE_PLANE;
    private static final String[] SPEECH = {
        "Hold your arms out like a plane and tilt to steer. Jump for a rocket boost, and crouch to slow down!",
        "Lean your body left and right to steer. Jump for a rocket boost, and crouch to slow down!",
        "Hold an invisible steering wheel in front of you and turn it. Jump for a rocket boost, and crouch to slow down!"};

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
        float total = 4 * PW + 3 * GAP;
        float x0 = (Stage.W - total) / 2f;
        for (int i = 0; i < 4; i++) {
            float px = x0 + i * (PW + GAP);
            fill.setColor(0x33000000);
            r.set(px + 6f, TOP + 8f, px + PW + 6f, TOP + PH + 8f);
            c.drawRoundRect(r, 36f, 36f, fill);
            fill.setColor(0xFFFFFFFF);
            r.set(px, TOP, px + PW, TOP + PH);
            c.drawRoundRect(r, 36f, 36f, fill);
            fill.setColor(i == 3 ? 0xFFFFF2C9 : 0xFFE3F3FA);
            r.set(px + 12f, TOP + 12f, px + PW - 12f, TOP + PH - 12f);
            c.drawRoundRect(r, 28f, 28f, fill);
            float cx = px + PW / 2f;
            switch (i) {
                case 0: steer(c, cx); break;
                case 1: jump(c, cx); break;
                case 2: crouch(c, cx); break;
                default: coins(c, cx); break;
            }
        }
    }

    /** Kid steering (arms out, leaning or turning a wheel), left then right; the truck below swings the same way. */
    private void steer(Canvas c, float cx) {
        float lt = t % LOOP;
        float tilt = 24f * (float) Math.sin(lt / LOOP * 2f * Math.PI);
        if (mode == RaceArt.MODE_LEAN) RaceArt.kidLean(c, cx, TOP + 190f, 170f, tilt * 0.6f, KID);
        else if (mode == RaceArt.MODE_WHEEL) RaceArt.kidWheel(c, cx, TOP + 190f, 180f, tilt * 1.6f, KID);
        else RaceArt.kid(c, cx, TOP + 190f, 190f, 1f, tilt, 0f, KID);
        float dx = -tilt * 2.2f; // left hand lower (tilt > 0) -> the truck goes left
        RaceArt.truckSide(c, cx + dx, TOP + 255f, 1.0f, lt * 6f);
        line.setColor(RaceArt.ORANGE);
        line.setStrokeWidth(10f);
        float dir = tilt > 0f ? -1f : 1f;
        float a = Math.min(1f, Math.abs(tilt) / 10f);
        line.setAlpha((int) (255 * a));
        float ax = cx + dir * 120f;
        c.drawLine(ax - dir * 28f, TOP + 300f, ax + dir * 28f, TOP + 300f, line);
        c.drawLine(ax + dir * 28f, TOP + 300f, ax + dir * 8f, TOP + 284f, line);
        c.drawLine(ax + dir * 28f, TOP + 300f, ax + dir * 8f, TOP + 316f, line);
        line.setAlpha(255);
    }

    /** Kid jumps; a rocket with a flame appears when the kid is in the air. */
    private void jump(Canvas c, float cx) {
        float lt = t % LOOP;
        float p = lt < 1.6f ? lt / 1.6f : 0f;
        float lift = (float) Math.sin(p * Math.PI);
        if (lift < 0f) lift = 0f;
        RaceArt.kid(c, cx - 80f, TOP + 285f - 70f * lift, 170f, 0f, 0f, 0f, KID);
        boolean up = lift > 0.25f;
        RaceArt.rocket(c, cx + 100f, TOP + 150f, 150f, up ? 0xFFE0605A : 0xFFB8C3CC, up ? 0.6f + 0.4f * lift : 0f, t);
        if (up) {
            line.setColor(0xFFFFFFFF);
            line.setStrokeWidth(7f);
            for (int i = 0; i < 3; i++) {
                float y = TOP + 120f + i * 38f, x = cx + 20f - ((t * 3f + i * 0.3f) % 1f) * 30f;
                c.drawLine(x, y, x + 22f, y, line);
            }
        }
    }

    /** Kid crouches; the truck's speed lines get short. */
    private void crouch(Canvas c, float cx) {
        float lt = t % LOOP;
        float k = lt < 0.6f ? lt / 0.6f : (lt < 2.2f ? 1f : Math.max(0f, 1f - (lt - 2.2f) / 0.6f));
        RaceArt.kid(c, cx - 80f, TOP + 285f, 170f, 0f, 0f, k, KID);
        RaceArt.truckSide(c, cx + 85f, TOP + 215f, 0.62f, lt * (k > 0.5f ? 2f : 9f));
        line.setColor(0xFF9AA5AD);
        line.setStrokeWidth(7f);
        float len = 46f * (1f - 0.75f * k);
        for (int i = 0; i < 3; i++) c.drawLine(cx + 10f - len, TOP + 150f + i * 26f, cx + 10f, TOP + 150f + i * 26f, line);
        // a down arrow for "slow"
        arrow.rewind();
        arrow.moveTo(cx + 60f, TOP + 70f);
        arrow.lineTo(cx + 110f, TOP + 70f);
        arrow.lineTo(cx + 85f, TOP + 100f);
        arrow.close();
        fill.setColor(k > 0.5f ? RaceArt.ORANGE : 0xFFC7CFD6);
        c.drawPath(arrow, fill);
    }

    /** A coin is worth 1, the golden star 5. */
    private void coins(Canvas c, float cx) {
        float bob = (float) Math.sin(t * 3f) * 6f;
        RaceArt.coin(c, cx - 85f, TOP + 150f + bob, 52f);
        Theme.drawCentered(c, "+1", cx + 70f, TOP + 168f + bob, 74f, 0xFFB57F00);
        android.graphics.Bitmap star = Theme.bigStar(true);
        float d = 56f + 4f * (float) Math.sin(t * 4f);
        r.set(cx - 85f - d, TOP + 255f - d, cx - 85f + d, TOP + 255f + d);
        c.drawBitmap(star, null, r, null);
        Theme.drawCentered(c, "+5", cx + 70f, TOP + 275f, 74f, 0xFFB57F00);
    }
}
