package com.wiggleplay.app.games.race;

/**
 * The three race levels: how fast the truck goes, how sharply it can turn and how strongly the road pulls it
 * back to the centre. Plain Java.
 */
public enum RaceLevel {
    /** 2 laps, road 11 m wide. The rocket meter starts full, fills by itself (0.2 per second) and with coins. */
    EASY(11f, 1.6f, 0.5f, false, 0, 5.5f, 2, 0.55f, 0.34f, 0.2f, 0),
    /** 3 laps, road 10 m wide. T-26: the rocket is earned with 4 nitro cans per lap (all on bends). */
    MEDIUM(13f, 1.9f, 0.25f, true, 4, 5.0f, 3, 0.55f, 0.30f, 0.2f, 4),
    /** 3 laps, road 9 m wide. T-26: the rocket is earned with 5 nitro cans per lap (2 on bends, 3 in side lanes). */
    HARD(15f, 2.2f, 0.1f, true, 5, 4.5f, 3, 0.55f, 0.25f, 0.2f, 2);

    /** Extra road assist in two-player races. */
    public static final float TWO_PLAYER_ASSIST = 0.15f;

    /** Normal speed in units per second. */
    public final float baseSpeed;
    /** Fastest turn in radians per second. */
    public final float maxYaw;
    /** How strongly the road pulls toward the centre line. */
    public final float assist;
    /**
     * T-26: true when the rocket must be earned. The meter then starts empty, does not refill with time, and coins do
     * not fill it; only nitro cans do.
     */
    public final boolean earnedBoost;
    /** Nitro cans on every lap (0 on Easy). */
    public final int nitroPerLap;
    /** T-28: half the width of the drivable road on a spline track (Easy 5.5, Medium 5.0, Hard 4.5). */
    public final float halfWidth;
    /** T-28: laps in a race (Easy 2, Medium 3, Hard 3). */
    public final int laps;
    /** T-28: a side lane is at plus or minus this times the half-width. */
    public final float laneFrac;
    /** T-28: chance that a straight run of coins is in the centre lane (the tuning knob for the star bands). */
    public final float centreWeight;
    /** T-28: coins on a bend sit this times the half-width toward the outside of the bend. */
    public final float bendFrac;
    /** T-28: how many of the nitro cans of a lap sit on bend runs (the others sit in side lanes). */
    public final int nitroBendCans;

    /** How much one nitro can fills the rocket meter (two cans make a full rocket). */
    public static final float NITRO_FILL = 0.5f;

    RaceLevel(float baseSpeed, float maxYaw, float assist, boolean earnedBoost, int nitroPerLap, float halfWidth, int laps,
              float laneFrac, float centreWeight, float bendFrac, int nitroBendCans) {
        this.baseSpeed = baseSpeed;
        this.maxYaw = maxYaw;
        this.assist = assist;
        this.earnedBoost = earnedBoost;
        this.nitroPerLap = nitroPerLap;
        this.halfWidth = halfWidth;
        this.laps = laps;
        this.laneFrac = laneFrac;
        this.centreWeight = centreWeight;
        this.bendFrac = bendFrac;
        this.nitroBendCans = nitroBendCans;
    }

    /**
     * The level for a level-select number.
     * @param level 1 (Easy), 2 (Medium) or 3 (Hard); other numbers are clamped
     * @return the level
     */
    public static RaceLevel fromNumber(int level) {
        return values()[Math.max(1, Math.min(3, level)) - 1];
    }

    /**
     * The assist to use.
     * @param twoPlayers true in a two-player race
     * @return the level's assist, plus 0.15 for two players
     */
    public float assistFor(boolean twoPlayers) {
        return assist + (twoPlayers ? TWO_PLAYER_ASSIST : 0f);
    }
}
