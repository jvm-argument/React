package com.g4vrk.react.placeholder;

import com.g4vrk.react.inference.ProbabilityFormatter;

import com.g4vrk.react.color.resolver.ValueColorResolver;
import com.g4vrk.react.color.resolver.impl.ProbabilityColorResolver;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.statistic.InferenceStatistic;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.function.Supplier;

public class ReactPlaceholderExpansion extends PlaceholderExpansion {

    private final String id;
    private final String author;
    private final String version;

    private final PlayerRegistry registry;
    private final java.util.function.Function<OfflinePlayer, String> selectedModel;

    private final ValueColorResolver probabilityColorResolver = new ProbabilityColorResolver();

    public ReactPlaceholderExpansion(
            @NotNull String id,
            @NotNull String author,
            @NotNull String version,
            @NotNull PlayerRegistry registry
    ) {
        this(id, author, version, registry, () -> "Unavailable");
    }

    public ReactPlaceholderExpansion(String id, String author, String version,
                                     PlayerRegistry registry, Supplier<String> selectedModel) {
        this(id, author, version, registry, ignored -> selectedModel.get());
    }

    public ReactPlaceholderExpansion(String id, String author, String version,
                                     PlayerRegistry registry, java.util.function.Function<OfflinePlayer, String> selectedModel) {
        this.id = id;
        this.author = author;
        this.version = version;
        this.registry = registry;
        this.selectedModel = selectedModel;
    }

    @Override
    public @NotNull String getIdentifier() {
        return id;
    }

    @Override
    public @NotNull String getAuthor() {
        return author;
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }


    @Override
    public @Nullable String onRequest(
            final OfflinePlayer offlinePlayer,
            final @NotNull String params
    ) {

        if (params.equals("model") || params.equals("model_family") || params.equals("model_name")) {
            String display = selectedModel.apply(offlinePlayer);
            if (display == null) display = "Unavailable";
            String[] parts = display.split("/", 2);
            return switch (params) {
                case "model_family" -> parts[0];
                case "model_name" -> parts.length == 2 ? parts[1] : display;
                default -> display;
            };
        }

        if (offlinePlayer == null) {
            return null;
        }

        final String prefix = "inference-history.";

        if (!params.startsWith(prefix)) {
            return null;
        }

        final ReactPlayer player =
                registry.getPlayer(offlinePlayer.getUniqueId());

        if (player == null) {
            return null;
        }

        final String path = params.substring(prefix.length());

        if (path.startsWith("entry.")) {

            final String entryPrefix = "entry.";

            final int separator = path.indexOf('.', entryPrefix.length());

            if (separator == -1) {
                return null;
            }

            final int index;

            try {
                index = Integer.parseInt(
                        path,
                        entryPrefix.length(),
                        separator,
                        10
                );
            } catch (NumberFormatException ignored) {
                return null;
            }

            final InferenceHistoryEntry entry =
                    player.inferenceHistory.get(index);

            if (entry == null) {
                return null;
            }

            return switch (path.substring(separator + 1)) {

                case "probability" -> {
                    final double probability = entry.getProbability();

                    yield ProbabilityFormatter.format(probability);
                }

                case "probability:colored" -> {
                    final double probability = entry.getProbability();

                    yield "<" +
                            probabilityColorResolver.resolve(probability).asHexString() +
                            ">" +
                            ProbabilityFormatter.format(probability);
                }

                case "check" -> entry.getCheck().getName();

                default -> null;
            };
        }

        final InferenceStatistic.Result statisticResult = player.inferenceStatistic.calculate();

        return switch (path) {

            case "size" ->
                    Integer.toString(player.inferenceHistory.size());

            case "avg-probability" -> ProbabilityFormatter.format(statisticResult.averageProbability());

            case "avg-probability:colored" -> {

                final double average = statisticResult.averageProbability();

                yield "<" +
                        probabilityColorResolver.resolve(average).asHexString() +
                        ">" +
                        ProbabilityFormatter.format(average);
            }

            default -> null;
        };
    }
}
