package com.wiggleplay.app.gl;

/**
 * 4x4 matrix maths on {@code float[16]} in column-major order (element at column c, row r is
 * {@code m[c * 4 + r]}), the layout OpenGL expects. Every method writes into a caller-provided array and
 * allocates nothing. Coordinates: +Y up, right-handed; {@code rotY(a)} maps (x, z) to
 * (x cos a + z sin a, -x sin a + z cos a), so {@code rotY(90 degrees)} turns +Z into +X.
 */
public final class Mat4 {

    private Mat4() { }

    /**
     * Sets a matrix to the identity.
     * @param out the matrix
     */
    public static void identity(float[] out) {
        for (int i = 0; i < 16; i++) out[i] = 0f;
        out[0] = 1f;
        out[5] = 1f;
        out[10] = 1f;
        out[15] = 1f;
    }

    /**
     * Multiplies two matrices: {@code out = a * b} (b is applied first). {@code out} may be the same array
     * as {@code a} or {@code b}.
     * @param out the result
     * @param a left matrix
     * @param b right matrix
     */
    public static void multiply(float[] out, float[] a, float[] b) {
        float a0 = a[0], a1 = a[1], a2 = a[2], a3 = a[3];
        float a4 = a[4], a5 = a[5], a6 = a[6], a7 = a[7];
        float a8 = a[8], a9 = a[9], a10 = a[10], a11 = a[11];
        float a12 = a[12], a13 = a[13], a14 = a[14], a15 = a[15];
        for (int c = 0; c < 16; c += 4) {
            float b0 = b[c], b1 = b[c + 1], b2 = b[c + 2], b3 = b[c + 3];
            out[c] = a0 * b0 + a4 * b1 + a8 * b2 + a12 * b3;
            out[c + 1] = a1 * b0 + a5 * b1 + a9 * b2 + a13 * b3;
            out[c + 2] = a2 * b0 + a6 * b1 + a10 * b2 + a14 * b3;
            out[c + 3] = a3 * b0 + a7 * b1 + a11 * b2 + a15 * b3;
        }
    }

    /**
     * Sets a matrix to a translation.
     * @param out the matrix
     * @param x offset x
     * @param y offset y
     * @param z offset z
     */
    public static void translate(float[] out, float x, float y, float z) {
        identity(out);
        out[12] = x;
        out[13] = y;
        out[14] = z;
    }

    /**
     * Sets a matrix to a scale.
     * @param out the matrix
     * @param x scale x
     * @param y scale y
     * @param z scale z
     */
    public static void scale(float[] out, float x, float y, float z) {
        identity(out);
        out[0] = x;
        out[5] = y;
        out[10] = z;
    }

    /**
     * Sets a matrix to a rotation about the Y axis.
     * @param out the matrix
     * @param rad angle in radians
     */
    public static void rotY(float[] out, float rad) {
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        identity(out);
        out[0] = c;
        out[2] = -s;
        out[8] = s;
        out[10] = c;
    }

    /**
     * Sets a matrix to a rotation about the X axis.
     * @param out the matrix
     * @param rad angle in radians
     */
    public static void rotX(float[] out, float rad) {
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        identity(out);
        out[5] = c;
        out[6] = s;
        out[9] = -s;
        out[10] = c;
    }

    /**
     * Sets a matrix to a rotation about the Z axis.
     * @param out the matrix
     * @param rad angle in radians
     */
    public static void rotZ(float[] out, float rad) {
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        identity(out);
        out[0] = c;
        out[1] = s;
        out[4] = -s;
        out[5] = c;
    }

    /**
     * Sets a matrix to the rotation of a unit quaternion.
     * @param out the matrix
     * @param x quaternion x
     * @param y quaternion y
     * @param z quaternion z
     * @param w quaternion w
     */
    public static void fromQuat(float[] out, float x, float y, float z, float w) {
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
        identity(out);
        out[0] = 1f - 2f * (yy + zz);
        out[1] = 2f * (xy + wz);
        out[2] = 2f * (xz - wy);
        out[4] = 2f * (xy - wz);
        out[5] = 1f - 2f * (xx + zz);
        out[6] = 2f * (yz + wx);
        out[8] = 2f * (xz + wy);
        out[9] = 2f * (yz - wx);
        out[10] = 1f - 2f * (xx + yy);
    }

