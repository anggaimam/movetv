package com.wiggleplay.app.shots;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.wiggleplay.app.games.race.PropMeshes;
import com.wiggleplay.app.games.race.Theme;
import com.wiggleplay.app.games.race.Themes;
import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * T-30a: every City Lights prop close up on one sheet, drawn by a small software renderer (the screenshot rig has no OpenGL):
 * the colours come from the theme's ramps (dark to light by the vertex's position on the ramp), the light is the theme's
 * ambient plus sun, back faces are culled, painter's algorithm. Test code only.
 */
final class CityShots {

    private static final int W = 1920, H = 1100, COLS = 5, TOP = 70;
    /** kind, variant, yaw (degrees): what the sheet shows. */
    private static final int[][] ITEMS = {
            {PropMeshes.BUILDING, 0, 30}, {PropMeshes.BUILDING, 1, 30}, {PropMeshes.BUILDING, 2, 30}, {PropMeshes.TOWER, 0, 30},
            {PropMeshes.TOWER, 2, 30}, {PropMeshes.PARKED_CAR, 0, 55}, {PropMeshes.PARKED_CAR, 1, 55}, {PropMeshes.PARKED_CAR, 2, 55},
            {PropMeshes.STREET_TREE, 0, 30}, {PropMeshes.STREET_TREE, 1, 30}, {PropMeshes.LAMP, 0, 40}, {PropMeshes.LAMP, 1, 40},
            {PropMeshes.BUS_STOP, 0, 30}, {PropMeshes.BENCH, 0, 30}, {PropMeshes.HYDRANT, 0, 30}, {PropMeshes.BIN, 0, 30},
            {PropMeshes.SIGN, 0, 30}, {PropMeshes.SIGN, 2, 30}, {PropMeshes.CONE, 1, 30}, {PropMeshes.FLOWER_PLANTER, 0, 30}};
    private static final String[] NAMES = {"saguaro", "butte", "barrel cacti", "rocks", "joshua tree", "scrub", "dry grass", "pebbles",
            "building", "tower", "lamp", "street tree", "parked car", "bus stop", "bench", "hydrant", "bin", "sign", "cone",
            "flower planter", "pine grove", "pine", "aspen", "rock outcrop", "boulders", "cabin", "dock", "canoe", "fallen log",
            "fern", "wildflowers", "grass"};

    private CityShots() { }

    /** Draws and saves {@code t30_city_closeups.png}. */
    static File shootCloseups() throws IOException {
        return sheet(Themes.CITY, ITEMS, "t30_city_closeups", "City Lights props (software drawing: theme ramps, 3/4 view)", 0xFFDCE6EE);
    }

