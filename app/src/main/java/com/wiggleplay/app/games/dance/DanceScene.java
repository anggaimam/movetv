package com.wiggleplay.app.games.dance;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.wiggleplay.app.audio.Music;
import com.wiggleplay.app.core.GameView;
import com.wiggleplay.app.core.Pool;
import com.wiggleplay.app.core.Scene;
import com.wiggleplay.app.core.Stage;
import com.wiggleplay.app.games.common.Hud;
import com.wiggleplay.app.pose.PlayerPose;
import com.wiggleplay.app.ui.Theme;
import com.wiggleplay.app.ui.TileMenu;

/**
 * Animal Dance Party: an animal host dances on the beat, shows a pose on a card (an animal doing it) and the big host
 * copies YOU, so the child sees an animal imitate them. Hold the pose: you score the best one second of the hold.
 * Perfect 3, Great 2, Good 1, and "Keep trying!" still earns 1. Rounds come in kinds: an ordinary pose, the other side,
 * freeze (the music stops), quick, and moves of several poses in a row. Great or better in a row builds a streak
 * bonus; the party (lights, guests, disco ball) grows as the round goes on. A level finished with 3 stars unlocks a
 * host and a stage colour. Neon arcade art; the camera picture is optional (a small box, see Settings).
 * One or two players. The round follows the music's beat callbacks and runs on its own clock in between.
 */
public final class DanceScene implements Scene {

    private static final String GAME_ID = "dance";
    private static final float INTRO = 2f;   // beats of dancing before the first pose
    private static final int SAMPLES = 600;
    private static final String[] POPUP = {"+0", "+1", "+2", "+3", "+4", "+5", "+6", "+7", "+8", "+9", "+10", "+11",
            "+12"};
    private static final String[] STREAK = new String[21];
    private static final String[] BONUS = {"", "Streak bonus +1!", "Streak bonus +2!", "Streak bonus +3!"};
    private static final String[] NEW_HOST = {"Giraffe", "Monkey", "Flamingo"};
    private static final boolean[] RULES_SEEN = new boolean[4];
    private static boolean unlockAll;
    private static final float[] GUEST_X_1P = {520f, 1300f, 690f}, GUEST_X_2P = {700f, 1220f, 840f};
    private static final int[] CONFETTI_COLOURS = {DanceArt.CYAN, DanceArt.PINK, DanceArt.ORANGE, DanceArt.GREEN, DanceArt.GOLD};

    static {
        for (int i = 0; i < STREAK.length; i++) STREAK[i] = "Streak " + i;
    }

    private static final class Conf {
        float x, y, vx, vy, rot, vr, life, size;
        int colour;
    }

    private final TileMenu.Env env;
    private Hud hud;
    private final DanceRules rules = new DanceRules();
    private final Pool<Conf> confetti = new Pool<>(100, Conf::new);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Poses.Result[] live = {new Poses.Result(), new Poses.Result()};
    private final float[][] sampleT = new float[2][SAMPLES], sampleQ = new float[2][SAMPLES];
    private final byte[][] samplePart = new byte[2][SAMPLES];
    private final int[] sampleN = new int[2];
    private final int[] score = new int[2], streak = new int[2];
    private final float[] resultQ = new float[2];
    private final int[] resultPts = new int[2], resultBonus = new int[2];
    private final float[] hostAng = {70f, 70f, 70f, 70f}, wantAng = new float[4], hostArms = new float[8];
    private float hostLean;

    private final Music.BeatListener beatListener = (beat, bar) -> onBeat(beat);

    private Frame frame;
    private int level = 1, players = 1, bpm, theme;
    private Poses.Plan plan;
    private float time, idleBeat, beatPos, beatBase;
    private int curSlot, lastPhase, finished;
    private boolean windowActive, windowDone, beatsOn, wasPaused, musicOff;
    private float resultT = 9f, teamFlash;
    private int maxPoints, roundCount;
    private final boolean[] hostOpen = new boolean[DanceArt.HOSTS];
    private int hostsOpen = 1, themesOpen = 1;
    private String unlockText;
    private int newStarsBefore;

    /**
     * Tells the game whether the rules screen has been shown already this session (tests use this).
     * @param seen true to skip the rules screen on the next entry
     */
    public static void setRulesSeen(boolean seen) {
        for (int i = 0; i < RULES_SEEN.length; i++) RULES_SEEN[i] = seen;
    }

