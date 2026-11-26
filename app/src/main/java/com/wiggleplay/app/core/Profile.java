package com.wiggleplay.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * What the app remembers between runs, kept in SharedPreferences on the TV (nothing leaves the device):
 * the hold time, the last player count, the light boost switch, the pointer setting, and the stars
 * earned per game and level (0 to 3). Every read goes through {@link ProfileLogic}, so a missing or
 * damaged value turns into the default instead of a crash.
 */
public final class Profile {

    private static final String FILE = "movetv_profile";
    private static final String K_HOLD = "hold_ms", K_PLAYERS = "players", K_BOOST = "light_boost", K_POINTER = "pointer", K_DANCE_CAM = "dance_camera", K_RACE_STEER = "race_steer", K_QUALITY = "quality", K_RACE_TRACK = "raceTrack", K_RACE_REVERSED = "raceReversed", K_RACE_TRUCK = "raceTruck", K_RACE_PAINT = "raceTruckPaint";

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
        Object o = prefs.getAll().get(key);
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        return def;
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
     * The number of players last picked for a game.
     * @param gameId the game
     * @param max the most players the game takes (2, or 4 for bowling)
     * @param def the answer when nothing was saved yet
     * @return 1..max
     */
    public int playersFor(String gameId, int max, int def) {
        int n = readInt("players_" + gameId, def);
        return Math.max(1, Math.min(max, n));
    }

    /**
     * Remembers the number of players picked for a game.
     * @param gameId the game
     * @param n players
     */
    public void setPlayersFor(String gameId, int n) {
        prefs.edit().putInt("players_" + gameId, Math.max(1, Math.min(4, n))).apply();
    }

    /**
     * Whether Animal Dance Party shows a small camera picture in a neon frame.
     * @return true for a camera box (default false: only the glowing outline)
     */
    public boolean danceCamera() {
        return readBool(K_DANCE_CAM, false);
    }

    /**
     * Saves the Animal Dance Party camera box setting.
     * @param on true to show the camera box
     */
    public void setDanceCamera(boolean on) {
        prefs.edit().putBoolean(K_DANCE_CAM, on).apply();
    }

    /**
     * How Turbo Trucks is steered.
     * @return "wheel" (default) or "lean"
     */
    public String raceSteer() {
        return ProfileLogic.cleanRaceSteer(prefs.getAll().get(K_RACE_STEER));
    }

    /**
     * Saves how Turbo Trucks is steered.
     * @param mode "lean" or "wheel" (anything else is saved as "wheel")
     */
    public void setRaceSteer(String mode) {
        prefs.edit().putString(K_RACE_STEER, ProfileLogic.cleanRaceSteer(mode)).apply();
    }

    /**
     * The Turbo Trucks track (T-32).
     * @return "desert", "city", "rockies" or "space" (default "desert")
     */
    public String raceTrack() {
        return ProfileLogic.cleanRaceTrack(prefs.getAll().get(K_RACE_TRACK));
    }

    /**
     * Saves the Turbo Trucks track.
     * @param id a track id (anything else is saved as "desert")
     */
    public void setRaceTrack(String id) {
        prefs.edit().putString(K_RACE_TRACK, ProfileLogic.cleanRaceTrack(id)).apply();
    }

    /**
     * Whether the Turbo Trucks track is driven the other way round (T-32).
     * @return true for the reversed direction (default false)
     */
    public boolean raceReversed() {
        return ProfileLogic.cleanRaceReversed(prefs.getAll().get(K_RACE_REVERSED));
    }

    /**
     * Saves the Turbo Trucks direction.
     * @param reversed true for the other way round
     */
    public void setRaceReversed(boolean reversed) {
        prefs.edit().putBoolean(K_RACE_REVERSED, reversed).apply();
    }

    /**
     * The Turbo Trucks truck colour (T-32).
     * @return "yellow", "red", "green" or "purple" (default "yellow")
     */
    public String raceTruck() {
        return ProfileLogic.cleanRaceTruck(prefs.getAll().get(K_RACE_TRUCK));
    }

    /**
     * Saves the Turbo Trucks truck colour.
     * @param colour a colour (anything else is saved as "yellow")
     */
    public void setRaceTruck(String colour) {
        prefs.edit().putString(K_RACE_TRUCK, ProfileLogic.cleanRaceTruck(colour)).apply();
    }

    /**
     * The paint job of the Turbo Trucks truck (T-34).
     * @return 0 (the truck's own colours) to 5
     */
    public int racePaint() {
        return com.wiggleplay.app.games.race.TruckPaints.clean(ProfileLogic.cleanInt(prefs.getAll().get(K_RACE_PAINT)));
    }

    /**
     * Saves the paint job of the Turbo Trucks truck.
     * @param paint 0 to 5 (anything else is saved as 0)
     */
    public void setRacePaint(int paint) {
        prefs.edit().putInt(K_RACE_PAINT, com.wiggleplay.app.games.race.TruckPaints.clean(paint)).apply();
    }

    /**
     * The best stars earned on a track and level.
     * @param trackKey {@code desert} or {@code desert-r}
     * @param level 1 to 3
     * @return 0 (never played) to 3
     */
    public int raceBest(String trackKey, int level) {
        return ProfileLogic.cleanRaceBest(prefs.getAll().get(ProfileLogic.raceBestKey(trackKey, level)));
    }

    /**
     * Saves the stars of a finished race when they beat the saved best.
     * @param trackKey {@code desert} or {@code desert-r}
     * @param level 1 to 3
     * @param stars stars earned, 0 to 3
     * @return true when the saved value was raised
     */
    public boolean recordRaceBest(String trackKey, int level, int stars) {
        if (!ProfileLogic.raisesRaceBest(prefs.getAll().get(ProfileLogic.raceBestKey(trackKey, level)), stars)) return false;
        prefs.edit().putInt(ProfileLogic.raceBestKey(trackKey, level), ProfileLogic.cleanRaceBest(stars)).apply();
        return true;
    }

    /**
     * The Picture setting.
     * @return "auto" (default), "high" or "low"
     */
    public String quality() {
        return ProfileLogic.cleanQuality(prefs.getAll().get(K_QUALITY));
    }

    /**
     * Saves the Picture setting.
     * @param mode "auto", "high" or "low" (anything else is saved as "auto")
     */
    public void setQuality(String mode) {
        prefs.edit().putString(K_QUALITY, ProfileLogic.cleanQuality(mode)).apply();
    }
}
