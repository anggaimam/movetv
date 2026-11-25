package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The ground of a track as a height field (T-29R Part R2): flat at y = -0.02 within 8 m of the centre line, rolling away from the
 * road with the theme's profile, coloured per vertex from a ramp (darker in the troughs and away from the sun, a packed
 * shoulder next to the road). Plain Java; built once on the worker thread, cut into 60 m tiles that the batcher merges with the road
 * and the props.
 *
 * <p>Every triangle uses one swatch block (the ground ramp, or the shoulder ramp near the road) and its three vertices are its own,
 * so a triangle never mixes blocks. Normals are smooth, from the height grid. Triangles lying entirely under the road are left out.
 */
public final class Terrain {

    /** The shape and colours of a theme's ground. */
    public static final class Profile {
        /** Dune ridge direction: the wind blows along this angle (degrees); ridges run across it. */
        public final float windDeg;
        /** Dune height, the dune wavelength, the size of the small bumps (m). */
        public final float amplitude, wavelength, bump;
        /** Ground ramp, dark and light end. */
        public final int groundDark, groundLight;
        /** Packed shoulder ramp next to the road, dark and light end. */
        public final int shoulderDark, shoulderLight;

        public Profile(float windDeg, float amplitude, float wavelength, float bump, int groundDark, int groundLight,
                       int shoulderDark, int shoulderLight) {
            this.windDeg = windDeg;
            this.amplitude = amplitude;
            this.wavelength = wavelength;
            this.bump = bump;
            this.groundDark = groundDark;
            this.groundLight = groundLight;
            this.shoulderDark = shoulderDark;
            this.shoulderLight = shoulderLight;
        }
    }

    /** One 60 m tile: its mesh (world space) and the 10 m cell of its centre. */
    public static final class Tile {
        public final MeshData mesh;
        public final int cellX, cellZ;
        public final int triangles;

        Tile(MeshData mesh, int cellX, int cellZ, int triangles) {
            this.mesh = mesh;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.triangles = triangles;
        }
    }

    /** Height of the ground under the road, and the distances where the ground starts to rise and is fully shaped. */
    public static final float ROAD_Y = -0.02f, FLAT_D = 8f, FULL_D = 22f, TILE = 60f, MARGIN = 55f;
    /** Distances beyond this are not told apart (the shape has long since saturated). */
    private static final float D_MAX = 24f, HASH = 12f;

    private final Profile pr;
    private final long seed;
    private final float sunX, sunZ, cut, windX, windZ;
    private final float[] px, pz;
    private final int[] head, next;
    private final float hx0, hz0;
    private final int hw, hh;
    private final float cell;
    private final int cpt, tilesX, tilesZ;
    private final float x0, z0;
    private final int gw, gh;
    private float[] heights, tones, dists;
    private boolean ready;

