package com.g4vrk.react.vector.model;

import org.jetbrains.annotations.NotNull;

public enum VectorFeature {

    ROTATION_PRESENT("rotation_present"),
    PITCH("pitch"),
    MODE_YAW("mode_yaw"),
    MODE_PITCH("mode_pitch"),
    MODE_YAW_VALID("mode_yaw_valid"),
    MODE_PITCH_VALID("mode_pitch_valid"),
    DELTA_DOTS_YAW("delta_dots_yaw"),
    DELTA_DOTS_PITCH("delta_dots_pitch"),
    TARGET_VALID("target_valid"),
    TARGET_AIM_ANGLE("target_aim_angle"),
    TARGET_REL_X("target_rel_x"),
    TARGET_REL_Y("target_rel_y"),
    TARGET_REL_Z("target_rel_z"),
    TARGETS_IN_RANGE_COUNT("targets_in_range_count"),
    HELD_ITEM_CLASS("held_item_class"),
    PLAYER_PING("player_ping");

    public static final int COUNT = values().length;

    private final String column;

    VectorFeature(final String column) {
        this.column = column;
    }

    public void put(final @NotNull float[] row, final float value) {
        row[ordinal()] = value;
    }

    public void put(final @NotNull float[] row, final boolean flag) {
        row[ordinal()] = flag ? 1.0f : 0.0f;
    }

    public int datasetIndex() {
        return 8 + ordinal();
    }

    public static @NotNull String csvColumns() {
        final String[] columns = new String[COUNT];
        for (final VectorFeature feature : values()) {
            columns[feature.ordinal()] = feature.column;
        }
        return String.join(",", columns);
    }

}
