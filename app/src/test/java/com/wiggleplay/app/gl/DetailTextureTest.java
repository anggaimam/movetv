package com.wiggleplay.app.gl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DetailTextureTest {

    private static int px(byte[] t, int x, int y) {
        return t[y * DetailTexture.TILE + x] & 0xFF;
    }

    @Test
    public void everyKindHasAMeanNearOneHalf() {
        for (int k = 0; k < DetailTexture.KINDS; k++) {
            byte[] t = DetailTexture.tile(k, 5L);
            assertEquals(DetailTexture.TILE * DetailTexture.TILE, t.length);
            double mean = 0;
            for (byte b : t) mean += (b & 0xFF) / 255.0;
            mean /= t.length;
            assertEquals("kind " + k, 0.5, mean, 0.03);
        }
    }

    @Test
    public void everyKindHasGrainAndIsNotFlat() {
        for (int k = 0; k < DetailTexture.KINDS; k++) {
            byte[] t = DetailTexture.tile(k, 5L);
            int min = 255, max = 0;
            for (byte b : t) {
                min = Math.min(min, b & 0xFF);
                max = Math.max(max, b & 0xFF);
            }
            assertTrue("kind " + k + " range " + min + " to " + max, max - min >= 60);
        }
    }

    @Test
    public void edgesWrapLikeTheInside() {
        // a tile that repeats has a seam no rougher than the pairs inside it
        for (int k = 0; k < DetailTexture.KINDS; k++) {
            byte[] t = DetailTexture.tile(k, 9L);
            double seamX = 0, seamY = 0, inX = 0, inY = 0;
            int n = DetailTexture.TILE;
            for (int i = 0; i < n; i++) {
                seamX += Math.abs(px(t, n - 1, i) - px(t, 0, i));
                seamY += Math.abs(px(t, i, n - 1) - px(t, i, 0));
                inX += Math.abs(px(t, n / 2, i) - px(t, n / 2 + 1, i));
                inY += Math.abs(px(t, i, n / 2) - px(t, i, n / 2 + 1));
            }
            assertTrue("kind " + k + " left/right seam " + seamX + " vs " + inX, seamX <= 1.35 * inX + 200);
            assertTrue("kind " + k + " top/bottom seam " + seamY + " vs " + inY, seamY <= 1.35 * inY + 200);
        }
    }

    @Test
    public void deterministicAndSeedMatters() {
        assertArrayEquals(DetailTexture.tile(DetailTexture.SAND, 3L), DetailTexture.tile(DetailTexture.SAND, 3L));
        assertTrue(!java.util.Arrays.equals(DetailTexture.tile(DetailTexture.SAND, 3L), DetailTexture.tile(DetailTexture.SAND, 4L)));
        assertTrue(!java.util.Arrays.equals(DetailTexture.tile(DetailTexture.SAND, 3L), DetailTexture.tile(DetailTexture.ASPHALT, 3L)));
    }

    @Test
    public void atlasHoldsTheFourTilesInTheirQuadrants() {
        int[] kinds = {DetailTexture.SAND, DetailTexture.ASPHALT, DetailTexture.GRAVEL, DetailTexture.CONCRETE};
        byte[] a = DetailTexture.atlas(kinds, 11L);
        assertEquals(DetailTexture.ATLAS * DetailTexture.ATLAS, a.length);
        for (int k = 0; k < 4; k++) {
            byte[] t = DetailTexture.tile(kinds[k], 11L * 31L + k);
            int x0 = (k % 2) * DetailTexture.TILE, y0 = (k / 2) * DetailTexture.TILE;
            for (int y = 0; y < DetailTexture.TILE; y += 17) {
                for (int x = 0; x < DetailTexture.TILE; x += 13) {
                    assertEquals(t[y * DetailTexture.TILE + x], a[(y0 + y) * DetailTexture.ATLAS + x0 + x]);
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void atlasNeedsFourKinds() {
        DetailTexture.atlas(new int[] {0, 1, 2}, 1L);
    }

    @Test
    public void sandRipplesRunAlongOneDirection() {
        // sand: rows differ more than columns (ripples across y), so the mean change between neighbouring rows of the
        // row averages is larger than the same for column averages
        byte[] t = DetailTexture.tile(DetailTexture.SAND, 1L);
        int n = DetailTexture.TILE;
        double[] rows = new double[n], cols = new double[n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                rows[y] += (t[y * n + x] & 0xFF) / (double) n;
                cols[x] += (t[y * n + x] & 0xFF) / (double) n;
            }
        }
        double vr = 0, vc = 0;
        for (int i = 0; i < n; i++) {
            vr += Math.pow(rows[i] - 127.5, 2);
            vc += Math.pow(cols[i] - 127.5, 2);
        }
        assertTrue("row averages vary " + vr + " vs column averages " + vc, vr > 3 * vc);
    }
}
