package com.wiggleplay.app.games.race;

/**
 * The lakes of a track (T-30b): ellipses on the ground. {@code point(θ) = (cx + rx cosθ cos r − rz sinθ sin r, cz + rx cosθ sin r + rz
 * sinθ cos r)}. The edge is the outer edge of the light edge band. Plain Java; immutable.
 */
public final class Lakes {

    /** No lakes. */
    public static final Lakes NONE = new Lakes(new float[0]);
    /** Edge distance reported far from every lake. */
    public static final float FAR = 99f;
    private static final int POLY = 96;

    private final int count;
    private final float[] cx, cz, rx, rz, cosR, sinR, reach;
    private final float[][] polyX, polyZ;

    /**
     * Creates the lakes of a track.
     * @param data cx, cz, rx, rz, rotation in degrees, per lake
     */
    public Lakes(float[] data) {
        count = data.length / 5;
        cx = new float[count];
        cz = new float[count];
        rx = new float[count];
        rz = new float[count];
        cosR = new float[count];
        sinR = new float[count];
        reach = new float[count];
        polyX = new float[count][POLY];
        polyZ = new float[count][POLY];
        float[] o = new float[2];
        for (int i = 0; i < count; i++) {
            cx[i] = data[5 * i];
            cz[i] = data[5 * i + 1];
            rx[i] = data[5 * i + 2];
            rz[i] = data[5 * i + 3];
            double r = Math.toRadians(data[5 * i + 4]);
            cosR[i] = (float) Math.cos(r);
            sinR[i] = (float) Math.sin(r);
            reach[i] = Math.max(rx[i], rz[i]);
            for (int k = 0; k < POLY; k++) {
                point(i, (float) (2.0 * Math.PI * k / POLY), 1f, o);
                polyX[i][k] = o[0];
                polyZ[i][k] = o[1];
            }
        }
    }

    /**
     * The lakes of a track.
     * @param def a track
     * @return its lakes ({@link #NONE} without)
     */
    public static Lakes of(TrackDef def) {
        return def.lakes.length == 0 ? NONE : new Lakes(def.lakes);
    }

    /** Number of lakes. */
    public int count() {
        return count;
    }

    /** Centre x of lake i. */
    public float centreX(int i) {
        return cx[i];
    }

    /** Centre z of lake i. */
    public float centreZ(int i) {
        return cz[i];
    }

    /** Half size along the lake's own x axis. */
    public float radiusX(int i) {
        return rx[i];
    }

    /** Half size along the lake's own z axis. */
    public float radiusZ(int i) {
        return rz[i];
    }

    /**
     * A point of lake i's outline or inside it.
     * @param i the lake
     * @param theta angle round the ellipse (radians)
     * @param scale 1 for the edge, 0 for the centre
     * @param out receives x, z
     */
    public void point(int i, float theta, float scale, float[] out) {
        float a = rx[i] * (float) Math.cos(theta) * scale, b = rz[i] * (float) Math.sin(theta) * scale;
        out[0] = cx[i] + a * cosR[i] - b * sinR[i];
        out[1] = cz[i] + a * sinR[i] + b * cosR[i];
    }

    /**
     * Distance to the nearest lake edge, negative inside a lake.
     * @param x world x
     * @param z world z
     * @return metres; {@link #FAR} when no lake is within 40 m
     */
    public float edge(float x, float z) {
        float best = FAR;
        for (int i = 0; i < count; i++) best = Math.min(best, edge(i, x, z));
        return best;
    }

    /**
     * Distance to the edge of one lake, negative inside it.
     * @param i the lake
     * @param x world x
     * @param z world z
     * @return metres; {@link #FAR} when more than 40 m away
     */
    public float edge(int i, float x, float z) {
        float dx = x - cx[i], dz = z - cz[i];
        if (dx * dx + dz * dz > (reach[i] + 40f) * (reach[i] + 40f)) return FAR;
        float u = dx * cosR[i] + dz * sinR[i], v = -dx * sinR[i] + dz * cosR[i];
        boolean inside = (u * u) / (rx[i] * rx[i]) + (v * v) / (rz[i] * rz[i]) < 1f;
        float best = Float.MAX_VALUE;
        for (int k = 0; k < POLY; k++) {
            int k2 = (k + 1) % POLY;
            float ax = polyX[i][k], az = polyZ[i][k], bx = polyX[i][k2] - ax, bz = polyZ[i][k2] - az;
            float t = ((x - ax) * bx + (z - az) * bz) / (bx * bx + bz * bz);
            t = Math.max(0f, Math.min(1f, t));
            float ex = ax + bx * t - x, ez = az + bz * t - z;
            best = Math.min(best, ex * ex + ez * ez);
        }
        float d = (float) Math.sqrt(best);
        return inside ? -d : d;
    }
}
