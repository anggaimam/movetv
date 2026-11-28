package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

import java.util.Random;

/**
 * Turns an ideal pose stream ({@link PoseGen.Fn}) into what the camera really delivers: pictures at 8 Hz (or 12 Hz),
 * each arriving 250 ms after it was measured, with +/-8 px of noise on every keypoint. The detectors are then run at
 * 60 fps with the newest arrived picture, exactly as the game does.
 */
final class LagSim {

    static final long T0 = 100000L;

    /** What the game loop does every display frame. */
    interface Sink {
        /**
         * @param p newest arrived picture (a reused object)
         * @param tMs the time that picture was measured (ms)
         * @param dt frame time in seconds
         * @param trueT the true time of this frame in seconds
         */
        void frame(PlayerPose p, long tMs, float dt, float trueT);
    }

    private LagSim() { }

    /**
     * Runs {@code fn} through the camera model.
     * @param fn the ideal pose
     * @param durS seconds to simulate
     * @param hz pictures per second
     * @param delayS camera delay in seconds
     * @param noisePx half-width of the uniform noise in px
     * @param seed random seed
     * @param sink the game loop
     */
    static void run(PoseGen.Fn fn, float durS, float hz, float delayS, float noisePx, long seed, Sink sink) {
        Random rnd = new Random(seed);
        PlayerPose shown = new PlayerPose(0);
        PlayerPose cap = new PlayerPose(0);
        float dt = 1f / 60f;
        float nextCapture = rnd.nextFloat() / hz;
        float[] arrive = new float[8];
        PlayerPose[] queue = new PlayerPose[8];
        long[] qMs = new long[8];
        for (int i = 0; i < queue.length; i++) queue[i] = new PlayerPose(0);
        int qHead = 0, qTail = 0;
        long shownMs = 0;
        boolean any = false;
        for (int f = 0; f * dt < durS; f++) {
            float t = f * dt;
            while (nextCapture <= t) {
                fn.at(nextCapture, cap);
                PlayerPose q = queue[qTail % 8];
                copy(cap, q);
                for (int k = 0; k < Kp.COUNT; k++) {
                    q.kps[k].x += (rnd.nextFloat() * 2f - 1f) * noisePx;
                    q.kps[k].y += (rnd.nextFloat() * 2f - 1f) * noisePx;
                }
                arrive[qTail % 8] = nextCapture + delayS;
                qMs[qTail % 8] = T0 + Math.round(nextCapture * 1000f);
                qTail++;
                nextCapture += (1f / hz) * (0.9f + 0.2f * rnd.nextFloat());
            }
            while (qHead < qTail && arrive[qHead % 8] <= t) {
                copy(queue[qHead % 8], shown);
                shownMs = qMs[qHead % 8];
                any = true;
                qHead++;
            }
            shown.present = any;
            sink.frame(shown, shownMs, dt, t);
        }
    }

    private static void copy(PlayerPose a, PlayerPose b) {
        b.present = a.present;
        for (int k = 0; k < Kp.COUNT; k++) {
            b.kps[k].x = a.kps[k].x;
            b.kps[k].y = a.kps[k].y;
            b.kps[k].s = a.kps[k].s;
        }
    }
}
