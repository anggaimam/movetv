package com.wiggleplay.app.gl;

/**
 * Colours painted into free blocks of Kenney's 512 x 512 {@code colormap.png} at runtime (T-28 Part C), so roads and themes
 * draw with the one existing texture and shader. Plain Java.
 *
 * <p>Swatch i is the 16 x 16 block at x = 16 (i % 32), y = 16 (1 + i / 32) (y counted from the top), that is rows 16 to 127.
 * Rows 0 to 15 hold the coin and can swatches ({@link ProcMesh}), and no Kenney mesh samples rows 0 to 139. Every vertex
 * of a quad that uses a swatch gets the same uv, the block's centre, so neighbouring blocks never bleed in.
 */
public final class PaletteSwatches {

    /** Number of swatches that fit in rows 16 to 127. */
    public static final int MAX = 224;
    /** Side of one block and of the whole texture. */
    public static final int BLOCK = 16, SIZE = 512;
    private static final int PER_ROW = SIZE / BLOCK;

    private final int[] rgb = new int[MAX];
    private int count;

    /**
     * Sets the colour of swatch {@code index}.
     * @param index 0 to 223
     * @param colour {@code 0xRRGGBB}
     * @throws IndexOutOfBoundsException for an index outside 0..223
     */
    public void set(int index, int colour) {
        check(index);
        rgb[index] = colour & 0xFFFFFF;
        if (index >= count) count = index + 1;
    }

    /**
     * The colour of a swatch.
     * @param index 0 to 223
     * @return {@code 0xRRGGBB}
     */
    public int rgb(int index) {
        check(index);
        return rgb[index];
    }

    /**
     * Texture coordinate u of the centre of a swatch block.
     * @param index 0 to 223
     * @return u in 0..1
     */
    public float u(int index) {
        check(index);
        return (BLOCK * (index % PER_ROW) + BLOCK / 2f) / SIZE;
    }

    /**
     * Texture coordinate v of the centre of a swatch block (v counted from the top of the image, as the coin swatches do).
     * @param index 0 to 223
     * @return v in 0..1
     */
    public float v(int index) {
        check(index);
        return (BLOCK * (1 + index / PER_ROW) + BLOCK / 2f) / SIZE;
    }

    /**
     * How many swatches are in use (one more than the highest index set).
     * @return the count
     */
    public int count() {
        return count;
    }

    /**
     * Writes the swatches into a decoded colormap. Only the blocks of swatches that were set change.
     * @param argb 512 x 512 pixels, row by row from the top, 0xAARRGGBB
     * @throws IllegalArgumentException if the array is not 512 x 512
     */
    public void paintInto(int[] argb) {
        if (argb.length != SIZE * SIZE) throw new IllegalArgumentException("colormap must be 512 x 512 pixels");
        for (int i = 0; i < count; i++) {
            int x0 = BLOCK * (i % PER_ROW), y0 = BLOCK * (1 + i / PER_ROW);
            int c = 0xFF000000 | rgb[i];
            for (int y = 0; y < BLOCK; y++) {
                int o = (y0 + y) * SIZE + x0;
                for (int x = 0; x < BLOCK; x++) argb[o + x] = c;
            }
        }
    }

    private static void check(int index) {
        if (index < 0 || index >= MAX) throw new IndexOutOfBoundsException("swatch " + index + " is outside 0.." + (MAX - 1));
    }
}
