package com.g4vrk.react.vector.feature;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class HeldItemClassifier {

    private HeldItemClassifier() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static int classify(final @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 1;
        }
        final Material type = item.getType();
        final String name = type.name();
        switch (name) {
            case "END_CRYSTAL":
                return 3;
            case "RESPAWN_ANCHOR":
                return 5;
            case "GLOWSTONE":
                return 6;
            case "TOTEM_OF_UNDYING":
                return 7;
            case "SHIELD":
                return 12;
            case "OBSIDIAN":
            case "CRYING_OBSIDIAN":
            case "BEDROCK":
                return 4;
            case "BOW":
            case "CROSSBOW":
            case "TRIDENT":
                return 9;
            case "ENDER_PEARL":
            case "SNOWBALL":
            case "EGG":
                return 10;
            default:
                break;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
            return 2;
        }
        if (type.isEdible()) {
            return 11;
        }
        return type.isBlock() ? 8 : 0;
    }

}
