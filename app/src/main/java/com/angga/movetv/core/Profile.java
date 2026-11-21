package com.angga.movetv.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * What the app remembers between runs, kept in SharedPreferences on the TV (nothing leaves the device):
 * the hold time, the last player count, the light boost switch, the pointer setting, the camera picture switch, which game rules were shown, and the stars
 * earned per game and level (0 to 3). Every read goes through {@link ProfileLogic}, so a missing or
 * damaged value turns into the default instead of a crash.
 */
public final class Profile {

    private static final String FILE = "movetv_profile";
    private static final String K_HOLD = "hold_ms", K_PLAYERS = "players", K_BOOST = "light_boost", K_POINTER = "pointer", K_CAM = "camView";

    private final SharedPreferences prefs;

    /**
     * Opens the saved profile.
     * @param ctx any context
     */
    public Profile(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private int readInt(String key, int def) {
        Map<String, ?> all = prefs.getAll();
        return ProfileLogic.asInt(all.get(key), def);
    }

    private boolean readBool(String key, boolean def) {
        return ProfileLogic.asBool(prefs.getAll().get(key), def);
    }

    /**
     * How long a hand must stay on a tile to select it.
     * @return 700, 1000 or 1400 (default 1000)
     */
    public int holdMs() {
        return ProfileLogic.cleanHold(readInt(K_HOLD, ProfileLogic.DEFAULT_HOLD_MS));
    }

    /**
     * Saves the hold time.
     * @param ms 700, 1000 or 1400 (anything else is saved as 1000)
     */
    public void setHoldMs(int ms) {
        prefs.edit().putInt(K_HOLD, ProfileLogic.cleanHold(ms)).apply();
    }

    /**
     * The player count chosen last time.
     * @return 1 or 2 (default 1)
     */
    public int players() {
        return ProfileLogic.cleanPlayers(readInt(K_PLAYERS, ProfileLogic.DEFAULT_PLAYERS));
    }

    /**
     * Saves the player count.
     * @param n 1 or 2
     */
    public void setPlayers(int n) {
        prefs.edit().putInt(K_PLAYERS, ProfileLogic.cleanPlayers(n)).apply();
    }

    /**
     * Whether the camera's light boost is switched on (it is on unless the player turned it off).
     * @return true for on
     */
    public boolean lightBoost() {
        return readBool(K_BOOST, true);
    }

    /**
     * Saves the light boost switch.
     * @param on true for on
     */
    public void setLightBoost(boolean on) {
        prefs.edit().putBoolean(K_BOOST, on).apply();
    }

    /**
     * How the hand pointer behaves.
     * @return "hand" (cursor on the hand, default) or "reach" (cursor reaches further)
     */
    public String pointer() {
        return ProfileLogic.cleanPointer(prefs.getAll().get(K_POINTER));
    }

    /**
     * Saves the pointer setting.
     * @param mode "hand" or "reach" (anything else is saved as "hand")
     */
    public void setPointer(String mode) {
        prefs.edit().putString(K_POINTER, ProfileLogic.cleanPointer(mode)).apply();
    }

    /**
     * Stars earned in one game level.
     * @param gameId the game id, for example "fruit"
     * @param level 1 to 3
     * @return 0 to 3
     */
    public int stars(String gameId, int level) {
        return ProfileLogic.cleanStars(readInt(ProfileLogic.starsKey(gameId, level), 0));
    }

    /**
     * Saves the stars of a finished round, never lowering what was earned before.
     * @param gameId the game id
     * @param level 1 to 3
     * @param stars 0 to 3
     */
    public void recordStars(String gameId, int level, int stars) {
        int best = Math.max(stars(gameId, level), ProfileLogic.cleanStars(stars));
        prefs.edit().putInt(ProfileLogic.starsKey(gameId, level), best).apply();
    }

    /**
     * Whether games show the camera picture behind the play (on unless the player turned it off).
     * @return true for on
     */
    public boolean camView() {
        return ProfileLogic.cleanCamView(prefs.getAll().get(K_CAM));
    }

    /**
     * Saves the camera picture setting.
     * @param on true to show the camera picture in games
     */
    public void setCamView(boolean on) {
        prefs.edit().putBoolean(K_CAM, on).apply();
    }

    /**
     * Whether the "how to play" pictures of a game level were shown before.
     * @param gameId the game id
     * @param level 1 to 3
     * @return true if they were shown (a missing or damaged value counts as not shown)
     */
    public boolean rulesSeen(String gameId, int level) {
        return ProfileLogic.cleanRulesSeen(prefs.getAll().get(ProfileLogic.rulesSeenKey(gameId, level)));
    }

    /**
     * Remembers that the "how to play" pictures of a game level were shown.
     * @param gameId the game id
     * @param level 1 to 3
     */
    public void setRulesSeen(String gameId, int level) {
        prefs.edit().putBoolean(ProfileLogic.rulesSeenKey(gameId, level), true).apply();
    }
}
