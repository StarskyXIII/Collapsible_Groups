package com.starskyxiii.collapsible_groups.internal.query;

import java.util.List;
import java.util.Map;

public interface IngredientCatalog<E, I> {
	List<E> ordered();

	Map<I, E> byIdentity();

	Object sourceToken();
}
