package com.starskyxiii.collapsible_groups.i18n;

import com.starskyxiii.collapsible_groups.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class GroupLanguageResources {
    public record Entry(ResourceLocation location, Resource resource) {
        public String source() { return resource.sourcePackId() + ":" + location; }
    }

    private GroupLanguageResources() {}

    public static List<Entry> ordered(ResourceManager manager, ResourceLocation standard) {
        List<Entry> entries = new ArrayList<>();
        manager.getResourceStack(standard).forEach(resource -> entries.add(new Entry(standard, resource)));
        if (!Constants.MOD_ID.equals(standard.getNamespace()) || !standard.getPath().startsWith("lang/")) {
            return List.copyOf(entries);
        }
        var groups = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID,
            "group_lang/" + standard.getPath().substring("lang/".length()));
        manager.getResourceStack(groups).forEach(resource -> entries.add(new Entry(groups, resource)));
        Map<PackResources, Integer> ranks = new IdentityHashMap<>();
        try (var packs = manager.listPacks()) {
            packs.forEachOrdered(pack -> ranks.put(pack, ranks.size()));
        }
        entries.sort(Comparator.comparingInt((Entry entry) -> ranks.getOrDefault(entry.resource().source(), Integer.MAX_VALUE))
            .thenComparingInt(entry -> entry.location().equals(standard) ? 0 : 1));
        return List.copyOf(entries);
    }

    public static List<Resource> runtimeStack(ResourceManager manager, ResourceLocation standard) {
        return ordered(manager, standard).stream().map(entry -> {
            if (!entry.location().getPath().startsWith("group_lang/")) return entry.resource();
            return new Resource(entry.resource().source(), () -> validatedStream(entry));
        }).toList();
    }

    private static ByteArrayInputStream validatedStream(Entry entry) throws IOException {
        byte[] bytes;
        try (var input = entry.resource().open()) {
            bytes = input.readAllBytes();
        } catch (RuntimeException failure) {
            throw new IOException("Cannot read group language: " + entry.source(), failure);
        }
        LanguageJson.parse(new StringReader(new String(bytes, StandardCharsets.UTF_8)), entry.source());
        return new ByteArrayInputStream(bytes);
    }
}
