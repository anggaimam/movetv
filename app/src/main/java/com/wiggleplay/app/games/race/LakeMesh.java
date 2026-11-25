package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;

/**
 * The water of one lake (T-30b): a flat ellipse grid of 32 segments and 4 rings at y = {@link #Y}, deep colour in the middle and
 * shallow towards the edge (the ramp position follows the distance to the edge), with a light band along the outer 2 m. Plain Java.
 * Every triangle uses one swatch block and faces up.
 */
public final class LakeMesh {

    /** Water level, segments round the lake, and the width of the light edge band (m). */
    public static final float Y = -0.01f, EDGE_BAND = 2f;
    public static final int SEGMENTS = 32;
    /** Triangle limit of the ticket. */
    public static final int MAX_TRIANGLES = 260;

    private LakeMesh() { }

    /**
     * Builds the water of lake {@code index}.
     * @param lakes the lakes
     * @param index which lake
     * @param pal the palette
     * @param waterSwatch ramp swatch: deep (t 0) to shallow (t 1)
     * @param edgeSwatch swatch of the light edge band
     * @return the mesh, one part named "lake", world space
     */
    public static MeshData build(Lakes lakes, int index, PaletteSwatches pal, int waterSwatch, int edgeSwatch) {
        MeshKit k = new MeshKit(pal);
        float minR = Math.min(lakes.radiusX(index), lakes.radiusZ(index));
        float[] scales = {0f, 0.42f, 0.74f, 1f - EDGE_BAND / minR, 1f};
        float[] p = new float[2];
        float[][][] pts = new float[scales.length][SEGMENTS][];
        for (int r = 0; r < scales.length; r++) {
            for (int s = 0; s < SEGMENTS; s++) {
                lakes.point(index, (float) (2.0 * Math.PI * s / SEGMENTS), scales[r], p);
                pts[r][s] = new float[] {p[0], Y, p[1]};
            }
        }
        // ramp position: 0 (deep) at the centre, 1 (shallow) at the edge, by the distance to the edge
        float[] t = new float[scales.length];
        for (int r = 0; r < scales.length; r++) t[r] = 1f - Math.min(1f, (1f - scales[r]) * minR / (0.8f * minR));
        for (int r = 0; r < 3; r++) ring(k, pts, t, r, waterSwatch);
        ring(k, pts, t, 3, edgeSwatch);
        return k.mesh("lake");
    }

    /** The band between ring r and ring r + 1 (a fan for the first). */
    private static void ring(MeshKit k, float[][][] pts, float[] t, int r, int sw) {
        float[] up = {0f, 1f, 0f};
        for (int s = 0; s < SEGMENTS; s++) {
            int s2 = (s + 1) % SEGMENTS;
            float tt0 = r == 3 ? 0.95f : t[r], tt1 = r == 3 ? 0.95f : t[r + 1];
            if (r == 0) {
                k.flatTri(pts[0][s], pts[1][s], pts[1][s2], tt0, tt1, tt1, sw, up[0], up[1], up[2]);
            } else {
                k.flatTri(pts[r][s], pts[r + 1][s], pts[r + 1][s2], tt0, tt1, tt1, sw, up[0], up[1], up[2]);
                k.flatTri(pts[r][s], pts[r + 1][s2], pts[r][s2], tt0, tt1, tt0, sw, up[0], up[1], up[2]);
            }
        }
    }
}
