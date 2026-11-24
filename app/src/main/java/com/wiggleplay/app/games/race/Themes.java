package com.wiggleplay.app.games.race;

/** The themes (T-29). {@code MEADOW} is today's look (and the answer for a theme that is not built yet); T-30 adds the others. */
public final class Themes {

    private Themes() { }

    /** Today's meadow: sky-blue sky and fog, green ground, no patches, no rings. */
    public static final Theme MEADOW = new Theme("meadow", 0xA8DDF2, 0xA8DDF2, 25f, 55f, new float[] {-0.4f, -0.8f, -0.45f},
            0x37966D, 0x37966D, 0x37966D, 0, 0f, 0f, RoadStyle.MEADOW, new Theme.Ring[0], 0, Theme.SLICK_NONE);

    /** Desert Dunes: pale blue sky, cream haze, sand, dune and mesa rings. */
    public static final Theme DESERT = new Theme("desert", 0xA9D6EA, 0xF3E2C3, 30f, 55f, new float[] {-0.30f, -0.85f, -0.43f},
            0xE9C98B, 0xDDB36F, 0xF2DCA8, 40, 4f, 12f,
            new RoadStyle(0x8C7E72, 0xD9534F, 0xF3EBDD, 0xCFC6BA, 0xE89A4C, 0x2F3A45, 0xF3EBDD, 0xF3EBDD, 0xE89A4C, 0),
            new Theme.Ring[] {
                    new Theme.Ring(50f, 0xE2B57C, Theme.ROLLING, 0.6f, 2.2f, 96, 11L, false, 0),
                    new Theme.Ring(54f, 0xD99A6E, Theme.MESAS, 2f, 6f, 96, 12L, true, 0),
                    new Theme.Ring(57f, 0xEBC4A0, Theme.MESAS, 4f, 11f, 96, 13L, true, 0)},
            0xE9DCC6, Theme.SLICK_WATER);

    /**
     * The theme of a track.
     * @param id {@code TrackDef.themeId}
     * @return the theme; {@link #MEADOW} for an id that is unknown or not built yet
     */
    public static Theme byId(String id) {
        if ("desert".equals(id)) return DESERT;
        return MEADOW;
    }
}
