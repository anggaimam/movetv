package com.angga.movetv.games.common;

/**
 * The fair star rule shared by every game: everybody gets 1 star, 2 stars for scoring at least 40% of the points
 * that were offered to that player, 3 stars at 70% or more. "Offered" means the positive points of everything that
 * was launched into the player's area (golden items included, "don't touch" items excluded), so a long Hard round
 * and a short Easy round ask for the same share. Plain Java, tested on the JVM.
 */
public final class StarRules {

    /** Share of the offered points for 2 stars, in percent. */
    public static final int TWO_STAR_PERCENT = 40;
    /** Share of the offered points for 3 stars, in percent. */
    public static final int THREE_STAR_PERCENT = 70;

    private StarRules() { }

    /**
     * Stars for a round.
     * @param scored points the player scored
     * @param offered points that were offered to the player (0 or less means nothing was offered)
     * @return 1, 2 or 3 (never less than 1)
     */
    public static int stars(int scored, int offered) {
        if (offered <= 0 || scored <= 0) return 1;
        long s = (long) scored * 100L;
        if (s >= (long) offered * THREE_STAR_PERCENT) return 3;
        if (s >= (long) offered * TWO_STAR_PERCENT) return 2;
        return 1;
    }
}
