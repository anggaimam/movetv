package com.wiggleplay.app.gl;

/**
 * Surface grain for the static world (T-29R Part R1): small tileable greyscale textures made in code (sand ripples, asphalt
 * speckle, grass streaks, gravel, concrete, moon dust), packed four to a 512 x 512 atlas of 256 x 256 tiles. The shader
 * multiplies the world colour by {@code 1 + strength x (d - 0.5) x 2}, so a texel of 0.5 changes nothing and every tile has
 * a mean of 0.5. Plain Java, deterministic by seed; made on the build thread.
 *
 * <p>Atlas layout: tile k sits at column {@code k % 2}, row {@code k / 2} (row 0 is the first row uploaded, v = 0).
 */
public final class DetailTexture {

    /** Kinds of grain. */
    public static final int SAND = 0, ASPHALT = 1, GRAVEL = 2, CONCRETE = 3, GRASS = 4, MOON = 5;
    /** Number of kinds. */
    public static final int KINDS = 6;
    /** Side of one tile and of the atlas. */
    public static final int TILE = 256, ATLAS = 512;
    /** Spread of the tile values around 0.5 (one standard deviation). */
    private static final float SPREAD = 0.16f;

    private DetailTexture() { }

    /**
     * One tile.
     * @param kind {@link #SAND} to {@link #MOON}
     * @param seed any number; the same seed gives the same tile
     * @return 256 x 256 bytes, row by row, mean about 128; the left edge continues the right edge and the top the bottom
     */
    public static byte[] tile(int kind, long seed) {
        float[] v = raw(kind, seed);
        int n = v.length;
        double mean = 0;
        for (float x : v) mean += x;
        mean /= n;
        double var = 0;
        for (float x : v) var += (x - mean) * (x - mean);
        double sd = Math.sqrt(var / n);
        if (sd < 1e-6) sd = 1;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            float d = 0.5f + (float) ((v[i] - mean) / sd) * SPREAD;
            out[i] = (byte) Math.round(Math.max(0f, Math.min(1f, d)) * 255f);
        }
        return out;
    }

    /**
     * Four tiles packed into one atlas.
     * @param kinds the kind of tile 0 to 3 (four entries)
     * @param seed any number
     * @return 512 x 512 bytes, row by row
     */
    public static byte[] atlas(int[] kinds, long seed) {
        if (kinds.length != 4) throw new IllegalArgumentException("four kinds are needed");
        byte[] out = new byte[ATLAS * ATLAS];
        for (int k = 0; k < 4; k++) {
            byte[] t = tile(kinds[k], seed * 31L + k);
            int x0 = (k % 2) * TILE, y0 = (k / 2) * TILE;
            for (int y = 0; y < TILE; y++) System.arraycopy(t, y * TILE, out, (y0 + y) * ATLAS + x0, TILE);
        }
        return out;
    }

    // ---- the kinds ----

    private static float[] raw(int kind, long seed) {
        float[] v = new float[TILE * TILE];
        long s = seed * 7919L + kind * 104729L;
        for (int y = 0; y < TILE; y++) {
            float fy = y / (float) TILE;
            for (int x = 0; x < TILE; x++) {
                float fx = x / (float) TILE;
                v[y * TILE + x] = value(kind, fx, fy, x, y, s);
            }
        }
        return v;
    }

    private static float value(int kind, float fx, float fy, int px, int py, long s) {
        switch (kind) {
            case SAND: {
                float grain = hash(px, py, s + 1);
                float fine = fbm(fx, fy, 32, 3, s + 2);
                float warp = noise(fx, fy, 4, 4, s + 3);
                float ripple = 0.5f + 0.5f * (float) Math.sin(2.0 * Math.PI * (5f * fy + 0.7f * warp));
                float broad = fbm(fx, fy, 4, 2, s + 4);
                return 0.30f * grain + 0.25f * fine + 0.35f * ripple + 0.10f * broad;
            }
            case ASPHALT: {
                float grain = hash(px, py, s + 1);
                float fine = fbm(fx, fy, 64, 2, s + 2);
                float speck = hash(px, py, s + 4) > 0.985f ? 1f : 0f;
                // no blotches: the road's mottling and wear are in the vertex colours, where they follow the road
                return 0.45f * grain + 0.30f * fine + 0.45f * speck;
            }
            case GRASS: {
                float blade = noise(fx, fy, 64, 6, s + 1);
                float blade2 = noise(fx, fy, 32, 4, s + 2);
                float grain = hash(px, py, s + 3);
                float clump = fbm(fx, fy, 6, 2, s + 4);
                return 0.40f * blade + 0.25f * blade2 + 0.15f * grain + 0.20f * clump;
            }
            case GRAVEL: {
                float a = noise(fx, fy, 48, 48, s + 1);
                float b = noise(fx, fy, 24, 24, s + 2);
                float stones = smooth01((a - 0.45f) * 4f) * 0.6f + smooth01((b - 0.5f) * 4f) * 0.4f;
                return 0.7f * stones + 0.3f * hash(px, py, s + 3);
            }
            case CONCRETE: {
                float fine = fbm(fx, fy, 16, 3, s + 1);
                float pit = hash(px, py, s + 2) > 0.995f ? 1f : 0f;
                return 0.8f * fine + 0.2f * hash(px, py, s + 3) - 0.5f * pit;
            }
            default: {   // MOON
                float broad = fbm(fx, fy, 6, 3, s + 1);
                float fine = fbm(fx, fy, 48, 2, s + 2);
                float pit = noise(fx, fy, 12, 12, s + 3);
                float dip = Math.max(0f, pit - 0.7f) * 3f;
                return 0.55f * broad + 0.3f * fine - 0.35f * dip + 0.15f * hash(px, py, s + 4);
            }
        }
    }

    private static float smooth01(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }

    /** Value noise with {@code periodX} x {@code periodY} lattice cells over the tile (so it wraps), 0..1. */
    private static float noise(float fx, float fy, int periodX, int periodY, long s) {
        float x = fx * periodX, y = fy * periodY;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float tx = x - ix, ty = y - iy;
        tx = tx * tx * (3f - 2f * tx);
        ty = ty * ty * (3f - 2f * ty);
        int x0 = Math.floorMod(ix, periodX), x1 = Math.floorMod(ix + 1, periodX);
        int y0 = Math.floorMod(iy, periodY), y1 = Math.floorMod(iy + 1, periodY);
        float a = hash(x0, y0, s), b = hash(x1, y0, s), c = hash(x0, y1, s), d = hash(x1, y1, s);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
    }

    /** Several octaves of {@link #noise}, doubling the lattice each time, 0..1. */
    private static float fbm(float fx, float fy, int period, int octaves, long s) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        int p = period;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(fx, fy, p, p, s + o * 17L);
            norm += amp;
            amp *= 0.5f;
            p *= 2;
        }
        return sum / norm;
    }

    /** A repeatable random number 0..1 for a lattice point. */
    private static float hash(int x, int y, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
