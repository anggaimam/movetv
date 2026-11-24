package com.wiggleplay.app.shots;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.ProcMesh;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * A tiny software renderer for the screenshot rig, which has no OpenGL: draws a proc mesh with the same light as the
 * race shader (ambient 0.62 + 0.38 of the sun) and the swatch colours of {@link ProcMesh#SWATCH_RGB}, so a coin or star can
 * be looked at on the {@code screenshots} branch. Painter's algorithm, back faces culled, perspective camera 3 units away.
 */
final class MeshPreview {

    private static final float SX = -0.4f, SY = -0.8f, SZ = -0.45f;

    private MeshPreview() { }

    /** One mesh drawn at several turns about the Y axis, side by side on a sky-blue picture. */
    static File shootTurns(String name, MeshData.Part p, float[] yawDeg, float tiltDeg, float scale, int w, int h)
            throws IOException {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFA8DDF2);
        int cell = w / yawDeg.length;
        for (int i = 0; i < yawDeg.length; i++) {
            draw(c, p, yawDeg[i], tiltDeg, scale, i * cell + cell / 2f, h / 2f, cell);
        }
        File dir = new File("build/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File out = new File(dir, name + ".png");
        try (FileOutputStream os = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + out.getAbsolutePath() + " " + out.length() + " bytes");
        return out;
    }

    private static void draw(Canvas c, MeshData.Part p, float yawDeg, float tiltDeg, float scale, float cx, float cy,
                             int cell) {
        double ya = Math.toRadians(yawDeg), ta = Math.toRadians(tiltDeg);
        double cyw = Math.cos(ya), syw = Math.sin(ya), ct = Math.cos(ta), st = Math.sin(ta);
        float len = (float) Math.sqrt(SX * SX + SY * SY + SZ * SZ);
        int n = p.vertexCount;
        final float[] x = new float[n], y = new float[n], z = new float[n], light = new float[n];
        for (int v = 0; v < n; v++) {
            float[] q = rot(p.vertices[v * 8], p.vertices[v * 8 + 1], p.vertices[v * 8 + 2], cyw, syw, ct, st);
            float[] nn = rot(p.vertices[v * 8 + 3], p.vertices[v * 8 + 4], p.vertices[v * 8 + 5], cyw, syw, ct, st);
            float d = -(nn[0] * SX + nn[1] * SY + nn[2] * SZ) / len;
            light[v] = 0.62f + 0.38f * Math.max(d, 0f);
            float persp = 3f / (3f - q[2]);
            x[v] = cx + q[0] * persp * scale * cell;
            y[v] = cy - q[1] * persp * scale * cell;
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
            if (area >= 0f) continue;   // back face (screen y points down, so front faces have negative area)
            int swatch = (int) (p.vertices[a * 8 + 6] * 512f) / ProcMesh.SWATCH_BLOCK;
            int rgb = ProcMesh.SWATCH_RGB[swatch];
            float l = (light[a] + light[b] + light[d]) / 3f;
            int r = Math.min(255, Math.round(((rgb >> 16) & 255) * l));
            int g = Math.min(255, Math.round(((rgb >> 8) & 255) * l));
            int bl = Math.min(255, Math.round((rgb & 255) * l));
            int col = 0xFF000000 | (r << 16) | (g << 8) | bl;
            path.reset();
            path.moveTo(x[a], y[a]);
            path.lineTo(x[b], y[b]);
            path.lineTo(x[d], y[d]);
            path.close();
            fill.setStyle(Paint.Style.FILL_AND_STROKE);   // the thin outline closes hairline gaps between neighbours
            fill.setStrokeWidth(0.8f);
            fill.setColor(col);
            c.drawPath(path, fill);
        }
    }

    /** Rotates about Y by yaw, then about X by tilt. */
    private static float[] rot(float x, float y, float z, double cyw, double syw, double ct, double st) {
        double x1 = x * cyw + z * syw, z1 = -x * syw + z * cyw;
        double y2 = y * ct - z1 * st, z2 = y * st + z1 * ct;
        return new float[] {(float) x1, (float) y2, (float) z2};
    }
}
