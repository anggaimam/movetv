package com.wiggleplay.app.pose;

/**
 * A fake body for testing without a camera. Plain Java, no android imports.
 *
 * Each player is a standing child (shoulder width 180 px) centred in their region of the 1920x1080
 * stage: the whole stage with 1 player, the left or right half with 2 players. All 17 keypoints
 * have s = 0.9. Input (all for the ACTIVE player):
 *  - pointer: moves the RIGHT wrist and raises it above the elbow; after the pointer is released
 *    the arm stays raised for 2 s and then drops
 *  - jump: the whole body rises 120 px for 500 ms
 *  - duck: head and shoulders drop 160 px while held
 *  - lean left/right: the upper body tilts 20 degrees while held
 *  - arms out (T-pose) while held; with the pointer active both wrists follow the pointer height
 *  - presets 1-9 while held: Star, Airplane, Rocket, Robot, Crab, Flamingo, Penguin, Monkey, Lean-left
 *
 * The skeleton is recomputed at 12 frames per second in {@link #sample}, which takes the time as an
 * argument so tests can use a fake clock.
 */
public final class TestPoseSource {

    /** Shoulder width of the test child in stage pixels. */
    public static final float SHOULDER_W = 180f;
    /** Frames per second of the test source. */
    public static final int FPS = 12;
    /** How high a jump goes, in stage pixels. */
    public static final float JUMP_PX = 120f;
    /** How long a jump lasts. */
    public static final long JUMP_MS = 500;
    /** How far head and shoulders drop while ducking, in stage pixels. */
    public static final float DUCK_PX = 160f;
    /** Lean angle in degrees at full lean. */
    public static final float LEAN_DEG = 20f;
    /** How long the arm stays raised after the pointer is released. */
    public static final long ARM_HOLD_MS = 2000;
    /** Confidence of every test keypoint. */
    public static final float CONFIDENCE = 0.9f;

    /** Held control: duck. */
    public static final int CTRL_DUCK = 0;
    /** Held control: lean to the left of the screen. */
    public static final int CTRL_LEAN_LEFT = 1;
    /** Held control: lean to the right of the screen. */
    public static final int CTRL_LEAN_RIGHT = 2;
    /** Held control: both arms straight out. */
    public static final int CTRL_ARMS_OUT = 3;

    /** Preset names, index 1..9 (index 0 unused). */
    public static final String[] PRESET_NAMES = {
            "", "Star", "Airplane", "Rocket", "Robot", "Crab", "Flamingo", "Penguin", "Monkey", "Lean-left"};

    // body proportions, in shoulder widths
    private static final float SHOULDER_Y = 400f;
    private static final float UPPER_ARM = 0.9f * SHOULDER_W;
    private static final float FOREARM = 0.8f * SHOULDER_W;
    private static final float TORSO = 1.5f * SHOULDER_W;
    private static final float HIP_HALF = 0.35f * SHOULDER_W;
    private static final float LEG = 1.0f * SHOULDER_W;
    private static final long FRAME_MS = 1000 / FPS; // 83

    // arm presets: upper-arm and forearm direction in degrees for the RIGHT arm
    // (0 = straight down, 90 = out to the side, 180 = straight up); the left arm is mirrored.
    // Special arms: ON_HIP and ON_NOSE put the wrist at the hip or the nose.
    private static final float ON_HIP = 1000f, ON_NOSE = 2000f;
    // {rightUpper, rightFore, leftUpper (as seen on the left arm), leftFore}, NaN-free
    private static final float[][] PRESETS = {
            null,
            {135, 135, 135, 135},          // 1 Star: arms up and out
            {90, 90, 90, 90},              // 2 Airplane: arms straight out
            {190, 190, 190, 190},          // 3 Rocket: arms up, hands together
            {90, 180, 90, 180},            // 4 Robot: upper arms out, forearms up
            {90, 205, 90, 205},            // 5 Crab: upper arms out, forearms up and in
            {ON_HIP, ON_HIP, 135, 135},    // 6 Flamingo: right hand on hip, left arm up and out
            {20, 30, 20, 30},              // 7 Penguin: arms down, hands out a little
            {ON_NOSE, ON_NOSE, 30, 20},    // 8 Monkey: right hand at the nose, left arm swinging
            {10, 5, 10, 5},                // 9 Lean-left: arms down (the body leans)
    };

    private int players = 1;
    private int active = 0;

    private final boolean[] held = new boolean[4];
    private int preset = 0;

