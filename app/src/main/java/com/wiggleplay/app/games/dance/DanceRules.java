package com.wiggleplay.app.games.dance;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.ui.Theme;

/**
 * The "how to play" pictures of Animal Dance Party: look at the pose card, copy it, hold it and turn gold.
 * Three neon panels in a row. Nothing is allocated per frame.
 */
final class DanceRules implements Hud.RulesPainter {

    private static final float PW = 540f, GAP = 40f, TOP = 430f, PH = 400f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float t;

    DanceRules() {
        line.setStyle(Paint.Style.STROKE);
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
        return "Look at the pose, copy it, and hold it still!";
    }

    @Override
    public void draw(Canvas c) {
        float x0 = (Stage.W - (3 * PW + 2 * GAP)) / 2f;
        int pose = ((int) (t / 1.6f)) % 3 == 0 ? Poses.STAR : ((int) (t / 1.6f)) % 3 == 1 ? Poses.ROBOT : Poses.AIRPLANE;
        for (int i = 0; i < 3; i++) {
            float px = x0 + i * (PW + GAP), cx = px + PW / 2f;
            panel(c, px, i);
            switch (i) {
                case 0:
                    DanceArt.host(c, (int) (t / 3.2f) % DanceArt.HOSTS, cx - 110f, TOP + PH - 90f, 0.34f, t * 1.8f, 0.5f);
                    DanceArt.card(c, pose, cx + 20f, TOP + 40f, 190f, 250f, DanceArt.PINK, 0f);
                    Theme.drawCentered(c, "Look", cx, TOP + PH - 26f, 60f, DanceArt.WHITE);
                    break;
                case 1:
                    DanceArt.figure(c, pose, cx, TOP + 120f, 62f, DanceArt.CYAN, 6f);
                    Theme.drawCentered(c, "Copy it", cx, TOP + PH - 26f, 60f, DanceArt.WHITE);
                    break;
                default: {
                    float k = (float) (0.5 + 0.5 * Math.sin(t * 3f));
                    DanceArt.figure(c, pose, cx - 90f, TOP + 120f, 62f, k > 0.5f ? DanceArt.GOLD : DanceArt.WHITE, 6f);
                    DanceArt.star(c, cx + 110f, TOP + 90f, 52f + 8f * k, DanceArt.GOLD);
                    DanceArt.star(c, cx + 190f, TOP + 190f, 34f, DanceArt.GOLD);
                    DanceArt.star(c, cx + 60f, TOP + 220f, 28f, DanceArt.GOLD);
                    Theme.drawCentered(c, "Hold it!", cx, TOP + PH - 26f, 60f, DanceArt.WHITE);
                    break;
                }
            }
        }
    }

    private void panel(Canvas c, float px, int i) {
        r.set(px, TOP, px + PW, TOP + PH);
        fill.setColor(0xF01E1245);
        c.drawRoundRect(r, 36f, 36f, fill);
        int col = i == 0 ? DanceArt.PINK : i == 1 ? DanceArt.CYAN : DanceArt.GOLD;
        line.setColor((col & 0x00FFFFFF) | (70 << 24));
        line.setStrokeWidth(18f);
        c.drawRoundRect(r, 36f, 36f, line);
        line.setColor(col);
        line.setStrokeWidth(5f);
        c.drawRoundRect(r, 36f, 36f, line);
    }
}
