package com.wiggleplay.app.games.dance;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Sheets of pose cards: every host (columns) doing a few poses (rows), to check the arms and hands of each animal.
 * Runs with the other screenshot tests ({@code -Pshots}); pictures go to build/screenshots.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class DanceHostsScreenshotTest {

    private static void sheet(String name, int[] poses) throws Exception {
        DanceArt.init(RuntimeEnvironment.getApplication());
        for (int i = 0; i < DanceArt.HOSTS; i++) DanceArt.preload(i);
        Bitmap bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setColor(0xFF140B2E);
        c.drawRect(0, 0, 1280, 720, p);
        c.scale(1280f / 1920f, 720f / 1080f);
        float cw = 222f, ch = 302f, gap = 12f;
        float x0 = (1920f - DanceArt.HOSTS * cw - (DanceArt.HOSTS - 1) * gap) / 2f;
        for (int r = 0; r < poses.length; r++) {
            for (int h = 0; h < DanceArt.HOSTS; h++) {
                DanceArt.card(c, poses[r], h, x0 + h * (cw + gap), 20f + r * (ch + 20f), cw, ch, 0xFF29D3FF, 0f);
            }
        }
        File dir = new File("build/screenshots");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        File out = new File(dir, name + ".png");
        try (FileOutputStream os = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        assertTrue(out.length() > 5000);
    }

    @Test
    public void raisedArmsA() throws Exception {
        sheet("dance_hosts_raised_a", new int[]{Poses.ROCKET, Poses.GIRAFFE, Poses.STAR});
    }

    @Test
    public void raisedArmsB() throws Exception {
        sheet("dance_hosts_raised_b", new int[]{Poses.REACH, Poses.PALM, Poses.BUNNY});
    }

    @Test
    public void armsAtTheSides() throws Exception {
        sheet("dance_hosts_sides", new int[]{Poses.AIRPLANE, Poses.FLAMINGO, Poses.PENGUIN});
    }
}
