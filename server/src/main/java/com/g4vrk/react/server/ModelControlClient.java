package com.g4vrk.react.server;

import com.google.gson.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

final class ModelControlClient {
    private final URI base;
    private final String key;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    ModelControlClient(byte[] inferenceConfiguration) throws Exception {
        YamlConfiguration config = new YamlConfiguration(); config.loadFromString(new String(inferenceConfiguration, java.nio.charset.StandardCharsets.UTF_8));
        base = URI.create(config.getString("inference.endpoint.base-url", "").replaceAll("/+$", ""));
        key = config.getString("auth.value", "");
        if (base.getHost() == null || base.getUserInfo() != null || base.getFragment() != null || base.getQuery() != null || key.isBlank()) throw new IllegalArgumentException("Inference configuration missing or invalid");
        if (!"https".equalsIgnoreCase(base.getScheme())) {
            // This URL is the explicitly configured, trusted inference origin, never supplied by a player.
            if (!"http".equalsIgnoreCase(base.getScheme())) throw new IllegalArgumentException("HTTPS required");
            for (InetAddress address : InetAddress.getAllByName(base.getHost())) if (!address.isLoopbackAddress() && !address.isSiteLocalAddress()) throw new IllegalArgumentException("Local HTTP requires private address");
        }
    }
    ModelProfile current() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/subscriptions/model"))
            .timeout(Duration.ofSeconds(8)).header("X-Subscription-Token", key).GET().build();
        return parse(send(request));
    }
    void select(ModelProfile model) throws Exception {
        select(model, false);
    }
    void validate(ModelProfile model) throws Exception {
        select(model, true);
    }
    private void select(ModelProfile model, boolean validationOnly) throws Exception {
        JsonObject body = new JsonObject(); body.addProperty("model_family", model.family()); body.addProperty("model_name", model.name());
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/v1/subscriptions/model/" + (validationOnly ? "validate" : "select")))
            .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json").header("X-Subscription-Token", key)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        ModelProfile selected = parse(send(request));
        if (!selected.equals(model)) throw new IllegalStateException("Unexpected model switch response");
    }
    private JsonObject send(HttpRequest request) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400 && response.statusCode() < 500) throw new RejectedException();
        if (response.statusCode() != 200 || response.body().length() > 16384) throw new IllegalStateException("Model switch rejected");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private static ModelProfile parse(JsonObject result) { return new ModelProfile(result.get("model_family").getAsString(), result.get("model_name").getAsString()); }
    static final class RejectedException extends Exception {
        RejectedException() { super("Backend rejected model access"); }
    }
}
