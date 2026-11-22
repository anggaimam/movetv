package com.wiggleplay.app.games.sky;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.pose.Kp;
import com.wiggleplay.app.pose.PlayerPose;

import org.junit.Test;

public class SkyLogicTest {

    /** A standing child, shoulders 200 px apart at y 400; wrists at the given heights (x just outside the shoulders). */
    private static PlayerPose pose(float rightShoulderDy, float lWristY, float rWristY) {
        PlayerPose p = new PlayerPose(0);
        p.present = true;
        for (int i = 0; i < Kp.COUNT; i++) {
            p.kps[i].x = 960f;
            p.kps[i].y = 600f;
            p.kps[i].s = 0.9f;
        }
        p.kps[Kp.L_SHOULDER].x = 860f;
        p.kps[Kp.L_SHOULDER].y = 400f;
        p.kps[Kp.R_SHOULDER].x = 1060f;
        p.kps[Kp.R_SHOULDER].y = 400f + rightShoulderDy;
        p.kps[Kp.L_WRIST].x = 660f;
        p.kps[Kp.L_WRIST].y = lWristY;
        p.kps[Kp.R_WRIST].x = 1260f;
        p.kps[Kp.R_WRIST].y = rWristY;
        return p;
    }

    private static final class Log implements SkyLogic.Listener {
        int rings, missed, coins, stars, bumps, fogs, ringPoints, magnets;
        int markedHits, markedBumps, markedFogs;

        @Override
        public void onEvent(int type, SkyLogic.Item it, int points) {
            boolean marked = it.seed > 5f;
            switch (type) {
                case SkyLogic.EV_RING: rings++; ringPoints += points; if (marked) markedHits++; break;
                case SkyLogic.EV_RING_MISS: missed++; break;
                case SkyLogic.EV_COIN: coins++; break;
                case SkyLogic.EV_STAR: stars++; break;
                case SkyLogic.EV_BUMP: bumps++; if (marked) markedBumps++; break;
                case SkyLogic.EV_MAGNET: magnets++; break;
                default: fogs++; if (marked) markedFogs++; break;
            }
        }
    }

    // ---- the body ----

    @Test
    public void steeringDeadZoneAndFullTurn() {
        assertEquals(0f, SkyLogic.steerFor(0f), 1e-6);
        assertEquals(0f, SkyLogic.steerFor(4f), 1e-6);
        assertEquals(0f, SkyLogic.steerFor(-3.9f), 1e-6);
        assertEquals(0.5f, SkyLogic.steerFor(13f), 1e-5);
        assertEquals(-0.5f, SkyLogic.steerFor(-13f), 1e-5);
        assertEquals(1f, SkyLogic.steerFor(22f), 1e-6);
        assertEquals(1f, SkyLogic.steerFor(60f), 1e-6);
        assertEquals(-1f, SkyLogic.steerFor(-40f), 1e-6);
        assertEquals(0f, SkyLogic.steerFor(Float.NaN), 1e-6);
    }

    @Test
    public void shoulderAngleSignAndMissingShoulders() {
        assertEquals(0f, SkyLogic.shoulderAngle(pose(0f, 400f, 400f)), 1e-4);
        // right shoulder (right side of the screen) lower: about 26.6 degrees, positive = steer right
        assertEquals(26.565f, SkyLogic.shoulderAngle(pose(100f, 400f, 400f)), 0.01f);
        assertEquals(-26.565f, SkyLogic.shoulderAngle(pose(-100f, 400f, 400f)), 0.01f);
        // the same tilt gives the same sign if the two shoulder points were swapped
        PlayerPose swapped = pose(100f, 400f, 400f);
        float lx = swapped.kps[Kp.L_SHOULDER].x, ly = swapped.kps[Kp.L_SHOULDER].y;
        swapped.kps[Kp.L_SHOULDER].x = swapped.kps[Kp.R_SHOULDER].x;
        swapped.kps[Kp.L_SHOULDER].y = swapped.kps[Kp.R_SHOULDER].y;
        swapped.kps[Kp.R_SHOULDER].x = lx;
        swapped.kps[Kp.R_SHOULDER].y = ly;
        assertEquals(26.565f, SkyLogic.shoulderAngle(swapped), 0.01f);
        PlayerPose dim = pose(0f, 400f, 400f);
        dim.kps[Kp.L_SHOULDER].s = 0.1f;
        assertTrue(Float.isNaN(SkyLogic.shoulderAngle(dim)));
    }