    /**
     * Unlocks every host and stage colour (tests use this to take pictures of them).
     * @param all true to unlock everything
     */
    public static void setUnlockAll(boolean all) {
        unlockAll = all;
    }

    /**
     * Creates the game.
     * @param env what the screens share
     */
    public DanceScene(TileMenu.Env env) {
        this.env = env;
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void enter(SceneArgs args) {
        env.leaveMenus();
        level = Math.max(1, Math.min(3, args.getInt("level", 1)));
        players = args.getInt("players", env.players) == 2 ? 2 : 1;
        bpm = Poses.bpm(level);
        readUnlocks();
        if (env.music != null) env.music.setBeatListener(beatListener);
        beginRound(!RULES_SEEN[level], true);
        RULES_SEEN[level] = true;
    }

    private void readUnlocks() {
        hostsOpen = 1;
        themesOpen = 1;
        hostOpen[0] = true;
        for (int l = 1; l <= 3; l++) {
            boolean three = unlockAll || (env.profile != null && env.profile.stars(GAME_ID, l) >= 3);
            hostOpen[l] = three;
            if (three) {
                hostsOpen++;
                themesOpen++;
            }
        }
    }

    private int hostForSlot(int slot) {
        int want = Math.max(0, slot) % hostsOpen, seen = 0;
        for (int i = 0; i < DanceArt.HOSTS; i++) {
            if (!hostOpen[i]) continue;
            if (seen == want) return i;
            seen++;
        }
        return 0;
    }

    private void beginRound(boolean showRules, boolean fresh) {
        for (int i = confetti.size() - 1; i >= 0; i--) confetti.freeAt(i);
        long seed = System.nanoTime();
        plan = Poses.plan(level, seed);
        maxPoints = Poses.maxPoints(plan);
        float seconds = (INTRO + plan.totalBeats) * 60f / bpm + 1.6f;
        hud = new Hud(env, seconds, "dance");
        hud.setRules(rules);
        theme = (int) (Math.abs(seed >> 9) % themesOpen);
        roundCount++;
        score[0] = score[1] = 0;
        streak[0] = streak[1] = 0;
        beatPos = beatBase = -INTRO;
        curSlot = -1;
        windowActive = false;
        windowDone = true;
        beatsOn = false;
        wasPaused = false;
        musicOff = false;
        resultT = 9f;
        teamFlash = 0f;
        lastPhase = Hud.COUNTDOWN;
        unlockText = null;
        if (env.music != null) env.music.setTempo(bpm);
        hud.start(players, showRules);
    }

    @Override
    public void exit() {
        if (env.music != null) {
            env.music.setBeatListener(null);
            env.music.stop(300);
        }
    }

    /** Called by the music on the main thread for every beat that is heard. */
    private void onBeat(int beat) {
        if (!beatsOn || musicOff) return;
        beatPos = beatBase + beat;
    }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        frame = f;
        String sel = hud.update(dt, f);
        if (sel != null) {
            if ("again".equals(sel) || "go".equals(sel)) {
                readUnlocks();
                beginRound(false, false);
            } else if ("rules".equals(sel)) {
                hud.openRules();
            } else if ("home".equals(sel)) {
                env.go("gameselect", SceneArgs.EMPTY);
            } else {
                env.go("levelselect", SceneArgs.of("gameId", GAME_ID));
            }
            return;
        }
        int phase = hud.phase();
        if (phase == Hud.RULES) {
            rules.update(dt);
            return;
        }
        idleBeat += dt * bpm / 60f;
        if (phase == Hud.PLAYING && lastPhase != Hud.PLAYING) {
            beatPos = beatBase = -INTRO;
            beatsOn = true;
        }
        lastPhase = phase;
        resultT += dt;
        if (teamFlash > 0f) teamFlash = Math.max(0f, teamFlash - dt * 1.2f);
        if (hud.paused()) {
            wasPaused = true;
        } else if (wasPaused) {
            wasPaused = false;
            rebase(); // the music starts from its first beat again after a pause
        }
        if (hud.running()) {
            beatPos += dt * bpm / 60f;
            runSlots();
            for (int p = 0; p < players; p++) liveMatch(p);
            if (hud.readyToFinish(true)) finishRound();
        }
        updateHost(dt, phase);
        animateEffects(dt);
    }

    /** Called when the music starts again from its first beat. */
    private void rebase() {
        musicOff = false;
        beatBase = (float) Math.floor(beatPos);
        beatPos = beatBase;
    }

