package com.angga.movetv.games.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class EndLayoutTest {

    private static final float TITLE_W = 760f;

    private static List<EndLayout.Box> all(EndLayout l) {
        List<EndLayout.Box> list = new ArrayList<>();
        list.add(l.title);
        list.add(l.back);
        for (int p = 0; p < l.players; p++) {
            if (l.players == 2) list.add(l.name[p]);
            list.add(l.score[p]);
            list.add(l.stars[p]);
            list.add(l.kid[p]);
        }
        for (EndLayout.Box b : l.tiles) list.add(b);
        return list;
    }

    private static void assertClear(EndLayout l) {
        List<EndLayout.Box> list = all(l);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                // the Back button sits in a corner: it only has to stay clear of the boxes, not 40 px away from the title
                EndLayout.Box a = list.get(i), b = list.get(j);
                assertFalse("blocks " + i + " and " + j + " are closer than " + EndLayout.GAP + " px",
                        a.closerThan(b, EndLayout.GAP));
            }
        }
    }

    private static void assertOnStage(EndLayout l) {
        for (EndLayout.Box b : all(l)) {
            assertTrue(b.l >= 0f && b.t >= 0f && b.r <= 1920f && b.b <= 1080f);
        }
    }

    @Test
    public void onePlayerThreeDigits() {
        EndLayout l = new EndLayout(1, TITLE_W, 3, 0);
        assertClear(l);
        assertOnStage(l);
    }

    @Test
    public void onePlayerFourDigits() {
        EndLayout l = new EndLayout(1, TITLE_W, 4, 0);
        assertClear(l);
        assertOnStage(l);
    }

    @Test
    public void twoPlayersThreeDigits() {
        EndLayout l = new EndLayout(2, TITLE_W, 3, 3);
        assertClear(l);
        assertOnStage(l);
    }

    @Test
    public void twoPlayersFourDigits() {
        EndLayout l = new EndLayout(2, TITLE_W, 4, 4);
        assertClear(l);
        assertOnStage(l);
    }

    @Test
    public void twoPlayersMixedDigits() {
        assertClear(new EndLayout(2, TITLE_W, 1, 4));
        assertClear(new EndLayout(2, TITLE_W, 4, 2));
    }

    @Test
    public void everyBlockInAColumnIsFortyPxFromTheNext() {
        EndLayout l = new EndLayout(2, TITLE_W, 3, 3);
        for (int p = 0; p < 2; p++) {
            assertTrue(l.name[p].t - l.title.b >= EndLayout.GAP);
            assertTrue(l.score[p].t - l.name[p].b >= EndLayout.GAP);
            assertTrue(l.stars[p].t - l.score[p].b >= EndLayout.GAP);
            assertTrue(l.tiles[0].t - l.stars[p].b >= EndLayout.GAP);
        }
        EndLayout one = new EndLayout(1, TITLE_W, 3, 0);
        assertTrue(one.score[0].t - one.title.b >= EndLayout.GAP);
        assertTrue(one.stars[0].t - one.score[0].b >= EndLayout.GAP);
        assertTrue(one.tiles[0].t - one.stars[0].b >= EndLayout.GAP);
    }

    @Test
    public void starSlotsFitInsideTheirRowBox() {
        EndLayout l = new EndLayout(2, TITLE_W, 3, 3);
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < 3; i++) {
                float half = EndLayout.STAR / 2f;
                assertTrue(l.slotX(p, i) - half >= l.stars[p].l);
                assertTrue(l.slotX(p, i) + half <= l.stars[p].r);
                assertTrue(l.slotY(p) - half >= l.stars[p].t);
                assertTrue(l.slotY(p) + half <= l.stars[p].b);
            }
        }
    }

    @Test
    public void tilesStartLowAndPlayerColumnsAreWhereTheTicketSaysAndOnePlayerIsCentred() {
        EndLayout two = new EndLayout(2, TITLE_W, 3, 3);
        assertEquals(560f, two.colX[0], 0.01f);
        assertEquals(1360f, two.colX[1], 0.01f);
        EndLayout one = new EndLayout(1, TITLE_W, 3, 0);
        assertEquals(960f, one.colX[0], 0.01f);
        for (EndLayout.Box b : one.tiles) assertTrue(b.t >= 760f);
        assertEquals(60f, one.title.t, 0.01f);
        assertEquals(180f, one.title.b, 0.01f);
    }
}
