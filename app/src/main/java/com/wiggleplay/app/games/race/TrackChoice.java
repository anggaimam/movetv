package com.wiggleplay.app.games.race;

/**
 * Which of the 8 tracks (4 shapes x 2 directions) Turbo Trucks races (T-28 Part D). Kept in memory only, not in the profile:
 * a temporary chip on the level screen cycles it with the remote's fast-forward key until T-32 brings the track cards.
 * Order: desert, desert reversed, city, city reversed, rockies, rockies reversed, space, space reversed.
 */
public final class TrackChoice {

    private static int index;

    private TrackChoice() { }

    /** @return the chosen index 0..7 */
    public static int index() {
        return index;
    }

    /** Moves to the next track (after the last comes the first). */
    public static void next() {
        index = (index + 1) % (TrackDefs.ALL.length * 2);
    }

    /**
     * Sets the choice (tests use this).
     * @param i an index 0..7; anything else wraps
     */
    public static void set(int i) {
        int n = TrackDefs.ALL.length * 2;
        index = ((i % n) + n) % n;
    }

    /**
     * Sets the choice from the saved profile values (T-32).
     * @param id a track id; an unknown id is the first track
     * @param reversed true for the other way round
     */
    public static void load(String id, boolean reversed) {
        int shape = 0;
        for (int i = 0; i < TrackDefs.ALL.length; i++) if (TrackDefs.ALL[i].id.equals(id)) shape = i;
        set(shape * 2 + (reversed ? 1 : 0));
    }

    /** @return the chosen shape */
    public static TrackDef def() {
        return TrackDefs.ALL[index / 2];
    }

    /** @return true when the chosen track is driven the other way round */
    public static boolean reversed() {
        return (index & 1) == 1;
    }

    /** @return the shape's name, for the chip ("Desert Dunes") */
    public static String name() {
        return def().name;
    }

    /** @return the key used for caches and stars ({@code desert} or {@code desert-r}) */
    public static String key() {
        return TrackSpline.key(def(), reversed());
    }
}
