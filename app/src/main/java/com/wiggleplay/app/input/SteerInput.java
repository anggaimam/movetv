package com.wiggleplay.app.input;

import com.wiggleplay.app.pose.PlayerPose;

/**
 * A steering gesture for Turbo Trucks (T-23): airplane arms, body lean or an invisible steering wheel. Every mode
 * gives the same {@link #steer()} from -1 (left on the screen) to +1, so the truck, the race rules, the jump boost
 * and the crouch brake do not care which one is used. Plain Java, no allocation in {@link #update}.
 *
 * <p>Angles follow {@link AirplaneArms}: positive when the screen-left point is lower, and a positive angle steers
 * left. The state method is called {@code steerState()} (not {@code state()}) because {@link AirplaneArms} already has
 * a {@code state()} with its own three-way answer.
 */
public interface SteerInput {

    /** Whether the gesture is being made right now. */
    enum State { NONE, ACTIVE }

    /**
     * Feeds the newest pose. Call once per display frame.
     * @param p the player's pose (may be null)
     * @param timeMs when the pose was measured
     * @param dt seconds since the last call
     */
    void update(PlayerPose p, long timeMs, float dt);

    /** @return steering -1 (left on the screen) to +1 (right), smoothed */
    float steer();

    /** @return ACTIVE while the gesture is made, NONE otherwise (the steering then eases to straight) */
    State steerState();

    /** @return the tilt for the HUD icon in degrees (positive = the icon's left side lower), smoothed */
    float iconAngleDeg();

    /** @return the angle the camera sees this frame, unsmoothed, in degrees; NaN when nothing is seen */
    float rawAngleDeg();

    /** @return the mode's short name for the speed meter: "plane", "lean" or "wheel" */
    String modeName();

    /** Forgets everything: straight steering. */
    void reset();
}
