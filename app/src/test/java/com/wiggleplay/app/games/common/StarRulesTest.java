package com.wiggleplay.app.games.common;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StarRulesTest {
    @Test public void thresholds() {
        assertEquals(3, StarRules.stars(70, 100));
        assertEquals(2, StarRules.stars(69, 100));
        assertEquals(2, StarRules.stars(40, 100));
        assertEquals(1, StarRules.stars(39, 100));
        assertEquals(3, StarRules.stars(100, 100));
    }

    @Test public void neverBelowOne() {
        assertEquals(1, StarRules.stars(0, 0));
        assertEquals(1, StarRules.stars(0, 50));
        assertEquals(1, StarRules.stars(5, -3));
    }
}
