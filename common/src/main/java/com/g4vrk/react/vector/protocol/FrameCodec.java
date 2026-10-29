package com.g4vrk.react.vector.protocol;

import com.g4vrk.react.vector.model.AimFrame;
import com.g4vrk.react.vector.protocol.schema.InferenceRequest;
import com.g4vrk.react.vector.protocol.schema.InferenceResponse;
import com.google.flatbuffers.FlatBufferBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FrameCodec {

    public static final int SCHEMA_VERSION = 2;
    public static final String SCHEMA_HASH = "b6e9e126d24af9bfeca4926b0c385bcd68f807b6baaf027934ec88cf14486c06";

    private FrameCodec() {
    }

    public static byte[] encodeRequest(final @NotNull AimFrame[] frames) {
        return encodeRequest(frames, "Unknown", null);
    }

    public static byte[] encodeRequest(
            final @NotNull AimFrame[] frames,
            final @NotNull String playerName,
            final @Nullable String token
    ) {
        if (frames.length == 0) {
            throw new IllegalArgumentException("VECTOR frames cannot be empty");
        }
        final float[] features = flatten(frames);
        final FlatBufferBuilder builder = new FlatBufferBuilder(64 + features.length * 4);
        final int featuresOffset = InferenceRequest.createFeaturesVector(builder, features);
        final int nameOffset = builder.createString(playerName);
        final int schemaOffset = builder.createString(SCHEMA_HASH);
        final int tokenOffset = token == null || token.isBlank() ? 0 : builder.createString(token);
        InferenceRequest.startInferenceRequest(builder);
        InferenceRequest.addToken(builder, tokenOffset);
        InferenceRequest.addFeatures(builder, featuresOffset);
        InferenceRequest.addFrameCount(builder, frames.length);
        InferenceRequest.addModelId(builder, 2);
        InferenceRequest.addSchemaVersion(builder, SCHEMA_VERSION);
        InferenceRequest.addPlayerName(builder, nameOffset);
        InferenceRequest.addSchemaHash(builder, schemaOffset);
        InferenceRequest.addSupportsObservation(builder, true);
        InferenceRequest.finishInferenceRequestBuffer(builder, InferenceRequest.endInferenceRequest(builder));
        return builder.sizedByteArray();
    }

    public static byte[] encodeBatch(final @NotNull AimFrame[] frames) {
        return encodeBatch(frames, "Unknown", null);
    }

    public static byte[] encodeBatch(
            final @NotNull AimFrame[] frames,
            final @NotNull String playerName,
            final @Nullable String token
    ) {
        final byte[] payload = encodeRequest(frames, playerName, token);
        return ByteBuffer.allocate(6 + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 1)
                .putInt(payload.length)
                .put(payload)
                .array();
    }

    public static double decodeResponse(final @NotNull byte[] body) {
        if (body.length < 14 || !hasBatchIdentifier(body)) {
            throw new IllegalArgumentException("Invalid VECTOR response; expected GAIB");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(4);
        if (Short.toUnsignedInt(buffer.getShort()) != 1) {
            throw new IllegalArgumentException("VECTOR response item count must be 1");
        }
        final double probability = buffer.getDouble();
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Invalid VECTOR probability");
        }
        return probability;
    }

    public static @NotNull Prediction decodePrediction(final @NotNull byte[] body) {
        final double prefixProbability = decodeResponse(body);
        final ByteBuffer payload = ByteBuffer.wrap(body, 14, body.length - 14)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        try {
            return readPrediction(payload, prefixProbability);
        } catch (final IndexOutOfBoundsException | NegativeArraySizeException | ArithmeticException malformed) {
            throw new IllegalArgumentException("Malformed VECTOR response", malformed);
        }
    }

    private static Prediction readPrediction(final ByteBuffer payload, final double prefixProbability) {
        if (!InferenceResponse.InferenceResponseBufferHasIdentifier(payload)) {
            throw new IllegalArgumentException("Expected GAIR response");
        }
        final InferenceResponse response = InferenceResponse.getRootAsInferenceResponse(payload);
        if (response.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported VECTOR response schema");
        }
        final double probability = response.cheatProbability();
        final double threshold = response.decisionThreshold();
        final int mode = response.deploymentMode();
        final boolean enforcementEnabled = response.enforcementEnabled();
        if (mode > 1 || enforcementEnabled != (mode == 1) || !Double.isFinite(threshold) || threshold < 0 || threshold > 1
                || Double.compare(probability, prefixProbability) != 0) {
            throw new IllegalArgumentException("Inconsistent VECTOR response metadata");
        }
        return new Prediction(probability, threshold, enforcementEnabled, readRunId(response));
    }

    private static String readRunId(final InferenceResponse response) {
        final ByteBuffer encoded = response.runIdAsByteBuffer();
        if (encoded == null) {
            return "";
        }
        if (encoded.remaining() > 1024) {
            throw new IllegalArgumentException("Invalid run identifier");
        }
        return response.runId();
    }

    private static float[] flatten(final AimFrame[] frames) {
        final float[] features = new float[frames.length * AimFrame.INFERENCE_FEATURE_COUNT];
        for (int frameIndex = 0; frameIndex < frames.length; frameIndex++) {
            final float[] inputs = frames[frameIndex].inferenceFeatures();
            for (final float value : inputs) {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException("Nonfinite VECTOR feature");
                }
            }
            System.arraycopy(inputs, 0, features, frameIndex * inputs.length, inputs.length);
        }
        return features;
    }

    private static boolean hasBatchIdentifier(final byte[] body) {
        return body[0] == 'G' && body[1] == 'A' && body[2] == 'I' && body[3] == 'B';
    }

    public record Prediction(double probability, double threshold, boolean enforcementEnabled, String runId) {

    }

}
