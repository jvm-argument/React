package com.g4vrk.react.vector.session;

public final class CombatWindow {

    private static final long MILLIS_PER_TICK = 50L;

    private volatile long activeUntilMillis;

    public boolean isActive() {
        return System.currentTimeMillis() <= activeUntilMillis;
    }

    public void extend(final long durationTicks) {
        activeUntilMillis = System.currentTimeMillis() + durationTicks * MILLIS_PER_TICK;
    }

    public void reset() {
        activeUntilMillis = 0L;
    }

}
