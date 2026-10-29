package com.g4vrk.react.vector.session;

public final class CombatWindow {

    private volatile long activeUntilMillis;

    public boolean isActive() {
        return System.currentTimeMillis() <= activeUntilMillis;
    }

    public void extend(final long durationTicks) {
        activeUntilMillis = System.currentTimeMillis() + durationTicks * 50L;
    }

    public void reset() {
        activeUntilMillis = 0L;
    }

}