    @Test
    public void climbDeadZoneAndFullRange() {
        assertEquals(0f, SkyLogic.climbFor(0f), 1e-6);
        assertEquals(0f, SkyLogic.climbFor(25f), 1e-6);
        assertEquals(0f, SkyLogic.climbFor(-24f), 1e-6);
        assertEquals(0.5f, SkyLogic.climbFor(52.5f), 1e-5);
        assertEquals(1f, SkyLogic.climbFor(80f), 1e-6);
        assertEquals(1f, SkyLogic.climbFor(300f), 1e-6);
        assertEquals(-1f, SkyLogic.climbFor(-80f), 1e-6);
        assertEquals(0f, SkyLogic.climbFor(Float.NaN), 1e-6);
    }

    @Test
    public void wingHeightUsesWingsOnly() {
        assertEquals(0f, SkyLogic.wingHeight(pose(0f, 400f, 400f)), 1e-4);
        assertEquals(100f, SkyLogic.wingHeight(pose(0f, 300f, 300f)), 1e-4);
        assertEquals(-100f, SkyLogic.wingHeight(pose(0f, 500f, 500f)), 1e-4);
        // one wing up, one arm hanging (more than a shoulder width below): only the raised wrist counts
        assertEquals(150f, SkyLogic.wingHeight(pose(0f, 250f, 900f)), 1e-4);
        // both arms hanging: no wings, level flight
        assertTrue(Float.isNaN(SkyLogic.wingHeight(pose(0f, 900f, 900f))));
        PlayerPose noWrists = pose(0f, 300f, 300f);
        noWrists.kps[Kp.L_WRIST].s = 0.1f;
        noWrists.kps[Kp.R_WRIST].s = 0.1f;
        assertTrue(Float.isNaN(SkyLogic.wingHeight(noWrists)));
    }

    @Test
    public void unseenPlayerFliesStraightAndLevel() {
        SkyLogic l = new SkyLogic(1, 1L, 2L, null);
        l.control(pose(100f, 300f, 300f));
        for (int i = 0; i < 30; i++) l.update(0.016f);
        assertTrue(l.steer() > 0.5f && l.climb() > 0.5f);
        PlayerPose gone = pose(100f, 300f, 300f);
        gone.present = false;
        l.control(gone);
        for (int i = 0; i < 120; i++) l.update(0.016f);
        assertEquals(0f, l.steer(), 0.01f);
        assertEquals(0f, l.climb(), 0.01f);
        l.control(null);
        float x = l.planeX(), y = l.planeY();
        for (int i = 0; i < 30; i++) l.update(0.016f);
        assertEquals(x, l.planeX(), 2f);
        assertEquals(y, l.planeY(), 2f);
    }

    @Test
    public void steeringIsSmoothedOverAbout150ms() {
        SkyLogic l = new SkyLogic(1, 1L, 2L, null);
        l.setTargets(1f, 0f);
        for (int i = 0; i < 9; i++) l.update(1f / 60f / 1f); // 150 ms in total (9 x 16.7 ms)
        assertEquals(1f - (float) Math.exp(-1.0), l.steer(), 0.02f);
    }

    // ---- stars ----

    @Test
    public void fairStarsBoundaries() {
        assertEquals(1, SkyLogic.stars(0, 0));
        assertEquals(1, SkyLogic.stars(0, 100));
        assertEquals(1, SkyLogic.stars(39, 100));
        assertEquals(2, SkyLogic.stars(40, 100));
        assertEquals(2, SkyLogic.stars(69, 100));
        assertEquals(3, SkyLogic.stars(70, 100));
        assertEquals(3, SkyLogic.stars(100, 100));
    }

    // ---- touching things ----

    /** Flies a plane that sits still next to a marked item placed just ahead. */
    private static Log flyInto(int kind, float itemX, float itemY, float r) {
        Log log = new Log();
        SkyLogic l = new SkyLogic(1, 5L, 6L, log);
        l.setPlane(0f, 300f);
        l.add(kind, itemX, itemY, l.planeZ() + 400f, r, 9f);
        for (int i = 0; i < 60; i++) l.update(0.016f);
        return log;
    }

    @Test
    public void ringPassedInsideIsCollectedOutsideIsMissed() {
        Log inside = flyInto(SkyLogic.RING, 100f, 350f, 260f);
        assertEquals(1, inside.markedHits);
        Log outside = flyInto(SkyLogic.RING, 400f, 300f, 260f);
        assertEquals(0, outside.markedHits);
    }

