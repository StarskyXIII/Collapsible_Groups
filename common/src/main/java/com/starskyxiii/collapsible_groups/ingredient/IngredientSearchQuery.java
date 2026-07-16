package com.starskyxiii.collapsible_groups.ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parsed search syntax shared by editor ingredient lists and reference pickers. */
public record IngredientSearchQuery(
	List<String> textTerms,
	List<String> modTerms,
	List<String> tagTerms,
	List<String> tooltipTerms
) {
	public IngredientSearchQuery {
		textTerms = List.copyOf(textTerms);
		modTerms = List.copyOf(modTerms);
		tagTerms = List.copyOf(tagTerms);
		tooltipTerms = List.copyOf(tooltipTerms);
	}

	public static IngredientSearchQuery parse(String input) {
		List<String> text = new ArrayList<>();
		List<String> mods = new ArrayList<>();
		List<String> tags = new ArrayList<>();
		List<String> tooltips = new ArrayList<>();
		String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
		if (!normalized.isEmpty()) {
			for (String token : normalized.split("\\s+")) {
				if (token.length() > 1 && token.charAt(0) == '@') {
					mods.add(token.substring(1));
				} else if (token.length() > 1 && token.charAt(0) == '#') {
					tags.add(token.substring(1));
				} else if (token.length() > 1 && token.charAt(0) == '$') {
					tooltips.add(token.substring(1));
				} else if (!token.isEmpty()) {
					text.add(token);
				}
			}
		}
		return new IngredientSearchQuery(text, mods, tags, tooltips);
	}

	public boolean isEmpty() {
		return textTerms.isEmpty() && modTerms.isEmpty() && tagTerms.isEmpty() && tooltipTerms.isEmpty();
	}

	public boolean matches(IngredientSearchDocument document) {
		return matches(document, List::of);
	}

	/** Tooltip values are requested only after all non-tooltip terms match. */
	public boolean matches(IngredientSearchDocument document, java.util.function.Supplier<? extends Iterable<String>> tooltipValues) {
		for (String term : textTerms) {
			if (!containsAny(document.textValues(), term)) return false;
		}
		for (String term : modTerms) {
			if (!containsAny(document.modValues(), term)) return false;
		}
		for (String term : tagTerms) {
			if (!containsAny(document.tagValues(), term)) return false;
		}
		if (!tooltipTerms.isEmpty()) {
			Iterable<String> values = tooltipValues.get();
			for (String term : tooltipTerms) {
				if (!containsAny(values, term)) return false;
			}
		}
		return true;
	}

	private static boolean containsAny(Iterable<String> values, String term) {
		for (String value : values) {
			if (value.contains(term)) return true;
		}
		return false;
	}
}
