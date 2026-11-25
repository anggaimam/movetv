package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NoiseTest {
    @Test
    public void valuesStayInRangeAndRepeat() {
        for (int i = 0; i < 2000; i++) {
            float x = i * 0.37f - 300f, z = i * 0.91f - 500f;
            float n = Noise.noise(x, z, 7L), f = Noise.fbm(x, z, 4, 7L);
            assertTrue(n >= 0f && n <= 1f);
            assertTrue(f >= 0f && f <= 1f);
            assertEquals(n, Noise.noise(x, z, 7L), 0f);
            assertEquals(f, Noise.fbm(x, z, 4, 7L), 0f);
        }
    }

    @Test
    public void anotherSeedGivesAnotherField() {
        int diff = 0;
        for (int i = 0; i < 100; i++) if (Noise.noise(i * 1.3f, i * 0.7f, 1L) != Noise.noise(i * 1.3f, i * 0.7f, 2L)) diff++;
        assertTrue(diff > 90);
    }

    @Test
    public void smoothstepClampsAndRises() {
        assertEquals(0f, Noise.smoothstep(2f, 5f, 1f), 0f);
        assertEquals(1f, Noise.smoothstep(2f, 5f, 9f), 0f);
        assertEquals(0.5f, Noise.smoothstep(2f, 6f, 4f), 1e-5f);
    }
}
