package com.starskyxiii.collapsible_groups.viewer;

import java.util.List;
import java.util.Optional;

/** Early build-phase access to universes and type resolution before normal viewer runtime exists. */
public interface ViewerBootstrapContext<E> extends ViewerUniverseProvider<E> {
	List<ViewerIngredientType<E>> ingredientTypes();

	default Optional<ViewerIngredientType<E>> resolveType(String id) {
		return ingredientTypes().stream().filter(type -> type.matchesId(id)).findFirst();
	}
}
