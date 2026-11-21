package com.wiggleplay.app.games;

import com.wiggleplay.app.core.SpriteCache;

/**
 * Everything the menus need to know about one game. Plain data: the game itself lives in its own scene.
 */
public final class GameInfo {

    /** Paints a game's tile picture (a small preview in the game's own art style) once into a bitmap. */
    public interface IconPainter extends SpriteCache.Painter { }

    /** Short id, for example "fruit"; used for saved stars and scene arguments. */
    public final String id;
    /** Name shown and spoken, for example "Fruit Splash". */
    public final String title;
    /** Tile colour (ARGB). */
    public final int color;
    /** Paints the tile picture. */
    public final IconPainter icon;
    /** Name of the registered scene that plays the game. */
    public final String sceneName;
    /** Fewest players the game supports. */
    public final int minPlayers;
    /** Most players the game supports. */
    public final int maxPlayers;
    /** True if players take turns in front of the camera (the 1P/2P choice is ignored). */
    public final boolean turnBased;

    /**
     * Creates the description of a game.
     * @param id short id
     * @param title name shown and spoken
     * @param color tile colour
     * @param icon paints the tile picture
     * @param sceneName registered scene that plays the game
     * @param minPlayers fewest players
     * @param maxPlayers most players
     * @param turnBased true if players take turns
     */
    public GameInfo(String id, String title, int color, IconPainter icon, String sceneName,
                    int minPlayers, int maxPlayers, boolean turnBased) {
        this.id = id;
        this.title = title;
        this.color = color;
        this.icon = icon;
        this.sceneName = sceneName;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.turnBased = turnBased;
    }
}
