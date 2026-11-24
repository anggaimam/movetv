package com.wiggleplay.app.shots;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.wiggleplay.app.ui.HowToFigure;
import com.wiggleplay.app.ui.HowToPose;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** The screenshot of the shared "how to play" figure: the eight poses of Angga's sheet, then blends between them. */
final class FigureSheet {

    private FigureSheet() { }

    static File shoot(String name) throws IOException {
        int w = 1280, h = 720;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFF3EBDD);
        HowToFigure fig = new HowToFigure();
        HowToPose[] poses = {HowToPose.WHEEL, HowToPose.ARMS_UP, HowToPose.T_POSE, HowToPose.LEAN_LEFT, HowToPose.LEAN_RIGHT,
                HowToPose.JUMP, HowToPose.SQUAT, HowToPose.STAND};
        for (int i = 0; i < 8; i++) {
            float cx = 160f + (i % 4) * 320f, foot = i < 4 ? 300f : 640f;
            fig.draw(c, poses[i], cx, foot, 240f, HowToFigure.BLUE);
        }
        Bitmap out = bmp;
        File dir = new File("build/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File f = new File(dir, name + ".png");
        try (FileOutputStream os = new FileOutputStream(f)) {
            out.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + f.getAbsolutePath() + " " + f.length() + " bytes");
        return f;
    }

    /** The wheel turned, and blends: stand to wheel, lean to lean, jump to squat, T to arms up. */
    static File shootBlends(String name) throws IOException {
        int w = 1280, h = 720;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFFF3EBDD);
        HowToFigure fig = new HowToFigure();
        HowToPose turned = new HowToPose();
        for (int i = 0; i < 4; i++) {
            turned.set(HowToPose.WHEEL);
            turned.wheelDeg = -60f + 40f * i;
            fig.draw(c, turned, 160f + i * 320f, 300f, 220f, HowToFigure.BLUE);
        }
        HowToPose[][] pairs = {{HowToPose.LEAN_LEFT, HowToPose.LEAN_RIGHT}, {HowToPose.JUMP, HowToPose.SQUAT},
                {HowToPose.T_POSE, HowToPose.ARMS_UP}, {HowToPose.STAND, HowToPose.JUMP}};
        float[] ks = {0.25f, 0.5f, 0.75f};
        for (int p = 0; p < 4; p++) {
            for (int k = 0; k < 3; k++) {
                if (p % 2 == 1 && k == 2) continue;
                float cx = 110f + (p % 2) * 640f + k * 190f, foot = p < 2 ? 520f : 700f;
                if (p >= 2) { cx = 110f + (p - 2) * 640f + k * 190f; }
                fig.drawBetween(c, pairs[p][0], pairs[p][1], ks[k], cx, foot, 130f, HowToFigure.BLUE);
            }
        }
        File dir = new File("build/screenshots");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File f = new File(dir, name + ".png");
        try (FileOutputStream os = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        System.out.println("SHOT " + f.getAbsolutePath() + " " + f.length() + " bytes");
        return f;
    }
}
