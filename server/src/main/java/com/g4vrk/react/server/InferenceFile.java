package com.g4vrk.react.server;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

final class InferenceFile {
    static byte[] select(byte[] original, ModelProfile model) throws Exception {
        YamlConfiguration config = new YamlConfiguration(); config.loadFromString(new String(original, StandardCharsets.UTF_8));
        config.set("model", model.family().toUpperCase(Locale.ROOT));
        config.set("inference.endpoint.path", model.path());
        config.set("inference.endpoint.model-family", model.family());
        config.set("inference.endpoint.model-name", model.name());
        return config.saveToString().getBytes(StandardCharsets.UTF_8);
    }
    static ModelProfile profile(byte[] bytes) throws Exception {
        YamlConfiguration config = new YamlConfiguration(); config.loadFromString(new String(bytes, StandardCharsets.UTF_8));
        String path = config.getString("inference.endpoint.path", "");
        String family = config.getString("model", "flower").toLowerCase(Locale.ROOT);
        String name = family.equals("universe") ? "beta" : "baseline";
        int query = path.indexOf('?');
        if (query >= 0) for (String part : path.substring(query + 1).split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals("model-family")) family = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            if (pair.length == 2 && pair[0].equals("model-name")) name = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
        return new ModelProfile(family, name);
    }
    static void atomicWrite(Path path, byte[] bytes) throws Exception {
        Path temp = Files.createTempFile(path.getParent(), ".inference-", ".tmp");
        try {
            if (Files.exists(path) && Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(path));
            Files.write(temp, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
