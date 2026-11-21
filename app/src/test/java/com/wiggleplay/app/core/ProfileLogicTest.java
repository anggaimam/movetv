package com.wiggleplay.app.core;

import static org.junit.Assert.assertEquals;

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
}
