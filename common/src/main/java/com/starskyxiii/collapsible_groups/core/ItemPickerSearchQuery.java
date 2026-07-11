package com.starskyxiii.collapsible_groups.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Parsed, immutable search query for the editor's item picker. */
public record ItemPickerSearchQuery(
	List<String> textTerms,
	List<String> namespacePrefixes,
	List<String> tagTerms
) {
	public ItemPickerSearchQuery {
		textTerms = List.copyOf(textTerms);
		namespacePrefixes = List.copyOf(namespacePrefixes);
		tagTerms = List.copyOf(tagTerms);
	}

	/**
	 * Splits on whitespace. Plain tokens match display name or id, {@code @x}
	 * filters namespace prefixes, and {@code #x} requires an item tag.
	 */
	public static ItemPickerSearchQuery parse(String input) {
		List<String> text = new ArrayList<>();
		List<String> namespaces = new ArrayList<>();
		List<String> tags = new ArrayList<>();
		String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
		if (!normalized.isEmpty()) {
			for (String token : normalized.split("\\s+")) {
				if (token.length() > 1 && token.charAt(0) == '@') {
					namespaces.add(token.substring(1));
				} else if (token.length() > 1 && token.charAt(0) == '#') {
					tags.add(token.substring(1));
				} else if (!token.isEmpty()) {
					text.add(token);
				}
			}
		}
		return new ItemPickerSearchQuery(text, namespaces, tags);
	}

	public boolean isEmpty() {
		return textTerms.isEmpty() && namespacePrefixes.isEmpty() && tagTerms.isEmpty();
	}

	/** Matches a live stack, including its item tags. */
	public boolean matches(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return matches(stack.getHoverName().getString(), id, tagId -> {
			ResourceLocation location = ResourceLocation.tryParse(tagId);
			return location != null && stack.is(TagKey.create(Registries.ITEM, location));
		});
	}

	/** Pure matching seam used by tests and non-rendering callers. */
	public boolean matches(String displayName, ResourceLocation id, Predicate<String> tagMatcher) {
		if (id == null) return false;
		String lowerName = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
		String lowerId = id.toString().toLowerCase(Locale.ROOT);
		String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
		for (String prefix : namespacePrefixes) {
			if (!namespace.startsWith(prefix)) return false;
		}
		for (String term : textTerms) {
			if (!lowerName.contains(term) && !lowerId.contains(term)) return false;
		}
		for (String tag : tagTerms) {
			if (!tagMatcher.test(tag)) return false;
		}
		return true;
	}
}
