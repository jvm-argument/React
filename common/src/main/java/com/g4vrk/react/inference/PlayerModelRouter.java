package com.g4vrk.react.inference;

import com.g4vrk.react.React;
import com.g4vrk.react.inference.server.InferenceServer;
import com.g4vrk.react.inference.server.settings.InferenceSettingsFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Local choices share an entitlement, never buffers or an HTTP cancellation domain. */
public final class PlayerModelRouter {
    private final java.util.function.Supplier<org.spongepowered.configurate.ConfigurationNode> configuration;
    private final org.slf4j.Logger logger;
    private final java.util.function.Consumer<UUID> onSelected;
    private final Map<UUID, String> selections = new ConcurrentHashMap<>();
    private final Map<UUID, InferenceServer> clients = new ConcurrentHashMap<>();

    public PlayerModelRouter() {
        this(() -> React.INSTANCE.getInferenceConfig().root(), org.slf4j.LoggerFactory.getLogger("React"), uuid -> {
            var player = React.INSTANCE.getPlayerRegistry().getPlayer(uuid);
            if (player != null) {
                player.rotationData.clear();
                player.universeData.clearHistory();
                player.checkManager.reload();
            }
        });
    }

    public PlayerModelRouter(java.util.function.Supplier<org.spongepowered.configurate.ConfigurationNode> configuration,
                             org.slf4j.Logger logger, java.util.function.Consumer<UUID> onSelected) {
        this.configuration = configuration;
        this.logger = logger;
        this.onSelected = onSelected;
    }

    public void remember(UUID uuid, String family, String name) {
        selections.put(uuid, family + "/" + name);
    }

    public synchronized InferenceServer server(UUID uuid) {
        String selection = selections.get(uuid);
        return clients.computeIfAbsent(uuid, ignored -> selection == null
                ? new InferenceServer(logger, new InferenceSettingsFactory().create(configuration.get()))
                : create(selection));
    }

    public synchronized void select(UUID uuid, String family, String name) {
        String identity = family + "/" + name;
        InferenceServer replacement = create(identity);
        release(uuid);
        selections.put(uuid, identity);
        clients.put(uuid, replacement);
        onSelected.accept(uuid);
    }

    private InferenceServer create(String identity) {
        if (!java.util.Set.of("flower/baseline", "flower/ultra", "universe/beta").contains(identity))
            throw new IllegalArgumentException("Unknown model");
        String[] parts = identity.split("/");
        try {
            var root = configuration.get().copy();
            root.node("model").set(parts[0]);
            root.node("inference", "endpoint", "path").set("/api/v2/inference/?model-family=" + parts[0] + "&model-name=" + parts[1]);
            String display = parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1) + "/"
                    + parts[1].substring(0, 1).toUpperCase() + parts[1].substring(1);
            return new InferenceServer(logger, new InferenceSettingsFactory().create(root), display);
        } catch (org.spongepowered.configurate.serialize.SerializationException failure) {
            throw new IllegalStateException("Invalid personal model configuration", failure);
        }
    }

    public synchronized void release(UUID uuid) {
        InferenceServer previous = clients.remove(uuid);
        if (previous != null) previous.shutdown();
    }

    /** Retain choices while rebuilding clients with the reloaded key and endpoint. */
    public synchronized void reload() {
        clients.values().forEach(InferenceServer::shutdown);
        clients.clear();
    }

    public synchronized void close() { reload(); selections.clear(); }
}