    /**
     * Sets a matrix to a perspective projection (OpenGL clip space, depth -1..1).
     * @param out the matrix
     * @param fovYDeg vertical field of view in degrees
     * @param aspect width divided by height
     * @param near near plane distance (greater than 0)
     * @param far far plane distance
     */
    public static void perspective(float[] out, float fovYDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovYDeg) * 0.5));
        for (int i = 0; i < 16; i++) out[i] = 0f;
        out[0] = f / aspect;
        out[5] = f;
        out[10] = (far + near) / (near - far);
        out[11] = -1f;
        out[14] = 2f * far * near / (near - far);
    }

    /**
     * Sets a matrix to a view matrix that looks from {@code eye} at {@code target}.
     * @param out the matrix
     * @param eye camera position (x,y,z)
     * @param target the point looked at (x,y,z)
     * @param up the up direction (x,y,z), usually 0,1,0
     */
    public static void lookAt(float[] out, float[] eye, float[] target, float[] up) {
        float fx = target[0] - eye[0], fy = target[1] - eye[1], fz = target[2] - eye[2];
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        float sx = fy * up[2] - fz * up[1];
        float sy = fz * up[0] - fx * up[2];
        float sz = fx * up[1] - fy * up[0];
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl;
        sy /= sl;
        sz /= sl;
        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;
        out[0] = sx;
        out[1] = ux;
        out[2] = -fx;
        out[3] = 0f;
        out[4] = sy;
        out[5] = uy;
        out[6] = -fy;
        out[7] = 0f;
        out[8] = sz;
        out[9] = uz;
        out[10] = -fz;
        out[11] = 0f;
        out[12] = -(sx * eye[0] + sy * eye[1] + sz * eye[2]);
        out[13] = -(ux * eye[0] + uy * eye[1] + uz * eye[2]);
        out[14] = fx * eye[0] + fy * eye[1] + fz * eye[2];
        out[15] = 1f;
    }

    /**
     * Transforms a point by the affine part of a matrix (no perspective divide).
     * @param out3 receives x,y,z
     * @param m the matrix
     * @param x point x
     * @param y point y
     * @param z point z
     */
    public static void transformPoint(float[] out3, float[] m, float x, float y, float z) {
        float ox = m[0] * x + m[4] * y + m[8] * z + m[12];
        float oy = m[1] * x + m[5] * y + m[9] * z + m[13];
        float oz = m[2] * x + m[6] * y + m[10] * z + m[14];
        out3[0] = ox;
        out3[1] = oy;
        out3[2] = oz;
    }

    /**
     * Inverts a rigid transform (rotation plus translation, no scale or shear). {@code out} may be the
     * same array as {@code m}.
     * @param out the inverse
     * @param m a rigid matrix
     */
    public static void invertRigid(float[] out, float[] m) {
        float r00 = m[0], r10 = m[1], r20 = m[2];
        float r01 = m[4], r11 = m[5], r21 = m[6];
        float r02 = m[8], r12 = m[9], r22 = m[10];
        float tx = m[12], ty = m[13], tz = m[14];
        out[0] = r00;
        out[1] = r01;
        out[2] = r02;
        out[3] = 0f;
        out[4] = r10;
        out[5] = r11;
        out[6] = r12;
        out[7] = 0f;
        out[8] = r20;
        out[9] = r21;
        out[10] = r22;
        out[11] = 0f;
        out[12] = -(r00 * tx + r10 * ty + r20 * tz);
        out[13] = -(r01 * tx + r11 * ty + r21 * tz);
        out[14] = -(r02 * tx + r12 * ty + r22 * tz);
        out[15] = 1f;
    }
}
