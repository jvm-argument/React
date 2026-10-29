package com.g4vrk.react.vector.model;

import java.util.Arrays;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AimFrame {

    public static final int DATASET_FEATURE_COUNT = 24;
    public static final int INFERENCE_FEATURE_COUNT = 23;
    public static final String BASE_HEADER = "delta_yaw,delta_pitch,accel_yaw,accel_pitch,jerk_yaw,jerk_pitch,gcd_error_yaw,gcd_error_pitch";
    public static final int ROTATION_PRESENT = 0;
    public static final int PITCH = 1;
    public static final int MODE_YAW = 2;
    public static final int MODE_PITCH = 3;
    public static final int MODE_YAW_VALID = 4;
    public static final int MODE_PITCH_VALID = 5;
    public static final int DELTA_DOTS_YAW = 6;
    public static final int DELTA_DOTS_PITCH = 7;
    public static final int TARGET_VALID = 8;
    public static final int TARGET_AIM_ANGLE = 9;
    public static final int TARGET_REL_X = 10;
    public static final int TARGET_REL_Y = 11;
    public static final int TARGET_REL_Z = 12;
    public static final int TARGETS_IN_RANGE_COUNT = 13;
    public static final int HELD_ITEM_CLASS = 14;
    public static final int PLAYER_PING = 15;
    public static final String[] VECTOR_FEATURE_NAMES = {
            "rotation_present", "pitch", "mode_yaw", "mode_pitch", "mode_yaw_valid", "mode_pitch_valid",
            "delta_dots_yaw", "delta_dots_pitch", "target_valid", "target_aim_angle", "target_rel_x",
            "target_rel_y", "target_rel_z", "targets_in_range_count", "held_item_class", "player_ping"
    };
    public static final int VECTOR_FEATURE_COUNT = VECTOR_FEATURE_NAMES.length;

    private static final int BASE_FEATURE_COUNT = 8;

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
        if (vector != null && vector.length != VECTOR_FEATURE_COUNT) {
            throw new IllegalArgumentException("Expected " + VECTOR_FEATURE_COUNT + " VECTOR features");
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
            System.arraycopy(vector, 0, features, BASE_FEATURE_COUNT, VECTOR_FEATURE_COUNT);
        }
    }

    public static @NotNull AimFrame fromFeatures(final @NotNull float[] values) {
        if (values.length != DATASET_FEATURE_COUNT) {
            throw new IllegalArgumentException("Expected " + DATASET_FEATURE_COUNT + " VECTOR features");
        }
        return new AimFrame(
                values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
                Arrays.copyOfRange(values, BASE_FEATURE_COUNT, values.length)
        );
    }

    public static @NotNull String csvHeader() {
        return "is_cheating," + BASE_HEADER + "," + String.join(",", VECTOR_FEATURE_NAMES);
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