    private void finishRound() {
        if (windowActive) endWindow();
        int s0 = Poses.stars(score[0], maxPoints);
        int s1 = players == 2 ? Poses.stars(score[1], maxPoints) : 0;
        int best = Math.max(s0, s1);
        unlockText = null;
        if (env.profile != null) {
            newStarsBefore = env.profile.stars(GAME_ID, level);
            env.profile.recordStars(GAME_ID, level, best);
        } else {
            newStarsBefore = 0;
        }
        if (best >= 3 && newStarsBefore < 3 && level >= 1 && level <= 3) {
            unlockText = "New friend: " + NEW_HOST[level - 1] + "! New stage: " + DanceArt.THEME_NAMES[Math.min(
                    DanceArt.THEMES - 1, themesOpen)] + "!";
        }
        hud.finish(s0, s1, score[0], players == 2 ? score[1] : 0);
    }

    // ---- the round ----

    private void runSlots() {
        if (beatPos < 0f) return;
        int idx = curSlot;
        while (idx + 1 < plan.slots && beatPos >= plan.start[idx + 1]) idx++;
        if (idx < 0 && plan.slots > 0) idx = 0;
        if (idx > curSlot) {
            if (windowActive) endWindow();
            curSlot = idx;
            startSlot();
        }
        if (curSlot < 0) return;
        float local = beatPos - plan.start[curSlot];
        if (local >= plan.length(curSlot)) {
            if (windowActive) endWindow();
            return;
        }
        if (!windowActive && !windowDone && local >= plan.ready[curSlot]) startWindow();
        if (windowActive) {
            if (local >= plan.ready[curSlot] + plan.hold[curSlot]) endWindow();
            else sample(local - plan.ready[curSlot]);
        }
    }

    private void startSlot() {
        windowDone = false;
        windowActive = false;
        env.say(plan.say[curSlot]);
    }

    private void startWindow() {
        windowActive = true;
        sampleN[0] = sampleN[1] = 0;
        if (plan.type[curSlot] == Poses.T_FREEZE && env.music != null) {
            musicOff = true;
            env.music.stop(120);
        }
    }

    /** The pose being asked for right now, or -1. */
    private int targetPose() {
        if (curSlot < 0) return -1;
        float local = beatPos - plan.start[curSlot];
        if (local >= plan.ready[curSlot] + plan.hold[curSlot]) return -1;
        return plan.poseAt(curSlot, local - plan.ready[curSlot]);
    }

    private boolean opposite() {
        return curSlot >= 0 && plan.type[curSlot] == Poses.T_MIRROR;
    }

    private void liveMatch(int p) {
        int pose = targetPose();
        PlayerPose body = frame.pose.players[p];
        if (pose < 0 || !body.present) {
            live[p].quality = 0f;
            live[p].torso = -1f;
            for (int i = 0; i < 4; i++) live[p].seg[i] = -1f;
            return;
        }
        Poses.match(pose, body, live[p], opposite());
    }

    private void sample(float intoHold) {
        int part = plan.parts[curSlot] == 1 ? 0 : Math.min(plan.parts[curSlot] - 1,
                (int) (intoHold / plan.hold[curSlot] * plan.parts[curSlot]));
        for (int p = 0; p < players; p++) {
            int n = sampleN[p];
            if (n >= SAMPLES) continue;
            sampleT[p][n] = time;
            sampleQ[p][n] = live[p].quality;
            samplePart[p][n] = (byte) part;
            sampleN[p] = n + 1;
        }
    }

    private void endWindow() {
        windowActive = false;
        windowDone = true;
        int parts = plan.parts[curSlot];
        float secs = plan.hold[curSlot] * 60f / bpm;
        float span = Math.min(1f, secs / parts * (parts == 1 ? 0.9f : 0.8f));
        boolean great = true;
        for (int p = 0; p < players; p++) {
            float sum = 0f;
            int from = 0;
            for (int k = 0; k < parts; k++) {
                int to = from;
                while (to < sampleN[p] && samplePart[p][to] <= k) to++;
                sum += Poses.bestWindow(sampleT[p], sampleQ[p], from, to, span);
                from = to;
            }
            float q = sum / parts;
            resultQ[p] = q;
            int base = Poses.points(q) * parts;
            if (q >= Poses.GREAT) {
                streak[p]++;
                resultBonus[p] = Poses.streakBonus(streak[p]);
            } else {
                streak[p] = 0;
                resultBonus[p] = 0;
                great = false;
            }
            resultPts[p] = base + resultBonus[p];
            score[p] += resultPts[p];
            if (q >= Poses.PERFECT) env.sfx("star", 1.3f);
            else if (q >= Poses.GREAT) env.sfx("star");
            else if (q >= Poses.GOOD) env.sfx("coin");
            else env.sfx("boing");
            burst(p, q >= Poses.PERFECT ? 26 : q >= Poses.GREAT ? 16 : 7);
        }
        resultT = 0f;
        if (plan.type[curSlot] == Poses.T_FREEZE && env.music != null) {
            env.music.play("dance"); // the music comes back
            rebase();
            musicOff = false;
        }
        if (players == 2 && great) {
            teamFlash = 1f;
            env.sfx("cheer");
            burst(0, 20);
            burst(1, 20);
        }
    }

