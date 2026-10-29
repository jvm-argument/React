package com.g4vrk.reactdataset;

import com.g4vrk.react.inference.aim.InferenceResult;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.react.inference.server.settings.InferenceSettingsFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.BasicConfigurationNode;
import static org.junit.jupiter.api.Assertions.*;

final class InferenceSwitchTest {
    @Test void finalEntityCallbackQueuedBeforePauseNeverPublishesAfterPauseOrResume() throws Exception {
        InferenceServer server = server();
        try {
            long oldEpoch = server.requestEpoch();
            var publications = new java.util.concurrent.atomic.AtomicInteger();
            Runnable queuedEntityCallback = () -> server.publishIfCurrent(oldEpoch, publications::incrementAndGet);
            server.pauseRequests();
            queuedEntityCallback.run();
            server.resumeRequests();
            queuedEntityCallback.run();
            assertEquals(0, publications.get());
            server.publishIfCurrent(server.requestEpoch(), publications::incrementAndGet);
            assertEquals(1, publications.get());
        } finally { server.shutdown(); }
    }
    private InferenceServer server() throws Exception {
        var root = BasicConfigurationNode.root();
        root.node("enabled").set(true);
        root.node("inference", "endpoint", "base-url").set("http://127.0.0.1:1");
        return new InferenceServer(LoggerFactory.getLogger("InferenceSwitchTest"),
                new InferenceSettingsFactory().create(root));
    }

    @Test void pausedAndResumedClientDoesNotReuseOldRequestEpoch() throws Exception {
        InferenceServer server = server();
        try {
            long queuedRequestEpoch = server.requestEpoch();
            assertTrue(server.isAcceptingRequests());
            server.pauseRequests();
            assertFalse(server.isAcceptingRequests());
            server.resumeRequests();
            assertTrue(server.isAcceptingRequests());
            // A response queued before pause remains stale even after a fast rollback/resume.
            assertNotEquals(queuedRequestEpoch, server.requestEpoch());
        } finally { server.shutdown(); }
    }

    @Test void closedOldModelCannotBeResumedBesideNewModel() throws Exception {
        InferenceServer oldModel = server();
        oldModel.pauseRequests();
        InferenceServer newModel = server();
        try {
            oldModel.shutdown();
            oldModel.resumeRequests();
            assertFalse(oldModel.isAcceptingRequests());
            assertTrue(newModel.isAcceptingRequests());
        } finally { newModel.shutdown(); }
    }

    @Test void predictionCarriesItsOwnModelIdentityWithoutChangingProbability() {
        double probability = 0.123456789012345;
        var baseline = new InferenceResult(probability, Double.NaN, true, "flower", "baseline");
        var ultra = new InferenceResult(probability, Double.NaN, true, "flower", "ultra");
        var universe = new InferenceResult(probability, .49, false, "universe", "beta");
        assertEquals("Flower/Baseline", baseline.modelDisplay());
        assertEquals("Flower/Ultra", ultra.modelDisplay());
        assertEquals("Universe/Beta", universe.modelDisplay());
        assertEquals(probability, universe.getProbability());
        assertEquals("Flower/Baseline", baseline.modelDisplay());
        assertEquals("Model unknown", new InferenceResult(probability).modelDisplay());
    }
}
