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
