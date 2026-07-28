package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Preserves JEI's dirty-state update before Collapsible Groups replaces
 * {@code IngredientFilter.getElements()} with its projected element list.
 */
public final class JeiIngredientFilterHook {
	private JeiIngredientFilterHook() {}

	public static <T> T getElementsAfterDirtyStateUpdate(
		Runnable updateDirtyState,
		Supplier<T> getElements
	) {
		Objects.requireNonNull(updateDirtyState, "updateDirtyState").run();
		return Objects.requireNonNull(getElements, "getElements").get();
	}
}
