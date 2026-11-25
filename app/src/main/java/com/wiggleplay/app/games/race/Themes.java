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
            new RoadStyle(0x5A5E65, 0xD9534F, 0xF3EBDD, 0xCFC6BA, 0xE89A4C, 0x2F3A45, 0xF3EBDD, 0xF3EBDD, 0xE89A4C, 0),
            new Theme.Ring[] {
                    new Theme.Ring(50f, 0xE2B57C, Theme.ROLLING, 0.6f, 2.2f, 192, 11L, false, 0),
                    new Theme.Ring(54f, 0xD99A6E, Theme.MESAS, 2f, 6f, 192, 12L, true, 0xC47F55),
                    new Theme.Ring(57f, 0xEBC4A0, Theme.MESAS, 4f, 11f, 192, 13L, true, 0xD8A57A)},
            0xE9DCC6, Theme.SLICK_WATER,
            new Theme.PropRule[] {
                    new Theme.PropRule(PropMeshes.SAGUARO, 6f, 8f, 30f, 0.6f, 0.8f, 1.3f, false,
                            new int[] {0x3E6E3F, 0x7BAA6A}),
                    new Theme.PropRule(PropMeshes.BUTTE, 0.5f, 22f, 45f, 7f, 0.8f, 1.2f, false,
                            new int[] {0xB86E45, 0xE3A574, 0xC98757, 0xEFC08F}),
                    new Theme.PropRule(PropMeshes.BARREL_CACTI, 4f, 7.7f, 20f, 0.8f, 0.8f, 1.2f, true,
                            new int[] {0x3E6E3F, 0x7BAA6A, 0xE8A598, 0xF7D0C8}),
                    new Theme.PropRule(PropMeshes.ROCKS, 6f, 8.1f, 35f, 1.2f, 0.7f, 1.4f, true,
                            new int[] {0x8E5E3E, 0xD9A27A}),
                    new Theme.PropRule(PropMeshes.JOSHUA_TREE, 2f, 10f, 35f, 1f, 0.8f, 1.2f, false,
                            new int[] {0x6E5A43, 0xA08766, 0x5F7F3E, 0xA3BE6B}),
                    new Theme.PropRule(PropMeshes.SCRUB, 8f, 7.6f, 30f, 0.2f, 0.8f, 1.3f, true,
                            new int[] {0x6D7A3E, 0xA8B26A}),
                    new Theme.PropRule(PropMeshes.DRY_GRASS, 20f, 7.0f, 25f, 0.1f, 0.8f, 1.4f, true,
                            new int[] {0xB89B5E, 0xE7D49A}),
                    new Theme.PropRule(PropMeshes.PEBBLES, 10f, 7.0f, 15f, 0.1f, 0.8f, 1.3f, true,
                            new int[] {0x9C7B5C, 0xD4B391})},
            // T-29R R1: warm sun, bluish sky light from above, sandy bounce light from below; sky dome, grain tiles
            new Theme.Look(Theme.Look.scaled(0x9DB8C6, 0.62f), Theme.Look.scaled(0xD9B98A, 0.50f),
                    Theme.Look.scaled(0xFFE9C4, 0.62f), 0x6FAFD6, 0xFFF6DD,
                    new int[] {DetailTexture.SAND, DetailTexture.ASPHALT, DetailTexture.GRAVEL, DetailTexture.CONCRETE},
                    // T-29R R2: dunes up to 4.5 m, ridges across a wind blowing along 20 degrees, sand ramp, packed shoulder
                    new Terrain.Profile(20f, 4.5f, 36f, 0.3f, 0xCDA66B, 0xF2D9A6, 0xA88A63, 0xC7A97E)));

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
