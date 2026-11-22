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
        return freed;
    }

    /**
     * Forgets every sprite (call when the system asks for memory back). Bitmaps are not recycled, so a scene
     * that still holds one can keep drawing it; the memory is freed once nobody holds it any more, and the
     * next {@link #get} paints a fresh copy.
     */
    public synchronized void clear() {
        map.clear();
        bytes = 0;
    }
}
