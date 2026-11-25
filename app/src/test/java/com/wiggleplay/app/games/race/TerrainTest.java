package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import java.util.List;
import org.junit.Test;

public class TerrainTest {
    private static final Theme T = Themes.DESERT;

    private static Terrain make(TrackDef def, boolean low) {
        return Terrain.create(TrackSpline.build(def, false, RaceLevel.HARD), T, def.id.hashCode(), low);
    }

    @Test
    public void flatWithinEightMetresOfTheRoadOnAllTracks() {
        for (TrackDef def : TrackDefs.ALL) {
            TrackSpline t = TrackSpline.build(def, false, RaceLevel.HARD);
            Terrain te = make(def, false);
            for (int i = 0; i < t.sampleCount(); i += 3) {
                for (float off = -8f; off <= 8f; off += 2f) {
                    float x = t.sampleX(i) + off * 0.7071f, z = t.sampleZ(i) + off * 0.7071f;
                    if (te.distance(x, z) > Terrain.FLAT_D) continue;
                    assertEquals(def.id + " flat", Terrain.ROAD_Y, te.height(x, z), 1e-3f);
                }
            }
        }
    }

    @Test
    public void heightsStayInsideTheAmplitudeAndTheSurfaceIsContinuous() {
        Terrain.Profile p = T.look.terrain;
        for (TrackDef def : TrackDefs.ALL) {
            Terrain te = make(def, false);
            for (int i = 0; i < 400; i++) {
                float x = (i * 37 % 400 - 200) * 1.7f, z = (i * 91 % 400 - 200) * 1.7f;
                float h = te.height(x, z);
                assertTrue(def.id + " h " + h, h >= -0.3f && h <= p.amplitude + p.bump + 1f);
                assertTrue(Math.abs(te.surface(x, z) - te.surface(x + 0.05f, z)) < 0.2f);
            }
        }
    }

    @Test
    public void tilesAreUpFacingAndWithinTheVertexLimit() {
        for (TrackDef def : TrackDefs.ALL) {
            for (boolean low : new boolean[] {false, true}) {
                Terrain te = make(def, low);
                PaletteSwatches pal = new PaletteSwatches();
                T.fillPalette(pal);
                List<Terrain.Tile> tiles = te.build(pal, T.terrainSwatch(0), T.terrainSwatch(1));
                int tris = 0;
                for (Terrain.Tile tile : tiles) {
                    MeshData.Part p = tile.mesh.parts[0];
                    assertTrue(p.vertexCount < 65535);
                    assertEquals(tile.triangles, p.indices.length / 3);
                    tris += tile.triangles;
                    for (int i = 0; i < p.indices.length; i += 3) {
                        int a = (p.indices[i] & 0xFFFF) * 8, b = (p.indices[i + 1] & 0xFFFF) * 8, c = (p.indices[i + 2] & 0xFFFF) * 8;
                        float ux = p.vertices[b] - p.vertices[a], uy = p.vertices[b + 1] - p.vertices[a + 1], uz = p.vertices[b + 2] - p.vertices[a + 2];
                        float vx = p.vertices[c] - p.vertices[a], vy = p.vertices[c + 1] - p.vertices[a + 1], vz = p.vertices[c + 2] - p.vertices[a + 2];
                        float ny = uz * vx - ux * vz;
                        assertTrue(def.id + " terrain triangle faces down", ny > 0f);
                    }
                }
                System.out.println(def.id + (low ? " LOW" : " HIGH") + " terrain tiles=" + tiles.size() + " triangles=" + tris);
                assertTrue(def.id + " terrain triangles " + tris, tris <= (low ? 30000 : 60000));
            }
        }
    }

    @Test
    public void lowUsesCoarserCells() {
        TrackDef def = TrackDefs.ALL[0];
        assertEquals(2.5f, make(def, false).cell(), 0f);
        assertEquals(4f, make(def, true).cell(), 0f);
    }
}
