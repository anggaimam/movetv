package com.wiggleplay.app.pose;

/** One body point. x and y are stage pixels (1920x1080, mirrored like a mirror); s is confidence 0..1. */
public final class Keypoint {
    public float x, y, s;
}
