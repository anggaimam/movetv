package com.wiggleplay.app.games.race;

/**
 * What the truck and the race rules need from a track: the centre line (sample, project, lap length), the road width and
 * the coin layout. Implemented by the spline track ({@link TrackSpline}).
 * Plain Java.
 */
public interface TrackPath {

    /**
     * Length of one lap along the centre line.
     * @return world units
     */
    float lapLength();

    /**
     * Half the width of the drivable road.
     * @return world units
     */
    float roadHalfWidth();

    /**
     * A point on the centre line.
     * @param s distance along the lap from the finish line; any value, it wraps
     * @param outXZYaw receives x, z and the yaw (at least 3 floats)
     */
    void sample(float s, float[] outXZYaw);

    /**
     * Finds where a point is relative to the centre line.
     * @param x world x
     * @param z world z
     * @param hint the sample index returned last time, or a negative number to search all
     * @param out receives distance along the lap, signed offset (positive = right) and road direction
     * @return the index to pass as the next hint
     */
    int project(float x, float z, int hint, TrackPoint out);

    /**
     * Coin, star, nitro-can and magnet positions for one lap.
     * @param seed layout seed (the same seed gives the same slots)
     * @param nitroCans nitro cans per lap
     * @param magnets 0 or 1 magnet per lap
     * @return the slots, sorted by distance along the lap
     */
    Slots coinSlots(long seed, int nitroCans, int magnets);
}
