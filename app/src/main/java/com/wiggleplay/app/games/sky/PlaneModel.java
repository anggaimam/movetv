package com.wiggleplay.app.games.sky;

/**
 * Toy planes drawn as flat shapes from a tiny 3D model. Nothing here depends on Android: the shapes are handed to a
 * {@link Sink} (the game draws them on a canvas, a test writes them as a picture). A plane is a handful of parts
 * (ellipsoids and flat slabs); each frame the parts are turned by roll, yaw and camera height, sorted far to near and
 * painted. Wings stay rigid, so turning really looks like turning. Flat fills only and no allocation per frame.
 * <p>
 * Plane space: x right, y up, z forward (the nose). The camera looks along +z from behind.
 */
final class PlaneModel {

    /** Where the flat shapes go. */
    interface Sink {
        /** A filled ellipse turned by {@code deg} degrees (clockwise on screen). */
        void ellipse(float cx, float cy, float rx, float ry, float deg, int argb);

        /** A closed filled polygon of {@code n} points ({@code xy} holds x,y pairs); {@code stroke} widens it with round corners. */
        void poly(float[] xy, int n, int argb, float stroke);

        /** A line with round ends. */
        void line(float x1, float y1, float x2, float y2, int argb, float width);
    }

    /** The plane kinds. */
    static final int PROP = 0, AIRLINER = 1, JET = 2, SHIP = 3, COUNT = 4;
    /** Names shown in the plane menu. */
    static final String[] NAMES = {"Propeller", "Airliner", "Jet", "Spaceship"};
    /** Relative size of each plane so they all look as wide on screen. */
    static final float[] SIZE = {1f, 0.66f, 0.74f, 0.74f};

    // palette slots
    private static final int B = 0, BS = 1, BL = 2, W = 3, WS = 4, A = 5, AS = 6, C = 7, CS = 8, CL = 9, D = 10, G = 11, GR = 12, ENG = 13;

    /** Colour themes per plane: body, wing, accent, canopy, glow. The first one is the picture on the menu tile. */
    private static final int[][][] THEMES = {
            { // propeller: body, wing, accent
                    {0xFF2FA4E7, 0xFFFBF0DA, 0xFFF7860F, 0xFF23407F, 0},
                    {0xFFE8504A, 0xFFFBF0DA, 0xFFFFC93C, 0xFF3A2A5E, 0},
                    {0xFF4CBF7A, 0xFFFFF4D6, 0xFFEF6C9A, 0xFF1F4D5E, 0},
                    {0xFF9B6BE0, 0xFFF6EEFF, 0xFF37C7B0, 0xFF2B2A66, 0},
                    {0xFFFFC93C, 0xFFFFFFFF, 0xFF2F7DE0, 0xFF1F3A66, 0}},
            { // airliner: body, wing, accent
                    {0xFF45B6EF, 0xFFFBF0DA, 0xFFF7762B, 0xFF22407A, 0},
                    {0xFFF4F1EA, 0xFFD9E2EA, 0xFFE8504A, 0xFF22407A, 0},
                    {0xFF58C27D, 0xFFFBF0DA, 0xFFFFC93C, 0xFF1F4D5E, 0},
                    {0xFFB07CE8, 0xFFFBF0DA, 0xFFFF7BAC, 0xFF2B2A66, 0},
                    {0xFFFFCB45, 0xFFFFF7E0, 0xFF2F8DE0, 0xFF1F3A66, 0}},
            { // jet: body, wing, accent
                    {0xFFFBF0DA, 0xFF2FA597, 0xFFF7762B, 0xFF23407F, 0},
                    {0xFFE9E6DD, 0xFF6F8F4E, 0xFFE8B83C, 0xFF2A3A2E, 0},
                    {0xFFF4F4F4, 0xFF4F7FC8, 0xFFE8504A, 0xFF1F2F5E, 0},
                    {0xFFF3E4C4, 0xFFC8935A, 0xFF3C6EA8, 0xFF3A2A1E, 0},
                    {0xFFEFEFF7, 0xFF8B6CD0, 0xFF37C7B0, 0xFF2B2A66, 0}},
            { // spaceship: body, wing, accent, canopy, glow
                    {0xFFFBF0DA, 0xFF2FA597, 0xFFF7862B, 0xFF23407F, 0xFF36E8F2},
                    {0xFFF2EEF8, 0xFF8B6CD0, 0xFFFFC93C, 0xFF2B2A66, 0xFFFF7BE0},
                    {0xFFF4F4F4, 0xFFE8504A, 0xFF2FA5E7, 0xFF1F2F5E, 0xFFFFD84A},
                    {0xFFE6F2E4, 0xFF4C9A5A, 0xFFEF6C9A, 0xFF1F4D5E, 0xFF7CFF8A},
                    {0xFFEFEFF7, 0xFF4F7FC8, 0xFFF7862B, 0xFF1F2F5E, 0xFF9CB4FF}}
    };

