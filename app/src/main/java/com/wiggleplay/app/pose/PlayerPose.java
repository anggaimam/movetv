package com.wiggleplay.app.pose;

/** The 17 keypoints of one player. All entries of {@code kps} are non-null. */
public final class PlayerPose {
    public final int id;
    public boolean present;
    public final Keypoint[] kps = new Keypoint[Kp.COUNT];

    /**
     * Creates an empty pose.
     * @param id player index (0 or 1)
     */
    public PlayerPose(int id) {
        this.id = id;
        for (int i = 0; i < kps.length; i++) kps[i] = new Keypoint();
    }
}
