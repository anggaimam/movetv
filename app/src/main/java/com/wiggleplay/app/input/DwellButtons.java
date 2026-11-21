package com.wiggleplay.app.input;

import java.util.ArrayList;
import java.util.List;

/**
 * Hover-and-hold selection for up to two cursors, plus remote/keyboard focus. Plain Java.
 * Select time = 150 ms dwell + hold time. Leaving drains progress over 300 ms.
 */
public final class DwellButtons {
    static final float DWELL_MS = 150f, DRAIN_MS = 300f, STICKY = 1.25f;
    static final float MAGNET_RANGE = 40f, MAGNET_MAX_SPEED = 600f, MAGNET_PULL = 0.12f;
    static final long COOLDOWN_MS = 600;
    private static final int MAX_PLAYERS = 2;

    private List<Target> targets = new ArrayList<>();
    private int holdMs = 1000;
    private float[] progress = new float[0];
    private int[] owner = new int[0];
    private final int[] hovered = {-1, -1};        // target index per player
    private final float[] hoverMs = new float[MAX_PLAYERS];
    private final long[] cooldownUntil = new long[MAX_PLAYERS];
    private final float[] magX = new float[MAX_PLAYERS], magY = new float[MAX_PLAYERS];
    private boolean[] held = new boolean[0];
    private int focus = -1;
    private final List<DwellEvent> pending = new ArrayList<>();

    /** Replaces all targets; progress and hover state are cleared, focus is kept if the id still exists. */
    public void setTargets(List<Target> list) {
        String keep = focusId();
        targets = new ArrayList<>(list);
        progress = new float[targets.size()];
        owner = new int[targets.size()];
        held = new boolean[targets.size()];
        java.util.Arrays.fill(owner, -1);
        java.util.Arrays.fill(hovered, -1);
        java.util.Arrays.fill(hoverMs, 0f);
        focus = keep == null ? -1 : indexOf(keep);
    }

    /** Sets the default hold time; only 700, 1000 or 1400 are allowed (others snap to the nearest). */
    public void setHoldMs(int ms) {
        holdMs = Math.abs(ms - 700) <= Math.abs(ms - 1000) ? 700 : (Math.abs(ms - 1000) <= Math.abs(ms - 1400) ? 1000 : 1400);
    }

    private int indexOf(String id) {
        for (int i = 0; i < targets.size(); i++) if (targets.get(i).id.equals(id)) return i;
        return -1;
    }

    private float holdFor(int i) {
        int h = targets.get(i).holdMs;
        return h > 0 ? h : holdMs;
    }

    private static boolean inside(Target t, float px, float py, float scale) {
        float w = t.w * scale, h = t.h * scale;
        float l = t.cx() - w * 0.5f, tp = t.cy() - h * 0.5f;
        return px >= l && px <= l + w && py >= tp && py <= tp + h;
    }

