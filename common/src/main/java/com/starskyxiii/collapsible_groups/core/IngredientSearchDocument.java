package com.starskyxiii.collapsible_groups.core;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable, recipe-viewer-neutral search data for one editor ingredient. */
public record IngredientSearchDocument(
	List<String> textValues,
	List<String> modValues,
	Set<String> tagValues
) {
	public IngredientSearchDocument {
		textValues = normalizedList(textValues);
		modValues = normalizedList(modValues);
		tagValues = normalizedSet(tagValues);
	}

	public static IngredientSearchDocument of(
		Collection<String> textValues,
		Collection<String> modValues,
		Collection<String> tagValues
	) {
		return new IngredientSearchDocument(List.copyOf(textValues), List.copyOf(modValues), Set.copyOf(tagValues));
	}

	private static List<String> normalizedList(Collection<String> values) {
		Objects.requireNonNull(values, "values");
		return values.stream().filter(Objects::nonNull).map(IngredientSearchDocument::normalize)
			.filter(value -> !value.isEmpty()).distinct().toList();
	}

	private static Set<String> normalizedSet(Collection<String> values) {
		Objects.requireNonNull(values, "values");
		return values.stream().filter(Objects::nonNull).map(IngredientSearchDocument::normalize)
			.filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
	}

	private static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}
}
