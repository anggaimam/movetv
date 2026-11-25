package com.wiggleplay.app.games.race;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.wiggleplay.app.gl.PaletteSwatches;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ThemeTest {

    @Test
    public void unknownAndUnbuiltThemesAreMeadow() {
        assertSame(Themes.MEADOW, Themes.byId("nope"));
        assertSame(Themes.MEADOW, Themes.byId(null));
        assertSame(Themes.ROCKIES, Themes.byId("rockies"));
        assertSame(Themes.SPACE, Themes.byId("space"));
        assertSame(Themes.CITY, Themes.byId("city"));
        assertSame(Themes.DESERT, Themes.byId("desert"));
    }

    @Test
    public void meadowIsTodaysLook() {
        Theme m = Themes.MEADOW;
        assertEquals(0xA8DDF2, m.sky);
        assertEquals(m.sky, m.horizon);
        assertEquals(25f, m.fogNear, 0f);
        assertEquals(55f, m.fogFar, 0f);
        assertEquals(0x37966D, m.ground);
        assertEquals(0, m.rings.length);
        assertEquals(0, m.patchCount);
        assertSame(RoadStyle.MEADOW, m.road);
    }

    @Test
    public void desertMatchesTheTicketTable() {
        Theme d = Themes.DESERT;
        assertEquals(0xA9D6EA, d.sky);
        assertEquals(0xF3E2C3, d.horizon);
        assertEquals(30f, d.fogNear, 0f);
        assertEquals(55f, d.fogFar, 0f);
        assertEquals(0xE9C98B, d.ground);
        assertEquals(0xDDB36F, d.patchA);
        assertEquals(0xF2DCA8, d.patchB);
        assertEquals(40, d.patchCount);
        assertEquals(4f, d.patchMinR, 0f);
        assertEquals(12f, d.patchMaxR, 0f);
        assertEquals(0x5A5E65, d.road.road);
        assertEquals(3, d.rings.length);
        assertEquals(50f, d.rings[0].radius, 0f);
        assertEquals(Theme.ROLLING, d.rings[0].profile);
        assertEquals(false, d.rings[0].low);
        assertEquals(54f, d.rings[1].radius, 0f);
        assertEquals(Theme.MESAS, d.rings[1].profile);
        assertEquals(true, d.rings[1].low);
        assertEquals(57f, d.rings[2].radius, 0f);
        assertEquals(0xEBC4A0, d.rings[2].color);
        assertTrue(d.fogFar <= 55f);
        assertEquals(Theme.SLICK_WATER, d.slick);
    }

    @Test
    public void fillPaletteUsesDistinctSlotsUnder224() {
        PaletteSwatches p = new PaletteSwatches();
        int n = Themes.DESERT.fillPalette(p);
        assertTrue(n <= PaletteSwatches.MAX);
        assertEquals(Theme.SW_FIRST_RING + 2 * 3 + 11 + 3, n);   // rings, 11 prop ramps of the 8 desert kinds, 3 sky swatches
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < 10; i++) used.add(i); // the road
        for (int i = 0; i < Themes.DESERT.rings.length; i++) {
            assertTrue(used.add(Themes.DESERT.ringSwatch(i)));
            assertTrue(used.add(Themes.DESERT.ringAccentSwatch(i)));
        }
        for (int r = 0; r < Themes.DESERT.props.length; r++) {
            for (int c = 0; c < Themes.DESERT.props[r].ramps(); c++) assertTrue(used.add(Themes.DESERT.propSwatch(r, c)));
        }
        for (int k = 0; k < 3; k++) assertTrue(used.add(Themes.DESERT.skySwatch(k)));
        for (int s : new int[] {Theme.SW_GROUND, Theme.SW_PATCH_A, Theme.SW_PATCH_B, Theme.SW_WHITE, Theme.SW_PALE_BLUE}) {
            assertTrue(s >= 16 && used.add(s));
        }
        assertEquals(16, Themes.DESERT.terrainSwatch(0));   // the terrain ramps take the ground and patch-A places
        assertEquals(17, Themes.DESERT.terrainSwatch(1));
        assertEquals(0xE2B57C, p.rgb(Themes.DESERT.ringSwatch(0)));
        assertEquals(0xE2B57C, p.rgb(Themes.DESERT.ringAccentSwatch(0))); // no accent: the body colour
        assertEquals(0x5A5E65, p.rgb(RoadStyle.SW_ROAD));
    }
}
