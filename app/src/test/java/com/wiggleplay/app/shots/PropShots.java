package com.wiggleplay.app.shots;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.wiggleplay.app.games.race.PropMeshes;
import com.wiggleplay.app.games.race.PropScatter;
import com.wiggleplay.app.games.race.Theme;
import com.wiggleplay.app.games.race.Themes;
import com.wiggleplay.app.games.race.TrackDef;
import com.wiggleplay.app.games.race.TrackDefs;
import com.wiggleplay.app.games.race.TrackSpline;
import com.wiggleplay.app.gl.StaticBatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * T-29 Part B: a top view of the Desert Dunes track with a dot for every prop (colour by kind, a ring round the filler tier),
 * so the layout can be checked without GL. The picture is flipped in x as in the T-28 layout pictures. Test code only.
 */
final class PropShots {

    private static final int W = 1920, H = 1080, HEADER = 90, MARGIN = 60;
    private static final float HALF = 5.5f, BARRIER = 0.9f;

    private PropShots() { }

    /** Draws and saves {@code t29_desert_props_map.png}. */
    static File shootDesert() throws IOException {
        Theme th = Themes.DESERT;
        TrackDef def = TrackDefs.ALL[0];
        TrackSpline t = TrackSpline.build(def, false, HALF);
        List<StaticBatcher.Placement> props = new ArrayList<>();
        PropScatter.scatter(t, th, def.id.hashCode(), true, props);

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < t.sampleCount(); i++) {
            minX = Math.min(minX, t.sampleX(i));
            maxX = Math.max(maxX, t.sampleX(i));
            minZ = Math.min(minZ, t.sampleZ(i));
            maxZ = Math.max(maxZ, t.sampleZ(i));
        }
        float pad = 45f;
        final float scale = Math.min((W - 2f * MARGIN) / (maxX - minX + 2f * pad), (H - HEADER - 2f * MARGIN) / (maxZ - minZ + 2f * pad));
        final float ox = W / 2f + (minX + maxX) / 2f * scale, oy = HEADER + (H - HEADER) / 2f + (minZ + maxZ) / 2f * scale;

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFE9C98B);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path centre = new Path();
        for (int i = 0; i < t.sampleCount(); i++) {
            float sx = ox - t.sampleX(i) * scale, sy = oy - t.sampleZ(i) * scale;
            if (i == 0) centre.moveTo(sx, sy);
            else centre.lineTo(sx, sy);
        }
        centre.close();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFFCFC6BA);
        p.setStrokeWidth(2f * (HALF + BARRIER) * scale);
        c.drawPath(centre, p);
        p.setColor(0xFF8C7E72);
        p.setStrokeWidth(2f * HALF * scale);
        c.drawPath(centre, p);

        int[] colours = {0xFF2E7D32, 0xFFB5541C, 0xFFD81B60, 0xFF5D4037, 0xFF6A1B9A};
        String[] names = {"saguaro", "butte", "barrel cacti*", "rocks*", "joshua tree*"};
        int[] counts = new int[PropMeshes.COUNT];
        for (StaticBatcher.Placement pl : props) {
            int kind = Integer.parseInt(pl.meshName.split("_")[1]);
            counts[kind]++;
            boolean tierB = false;
            float fp = 1f;
            for (Theme.PropRule r : th.props) {
                if (r.kind == kind) {
                    tierB = r.tierB;
                    fp = Math.max(r.footprint, PropMeshes.reach(kind));
                }
            }
            float sx = ox - pl.matrix[12] * scale, sy = oy - pl.matrix[14] * scale;
            p.setStyle(Paint.Style.FILL);
            p.setColor(colours[kind]);
            c.drawCircle(sx, sy, Math.max(4f, fp * scale * (float) Math.sqrt(pl.matrix[0] * pl.matrix[0] + pl.matrix[1] * pl.matrix[1] + pl.matrix[2] * pl.matrix[2])), p);
            if (tierB) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(sx, sy, 7f, p);
            }
        }
        Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
        head.setColor(0xFF1B1B1B);
        head.setTextSize(44f);
        c.drawText("Desert Dunes props: " + props.size() + " on a " + Math.round(t.lapLength()) + " m lap", 40f, 58f, head);
        head.setTextSize(26f);
        StringBuilder legend = new StringBuilder();
        for (int k = 0; k < PropMeshes.COUNT; k++) legend.append(names[k]).append(' ').append(counts[k]).append("   ");
        c.drawText(legend + "(* = filler tier, ringed, off on LOW)   circle size = footprint", 40f, 86f, head);
        for (int k = 0; k < PropMeshes.COUNT; k++) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(colours[k]);
            c.drawCircle(60f + k * 40f, H - 30f, 12f, p);
        }

        File dir = new File("build/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File f = new File(dir, "t29_desert_props_map.png");
        try (FileOutputStream os = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + f.getAbsolutePath() + " " + f.length() + " bytes");
        return f;
    }
}