    @Test
    public void obstaclesWobbleButNeverCostPoints() {
        Log hit = flyInto(SkyLogic.BALLOON, 50f, 300f, 130f);
        assertEquals(1, hit.markedBumps);
        Log far = flyInto(SkyLogic.BIRD, 600f, 300f, 90f);
        assertEquals(0, far.markedBumps);
        Log log = new Log();
        SkyLogic l = new SkyLogic(1, 5L, 6L, log);
        l.setPlane(0f, 300f);
        int before = l.score();
        l.add(SkyLogic.BALLOON, 0f, 300f, l.planeZ() + 300f, 130f, 9f);
        for (int i = 0; i < 40; i++) l.update(0.016f);
        assertTrue(l.score() >= before);
        assertTrue(log.markedBumps == 1);
    }

    @Test
    public void cloudsFogOnlyWhenRealAndNearby() {
        Log solid = flyInto(SkyLogic.CLOUD, 0f, 300f, 260f);
        assertEquals(1, solid.markedFogs);
        Log decoration = flyInto(SkyLogic.CLOUD, 0f, 300f, 0f);
        assertEquals(0, decoration.markedFogs);
    }

    @Test
    public void assistDriftsTheNextRingThirtyPercentTowardThePlane() {
        SkyLogic l = new SkyLogic(1, 11L, 12L, null);
        l.setPlane(-800f, 100f);
        SkyLogic.Item target = null;
        for (int i = 0; i < l.itemCount(); i++) {
            SkyLogic.Item it = l.item(i);
            if (it.kind == SkyLogic.RING && it.z > l.planeZ() + 1500f) {
                target = it;
                break;
            }
        }
        float x0 = target.x, y0 = target.y;
        l.ringResult(false);
        l.ringResult(false);
        assertEquals(x0, target.x, 1e-3);
        l.ringResult(false);
        assertEquals(x0 + (-800f - x0) * 0.3f, target.x, 1e-2);
        assertEquals(y0 + (100f - y0) * 0.3f, target.y, 1e-2);
        assertEquals(0, l.missStreak());
        l.ringResult(false);
        l.ringResult(true);
        assertEquals(0, l.missStreak());
    }

    // ---- the whole course ----

    @Test
    public void sameSeedGivesTheSameCourse() {
        SkyLogic a = new SkyLogic(2, 77L, 1L, null), b = new SkyLogic(2, 77L, 99L, null);
        assertEquals(a.itemCount(), b.itemCount());
        for (int i = 0; i < a.itemCount(); i++) {
            assertEquals(a.item(i).kind, b.item(i).kind);
            assertEquals(a.item(i).x, b.item(i).x, 1e-4);
            assertEquals(a.item(i).z, b.item(i).z, 1e-4);
        }
    }

    /** A pilot that follows the line from the ring it just passed to the next ring (so it also picks up the coins). */
    private static SkyLogic pilot(int level, long seed) {
        SkyLogic l = new SkyLogic(level, seed, seed + 1, null);
        float px = 0f, py = SkyLogic.START_Y, pz = 0f; // the last ring passed (the start counts as one)
        for (int i = 0; i < 80 * 60; i++) {
            SkyLogic.Item next = null;
            for (int k = 0; k < l.itemCount(); k++) {
                SkyLogic.Item it = l.item(k);
                if (it.kind == SkyLogic.RING && it.z > l.planeZ()) {
                    next = it;
                    break;
                }
                if (it.kind == SkyLogic.RING) {
                    px = it.x;
                    py = it.y;
                    pz = it.z;
                }
            }
            if (next != null) {
                float f = Math.max(0f, Math.min(1f, (l.planeZ() + 200f - pz) / (next.z - pz)));
                float tx = px + (next.x - px) * f, ty = py + (next.y - py) * f;
                l.setTargets((tx - l.planeX()) / 80f, (ty - l.planeY()) / 80f);
            }
            l.update(1f / 60f);
        }
        return l;
    }

    @Test
    public void everyCourseCanBeFlownForThreeStars() {
        for (int level = 1; level <= 3; level++) {
            for (long seed = 1; seed <= 6; seed++) {
                SkyLogic l = pilot(level, seed * 31L);
                assertTrue("level " + level + " seed " + seed + " offered", l.offered() > 40);
                assertEquals("level " + level + " seed " + seed + " " + l.score() + "/" + l.offered(),
                        3, SkyLogic.stars(l.score(), l.offered()));
            }
        }
    }

    @Test
    public void flyingStraightStillEarnsAStarAndNothingBreaks() {
        for (int level = 1; level <= 3; level++) {
            SkyLogic l = new SkyLogic(level, 5L, 6L, null);
            for (int i = 0; i < 80 * 60; i++) {
                l.update(1f / 60f);
                assertTrue(l.itemCount() <= 96);
                assertTrue(l.planeX() >= -SkyLogic.X_MAX && l.planeX() <= SkyLogic.X_MAX);
                assertTrue(l.planeY() >= 0f && l.planeY() <= SkyLogic.Y_MAX);
            }
            assertTrue(l.score() >= 0);
            assertTrue(SkyLogic.stars(l.score(), l.offered()) >= 1);
        }
    }

