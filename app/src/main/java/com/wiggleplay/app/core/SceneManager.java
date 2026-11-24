package com.wiggleplay.app.core;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the registered scenes and switches between them with a 250 ms fade to black and back.
 * Switching allocates nothing except what the scenes themselves do.
 */
public final class SceneManager {

    /** Length of each half of the fade, in seconds. */
    public static final float FADE_SECONDS = 0.25f;

    private static final int PHASE_NONE = 0, PHASE_OUT = 1, PHASE_IN = 2;

    private final Map<String, Scene> scenes = new HashMap<>();
    private final Paint fade = new Paint();

    private String currentName;
    private Scene current;
    private int phase = PHASE_NONE;
    private float t;
    private String pendingName;
    private Scene.SceneArgs pendingArgs;
    private SwapListener swapListener;

    /** Told just before a new scene's {@code enter} runs (the game view uses it to settle the picture quality first). */
    public interface SwapListener {
        /**
         * Called after the old scene has exited and before the new one is entered.
         * @param name the name of the scene about to be entered
         */
        void beforeEnter(String name);
    }

    /**
     * Sets the listener told before every scene is entered.
     * @param l the listener, or null
     */
    public void setSwapListener(SwapListener l) {
        swapListener = l;
    }

    /** Creates an empty manager. */
    public SceneManager() {
        fade.setStyle(Paint.Style.FILL);
    }

    /**
     * Makes a scene available under a name.
     * @param name the name used with {@link #go}
     * @param scene the scene
     */
    public void register(String name, Scene scene) {
        scenes.put(name, scene);
    }

    /**
     * Switches to a scene: the current one fades out, then the new one is entered and fades in. If no scene
     * is showing yet, the new one is entered at once. Calling this during a fade replaces the target.
     * @param name a registered scene name
     * @param args handed to the new scene's {@code enter}; null means no arguments
     */
    public void go(String name, Scene.SceneArgs args) {
        if (!scenes.containsKey(name)) throw new IllegalArgumentException("no scene named " + name);
        if (args == null) args = Scene.SceneArgs.EMPTY;
        if (current == null) {
            swap(name, args);
            phase = PHASE_IN;
            t = 0f;
            return;
        }
        pendingName = name;
        pendingArgs = args;
        if (phase == PHASE_IN) {
            t = FADE_SECONDS - t; // inOutCubic is symmetric, so the darkness stays continuous
            phase = PHASE_OUT;
        } else if (phase == PHASE_NONE) {
            phase = PHASE_OUT;
            t = 0f;
        } // already fading out: only the target changed
    }

    /**
     * Whether the current scene draws a 3D world (see {@link Scene#world3d()}).
     * @return true if the GL view should be showing
     */
    public boolean world3d() {
        return current != null && current.world3d();
    }

    /**
     * Name of the scene that is current (the new one once the swap has happened).
     * @return the name, or null before the first {@link #go}
     */
    public String currentName() {
        return currentName;
    }

    private void swap(String name, Scene.SceneArgs args) {
        if (current != null) current.exit();
        current = scenes.get(name);
        currentName = name;
        if (swapListener != null) swapListener.beforeEnter(name);
        current.enter(args);
    }

    /**
     * Advances the fade and the current scene.
     * @param dt seconds since the last frame
     * @param f this frame's data
     */
    public void update(float dt, Scene.Frame f) {
        if (phase == PHASE_OUT) {
            t += dt;
            if (t >= FADE_SECONDS) {
                String n = pendingName;
                Scene.SceneArgs a = pendingArgs;
                pendingName = null;
                pendingArgs = null;
                swap(n, a);
                phase = PHASE_IN;
                t = 0f;
            }
        } else if (phase == PHASE_IN) {
            t += dt;
            if (t >= FADE_SECONDS) {
                phase = PHASE_NONE;
                t = 0f;
            }
        }
        if (current != null) current.update(dt, f);
    }

    /**
     * Draws the current scene and the fade overlay (stage coordinates).
     * @param c the canvas
     */
    public void draw(Canvas c) {
        if (current != null) current.draw(c);
        float a = fadeAmount();
        if (a > 0.003f) {
            fade.setColor(((int) (a * 255f + 0.5f) << 24)); // black with alpha
            c.drawRect(0f, 0f, Stage.W, Stage.H, fade);
        }
    }

    /**
     * How dark the screen is because of a scene change.
     * @return 0 (clear) to 1 (black)
     */
    public float fadeAmount() {
        if (phase == PHASE_OUT) return Ease.inOutCubic(t / FADE_SECONDS);
        if (phase == PHASE_IN) return 1f - Ease.inOutCubic(t / FADE_SECONDS);
        return 0f;
    }
}
