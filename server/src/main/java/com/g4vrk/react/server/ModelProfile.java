package com.g4vrk.react.server;

import java.util.Locale;

record ModelProfile(String family, String name) {
    ModelProfile {
        family = family.toLowerCase(Locale.ROOT); name = name.toLowerCase(Locale.ROOT);
        if (!(family.equals("flower") && (name.equals("baseline") || name.equals("ultra"))) && !(family.equals("universe") && name.equals("beta")))
            throw new IllegalArgumentException("Unsupported model profile");
    }
    String path() { return "/api/v2/inference/?model-family=" + family + "&model-name=" + name; }
    String display() { return (family.equals("flower") ? "Flower" : "Universe") + "/" + Character.toUpperCase(name.charAt(0)) + name.substring(1); }
}