    // per player state
    private final long[] jumpStart = {-1, -1};
    private final float[] duckAmt = new float[2];
    private final float[] leanAmt = new float[2];
    private final boolean[] pointerDown = new boolean[2];
    private final long[] pointerUpAt = {-1, -1};
    private final float[] armX = new float[2], armY = new float[2];
    private final boolean[] armSet = new boolean[2];

    private final PoseFrame frame = new PoseFrame();
    private final float[] bx = new float[Kp.COUNT], by = new float[Kp.COUNT]; // scratch for build()
    private long lastComputeMs = Long.MIN_VALUE;

    /** Creates a test source with 1 player. */
    public TestPoseSource() {
        frame.source = "test";
        frame.aiFps = FPS;
    }

    /**
     * Sets the number of players.
     * @param n 1 or 2
     */
    public synchronized void setPlayers(int n) {
        players = n == 2 ? 2 : 1;
        if (active >= players) active = 0;
        lastComputeMs = Long.MIN_VALUE; // recompute at the next sample
    }

    /**
     * Number of players.
     * @return 1 or 2
     */
    public synchronized int players() {
        return players;
    }

    /**
     * Chooses which player the input controls.
     * @param p 0 or 1 (ignored if there is only 1 player)
     */
    public synchronized void setActivePlayer(int p) {
        if (p >= 0 && p < players) active = p;
    }

    /**
     * Which player the input controls.
     * @return 0 or 1
     */
    public synchronized int activePlayer() {
        return active;
    }

    /** In 2 players, switches which player the input controls. */
    public synchronized void switchPlayer() {
        if (players == 2) active = 1 - active;
    }

    /**
     * A finger or mouse button went down.
     * @param x stage x
     * @param y stage y
     */
    public synchronized void pointerDown(float x, float y) {
        pointerMove(x, y);
    }

    /**
     * The finger or mouse moved while down.
     * @param x stage x
     * @param y stage y
     */
    public synchronized void pointerMove(float x, float y) {
        pointerDown[active] = true;
        armX[active] = x;
        armY[active] = y;
        armSet[active] = true;
    }

    /**
     * The finger or mouse was released. The arm stays raised for 2 s.
     * @param nowMs the current time in milliseconds
     */
    public synchronized void pointerUp(long nowMs) {
        pointerDown[active] = false;
        pointerUpAt[active] = nowMs;
    }

    /**
     * Starts a jump (ignored while one is still going).
     * @param nowMs the current time in milliseconds
     */
    public synchronized void jump(long nowMs) {
        long start = jumpStart[active];
        if (start >= 0 && nowMs - start < JUMP_MS) return;
        jumpStart[active] = nowMs;
    }

    /**
     * Presses or releases a held control.
     * @param control one of the CTRL_ constants
     * @param down true while the key is held
     */
    public synchronized void setHeld(int control, boolean down) {
        if (control >= 0 && control < held.length) held[control] = down;
    }

    /**
     * Presses or releases a preset pose key.
     * @param n 1..9
     * @param down true while the key is held
     */
    public synchronized void setPreset(int n, boolean down) {
        if (n < 1 || n > 9) return;
        if (down) preset = n;
        else if (preset == n) preset = 0;
    }

    /** Releases every held key and the pointer (used when test mode is switched off). */
    public synchronized void releaseAll() {
        java.util.Arrays.fill(held, false);
        preset = 0;
        pointerDown[0] = pointerDown[1] = false;
        pointerUpAt[0] = pointerUpAt[1] = -1;
        armSet[0] = armSet[1] = false;
    }

    /**
     * Writes the current test pose into {@code dst}. The skeleton is recomputed at most 12 times a
     * second; in between, the previous result is copied again.
     * @param nowMs the current time in milliseconds (SystemClock.uptimeMillis() on the device)
     * @param dst target frame (reused); a new frame is created only if null
     * @return the frame that was written to
     */
    public synchronized PoseFrame sample(long nowMs, PoseFrame dst) {
        if (lastComputeMs == Long.MIN_VALUE || nowMs - lastComputeMs >= FRAME_MS) {
            float dt = lastComputeMs == Long.MIN_VALUE ? 1f / FPS : Math.min(0.25f, (nowMs - lastComputeMs) / 1000f);
            lastComputeMs = nowMs;
            compute(nowMs, dt);
        }
        return frame.copyInto(dst);
    }

