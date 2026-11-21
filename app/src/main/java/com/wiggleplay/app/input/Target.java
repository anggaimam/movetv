package com.wiggleplay.app.input;

/** A rectangular dwell target in stage pixels (x,y = top-left). Plain Java. */
public final class Target {
    public String id;
    public float x, y, w, h;
    /** Hold time in ms; 0 means "use the DwellButtons default". Home/Exit targets use 1500. */
    public int holdMs;

    public Target() { }

    public Target(String id, float x, float y, float w, float h) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public Target(String id, float x, float y, float w, float h, int holdMs) {
        this(id, x, y, w, h);
        this.holdMs = holdMs;
    }

    /** @return horizontal centre */
    public float cx() { return x + w * 0.5f; }

    /** @return vertical centre */
    public float cy() { return y + h * 0.5f; }
}
