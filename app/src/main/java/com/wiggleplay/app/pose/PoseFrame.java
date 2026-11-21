package com.wiggleplay.app.pose;

/** One body-tracking result for up to two players, in stage coordinates. Plain Java, no android imports. */
public final class PoseFrame {
    public static final float STAGE_W = 1920f, STAGE_H = 1080f;
    /** SystemClock.uptimeMillis() when measured (passed in, never read inside this class). */
    public long timeMs;
    /** 1 or 2 players; in 2 players, players[0] is the LEFT half of the screen. */
    public int mode;
    public final PlayerPose[] players = { new PlayerPose(0), new PlayerPose(1) };
    public float aiFps;
    /** "camera" or "test". */
    public String source;

    /**
     * Deep-copies this frame into {@code dst}.
     * @param dst target frame (reused, no allocation); a new frame is created only if null
     * @return the frame that was written to
     */
    public PoseFrame copyInto(PoseFrame dst) {
        if (dst == null) dst = new PoseFrame();
        dst.timeMs = timeMs;
        dst.mode = mode;
        dst.aiFps = aiFps;
        dst.source = source;
        for (int i = 0; i < players.length; i++) {
            PlayerPose a = players[i], b = dst.players[i];
            b.present = a.present;
            for (int k = 0; k < Kp.COUNT; k++) {
                Keypoint ka = a.kps[k], kb = b.kps[k];
                kb.x = ka.x;
                kb.y = ka.y;
                kb.s = ka.s;
            }
        }
        return dst;
    }

    /**
     * Whether a keypoint is confident enough.
     * @return true when {@code k.s >= min}
     */
    public static boolean ok(Keypoint k, float min) {
        return k.s >= min;
    }

    /**
     * Distance between two keypoints in stage pixels.
     * @return the straight-line distance
     */
    public static float dist(Keypoint a, Keypoint b) {
        float dx = a.x - b.x, dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Distance between the shoulders.
     * @return stage pixels, or 0 if either shoulder has s &lt; 0.3
     */
    public static float shoulderWidth(PlayerPose p) {
        Keypoint l = p.kps[Kp.L_SHOULDER], r = p.kps[Kp.R_SHOULDER];
        if (l.s < 0.3f || r.s < 0.3f) return 0f;
        return dist(l, r);
    }
}
