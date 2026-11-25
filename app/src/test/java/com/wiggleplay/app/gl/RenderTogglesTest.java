package com.wiggleplay.app.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RenderTogglesTest {

    @Test
    public void meshNamesFallIntoTheirGroups() {
        assertEquals(RenderToggles.GROUP_TERRAIN, RenderToggles.groupOf("terrain_12"));
        assertEquals(RenderToggles.GROUP_TERRAIN, RenderToggles.groupOf("patch_3"));
        assertEquals(RenderToggles.GROUP_PROPS, RenderToggles.groupOf("prop_2_1"));
        assertEquals(RenderToggles.GROUP_PROPS, RenderToggles.groupOf("shd_40"));
        assertEquals(RenderToggles.GROUP_BASE, RenderToggles.groupOf("trk_road_0"));
        assertEquals(RenderToggles.GROUP_BASE, RenderToggles.groupOf("ring_1"));
    }

    @Test
    public void flipTogglesOneSwitchAndResetPutsAllBack() {
        RenderToggles t = new RenderToggles();
        for (int i = 0; i < RenderToggles.COUNT; i++) assertFalse(t.isOff(i));
        t.flip(RenderToggles.PROPS);
        assertTrue(t.isOff(RenderToggles.PROPS));
        assertFalse(t.isOff(RenderToggles.TERRAIN));
        assertTrue(t.groupOff(RenderToggles.GROUP_PROPS));
        assertFalse(t.groupOff(RenderToggles.GROUP_TERRAIN));
        assertFalse(t.groupOff(RenderToggles.GROUP_BASE));
        t.flip(RenderToggles.PROPS);
        assertFalse(t.isOff(RenderToggles.PROPS));
        t.flip(RenderToggles.TERRAIN);
        t.flip(RenderToggles.HUD);
        t.reset();
        for (int i = 0; i < RenderToggles.COUNT; i++) assertFalse(t.isOff(i));
        t.flip(99);   // ignored
        for (int i = 0; i < RenderToggles.COUNT; i++) assertFalse(t.isOff(i));
    }

    @Test
    public void roadIsNeverSwitchedOff() {
        RenderToggles t = new RenderToggles();
        t.flip(RenderToggles.TERRAIN);
        t.flip(RenderToggles.PROPS);
        assertFalse(t.groupOff(RenderToggles.GROUP_BASE));
    }

    @Test
    public void describeShowsStateAndWorld() {
        RenderToggles t = new RenderToggles();
        t.setWorld(true, 34);
        t.flip(RenderToggles.SKY);
        StringBuilder sb = new StringBuilder();
        t.describe(sb);
        String s = sb.toString();
        assertTrue(s, s.contains("4 sky OFF"));
        assertTrue(s, s.contains("1 terrain on"));
        assertTrue(s, s.contains("world split 34 chunks"));
        t.setWorld(false, 12);
        sb.setLength(0);
        t.describe(sb);
        assertTrue(sb.toString(), sb.toString().contains("merged"));
    }
}
