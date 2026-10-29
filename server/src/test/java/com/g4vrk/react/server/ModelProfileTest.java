package com.g4vrk.react.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelProfileTest {
    @Test void permitsExactlyTheThreeSupportedProfiles() {
        assertEquals("Flower/Baseline", new ModelProfile("FLOWER", "BASELINE").display());
        assertEquals("/api/v2/inference/?model-family=flower&model-name=ultra", new ModelProfile("flower", "ultra").path());
        assertEquals("Universe/Beta", new ModelProfile("universe", "beta").display());
        assertThrows(IllegalArgumentException.class, () -> new ModelProfile("flower", "beta"));
        assertThrows(IllegalArgumentException.class, () -> new ModelProfile("universe", "baseline"));
        assertThrows(IllegalArgumentException.class, () -> new ModelProfile("../flower", "ultra"));
    }
}
