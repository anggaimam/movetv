package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.PaletteSwatches;

/**
 * How a track's world looks (T-29): sky, fog, sun, ground, backdrop rings and the road colours. Plain Java. Colours are
 * {@code 0xRRGGBB}. A track picks its theme by {@code TrackDef.themeId}; {@link Themes#byId} gives the theme.
 *
 * <p>Every colour reaches the screen through a swatch in the colormap texture ({@link #fillPalette}), so the road, the ground
 * patches and the rings share one texture and one shader. Swatches 0 to 15 are the road ({@link RoadStyle} uses 0 to 9);
 * 16 and up are this theme's: ground, the two patch colours, white and pale blue for stars, then two per ring.
 */
public final class Theme {

    /** Ring profiles. */
    public static final int MESAS = 0, ROLLING = 1, SKYLINE = 2, PEAKS = 3, CRATERS = 4, STARS = 5;
    /** Slippery patch kinds (T-31). */
    public static final int SLICK_NONE = 0, SLICK_ICE = 1, SLICK_WATER = 2;

    /** First swatch of the theme's own colours, and the fixed ones. */
    public static final int SW_GROUND = 16, SW_PATCH_A = 17, SW_PATCH_B = 18, SW_WHITE = 19, SW_PALE_BLUE = 20, SW_FIRST_RING = 21;

    /** One backdrop ring: a silhouette band round the camera (or, for STARS, a sprinkle of small quads). */
    public static final class Ring {
        /** Distance from the camera in metres; must stay under the far plane (60). */
        public final float radius;
        /** Body colour. */
        public final int color;
        /** MESAS, ROLLING, SKYLINE, PEAKS, CRATERS or STARS. */
        public final int profile;
        /** Lowest and highest top height in metres (the gaps of MESAS and the valleys of PEAKS go below minH). */
        public final float minH, maxH;
        /** Columns round the ring (for STARS: the number of stars). */
        public final int segments;
        /** Seed of the profile. */
        public final long seed;
        /** Whether the ring is kept on LOW quality. */
        public final boolean low;
        /** Second colour: snow caps (PEAKS), star tint (STARS), or 0. */
        public final int accent;

        public Ring(float radius, int color, int profile, float minH, float maxH, int segments, long seed, boolean low,
                    int accent) {
            this.radius = radius;
            this.color = color;
            this.profile = profile;
            this.minH = minH;
            this.maxH = maxH;
            this.segments = segments;
            this.seed = seed;
            this.low = low;
            this.accent = accent;
        }
    }

    public final String id;
    /** Clear colour (the upper sky). */
    public final int sky;
    /** Fog colour, and the colour the far ground fades to. */
    public final int horizon;
    public final float fogNear, fogFar;
    /** The way the sun's light travels (not normalised). */
    public final float[] sunDir;
    public final int ground, patchA, patchB;
    /** Ground patches per track on HIGH (LOW keeps half). */
    public final int patchCount;
    public final float patchMinR, patchMaxR;
    public final RoadStyle road;
    public final Ring[] rings;
    /** Landing dust colour (T-31), 0 for none. */
    public final int dust;
    /** {@link #SLICK_NONE}, {@link #SLICK_ICE} or {@link #SLICK_WATER} (T-31). */
    public final int slick;

    public Theme(String id, int sky, int horizon, float fogNear, float fogFar, float[] sunDir, int ground, int patchA,
                 int patchB, int patchCount, float patchMinR, float patchMaxR, RoadStyle road, Ring[] rings, int dust,
                 int slick) {
        this.id = id;
        this.sky = sky;
        this.horizon = horizon;
        this.fogNear = fogNear;
        this.fogFar = fogFar;
        this.sunDir = sunDir.clone();
        this.ground = ground;
        this.patchA = patchA;
        this.patchB = patchB;
        this.patchCount = patchCount;
        this.patchMinR = patchMinR;
        this.patchMaxR = patchMaxR;
        this.road = road;
        this.rings = rings.clone();
        this.dust = dust;
        this.slick = slick;
    }

    /**
     * The swatch of a ring's body colour.
     * @param ring ring index
     * @return the swatch index
     */
    public int ringSwatch(int ring) {
        return SW_FIRST_RING + 2 * ring;
    }

    /**
     * The swatch of a ring's accent colour (the body colour when the ring has no accent).
     * @param ring ring index
     * @return the swatch index
     */
    public int ringAccentSwatch(int ring) {
        return SW_FIRST_RING + 2 * ring + 1;
    }

    /**
     * Writes every colour of the theme into the palette: the road into swatches 0 to 9, then ground, patches, white, pale
     * blue and the rings from 16 up.
     * @param p the palette
     * @return the number of swatches in use
     * @throws IllegalStateException if the theme needs more than 224 swatches
     */
    public int fillPalette(PaletteSwatches p) {
        int count = SW_FIRST_RING + 2 * rings.length;
        if (count > PaletteSwatches.MAX) throw new IllegalStateException("theme " + id + " needs " + count + " swatches");
        road.paint(p);
        p.set(SW_GROUND, ground);
        p.set(SW_PATCH_A, patchA);
        p.set(SW_PATCH_B, patchB);
        p.set(SW_WHITE, 0xFFFFFF);
        p.set(SW_PALE_BLUE, 0xA8DDF2);
        for (int i = 0; i < rings.length; i++) {
            p.set(ringSwatch(i), rings[i].color);
            p.set(ringAccentSwatch(i), rings[i].accent != 0 ? rings[i].accent : rings[i].color);
        }
        return count;
    }
}
