package com.g4vrk.react.inference.aim;

import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.react.util.moshi.MoshiHolder;
import com.g4vrk.react.vector.model.AimFrame;
import com.g4vrk.react.vector.protocol.FrameCodec;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import com.squareup.moshi.JsonAdapter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

public final class InferenceAimProcessor {

    private final Logger logger;
    private final InferenceServer inferenceServer;
    private final Scheduler scheduler;

    private final JsonAdapter<Map<String, Object>> responseAdapter;

    public InferenceAimProcessor(
            @NotNull Logger logger,
            @NotNull InferenceServer inferenceServer,
            @NotNull Scheduler scheduler
    ) {
        this.logger = logger;
        this.inferenceServer = inferenceServer;
        this.scheduler = scheduler;

        this.responseAdapter = MoshiHolder.RESPONSE_ADAPTER;
    }

    public long requestEpoch() {
        return inferenceServer.requestEpoch();
    }

    public void publishIfCurrent(final long epoch, final @NotNull Runnable publication) {
        inferenceServer.publishIfCurrent(epoch, publication);
    }

    public void check(
            final @NotNull String playerName,
            final @NotNull AimFrame[] snapshot,
            final @NotNull Consumer<InferenceResult> resultHandler
    ) {
        if (!inferenceServer.isAcceptingRequests() || snapshot.length == 0) {
            complete(resultHandler, InferenceResult.unavailable());
            return;
        }
        try {
            final byte[] batch = FrameCodec.encodeBatch(snapshot, playerName, inferenceServer.getAuthBodyToken());
            final HttpRequest request = new HttpRequest("POST",
                    RequestBody.create(batch, MediaType.get("application/x-flatbuffers")),
                    Map.of("Accept", "application/x-flatbuffers", "X-Batch", "1"));
            enqueue(playerName, request, resultHandler);
        } catch (RuntimeException failure) {
            logger.warn("Could not enqueue VECTOR request for {}", playerName, failure);
            complete(resultHandler, InferenceResult.unavailable());
        }
    }

    private void enqueue(
            final String playerName,
            final HttpRequest request,
            final Consumer<InferenceResult> originalResultHandler
    ) {
        final long requestEpoch = inferenceServer.requestEpoch();
        final Consumer<InferenceResult> resultHandler = result -> {
            if (requestEpoch == inferenceServer.requestEpoch()) originalResultHandler.accept(result);
        };
        inferenceServer.callAsync(request, new Callback() {

            @Override
            public void onFailure(
                    @NotNull Call call,
                    @NotNull IOException ex
            ) {
                logger.warn("Could not send VECTOR frames for {}", playerName, ex);

                complete(resultHandler, InferenceResult.unavailable());
            }

            @Override
            public void onResponse(
                    @NotNull Call call,
                    @NotNull Response response
            ) {
                try (ResponseBody body = response.body()) {
                    final String responseText;
                    if (body == null) {
                        responseText = "";
                    } else {
                        final BufferedSource source = body.source();
                        if (source.request(64L * 1024L + 1L)) {
                            logger.warn(
                                    "ML server response is too large for {}: more than {} bytes",
                                    playerName,
                                    64L * 1024L
                            );
                            complete(resultHandler, InferenceResult.unavailable());
                            return;
                        }
                        if (response.isSuccessful()) {
                            final byte[] bytes = source.readByteArray();
                            if (bytes.length >= 4 && bytes[0] == 'G' && bytes[1] == 'A'
                                    && bytes[2] == 'I' && bytes[3] == 'B') {
                                FrameCodec.Prediction prediction = FrameCodec.decodePrediction(bytes);
                                complete(resultHandler, new InferenceResult(prediction.probability(),
                                        prediction.threshold(), prediction.enforcementEnabled(),
                                        response.header("X-React-Model-Family"), response.header("X-React-Model-Name")));
                                return;
                            }
                            responseText = new String(bytes, StandardCharsets.UTF_8);
                        } else {
                            responseText = source.readUtf8();
                        }
                    }

                    if (!response.isSuccessful()) {
                        logger.warn(
                                "ML server rejected request for {}: HTTP {} {}",
                                playerName,
                                response.code(),
                                responseText
                        );

                        complete(resultHandler, InferenceResult.unavailable());
                        return;
                    }

                    if (responseText.isBlank()) {
                        logger.warn("ML server returned an empty response for {}", playerName);

                        complete(resultHandler, InferenceResult.unavailable());
                        return;
                    }

                    final Map<String, Object> result = responseAdapter.fromJson(responseText);

                    if (result == null) {
                        logger.warn("Could not parse ML response for {}: {}", playerName, responseText);

                        complete(resultHandler, InferenceResult.unavailable());
                        return;
                    }

                    final Double probability = readNumber(result, "cheat_probability");

                    if (probability == null || !Double.isFinite(probability)
                            || probability < 0.0D || probability > 1.0D) {
                        logger.warn(
                                "ML response does not contain a valid 'cheat_probability' field for {}: {}",
                                playerName,
                                responseText
                        );

                        complete(resultHandler, InferenceResult.unavailable());
                        return;
                    }

                    Double threshold = readNumber(result, "decision_threshold");
                    Object enabled = result.get("enforcement_enabled");
                    Object mode = result.get("deployment_mode");
                    if (threshold == null || !Double.isFinite(threshold) || threshold < 0 || threshold > 1
                            || !(enabled instanceof Boolean) || !(mode instanceof String)
                            || !((Boolean) enabled ? "enforce" : "observe").equals(mode)) {
                        throw new IllegalArgumentException("Vector response requires a valid deployment mode and threshold");
                    }
                    complete(resultHandler, new InferenceResult(probability, threshold, (Boolean) enabled,
                            readModelPart(result, "model_family"), readModelPart(result, "model_name")));
                } catch (Exception ex) {
                    logger.warn("Could not process ML response for {}", playerName, ex);

                    complete(resultHandler, InferenceResult.unavailable());
                }
            }
        });
    }

    private static Double readNumber(
            final @NotNull Map<String, Object> response,
            final @NotNull String field
    ) {
        final Object raw = response.get(field);
        return raw instanceof Number number ? number.doubleValue() : null;
    }

    private static String readModelPart(final Map<String, Object> response, final String key) {
        final Object value = response.get(key);
        return value instanceof String text ? text : "";
    }

    private void complete(
            @NotNull Consumer<InferenceResult> resultHandler,
            @NotNull InferenceResult result
    ) {
        scheduler.schedule(
                () -> {
                    if (inferenceServer.isAcceptingRequests()) resultHandler.accept(result);
                },
                TickSchedule.instant()
        );
    }

}
