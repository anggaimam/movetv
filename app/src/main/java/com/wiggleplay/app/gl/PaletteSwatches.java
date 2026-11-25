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
    /** For a ramp swatch: the colour of the right-hand end (the left-hand end is in {@code rgb}); -1 for a solid block. */
    private final int[] rampLight = new int[MAX];
    private int count;

    public PaletteSwatches() {
        java.util.Arrays.fill(rampLight, -1);
    }

    /**
     * Sets the colour of swatch {@code index}.
     * @param index 0 to 223
     * @param colour {@code 0xRRGGBB}
     * @throws IndexOutOfBoundsException for an index outside 0..223
     */
    public void set(int index, int colour) {
        check(index);
        rgb[index] = colour & 0xFFFFFF;
        rampLight[index] = -1;
        if (index >= count) count = index + 1;
    }

    /**
     * Makes swatch {@code index} a ramp (T-29R): the 16 columns of the block go from {@code dark} on the left to
     * {@code light} on the right. A vertex picks a column with {@link #uRamp}; the vertices of one triangle may use
     * different columns of the same block, which is how smooth gradients and baked shading are made. A triangle must
     * still never mix blocks.
     * @param index 0 to 223
     * @param dark {@code 0xRRGGBB} of the left end
     * @param light {@code 0xRRGGBB} of the right end
     */
    public void setRamp(int index, int dark, int light) {
        check(index);
        rgb[index] = dark & 0xFFFFFF;
        rampLight[index] = light & 0xFFFFFF;
        if (index >= count) count = index + 1;
    }

    /**
     * Whether a swatch is a ramp.
     * @param index 0 to 223
     * @return true after {@link #setRamp}, false for a solid block
     */
    public boolean isRamp(int index) {
        check(index);
        return rampLight[index] >= 0;
    }

    /**
     * The colour of one column of a swatch block.
     * @param index 0 to 223
     * @param column 0 to 15
     * @return {@code 0xRRGGBB}: the solid colour for a solid block, the blend of the ramp ends for a ramp
     */
    public int columnRgb(int index, int column) {
        check(index);
        if (rampLight[index] < 0) return rgb[index];
        int a = rgb[index], b = rampLight[index];
        float t = column / (float) (BLOCK - 1);
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * Texture coordinate u that picks the column at position {@code t} along a ramp: {@code (x + 0.5 + t * 15) / 512}.
     * @param index 0 to 223
     * @param t 0 (dark end) to 1 (light end); values outside are clamped
     * @return u in 0..1
     */
    public float uRamp(int index, float t) {
        check(index);
        float c = Math.max(0f, Math.min(1f, t));
        return (BLOCK * (index % PER_ROW) + 0.5f + c * (BLOCK - 1)) / SIZE;
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
            for (int y = 0; y < BLOCK; y++) {
                int o = (y0 + y) * SIZE + x0;
                for (int x = 0; x < BLOCK; x++) argb[o + x] = 0xFF000000 | columnRgb(i, x);
            }
        }
    }

    private static void check(int index) {
        if (index < 0 || index >= MAX) throw new IndexOutOfBoundsException("swatch " + index + " is outside 0.." + (MAX - 1));
    }
}
