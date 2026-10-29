package com.g4vrk.react.vector.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FrameHistory {

    private final AimFrame[] frames;
    private int writeIndex;
    private int size;
    private int framesSinceLastSnapshot;
    private boolean firstSnapshotSent;

    public FrameHistory(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than zero");
        }
        this.frames = new AimFrame[capacity];
    }

    public synchronized void addFrame(final @NotNull AimFrame frame) {
        if (frame == null) {
            throw new NullPointerException("frame");
        }
        frames[writeIndex] = frame;
        writeIndex = (writeIndex + 1) % frames.length;
        if (size < frames.length) {
            size++;
        }
        framesSinceLastSnapshot++;
    }

    public synchronized boolean isFull() {
        return size == frames.length;
    }

    public synchronized int size() {
        return size;
    }

    public int capacity() {
        return frames.length;
    }

    public synchronized @Nullable List<AimFrame> pollSnapshot(final int step) {
        if (!isFull()) {
            return null;
        }
        if (firstSnapshotSent && framesSinceLastSnapshot < Math.max(1, step)) {
            return null;
        }
        firstSnapshotSent = true;
        framesSinceLastSnapshot = 0;
        return getSnapshot();
    }

    public synchronized @NotNull List<AimFrame> getSnapshot() {
        final List<AimFrame> snapshot = new ArrayList<>(size);
        final int oldest = oldestIndex();
        for (int i = 0; i < size; i++) {
            snapshot.add(frames[(oldest + i) % frames.length]);
        }
        return snapshot;
    }

    public synchronized @Nullable AimFrame[] latest(final int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (size < count) {
            return null;
        }
        final AimFrame[] window = new AimFrame[count];
        final int start = oldestIndex() + size - count;
        for (int i = 0; i < count; i++) {
            window[i] = frames[(start + i) % frames.length];
        }
        return window;
    }

    public synchronized void clear() {
        Arrays.fill(frames, null);
        writeIndex = 0;
        size = 0;
        framesSinceLastSnapshot = 0;
        firstSnapshotSent = false;
    }

    private int oldestIndex() {
        return size == frames.length ? writeIndex : 0;
    }

}
