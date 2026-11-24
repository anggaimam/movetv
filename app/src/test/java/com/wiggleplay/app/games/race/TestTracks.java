package com.wiggleplay.app.games.race;

/** The track the single-track unit tests drive on: Desert Dunes, forward, at the width of the level (T-28 Part D). */
final class TestTracks {
    private TestTracks() { }

    /**
     * @param level 0 = Easy, 1 = Medium, 2 = Hard
     * @return Desert Dunes at that level's road width
     */
    static TrackSpline forLevel(int level) {
        return TrackSpline.build(TrackDefs.ALL[0], false, RaceLevel.fromNumber(level + 1));
    }
}
