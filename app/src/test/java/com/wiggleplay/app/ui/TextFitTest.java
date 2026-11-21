package com.wiggleplay.app.ui;

import static org.junit.Assert.*;

import org.junit.Test;

public class TextFitTest {
    /** Every character is 0.5 x size wide. */
    static final TextFit.Measure M = (s, size) -> s.length() * size * 0.5f;

    @Test
    public void shortTextKeepsItsSize() {
        TextFit.Result r = TextFit.fit(M, "Easy", 360f, 48f, 40f, 2);
        assertEquals(48f, r.size, 0f);
        assertEquals(1, r.lines.length);
        assertFalse(r.cut);
    }

    @Test
    public void shrinksBeforeWrapping() {
        // 12 chars: 48 px = 288... make it need 44 px: width 264 at 44
        TextFit.Result r = TextFit.fit(M, "Fruit Splash", 270f, 48f, 40f, 2);
        assertEquals(1, r.lines.length);
        assertTrue(r.size < 48f && r.size >= 40f);
        assertTrue(M.width(r.lines[0], r.size) <= 270f);
    }

    @Test
    public void wrapsAtTheMinimumSize() {
        TextFit.Result r = TextFit.fit(M, "Animal Dance Party", 300f, 48f, 40f, 2);
        assertEquals(40f, r.size, 0f);
        assertEquals(2, r.lines.length);
        for (String l : r.lines) assertTrue(M.width(l, r.size) <= 300f);
        assertFalse(r.cut);
        assertEquals("Animal", r.lines[0]);
        assertEquals("Dance Party", r.lines[1]);
    }

    @Test
    public void cutsWithAnEllipsisWhenNothingElseFits() {
        TextFit.Result r = TextFit.fit(M, "Extraordinarily Magnificent Adventure", 200f, 48f, 40f, 2);
        assertTrue(r.cut);
        assertEquals(40f, r.size, 0f);
        for (String l : r.lines) assertTrue(l, M.width(l, r.size) <= 200f);
        assertTrue(r.lines[r.lines.length - 1].endsWith("…"));
    }

    @Test
    public void oneLongWordIsCutNotWrapped() {
        TextFit.Result r = TextFit.fit(M, "Supercalifragilistic", 200f, 48f, 40f, 2);
        assertEquals(1, r.lines.length);
        assertTrue(r.cut);
        assertTrue(M.width(r.lines[0], r.size) <= 200f);
    }

    @Test
    public void singleLineLimitCuts() {
        TextFit.Result r = TextFit.fit(M, "A very long screen title that cannot fit anywhere at all", 400f, 72f, 40f, 1);
        assertEquals(1, r.lines.length);
        assertTrue(r.cut);
        assertEquals(40f, r.size, 0f);
        assertTrue(M.width(r.lines[0], 40f) <= 400f);
    }

    @Test
    public void neverGoesBelowTheMinimum() {
        TextFit.Result r = TextFit.fit(M, "Animal Dance Party", 50f, 48f, 40f, 2);
        assertEquals(40f, r.size, 0f);
    }
}