    /**
     * How many colour themes a plane has.
     * @param type plane kind
     * @return the number of themes
     */
    static int themes(int type) {
        return THEMES[type].length;
    }

    // ---- shapes (x right, y up, z forward) ----
    private static final float[] PROP_WING = {0.4f, 0f, -0.2f, 0.4f, 0f, 0.6f, 2.05f, 0.10f, 0.42f, 2.05f, 0.10f, 0.02f};
    private static final float[] PROP_TIP = {1.62f, 0.10f, 0.02f, 1.62f, 0.10f, 0.42f, 2.05f, 0.10f, 0.42f, 2.05f, 0.10f, 0.02f};
    private static final float[] PROP_TAIL = {-0.82f, 0.02f, -1.22f, 0.82f, 0.02f, -1.22f, 0.82f, 0.02f, -0.92f, -0.82f, 0.02f, -0.92f};
    private static final float[] FIN_PROP_L = {-0.025f, 0.12f, -0.80f, -0.025f, 1.10f, -1.12f, -0.025f, 1.10f, -1.36f, -0.025f, 0.12f, -1.40f};
    private static final float[] FIN_PROP_R = {0.025f, 0.12f, -0.80f, 0.025f, 1.10f, -1.12f, 0.025f, 1.10f, -1.36f, 0.025f, 0.12f, -1.40f};
    private static final float[] DISC = new float[16 * 3];
    private final float[] blade = new float[12];
    private float propAngle = 0.5f;

    /** Sets the turn of the propeller in radians (spin it a little every frame). */
    void setProp(float radians) {
        propAngle = radians;
    }



    private static final float[] SHIP_WING = {0.4f, 0f, 0.7f, 2.45f, 0.05f, -0.8f, 2.25f, 0.05f, -1.0f, 0.5f, 0f, -0.9f};
    private static final float[] SHIP_PANEL = {0.6f, 0.01f, 0.25f, 1.85f, 0.055f, -0.62f, 1.75f, 0.055f, -0.88f, 0.6f, 0.01f, -0.88f};
    private static final float[] SHIP_TIP = {2.45f, 0.05f, -0.8f, 2.25f, 0.05f, -1.0f, 1.95f, 0.05f, -0.43f};
    private static final float[] SHIP_FIN = {0.52f, 0.28f, -0.45f, 0.84f, 1.1f, -0.85f, 0.92f, 1.1f, -1.05f, 0.52f, 0.28f, -0.95f};
    private static final float[] SHIP_BLOCK = {-0.11f, 0.41f, -0.3f, 0.11f, 0.41f, -0.3f, 0.2f, 0.36f, -1.45f, -0.2f, 0.36f, -1.45f};

    static {
        for (int t = 0; t < 16; t++) {
            double a = t / 16.0 * 2 * Math.PI;
            DISC[t * 3] = (float) (0.62 * Math.cos(a));
            DISC[t * 3 + 1] = (float) (0.62 * Math.sin(a));
            DISC[t * 3 + 2] = 1.38f;
        }
    }

    // ---- per-frame state ----
    private Sink sink;
    private float cr, sr, cw, sw, ce, se;
    private float ox, oy, k, kw;
    private float tx, ty, tz;
    private final float[] buf = new float[96];
    private final int[] pal = new int[14];
    private final float[] cen = new float[3];
    private final int[] order = new int[16];
    private final float[] dep = new float[16];
    private int type;

    private static final float[][] CENTERS_PROP = {
            {0, 0, 1.38f}, {-0.6f, -0.66f, -0.5f}, {0.6f, -0.66f, -0.5f}, {-1.2f, 0.05f, 0.25f}, {1.2f, 0.05f, 0.25f},
            {0, 0, 0.1f}, {0, 0.36f, 0.34f}, {0, 0, -1.0f}, {0, 0.02f, -1.08f}, {0, 0.6f, -1.12f}};
    private static final float[][] CENTERS_SHIP = {
            {-1.3f, 0, 0.35f}, {1.3f, 0, 0.35f}, {-0.62f, 0, -0.95f}, {0.62f, 0, -0.95f}, {0, 0.1f, 0.25f},
            {-0.75f, 0.7f, -0.7f}, {0.75f, 0.7f, -0.7f}, {0, 0.4f, -0.9f}};
    private static final float[][][] CENTERS = {CENTERS_PROP, null, null, CENTERS_SHIP};

