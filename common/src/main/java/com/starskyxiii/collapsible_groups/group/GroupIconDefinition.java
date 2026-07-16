package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;

import java.util.Objects;

/** Persistent identity of an ingredient used as a group header icon. */
public record GroupIconDefinition(String ingredientType, String valueId) {
	public GroupIconDefinition {
		ingredientType = requireNonBlank(ingredientType, "ingredientType");
		valueId = requireNonBlank(valueId, "valueId");
	}

	public static GroupIconDefinition item(String itemId) {
		return new GroupIconDefinition("item", itemId);
	}

	/** Resolves aliases lazily so configs parsed before JEI bootstrap still work. */
	public String canonicalIngredientType() {
		String canonical = IngredientTypeIds.getCanonicalId(ingredientType);
		return canonical != null ? canonical : ingredientType;
	}

	public boolean isItem() {
		return "item".equals(canonicalIngredientType());
	}

	private static String requireNonBlank(String value, String field) {
		String normalized = Objects.requireNonNull(value, field).trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
		return normalized;
	}
}
