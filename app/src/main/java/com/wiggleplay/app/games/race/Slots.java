package com.wiggleplay.app.games.race;

/**
 * Coin, star, nitro-can and magnet positions along one lap, as (s, offset), sorted by s. Made by
 * {@link TrackPath#coinSlots}. Moved out of the old tile track in T-28 Part B so that it could be deleted in Part D.
 */
public final class Slots {
    /** Distance along the centre line of each coin. */
    public final float[] s;
    /** Offset from the centre line of each coin (positive = right). */
    public final float[] offset;
    /** True for the coins on a corner (the others are on straights). */
    public final boolean[] corner;
    /** True where the "coin" is a nitro can instead (T-26; it is worth no points). */
    public final boolean[] nitro;
    /** True where the "coin" is a magnet instead (it is worth 1 point like the coin it replaces). */
    public final boolean[] magnet;
    /** Number of coins. */
    public final int count;
    /** Distance along the centre line of the golden star (one per lap, at a corner apex). */
    public final float starS;
    /** Offset of the golden star. */
    public final float starOffset;

    Slots(float[] s, float[] offset, boolean[] corner, boolean[] nitro, boolean[] magnet, int count, float starS, float starOffset) {
        this.magnet = magnet;
        this.s = s;
        this.offset = offset;
        this.corner = corner;
        this.nitro = nitro;
        this.count = count;
        this.starS = starS;
        this.starOffset = starOffset;
    }
}
