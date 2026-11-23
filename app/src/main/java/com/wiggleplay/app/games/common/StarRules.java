package com.wiggleplay.app.games.common;

/**
 * The fair star rule shared by new games: 1 star always, 2 stars from 40% of what was offered to the player, 3 stars
 * from 70%. Plain Java. (Older games keep their own copy of the same thresholds.)
 */
public final class StarRules {

    private StarRules() { }

    /**
     * Stars for a round.
     * @param scored points the player got
     * @param offered points that were offered to this player (what passed by or was collected)
     * @return 1, 2 or 3; always at least 1, and 1 when nothing was offered
     */
    public static int stars(int scored, int offered) {
        if (offered <= 0) return 1;
        float f = scored / (float) offered;
        return f >= 0.70f ? 3 : (f >= 0.40f ? 2 : 1);
    }
}
