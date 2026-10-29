package com.g4vrk.react.vector.feature;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class HeldItemClassifier {

    private static final int OTHER = 0;
    private static final int EMPTY = 1;
    private static final int MELEE = 2;
    private static final int END_CRYSTAL = 3;
    private static final int OBSIDIAN = 4;
    private static final int RESPAWN_ANCHOR = 5;
    private static final int GLOWSTONE = 6;
    private static final int TOTEM = 7;
    private static final int BLOCK = 8;
    private static final int RANGED = 9;
    private static final int THROWABLE = 10;
    private static final int EDIBLE = 11;
    private static final int SHIELD = 12;

    private HeldItemClassifier() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static int classify(final @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return EMPTY;
        }
        final Material type = item.getType();
        final String name = type.name();
        switch (name) {
            case "END_CRYSTAL":
                return END_CRYSTAL;
            case "RESPAWN_ANCHOR":
                return RESPAWN_ANCHOR;
            case "GLOWSTONE":
                return GLOWSTONE;
            case "TOTEM_OF_UNDYING":
                return TOTEM;
            case "SHIELD":
                return SHIELD;
            case "OBSIDIAN":
            case "CRYING_OBSIDIAN":
            case "BEDROCK":
                return OBSIDIAN;
            case "BOW":
            case "CROSSBOW":
            case "TRIDENT":
                return RANGED;
            case "ENDER_PEARL":
            case "SNOWBALL":
            case "EGG":
                return THROWABLE;
            default:
                break;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE")) {
            return MELEE;
        }
        if (type.isEdible()) {
            return EDIBLE;
        }
        return type.isBlock() ? BLOCK : OTHER;
    }

}
