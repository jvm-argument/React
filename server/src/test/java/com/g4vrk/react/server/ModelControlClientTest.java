package com.g4vrk.react.server;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ModelControlClientTest {
    @Test void personalValidationDoesNotCallGlobalSelection() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/api/v1/subscriptions/model", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = "{\"model_family\":\"flower\",\"model_name\":\"ultra\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            new ModelControlClient(config("http://127.0.0.1:" + server.getAddress().getPort())).validate(new ModelProfile("flower", "ultra"));
            assertEquals("/api/v1/subscriptions/model/validate", path.get());
        } finally { server.stop(0); }
    }
    private static byte[] config(String origin) {
        return ("auth:\n  value: private-test-subscription\ninference:\n  endpoint:\n    base-url: " + origin + "\n").getBytes(StandardCharsets.UTF_8);
    }
    @Test void usesSubscriptionHeaderAndReadsIndependentCurrentProfileBeforeSwitching() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> selected = new AtomicReference<>("ultra"), received = new AtomicReference<>();
        server.createContext("/api/v1/subscriptions/model", exchange -> {
            if (!"private-test-subscription".equals(exchange.getRequestHeaders().getFirst("X-Subscription-Token")) || exchange.getRequestHeaders().containsKey("X-ReactAuth-Key")) {
                exchange.sendResponseHeaders(403, -1); exchange.close(); return;
            }
            if (exchange.getRequestMethod().equals("POST")) { received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); selected.set("baseline"); }
            byte[] response = ("{\"model_family\":\"flower\",\"model_name\":\"" + selected.get() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            ModelControlClient client = new ModelControlClient(config("http://127.0.0.1:" + server.getAddress().getPort()));
            assertEquals(new ModelProfile("flower", "ultra"), client.current());
            client.select(new ModelProfile("flower", "baseline"));
            assertTrue(received.get().contains("\"model_family\":\"flower\""));
            assertTrue(received.get().contains("\"model_name\":\"baseline\""));
            assertEquals(new ModelProfile("flower", "baseline"), client.current());
        } finally { server.stop(0); }
    }
    @Test void deniesPublicHttpAndMissingKeys() {
        assertThrows(Exception.class, () -> new ModelControlClient(config("http://8.8.8.8")));
        assertThrows(Exception.class, () -> new ModelControlClient("inference: {}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> new ModelControlClient(config("https://user:secret@example.org")));
    }
    @Test void redirectsAreRejectedWithoutForwardingCredentials() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/subscriptions/model", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://example.org/stolen-key"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        try { assertThrows(Exception.class, () -> new ModelControlClient(config("http://127.0.0.1:" + server.getAddress().getPort())).current()); }
        finally { server.stop(0); }
    }
}
