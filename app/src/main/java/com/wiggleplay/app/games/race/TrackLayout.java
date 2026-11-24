package com.wiggleplay.app.games.race;

import com.wiggleplay.app.gl.Mat4;
import com.wiggleplay.app.gl.StaticBatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A race track made from an ASCII map: which Kenney tile goes where (with its rotation), the scenery, a
 * smooth centre line (every ~0.5 units) that all race logic uses, and the coin positions. Plain Java (no
 * Android classes), tested on the JVM.
 *
 * <p>Coordinates: +Y up, map row = z, column = x, cell centre = (column * 10, 0, row * 10). Directions
 * are N = -Z, E = +X, S = +Z, W = -X. Yaw 0 = +Z; the position moves by (sin yaw, cos yaw), so turning right
 * lowers the yaw. {@code rotY(a)} maps (x, z) to (x cos a + z sin a, -x sin a + z cos a), as in {@link Mat4}.
 * Cells are written {@code (column,row)} in error messages.
 *
 * <p>Tile art at rotation 0: straight and finish run along Z (edges -Z and +Z); the corner joins the -X
 * and +Z edges along a quarter circle of radius 5 around the cell corner (-5, +5).
 */
public final class TrackLayout implements TrackPath {

    /** The three kinds of road tile. */
    public enum Kind { STRAIGHT, CORNER, FINISH }

    /** One road tile. {@code cx} and {@code cz} are the map column and row, not world units. */
    public static final class Tile {
        /** Map column. */
        public final int cx;
        /** Map row. */
        public final int cz;
        /** Which tile art. */
        public final Kind kind;
        /** Rotation about Y in degrees: 0, 90, 180 or 270. */
        public final int rotDeg;

        Tile(int cx, int cz, Kind kind, int rotDeg) {
            this.cx = cx;
            this.cz = cz;
            this.kind = kind;
            this.rotDeg = rotDeg;
        }
    }

    /** Size of one tile in world units. */
    public static final float CELL = 10f;
    private static final float ROAD_HALF_WIDTH = 4f;
    private static final float SAMPLE_STEP = 0.5f;
    private static final int SEARCH = 40;
    private static final float ARC_R = 5f;
    private static final float ARC_LEN = (float) (Math.PI / 2.0 * ARC_R);

