package com.g4vrk.react.check.impl.aim;

import com.g4vrk.config.Config;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.event.InferenceHistoryEntryAddedEvent;
import com.g4vrk.react.check.Check;
import com.g4vrk.react.check.debug.DebugHandler;
import com.g4vrk.react.check.decay.DecayStrategy;
import com.g4vrk.react.check.decay.impl.LinearDecay;
import com.g4vrk.react.check.info.CheckInfo;
import com.g4vrk.react.color.resolver.ValueColorResolver;
import com.g4vrk.react.color.resolver.impl.ProbabilityColorResolver;
import com.g4vrk.react.history.entry.InferenceHistoryEntry;
import com.g4vrk.react.inference.aim.InferenceAimProcessor;
import com.g4vrk.react.inference.aim.InferenceResult;
import com.g4vrk.react.player.ReactPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.atomic.AtomicBoolean;
import com.g4vrk.react.vector.model.AimFrame;
import com.g4vrk.schedula.task.TickSchedule;
import java.util.concurrent.atomic.AtomicLong;
import com.g4vrk.react.inference.ProbabilityFormatter;

@CheckInfo(
        name = "Aim-AI",
        configId = "aim-ai"
)
public final class AimAI extends Check implements ReloadObserver {

    private final ValueColorResolver colorResolver;

    private final DebugHandler debugHandler;

    private InferenceAimProcessor inferenceAimProcessor;

    private boolean debug;

    private int sampleWindowSize;
    private int requestStep;
    private double alertThreshold;

    private final AtomicBoolean requesting = new AtomicBoolean();
    private final AtomicLong requestGeneration = new AtomicLong();

    private DecayStrategy decayStrategy;

    public AimAI(@NotNull ReactPlayer player) {
        super(player);

        this.inferenceAimProcessor = React.INSTANCE.getInferenceAimProcessor();
        this.colorResolver = new ProbabilityColorResolver();

        this.debugHandler = new DebugHandler(this);
    }

    @Override
    public void onReload(@NotNull Config config) {

        this.debug = config.node("debug").getBoolean();

        final Config vectorConfig = React.INSTANCE.getVectorConfig();
        this.sampleWindowSize = Math.max(1, vectorConfig.node("buffer", "sequence").getInt(64));
        this.requestStep = Math.max(1, vectorConfig.node("buffer", "step").getInt(32));
        resetSamples();
        player.universeData.resize(this.sampleWindowSize);
        this.alertThreshold = config.node("alert", "threshold").getDouble(0.49D);

        final double decayAmount = config.node("decay", "amount").getDouble(0.5D);

        this.decayStrategy = new LinearDecay(decayAmount);

        this.inferenceAimProcessor = new InferenceAimProcessor(React.INSTANCE.getLogger(),
                React.INSTANCE.getInferenceServer(), React.INSTANCE.getScheduler());

    }

    @Override
    public @NotNull DecayStrategy decayStrategy() {
        return this.decayStrategy;
    }

    public void resetSamples() {
        requestGeneration.incrementAndGet();
        requesting.set(false);
    }

    public void onVectorFrame() {
        if (!player.universeData.combatActivity.isActive() || !shouldCheck()) {
            return;
        }
        player.universeData.resize(sampleWindowSize);
        if (requesting.get()) return;
        final AimFrame[] snapshot = player.universeData.pollSnapshot(requestStep);
        if (snapshot == null || !requesting.compareAndSet(false, true)) {
            return;
        }
        final long generation = requestGeneration.get();
        final InferenceAimProcessor processor = inferenceAimProcessor;
        final long clientEpoch = processor.requestEpoch();
        try {
            processor.check(player.getName(), snapshot, result ->
                    React.INSTANCE.getScheduler().scheduleEntity(player.bukkitPlayer, () -> {
                        processor.publishIfCurrent(clientEpoch, () -> {
                            if (requestGeneration.get() == generation) onServerResult(result);
                        });
                    }, TickSchedule.instant()));
        } catch (Throwable failure) {
            requesting.set(false);
            React.INSTANCE.getLogger().warn("Could not start VECTOR request for {}", player.getName(), failure);
        }
    }

    private void onServerResult(
            final @NotNull InferenceResult result
    ) {
        try {

            if (!player.bukkitPlayer.isOnline() || !shouldCheck()) {
                return;
            }

            if (!result.isAvailable()) {

                if (debug) {
                    debugHandler.debug("ML result unavailable");
                }

                return;
            }

            final double probability = result.getProbability();

            if (debug) {
                debugHandler.debug("Received ML response: " + probability);
            }

            final TextColor color = colorResolver.resolve(probability);

            final Component verbose = Component.text(
                    // Presentation only: keep the original double for decisions,
                    // events and persistence, including sub-micro probabilities.
                    ProbabilityFormatter.format(probability) + " (" + result.modelDisplay() + ")"
            ).color(color);

            final double threshold = Double.isFinite(result.getDecisionThreshold())
                    ? result.getDecisionThreshold() : alertThreshold;
            final boolean flagged = Double.isFinite(result.getDecisionThreshold())
                    ? probability >= threshold : probability > threshold;
            if (!result.isEnforcementEnabled()) {
                if (debug) debugHandler.debug("VECTOR observation: " + probability + ", threshold=" + threshold);
            } else if (flagged) {

                if (debug) {
                    debugHandler.debug(
                            "Flagged: " + probability + " > " + alertThreshold
                    );
                }

                failAndAlert(1, verbose);

            } else {

                if (debug) {
                    debugHandler.debug(
                            "Reward: " + probability + " <= " + alertThreshold
                    );
                }

                reward();
            }

            verbose(verbose);

            final InferenceHistoryEntry entry = new InferenceHistoryEntry(this, probability);

            player.inferenceHistory.add(entry);

            new InferenceHistoryEntryAddedEvent(player, entry).callEvent();

        } finally {

            requesting.set(false);

            if (debug) {
                debugHandler.debug("Request finished");
            }
        }
    }

}