    private Terrain(TrackSpline t, Theme th, long seed, boolean low) {
        this.pr = th.look.terrain;
        this.seed = seed;
        this.cell = low ? 4f : 2.5f;
        this.cpt = Math.round(TILE / cell);
        float len = (float) Math.sqrt(th.sunDir[0] * th.sunDir[0] + th.sunDir[2] * th.sunDir[2]);
        // horizontal direction towards the sun (the light travels along sunDir)
        sunX = len < 1e-6f ? 0f : -th.sunDir[0] / len;
        sunZ = len < 1e-6f ? 0f : -th.sunDir[2] / len;
        double a = Math.toRadians(pr.windDeg);
        windX = (float) Math.cos(a);
        windZ = (float) Math.sin(a);
        cut = Math.max(2f, t.roadHalfWidth() - 0.7f);
        // centre line samples every metre of the lap
        int n = Math.max(8, Math.round(t.lapLength()));
        px = new float[n];
        pz = new float[n];
        float[] o = new float[3];
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            t.sample(i * t.lapLength() / n, o);
            px[i] = o[0];
            pz[i] = o[1];
            minX = Math.min(minX, o[0]);
            maxX = Math.max(maxX, o[0]);
            minZ = Math.min(minZ, o[1]);
            maxZ = Math.max(maxZ, o[1]);
        }
        hx0 = minX - 2f * HASH;
        hz0 = minZ - 2f * HASH;
        hw = (int) Math.ceil((maxX - minX + 4f * HASH) / HASH) + 1;
        hh = (int) Math.ceil((maxZ - minZ + 4f * HASH) / HASH) + 1;
        head = new int[hw * hh];
        next = new int[n];
        Arrays.fill(head, -1);
        for (int i = 0; i < n; i++) {
            int c = (int) ((px[i] - hx0) / HASH) + hw * (int) ((pz[i] - hz0) / HASH);
            next[i] = head[c];
            head[c] = i;
        }
        x0 = (float) Math.floor((minX - MARGIN) / TILE) * TILE;
        z0 = (float) Math.floor((minZ - MARGIN) / TILE) * TILE;
        tilesX = (int) Math.ceil((maxX + MARGIN - x0) / TILE);
        tilesZ = (int) Math.ceil((maxZ + MARGIN - z0) / TILE);
        gw = tilesX * cpt + 1;
        gh = tilesZ * cpt + 1;
    }

    /**
     * Creates the terrain of a track.
     * @param forward the track in the forward direction (the same ground serves both driving directions)
     * @param th a theme with a terrain profile
     * @param seed for example {@code trackId.hashCode()}
     * @param low true for LOW quality (4 m cells)
     * @return the terrain
     * @throws IllegalArgumentException for a theme without a terrain profile
     */
    public static Terrain create(TrackSpline forward, Theme th, long seed, boolean low) {
        if (th.look.terrain == null) throw new IllegalArgumentException("theme " + th.id + " has no terrain");
        Terrain t = new Terrain(forward, th, seed, low);
        t.fill();
        return t;
    }

    /**
     * Distance to the nearest centre line sample, capped.
     * @param x world x
     * @param z world z
     * @return metres, at most 24
     */
    public float distance(float x, float z) {
        int cx = (int) Math.floor((x - hx0) / HASH), cz = (int) Math.floor((z - hz0) / HASH);
        float best = D_MAX * D_MAX;
        for (int dz = -2; dz <= 2; dz++) {
            int zz = cz + dz;
            if (zz < 0 || zz >= hh) continue;
            for (int dx = -2; dx <= 2; dx++) {
                int xx = cx + dx;
                if (xx < 0 || xx >= hw) continue;
                for (int i = head[xx + hw * zz]; i >= 0; i = next[i]) {
                    float ddx = px[i] - x, ddz = pz[i] - z;
                    float d2 = ddx * ddx + ddz * ddz;
                    if (d2 < best) best = d2;
                }
            }
        }
        return (float) Math.sqrt(best);
    }

    /**
     * The height of the ground (the analytic height field).
     * @param x world x
     * @param z world z
     * @return y in metres, {@code ROAD_Y} within 8 m of the road
     */
    public float height(float x, float z) {
        return heightAt(x, z, distance(x, z));
    }

    private float heightAt(float x, float z, float d) {
        float ramp = Noise.smoothstep(FLAT_D, FULL_D, d);
        if (ramp <= 0f) return ROAD_Y;
        float p = (x * windX + z * windZ) / pr.wavelength + 0.5f * (Noise.fbm(x / 90f, z / 90f, 2, seed + 5L) - 0.5f);
        float q = (-x * windZ + z * windX) / (pr.wavelength * 2.5f);
        float ridge = 1f - Math.abs(2f * Noise.noise(p, q, seed) - 1f);
        float body = pr.amplitude * (float) Math.pow(ridge, 1.4) * (0.55f + 0.45f * Noise.fbm(x / 70f, z / 70f, 2, seed + 9L));
        float bumps = pr.bump * 2f * (Noise.noise(x / 3f, z / 3f, seed + 3L) - 0.5f);
        return ROAD_Y + ramp * (Math.max(0f, body) + bumps + 0.02f);
    }

    private void fill() {
        heights = new float[gw * gh];
        tones = new float[gw * gh];
        float[] dist = new float[gw * gh];
        dists = dist;
        for (int j = 0; j < gh; j++) {
            for (int i = 0; i < gw; i++) {
                float x = x0 + i * cell, z = z0 + j * cell;
                float d = distance(x, z);
                dist[i + gw * j] = d;
                heights[i + gw * j] = heightAt(x, z, d);
            }
        }
        for (int j = 0; j < gh; j++) {
            for (int i = 0; i < gw; i++) {
                int ia = Math.max(0, i - 1), ib = Math.min(gw - 1, i + 1), ja = Math.max(0, j - 1), jb = Math.min(gh - 1, j + 1);
                float gx = (heights[ib + gw * j] - heights[ia + gw * j]) / ((ib - ia) * cell);
                float gz = (heights[i + gw * jb] - heights[i + gw * ja]) / ((jb - ja) * cell);
                float facing = Math.max(-1f, Math.min(1f, -(gx * sunX + gz * sunZ) * 1.5f));
                float x = x0 + i * cell, z = z0 + j * cell;
                float d = dist[i + gw * j];
                float t = 0.5f + 0.25f * (Noise.fbm(x / 18f, z / 18f, 2, seed + 21L) - 0.5f) * 2f
                        + 0.15f * Math.max(facing, 0f) - 0.2f * Math.max(-facing, 0f)
                        - 0.15f * (1f - Noise.smoothstep(FLAT_D, 16f, d));
                tones[i + gw * j] = Math.max(0f, Math.min(1f, t));
            }
        }
        ready = true;
    }

    /**
     * The height of the built ground at a point: the same surface the triangles make (bilinear over the grid), for putting things on it.
     * @param x world x
     * @param z world z
     * @return y in metres
     */
    public float surface(float x, float z) {
        float fx = (x - x0) / cell, fz = (z - z0) / cell;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        if (i < 0 || j < 0 || i >= gw - 1 || j >= gh - 1) return height(x, z);
        float u = fx - i, v = fz - j;
        float a = heights[i + gw * j], b = heights[i + 1 + gw * j], c = heights[i + gw * (j + 1)], d = heights[i + 1 + gw * (j + 1)];
        return (a + (b - a) * u) + ((c + (d - c) * u) - (a + (b - a) * u)) * v;
    }

    /**
     * The ground ramp position at a point (for shadows, which take the ground colour).
     * @param x world x
     * @param z world z
     * @return t in 0..1 of the ground ramp
     */
    public float tone(float x, float z) {
        float fx = (x - x0) / cell, fz = (z - z0) / cell;
        int i = Math.max(0, Math.min(gw - 1, Math.round(fx))), j = Math.max(0, Math.min(gh - 1, Math.round(fz)));
        return tones[i + gw * j];
    }

    /** Horizontal direction towards the sun, x and z (unit length). */
    public float[] sunToward() {
        return new float[] {sunX, sunZ};
    }

    /** The grid spacing (m). */
    public float cell() {
        return cell;
    }

    /**
     * Builds the tiles.
     * @param pal the palette (the theme's terrain swatches must be set)
     * @param groundSwatch swatch of the ground ramp
     * @param shoulderSwatch swatch of the shoulder ramp
     * @return the tiles that are near enough to the road to be seen
     */
    public List<Tile> build(PaletteSwatches pal, int groundSwatch, int shoulderSwatch) {
        if (!ready) throw new IllegalStateException("not filled");
        List<Tile> out = new ArrayList<>();
        float gv = pal.v(groundSwatch), sv = pal.v(shoulderSwatch);
        float[] nrm = new float[3];
        for (int tj = 0; tj < tilesZ; tj++) {
            for (int ti = 0; ti < tilesX; ti++) {
                float cx = x0 + (ti + 0.5f) * TILE, cz = z0 + (tj + 0.5f) * TILE;
                if (nearestSample(cx, cz) > 60f + 43f) continue;
                float[] v = new float[8 * cpt * cpt * 6];
                short[] ix = new short[3 * cpt * cpt * 2];
                int vc = 0, ic = 0;
                for (int j = 0; j < cpt; j++) {
                    for (int i = 0; i < cpt; i++) {
                        int gi = ti * cpt + i, gj = tj * cpt + j;
                        // (a, c, b) and (b, c, d) face up
                        int[][] cells = {{gi, gj, gi, gj + 1, gi + 1, gj}, {gi + 1, gj, gi, gj + 1, gi + 1, gj + 1}};
                        for (int k = 0; k < 2; k++) {
                            float dsum = 0f, dmax = 0f;
                            for (int m = 0; m < 3; m++) {
                                float d = dists[cells[k][m * 2] + gw * cells[k][m * 2 + 1]];
                                dsum += d;
                                dmax = Math.max(dmax, d);
                            }
                            if (dmax < cut) continue;   // under the road
                            boolean shoulder = dsum / 3f < FLAT_D + 0.5f;
                            int sw = shoulder ? shoulderSwatch : groundSwatch;
                            float tv = shoulder ? sv : gv;
                            for (int m = 0; m < 3; m++) {
                                int ci = cells[k][m * 2], cj = cells[k][m * 2 + 1];
                                normalAt(ci, cj, nrm);
                                float t = tones[ci + gw * cj];
                                if (shoulder) t = 0.35f + 0.5f * Noise.noise(ci * 0.7f, cj * 0.7f, seed + 77L);
                                int o = vc * 8;
                                v[o] = x0 + ci * cell;
                                v[o + 1] = heights[ci + gw * cj];
                                v[o + 2] = z0 + cj * cell;
                                v[o + 3] = nrm[0];
                                v[o + 4] = nrm[1];
                                v[o + 5] = nrm[2];
                                v[o + 6] = pal.uRamp(sw, t);
                                v[o + 7] = tv;
                                ix[ic++] = (short) vc;
                                vc++;
                            }
                        }
                    }
                }
                if (ic == 0) continue;
                MeshData.Part part = new MeshData.Part("terrain", -1, new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f, 1f},
                        new float[] {1f, 1f, 1f}, Arrays.copyOf(v, vc * 8), Arrays.copyOf(ix, ic), vc);
                out.add(new Tile(new MeshData(false, new MeshData.Part[] {part}), (int) Math.floor(cx / 10f),
                        (int) Math.floor(cz / 10f), ic / 3));
            }
        }
        return out;
    }

    private float nearestSample(float x, float z) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < px.length; i += 4) {
            float dx = px[i] - x, dz = pz[i] - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return (float) Math.sqrt(best);
    }

    private void normalAt(int i, int j, float[] out) {
        int ia = Math.max(0, i - 1), ib = Math.min(gw - 1, i + 1), ja = Math.max(0, j - 1), jb = Math.min(gh - 1, j + 1);
        float gx = (heights[ib + gw * j] - heights[ia + gw * j]) / ((ib - ia) * cell);
        float gz = (heights[i + gw * jb] - heights[i + gw * ja]) / ((jb - ja) * cell);
        float l = (float) Math.sqrt(gx * gx + 1f + gz * gz);
        out[0] = -gx / l;
        out[1] = 1f / l;
        out[2] = -gz / l;
    }

    /** Number of tile columns and rows (for tests). */
    int[] tileCounts() {
        return new int[] {tilesX, tilesZ};
    }
}
