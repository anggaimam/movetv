package com.angga.movetv.games.common;

/**
 * Where everything on the end screen goes (1920x1080 stage), worked out so that no block touches another:
 * the title pill on top, then for each player a column (name, score, three star slots), the kids beside the
 * columns, and the tiles at the bottom. Every block keeps at least {@link #GAP} px of clear space from the next
 * one, including the room the stars need to bob and twinkle. Plain Java, so the unit test can check it.
 */
public final class EndLayout {

    /** Smallest clear space between two blocks, in px. */
    public static final float GAP = 40f;
    /** Star size and the space between star slots. */
    public static final float STAR = 120f, STAR_GAP = 24f;
    /** Text sizes of the name and the score. */
    public static final float NAME_SIZE = 36f, SCORE_SIZE = 96f;
    /** Top of the tiles. */
    public static final float TILES_TOP = 760f;
    /** Extra room around the star row for bobbing (5 px) and twinkling (6%). */
    static final float STAR_PAD = 12f;
    /** Rough width of one digit as a share of the text size (the rounded font is about 0.62). */
    static final float DIGIT_W = 0.66f;

    /** A rectangle in stage px. */
    public static final class Box {
        /** Left, top, right, bottom. */
        public float l, t, r, b;

        void set(float l, float t, float r, float b) {
            this.l = l;
            this.t = t;
            this.r = r;
            this.b = b;
        }

        /** @return the centre x */
        public float cx() { return (l + r) / 2f; }

        /** @return the centre y */
        public float cy() { return (t + b) / 2f; }

        /**
         * Whether two boxes come closer than a distance.
         * @param o the other box
         * @param gap the smallest clear distance wanted
         * @return true if they overlap or are closer than the gap
         */
        public boolean closerThan(Box o, float gap) {
            return l < o.r + gap && o.l < r + gap && t < o.b + gap && o.t < b + gap;
        }
    }

    /** The title pill. */
    public final Box title = new Box();
    /** Player name boxes (2 players only; empty for one). */
    public final Box[] name = {new Box(), new Box()};
    /** Score boxes. */
    public final Box[] score = {new Box(), new Box()};
    /** The three star slots of each player, including bobbing room. */
    public final Box[] stars = {new Box(), new Box()};
    /** The kid beside each column. */
    public final Box[] kid = {new Box(), new Box()};
    /** Centre x of each player's column. */
    public final float[] colX = new float[2];
    /** Where each kid stands (feet) and how tall it is. */
    public final float[] kidFootX = new float[2], kidFootY = new float[2], kidHeight = new float[2];
    /** The three tiles at the bottom. */
    public final Box[] tiles = {new Box(), new Box(), new Box()};
    /** The round Back button area at the top left (kept clear). */
    public final Box back = new Box();
    /** How many players are laid out (1 or 2). */
    public final int players;

    /**
     * Works out the layout.
     * @param players 1 or 2
     * @param titleWidth width of the title pill
     * @param digits0 characters in player 1's score text (for example 3 for "102" or 4 for "400+")
     * @param digits1 characters in player 2's score text
     */
    public EndLayout(int players, float titleWidth, int digits0, int digits1) {
        this.players = players == 2 ? 2 : 1;
        title.set(Stage.CX - titleWidth / 2f, 60f, Stage.CX + titleWidth / 2f, 180f);
        back.set(20f, 20f, 260f, 260f);
        int[] digits = {digits0, digits1};
        for (int p = 0; p < this.players; p++) {
            float cx = this.players == 1 ? Stage.CX : (p == 0 ? 560f : 1360f);
            colX[p] = cx;
            float top = this.players == 1 ? 250f : 240f;
            if (this.players == 2) {
                float nw = 8 * DIGIT_W * NAME_SIZE;
                name[p].set(cx - nw / 2f, top, cx + nw / 2f, top + 50f);
                top += 50f + GAP + 10f;
            }
            float sw = Math.max(1, digits[p]) * DIGIT_W * SCORE_SIZE;
            score[p].set(cx - sw / 2f, top, cx + sw / 2f, top + 120f);
            top += 120f + GAP + 10f;
            float rw = 3f * STAR + 2f * STAR_GAP + 2f * STAR_PAD;
            stars[p].set(cx - rw / 2f, top, cx + rw / 2f, top + STAR + 2f * STAR_PAD);
            float footY = 700f;
            float kh = this.players == 1 ? 440f : 400f;
            float fx = this.players == 1 ? 1500f : (p == 0 ? 170f : 1750f);
            kidFootX[p] = fx;
            kidFootY[p] = footY;
            kidHeight[p] = kh;
            float kw = kh * 0.5f; // a kid is about half as wide as it is tall
            kid[p].set(fx - kw / 2f, footY - kh, fx + kw / 2f, footY + 20f);
        }
        float tw = 400f, th = 280f, gap = 40f;
        float x0 = (Stage.W - 3f * tw - 2f * gap) / 2f;
        for (int i = 0; i < 3; i++) tiles[i].set(x0 + i * (tw + gap), TILES_TOP, x0 + i * (tw + gap) + tw, TILES_TOP + th);
    }

    /**
     * Centre x of a star slot.
     * @param p player 0 or 1
     * @param i slot 0..2
     * @return stage x
     */
    public float slotX(int p, int i) {
        return colX[p] + (i - 1) * (STAR + STAR_GAP);
    }

    /**
     * Centre y of the star row.
     * @param p player 0 or 1
     * @return stage y
     */
    public float slotY(int p) {
        return stars[p].cy();
    }

    /**
     * Baseline for the score text.
     * @param p player 0 or 1
     * @return stage y
     */
    public float scoreBaseline(int p) {
        return score[p].t + SCORE_SIZE * 0.95f;
    }

    /**
     * Baseline for the player's name.
     * @param p player 0 or 1
     * @return stage y
     */
    public float nameBaseline(int p) {
        return name[p].t + NAME_SIZE * 1.05f;
    }

    /** Stage size constants (the real Stage class pulls in android, so they are copied here). */
    private static final class Stage {
        static final float W = 1920f, H = 1080f, CX = 960f;
    }
}
