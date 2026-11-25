package com.wiggleplay.app.games.race;

/**
 * The data of one track shape (T-28): a name, the theme it belongs to and the control points the road passes through.
 * Immutable. Later tickets add fields (lakes, road objects). Plain Java.
 */
public final class TrackDef {
    /** Short id, also used in saved settings: {@code desert}, {@code city}, {@code rockies}, {@code space}. */
    public final String id;
    /** Name shown to the player, for example {@code Desert Dunes}. */
    public final String name;
    /** The world theme this track is drawn in. */
    public final String themeId;
    /**
     * Control points as x0, z0, x1, z1, and so on, in driving order. Point 0 is the start line; the road starts there
     * heading toward point 1. Do not modify.
     */
    public final float[] control;
    /**
     * Lakes (T-30b) as cx, cz, rx, rz, rotation in degrees, five numbers per lake; empty for none. The same for both driving
     * directions. Do not modify.
     */
    public final float[] lakes;
    /** Road objects (T-31) for the forward direction and for the reversed one, sorted by s. Do not modify. */
    public final RoadObject[] objects, objectsReversed;

    /**
     * Creates a track definition.
     * @param id short id
     * @param name display name
     * @param themeId theme id
     * @param control control points (x, z pairs); copied
     */
    public TrackDef(String id, String name, String themeId, float[] control) {
        this(id, name, themeId, control, new float[0]);
    }

    /**
     * Creates a track definition with lakes.
     * @param lakes cx, cz, rx, rz, rotation (degrees) per lake; copied
     */
    public TrackDef(String id, String name, String themeId, float[] control, float[] lakes) {
        this(id, name, themeId, control, lakes, new RoadObject[0], new RoadObject[0]);
    }

    /**
     * Creates a track definition with lakes and road objects.
     * @param objects the objects when driven forward (copied)
     * @param objectsReversed the objects when driven the other way, with s measured along the reversed track (copied)
     */
    public TrackDef(String id, String name, String themeId, float[] control, float[] lakes, RoadObject[] objects,
                    RoadObject[] objectsReversed) {
        this.objects = objects.clone();
        this.objectsReversed = objectsReversed.clone();
        this.lakes = lakes.clone();
        this.id = id;
        this.name = name;
        this.themeId = themeId;
        this.control = control.clone();
    }

    /**
     * Number of control points.
     * @return the count
     */
    public int pointCount() {
        return control.length / 2;
    }
}
