package com.wiggleplay.app.games.race;

/**
 * The four track shapes of Turbo Trucks (T-28). Each is used on every level and can be driven both ways.
 * All start at control point 0 heading west (-X). The numbers were checked with {@code architect-T28-track-check.py}.
 */
public final class TrackDefs {

    private TrackDefs() { }

    private static final TrackDef DESERT = new TrackDef("desert", "Desert Dunes", "desert", new float[] {
            0, 0, -45, 0, -88, 8, -105, 38, -90, 66, -55, 72, -20, 60, 15, 68, 52, 70, 72, 42, 55, 10});

    private static final TrackDef CITY = new TrackDef("city", "City Lights", "city", new float[] {
            0, 0, -50, 0, -70, 4, -80, 22, -80, 55, -73, 74, -53, 83, -36, 76, -24, 58, -8, 50, 10, 58,
            21, 75, 38, 84, 58, 78, 68, 58, 68, 22, 58, 5, 40, 0});

    private static final TrackDef ROCKIES = new TrackDef("rockies", "Rocky Lakes", "rockies", new float[] {
            0, 0, -40, 0, -72, 10, -88, 38, -80, 66, -55, 80, -28, 78, -8, 92, 15, 104, 42, 100, 55, 78,
            50, 52, 59, 28, 51, 6, 25, 0},
            // T-30b: big lake and small lake in the infield, one beside the start straight, one beyond the S-bend at the top
            new float[] {24, 64, 26, 15, 90, -30, 38, 20, 12, 45, -20, -24, 28, 12, 0, -44, 108, 26, 15, 0});

    private static final TrackDef SPACE = new TrackDef("space", "Star Road", "space", new float[] {
            0, 0, -42, 0, -78, 12, -90, 42, -70, 68, -38, 62, -8, 48, 22, 58, 52, 74, 82, 56, 80, 20, 45, 0});

    /** All tracks, in menu order: desert, city, rockies, space. */
    public static final TrackDef[] ALL = {DESERT, CITY, ROCKIES, SPACE};

    /**
     * Finds a track by id.
     * @param id a track id, or null
     * @return the track; the desert track for an unknown id or null
     */
    public static TrackDef byId(String id) {
        if (id != null) {
            for (TrackDef d : ALL) {
                if (d.id.equals(id)) return d;
            }
        }
        return DESERT;
    }
}
