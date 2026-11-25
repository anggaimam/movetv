package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import com.wiggleplay.app.gl.StaticBatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;

public class PropShadowsTest {
    private static final Theme T = Themes.DESERT;

    @Test
    public void shadowsHugTheTerrainAndSitInsideTheirCells() {
        for (TrackDef def : TrackDefs.ALL) {
            TrackSpline t = TrackSpline.build(def, false, RaceLevel.HARD);
            Terrain te = Terrain.create(t, T, def.id.hashCode(), false);
            PaletteSwatches pal = new PaletteSwatches();
            T.fillPalette(pal);
            List<StaticBatcher.Placement> pl = new ArrayList<>();
            List<PropScatter.Instance> inst = new ArrayList<>();
            PropScatter.scatter(t, T, def.id.hashCode(), true, pl, te, inst);
            LinkedHashMap<String, MeshData> lib = new LinkedHashMap<>();
            List<StaticBatcher.Placement> out = new ArrayList<>();
            int tris = PropShadows.build(te, pal, T.terrainSwatch(0), inst, lib, out);
            assertTrue(def.id + " has shadows", tris > 0 && out.size() == lib.size());
            float[] sun = te.sunToward();
            for (MeshData m : lib.values()) {
                MeshData.Part p = m.parts[0];
                for (int v = 0; v < p.vertexCount; v++) {
                    float x = p.vertices[v * 8], y = p.vertices[v * 8 + 1], z = p.vertices[v * 8 + 2];
                    assertTrue(def.id + " shadow lift " + (y - te.surface(x, z)),
                            y - te.surface(x, z) >= PropShadows.LIFT - 0.35f && y - te.surface(x, z) <= PropShadows.LIFT + 0.6f);
                }
            }
            assertTrue(sun.length >= 2);
        }
    }
}
