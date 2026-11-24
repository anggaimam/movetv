package com.wiggleplay.app.shots;

import com.wiggleplay.app.gl.MeshData;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

/**
 * A tiny software renderer for the screenshot rig, which has no OpenGL: draws a proc mesh with the same light as the
 * race shader (ambient 0.62 + 0.38 of the sun) and colours from the palette texture, so a coin or star can be looked at
 * on the {@code screenshots} branch. Painter's algorithm, back faces culled, perspective camera 3 units away.
 */
final class MeshPreview {

    private static final float SX = -0.4f, SY = -0.8f, SZ = -0.45f;

    private MeshPreview() { }

    /** One mesh drawn at several turns about the Y axis, side by side on a sky-blue picture. */
    static File shootTurns(String name, MeshData.Part p, float[] yawDeg, float tiltDeg, float scale, int w, int h)
            throws IOException {
        BufferedImage palette = ImageIO.read(new File("src/main/assets/art/race/colormap.png"));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xA8DDF2));
        g.fillRect(0, 0, w, h);
        int cell = w / yawDeg.length;
        for (int i = 0; i < yawDeg.length; i++) {
            draw(g, palette, p, yawDeg[i], tiltDeg, scale, i * cell + cell / 2f, h / 2f, cell);
        }
        g.dispose();
        File dir = new File("build/screenshots");
        dir.mkdirs();
        File out = new File(dir, name + ".png");
        ImageIO.write(img, "png", out);
        return out;
    }

    private static void draw(Graphics2D g, BufferedImage palette, MeshData.Part p, float yawDeg, float tiltDeg,
                             float scale, float cx, float cy, int cell) {
        double ya = Math.toRadians(yawDeg), ta = Math.toRadians(tiltDeg);
        double cyw = Math.cos(ya), syw = Math.sin(ya), ct = Math.cos(ta), st = Math.sin(ta);
        float len = (float) Math.sqrt(SX * SX + SY * SY + SZ * SZ);
        int n = p.vertexCount;
        float[] x = new float[n], y = new float[n], z = new float[n], light = new float[n];
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
        int tris = p.indices.length / 3;
        Integer[] order = new Integer[tris];
        for (int t = 0; t < tris; t++) order[t] = t;
        final float[] zz = z;
        final short[] idx = p.indices;
        Arrays.sort(order, (a, b) -> Float.compare(
                (zz[idx[a * 3] & 0xFFFF] + zz[idx[a * 3 + 1] & 0xFFFF] + zz[idx[a * 3 + 2] & 0xFFFF]),
                (zz[idx[b * 3] & 0xFFFF] + zz[idx[b * 3 + 1] & 0xFFFF] + zz[idx[b * 3 + 2] & 0xFFFF])));
        for (int k = 0; k < tris; k++) {
            int t = order[k];
            int a = idx[t * 3] & 0xFFFF, b = idx[t * 3 + 1] & 0xFFFF, c = idx[t * 3 + 2] & 0xFFFF;
            float area = (x[b] - x[a]) * (y[c] - y[a]) - (x[c] - x[a]) * (y[b] - y[a]);
            if (area >= 0f) continue;   // back face (screen y points down, so front faces have negative area)
            int u = (int) (p.vertices[a * 8 + 6] * palette.getWidth()), v = (int) (p.vertices[a * 8 + 7] * palette.getHeight());
            int rgb = palette.getRGB(u, v);
            float l = (light[a] + light[b] + light[c]) / 3f;
            int r = Math.min(255, Math.round(((rgb >> 16) & 255) * l));
            int gr = Math.min(255, Math.round(((rgb >> 8) & 255) * l));
            int bl = Math.min(255, Math.round((rgb & 255) * l));
            g.setColor(new Color(r, gr, bl));
            int[] px = {Math.round(x[a]), Math.round(x[b]), Math.round(x[c])};
            int[] py = {Math.round(y[a]), Math.round(y[b]), Math.round(y[c])};
            g.fillPolygon(px, py, 3);
            g.drawPolygon(px, py, 3);   // closes hairline gaps between neighbours
        }
    }

    /** Rotates about Y by yaw, then about X by tilt. */
    private static float[] rot(float x, float y, float z, double cyw, double syw, double ct, double st) {
        double x1 = x * cyw + z * syw, z1 = -x * syw + z * cyw;
        double y2 = y * ct - z1 * st, z2 = y * st + z1 * ct;
        return new float[] {(float) x1, (float) y2, (float) z2};
    }
}
