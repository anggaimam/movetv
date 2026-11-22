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
 * Animal Dance Party: a cartoon animal host dances on the beat and shows a pose on a card. Copy it with your arms and
 * hold it. You score the best one second of your hold (so a late camera picture does not matter): Perfect 3, Great 2,
 * Good 1, and "Keep trying!" still earns 1. Neon arcade art. One or two players (the same pose, their own scores, a
 * team bonus when both are Great or better). Hard has two-pose combos. The round follows the music's beat callbacks
 * and runs on its own clock in between (and when there is no music).
 */
public final class DanceScene implements Scene {

    private static final String GAME_ID = "dance";
    private static final float INTRO = 2f;   // beats of dancing before the first pose
    private static final float READY = 2f;   // beats to get ready after the pose is shown
    private static final float REST = 1f;    // beats to show the result
    private static final int SAMPLES = 600;
    private static final String[] POPUP = {"+0", "+1", "+2", "+3", "+4", "+5", "+6"};
    private static final boolean[] RULES_SEEN = new boolean[4];

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
    private final int[] sampleN = new int[2], split = new int[2];
    private final int[] score = new int[2];
    private final float[] resultQ = new float[2];
    private final int[] resultPts = new int[2];
    private final int[] resultMul = new int[2];

    private final Music.BeatListener beatListener = (beat, bar) -> onBeat(beat);

    private Frame frame;
    private Bitmap camera;
    private int level = 1, players = 1, slots, winBeats, bpm;
    private float slotLen;
    private int[] first = new int[16], second = new int[16];
    private String[] comboTitle = new String[16];
    private float time, idleBeat, beatPos, beatBase;
    private int curSlot, lastPhase;
    private boolean windowActive, windowDone, beatsOn, wasPaused;
    private float resultT = 9f, teamFlash;
    private int host;
    private int maxPoints;

    /**
     * Tells the game whether the rules screen has been shown already this session (tests use this).
     * @param seen true to skip the rules screen on the next entry
     */
    public static void setRulesSeen(boolean seen) {
        for (int i = 0; i < RULES_SEEN.length; i++) RULES_SEEN[i] = seen;
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
        slots = Poses.slots(level);
        winBeats = Poses.windowBeats(level);
        slotLen = READY + winBeats + REST;
        maxPoints = Poses.maxPoints(level);
        float seconds = (INTRO + slots * slotLen) * 60f / bpm + 1.6f;
        hud = new Hud(env, seconds, "dance");
        hud.setRules(rules);
        if (env.music != null) env.music.setBeatListener(beatListener);
        beginRound(!RULES_SEEN[level]);
        RULES_SEEN[level] = true;
    }

    private void beginRound(boolean showRules) {
        for (int i = confetti.size() - 1; i >= 0; i--) confetti.freeAt(i);
        Poses.plan(level, System.nanoTime(), first, second);
        for (int s = 0; s < slots; s++) {
            comboTitle[s] = second[s] >= 0 ? Poses.NAMES[first[s]] + " then " + Poses.NAMES[second[s]] + "!" : null;
        }
        score[0] = score[1] = 0;
        beatPos = beatBase = -INTRO;
        curSlot = -1;
        windowActive = false;
        windowDone = true;
        beatsOn = false;
        wasPaused = false;
        resultT = 9f;
        teamFlash = 0f;
        lastPhase = Hud.COUNTDOWN;
        host = 0;
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
        if (!beatsOn) return;
        beatPos = beatBase + beat;
    }

