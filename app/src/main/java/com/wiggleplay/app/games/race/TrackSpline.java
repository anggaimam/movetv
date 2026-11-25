package com.wiggleplay.app.games.race;

/**
 * A smooth closed road made from control points (T-28 Part A). The centre line is a closed centripetal Catmull-Rom
 * curve (alpha 0.5) through the points, resampled at equal arc length (about 0.5 m). It gives the same services as
 * the old tile track (sample, project, lap length) in either driving direction. Plain Java, no Android classes; every
 * array is allocated in {@link #build}, {@code sample} and {@code project} allocate nothing.
 *
 * <p>Coordinates: +Y up, yaw 0 = +Z, the position moves by (sin yaw, cos yaw), turning right lowers the yaw. The right
 * of a heading, seen from a chase camera behind the truck, is (-cos yaw, sin yaw) in (x, z). Offsets are positive to
 * the right.
 */
public final class TrackSpline implements TrackPath {

    private static final int STEPS_PER_SEGMENT = 200;
    private static final float SAMPLE_STEP = 0.5f;
    private static final int SEARCH = 40;
    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = 2f * (float) Math.PI;
    /** Half of the 8-sample window (4 m) the radius is measured over. */
    private static final int RADIUS_HALF = 4;

    private final float halfWidth;
    private final float lapLength;
    private final int count;
    private final float ds;
    private final float[] x, z, yaw, radius, fwdX, fwdZ;
    private final float minRadius;
    private RaceLevel level;
    private RoadObjects objects = RoadObjects.NONE;

