package com.wiggleplay.app.shots;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.wiggleplay.app.games.race.Lakes;
import com.wiggleplay.app.games.race.PropMeshes;
import com.wiggleplay.app.games.race.PropScatter;
import com.wiggleplay.app.games.race.RaceLevel;
import com.wiggleplay.app.games.race.Terrain;
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

/** T-30b: the Rocky Lakes height map with the lakes, the road and the props, and the close-up sheet. Test code only. */
final class RockyShots {

    private static final int W = 1920, H = 1080, HEADER = 90, MARGIN = 40;
    /** kind, variant, yaw (degrees) of the close-up sheet. */
    private static final int[][] ITEMS = {
            {PropMeshes.PINE, 0, 30}, {PropMeshes.PINE, 1, 30}, {PropMeshes.PINE, 2, 30}, {PropMeshes.PINE_GROVE, 1, 30},
            {PropMeshes.PINE_GROVE, 3, 30}, {PropMeshes.ASPEN, 0, 30}, {PropMeshes.ROCK_OUTCROP, 0, 30}, {PropMeshes.ROCK_OUTCROP, 2, 30},
            {PropMeshes.BOULDERS, 1, 30}, {PropMeshes.CABIN, 0, 35}, {PropMeshes.CABIN, 1, 35}, {PropMeshes.DOCK, 0, 40},
            {PropMeshes.CANOE, 0, 40}, {PropMeshes.FALLEN_LOG, 1, 25}, {PropMeshes.FERN, 0, 30}, {PropMeshes.WILDFLOWERS, 0, 30},
            {PropMeshes.WILDFLOWERS, 1, 30}, {PropMeshes.GRASS, 0, 30}};

    private RockyShots() { }

    static File shootCloseups() throws IOException {
        return CityShots.sheet(Themes.ROCKIES, ITEMS, "t30_rockies_closeups", "Rocky Lakes props (software drawing: theme ramps, 3/4 view)",
                0xFFE2F0F7);
    }

