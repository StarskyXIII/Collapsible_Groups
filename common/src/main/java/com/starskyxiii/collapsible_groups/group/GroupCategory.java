package com.starskyxiii.collapsible_groups.group;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public record GroupCategory(ResourceLocation id, GroupDisplayName displayName, boolean available) {
    public static GroupCategory parse(ResourceLocation id, String json, Predicate<String> modLoaded) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        requireKeys(root, Set.of("loaders", "category", "requirement"));
        var loaders = root.getAsJsonArray("loaders");
        if (loaders == null || loaders.isEmpty()) throw new IllegalArgumentException("Missing category loaders");
        Set<String> declared = new java.util.HashSet<>();
        for (JsonElement loader : loaders) {
            String value = string(loader);
            if (!Set.of("fabric", "forge", "neoforge").contains(value) || !declared.add(value)) {
                throw new IllegalArgumentException("Invalid or duplicate category loader: " + value);
            }
        }
        JsonObject name = root.getAsJsonObject("category");
        if (name == null) throw new IllegalArgumentException("Missing category name");
        requireKeys(name, Set.of("translate", "fallback"));
        GroupDisplayName displayName = new GroupDisplayName.Localized(string(name.get("translate")), string(name.get("fallback")));
        boolean available = true;
        if (root.has("requirement")) {
            JsonObject requirement = root.getAsJsonObject("requirement");
            if (requirement == null || requirement.size() != 1
                || !requirement.has("any") && !requirement.has("all")) {
                throw new IllegalArgumentException("Requirement must contain exactly one of any or all");
            }
            boolean any = requirement.has("any");
            var conditions = requirement.getAsJsonArray(any ? "any" : "all");
            if (conditions == null || conditions.isEmpty()) throw new IllegalArgumentException("Empty mod requirement");
            List<String> mods = new ArrayList<>();
            for (JsonElement element : conditions) {
                JsonObject condition = element.getAsJsonObject();
                requireKeys(condition, Set.of("mod"));
                String mod = string(condition.get("mod"));
                if (!mod.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("Invalid mod ID: " + mod);
                mods.add(mod);
            }
            available = any ? mods.stream().anyMatch(modLoaded) : mods.stream().allMatch(modLoaded);
        }
        return new GroupCategory(id, displayName, available);
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
            || value.getAsString().isBlank()) throw new IllegalArgumentException("Expected non-blank text");
        return value.getAsString();
    }

    private static void requireKeys(JsonObject object, Set<String> allowed) {
        if (!allowed.containsAll(object.keySet())) throw new IllegalArgumentException("Unsupported metadata field");
    }
}
