package com.g4vrk.reactdataset;

import com.g4vrk.react.vector.model.AimFrame;
import com.g4vrk.react.vector.util.MouseMath;
import com.g4vrk.react.vector.session.AimState;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import static com.g4vrk.react.vector.model.AimFrame.*;
import static org.junit.jupiter.api.Assertions.*;

class UniverseStateTest {
    @Test void packetTargetsResolveOnceAndNeverReuseAPreviousAttackAfterReset() {
        AimState state = new AimState();
        UUID player = UUID.randomUUID();
        state.onAttack(player);
        state.onAttackEntity(123);
        assertNull(state.targetId());
        assertEquals(123, state.takePendingTargetEntityId());
        assertEquals(-1, state.takePendingTargetEntityId());
        state.onAttack(player);
        assertEquals(player, state.targetId());
        state.onAttackEntity(456);
        state.resetVector();
        assertNull(state.targetId());
        assertEquals(-1, state.takePendingTargetEntityId());
        state.onAttackEntity(789);
        state.onAttack(player);
        assertEquals(-1, state.takePendingTargetEntityId());
    }
    @Test
    void yawWrapPitchClampAndSignedDerivativesUseTheSameContinuousHistory() {
        Fixture fixture = new Fixture();
        AimState state = new AimState();
        state.capture(fixture.player, null, 179, 10);
        float[] values = state.capture(fixture.player, null, -179, 11).datasetFeatures();
        assertArrayEquals(new float[]{2, 1, -177, -9, -356, -19},
                java.util.Arrays.copyOf(values, 6));
        assertEquals(0.0f, MouseMath.calculateGCDError(0.15f, 0));
        assertEquals(MouseMath.calculateGCDError(0.123f, 0.2f),
                MouseMath.calculateGCDError(-0.123f, -0.2f));
        assertTrue(MouseMath.calculateGCDError(0.123f, 0.2f) > 0.0f);
        assertTrue(MouseMath.calculateGCDError(0.123f, 0.2f) <= 0.5f);
        values = state.capture(fixture.player, null, -178, 120).datasetFeatures();
        assertEquals(90.0f, feature(values, PITCH));
        assertEquals(79.0f, values[1]);
        state.onFlying(false);
        assertEquals(0.0f, feature(state.capture(fixture.player, null, -178, 90), ROTATION_PRESENT));
        assertEquals(0.0f, feature(state.capture(fixture.player, null, -178, 90), ROTATION_PRESENT));
        assertEquals(1.0f, feature(state.capture(fixture.player, null, -178, 90), ROTATION_PRESENT));
    }

    @Test
    void mouseModesRequireWarmupAndDotsUseTheLearnedGrid() {
        Fixture fixture = new Fixture();
        AimState state = new AimState();
        AimFrame frame = state.capture(fixture.player, null, 0.25f, 0.125f);
        assertEquals(0.0f, feature(frame, MODE_YAW_VALID));
        assertEquals(0.0f, feature(frame, DELTA_DOTS_YAW));
        for (int index = 2; index <= 80; index++) {
            frame = state.capture(fixture.player, null, index * 0.25f, index * 0.125f);
        }
        assertEquals(1.0f, feature(frame, MODE_YAW_VALID));
        assertEquals(1.0f, feature(frame, MODE_PITCH_VALID));
        assertEquals(0.25f, feature(frame, MODE_YAW));
        assertEquals(0.125f, feature(frame, MODE_PITCH));
        assertEquals(1.0f, feature(frame, DELTA_DOTS_YAW));
        assertEquals(1.0f, feature(frame, DELTA_DOTS_PITCH));
    }

    @Test
    void worldXReflectionChangesYawAndXGeometryTogetherAndKeepsQuantization() {
        Fixture original = new Fixture();
        Fixture reflected = new Fixture();
        original.targetX = 0.75;
        reflected.targetX = -0.75;
        AimState first = new AimState();
        AimState second = new AimState();
        UUID target = UUID.randomUUID();
        int[] signed = {0, 2, 4, 8 + TARGET_REL_X};
        for (int index = 0; index < 170; index++) {
            if (index % 5 == 0) {
                first.onAttack(target);
                second.onAttack(target);
            }
            float yaw = (index % 61 - 30) * 0.5f;
            float pitch = (index % 17 - 8) * 0.125f;
            float[] a = first.capture(original.player, original.target, yaw, pitch).datasetFeatures();
            float[] b = second.capture(reflected.player, reflected.target, -yaw, pitch).datasetFeatures();
            for (int feature : signed) assertEquals(-a[feature], b[feature], 1e-5f);
            // Age/cooldown use wall time; their equality is not assumed here.
            for (int feature : new int[]{1, 3, 5, 6, 7, 8 + PITCH, 8 + MODE_YAW,
                    8 + MODE_PITCH, 8 + MODE_YAW_VALID, 8 + MODE_PITCH_VALID,
                    8 + DELTA_DOTS_YAW, 8 + DELTA_DOTS_PITCH, 8 + TARGET_REL_Y,
                    8 + TARGET_REL_Z, 8 + TARGETS_IN_RANGE_COUNT, 8 + HELD_ITEM_CLASS}) assertEquals(a[feature], b[feature], 1e-5f);
        }
    }





