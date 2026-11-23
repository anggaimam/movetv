package com.wiggleplay.app.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Draws the 3D world with OpenGL ES 2.0 on the GL thread. The main thread talks to it through the
 * synchronized setters ({@link #setSky}, {@link #setFog}, {@link #setViewports}, {@link #setCamera},
 * {@link #submit}); meshes and static chunks arrive through {@code queueEvent}. {@link #onDrawFrame} does not
 * allocate (everything is preallocated); uploads of new meshes, the shader and the texture happen on the first
 * frame that needs them.
 *
 * <p>Draw order per viewport: clear (sky colour and depth), static chunks (frustum culled, near to far),
 * opaque dynamic items, then translucent flat items (depth test on, depth write off, blending on).
 * A flat item is translucent when bits 24..31 of its colour hold an alpha from 1 to 254; 0 and 255 mean opaque.
 */
public final class GlRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "WigglePlayGL";
    static final float NEAR = 0.3f;
    static final float FAR = 60f;
    private static final float[] UP = {0f, 1f, 0f};
    private static final float[] IDENTITY = new float[16];
    private static final float SUN_LEN = (float) Math.sqrt(Shaders.SUN_X * Shaders.SUN_X
            + Shaders.SUN_Y * Shaders.SUN_Y + Shaders.SUN_Z * Shaders.SUN_Z);

    static {
        Mat4.identity(IDENTITY);
    }

    // ---- <plain-java> (no Android classes inside: the unit tests extract and run this part) ----

    /** Opacity of an item from its colour: bits 24..31 are the alpha, where 0 and 255 both mean fully opaque. */
    static float alphaOf(int colorRgb) {
        int a = (colorRgb >>> 24) & 0xFF;
        return (a == 0 || a == 255) ? 1f : a / 255f;
    }

    /**
     * Hands render lists from the main thread to the GL thread without allocating: {@link #submit} copies into
     * a back list under a lock; {@link #acquire} (GL thread) swaps in the newest submitted list.
     */
    static final class DoubleBuffer {
        private final Object lock = new Object();
        private RenderList front;
        private RenderList back;
        private boolean fresh;

        DoubleBuffer(int capacity) {
            front = new RenderList(capacity);
            back = new RenderList(capacity);
        }

        /**
         * Copies a list into the back buffer; the newest submission wins. Main thread.
         * @param src the list to copy (not kept)
         */
        void submit(RenderList src) {
            synchronized (lock) {
                back.copyFrom(src);
                fresh = true;
            }
        }

        /**
         * The list to draw: the newest submitted one, or the previous one if nothing new came. GL thread only;
         * the returned list stays valid and unchanged until the next call.
         * @return the list
         */
        RenderList acquire() {
            synchronized (lock) {
                if (fresh) {
                    RenderList t = front;
                    front = back;
                    back = t;
                    fresh = false;
                }
                return front;
            }
        }

        /** Empties both lists. */
        void clear() {
            synchronized (lock) {
                front.begin();
                back.begin();
                fresh = false;
            }
        }
    }

    /** The six planes of a view frustum, for culling spheres. */
    static final class Frustum {
        private final float[] p = new float[24];

        /**
         * Reads the planes out of a combined projection * view matrix (column-major).
         * @param m the matrix
         */
        void set(float[] m) {
            plane(0, m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12]);   // left
            plane(1, m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12]);   // right
            plane(2, m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13]);   // bottom
            plane(3, m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13]);   // top
            plane(4, m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14]);  // near
            plane(5, m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14]);  // far
        }

        private void plane(int i, float a, float b, float c, float d) {
            float len = (float) Math.sqrt(a * a + b * b + c * c);
            int o = i * 4;
            p[o] = a / len;
            p[o + 1] = b / len;
            p[o + 2] = c / len;
            p[o + 3] = d / len;
        }

        /**
         * Whether a sphere is at least partly inside.
         * @param x centre x
         * @param y centre y
         * @param z centre z
         * @param r radius
         * @return false if the sphere is entirely outside one plane
         */
        boolean sphereVisible(float x, float y, float z, float r) {
            for (int i = 0; i < 24; i += 4) {
                if (p[i] * x + p[i + 1] * y + p[i + 2] * z + p[i + 3] < -r) return false;
            }
            return true;
        }
    }

    // ---- </plain-java> ----

    private final Context context;
    private final GlStats stats;

    // shared with the main thread (guarded by lock)
    private final Object lock = new Object();
    private final DoubleBuffer items = new DoubleBuffer(1024);
    private int skyRgb = 0xA8DDF2;
    private float fogNear = 25f, fogFar = 55f;
    private int viewports = 1;
    private final float[][] camEye = {{0f, 3f, -6f}, {0f, 3f, -6f}};
    private final float[][] camTarget = {{0f, 1f, 4f}, {0f, 1f, 4f}};
    private final float[] camFov = {60f, 60f};

    // GL thread only
    private final float[][] snapEye = new float[2][3];
    private final float[][] snapTarget = new float[2][3];
    private final float[] snapFov = new float[2];
    private int surfaceW, surfaceH;
    private int program, texture;
    private boolean programFailed;
    private int aPos, aNormal, aUv;
    private int uMvp, uModel, uSunDir, uEye, uFogNear, uFogFar, uTex, uFlat, uColor, uFogColor, uAlpha;
    private GlMesh[] meshes = new GlMesh[0];
    private GlMesh[] chunks = new GlMesh[0];
    private float[] spheres = new float[0];
    private int[] order = new int[0];
    private float[] dist = new float[0];
    private final float[] proj = new float[16], view = new float[16], vp = new float[16];
    private final float[] mvp = new float[16], model = new float[16];
    private final Frustum frustum = new Frustum();
    private int cull = -1;

    /**
     * Creates the renderer.
     * @param context any context (the palette texture is read from its assets)
     * @param stats where the speed numbers go
     */
    GlRenderer(Context context, GlStats stats) {
        this.context = context;
        this.stats = stats;
    }

    // ---- main thread ----

    void setSky(int rgb) {
        synchronized (lock) {
            skyRgb = rgb;
        }
    }

    void setFog(float near, float far) {
        synchronized (lock) {
            fogNear = near;
            fogFar = Math.max(far, near + 0.01f);
        }
    }

    void setViewports(int n) {
        synchronized (lock) {
            viewports = n;
        }
    }

    void setCamera(int viewport, float[] eye, float[] target, float fovYDeg) {
        synchronized (lock) {
            System.arraycopy(eye, 0, camEye[viewport], 0, 3);
            System.arraycopy(target, 0, camTarget[viewport], 0, 3);
            camFov[viewport] = fovYDeg;
        }
    }

    void submit(RenderList list) {
        items.submit(list);
    }

    // ---- GL thread, called through queueEvent ----

    void setMesh(int id, GlMesh mesh) {
        if (id >= meshes.length) meshes = java.util.Arrays.copyOf(meshes, Math.max(id + 1, meshes.length * 2));
        if (meshes[id] != null && meshes[id].isUploaded()) meshes[id].release();
        meshes[id] = mesh;
    }

    void setChunks(GlMesh[] newChunks, float[] newSpheres) {
        for (GlMesh m : chunks) if (m != null) m.release();
        chunks = newChunks;
        spheres = newSpheres;
        order = new int[newChunks.length];
        dist = new float[newChunks.length];
    }

    /** Frees every GPU object (buffers, texture, shader) and forgets all meshes. */
    void releaseGl() {
        for (GlMesh m : meshes) if (m != null) m.release();
        for (GlMesh m : chunks) if (m != null) m.release();
        meshes = new GlMesh[0];
        chunks = new GlMesh[0];
        spheres = new float[0];
        order = new int[0];
        dist = new float[0];
        if (texture != 0) {
            GLES20.glDeleteTextures(1, new int[] {texture}, 0);
            texture = 0;
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        programFailed = false;
        items.clear();
        stats.reset();
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // A new context: every GPU object of the old one is gone. Everything is rebuilt lazily.
        program = 0;
        texture = 0;
        programFailed = false;
        for (GlMesh m : meshes) if (m != null) m.invalidate();
        for (GlMesh m : chunks) if (m != null) m.invalidate();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        surfaceW = width;
        surfaceH = height;
        stats.reset();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long t0 = System.nanoTime();
        if (surfaceW <= 0 || surfaceH <= 0) return;
        if (!ensureProgram()) {
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            return;
        }
        ensureTexture();
        for (GlMesh m : meshes) if (m != null && !m.isUploaded()) m.upload();
        for (GlMesh m : chunks) if (m != null && !m.isUploaded()) m.upload();

        int sky, nv;
        float fn, ff;
        RenderList list;
        synchronized (lock) {
            sky = skyRgb;
            fn = fogNear;
            ff = fogFar;
            nv = viewports;
            for (int v = 0; v < 2; v++) {
                System.arraycopy(camEye[v], 0, snapEye[v], 0, 3);
                System.arraycopy(camTarget[v], 0, snapTarget[v], 0, 3);
                snapFov[v] = camFov[v];
            }
            list = items.acquire();
        }
        float sr = ((sky >> 16) & 0xFF) / 255f, sg = ((sky >> 8) & 0xFF) / 255f, sb = (sky & 0xFF) / 255f;

        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform3f(uSunDir, Shaders.SUN_X / SUN_LEN, Shaders.SUN_Y / SUN_LEN, Shaders.SUN_Z / SUN_LEN);
        GLES20.glUniform3f(uFogColor, sr, sg, sb);
        GLES20.glUniform1f(uFogNear, fn);
        GLES20.glUniform1f(uFogFar, ff);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);
        cull = -1;

        int tris = 0, draws = 0;
        int count = list.count();
        for (int v = 0; v < nv; v++) {
            int vw = surfaceW / nv;
            int vx = v * vw;
            if (v == nv - 1) vw = surfaceW - vx;
            GLES20.glViewport(vx, 0, vw, surfaceH);
            GLES20.glScissor(vx, 0, vw, surfaceH);
            GLES20.glDepthMask(true);
            GLES20.glClearColor(sr, sg, sb, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            float[] eye = snapEye[v];
            Mat4.perspective(proj, snapFov[v], vw / (float) surfaceH, NEAR, FAR);
            Mat4.lookAt(view, eye, snapTarget[v], UP);
            Mat4.multiply(vp, proj, view);
            frustum.set(vp);
            GLES20.glUniform3f(uEye, eye[0], eye[1], eye[2]);

            // static chunks: visible ones, nearest first
            GLES20.glUniform1f(uFlat, 0f);
            GLES20.glUniform1f(uAlpha, 1f);
            GLES20.glUniformMatrix4fv(uModel, 1, false, IDENTITY, 0);
            GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0);
            int visible = 0;
            for (int c = 0; c < chunks.length; c++) {
                int s = c * 4;
                if (chunks[c] == null || !chunks[c].isUploaded()) continue;
                if (!frustum.sphereVisible(spheres[s], spheres[s + 1], spheres[s + 2], spheres[s + 3])) continue;
                float dx = spheres[s] - eye[0], dy = spheres[s + 1] - eye[1], dz = spheres[s + 2] - eye[2];
                float d2 = dx * dx + dy * dy + dz * dz;
                int j = visible++;
                while (j > 0 && dist[j - 1] > d2) {
                    dist[j] = dist[j - 1];
                    order[j] = order[j - 1];
                    j--;
                }
                dist[j] = d2;
                order[j] = c;
            }
            for (int k = 0; k < visible; k++) {
                GlMesh m = chunks[order[k]];
                setCull(!m.doubleSided());
                tris += m.draw(0, aPos, aNormal, aUv);
                draws++;
            }

            // dynamic items: opaque first, then the translucent flat ones
            int bit = 1 << v;
            for (int pass = 0; pass < 2; pass++) {
                boolean alphaPass = pass == 1;
                if (alphaPass) {
                    GLES20.glEnable(GLES20.GL_BLEND);
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                    GLES20.glDepthMask(false);
                }
                for (int i = 0; i < count; i++) {
                    if ((list.viewportMask(i) & bit) == 0) continue;
                    int color = list.colorRgb(i);
                    boolean flat = list.flat(i);
                    float alpha = flat ? alphaOf(color) : 1f;
                    if ((alpha < 1f) != alphaPass) continue;
                    int id = list.meshId(i);
                    if (id < 0 || id >= meshes.length) continue;
                    GlMesh m = meshes[id];
                    if (m == null || !m.isUploaded()) continue;
                    System.arraycopy(list.models(), list.modelOffset(i), model, 0, 16);
                    Mat4.multiply(mvp, vp, model);
                    GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
                    GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
                    if (flat) {
                        GLES20.glUniform1f(uFlat, 1f);
                        GLES20.glUniform3f(uColor, ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
                    } else {
                        GLES20.glUniform1f(uFlat, 0f);
                    }
                    GLES20.glUniform1f(uAlpha, alpha);
                    setCull(!m.doubleSided());
                    tris += m.draw(list.part(i), aPos, aNormal, aUv);
                    draws++;
                }
            }
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glDepthMask(true);
        }
        long now = System.nanoTime();
        stats.frameDone(now, now - t0, tris, draws);
    }

    private void setCull(boolean on) {
        int want = on ? 1 : 0;
        if (cull == want) return;
        cull = want;
        if (on) GLES20.glEnable(GLES20.GL_CULL_FACE);
        else GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    private boolean ensureProgram() {
        if (program != 0) return true;
        if (programFailed) return false;
        int vs = compile(GLES20.GL_VERTEX_SHADER, Shaders.VERTEX);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, Shaders.FRAGMENT);
        if (vs == 0 || fs == 0) {
            programFailed = true;
            return false;
        }
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, vs);
        GLES20.glAttachShader(p, fs);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        if (ok[0] == 0) {
            Log.e(TAG, "Shader link failed: " + GLES20.glGetProgramInfoLog(p));
            GLES20.glDeleteProgram(p);
            programFailed = true;
            return false;
        }
        program = p;
        aPos = GLES20.glGetAttribLocation(p, "aPos");
        aNormal = GLES20.glGetAttribLocation(p, "aNormal");
        aUv = GLES20.glGetAttribLocation(p, "aUv");
        uMvp = GLES20.glGetUniformLocation(p, "uMvp");
        uModel = GLES20.glGetUniformLocation(p, "uModel");
        uSunDir = GLES20.glGetUniformLocation(p, "uSunDir");
        uEye = GLES20.glGetUniformLocation(p, "uEye");
        uFogNear = GLES20.glGetUniformLocation(p, "uFogNear");
        uFogFar = GLES20.glGetUniformLocation(p, "uFogFar");
        uTex = GLES20.glGetUniformLocation(p, "uTex");
        uFlat = GLES20.glGetUniformLocation(p, "uFlat");
        uColor = GLES20.glGetUniformLocation(p, "uColor");
        uFogColor = GLES20.glGetUniformLocation(p, "uFogColor");
        uAlpha = GLES20.glGetUniformLocation(p, "uAlpha");
        GLES20.glUseProgram(p);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aNormal);
        GLES20.glEnableVertexAttribArray(aUv);
        return true;
    }

    private static int compile(int type, String source) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, source);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e(TAG, "Shader compile failed: " + GLES20.glGetShaderInfoLog(s));
            GLES20.glDeleteShader(s);
            return 0;
        }
        return s;
    }

    /** The palette atlas: nearest filtering, no mipmaps, clamped. Uploaded once. */
    private void ensureTexture() {
        if (texture != 0) return;
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        texture = t[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = context.getAssets().open("art/race/colormap.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (b != null) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, b, 0);
                b.recycle();
            } else {
                Log.e(TAG, "colormap.png could not be decoded");
            }
        } catch (IOException e) {
            Log.e(TAG, "colormap.png could not be read: " + e.getMessage());
        }
    }
}
