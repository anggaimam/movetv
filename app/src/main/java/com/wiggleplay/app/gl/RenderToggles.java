package com.wiggleplay.app.gl;

/**
 * The five debug switches of the speed meter (T-30 Gate 0): in the race, with the speed meter showing and test mode on, keys
 * 1 to 5 switch off terrain, props with their shadows, the detail texture, the sky dome and the HUD, one by one, so a photo of
 * the overlay shows which part costs the frames. They are all on when a race loads. Plain Java; the values are volatile (the
 * main thread flips them, the GL thread reads them).
 *
 * <p>Terrain and props can only be switched off when the world was built "split" (the static meshes merged per group instead of
 * all together); the race scene does that whenever the speed meter is showing when the world is built.
 */
public final class RenderToggles {

    /** Key 1: the terrain tiles (and the old ground patches). */
    public static final int TERRAIN = 0;
    /** Key 2: props and their shadows. */
    public static final int PROPS = 1;
    /** Key 3: the detail texture. */
    public static final int DETAIL = 2;
    /** Key 4: the sky dome. */
    public static final int SKY = 3;
    /** Key 5: the HUD (the 2D layer drawn by the scene). */
    public static final int HUD = 4;
    /** The number of switches. */
    public static final int COUNT = 5;

    /** Chunk group: road, kerbs, rails and anything else. */
    public static final int GROUP_BASE = 0;
    /** Chunk group: terrain tiles and ground patches. */
    public static final int GROUP_TERRAIN = 1;
    /** Chunk group: props and prop shadows. */
    public static final int GROUP_PROPS = 2;

    private static final String[] NAMES = {"terrain", "props+shadows", "detail", "sky", "hud"};

    private volatile boolean terrainOff, propsOff, detailOff, skyOff, hudOff;
    private volatile boolean split;
    private volatile int chunkCount;

    /**
     * Which chunk group a static mesh belongs to, from the name it is registered under.
     * @param meshName for example {@code terrain_3}, {@code prop_2_1}, {@code shd_17}, {@code trk_road_0}
     * @return {@link #GROUP_TERRAIN}, {@link #GROUP_PROPS} or {@link #GROUP_BASE}
     */
    public static int groupOf(String meshName) {
        if (meshName.startsWith("terrain_") || meshName.startsWith("patch_")) return GROUP_TERRAIN;
        if (meshName.startsWith("prop_") || meshName.startsWith("shd_")) return GROUP_PROPS;
        return GROUP_BASE;
    }

    /**
     * Flips one switch.
     * @param which {@link #TERRAIN} to {@link #HUD}; anything else is ignored
     */
    public void flip(int which) {
        switch (which) {
            case TERRAIN: terrainOff = !terrainOff; break;
            case PROPS: propsOff = !propsOff; break;
            case DETAIL: detailOff = !detailOff; break;
            case SKY: skyOff = !skyOff; break;
            case HUD: hudOff = !hudOff; break;
            default: break;
        }
    }

    /**
     * Whether one switch is off.
     * @param which {@link #TERRAIN} to {@link #HUD}
     * @return true when that part is switched off
     */
    public boolean isOff(int which) {
        switch (which) {
            case TERRAIN: return terrainOff;
            case PROPS: return propsOff;
            case DETAIL: return detailOff;
            case SKY: return skyOff;
            case HUD: return hudOff;
            default: return false;
        }
    }

    /**
     * Whether a chunk group should be skipped.
     * @param group {@link #GROUP_BASE}, {@link #GROUP_TERRAIN} or {@link #GROUP_PROPS}
     * @return true when its switch is off
     */
    public boolean groupOff(int group) {
        return group == GROUP_TERRAIN ? terrainOff : group == GROUP_PROPS && propsOff;
    }

    /** Puts everything back on (a race is loading). */
    public void reset() {
        terrainOff = false;
        propsOff = false;
        detailOff = false;
        skyOff = false;
        hudOff = false;
    }

    /**
     * Records how the static world was built, for the overlay.
     * @param split true when the chunks are grouped (terrain and props can be switched off)
     * @param chunks the number of chunks
     */
    public void setWorld(boolean split, int chunks) {
        this.split = split;
        this.chunkCount = chunks;
    }

    /**
     * One overlay line: the state of the five switches and how the world was built.
     * @param sb where to append (the line is appended, not cleared first)
     */
    public void describe(StringBuilder sb) {
        sb.append("keys 1-5:");
        for (int i = 0; i < COUNT; i++) {
            sb.append(' ').append(i + 1).append(' ').append(NAMES[i]).append(isOff(i) ? " OFF" : " on");
            if (i < COUNT - 1) sb.append(',');
        }
        sb.append("  world ").append(split ? "split" : "merged (1 and 2 need the meter on before the race loads)")
                .append(' ').append(chunkCount).append(" chunks");
    }
}
