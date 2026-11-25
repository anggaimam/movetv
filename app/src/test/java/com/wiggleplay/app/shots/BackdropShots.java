package com.wiggleplay.app.shots;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.wiggleplay.app.games.race.Backdrop;
import com.wiggleplay.app.games.race.Theme;
import com.wiggleplay.app.games.race.Themes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * T-29 Part A: the backdrop rings of a theme unrolled (x = angle 0 to 360 degrees, y = height in metres), far ring first, over
 * the sky colour with the ground colour under the zero line, so the silhouettes can be checked without GL. Test code only.
 */
final class BackdropShots {

    private static final int W = 1920, H = 700, LEFT = 90, RIGHT = 30, TOP = 90, BOTTOM = 110;

    private BackdropShots() { }

    /** Draws and saves {@code t29_desert_backdrop.png}. */
    static File shootDesert() throws IOException {
        return shoot(Themes.DESERT, "t29_desert_backdrop");
    }

    static File shoot(Theme th, String name) throws IOException {
        float maxH = 0f;
        for (Theme.Ring r : th.rings) maxH = Math.max(maxH, r.maxH);
        maxH = (float) Math.ceil(maxH) + 1f;
        float plotW = W - LEFT - RIGHT, plotH = H - TOP - BOTTOM, zeroY = TOP + plotH * (maxH / (maxH + 1f));
        float mPerPx = plotH / (maxH + 1f);   // pixels per metre

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000 | th.sky);
        c.drawRect(0f, 0f, W, H, p);
        p.setColor(0xFF000000 | th.ground);
        c.drawRect(LEFT, zeroY, W - RIGHT, TOP + plotH, p);

        // far ring first so the near ones cover it
        for (int i = th.rings.length - 1; i >= 0; i--) {
            Theme.Ring r = th.rings[i];
            if (r.profile == Theme.STARS) continue;
            Backdrop.Profile pr = Backdrop.profile(r);
            int n = r.segments;
            float colW = plotW / n;
            p.setColor(0xFF000000 | r.color);
            for (int col = 0; col < n; col++) {
                // columns are numbered by angle; the larger angle is the left edge as seen from the centre, so x = angle
                float x0 = LEFT + col * colW, x1 = x0 + colW;
                Path q = new Path();
                q.moveTo(x0, zeroY);
                q.lineTo(x1, zeroY);
                q.lineTo(x1, zeroY - pr.right[col] * mPerPx);
                q.lineTo(x0, zeroY - pr.left[col] * mPerPx);
                q.close();
                c.drawPath(q, p);
            }
        }

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(0xFF1B1B1B);
        txt.setTextSize(30f);
        c.drawText(th.id + " backdrop: x = angle 0 to 360 degrees, y = height (m); far ring behind, near ring in front", LEFT, 50f, txt);
        txt.setTextSize(22f);
        for (int m = 0; m <= (int) maxH; m += 2) {
            c.drawText(m + " m", 20f, zeroY - m * mPerPx + 7f, txt);
        }
        for (int a = 0; a <= 360; a += 45) {
            c.drawText(a + "°", LEFT + plotW * a / 360f - 20f, TOP + plotH + 36f, txt);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < th.rings.length; i++) {
            Theme.Ring r = th.rings[i];
            sb.append(String.format("ring %d: radius %.0f, profile %d, h %.1f to %.1f%s   ", i, r.radius, r.profile, r.minH, r.maxH,
                    r.low ? "" : " (not on LOW)"));
        }
        c.drawText(sb.toString(), LEFT, H - 30f, txt);

        File dir = new File("build/screenshots");
        dir.mkdirs();
        File f = new File(dir, name + ".png");
        try (FileOutputStream os = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        return f;
    }
}