    /** Draws and saves {@code t30_rockies_heightmap.png}: shaded height, lakes, road, props. The picture is flipped in x as the T-28 ones. */
    static File shootHeightmap() throws IOException {
        Theme th = Themes.ROCKIES;
        TrackDef def = TrackDefs.byId("rockies");
        TrackSpline t = TrackSpline.build(def, false, RaceLevel.HARD);
        Lakes lakes = Lakes.of(def);
        Terrain te = Terrain.create(t, th, def.id.hashCode(), false, lakes);
        List<PropScatter.Instance> inst = new ArrayList<>();
        PropScatter.scatter(t, th, def.id.hashCode(), true, new ArrayList<StaticBatcher.Placement>(), te, inst);

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < t.sampleCount(); i++) {
            minX = Math.min(minX, t.sampleX(i));
            maxX = Math.max(maxX, t.sampleX(i));
            minZ = Math.min(minZ, t.sampleZ(i));
            maxZ = Math.max(maxZ, t.sampleZ(i));
        }
        float pad = 50f;
        final float scale = Math.min((W - 2f * MARGIN) / (maxX - minX + 2f * pad), (H - HEADER - 2f * MARGIN) / (maxZ - minZ + 2f * pad));
        final float ox = W / 2f + (minX + maxX) / 2f * scale, oy = HEADER + (H - HEADER) / 2f + (minZ + maxZ) / 2f * scale;

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF2F5A48);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        // shaded height: green valley floor to grey rock, lit from the sun side
        float[] sun = te.sunToward();
        for (int py = HEADER; py < H; py += 3) {
            for (int px = 0; px < W; px += 3) {
                float x = (ox - px) / scale, z = (oy - py) / scale;
                if (x < minX - pad || x > maxX + pad || z < minZ - pad || z > maxZ + pad) continue;
                float h = te.surface(x, z);
                float gx = (te.surface(x + 1.5f, z) - te.surface(x - 1.5f, z)) / 3f, gz = (te.surface(x, z + 1.5f) - te.surface(x, z - 1.5f)) / 3f;
                float shade = Math.max(0.55f, Math.min(1.2f, 0.85f - (gx * sun[0] + gz * sun[1]) * 0.9f));
                float k = Math.min(1f, h / 8f);
                float e = lakes.edge(x, z);
                int r, g, b;
                if (e < 0f) {
                    float d = Math.min(1f, -e / 12f);
                    r = Math.round(0x5F + (0x1F - 0x5F) * d);
                    g = Math.round(0xC9 + (0x7F - 0xC9) * d);
                    b = Math.round(0xD6 + (0x95 - 0xD6) * d);
                } else if (e < 3f) {
                    r = 0xA3;
                    g = 0x9C;
                    b = 0x8E;
                } else {
                    float slope = (float) Math.hypot(gx, gz);
                    float rock = Math.min(1f, Math.max(0f, (slope - 0.35f) / 0.25f));
                    r = Math.round((0x4E + (0x93 - 0x4E) * k) * (1f - rock) + 0x8A * rock);
                    g = Math.round((0x7F + (0xBF - 0x7F) * k) * (1f - rock) + 0x92 * rock);
                    b = Math.round((0x3C + (0x6A - 0x3C) * k) * (1f - rock) + 0x98 * rock);
                    r = Math.min(255, Math.round(r * shade));
                    g = Math.min(255, Math.round(g * shade));
                    b = Math.min(255, Math.round(b * shade));
                }
                p.setColor(0xFF000000 | (r << 16) | (g << 8) | b);
                c.drawRect(px, py, px + 3, py + 3, p);
            }
        }
        Path centre = new Path();
        for (int i = 0; i < t.sampleCount(); i++) {
            float sx = ox - t.sampleX(i) * scale, sy = oy - t.sampleZ(i) * scale;
            if (i == 0) centre.moveTo(sx, sy);
            else centre.lineTo(sx, sy);
        }
        centre.close();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFFC9453C);
        p.setStrokeWidth(2f * 6.4f * scale);
        c.drawPath(centre, p);
        p.setColor(0xFF575B62);
        p.setStrokeWidth(2f * 5.5f * scale);
        c.drawPath(centre, p);
        int[] counts = new int[PropMeshes.COUNT];
        for (PropScatter.Instance in : inst) {
            counts[in.kind]++;
            p.setStyle(Paint.Style.FILL);
            p.setColor(KIND[in.kind - PropMeshes.PINE_GROVE]);
            c.drawCircle(ox - in.x * scale, oy - in.z * scale, Math.max(3.5f, Math.min(in.footprint, 4f) * scale * 0.7f), p);
        }
        Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
        head.setColor(0xFFFFFFFF);
        head.setTextSize(42f);
        c.drawText("Rocky Lakes: height map, 4 lakes, " + inst.size() + " props on a " + Math.round(t.lapLength()) + " m lap (highest "
                + 8 + " m)", 30f, 50f, head);
        head.setTextSize(24f);
        StringBuilder leg = new StringBuilder();
        for (int k = PropMeshes.PINE_GROVE; k <= PropMeshes.GRASS; k++) leg.append(NAME[k - PropMeshes.PINE_GROVE]).append(' ').append(counts[k]).append("   ");
        c.drawText(leg.toString(), 30f, 80f, head);

        File dir = new File("build/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File f = new File(dir, "t30_rockies_heightmap.png");
        try (FileOutputStream os = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + f.getAbsolutePath() + " " + f.length() + " bytes");
        return f;
    }

    private static final int[] KIND = {0xFF0B3D1E, 0xFF1B5E20, 0xFFFFEB3B, 0xFF424242, 0xFFBDBDBD, 0xFF8D4E2E, 0xFFFF9800, 0xFFE53935,
            0xFF4E342E, 0xFFAED581, 0xFFCE93D8, 0xFFDCEDC8};
    private static final String[] NAME = {"grove", "pine", "aspen", "outcrop", "boulders", "cabin", "dock", "canoe", "log", "fern",
            "flowers", "grass"};
}
