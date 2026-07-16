package com.starskyxiii.collapsible_groups.viewer;

import java.util.List;
import java.util.Objects;

/** Ingredient type and entries available during viewer bootstrap. */
public record ViewerIngredientType<E>(
	String canonicalId,
	List<String> aliases,
	List<ViewerIngredient<E>> ingredients
) {
	public ViewerIngredientType {
		Objects.requireNonNull(canonicalId, "canonicalId");
		if (canonicalId.isBlank()) throw new IllegalArgumentException("canonicalId must not be blank");
		aliases = List.copyOf(aliases);
		ingredients = List.copyOf(ingredients);
	}

	public boolean matchesId(String id) {
		return canonicalId.equals(id) || aliases.contains(id);
	}
}