    @Override
    public void update(float dt, Frame f) {
        time += dt;
        frame = f;
        camera = f.camera;
        String sel = hud.update(dt, f);
        if (sel != null) {
            if ("again".equals(sel) || "go".equals(sel)) {
                beginRound(false);
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
            // the music starts from its first beat again after a pause
            wasPaused = false;
            beatBase = (float) Math.floor(beatPos);
            beatPos = beatBase;
        }
        if (hud.running()) {
            beatPos += dt * bpm / 60f;
            runSlots();
            for (int p = 0; p < players; p++) liveMatch(p);
            if (hud.readyToFinish(true)) {
                if (windowActive) endWindow();
                int s0 = Poses.stars(score[0], maxPoints);
                int s1 = players == 2 ? Poses.stars(score[1], maxPoints) : 0;
                if (env.profile != null) env.profile.recordStars(GAME_ID, level, Math.max(s0, s1));
                hud.finish(s0, s1, score[0], players == 2 ? score[1] : 0);
            }
        }
        animateEffects(dt);
    }

    // ---- the round ----

    private void runSlots() {
        if (beatPos < 0f) return;
        int idx = (int) Math.floor(beatPos / slotLen);
        if (idx > curSlot) {
            if (windowActive) endWindow();
            curSlot = idx;
            if (curSlot < slots) startSlot();
        }
        if (curSlot < 0 || curSlot >= slots) return;
        float local = beatPos - curSlot * slotLen;
        if (!windowActive && !windowDone && local >= READY) startWindow();
        if (windowActive) {
            if (local >= READY + winBeats) {
                endWindow();
            } else {
                if (second[curSlot] >= 0 && split[0] < 0 && local >= READY + winBeats / 2f) {
                    split[0] = sampleN[0];
                    split[1] = sampleN[1];
                }
                sample();
            }
        }
    }

    private void startSlot() {
        windowDone = false;
        windowActive = false;
        host = curSlot % DanceArt.HOSTS;
        env.say(comboTitle[curSlot] != null ? comboTitle[curSlot] : Poses.SAY[first[curSlot]]);
    }

    private void startWindow() {
        windowActive = true;
        sampleN[0] = sampleN[1] = 0;
        split[0] = split[1] = -1;
    }

    /** The pose being asked for right now, or -1. */
    private int targetPose() {
        if (curSlot < 0 || curSlot >= slots) return -1;
        float local = beatPos - curSlot * slotLen;
        if (local >= slotLen - REST) return -1;
        if (second[curSlot] >= 0 && local >= READY + winBeats / 2f) return second[curSlot];
        return first[curSlot];
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
        Poses.match(pose, body, live[p]);
    }

    private void sample() {
        for (int p = 0; p < players; p++) {
            int n = sampleN[p];
            if (n >= SAMPLES) continue;
            sampleT[p][n] = time;
            sampleQ[p][n] = live[p].quality;
            sampleN[p] = n + 1;
        }
    }

    private void endWindow() {
        windowActive = false;
        windowDone = true;
        boolean combo = second[curSlot] >= 0;
        float secs = winBeats * 60f / bpm;
        for (int p = 0; p < players; p++) {
            float q;
            int mul = combo ? 2 : 1;
            if (combo) {
                float span = Math.min(1f, secs / 2f * 0.8f);
                int cut = split[p] < 0 ? sampleN[p] / 2 : split[p];
                float qa = Poses.bestWindow(sampleT[p], sampleQ[p], 0, cut, span);
                float qb = Poses.bestWindow(sampleT[p], sampleQ[p], cut, sampleN[p], span);
                q = (qa + qb) / 2f;
            } else {
                q = Poses.bestWindow(sampleT[p], sampleQ[p], 0, sampleN[p], Math.min(1f, secs * 0.9f));
            }
            resultQ[p] = q;
            resultMul[p] = mul;
            resultPts[p] = Poses.points(q) * mul;
            score[p] += resultPts[p];
            if (q >= Poses.PERFECT) env.sfx("star", 1.3f);
            else if (q >= Poses.GREAT) env.sfx("star");
            else if (q >= Poses.GOOD) env.sfx("coin");
            else env.sfx("boing");
            burst(p, q >= Poses.PERFECT ? 26 : q >= Poses.GREAT ? 16 : 7);
        }
        resultT = 0f;
        if (players == 2 && resultQ[0] >= Poses.GREAT && resultQ[1] >= Poses.GREAT) {
            teamFlash = 1f;
            env.sfx("cheer");
            burst(0, 20);
            burst(1, 20);
        }
    }

    // ---- effects ----

    private float bodyX(int p) {
        return players == 2 ? (p == 0 ? Stage.W * 0.25f : Stage.W * 0.75f) : Stage.W / 2f;
    }

    private void burst(int p, int n) {
        if (GameView.quality == GameView.LOW) n = (n + 1) / 2;
        int cap = GameView.quality == GameView.LOW ? 50 : 100;
        int[] cols = {DanceArt.CYAN, DanceArt.PINK, DanceArt.ORANGE, DanceArt.GREEN, DanceArt.GOLD};
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
        if (frame == null) {
            DanceArt.stage(c);
            return;
        }
        float beat = hud.phase() == Hud.PLAYING || hud.phase() == Hud.ENDING ? Math.max(beatPos, 0f) : idleBeat;
        if (camera != null) {
            DanceArt.camera(c, camera);
        } else {
            DanceArt.stage(c);
        }
        int pose = targetPose();
        float boost = Math.max(teamFlash, pose >= 0 ? Math.max(live[0].quality, players == 2 ? live[1].quality : 0f) : 0f);
        DanceArt.lights(c, beat, boost);
        boolean playing = hud.phase() == Hud.PLAYING || hud.phase() == Hud.ENDING;
        if (hud.phase() != Hud.END && hud.phase() != Hud.RULES) {
            drawHostAndCard(c, beat, pose);
            if (playing) {
                for (int p = 0; p < players; p++) {
                    DanceArt.body(c, frame.pose.players[p], pose >= 0 ? live[p] : null, p == 0 ? DanceArt.CYAN : DanceArt.PINK,
                            7f);
                }
                drawBeats(c);
                drawResults(c);
            }
            drawConfetti(c);
        }
        hud.draw(c, score[0], score[1]);
    }

    private void drawHostAndCard(Canvas c, float beat, int pose) {
        boolean two = players == 2;
        float excite = pose >= 0 ? 1f : 0.4f;
        if (two) {
            DanceArt.host(c, host, Stage.W / 2f, 560f, 0.52f, beat, excite);
        } else {
            DanceArt.host(c, host, 300f, 760f, 0.8f, beat, excite);
        }
        if (curSlot < 0 || curSlot >= slots || hud.phase() == Hud.COUNTDOWN) return;
        float pulse = 1f - (beat - (float) Math.floor(beat));
        boolean combo = second[curSlot] >= 0;
        float local = beatPos - curSlot * slotLen;
        boolean secondHalf = combo && local >= READY + winBeats / 2f;
        float cy = two ? 590f : 200f;
        float ch = two ? 300f : 340f;
        if (combo) {
            float cw = two ? 150f : 200f;
            float x0 = Stage.W / 2f - (two ? cw + 10f : 0f);
            if (!two) x0 = 1450f;
            float gap = two ? 20f : 20f;
            DanceArt.card(c, first[curSlot], x0, cy, cw, ch, secondHalf ? DanceArt.CYAN : DanceArt.GOLD, secondHalf ? 0f : pulse);
            DanceArt.card(c, second[curSlot], x0 + cw + gap, cy, cw, ch, secondHalf ? DanceArt.GOLD : DanceArt.CYAN,
                    secondHalf ? pulse : 0f);
            Theme.drawCentered(c, comboTitle[curSlot], two ? Stage.W / 2f : x0 + cw + gap / 2f, cy + ch + 70f,
                    two ? 40f : 46f, DanceArt.WHITE);
        } else {
            float cw = two ? 300f : 380f;
            float x0 = two ? Stage.W / 2f - cw / 2f : 1470f;
            DanceArt.card(c, first[curSlot], x0, cy, cw, ch, DanceArt.GOLD, pulse);
            Theme.drawCentered(c, Poses.NAMES[first[curSlot]], x0 + cw / 2f, cy + ch + 70f, two ? 54f : 66f, DanceArt.WHITE);
        }
    }

    /** A row of dots: one for each beat of this pose (get ready, hold, rest). */
    private void drawBeats(Canvas c) {
        if (curSlot < 0 || curSlot >= slots) return;
        float local = beatPos - curSlot * slotLen;
        int n = (int) slotLen;
        float x0 = Stage.W / 2f - (n - 1) * 40f / 2f, y = 176f;
        for (int i = 0; i < n; i++) {
            boolean ready = i < READY, rest = i >= READY + winBeats;
            int col = ready ? DanceArt.CYAN : rest ? 0xFF8A7FB0 : DanceArt.GOLD;
            boolean now = local >= i && local < i + 1;
            paint.setColor(col);
            paint.setAlpha(now ? 255 : 120);
            c.drawCircle(x0 + i * 40f, y, now ? 14f : 9f, paint);
        }
        paint.setAlpha(255);
        if (local < READY) {
            Theme.drawCentered(c, "Get ready!", Stage.W / 2f, 250f, 56f, DanceArt.CYAN);
        } else if (local < READY + winBeats) {
            Theme.drawCentered(c, "Hold it!", Stage.W / 2f, 250f, 56f, DanceArt.GOLD);
        }
    }

    private void drawResults(Canvas c) {
        if (resultT > 1.6f) return;
        float pop = 1f + 0.35f * Math.max(0f, 1f - resultT * 5f);
        float fade = resultT < 1.2f ? 1f : 1f - (resultT - 1.2f) / 0.4f;
        for (int p = 0; p < players; p++) {
            float q = resultQ[p];
            int col = q >= Poses.PERFECT ? DanceArt.GOLD : q >= Poses.GREAT ? DanceArt.GREEN : q >= Poses.GOOD ? DanceArt.CYAN
                    : DanceArt.ORANGE;
            float x = bodyX(p);
            Theme.drawCentered(c, Poses.label(q), x, 560f, 110f * pop, (col & 0x00FFFFFF) | ((int) (255 * fade) << 24));
            Theme.drawCentered(c, POPUP[Math.min(6, resultPts[p])], x, 650f, 72f,
                    (0xFFFFFF) | ((int) (255 * fade) << 24));
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
