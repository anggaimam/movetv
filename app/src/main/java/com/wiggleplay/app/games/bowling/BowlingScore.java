package com.wiggleplay.app.games.bowling;

/**
 * Turns, frames and points for one to four bowlers. Plain Java.
 *
 * <p>A game is {@link #FRAMES} frames, two rolls in each. A frame scores the pins knocked down in it; a spare (all
 * ten down with the second roll) adds {@link #SPARE_BONUS}, a strike (all ten with the first roll, which ends the
 * frame) adds {@link #STRIKE_BONUS}. Nothing is ever taken away. Players take turns frame by frame: everyone bowls
 * frame 1, then everyone bowls frame 2, and so on.
 */
public final class BowlingScore {

    /** Frames in a game, rolls in a frame, and the bonus points for a spare and for a strike. */
    public static final int FRAMES = 5, ROLLS = 2, SPARE_BONUS = 5, STRIKE_BONUS = 10;
    /** What happened with a roll. */
    public static final int NORMAL = 0, SPARE = 1, STRIKE = 2, GUTTER = 3;

    private final int players;
    private final int[][][] pins;         // [player][frame][roll] pins knocked down, -1 = not rolled
    private int player, frame, roll;
    private boolean finished;

    /**
     * Starts a game.
     * @param players 1..4
     */
    public BowlingScore(int players) {
        this.players = Math.max(1, Math.min(4, players));
        pins = new int[this.players][FRAMES][ROLLS];
        for (int[][] p : pins) for (int[] f : p) java.util.Arrays.fill(f, -1);
    }

    /** @return the number of bowlers */
    public int players() { return players; }

    /** @return whose turn it is, 0-based */
    public int player() { return player; }

    /** @return the frame being bowled, 0-based (FRAMES when the game is over) */
    public int frame() { return finished ? FRAMES : frame; }

    /** @return the roll in the frame, 0 or 1 */
    public int roll() { return roll; }

    /** @return true when everyone has bowled all frames */
    public boolean finished() { return finished; }

    /**
     * How many pins are still up at the start of the current roll: 10 on the first roll, the rest on the second.
     * @return 0..10
     */
    public int pinsUpNow() {
        if (roll == 0) return 10;
        return 10 - Math.max(0, pins[player][frame][0]);
    }

    /**
     * Writes down a roll and moves the turn on.
     * @param down pins knocked down by this roll (clamped to the pins that were up)
     * @return {@link #NORMAL}, {@link #SPARE}, {@link #STRIKE} or {@link #GUTTER} (nothing knocked down)
     */
    public int record(int down) {
        if (finished) return NORMAL;
        int up = pinsUpNow();
        int d = Math.max(0, Math.min(up, down));
        pins[player][frame][roll] = d;
        int result;
        boolean endFrame;
        if (roll == 0 && d == 10) {
            result = STRIKE;
            endFrame = true;
        } else if (roll == 1 && pins[player][frame][0] + d == 10) {
            result = SPARE;
            endFrame = true;
        } else {
            result = d == 0 ? GUTTER : NORMAL;
            endFrame = roll == ROLLS - 1;
        }
        if (endFrame) {
            roll = 0;
            player++;
            if (player >= players) {
                player = 0;
                frame++;
                if (frame >= FRAMES) finished = true;
            }
        } else {
            roll++;
        }
        return result;
    }

    /**
     * Pins knocked down by a bowler in a frame (both rolls).
     * @param p player
     * @param f frame
     * @return pins, or -1 if the frame has not been bowled yet
     */
    public int framePins(int p, int f) {
        int a = pins[p][f][0], b = pins[p][f][1];
        if (a < 0) return -1;
        return a + Math.max(0, b);
    }

    /**
     * Points of one frame: its pins plus a spare or strike bonus.
     * @param p player
     * @param f frame
     * @return points, or -1 if the frame has not been bowled
     */
    public int framePoints(int p, int f) {
        int a = pins[p][f][0], b = pins[p][f][1];
        if (a < 0) return -1;
        if (a == 10) return 10 + STRIKE_BONUS;
        if (b >= 0 && a + b == 10) return 10 + SPARE_BONUS;
        return a + Math.max(0, b);
    }

    /**
     * Whether the frame ended in a strike or a spare.
     * @param p player
     * @param f frame
     * @return {@link #STRIKE}, {@link #SPARE} or {@link #NORMAL}
     */
    public int frameKind(int p, int f) {
        int a = pins[p][f][0], b = pins[p][f][1];
        if (a == 10) return STRIKE;
        if (a >= 0 && b >= 0 && a + b == 10) return SPARE;
        return NORMAL;
    }

    /**
     * Total points of a bowler so far.
     * @param p player
     * @return points
     */
    public int total(int p) {
        int t = 0;
        for (int f = 0; f < FRAMES; f++) {
            int fp = framePoints(p, f);
            if (fp > 0) t += fp;
        }
        return t;
    }

    /**
     * Pins a bowler has knocked down in all frames (what the stars are counted from).
     * @param p player
     * @return pins
     */
    public int totalPins(int p) {
        int t = 0;
        for (int f = 0; f < FRAMES; f++) {
            int fp = framePins(p, f);
            if (fp > 0) t += fp;
        }
        return t;
    }

    /**
     * The bowler with the most points (the first one in a tie).
     * @return player index
     */
    public int leader() {
        int best = 0;
        for (int p = 1; p < players; p++) if (total(p) > total(best)) best = p;
        return best;
    }

    /**
     * Stars for a number of pins: 1 always, 2 from 32, 3 from 42 on Easy; the limits are 10% higher on Medium and 20%
     * higher on Hard (rounded to the nearest pin). Five frames hold 50 pins at most.
     * @param pins pins knocked down in the whole game
     * @param level 1..3
     * @return 1..3
     */
    public static int stars(int pins, int level) {
        float k = level == 3 ? 1.2f : level == 2 ? 1.1f : 1f;
        if (pins >= Math.round(42 * k)) return 3;
        if (pins >= Math.round(32 * k)) return 2;
        return 1;
    }
}
