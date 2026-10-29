package com.g4vrk.react.vector.feature;

import com.g4vrk.react.vector.model.VectorFeature;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

public final class TargetFeatures {

    private static final double[] POSSIBLE_EYE_HEIGHTS = {1.62, 1.27, 0.4};

    private TargetFeatures() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void writeTo(
            final @NotNull float[] features,
            final @NotNull Player player,
            final @NotNull Entity target,
            final float fromYaw,
            final float fromPitch,
            final float yaw,
            final float pitch
    ) {
        final Location feet = player.getLocation();
        final double px = feet.getX();
        final double py = feet.getY();
        final double pz = feet.getZ();
        final double[] directions = lookDirections(player, fromYaw, fromPitch, yaw, pitch);
        VectorFeature.TARGET_AIM_ANGLE.put(features, (float) minAngleToBox(px, py, pz, directions, target.getBoundingBox()));
        final Location position = target.getLocation();
        VectorFeature.TARGET_REL_X.put(features, (float) (position.getX() - px));
        VectorFeature.TARGET_REL_Y.put(features, (float) (position.getY() - py));
        VectorFeature.TARGET_REL_Z.put(features, (float) (position.getZ() - pz));
        VectorFeature.TARGETS_IN_RANGE_COUNT.put(features, countTargetsInRange(player, px, py, pz));
    }

    private static double[] lookDirections(
            final Player player,
            final float lastYaw,
            final float lastPitch,
            final float yaw,
            final float pitch
    ) {
        final ClientVersion version = PlayerClientInfo.version(player);
        if (version != null && version.isOlderThan(ClientVersion.V_1_8)) {
            return writeLookVector(yaw, pitch, new double[3], 0);
        }
        final boolean includeLastRotation = version == null || version.isNewerThanOrEquals(ClientVersion.V_1_9);
        final double[] directions = new double[3 * (includeLastRotation ? 3 : 2)];
        writeLookVector(yaw, pitch, directions, 0);
        writeLookVector(lastYaw, pitch, directions, 3);
        if (includeLastRotation) {
            writeLookVector(lastYaw, lastPitch, directions, 3 * 2);
        }
        return directions;
    }

    private static double[] writeLookVector(final float yaw, final float pitch, final double[] out, final int offset) {
        final double pitchRadians = Math.toRadians(pitch);
        final double yawRadians = Math.toRadians(-yaw);
        final double pitchCos = Math.cos(pitchRadians);
        final double x = Math.sin(yawRadians) * pitchCos;
        final double y = -Math.sin(pitchRadians);
        final double z = Math.cos(yawRadians) * pitchCos;
        final double length = Math.sqrt(x * x + y * y + z * z);
        final double scale = length == 0.0 ? 1.0 : 1.0 / length;
        out[offset] = x * scale;
        out[offset + 1] = y * scale;
        out[offset + 2] = z * scale;
        return out;
    }

    private static double minAngleToBox(
            final double px,
            final double py,
            final double pz,
            final double[] directions,
            final BoundingBox box
    ) {
        double maxDot = Double.NEGATIVE_INFINITY;
        for (final double eyeHeight : POSSIBLE_EYE_HEIGHTS) {
            final double eyeY = py + eyeHeight;
            for (int point = 0; point < 9; point++) {
                final double dx = pointX(box, point) - px;
                final double dy = pointY(box, point) - eyeY;
                final double dz = pointZ(box, point) - pz;
                final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length < 0.001) {
                    return 0.0;
                }
                for (int offset = 0; offset < directions.length; offset += 3) {
                    final double dot = directions[offset] * (dx / length)
                            + directions[offset + 1] * (dy / length)
                            + directions[offset + 2] * (dz / length);
                    if (dot > maxDot) {
                        maxDot = dot;
                    }
                }
            }
        }
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, maxDot))));
    }

    private static double pointX(final BoundingBox box, final int point) {
        if (point == 0) {
            return box.getCenterX();
        }
        return ((point - 1) & 1) == 0 ? box.getMinX() : box.getMaxX();
    }

    private static double pointY(final BoundingBox box, final int point) {
        if (point == 0) {
            return box.getCenterY();
        }
        return ((point - 1) & 2) == 0 ? box.getMinY() : box.getMaxY();
    }

    private static double pointZ(final BoundingBox box, final int point) {
        if (point == 0) {
            return box.getCenterZ();
        }
        return ((point - 1) & 4) == 0 ? box.getMinZ() : box.getMaxZ();
    }

    private static int countTargetsInRange(final Player player, final double px, final double py, final double pz) {
        int count = 0;
        for (final Entity entity : player.getNearbyEntities(6.0, 6.0, 6.0)) {
            if (!(entity instanceof LivingEntity) || entity.isDead()) {
                continue;
            }
            if (entity instanceof Player other && other.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            final Location position = entity.getLocation();
            final double dx = position.getX() - px;
            final double dy = position.getY() - py;
            final double dz = position.getZ() - pz;
            if (dx * dx + dy * dy + dz * dz <= 36.0) {
                count++;
            }
        }
        return count;
    }

}
