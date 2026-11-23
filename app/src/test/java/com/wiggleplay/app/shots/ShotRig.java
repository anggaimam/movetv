package com.wiggleplay.app.shots;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.wiggleplay.app.core.Input;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.pose.TestPoseSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Renders a {@link Scene} off-screen on the JVM (Robolectric native graphics) and saves a PNG.
 * CI publishes the PNGs to the {@code screenshots} branch so a scene can be looked at without a TV.
 * Output is 1280x720 (the 1920x1080 stage scaled by 2/3), written to {@code build/screenshots}.
 */
public final class ShotRig {
    public static final int OUT_W = 1280, OUT_H = 720;
    private static final float DT = 1f / 60f;

    private ShotRig() { }

    /** A scripted body: what the fake player does before the picture is taken. */
    public interface Script {
        /** Called every frame before update, e.g. to move the pointer or press keys. */
        void step(int frame, long nowMs, TestPoseSource body, Input input);
    }

    /**
     * Enters the scene, runs it for a number of frames with a test-mode body, draws it, saves a PNG.
     * @param name file name without extension
     * @param scene the scene to show (not exited afterwards)
     * @param args scene arguments
     * @param players 1 or 2
     * @param frames how many 60 fps frames to run first
     * @param script optional scripted input, may be null
     * @return the saved file
     */
    public static File shoot(String name, Scene scene, Scene.SceneArgs args, int players, int frames, Script script)
            throws IOException {
        return shoot(name, scene, args, players, frames, script, false);
    }

    /**
     * Like {@link #shoot(String, Scene, Scene.SceneArgs, int, int, Script)}; with {@code drawEveryFrame} the scene is
     * also drawn after every update, so pictures that animate from the draw calls (live tile pictures) move.
     * @param drawEveryFrame true to draw each frame
     * @return the saved file
     */
    public static File shoot(String name, Scene scene, Scene.SceneArgs args, int players, int frames, Script script,
                             boolean drawEveryFrame) throws IOException {
        TestPoseSource body = new TestPoseSource();
        body.setPlayers(players);
        Input input = new Input();
        Scene.Frame f = new Scene.Frame(input);
        long now = 1000;
        f.nowMs = now;
        body.sample(now, f.pose);
        scene.enter(args);
        for (int i = 0; i < frames; i++) {
            now += 17;
            f.nowMs = now;
            input.beginFrame();
            if (script != null) script.step(i, now, body, input);
            body.sample(now, f.pose);
            scene.update(DT, f);
            if (drawEveryFrame) {
                Canvas dc = new Canvas(Bitmap.createBitmap(OUT_W / 4, OUT_H / 4, Bitmap.Config.ARGB_8888));
                dc.scale(OUT_W / 4f / 1920f, OUT_H / 4f / 1080f);
                scene.draw(dc);
            }
        }
        Bitmap bmp = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(OUT_W / 1920f, OUT_H / 1080f);
        scene.draw(c);
        File dir = new File("build/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File out = new File(dir, name + ".png");
        try (FileOutputStream os = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + out.getAbsolutePath() + " " + out.length() + " bytes");
        return out;
    }
}
