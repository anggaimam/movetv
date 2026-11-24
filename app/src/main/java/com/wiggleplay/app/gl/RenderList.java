package com.wiggleplay.app.gl;

/**
 * A fixed-capacity list of things to draw this frame. Everything is allocated once in the constructor;
 * {@link #begin()}, {@link #add} and {@link #copyFrom} never allocate. Plain Java (no Android classes).
 *
 * <p>An item is: which mesh ({@code meshId}), which part of it, the model matrix (copied in), a colour,
 * whether it is drawn {@code flat} (with the colour instead of the palette texture), a viewport mask
 * (bit 0 = left or only viewport, bit 1 = right viewport) and whether it is drawn without fog (the backdrop rings, T-29).
 */
public final class RenderList {

    private final int capacity;
    private final int[] meshIds;
    private final int[] parts;
    private final int[] colors;
    private final int[] masks;
    private final boolean[] flats;
    private final boolean[] noFogs;
    private final float[] models;
    private int count;

    /**
     * Creates a list.
     * @param capacity the most items it can hold, for example 512
     */
    public RenderList(int capacity) {
        this.capacity = capacity;
        meshIds = new int[capacity];
        parts = new int[capacity];
        colors = new int[capacity];
        masks = new int[capacity];
        flats = new boolean[capacity];
        noFogs = new boolean[capacity];
        models = new float[capacity * 16];
    }

    /** Empties the list (the capacity stays). */
    public void begin() {
        count = 0;
    }

    /**
     * Adds an item.
     * @param meshId which loaded mesh
     * @param part the part index inside that mesh
     * @param model the 16 model matrix floats (copied)
     * @param colorRgb 0xRRGGBB, used when {@code flat}
     * @param flat draw with {@code colorRgb} instead of the texture
     * @param viewportMask which viewports show it (bit 0 = left/only, bit 1 = right)
     * @return false if the list is full (the item is not added)
     */
    public boolean add(int meshId, int part, float[] model, int colorRgb, boolean flat, int viewportMask) {
        return add(meshId, part, model, colorRgb, flat, viewportMask, false);
    }

    /**
     * Adds an item, with the choice of drawing it without fog.
     * @param meshId which loaded mesh
     * @param part the part index inside that mesh
     * @param model the 16 model matrix floats (copied)
     * @param colorRgb 0xRRGGBB, used when {@code flat}
     * @param flat draw with {@code colorRgb} instead of the texture
     * @param viewportMask which viewports show it (bit 0 = left/only, bit 1 = right)
     * @param noFog true to draw it with no distance fog (the backdrop rings)
     * @return false if the list is full (the item is not added)
     */
    public boolean add(int meshId, int part, float[] model, int colorRgb, boolean flat, int viewportMask, boolean noFog) {
        if (count >= capacity) return false;
        noFogs[count] = noFog;
        meshIds[count] = meshId;
        parts[count] = part;
        colors[count] = colorRgb;
        flats[count] = flat;
        masks[count] = viewportMask;
        System.arraycopy(model, 0, models, count * 16, 16);
        count++;
        return true;
    }

    /**
     * Number of items.
     * @return the count
     */
    public int count() { return count; }

    /**
     * The most items this list can hold.
     * @return the capacity
     */
    public int capacity() { return capacity; }

    /**
     * Mesh id of an item.
     * @param i item index, 0 until {@link #count()}
     * @return the mesh id
     */
    public int meshId(int i) { return meshIds[i]; }

    /**
     * Part index of an item.
     * @param i item index
     * @return the part index
     */
    public int part(int i) { return parts[i]; }

    /**
     * Colour of an item.
     * @param i item index
     * @return 0xRRGGBB
     */
    public int colorRgb(int i) { return colors[i]; }

    /**
     * Whether an item is drawn flat.
     * @param i item index
     * @return true for flat colour
     */
    public boolean flat(int i) { return flats[i]; }

    /**
     * Whether an item is drawn without fog.
     * @param i item index
     * @return true for no fog
     */
    public boolean noFog(int i) { return noFogs[i]; }

    /**
     * Viewport mask of an item.
     * @param i item index
     * @return the mask
     */
    public int viewportMask(int i) { return masks[i]; }

    /**
     * All model matrices, 16 floats per item, item {@code i} starting at {@link #modelOffset(int)}.
     * Read only.
     * @return the shared array
     */
    public float[] models() { return models; }

    /**
     * Where an item's matrix starts inside {@link #models()}.
     * @param i item index
     * @return the offset
     */
    public int modelOffset(int i) { return i * 16; }

    /**
     * Replaces this list's content with a copy of another list (no allocation). Items beyond this list's
     * capacity are dropped.
     * @param src the list to copy
     */
    public void copyFrom(RenderList src) {
        int n = Math.min(src.count, capacity);
        System.arraycopy(src.meshIds, 0, meshIds, 0, n);
        System.arraycopy(src.parts, 0, parts, 0, n);
        System.arraycopy(src.colors, 0, colors, 0, n);
        System.arraycopy(src.masks, 0, masks, 0, n);
        System.arraycopy(src.flats, 0, flats, 0, n);
        System.arraycopy(src.noFogs, 0, noFogs, 0, n);
        System.arraycopy(src.models, 0, models, 0, n * 16);
        count = n;
    }
}
