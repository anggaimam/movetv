package com.wiggleplay.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileLogicTest {

    @Test
    public void defaultsWhenNothingIsSaved() {
        assertEquals(1000, ProfileLogic.cleanHold(ProfileLogic.asInt(null, ProfileLogic.DEFAULT_HOLD_MS)));
        assertEquals(1, ProfileLogic.cleanPlayers(ProfileLogic.asInt(null, ProfileLogic.DEFAULT_PLAYERS)));
        assertEquals(0, ProfileLogic.cleanStars(ProfileLogic.asInt(null, 0)));
    }

    @Test
    public void holdOnlyAllowsTheThreeChoices() {
        assertEquals(700, ProfileLogic.cleanHold(700));
        assertEquals(1000, ProfileLogic.cleanHold(1000));
        assertEquals(1400, ProfileLogic.cleanHold(1400));
        assertEquals(1000, ProfileLogic.cleanHold(0));
        assertEquals(1000, ProfileLogic.cleanHold(-5));
        assertEquals(1000, ProfileLogic.cleanHold(850));
        assertEquals(1000, ProfileLogic.cleanHold(Integer.MAX_VALUE));
    }

    @Test
    public void playersAreOneOrTwo() {
        assertEquals(1, ProfileLogic.cleanPlayers(1));
        assertEquals(2, ProfileLogic.cleanPlayers(2));
        assertEquals(1, ProfileLogic.cleanPlayers(0));
        assertEquals(1, ProfileLogic.cleanPlayers(3));
        assertEquals(1, ProfileLogic.cleanPlayers(-1));
    }

    @Test
    public void starsOutsideZeroToThreeBecomeZero() {
        for (int i = 0; i <= 3; i++) assertEquals(i, ProfileLogic.cleanStars(i));
        assertEquals(0, ProfileLogic.cleanStars(-1));
        assertEquals(0, ProfileLogic.cleanStars(4));
        assertEquals(0, ProfileLogic.cleanStars(99999));
    }

    @Test
    public void damagedValuesRecover() {
        assertEquals(1000, ProfileLogic.asInt("abc", 1000));
        assertEquals(1000, ProfileLogic.asInt("", 1000));
        assertEquals(1000, ProfileLogic.asInt(Boolean.TRUE, 1000));
        assertEquals(1000, ProfileLogic.asInt(new Object(), 1000));
        assertEquals(700, ProfileLogic.asInt("700", 1000));
        assertEquals(700, ProfileLogic.asInt(" 700 ", 1000));
        assertEquals(1400, ProfileLogic.asInt(1400L, 1000));
        assertEquals(1000, ProfileLogic.cleanHold(ProfileLogic.asInt("fast", ProfileLogic.DEFAULT_HOLD_MS)));
        assertEquals(0, ProfileLogic.cleanStars(ProfileLogic.asInt("many", 0)));
    }

    @Test
    public void levelAndKey() {
        assertEquals(1, ProfileLogic.cleanLevel(0));
        assertEquals(1, ProfileLogic.cleanLevel(4));
        assertEquals(3, ProfileLogic.cleanLevel(3));
        assertEquals("stars_fruit_2", ProfileLogic.starsKey("fruit", 2));
        assertEquals("stars_sky_1", ProfileLogic.starsKey("sky", 9));
    }

    @Test
    public void pointerDefaultsToHandAndRecoversFromJunk() {
        assertEquals("hand", ProfileLogic.cleanPointer(null));
        assertEquals("hand", ProfileLogic.cleanPointer("hand"));
        assertEquals("reach", ProfileLogic.cleanPointer("reach"));
        assertEquals("hand", ProfileLogic.cleanPointer("banana"));
        assertEquals("hand", ProfileLogic.cleanPointer(Integer.valueOf(7)));
        assertEquals(1.0f, ProfileLogic.pointerGain("hand"), 0f);
        assertEquals(1.6f, ProfileLogic.pointerGain("reach"), 0f);
        assertEquals(1.0f, ProfileLogic.pointerGain(null), 0f);
    }

    @Test
    public void raceSteerIsWheelOrLeanAndDefaultsToWheel() {
        assertEquals("lean", ProfileLogic.cleanRaceSteer("lean"));
        assertEquals("wheel", ProfileLogic.cleanRaceSteer("wheel"));
        assertEquals("wheel", ProfileLogic.cleanRaceSteer("plane"));   // saved by the T-23 Part B picker
        assertEquals("wheel", ProfileLogic.cleanRaceSteer(null));
        assertEquals("wheel", ProfileLogic.cleanRaceSteer("LEAN"));
        assertEquals("wheel", ProfileLogic.cleanRaceSteer(3));
        assertEquals("wheel", ProfileLogic.cleanRaceSteer(""));
    }

    @Test
    public void raceTrackIsOneOfTheFourAndDefaultsToDesert() {
        for (String id : new String[] {"desert", "city", "rockies", "space"}) assertEquals(id, ProfileLogic.cleanRaceTrack(id));
        assertEquals("desert", ProfileLogic.cleanRaceTrack(null));
        assertEquals("desert", ProfileLogic.cleanRaceTrack("moon"));
        assertEquals("desert", ProfileLogic.cleanRaceTrack("desert-r"));
        assertEquals("desert", ProfileLogic.cleanRaceTrack(7));
        assertEquals("desert", ProfileLogic.cleanRaceTrack(""));
    }

    @Test
    public void raceDirectionIsForwardUnlessSavedTrue() {
        assertFalse(ProfileLogic.cleanRaceReversed(null));
        assertFalse(ProfileLogic.cleanRaceReversed("true"));
        assertFalse(ProfileLogic.cleanRaceReversed(1));
        assertFalse(ProfileLogic.cleanRaceReversed(Boolean.FALSE));
        assertTrue(ProfileLogic.cleanRaceReversed(Boolean.TRUE));
    }

    @Test
    public void raceTruckCyclesYellowRedGreenPurpleAndDefaultsToYellow() {
        assertEquals("yellow", ProfileLogic.cleanRaceTruck(null));
        assertEquals("yellow", ProfileLogic.cleanRaceTruck("blue"));
        assertEquals("yellow", ProfileLogic.cleanRaceTruck("RED"));
        assertEquals("purple", ProfileLogic.cleanRaceTruck("purple"));
        assertEquals("red", ProfileLogic.nextRaceTruck("yellow"));
        assertEquals("green", ProfileLogic.nextRaceTruck("red"));
        assertEquals("purple", ProfileLogic.nextRaceTruck("green"));
        assertEquals("yellow", ProfileLogic.nextRaceTruck("purple"));
        assertEquals("red", ProfileLogic.nextRaceTruck("junk"));   // junk counts as yellow
    }

    @Test
    public void raceBestOnlyGoesUpAndTheTruckIndexFollowsTheCycle() {
        assertTrue(ProfileLogic.raisesRaceBest(null, 1));
        assertTrue(ProfileLogic.raisesRaceBest(1, 2));
        assertFalse(ProfileLogic.raisesRaceBest(2, 2));
        assertFalse(ProfileLogic.raisesRaceBest(3, 1));
        assertFalse(ProfileLogic.raisesRaceBest(null, 0));
        assertTrue(ProfileLogic.raisesRaceBest("junk", 1));   // a corrupt value counts as never played
        assertFalse(ProfileLogic.raisesRaceBest(1, 9 - 6 - 2));   // 1 is not higher than 1
        assertEquals(0, ProfileLogic.raceTruckIndex(null));
        assertEquals(1, ProfileLogic.raceTruckIndex("red"));
        assertEquals(2, ProfileLogic.raceTruckIndex("green"));
        assertEquals(3, ProfileLogic.raceTruckIndex("purple"));
        assertEquals(0, ProfileLogic.raceTruckIndex("blue"));
    }

    @Test
    public void raceBestStarsAreClampedAndNonNumbersAreZero() {
        assertEquals(0, ProfileLogic.cleanRaceBest(null));
        assertEquals(0, ProfileLogic.cleanRaceBest("3"));
        assertEquals(0, ProfileLogic.cleanRaceBest(Double.NaN));
        assertEquals(2, ProfileLogic.cleanRaceBest(2));
        assertEquals(3, ProfileLogic.cleanRaceBest(3));
        assertEquals(3, ProfileLogic.cleanRaceBest(9));
        assertEquals(0, ProfileLogic.cleanRaceBest(-4));
        assertEquals("raceBest_desert_EASY", ProfileLogic.raceBestKey("desert", 1));
        assertEquals("raceBest_city-r_HARD", ProfileLogic.raceBestKey("city-r", 3));
    }

    @Test
    public void pictureIsAutoHighOrLowAndDefaultsToAuto() {
        assertEquals("auto", ProfileLogic.cleanQuality(null));
        assertEquals("auto", ProfileLogic.cleanQuality("auto"));
        assertEquals("high", ProfileLogic.cleanQuality("high"));
        assertEquals("low", ProfileLogic.cleanQuality("low"));
        assertEquals("auto", ProfileLogic.cleanQuality("HIGH"));
        assertEquals("auto", ProfileLogic.cleanQuality("ultra"));
        assertEquals("auto", ProfileLogic.cleanQuality(2));
        assertEquals("auto", ProfileLogic.cleanQuality(""));
    }

    @Test
    public void pictureTileCyclesAutoHighLow() {
        assertEquals("high", ProfileLogic.nextQuality("auto"));
        assertEquals("low", ProfileLogic.nextQuality("high"));
        assertEquals("auto", ProfileLogic.nextQuality("low"));
        assertEquals("high", ProfileLogic.nextQuality("damaged"));
    }
}
