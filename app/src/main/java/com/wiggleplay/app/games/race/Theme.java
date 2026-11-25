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
    /** Prop orientations: any yaw; the local +z (the front) turned to the road; the local z parallel to the road. */
    public static final int ORIENT_RANDOM = 0, ORIENT_FACE_ROAD = 1, ORIENT_PARALLEL = 2;
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

    /**
     * The light, the sky and the surface grain of a theme (T-29R Part R1).
     */
    public static final class Look {
        /** The old fixed look: a 0.62 grey ambient everywhere, a 0.38 grey sun, no sky dome, no surface grain. */
        public static final Look PLAIN = new Look(new float[] {0.62f, 0.62f, 0.62f}, new float[] {0.62f, 0.62f, 0.62f},
                new float[] {0.38f, 0.38f, 0.38f}, 0, 0, null);

        /** Ambient light from above (a surface facing up gets all of it), from below, and the sun's colour; each r, g, b 0 to 1. */
        public final float[] skyAmb, groundAmb, sunColor;
        /** Colour at the top of the sky dome ({@code 0xRRGGBB}); 0 means no sky dome. */
        public final int zenith;
        /** Colour of the sun disc; used only with a sky dome. */
        public final int sunDisc;
        /** Grain kinds ({@code DetailTexture.SAND} and so on) of the four tiles: ground, road, rock or aux, kerb and barrier; null = no grain. */
        public final int[] detail;
        /** The shape and colours of the ground (T-29R R2); null keeps the flat ground grid and the patches. */
        public final Terrain.Profile terrain;

        public Look(float[] skyAmb, float[] groundAmb, float[] sunColor, int zenith, int sunDisc, int[] detail) {
            this(skyAmb, groundAmb, sunColor, zenith, sunDisc, detail, null);
        }

        public Look(float[] skyAmb, float[] groundAmb, float[] sunColor, int zenith, int sunDisc, int[] detail,
                    Terrain.Profile terrain) {
            this.terrain = terrain;
            this.skyAmb = skyAmb.clone();
            this.groundAmb = groundAmb.clone();
            this.sunColor = sunColor.clone();
            this.zenith = zenith;
            this.sunDisc = sunDisc;
            this.detail = detail == null ? null : detail.clone();
        }

        /**
         * A colour times a brightness.
         * @param rgb {@code 0xRRGGBB}
         * @param k brightness
         * @return r, g, b in 0..1 (not clamped)
         */
        public static float[] scaled(int rgb, float k) {
            return new float[] {((rgb >> 16) & 0xFF) / 255f * k, ((rgb >> 8) & 0xFF) / 255f * k, (rgb & 0xFF) / 255f * k};
        }
    }

    /** One scatter rule of a theme (T-29 Part B): which prop, how many, how far from the road, how big. */
    public static final class PropRule {
        /** A {@link PropMeshes} kind. */
        public final int kind;
        /** Props per 100 m of lap. */
        public final float per100m;
        /** Distance from the centre line, nearest and farthest. {@code minDist} must be at least 6.4 + footprint + 0.5. */
        public final float minDist, maxDist;
        /** Radius used for spacing from the road and from other props. */
        public final float footprint;
        public final float minScale, maxScale;
        /** True for the filler tier, dropped on LOW quality (and in 2P). */
        public final boolean tierB;
        /** Colour ramps of the prop as pairs {@code dark, light} ({@code 0xRRGGBB}); the meaning of each pair is set by the kind. */
        public final int[] colors;
        /** How the prop is turned: {@link #ORIENT_RANDOM}, facing the road, or parallel to it (T-30a). */
        public final int orient;
        /** A prop taller than {@code tallAbove} (m, after scaling; 0 = no rule) stands at least {@code tallMinDist} from the road. */
        public final float tallAbove, tallMinDist;

        public PropRule(int kind, float per100m, float minDist, float maxDist, float footprint, float minScale,
                        float maxScale, boolean tierB, int[] colors) {
            this(kind, per100m, minDist, maxDist, footprint, minScale, maxScale, tierB, colors, ORIENT_RANDOM, 0f, 0f);
        }

        public PropRule(int kind, float per100m, float minDist, float maxDist, float footprint, float minScale,
                        float maxScale, boolean tierB, int[] colors, int orient, float tallAbove, float tallMinDist) {
            this.orient = orient;
            this.tallAbove = tallAbove;
            this.tallMinDist = tallMinDist;
            this.kind = kind;
            this.per100m = per100m;
            this.minDist = minDist;
            this.maxDist = maxDist;
            this.footprint = footprint;
            this.minScale = minScale;
            this.maxScale = maxScale;
            this.tierB = tierB;
            this.colors = colors.clone();
        }

        /** Number of ramps (colour pairs). */
        public int ramps() {
            return colors.length / 2;
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
    /** The props scattered along the track (empty for none). */
    public final PropRule[] props;
    /** Light, sky dome and surface grain ({@link Look#PLAIN} for the old look). */
    public final Look look;

    public Theme(String id, int sky, int horizon, float fogNear, float fogFar, float[] sunDir, int ground, int patchA,
                 int patchB, int patchCount, float patchMinR, float patchMaxR, RoadStyle road, Ring[] rings, int dust,
                 int slick) {
        this(id, sky, horizon, fogNear, fogFar, sunDir, ground, patchA, patchB, patchCount, patchMinR, patchMaxR, road,
                rings, dust, slick, new PropRule[0]);
    }

    public Theme(String id, int sky, int horizon, float fogNear, float fogFar, float[] sunDir, int ground, int patchA,
                 int patchB, int patchCount, float patchMinR, float patchMaxR, RoadStyle road, Ring[] rings, int dust,
                 int slick, PropRule[] props) {
        this(id, sky, horizon, fogNear, fogFar, sunDir, ground, patchA, patchB, patchCount, patchMinR, patchMaxR, road,
                rings, dust, slick, props, Look.PLAIN);
    }

    public Theme(String id, int sky, int horizon, float fogNear, float fogFar, float[] sunDir, int ground, int patchA,
                 int patchB, int patchCount, float patchMinR, float patchMaxR, RoadStyle road, Ring[] rings, int dust,
                 int slick, PropRule[] props, Look look) {
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
        this.props = props.clone();
        this.look = look;
    }

    /** Whether the theme has a sky dome (a zenith colour). */
    public boolean hasSky() {
        return look.zenith != 0;
    }

    /** First swatch of the sky: the three swatches follow the prop colours. */
    private int skyBase() {
        int n = SW_FIRST_RING + 2 * rings.length;
        for (PropRule r : props) n += r.ramps();
        return n;
    }

    /**
     * The sky swatches (only with a sky dome): 0 = ramp from the horizon colour to the zenith colour, 1 = the sun disc,
     * 2 = the glow ramp from the sun disc colour to the sky colour at the sun's height.
     * @param k 0, 1 or 2
     * @return the swatch index
     */
    public int skySwatch(int k) {
        return skyBase() + k;
    }

    /**
     * The sky colour at a height on the dome.
     * @param t 0 at the horizon, 1 at the zenith
     * @return {@code 0xRRGGBB}
     */
    public int skyColorAt(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        int a = horizon, b = look.zenith;
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * c);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * c);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * c);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * The sun's height above the horizon.
     * @return degrees, from the sun direction (the light travels along {@code sunDir}, so the sun is opposite)
     */
    public float sunElevationDeg() {
        float len = (float) Math.sqrt(sunDir[0] * sunDir[0] + sunDir[1] * sunDir[1] + sunDir[2] * sunDir[2]);
        return (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, -sunDir[1] / len))));
    }

    /**
     * The swatch of colour {@code colorIndex} of prop rule {@code rule} (they follow the rings).
     * @param rule index into {@link #props}
     * @param colorIndex index into that rule's colours
     * @return the swatch index
     */
    public int propSwatch(int rule, int colorIndex) {
        int n = SW_FIRST_RING + 2 * rings.length;
        for (int i = 0; i < rule; i++) n += props[i].ramps();
        return n + colorIndex;
    }

    /**
     * The swatch indices of one prop rule's colours.
     * @param rule index into {@link #props}
     * @return the swatches, one per colour
     */
    public int[] propSwatches(int rule) {
        int[] out = new int[props[rule].ramps()];
        for (int i = 0; i < out.length; i++) out[i] = propSwatch(rule, i);
        return out;
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
        for (PropRule r : props) count += r.ramps();
        if (hasSky()) count += 3;
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
        for (int r = 0; r < props.length; r++) {
            for (int c = 0; c < props[r].ramps(); c++) p.setRamp(propSwatch(r, c), props[r].colors[2 * c], props[r].colors[2 * c + 1]);
        }
        if (hasSky()) {
            p.setRamp(skySwatch(0), horizon, look.zenith);
            p.set(skySwatch(1), look.sunDisc);
            p.setRamp(skySwatch(2), look.sunDisc, skyColorAt(sunElevationDeg() / 90f));
        }
        if (look.terrain != null) {
            p.setRamp(terrainSwatch(0), look.terrain.groundDark, look.terrain.groundLight);
            p.setRamp(terrainSwatch(1), look.terrain.shoulderDark, look.terrain.shoulderLight);
            if (look.terrain.parkDark != 0) p.setRamp(terrainSwatch(2), look.terrain.parkDark, look.terrain.parkLight);
        }
        return count;
    }

    /**
     * The terrain swatches (only with a terrain profile): 0 = the ground ramp, 1 = the packed shoulder ramp. They take the places of the
     * flat ground and the first patch colour (16 and 17), which a terrain theme does not use; the shader gives blocks 16 to 18 the sand grain.
     * @param k 0 or 1, or 2 for the park grass ramp (a terrain with parks only; the grain of the ground)
     * @return the swatch index
     */
    public int terrainSwatch(int k) {
        return SW_GROUND + k;
    }
}
