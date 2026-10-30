package com.g4vrk.react.inference.server;

import com.g4vrk.react.inference.auth.AuthApplier;
import com.g4vrk.react.inference.auth.AuthSettings;
import com.g4vrk.react.inference.auth.type.AuthType;
import com.g4vrk.react.inference.http.model.HttpRequest;
import com.g4vrk.react.inference.server.settings.InferenceEndpointSettings;
import com.g4vrk.react.inference.server.settings.InferenceSettings;
import lombok.Getter;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import java.util.Map;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import com.squareup.moshi.Moshi;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Getter
public final class InferenceServer {

    private static final String PATH = "/api/v2/inference/";

    private final OkHttpClient client;
    private final AuthApplier authApplier;

    private final String baseUrl;
    private final InferenceEndpointSettings endpoint;

    private final boolean enabled;
    private final String authBodyToken;
    private final String modelMetadataToken;
    private final AtomicLong nextModelMetadataRequest = new AtomicLong();
    private volatile String selectedModelDisplay = "Unavailable";

    private final AtomicBoolean suspended = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong requestEpoch = new AtomicLong();

    public long requestEpoch() { return requestEpoch.get(); }

    /** Serialize the FINAL publication with pause, including callbacks on entity schedulers. */
    public synchronized void publishIfCurrent(long epoch, Runnable publication) {
        if (isAcceptingRequests() && requestEpoch.get() == epoch) publication.run();
    }

    public boolean isAcceptingRequests() {
        return enabled && !suspended.get() && !closed.get();
    }

    public synchronized void pauseRequests() {
        suspended.set(true);
        requestEpoch.incrementAndGet();
        client.dispatcher().cancelAll();
    }

    public synchronized void resumeRequests() {
        if (!closed.get()) suspended.set(false);
    }

    public InferenceServer(
            final @NotNull Logger logger,
            final @NotNull InferenceSettings settings
    ) {
        this.client = settings.buildClient();
        this.enabled = settings.isEnabled();
        this.modelMetadataToken = settings.getAuth().isEnabled() ? settings.getAuth().getValue() : null;
        this.authBodyToken = settings.getAuth().isEnabled() && settings.getAuth().getType() == AuthType.BODY
                ? settings.getAuth().getValue() : null;

        this.endpoint = settings.getEndpoint();

        this.baseUrl = normalizeUrl(endpoint.getBaseUrl());
        this.authApplier = new AuthApplier(settings.getAuth());

        if (enabled) {
            validate(logger, settings.getAuth());
        }
    }

    /** Nonblocking display metadata, resolved from the same subscription as inference. */
    public String getSelectedModelDisplay() {
        if (!enabled) return "Disabled";
        long now = System.currentTimeMillis();
        long next = nextModelMetadataRequest.get();
        if (now >= next && nextModelMetadataRequest.compareAndSet(next, now + 60_000L)) {
            refreshSelectedModelDisplay();
        }
        return selectedModelDisplay;
    }

    private void refreshSelectedModelDisplay() {
        if (modelMetadataToken == null || modelMetadataToken.isBlank()) {
            selectedModelDisplay = "Unavailable";
            return;
        }
        HttpUrl origin = HttpUrl.parse(baseUrl);
        if (origin == null) return;
        HttpUrl url = origin.newBuilder().encodedPath("/api/v1/subscriptions/model")
                .query(null).fragment(null).build();
        Request request = new Request.Builder().url(url)
                .header("X-Subscription-Token", modelMetadataToken).get().build();
        try {
            // Never forward the subscription secret to a redirect destination.
            client.newBuilder().followRedirects(false).followSslRedirects(false).build()
                    .newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException exception) {
                    selectedModelDisplay = "Unavailable";
                }
                @Override public void onResponse(Call call, Response response) {
                    try (response; ResponseBody body = response.body()) {
                        if (!response.isSuccessful() || body == null || body.source().request(8193)) {
                            selectedModelDisplay = "Unavailable";
                            return;
                        }
                        Map<?, ?> metadata = new Moshi.Builder().build().adapter(Map.class).fromJson(body.string());
                        String family = metadata == null ? "" : String.valueOf(metadata.get("model_family"));
                        String name = metadata == null ? "" : String.valueOf(metadata.get("model_name"));
                        String identity = family + "/" + name;
                        selectedModelDisplay = switch (identity) {
                            case "vector/preview" -> "Vector Preview";
                            default -> "Unavailable";
                        };
                    } catch (Exception ignored) {
                        selectedModelDisplay = "Unavailable";
                    }
                }
            });
        } catch (RuntimeException ignored) {
            selectedModelDisplay = "Unavailable";
        }
    }

    public @NotNull Call newCall(
            final @NotNull HttpRequest httpRequest
    ) {
        if (!isAcceptingRequests()) throw new IllegalStateException("Inference client is paused or closed");
        final String rawUrl = baseUrl + PATH;
        final HttpUrl parsedUrl = HttpUrl.parse(rawUrl);

        if (parsedUrl == null) {
            throw new IllegalStateException(
                    "Invalid ML inference endpoint URL: '" + rawUrl + "'. " +
                            "Check inference.endpoint.base-url"
            );
        }

        final HttpUrl url = authApplier.applyToUrl(parsedUrl);

        final Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .method(httpRequest.getMethod(), httpRequest.getBody());

        httpRequest.getHeaders().forEach(requestBuilder::header);
        authApplier.applyToHeaders(requestBuilder);

        return this.client.newCall(requestBuilder.build());
    }

    public synchronized void callAsync(
            final @NotNull HttpRequest request,
            final @NotNull Callback callback
    ) {
        this.newCall(request).enqueue(callback);
    }


    public synchronized void shutdown() {
        closed.set(true);
        suspended.set(true);
        requestEpoch.incrementAndGet();
        this.client.dispatcher().cancelAll();
        try {
            this.client.dispatcher().executorService().shutdown();
        } catch (final Throwable ignored) {
        }

        try {
            this.client.connectionPool().evictAll();
        } catch (final Throwable ignored) {
        }

        try {
            final Cache cache = this.client.cache();

            if (cache != null) {
                cache.close();
            }
        } catch (final Throwable ignored) {
        }
    }

    private void validate(
            final @NotNull Logger logger,
            final @NotNull AuthSettings auth
    ) {
        if (HttpUrl.parse(baseUrl + PATH) == null) {
            logger.warn(
                    "ML inference endpoint '{}{}' is not a valid URL, requests will fail",
                    baseUrl, PATH
            );
        }

        if (!auth.isEnabled() || auth.getType() == AuthType.NONE) {
            return;
        }

        switch (auth.getType()) {
            case HEADER -> warnIfBlank(logger, "auth.header.name", auth.getHeaderName());

            case QUERY -> warnIfBlank(logger, "auth.query.parameter", auth.getQueryParameter());

            case BODY -> warnIfBlank(logger, "auth.body.field", auth.getBodyField());
        }
    }

    private static void warnIfBlank(
            final @NotNull Logger logger,
            final @NotNull String key,
            final String value
    ) {
        if (value == null || value.isBlank()) {
            logger.warn("ML inference config '{}' is blank, requests may be rejected", key);
        }
    }

    private static @NotNull String normalizeUrl(
            final @NotNull String rawUrl
    ) {
        String url = rawUrl.trim();

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        return url;
    }
}