    /**
     * Draws one plane.
     * @param s where the shapes go
     * @param planeType PROP, AIRLINER, JET or SHIP
     * @param theme colour theme number (wraps around)
     * @param rollDeg roll, positive = right wing down
     * @param yawDeg yaw, positive = nose to the right
     * @param elevDeg camera height angle, positive = seen from above
     * @param cx screen x of the middle of the plane
     * @param cy screen y of the middle of the plane
     * @param scale pixels per model unit
     */
    void draw(Sink s, int planeType, int theme, float rollDeg, float yawDeg, float elevDeg, float cx, float cy, float scale) {
        sink = s;
        type = planeType;
        palette(planeType, theme);
        double r = Math.toRadians(rollDeg), w = Math.toRadians(yawDeg), e = Math.toRadians(elevDeg);
        cr = (float) Math.cos(r);
        sr = (float) Math.sin(r);
        cw = (float) Math.cos(w);
        sw = (float) Math.sin(w);
        ce = (float) Math.cos(e);
        se = (float) Math.sin(e);
        ox = cx;
        oy = cy;
        k = scale * SIZE[planeType];
        kw = k / 118f;
        if (planeType == AIRLINER) {
            liner();
            return;
        }
        if (planeType == JET) {
            jet();
            return;
        }
        float[][] cs = CENTERS[planeType];
        int n = cs.length;
        for (int i = 0; i < n; i++) {
            tf(cs[i][0], cs[i][1], cs[i][2]);
            dep[i] = tz;
            int j = i - 1;
            while (j >= 0 && dep[order[j]] < tz) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = i;
        }
        for (int i = 0; i < n; i++) part(order[i]);
    }

    private void palette(int t, int theme) {
        int[][] th = THEMES[t];
        int[] c = th[((theme % th.length) + th.length) % th.length];
        pal[B] = c[0];
        pal[BS] = shade(c[0], 0.78f);
        pal[BL] = light(c[0], 0.45f);
        pal[W] = c[1];
        pal[WS] = shade(c[1], 0.78f);
        pal[A] = c[2];
        pal[AS] = shade(c[2], 0.74f);
        pal[C] = c[3];
        pal[CS] = shade(c[3], 0.7f);
        pal[CL] = light(c[3], 0.35f);
        pal[D] = 0xFF25323F;
        pal[G] = c[4] != 0 ? c[4] : 0xFF36E8F2;
        pal[GR] = 0xFFC9CFD6;
        pal[ENG] = 0xFF8E9CAD;
    }