    @Test
    public void itemsStaySortedAndAheadOfThePlane() {
        SkyLogic l = pilot(3, 4L);
        float last = -1e9f;
        for (int i = 0; i < l.itemCount(); i++) {
            assertTrue(l.item(i).z >= last);
            last = l.item(i).z;
        }
        assertTrue(l.item(l.itemCount() - 1).z > l.planeZ() + SkyLogic.SPAWN_DIST * 0.8f);
    }

    @Test
    public void windGustsOnlyOnHard() {
        for (int level = 1; level <= 3; level++) {
            SkyLogic l = new SkyLogic(level, 5L, 6L, null);
            float maxX = 0f;
            for (int i = 0; i < 40 * 60; i++) {
                l.update(1f / 60f);
                maxX = Math.max(maxX, Math.abs(l.planeX()));
            }
            if (level == 3) assertTrue("hard should be nudged", maxX > 50f);
            else assertEquals(0f, maxX, 1e-3);
        }
    }

    @Test
    public void ringsGetSmallerWithLevel() {
        assertEquals(260f, new SkyLogic(1, 1L, 1L, null).ringRadius(), 0f);
        assertEquals(200f, new SkyLogic(2, 1L, 1L, null).ringRadius(), 0f);
        assertEquals(150f, new SkyLogic(3, 1L, 1L, null).ringRadius(), 0f);
        assertFalse(new SkyLogic(1, 1L, 1L, null).itemCount() == 0);
    }

    @Test
    public void oneOrTwoMagnetsPerRoundOnEveryLevel() {
        for (int level = 1; level <= 3; level++) {
            for (long seed = 1; seed <= 25; seed++) {
                SkyLogic l = new SkyLogic(level, seed * 17L, 1L, null);
                for (int i = 0; i < 80 * 60; i++) l.update(1f / 60f);
                int n = l.magnetsMade();
                assertTrue("level " + level + " seed " + seed + " made " + n, n == 1 || n == 2);
            }
        }
    }

    @Test
    public void magnetsDoNotChangeTheRestOfTheCourse() {
        SkyLogic a = new SkyLogic(2, 5L, 1L, null), b = new SkyLogic(2, 5L, 1L, null);
        for (int i = 0; i < 20 * 60; i++) {
            a.update(1f / 60f);
            b.update(1f / 60f);
        }
        assertEquals(a.itemCount(), b.itemCount());
    }

    @Test
    public void pickingUpAMagnetPullsCoinsInAndGivesPoints() {
        Log log = new Log();
        SkyLogic l = new SkyLogic(1, 3L, 4L, log);
        l.setPlane(0f, 300f);
        l.startMagnet();
        assertEquals(SkyLogic.MAGNET_TIME, l.magnet(), 1e-4);
        // a coin 600 px to the side and 400 up, ahead of the plane: far outside the normal touch radius of 110
        SkyLogic.Item coin = l.add(SkyLogic.COIN, 600f, 700f, l.planeZ() + 2200f, 110f, 0.5f);
        for (int i = 0; i < 4 * 60 && !coin.done; i++) l.update(1f / 60f);
        assertTrue("the coin should be pulled in and collected", coin.hit);
        assertTrue(log.coins >= 1);
    }

    @Test
    public void withoutTheMagnetThatCoinIsMissed() {
        SkyLogic l = new SkyLogic(1, 3L, 4L, null);
        l.setPlane(0f, 300f);
        SkyLogic.Item coin = l.add(SkyLogic.COIN, 600f, 700f, l.planeZ() + 2200f, 110f, 0.5f);
        for (int i = 0; i < 4 * 60 && !coin.done; i++) l.update(1f / 60f);
        assertFalse(coin.hit);
    }

    @Test
    public void magnetRunsOutAndFlyingThroughOneStartsIt() {
        Log log = new Log();
        SkyLogic l = new SkyLogic(1, 3L, 4L, log);
        l.setPlane(0f, 300f);
        l.add(SkyLogic.MAGNET, 0f, 300f, l.planeZ() + 500f, 140f, 0.5f);
        for (int i = 0; i < 90 && log.magnets == 0; i++) l.update(1f / 60f);
        assertEquals(1, log.magnets);
        assertTrue(l.magnet() > SkyLogic.MAGNET_TIME - 1f);
        for (int i = 0; i < 7 * 60; i++) l.update(1f / 60f);
        assertEquals(0f, l.magnet(), 1e-4);
    }
}
