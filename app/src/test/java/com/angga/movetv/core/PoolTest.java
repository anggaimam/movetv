package com.angga.movetv.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class PoolTest {

    private static final class Item { int id; }

    private static Pool<Item> make(int cap, int[] created) {
        return new Pool<>(cap, () -> { created[0]++; return new Item(); });
    }

    @Test
    public void createsExactlyCapacityObjectsUpFrontAndNeverMore() {
        int[] created = {0};
        Pool<Item> p = make(5, created);
        assertEquals(5, created[0]);
        assertEquals(5, p.capacity());
        assertEquals(0, p.size());
        for (int i = 0; i < 100; i++) {
            Item a = p.obtain();
            p.free(a);
        }
        assertEquals("no allocation after construction", 5, created[0]);
    }

    @Test
    public void obtainReturnsNullWhenFullAndWorksAgainAfterFree() {
        Pool<Item> p = make(3, new int[1]);
        Item a = p.obtain(), b = p.obtain(), c = p.obtain();
        assertNotNull(a); assertNotNull(b); assertNotNull(c);
        assertEquals(3, p.size());
        assertNull(p.obtain());
        p.free(b);
        assertEquals(2, p.size());
        Item d = p.obtain();
        assertSame(b, d); // the freed object is reused
        assertNull(p.obtain());
    }

    @Test
    public void obtainGivesDistinctObjects() {
        Pool<Item> p = make(10, new int[1]);
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < 10; i++) assertTrue(seen.add(p.obtain()));
    }

    @Test
    public void forEachActiveVisitsOnlyActiveObjects() {
        Pool<Item> p = make(6, new int[1]);
        Item[] got = new Item[4];
        for (int i = 0; i < 4; i++) { got[i] = p.obtain(); got[i].id = i + 1; }
        p.free(got[1]);
        int[] sum = {0, 0};
        p.forEachActive(it -> { sum[0] += it.id; sum[1]++; });
        assertEquals(1 + 3 + 4, sum[0]);
        assertEquals(3, sum[1]);
    }

    @Test
    public void freeingBackwardsInALoopIsSafe() {
        Pool<Item> p = make(8, new int[1]);
        for (int i = 0; i < 8; i++) p.obtain().id = i;
        for (int i = p.size() - 1; i >= 0; i--) {
            if (p.at(i).id % 2 == 0) p.freeAt(i);
        }
        assertEquals(4, p.size());
        for (int i = 0; i < p.size(); i++) assertEquals(1, p.at(i).id % 2);
    }

    @Test
    public void freeingTwiceOrFreeingAStrangerIsIgnored() {
        Pool<Item> p = make(3, new int[1]);
        Item a = p.obtain();
        p.obtain();
        p.free(a);
        p.free(a);
        assertEquals(1, p.size());
        p.free(new Item());
        assertEquals(1, p.size());
        p.freeAt(5);
        p.freeAt(-1);
        assertEquals(1, p.size());
    }

    @Test
    public void clearGivesEverythingBack() {
        Pool<Item> p = make(4, new int[1]);
        p.obtain(); p.obtain(); p.obtain();
        p.clear();
        assertEquals(0, p.size());
        for (int i = 0; i < 4; i++) assertNotNull(p.obtain());
        assertNull(p.obtain());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void atRejectsInactiveIndexes() {
        Pool<Item> p = make(4, new int[1]);
        p.obtain();
        p.at(1);
    }
}
