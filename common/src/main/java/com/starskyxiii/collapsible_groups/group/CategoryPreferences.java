package com.starskyxiii.collapsible_groups.group;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record CategoryPreferences(Map<String, String> customCategories, Map<String, String> sourceNames,
                                  Map<String, String> groupCategories) {
    public CategoryPreferences {
        customCategories = freeze(customCategories);
        sourceNames = freeze(sourceNames);
        groupCategories = freeze(groupCategories);
        customCategories.forEach((id, name) -> {
            if (!localId(id)) throw new IllegalArgumentException("Invalid local category ID");
            text(name);
        });
        sourceNames.forEach((id, name) -> {
            sourceId(id);
            text(name);
        });
        for (var assignment : groupCategories.entrySet()) {
            text(assignment.getKey());
            String id = assignment.getValue();
            if (id == null) continue;
            if (id.startsWith("local:")) {
                if (!customCategories.containsKey(id)) throw new IllegalArgumentException("Unknown local category: " + id);
            } else sourceId(id);
        }
    }

    public static CategoryPreferences empty() { return new CategoryPreferences(Map.of(), Map.of(), Map.of()); }

    public static CategoryPreferences parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.keySet().equals(Set.of("version", "custom_categories", "source_names", "group_categories"))
            || !new JsonPrimitive(1).equals(root.get("version"))) throw new IllegalArgumentException("Unsupported category preferences");
        return new CategoryPreferences(read(root.getAsJsonObject("custom_categories"), false),
            read(root.getAsJsonObject("source_names"), false), read(root.getAsJsonObject("group_categories"), true));
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("custom_categories", object(customCategories));
        root.add("source_names", object(sourceNames));
        root.add("group_categories", object(groupCategories));
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create().toJson(root) + "\n";
    }

    public CategoryPreferences rename(String id, String name) {
        var custom = new LinkedHashMap<>(customCategories);
        var sources = new LinkedHashMap<>(sourceNames);
        if (id.startsWith("local:")) {
            if (!custom.containsKey(id)) throw new IllegalArgumentException("Unknown local category");
            custom.put(id, text(name).trim());
        } else sources.put(id, text(name).trim());
        return new CategoryPreferences(custom, sources, groupCategories);
    }

    public CategoryPreferences resetName(String id) {
        var sources = new LinkedHashMap<>(sourceNames);
        sources.remove(id);
        return new CategoryPreferences(customCategories, sources, groupCategories);
    }

    public CategoryPreferences create(String id, String name) {
        var custom = new LinkedHashMap<>(customCategories);
        if (custom.putIfAbsent(id, text(name).trim()) != null) throw new IllegalArgumentException("Duplicate category ID");
        return new CategoryPreferences(custom, sourceNames, groupCategories);
    }

    public CategoryPreferences delete(String id) {
        var custom = new LinkedHashMap<>(customCategories);
        if (custom.remove(id) == null) throw new IllegalArgumentException("Unknown local category");
        var assignments = new LinkedHashMap<>(groupCategories);
        assignments.values().removeIf(id::equals);
        return new CategoryPreferences(custom, sourceNames, assignments);
    }

    public CategoryPreferences assign(Collection<String> groups, String category) {
        var assignments = new LinkedHashMap<>(groupCategories);
        groups.forEach(id -> assignments.put(id, category));
        return new CategoryPreferences(customCategories, sourceNames, assignments);
    }

    public CategoryPreferences followSource(Collection<String> groups) {
        var assignments = new LinkedHashMap<>(groupCategories);
        groups.forEach(assignments::remove);
        return new CategoryPreferences(customCategories, sourceNames, assignments);
    }

    public String effectiveCategory(String groupId, GroupOrigin origin, GroupResourceData resources) {
        String id = groupCategories.containsKey(groupId) ? groupCategories.get(groupId)
            : java.util.Optional.ofNullable(GroupResourceLoader.categoryId(origin)).map(Object::toString).orElse(null);
        if (id == null || customCategories.containsKey(id)) return id;
        GroupCategory source = resources.categories().get(ResourceLocation.tryParse(id));
        return source != null && source.available() ? id : null;
    }

    private static Map<String, String> freeze(Map<String, String> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, String> read(JsonObject object, boolean nullable) {
        if (object == null) throw new IllegalArgumentException("Missing category preferences object");
        Map<String, String> values = new LinkedHashMap<>();
        object.entrySet().forEach(entry -> {
            JsonElement value = entry.getValue();
            if (nullable && value.isJsonNull()) values.put(entry.getKey(), null);
            else {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected category text");
                values.put(entry.getKey(), text(value.getAsString()));
            }
        });
        return values;
    }

    private static JsonObject object(Map<String, String> values) {
        JsonObject object = new JsonObject();
        values.forEach(object::addProperty);
        return object;
    }

    private static boolean localId(String id) {
        if (id == null || !id.startsWith("local:")) return false;
        try { return UUID.fromString(id.substring(6)).toString().equals(id.substring(6)); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static void sourceId(String id) {
        if (id == null || id.startsWith("local:") || !id.contains(":") || ResourceLocation.tryParse(id) == null)
            throw new IllegalArgumentException("Invalid source category ID");
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Category text must not be blank");
        return value;
    }
}