    /** The big host copies the child (or dances when nobody is there). */
    private void updateHost(float dt, int phase) {
        PlayerPose src = null;
        if (phase == Hud.PLAYING || phase == Hud.ENDING) {
            src = frame.pose.players[0];
            if (players == 2 && frame.pose.players[1].present && (!src.present || live[1].quality > live[0].quality)) {
                src = frame.pose.players[1];
            }
        }
        float e = 1f - (float) Math.exp(-dt * 10f);
        if (src != null && src.present && Poses.measure(src, wantAng)) {
            for (int i = 0; i < 4; i++) {
                if (Float.isNaN(wantAng[i])) continue;
                float d = (wantAng[i] - hostAng[i] + 540f) % 360f - 180f;
                hostAng[i] += d * e;
            }
            float lean = Math.max(-35f, Math.min(35f, Poses.tiltOf(src)));
            hostLean += (lean - hostLean) * e;
        } else {
            float beat = phase == Hud.PLAYING || phase == Hud.ENDING ? Math.max(beatPos, 0f) : idleBeat;
            DanceArt.danceAngles(beat, wantAng);
            for (int i = 0; i < 4; i++) {
                float d = (wantAng[i] - hostAng[i] + 540f) % 360f - 180f;
                hostAng[i] += d * e;
            }
            hostLean += (0f - hostLean) * e;
        }
    }

    // ---- effects ----

    private float bodyX(int p) {
        return players == 2 ? (p == 0 ? Stage.W * 0.25f : Stage.W * 0.75f) : Stage.W / 2f;
    }

    private void burst(int p, int n) {
        if (GameView.quality == GameView.LOW) n = (n + 1) / 2;
        int cap = GameView.quality == GameView.LOW ? 50 : 100;
        int[] cols = CONFETTI_COLOURS;
        for (int i = 0; i < n; i++) {
            if (confetti.size() >= cap) return;
            Conf k = confetti.obtain();
            if (k == null) return;
            k.x = bodyX(p) + ((float) Math.random() - 0.5f) * 260f;
            k.y = 230f;
            float a = (float) (Math.random() * Math.PI);
            float sp = 120f + (float) Math.random() * 380f;
            k.vx = (float) Math.cos(a) * sp * (Math.random() < 0.5 ? -1f : 1f);
            k.vy = -(float) Math.sin(a) * sp * 0.6f;
            k.rot = (float) (Math.random() * 6.28);
            k.vr = (float) (Math.random() - 0.5) * 14f;
            k.life = 1.1f + (float) Math.random() * 0.9f;
            k.size = 12f + (float) Math.random() * 12f;
            k.colour = cols[i % cols.length];
        }
    }

    private void animateEffects(float dt) {
        for (int i = confetti.size() - 1; i >= 0; i--) {
            Conf k = confetti.at(i);
            k.life -= dt;
            if (k.life <= 0f) {
                confetti.freeAt(i);
                continue;
            }
            k.vy += 620f * dt;
            k.x += k.vx * dt;
            k.y += k.vy * dt;
            k.rot += k.vr * dt;
        }
    }

    // ---- drawing ----

