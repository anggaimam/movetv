package com.wiggleplay.app.games.race;

/**
 * The three race tracks of Turbo Trucks as ASCII maps. {@code o} road, {@code S} road with the finish
 * line, {@code .} automatic decoration, {@code T} tents, space nothing. Row = z, column = x. In every map
 * the cars leave {@code S} heading west ({@link #START_DIR}).
 */
public final class TrackMaps {

    private TrackMaps() { }

    /** The driving direction leaving the finish line on every map: W = towards -X. */
    public static final char START_DIR = 'W';

    /** Easy: 22 tiles, 4 corners. */
    public static final String[] EASY = {
        "oooooooo",
        "o......o",
        "o......o",
        "o......o",
        "oooSoooo",
    };

    /** Medium: 28 tiles, 6 corners. */
    public static final String[] MEDIUM = {
        "ooooooo...",
        "o.....o...",
        "o.....oooo",
        "o........o",
        "o........o",
        "oooSoooooo",
    };

    /** Hard: 40 tiles, 12 corners. */
    public static final String[] HARD = {
        "oooo...oooo",
        "o..o...o..o",
        "o..ooooo..o",
        "o.........o",
        "o...ooo...o",
        "o...o.o...o",
        "ooSoo.ooooo",
    };

    /**
     * Parses the map of a level.
     * @param level 0 = Easy, 1 = Medium, 2 = Hard
     * @return the layout
     * @throws IllegalArgumentException for any other level
     */
    public static TrackLayout forLevel(int level) {
        switch (level) {
            case 0:
                return TrackLayout.parse(EASY, START_DIR);
            case 1:
                return TrackLayout.parse(MEDIUM, START_DIR);
            case 2:
                return TrackLayout.parse(HARD, START_DIR);
            default:
                throw new IllegalArgumentException("level must be 0, 1 or 2: " + level);
        }
    }
}
