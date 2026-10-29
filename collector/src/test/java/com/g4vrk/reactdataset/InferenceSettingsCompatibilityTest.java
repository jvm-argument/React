package com.g4vrk.reactdataset;

import com.g4vrk.react.inference.InferenceModel;
import com.g4vrk.react.inference.server.settings.InferenceSettings;
import com.g4vrk.react.inference.server.settings.InferenceSettingsFactory;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;

import static org.junit.jupiter.api.Assertions.*;

class InferenceSettingsCompatibilityTest {
    @Test
    void downloadedUniverseSettingsKeepTheRealTokenAndExplicitEndpoint() throws Exception {
        BasicConfigurationNode root = BasicConfigurationNode.root();
        root.node("model").set("UNIVERSE");
        root.node("auth", "enabled").set(true);
        root.node("auth", "type").set("body");
        root.node("auth", "value").set("verification-token");
        root.node("inference", "endpoint", "base-url").set("http://127.0.0.1:8000");
        root.node("inference", "endpoint", "path").set("/api/v2/inference/?model-family=universe&model-name=beta");
        InferenceSettings settings = new InferenceSettingsFactory().create(root);
        assertEquals(InferenceModel.UNIVERSE, settings.getModel());
        assertEquals("verification-token", settings.getAuth().getValue());
        assertEquals("token", settings.getAuth().getBodyField());
        assertTrue(settings.getEndpoint().getPath().contains("model-family=universe"));
    }

    @Test
    void olderNestedTokenSettingsAreStillAcceptedWithoutChangingFlowerDefaults() throws Exception {
        for (String method : new String[]{"body", "header", "query", "bearer"}) {
            BasicConfigurationNode root = BasicConfigurationNode.root();
            root.node("auth", "enabled").set(true);
            root.node("auth", "type").set(method);
            root.node("auth", method, method.equals("bearer") ? "token" : "value").set("legacy-token");
            InferenceSettings settings = new InferenceSettingsFactory().create(root);
            assertEquals(InferenceModel.FLOWER, settings.getModel());
            assertEquals("legacy-token", settings.getAuth().getValue());
        }
    }
}
