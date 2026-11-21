package com.wiggleplay.app.ui;

/**
 * Makes text fit a container: first it shrinks (one line) down to a minimum size, then it wraps onto up to
 * {@code maxLines} lines at that size, and as a last resort it cuts the last line and adds an ellipsis.
 * Plain Java (the caller supplies the measuring function), so it is unit-tested on the JVM. It allocates, so
 * use it while painting something once, never per frame.
 */
public final class TextFit {

    /** Measures text. */
    public interface Measure {
        /**
         * Width of a string.
         * @param s the text
         * @param size text size in px
         * @return the width in px
         */
        float width(String s, float size);
    }

    /** What to draw. */
    public static final class Result {
        /** Text size to use. */
        public float size;
        /** The lines, top to bottom (1 to maxLines). */
        public String[] lines;
        /** True if the text had to be cut. */
        public boolean cut;
    }

    private static final String DOTS = "…";

    private TextFit() { }

    /**
     * Fits text into a width.
     * @param m measuring function
     * @param text the text
     * @param maxW available width in px
     * @param startSize preferred size
     * @param minSize smallest size allowed
     * @param maxLines most lines allowed (1 or more)
     * @return the size and lines; every line is at most maxW wide at that size, unless even one character is wider
     */
    public static Result fit(Measure m, String text, float maxW, float startSize, float minSize, int maxLines) {
        Result r = new Result();
        for (float s = startSize; s >= minSize; s -= 1f) {
            if (m.width(text, s) <= maxW) {
                r.size = s;
                r.lines = new String[] {text};
                return r;
            }
        }
        r.size = minSize;
        if (maxLines >= 2) {
            int best = -1;
            float bestW = Float.MAX_VALUE;
            for (int i = 1; i < text.length() - 1; i++) {
                if (text.charAt(i) != ' ') continue;
                float w = Math.max(m.width(text.substring(0, i), minSize), m.width(text.substring(i + 1), minSize));
                if (w < bestW) {
                    bestW = w;
                    best = i;
                }
            }
            if (best >= 0) {
                String a = text.substring(0, best), b = text.substring(best + 1);
                if (bestW <= maxW) {
                    r.lines = new String[] {a, b};
                    return r;
                }
                r.cut = true;
                r.lines = new String[] {cutTo(m, a, maxW, minSize), cutTo(m, b, maxW, minSize)};
                return r;
            }
        }
        r.cut = true;
        r.lines = new String[] {cutTo(m, text, maxW, minSize)};
        return r;
    }

    /** Removes characters from the end until the text plus an ellipsis fits. */
    static String cutTo(Measure m, String s, float maxW, float size) {
        if (m.width(s, size) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && m.width(t + DOTS, size) > maxW) t = t.substring(0, t.length() - 1);
        return t.trim() + DOTS;
    }
}
