package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchDocument;
import com.starskyxiii.collapsible_groups.ingredient.IngredientSearchQuery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class EditorItemSearchHelper {
	private EditorItemSearchHelper() {}

	static IngredientSearchDocument document(ItemStack stack) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		Set<String> tags = stack.getItem().builtInRegistryHolder().tags()
			.map(tag -> tag.location().toString())
			.collect(Collectors.toUnmodifiableSet());
		return IngredientSearchDocument.of(
			List.of(stack.getHoverName().getString(), id.toString()),
			List.of(id.getNamespace()),
			tags
		);
	}

	static List<ItemStack> filterItems(
		List<ItemStack> items,
		EditorItemSearchSession searchSession,
		Map<ItemStack, List<String>> ownership,
		boolean hideUsed,
		IngredientSearchQuery query
	) {
		List<ItemStack> result = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			if (hideUsed && !ownership.getOrDefault(stack, List.of()).isEmpty()) continue;
			if (searchSession.matches(stack, query)) result.add(stack);
		}
		return result;
	}
}
