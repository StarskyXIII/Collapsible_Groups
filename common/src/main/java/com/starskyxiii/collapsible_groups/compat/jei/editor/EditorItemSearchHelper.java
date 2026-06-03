package com.starskyxiii.collapsible_groups.compat.jei.editor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EditorItemSearchHelper {
	private EditorItemSearchHelper() {}

	static String normalizeQuery(String rawQuery) {
		return rawQuery == null ? "" : rawQuery.toLowerCase(Locale.ROOT);
	}

	static List<String> buildSearchKeys(List<ItemStack> items) {
		List<String> keys = new ArrayList<>(items.size());
		for (ItemStack stack : items) {
			String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
			keys.add(name + "|" + id);
		}
		return keys;
	}

	static List<ItemStack> filterItems(
		List<ItemStack> items,
		List<String> searchKeys,
		Map<ItemStack, List<String>> ownership,
		boolean hideUsed,
		String normalizedQuery
	) {
		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			if (hideUsed && !ownership.getOrDefault(stack, List.of()).isEmpty()) continue;
			if (normalizedQuery.isBlank() || searchKeys.get(i).contains(normalizedQuery)) result.add(stack);
		}
		return result;
	}
}
