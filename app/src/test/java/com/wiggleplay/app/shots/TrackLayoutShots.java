package com.wiggleplay.app.shots;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.wiggleplay.app.games.race.TrackDef;
import com.wiggleplay.app.games.race.TrackDefs;
import com.wiggleplay.app.games.race.TrackSpline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * T-28 Part A: a top view of each of the four track layouts (1920 x 1080), so the architect can compare them with the
 * picture from the track checker script. The picture is flipped in x so it turns the way the driver turns (looking along +Z,
 * screen-right is -X). Test code only.
 */
final class TrackLayoutShots {

    private static final int W = 1920, H = 1080, HEADER = 90, MARGIN = 90;
    private static final float HALF = 5.5f;   // Easy half-width
    private static final float BARRIER = 0.6f;
    private static final int[] GROUND = {0xFFE9C98B, 0xFFB9BDB8, 0xFF6FAE5C, 0xFF4A4566};

    private TrackLayoutShots() { }

    /** Draws and saves the four pictures {@code t28_layout_<id>.png}. */
    static File[] shootAll() throws IOException {
        File[] out = new File[TrackDefs.ALL.length];
        for (int i = 0; i < out.length; i++) out[i] = shoot(TrackDefs.ALL[i], GROUND[i]);
        return out;
    }

