package com.g4vrk.react.vector.session;

import com.g4vrk.react.vector.feature.AimModeTracker;
import com.g4vrk.react.vector.feature.HeldItemClassifier;
import com.g4vrk.react.vector.feature.PlayerClientInfo;
import com.g4vrk.react.vector.feature.TargetFeatures;
import com.g4vrk.react.vector.model.AimFrame;
import com.g4vrk.react.vector.model.VectorFeature;
import com.g4vrk.react.vector.util.MouseMath;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AimState {

    private final Object lock = new Object();
    private final AimModeTracker aimModes = new AimModeTracker();
    private float lastYaw;
    private float lastPitch;
    private float lastDeltaYaw;
    private float lastDeltaPitch;
    private float lastAccelYaw;
    private float lastAccelPitch;
    private boolean rotationOnPreviousFlying = true;
    private boolean idleFlyingSinceLastFrame;
    private UUID targetId;
    private int pendingTargetEntityId = -1;

    public void onAttackEntity(final int entityId) {
        synchronized (lock) {
            targetId = null;
            pendingTargetEntityId = entityId;
        }
    }

    public void onAttack(final @Nullable UUID targetUuid) {
        synchronized (lock) {
            targetId = targetUuid;
            pendingTargetEntityId = -1;
        }
    }

    public int takePendingTargetEntityId() {
        synchronized (lock) {
            final int entityId = pendingTargetEntityId;
            pendingTargetEntityId = -1;
            return entityId;
        }
    }

    public @Nullable UUID targetId() {
        synchronized (lock) {
            return targetId;
        }
    }

    public void onFlying(final boolean hasRotation) {
        if (hasRotation) {
            return;
        }
        synchronized (lock) {
            idleFlyingSinceLastFrame = true;
        }
    }

    public void resetVector() {
        synchronized (lock) {
            aimModes.reset();
            targetId = null;
            pendingTargetEntityId = -1;
        }
    }

    public @NotNull AimFrame capture(
            final @NotNull Player player,
            final @Nullable Entity target,
            final float rawYaw,
            final float rawPitch
    ) {
        synchronized (lock) {
            final float yaw = MouseMath.normalizeAngle(rawYaw);
            final float pitch = Math.max(-90.0f, Math.min(90.0f, rawPitch));
            final float deltaYaw = MouseMath.normalizeAngle(yaw - lastYaw);
            final float deltaPitch = pitch - lastPitch;
            final float accelYaw = MouseMath.calculateAcceleration(deltaYaw, lastDeltaYaw);
            final float accelPitch = MouseMath.calculateAcceleration(deltaPitch, lastDeltaPitch);
            final float jerkYaw = MouseMath.calculateJerk(accelYaw, lastAccelYaw);
            final float jerkPitch = MouseMath.calculateJerk(accelPitch, lastAccelPitch);
            final float gcdErrorYaw = MouseMath.calculateGCDError(deltaYaw, lastDeltaYaw);
            final float gcdErrorPitch = MouseMath.calculateGCDError(deltaPitch, lastDeltaPitch);
            final float[] vector = captureFeatures(player, target, yaw, pitch, deltaYaw, deltaPitch);
            lastYaw = yaw;
            lastPitch = pitch;
            lastDeltaYaw = deltaYaw;
            lastDeltaPitch = deltaPitch;
            lastAccelYaw = accelYaw;
            lastAccelPitch = accelPitch;
            return new AimFrame(
                    deltaYaw, deltaPitch, accelYaw, accelPitch, jerkYaw, jerkPitch, gcdErrorYaw, gcdErrorPitch, vector
            );
        }
    }

    private float[] captureFeatures(
            final Player player,
            final Entity target,
            final float yaw,
            final float pitch,
            final float deltaYaw,
            final float deltaPitch
    ) {
        final float[] features = new float[VectorFeature.COUNT];
        VectorFeature.ROTATION_PRESENT.put(features, rotationOnPreviousFlying && !idleFlyingSinceLastFrame);
        rotationOnPreviousFlying = !idleFlyingSinceLastFrame;
        idleFlyingSinceLastFrame = false;
        VectorFeature.PITCH.put(features, pitch);
        aimModes.update(lastYaw, yaw, deltaYaw, deltaPitch);
        aimModes.writeTo(features);
        final boolean targetValid = target != null && target.isValid() && !target.isDead()
                && target.getWorld().equals(player.getWorld());
        VectorFeature.TARGET_VALID.put(features, targetValid);
        if (targetValid) {
            TargetFeatures.writeTo(features, player, target, lastYaw, lastPitch, yaw, pitch);
        }
        VectorFeature.HELD_ITEM_CLASS.put(features, HeldItemClassifier.classify(player.getInventory().getItemInMainHand()));
        VectorFeature.PLAYER_PING.put(features, PlayerClientInfo.ping(player));
        return features;
    }

}