    /** One sheet of close-ups of a theme's props: {@code items} are {kind, variant, yaw degrees}. */
    static File sheet(Theme th, int[][] items, String file, String title, int background) throws IOException {
        PaletteSwatches pal = new PaletteSwatches();
        th.fillPalette(pal);
        // block index -> {dark, light} colours of the ramp
        Map<Integer, int[]> ramps = new HashMap<>();
        for (int r = 0; r < th.props.length; r++) {
            int[] sw = th.propSwatches(r);
            for (int i = 0; i < sw.length; i++) ramps.put(sw[i], new int[] {th.props[r].colors[2 * i], th.props[r].colors[2 * i + 1]});
        }
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(background);
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(0xFF1B1B1B);
        txt.setTextSize(36f);
        c.drawText(title, 30f, 48f, txt);
        txt.setTextSize(22f);
        int rows = (items.length + COLS - 1) / COLS;
        int cw = W / COLS, ch = (H - TOP) / rows;
        for (int i = 0; i < items.length; i++) {
            int kind = items[i][0], variant = items[i][1];
            int rule = 0;
            for (int r = 0; r < th.props.length; r++) if (th.props[r].kind == kind) rule = r;
            MeshData m = PropMeshes.build(kind, variant, pal, th.propSwatches(rule));
            float height = 0f, reach = 0f;
            MeshData.Part p = m.parts[0];
            for (int v = 0; v < p.vertexCount; v++) {
                height = Math.max(height, p.vertices[v * 8 + 1]);
                reach = Math.max(reach, (float) Math.hypot(p.vertices[v * 8], p.vertices[v * 8 + 2]));
            }
            float extent = Math.max(height, 2f * reach);
            float cx = (i % COLS) * cw + cw / 2f, base = TOP + (i / COLS) * ch + ch - 40f;
            draw(c, p, ramps, items[i][2], 14f, 0.8f * (ch - 70f) / extent, cx, base, th);
            c.drawText(NAMES[kind] + " v" + variant + "  " + m.triangleCount() + " tris", cx - cw / 2f + 14f, base + 30f, txt);
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

    /** One part with its base centre at (cx, base) on the picture; {@code px} pixels per metre. */
    private static void draw(Canvas c, MeshData.Part p, Map<Integer, int[]> ramps, float yawDeg, float tiltDeg, float px, float cx,
                             float base, Theme th) {
        double ya = Math.toRadians(yawDeg), ta = Math.toRadians(tiltDeg);
        double cyw = Math.cos(ya), syw = Math.sin(ya), ct = Math.cos(ta), st = Math.sin(ta);
        float[] sun = th.sunDir;
        float sl = (float) Math.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]);
        int n = p.vertexCount;
        final float[] x = new float[n], y = new float[n], z = new float[n], light = new float[n];
        for (int v = 0; v < n; v++) {
            float[] q = rot(p.vertices[v * 8], p.vertices[v * 8 + 1], p.vertices[v * 8 + 2], cyw, syw, ct, st);
            float[] nn = rot(p.vertices[v * 8 + 3], p.vertices[v * 8 + 4], p.vertices[v * 8 + 5], cyw, syw, ct, st);
            // the sun is fixed to the world, so it is turned the same way as the object is not: use the unrotated normal
            float d = -(p.vertices[v * 8 + 3] * sun[0] + p.vertices[v * 8 + 4] * sun[1] + p.vertices[v * 8 + 5] * sun[2]) / sl;
            light[v] = Math.max(0.35f, 0.62f + 0.55f * Math.max(d, 0f) + 0.1f * nn[1]);
            x[v] = cx + q[0] * px;
            y[v] = base - q[1] * px;
            z[v] = q[2];
        }
        final short[] idx = p.indices;
        int tris = idx.length / 3;
        Integer[] order = new Integer[tris];
        for (int t = 0; t < tris; t++) order[t] = t;
        Arrays.sort(order, (a, b) -> Float.compare(
                z[idx[a * 3] & 0xFFFF] + z[idx[a * 3 + 1] & 0xFFFF] + z[idx[a * 3 + 2] & 0xFFFF],
                z[idx[b * 3] & 0xFFFF] + z[idx[b * 3 + 1] & 0xFFFF] + z[idx[b * 3 + 2] & 0xFFFF]));
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        for (int k = 0; k < tris; k++) {
            int t = order[k];
            int a = idx[t * 3] & 0xFFFF, b = idx[t * 3 + 1] & 0xFFFF, d = idx[t * 3 + 2] & 0xFFFF;
            float area = (x[b] - x[a]) * (y[d] - y[a]) - (x[d] - x[a]) * (y[b] - y[a]);
            if (area >= 0f) continue;   // back face
            float u = (p.vertices[a * 8 + 6] + p.vertices[b * 8 + 6] + p.vertices[d * 8 + 6]) / 3f * 512f;
            float vv = p.vertices[a * 8 + 7] * 512f;
            int bx = (int) Math.floor(u / 16f), by = (int) Math.floor(vv / 16f) - 1;
            int[] pair = ramps.get(bx + 32 * by);
            if (pair == null) continue;
            float tr = Math.max(0f, Math.min(1f, (u - 16f * bx - 0.5f) / 15f));
            float l = (light[a] + light[b] + light[d]) / 3f;
            int col = 0xFF000000;
            for (int sh = 16, ch = 0; ch < 3; ch++, sh -= 8) {
                float lo = (pair[0] >> sh) & 255, hi = (pair[1] >> sh) & 255;
                col |= Math.min(255, Math.round((lo + (hi - lo) * tr) * l)) << sh;
            }
            path.reset();
            path.moveTo(x[a], y[a]);
            path.lineTo(x[b], y[b]);
            path.lineTo(x[d], y[d]);
            path.close();
            fill.setStyle(Paint.Style.FILL_AND_STROKE);
            fill.setStrokeWidth(0.8f);
            fill.setColor(col);
            c.drawPath(path, fill);
        }
    }

    private static float[] rot(float x, float y, float z, double cyw, double syw, double ct, double st) {
        double x1 = x * cyw + z * syw, z1 = -x * syw + z * cyw;
        double y2 = y * ct - z1 * st, z2 = y * st + z1 * ct;
        return new float[] {(float) x1, (float) y2, (float) z2};
    }
}
