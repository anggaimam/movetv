package com.wiggleplay.app.games.race;

/** Small deterministic 2D value noise for the world builders (terrain, road mottling, prop shapes). Plain Java, no allocation. */
final class Noise {

    private Noise() { }

    /** A hash of an integer lattice point and a seed, 0 to 1. */
    static float hash(int x, int z, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + z * 0x85157AF5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (float) 0x1000000;
    }

    /** Smooth value noise, 0 to 1, one lattice cell per unit. */
    static float noise(float x, float z, long seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        float ux = fx * fx * (3f - 2f * fx), uz = fz * fz * (3f - 2f * fz);
        float a = hash(xi, zi, seed), b = hash(xi + 1, zi, seed), c = hash(xi, zi + 1, seed), d = hash(xi + 1, zi + 1, seed);
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uz;
    }

    /** Fractal noise of {@code octaves} octaves, 0 to 1. */
    static float fbm(float x, float z, int octaves, long seed) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(x, z, seed + o * 31L);
            norm += amp;
            x *= 2.03f;
            z *= 2.03f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    static float smoothstep(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }
}
