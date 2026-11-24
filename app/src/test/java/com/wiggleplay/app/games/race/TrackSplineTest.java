package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

public class TrackSplineTest {

    private static final float PI = (float) Math.PI;
    private static final float EASY_HALF = 5.5f;
    private static final float EASY_WALL = 5.35f;

    // the architect's table (architect-T28-track-check.py), in TrackDefs.ALL order: desert, city, rockies, space
    private static final float[] LAP = {434.2f, 445.9f, 426.5f, 440.4f};
    private static final float[] MIN_R = {17.7f, 13.6f, 13.4f, 14.2f};
    private static final int[] LEFT = {6, 5, 7, 6};
    private static final int[] RIGHT = {1, 3, 2, 1};
    private static final float[] CLEAR = {52f, 47f, 48f, 48f};

    private static TrackSpline build(int def, boolean reversed) {
        return TrackSpline.build(TrackDefs.ALL[def], reversed, EASY_HALF);
    }

    private static float wrap(float d) {
        while (d > PI) d -= 2 * PI;
        while (d <= -PI) d += 2 * PI;
        return d;
    }

    /** Distance between two laps positions, the short way round. */
    private static float lapGap(float a, float b, float lap) {
        float d = Math.abs(a - b);
        return Math.min(d, lap - d);
    }

    @Test
    public void tracksAreFourInMenuOrderAndKeysAndLookupWork() {
        String[] ids = {"desert", "city", "rockies", "space"};
        assertEquals(4, TrackDefs.ALL.length);
        for (int i = 0; i < 4; i++) {
            assertEquals(ids[i], TrackDefs.ALL[i].id);
            assertSame(TrackDefs.ALL[i], TrackDefs.byId(ids[i]));
            assertEquals(ids[i], TrackSpline.key(TrackDefs.ALL[i], false));
            assertEquals(ids[i] + "-r", TrackSpline.key(TrackDefs.ALL[i], true));
        }
        assertSame(TrackDefs.ALL[0], TrackDefs.byId(null));
        assertSame(TrackDefs.ALL[0], TrackDefs.byId("moon"));
    }

    @Test
    public void lapLengthAndTightestRadiusMatchTheTableInBothDirections() {
        for (int d = 0; d < 4; d++) {
            TrackSpline f = build(d, false), r = build(d, true);
            String id = TrackDefs.ALL[d].id;
            System.out.printf("%-8s lap expected %6.1f actual %6.2f (rev %6.2f) | min radius expected %4.1f actual %5.2f (rev %5.2f)%n",
                    id, LAP[d], f.lapLength(), r.lapLength(), MIN_R[d], f.minRadius(), r.minRadius());
            assertEquals(id + " lap", LAP[d], f.lapLength(), LAP[d] * 0.01f);
            assertEquals(id + " min radius", MIN_R[d], f.minRadius(), 0.7f);
            assertEquals(id + " reversed lap", f.lapLength(), r.lapLength(), f.lapLength() * 0.001f);
            assertEquals(id + " reversed min radius", f.minRadius(), r.minRadius(), f.minRadius() * 0.001f);
            assertTrue(id + " min radius " + f.minRadius(), f.minRadius() >= 12.5f);
            assertTrue(id + " reversed min radius " + r.minRadius(), r.minRadius() >= 12.5f);
            assertEquals(EASY_HALF, f.roadHalfWidth(), 0f);
        }
    }

