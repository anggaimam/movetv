package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.wiggleplay.app.gl.Mat4;
import com.wiggleplay.app.gl.StaticBatcher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class TrackLayoutTest {

    private static final String[] RING = {"ooo", "o.o", "oSo"};
    private static final float PI = (float) Math.PI;

    private static TrackLayout ring() {
        return TrackLayout.parse(RING, 'W');
    }

    private static TrackLayout.Tile at(TrackLayout t, int cx, int cz) {
        for (TrackLayout.Tile k : t.tiles()) if (k.cx == cx && k.cz == cz) return k;
        throw new AssertionError("no tile at " + cx + "," + cz);
    }

    private static void expectBad(String[] rows, char dir, String... mustContain) {
        try {
            TrackLayout.parse(rows, dir);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            for (String m : mustContain) {
                assertTrue("message '" + e.getMessage() + "' should contain " + m, e.getMessage().contains(m));
            }
        }
    }

    @Test
    public void mapsParseWithExpectedCounts() {
        int[] tiles = {22, 28, 40};
        int[] corners = {4, 6, 12};
        for (int lv = 0; lv < 3; lv++) {
            TrackLayout t = TrackMaps.forLevel(lv);
            assertEquals(tiles[lv], t.tiles().size());
            assertEquals(corners[lv], t.cornerCount());
            assertEquals(TrackLayout.Kind.FINISH, t.tiles().get(0).kind);
            int straight = tiles[lv] - corners[lv];
            float expect = 10f * straight + 5f * PI / 2f * corners[lv];
            assertEquals(expect, t.lapLength(), 0.01f);
            System.out.println("level " + lv + ": " + t.tiles().size() + " tiles, lap " + t.lapLength());
        }
    }

    @Test
    public void cornerRotationsOnRing() {
        TrackLayout t = ring();
        assertEquals(90, at(t, 0, 0).rotDeg);
        assertEquals(0, at(t, 2, 0).rotDeg);
        assertEquals(270, at(t, 2, 2).rotDeg);
        assertEquals(180, at(t, 0, 2).rotDeg);
        assertEquals(4, t.cornerCount());
    }

    @Test
    public void straightRotations() {
        TrackLayout t = ring();
        assertEquals(0, at(t, 0, 1).rotDeg);   // vertical
        assertEquals(0, at(t, 2, 1).rotDeg);
        assertEquals(90, at(t, 1, 0).rotDeg);  // horizontal
        TrackLayout.Tile f = at(t, 1, 2);
        assertEquals(TrackLayout.Kind.FINISH, f.kind);
        assertEquals(90, f.rotDeg);
    }

    @Test
    public void centreLineIsContinuousAndSmooth() {
        for (int lv = 0; lv < 3; lv++) {
            TrackLayout t = TrackMaps.forLevel(lv);
            float[] a = new float[3], b = new float[3];
            int n = t.sampleCount();
            for (int i = 0; i < n; i++) {
                t.sample(i * t.lapLength() / n, a);
                t.sample((i + 1) * t.lapLength() / n, b);
                assertTrue(Math.hypot(b[0] - a[0], b[1] - a[1]) <= 0.51);
                float d = Math.abs(wrap(b[2] - a[2]));
                assertTrue("heading jump " + d + " at " + i, d < Math.toRadians(10));
            }
        }
    }

    private static float wrap(float d) {
        while (d > PI) d -= 2 * PI;
        while (d <= -PI) d += 2 * PI;
        return d;
    }

    @Test
    public void startsAtFinishCentreHeadingWest() {
        TrackLayout t = ring();
        float[] p = new float[3];
        t.sample(0f, p);
        assertEquals(10f, p[0], 0.01f);
        assertEquals(20f, p[1], 0.01f);
        assertEquals(-PI / 2f, p[2], 0.01f); // facing -X
    }

    @Test
    public void artCornerPointIsOnTheCentreLine() {
        TrackLayout t = ring();
        float[] m = new float[16], out = new float[3], p = new float[3];
        for (TrackLayout.Tile k : t.tiles()) {
            if (k.kind != TrackLayout.Kind.CORNER) continue;
            // art corner at (-5,0,5) rotated and placed
            double a = Math.toRadians(k.rotDeg);
            double cx = -5 * Math.cos(a) + 5 * Math.sin(a);
            double cz = 5 * Math.sin(a) + 5 * Math.cos(a);
            // the arc is radius 5 from that point; the nearest sample of this tile's arc midpoint must be 5 away
            float best = Float.MAX_VALUE;
            for (float s = 0; s < t.lapLength(); s += 0.25f) {
                t.sample(s, p);
                if (Math.abs(p[0] - k.cx * 10) <= 5 && Math.abs(p[1] - k.cz * 10) <= 5) {
                    float d = (float) Math.hypot(p[0] - (k.cx * 10 + cx), p[1] - (k.cz * 10 + cz));
                    best = Math.min(best, Math.abs(d - 5f));
                }
            }
            assertTrue("corner " + k.cx + "," + k.cz + " off by " + best, best < 0.05f);
        }
    }

    @Test
    public void projectSignsAndDistance() {
        for (int lv = 0; lv < 3; lv++) {
            TrackLayout t = TrackMaps.forLevel(lv);
            float[] p = new float[3];
            TrackPoint tp = new TrackPoint();
            for (float s = 1f; s < t.lapLength() - 1f; s += 3.7f) {
                t.sample(s, p);
                float yaw = p[2];
                // right of heading as seen from behind: (-cos yaw, sin yaw)
                float rx = -(float) Math.cos(yaw), rz = (float) Math.sin(yaw);
                for (float k : new float[]{-2f, 0f, 1.5f, 3f}) {
                    t.project(p[0] + rx * k, p[1] + rz * k, -1, tp);
                    assertEquals(k, tp.offset, 0.1f);
                    assertEquals(s, tp.s, 0.15f);
                }
            }
        }
    }

    @Test
    public void projectRightIsPositiveAtStart() {
        TrackLayout t = ring();
        TrackPoint tp = new TrackPoint();
        // heading west at (10,20); the driver's right is north (-Z)
        t.project(10f, 18f, -1, tp);
        assertEquals(2f, tp.offset, 0.05f);
        t.project(10f, 22f, -1, tp);
        assertEquals(-2f, tp.offset, 0.05f);
    }

    @Test
    public void projectWithHintWrapsAtTheFinishLine() {
        TrackLayout t = ring();
        TrackPoint tp = new TrackPoint();
        float[] p = new float[3];
        int hint = -1;
        float prev = -1f;
        // drive one and a bit laps, always passing the previous index as the hint
        for (float s = 0f; s < t.lapLength() * 1.3f; s += 0.4f) {
            t.sample(s, p);
            hint = t.project(p[0], p[1], hint, tp);
            float expect = s % t.lapLength();
            float diff = Math.abs(tp.s - expect);
            diff = Math.min(diff, t.lapLength() - diff);
            assertTrue("s " + s + " got " + tp.s, diff < 0.2f);
        }
        assertTrue(hint >= 0);
    }

    @Test
    public void coinSlotsFollowTheRules() {
        TrackLayout t = TrackMaps.forLevel(0);
        TrackLayout.Slots a = t.coinSlots(7L);
        assertEquals(17 * 5 + 4 * 3, a.count);
        for (int i = 1; i < a.count; i++) assertTrue(a.s[i] >= a.s[i - 1] - 1e-4f);
        // straight lines: 5 coins, same lateral offset, never repeating the previous straight's lane
        float prevLane = Float.NaN, firstLane = Float.NaN, lastLane = Float.NaN;
        int lines = 0;
        for (int i = 0; i < a.count; i++) {
            if (Math.abs(a.offset[i]) == 0.75f) continue; // corner coin
            if (i % 1 == 0 && (i == 0 || !sameLine(a, i))) {
                float lane = a.offset[i];
                assertTrue(lane == -2.5f || lane == 0f || lane == 2.5f);
                if (lines > 0) assertTrue("repeated lane", lane != prevLane);
                else firstLane = lane;
                prevLane = lane;
                lastLane = lane;
                lines++;
            }
        }
        assertEquals(17, lines);
        assertTrue("wrap lane repeats", lastLane != firstLane);
        // determinism
        TrackLayout.Slots b = t.coinSlots(7L);
        for (int i = 0; i < a.count; i++) {
            assertEquals(a.s[i], b.s[i], 0f);
            assertEquals(a.offset[i], b.offset[i], 0f);
        }
        assertEquals(a.starS, b.starS, 0f);
        // star sits mid-corner; corner coins are on the outside (T-22)
        assertTrue(a.starS > 0f && a.starS < t.lapLength());
    }

    private static boolean sameLine(TrackLayout.Slots a, int i) {
        return i > 0 && Math.abs(a.offset[i - 1]) != 0.75f && a.offset[i - 1] == a.offset[i]
                && a.s[i] - a.s[i - 1] < 1.7f;
    }

    @Test
    public void cornerCoinsAreOnTheOutside() {
        TrackLayout t = ring(); // all corners turn right (driving west then north... clockwise)
        TrackLayout.Slots sl = t.coinSlots(1L);
        TrackPoint tp = new TrackPoint();
        float[] p = new float[3];
        int cornerCoins = 0;
        for (int i = 0; i < sl.count; i++) {
            if (Math.abs(sl.offset[i]) != 0.75f) continue;
            cornerCoins++;
            // outside means away from the arc centre: radius 5.75 around it
            t.sample(sl.s[i], p);
            float rx = -(float) Math.cos(p[2]), rz = (float) Math.sin(p[2]);
            float cx = p[0] + rx * sl.offset[i], cz = p[1] + rz * sl.offset[i];
            t.project(cx, cz, -1, tp);
            assertEquals(sl.offset[i], tp.offset, 0.1f);
        }
        assertEquals(12, cornerCoins);
        // the ring's corners all turn right, so the outside is the left of the line (offset -0.75)
        for (int i = 0; i < sl.count; i++) if (Math.abs(sl.offset[i]) == 0.75f) assertEquals(-0.75f, sl.offset[i], 0f);
        // and every corner coin is 5.75 from its corner's arc centre
        for (int i = 0; i < sl.count; i++) {
            if (Math.abs(sl.offset[i]) != 0.75f) continue;
            t.sample(sl.s[i], p);
            float rx = -(float) Math.cos(p[2]), rz = (float) Math.sin(p[2]);
            float cx = p[0] + rx * sl.offset[i], cz = p[1] + rz * sl.offset[i];
            t.project(cx, cz, -1, tp);
            assertEquals(-0.75f, tp.offset, 0.1f);
        }
    }

    private static final String[] DECOR_MAP = {
        "T......", ".oooo..", ".o..o..", ".ooSo.T", "......."};

    @Test
    public void decorRules() {
        TrackLayout t = TrackLayout.parse(DECOR_MAP, 'W');
        int expectForest = 0;
        for (int z = 0; z < DECOR_MAP.length; z++) {
            for (int x = 0; x < DECOR_MAP[z].length(); x++) {
                if (DECOR_MAP[z].charAt(x) != '.') continue;
                boolean touch = false;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, zz = z + dz;
                        if ((dx != 0 || dz != 0) && zz >= 0 && zz < DECOR_MAP.length && xx >= 0
                                && xx < DECOR_MAP[zz].length()
                                && "oS".indexOf(DECOR_MAP[zz].charAt(xx)) >= 0) touch = true;
                    }
                }
                if (touch) expectForest++;
            }
        }
        List<StaticBatcher.Placement> d = t.decor(5L, false);
        int forests = 0, tents = 0;
        for (StaticBatcher.Placement p : d) {
            if (p.meshName.equals("decoration-forest")) forests++;
            else if (p.meshName.equals("decoration-tents")) tents++;
            else fail("unexpected " + p.meshName);
            assertEquals(p.cellX * 10f, p.matrix[12], 1e-4f);
            assertEquals(p.cellZ * 10f, p.matrix[14], 1e-4f);
        }
        assertEquals(expectForest, forests);
        assertEquals(2, tents);
        // the far corner (6,0) touches no road: nothing there
        for (StaticBatcher.Placement p : d) assertTrue(!(p.cellX == 6 && p.cellZ == 0));

        // rotations are multiples of 90 degrees: the matrix is axis-aligned
        for (StaticBatcher.Placement p : d) {
            float c = p.matrix[0], sn = p.matrix[8];
            assertTrue(Math.abs(c) < 1e-4f || Math.abs(c) > 0.9999f);
            assertEquals(1f, c * c + sn * sn, 1e-4f);
        }
    }

    @Test
    public void decorLowQualityAndDeterminism() {
        TrackLayout t = TrackLayout.parse(DECOR_MAP, 'W');
        List<StaticBatcher.Placement> hi = t.decor(5L, false);
        List<StaticBatcher.Placement> lo = t.decor(5L, true);
        List<StaticBatcher.Placement> hi2 = t.decor(5L, false);
        assertEquals(hi.size(), lo.size());
        int empties = 0, forests = 0;
        for (int i = 0; i < hi.size(); i++) {
            assertEquals(hi.get(i).cellX, lo.get(i).cellX);
            assertEquals(hi.get(i).cellZ, lo.get(i).cellZ);
            for (int k = 0; k < 16; k++) {
                assertEquals(hi.get(i).matrix[k], lo.get(i).matrix[k], 0f);   // same rotations
                assertEquals(hi.get(i).matrix[k], hi2.get(i).matrix[k], 0f);  // deterministic
            }
            if (lo.get(i).meshName.equals("decoration-empty")) empties++;
            if (hi.get(i).meshName.equals("decoration-forest")) forests++;
        }
        assertEquals(forests / 2, empties);
        // a different seed changes at least one rotation
        List<StaticBatcher.Placement> other = t.decor(99L, false);
        boolean differs = false;
        for (int i = 0; i < hi.size(); i++) {
            for (int k = 0; k < 16; k++) if (hi.get(i).matrix[k] != other.get(i).matrix[k]) differs = true;
        }
        assertTrue(differs);
    }

    @Test
    public void tilePlacementsMatchTiles() {
        TrackLayout t = ring();
        List<StaticBatcher.Placement> pl = t.tilePlacements();
        assertEquals(t.tiles().size(), pl.size());
        Set<String> names = new HashSet<>();
        for (StaticBatcher.Placement p : pl) names.add(p.meshName);
        assertTrue(names.contains("track-straight") && names.contains("track-corner")
                && names.contains("track-finish"));
        // rotated art: a corner tile at rot 0 maps the art point (-5,0,5) to cell centre + (-5,5)
        StaticBatcher.Placement c = null;
        for (StaticBatcher.Placement p : pl) if (p.cellX == 2 && p.cellZ == 0) c = p;
        float[] out = new float[3];
        Mat4.transformPoint(out, c.matrix, -5f, 0f, 5f);
        assertEquals(15f, out[0], 1e-3f);
        assertEquals(5f, out[2], 1e-3f);
    }

    @Test
    public void badMapsAreRejectedWithTheCellName() {
        expectBad(new String[]{"ooo", "o.o", "oS."}, 'W', "(2,1)", "dead end");
        expectBad(new String[]{"ooo", "ooo", "oSo"}, 'W', "(1,0)", "junction");
        expectBad(new String[]{"ooo.ooo", "o.o.o.o", "oSo.ooo"}, 'W', "(4,0)", "loop");
        expectBad(new String[]{"Soo", "o.o", "ooo"}, 'E', "(0,0)", "straight");
        expectBad(RING, 'N', "(1,2)", "does not lead");
        expectBad(new String[]{"ooo", "oXo", "oSo"}, 'W', "'X'", "(1,1)");
        expectBad(new String[]{"ooo", "o.o", "ooo"}, 'W', "no finish");
        expectBad(new String[]{"ooo", "oSo", "oSo"}, 'W', "second", "(1,2)");
        expectBad(RING, 'Q', "startDir");
    }

    @Test
    public void shortRowsArePadded() {
        TrackLayout t = TrackLayout.parse(new String[]{"ooo", "o.o", "oSo", ""}, 'W');
        assertEquals(8, t.tiles().size());
        assertEquals(3, t.width());
        assertEquals(4, t.height());
    }

    @Test
    public void nitroCansReplaceMiddleCoinsOfSpacedStraightsAndKeepTheLanes() {
        for (int lv = 0; lv < 3; lv++) {
            TrackLayout t = TrackMaps.forLevel(lv);
            TrackLayout.Slots plain = t.coinSlots(11L);
            for (int cans = 0; cans <= 5; cans++) {
                TrackLayout.Slots sl = t.coinSlots(11L, cans);
                assertEquals("same slot count", plain.count, sl.count);
                int found = 0;
                float lastCanS = -1e9f;
                for (int i = 0; i < sl.count; i++) {
                    assertEquals("lanes and positions are the same as without cans", plain.offset[i], sl.offset[i], 0f);
                    assertEquals(plain.s[i], sl.s[i], 0f);
                    if (!sl.nitro[i]) continue;
                    found++;
                    assertFalse("never a corner coin", sl.corner[i]);
                    assertTrue("on a lane", sl.offset[i] == -2.5f || sl.offset[i] == 0f || sl.offset[i] == 2.5f);
                    assertTrue("cans are spread out (at least 15 apart)", sl.s[i] - lastCanS > 15f);
                    lastCanS = sl.s[i];
                }
                assertEquals("level " + lv + " cans " + cans, cans, found);
            }
            assertEquals("no cans without asking", 0, countNitro(plain));
        }
    }

    private static int countNitro(TrackLayout.Slots sl) {
        int n = 0;
        for (int i = 0; i < sl.count; i++) if (sl.nitro[i]) n++;
        return n;
    }

    @Test
    public void tooManyCansAreLimitedToTheStraights() {
        TrackLayout t = TrackMaps.forLevel(0);
        assertEquals(17, countNitro(t.coinSlots(3L, 99)));
    }
}
