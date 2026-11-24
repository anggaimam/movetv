package com.wiggleplay.app.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class PaletteSwatchesTest {

    private static int[] realColormap() throws Exception {
        PngPixels im = new PngPixels(new java.io.File("src/main/assets/art/race/colormap.png"));
        int[] px = new int[512 * 512];
        for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) px[y * 512 + x] = 0xFF000000 | im.rgb(x, y);
        return px;
    }

    @Test
    public void everyPixelOfEveryBlockHoldsItsColourAndNothingElseChanges() throws Exception {
        int[] before = realColormap();
        int[] px = before.clone();
        PaletteSwatches pal = new PaletteSwatches();
        for (int i = 0; i < PaletteSwatches.MAX; i++) pal.set(i, 0x010203 * (i + 1) & 0xFFFFFF);
        assertEquals(224, pal.count());
        pal.paintInto(px);
        for (int i = 0; i < 224; i++) {
            int x0 = 16 * (i % 32), y0 = 16 * (1 + i / 32);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    assertEquals("swatch " + i + " pixel " + x + "," + y, 0xFF000000 | pal.rgb(i), px[(y0 + y) * 512 + x0 + x]);
                }
            }
        }
        for (int y = 0; y < 512; y++) {
            if (y >= 16 && y < 128) continue;
            for (int x = 0; x < 512; x++) assertEquals("outside rows 16..127 at " + x + "," + y, before[y * 512 + x], px[y * 512 + x]);
        }
    }

    @Test
    public void onlySwatchesThatWereSetArePainted() throws Exception {
        int[] before = realColormap();
        int[] px = before.clone();
        PaletteSwatches pal = new PaletteSwatches();
        pal.set(3, 0xABCDEF);
        assertEquals(4, pal.count());
        pal.paintInto(px);
        // block 4 is not in use, so it keeps Kenney's pixels
        assertEquals(before[16 * 512 + 16 * 4], px[16 * 512 + 16 * 4]);
        assertEquals(0xFFABCDEF, px[16 * 512 + 16 * 3]);
    }

    @Test
    public void uvHitsTheBlockCentreAndTheCoinSwatchesSurvive() throws Exception {
        PaletteSwatches pal = new PaletteSwatches();
        assertEquals(8f / 512f, pal.u(0), 1e-7f);
        assertEquals(24f / 512f, pal.v(0), 1e-7f);
        assertEquals((16f * 31 + 8f) / 512f, pal.u(31), 1e-7f);
        assertEquals((16f * 2 + 8f) / 512f, pal.v(32), 1e-7f);
        assertEquals((16f * 7 + 8f) / 512f, pal.v(223), 1e-7f);
        // a uv lands in the block it names (nearest sampling: floor(u * 512), floor(v * 512))
        for (int i : new int[] {0, 1, 31, 32, 100, 223}) {
            int px = (int) Math.floor(pal.u(i) * 512), py = (int) Math.floor(pal.v(i) * 512);
            assertEquals(16 * (i % 32), px - px % 16);
            assertEquals(16 * (1 + i / 32), py - py % 16);
        }
        // the coin swatches (T-23 C) use the same formula: v = (y + 8) / 512 with y the row from the top of the image
        int[] px = realColormap();
        int[] coins = px.clone();
        PaletteSwatches pal2 = new PaletteSwatches();
        pal2.set(0, 0x123456);
        pal2.paintInto(coins);
        for (int k = 0; k < ProcMesh.SWATCH_RGB.length; k++) assertEquals(px[8 * 512 + 16 * k + 8], coins[8 * 512 + 16 * k + 8]);
    }

    @Test
    public void indexOutOfRangeThrows() {
        PaletteSwatches pal = new PaletteSwatches();
        for (int bad : new int[] {224, -1, 1000}) {
            try {
                pal.set(bad, 0);
                fail("index " + bad + " should throw");
            } catch (IndexOutOfBoundsException expected) {
                assertTrue(expected.getMessage().contains(String.valueOf(bad)));
            }
        }
        try {
            pal.paintInto(new int[10]);
            fail("wrong size should throw");
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }
}