    private static File shoot(TrackDef def, int ground) throws IOException {
        TrackSpline t = TrackSpline.build(def, false, HALF);
        int n = t.sampleCount();
        float ds = t.lapLength() / n;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, t.sampleX(i));
            maxX = Math.max(maxX, t.sampleX(i));
            minZ = Math.min(minZ, t.sampleZ(i));
            maxZ = Math.max(maxZ, t.sampleZ(i));
        }
        float pad = HALF + BARRIER + 8f;   // room for the tick labels
        float scale = Math.min((W - 2f * MARGIN) / (maxX - minX + 2f * pad), (H - HEADER - 2f * MARGIN) / (maxZ - minZ + 2f * pad));
        final M m = new M(scale, W / 2f + (minX + maxX) / 2f * scale, HEADER + (H - HEADER) / 2f + (minZ + maxZ) / 2f * scale);

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(ground);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // barrier band, then road, then edge and barrier lines
        Path centre = offsetPath(t, m, 0f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFFD7DCE0);
        p.setStrokeWidth(2f * (HALF + BARRIER) * scale);
        c.drawPath(centre, p);
        p.setColor(0xFF5A5F66);
        p.setStrokeWidth(2f * HALF * scale);
        c.drawPath(centre, p);
        p.setStrokeWidth(2f);
        p.setColor(0xFFF3EBDD);
        c.drawPath(offsetPath(t, m, HALF), p);
        c.drawPath(offsetPath(t, m, -HALF), p);
        p.setColor(0xFFE89A4C);
        c.drawPath(offsetPath(t, m, HALF + BARRIER), p);
        c.drawPath(offsetPath(t, m, -(HALF + BARRIER)), p);
        p.setColor(0x80FFFFFF);
        p.setStrokeWidth(1.5f);
        c.drawPath(centre, p);

        // ticks every 50 m with their s
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setTextSize(22f);
        txt.setColor(0xFF1B1B1B);
        float[] q = new float[3];
        for (float s = 0f; s < t.lapLength() - 10f; s += 50f) {
            t.sample(s, q);
            p.setColor(0xFF1B1B1B);
            p.setStrokeWidth(3f);
            c.drawLine(m.x(q[0], q[1], q[2], HALF + BARRIER), m.y(q[0], q[1], q[2], HALF + BARRIER),
                    m.x(q[0], q[1], q[2], HALF + BARRIER + 3f), m.y(q[0], q[1], q[2], HALF + BARRIER + 3f), p);
            if (s > 0f) {
                c.drawText("s=" + Math.round(s), m.x(q[0], q[1], q[2], HALF + BARRIER + 3.5f) - 22f,
                        m.y(q[0], q[1], q[2], HALF + BARRIER + 3.5f) + 8f, txt);
            }
        }

        // control points with their numbers
        float[] cp = def.control;
        for (int k = 0; k < def.pointCount(); k++) {
            float sx = m.px(cp[k * 2]), sy = m.pz(cp[k * 2 + 1]);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF1B1B1B);
            c.drawCircle(sx, sy, 6f, p);
            txt.setColor(0xFF7A1010);
            c.drawText(String.valueOf(k), sx + 9f, sy - 9f, txt);
        }

        // start square: two rows of chequers across the road
        t.sample(0f, q);
        int cells = Math.round(2f * HALF);
        for (int row = 0; row < 2; row++) {
            for (int k = 0; k < cells; k++) {
                float a = -HALF + k, b = a + 1f, r0 = row - 1f, r1 = r0 + 1f;
                Path sq = new Path();
                sq.moveTo(m.x(q[0], q[1], q[2], a, r0), m.y(q[0], q[1], q[2], a, r0));
                sq.lineTo(m.x(q[0], q[1], q[2], b, r0), m.y(q[0], q[1], q[2], b, r0));
                sq.lineTo(m.x(q[0], q[1], q[2], b, r1), m.y(q[0], q[1], q[2], b, r1));
                sq.lineTo(m.x(q[0], q[1], q[2], a, r1), m.y(q[0], q[1], q[2], a, r1));
                sq.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(((k + row) & 1) == 0 ? 0xFF2F3A45 : 0xFFF3EBDD);
                c.drawPath(sq, p);
            }
        }
        // direction arrow: 14 m along the road from the start line
        float[] e = new float[3];
        t.sample(16f, e);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6f);
        p.setColor(0xFFC8102E);
        c.drawLine(m.px(q[0]), m.pz(q[1]), m.px(e[0]), m.pz(e[1]), p);
        float hx = e[0] + (float) Math.sin(e[2]) * 3f, hz = e[1] + (float) Math.cos(e[2]) * 3f;
        Path head = new Path();
        head.moveTo(m.px(hx), m.pz(hz));
        head.lineTo(m.x(e[0], e[1], e[2], 2.2f), m.y(e[0], e[1], e[2], 2.2f));
        head.lineTo(m.x(e[0], e[1], e[2], -2.2f), m.y(e[0], e[1], e[2], -2.2f));
        head.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(head, p);

        // the tightest bend, circled
        int tight = 0;
        for (int i = 1; i < n; i++) if (Math.abs(t.radiusAt(i)) < Math.abs(t.radiusAt(tight))) tight = i;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5f);
        p.setColor(0xFFC8102E);
        c.drawCircle(m.px(t.sampleX(tight)), m.pz(t.sampleZ(tight)), 34f, p);

        // header
        Paint head1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        head1.setColor(0xFF1B1B1B);
        head1.setTextSize(44f);
        c.drawText(def.name + " (" + def.themeId + ")   lap " + String.format("%.1f", t.lapLength()) + " m   tightest radius "
                + String.format("%.1f", t.minRadius()) + " m at s=" + Math.round(tight * ds), 40f, 58f, head1);
        head1.setTextSize(24f);
        c.drawText("Easy half-width " + HALF + " m, barrier to +" + BARRIER + " m, ticks every 50 m, red circle = tightest bend, red arrow = driving direction",
                40f, 84f, head1);

        File dir = new File("build/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File f = new File(dir, "t28_layout_" + def.id + ".png");
        try (FileOutputStream os = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + f.getAbsolutePath() + " " + f.length() + " bytes");
        return f;
    }

    /** The centre line shifted {@code off} metres to the right, as a closed screen path. */
    private static Path offsetPath(TrackSpline t, M m, float off) {
        Path path = new Path();
        int n = t.sampleCount();
        for (int i = 0; i < n; i++) {
            float sx = m.x(t.sampleX(i), t.sampleZ(i), t.sampleYaw(i), off), sy = m.y(t.sampleX(i), t.sampleZ(i), t.sampleYaw(i), off);
            if (i == 0) path.moveTo(sx, sy);
            else path.lineTo(sx, sy);
        }
        path.close();
        return path;
    }

    /** World to screen: x is flipped, +Z is up the picture. */
    private static final class M {
        final float scale, ox, oy;

        M(float scale, float ox, float oy) {
            this.scale = scale;
            this.ox = ox;
            this.oy = oy;
        }

        float px(float x) { return ox - x * scale; }

        float pz(float z) { return oy - z * scale; }

        /** Screen x of a point {@code off} metres to the right of (x, z) at heading yaw. */
        float x(float x, float z, float yaw, float off) { return px(x - (float) Math.cos(yaw) * off); }

        float y(float x, float z, float yaw, float off) { return pz(z + (float) Math.sin(yaw) * off); }

        /** Point {@code off} to the right and {@code along} metres ahead. */
        float x(float x, float z, float yaw, float off, float along) {
            return px(x - (float) Math.cos(yaw) * off + (float) Math.sin(yaw) * along);
        }

        float y(float x, float z, float yaw, float off, float along) {
            return pz(z + (float) Math.sin(yaw) * off + (float) Math.cos(yaw) * along);
        }
    }
}
