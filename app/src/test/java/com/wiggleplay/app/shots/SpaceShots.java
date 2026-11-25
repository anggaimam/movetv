package com.wiggleplay.app.shots;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

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

/** T-30c: the Star Road height map with the craters, the road and the props, and the close-up sheet. Test code only. */
final class SpaceShots {

    private static final int W = 1920, H = 1080, HEADER = 90, MARGIN = 40;
    /** kind, variant, yaw (degrees) of the close-up sheet. */
    private static final int[][] ITEMS = {
            {PropMeshes.HABITAT_DOME, 0, 35}, {PropMeshes.HABITAT_DOME, 1, 35}, {PropMeshes.ANTENNA, 0, 30}, {PropMeshes.ANTENNA, 1, 30},
            {PropMeshes.ROVER, 0, 35}, {PropMeshes.ROVER, 1, 35}, {PropMeshes.SOLAR_ARRAY, 0, 30}, {PropMeshes.BIG_BOULDER, 0, 30},
            {PropMeshes.BIG_BOULDER, 1, 30}, {PropMeshes.CRYSTALS, 0, 30}, {PropMeshes.CRYSTALS, 1, 30}, {PropMeshes.CRYSTALS, 2, 30},
            {PropMeshes.MOON_ROCKS, 0, 30}, {PropMeshes.MOON_ROCKS, 1, 30}, {PropMeshes.DUST_PEBBLES, 0, 30}};

    private SpaceShots() { }

    static File shootCloseups() throws IOException {
        return CityShots.sheet(Themes.SPACE, ITEMS, "t30_space_closeups", "Star Road props (software drawing: theme ramps, 3/4 view)",
                0xFF2A2550);
    }

    /** Draws and saves {@code t30_space_heightmap.png}: shaded height, lakes, road, props. The picture is flipped in x as the T-28 ones. */
    static File shootHeightmap() throws IOException {
        Theme th = Themes.SPACE;
        TrackDef def = TrackDefs.byId("space");
        TrackSpline t = TrackSpline.build(def, false, RaceLevel.HARD);
        Terrain te = Terrain.create(t, th, def.id.hashCode(), false);
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
        c.drawColor(0xFF2A2550);
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
                float k = Math.max(0f, Math.min(1f, (h + 2.5f) / 4f));
                int r = Math.round(0x3C + (0x7A - 0x3C) * k), g = Math.round(0x38 + (0x75 - 0x38) * k), b = Math.round(0x54 + (0x98 - 0x54) * k);
                r = Math.min(255, Math.round(r * shade));
                g = Math.min(255, Math.round(g * shade));
                b = Math.min(255, Math.round(b * shade));
                p.setColor(0xFF000000 | (r << 16) | (g << 8) | b);
                c.drawRect(px, py, px + 3, py + 3, p);
            }
        }
        float[] cr = te.craters();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0xFFC8B4FF);
        for (int i = 0; i < cr.length; i += 3) c.drawCircle(ox - cr[i] * scale, oy - cr[i + 1] * scale, cr[i + 2] * scale, p);
        Path centre = new Path();
        for (int i = 0; i < t.sampleCount(); i++) {
            float sx = ox - t.sampleX(i) * scale, sy = oy - t.sampleZ(i) * scale;
            if (i == 0) centre.moveTo(sx, sy);
            else centre.lineTo(sx, sy);
        }
        centre.close();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0xFF3FBFD0);
        p.setStrokeWidth(2f * 6.4f * scale);
        c.drawPath(centre, p);
        p.setColor(0xFF2C3050);
        p.setStrokeWidth(2f * 5.5f * scale);
        c.drawPath(centre, p);
        int[] counts = new int[PropMeshes.COUNT];
        for (PropScatter.Instance in : inst) {
            counts[in.kind]++;
            p.setStyle(Paint.Style.FILL);
            p.setColor(KIND[in.kind - PropMeshes.HABITAT_DOME]);
            c.drawCircle(ox - in.x * scale, oy - in.z * scale, Math.max(3.5f, Math.min(in.footprint, 4f) * scale * 0.7f), p);
        }
        Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
        head.setColor(0xFFFFFFFF);
        head.setTextSize(42f);
        c.drawText("Star Road: height map, " + te.craters().length / 3 + " craters, " + inst.size() + " props on a " + Math.round(t.lapLength()) + " m lap", 30f, 50f, head);
        head.setTextSize(24f);
        StringBuilder leg = new StringBuilder();
        for (int k = PropMeshes.HABITAT_DOME; k <= PropMeshes.DUST_PEBBLES; k++) leg.append(NAME[k - PropMeshes.HABITAT_DOME]).append(' ').append(counts[k]).append("   ");
        c.drawText(leg.toString(), 30f, 80f, head);

        File dir = new File("build/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File f = new File(dir, "t30_space_heightmap.png");
        try (FileOutputStream os = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + f.getAbsolutePath() + " " + f.length() + " bytes");
        return f;
    }

    private static final int[] KIND = {0xFFE8E4F0, 0xFFC9CEDF, 0xFFB9BED0, 0xFF3F51B5, 0xFF9A95B8, 0xFF9C7BE0, 0xFF6E6A8C, 0xFFF2C572};
    private static final String[] NAME = {"dome", "antenna", "rover", "solar array", "big boulder", "crystals", "moon rocks", "pebbles"};
}
