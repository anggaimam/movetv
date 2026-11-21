package com.wiggleplay.app.games;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.wiggleplay.app.core.Brushes;
import com.wiggleplay.app.ui.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The list of games, in the order the menu shows them. Games that are not built yet point at
 * the placeholder scene ({@link #PLACEHOLDER_SCENE}); a game's ticket replaces its entry's scene name.
 * T-13 to T-15 add bowling, bubbles and mirror later. Each tile picture is a small preview in the game's
 * own art style (flat storybook, crayon doodle or neon arcade), painted once.
 */
public final class GameRegistry {

    /** Scene name used by every game that is not built yet. */
    public static final String PLACEHOLDER_SCENE = "placeholder";

    private static final List<GameInfo> GAMES;

    static {
        List<GameInfo> g = new ArrayList<>();
        g.add(new GameInfo("fruit", "Fruit Splash", Theme.ORANGE, GameRegistry::paintFruit, "fruit", 1, 2, false));
        g.add(new GameInfo("sky", "Sky Glider", Theme.BLUE, GameRegistry::paintSky, "sky", 1, 2, false));
        g.add(new GameInfo("dance", "Animal Dance Party", Theme.PINK, GameRegistry::paintDance, PLACEHOLDER_SCENE, 1, 2, false));
        g.add(new GameInfo("jungle", "Jungle Dash", Theme.SAGE, GameRegistry::paintJungle, PLACEHOLDER_SCENE, 1, 2, false));
        GAMES = Collections.unmodifiableList(g);
    }

    private GameRegistry() { }

    /**
     * All games, in menu order.
     * @return an unmodifiable list
     */
    public static List<GameInfo> all() {
        return GAMES;
    }

    /**
     * Looks a game up.
     * @param id the game id
     * @return the game, or null
     */
    public static GameInfo get(String id) {
        for (GameInfo g : GAMES) if (g.id.equals(id)) return g;
        return null;
    }

    /**
     * The games that can be played with a number of players: games whose most players is below the number
     * are left out. Turn-based games always stay (they ask for their own player count).
     * @param players 1 or 2
     * @return a new list, in menu order
     */
    public static List<GameInfo> forPlayers(int players) {
        List<GameInfo> out = new ArrayList<>();
        for (GameInfo g : GAMES) if (g.turnBased || g.maxPlayers >= players) out.add(g);
        return out;
    }

    /**
     * Whether a scene name belongs to a game (so the remote's arrow keys drive the test body there).
     * @param sceneName a registered scene name
     * @return true for placeholder and game scenes
     */
    public static boolean isGameScene(String sceneName) {
        if (sceneName == null) return false;
        if (PLACEHOLDER_SCENE.equals(sceneName)) return true;
        for (GameInfo g : GAMES) if (g.sceneName.equals(sceneName)) return true;
        return false;
    }

    // ---- tile pictures, each in its game's own style ----

    private static final Paint F = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();

    /** Fruit Splash, Flat storybook: a watermelon slice and an orange, flat fills and a flat highlight. */
    private static void paintFruit(Canvas c, int w, int h) {
        Path slice = new Path();
        R.set(w * 0.08f, h * 0.10f, w * 0.92f, h * 0.94f);
        slice.addArc(R, 0f, 180f);
        slice.close();
        Brushes.flatShadow(c, slice, w * 0.03f, 0xFF4F7F4B);
        F.setColor(0xFF6FA06B);
        c.drawPath(slice, F);
        F.setColor(0xFFE0605A);
        R.set(w * 0.15f, h * 0.17f, w * 0.85f, h * 0.87f);
        Path flesh = new Path();
        flesh.addArc(R, 0f, 180f);
        flesh.close();
        c.drawPath(flesh, F);
        F.setColor(0xFF2F3A45);
        for (int i = 0; i < 5; i++) {
            float sx = w * (0.28f + i * 0.11f), sy = h * (0.58f + (i % 2) * 0.08f);
            R.set(sx - w * 0.015f, sy - h * 0.03f, sx + w * 0.015f, sy + h * 0.03f);
            c.drawOval(R, F);
        }
        F.setColor(0xFFF2C572);
        c.drawCircle(w * 0.80f, h * 0.30f, w * 0.15f, F);
        F.setColor(0x88FFFFFF);
        c.drawCircle(w * 0.75f, h * 0.25f, w * 0.04f, F);
    }

    /** Sky Glider, Flat storybook: three flat sky bands, a hill, a plane and a cloud. */
    private static void paintSky(Canvas c, int w, int h) {
        F.setColor(0xFFBFD8EC);
        c.drawRect(w * 0.06f, h * 0.06f, w * 0.94f, h * 0.94f, F);
        F.setColor(0xFFA6C8E4);
        c.drawRect(w * 0.06f, h * 0.38f, w * 0.94f, h * 0.94f, F);
        F.setColor(0xFF8DB8DA);
        c.drawRect(w * 0.06f, h * 0.62f, w * 0.94f, h * 0.94f, F);
        Path hill = new Path();
        hill.moveTo(w * 0.06f, h * 0.94f);
        hill.quadTo(w * 0.40f, h * 0.60f, w * 0.94f, h * 0.94f);
        hill.close();
        F.setColor(0xFF9CB89A);
        c.drawPath(hill, F);
        F.setColor(0xFFFFFFFF);
        c.drawCircle(w * 0.24f, h * 0.24f, w * 0.07f, F);
        c.drawCircle(w * 0.31f, h * 0.22f, w * 0.09f, F);
        c.drawCircle(w * 0.39f, h * 0.25f, w * 0.06f, F);
        Path plane = new Path();
        plane.moveTo(w * 0.78f, h * 0.38f);
        plane.lineTo(w * 0.52f, h * 0.48f);
        plane.lineTo(w * 0.60f, h * 0.38f);
        plane.close();
        plane.moveTo(w * 0.52f, h * 0.48f);
        plane.lineTo(w * 0.40f, h * 0.40f);
        plane.lineTo(w * 0.48f, h * 0.52f);
        plane.close();
        F.setColor(0xFFE89A4C);
        c.drawPath(plane, F);
        F.setColor(0xFFE8A598);
        R.set(w * 0.48f, h * 0.44f, w * 0.78f, h * 0.56f);
        c.drawOval(R, F);
    }

    /** Animal Dance Party, Neon arcade: a dark panel, a glowing penguin face and a glowing star. */
    private static void paintDance(Canvas c, int w, int h) {
        Path panel = new Path();
        R.set(w * 0.04f, h * 0.04f, w * 0.96f, h * 0.96f);
        panel.addRoundRect(R, w * 0.1f, w * 0.1f, Path.Direction.CW);
        F.setColor(0xFF140B2E);
        c.drawPath(panel, F);
        Path head = new Path();
        R.set(w * 0.22f, h * 0.24f, w * 0.78f, h * 0.84f);
        head.addOval(R, Path.Direction.CW);
        Brushes.neonStroke(c, head, 0xFF7FF4FF, w * 0.030f);
        Path eyes = new Path();
        eyes.addCircle(w * 0.41f, h * 0.48f, w * 0.045f, Path.Direction.CW);
        eyes.addCircle(w * 0.59f, h * 0.48f, w * 0.045f, Path.Direction.CW);
        Brushes.neonStroke(c, eyes, 0xFFFFFFFF, w * 0.025f);
        Path beak = new Path();
        beak.moveTo(w * 0.44f, h * 0.58f);
        beak.lineTo(w * 0.56f, h * 0.58f);
        beak.lineTo(w * 0.50f, h * 0.68f);
        beak.close();
        Brushes.neonStroke(c, beak, 0xFFFFC27A, w * 0.025f);
        Path star = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? w * 0.11f : w * 0.045f;
            float px = w * 0.80f + (float) Math.cos(a) * r, py = h * 0.18f + (float) Math.sin(a) * r;
            if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
        }
        star.close();
        Brushes.neonStroke(c, star, 0xFFFF4FD8, w * 0.025f);
    }

    /** Jungle Dash, Crayon doodle: a hatched, wobbly monkey face and a banana. */
    private static void paintJungle(Canvas c, int w, int h) {
        F.setColor(0xFFFFFDF7);
        R.set(w * 0.04f, h * 0.04f, w * 0.96f, h * 0.96f);
        c.drawRoundRect(R, w * 0.1f, w * 0.1f, F);
        Path head = new Path();
        R.set(w * 0.22f, h * 0.22f, w * 0.78f, h * 0.84f);
        head.addOval(R, Path.Direction.CW);
        Path ears = new Path();
        ears.addCircle(w * 0.20f, h * 0.46f, w * 0.10f, Path.Direction.CW);
        ears.addCircle(w * 0.80f, h * 0.46f, w * 0.10f, Path.Direction.CW);
        Brushes.crayonFill(c, ears, 0xFFB5651D, 45f, w * 0.03f);
        Brushes.drawCrayonOutline(c, ears, 11L, w * 0.01f, 0xFF7A4210, w * 0.02f);
        Brushes.crayonFill(c, head, 0xFFB5651D, 40f, w * 0.03f);
        Brushes.drawCrayonOutline(c, head, 21L, w * 0.012f, 0xFF7A4210, w * 0.025f);
        Path face = new Path();
        R.set(w * 0.34f, h * 0.44f, w * 0.66f, h * 0.76f);
        face.addOval(R, Path.Direction.CW);
        Brushes.crayonFill(c, face, 0xFFF2C572, 120f, w * 0.03f);
        Brushes.drawCrayonOutline(c, face, 31L, w * 0.01f, 0xFF7A4210, w * 0.018f);
        F.setColor(0xFF2F3A45);
        c.drawCircle(w * 0.43f, h * 0.45f, w * 0.03f, F);
        c.drawCircle(w * 0.57f, h * 0.45f, w * 0.03f, F);
        Path banana = new Path();
        R.set(w * 0.50f, h * 0.10f, w * 0.96f, h * 0.60f);
        banana.addArc(R, 100f, 120f);
        Brushes.drawCrayonOutline(c, banana, 41L, w * 0.01f, 0xFFE0A800, w * 0.05f);
    }
}
