package com.wiggleplay.app.games.bubbles;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.wiggleplay.app.core.Ease;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.ui.Theme;

/**
 * The "how to play" pictures of Bubble Burst, readable without words: touch a bubble and it pops, bubbles that
 * fade cost nothing, blue is for the left hand and red for the right (Medium), and on Hard gold is worth 5,
 * purple needs both hands and the spiky grey ones are skipped. Two to four animated panels. No allocation per frame.
 */
final class BubbleRules implements Hud.RulesPainter {

    private static final float PW = 400f, GAP = 30f, TOP = 470f, PH = 340f, LOOP = 3.2f;

    private final int level;
    private final int panels;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float t;

    /**
     * Creates the rules pictures.
     * @param level 1..3
     */
    BubbleRules(int level) {
        this.level = level;
        this.panels = level == 1 ? 2 : 4;
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
        if (level == 1) return "Touch the bubbles with your hands!";
        if (level == 2) return "Blue bubbles are for your left hand, red for your right. Keep away from the spiky ones: they take a point!";
        return "Pop the bubbles, use both hands for purple, and keep away from the spiky ones: they take two points!";
    }

    @Override
    public void draw(Canvas c) {
        float total = panels * PW + (panels - 1) * GAP;
        float x0 = (Stage.W - total) / 2f;
        for (int i = 0; i < panels; i++) {
            float px = x0 + i * (PW + GAP);
            fill.setColor(0x55124E6B);
            r.set(px + 6f, TOP + 8f, px + PW + 6f, TOP + PH + 8f);
            c.drawRoundRect(r, 36f, 36f, fill);
            fill.setColor(0xEAF4FFFF);
            r.set(px, TOP, px + PW, TOP + PH);
            c.drawRoundRect(r, 36f, 36f, fill);
            float cx = px + PW / 2f;
            switch (i) {
                case 0: touch(c, cx); break;
                case 1: fade(c, cx); break;
                case 2: colours(c, cx); break;
                default: special(c, cx); break;
            }
        }
    }

    /** A fish swims at a bubble and pops it, then a new one appears. */
    private void swim(Canvas c, float bx, float by, float fromX, int kind, int fin, float start, float lt, boolean right) {
        float pop = start + 0.7f;
        if (lt < pop) {
            BubbleArt.drawBubble(c, kind, bx, by, 66f, 1f, t);
        } else if (lt < pop + 0.4f) {
            BubbleArt.drawPop(c, bx, by, 66f, (lt - pop) / 0.4f, kind);
        }
        float k = lt < start ? 0f : Math.min(1f, Ease.outCubic((lt - start) / 0.7f));
        float fx = fromX + (bx - 70f * (right ? 1f : -1f) - fromX) * k;
        BubbleArt.drawFish(c, fx, by + 20f * (1f - k), right, Theme.P1, fin, t, 0.8f);
    }

    private void touch(Canvas c, float cx) {
        float lt = t % LOOP;
        swim(c, cx + 40f, TOP + 160f, cx - 160f, BubbleLogic.ANY, BubbleArt.FIN_NEUTRAL, 0.3f, lt, true);
    }

    private void fade(Canvas c, float cx) {
        float lt = t % LOOP, k = Math.min(1f, lt / 2.2f);
        BubbleArt.drawBubble(c, BubbleLogic.ANY, cx, TOP + 130f, 66f * (1f - 0.25f * k), 1f - k, t);
        // a calm smile: fading is fine
        fill.setColor(0xFFFFD04D);
        c.drawCircle(cx, TOP + 255f, 46f, fill);
        fill.setColor(0xFF2F3A45);
        c.drawCircle(cx - 16f, TOP + 244f, 6f, fill);
        c.drawCircle(cx + 16f, TOP + 244f, 6f, fill);
        line.setColor(0xFF2F3A45);
        line.setStrokeWidth(5f);
        r.set(cx - 24f, TOP + 246f, cx + 24f, TOP + 280f);
        c.drawArc(r, 20f, 140f, false, line);
    }

    private void colours(Canvas c, float cx) {
        float lt = t % LOOP;
        swim(c, cx - 20f, TOP + 110f, cx - 150f, BubbleLogic.BLUE, BubbleArt.FIN_BLUE, 0.2f, lt, true);
        swim(c, cx + 20f, TOP + 240f, cx + 150f, BubbleLogic.RED, BubbleArt.FIN_RED, 1.3f, lt, false);
    }

    private void special(Canvas c, float cx) {
        float lt = t % LOOP;
        boolean hard = level >= 3;
        BubbleArt.drawBubble(c, BubbleLogic.GOLD, cx - 100f, TOP + (hard ? 85f : 100f), 50f, 1f, t);
        Theme.drawCentered(c, "+5", cx + 60f, TOP + (hard ? 103f : 118f), 52f, 0xFFE0A100);
        if (hard) {
            BubbleArt.drawBubble(c, BubbleLogic.BOTH, cx - 100f, TOP + 185f, 50f, 1f, t);
            float k = (float) (0.5 + 0.5 * Math.sin(lt * 3.2f));
            BubbleArt.drawFish(c, cx - 10f + 20f * k, TOP + 185f, false, Theme.P1, BubbleArt.FIN_BLUE, t, 0.55f);
            BubbleArt.drawFish(c, cx + 110f - 20f * k, TOP + 185f, true, Theme.P1, BubbleArt.FIN_RED, t, 0.55f);
        }
        float dy = hard ? 285f : 245f;
        BubbleArt.drawBubble(c, BubbleLogic.DECOY, cx - 100f, TOP + dy, hard ? 30f : 44f, 1f, t);
        Theme.drawCentered(c, hard ? "-2" : "-1", cx + 60f, TOP + dy + 18f, 52f, 0xFFD64545);
    }
}
