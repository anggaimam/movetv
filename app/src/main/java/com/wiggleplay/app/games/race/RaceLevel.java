package com.wiggleplay.app.games.race;

/**
 * The three race levels: how fast the truck goes, how sharply it can turn and how strongly the road pulls it
 * back to the centre. Plain Java.
 */
public enum RaceLevel {
    /** 22 tiles, 4 corners. The rocket meter starts full, fills by itself (0.2 per second) and with coins. */
    EASY(11f, 1.6f, 0.5f, false, 0),
    /** 28 tiles, 6 corners. T-26: the rocket is earned with 4 nitro cans per lap. */
    MEDIUM(13f, 1.9f, 0.25f, true, 4),
    /** 40 tiles, 12 corners. T-26: the rocket is earned with 5 nitro cans per lap. */
    HARD(15f, 2.2f, 0.1f, true, 5);

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

    /** How much one nitro can fills the rocket meter (two cans make a full rocket). */
    public static final float NITRO_FILL = 0.5f;

    RaceLevel(float baseSpeed, float maxYaw, float assist, boolean earnedBoost, int nitroPerLap) {
        this.baseSpeed = baseSpeed;
        this.maxYaw = maxYaw;
        this.assist = assist;
        this.earnedBoost = earnedBoost;
        this.nitroPerLap = nitroPerLap;
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
