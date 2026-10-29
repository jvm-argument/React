package com.g4vrk.react.vector.util;

public final class MouseMath {

    private static final double MINIMUM_DIVISOR = Math.pow(0.2, 3.0) * 8.0 * 0.15 - 0.001;
    private static final double GCD_INPUT_EPSILON = 1.0E-6;

    private MouseMath() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static float calculateAcceleration(final float currentDelta, final float previousDelta) {
        return currentDelta - previousDelta;
    }

    public static float calculateJerk(final float currentAccel, final float previousAccel) {
        return currentAccel - previousAccel;
    }

    public static float calculateGCDError(final float currentDelta, final float previousDelta) {
        final double current = Math.abs(currentDelta);
        final double previous = Math.abs(previousDelta);
        if (current < MINIMUM_DIVISOR || previous < MINIMUM_DIVISOR) {
            return 0.0f;
        }
        final double gcd = gcd(current, previous);
        if (gcd < MINIMUM_DIVISOR) {
            return 0.0f;
        }
        final double snapped = (double) Math.round(current / gcd) * gcd;
        return (float) (Math.abs(current - snapped) / gcd);
    }

    public static float calculateGrid(final float currentDelta, final float previousDelta) {
        final double current = Math.abs(currentDelta);
        final double previous = Math.abs(previousDelta);
        if (current < MINIMUM_DIVISOR || previous < MINIMUM_DIVISOR) {
            return 0.0f;
        }
        final double gcd = gcd(current, previous);
        return gcd < MINIMUM_DIVISOR ? 0.0f : (float) gcd;
    }

    public static float normalizeAngle(final float angle) {
        final float normalized = angle % 360.0f;
        if (normalized <= -180.0f) {
            return normalized + 360.0f;
        }
        if (normalized > 180.0f) {
            return normalized - 360.0f;
        }
        return normalized;
    }

    public static double gcd(final double first, final double second) {
        double a = Math.abs(first);
        double b = Math.abs(second);
        if (a < GCD_INPUT_EPSILON || b < GCD_INPUT_EPSILON) {
            return 0.0;
        }
        if (a < b) {
            final double swap = a;
            a = b;
            b = swap;
        }
        while (b > MINIMUM_DIVISOR) {
            final double remainder = a - Math.floor(a / b) * b;
            a = b;
            b = remainder;
        }
        return a;
    }

    public static float distanceToGrid(final float delta, final float grid) {
        final double absDelta = Math.abs(delta);
        final double absGrid = Math.abs(grid);
        if (absDelta < MINIMUM_DIVISOR || absGrid < MINIMUM_DIVISOR) {
            return 0.0f;
        }
        final double snapped = (double) Math.round(absDelta / absGrid) * absGrid;
        return (float) Math.abs(absDelta - snapped);
    }

    public static float getMinimumDivisor() {
        return (float) MINIMUM_DIVISOR;
    }

}
