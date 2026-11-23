package com.wiggleplay.app.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The plain-Java parts of the GL renderer (the render list hand-over between the main thread and the GL
 * thread, the alpha rule and the frustum test). The OpenGL drawing itself can only be checked on a device.
 */
public class RenderListSwapTest {

    private static final float[] M = new float[16];

    static {
        Mat4.identity(M);
    }

    private static RenderList list(int n, int marker) {
        RenderList l = new RenderList(16);
        for (int i = 0; i < n; i++) l.add(marker, i, M, 0, false, 1);
        return l;
    }

    @Test
    public void startsEmpty() {
        GlRenderer.DoubleBuffer b = new GlRenderer.DoubleBuffer(16);
        assertEquals(0, b.acquire().count());
    }

    @Test
    public void acquireReturnsTheSubmittedContent() {
        GlRenderer.DoubleBuffer b = new GlRenderer.DoubleBuffer(16);
        b.submit(list(3, 7));
        RenderList got = b.acquire();
        assertEquals(3, got.count());
        assertEquals(7, got.meshId(0));
        assertEquals(2, got.part(2));
    }

    @Test
    public void withoutANewSubmitTheSameListComesBack() {
        GlRenderer.DoubleBuffer b = new GlRenderer.DoubleBuffer(16);
        b.submit(list(2, 1));
        RenderList first = b.acquire();
        assertSame(first, b.acquire());
        assertEquals(2, first.count());
    }

    @Test
    public void theNewestSubmitWins() {
        GlRenderer.DoubleBuffer b = new GlRenderer.DoubleBuffer(16);
        b.submit(list(2, 1));
        b.submit(list(5, 2));
        RenderList got = b.acquire();
        assertEquals(5, got.count());
        assertEquals(2, got.meshId(0));
    }

    @Test
    public void aListBeingDrawnIsNotChangedByALaterSubmit() {
        GlRenderer.DoubleBuffer b = new GlRenderer.DoubleBuffer(16);
        b.submit(list(2, 1));
        RenderList drawing = b.acquire();
        b.submit(list(4, 9)); // arrives while the GL thread still draws `drawing`
        assertEquals(2, drawing.count());
        assertEquals(1, drawing.meshId(0));
        RenderList next = b.acquire();
        assertNotSame(drawing, next);
        assertEquals(4, next.count());
    }

    @Test
    public void theSourceListMayBeReusedRightAfterSubmit() {
        GlRenderer.DoubleBuffer b = new GlRenderer.DoubleBuffer(16);
        RenderList src = list(3, 5);
        b.submit(src);
        src.begin();
        src.add(99, 0, M, 0, false, 1);
        assertEquals(3, b.acquire().count());
    }

    @Test
    public void clearEmptiesBothLists() {
        GlRenderer.DoubleBuffer b = new GlRenderer.DoubleBuffer(16);
        b.submit(list(3, 1));
        b.acquire();
        b.submit(list(2, 1));
        b.clear();
        assertEquals(0, b.acquire().count());
    }

    @Test
    public void aReaderNeverSeesAHalfWrittenList() throws Exception {
        final GlRenderer.DoubleBuffer b = new GlRenderer.DoubleBuffer(16);
        final boolean[] stop = {false};
        final String[] failure = {null};
        Thread writer = new Thread(new Runnable() {
            @Override
            public void run() {
                RenderList l = new RenderList(16);
                for (int n = 1; n < 20000 && !stop[0]; n++) {
                    int k = 1 + n % 10; // every item of a list carries the list's size as its mesh id
                    l.begin();
                    for (int i = 0; i < k; i++) l.add(k, i, M, 0, false, 1);
                    b.submit(l);
                }
            }
        });
        writer.start();
        for (int n = 0; n < 20000; n++) {
            RenderList got = b.acquire();
            for (int i = 0; i < got.count(); i++) {
                if (got.meshId(i) != got.count()) failure[0] = "item " + i + " says " + got.meshId(i) + " but count is " + got.count();
            }
        }
        stop[0] = true;
        writer.join();
        assertEquals(null, failure[0]);
    }

    @Test
    public void alphaRule() {
        assertEquals(1f, GlRenderer.alphaOf(0x112233), 0f);          // plain 0xRRGGBB: opaque
        assertEquals(1f, GlRenderer.alphaOf(0xFF112233), 0f);        // alpha 255: opaque
        assertEquals(89f / 255f, GlRenderer.alphaOf(0x59112233), 1e-6f); // 0x59 = 89, about 0.35
        assertEquals(1f / 255f, GlRenderer.alphaOf(0x01112233), 1e-6f);
        assertEquals(254f / 255f, GlRenderer.alphaOf(0xFE112233), 1e-6f);
    }

    private static GlRenderer.Frustum frustumLookingDownMinusZ() {
        float[] proj = new float[16], view = new float[16], vp = new float[16];
        Mat4.perspective(proj, 90f, 1f, 1f, 100f);
        Mat4.lookAt(view, new float[] {0, 0, 0}, new float[] {0, 0, -1}, new float[] {0, 1, 0});
        Mat4.multiply(vp, proj, view);
        GlRenderer.Frustum f = new GlRenderer.Frustum();
        f.set(vp);
        return f;
    }

    @Test
    public void frustumSeesWhatIsInFront() {
        GlRenderer.Frustum f = frustumLookingDownMinusZ();
        assertTrue(f.sphereVisible(0, 0, -10, 1));
        assertTrue(f.sphereVisible(5, 3, -10, 1));
    }

    @Test
    public void frustumRejectsBehindFarAndSides() {
        GlRenderer.Frustum f = frustumLookingDownMinusZ();
        assertFalse(f.sphereVisible(0, 0, 10, 1));      // behind the camera
        assertFalse(f.sphereVisible(0, 0, -200, 1));    // beyond the far plane
        assertFalse(f.sphereVisible(50, 0, -10, 1));    // far to the side (the edge is at x = 10)
        assertFalse(f.sphereVisible(0, -50, -10, 1));
        assertFalse(f.sphereVisible(0, 0, -0.2f, 0.1f)); // in front of the near plane
    }

    @Test
    public void frustumKeepsASphereThatTouchesAnEdge() {
        GlRenderer.Frustum f = frustumLookingDownMinusZ();
        assertTrue(f.sphereVisible(11, 0, -10, 2));  // centre outside by 1, radius 2: partly inside
        assertFalse(f.sphereVisible(13, 0, -10, 2)); // centre outside by 3: out
        assertTrue(f.sphereVisible(0, 0, -100.5f, 1)); // straddles the far plane
    }

    @Test
    public void frustumFollowsTheCamera() {
        // Chase camera behind a truck looking along +Z: a point ahead is visible, one behind is not.
        float[] proj = new float[16], view = new float[16], vp = new float[16];
        Mat4.perspective(proj, 60f, 16f / 9f, GlRenderer.NEAR, GlRenderer.FAR);
        Mat4.lookAt(view, new float[] {0, 3.2f, -6.5f}, new float[] {0, 0.8f, 4f}, new float[] {0, 1, 0});
        Mat4.multiply(vp, proj, view);
        GlRenderer.Frustum f = new GlRenderer.Frustum();
        f.set(vp);
        assertTrue(f.sphereVisible(0, 0, 20, 1));
        assertFalse(f.sphereVisible(0, 0, -40, 1));
        assertFalse(f.sphereVisible(0, 0, 120, 1));
    }
}
