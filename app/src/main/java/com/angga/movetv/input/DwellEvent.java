package com.angga.movetv.input;

/** Something a dwell target reports: hover started, or selected. Plain Java. */
public final class DwellEvent {
    public static final int HOVER = 0, SELECT = 1;

    public final int type;
    public final String targetId;
    public final int player;

    public DwellEvent(int type, String targetId, int player) {
        this.type = type;
        this.targetId = targetId;
        this.player = player;
    }

    @Override
    public String toString() {
        return (type == HOVER ? "HOVER " : "SELECT ") + targetId + " p" + player;
    }
}