    // directions: index 0 N (0,-1), 1 E (1,0), 2 S (0,1), 3 W (-1,0)
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};
    private static final String DIR_CHARS = "NESW";

    private final char[][] grid;
    private final int width;
    private final int height;
    private final List<Tile> tiles = new ArrayList<>();
    private final float[] tileS0;       // lap distance where each tile (in driving order) begins
    private final boolean[] tileRight;  // corner turns right
    private int corners;
    private float lapLength;
    private int sampleCount;
    private float step;
    private float[] sx, sz, syaw, fwdX, fwdZ;

    private TrackLayout(char[][] grid, int width, int height, int tileCount) {
        this.grid = grid;
        this.width = width;
        this.height = height;
        this.tileS0 = new float[tileCount];
        this.tileRight = new boolean[tileCount];
    }

    // ------------------------------------------------------------------ parsing

    /**
     * Parses a map.
     * @param rows the map rows (see {@link TrackMaps})
     * @param startDir the driving direction leaving {@code S}: 'N', 'E', 'S' or 'W'
     * @return the layout
     * @throws IllegalArgumentException if the map is not one closed loop of road where every road cell has
     *         exactly two road neighbours, {@code S} is not on a straight run, or {@code startDir} does not
     *         lead along the road; the message names the cell
     */
    public static TrackLayout parse(String[] rows, char startDir) {
        int h = rows.length;
        int w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        char[][] g = new char[h][w];
        int roadCount = 0, sCol = -1, sRow = -1;
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                char c = x < rows[z].length() ? rows[z].charAt(x) : ' ';
                if ("oS.T ".indexOf(c) < 0) {
                    throw new IllegalArgumentException("Unknown map character '" + c + "' at cell " + cell(x, z));
                }
                g[z][x] = c;
                if (c == 'o' || c == 'S') roadCount++;
                if (c == 'S') {
                    if (sCol >= 0) {
                        throw new IllegalArgumentException("A second finish line S at cell " + cell(x, z)
                                + " (the first is at " + cell(sCol, sRow) + ")");
                    }
                    sCol = x;
                    sRow = z;
                }
            }
        }
        if (sCol < 0) throw new IllegalArgumentException("The map has no finish line S");

        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                if (!isRoad(g, x, z)) continue;
                int n = 0;
                for (int d = 0; d < 4; d++) if (isRoad(g, x + DX[d], z + DZ[d])) n++;
                if (n != 2) {
                    throw new IllegalArgumentException("Road cell " + cell(x, z) + " has " + n
                            + " road neighbours (needs exactly 2)"
                            + (n < 2 ? ": dead end" : ": junction"));
                }
            }
        }
        int sd = DIR_CHARS.indexOf(Character.toUpperCase(startDir));
        if (sd < 0) throw new IllegalArgumentException("startDir must be N, E, S or W: " + startDir);
        if (!isRoad(g, sCol + DX[sd], sRow + DZ[sd])) {
            throw new IllegalArgumentException("Start direction " + DIR_CHARS.charAt(sd) + " from the finish line "
                    + cell(sCol, sRow) + " does not lead to a road cell");
        }
        if (!isRoad(g, sCol - DX[sd], sRow - DZ[sd])) {
            throw new IllegalArgumentException("The finish line " + cell(sCol, sRow)
                    + " must be on a straight run (road on both sides), not on a corner");
        }

        // walk the loop from S in driving order
        int[] pathX = new int[roadCount];
        int[] pathZ = new int[roadCount];
        int[] heading = new int[roadCount]; // direction of travel when leaving the cell
        boolean[][] seen = new boolean[h][w];
        int n = 0, x = sCol, z = sRow, dir = sd;
        while (true) {
            pathX[n] = x;
            pathZ[n] = z;
            seen[z][x] = true;
            heading[n] = dir;
            n++;
            int nx = x + DX[dir], nz = z + DZ[dir];
            if (nx == sCol && nz == sRow) break;
            if (n >= roadCount) break; // cannot happen when every cell has two neighbours, but never loop forever
            // next heading: the road neighbour that is not the cell we came from
            int back = (dir + 2) % 4;
            int next = -1;
            for (int d = 0; d < 4; d++) {
                if (d != back && isRoad(g, nx + DX[d], nz + DZ[d])) next = d;
            }
            x = nx;
            z = nz;
            dir = next;
        }
        if (n != roadCount) {
            for (int zz = 0; zz < h; zz++) {
                for (int xx = 0; xx < w; xx++) {
                    if (isRoad(g, xx, zz) && !seen[zz][xx]) {
                        throw new IllegalArgumentException("The road is not one loop: cell " + cell(xx, zz)
                                + " is on a separate loop");
                    }
                }
            }
        }

        TrackLayout t = new TrackLayout(g, w, h, n);
        t.build(pathX, pathZ, heading, n);
        return t;
    }

    private static boolean isRoad(char[][] g, int x, int z) {
        return z >= 0 && z < g.length && x >= 0 && x < g[0].length && (g[z][x] == 'o' || g[z][x] == 'S');
    }

    private static String cell(int x, int z) {
        return "(" + x + "," + z + ")";
    }

    // ------------------------------------------------------------------ building

    /** Rotates a direction index by 90 degrees the way rotY does: N->W, E->N, S->E, W->S. */
    private static int rot90(int d) {
        return (d + 3) % 4;
    }

    private void build(int[] px, int[] pz, int[] heading, int n) {
        // tiles in driving order (index 0 is the finish line)
        for (int i = 0; i < n; i++) {
            int out = heading[i];
            int in = (heading[(i + n - 1) % n] + 2) % 4; // the edge we entered through
            Kind kind;
            int e1, e2; // base edges
            if (i == 0) {
                kind = Kind.FINISH;
                e1 = 0;
                e2 = 2;
            } else if (out == in || out == (in + 2) % 4) {
                kind = Kind.STRAIGHT;
                e1 = 0;
                e2 = 2;
            } else {
                kind = Kind.CORNER;
                e1 = 3;
                e2 = 2;
            }
            int rot = -1;
            int a = e1, b = e2;
            for (int k = 0; k < 4; k++) {
                if ((a == in && b == out) || (a == out && b == in)) {
                    rot = k * 90;
                    break;
                }
                a = rot90(a);
                b = rot90(b);
            }
            if (rot < 0) throw new IllegalStateException("No rotation fits cell " + cell(px[i], pz[i]));
            tiles.add(new Tile(px[i], pz[i], kind, rot));
            if (kind == Kind.CORNER) {
                corners++;
                // heading in is the opposite of the entry edge; turning right lowers the yaw
                tileRight[i] = angleDiffD(yawOf(out), yawOf((in + 2) % 4)) < 0;
            }
        }

        // centre line: segments in driving order, starting at the centre of the finish cell
        // segment list: [S exit half][tiles 1..n-1][S entry half]
        int segCount = n + 1;
        float[] segLen = new float[segCount];
        int[] segTile = new int[segCount];
        for (int k = 0; k < segCount; k++) {
            int ti = k == 0 ? 0 : (k == n ? 0 : k);
            segTile[k] = ti;
            if (k == 0 || k == n) segLen[k] = CELL / 2f;
            else segLen[k] = tiles.get(ti).kind == Kind.CORNER ? ARC_LEN : CELL;
        }
        float total = 0f;
        for (int k = 0; k < segCount; k++) {
            if (k >= 1 && k < n) tileS0[k] = total;
            total += segLen[k];
        }
        lapLength = total;
        sampleCount = (int) Math.ceil(total / SAMPLE_STEP);
        step = total / sampleCount;
        sx = new float[sampleCount];
        sz = new float[sampleCount];
        syaw = new float[sampleCount];
        fwdX = new float[sampleCount];
        fwdZ = new float[sampleCount];

        int seg = 0;
        float segStart = 0f;
        for (int i = 0; i < sampleCount; i++) {
            double s = i * (double) step;
            while (seg < segCount - 1 && s >= segStart + segLen[seg]) {
                segStart += segLen[seg];
                seg++;
            }
            double t = s - segStart; // distance into this segment
            int ti = segTile[seg];
            double cx = px[ti] * (double) CELL, cz = pz[ti] * (double) CELL;
            int out = heading[ti];
            int in = (heading[(ti + n - 1) % n] + 2) % 4;
            int travelIn = (in + 2) % 4; // direction of travel when entering the cell
            double x, z, yaw;
            if (tiles.get(ti).kind == Kind.CORNER) {
                // arc around the cell corner that touches both edges used
                double ccx = cx + (-DX[travelIn] + DX[out]) * (CELL / 2.0);
                double ccz = cz + (-DZ[travelIn] + DZ[out]) * (CELL / 2.0);
                double vinX = (cx - DX[travelIn] * (CELL / 2.0)) - ccx;
                double vinZ = (cz - DZ[travelIn] * (CELL / 2.0)) - ccz;
                double voutX = (cx + DX[out] * (CELL / 2.0)) - ccx;
                double voutZ = (cz + DZ[out] * (CELL / 2.0)) - ccz;
                double sigma = (vinX * voutZ - vinZ * voutX) > 0 ? 1.0 : -1.0;
                double a = Math.atan2(vinZ, vinX) + sigma * (t / ARC_R);
                x = ccx + ARC_R * Math.cos(a);
                z = ccz + ARC_R * Math.sin(a);
                double tx = -sigma * Math.sin(a), tz = sigma * Math.cos(a);
                yaw = Math.atan2(tx, tz);
            } else {
                // straight (also both halves of the finish cell): along the travel direction through the centre
                double startX, startZ;
                if (seg == n) { // entry half: from the entry edge to the centre
                    startX = cx - DX[travelIn] * (CELL / 2.0);
                    startZ = cz - DZ[travelIn] * (CELL / 2.0);
                } else if (seg == 0) { // exit half: from the centre to the exit edge
                    startX = cx;
                    startZ = cz;
                } else {
                    startX = cx - DX[travelIn] * (CELL / 2.0);
                    startZ = cz - DZ[travelIn] * (CELL / 2.0);
                }
                x = startX + DX[travelIn] * t;
                z = startZ + DZ[travelIn] * t;
                yaw = Math.atan2(DX[travelIn], DZ[travelIn]);
            }
            sx[i] = (float) x;
            sz[i] = (float) z;
            syaw[i] = (float) yaw;
            fwdX[i] = (float) Math.sin(yaw);
            fwdZ[i] = (float) Math.cos(yaw);
        }
    }

    /** Yaw of a travel direction index (N, E, S, W). */
    private static double yawOf(int d) {
        return Math.atan2(DX[d], DZ[d]);
    }

    private static double angleDiffD(double to, double from) {
        double d = to - from;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d <= -Math.PI) d += 2 * Math.PI;
        return d;
    }

    private static float angleDiff(float to, float from) {
        float d = to - from;
        while (d > (float) Math.PI) d -= 2f * (float) Math.PI;
        while (d <= -(float) Math.PI) d += 2f * (float) Math.PI;
        return d;
    }

    // ------------------------------------------------------------------ tiles and scenery

    /**
     * The road tiles in driving order, starting with the finish line.
     * @return the tiles (do not modify)
     */
    public List<Tile> tiles() {
        return tiles;
    }

    /**
     * Number of corner tiles.
     * @return the count
     */
    public int cornerCount() {
        return corners;
    }

    /**
     * Map width in cells.
     * @return columns
     */
    public int width() { return width; }

    /**
     * Map height in cells.
     * @return rows
     */
    public int height() { return height; }

    /**
     * The mesh name of a road tile.
     * @param k the kind
     * @return {@code track-straight}, {@code track-corner} or {@code track-finish}
     */
    public static String meshName(Kind k) {
        switch (k) {
            case CORNER:
                return "track-corner";
            case FINISH:
                return "track-finish";
            default:
                return "track-straight";
        }
    }

    private static float[] placementMatrix(int cellX, int cellZ, int rotDeg) {
        float[] t = new float[16], r = new float[16], m = new float[16];
        Mat4.translate(t, cellX * CELL, 0f, cellZ * CELL);
        Mat4.rotY(r, (float) Math.toRadians(rotDeg));
        Mat4.multiply(m, t, r);
        return m;
    }

    /**
     * Placements of the road tiles, ready for {@code StaticBatcher}.
     * @return one placement per tile
     */
    public List<StaticBatcher.Placement> tilePlacements() {
        List<StaticBatcher.Placement> out = new ArrayList<>(tiles.size());
        for (Tile t : tiles) {
            out.add(new StaticBatcher.Placement(meshName(t.kind), placementMatrix(t.cx, t.cz, t.rotDeg), t.cx, t.cz));
        }
        return out;
    }

    /**
     * Placements of the scenery. Decoration cells ({@code .}) that touch the road (8 neighbours) get
     * {@code decoration-forest} with a random rotation of 0, 90, 180 or 270 degrees; other decoration cells get
     * nothing (the ground covers them); {@code T} gets {@code decoration-tents}. In low quality every other forest
     * becomes {@code decoration-empty} (same rotations).
     * @param seed seeds the random rotations; the same seed gives the same scenery
     * @param lowQuality true to swap every other forest for an empty decoration
     * @return the placements, in map order
     */
    public List<StaticBatcher.Placement> decor(long seed, boolean lowQuality) {
        Random rnd = new Random(seed);
        List<StaticBatcher.Placement> out = new ArrayList<>();
        int forests = 0;
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                char c = grid[z][x];
                if (c == 'T') {
                    out.add(new StaticBatcher.Placement("decoration-tents", placementMatrix(x, z, 0), x, z));
                } else if (c == '.' && touchesRoad(x, z)) {
                    int rot = rnd.nextInt(4) * 90;
                    boolean empty = lowQuality && (forests % 2 == 1);
                    forests++;
                    out.add(new StaticBatcher.Placement(empty ? "decoration-empty" : "decoration-forest",
                            placementMatrix(x, z, rot), x, z));
                }
            }
        }
        return out;
    }

    private boolean touchesRoad(int x, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dz != 0) && isRoad(grid, x + dx, z + dz)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ centre line

    /**
     * Length of one lap along the centre line.
     * @return world units
     */
    public float lapLength() {
        return lapLength;
    }

    /**
     * Half the width of the drivable road.
     * @return 4.0
     */
    public float roadHalfWidth() {
        return ROAD_HALF_WIDTH;
    }

    /**
     * Number of centre line samples.
     * @return the count
     */
    public int sampleCount() {
        return sampleCount;
    }

    /**
     * A point on the centre line.
     * @param s distance along the lap from the finish line; any value, it wraps
     * @param outXZYaw receives x, z and the yaw (at least 3 floats)
     */
    public void sample(float s, float[] outXZYaw) {
        float w = s % lapLength;
        if (w < 0f) w += lapLength;
        float f = w / step;
        int i = (int) f;
        if (i >= sampleCount) i = sampleCount - 1;
        float t = f - i;
        int j = i + 1 == sampleCount ? 0 : i + 1;
        outXZYaw[0] = sx[i] + (sx[j] - sx[i]) * t;
        outXZYaw[1] = sz[i] + (sz[j] - sz[i]) * t;
        float yaw = syaw[i] + angleDiff(syaw[j], syaw[i]) * t;
        if (yaw > (float) Math.PI) yaw -= 2f * (float) Math.PI;
        else if (yaw <= -(float) Math.PI) yaw += 2f * (float) Math.PI;
        outXZYaw[2] = yaw;
    }

    /**
     * Finds where a point is relative to the centre line.
     * @param x world x
     * @param z world z
     * @param hint the sample index returned last time (searches 40 samples either side), or a negative number to search all
     * @param out receives distance along the lap, signed offset (positive = right of the driving direction) and road direction
     * @return the index of the nearest sample, to pass as the next hint
     */
    public int project(float x, float z, int hint, TrackPoint out) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        if (hint < 0) {
            for (int i = 0; i < sampleCount; i++) {
                float dx = x - sx[i], dz = z - sz[i];
                float d = dx * dx + dz * dz;
                if (d < bd) {
                    bd = d;
                    best = i;
                }
            }
        } else {
            for (int k = -SEARCH; k <= SEARCH; k++) {
                int i = Math.floorMod(hint + k, sampleCount);
                float dx = x - sx[i], dz = z - sz[i];
                float d = dx * dx + dz * dz;
                if (d < bd) {
                    bd = d;
                    best = i;
                }
            }
        }
        float dx = x - sx[best], dz = z - sz[best];
        float fx = fwdX[best], fz = fwdZ[best];
        float along = dx * fx + dz * fz;
        float s = best * step + along;
        s %= lapLength;
        if (s < 0f) s += lapLength;
        out.s = s;
        // the right-hand side of the heading (sin y, cos y) seen from behind is (-cos y, sin y) = (-fz, fx)
        out.offset = dx * (-fz) + dz * fx;
        out.tangentYaw = syaw[best];
        return best;
    }

    // ------------------------------------------------------------------ coins

    /**
     * Coin and star positions for one lap. Every straight tile (the finish tile has none) gets a line of 5 coins
     * 1.6 apart at a lateral offset of -2.5, 0 or +2.5 that is never the same as the previous straight tile's
     * (also not across the lap wrap); every corner gets 3 coins on the OUTSIDE line (offset 1.5 away from the corner
     * centre, radius 5.75, T-22 section 6). One golden star sits on the centre line at the apex of a random corner.
     * @param seed the random seed (the same seed gives the same slots)
     * @return the slots, sorted by distance along the lap
     */
    public Slots coinSlots(long seed) {
        return coinSlots(seed, 0);
    }

    /**
     * Coin and star positions, with nitro cans (T-26). A nitro can takes the place of the middle coin of a straight, in the
     * same lane, so a driver who follows the coins meets it; the cans are spread evenly over the straights, starting at a
     * place chosen from the seed (its own random numbers: the lanes of the coins are the same as without cans).
     * The slot count is the same as without cans.
     * @param seed layout seed
     * @param nitroCans cans per lap (at most one per straight)
     * @return the slots, sorted by distance along the lap
     */
    public Slots coinSlots(long seed, int nitroCans) {
        return coinSlots(seed, nitroCans, 0);
    }

    /**
     * Coin, star, nitro-can and magnet positions. With {@code magnets} = 1 the middle coin of one straight that has no
     * nitro can becomes a magnet (the straight is chosen from the seed with its own random numbers, so lanes, cans and the
     * slot count stay the same).
     * @param seed layout seed
     * @param nitroCans cans per lap (at most one per straight)
     * @param magnets 0 or 1 magnet per lap
     * @return the slots, sorted by distance along the lap
     */
    public Slots coinSlots(long seed, int nitroCans, int magnets) {
        Random rnd = new Random(seed);
        int n = tiles.size();
        int straights = 0, cornerTiles = 0;
        for (int i = 1; i < n; i++) {
            if (tiles.get(i).kind == Kind.STRAIGHT) straights++;
            else cornerTiles++;
        }
        float[] cs = new float[straights * 5 + cornerTiles * 3];
        float[] co = new float[cs.length];
        boolean[] cor = new boolean[cs.length];
        boolean[] nit = new boolean[cs.length];
        int cans = Math.max(0, Math.min(nitroCans, straights));
        float stride = cans > 0 ? (float) straights / cans : 0f;
        float first0 = cans > 0 ? new Random(seed ^ 0x9E3779B97F4A7C15L).nextFloat() * stride : 0f;
        int nextCan = 0, canStraight = cans > 0 ? (int) first0 : -1;
        boolean[] canAt = new boolean[Math.max(1, straights)];
        for (int q = 0; q < cans; q++) canAt[(int) (first0 + q * stride)] = true;
        int magStraight = -1;
        if (magnets > 0 && straights > 0) {
            int start = new Random(seed ^ 0x2545F4914F6CDD1DL).nextInt(straights);
            for (int q = 0; q < straights; q++) {
                int cand = (start + q) % straights;
                if (!canAt[cand]) { magStraight = cand; break; }
            }
        }
        boolean[] mag = new boolean[cs.length];
        int count = 0;
        final float[] lanes = {-2.5f, 0f, 2.5f};
        int prev = -1, first = -1, seenStraights = 0;
        List<Integer> cornerIdx = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            Tile t = tiles.get(i);
            float s0 = tileS0[i];
            if (t.kind == Kind.STRAIGHT) {
                seenStraights++;
                boolean last = seenStraights == straights;
                int pick;
                do {
                    pick = rnd.nextInt(3);
                } while (pick == prev || (last && straights > 1 && pick == first));
                if (first < 0) first = pick;
                prev = pick;
                boolean can = cans > 0 && nextCan < cans && seenStraights - 1 == canStraight;
                if (can) {
                    nextCan++;
                    canStraight = nextCan < cans ? (int) (first0 + nextCan * stride) : -1;
                }
                for (int k = 0; k < 5; k++) {
                    cs[count] = s0 + CELL / 2f + (k - 2) * 1.6f;
                    co[count] = lanes[pick];
                    nit[count] = can && k == 2;
                    mag[count] = seenStraights - 1 == magStraight && k == 2;
                    count++;
                }
            } else {
                cornerIdx.add(i);
                // T-22: on the OUTSIDE of the turn (radius 5.75, away from the corner centre), where a truck
                // that cannot turn tighter than radius ~6.8 naturally drives
                float outside = tileRight[i] ? -0.75f : 0.75f;
                for (int k = 1; k <= 3; k++) {
                    cs[count] = s0 + ARC_LEN * k / 4f;
                    co[count] = outside;
                    cor[count] = true;
                    count++;
                }
            }
        }
        float starS = 0f;
        if (!cornerIdx.isEmpty()) starS = tileS0[cornerIdx.get(rnd.nextInt(cornerIdx.size()))] + ARC_LEN / 2f;
        return new Slots(cs, co, cor, nit, mag, count, starS, 0f);
    }
}
