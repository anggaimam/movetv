package com.wiggleplay.app.games.race;

/**
 * The preset paint jobs of the Pick Your Truck screen (T-34): one colour each for the body and accessories (the whole
 * model except the wheels). Paint 0 is the truck as it comes, with its own colours.
 */
public final class TruckPaints {

    /** The truck models, in the order of {@code ProfileLogic.RACE_TRUCKS}: the meshes, then the names shown. */
    public static final String[] MESH = {"vehicle-truck-yellow", "vehicle-truck-red", "vehicle-truck-green", "vehicle-truck-purple"};
    public static final String[] TRUCK_NAMES = {"Sunny", "Rusty", "Leafy", "Grape"};

    /** Names of the paint jobs; the first is the truck's own colours. */
    public static final String[] NAMES = {"Factory", "Sky blue", "Hot pink", "Lime", "Fire red", "Snow white"};
    /** 0xRRGGBB of each paint job (0 for Factory). */
    public static final int[] RGB = {0, 0x4FA8E8, 0xFF6EB4, 0x8BD450, 0xE8453C, 0xF2F2F2};
    /** Bits 24..31 that make the renderer paint a textured item with the colour (see GlRenderer.TINT_FLAG). */
    private static final int TINT = 0x01000000;

    private TruckPaints() { }

    /**
     * The colour value to give the render list for a paint job on the body and accessories.
     * @param paint a paint job number (anything out of range counts as Factory)
     * @return 0 for the truck's own colours, else the colour with the paint flag set
     */
    public static int renderColour(int paint) {
        return paint > 0 && paint < RGB.length ? RGB[paint] | TINT : 0;
    }

    /**
     * Keeps a paint job number in range.
     * @param paint any number
     * @return the same number, or 0 when it is not a paint job
     */
    public static int clean(int paint) {
        return paint >= 0 && paint < RGB.length ? paint : 0;
    }
}