    @Override
    public void draw(Canvas c) {
        DanceArt.stage(c, theme);
        if (frame == null) return;
        int ph = hud.phase();
        boolean playing = ph == Hud.PLAYING || ph == Hud.ENDING;
        float beat = playing ? Math.max(beatPos, 0f) : idleBeat;
        int pose = targetPose();
        boolean freeze = curSlot >= 0 && playing && plan.type[curSlot] == Poses.T_FREEZE && windowActive;
        float done = curSlot < 0 ? 0f : Math.min(1f, (curSlot + (windowDone ? 1 : 0)) / (float) plan.slots);
        float boost = Math.max(teamFlash, pose >= 0 ? Math.max(live[0].quality, players == 2 ? live[1].quality : 0f) : 0f);
        DanceArt.lights(c, beat, boost, 2 + Math.min(2, (int) (done * 3f)));
        if (ph != Hud.END && ph != Hud.RULES) {
            if (done >= 0.5f) DanceArt.discoBall(c, 700f, 150f, 46f, beat);
            drawGuests(c, beat, done);
            drawHost(c, beat, pose, freeze);
            drawCards(c, beat);
            if (playing) {
                Bitmap cam = frame.camera;
                if (cam != null && env.profile != null && env.profile.danceCamera()) {
                    DanceArt.cameraBox(c, cam, 40f, 190f, 352f, 198f, DanceArt.CYAN);
                }
                for (int p = 0; p < players; p++) {
                    DanceArt.body(c, frame.pose.players[p], pose >= 0 ? live[p] : null, p == 0 ? DanceArt.CYAN : DanceArt.PINK,
                            7f);
                }
                if (freeze) {
                    paint.setColor(0x26A0E8FF);
                    c.drawRect(0f, 0f, Stage.W, Stage.H, paint);
                }
                drawBeats(c, freeze);
                drawStreaks(c);
                drawResults(c);
            }
            drawConfetti(c);
        } else if (ph == Hud.END) {
            drawConfetti(c);
        }
        hud.draw(c, score[0], score[1]);
        if (ph == Hud.END && unlockText != null) {
            Theme.drawBanner(c, unlockText, Stage.W / 2f, 600f, 50f, 0xF0FFD84A, Theme.INK);
        }
    }

    private void drawGuests(Canvas c, float beat, float done) {
        int n = Math.min(3, (int) (done * 4f));
        for (int i = 0; i < n; i++) {
            float x = players == 2 ? GUEST_X_2P[i] : GUEST_X_1P[i];
            DanceArt.hostDance(c, hostForSlot(i * 2 + 1 + roundCount), x, 640f - i * 6f, 0.3f, beat + i * 0.5f, 0.3f);
        }
    }

    private void drawHost(Canvas c, float beat, int pose, boolean freeze) {
        boolean two = players == 2;
        float excite = pose >= 0 ? 1f : 0.4f;
        DanceArt.hostArmsAngles(hostForSlot(curSlot), hostAng, hostArms);
        if (two) DanceArt.host(c, hostForSlot(curSlot), Stage.W / 2f, 560f, 0.52f, beat, excite, hostArms, hostLean, freeze);
        else DanceArt.host(c, hostForSlot(curSlot), 300f, 760f, 0.8f, beat, excite, hostArms, hostLean, freeze);
    }

    private void drawCards(Canvas c, float beat) {
        if (curSlot < 0 || hud.phase() == Hud.COUNTDOWN) return;
        boolean two = players == 2;
        float pulse = 1f - (beat - (float) Math.floor(beat));
        float local = beatPos - plan.start[curSlot];
        int parts = plan.parts[curSlot];
        int now = plan.poseAt(curSlot, local - plan.ready[curSlot]);
        int activePart = 0;
        for (int k = 0; k < parts; k++) if (plan.pose[curSlot][k] == now && (k == 0 || local >= plan.ready[curSlot])) activePart = k;
        if (parts > 1 && local >= plan.ready[curSlot]) {
            activePart = Math.min(parts - 1, (int) ((local - plan.ready[curSlot]) / plan.hold[curSlot] * parts));
        }
        float cw = parts == 1 ? (two ? 230f : 320f) : (two ? 140f : 200f);
        float ch = cw * 1.36f;
        float gap = 12f;
        float total = parts * cw + (parts - 1) * gap;
        float x0 = two ? Stage.W / 2f - total / 2f : Stage.W - 50f - total;
        float cy = two ? 600f : 165f;
        int hostIdx = hostForSlot(curSlot);
        for (int k = 0; k < parts; k++) {
            boolean act = k == activePart && local >= plan.ready[curSlot] || (parts == 1);
            DanceArt.card(c, plan.pose[curSlot][k], hostIdx, x0 + k * (cw + gap), cy, cw, ch,
                    act ? DanceArt.GOLD : DanceArt.CYAN, act ? pulse : 0f);
        }
        float tx = x0 + total / 2f;
        Theme.drawCentered(c, plan.title[curSlot], tx, cy + ch + (two ? 56f : 70f), parts == 1 ? (two ? 50f : 62f) : (two ? 36f : 44f),
                DanceArt.WHITE);
        if (plan.tag[curSlot] != null) {
            int col = plan.type[curSlot] == Poses.T_FREEZE ? 0xFFA0E8FF : plan.type[curSlot] == Poses.T_SPEED ? DanceArt.ORANGE
                    : plan.type[curSlot] == Poses.T_MIRROR ? DanceArt.PINK : DanceArt.GREEN;
            Theme.drawCentered(c, plan.tag[curSlot], tx, cy + ch + (two ? 100f : 120f), two ? 34f : 40f, col);
        }
    }