    private void compute(long now, float dt) {
        frame.timeMs = now;
        frame.mode = players;
        frame.aiFps = FPS;
        frame.source = "test";
        float ease = 1f - (float) Math.exp(-dt * 14f);
        for (int p = 0; p < 2; p++) {
            PlayerPose out = frame.players[p];
            if (p >= players) {
                out.present = false;
                for (Keypoint k : out.kps) { k.x = 0; k.y = 0; k.s = 0; }
                continue;
            }
            boolean mine = p == active;
            float duckT = mine && held[CTRL_DUCK] ? 1f : 0f;
            float leanT = 0f;
            if (mine) {
                if (held[CTRL_LEAN_LEFT]) leanT -= 1f;
                if (held[CTRL_LEAN_RIGHT]) leanT += 1f;
                if (preset == 9) leanT = -1f;
            }
            duckAmt[p] += (duckT - duckAmt[p]) * ease;
            leanAmt[p] += (leanT - leanAmt[p]) * ease;
            if (Math.abs(duckAmt[p] - duckT) < 0.002f) duckAmt[p] = duckT;
            if (Math.abs(leanAmt[p] - leanT) < 0.002f) leanAmt[p] = leanT;
            build(p, now, mine, out);
        }
    }

    private void build(int p, long now, boolean mine, PlayerPose out) {
        float cx = players == 1 ? PoseFrame.STAGE_W / 2f : (p == 0 ? PoseFrame.STAGE_W / 4f : PoseFrame.STAGE_W * 0.75f);
        float duck = duckAmt[p] * DUCK_PX;
        float sy = SHOULDER_Y + duck;
        float sw = SHOULDER_W;

        // head and shoulders (these move with a duck)
        float[] x = bx, y = by;
        x[Kp.NOSE] = cx;                 y[Kp.NOSE] = sy - 0.8f * sw;
        x[Kp.L_EYE] = cx - 0.17f * sw;   y[Kp.L_EYE] = y[Kp.NOSE] - 0.15f * sw;
        x[Kp.R_EYE] = cx + 0.17f * sw;   y[Kp.R_EYE] = y[Kp.NOSE] - 0.15f * sw;
        x[Kp.L_EAR] = cx - 0.33f * sw;   y[Kp.L_EAR] = y[Kp.NOSE] - 0.08f * sw;
        x[Kp.R_EAR] = cx + 0.33f * sw;   y[Kp.R_EAR] = y[Kp.NOSE] - 0.08f * sw;
        x[Kp.L_SHOULDER] = cx - 0.5f * sw; y[Kp.L_SHOULDER] = sy;
        x[Kp.R_SHOULDER] = cx + 0.5f * sw; y[Kp.R_SHOULDER] = sy;
        // hips and legs (a duck does not move them)
        float hy = SHOULDER_Y + TORSO;
        x[Kp.L_HIP] = cx - HIP_HALF;  y[Kp.L_HIP] = hy;
        x[Kp.R_HIP] = cx + HIP_HALF;  y[Kp.R_HIP] = hy;
        x[Kp.L_KNEE] = cx - HIP_HALF; y[Kp.L_KNEE] = hy + LEG;
        x[Kp.R_KNEE] = cx + HIP_HALF; y[Kp.R_KNEE] = hy + LEG;
        x[Kp.L_ANKLE] = cx - HIP_HALF; y[Kp.L_ANKLE] = hy + 2 * LEG;
        x[Kp.R_ANKLE] = cx + HIP_HALF; y[Kp.R_ANKLE] = hy + 2 * LEG;

        // arms
        boolean armsOut = mine && held[CTRL_ARMS_OUT];
        int pre = mine ? preset : 0;
        float[] pr = pre >= 1 ? PRESETS[pre] : null;
        if (armsOut) {
            arm(x, y, true, 90, 90);
            arm(x, y, false, 90, 90);
        } else if (pr != null) {
            presetArm(x, y, true, pr[0], pr[1]);
            presetArm(x, y, false, pr[2], pr[3]);
        } else {
            arm(x, y, true, 10, 5);
            arm(x, y, false, 10, 5);
        }

        // lean: the upper body turns around the hips (negative = towards the left of the screen)
        float lean = leanAmt[p] * LEAN_DEG;
        if (Math.abs(lean) > 0.001f) {
            double a = Math.toRadians(lean), cos = Math.cos(a), sin = Math.sin(a);
            float px = cx, py = hy;
            int[] upper = {Kp.NOSE, Kp.L_EYE, Kp.R_EYE, Kp.L_EAR, Kp.R_EAR, Kp.L_SHOULDER, Kp.R_SHOULDER,
                    Kp.L_ELBOW, Kp.R_ELBOW, Kp.L_WRIST, Kp.R_WRIST};
            for (int k : upper) {
                float dx = x[k] - px, dy = y[k] - py;
                x[k] = (float) (px + dx * cos - dy * sin);
                y[k] = (float) (py + dx * sin + dy * cos);
            }
        }

        // jump: everything rises, along an arc
        long js = jumpStart[p];
        if (js >= 0) {
            long t = now - js;
            if (t >= 0 && t < JUMP_MS) {
                float up = JUMP_PX * (float) Math.sin(Math.PI * t / JUMP_MS);
                for (int k = 0; k < Kp.COUNT; k++) y[k] -= up;
            } else if (t >= JUMP_MS) {
                jumpStart[p] = -1;
            }
        }

        // pointer: the right wrist follows it (the elbow is kept below the wrist so the hand counts as raised)
        boolean raised = armSet[p] && (pointerDown[p] || (pointerUpAt[p] >= 0 && now - pointerUpAt[p] < ARM_HOLD_MS));
        if (!raised && armSet[p] && !pointerDown[p]) armSet[p] = false;
        if (raised && !armsOut && pr == null) {
            float wx = armX[p], wy = armY[p];
            float shx = x[Kp.R_SHOULDER], shy = y[Kp.R_SHOULDER];
            x[Kp.R_WRIST] = wx;
            y[Kp.R_WRIST] = wy;
            x[Kp.R_ELBOW] = (shx + wx) / 2f;
            y[Kp.R_ELBOW] = Math.max((shy + wy) / 2f, wy + 40f);
        } else if (raised && armsOut) {
            // arms out + pointer: both wrists follow the pointer height (steer-and-climb testing)
            float wy = armY[p];
            for (int side = 0; side < 2; side++) {
                int sh = side == 0 ? Kp.L_SHOULDER : Kp.R_SHOULDER;
                int el = side == 0 ? Kp.L_ELBOW : Kp.R_ELBOW;
                int wr = side == 0 ? Kp.L_WRIST : Kp.R_WRIST;
                y[wr] = wy;
                x[el] = (x[sh] + x[wr]) / 2f;
                y[el] = (y[sh] + y[wr]) / 2f;
            }
        }

        out.present = true;
        for (int k = 0; k < Kp.COUNT; k++) {
            Keypoint kp = out.kps[k];
            kp.x = x[k];
            kp.y = y[k];
            kp.s = CONFIDENCE;
        }
    }

