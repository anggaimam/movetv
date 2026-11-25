package com.wiggleplay.app.core;

/**
 * The rules {@link Profile} applies to saved values: which values are allowed and what a missing or
 * damaged value turns into. Plain Java (no android imports), so it is unit-tested on the JVM.
 */
public final class ProfileLogic {

    /** The hold times a player can pick, in milliseconds. */
    public static final int[] HOLD_CHOICES = {700, 1000, 1400};
    /** The hold time used when nothing valid is saved. */
    public static final int DEFAULT_HOLD_MS = 1000;
    /** The player count used when nothing valid is saved. */
    public static final int DEFAULT_PLAYERS = 1;

    /** Pointer setting: the cursor sits on the hand. */
    public static final String POINTER_HAND = "hand";
    /** Pointer setting: the cursor reaches further than the hand. */
    public static final String POINTER_REACH = "reach";
    /** The pointer setting used when nothing valid is saved. */
    public static final String DEFAULT_POINTER = POINTER_HAND;
    /** Gain of "on my hand". */
    public static final float GAIN_HAND = 1.0f;
    /** Gain of "easy reach". */
    public static final float GAIN_REACH = 1.6f;

    /** Airplane arms: no longer offered in Turbo Trucks (T-23 Part B2); the Sky Glider uses its own arms input. */
    public static final String STEER_PLANE = "plane";
    /** Turbo Trucks steering: lean the shoulders, arms down. */
    public static final String STEER_LEAN = "lean";
    /** Turbo Trucks steering: an invisible go-kart wheel. */
    public static final String STEER_WHEEL = "wheel";
    /** The Turbo Trucks steering used when nothing valid is saved. */
    public static final String DEFAULT_RACE_STEER = STEER_WHEEL;

    /** Picture setting: drop to LOW by itself when play is slow. */
    public static final String QUALITY_AUTO = "auto";
    /** Picture setting: always HIGH. */
    public static final String QUALITY_HIGH = "high";
    /** Picture setting: always LOW. */
    public static final String QUALITY_LOW = "low";

    private ProfileLogic() { }

    /**
     * Checks a Picture setting.
     * @param o the saved value (anything, even null)
     * @return "high" or "low" if the value is that string, otherwise "auto"
     */
    public static String cleanQuality(Object o) {
        if (QUALITY_HIGH.equals(o)) return QUALITY_HIGH;
        if (QUALITY_LOW.equals(o)) return QUALITY_LOW;
        return QUALITY_AUTO;
    }

    /**
     * The next Picture setting when the tile is pressed: auto, high, low, auto.
     * @param cur the current setting (anything is cleaned first)
     * @return the next setting
     */
    public static String nextQuality(Object cur) {
        String c = cleanQuality(cur);
        if (QUALITY_AUTO.equals(c)) return QUALITY_HIGH;
        if (QUALITY_HIGH.equals(c)) return QUALITY_LOW;
        return QUALITY_AUTO;
    }

    /**
     * Checks a Turbo Trucks steering setting.
     * @param o the saved value (anything, even null)
     * @return "lean" or "wheel"; anything else (an old "plane" included) becomes "wheel"
     */
    public static String cleanRaceSteer(Object o) {
        if (STEER_LEAN.equals(o)) return STEER_LEAN;
        return STEER_WHEEL;
    }

    /** Turbo Trucks truck colours in the order the Truck button cycles them (T-32), and the default. */
    public static final String[] RACE_TRUCKS = {"yellow", "red", "green", "purple"};
    public static final String DEFAULT_RACE_TRUCK = "yellow";
    /** The default Turbo Trucks track (T-32). */
    public static final String DEFAULT_RACE_TRACK = "desert";

    /**
     * Checks a saved Turbo Trucks track.
     * @param o the saved value (anything, even null)
     * @return the id of one of the tracks ({@code desert}, {@code city}, {@code rockies}, {@code space}); anything else becomes "desert"
     */
    public static String cleanRaceTrack(Object o) {
        if (o instanceof String) {
            for (com.wiggleplay.app.games.race.TrackDef d : com.wiggleplay.app.games.race.TrackDefs.ALL) {
                if (d.id.equals(o)) return d.id;
            }
        }
        return DEFAULT_RACE_TRACK;
    }

    /**
     * Checks the saved direction of the Turbo Trucks track.
     * @param o the saved value (anything, even null)
     * @return true only for a saved {@code true}
     */
    public static boolean cleanRaceReversed(Object o) {
        return Boolean.TRUE.equals(o);
    }

    /**
     * Checks a saved truck colour.
     * @param o the saved value (anything, even null)
     * @return "yellow", "red", "green" or "purple"; anything else becomes "yellow"
     */
    public static String cleanRaceTruck(Object o) {
        if (o instanceof String) {
            for (String t : RACE_TRUCKS) if (t.equals(o)) return t;
        }
        return DEFAULT_RACE_TRUCK;
    }

