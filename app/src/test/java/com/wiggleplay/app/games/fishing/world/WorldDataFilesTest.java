package com.wiggleplay.app.games.fishing.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Random;

/** Header and range checks of the committed Gone Fishing world files (made by tools/terrain_import.py). Plain Java. */
public class WorldDataFilesTest {

    private static ByteBuffer load(String name, String magic) throws IOException {
        File f = new File("src/main/assets/fishing/world/" + name);
        if (!f.exists()) f = new File("app/src/main/assets/fishing/world/" + name);
        assertTrue(name + " is missing", f.exists());
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(f.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        byte[] m = new byte[4];
        b.get(m);
        assertEquals(name + " magic", magic, new String(m, "US-ASCII"));
        return b;
    }

    /** A grid header: u16 w, u16 h, f32 cell, f32 originX, f32 originZ (height files have two more floats). */
    private static final class Header {
        int w, h;
        float cell, ox, oz;
    }

    private static Header header(ByteBuffer b) {
        Header g = new Header();
        g.w = b.getShort() & 0xFFFF;
        g.h = b.getShort() & 0xFFFF;
        g.cell = b.getFloat();
        g.ox = b.getFloat();
        g.oz = b.getFloat();
        return g;
    }

    @Test
    public void heightFilesHaveHeaderAndRange() throws IOException {
        for (String[] spec : new String[][] {{"height.bin", "750", "4.0"}, {"height_far.bin", "290", "16.0"}}) {
            ByteBuffer b = load(spec[0], "FHM1");
            Header g = header(b);
            float min = b.getFloat(), max = b.getFloat();
            assertEquals(spec[0] + " width", Integer.parseInt(spec[1]), g.w);
            assertEquals(spec[0] + " height", Integer.parseInt(spec[1]), g.h);
            assertEquals(spec[0] + " cell", Float.parseFloat(spec[2]), g.cell, 1e-6f);
            assertTrue(spec[0] + " max >= min", max >= min);
            assertEquals(spec[0] + " size", 4 + 24 + 2 * g.w * g.h, b.capacity());
            assertTrue("lake level lies inside the relief of " + spec[0], min < 0f && max > 100f);
            assertEquals("grid is centred on the export centre", -g.w * g.cell / 2f, g.ox, 1e-3f);
            assertEquals("grid is centred on the export centre", -g.h * g.cell / 2f, g.oz, 1e-3f);
        }
    }

    @Test
    public void shoreSignMatchesWaterMaskAndDepthIsZeroOnLand() throws IOException {
        ByteBuffer mb = load("masks.bin", "FMK1");
        Header mg = header(mb);
        assertEquals(1500, mg.w);
        assertEquals(2f, mg.cell, 1e-6f);
        assertEquals(4 + 16 + mg.w * mg.h, mb.capacity());
        byte[] masks = new byte[mg.w * mg.h];
        mb.get(masks);

        ByteBuffer sb = load("shore.bin", "FSD1");
        Header sg = header(sb);
        assertEquals(750, sg.w);
        assertEquals(4f, sg.cell, 1e-6f);
        assertEquals(4 + 16 + 2 * sg.w * sg.h, sb.capacity());
        short[] shore = new short[sg.w * sg.h];
        for (int i = 0; i < shore.length; i++) shore[i] = sb.getShort();

        ByteBuffer db = load("depth.bin", "FDP1");
        Header dg = header(db);
        assertEquals(375, dg.w);
        assertEquals(8f, dg.cell, 1e-6f);
        assertEquals(4 + 16 + dg.w * dg.h, db.capacity());
        byte[] depth = new byte[dg.w * dg.h];
        db.get(depth);

        Random rnd = new Random(7);
        int checked = 0, water = 0, land = 0, tries = 0;
        while (checked < 1000 && tries++ < 200000) {
            int i = rnd.nextInt(sg.h), j = rnd.nextInt(sg.w);
            // the four 2 m cells under this 4 m cell must agree, otherwise the cell sits on the shoreline
            int mi = 2 * i, mj = 2 * j;
            int a = masks[mi * mg.w + mj] & 1, b = masks[mi * mg.w + mj + 1] & 1,
                    c = masks[(mi + 1) * mg.w + mj] & 1, d = masks[(mi + 1) * mg.w + mj + 1] & 1;
            if (a != b || b != c || c != d) continue;
            checked++;
            short s = shore[i * sg.w + j];
            if (a == 1) {
                water++;
                assertTrue("water cell (" + i + "," + j + ") has shore " + s, s < 0);
            } else {
                land++;
                assertTrue("land cell (" + i + "," + j + ") has shore " + s, s > 0);
                int di = i / 2, dj = j / 2;
                // depth 0 on land: only when the whole 8 m cell is land
                boolean all = true;
                for (int y = 0; y < 2 && all; y++) for (int x = 0; x < 2; x++) if (shore[(2 * di + y) * sg.w + 2 * dj + x] < 0) all = false;
                if (all) assertEquals("depth on land", 0, depth[di * dg.w + dj] & 0xFF);
            }
        }
        assertEquals(1000, checked);
        assertTrue("the sample must contain water and land (water " + water + ", land " + land + ")", water > 50 && land > 50);
    }

    @Test
    public void shoreIsClippedAndDepthHasWater() throws IOException {
        ByteBuffer sb = load("shore.bin", "FSD1");
        header(sb);
        int min = 0, max = 0;
        while (sb.remaining() >= 2) {
            short s = sb.getShort();
            min = Math.min(min, s);
            max = Math.max(max, s);
        }
        assertTrue("shore clipped to +/-300 m", min >= -3000 && max <= 3000 && min < -100 && max > 100);
        ByteBuffer db = load("depth.bin", "FDP1");
        header(db);
        int deepest = 0;
        while (db.hasRemaining()) deepest = Math.max(deepest, db.get() & 0xFF);
        assertTrue("deepest water is between 10 m and 127.5 m", deepest >= 20 && deepest <= 255);
    }

    @Test
    public void jsonFilesExistAndHaveContent() throws IOException {
        for (String n : new String[] {"meta.json", "buildings.json", "piers.json", "waterfalls.json", "spots_candidates.json"}) {
            File f = new File("src/main/assets/fishing/world/" + n);
            if (!f.exists()) f = new File("app/src/main/assets/fishing/world/" + n);
            assertTrue(n + " is missing", f.exists());
            String s = new String(Files.readAllBytes(f.toPath()), "UTF-8").trim();
            assertTrue(n + " is not JSON", s.startsWith("{") || s.startsWith("["));
        }
        File f = new File("src/main/assets/fishing/world/meta.json");
        if (!f.exists()) f = new File("app/src/main/assets/fishing/world/meta.json");
        String meta = new String(Files.readAllBytes(f.toPath()), "UTF-8");
        assertTrue(meta.contains("\"mPerMm\": 23.181"));
        assertTrue(meta.contains("\"playRadius\": 1500"));
    }
}
