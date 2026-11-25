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
        return shootTheme(Themes.DESERT, TrackDefs.ALL[0], "t29_desert_props_map", 0xFFE9C98B, "Desert Dunes", false);
    }

    /** Draws and saves {@code t30_city_props_map.png} (the sidewalk is the light band beside the road). */
    static File shootCity() throws IOException {
        return shootTheme(Themes.CITY, TrackDefs.byId("city"), "t30_city_props_map", 0xFF9EA3A6, "City Lights", true);
    }

    private static final int[] KIND_COLOURS = {0xFF2E7D32, 0xFFB5541C, 0xFFD81B60, 0xFF5D4037, 0xFF6A1B9A, 0xFF8D6E63, 0xFFC0A060,
            0xFF9E9E9E, 0xFFA0522D, 0xFF455A64, 0xFFFFC107, 0xFF43A047, 0xFFD32F2F, 0xFF1E88E5, 0xFF795548, 0xFFFF5252, 0xFF37474F,
            0xFF8E24AA, 0xFFFF6F00, 0xFFEC407A, 0xFF1B5E20, 0xFF2E7D32, 0xFFFBC02D, 0xFF616161, 0xFF9E9E9E, 0xFF6D4C41, 0xFFA1887F,
            0xFFE53935, 0xFF4E342E, 0xFF558B2F, 0xFF8E24AA, 0xFF9CCC65};
    private static final String[] KIND_NAMES = {"saguaro", "butte", "barrel cacti", "rocks", "joshua tree", "scrub", "dry grass",
            "pebbles", "building", "tower", "lamp", "street tree", "parked car", "bus stop", "bench", "hydrant", "bin", "sign", "cone",
            "flower planter", "pine grove", "pine", "aspen", "rock outcrop", "boulders", "cabin", "dock", "canoe", "fallen log",
            "fern", "wildflowers", "grass"};

    private static File shootTheme(Theme th, TrackDef def, String file, int background, String title, boolean sidewalk)
            throws IOException {
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
        c.drawColor(background);
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
        if (sidewalk) {
            p.setColor(0xFFC9C5BB);
            p.setStrokeWidth(2f * 9f * scale);
            c.drawPath(centre, p);
        }
        p.setColor(0xFFCFC6BA);
        p.setStrokeWidth(2f * (HALF + BARRIER) * scale);
        c.drawPath(centre, p);
        p.setColor(0xFF8C7E72);
        p.setStrokeWidth(2f * HALF * scale);
        c.drawPath(centre, p);

        int[] colours = KIND_COLOURS;
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
        c.drawText(title + " props: " + props.size() + " on a " + Math.round(t.lapLength()) + " m lap", 40f, 58f, head);
        head.setTextSize(26f);
        StringBuilder legend = new StringBuilder();
        for (Theme.PropRule r : th.props) {
            legend.append(KIND_NAMES[r.kind]).append(r.tierB ? "*" : "").append(' ').append(counts[r.kind]).append("   ");
        }
        c.drawText(legend + "(* = filler tier, ringed, off on LOW)   circle size = footprint", 40f, 86f, head);
        int li = 0;
        for (Theme.PropRule r : th.props) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(colours[r.kind]);
            c.drawCircle(60f + li++ * 40f, H - 30f, 12f, p);
        }

        File dir = new File("build/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File f = new File(dir, file + ".png");
        try (FileOutputStream os = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + f.getAbsolutePath() + " " + f.length() + " bytes");
        return f;
    }
}
