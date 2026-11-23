package com.wiggleplay.app.games.race;

/**
 * The three race levels: how fast the truck goes, how sharply it can turn and how strongly the road pulls it
 * back to the centre. Plain Java.
 */
public enum RaceLevel {
    /** 22 tiles, 4 corners. */
    EASY(11f, 1.6f, 0.5f),
    /** 28 tiles, 6 corners. */
    MEDIUM(13f, 1.9f, 0.25f),
    /** 40 tiles, 12 corners. */
    HARD(15f, 2.2f, 0.1f);

    /** Extra road assist in two-player races. */
    public static final float TWO_PLAYER_ASSIST = 0.15f;

    /** Normal speed in units per second. */
    public final float baseSpeed;
    /** Fastest turn in radians per second. */
    public final float maxYaw;
    /** How strongly the road pulls toward the centre line. */
    public final float assist;

    RaceLevel(float baseSpeed, float maxYaw, float assist) {
        this.baseSpeed = baseSpeed;
        this.maxYaw = maxYaw;
        this.assist = assist;
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
