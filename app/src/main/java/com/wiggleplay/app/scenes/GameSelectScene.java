package com.wiggleplay.app.scenes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;

import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.GameInfo;
import com.wiggleplay.app.games.GameRegistry;
import com.wiggleplay.app.ui.RoundButton;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks a game. Up to 4 games stand in one row of big tiles (400 x 520). With more games the row shows 4 at a time
 * (335 x 520) and a round arrow at each side turns the page (left and right arrows on the remote work the same, the
 * dots under the row show which page it is). The page is remembered when a game is left. Games that cannot take the
 * chosen number of players are hidden. A Back tile (hold 1.5 s) at the top left goes back to the players screen.
 */
public final class GameSelectScene implements Scene {

    static final float HOME_X = 48f, HOME_Y = 40f, HOME_W = 300f, HOME_H = 250f;
    private static final String T_TITLE = "Pick a game!";
    /** Tiles on one page when there are more games than fit in one row. */
    static final int PER_PAGE = 4;
    private static final float ROW_Y = 340f, ROW_H = 520f, ARROW_Y = ROW_Y + ROW_H / 2f;
    private static final float ARROW_L = 120f, ARROW_R = 1800f;

    /** The page last shown (kept while the app runs, so coming back from a game lands on the same page). */
    private static int lastPage;

    private final TileMenu.Env env;
    private final TileMenu menu;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<GameInfo> games = new ArrayList<>();
    private int page;
    private Bitmap camera;
    private float time;

    /**
     * Creates the screen.
     * @param env what the menu screens share
     */
    public GameSelectScene(TileMenu.Env env) {
        this.env = env;
        this.menu = new TileMenu(env);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * How many pages the games need.
     * @param n games
     * @return 1 when they fit in a row, else the number of pages of {@link #PER_PAGE}
     */
    static int pageCount(int n) {
        return n <= PER_PAGE ? 1 : (n + PER_PAGE - 1) / PER_PAGE;
    }

    /**
     * Places the game tiles of one page.
     * @param games all the games
     * @param page the page, 0-based (ignored when everything fits in one row)
     * @return one tile per game on the page, placed (no buttons)
     */
    static List<TileMenu.Tile> layout(List<GameInfo> games, int page) {
        List<TileMenu.Tile> out = new ArrayList<>();
        int n = games.size();
        if (n <= PER_PAGE) {
            float gap = 48f, w = 400f;
            float x0 = (Stage.W - (n * w + (n - 1) * gap)) / 2f;
            for (int i = 0; i < n; i++) out.add(tile(games.get(i), x0 + i * (w + gap), ROW_Y, w, ROW_H));
        } else {
            // four slots between the two arrows; a short last page keeps the same slots (starting from the left)
            float w = 335f, gap = 26f, x0 = 250f;
            int from = Math.max(0, Math.min(pageCount(n) - 1, page)) * PER_PAGE;
            for (int i = from; i < n && i < from + PER_PAGE; i++) {
                out.add(tile(games.get(i), x0 + (i - from) * (w + gap), ROW_Y, w, ROW_H));
            }
        }
        return out;
    }

    private static TileMenu.Tile tile(GameInfo g, float x, float y, float w, float h) {
        TileMenu.Tile t = new TileMenu.Tile(g.id, g.title, null, g.color, g.icon, x, y, w, h);
        t.live = g.live;
        return t;
    }

    @Override
    public void enter(SceneArgs args) {
        env.menuMusic();
        games = GameRegistry.all();   // how many play is asked per game, after this screen
        page = Math.max(0, Math.min(pageCount(games.size()) - 1, lastPage));
        showPage(null);
    }

    /** Builds the tiles of the current page; keeps the remote highlight on the given tile if it is still there. */
    private void showPage(String focusId) {
        List<TileMenu.Tile> tiles = new ArrayList<>(layout(games, page));
        String first = tiles.isEmpty() ? "back" : tiles.get(0).id;
        int pages = pageCount(games.size());
        if (pages > 1) {
            if (page > 0) tiles.add(RoundButton.pagePrev(ARROW_L, ARROW_Y, "Earlier games"));
            if (page < pages - 1) tiles.add(RoundButton.pageNext(ARROW_R, ARROW_Y, "More games"));
        }
        tiles.add(0, RoundButton.back());
        tiles.add(RoundButton.settings());
        String focus = first;
        if (focusId != null) {
            for (TileMenu.Tile t : tiles) if (focusId.equals(t.id)) focus = focusId;
        }
        menu.setTiles(tiles, focus);
    }

    @Override
    public void exit() {
        lastPage = page;
    }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        camera = f.camera;
        String sel = menu.update(dt, f);
        if (f.input.pressed(KeyEvent.KEYCODE_BACK) || "back".equals(sel)) {
            env.sfx("back");
            env.go("setup", SceneArgs.of("back", 1));
            return;
        }
        if ("settings".equals(sel)) {
            env.sfx("select");
            if (env.settings != null) env.settings.open();
            return;
        }
        if ("next".equals(sel) || "prev".equals(sel)) {
            int pages = pageCount(games.size());
            page = Math.max(0, Math.min(pages - 1, page + ("next".equals(sel) ? 1 : -1)));
            lastPage = page;
            env.sfx("select");
            // stay on an arrow if there is still another page that way, else on the first game
            boolean more = "next".equals(sel) ? page < pages - 1 : page > 0;
            showPage(more ? sel : null);
            return;
        }
        if (sel != null) env.go("players", SceneArgs.of("gameId", sel));
    }

    @Override
    public void draw(Canvas c) {
        Theme.drawBackdrop(c, camera, time);
        Theme.drawBanner(c, T_TITLE, Stage.W / 2f, 120f, 72f, 0xE6F3EBDD, Theme.INK);
        menu.draw(c);
        int pages = pageCount(games.size());
        if (pages > 1) {
            // dots: the page being shown is the big white one
            float gap = 56f, x0 = Stage.W / 2f - (pages - 1) * gap / 2f, y = ROW_Y + ROW_H + 90f;
            for (int i = 0; i < pages; i++) {
                boolean on = i == page;
                paint.setColor(on ? 0xFFFFFFFF : 0x88FFFFFF);
                c.drawCircle(x0 + i * gap, y, on ? 17f : 11f, paint);
                if (on) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(5f);
                    paint.setColor(Theme.INK);
                    c.drawCircle(x0 + i * gap, y, 17f, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
        }
    }
}
