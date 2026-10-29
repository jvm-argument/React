package com.g4vrk.react.vector.feature;

import com.g4vrk.react.vector.model.VectorFeature;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class AimModeTracker {

    private static final double MINIMUM_DIVISOR = (double) ((float) Math.pow(0.2f, 3.0)) * 8.0 * 0.15 - 0.001;

    private final RunningMode yawMode = new RunningMode();
    private final RunningMode pitchMode = new RunningMode();
    private double lastYawDelta;
    private double lastPitchDelta;
    private double modeYaw;
    private double modePitch;
    private double deltaDotsYaw;
    private double deltaDotsPitch;

    public void update(final float fromYaw, final float toYaw, final float deltaYaw, final float deltaPitch) {
        final double yawAbs = Math.abs(deltaYaw);
        final double pitchAbs = Math.abs(deltaPitch);
        trackYaw(fromYaw, toYaw, yawAbs);
        trackPitch(pitchAbs);
        if (yawMode.size() > 15) {
            yawMode.refreshPeak();
            if (yawMode.peakCount > 15) {
                modeYaw = yawMode.peakValue;
            }
        }
        if (pitchMode.size() > 15) {
            pitchMode.refreshPeak();
            if (pitchMode.peakCount > 15) {
                modePitch = pitchMode.peakValue;
            }
        }
        if (modeYaw > 0.0) {
            deltaDotsYaw = yawAbs / modeYaw;
        }
        if (modePitch > 0.0) {
            deltaDotsPitch = pitchAbs / modePitch;
        }
    }

    public void writeTo(final @NotNull float[] features) {
        final boolean modeYawValid = yawMode.peakCount > 15;
        final boolean modePitchValid = pitchMode.peakCount > 15;
        VectorFeature.MODE_YAW.put(features, (float) modeYaw);
        VectorFeature.MODE_PITCH.put(features, (float) modePitch);
        VectorFeature.MODE_YAW_VALID.put(features, modeYawValid);
        VectorFeature.MODE_PITCH_VALID.put(features, modePitchValid);
        VectorFeature.DELTA_DOTS_YAW.put(features, modeYawValid ? (float) deltaDotsYaw : 0.0f);
        VectorFeature.DELTA_DOTS_PITCH.put(features, modePitchValid ? (float) deltaDotsPitch : 0.0f);
    }

    public void reset() {
        yawMode.clear();
        pitchMode.clear();
        lastYawDelta = 0.0;
        lastPitchDelta = 0.0;
        modeYaw = 0.0;
        modePitch = 0.0;
        deltaDotsYaw = 0.0;
        deltaDotsPitch = 0.0;
    }

    private void trackYaw(final float fromYaw, final float toYaw, final double yawAbs) {
        final double divisor = gcd(yawAbs, lastYawDelta);
        if (yawAbs <= 0.0 || yawAbs >= 5.0 || divisor <= MINIMUM_DIVISOR) {
            return;
        }
        final double yawUlp = Math.ulp(Math.max(Math.abs(fromYaw), Math.abs(toYaw)));
        if (yawUlp >= divisor / 16.0) {
            return;
        }
        if (lastYawDelta != 0.0) {
            yawMode.add(divisor, Math.max(0.001, yawUlp * 2.0));
        }
        lastYawDelta = yawAbs;
    }

    private void trackPitch(final double pitchAbs) {
        final double divisor = gcd(pitchAbs, lastPitchDelta);
        if (pitchAbs <= 0.0 || pitchAbs >= 5.0 || divisor <= MINIMUM_DIVISOR) {
            return;
        }
        if (lastPitchDelta != 0.0) {
            pitchMode.add(divisor, 0.001);
        }
        lastPitchDelta = pitchAbs;
    }

    private static double gcd(final double first, final double second) {
        if (first == 0.0) {
            return 0.0;
        }
        double a = first;
        double b = second;
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

    private static final class RunningMode {

        private final ArrayDeque<Double> window = new ArrayDeque<>(80);
        private final Map<Double, Integer> popularity = new HashMap<>();
        private double peakValue;
        private int peakCount;

        int size() {
            return window.size();
        }

        void add(final double value, final double bucketWidth) {
            if (window.size() >= 80) {
                evictOldest();
            }
            for (final Map.Entry<Double, Integer> entry : popularity.entrySet()) {
                if (Math.abs(entry.getKey() - value) < bucketWidth) {
                    entry.setValue(entry.getValue() + 1);
                    window.add(entry.getKey());
                    return;
                }
            }
            popularity.put(value, 1);
            window.add(value);
        }

        void refreshPeak() {
            int max = 0;
            double mostPopular = 0.0;
            for (final Map.Entry<Double, Integer> entry : popularity.entrySet()) {
                if (entry.getValue() > max) {
                    max = entry.getValue();
                    mostPopular = entry.getKey();
                }
            }
            peakValue = mostPopular;
            peakCount = max;
        }

        void clear() {
            window.clear();
            popularity.clear();
            peakValue = 0.0;
            peakCount = 0;
        }

        private void evictOldest() {
            final Double oldest = window.poll();
            if (oldest == null) {
                return;
            }
            final Integer count = popularity.get(oldest);
            if (count == null || count == 1) {
                popularity.remove(oldest);
            } else {
                popularity.put(oldest, count - 1);
            }
        }

    }

}
