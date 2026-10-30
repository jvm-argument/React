package com.g4vrk.react.inference.aim;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

@Value
public class InferenceResult {

    private static final double DEFAULT_VALUE = -1.0D;

    double probability;
    double decisionThreshold;
    boolean enforcementEnabled;
    String modelFamily;
    String modelName;

    public InferenceResult(double probability) {
        this(probability, Double.NaN, true);
    }

    public InferenceResult(double probability, double decisionThreshold, boolean enforcementEnabled) {
        this(probability, decisionThreshold, enforcementEnabled, "", "");
    }

    public InferenceResult(double probability, double decisionThreshold, boolean enforcementEnabled,
                           String modelFamily, String modelName) {
        this.probability = probability;
        this.decisionThreshold = decisionThreshold;
        this.enforcementEnabled = enforcementEnabled;
        this.modelFamily = modelFamily == null ? "" : modelFamily;
        this.modelName = modelName == null ? "" : modelName;
    }

    public String modelDisplay() {
        return switch (modelFamily + "/" + modelName) {
            case "vector/preview" -> "Vector/Preview";
            default -> "Model unknown";
        };
    }

    public boolean isAvailable() {
        return probability >= 0.0D;
    }

    public static @NotNull InferenceResult unavailable() {
        return new InferenceResult(DEFAULT_VALUE);
    }
}
