package com.angga.movetv.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.angga.movetv.core.Brushes;
import com.angga.movetv.core.Ease;
import com.angga.movetv.core.SpriteCache;
import com.angga.movetv.core.Stage;

/**
 * Colours, fonts and drawing helpers shared by every menu screen: Flat storybook style, no gradients.
 * Anything that costs more than a few shapes is painted once into the {@link SpriteCache} and drawn as a
 * bitmap afterwards. The draw methods allocate nothing. Use from the main thread only (they share paints).
 */
public final class Theme {

    /** Warm paper background. */
    public static final int PAPER = 0xFFF3EBDD;
    /** Muted blue. */
    public static final int BLUE = 0xFF5B8DB8;
    /** Sage green. */
    public static final int SAGE = 0xFF9CB89A;
    /** Warm orange. */
    public static final int ORANGE = 0xFFE89A4C;
    /** Mustard yellow. */
    public static final int MUSTARD = 0xFFF2C572;
    /** Dusty pink. */
    public static final int PINK = 0xFFE8A598;
    /** Dark ink, used for text and details. */
    public static final int INK = 0xFF2F3A45;
    /** Player 1 colour (cursor and ring). */
    public static final int P1 = 0xFF29B6F6;
    /** Player 2 colour (cursor and ring). */
    public static final int P2 = 0xFFFF6EC7;
    /** Lighter green used for leaves. */
    public static final int LEAF = 0xFF6FA06B;
    /** Brown used for trunks. */
    public static final int BROWN = 0xFF9B6B43;

    /** The rounded bold system font, used until (or unless) Fredoka loads. */
    public static final Typeface FONT = Typeface.create("sans-serif-rounded", Typeface.BOLD);
    private static Typeface titleFont = FONT, labelFont = FONT;
    private static boolean fontsTried;

    /** Text at or below this size uses the lighter label weight. */
    static final float LABEL_MAX = 36.5f;

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RING = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();
    private static final RectF R2 = new RectF();
    private static final float[] CLOUD_SPEED = {14f, 9f, 19f}, CLOUD_X = {120f, 900f, 1500f}, CLOUD_Y = {120f, 260f, 90f};
    private static final int[] CLOUD_SIZE = {170, 230, 140};
    // Bitmaps looked up every frame are remembered here, so no cache key text is built per frame.
    private static final Bitmap[] cursorBmp = new Bitmap[2];
    private static final Bitmap[] cloudBmp = new Bitmap[3];
    private static final Bitmap[] starsBmp = new Bitmap[4];
    private static Bitmap hillFar, hillNear;

    static {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setStrokeJoin(Paint.Join.ROUND);
        TEXT.setTextAlign(Paint.Align.CENTER);
        RING.setStyle(Paint.Style.STROKE);
        RING.setStrokeCap(Paint.Cap.ROUND);
    }

    private Theme() { }

    /**
     * Loads the Fredoka font (SemiBold for titles, Medium for small labels) from the app's assets. Safe to call
     * more than once. If the font cannot be loaded (Android older than 8, or a read error) the rounded system
     * font stays in use; that is logged once.
     * @param ctx any context
     */
    public static void init(android.content.Context ctx) {
        if (fontsTried || ctx == null) return;
        fontsTried = true;
        try {
            if (android.os.Build.VERSION.SDK_INT < 26) throw new IllegalStateException("needs Android 8 for variable fonts");
            android.content.res.AssetManager am = ctx.getApplicationContext().getAssets();
            Typeface semi = new Typeface.Builder(am, "fonts/Fredoka-Variable.ttf")
                    .setFontVariationSettings("'wght' 600, 'wdth' 100").build();
            Typeface med = new Typeface.Builder(am, "fonts/Fredoka-Variable.ttf")
                    .setFontVariationSettings("'wght' 500, 'wdth' 100").build();
            if (semi == null || med == null) throw new IllegalStateException("font did not load");
            titleFont = semi;
            labelFont = med;
        } catch (Throwable t) {
            android.util.Log.w("MoveTV", "Fredoka not loaded, using the system font: " + t);
        }
    }

    private static void useFont(float size) {
        TEXT.setTypeface(size <= LABEL_MAX ? labelFont : titleFont);
    }

