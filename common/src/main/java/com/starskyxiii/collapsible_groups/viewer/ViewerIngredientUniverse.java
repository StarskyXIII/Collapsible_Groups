package com.starskyxiii.collapsible_groups.viewer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered item, fluid, and generic ingredient universe supplied by a viewer. */
public final class ViewerIngredientUniverse<E> {
	private final List<ViewerIngredient<E>> ordered;
	private final Map<ViewerIngredientIdentity, ViewerIngredient<E>> byIdentity;

	public ViewerIngredientUniverse(List<ViewerIngredient<E>> ordered) {
		this.ordered = List.copyOf(ordered);
		Map<ViewerIngredientIdentity, ViewerIngredient<E>> indexed = new LinkedHashMap<>();
		for (ViewerIngredient<E> ingredient : this.ordered) {
			if (indexed.putIfAbsent(ingredient.identity(), ingredient) != null) {
				throw new IllegalArgumentException("Duplicate ingredient identity: " + ingredient.identity());
			}
		}
		this.byIdentity = Map.copyOf(indexed);
	}

	public List<ViewerIngredient<E>> ordered() {
		return ordered;
	}

	public Map<ViewerIngredientIdentity, ViewerIngredient<E>> byIdentity() {
		return byIdentity;
	}

	public List<ViewerIngredient<E>> items() {
		return ingredientsOfKind(ViewerIngredient.Kind.ITEM);
	}

	public List<ViewerIngredient<E>> fluids() {
		return ingredientsOfKind(ViewerIngredient.Kind.FLUID);
	}

	public List<ViewerIngredient<E>> generic() {
		return ingredientsOfKind(ViewerIngredient.Kind.GENERIC);
	}

	private List<ViewerIngredient<E>> ingredientsOfKind(ViewerIngredient.Kind kind) {
		return ordered.stream().filter(ingredient -> ingredient.kind() == kind).toList();
	}
}
