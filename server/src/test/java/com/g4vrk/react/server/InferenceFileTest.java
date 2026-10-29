package com.g4vrk.react.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class InferenceFileTest {
    @TempDir Path directory;
    @Test void switchingPreservesSecretsAndUnrelatedSettingsAndSupportsExactRollback() throws Exception {
        byte[] original = ("model: FLOWER\nauth:\n  enabled: true\n  value: private-token-not-logged\nuniverse:\n  sequence: 64\n  step: 32\ninference:\n  endpoint:\n    base-url: http://127.0.0.1:8080\n    path: /api/v2/inference/?model-family=flower&model-name=ultra\n").getBytes(StandardCharsets.UTF_8);
        Path file = directory.resolve("inference.yml"); Files.write(file, original);
        byte[] changed = InferenceFile.select(original, new ModelProfile("universe", "beta"));
        InferenceFile.atomicWrite(file, changed);
        YamlConfiguration parsed = new YamlConfiguration(); parsed.loadFromString(Files.readString(file));
        assertEquals("UNIVERSE", parsed.getString("model"));
        assertEquals("private-token-not-logged", parsed.getString("auth.value"));
        assertEquals(64, parsed.getInt("universe.sequence"));
        assertEquals(new ModelProfile("universe", "beta"), InferenceFile.profile(changed));
        InferenceFile.atomicWrite(file, original);
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(new ModelProfile("flower", "ultra"), InferenceFile.profile(original));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void malformedYamlDoesNotGetOverwritten() {
        assertThrows(Exception.class, () -> InferenceFile.select("broken: [".getBytes(StandardCharsets.UTF_8), new ModelProfile("flower", "baseline")));
    }
}