    /** A row of dots: one for each beat of this pose (get ready, hold, rest). */
    private void drawBeats(Canvas c, boolean freeze) {
        if (curSlot < 0) return;
        float local = beatPos - plan.start[curSlot];
        int n = plan.length(curSlot), ready = plan.ready[curSlot], hold = plan.hold[curSlot];
        float x0 = Stage.W / 2f - (n - 1) * 36f / 2f, y = 176f;
        for (int i = 0; i < n; i++) {
            boolean isReady = i < ready, rest = i >= ready + hold;
            int col = isReady ? DanceArt.CYAN : rest ? 0xFF8A7FB0 : DanceArt.GOLD;
            boolean now = local >= i && local < i + 1;
            paint.setColor(col);
            paint.setAlpha(now ? 255 : 120);
            c.drawCircle(x0 + i * 36f, y, now ? 14f : 9f, paint);
        }
        paint.setAlpha(255);
        if (local < ready) {
            Theme.drawCentered(c, "Get ready!", Stage.W / 2f, 250f, 56f, DanceArt.CYAN);
        } else if (local < ready + hold) {
            Theme.drawCentered(c, freeze ? "Freeze!" : "Hold it!", Stage.W / 2f, 250f, 56f, freeze ? 0xFFA0E8FF : DanceArt.GOLD);
        }
    }

    private void drawStreaks(Canvas c) {
        for (int p = 0; p < players; p++) {
            if (streak[p] < 2) continue;
            float x = p == 0 ? 150f : Stage.W - 150f;
            Theme.drawCentered(c, STREAK[Math.min(20, streak[p])], x, 186f, 38f, DanceArt.ORANGE);
        }
    }

    private void drawResults(Canvas c) {
        if (resultT > 1.6f) return;
        float pop = 1f + 0.35f * Math.max(0f, 1f - resultT * 5f);
        float fade = resultT < 1.2f ? 1f : 1f - (resultT - 1.2f) / 0.4f;
        int alpha = (int) (255 * fade) << 24;
        for (int p = 0; p < players; p++) {
            float q = resultQ[p];
            int col = q >= Poses.PERFECT ? DanceArt.GOLD : q >= Poses.GREAT ? DanceArt.GREEN : q >= Poses.GOOD ? DanceArt.CYAN
                    : DanceArt.ORANGE;
            float x = bodyX(p);
            Theme.drawCentered(c, Poses.label(q), x, 560f, 110f * pop, (col & 0x00FFFFFF) | alpha);
            Theme.drawCentered(c, POPUP[Math.min(POPUP.length - 1, resultPts[p])], x, 650f, 72f, 0xFFFFFF | alpha);
            if (resultBonus[p] > 0) {
                Theme.drawCentered(c, BONUS[resultBonus[p]], x, 710f, 44f, (DanceArt.ORANGE & 0x00FFFFFF) | alpha);
            }
        }
        if (teamFlash > 0f) {
            Theme.drawCentered(c, "Team bonus!", Stage.W / 2f, 450f, 90f + 20f * teamFlash,
                    (DanceArt.PINK & 0x00FFFFFF) | ((int) (255 * Math.min(1f, teamFlash * 2f)) << 24));
        }
    }

    private void drawConfetti(Canvas c) {
        for (int i = 0; i < confetti.size(); i++) {
            Conf k = confetti.at(i);
            float w = k.size * (float) Math.abs(Math.cos(k.rot));
            paint.setColor(k.colour);
            paint.setAlpha((int) (255 * Math.min(1f, k.life * 2f)));
            rect.set(k.x - w / 2f, k.y - k.size / 2f, k.x + w / 2f, k.y + k.size / 2f);
            c.drawRect(rect, paint);
        }
        paint.setAlpha(255);
    }
}
