package com.angga.movetv.games.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StarRulesTest {

    @Test
    public void everyoneGetsAtLeastOneStar() {
        assertEquals(1, StarRules.stars(0, 0));
        assertEquals(1, StarRules.stars(0, 50));
        assertEquals(1, StarRules.stars(-5, 50));
        assertEquals(1, StarRules.stars(3, 0));
    }

    @Test
    public void twoStarsFromFortyPercent() {
        assertEquals(1, StarRules.stars(39, 100));
        assertEquals(2, StarRules.stars(40, 100));
        assertEquals(2, StarRules.stars(69, 100));
        // 40% of 25 is exactly 10
        assertEquals(1, StarRules.stars(9, 25));
        assertEquals(2, StarRules.stars(10, 25));
    }

    @Test
    public void threeStarsFromSeventyPercent() {
        assertEquals(2, StarRules.stars(69, 100));
        assertEquals(3, StarRules.stars(70, 100));
        assertEquals(3, StarRules.stars(100, 100));
        // 70% of 30 is exactly 21
        assertEquals(2, StarRules.stars(20, 30));
        assertEquals(3, StarRules.stars(21, 30));
    }

    @Test
    public void aScoreAboveWhatWasOfferedStillCountsAsThree() {
        // a combo bonus can push a score past the offered points
        assertEquals(3, StarRules.stars(130, 100));
    }

    @Test
    public void bigNumbersDoNotOverflow() {
        assertEquals(3, StarRules.stars(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(1, StarRules.stars(1, Integer.MAX_VALUE));
    }

    @Test
    public void sameShareSameStarsOnAnyLength() {
        assertEquals(StarRules.stars(14, 20), StarRules.stars(70, 100));
        assertEquals(StarRules.stars(8, 20), StarRules.stars(40, 100));
    }
}
