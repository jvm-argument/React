package com.g4vrk.react.server;

import com.g4vrk.react.React;
import com.g4vrk.react.paper.PaperReactPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Personal model choices never mutate the shared subscription or inference.yml. */
public final class ServerReactPlugin extends PaperReactPlugin {
    private final Set<UUID> switching = ConcurrentHashMap.newKeySet();
    private ExecutorService worker;
    private Path choicesFile;
    private YamlConfiguration choices;
    @Override public void onEnable() {
        super.onEnable();
        if (!isEnabled() || React.INSTANCE.getCommandBuilderFactory() == null) return;
        choicesFile = getDataFolder().toPath().resolve("player-models.yml");
        choices = YamlConfiguration.loadConfiguration(choicesFile.toFile());
        for (String key : choices.getKeys(false)) {
            try {
                String[] parts = choices.getString(key, "").split("/");
                ModelProfile profile = new ModelProfile(parts[0], parts[1]);
                React.INSTANCE.getPlayerModelRouter().remember(UUID.fromString(key), profile.family(), profile.name());
            } catch (RuntimeException invalid) { getLogger().warning("Ignored invalid saved player model entry."); }
        }
        worker = Executors.newFixedThreadPool(2, task -> { Thread t = new Thread(task, "React-player-model-switch"); t.setDaemon(true); return t; });
        var factory = React.INSTANCE.getCommandBuilderFactory();
        factory.getManager().command(factory.create().literal("model").permission("react.server.model").handler(context -> {
            if (!(context.sender() instanceof Player p)) { say(context.sender(), "Выбор модели доступен игроку в игре."); return; }
            say(p, "Твоя модель: " + React.INSTANCE.getInferenceServer(p.getUniqueId()).getSelectedModelDisplay() + ". /react model flower baseline|ultra или /react model universe beta");
        }));
        for (ModelProfile profile : List.of(new ModelProfile("flower", "baseline"), new ModelProfile("flower", "ultra"), new ModelProfile("universe", "beta"))) {
            factory.getManager().command(factory.create().literal("model").literal(profile.family()).literal(profile.name()).permission("react.server.model").handler(context -> switchModel(context.sender(), profile)));
        }
    }
    @Override public void onDisable() { if (worker != null) worker.shutdownNow(); super.onDisable(); }
    private void say(CommandSender sender, String text) {
        Runnable send = () -> sender.sendMessage(React.INSTANCE.getSerializers().universalSerializer().serialize("<b><gradient:#eb2544:#ff3655>⚡ ʀᴇᴀᴄᴛ</gradient></b> <gray>»</gray> <white>" + text));
        if (Bukkit.isPrimaryThread()) send.run(); else if (isEnabled()) Bukkit.getScheduler().runTask(this, send);
    }
    private void switchModel(CommandSender sender, ModelProfile selected) {
        if (!(sender instanceof Player p)) { say(sender, "Выбор модели доступен игроку в игре."); return; }
        UUID uuid = p.getUniqueId();
        if (!switching.add(uuid)) { say(sender, "Предыдущее переключение ещё выполняется."); return; }
        say(sender, "Переключаю твою модель на " + selected.display() + "…");
        worker.execute(() -> {
            try {
                byte[] config = Files.readAllBytes(getDataFolder().toPath().resolve("inference.yml"));
                new ModelControlClient(config).validate(selected);
                if (!isEnabled()) { switching.remove(uuid); return; }
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        var registered = React.INSTANCE.getPlayerRegistry().getPlayer(uuid);
                        if (!p.isOnline() || registered == null || registered.bukkitPlayer != p) return;
                        if (!Arrays.equals(config, Files.readAllBytes(getDataFolder().toPath().resolve("inference.yml")))) throw new IllegalStateException("Configuration changed");
                        YamlConfiguration next = new YamlConfiguration(); next.loadFromString(choices.saveToString());
                        next.set(uuid.toString(), selected.family() + "/" + selected.name());
                        InferenceFile.atomicWrite(choicesFile, next.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        React.INSTANCE.getPlayerModelRouter().select(uuid, selected.family(), selected.name());
                        choices = next;
                        say(p, "Твоя модель: " + selected.display() + ". Выбор сохранён; модели других игроков не изменены.");
                    } catch (Exception failure) {
                        say(p, "Не удалось сохранить выбор модели. Попробуй ещё раз.");
                        getLogger().warning("Could not apply player model: " + failure.getClass().getSimpleName());
                    } finally { switching.remove(uuid); }
                });
            } catch (Exception failure) {
                switching.remove(uuid);
                say(sender, "Переключение не выполнено: проверь доступ ключа и доступность backend. Твоя модель сохранена.");
                getLogger().warning("Player model validation failed: " + failure.getClass().getSimpleName());
            }
        });
    }
}