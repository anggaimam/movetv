package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.Mat4;
import com.wiggleplay.app.gl.StaticBatcher;

import java.util.List;
import java.util.Random;

/**
 * Lays a theme's props along a track by fixed rules (T-29 Part B): the same layout every time for the same track, seed and
 * options. Plain Java; runs once when the scene loads. Use a spline built in the forward direction (the scene does) so the
 * layout is the same in both driving directions.
 *
 * <p>A prop is rejected (new random values, up to 4 retries, then skipped) if it is closer than
 * {@code 6.4 + footprint + 0.5} to any centre line sample (6.4 = Easy half-width 5.5 + barrier 0.9, so it never touches a
 * barrier on any level), inside the start zone, too close to another prop, or farther than 40 m from the road (fog would hide
 * it). The footprint is the larger of the rule's footprint and the mesh's real reach, times the prop's scale.
 */
public final class PropScatter {

    /** Easy half-width (5.5) plus the barrier (0.9). */
    public static final float BARRIER_EDGE = 6.4f;
    /** Clear distance kept between a prop and the barrier. */
    public static final float BARRIER_GAP = 0.5f;
    /** Start zone: along the lap and from the centre line. */
    public static final float START_ALONG = 30f, START_SIDE = 15f;
    /** Props farther than this from every centre line sample are skipped. */
    public static final float SEEN_RANGE = 40f;
    /** Tries per prop: the first and four retries. */
    public static final int TRIES = 5;

    private PropScatter() { }

    /**
     * Places the props of a theme along a track.
     * @param t the track (forward direction)
     * @param th the theme
     * @param seed for example {@code trackId.hashCode()}
     * @param includeTierB false to leave the filler tier out (LOW quality, 2P)
     * @param out receives one placement per prop, mesh name {@code prop_<kind>_<variant>}, cells of 10 m
     * @return the number of props placed
     */
    public static int scatter(TrackSpline t, Theme th, long seed, boolean includeTierB, List<StaticBatcher.Placement> out) {
        return scatter(t, th, seed, includeTierB, out, null, null);
    }

    /** One placed prop: what, where (y is the base on the terrain), how it is turned and how big. */
    public static final class Instance {
        public final int kind, variant;
        public final float x, y, z, yaw, scale, footprint;

        Instance(int kind, int variant, float x, float y, float z, float yaw, float scale, float footprint) {
            this.kind = kind;
            this.variant = variant;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.scale = scale;
            this.footprint = footprint;
        }
    }

    /** How far the terrain under a prop may vary before the prop would float or sink (m), and how far its base is sunk in. */
    public static final float MAX_SLOPE = 0.6f, SINK = 0.1f;

