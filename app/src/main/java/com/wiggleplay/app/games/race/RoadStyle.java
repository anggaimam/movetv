package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.PaletteSwatches;

/**
 * The colours of a road (T-28 Part C), as {@code 0xRRGGBB}. {@link #paint} puts them into swatches 0 to 9 of the palette;
 * themes (T-29) use swatches 16 and up.
 */
public final class RoadStyle {

    /** Ramp swatches of the detailed road (T-29R R3): asphalt, paint, the two kerb colours, galvanised rail, dark post. */
    public static final int RAMP_ASPHALT = 100, RAMP_PAINT = 101, RAMP_KERB_A = 102, RAMP_KERB_B = 103, RAMP_RAIL = 104,
            RAMP_POST = 105;

    /** A colour times a brightness, clamped. */
    static int shade(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((rgb & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }

    /** Swatch indices of the road colours. */
    public static final int SW_ROAD = 0, SW_KERB_A = 1, SW_KERB_B = 2, SW_BARRIER = 3, SW_BARRIER_STRIPE = 4,
            SW_START_DARK = 5, SW_START_LIGHT = 6, SW_ARCH_POST = 7, SW_ARCH_BANNER = 8, SW_CENTRE_DASH = 9;

    /**
     * The default: Kenney's own road grey ({@code #7D829C}, the asphalt colour of the {@code track-straight} tile, read from
     * {@code colormap.png} through the mesh's uvs), red and cream kerbs, pale barriers with orange stripes, a dark and cream
     * start line, cream posts and an orange banner, no centre dashes.
     */
    public static final RoadStyle MEADOW = new RoadStyle(0x7D829C, 0xE8574A, 0xF3EBDD, 0xD7DCE0, 0xE89A4C, 0x2F3A45,
            0xF3EBDD, 0xF3EBDD, 0xE89A4C, 0);

    public final int road, kerbA, kerbB, barrier, barrierStripe, startDark, startLight, archPost, archBanner;
    /** Colour of the centre dashes, 0 for no dashes. */
    public final int centreDash;

    public RoadStyle(int road, int kerbA, int kerbB, int barrier, int barrierStripe, int startDark, int startLight,
                     int archPost, int archBanner, int centreDash) {
        this.road = road;
        this.kerbA = kerbA;
        this.kerbB = kerbB;
        this.barrier = barrier;
        this.barrierStripe = barrierStripe;
        this.startDark = startDark;
        this.startLight = startLight;
        this.archPost = archPost;
        this.archBanner = archBanner;
        this.centreDash = centreDash;
    }

    /**
     * Fills swatches 0 to 9 (road, kerbA, kerbB, barrier, barrierStripe, startDark, startLight, archPost, archBanner,
     * centreDash). Swatch 9 holds the road colour when there are no dashes.
     * @param pal the palette
     */
    public void paint(PaletteSwatches pal) {
        pal.set(SW_ROAD, road);
        pal.set(SW_KERB_A, kerbA);
        pal.set(SW_KERB_B, kerbB);
        pal.set(SW_BARRIER, barrier);
        pal.set(SW_BARRIER_STRIPE, barrierStripe);
        pal.set(SW_START_DARK, startDark);
        pal.set(SW_START_LIGHT, startLight);
        pal.set(SW_ARCH_POST, archPost);
        pal.set(SW_ARCH_BANNER, archBanner);
        pal.set(SW_CENTRE_DASH, centreDash != 0 ? centreDash : road);
        pal.setRamp(RAMP_ASPHALT, shade(road, 0.82f), shade(road, 1.18f));
        pal.setRamp(RAMP_PAINT, 0xD8D8D2, 0xF4F4EE);
        pal.setRamp(RAMP_KERB_A, shade(kerbA, 0.7f), shade(kerbA, 1.15f));
        pal.setRamp(RAMP_KERB_B, shade(kerbB, 0.75f), shade(kerbB, 1.05f));
        pal.setRamp(RAMP_RAIL, 0x9CA3A8, 0xE6EAEC);
        pal.setRamp(RAMP_POST, 0x5E656B, 0x7E858B);
    }
}
