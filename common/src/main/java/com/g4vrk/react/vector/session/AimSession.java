package com.g4vrk.react.vector.session;

import com.g4vrk.react.vector.model.FrameHistory;
import com.g4vrk.react.vector.model.AimFrame;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AimSession {

    public final CombatWindow combatActivity = new CombatWindow();

    private AimState state = new AimState();
    private FrameHistory history = new FrameHistory(64);
    private boolean active;

    public synchronized @NotNull AimState activate() {
        active = true;
        return state;
    }

    public synchronized boolean isCurrent(final @Nullable AimState expected) {
        return active && state == expected;
    }

    public synchronized void deactivate() {
        if (active) {
            reset();
        }
    }

    public synchronized void reset() {
        state = new AimState();
        history.clear();
        combatActivity.reset();
        active = false;
    }

    public synchronized void clearHistory() {
        history.clear();
    }

    public synchronized void push(final @NotNull AimFrame frame) {
        history.addFrame(frame);
    }

    public synchronized @Nullable AimFrame[] snapshot(final int window) {
        return history.latest(window);
    }

    public synchronized void resize(final int size) {
        if (history.capacity() != size) {
            history = new FrameHistory(size);
        }
    }

    public synchronized @Nullable AimFrame[] pollSnapshot(final int step) {
        final List<AimFrame> frames = history.pollSnapshot(step);
        return frames == null ? null : frames.toArray(AimFrame[]::new);
    }

}
