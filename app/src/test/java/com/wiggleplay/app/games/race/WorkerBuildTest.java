package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.wiggleplay.app.gl.MeshData;
import com.wiggleplay.app.gl.PaletteSwatches;
import com.wiggleplay.app.gl.StaticBatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/** The world is built on a worker thread; it must come out exactly as it does on the calling thread. */
public class WorkerBuildTest {

    private static List<StaticBatcher.Chunk> build(final Map<String, MeshData> lib, List<StaticBatcher.Placement> all) {
        return StaticBatcher.build(all, new StaticBatcher.MeshLibrary() {
            @Override
            public MeshData get(String name) {
                return lib.get(name);
            }
        }, 6);
    }

    private static final class World {
        final Map<String, MeshData> lib = new HashMap<>();
        final List<StaticBatcher.Placement> all = new ArrayList<>();
    }

    private static World make() {
        TrackDef def = TrackDefs.ALL[0];
        Theme th = Themes.DESERT;
        TrackSpline layout = TrackSpline.build(def, false, 5.5f);
        PaletteSwatches pal = new PaletteSwatches();
        th.fillPalette(pal);
        World w = new World();
        List<MeshData> pieces = TrackMeshBuilder.build(layout, th.road, pal);
        float[] ident = new float[16];
        com.wiggleplay.app.gl.Mat4.identity(ident);
        for (int i = 0; i < pieces.size(); i++) {
            w.lib.put("trk_road_" + i, pieces.get(i));
            w.all.add(new StaticBatcher.Placement("trk_road_" + i, ident, i, 0));
        }
        for (int r = 0; r < th.props.length; r++) {
            for (int v = 0; v < PropMeshes.variants(th.props[r].kind); v++) {
                w.lib.put(PropMeshes.name(th.props[r].kind, v), PropMeshes.build(th.props[r].kind, v, pal, th.propSwatches(r)));
            }
        }
        PropScatter.scatter(layout, th, def.id.hashCode(), true, w.all);
        return w;
    }

    @Test
    public void aWorkerThreadMakesTheSameChunks() throws Exception {
        World a = make();
        List<StaticBatcher.Chunk> here = build(a.lib, a.all);
        final AtomicReference<List<StaticBatcher.Chunk>> there = new AtomicReference<>();
        final AtomicReference<Throwable> err = new AtomicReference<>();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    World b = make();
                    there.set(build(b.lib, b.all));
                } catch (Throwable e) {
                    err.set(e);
                }
            }
        });
        t.start();
        t.join();
        assertNull(String.valueOf(err.get()), err.get());
        assertEquals(here.size(), there.get().size());
        for (int i = 0; i < here.size(); i++) {
            StaticBatcher.Chunk x = here.get(i), y = there.get().get(i);
            assertEquals(x.vertexCount, y.vertexCount);
            assertEquals(x.indexCount, y.indexCount);
            assertArrayEquals(java.util.Arrays.copyOf(x.vertices, x.vertexCount * 8), java.util.Arrays.copyOf(y.vertices, y.vertexCount * 8), 0f);
            assertArrayEquals(java.util.Arrays.copyOf(x.indices, x.indexCount), java.util.Arrays.copyOf(y.indices, y.indexCount));
        }
    }
}