    private static final TextFit.Measure MEASURE = (str, size) -> {
        useFont(size);
        TEXT.setTextSize(size);
        return TEXT.measureText(str);
    };

    // ---- small colour helpers ----

    /**
     * A darker shade of a colour (for flat shadows and thin outlines).
     * @param colour ARGB colour
     * @return the darker colour, fully opaque
     */
    public static int darker(int colour) {
        int r = (int) (((colour >> 16) & 0xff) * 0.72f), g = (int) (((colour >> 8) & 0xff) * 0.72f),
                b = (int) ((colour & 0xff) * 0.72f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Whether dark text reads better than white on a colour.
     * @param colour ARGB colour
     * @return true if the colour is light
     */
    public static boolean isLight(int colour) {
        float l = 0.299f * ((colour >> 16) & 0xff) + 0.587f * ((colour >> 8) & 0xff) + 0.114f * (colour & 0xff);
        return l > 150f;
    }

    /**
     * The player's colour.
     * @param player 0 or 1
     * @return P1 or P2
     */
    public static int playerColour(int player) {
        return player == 1 ? P2 : P1;
    }

    private static void rounded(Path p, float x, float y, float w, float h, float r) {
        R.set(x, y, x + w, y + h);
        p.addRoundRect(R, r, r, Path.Direction.CW);
    }

    /**
     * Draws text centred on x, in the rounded bold font.
     * @param c canvas
     * @param s the text (must not be built every frame)
     * @param cx centre x
     * @param baseline baseline y
     * @param size text size in stage pixels
     * @param colour ARGB colour
     */
    public static void drawCentered(Canvas c, String s, float cx, float baseline, float size, int colour) {
        useFont(size);
        TEXT.setTextSize(size);
        TEXT.setColor(colour);
        c.drawText(s, cx, baseline, TEXT);
    }

    /**
     * Draws a line of text on a soft rounded panel, so it reads over the camera picture.
     * @param c canvas
     * @param s the text (must not be built every frame)
     * @param cx centre x
     * @param cy centre y of the panel
     * @param size text size (at least 36)
     * @param bg panel colour (ARGB, usually mostly opaque)
     * @param fg text colour
     */
    public static void drawBanner(Canvas c, String s, float cx, float cy, float size, int bg, int fg) {
        // never wider than the stage: shrink first (not below 40 for big titles), then cut
        float maxText = Stage.W - 160f - size * 1.6f;
        String shown = s;
        float sz = size;
        useFont(sz);
        TEXT.setTextSize(sz);
        if (TEXT.measureText(s) > maxText) {
            TextFit.Result r = TextFit.fit(MEASURE, s, maxText, size, Math.min(size, Math.max(36f, 40f)), 1);
            shown = r.lines[0];
            sz = r.size;
        }
        useFont(sz);
        TEXT.setTextSize(sz);
        float w = TEXT.measureText(shown) + sz * 1.6f, h = sz * 1.9f;
        R.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        FILL.setColor(bg);
        c.drawRoundRect(R, h / 2f, h / 2f, FILL);
        TEXT.setColor(fg);
        c.drawText(shown, cx, cy + sz * 0.35f, TEXT);
    }

    // ---- cursor and ring ----

    /**
     * The hand cursor sprite (a white mitten with a glow in the player's colour), painted once.
     * @param player 0 or 1
     * @return a 168x168 bitmap whose centre is the cursor point
     */
    public static Bitmap cursorSprite(final int player) {
        final int idx = player == 1 ? 1 : 0;
        if (cursorBmp[idx] != null) return cursorBmp[idx];
        final int col = playerColour(idx);
        cursorBmp[idx] = SpriteCache.shared().get("theme_cursor_" + idx, 168, 168, (c, w, h) -> {
            float cx = w / 2f, cy = h / 2f;
            FILL.setColor((col & 0x00FFFFFF) | (0x38 << 24)); // glow: wide, low-opacity disc (no blur)
            c.drawCircle(cx, cy, 80f, FILL);
            FILL.setColor((col & 0x00FFFFFF) | (0x55 << 24));
            c.drawCircle(cx, cy, 58f, FILL);
            // hand: four fingers, a thumb and a palm, white with a player-colour outline
            Path hand = new Path();
            hand.addCircle(cx, cy + 14f, 28f, Path.Direction.CW);
            for (int i = 0; i < 4; i++) {
                float ang = (-24f + i * 16f);
                float fx = cx + (float) Math.sin(Math.toRadians(ang)) * 12f;
                R.set(fx - 8f, cy - 52f, fx + 8f, cy + 8f);
                Path f = new Path();
                f.addRoundRect(R, 8f, 8f, Path.Direction.CW);
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(ang, fx, cy + 8f);
                f.transform(m);
                hand.addPath(f);
            }
            Path thumb = new Path();
            R.set(cx - 44f, cy - 8f, cx - 22f, cy + 28f);
            thumb.addRoundRect(R, 10f, 10f, Path.Direction.CW);
            android.graphics.Matrix tm = new android.graphics.Matrix();
            tm.postRotate(-35f, cx - 30f, cy + 20f);
            thumb.transform(tm);
            hand.addPath(thumb);
            LINE.setColor(col | 0xFF000000);
            LINE.setStrokeWidth(10f);
            c.drawPath(hand, LINE);
            FILL.setColor(0xFFFFFFFF);
            c.drawPath(hand, FILL);
        });
        return cursorBmp[idx];
    }

    /**
     * Draws the hand cursor.
     * @param c canvas
     * @param x centre x
     * @param y centre y
     * @param player 0 or 1 (chooses the colour)
     * @param pulse 0..1, a gentle size pulse
     */
    public static void drawCursor(Canvas c, float x, float y, int player, float pulse) {
        Bitmap b = cursorSprite(player);
        float s = 1f + 0.05f * pulse;
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        c.drawBitmap(b, -b.getWidth() / 2f, -b.getHeight() / 2f, null);
        c.restore();
    }

    /**
     * Draws the progress ring that fills clockwise around the cursor.
     * @param c canvas
     * @param cx centre x
     * @param cy centre y
     * @param radius ring radius
     * @param progress 0..1
     * @param colour ring colour
     */
    public static void drawRing(Canvas c, float cx, float cy, float radius, float progress, int colour) {
        if (progress <= 0f) return;
        R.set(cx - radius, cy - radius, cx + radius, cy + radius);
        RING.setStrokeWidth(14f);
        RING.setColor(0x55FFFFFF);
        c.drawArc(R, 0f, 360f, false, RING);
        RING.setStrokeWidth(14f);
        RING.setColor(colour | 0xFF000000);
        c.drawArc(R, -90f, 360f * Math.min(1f, progress), false, RING);
    }

    // ---- tiles ----

    /**
     * Where a tile's icon sits and how big it is: a square in the upper part of the tile.
     * @param h tile height
     * @param w tile width
     * @return the icon's side length
     */
    public static float iconSize(float w, float h) {
        return Math.min(w * 0.72f, h * 0.56f);
    }

    /**
     * The tile body: a flat rounded rectangle in its colour with a thin darker outline and the title (and
     * optional subtitle) baked in. Painted once.
     * @param key unique cache key
     * @param w width
     * @param h height
     * @param colour fill colour
     * @param title big text at the bottom
     * @param subtitle small text under the title, or null
     * @return the body bitmap (w x h)
     */
    public static Bitmap tileBody(String key, final int w, final int h, final int colour, final String title,
                                  final String subtitle) {
        return SpriteCache.shared().get(key, w, h, (c, bw, bh) -> {
            float r = Math.min(bw, bh) * 0.12f;
            Path p = new Path();
            rounded(p, 3f, 3f, bw - 6f, bh - 6f, r);
            FILL.setColor(colour);
            c.drawPath(p, FILL);
            LINE.setColor(darker(colour));
            LINE.setStrokeWidth(5f);
            c.drawPath(p, LINE);
            int ink = isLight(colour) ? INK : 0xFFFFFFFF;
            float maxW = bw * 0.88f;
            if (subtitle == null) {
                drawFitted(c, title, bw / 2f, bh * 0.88f, 48f, 40f, 2, maxW, ink);
            } else {
                drawFitted(c, title, bw / 2f, bh * 0.78f, 48f, 40f, 2, maxW, ink);
                drawFitted(c, subtitle, bw / 2f, bh * 0.92f, 36f, 36f, 1, maxW, ink);
            }
        });
    }

    /**
     * Draws text that always fits its container, using {@link TextFit}: shrink, then wrap, then cut with an
     * ellipsis. Used while painting a tile once, so it may allocate.
     */
    private static void drawFitted(Canvas c, String text, float cx, float baseline, float start, float min,
                                   int maxLines, float maxW, int colour) {
        TextFit.Result r = TextFit.fit(MEASURE, text, maxW, start, min, maxLines);
        int n = r.lines.length;
        for (int i = 0; i < n; i++) {
            drawCentered(c, r.lines[i], cx, baseline - (n - 1 - i) * r.size * 1.1f, r.size, colour);
        }
    }

    /**
     * The flat shadow under a tile: the same rounded shape in a darker shade. Painted once.
     * @param key unique cache key
     * @param w width
     * @param h height
     * @param colour the tile's fill colour
     * @return the shadow bitmap (w x h)
     */
    public static Bitmap tileShadow(String key, final int w, final int h, final int colour) {
        return SpriteCache.shared().get(key, w, h, (c, bw, bh) -> {
            Path p = new Path();
            rounded(p, 3f, 3f, bw - 6f, bh - 6f, Math.min(bw, bh) * 0.12f);
            Brushes.flatShadow(c, p, 0f, (darker(darker(colour)) & 0x00FFFFFF) | (0xCC << 24));
        });
    }

    /**
     * Draws a tile: shadow, body, optional icon and badge. On hover the tile grows (outBack), gets a bigger
     * shadow and a brighter face, and the icon wobbles.
     * @param c canvas
     * @param shadow shadow bitmap from {@link #tileShadow}
     * @param body body bitmap from {@link #tileBody}
     * @param icon icon bitmap (square), or null
     * @param badge extra bitmap drawn near the bottom of the icon area (stars, ON/OFF), or null
     * @param corner small bitmap drawn in the top-right corner (a tick for the chosen option), or null
     * @param x left
     * @param y top
     * @param hover 0..1 hover amount
     * @param time seconds, drives the icon wobble
     */
    public static void drawTile(Canvas c, Bitmap shadow, Bitmap body, Bitmap icon, Bitmap badge, Bitmap corner,
                                float x, float y, float hover, float time) {
        float w = body.getWidth(), h = body.getHeight();
        float cx = x + w / 2f, cy = y + h / 2f;
        float s = 1f + 0.08f * Ease.outBack(hover);
        float d = 10f + 16f * hover;
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        c.translate(-cx, -cy);
        c.drawBitmap(shadow, x + d * 0.6f, y + d, null);
        c.drawBitmap(body, x, y, null);
        if (hover > 0f) {
            FILL.setColor(((int) (hover * 46f) << 24) | 0x00FFFFFF);
            R.set(x + 3f, y + 3f, x + w - 3f, y + h - 3f);
            float rr = Math.min(w, h) * 0.12f;
            c.drawRoundRect(R, rr, rr, FILL);
        }
        if (icon != null) {
            float isz = icon.getWidth();
            float icx = cx, icy = y + h * 0.40f - (badge != null ? h * 0.05f : 0f);
            float wob = hover > 0f ? (float) Math.sin(time * 7f) * 4f * hover : 0f;
            float bob = hover > 0f ? (float) Math.sin(time * 5f) * 6f * hover : 0f;
            c.save();
            c.translate(icx, icy + bob);
            c.rotate(wob);
            c.drawBitmap(icon, -isz / 2f, -isz / 2f, null);
            c.restore();
        }
        if (badge != null) {
            c.drawBitmap(badge, cx - badge.getWidth() / 2f, y + h * 0.66f - badge.getHeight() / 2f, null);
        }
        if (corner != null) {
            c.drawBitmap(corner, x + w - corner.getWidth() - 20f, y + 20f, null);
        }
        c.restore();
    }

    /**
     * Draws the remote focus highlight around a tile.
     * @param c canvas
     * @param x tile left
     * @param y tile top
     * @param w tile width
     * @param h tile height
     * @param hover the tile's hover amount (the highlight grows with it)
     * @param time seconds, for a gentle pulse
     */
    public static void drawFocus(Canvas c, float x, float y, float w, float h, float hover, float time) {
        float s = 1f + 0.08f * Ease.outBack(hover);
        float cx = x + w / 2f, cy = y + h / 2f;
        float grow = 14f + 4f * (float) Math.sin(time * 6f);
        float hw = w * s / 2f + grow, hh = h * s / 2f + grow;
        float rr = Math.min(w, h) * 0.12f + grow;
        R.set(cx - hw, cy - hh, cx + hw, cy + hh);
        LINE.setStrokeWidth(12f);
        LINE.setColor(0xFFFFFFFF);
        c.drawRoundRect(R, rr, rr, LINE);
        R2.set(cx - hw - 8f, cy - hh - 8f, cx + hw + 8f, cy + hh + 8f);
        LINE.setStrokeWidth(6f);
        LINE.setColor(INK);
        c.drawRoundRect(R2, rr + 8f, rr + 8f, LINE);
    }

    // ---- backdrop ----

    private static Bitmap cloud(final int size) {
        return SpriteCache.shared().get("theme_cloud_" + size, size * 2, size, (c, w, h) -> {
            FILL.setColor(0xFFFFFFFF);
            c.drawCircle(w * 0.30f, h * 0.60f, h * 0.30f, FILL);
            c.drawCircle(w * 0.50f, h * 0.42f, h * 0.38f, FILL);
            c.drawCircle(w * 0.70f, h * 0.60f, h * 0.30f, FILL);
            R.set(w * 0.20f, h * 0.58f, w * 0.80f, h * 0.90f);
            c.drawRoundRect(R, h * 0.16f, h * 0.16f, FILL);
        });
    }

    private static Bitmap hill(final String key, final int colour, final float peak, final float wide) {
        return SpriteCache.shared().get(key, 1920, 420, (c, w, h) -> {
            Path p = new Path();
            p.moveTo(0f, h);
            p.lineTo(0f, h * (1f - peak) + 60f);
            p.cubicTo(w * 0.18f * wide, h * (1f - peak) - 70f, w * 0.40f, h * (1f - peak) - 30f, w * 0.55f,
                    h * (1f - peak) + 50f);
            p.cubicTo(w * 0.72f, h * (1f - peak) + 120f, w * 0.88f, h * (1f - peak) - 20f, w, h * (1f - peak) + 40f);
            p.lineTo(w, h);
            p.close();
            FILL.setColor(colour);
            c.drawPath(p, FILL);
        });
    }

    /**
     * Draws the backdrop of every menu screen: the camera picture, dimmed to 60%, so kids see themselves; or,
     * without a camera picture (test mode), calm flat storybook clouds and hills on paper.
     * @param c canvas
     * @param camera the camera picture, or null
     * @param time seconds, drives the drifting clouds
     */
    public static void drawBackdrop(Canvas c, Bitmap camera, float time) {
        if (camera != null) {
            Stage.drawCamera(c, camera, 0.6f);
            return;
        }
        FILL.setColor(PAPER);
        c.drawRect(0f, 0f, Stage.W, Stage.H, FILL);
        if (hillFar == null) hillFar = hill("theme_hill_far", 0xFFC5D6BE, 0.62f, 1.1f);
        if (hillNear == null) hillNear = hill("theme_hill_near", SAGE, 0.42f, 0.8f);
        c.drawBitmap(hillFar, 0f, Stage.H - hillFar.getHeight() + 40f, null);
        c.drawBitmap(hillNear, 0f, Stage.H - hillNear.getHeight() + 80f, null);
        for (int i = 0; i < 3; i++) {
            if (cloudBmp[i] == null) cloudBmp[i] = cloud(CLOUD_SIZE[i]);
            Bitmap cl = cloudBmp[i];
            float span = Stage.W + cl.getWidth();
            float x = ((CLOUD_X[i] + time * CLOUD_SPEED[i]) % span) - cl.getWidth() * 0.5f;
            c.drawBitmap(cl, x, CLOUD_Y[i], null);
        }
    }

    // ---- stars and badges ----

    private static void starPath(Path p, float cx, float cy, float r) {
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = (i % 2 == 0) ? r : r * 0.45f;
            float px = cx + (float) Math.cos(a) * rr, py = cy + (float) Math.sin(a) * rr;
            if (i == 0) p.moveTo(px, py); else p.lineTo(px, py);
        }
        p.close();
    }

    /**
     * A row of three stars with the first {@code n} filled.
     * @param n 0..3 filled stars
     * @return a 270x90 bitmap
     */
    public static Bitmap starsBadge(final int n) {
        final int count = Math.max(0, Math.min(3, n));
        if (starsBmp[count] != null) return starsBmp[count];
        starsBmp[count] = SpriteCache.shared().get("theme_stars_" + count, 270, 90, (c, w, h) -> {
            for (int i = 0; i < 3; i++) {
                Path p = new Path();
                starPath(p, 45f + i * 90f, 46f, 38f);
                boolean on = i < count;
                FILL.setColor(on ? 0xFFF2B705 : 0xFFD9CDB8);
                c.drawPath(p, FILL);
                LINE.setColor(on ? 0xFFC98F00 : 0xFFBFB29A);
                LINE.setStrokeWidth(4f);
                c.drawPath(p, LINE);
            }
        });
        return starsBmp[count];
    }

    /**
     * A round "ON" or "OFF" label.
     * @param on true for ON (green), false for OFF (pink)
     * @return a 200x84 bitmap
     */
    public static Bitmap onOffBadge(final boolean on) {
        return SpriteCache.shared().get(on ? "theme_badge_on" : "theme_badge_off", 200, 84, (c, w, h) -> {
            R.set(2f, 2f, w - 2f, h - 2f);
            FILL.setColor(on ? 0xFF6FA06B : 0xFFD98B7E);
            c.drawRoundRect(R, h / 2f, h / 2f, FILL);
            drawCentered(c, on ? "ON" : "OFF", w / 2f, h * 0.68f, 46f, 0xFFFFFFFF);
        });
    }

    /**
     * A round label for the pointer setting.
     * @param reach true for "Easy reach", false for "On my hand"
     * @return a 320x84 bitmap
     */
    public static Bitmap pointerBadge(final boolean reach) {
        return SpriteCache.shared().get(reach ? "theme_badge_reach" : "theme_badge_hand", 320, 84, (c, w, h) -> {
            R.set(2f, 2f, w - 2f, h - 2f);
            FILL.setColor(reach ? 0xFFE89A4C : 0xFF5B8DB8);
            c.drawRoundRect(R, h / 2f, h / 2f, FILL);
            drawCentered(c, reach ? "Easy reach" : "On my hand", w / 2f, h * 0.68f, 44f, 0xFFFFFFFF);
        });
    }

    /**
     * An arm reaching out to a hand with a ring (Pointer).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paintPointer(Canvas c, int w, int h) {
        LINE.setColor(INK);
        LINE.setStrokeWidth(w * 0.07f);
        c.drawLine(w * 0.18f, h * 0.88f, w * 0.52f, h * 0.48f, LINE);
        FILL.setColor(PAPER);
        c.drawCircle(w * 0.56f, h * 0.44f, w * 0.11f, FILL);
        c.drawCircle(w * 0.56f, h * 0.44f, w * 0.11f, LINE);
        LINE.setStrokeWidth(w * 0.04f);
        FILL.setColor(PINK);
        c.drawCircle(w * 0.74f, h * 0.30f, w * 0.14f, FILL);
        c.drawCircle(w * 0.74f, h * 0.30f, w * 0.14f, LINE);
    }

    /**
     * A round tick mark, used to show the chosen hold time.
     * @return an 84x84 bitmap
     */
    public static Bitmap checkBadge() {
        return SpriteCache.shared().get("theme_badge_check", 84, 84, (c, w, h) -> {
            FILL.setColor(0xFF6FA06B);
            c.drawCircle(w / 2f, h / 2f, 40f, FILL);
            Path p = new Path();
            p.moveTo(w * 0.27f, h * 0.52f);
            p.lineTo(w * 0.44f, h * 0.68f);
            p.lineTo(w * 0.74f, h * 0.34f);
            LINE.setColor(0xFFFFFFFF);
            LINE.setStrokeWidth(10f);
            c.drawPath(p, LINE);
        });
    }

    // ---- icons (painted once into tile icon bitmaps) ----

    /**
     * Makes a square icon bitmap from a painter.
     * @param key unique cache key
     * @param size side length
     * @param p the painter
     * @return the icon bitmap
     */
    public static Bitmap icon(String key, int size, SpriteCache.Painter p) {
        return SpriteCache.shared().get(key, size, size, p);
    }

    /**
     * A house.
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paintHome(Canvas c, int w, int h) {
        Path roof = new Path();
        roof.moveTo(w * 0.5f, h * 0.10f);
        roof.lineTo(w * 0.94f, h * 0.50f);
        roof.lineTo(w * 0.06f, h * 0.50f);
        roof.close();
        FILL.setColor(darker(ORANGE));
        c.drawRect(w * 0.20f + 8f, h * 0.48f + 8f, w * 0.80f + 8f, h * 0.88f + 8f, FILL);
        FILL.setColor(PAPER);
        c.drawRect(w * 0.20f, h * 0.48f, w * 0.80f, h * 0.88f, FILL);
        FILL.setColor(ORANGE);
        c.drawPath(roof, FILL);
        FILL.setColor(INK);
        R.set(w * 0.43f, h * 0.60f, w * 0.57f, h * 0.88f);
        c.drawRoundRect(R, w * 0.05f, w * 0.05f, FILL);
    }

    /**
     * A sprout (Easy).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paintSprout(Canvas c, int w, int h) {
        FILL.setColor(BROWN);
        Path pot = new Path();
        pot.moveTo(w * 0.28f, h * 0.66f);
        pot.lineTo(w * 0.72f, h * 0.66f);
        pot.lineTo(w * 0.64f, h * 0.92f);
        pot.lineTo(w * 0.36f, h * 0.92f);
        pot.close();
        c.drawPath(pot, FILL);
        LINE.setColor(0xFF4F8A4B);
        LINE.setStrokeWidth(w * 0.05f);
        c.drawLine(w * 0.5f, h * 0.66f, w * 0.5f, h * 0.36f, LINE);
        FILL.setColor(LEAF);
        R.set(w * 0.50f, h * 0.18f, w * 0.88f, h * 0.44f);
        c.drawOval(R, FILL);
        R.set(w * 0.12f, h * 0.28f, w * 0.50f, h * 0.54f);
        c.drawOval(R, FILL);
    }

    /**
     * A tree (Medium).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paintTree(Canvas c, int w, int h) {
        FILL.setColor(BROWN);
        c.drawRect(w * 0.44f, h * 0.55f, w * 0.56f, h * 0.92f, FILL);
        FILL.setColor(darker(LEAF));
        c.drawCircle(w * 0.52f, h * 0.38f, w * 0.30f, FILL);
        FILL.setColor(LEAF);
        c.drawCircle(w * 0.50f, h * 0.36f, w * 0.30f, FILL);
        FILL.setColor(0x55FFFFFF);
        c.drawCircle(w * 0.40f, h * 0.28f, w * 0.08f, FILL);
    }

    /**
     * A mountain (Hard).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paintMountain(Canvas c, int w, int h) {
        Path back = new Path();
        back.moveTo(w * 0.40f, h * 0.90f);
        back.lineTo(w * 0.68f, h * 0.30f);
        back.lineTo(w * 0.96f, h * 0.90f);
        back.close();
        FILL.setColor(darker(BLUE));
        c.drawPath(back, FILL);
        Path front = new Path();
        front.moveTo(w * 0.04f, h * 0.90f);
        front.lineTo(w * 0.38f, h * 0.18f);
        front.lineTo(w * 0.72f, h * 0.90f);
        front.close();
        FILL.setColor(BLUE);
        c.drawPath(front, FILL);
        Path snow = new Path();
        snow.moveTo(w * 0.38f, h * 0.18f);
        snow.lineTo(w * 0.47f, h * 0.38f);
        snow.lineTo(w * 0.41f, h * 0.35f);
        snow.lineTo(w * 0.38f, h * 0.42f);
        snow.lineTo(w * 0.34f, h * 0.35f);
        snow.lineTo(w * 0.29f, h * 0.38f);
        snow.close();
        FILL.setColor(0xFFFFFFFF);
        c.drawPath(snow, FILL);
    }

    /**
     * A clock face with 1, 2 or 3 dots (hold time choices).
     * @param dots 1 to 3
     * @return the painter
     */
    public static SpriteCache.Painter clockPainter(final int dots) {
        return (c, w, h) -> {
            FILL.setColor(darker(MUSTARD));
            c.drawCircle(w * 0.5f + 8f, h * 0.45f + 8f, w * 0.34f, FILL);
            FILL.setColor(PAPER);
            c.drawCircle(w * 0.5f, h * 0.45f, w * 0.34f, FILL);
            LINE.setColor(INK);
            LINE.setStrokeWidth(w * 0.045f);
            c.drawCircle(w * 0.5f, h * 0.45f, w * 0.34f, LINE);
            c.drawLine(w * 0.5f, h * 0.45f, w * 0.5f, h * 0.24f, LINE);
            c.drawLine(w * 0.5f, h * 0.45f, w * 0.64f, h * 0.52f, LINE);
            FILL.setColor(INK);
            for (int i = 0; i < dots; i++) {
                c.drawCircle(w * (0.5f + (i - (dots - 1) / 2f) * 0.16f), h * 0.88f, w * 0.045f, FILL);
            }
        };
    }

    /**
     * A sun (light boost).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paintSun(Canvas c, int w, int h) {
        LINE.setColor(darker(MUSTARD));
        LINE.setStrokeWidth(w * 0.06f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            c.drawLine(w * 0.5f + (float) Math.cos(a) * w * 0.30f, h * 0.5f + (float) Math.sin(a) * w * 0.30f,
                    w * 0.5f + (float) Math.cos(a) * w * 0.44f, h * 0.5f + (float) Math.sin(a) * w * 0.44f, LINE);
        }
        FILL.setColor(darker(MUSTARD));
        c.drawCircle(w * 0.5f + 6f, h * 0.5f + 6f, w * 0.24f, FILL);
        FILL.setColor(0xFFFFE08A);
        c.drawCircle(w * 0.5f, h * 0.5f, w * 0.24f, FILL);
    }

    /**
     * A speed gauge (performance overlay).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paintGauge(Canvas c, int w, int h) {
        R.set(w * 0.10f, h * 0.20f, w * 0.90f, h * 1.00f);
        FILL.setColor(PAPER);
        c.drawArc(R, 180f, 180f, true, FILL);
        LINE.setColor(INK);
        LINE.setStrokeWidth(w * 0.05f);
        c.drawArc(R, 180f, 180f, false, LINE);
        c.drawLine(w * 0.5f, h * 0.60f, w * 0.68f, h * 0.36f, LINE);
        FILL.setColor(INK);
        c.drawCircle(w * 0.5f, h * 0.60f, w * 0.05f, FILL);
    }

    /**
     * Two forward arrows (Skip).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paintSkip(Canvas c, int w, int h) {
        LINE.setColor(INK);
        LINE.setStrokeWidth(w * 0.09f);
        for (int i = 0; i < 2; i++) {
            float x = w * (0.30f + i * 0.26f);
            Path p = new Path();
            p.moveTo(x, h * 0.25f);
            p.lineTo(x + w * 0.22f, h * 0.50f);
            p.lineTo(x, h * 0.75f);
            c.drawPath(p, LINE);
        }
    }

    /**
     * A big tick (Done).
     * @param c canvas
     * @param w width
     * @param h height
     */
    public static void paintDone(Canvas c, int w, int h) {
        FILL.setColor(0xFF6FA06B);
        c.drawCircle(w * 0.5f, h * 0.5f, w * 0.40f, FILL);
        Path p = new Path();
        p.moveTo(w * 0.30f, h * 0.52f);
        p.lineTo(w * 0.45f, h * 0.67f);
        p.lineTo(w * 0.72f, h * 0.36f);
        LINE.setColor(0xFFFFFFFF);
        LINE.setStrokeWidth(w * 0.09f);
        c.drawPath(p, LINE);
    }
}