    static int shade(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int light(int c, float f) {
        int r = ((c >> 16) & 255), g = ((c >> 8) & 255), b = (c & 255);
        r += (int) ((255 - r) * f);
        g += (int) ((255 - g) * f);
        b += (int) ((255 - b) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int alpha(int c, int a) {
        return (c & 0xFFFFFF) | (a << 24);
    }

    /** Turns a point: roll, then yaw, then camera height. Result in tx, ty, tz. */
    private void tf(float x, float y, float z) {
        float x1 = x * cr + y * sr, y1 = -x * sr + y * cr;
        float x2 = x1 * cw + z * sw, z2 = -x1 * sw + z * cw;
        tx = x2;
        ty = y1 * ce + z2 * se;
        tz = -y1 * se + z2 * ce;
    }

    /** Screen position of a point into cen[0], cen[1]; cen[2] = size factor. */
    private void proj(float x, float y, float z) {
        tf(x, y, z);
        float q = 7f / (7f + tz);
        cen[0] = ox + tx * k * q;
        cen[1] = oy - ty * k * q;
        cen[2] = q;
    }

    private void ell(float x, float y, float z, float ax, float ay, float az, int col) {
        proj(x, y, z);
        float px = cen[0], py = cen[1], q = cen[2];
        tf(ax, 0, 0);
        float a1x = tx, a1y = ty;
        tf(0, ay, 0);
        float a2x = tx, a2y = ty;
        tf(0, 0, az);
        float a3x = tx, a3y = ty;
        float a = a1x * a1x + a2x * a2x + a3x * a3x;
        float b = a1x * a1y + a2x * a2y + a3x * a3y;
        float d = a1y * a1y + a2y * a2y + a3y * a3y;
        float th = 0.5f * (float) Math.atan2(2 * b, a - d);
        float m = (a + d) / 2f, df = (float) Math.sqrt(((a - d) / 2f) * ((a - d) / 2f) + b * b);
        float l1 = (float) Math.sqrt(Math.max(m + df, 1e-6f)), l2 = (float) Math.sqrt(Math.max(m - df, 1e-6f));
        sink.ellipse(px, py, l1 * k * q, l2 * k * q, -(float) Math.toDegrees(th), col);
    }

    /** A shaded ellipsoid: dark base, main colour a little higher, a soft highlight on top. */
    private void hull(float x, float y, float z, float ax, float ay, float az, int shadeCol, int main, int lightCol) {
        ell(x, y, z, ax, ay, az, shadeCol);
        ell(x, y + 0.13f * ay, z - 0.04f * az, ax * 0.97f, ay * 0.97f, az * 0.97f, main);
        ell(x, y + 0.54f * ay, z - 0.17f * az, ax * 0.31f, ay * 0.23f, az * 0.43f, alpha(lightCol, 140));
    }

    private void poly(float[] pts, int n, float sx, float dy, int col, float round) {
        for (int i = 0; i < n; i++) {
            tf(pts[i * 3] * sx, pts[i * 3 + 1] + dy, pts[i * 3 + 2]);
            float q = 7f / (7f + tz);
            buf[i * 2] = ox + tx * k * q;
            buf[i * 2 + 1] = oy - ty * k * q;
        }
        sink.poly(buf, n, col, round * kw);
    }

    /** A flat slab with a visible edge: the darker copy sits a little lower. */
    private void slab(float[] pts, int n, float sx, int shadeCol, int main, float round, float thick) {
        poly(pts, n, sx, -thick, shadeCol, round);
        poly(pts, n, sx, 0f, main, round);
    }

    private void line(float x1, float y1, float z1, float x2, float y2, float z2, int col, float w) {
        proj(x1, y1, z1);
        float ax = cen[0], ay = cen[1];
        proj(x2, y2, z2);
        sink.line(ax, ay, cen[0], cen[1], col, w * kw);
    }

    /** True when a surface with this outward direction faces the camera. */
    private boolean faces(float nx, float ny, float nz) {
        tf(nx, ny, nz);
        return tz < 0.02f;
    }

    private void windows(float[] table, int n, float shift, int col) {
        for (int side = -1; side <= 1; side += 2) {
            if (!faces(side, 0.25f, 0f)) continue;
            for (int i = 0; i < n; i++) ell(side * table[i * 3], table[i * 3 + 1], table[i * 3 + 2] + shift, 0.03f, 0.055f, 0.07f, col);
        }
    }

    private void part(int i) {
        switch (type) {
            case PROP: prop(i); break;
            default: ship(i); break;
        }
    }

    private void bladeAt(float a) {
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
        for (int i = 0; i < 4; i++) {
            float bx = (i == 0 || i == 3) ? -0.64f : 0.64f, by = i < 2 ? 0.09f : -0.09f;
            blade[i * 3] = bx * ca - by * sa;
            blade[i * 3 + 1] = bx * sa + by * ca;
            blade[i * 3 + 2] = 1.4f;
        }
    }

    // ---- the propeller plane ----
    private void prop(int i) {
        switch (i) {
            case 0: {
                for (int t = 0; t < 16; t++) {
                    tf(DISC[t * 3], DISC[t * 3 + 1], DISC[t * 3 + 2]);
                    float q = 7f / (7f + tz);
                    buf[t * 2] = ox + tx * k * q;
                    buf[t * 2 + 1] = oy - ty * k * q;
                }
                sink.poly(buf, 16, alpha(0xFFDDE2E8, 140), 0f);
                bladeAt(propAngle);
                poly(blade, 4, 1f, 0f, alpha(pal[GR], 190), 6f);
                bladeAt(propAngle + 1.5707964f);
                poly(blade, 4, 1f, 0f, alpha(pal[GR], 190), 6f);
                ell(0, 0, 1.3f, 0.16f, 0.16f, 0.14f, pal[BS]);
                break;
            }
            case 1:
            case 2: {
                float sx = i == 1 ? -1f : 1f;
                line(sx * 0.28f, -0.3f, -0.4f, sx * 0.6f, -0.66f, -0.5f, pal[BS], 10f);
                ell(sx * 0.6f, -0.66f, -0.5f, 0.2f, 0.2f, 0.2f, 0xFF1F2227);
                ell(sx * 0.62f, -0.66f, -0.5f, 0.07f, 0.07f, 0.07f, 0xFF8C939C);
                break;
            }
            case 3:
            case 4: {
                float sx = i == 3 ? -1f : 1f;
                poly(PROP_WING, 4, sx, -0.12f, pal[WS], 20f);
                poly(PROP_TIP, 4, sx, -0.12f, pal[AS], 20f);
                poly(PROP_WING, 4, sx, 0f, pal[W], 20f);
                poly(PROP_TIP, 4, sx, 0f, pal[A], 20f);
                break;
            }
            case 5:
                hull(0, 0, 0.1f, 0.58f, 0.52f, 1.15f, pal[BS], pal[B], pal[BL]);
                break;
            case 6:
                ell(0, 0.36f, 0.34f, 0.42f, 0.32f, 0.62f, pal[CS]);
                ell(0, 0.39f, 0.34f, 0.40f, 0.30f, 0.60f, pal[C]);
                ell(0, 0.5f, 0.33f, 0.13f, 0.07f, 0.28f, alpha(pal[CL], 150));
                break;
            case 7:
                ell(0, 0, -1.0f, 0.29f, 0.28f, 0.45f, pal[BS]);
                ell(0, 0.03f, -1.0f, 0.27f, 0.26f, 0.43f, pal[B]);
                break;
            case 8:
                slab(PROP_TAIL, 4, 1f, pal[WS], pal[W], 26f, 0.1f);
                break;
            default:
                poly(FIN_PROP_L, 4, 1f, 0f, pal[AS], 9f);
                poly(FIN_PROP_R, 4, 1f, 0f, pal[A], 9f);
                break;
        }
    }

    // ---- shared pieces for the ordered planes ----

    /**
     * A smooth tube through a chain of balls (x, y, z, r per ball): each ball is a circle on screen and neighbouring
     * circles are joined by their outer tangent lines, so the tube has no seams and no gaps.
     */
    private void tube(float[] ch, int n, float dy, float rScale, int col) {
        float px = 0, py = 0, pr = 0;
        for (int i = 0; i < n; i++) {
            proj(ch[i * 4], ch[i * 4 + 1] + dy, ch[i * 4 + 2]);
            float x = cen[0], y = cen[1], r = ch[i * 4 + 3] * rScale * k * cen[2];
            sink.ellipse(x, y, r, r, 0f, col);
            if (i > 0) joint(px, py, pr, x, y, r, col);
            px = x;
            py = y;
            pr = r;
        }
    }

    private void joint(float x1, float y1, float r1, float x2, float y2, float r2, int col) {
        float dx = x2 - x1, dy = y2 - y1, d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-3f || d <= Math.abs(r1 - r2)) return;
        float ux = dx / d, uy = dy / d, nx = -uy, ny = ux;
        float sn = (r1 - r2) / d, cs = (float) Math.sqrt(Math.max(0f, 1f - sn * sn));
        float ax = ux * sn + nx * cs, ay = uy * sn + ny * cs;
        float bx = ux * sn - nx * cs, by = uy * sn - ny * cs;
        buf[0] = x1 + r1 * ax;
        buf[1] = y1 + r1 * ay;
        buf[2] = x2 + r2 * ax;
        buf[3] = y2 + r2 * ay;
        buf[4] = x2 + r2 * bx;
        buf[5] = y2 + r2 * by;
        buf[6] = x1 + r1 * bx;
        buf[7] = y1 + r1 * by;
        sink.poly(buf, 4, col, 1.5f);
    }

    /** A shaded tube: dark underside, main colour a little higher, a soft light stripe along the top. */
    private void body(float[] ch, int n, int shadeCol, int main, int lightCol) {
        tube(ch, n, 0f, 1f, shadeCol);
        tube(ch, n, 0.04f, 0.93f, main);
        int m = n - 2;
        for (int i = 0; i < m * 4; i += 4) {
            light4[i] = ch[i + 4];
            light4[i + 1] = ch[i + 5] + ch[i + 7] * 0.55f;
            light4[i + 2] = ch[i + 6];
            light4[i + 3] = ch[i + 7] * 0.2f;
        }
        tube(light4, m, 0f, 1f, alpha(lightCol, 120));
    }

    private final float[] light4 = new float[40];

    /** A flat slab seen from above (top colour on a darker edge) or from below (darker underside). */
    private void wing(float[] pts, int n, float sx, boolean above, int top, int under, float round, float thick) {
        if (above) {
            poly(pts, n, sx, -thick, shade(top, 0.8f), round);
            poly(pts, n, sx, 0f, top, round);
        } else {
            poly(pts, n, sx, 0f, shade(top, 0.8f), round);
            poly(pts, n, sx, -thick, under, round);
        }
    }

    /**
     * An upright fin (4 points at x = 0: base front, top front, top back, base back). It is thicker at the base, the
     * side facing the camera is drawn last and the back edge shows as a strip when seen from behind.
     */
    private void fin(float[] pts, float baseHalf, float topHalf, int main, int edge, float round) {
        float s = faces(1f, 0f, 0f) ? 1f : -1f;
        side(pts, -s, baseHalf, topHalf);
        poly(tmp, 4, 1f, 0f, edge, round);
        if (faces(0f, 0f, -1f)) {
            // back edge: base back and top back points, both sides
            for (int i = 0; i < 12; i++) tmp[i] = 0f;
            putFin(tmp, 0, pts, 3, -baseHalf);
            putFin(tmp, 1, pts, 3, baseHalf);
            putFin(tmp, 2, pts, 2, topHalf);
            putFin(tmp, 3, pts, 2, -topHalf);
            poly(tmp, 4, 1f, 0f, edge, round * 0.6f);
        }
        side(pts, s, baseHalf, topHalf);
        poly(tmp, 4, 1f, 0f, main, round);
    }

    private static void putFin(float[] out, int i, float[] pts, int j, float x) {
        out[i * 3] = x;
        out[i * 3 + 1] = pts[j * 3 + 1];
        out[i * 3 + 2] = pts[j * 3 + 2];
    }

    private void side(float[] pts, float s, float baseHalf, float topHalf) {
        putFin(tmp, 0, pts, 0, s * baseHalf);
        putFin(tmp, 1, pts, 1, s * topHalf);
        putFin(tmp, 2, pts, 2, s * topHalf);
        putFin(tmp, 3, pts, 3, s * baseHalf);
    }

    private final float[] tmp = new float[96];

    /** A disc seen from behind (an engine exhaust), only when the back of the plane faces the camera. */
    private void exhaust(float x, float y, float z, float r, int ring, int hole) {
        if (!faces(0f, 0f, -1f)) return;
        ell(x, y, z, r, r, 0.02f, ring);
        ell(x, y, z - 0.01f, r * 0.68f, r * 0.68f, 0.02f, hole);
    }

    // ---- the airliner (737 style): low swept wings, engines hung under them, upswept tail ----

    private static final float[] LB = {
            0f, -0.02f, 2.5f, 0.06f,
            0f, 0f, 2.25f, 0.32f,
            0f, 0f, 1.75f, 0.5f,
            0f, 0f, -1.1f, 0.5f,
            0f, 0.1f, -1.9f, 0.32f,
            0f, 0.24f, -2.55f, 0.1f};
    private static final float[] LW = {
            0.3f, -0.22f, 0.65f, 2.25f, 0f, -0.78f, 2.3f, 0f, -1.02f, 1.05f, -0.14f, -0.72f, 0.3f, -0.22f, -0.6f};
    private static final float[] LWL = {2.25f, 0f, -0.78f, 2.3f, 0f, -1.02f, 2.42f, 0.5f, -1.22f, 2.37f, 0.5f, -1.07f};
    private static final float[] LN = {
            0.95f, -0.42f, 0.85f, 0.2f,
            0.95f, -0.42f, 0.55f, 0.23f,
            0.95f, -0.42f, -0.05f, 0.2f,
            0.95f, -0.41f, -0.22f, 0.15f};
    private static final float[] LP = {0.95f, -0.3f, 0.45f, 0.95f, -0.16f, 0.35f, 0.95f, -0.16f, -0.4f, 0.95f, -0.3f, -0.3f};
    private static final float[] LT = {0.08f, 0.15f, -1.85f, 1.05f, 0.25f, -2.3f, 1.05f, 0.25f, -2.52f, 0.08f, 0.15f, -2.45f};
    private static final float[] LF = {0f, 0.3f, -1.45f, 0f, 1.35f, -2.15f, 0f, 1.35f, -2.45f, 0f, 0.25f, -2.52f};
    private final float[] nac = new float[16];

    private void liner() {
        boolean above = faces(0f, 1f, 0f);
        float far = faces(1f, 0f, 0f) ? -1f : 1f;
        if (above) {
            linerWing(far, true);
            linerWing(-far, true);
            linerBody();
            wing(LT, 4, far, true, pal[W], shade(pal[W], 0.88f), 12f, 0.06f);
            wing(LT, 4, -far, true, pal[W], shade(pal[W], 0.88f), 12f, 0.06f);
            fin(LF, 0.1f, 0.035f, pal[A], pal[AS], 8f);
        } else {
            fin(LF, 0.1f, 0.035f, pal[A], pal[AS], 8f);
            wing(LT, 4, far, false, pal[W], shade(pal[W], 0.88f), 12f, 0.06f);
            wing(LT, 4, -far, false, pal[W], shade(pal[W], 0.88f), 12f, 0.06f);
            linerBody();
            linerWing(far, false);
            linerWing(-far, false);
        }
    }

    private void linerWing(float sx, boolean above) {
        if (above) {
            nacelle(sx);
            poly(LP, 4, sx, 0f, pal[WS], 6f);
            wing(LW, 5, sx, true, pal[W], shade(pal[W], 0.88f), 14f, 0.07f);
            poly(LWL, 4, sx, 0f, pal[A], 8f);
        } else {
            poly(LWL, 4, sx, 0f, pal[AS], 8f);
            wing(LW, 5, sx, false, pal[W], shade(pal[W], 0.88f), 14f, 0.07f);
            poly(LP, 4, sx, 0f, pal[WS], 6f);
            nacelle(sx);
        }
    }

    private void nacelle(float sx) {
        for (int i = 0; i < 16; i++) nac[i] = LN[i];
        for (int i = 0; i < 4; i++) nac[i * 4] *= sx;
        body(nac, 4, pal[BS], pal[B], pal[BL]);
        exhaust(sx * 0.95f, -0.41f, -0.24f, 0.13f, 0xFF9AA6B2, pal[D]);
    }

    private void linerBody() {
        body(LB, 6, pal[BS], pal[B], pal[BL]);
        int win = alpha(pal[C], 235);
        for (int side = -1; side <= 1; side += 2) {
            if (!faces(side, 0.3f, 0f)) continue;
            for (int i = 0; i < 12; i++) ell(side * 0.47f, 0.13f, 1.45f - i * 0.22f, 0.025f, 0.055f, 0.065f, win);
        }
        ell(0f, 0.22f, 2.05f, 0.24f, 0.09f, 0.13f, pal[C]);
    }

    // ---- the jet: a blended teal delta wing under a long cream body, twin engine pods, one fin ----

    private static final float[] JW = new float[21 * 3];
    private static final float[] JLE = new float[6 * 3];
    private static final float[] JTIP = new float[4 * 3];
    private static final float[] JB = {
            0f, 0.1f, 2.7f, 0.03f,
            0f, 0.12f, 2.25f, 0.16f,
            0f, 0.14f, 1.5f, 0.27f,
            0f, 0.14f, 0f, 0.3f,
            0f, 0.16f, -1.2f, 0.25f,
            0f, 0.22f, -1.75f, 0.1f};
    private static final float[] JFAIR = {0f, 0.02f, 1.1f, 0.2f, 0f, 0.02f, -1.35f, 0.18f};
    private static final float[] JF = {0f, 0.3f, -0.45f, 0f, 1.25f, -1.35f, 0f, 1.25f, -1.65f, 0f, 0.28f, -1.72f};
    private final float[] pod = new float[8];

    private static float bez(float a, float c, float b, float t) {
        float u = 1f - t;
        return u * u * a + 2f * u * t * c + t * t * b;
    }

    private static void jetPoint(float[] out, int i, float x, float z) {
        out[i * 3] = x;
        out[i * 3 + 1] = 0.02f + 0.04f * Math.abs(x) / 2.4f;
        out[i * 3 + 2] = z;
    }

    static {
        // right half: leading edge (curved), the tip, the trailing edge curving back in to a point at the tail
        float[] hx = new float[11], hz = new float[11];
        for (int i = 0; i < 6; i++) {
            float t = i / 5f;
            hx[i] = bez(0.22f, 0.55f, 2.4f, t);
            hz[i] = bez(1.25f, -0.1f, -0.95f, t);
        }
        hx[6] = 2.2f;
        hz[6] = -1.18f;
        for (int i = 1; i <= 4; i++) {
            float t = i / 4f;
            hx[6 + i] = bez(2.2f, 1.1f, 0f, t);
            hz[6 + i] = bez(-1.18f, -0.75f, -1.6f, t);
        }
        int n = 0;
        for (int i = 0; i < 11; i++) jetPoint(JW, n++, hx[i], hz[i]);
        for (int i = 9; i >= 0; i--) jetPoint(JW, n++, -hx[i], hz[i]);
        // a lighter band along the leading edge
        for (int i = 0; i < 3; i++) {
            float t = 0.15f + i * 0.3f;
            jetPoint(JLE, i, bez(0.22f, 0.55f, 2.4f, t), bez(1.25f, -0.1f, -0.95f, t));
            jetPoint(JLE, 5 - i, bez(0.22f, 0.55f, 2.4f, t) - 0.05f, bez(1.25f, -0.1f, -0.95f, t) - 0.28f);
        }
        // orange tip
        jetPoint(JTIP, 0, bez(0.22f, 0.55f, 2.4f, 0.86f), bez(1.25f, -0.1f, -0.95f, 0.86f));
        jetPoint(JTIP, 1, 2.4f, -0.95f);
        jetPoint(JTIP, 2, 2.2f, -1.18f);
        jetPoint(JTIP, 3, bez(2.2f, 1.1f, 0f, 0.14f), bez(-1.18f, -0.75f, -1.6f, 0.14f));
    }

    private void jet() {
        boolean above = faces(0f, 1f, 0f);
        float far = faces(1f, 0f, 0f) ? -1f : 1f;
        if (above) {
            jetPods(far);
            jetPods(-far);
            jetWing(true);
            jetBody();
            fin(JF, 0.09f, 0.03f, pal[A], pal[AS], 8f);
        } else {
            fin(JF, 0.09f, 0.03f, pal[A], pal[AS], 8f);
            jetBody();
            jetWing(false);
            tube(JFAIR, 2, 0f, 1f, shade(pal[W], 0.86f));
            jetPods(far);
            jetPods(-far);
        }
    }

    private void jetWing(boolean above) {
        wing(JW, 21, 1f, above, pal[W], shade(pal[W], 0.88f), 10f, 0.08f);
        if (above) {
            int band = alpha(light(pal[W], 0.25f), 200);
            poly(JLE, 6, 1f, 0f, band, 6f);
            poly(JLE, 6, -1f, 0f, band, 6f);
        }
        float dy = above ? 0f : -0.08f;
        poly(JTIP, 4, 1f, dy, pal[A], 8f);
        poly(JTIP, 4, -1f, dy, pal[A], 8f);
    }

    private void jetPods(float sx) {
        for (int p = 0; p < 2; p++) {
            float x = sx * (p == 0 ? 0.74f : 0.98f);
            pod[0] = x;
            pod[1] = -0.13f;
            pod[2] = -0.35f;
            pod[3] = 0.14f;
            pod[4] = x;
            pod[5] = -0.13f;
            pod[6] = -1.15f;
            pod[7] = 0.15f;
            tube(pod, 2, 0f, 1f, pal[BS]);
            tube(pod, 2, 0.02f, 0.92f, pal[B]);
        }
        exhaust(sx * 0.74f, -0.13f, -1.2f, 0.11f, pal[BS], pal[D]);
        exhaust(sx * 0.98f, -0.13f, -1.2f, 0.11f, pal[BS], pal[D]);
    }

    private void jetBody() {
        body(JB, 6, pal[BS], pal[B], light(pal[B], 0.6f));
        int win = alpha(pal[C], 235);
        for (int side = -1; side <= 1; side += 2) {
            if (!faces(side, 0.3f, 0f)) continue;
            for (int i = 0; i < 12; i++) ell(side * 0.27f, 0.22f, 1.65f - i * 0.2f, 0.018f, 0.035f, 0.045f, win);
        }
        ell(0f, 0.3f, 2.0f, 0.13f, 0.05f, 0.14f, pal[C]);
    }

    // ---- the spaceship ----
    private void ship(int i) {
        switch (i) {
            case 0:
            case 1: {
                float sx = i == 0 ? -1f : 1f;
                poly(SHIP_WING, 4, sx, -0.1f, pal[BS], 14f);
                poly(SHIP_WING, 4, sx, 0f, pal[B], 14f);
                poly(SHIP_PANEL, 4, sx, 0f, pal[W], 8f);
                poly(SHIP_TIP, 3, sx, 0f, pal[A], 12f);
                break;
            }
            case 2:
            case 3: {
                float sx = i == 2 ? -1f : 1f;
                float x = sx * 0.62f;
                hull(x, 0, -0.95f, 0.3f, 0.3f, 0.62f, pal[BS], pal[B], pal[BL]);
                ell(x, 0, -0.85f, 0.27f, 0.27f, 0.08f, pal[W]);
                ell(x, 0, -1.5f, 0.25f, 0.25f, 0.04f, pal[D]);
                ell(x, 0, -1.54f, 0.18f, 0.18f, 0.04f, pal[G]);
                ell(x, 0, -1.58f, 0.09f, 0.09f, 0.03f, light(pal[G], 0.7f));
                break;
            }
            case 4:
                hull(0, 0.1f, 0.25f, 0.42f, 0.34f, 1.55f, pal[BS], pal[B], pal[BL]);
                ell(0, 0.1f, 1.7f, 0.12f, 0.12f, 0.6f, pal[B]);
                ell(0, 0.1f, 1.4f, 0.17f, 0.17f, 0.05f, pal[A]);
                ell(0, 0.36f, 0.62f, 0.28f, 0.24f, 0.55f, pal[CS]);
                ell(0, 0.39f, 0.62f, 0.26f, 0.22f, 0.52f, pal[C]);
                ell(0, 0.5f, 0.6f, 0.08f, 0.07f, 0.25f, alpha(pal[CL], 150));
                break;
            case 5:
            case 6: {
                float sx = i == 5 ? -1f : 1f;
                slab(SHIP_FIN, 4, sx, pal[AS], pal[A], 6f, 0.06f);
                break;
            }
            default:
                slab(SHIP_BLOCK, 4, 1f, pal[AS], pal[A], 10f, 0.05f);
                break;
        }
    }
}
