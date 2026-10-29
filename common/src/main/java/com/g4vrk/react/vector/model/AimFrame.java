package com.g4vrk.react.vector.model;

import java.util.Arrays;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AimFrame {

    public static final int DATASET_FEATURE_COUNT = 8 + VectorFeature.COUNT;
    public static final int INFERENCE_FEATURE_COUNT = DATASET_FEATURE_COUNT - 1;
    public static final String BASE_HEADER = "delta_yaw,delta_pitch,accel_yaw,accel_pitch,jerk_yaw,jerk_pitch,gcd_error_yaw,gcd_error_pitch";

    private final float[] features;
    private volatile float[] inferenceFeatures;

    public AimFrame(
            final float deltaYaw,
            final float deltaPitch,
            final float accelYaw,
            final float accelPitch,
            final float jerkYaw,
            final float jerkPitch,
            final float gcdErrorYaw,
            final float gcdErrorPitch,
            final @Nullable float[] vector
    ) {
        if (vector != null && vector.length != VectorFeature.COUNT) {
            throw new IllegalArgumentException("Expected " + VectorFeature.COUNT + " VECTOR features");
        }
        this.features = new float[DATASET_FEATURE_COUNT];
        features[0] = deltaYaw;
        features[1] = deltaPitch;
        features[2] = accelYaw;
        features[3] = accelPitch;
        features[4] = jerkYaw;
        features[5] = jerkPitch;
        features[6] = gcdErrorYaw;
        features[7] = gcdErrorPitch;
        if (vector != null) {
            System.arraycopy(vector, 0, features, VectorFeature.ROTATION_PRESENT.datasetIndex(), VectorFeature.COUNT);
        }
    }

    public static @NotNull AimFrame fromFeatures(final @NotNull float[] values) {
        if (values.length != DATASET_FEATURE_COUNT) {
            throw new IllegalArgumentException("Expected " + DATASET_FEATURE_COUNT + " VECTOR features");
        }
        return new AimFrame(
                values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
                Arrays.copyOfRange(values, VectorFeature.ROTATION_PRESENT.datasetIndex(), values.length)
        );
    }

    public static @NotNull String csvHeader() {
        return "is_cheating," + BASE_HEADER + "," + VectorFeature.csvColumns();
    }

    public @NotNull float[] datasetFeatures() {
        return features.clone();
    }

    public @NotNull float[] inferenceFeatures() {
        float[] inputs = inferenceFeatures;
        if (inputs == null) {
            inputs = new float[INFERENCE_FEATURE_COUNT];
            for (int index = 0; index < inputs.length; index++) {
                inputs[index] = Float.parseFloat(formatDatasetValue(features[index]));
            }
            inferenceFeatures = inputs;
        }
        return inputs.clone();
    }

    public @NotNull String toCsvRow(final boolean cheater) {
        final StringBuilder row = new StringBuilder(cheater ? "1" : "0");
        for (final float value : features) {
            row.append(',').append(formatDatasetValue(value));
        }
        return row.toString();
    }

    private static String formatDatasetValue(final float value) {
        return String.format(Locale.US, "%.6f", value);
    }

}