    /**
     * Places the props of a theme along a track, on a terrain (T-29R R2).
     * @param terrain the ground the props stand on, or null for a flat world at y = 0
     * @param instances receives one {@link Instance} per prop (may be null)
     */
    public static int scatter(TrackSpline t, Theme th, long seed, boolean includeTierB, List<StaticBatcher.Placement> out,
                              Terrain terrain, List<Instance> instances) {
        Random rnd = new Random(seed * 131L + 17L);
        float len = t.lapLength();
        int count = t.sampleCount();
        float ds = len / count;
        int startSamples = (int) Math.ceil(START_ALONG / ds);
        float[] px = new float[256], pz = new float[256], pr = new float[256];
        int placed = 0;
        float[] sm = new float[3];
        float[] rot = new float[16], sc = new float[16], rs = new float[16], tr = new float[16];
        for (Theme.PropRule rule : th.props) {
            if (rule.tierB && !includeTierB) continue;
            int n = Math.round(rule.per100m * len / 100f);
            float reach = Math.max(rule.footprint, PropMeshes.reach(rule.kind));
            for (int k = 0; k < n; k++) {
                for (int attempt = 0; attempt < TRIES; attempt++) {
                    float s = (k + rnd.nextFloat()) * len / n;
                    boolean left = rnd.nextBoolean();
                    float d = rule.minDist + rnd.nextFloat() * (rule.maxDist - rule.minDist);
                    float angle = rnd.nextFloat() * 6.2831855f;
                    float scale = rule.minScale + rnd.nextFloat() * (rule.maxScale - rule.minScale);
                    int variant = rnd.nextInt(PropMeshes.variants(rule.kind));
                    t.sample(s, sm);
                    float side = left ? -d : d;
                    float x = sm[0] + (float) Math.cos(sm[2]) * side;
                    float z = sm[1] - (float) Math.sin(sm[2]) * side;
                    float fp = reach * scale;
                    if (!accept(t, x, z, fp, startSamples, px, pz, pr, placed)) continue;
                    if (rule.tallAbove > 0f && PropMeshes.height(rule.kind, variant) * scale > rule.tallAbove
                            && nearestRoad(t, x, z) < rule.tallMinDist) continue;
                    float yaw = angle;
                    if (rule.orient == Theme.ORIENT_FACE_ROAD) {
                        yaw = left ? sm[2] + 1.5707964f : sm[2] - 1.5707964f;
                    } else if (rule.orient == Theme.ORIENT_PARALLEL) {
                        yaw = sm[2] + (rnd.nextBoolean() ? 0f : 3.1415927f) + (rnd.nextFloat() - 0.5f) * 0.1f;
                    }
                    float y = 0f;
                    if (terrain != null) {
                        y = terrain.surface(x, z);
                        float lo = y, hi = y, rr = Math.min(fp, 3f) * 0.7f;
                        for (int q = 0; q < 8; q++) {
                            float h = terrain.surface(x + (float) Math.cos(q * 0.785398) * rr, z + (float) Math.sin(q * 0.785398) * rr);
                            lo = Math.min(lo, h);
                            hi = Math.max(hi, h);
                        }
                        if (hi - lo > MAX_SLOPE) continue;
                        y -= SINK;
                    }
                    if (placed == px.length) {
                        px = java.util.Arrays.copyOf(px, placed * 2);
                        pz = java.util.Arrays.copyOf(pz, placed * 2);
                        pr = java.util.Arrays.copyOf(pr, placed * 2);
                    }
                    px[placed] = x;
                    pz[placed] = z;
                    pr[placed] = fp;
                    placed++;
                    Mat4.rotY(rot, yaw);
                    Mat4.scale(sc, scale, scale, scale);
                    Mat4.multiply(rs, rot, sc);
                    Mat4.translate(tr, x, y, z);
                    float[] m = new float[16];
                    Mat4.multiply(m, tr, rs);
                    out.add(new StaticBatcher.Placement(PropMeshes.name(rule.kind, variant), m, (int) Math.floor(x / 10f),
                            (int) Math.floor(z / 10f)));
                    if (instances != null) instances.add(new Instance(rule.kind, variant, x, y + SINK, z, yaw, scale, fp));
                    break;
                }
            }
        }
        return placed;
    }

    /** Distance from a point to the nearest centre line sample. */
    static float nearestRoad(TrackSpline t, float x, float z) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < t.sampleCount(); i++) {
            float dx = t.sampleX(i) - x, dz = t.sampleZ(i) - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return (float) Math.sqrt(best);
    }

    private static boolean accept(TrackSpline t, float x, float z, float fp, int startSamples, float[] px, float[] pz,
                                  float[] pr, int placed) {
        int count = t.sampleCount();
        float near2 = Float.MAX_VALUE;
        float clear = BARRIER_EDGE + fp + BARRIER_GAP;
        float clear2 = clear * clear;
        float start2 = START_SIDE * START_SIDE;
        for (int i = 0; i < count; i++) {
            float dx = t.sampleX(i) - x, dz = t.sampleZ(i) - z;
            float d2 = dx * dx + dz * dz;
            if (d2 < clear2) return false;
            if (d2 < start2 && (i < startSamples || i >= count - startSamples)) return false;
            if (d2 < near2) near2 = d2;
        }
        if (near2 > SEEN_RANGE * SEEN_RANGE) return false;
        for (int j = 0; j < placed; j++) {
            float dx = px[j] - x, dz = pz[j] - z;
            float min = pr[j] + fp + 1f;
            if (dx * dx + dz * dz < min * min) return false;
        }
        return true;
    }
}
