package com.starskyxiii.collapsible_groups.client.manager;

import com.starskyxiii.collapsible_groups.group.CategoryPreferences;
import com.starskyxiii.collapsible_groups.group.GroupResourceData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CategoryChoices {
    public static final String ALL = "all";
    public static final String UNCATEGORIZED = "uncategorized";
    public static final String FOLLOW_SOURCE = "follow_source";
    public static final String MANAGE = "manage";

    public record Entry(String id, Component label) {}

    private CategoryChoices() {}

    public static List<Entry> available(CategoryPreferences preferences, GroupResourceData resources) {
        List<Entry> entries = new ArrayList<>();
        preferences.customCategories().forEach((id, name) -> entries.add(new Entry(id, Component.literal(name))));
        resources.categories().values().stream().filter(category -> category.available()).forEach(category ->
            entries.add(new Entry(category.id().toString(), name(category.id().toString(), preferences, resources))));
        entries.sort(Comparator.comparing((Entry entry) -> entry.label().getString(), String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id));
        return List.copyOf(entries);
    }

    public static Component name(String id, CategoryPreferences preferences, GroupResourceData resources) {
        if (id == null || UNCATEGORIZED.equals(id)) return label("uncategorized");
        if (ALL.equals(id)) return label("all");
        String custom = preferences.customCategories().get(id);
        if (custom != null) return Component.literal(custom);
        String renamed = preferences.sourceNames().get(id);
        if (renamed != null) return Component.literal(renamed);
        var category = resources.categories().get(ResourceLocation.tryParse(id));
        return category == null ? label("uncategorized") : category.displayName().toComponent();
    }

    public static Component label(String key, Object... arguments) {
        return Component.translatable("collapsible_groups.category." + key, arguments);
    }
}
