package com.wiggleplay.app.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders complex art (layered shapes, hatching, neon strokes) ONCE into a bitmap and keeps it, so later
 * frames only copy the bitmap. The TV's GPU is weak: this is what keeps the frame rate up.
 *
 * Ask for a bitmap once (for example in a scene's {@code enter}) and keep the reference. Looking it up
 * with a constant key every frame is fine too; building the key text every frame would allocate.
 */
public final class SpriteCache {

    /** Draws one sprite. */
    public interface Painter {
        /**
         * Paints the sprite on a transparent canvas.
         * @param c software canvas on the new bitmap
         * @param w bitmap width in pixels
         * @param h bitmap height in pixels
         */
        void paint(Canvas c, int w, int h);
    }

    private static final SpriteCache SHARED = new SpriteCache();

    private final Map<String, Bitmap> map = new HashMap<>();
    private long bytes;
    private int clears, lastTrim = -1;

    /**
     * The cache shared by the whole app.
     * @return the shared cache
     */
    public static SpriteCache shared() {
        return SHARED;
    }

    /**
     * Gets a sprite, painting it the first time.
     * @param key unique name of the sprite (include the size if it can vary)
     * @param w width in pixels
     * @param h height in pixels
     * @param p paints the sprite; only called when the key is not cached yet
     * @return the bitmap (ARGB_8888); do not recycle it
     */
    public synchronized Bitmap get(String key, int w, int h, Painter p) {
        Bitmap b = map.get(key);
        if (b != null) return b;
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        p.paint(new Canvas(b), w, h); // a Canvas on a Bitmap is a software canvas
        map.put(key, b);
        bytes += (long) b.getByteCount();
        return b;
    }

    /**
     * Looks a sprite up without creating it.
     * @param key the name
     * @return the bitmap, or null
     */
    public synchronized Bitmap peek(String key) {
        return map.get(key);
    }

    /**
     * Memory held by the cached bitmaps.
     * @return bytes
     */
    public synchronized long bytes() {
        return bytes;
    }

    /**
     * Forgets the sprites whose key starts with a prefix (a game leaving the screen frees its own pictures).
     * Bitmaps are not recycled, so a scene still holding one can finish its frame; the next {@link #get} repaints.
     * @param prefix start of the keys, e.g. "jg_"
     * @return bytes freed from the cache's count
     */
    public synchronized long removePrefix(String prefix) {
        long freed = 0;
        java.util.Iterator<Map.Entry<String, Bitmap>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bitmap> e = it.next();
            if (e.getKey().startsWith(prefix)) {
                freed += e.getValue().getByteCount();
                it.remove();
            }
        }
        bytes -= freed;
        if (freed > 0) {
            // The bitmaps' memory is native and is only given back once the garbage collector runs; ask for one a
            // moment after the game has left the screen (the menu is quiet then) so the next game starts clean.
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(System::gc, 700);
            } catch (RuntimeException ignored) {
                // no looper (plain Java tests)
            }
        }
        return freed;
    }

    /**
     * Remembers the last memory-trim level the system sent (shown in the speed meter).
     * @param level the level from onTrimMemory
     */
    public synchronized void noteTrim(int level) {
        lastTrim = level;
    }

    /**
     * Short text for the speed meter: biggest groups of sprites by name prefix, the last trim level and how many
     * times the cache was emptied.
     * @return for example "jg 12, round 6, theme 3 | trim 15, emptied 1x"
     */
    public synchronized String summary() {
        java.util.Map<String, Long> by = new java.util.HashMap<>();
        for (Map.Entry<String, Bitmap> e : map.entrySet()) {
            String k = e.getKey();
            int i = k.indexOf('_');
            String p = i > 0 ? k.substring(0, i) : k;
            Long v = by.get(p);
            by.put(p, (v == null ? 0L : v) + e.getValue().getByteCount());
        }
        StringBuilder sb = new StringBuilder();
        for (int n = 0; n < 4 && !by.isEmpty(); n++) {
            String best = null;
            long bv = -1;
            for (Map.Entry<String, Long> e : by.entrySet()) if (e.getValue() > bv) { bv = e.getValue(); best = e.getKey(); }
            by.remove(best);
            if (bv < (1L << 20) / 2) break;
            if (sb.length() > 0) sb.append(", ");
            sb.append(best).append(' ').append(bv >> 20);
        }
        return sb.append(" | trim ").append(lastTrim).append(", emptied ").append(clears).append('x').toString();
    }

    /**
     * Forgets every sprite (call when the system asks for memory back). Bitmaps are not recycled, so a scene
     * that still holds one can keep drawing it; the memory is freed once nobody holds it any more, and the
     * next {@link #get} paints a fresh copy.
     */
    public synchronized void clear() {
        clears++;
        map.clear();
        bytes = 0;
    }
}
