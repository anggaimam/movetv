package com.wiggleplay.app.gl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

public class RenderListTest {

    private static float[] matrix(float tx) {
        float[] m = new float[16];
        Mat4.translate(m, tx, 0, 0);
        return m;
    }

    @Test
    public void addStoresEveryFieldAndCopiesTheMatrix() {
        RenderList l = new RenderList(4);
        float[] m = matrix(5);
        assertTrue(l.add(3, 2, m, 0x112233, true, 2));
        m[12] = 99f; // changing the caller's array must not change the item
        assertEquals(1, l.count());
        assertEquals(3, l.meshId(0));
        assertEquals(2, l.part(0));
        assertEquals(0x112233, l.colorRgb(0));
        assertTrue(l.flat(0));
        assertEquals(2, l.viewportMask(0));
        assertEquals(5f, l.models()[l.modelOffset(0) + 12], 0f);
    }

    @Test
    public void fullListRefusesAndKeepsItsItems() {
        RenderList l = new RenderList(2);
        assertTrue(l.add(0, 0, matrix(0), 0, false, 1));
        assertTrue(l.add(1, 0, matrix(1), 0, false, 1));
        assertFalse(l.add(2, 0, matrix(2), 0, false, 1));
        assertEquals(2, l.count());
        assertEquals(1, l.meshId(1));
        assertEquals(2, l.capacity());
    }

    @Test
    public void beginEmptiesTheListAndItCanBeFilledAgain() {
        RenderList l = new RenderList(2);
        l.add(0, 0, matrix(0), 0, false, 1);
        l.add(0, 0, matrix(0), 0, false, 1);
        l.begin();
        assertEquals(0, l.count());
        assertTrue(l.add(7, 1, matrix(0), 0, false, 1));
        assertEquals(7, l.meshId(0));
    }

    @Test
    public void copyFromCopiesAllItems() {
        RenderList a = new RenderList(8), b = new RenderList(8);
        a.add(1, 2, matrix(3), 0xABCDEF, true, 3);
        a.add(4, 5, matrix(6), 0x010203, false, 1);
        b.add(9, 9, matrix(9), 0, false, 1);
        b.copyFrom(a);
        assertEquals(2, b.count());
        for (int i = 0; i < 2; i++) {
            assertEquals(a.meshId(i), b.meshId(i));
            assertEquals(a.part(i), b.part(i));
            assertEquals(a.colorRgb(i), b.colorRgb(i));
            assertEquals(a.flat(i), b.flat(i));
            assertEquals(a.viewportMask(i), b.viewportMask(i));
            float[] ma = new float[16], mb = new float[16];
            System.arraycopy(a.models(), a.modelOffset(i), ma, 0, 16);
            System.arraycopy(b.models(), b.modelOffset(i), mb, 0, 16);
            assertArrayEquals(ma, mb, 0f);
        }
    }

    @Test
    public void copyFromDropsWhatDoesNotFit() {
        RenderList a = new RenderList(4), b = new RenderList(2);
        for (int i = 0; i < 4; i++) a.add(i, 0, matrix(i), 0, false, 1);
        b.copyFrom(a);
        assertEquals(2, b.count());
    }

    /** Bytes allocated so far by this thread, or -1 when the JVM cannot tell (looked up by reflection: the Android compile classpath has no java.lang.management). */
    private static long allocatedBytes() {
        try {
            Class<?> mf = Class.forName("java.lang.management.ManagementFactory");
            Object bean = mf.getMethod("getThreadMXBean").invoke(null);
            Class<?> sun = Class.forName("com.sun.management.ThreadMXBean");
            if (!sun.isInstance(bean)) return -1;
            sun.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(bean, true);
            return (Long) sun.getMethod("getThreadAllocatedBytes", long.class).invoke(bean, Thread.currentThread().getId());
        } catch (Exception | LinkageError e) {
            return -1;
        }
    }

    @Test
    public void addBeginAndCopyDoNotAllocate() {
        Assume.assumeTrue("allocation counter not available", allocatedBytes() >= 0);
        RenderList a = new RenderList(512), b = new RenderList(512);
        float[] m = matrix(1);
        for (int w = 0; w < 200; w++) { // warm up the JIT
            a.begin();
            for (int i = 0; i < 512; i++) a.add(i, 0, m, 0, false, 1);
            b.copyFrom(a);
        }
        long before = allocatedBytes();
        for (int w = 0; w < 200; w++) {
            a.begin();
            for (int i = 0; i < 512; i++) a.add(i, 0, m, 0, false, 1);
            b.copyFrom(a);
        }
        long used = allocatedBytes() - before;
        assertTrue("allocated " + used + " bytes", used < 2048);
    }
}