    /** Puts one arm in place from two direction angles (0 = down, 90 = out, 180 = up). */
    private void arm(float[] x, float[] y, boolean right, float upperDeg, float foreDeg) {
        int sh = right ? Kp.R_SHOULDER : Kp.L_SHOULDER;
        int el = right ? Kp.R_ELBOW : Kp.L_ELBOW;
        int wr = right ? Kp.R_WRIST : Kp.L_WRIST;
        float side = right ? 1f : -1f;
        double a = Math.toRadians(upperDeg), b = Math.toRadians(foreDeg);
        x[el] = x[sh] + side * UPPER_ARM * (float) Math.sin(a);
        y[el] = y[sh] + UPPER_ARM * (float) Math.cos(a);
        x[wr] = x[el] + side * FOREARM * (float) Math.sin(b);
        y[wr] = y[el] + FOREARM * (float) Math.cos(b);
    }

    private void presetArm(float[] x, float[] y, boolean right, float upper, float fore) {
        if (upper == ON_HIP) {
            int hip = right ? Kp.R_HIP : Kp.L_HIP;
            int wr = right ? Kp.R_WRIST : Kp.L_WRIST;
            x[wr] = x[hip] + (right ? 12f : -12f);
            y[wr] = y[hip];
            bend(x, y, right);
        } else if (upper == ON_NOSE) {
            int wr = right ? Kp.R_WRIST : Kp.L_WRIST;
            x[wr] = x[Kp.NOSE] + (right ? 0.15f : -0.15f) * SHOULDER_W;
            y[wr] = y[Kp.NOSE] + 0.15f * SHOULDER_W;
            bend(x, y, right);
        } else {
            arm(x, y, right, upper, fore);
        }
    }

    /** Puts the elbow beside the line from shoulder to wrist, pointing outwards. */
    private void bend(float[] x, float[] y, boolean right) {
        int sh = right ? Kp.R_SHOULDER : Kp.L_SHOULDER;
        int el = right ? Kp.R_ELBOW : Kp.L_ELBOW;
        int wr = right ? Kp.R_WRIST : Kp.L_WRIST;
        x[el] = (x[sh] + x[wr]) / 2f + (right ? 0.5f : -0.5f) * SHOULDER_W;
        y[el] = (y[sh] + y[wr]) / 2f;
    }
}
