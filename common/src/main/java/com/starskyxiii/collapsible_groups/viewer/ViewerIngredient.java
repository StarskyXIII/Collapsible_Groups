package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.core.IngredientView;

import java.util.Objects;

/** An opaque viewer entry paired with its neutral identity and filter view. */
public record ViewerIngredient<E>(
	ViewerIngredientIdentity identity,
	Kind kind,
	E entry,
	IngredientView view
) {
	public ViewerIngredient {
		Objects.requireNonNull(identity, "identity");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(entry, "entry");
		Objects.requireNonNull(view, "view");
	}

	public enum Kind {
		ITEM,
		FLUID,
		GENERIC
	}
}