    @Test
    void attacksAndHealthLossCannotEnterTheNewFeatureVector() {
        Fixture firstFixture = new Fixture();
        Fixture secondFixture = new Fixture();
        AimState first = new AimState();
        AimState second = new AimState();
        for (int index = 0; index < 850; index++) {
            if (index % 7 == 0) second.onAttack(UUID.randomUUID());
            secondFixture.health = index % 20;
            float yaw = index % 90;
            float pitch = index % 40;
            assertArrayEquals(first.capture(firstFixture.player, firstFixture.target, yaw, pitch).datasetFeatures(),
                    second.capture(secondFixture.player, secondFixture.target, yaw, pitch).datasetFeatures());
        }
        AimFrame frame = first.capture(firstFixture.player, firstFixture.target, 0, 0);
        assertEquals(1.0f, feature(frame, TARGET_VALID));
        assertEquals(0.0f, feature(frame, TARGET_REL_X));
        assertEquals(0.0f, feature(frame, TARGET_REL_Y));
        assertEquals(3.0f, feature(frame, TARGET_REL_Z));
        assertEquals(1.0f, feature(frame, TARGETS_IN_RANGE_COUNT));
        assertEquals(23, frame.inferenceFeatures().length);
        assertEquals(24, frame.datasetFeatures().length);
        for (String forbidden : new String[]{"miss_share", "attack_this_tick", "hits_600", "air_200",
                "ticks_to_attack", "damage_taken_this_tick", "raytrace_hit", "aim_error_yaw"}) {
            assertFalse(AimFrame.csvHeader().contains(forbidden));
        }
    }

    @Test
    void heldItemClassesKeepTheirCategoricalCodes() {
        Fixture fixture = new Fixture();
        AimState state = new AimState();
        Material[] materials = {Material.STICK, Material.AIR, Material.DIAMOND_SWORD,
                Material.END_CRYSTAL, Material.OBSIDIAN, Material.RESPAWN_ANCHOR,
                Material.GLOWSTONE, Material.TOTEM_OF_UNDYING, Material.STONE,
                Material.BOW, Material.ENDER_PEARL, Material.APPLE, Material.SHIELD};
        for (int category = 0; category < materials.length; category++) {
            fixture.held = new ItemStack(materials[category]);
            assertEquals((float) category,
                    feature(state.capture(fixture.player, null, 0, 0), HELD_ITEM_CLASS));
        }
        fixture.held = new ItemStack(Material.DIAMOND_PICKAXE);
        assertEquals(0.0f, feature(state.capture(fixture.player, null, 0, 0), HELD_ITEM_CLASS));
    }

    private static float feature(AimFrame frame, int index) {
        return feature(frame.datasetFeatures(), index);
    }

    private static float feature(float[] values, int index) {
        return values[8 + index];
    }

    private static float sum(int[] values, int end, int count) {
        int sum = 0;
        for (int index = Math.max(0, end - count + 1); index <= end; index++) sum += values[index];
        return sum;
    }

    private static final class Fixture {
        double health = 20;
        double targetX;
        ItemStack held;
        final World world = proxy(World.class, method -> {
            throw new UnsupportedOperationException(method);
        });
        final PlayerInventory inventory = proxy(PlayerInventory.class, method -> switch (method) {
            case "getItemInMainHand" -> held;
            default -> throw new UnsupportedOperationException(method);
        });
        final Player target = proxy(Player.class, method -> switch (method) {
            case "getWorld" -> world;
            case "getLocation" -> new Location(world, targetX, 0, 3);
            case "getBoundingBox" -> new BoundingBox(targetX - 0.3, 0, 2.7, targetX + 0.3, 1.8, 3.3);
            case "isValid" -> true;
            case "isDead" -> false;
            case "getGameMode" -> GameMode.SURVIVAL;
            default -> throw new UnsupportedOperationException(method);
        });
        final Player player = proxy(Player.class, method -> switch (method) {
            case "getWorld" -> world;
            case "getLocation" -> new Location(world, 0, 0, 0);
            case "getHealth" -> health;
            case "getInventory" -> inventory;
            case "getAttribute" -> null;
            case "isSneaking", "isSwimming", "isGliding" -> false;
            case "getNearbyEntities" -> List.<Entity>of(target);
            default -> throw new UnsupportedOperationException(method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.Function<String, Object> methods) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> switch (method.getName()) {
                    case "equals" -> instance == args[0];
                    case "hashCode" -> System.identityHashCode(instance);
                    case "toString" -> type.getSimpleName() + "Fixture";
                    default -> methods.apply(method.getName());
                });
    }
}
