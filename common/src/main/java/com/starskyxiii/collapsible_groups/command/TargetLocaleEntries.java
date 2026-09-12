package com.starskyxiii.collapsible_groups.command;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TargetLocaleEntries {
    public record Snapshot(Map<String, String> entries, List<String> sources, Path overlay, byte[] overlayBytes) {
        public Snapshot {
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
            sources = List.copyOf(sources);
            overlayBytes = overlayBytes == null ? null : overlayBytes.clone();
        }

        @Override public byte[] overlayBytes() { return overlayBytes == null ? null : overlayBytes.clone(); }

        public boolean unchanged() throws IOException {
            return Arrays.equals(overlayBytes, readOptional(overlay));
        }
    }

    private TargetLocaleEntries() {}

    public static void validateLocale(String locale) {
        if (locale == null || !locale.matches("[a-z0-9]{2,8}(?:_[a-z0-9]{2,8})*")) {
            throw new IllegalArgumentException("Invalid locale: " + locale);
        }
    }

    public static Snapshot read(ResourceManager manager, Path overlayDirectory, String locale) throws IOException {
        validateLocale(locale);
        Map<String, String> entries = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>();
        for (String namespace : manager.getNamespaces()) {
            ResourceLocation location = new ResourceLocation(namespace, "lang/" + locale + ".json");
            for (var resource : manager.getResourceStack(location)) {
                String source = resource.sourcePackId() + ":" + location;
                try (Reader reader = resource.openAsReader()) {
                    entries.putAll(parse(reader, source));
                    sources.add(source);
                }
            }
        }
        Path overlay = overlayDirectory.toAbsolutePath().normalize().resolve(locale + ".json");
        byte[] bytes = readOptional(overlay);
        if (bytes != null) {
            entries.putAll(parse(new StringReader(new String(bytes, StandardCharsets.UTF_8)), overlay.toString()));
            sources.add(overlay.toString());
        }
        return new Snapshot(entries, sources, overlay, bytes);
    }

    public static Map<String, String> parse(Reader reader, String source) throws IOException {
        try {
            var object = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, String> values = new LinkedHashMap<>();
            for (var entry : object.entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IOException("Language entry is not a string: " + source + " / " + entry.getKey());
                }
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
            return values;
        } catch (RuntimeException failure) {
            throw new IOException("Cannot parse target language: " + source, failure);
        }
    }

    private static byte[] readOptional(Path file) throws IOException {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        Path root = file.getParent();
        if (!file.toRealPath().startsWith(root.toRealPath())) throw new IOException("Target language file is outside its directory");
        return Files.readAllBytes(file);
    }
}