    private static float edgeDist(Target t, float px, float py) {
        float dx = Math.max(Math.max(t.x - px, 0f), px - (t.x + t.w));
        float dy = Math.max(Math.max(t.y - py, 0f), py - (t.y + t.h));
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Advances hover/hold state.
     * @param dt seconds since last call
     * @param cursors the players' cursors (index = player)
     * @param nowMs clock in ms
     * @param out receives HOVER/SELECT events (appended)
     */
    public void update(float dt, HandCursor[] cursors, long nowMs, List<DwellEvent> out) {
        float dms = dt * 1000f;
        if (!pending.isEmpty()) {
            out.addAll(pending);
            pending.clear();
        }
        int n = targets.size();
        java.util.Arrays.fill(held, false);

        for (int c = 0; c < cursors.length && c < MAX_PLAYERS; c++) {
            HandCursor cur = cursors[c];
            magX[c] = 0f;
            magY[c] = 0f;
            int prev = hovered[c];
            int now = -1;
            if (cur != null && cur.visible()) {
                float px = cur.x(), py = cur.y();
                if (prev >= 0 && prev < n && inside(targets.get(prev), px, py, STICKY)) {
                    now = prev;
                } else {
                    float best = Float.MAX_VALUE;
                    for (int i = 0; i < n; i++) {
                        Target t = targets.get(i);
                        if (!inside(t, px, py, 1f)) continue;
                        float d = (t.cx() - px) * (t.cx() - px) + (t.cy() - py) * (t.cy() - py);
                        if (d < best) { best = d; now = i; }
                    }
                }
                // magnet (drawing only)
                if (cur.speed() < MAGNET_MAX_SPEED) {
                    int near = -1;
                    float bd = MAGNET_RANGE;
                    for (int i = 0; i < n; i++) {
                        float d = edgeDist(targets.get(i), px, py);
                        if (d <= bd) { bd = d; near = i; }
                    }
                    if (near >= 0) {
                        magX[c] = (targets.get(near).cx() - px) * MAGNET_PULL;
                        magY[c] = (targets.get(near).cy() - py) * MAGNET_PULL;
                    }
                }
            }
            if (now != prev) {
                hovered[c] = now;
                if (now >= 0) {
                    hoverMs[c] = progress[now] > 0f ? DWELL_MS : 0f;   // re-entering continues at once
                    out.add(new DwellEvent(DwellEvent.HOVER, targets.get(now).id, c));
                } else {
                    hoverMs[c] = 0f;
                }
            }
            if (now < 0) continue;
            held[now] = true;
            if (nowMs < cooldownUntil[c]) continue;                     // no progress during cooldown
            if (owner[now] != -1 && owner[now] != c) continue;          // someone else is filling it
            owner[now] = c;
            hoverMs[c] += dms;
            if (hoverMs[c] >= DWELL_MS) {
                float add = hoverMs[c] - DWELL_MS < dms ? hoverMs[c] - DWELL_MS : dms;  // only time past the dwell
                progress[now] += add / holdFor(now);
                if (progress[now] >= 1f) {
                    progress[now] = 0f;
                    owner[now] = -1;
                    hoverMs[c] = 0f;
                    cooldownUntil[c] = nowMs + COOLDOWN_MS;
                    out.add(new DwellEvent(DwellEvent.SELECT, targets.get(now).id, c));
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (held[i]) continue;
            if (progress[i] > 0f) {
                progress[i] -= dms / DRAIN_MS;
                if (progress[i] <= 0f) { progress[i] = 0f; owner[i] = -1; }
            } else {
                owner[i] = -1;
            }
        }
    }

    /** @return fill 0..1 of the target, 0 if unknown */
    public float progress(String id) {
        int i = indexOf(id);
        return i < 0 ? 0f : Math.min(progress[i], 1f);
    }

    /** @return the player currently filling the target, or -1 */
    public int progressPlayer(String id) {
        int i = indexOf(id);
        return i < 0 || progress[i] <= 0f ? -1 : owner[i];
    }

    /** @return x offset (stage px) to draw the cursor towards the nearest target */
    public float magnetDx(int player) { return player >= 0 && player < MAX_PLAYERS ? magX[player] : 0f; }

    /** @return y offset (stage px) to draw the cursor towards the nearest target */
    public float magnetDy(int player) { return player >= 0 && player < MAX_PLAYERS ? magY[player] : 0f; }

    /** @return id of the remote-focused target, or null */
    public String focusId() { return focus >= 0 && focus < targets.size() ? targets.get(focus).id : null; }

    /** Puts the remote focus on a target (ignored if the id is unknown). */
    public void setFocus(String id) {
        int i = id == null ? -1 : indexOf(id);
        if (i >= 0) focus = i;
    }

    /**
     * Moves the remote focus to the visually nearest target in a direction.
     * @param dx -1 left, +1 right, 0 none
     * @param dy -1 up, +1 down, 0 none
     */
    public void moveFocus(int dx, int dy) {
        if (targets.isEmpty()) return;
        if (focus < 0 || focus >= targets.size()) { focus = 0; return; }
        Target f = targets.get(focus);
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < targets.size(); i++) {
            if (i == focus) continue;
            Target t = targets.get(i);
            float ddx = t.cx() - f.cx(), ddy = t.cy() - f.cy();
            float along = dx != 0 ? ddx * dx : ddy * dy;
            float perp = dx != 0 ? Math.abs(ddy) : Math.abs(ddx);
            if (along <= 0f) continue;
            float score = along + 2f * perp;
            if (score < bestScore) { bestScore = score; best = i; }
        }
        if (best >= 0) focus = best;
    }

    /** Emits a SELECT for the focused target on the next update. */
    public void selectFocused(int player) {
        if (focus >= 0 && focus < targets.size()) {
            pending.add(new DwellEvent(DwellEvent.SELECT, targets.get(focus).id, player));
        }
    }
}
