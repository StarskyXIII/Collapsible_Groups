package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Item bucket index over the JEI ingredient list, used during the ownership build phase.
 *
 * <p>Unlike {@link EditorItemIndex}, this index retains {@link ITypedIngredient} references
 * because the ownership index is keyed by JEI ingredient instance identity.
 */
public final class IngredientFilterItemIndex {
	public record ItemEntry(ITypedIngredient<?> typed, ItemStack stack, int ordinal) {}

	private final List<ItemEntry> orderedEntries;
	private final Map<ResourceLocation, List<ItemEntry>> byId;
	private final Map<String, List<ItemEntry>> byNamespace;
	private final Map<ResourceLocation, List<ItemEntry>> byTag;

	private IngredientFilterItemIndex(
		List<ItemEntry> orderedEntries,
		Map<ResourceLocation, List<ItemEntry>> byId,
		Map<String, List<ItemEntry>> byNamespace,
		Map<ResourceLocation, List<ItemEntry>> byTag
	) {
		this.orderedEntries = orderedEntries;
		this.byId = byId;
		this.byNamespace = byNamespace;
		this.byTag = byTag;
	}

	public static IngredientFilterItemIndex build(List<ITypedIngredient<?>> all) {
		Map<ResourceLocation, List<ItemEntry>> byId = new HashMap<>();
		Map<String, List<ItemEntry>> byNamespace = new HashMap<>();
		Map<ResourceLocation, List<ItemEntry>> byTag = new HashMap<>();
		List<ItemEntry> orderedEntries = new ArrayList<>();

		for (ITypedIngredient<?> ingredient : all) {
			ingredient.getItemStack().ifPresent(stack -> {
				ItemEntry entry = new ItemEntry(ingredient, stack, orderedEntries.size());
				orderedEntries.add(entry);

				ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
				if (id != null) {
					byId.computeIfAbsent(id, k -> new ArrayList<>()).add(entry);
					byNamespace.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(entry);
				}

				stack.getItem().builtInRegistryHolder().tags().forEach(tagKey ->
					byTag.computeIfAbsent(tagKey.location(), k -> new ArrayList<>()).add(entry)
				);
			});
		}

		return new IngredientFilterItemIndex(
			List.copyOf(orderedEntries),
			freezeBuckets(byId),
			freezeBuckets(byNamespace),
			freezeBuckets(byTag)
		);
	}

	public List<ItemEntry> orderedEntries() {
		return orderedEntries;
	}

	public List<ItemEntry> byId(ResourceLocation id) {
		return byId.getOrDefault(id, List.of());
	}

	public List<ItemEntry> byNamespace(String namespace) {
		return byNamespace.getOrDefault(namespace, List.of());
	}

	public List<ItemEntry> byTag(ResourceLocation tagId) {
		return byTag.getOrDefault(tagId, List.of());
	}

	public static List<ItemEntry> collectOrderedUnion(List<List<ItemEntry>> buckets) {
		if (buckets.isEmpty()) return List.of();
		if (buckets.size() == 1) return buckets.getFirst();

		IdentityHashMap<ITypedIngredient<?>, Boolean> seen = new IdentityHashMap<>();
		List<ItemEntry> merged = new ArrayList<>();
		for (List<ItemEntry> bucket : buckets) {
			for (ItemEntry entry : bucket) {
				if (seen.put(entry.typed(), Boolean.TRUE) == null) {
					merged.add(entry);
				}
			}
		}
		merged.sort(Comparator.comparingInt(ItemEntry::ordinal));
		return List.copyOf(merged);
	}

	private static <K> Map<K, List<ItemEntry>> freezeBuckets(Map<K, List<ItemEntry>> mutable) {
		Map<K, List<ItemEntry>> copy = new LinkedHashMap<>(mutable.size());
		for (var entry : mutable.entrySet()) {
			copy.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Map.copyOf(copy);
	}
}
