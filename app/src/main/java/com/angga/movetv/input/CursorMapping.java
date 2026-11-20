package com.angga.movetv.input;

import com.angga.movetv.pose.PlayerPose;

/**
 * Decides where on the screen a raised wrist points. {@link HandCursor} chooses the hand, applies the
 * raised-wrist rule and smooths the result; a mapping only turns one wrist into a stage position. Plain Java.
 */
public interface CursorMapping {

    /**
     * Tells the mapping when the pose was measured, once before each {@link #map} call. Mappings that do not
     * look ahead can ignore it.
     * @param poseTimeMs when the newest pose was measured (same clock as nowMs)
     * @param nowMs the current time
     */
    default void timing(long poseTimeMs, long nowMs) { }

    /** Forgets anything remembered about the hand's movement (the hand was lowered or switched). */
    default void reset() { }

    /**
     * Maps one wrist to the stage.
     * @param p the player's pose
     * @param hand 0 for the left hand, 1 for the right hand
     * @param out receives the target x and y in stage px (out[0], out[1])
     * @return false if the pose cannot be mapped (the cursor then freezes where it is)
     */
    boolean map(PlayerPose p, int hand, float[] out);
}
