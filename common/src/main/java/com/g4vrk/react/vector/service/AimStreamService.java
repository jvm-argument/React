package com.g4vrk.react.vector.service;

import com.g4vrk.react.React;
import com.g4vrk.react.check.impl.aim.AimAI;
import com.g4vrk.react.player.CombatTargets;
import com.g4vrk.react.player.ReactPlayer;
import com.g4vrk.react.player.registry.PlayerRegistry;
import com.g4vrk.react.vector.model.AimFrame;
import com.g4vrk.react.vector.session.AimSession;
import com.g4vrk.react.vector.session.AimState;
import com.g4vrk.react.vector.thread.EntityTaskRunner;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AimStreamService {

    private final PlayerRegistry players;
    private final EntityTaskRunner entityRunner;
    private final Map<UUID, AimSession> playerData = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public AimStreamService(final @NotNull PlayerRegistry players, final @NotNull Scheduler scheduler) {
        this.players = players;
        this.entityRunner = new EntityTaskRunner(React.INSTANCE.getPlugin(), scheduler);
    }

    public @NotNull AimSession claimPlayerData(final @NotNull UUID uuid) {
        return playerData.computeIfAbsent(uuid, ignored -> new AimSession());
    }

    public void removePlayerData(final @NotNull UUID uuid) {
        final AimSession removed = playerData.remove(uuid);
        if (removed != null) {
            removed.reset();
        }
    }

    public void onPacketBeforeJoin(
            final @NotNull UUID uuid,
            final @Nullable Player bukkitPlayer,
            final @NotNull PacketReceiveEvent event
    ) {
        onPacket(uuid, bukkitPlayer, claimPlayerData(uuid), event);
    }

    public void onPacket(final @NotNull ReactPlayer player, final @NotNull PacketReceiveEvent event) {
        onPacket(player.getUniqueId(), player.bukkitPlayer, player.universeData, event);
    }

    public @NotNull AutoCloseable subscribe(
            final @NotNull Predicate<ReactPlayer> interested,
            final @NotNull BiConsumer<ReactPlayer, AimFrame> consumer
    ) {
        final Subscription subscription = new Subscription(interested, consumer);
        subscriptions.add(subscription);
        return () -> subscriptions.remove(subscription);
    }

    private void onPacket(
            final UUID uuid,
            final Player bukkitPlayer,
            final AimSession data,
            final PacketReceiveEvent event
    ) {
        synchronized (data) {
            processPacket(uuid, bukkitPlayer, data, event);
        }
    }

    private void processPacket(
            final UUID uuid,
            final Player bukkitPlayer,
            final AimSession data,
            final PacketReceiveEvent event
    ) {
        final AimState state = data.activate();
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            onInteract(data, state, event);
            return;
        }
        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            return;
        }
        final WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        final boolean rotationChanged = flying.hasRotationChanged();
        state.onFlying(rotationChanged);
        if (!rotationChanged || bukkitPlayer == null) {
            return;
        }
        final float yaw = flying.getLocation().getYaw();
        final float pitch = flying.getLocation().getPitch();
        entityRunner.run(bukkitPlayer, () -> capture(uuid, bukkitPlayer, data, state, yaw, pitch));
    }

    private void onInteract(final AimSession data, final AimState state, final PacketReceiveEvent event) {
        final WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
        if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
            return;
        }
        final ReactPlayer target = players.getByEntityId(interact.getEntityId());
        if (target != null) {
            state.onAttack(target.getUniqueId());
        } else {
            state.onAttackEntity(interact.getEntityId());
        }
        if (!data.combatActivity.isActive()) {
            data.clearHistory();
        }
        data.combatActivity.extend(combatTicks());
    }

    private long combatTicks() {
        return Math.max(1L, React.INSTANCE.getCheckConfigRegistry().config(AimAI.class).node("combat-ticks").getLong(64L));
    }

    private void capture(
            final UUID uuid,
            final Player bukkitPlayer,
            final AimSession data,
            final AimState state,
            final float yaw,
            final float pitch
    ) {
        final ReactPlayer player;
        final AimFrame frame;
        synchronized (data) {
            player = players.getPlayer(uuid);
            if (!bukkitPlayer.isOnline() || !ownsData(uuid, player, data) || !data.isCurrent(state)) {
                return;
            }
            resolvePendingTarget(bukkitPlayer, player, state);
            frame = state.capture(bukkitPlayer, resolveTarget(data, state), yaw, pitch);
            if (player == null) {
                return;
            }
            recordFrame(player, data, frame);
        }
        if (data.isCurrent(state)) {
            notifySubscribers(player, frame);
        }
    }

    private boolean ownsData(final UUID uuid, final ReactPlayer player, final AimSession data) {
        return player == null ? playerData.get(uuid) == data : player.universeData == data;
    }

    private void resolvePendingTarget(final Player bukkitPlayer, final ReactPlayer player, final AimState state) {
        final int pendingTarget = state.takePendingTargetEntityId();
        if (pendingTarget == -1) {
            return;
        }
        final Entity attacked = findSupportedEntity(bukkitPlayer, pendingTarget);
        state.onAttack(attacked == null ? null : attacked.getUniqueId());
        if (player != null && CombatTargets.isMannequin(attacked)) {
            React.INSTANCE.getCombatListener().activate(player);
        }
    }

    private static @Nullable Entity findSupportedEntity(final Player bukkitPlayer, final int entityId) {
        for (final Entity entity : bukkitPlayer.getWorld().getEntities()) {
            if (entity.getEntityId() == entityId && CombatTargets.isSupported(entity)) {
                return entity;
            }
        }
        return null;
    }

    private @Nullable Entity resolveTarget(final AimSession data, final AimState state) {
        final UUID targetId = state.targetId();
        if (targetId == null || !data.combatActivity.isActive()) {
            return null;
        }
        final ReactPlayer targetPlayer = players.getPlayer(targetId);
        final Entity target = targetPlayer != null ? targetPlayer.bukkitPlayer : Bukkit.getEntity(targetId);
        return target != null && CombatTargets.isSupported(target) ? target : null;
    }

    private void recordFrame(final ReactPlayer player, final AimSession data, final AimFrame frame) {
        if (!data.combatActivity.isActive()) {
            data.clearHistory();
            return;
        }
        data.push(frame);
        final AimAI check = player.checkManager.getCheck(AimAI.class);
        if (check != null) {
            check.onVectorFrame();
        }
    }

    private void notifySubscribers(final ReactPlayer player, final AimFrame frame) {
        for (final Subscription subscription : subscriptions) {
            if (subscription.interested().test(player)) {
                subscription.consumer().accept(player, frame);
            }
        }
    }

    private record Subscription(
            Predicate<ReactPlayer> interested,
            BiConsumer<ReactPlayer, AimFrame> consumer
    ) {

    }

}
