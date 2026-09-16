package com.starskyxiii.collapsible_groups.group;

import java.util.Map;
import java.util.Set;

public record BuiltinCategoryPolicy(Map<String, String> originalCategories, Set<String> disabledCategories) {
    public static final BuiltinCategoryPolicy EMPTY = new BuiltinCategoryPolicy(Map.of(), Set.of());

    public BuiltinCategoryPolicy {
        originalCategories = Map.copyOf(originalCategories);
        disabledCategories = Set.copyOf(disabledCategories);
    }

    public boolean suppresses(String id) {
        String category = originalCategories.get(id);
        return category != null && disabledCategories.contains(category);
    }
}
