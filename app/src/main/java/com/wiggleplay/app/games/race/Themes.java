package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.DetailTexture;

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
            0xE9DCC6, Theme.SLICK_WATER,
            new Theme.PropRule[] {
                    new Theme.PropRule(PropMeshes.SAGUARO, 4f, 8f, 25f, 0.6f, 0.8f, 1.3f, false,
                            new int[] {0x5E9B5A, 0x4B7F48}),
                    new Theme.PropRule(PropMeshes.BUTTE, 0.5f, 22f, 38f, 7f, 0.8f, 1.2f, false,
                            new int[] {0xCF8A5C, 0xE0A574}),
                    new Theme.PropRule(PropMeshes.BARREL_CACTI, 4f, 7.7f, 20f, 0.8f, 0.8f, 1.2f, true,
                            new int[] {0x6FA65F, 0xE8A598}),
                    new Theme.PropRule(PropMeshes.ROCKS, 5f, 8.1f, 30f, 1.2f, 0.7f, 1.4f, true,
                            new int[] {0xC98B5E, 0xB07450}),
                    new Theme.PropRule(PropMeshes.JOSHUA_TREE, 1.5f, 10f, 30f, 1f, 0.8f, 1.2f, true,
                            new int[] {0x8A7356, 0x7FA15A})},
            // T-29R R1: warm sun, bluish sky light from above, sandy bounce light from below; sky dome, grain tiles
            new Theme.Look(Theme.Look.scaled(0x9DB8C6, 0.62f), Theme.Look.scaled(0xD9B98A, 0.50f),
                    Theme.Look.scaled(0xFFE9C4, 0.62f), 0x6FAFD6, 0xFFF6DD,
                    new int[] {DetailTexture.SAND, DetailTexture.ASPHALT, DetailTexture.GRAVEL, DetailTexture.CONCRETE}));

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
