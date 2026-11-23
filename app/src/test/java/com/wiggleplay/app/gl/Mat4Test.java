package com.wiggleplay.app.gl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Mat4Test {

    private static final float EPS = 1e-5f;

    private static float[] pt(float[] m, float x, float y, float z) {
        float[] o = new float[3];
        Mat4.transformPoint(o, m, x, y, z);
        return o;
    }

    @Test
    public void identityLeavesPointsAlone() {
        float[] m = new float[16];
        Mat4.identity(m);
        assertArrayEquals(new float[] {1, 2, 3}, pt(m, 1, 2, 3), EPS);
    }

    @Test
    public void rotY90MapsPlusZToPlusX() {
        float[] m = new float[16];
        Mat4.rotY(m, (float) Math.toRadians(90));
        assertArrayEquals(new float[] {1, 0, 0}, pt(m, 0, 0, 1), EPS);
        assertArrayEquals(new float[] {0, 0, -1}, pt(m, 1, 0, 0), EPS);
    }

    @Test
    public void rotYMatchesTheDocumentedFormula() {
        float[] m = new float[16];
        float a = 0.7f;
        Mat4.rotY(m, a);
        float x = 2f, z = -3f;
        float[] o = pt(m, x, 5f, z);
        assertEquals(x * Math.cos(a) + z * Math.sin(a), o[0], EPS);
        assertEquals(5f, o[1], EPS);
        assertEquals(-x * Math.sin(a) + z * Math.cos(a), o[2], EPS);
    }

    @Test
    public void rotXAndRotZ() {
        float[] m = new float[16];
        Mat4.rotX(m, (float) Math.toRadians(90));
        assertArrayEquals(new float[] {0, 0, 1}, pt(m, 0, 1, 0), EPS);
        Mat4.rotZ(m, (float) Math.toRadians(90));
        assertArrayEquals(new float[] {0, 1, 0}, pt(m, 1, 0, 0), EPS);
    }

    @Test
    public void translateAndScale() {
        float[] m = new float[16];
        Mat4.translate(m, 1, 2, 3);
        assertArrayEquals(new float[] {2, 3, 4}, pt(m, 1, 1, 1), EPS);
        Mat4.scale(m, 2, 3, 4);
        assertArrayEquals(new float[] {2, 3, 4}, pt(m, 1, 1, 1), EPS);
    }

    @Test
    public void multiplyAppliesTheRightMatrixFirst() {
        float[] t = new float[16], r = new float[16], m = new float[16];
        Mat4.translate(t, 10, 0, 0);
        Mat4.rotY(r, (float) Math.toRadians(90));
        Mat4.multiply(m, t, r); // rotate first, then move
        assertArrayEquals(new float[] {11, 0, 0}, pt(m, 0, 0, 1), EPS);
        Mat4.multiply(m, r, t); // move first, then rotate: (10,0,0) -> (0,0,-10)
        assertArrayEquals(new float[] {0, 0, -10}, pt(m, 0, 0, 0), EPS);
    }

    @Test
    public void multiplyMayWriteIntoItsOwnInput() {
        float[] t = new float[16], r = new float[16], expect = new float[16];
        Mat4.translate(t, 1, 2, 3);
        Mat4.rotY(r, 0.5f);
        Mat4.multiply(expect, t, r);
        float[] a = t.clone();
        Mat4.multiply(a, a, r);
        assertArrayEquals(expect, a, EPS);
        float[] b = r.clone();
        Mat4.multiply(b, t, b);
        assertArrayEquals(expect, b, EPS);
    }

    @Test
    public void fromQuatMatchesRotY() {
        float[] q = new float[16], r = new float[16];
        float half = (float) Math.toRadians(90) * 0.5f;
        Mat4.fromQuat(q, 0, (float) Math.sin(half), 0, (float) Math.cos(half));
        Mat4.rotY(r, (float) Math.toRadians(90));
        assertArrayEquals(r, q, EPS);
    }

    @Test
    public void perspectiveKnownValues() {
        float[] m = new float[16];
        Mat4.perspective(m, 90f, 2f, 1f, 3f); // f = 1
        assertEquals(0.5f, m[0], EPS);
        assertEquals(1f, m[5], EPS);
        assertEquals(-2f, m[10], EPS);
        assertEquals(-1f, m[11], EPS);
        assertEquals(-3f, m[14], EPS);
        assertEquals(0f, m[15], EPS);
        // a point on the near plane maps to depth -1, on the far plane to +1
        assertEquals(-1f, clipDepth(m, -1f), EPS);
        assertEquals(1f, clipDepth(m, -3f), EPS);
    }

    private static float clipDepth(float[] m, float z) {
        float zc = m[10] * z + m[14];
        float wc = m[11] * z;
        return zc / wc;
    }

    @Test
    public void lookAtFromBehindIsAPureTranslation() {
        float[] v = new float[16];
        Mat4.lookAt(v, new float[] {0, 0, 5}, new float[] {0, 0, 0}, new float[] {0, 1, 0});
        assertArrayEquals(new float[] {0, 0, -5}, pt(v, 0, 0, 0), EPS);
        assertArrayEquals(new float[] {1, 2, -5}, pt(v, 1, 2, 0), EPS);
    }

    @Test
    public void lookAtFromTheSideTurnsTheWorld() {
        float[] v = new float[16];
        Mat4.lookAt(v, new float[] {5, 0, 0}, new float[] {0, 0, 0}, new float[] {0, 1, 0});
        assertArrayEquals(new float[] {0, 0, -5}, pt(v, 0, 0, 0), EPS);
        // the camera looks along -X; world +Z is then on the camera's right? (right = forward x up = -X x Y = -Z)
        // so world +Z is on the left: camera x = -1
        assertArrayEquals(new float[] {-1, 0, -5}, pt(v, 0, 0, 1), EPS);
        assertArrayEquals(new float[] {0, 1, -5}, pt(v, 0, 1, 0), EPS);
    }

    @Test
    public void chaseCameraBehindATruckSeesPlusXOnTheLeft() {
        // Looking along +Z from behind: screen-right is -X (see the coordinate rules in the design).
        float[] v = new float[16];
        Mat4.lookAt(v, new float[] {0, 3, -6}, new float[] {0, 1, 4}, new float[] {0, 1, 0});
        float[] right = pt(v, -1, 0, 0);
        float[] origin = pt(v, 0, 0, 0);
        assertEquals(true, right[0] > origin[0]);
    }

    @Test
    public void invertRigidUndoesTheTransform() {
        float[] r = new float[16], t = new float[16], m = new float[16], inv = new float[16], id = new float[16];
        Mat4.rotY(r, 0.9f);
        Mat4.translate(t, 3, -2, 7);
        Mat4.multiply(m, t, r);
        Mat4.invertRigid(inv, m);
        Mat4.multiply(id, inv, m);
        float[] ident = new float[16];
        Mat4.identity(ident);
        assertArrayEquals(ident, id, EPS);
        float[] self = m.clone();
        Mat4.invertRigid(self, self);
        assertArrayEquals(inv, self, EPS);
    }
}
