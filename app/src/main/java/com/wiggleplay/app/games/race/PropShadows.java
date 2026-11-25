package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import com.wiggleplay.app.gl.StaticBatcher;

import java.util.List;
import java.util.Map;

/**
 * Baked shadows on the terrain (T-29R R2): a dark disc under every prop and a long ellipse cast away from the sun by the tall ones.
 * Each is a flat fan lying 0.08 m above the ground surface (sampled at its rim points so it follows the dunes), coloured from the
 * ground ramp: very dark in the middle, the ground's own tone at the rim. Plain Java.
 */
public final class PropShadows {

    /** Segments of a shadow fan, the lift above the ground, and the share of the footprint a disc covers. */
    public static final int SEGMENTS = 12;
    public static final float LIFT = 0.08f, DISC = 1.2f, MIN_TALL = 2f, CAST_LENGTH = 0.9f, CAST_WIDTH = 0.8f, MIN_REACH = 0.4f;

    private PropShadows() { }

    /**
     * Makes the shadows of a set of props.
     * @param terrain the ground
     * @param pal the palette
     * @param groundSwatch the ground ramp swatch
     * @param props the props
     * @param library receives the meshes (world space), names {@code shd_<n>}
     * @param out receives the placements (identity matrix)
     * @return the number of triangles
     */
    public static int build(Terrain terrain, PaletteSwatches pal, int groundSwatch, List<PropScatter.Instance> props,
                            Map<String, MeshData> library, List<StaticBatcher.Placement> out) {
        float[] sun = terrain.sunToward();
        float[] ident = new float[16];
        com.wiggleplay.app.gl.Mat4.identity(ident);
        int tris = 0, n = 0;
        for (PropScatter.Instance p : props) {
            float reach = PropMeshes.reach(p.kind) * p.scale;
            if (reach < MIN_REACH || p.kind == PropMeshes.DRY_GRASS || p.kind == PropMeshes.PEBBLES) continue;
            float r = Math.max(p.footprint, reach * 0.6f) * DISC;
            tris += add(terrain, pal, groundSwatch, p.x, p.z, r, r, 0f, 1f, library, out, ident, n++);
            float height = PropMeshes.height(p.kind) * p.scale;
            if (height > MIN_TALL) {
                float len = height * CAST_LENGTH, half = len / 2f;
                float dx = -sun[0], dz = -sun[1];
                float w = CAST_WIDTH * Math.min(p.footprint, 2f);
                tris += add(terrain, pal, groundSwatch, p.x + dx * half, p.z + dz * half, half, w, dx, dz, library, out, ident, n++);
            }
        }
        return tris;
    }

    /** One fan: an ellipse of half length {@code a} along (dx, dz) and half width {@code b} across it (a disc when dx = 0, dz = 1). */
    private static int add(Terrain terrain, PaletteSwatches pal, int sw, float cx, float cz, float a, float b, float dx, float dz,
                           Map<String, MeshData> library, List<StaticBatcher.Placement> out, float[] ident, int n) {
        if (dx == 0f && dz == 1f) {
            dx = 1f;
            dz = 0f;
        }
        float px = -dz, pz = dx;
        MeshKit k = new MeshKit(pal);
        float cy = terrain.surface(cx, cz) + LIFT;
        int centre = k.vert(cx, cy, cz, 0f, 1f, 0f, sw, 0.05f);
        int[] rim = new int[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            double t = 2.0 * Math.PI * i / SEGMENTS;
            float x = cx + dx * a * (float) Math.cos(t) + px * b * (float) Math.sin(t);
            float z = cz + dz * a * (float) Math.cos(t) + pz * b * (float) Math.sin(t);
            rim[i] = k.vert(x, terrain.surface(x, z) + LIFT, z, 0f, 1f, 0f, sw, terrain.tone(x, z));
        }
        // the fan is counter-clockwise from above when it runs from +x towards +z; the basis (d, p) may run the other way
        boolean ccw = dx * pz - dz * px > 0f;   // always true: p = (-dz, dx) is d turned towards +z... kept for clarity
        for (int i = 0; i < SEGMENTS; i++) {
            int i2 = (i + 1) % SEGMENTS;
            if (ccw) k.tri(centre, rim[i2], rim[i]);
            else k.tri(centre, rim[i], rim[i2]);
        }
        MeshData.Part part = new MeshData.Part("shadow", -1, new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f, 1f},
                new float[] {1f, 1f, 1f}, java.util.Arrays.copyOf(k.v, k.vc * 8), java.util.Arrays.copyOf(k.ix, k.ic), k.vc);
        String name = "shd_" + n;
        library.put(name, new MeshData(false, new MeshData.Part[] {part}));
        out.add(new StaticBatcher.Placement(name, ident, (int) Math.floor(cx / 10f), (int) Math.floor(cz / 10f)));
        return SEGMENTS;
    }
}
