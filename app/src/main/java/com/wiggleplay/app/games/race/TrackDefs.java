package com.wiggleplay.app.games.race;

/**
 * The four track shapes of Turbo Trucks (T-28). Each is used on every level and can be driven both ways.
 * All start at control point 0 heading west (-X). The numbers were checked with {@code architect-T28-track-check.py}.
 */
public final class TrackDefs {

    private TrackDefs() { }

    private static final TrackDef DESERT = new TrackDef("desert", "Desert Dunes", "desert", new float[] {
            0, 0, -45, 0, -88, 8, -105, 38, -90, 66, -55, 72, -20, 60, 15, 68, 52, 70, 72, 42, 55, 10},
            new float[0],
            // T-31: ramps (E: Easy and up), potholes (M: Medium and up) and ice or water patches (H: Hard only); data, not random
            new RoadObject[] {
            new RoadObject(RoadObject.RAMP, 35f, 0, RaceLevel.EASY),
            new RoadObject(RoadObject.POTHOLE, 95f, 0, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 134f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 177f, 1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 216f, -1, RaceLevel.HARD),
            new RoadObject(RoadObject.RAMP, 272f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 322f, 0, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 350f, -1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.RAMP, 394f, 0, RaceLevel.EASY)},
            new RoadObject[] {
            new RoadObject(RoadObject.POTHOLE, 30f, 0, RaceLevel.HARD),
            new RoadObject(RoadObject.ICE, 60f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 104f, 1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.RAMP, 148f, 0, RaceLevel.EASY),
            new RoadObject(RoadObject.POTHOLE, 226f, -1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 283f, -1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 337f, 0, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.RAMP, 394f, 0, RaceLevel.EASY)});

    private static final TrackDef CITY = new TrackDef("city", "City Lights", "city", new float[] {
            0, 0, -50, 0, -70, 4, -80, 22, -80, 55, -73, 74, -53, 83, -36, 76, -24, 58, -8, 50, 10, 58,
            21, 75, 38, 84, 58, 78, 68, 58, 68, 22, 58, 5, 40, 0},
            new float[0],
            new RoadObject[] {
            new RoadObject(RoadObject.RAMP, 35f, 0, RaceLevel.EASY),
            new RoadObject(RoadObject.POTHOLE, 91f, 0, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 126f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 156f, 1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.POTHOLE, 273f, -1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 318f, -1, RaceLevel.HARD),
            new RoadObject(RoadObject.RAMP, 353f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.RAMP, 405f, 0, RaceLevel.EASY)},
            new RoadObject[] {
            new RoadObject(RoadObject.POTHOLE, 30f, 0, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.RAMP, 77.5f, 0, RaceLevel.EASY),
            new RoadObject(RoadObject.POTHOLE, 125f, 0, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 170f, -1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 312f, -1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 358f, 1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.RAMP, 405f, 0, RaceLevel.EASY)});

    private static final TrackDef ROCKIES = new TrackDef("rockies", "Rocky Lakes", "rockies", new float[] {
            0, 0, -40, 0, -72, 10, -88, 38, -80, 66, -55, 80, -28, 78, -8, 92, 15, 104, 42, 100, 55, 78,
            50, 52, 59, 28, 51, 6, 25, 0},
            // T-30b: big lake and small lake in the infield, one beside the start straight, one beyond the S-bend at the top
            new float[] {24, 64, 26, 15, 90, -30, 38, 20, 12, 45, -20, -24, 28, 12, 0, -44, 108, 26, 15, 0},
            new RoadObject[] {
            new RoadObject(RoadObject.RAMP, 35f, 0, RaceLevel.EASY),
            new RoadObject(RoadObject.POTHOLE, 93f, 1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 130f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 176f, 0, RaceLevel.HARD),
            new RoadObject(RoadObject.RAMP, 214f, 0, RaceLevel.EASY),
            new RoadObject(RoadObject.POTHOLE, 312f, -1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.POTHOLE, 357f, 0, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 403f, -1, RaceLevel.HARD)},
            new RoadObject[] {
            new RoadObject(RoadObject.POTHOLE, 30f, -1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.POTHOLE, 87f, 0, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 135f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.RAMP, 198f, 0, RaceLevel.EASY),
            new RoadObject(RoadObject.POTHOLE, 266f, 0, RaceLevel.HARD),
            new RoadObject(RoadObject.ICE, 298f, -1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 342f, 1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.RAMP, 386f, 0, RaceLevel.EASY)});

    private static final TrackDef SPACE = new TrackDef("space", "Star Road", "space", new float[] {
            0, 0, -42, 0, -78, 12, -90, 42, -70, 68, -38, 62, -8, 48, 22, 58, 52, 74, 82, 56, 80, 20, 45, 0},
            new float[0],
            new RoadObject[] {
            new RoadObject(RoadObject.RAMP, 35f, 0, RaceLevel.EASY),
            new RoadObject(RoadObject.POTHOLE, 102f, -1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.POTHOLE, 132f, 0, RaceLevel.HARD),
            new RoadObject(RoadObject.RAMP, 167f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.ICE, 223f, -1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 264f, 1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.ICE, 300f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 344f, 0, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.RAMP, 400f, 0, RaceLevel.EASY)},
            new RoadObject[] {
            new RoadObject(RoadObject.RAMP, 35f, 0, RaceLevel.EASY),
            new RoadObject(RoadObject.POTHOLE, 95f, 1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.POTHOLE, 133f, -1, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.POTHOLE, 172f, 0, RaceLevel.HARD),
            new RoadObject(RoadObject.ICE, 210f, -1, RaceLevel.HARD),
            new RoadObject(RoadObject.RAMP, 250f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.ICE, 302f, 1, RaceLevel.HARD),
            new RoadObject(RoadObject.POTHOLE, 351f, 0, RaceLevel.MEDIUM),
            new RoadObject(RoadObject.RAMP, 400f, 0, RaceLevel.EASY)});

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
