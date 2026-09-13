package com.starskyxiii.collapsible_groups.command;

import com.starskyxiii.collapsible_groups.i18n.GroupLanguageResources;
import com.starskyxiii.collapsible_groups.i18n.LanguageJson;
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
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, "lang/" + locale + ".json");
            for (var entry : GroupLanguageResources.ordered(manager, location)) {
                String source = entry.source();
                try (Reader reader = entry.resource().openAsReader()) {
                    entries.putAll(LanguageJson.parse(reader, source));
                    sources.add(source);
                }
            }
        }
        Path overlay = overlayDirectory.toAbsolutePath().normalize().resolve(locale + ".json");
        byte[] bytes = readOptional(overlay);
        if (bytes != null) {
            entries.putAll(LanguageJson.parse(new StringReader(new String(bytes, StandardCharsets.UTF_8)), overlay.toString()));
            sources.add(overlay.toString());
        }
        return new Snapshot(entries, sources, overlay, bytes);
    }

    private static byte[] readOptional(Path file) throws IOException {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        Path root = file.getParent();
        if (!file.toRealPath().startsWith(root.toRealPath())) throw new IOException("Target language file is outside its directory");
        return Files.readAllBytes(file);
    }
}
