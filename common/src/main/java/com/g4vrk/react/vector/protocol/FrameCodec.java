package com.g4vrk.react.vector.protocol;

import com.g4vrk.react.vector.model.AimFrame;
import com.google.flatbuffers.FlatBufferBuilder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FrameCodec {

    public static final int SCHEMA_VERSION = 2;
    public static final String SCHEMA_HASH = "b6e9e126d24af9bfeca4926b0c385bcd68f807b6baaf027934ec88cf14486c06";

    private static final int MODEL_ID = 2;
    private static final String REQUEST_IDENTIFIER = "GAIQ";
    private static final int BATCH_HEADER_SIZE = 2 + 4;
    private static final int RESPONSE_PREFIX_SIZE = 14;
    private static final int MIN_VERSIONED_RESPONSE_SIZE = 26;
    private static final int MAX_RUN_ID_LENGTH = 1024;
    private static final String UNKNOWN_PLAYER = "Unknown";

    private FrameCodec() {
    }

    public static byte[] encodeRequest(final @NotNull AimFrame[] frames) {
        return encodeRequest(frames, UNKNOWN_PLAYER, null);
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
        builder.startVector(4, features.length, 4);
        for (int index = features.length - 1; index >= 0; index--) {
            builder.addFloat(features[index]);
        }
        final int featuresOffset = builder.endVector();
        final int nameOffset = builder.createString(playerName);
        final int schemaOffset = builder.createString(SCHEMA_HASH);
        final int tokenOffset = token == null || token.isBlank() ? 0 : builder.createString(token);
        builder.startTable(8);
        builder.addOffset(0, tokenOffset, 0);
        builder.addOffset(2, featuresOffset, 0);
        builder.addInt(1, frames.length, 0);
        builder.addInt(3, MODEL_ID, 0);
        builder.addInt(4, SCHEMA_VERSION, 0);
        builder.addOffset(5, nameOffset, 0);
        builder.addOffset(6, schemaOffset, 0);
        builder.addBoolean(7, true, false);
        builder.finish(builder.endTable(), REQUEST_IDENTIFIER);
        return builder.sizedByteArray();
    }

    public static byte[] encodeBatch(final @NotNull AimFrame[] frames) {
        return encodeBatch(frames, UNKNOWN_PLAYER, null);
    }

    public static byte[] encodeBatch(
            final @NotNull AimFrame[] frames,
            final @NotNull String playerName,
            final @Nullable String token
    ) {
        final byte[] payload = encodeRequest(frames, playerName, token);
        return ByteBuffer.allocate(BATCH_HEADER_SIZE + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 1)
                .putInt(payload.length)
                .put(payload)
                .array();
    }

    public static double decodeResponse(final @NotNull byte[] body) {
        if (body.length < RESPONSE_PREFIX_SIZE || !hasIdentifier(body, 0, "GAIB")) {
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
        if (body.length < MIN_VERSIONED_RESPONSE_SIZE) {
            throw new IllegalArgumentException("Versioned VECTOR response is required");
        }
        final ByteBuffer payload = ByteBuffer.wrap(body, RESPONSE_PREFIX_SIZE, body.length - RESPONSE_PREFIX_SIZE)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        if (payload.get(4) != 'G' || payload.get(5) != 'A' || payload.get(6) != 'I' || payload.get(7) != 'R') {
            throw new IllegalArgumentException("Expected GAIR response");
        }
        final int table = payload.getInt(0);
        require(payload, table, 4);
        final int vtable = Math.subtractExact(table, payload.getInt(table));
        require(payload, vtable, 4);
        final int vtableSize = Short.toUnsignedInt(payload.getShort(vtable));
        final int objectSize = Short.toUnsignedInt(payload.getShort(vtable + 2));
        if (vtableSize < 4 || vtableSize % 2 != 0 || objectSize < 4) {
            throw new IllegalArgumentException("Invalid response table dimensions");
        }
        require(payload, vtable, vtableSize);
        require(payload, table, objectSize);
        final int version = field(payload, table, vtable, 0, 4);
        if (version < 0 || payload.getInt(version) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported VECTOR response schema");
        }
        final int probabilityField = field(payload, table, vtable, 1, 8);
        final int thresholdField = field(payload, table, vtable, 2, 8);
        final int modeField = field(payload, table, vtable, 3, 1);
        final int enabledField = field(payload, table, vtable, 4, 1);
        final double probability = probabilityField < 0 ? 0 : payload.getDouble(probabilityField);
        final double threshold = thresholdField < 0 ? 0 : payload.getDouble(thresholdField);
        final int mode = modeField < 0 ? 0 : Byte.toUnsignedInt(payload.get(modeField));
        final int enabled = enabledField < 0 ? 0 : Byte.toUnsignedInt(payload.get(enabledField));
        if (mode > 1 || enabled != mode || !Double.isFinite(threshold) || threshold < 0 || threshold > 1
                || Double.compare(probability, prefixProbability) != 0) {
            throw new IllegalArgumentException("Inconsistent VECTOR response metadata");
        }
        return new Prediction(probability, threshold, enabled == 1, readRunId(payload, table, vtable));
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

    private static boolean hasIdentifier(final byte[] body, final int offset, final String identifier) {
        for (int index = 0; index < identifier.length(); index++) {
            if (body[offset + index] != identifier.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static String readRunId(final ByteBuffer payload, final int table, final int vtable) {
        final int runField = field(payload, table, vtable, 5, 4);
        if (runField < 0) {
            return "";
        }
        final int string = Math.addExact(runField, payload.getInt(runField));
        require(payload, string, 4);
        final int length = payload.getInt(string);
        if (length < 0 || length > MAX_RUN_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid run identifier");
        }
        require(payload, string + 4, length + 1);
        if (payload.get(string + 4 + length) != 0) {
            throw new IllegalArgumentException("Unterminated run identifier");
        }
        final byte[] text = new byte[length];
        payload.position(string + 4);
        payload.get(text);
        return new String(text, StandardCharsets.UTF_8);
    }

    private static int field(
            final ByteBuffer buffer,
            final int table,
            final int vtable,
            final int slot,
            final int width
    ) {
        final int offset = 4 + slot * 2;
        final int vtableSize = Short.toUnsignedInt(buffer.getShort(vtable));
        if (offset + 2 > vtableSize) {
            return -1;
        }
        final int relative = Short.toUnsignedInt(buffer.getShort(vtable + offset));
        if (relative == 0) {
            return -1;
        }
        final int objectSize = Short.toUnsignedInt(buffer.getShort(vtable + 2));
        if (relative < 4 || relative + width > objectSize) {
            throw new IllegalArgumentException("Invalid response field");
        }
        return Math.addExact(table, relative);
    }

    private static void require(final ByteBuffer buffer, final int start, final int length) {
        if (start < 0 || length < 0 || start > buffer.limit() - length) {
            throw new IllegalArgumentException("Invalid VECTOR response offset");
        }
    }

    public record Prediction(double probability, double threshold, boolean enforcementEnabled, String runId) {

    }

}
