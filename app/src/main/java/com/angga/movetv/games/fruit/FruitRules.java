package com.angga.movetv.games.fruit;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.angga.movetv.core.Ease;
import com.angga.movetv.core.Stage;
import com.angga.movetv.games.common.Hud;
import com.angga.movetv.ui.Theme;

/**
 * The "how to play" pictures of Fruit Splash, made so a child can follow them without reading: a fast swipe cuts a
 * fruit in two, a resting hand cuts nothing, what each fruit is worth, and (on Medium and Hard) the grumpy coconut.
 * Three or four animated panels in a row. Nothing is allocated per frame.
 */
final class FruitRules implements Hud.RulesPainter {

    private static final float PW = 400f, GAP = 30f, TOP = 300f, PH = 340f;
    private static final float LOOP = 3.0f;

    private final int level;
    private final int panels;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private float t;

    /**
     * Creates the rules pictures.
     * @param level 1..3 (the coconut panel shows from Medium up, with its penalty on Hard)
     */
    FruitRules(int level) {
        this.level = level;
        this.panels = level >= 2 ? 4 : 3;
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
        return "Wave your hand fast through the fruit!";
    }

    @Override
    public void draw(Canvas c) {
        float total = panels * PW + (panels - 1) * GAP;
        float x0 = (Stage.W - total) / 2f;
        for (int i = 0; i < panels; i++) {
            float px = x0 + i * (PW + GAP);
            panel(c, px);
            float cx = px + PW / 2f;
            switch (i) {
                case 0: swipe(c, cx); break;
                case 1: resting(c, cx); break;
                case 2: points(c, cx); break;
                default: coconut(c, cx); break;
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

    private void fruit(Canvas c, Bitmap b, float cx, float cy, float radius, float rot) {
        float half = FruitArt.SIZE / 2f * radius / FruitArt.BODY_R;
        c.save();
        c.translate(cx, cy);
        c.rotate(rot);
        r.set(-half, -half, half, half);
        c.drawBitmap(b, null, r, sprite);
        c.restore();
    }

    private void hand(Canvas c, float x, float y, int colour) {
        fill.setColor(colour);
        c.drawCircle(x, y, 30f, fill);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, 22f, fill);
        fill.setColor(colour);
        c.drawCircle(x, y, 12f, fill);
    }

    /** Panel 1: a fast swipe through a fruit cuts it in two. */
    private void swipe(Canvas c, float cx) {
        float cy = TOP + 150f, lt = t % LOOP;
        float pass = 0.9f;
        if (lt < pass) {
            fruit(c, FruitArt.sprite(0), cx, cy, 62f, 0f);
            float k = Ease.outCubic(lt / pass);
            float hx = cx - 150f + 300f * k, hy = cy + 24f - 48f * k;
            line.setColor(Theme.P1);
            line.setAlpha(150);
            line.setStrokeWidth(16f);
            c.drawLine(cx - 150f, cy + 24f, hx, hy, line);
            hand(c, hx, hy, Theme.P1);
        } else {
            float a = Math.min(1f, (lt - pass) / 1.2f), off = 10f + 46f * a;
            fruit(c, FruitArt.half(0, -1), cx - off, cy + 12f * a, 62f, -18f * a);
            fruit(c, FruitArt.half(0, 1), cx + off, cy + 12f * a, 62f, 18f * a);
            fill.setColor(FruitArt.FLESH[0]);
            fill.setAlpha((int) (200 * (1f - a)));
            for (int i = 0; i < 6; i++) {
                float ang = i * 1.05f;
                c.drawCircle(cx + (float) Math.cos(ang) * (30f + 60f * a), cy + (float) Math.sin(ang) * (30f + 60f * a), 9f, fill);
            }
            fill.setAlpha(255);
            Theme.drawCentered(c, "+1", cx, cy - 84f - 14f * a, 56f, Theme.P1);
        }
        Theme.drawCentered(c, "Swipe fast!", cx, TOP + PH - 36f, 44f, Theme.INK);
    }

    /** Panel 2: a hand that rests on a fruit cuts nothing. */
    private void resting(Canvas c, float cx) {
        float cy = TOP + 150f;
        fruit(c, FruitArt.sprite(2), cx, cy, 62f, 0f);
        hand(c, cx + 10f, cy + 6f, Theme.P1);
        float pulse = 1f + 0.08f * (float) Math.sin(t * 5f);
        float s = 74f * pulse;
        line.setColor(0xFFD64545);
        line.setAlpha(255);
        line.setStrokeWidth(18f);
        c.drawLine(cx - s, cy - s, cx + s, cy + s, line);
        c.drawLine(cx - s, cy + s, cx + s, cy - s, line);
        Theme.drawCentered(c, "Resting: no points", cx, TOP + PH - 36f, 40f, Theme.INK);
    }

    /** Panel 3: what the fruit is worth. */
    private void points(Canvas c, float cx) {
        float y1 = TOP + 80f, y2 = TOP + 190f;
        fruit(c, FruitArt.sprite(6), cx - 130f, y1, 36f, 0f);
        Theme.drawCentered(c, "+1", cx - 60f, y1 + 18f, 52f, Theme.INK);
        fruit(c, FruitArt.sprite(FruitLogic.STAR), cx + 40f, y1, 40f, 0f);
        Theme.drawCentered(c, "+5", cx + 120f, y1 + 18f, 52f, Theme.ORANGE);
        fruit(c, FruitArt.sprite(10), cx - 140f, y2, 30f, 0f);
        fruit(c, FruitArt.sprite(0), cx - 85f, y2, 30f, 0f);
        fruit(c, FruitArt.sprite(5), cx - 30f, y2, 30f, 0f);
        Theme.drawCentered(c, "fast", cx + 35f, y2 + 12f, 36f, Theme.INK);
        Theme.drawCentered(c, "+3", cx + 125f, y2 + 18f, 52f, Theme.ORANGE);
        // more points, more stars: three stars for slicing most of what flies by
        Bitmap stars = Theme.starsBadge(3);
        float sw = stars.getWidth() * 0.5f, sh = stars.getHeight() * 0.5f, sy = TOP + 262f;
        r.set(cx - sw / 2f, sy - sh / 2f, cx + sw / 2f, sy + sh / 2f);
        c.drawBitmap(stars, null, r, sprite);
        Theme.drawCentered(c, "Slice most: 3 stars!", cx, TOP + PH - 30f, 38f, Theme.INK);
    }

    /** Panel 4: the grumpy coconut. */
    private void coconut(Canvas c, float cx) {
        float cy = TOP + 140f, bob = (float) Math.sin(t * 4f) * 5f;
        fruit(c, FruitArt.sprite(FruitLogic.COCONUT), cx, cy + bob, 66f, 0f);
        String s = level >= 3 ? "-3" : "0";
        Theme.drawCentered(c, s, cx + 105f, cy - 62f, 72f, level >= 3 ? 0xFFD64545 : Theme.INK);
        Theme.drawCentered(c, "Skip the coconut!", cx, TOP + PH - 36f, 40f, Theme.INK);
    }
}
