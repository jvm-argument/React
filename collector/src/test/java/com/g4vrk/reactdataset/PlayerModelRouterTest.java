package com.g4vrk.reactdataset;

import com.g4vrk.react.inference.PlayerModelRouter;
import com.g4vrk.react.inference.http.model.HttpRequest;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import static org.junit.jupiter.api.Assertions.*;

class PlayerModelRouterTest {
    @Test void twoPlayersKeepSeparateClientsChoicesCancellationAndReloadedCredentials() throws Exception {
        var root = BasicConfigurationNode.root();
        root.node("model").set("universe");
        root.node("inference", "endpoint", "base-url").set("http://127.0.0.1:1");
        root.node("inference", "endpoint", "path").set("/api/v2/inference/?model-family=universe&model-name=beta");
        root.node("auth", "enabled").set(true);
        root.node("auth", "type").set("body");
        root.node("auth", "value").set("first-key");
        var router = new PlayerModelRouter(() -> root, org.slf4j.LoggerFactory.getLogger("PlayerModelRouterTest"), uuid -> {});
        try {
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            var originalFirst = router.server(first);
            var originalSecond = router.server(second);
            assertNotSame(originalFirst, originalSecond);
            long oldEpoch = originalFirst.requestEpoch();
            router.select(first, "flower", "ultra");
            assertFalse(originalFirst.isAcceptingRequests());
            var publications = new java.util.concurrent.atomic.AtomicInteger();
            originalFirst.publishIfCurrent(oldEpoch, publications::incrementAndGet);
            assertEquals(0, publications.get());
            assertTrue(originalSecond.isAcceptingRequests());
            assertSame(originalSecond, router.server(second));
            router.select(second, "universe", "beta");
            assertEquals("Flower/Ultra", router.server(first).getSelectedModelDisplay());
            assertEquals("Universe/Beta", router.server(second).getSelectedModelDisplay());
            assertEquals("universe", root.node("model").getString());
            var request = router.server(first).newCall(new HttpRequest("POST",
                    RequestBody.create("{}", MediaType.get("application/json")))).request();
            assertEquals("ultra", request.url().queryParameter("model-name"));
            assertEquals("true", request.header("X-React-Player-Scoped"));
            root.node("auth", "value").set("reloaded-key");
            router.reload();
            assertEquals("Flower/Ultra", router.server(first).getSelectedModelDisplay());
            assertEquals("reloaded-key", router.server(first).getAuthBodyToken());
            router.release(first);
            assertEquals("Flower/Ultra", router.server(first).getSelectedModelDisplay());
        } finally {
            router.close();
        }
    }
}