    @Test
    public void samplesAreEvenlySpacedAndTheHeadingNeverJumps() {
        for (int d = 0; d < 4; d++) {
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = build(d, rev);
                int n = t.sampleCount();
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    double gap = Math.hypot(t.sampleX(j) - t.sampleX(i), t.sampleZ(j) - t.sampleZ(i));
                    assertTrue(TrackDefs.ALL[d].id + " gap " + gap + " at " + i, gap <= 0.51);
                    float jump = Math.abs(wrap(t.sampleYaw(j) - t.sampleYaw(i)));
                    assertTrue(TrackDefs.ALL[d].id + " yaw jump " + Math.toDegrees(jump) + " deg at " + i,
                            jump <= Math.toRadians(3));
                }
            }
        }
    }

    @Test
    public void sampleZeroIsControlPointZeroAndTheStartHeadsWestOrEast() {
        for (int d = 0; d < 4; d++) {
            TrackDef def = TrackDefs.ALL[d];
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = build(d, rev);
                assertEquals(def.control[0], t.sampleX(0), 1e-4f);
                assertEquals(def.control[1], t.sampleZ(0), 1e-4f);
                float[] p = new float[3];
                t.sample(0f, p);
                assertEquals(def.control[0], p[0], 1e-4f);
                assertEquals(def.control[1], p[1], 1e-4f);
                float want = rev ? PI / 2f : -PI / 2f;   // west is -X (yaw -90 deg), east +X (yaw +90 deg)
                System.out.printf("%-8s%s start heading %.1f deg%n", def.id, rev ? " (rev)" : "", Math.toDegrees(p[2]));
                assertEquals(def.id + (rev ? " reversed" : "") + " start heading " + Math.toDegrees(p[2]) + " deg",
                        want, p[2], Math.toRadians(6));
            }
        }
    }

    @Test
    public void theRoadIsStraightNearTheStartLine() {
        for (int d = 0; d < 4; d++) {
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = build(d, rev);
                int n = t.sampleCount();
                float ds = t.lapLength() / n;
                float min15 = tightestWithin(t, Math.round(15f / ds)), min30 = tightestWithin(t, Math.round(30f / ds));
                String id = TrackDefs.ALL[d].id + (rev ? " reversed" : "");
                System.out.printf("%-8s tightest radius within 15 m of the start line %.1f, within 30 m %.1f%n", id, min15, min30);
                assertTrue(id + " |radius| within 15 m: " + min15, min15 >= 100f);
                assertTrue(id + " |radius| within 30 m: " + min30, min30 >= 60f);
            }
        }
    }

    private static float tightestWithin(TrackSpline t, int steps) {
        int n = t.sampleCount();
        float min = Float.MAX_VALUE;
        for (int k = -steps; k <= steps; k++) min = Math.min(min, Math.abs(t.radiusAt(Math.floorMod(k, n))));
        return min;
    }

    @Test
    public void partsOfTheRoadMoreThanSixtyMetresApartStayTwentyFiveMetresApart() {
        for (int d = 0; d < 4; d++) {
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = build(d, rev);
                int n = t.sampleCount();
                float lap = t.lapLength(), ds = lap / n;
                double best = Double.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    for (int k = i + 1; k < n; k++) {
                        float sep = lapGap(i * ds, k * ds, lap);
                        if (sep <= 60f) continue;
                        double dist = Math.hypot(t.sampleX(i) - t.sampleX(k), t.sampleZ(i) - t.sampleZ(k));
                        best = Math.min(best, dist);
                    }
                }
                if (!rev) System.out.printf("%-8s closest approach expected %4.0f actual %5.1f%n", TrackDefs.ALL[d].id, CLEAR[d], best);
                assertTrue(TrackDefs.ALL[d].id + (rev ? " reversed" : "") + " closest approach " + best, best >= 25.0);
            }
        }
    }

    /** Counts the bends: regions with |radius| under 40 m that turn more than 20 degrees. Returns {left, right}. */
    private static int[] bends(TrackSpline t) {
        int m = t.sampleCount();
        boolean[] bend = new boolean[m];
        int st = -1;
        for (int i = 0; i < m; i++) {
            bend[i] = Math.abs(t.radiusAt(i)) < 40f;
            if (!bend[i] && st < 0) st = i;
        }
        int left = 0, right = 0, i0 = -1;
        for (int j = 0; j <= m; j++) {
            int i = (st + j) % m;
            if (j < m && bend[i] && i0 < 0) i0 = j;
            if ((j == m || !bend[i]) && i0 >= 0) {
                double turn = 0;
                for (int q = i0; q < j; q++) {
                    int a = (st + q) % m, b = (a + 1) % m;
                    turn += wrap(t.sampleYaw(b) - t.sampleYaw(a));
                }
                if (Math.abs(turn) > Math.toRadians(20)) {
                    if (turn > 0) left++;
                    else right++;
                }
                i0 = -1;
            }
        }
        return new int[] {left, right};
    }

    @Test
    public void bendCountsMatchTheTableAndSwapWhenReversed() {
        for (int d = 0; d < 4; d++) {
            int[] f = bends(build(d, false)), r = bends(build(d, true));
            String id = TrackDefs.ALL[d].id;
            System.out.println(id + " bends L" + f[0] + "/R" + f[1] + " (reversed L" + r[0] + "/R" + r[1] + "), table L"
                    + LEFT[d] + "/R" + RIGHT[d]);
            assertTrue(id + " left " + f[0], Math.abs(f[0] - LEFT[d]) <= 1);
            assertTrue(id + " right " + f[1], Math.abs(f[1] - RIGHT[d]) <= 1);
            assertTrue(id + " reversed left " + r[0], Math.abs(r[0] - RIGHT[d]) <= 1);
            assertTrue(id + " reversed right " + r[1], Math.abs(r[1] - LEFT[d]) <= 1);
        }
    }

    @Test
    public void projectGivesPositiveOffsetOnTheRightAndTheRightDistanceAlong() {
        for (int d = 0; d < 4; d++) {
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = build(d, rev);
                int n = t.sampleCount();
                float ds = t.lapLength() / n;
                TrackPoint tp = new TrackPoint();
                for (int k = 0; k < n; k += 7) {
                    float yaw = t.sampleYaw(k);
                    float px = t.sampleX(k) - (float) Math.cos(yaw) * 2f, pz = t.sampleZ(k) + (float) Math.sin(yaw) * 2f;
                    t.project(px, pz, -1, tp);
                    assertEquals("offset at sample " + k, 2f, tp.offset, 0.02f);
                    assertTrue("s at sample " + k + ": " + tp.s, lapGap(tp.s, k * ds, t.lapLength()) < 0.05f);
                }
            }
        }
    }

    @Test
    public void projectHasNoSawToothAtTheTightestBendEvenAtTheWall() {
        float[] p = new float[3];
        TrackPoint tp = new TrackPoint();
        for (int d = 0; d < 4; d++) {
            for (boolean rev : new boolean[] {false, true}) {
                TrackSpline t = build(d, rev);
                int n = t.sampleCount();
                int tight = 0;
                for (int i = 1; i < n; i++) if (Math.abs(t.radiusAt(i)) < Math.abs(t.radiusAt(tight))) tight = i;
                float s0 = tight * t.lapLength() / n;
                for (float off : new float[] {-EASY_WALL, 0f, EASY_WALL}) {
                    int hint = -1;
                    float prev = Float.NaN;
                    float maxErr = 0f;
                    for (int q = -400; q <= 400; q++) {   // 20 m before to 20 m after, every 0.05 m
                        t.sample(s0 + q * 0.05f, p);
                        float px = p[0] - (float) Math.cos(p[2]) * off, pz = p[1] + (float) Math.sin(p[2]) * off;
                        hint = t.project(px, pz, hint, tp);
                        float s = tp.s;
                        if (!Float.isNaN(prev) && s < prev - t.lapLength() / 2f) s += t.lapLength();
                        if (!Float.isNaN(prev)) {
                            assertTrue(TrackDefs.ALL[d].id + (rev ? " reversed" : "") + " offset " + off + ": s went "
                                    + prev + " -> " + s + " at step " + q, s > prev);
                        }
                        maxErr = Math.max(maxErr, Math.abs(tp.offset - off));
                        assertEquals(TrackDefs.ALL[d].id + (rev ? " reversed" : "") + " offset at step " + q, off,
                                tp.offset, 0.05f);
                        prev = s;
                    }
                    if (!rev && off == EASY_WALL) {
                        System.out.printf("%-8s tightest bend at s=%.1f (radius %.1f): largest offset error at the wall %.4f m%n",
                                TrackDefs.ALL[d].id, s0, t.radiusAt(tight), maxErr);
                    }
                }
            }
        }
    }

    @Test
    public void projectWrapsAtTheStartLine() {
        for (int d = 0; d < 4; d++) {
            TrackSpline t = build(d, false);
            float lap = t.lapLength();
            float[] p = new float[3];
            TrackPoint tp = new TrackPoint();
            t.sample(lap - 0.3f, p);
            int hint = t.project(p[0], p[1], -1, tp);
            assertEquals(lap - 0.3f, tp.s, 0.05f);
            t.sample(0.3f, p);
            t.project(p[0], p[1], hint, tp);   // drive over the line with the hint
            assertEquals(0.3f, tp.s, 0.05f);
            t.sample(0.3f, p);
            t.project(p[0], p[1], -1, tp);
            assertEquals(0.3f, tp.s, 0.05f);
        }
    }

    @Test
    public void minimapGivesEvenlySpacedPointsStartingAtControlPointZero() {
        for (int d = 0; d < 4; d++) {
            TrackSpline t = build(d, false);
            float[] out = new float[192];
            t.minimap(96, out);
            assertEquals(TrackDefs.ALL[d].control[0], out[0], 1e-4f);
            assertEquals(TrackDefs.ALL[d].control[1], out[1], 1e-4f);
            float[] p = new float[3];
            for (int k = 0; k < 96; k += 11) {
                t.sample(t.lapLength() * k / 96f, p);
                assertEquals(p[0], out[k * 2], 1e-4f);
                assertEquals(p[1], out[k * 2 + 1], 1e-4f);
            }
        }
    }

    /** Bytes allocated so far by this thread, or -1 when the JVM cannot tell (by reflection, as in RenderListTest). */
    private static long allocatedBytes() {
        try {
            Class<?> mf = Class.forName("java.lang.management.ManagementFactory");
            Object bean = mf.getMethod("getThreadMXBean").invoke(null);
            Class<?> sun = Class.forName("com.sun.management.ThreadMXBean");
            if (!sun.isInstance(bean)) return -1;
            sun.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(bean, true);
            return (Long) sun.getMethod("getThreadAllocatedBytes", long.class).invoke(bean, Thread.currentThread().getId());
        } catch (Exception | LinkageError e) {
            return -1;
        }
    }

    @Test
    public void sampleAndProjectDoNotAllocate() {
        Assume.assumeTrue("allocation counter not available", allocatedBytes() >= 0);
        TrackSpline t = build(1, false);
        float[] p = new float[3];
        TrackPoint tp = new TrackPoint();
        for (int w = 0; w < 3; w++) run(t, p, tp);   // warm up the JIT
        long before = allocatedBytes();
        run(t, p, tp);
        long used = allocatedBytes() - before;
        assertTrue("allocated " + used + " bytes", used < 2048);
    }

    private static void run(TrackSpline t, float[] p, TrackPoint tp) {
        int hint = -1;
        for (int i = 0; i < 10000; i++) {
            t.sample(i * 0.05f, p);
            hint = t.project(p[0] + 1f, p[1] - 1f, hint, tp);
        }
    }
}
