package com.g4vrk.react.vector.feature;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerClientInfo {

    private PlayerClientInfo() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static @Nullable ClientVersion version(final @NotNull Player player) {
        try {
            return PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
        } catch (final RuntimeException exception) {
            return null;
        }
    }

    public static float ping(final @NotNull Player player) {
        try {
            return PacketEvents.getAPI().getPlayerManager().getPing(player);
        } catch (final RuntimeException exception) {
            return 0.0f;
        }
    }

}