    private TrackSpline(float halfWidth, float lapLength, int count, float[] x, float[] z) {
        this.halfWidth = halfWidth;
        this.lapLength = lapLength;
        this.count = count;
        this.ds = lapLength / count;
        this.x = x;
        this.z = z;
        this.yaw = new float[count];
        this.radius = new float[count];
        this.fwdX = new float[count];
        this.fwdZ = new float[count];
        for (int i = 0; i < count; i++) {
            int a = Math.floorMod(i - 1, count), b = (i + 1) % count;
            double y = Math.atan2(x[b] - x[a], z[b] - z[a]);
            yaw[i] = (float) y;
            fwdX[i] = (float) Math.sin(y);
            fwdZ[i] = (float) Math.cos(y);
        }
        float min = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float d = angleDiff(yaw[(i + RADIUS_HALF) % count], yaw[Math.floorMod(i - RADIUS_HALF, count)]);
            radius[i] = Math.abs(d) < 1e-5f ? Float.MAX_VALUE : 2f * RADIUS_HALF * ds / d;
            min = Math.min(min, Math.abs(radius[i]));
        }
        this.minRadius = min;
    }

    /**
     * Builds the road once.
     * @param def the track shape
     * @param reversed true to drive the same road the other way: the control list becomes
     *        {@code [p0, pN-1, ..., p2, p1]}, so the road starts at the same line heading east, and left and right
     *        bends swap
     * @param halfWidth half the width of the drivable road (given back by {@link #roadHalfWidth})
     * @return the spline
     */
    public static TrackSpline build(TrackDef def, boolean reversed, float halfWidth) {
        int n = def.pointCount();
        double[] px = new double[n], pz = new double[n];
        for (int i = 0; i < n; i++) {
            int src = reversed && i > 0 ? n - i : i;
            px[i] = def.control[src * 2];
            pz[i] = def.control[src * 2 + 1];
        }
        int total = n * STEPS_PER_SEGMENT;
        double[] rx = new double[total + 1], rz = new double[total + 1], cum = new double[total + 1];
        double[] out = new double[2];
        for (int i = 0; i < n; i++) {
            int i0 = Math.floorMod(i - 1, n), i2 = (i + 1) % n, i3 = (i + 2) % n;
            for (int k = 0; k < STEPS_PER_SEGMENT; k++) {
                catmullRom(px[i0], pz[i0], px[i], pz[i], px[i2], pz[i2], px[i3], pz[i3],
                        (double) k / STEPS_PER_SEGMENT, out);
                rx[i * STEPS_PER_SEGMENT + k] = out[0];
                rz[i * STEPS_PER_SEGMENT + k] = out[1];
            }
        }
        rx[total] = rx[0];
        rz[total] = rz[0];
        for (int i = 1; i <= total; i++) cum[i] = cum[i - 1] + Math.hypot(rx[i] - rx[i - 1], rz[i] - rz[i - 1]);
        double len = cum[total];
        int m = (int) Math.round(len / SAMPLE_STEP);
        double step = len / m;
        float[] sx = new float[m], sz = new float[m];
        int j = 0;
        for (int i = 0; i < m; i++) {
            double s = i * step;
            while (j < total - 1 && cum[j + 1] <= s) j++;
            double span = cum[j + 1] - cum[j];
            double t = span > 0 ? (s - cum[j]) / span : 0;
            sx[i] = (float) (rx[j] + (rx[j + 1] - rx[j]) * t);
            sz[i] = (float) (rz[j] + (rz[j + 1] - rz[j]) * t);
        }
        return new TrackSpline(halfWidth, (float) len, m, sx, sz);
    }

    /**
     * Builds the road for a level: the half-width comes from the level, and {@link #coinSlots} lays the coins out for it.
     * @param def the track shape
     * @param reversed true to drive the road the other way
     * @param level the level (half-width, coin and can rules)
     * @return the spline
     */
    public static TrackSpline build(TrackDef def, boolean reversed, RaceLevel level) {
        return build(def, reversed, level, RoadObjects.RAMPS_ONLY);
    }

    /**
     * Builds the road for a level with a chosen set of road objects.
     * @param def the track shape
     * @param reversed true to drive the road the other way
     * @param level the level
     * @param kinds the object kinds to include: {@link RoadObjects#RAMPS_ONLY}, {@link RoadObjects#ALL}, or 0 for a road without objects
     * @return the spline
     */
    public static TrackSpline build(TrackDef def, boolean reversed, RaceLevel level, int kinds) {
        TrackSpline t = build(def, reversed, level.halfWidth);
        t.level = level;
        t.objects = RoadObjects.of(def, reversed, level, level.halfWidth, kinds);
        return t;
    }

    /**
     * Coin, star, nitro-can and magnet positions for one lap, laid out by {@link CoinPlanner}.
     * @param seed layout seed
     * @param nitroCans nitro cans per lap
     * @param magnets 0 or 1 magnet per lap
     * @return the slots, sorted by distance along the lap
     * @throws IllegalStateException if the spline was built from a bare half-width and not from a level
     */
    @Override
    public RoadObjects roadObjects() {
        return objects;
    }

    @Override
    public Slots coinSlots(long seed, int nitroCans, int magnets) {
        if (level == null) throw new IllegalStateException("coinSlots needs a spline built with a RaceLevel");
        return CoinPlanner.plan(this, level, seed, nitroCans, magnets);
    }

    /**
     * The key of a track in saved settings and best-star records.
     * @param def the track shape
     * @param reversed true when driven the other way
     * @return the id, or the id plus {@code -r} when reversed
     */
    public static String key(TrackDef def, boolean reversed) {
        return reversed ? def.id + "-r" : def.id;
    }

    /** One point of a centripetal Catmull-Rom segment from p1 to p2 (alpha 0.5), t = 0..1 along the segment. */
    private static void catmullRom(double x0, double z0, double x1, double z1, double x2, double z2, double x3,
                                   double z3, double u, double[] out) {
        double t0 = 0;
        double t1 = t0 + Math.sqrt(Math.max(Math.hypot(x1 - x0, z1 - z0), 1e-6));
        double t2 = t1 + Math.sqrt(Math.max(Math.hypot(x2 - x1, z2 - z1), 1e-6));
        double t3 = t2 + Math.sqrt(Math.max(Math.hypot(x3 - x2, z3 - z2), 1e-6));
        double t = t1 + (t2 - t1) * u;
        double a1x = (t1 - t) / (t1 - t0) * x0 + (t - t0) / (t1 - t0) * x1;
        double a1z = (t1 - t) / (t1 - t0) * z0 + (t - t0) / (t1 - t0) * z1;
        double a2x = (t2 - t) / (t2 - t1) * x1 + (t - t1) / (t2 - t1) * x2;
        double a2z = (t2 - t) / (t2 - t1) * z1 + (t - t1) / (t2 - t1) * z2;
        double a3x = (t3 - t) / (t3 - t2) * x2 + (t - t2) / (t3 - t2) * x3;
        double a3z = (t3 - t) / (t3 - t2) * z2 + (t - t2) / (t3 - t2) * z3;
        double b1x = (t2 - t) / (t2 - t0) * a1x + (t - t0) / (t2 - t0) * a2x;
        double b1z = (t2 - t) / (t2 - t0) * a1z + (t - t0) / (t2 - t0) * a2z;
        double b2x = (t3 - t) / (t3 - t1) * a2x + (t - t1) / (t3 - t1) * a3x;
        double b2z = (t3 - t) / (t3 - t1) * a2z + (t - t1) / (t3 - t1) * a3z;
        out[0] = (t2 - t) / (t2 - t1) * b1x + (t - t1) / (t2 - t1) * b2x;
        out[1] = (t2 - t) / (t2 - t1) * b1z + (t - t1) / (t2 - t1) * b2z;
    }

    private static float angleDiff(float to, float from) {
        float d = to - from;
        while (d > PI) d -= TWO_PI;
        while (d <= -PI) d += TWO_PI;
        return d;
    }

    // ------------------------------------------------------------------ the centre line

    /**
     * Length of one lap along the centre line.
     * @return world units (metres)
     */
    public float lapLength() {
        return lapLength;
    }

    /**
     * Half the width of the drivable road.
     * @return the value given to {@link #build}
     */
    public float roadHalfWidth() {
        return halfWidth;
    }

    /**
     * Number of centre line samples.
     * @return the count
     */
    public int sampleCount() {
        return count;
    }

    /**
     * X of a centre line sample.
     * @param i sample index, 0 to {@code sampleCount() - 1}
     * @return world x
     */
    public float sampleX(int i) {
        return x[i];
    }

    /**
     * Z of a centre line sample.
     * @param i sample index
     * @return world z
     */
    public float sampleZ(int i) {
        return z[i];
    }

    /**
     * Road direction at a centre line sample.
     * @param i sample index
     * @return yaw (0 = +Z, turning right lowers it)
     */
    public float sampleYaw(int i) {
        return yaw[i];
    }

    /**
     * Signed radius of the bend at a sample, measured over a 4 m window.
     * @param i sample index
     * @return the radius in metres; positive for a left bend, negative for a right bend, {@code Float.MAX_VALUE} on a straight
     */
    public float radiusAt(int i) {
        return radius[i];
    }

    /**
     * The tightest bend of the lap.
     * @return the smallest absolute radius in metres
     */
    public float minRadius() {
        return minRadius;
    }

    /**
     * A point on the centre line.
     * @param s distance along the lap from the start line; any value, it wraps
     * @param outXZYaw receives x, z and the yaw (at least 3 floats)
     */
    public void sample(float s, float[] outXZYaw) {
        float w = s % lapLength;
        if (w < 0f) w += lapLength;
        float f = w / ds;
        int i = (int) f;
        if (i >= count) i = count - 1;
        float t = f - i;
        int j = i + 1 == count ? 0 : i + 1;
        outXZYaw[0] = x[i] + (x[j] - x[i]) * t;
        outXZYaw[1] = z[i] + (z[j] - z[i]) * t;
        outXZYaw[2] = wrapYaw(yaw[i] + angleDiff(yaw[j], yaw[i]) * t);
    }

    private static float wrapYaw(float a) {
        if (a > PI) return a - TWO_PI;
        if (a <= -PI) return a + TWO_PI;
        return a;
    }

    /** f(i): how far the point is ahead of the tangent plane (the line across the road) of sample i. */
    private float ahead(int i, float px, float pz) {
        return (px - x[i]) * fwdX[i] + (pz - z[i]) * fwdZ[i];
    }

    /**
     * Finds where a point is relative to the centre line. It looks for the two samples whose tangent planes
     * (the lines across the road) lie either side of the point and interpolates between them, so {@code s} grows
     * smoothly for any offset smaller than the bend radius.
     * @param px world x
     * @param pz world z
     * @param hint the sample index returned last time (walks at most 40 samples either way), or a negative number to
     *        start from the nearest sample
     * @param out receives distance along the lap, signed offset (positive = right of the driving direction) and road direction
     * @return the index of the sample just behind the point, to pass as the next hint
     */
    public int project(float px, float pz, int hint, TrackPoint out) {
        int i;
        if (hint < 0) {
            i = 0;
            float bd = Float.MAX_VALUE;
            for (int k = 0; k < count; k++) {
                float dx = px - x[k], dz = pz - z[k];
                float d = dx * dx + dz * dz;
                if (d < bd) {
                    bd = d;
                    i = k;
                }
            }
        } else {
            i = hint % count;
        }
        for (int k = 0; k < SEARCH && ahead((i + 1) % count, px, pz) >= 0f; k++) i = (i + 1) % count;
        for (int k = 0; k < SEARCH && ahead(i, px, pz) < 0f; k++) i = i == 0 ? count - 1 : i - 1;
        int j = (i + 1) % count;
        float fi = ahead(i, px, pz), fj = ahead(j, px, pz);
        float den = fi - fj;
        float t = den > 1e-9f ? fi / den : 0f;
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;
        float s = (i + t) * ds;
        s %= lapLength;
        if (s < 0f) s += lapLength;
        float cx = x[i] + (x[j] - x[i]) * t, cz = z[i] + (z[j] - z[i]) * t;
        float ty = wrapYaw(yaw[i] + angleDiff(yaw[j], yaw[i]) * t);
        out.s = s;
        out.tangentYaw = ty;
        out.offset = (px - cx) * -(float) Math.cos(ty) + (pz - cz) * (float) Math.sin(ty);
        return i;
    }

    /**
     * Points of the centre line at equal distances, for the minimap and the track cards.
     * @param n number of points
     * @param outXZ receives n x, z pairs (at least {@code 2 * n} floats); point 0 is control point 0
     */
    public void minimap(int n, float[] outXZ) {
        float[] p = new float[3];
        for (int k = 0; k < n; k++) {
            sample(lapLength * k / n, p);
            outXZ[k * 2] = p[0];
            outXZ[k * 2 + 1] = p[1];
        }
    }
}
