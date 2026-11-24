package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Flat patches of a second ground colour (T-29 Part A), so the ground is not one flat colour. Each patch is a blob of 48
 * triangles (a centre fan of 16 and a ring of 32) in world space, just above the ground grid (y = -0.015; the grid is at -0.02
 * and the road at 0). The scene merges them into the static chunks with the road. Plain Java.
 */
public final class GroundPatches {

    /** Height of the patches. */
    public static final float Y = -0.015f;
    /** Corners round a patch and triangles in one patch. */
    public static final int SEGMENTS = 16, TRIANGLES = 48;
    /** How far outside the track's bounding box a patch centre may be. */
    public static final float MARGIN = 40f;

    private GroundPatches() { }

    /** One patch: centre, radius, and the swatch of its colour. */
    public static final class Patch {
        public final float x, z, radius;
        public final int swatch;
        final long shape;

        Patch(float x, float z, float radius, int swatch, long shape) {
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.swatch = swatch;
            this.shape = shape;
        }
    }

    /**
     * Places the patches of a track: deterministic by seed, centres inside the track's bounding box plus {@link #MARGIN},
     * radius between the theme's minimum and maximum, colours alternating patchA and patchB.
     * @param t the track
     * @param th the theme
     * @param seed for example {@code trackId.hashCode()}
     * @param low true on LOW quality (half the patches)
     * @return the patches (empty for a theme without patches)
     */
    public static List<Patch> generate(TrackSpline t, Theme th, long seed, boolean low) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < t.sampleCount(); i++) {
            minX = Math.min(minX, t.sampleX(i));
            maxX = Math.max(maxX, t.sampleX(i));
            minZ = Math.min(minZ, t.sampleZ(i));
            maxZ = Math.max(maxZ, t.sampleZ(i));
        }
        return generate(minX, maxX, minZ, maxZ, th, seed, low);
    }

    /**
     * Like {@link #generate(TrackSpline, Theme, long, boolean)} for a given bounding box of the track.
     * @param minX left edge of the track's box
     * @param maxX right edge
     * @param minZ near edge
     * @param maxZ far edge
     * @param th the theme
     * @param seed the seed
     * @param low true on LOW quality
     * @return the patches
     */
    public static List<Patch> generate(float minX, float maxX, float minZ, float maxZ, Theme th, long seed, boolean low) {
        int n = low ? th.patchCount / 2 : th.patchCount;
        List<Patch> out = new ArrayList<>(n);
        Random rnd = new Random(seed * 31L + 7L);
        for (int k = 0; k < n; k++) {
            float x = minX - MARGIN + rnd.nextFloat() * (maxX - minX + 2f * MARGIN);
            float z = minZ - MARGIN + rnd.nextFloat() * (maxZ - minZ + 2f * MARGIN);
            float r = th.patchMinR + rnd.nextFloat() * (th.patchMaxR - th.patchMinR);
            out.add(new Patch(x, z, r, (k & 1) == 0 ? Theme.SW_PATCH_A : Theme.SW_PATCH_B, rnd.nextLong()));
        }
        return out;
    }

    /**
     * The mesh of a patch, in world space (identity placement). One part named "patch", single sided, normals up, one swatch,
     * 48 triangles, counter clockwise seen from above.
     * @param p the patch
     * @param pal the palette (only its uvs are used)
     * @return the mesh
     */
    public static MeshData mesh(Patch p, PaletteSwatches pal) {
        Random rnd = new Random(p.shape);
        float u = pal.u(p.swatch), v = pal.v(p.swatch);
        int vc = 1 + 2 * SEGMENTS;
        float[] vert = new float[vc * 8];
        put(vert, 0, p.x, p.z, u, v);
        for (int i = 0; i < SEGMENTS; i++) {
            double a = 2 * Math.PI * i / SEGMENTS;
            float inner = p.radius * (0.40f + 0.15f * rnd.nextFloat());
            float outer = p.radius * (0.80f + 0.20f * rnd.nextFloat());
            put(vert, 1 + i, p.x + (float) Math.sin(a) * inner, p.z + (float) Math.cos(a) * inner, u, v);
            put(vert, 1 + SEGMENTS + i, p.x + (float) Math.sin(a) * outer, p.z + (float) Math.cos(a) * outer, u, v);
        }
        short[] ix = new short[TRIANGLES * 3];
        int o = 0;
        for (int i = 0; i < SEGMENTS; i++) {
            int j = (i + 1) % SEGMENTS;
            int in0 = 1 + i, in1 = 1 + j, out0 = 1 + SEGMENTS + i, out1 = 1 + SEGMENTS + j;
            ix[o++] = 0;
            ix[o++] = (short) in0;
            ix[o++] = (short) in1;
            ix[o++] = (short) in0;
            ix[o++] = (short) out0;
            ix[o++] = (short) out1;
            ix[o++] = (short) in0;
            ix[o++] = (short) out1;
            ix[o++] = (short) in1;
        }
        MeshData.Part part = new MeshData.Part("patch", -1, new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f, 1f},
                new float[] {1f, 1f, 1f}, vert, ix, vc);
        return new MeshData(false, new MeshData.Part[] {part});
    }

    private static void put(float[] vert, int i, float x, float z, float u, float v) {
        int o = i * 8;
        vert[o] = x;
        vert[o + 1] = Y;
        vert[o + 2] = z;
        vert[o + 3] = 0f;
        vert[o + 4] = 1f;
        vert[o + 5] = 0f;
        vert[o + 6] = u;
        vert[o + 7] = v;
    }
}
