package com.wiggleplay.app.games.jungle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.ui.Theme;

/**
 * The "how to play" pictures of Jungle Dash, made so a child can follow them without reading: jump over the logs,
 * duck under the vines, grab bananas and never worry about a bump. Three animated panels in a row. Nothing is
 * allocated per frame.
 */
final class JungleRules implements Hud.RulesPainter {

    private static final float PW = 540f, GAP = 40f, TOP = 470f, PH = 340f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sprite = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private float t;

    JungleRules() {
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
        return "Hop over the logs, duck under the vines, and grab the bananas!";
    }

    @Override
    public void draw(Canvas c) {
        float x0 = (Stage.W - (3 * PW + 2 * GAP)) / 2f;
        for (int i = 0; i < 3; i++) {
            float px = x0 + i * (PW + GAP), cx = px + PW / 2f;
            panel(c, px);
            switch (i) {
                case 0: hop(c, cx); break;
                case 1: duck(c, cx); break;
                default: bananas(c, cx); break;
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
     * A little kid from the front.
     * @param feetY where the feet are
     * @param squash 1 for standing tall, less for crouching
     * @param armsUp 0 for arms down, 1 for arms up
     */
    private void kid(Canvas c, float cx, float feetY, float scale, float squash, float armsUp) {
        c.save();
        c.translate(cx, feetY);
        c.scale(scale, scale * squash);
        line.setColor(Theme.P1);
        line.setStrokeWidth(22f);
        float ay = -130f - armsUp * 90f, ax = 54f + armsUp * 14f;
        c.drawLine(-44f, -150f, -ax, ay + 60f, line);
        c.drawLine(44f, -150f, ax, ay + 60f, line);
        c.drawLine(-22f, -60f, -26f, 0f, line);
        c.drawLine(22f, -60f, 26f, 0f, line);
        fill.setColor(Theme.P1);
        r.set(-48f, -200f, 48f, -50f);
        c.drawRoundRect(r, 38f, 38f, fill);
        fill.setColor(0xFFF1D3B3);
        c.drawCircle(0f, -250f, 38f, fill);
        fill.setColor(Theme.INK);
        c.drawCircle(-13f, -254f, 5f, fill);
        c.drawCircle(13f, -254f, 5f, fill);
        c.restore();
    }

    private void ground(Canvas c, float cx, float y) {
        fill.setColor(0xFFC9B48A);
        r.set(cx - 240f, y, cx + 240f, y + 16f);
        c.drawRoundRect(r, 8f, 8f, fill);
    }

    private void blit(Canvas c, android.graphics.Bitmap b, float x, float y, float w, float h) {
        r.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f);
        c.drawBitmap(b, null, r, sprite);
    }

    /** Panel 1: the kid hops, the log slides underneath. */
    private void hop(Canvas c, float cx) {
        float k = (t % 2.4f) / 2.4f;
        float gy = TOP + 250f;
        ground(c, cx, gy);
        float jump = (float) Math.sin(Math.min(1f, Math.max(0f, (k - 0.25f) / 0.5f)) * Math.PI);
        kid(c, cx - 120f, gy - jump * 90f, 0.62f, 1f, jump);
        float lx = cx + 200f - k * 360f;
        blit(c, JungleArt.log(), lx, gy - 20f, 170f, 45f);
        blit(c, JungleArt.markUp(), cx + 150f, TOP + 70f, 96f, 96f);
        Theme.drawCentered(c, "Hop over logs", cx, TOP + PH - 30f, 44f, Theme.INK);
    }

    /** Panel 2: the kid crouches, the vine swings past. */
    private void duck(Canvas c, float cx) {
        float k = (t % 2.4f) / 2.4f;
        float gy = TOP + 250f;
        ground(c, cx, gy);
        float low = k > 0.3f && k < 0.75f ? 1f : 0f;
        kid(c, cx - 120f, gy, 0.62f, 1f - 0.4f * low, 0f);
        float vx = cx + 200f - k * 360f;
        blit(c, JungleArt.vine(), vx, TOP + 110f, 56f, 200f);
        blit(c, JungleArt.markDown(), cx + 150f, TOP + 70f, 96f, 96f);
        Theme.drawCentered(c, "Duck under vines", cx, TOP + PH - 30f, 44f, Theme.INK);
    }

    /** Panel 3: bananas count, the golden coconut counts five, and a bump costs nothing. */
    private void bananas(Canvas c, float cx) {
        float bob = (float) Math.sin(t * 4f) * 6f;
        blit(c, JungleArt.banana(), cx - 150f, TOP + 100f + bob, 90f, 90f);
        Theme.drawCentered(c, "+1", cx - 150f, TOP + 190f, 48f, Theme.INK);
        blit(c, JungleArt.golden(), cx + 130f, TOP + 100f - bob, 110f, 110f);
        Theme.drawCentered(c, "+5", cx + 130f, TOP + 190f, 48f, Theme.ORANGE);
        Theme.drawCentered(c, "Bumps never hurt", cx, TOP + 250f, 40f, Theme.INK);
        Theme.drawCentered(c, "Grab bananas", cx, TOP + PH - 30f, 44f, Theme.INK);
    }
}
