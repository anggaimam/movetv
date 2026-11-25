package com.wiggleplay.app.games.race;

/**
 * The road objects of one race (T-31): the ramps, potholes and ice or water patches of the track and direction that are active for the
 * level, sorted by s, with their lateral positions. Immutable; the lookups do not allocate. Plain Java.
 *
 * <p>Lateral position = lane x {@link RaceLevel#laneFrac} x half-width (the coin lanes), positive to the right as seen from behind.
 * For a ramp {@link #s} is the lip and the ramp runs from {@code s - RAMP_LENGTH} to {@code s}; for a pothole group and a patch it is
 * where it starts.
 */
public final class RoadObjects {
    /** The kind constants of {@link RoadObject}, for convenience. */
    public static final int RAMP = RoadObject.RAMP, POTHOLE = RoadObject.POTHOLE, ICE = RoadObject.ICE;
    /** Mask bit of a kind, for {@link #of}. */
    public static final int RAMPS_ONLY = 1 << RAMP, ALL = (1 << RAMP) | (1 << POTHOLE) | (1 << ICE);

    /** Ramp: length, width, lip height; the truck is on it within {@link #RAMP_HIT} of its lateral position. */
    public static final float RAMP_LENGTH = 5f, RAMP_WIDTH = 4f, RAMP_HEIGHT = 0.8f, RAMP_HIT = 2.25f;
    /** Patch: length and width. */
    public static final float PATCH_LENGTH = 8f, PATCH_WIDTH = 4f;
    /** Pothole: radius, and the gap along s of the second one in a group of two. */
    public static final float POTHOLE_RADIUS = 0.9f, POTHOLE_GAP = 1.5f;
    /** Warning sign distance before the start of an object. */
    public static final float SIGN_BEFORE = 20f;

    /** No objects (a track built from a bare half-width, or the reference run of the tests). */
    public static final RoadObjects NONE = new RoadObjects(new int[0], new float[0], new float[0], new int[0], new float[0]);

    private final int[] kind;
    private final float[] s, lateral;
    private final int[] lane;
    private final float[] extra;   // pothole: number in the group (1 or 2) as a float; unused otherwise

    private RoadObjects(int[] kind, float[] s, float[] lateral, int[] lane, float[] extra) {
        this.kind = kind;
        this.s = s;
        this.lateral = lateral;
        this.lane = lane;
        this.extra = extra;
    }

    /**
     * The objects of a track and direction for a level.
     * @param def the track
     * @param reversed true for the reversed list
     * @param level the level: an object appears when the level is its first level or a harder one
     * @param halfWidth half the road width of the level
     * @param kinds a mask of the kinds to include ({@link #RAMPS_ONLY} or {@link #ALL})
     * @return the objects sorted by s
     */
    public static RoadObjects of(TrackDef def, boolean reversed, RaceLevel level, float halfWidth, int kinds) {
        RoadObject[] src = reversed ? def.objectsReversed : def.objects;
        int n = 0;
        for (RoadObject o : src) if (active(o, level, kinds)) n++;
        int[] k = new int[n], ln = new int[n];
        float[] ss = new float[n], lat = new float[n], ex = new float[n];
        int i = 0;
        for (RoadObject o : src) {
            if (!active(o, level, kinds)) continue;
            k[i] = o.kind;
            ss[i] = o.s;
            ln[i] = o.lane;
            lat[i] = o.lane * level.laneFrac * halfWidth;
            ex[i] = o.kind == POTHOLE ? 1 + (((int) o.s * 7 + 3) % 2 == 0 ? 1 : 0) : 0;   // seeded by s: 1 or 2 potholes
            i++;
        }
        // sort by s (the lists are written sorted; this keeps a hand-edited list safe)
        for (int a = 1; a < n; a++) {
            for (int b = a; b > 0 && ss[b] < ss[b - 1]; b--) {
                int tk = k[b]; k[b] = k[b - 1]; k[b - 1] = tk;
                int tl = ln[b]; ln[b] = ln[b - 1]; ln[b - 1] = tl;
                float t1 = ss[b]; ss[b] = ss[b - 1]; ss[b - 1] = t1;
                float t2 = lat[b]; lat[b] = lat[b - 1]; lat[b - 1] = t2;
                float t3 = ex[b]; ex[b] = ex[b - 1]; ex[b - 1] = t3;
            }
        }
        return new RoadObjects(k, ss, lat, ln, ex);
    }

    private static boolean active(RoadObject o, RaceLevel level, int kinds) {
        return (kinds & (1 << o.kind)) != 0 && level.ordinal() >= o.first.ordinal();
    }

    /** @return number of objects */
    public int count() { return kind.length; }

    /**
     * @param i object index
     * @return {@link #RAMP}, {@link #POTHOLE} or {@link #ICE}
     */
    public int kind(int i) { return kind[i]; }

    /**
     * @param i object index
     * @return the data's s: the lip of a ramp, the start of a pothole group or a patch
     */
    public float s(int i) { return s[i]; }

    /**
     * @param i object index
     * @return where the object starts along the lap (a ramp: the foot, 5 m before the lip)
     */
    public float start(int i) { return kind[i] == RAMP ? s[i] - RAMP_LENGTH : s[i]; }

    /**
     * @param i object index
     * @return where the object ends along the lap (a pothole group: past the last pothole)
     */
    public float end(int i) {
        switch (kind[i]) {
            case RAMP: return s[i];
            case POTHOLE: return s[i] + (extra[i] > 1.5f ? POTHOLE_GAP : 0f);
            default: return s[i] + PATCH_LENGTH;
        }
    }

    /**
     * @param i object index
     * @return lateral position of the object's centre (positive right)
     */
    public float lateral(int i) { return lateral[i]; }

    /**
     * @param i object index
     * @return -1, 0 or +1
     */
    public int lane(int i) { return lane[i]; }

    /**
     * @param i a pothole group
     * @return 1 or 2 potholes
     */
    public int potholes(int i) { return (int) extra[i]; }

    /**
     * The ramp under a truck.
     * @param sAlong the truck's distance along the lap
     * @param offset the truck's lateral offset
     * @return the ramp's index when the truck is on it (s from the foot to the lip, lateral within {@link #RAMP_HIT}), else -1
     */
    public int rampAt(float sAlong, float offset) {
        for (int i = 0; i < kind.length; i++) {
            if (kind[i] != RAMP) continue;
            if (sAlong >= s[i] - RAMP_LENGTH && sAlong <= s[i] && Math.abs(offset - lateral[i]) <= RAMP_HIT) return i;
        }
        return -1;
    }

    /**
     * Whether a place on the lap is near an object (coin runs there are dropped).
     * @param from start of the span
     * @param to end of the span
     * @param margin metres added on both ends of every object
     * @return true when the span overlaps an object's extent plus the margin
     */
    public boolean overlaps(float from, float to, float margin) {
        for (int i = 0; i < kind.length; i++) {
            if (to >= start(i) - margin && from <= end(i) + margin) return true;
        }
        return false;
    }
}
