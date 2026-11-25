package com.wiggleplay.app.games.race;

/**
 * One object on the road of a track (T-31), placed by data: a kind, where along the lap, which lane and the first level it appears
 * on. Immutable. Plain Java.
 */
public final class RoadObject {
    /** Kinds: a jump ramp, a pothole (group), an ice or water patch. */
    public static final int RAMP = 0, POTHOLE = 1, ICE = 2;

    /** {@link #RAMP}, {@link #POTHOLE} or {@link #ICE}. */
    public final int kind;
    /** Distance along the lap in metres: the lip of a ramp, the start of a pothole or a patch. */
    public final float s;
    /** -1 (left), 0 (centre) or +1 (right); the lateral position is lane x laneFrac x half-width. */
    public final int lane;
    /** The first level the object appears on (it stays on every harder level). */
    public final RaceLevel first;

    public RoadObject(int kind, float s, int lane, RaceLevel first) {
        this.kind = kind;
        this.s = s;
        this.lane = lane;
        this.first = first;
    }
}