    /**
     * The position of a truck colour in {@link #RACE_TRUCKS}.
     * @param cur a saved colour (anything, even null)
     * @return 0 (yellow), 1 (red), 2 (green) or 3 (purple)
     */
    public static int raceTruckIndex(Object cur) {
        String c = cleanRaceTruck(cur);
        for (int i = 0; i < RACE_TRUCKS.length; i++) if (RACE_TRUCKS[i].equals(c)) return i;
        return 0;
    }

    /**
     * The colour after a truck colour in the cycle yellow, red, green, purple.
     * @param cur the current colour (anything, even null)
     * @return the next colour
     */
    public static String nextRaceTruck(Object cur) {
        String c = cleanRaceTruck(cur);
        for (int i = 0; i < RACE_TRUCKS.length; i++) if (RACE_TRUCKS[i].equals(c)) return RACE_TRUCKS[(i + 1) % RACE_TRUCKS.length];
        return DEFAULT_RACE_TRUCK;
    }

    /**
     * Checks a saved best star count.
     * @param o the saved value (anything, even null)
     * @return 0 to 3; a value outside the range is clamped, anything that is not a number is 0 (never played)
     */
    public static int cleanRaceBest(Object o) {
        if (!(o instanceof Number)) return 0;
        double d = ((Number) o).doubleValue();
        if (Double.isNaN(d)) return 0;
        return (int) Math.max(0, Math.min(3, Math.round(d)));
    }

    /**
     * Whether a finished race's stars should replace the saved best.
     * @param saved the saved best (anything, even null)
     * @param earned the stars of the race just finished
     * @return true only when the earned stars are higher than the saved ones
     */
    public static boolean raisesRaceBest(Object saved, int earned) {
        return cleanRaceBest(earned) > cleanRaceBest(saved);
    }

    /**
     * The profile key of the best stars on a track and level.
     * @param trackKey {@code desert} or {@code desert-r} (see TrackSpline.key)
     * @param level 1 (Easy), 2 (Medium) or 3 (Hard)
     * @return for example {@code raceBest_desert-r_MEDIUM}
     */
    public static String raceBestKey(String trackKey, int level) {
        String[] names = {"EASY", "MEDIUM", "HARD"};
        return "raceBest_" + trackKey + "_" + names[Math.max(1, Math.min(3, level)) - 1];
    }

    /**
     * Checks a pointer setting.
     * @param o the saved value (anything, even null)
     * @return "reach" if the value is the string "reach", otherwise "hand"
     */
    public static String cleanPointer(Object o) {
        return POINTER_REACH.equals(o) ? POINTER_REACH : DEFAULT_POINTER;
    }

    /**
     * How far the cursor reaches for a pointer setting.
     * @param pointer "hand" or "reach" (anything else counts as "hand")
     * @return 1.0 for "hand", 1.6 for "reach"
     */
    public static float pointerGain(String pointer) {
        return POINTER_REACH.equals(pointer) ? GAIN_REACH : GAIN_HAND;
    }

    /**
     * Reads a saved number whatever type it was stored as.
     * @param o the stored value: an Integer or other Number, a numeric String, or anything else (even null)
     * @param def returned when the value is missing or not a number
     * @return the number as an int
     */
    public static int asInt(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try {
                return Integer.parseInt(((String) o).trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    /**
     * Checks a hold time.
     * @param ms the saved value
     * @return the value if it is 700, 1000 or 1400, otherwise 1000
     */
    public static int cleanHold(int ms) {
        for (int h : HOLD_CHOICES) if (h == ms) return ms;
        return DEFAULT_HOLD_MS;
    }

    /**
     * Checks a player count.
     * @param n the saved value
     * @return 2 if the value is 2, otherwise 1
     */
    public static int cleanPlayers(int n) {
        return n == 2 ? 2 : DEFAULT_PLAYERS;
    }

    /**
     * Checks a star count.
     * @param n the saved value
     * @return the value if it is 0 to 3, otherwise 0 (a damaged value just means "play it again")
     */
    public static int cleanStars(int n) {
        return n >= 0 && n <= 3 ? n : 0;
    }

    /**
     * Checks a level number.
     * @param level the value
     * @return 1 to 3; anything else becomes 1
     */
    public static int cleanLevel(int level) {
        return level >= 1 && level <= 3 ? level : 1;
    }

    /**
     * The name under which the stars of one game level are saved.
     * @param gameId the game id, for example "fruit"
     * @param level 1 to 3 (anything else is treated as 1)
     * @return the key, for example "stars_fruit_2"
     */
    public static String starsKey(String gameId, int level) {
        return "stars_" + gameId + "_" + cleanLevel(level);
    }
}
