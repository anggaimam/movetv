package com.wiggleplay.app.gl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.View;

import com.wiggleplay.app.core.GameView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * The OpenGL ES 2.0 surface that sits under the Canvas {@link GameView}. It is hidden ({@code GONE}) except
 * while a scene returns {@code world3d() == true}. It renders at a fixed 1280x720 (960x540 when the game runs in
 * LOW quality) and the system scales that up to a 16:9 box that is centred in the window, exactly where the
 * stage's letterbox puts the 1920x1080 stage. Drawing is on the GL thread, one frame per {@link #submit}.
 *
 * <p>All methods are for the main thread. Meshes get ids in the order they are loaded (see {@link #meshId});
 * a {@link RenderList} item refers to a mesh by that id. In a render list, a flat item (drawn with its colour)
 * is translucent when bits 24..31 of {@code colorRgb} hold an alpha from 1 to 254 (0 and 255 mean opaque);
 * its viewport mask must have bit 0 set to show in the left or only viewport and bit 1 for the right one.
 */
public final class RaceGlView extends GLSurfaceView {

    private static final String TAG = "WigglePlayGL";
    /** Render size in HIGH quality. */
    public static final int HIGH_W = 1280, HIGH_H = 720;
    /** Render size in LOW quality. */
    public static final int LOW_W = 960, LOW_H = 540;

    private final GlStats stats = new GlStats();
    private final GlRenderer renderer;
    private final Context appContext;
    private final HashMap<String, Integer> ids = new HashMap<>();
    private final ArrayList<MeshData> datas = new ArrayList<>();
    private int renderW = HIGH_W, renderH = HIGH_H;

    /**
     * Creates the view (hidden). Call {@link #onPause}/{@link #onResume} from the activity.
     * @param context any context
     */
    public RaceGlView(Context context) {
        this(context, false);
    }

    /**
     * Creates the view (hidden). With {@code high} the surface asks for 8-bit colour, 24-bit depth and 4x multisampling
     * (falling back to 2x, then none, then the old RGB565 chain); the choice is made once, when the surface is created.
     * @param context any context
     * @param high true when the Picture setting is not Low
     */
    public RaceGlView(Context context, boolean high) {
        super(context);
        appContext = context.getApplicationContext();
        renderer = new GlRenderer(appContext, stats);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(new Chooser(stats, high));
        setPreserveEGLContextOnPause(true);
        getHolder().setFixedSize(HIGH_W, HIGH_H);
        setFocusable(false);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setVisibility(View.GONE);
    }

    /** Takes the largest 16:9 box that fits, so the picture is letterboxed like the stage on any window shape. */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = View.MeasureSpec.getSize(widthSpec);
        int h = View.MeasureSpec.getSize(heightSpec);
        int mw = w;
        int mh = w * 9 / 16;
        if (mh > h) {
            mh = h;
            mw = h * 16 / 9;
        }
        setMeasuredDimension(mw, mh);
    }

    /** Shows the view at the render size of the current quality level. Called when a 3D scene becomes current. */
    public void show() {
        if (GameView.quality == GameView.LOW) setRenderSize(LOW_W, LOW_H);
        else setRenderSize(HIGH_W, HIGH_H);
        setVisibility(View.VISIBLE);
    }

    /** Hides the view. Its GPU objects stay until {@link #release()}. */
    public void hide() {
        setVisibility(View.GONE);
    }

    /**
     * Sets the size the world is rendered at; the system scales it to the view.
     * @param w width in pixels (1280 or 960)
     * @param h height in pixels (720 or 540)
     */
    public void setRenderSize(int w, int h) {
        if (w == renderW && h == renderH) return;
        renderW = w;
        renderH = h;
        getHolder().setFixedSize(w, h);
    }

    /**
     * Reads meshes from {@code assets/race/meshes/<name>.mesh} and registers them; already loaded names are skipped.
     * @param names mesh names, for example {@code vehicle-truck-yellow}
     * @throws IllegalStateException if a file is missing or broken
     */
    public void loadMeshes(String... names) {
        for (String name : names) {
            if (ids.containsKey(name)) continue;
            try (InputStream in = appContext.getAssets().open("race/meshes/" + name + ".mesh")) {
                addMesh(name, MeshData.read(in));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load mesh " + name + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Registers a mesh made in code (for example from {@link ProcMesh}); an already registered name keeps its first mesh.
     * @param name the name to find it by
     * @param data the mesh
     * @return the mesh id for {@link RenderList} items
     */
    public int addMesh(String name, MeshData data) {
        Integer have = ids.get(name);
        if (have != null) return have;
        final int id = datas.size();
        datas.add(data);
        ids.put(name, id);
        final GlMesh gm = GlMesh.of(data);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setMesh(id, gm);
            }
        });
        return id;
    }

    /**
     * Looks a mesh id up.
     * @param name the mesh name
     * @return the id, or -1 if it is not loaded
     */
    public int meshId(String name) {
        Integer i = ids.get(name);
        return i == null ? -1 : i;
    }

    /**
     * The model behind a mesh id (to read part names and node transforms).
     * @param id a mesh id
     * @return the mesh data, or null for an unknown id
     */
    public MeshData meshData(int id) {
        return id >= 0 && id < datas.size() ? datas.get(id) : null;
    }

    /**
     * Sets the static scenery: merges the placements into chunks (once) and uploads them on the GL thread.
     * The meshes the placements name must be loaded first. An empty list clears the scenery.
     * @param placements the scenery
     * @throws IllegalArgumentException if a placement names a mesh that is not loaded
     */
    public void setStatic(List<StaticBatcher.Placement> placements) {
        setStatic(placements, StaticBatcher.DEFAULT_CHUNK_CELLS);
    }

    /**
     * Like {@link #setStatic(List)} with a chosen block size in tile cells (the spline road uses 6 cells = 60 m).
     * @param placements the scenery
     * @param chunkCells block size in cells, at least 1
     */
    public void setStatic(List<StaticBatcher.Placement> placements, int chunkCells) {
        List<StaticBatcher.Chunk> built = StaticBatcher.build(placements, new StaticBatcher.MeshLibrary() {
            @Override
            public MeshData get(String name) {
                Integer i = ids.get(name);
                return i == null ? null : datas.get(i);
            }
        }, chunkCells);
        final GlMesh[] cm = new GlMesh[built.size()];
        final float[] sph = new float[built.size() * 4];
        for (int i = 0; i < cm.length; i++) {
            StaticBatcher.Chunk c = built.get(i);
            cm[i] = GlMesh.of(c);
            sph[i * 4] = c.cx;
            sph[i * 4 + 1] = c.cy;
            sph[i * 4 + 2] = c.cz;
            sph[i * 4 + 3] = c.radius;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setChunks(cm, sph);
            }
        });
    }

    /** Scenery merged into chunks off the GL thread, waiting for {@link #commitStatic}. */
    public static final class PreparedStatic {
        private final GlMesh[] meshes;
        private final float[] spheres;

        private PreparedStatic(GlMesh[] meshes, float[] spheres) {
            this.meshes = meshes;
            this.spheres = spheres;
        }
    }

    /**
     * Merges placements into chunks. It touches no view state (the meshes are given in {@code library}), so it can run on a
     * worker thread; hand the result to {@link #commitStatic} on the main thread.
     * @param placements the scenery
     * @param library every mesh the placements name, by name
     * @param chunkCells block size in cells, at least 1
     * @return the prepared chunks
     */
    public static PreparedStatic prepareStatic(List<StaticBatcher.Placement> placements, final java.util.Map<String, MeshData> library,
            int chunkCells) {
        List<StaticBatcher.Chunk> built = StaticBatcher.build(placements, new StaticBatcher.MeshLibrary() {
            @Override
            public MeshData get(String name) {
                return library.get(name);
            }
        }, chunkCells);
        GlMesh[] cm = new GlMesh[built.size()];
        float[] sph = new float[built.size() * 4];
        for (int i = 0; i < cm.length; i++) {
            StaticBatcher.Chunk c = built.get(i);
            cm[i] = GlMesh.of(c);
            sph[i * 4] = c.cx;
            sph[i * 4 + 1] = c.cy;
            sph[i * 4 + 2] = c.cz;
            sph[i * 4 + 3] = c.radius;
        }
        return new PreparedStatic(cm, sph);
    }

    /**
     * Uploads chunks made by {@link #prepareStatic} (on the GL thread, at the next frame).
     * @param p the prepared chunks
     */
    public void commitStatic(PreparedStatic p) {
        final GlMesh[] cm = p.meshes;
        final float[] sph = p.spheres;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setChunks(cm, sph);
            }
        });
    }

    /**
     * Sets the hemisphere light: what the sky adds to surfaces facing up, what the ground adds to surfaces facing down, and the
     * colour of the sun. Each is r, g, b in 0..1. {@link #setSky} puts the plain look back (0.62 ambient, 0.38 sun).
     * @param skyAmbient light from above
     * @param groundAmbient light from below
     * @param sunColor the sun's colour
     */
    public void setLight(float[] skyAmbient, float[] groundAmbient, float[] sunColor) {
        renderer.setLight(skyAmbient, groundAmbient, sunColor);
    }

    /**
     * Sets the detail texture of the static world (a 512x512 luminance atlas, see {@link DetailTexture#atlas}); null removes it.
     * @param atlas the bytes, or null
     */
    public void setDetailTexture(final byte[] atlas) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setDetail(atlas);
            }
        });
    }

    /**
     * Sets the sky dome mesh (drawn first, unlit, without fog, centred on the eye); null removes it.
     * @param dome the dome, or null
     */
    public void setSkyDome(MeshData dome) {
        final GlMesh gm = dome == null ? null : GlMesh.of(dome);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setSkyMesh(gm);
            }
        });
    }

    /**
     * Sets the swatches painted into the colormap texture (T-28 Part C). Call before the scene's first frame; a later call
     * uploads the texture again. The palette must not be changed afterwards.
     * @param swatches the swatches, or null for Kenney's colormap as it is
     */
    public void setColormapSwatches(final PaletteSwatches swatches) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setPalette(swatches);
            }
        });
    }

    /**
     * Sets how many viewports are drawn: 1 = the whole picture, 2 = a left and a right half.
     * @param n 1 or 2
     */
    public void setViewports(int n) {
        if (n < 1 || n > 2) throw new IllegalArgumentException("viewports must be 1 or 2: " + n);
        renderer.setViewports(n);
    }

    /**
     * Places the camera of a viewport (the arrays are copied).
     * @param viewport 0 (left or only) or 1 (right)
     * @param eye camera position x,y,z
     * @param target the point looked at x,y,z
     * @param fovYDeg vertical field of view in degrees
     */
    public void setCamera(int viewport, float[] eye, float[] target, float fovYDeg) {
        renderer.setCamera(viewport, eye, target, fovYDeg);
    }

    /**
     * Hands over the dynamic items for the next frame (copied) and asks for a frame to be drawn.
     * @param dynamicItems this frame's render list
     */
    public void submit(RenderList dynamicItems) {
        renderer.submit(dynamicItems);
        requestRender();
    }

    /**
     * Sets the sky colour, which is also the fog colour.
     * @param rgb 0xRRGGBB
     */
    public void setSky(int rgb) {
        renderer.setSky(rgb);
        renderer.resetLook();
        renderer.setFogColor(-1);
    }

    /**
     * Sets the fog colour on its own (T-29). Until it is called, and after {@link #setSky}, the fog colour is the sky colour.
     * @param rgb 0xRRGGBB
     */
    public void setFogColor(int rgb) {
        renderer.setFogColor(rgb);
    }

    /**
     * Sets the sun: the direction the light travels (normalised here). The default is the old fixed sun.
     * @param x x of the direction
     * @param y y of the direction (negative = light from above)
     * @param z z of the direction
     */
    public void setSunDir(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-6f) return;
        renderer.setSunDir(x / len, y / len, z / len);
    }

    /**
     * Sets where the fog starts and where it is complete (distance from the eye).
     * @param near start distance
     * @param far end distance
     */
    public void setFog(float near, float far) {
        renderer.setFog(near, far);
    }

    /**
     * Skips static chunks that lie entirely farther than this from a viewport's eye (T-29 Part B; the 2P rule uses 45 m).
     * Off (0) by default, so 1P is unchanged.
     * @param metres distance from the eye, 0 or less to turn it off
     */
    public void setChunkCullDistance(float metres) {
        renderer.setChunkCullDistance(metres);
    }

    /**
     * The speed numbers of the GL thread.
     * @return the stats (read from any thread)
     */
    public GlStats stats() {
        return stats;
    }

    /**
     * Frees every GPU object (buffers, texture, shader) and forgets the loaded meshes and the scenery. Call it
     * when the 3D scene is left; the next 3D scene loads what it needs again.
     */
    public void release() {
        ids.clear();
        datas.clear();
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.releaseGl();
            }
        });
    }

    /**
     * Picks the surface format. In HIGH: 8-bit colour with 24-bit depth and 4x, then 2x, then no multisampling; when none of
     * those is offered (or in LOW) it takes RGB565 with 16-bit depth, and otherwise the closest configuration. The
     * result is written to the speed meter.
     */
    private static final class Chooser implements GLSurfaceView.EGLConfigChooser {
        private static final int EGL_SAMPLE_BUFFERS = 0x3032, EGL_SAMPLES = 0x3031;
        private final GlStats stats;
        private final boolean high;

        Chooser(GlStats stats, boolean high) {
            this.stats = stats;
            this.high = high;
        }

        private static int[] spec(int r, int g, int b, int a, int depth, int samples) {
            if (samples > 0) {
                return new int[] {
                        EGL10.EGL_RED_SIZE, r, EGL10.EGL_GREEN_SIZE, g, EGL10.EGL_BLUE_SIZE, b, EGL10.EGL_ALPHA_SIZE, a,
                        EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_STENCIL_SIZE, 0,
                        EGL_SAMPLE_BUFFERS, 1, EGL_SAMPLES, samples,
                        0x3040, 4, // EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT
                        EGL10.EGL_NONE};
            }
            return new int[] {
                    EGL10.EGL_RED_SIZE, r, EGL10.EGL_GREEN_SIZE, g, EGL10.EGL_BLUE_SIZE, b, EGL10.EGL_ALPHA_SIZE, a,
                    EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_STENCIL_SIZE, 0,
                    0x3040, 4, // EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT
                    EGL10.EGL_NONE};
        }

        private static EGLConfig[] offered(EGL10 egl, EGLDisplay display, int[] spec) {
            int[] n = new int[1];
            if (!egl.eglChooseConfig(display, spec, null, 0, n) || n[0] <= 0) return new EGLConfig[0];
            EGLConfig[] out = new EGLConfig[n[0]];
            egl.eglChooseConfig(display, spec, out, n[0], n);
            return out;
        }

        private static int attr(EGL10 egl, EGLDisplay display, EGLConfig c, int what) {
            int[] v = new int[1];
            return egl.eglGetConfigAttrib(display, c, what, v) ? v[0] : -1;
        }

        private static String describe(EGL10 egl, EGLDisplay display, EGLConfig c) {
            return "r" + attr(egl, display, c, EGL10.EGL_RED_SIZE) + "g" + attr(egl, display, c, EGL10.EGL_GREEN_SIZE)
                    + "b" + attr(egl, display, c, EGL10.EGL_BLUE_SIZE) + "a" + attr(egl, display, c, EGL10.EGL_ALPHA_SIZE)
                    + " d" + attr(egl, display, c, EGL10.EGL_DEPTH_SIZE) + " msaa" + Math.max(0, attr(egl, display, c, EGL_SAMPLES));
        }

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            if (high) {
                int[] tries = {4, 2, 0};
                for (int want : tries) {
                    EGLConfig[] list = offered(egl, display, spec(8, 8, 8, 0, 24, want));
                    for (EGLConfig c : list) {
                        int samples = Math.max(0, attr(egl, display, c, EGL_SAMPLES));
                        if (attr(egl, display, c, EGL10.EGL_RED_SIZE) == 8 && attr(egl, display, c, EGL10.EGL_GREEN_SIZE) == 8
                                && attr(egl, display, c, EGL10.EGL_BLUE_SIZE) == 8 && attr(egl, display, c, EGL10.EGL_DEPTH_SIZE) >= 24
                                && (want == 0 ? samples == 0 : samples >= want)) {
                            String text = "888 d" + attr(egl, display, c, EGL10.EGL_DEPTH_SIZE) + " msaa" + samples;
                            stats.setConfig(text);
                            Log.i(TAG, "EGL config " + text);
                            return c;
                        }
                    }
                }
            }
            EGLConfig[] wanted = offered(egl, display, spec(5, 6, 5, 0, 16, 0));
            for (EGLConfig c : wanted) {
                if (attr(egl, display, c, EGL10.EGL_RED_SIZE) == 5 && attr(egl, display, c, EGL10.EGL_GREEN_SIZE) == 6
                        && attr(egl, display, c, EGL10.EGL_BLUE_SIZE) == 5 && attr(egl, display, c, EGL10.EGL_DEPTH_SIZE) == 16) {
                    stats.setConfig(high ? "565 d16 msaa0 (888 not offered)" : "r5g6b5 d16");
                    return c;
                }
            }
            // Not offered: take what is closest and say so (the overlay shows it; the architect wants to know).
            EGLConfig[] all = offered(egl, display, spec(0, 0, 0, 0, 0, 0));
            StringBuilder sb = new StringBuilder("FALLBACK, offered:");
            EGLConfig best = null;
            for (int i = 0; i < all.length; i++) {
                if (i < 6) sb.append(' ').append(describe(egl, display, all[i]));
                if (best == null && attr(egl, display, all[i], EGL10.EGL_DEPTH_SIZE) >= 16) best = all[i];
            }
            if (best == null && all.length > 0) best = all[0];
            if (best == null) throw new IllegalArgumentException("No OpenGL ES 2 configuration is offered by this device");
            sb.append(" | using ").append(describe(egl, display, best));
            stats.setConfig(sb.toString());
            Log.w(TAG, sb.toString());
            return best;
        }
    }
}
