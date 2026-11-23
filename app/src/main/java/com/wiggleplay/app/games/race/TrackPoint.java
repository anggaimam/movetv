package com.wiggleplay.app.games.race;

/**
 * Where a point on the ground is relative to the track's centre line. One instance is reused for every
 * {@link TrackLayout#project} call (no allocation).
 */
public final class TrackPoint {
    /** Distance along the centre line from the start line, 0 to the lap length (wraps). */
    public float s;
    /**
     * Signed distance from the centre line; positive is to the right as seen from a chase camera behind
     * the truck, negative to the left.
     */
    public float offset;
    /** Direction of the road at that place, in the truck's yaw convention (yaw 0 = +Z, turning right lowers it). */
    public float tangentYaw;
}
