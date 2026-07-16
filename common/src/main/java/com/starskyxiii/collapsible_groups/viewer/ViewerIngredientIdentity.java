package com.starskyxiii.collapsible_groups.viewer;

import java.util.Objects;

/** Stable viewer-independent identity for an ingredient entry. */
public record ViewerIngredientIdentity(String typeId, String valueId) {
	public ViewerIngredientIdentity {
		Objects.requireNonNull(typeId, "typeId");
		Objects.requireNonNull(valueId, "valueId");
		if (typeId.isBlank()) throw new IllegalArgumentException("typeId must not be blank");
		if (valueId.isBlank()) throw new IllegalArgumentException("valueId must not be blank");
	}
}
