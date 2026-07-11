package com.starskyxiii.collapsible_groups.core;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IngredientView {
	String ingredientType();

	@Nullable
	Identifier resourceLocation();

	boolean hasTag(Identifier tagId);

	default boolean hasBlockTag(Identifier tagId) {
		return false;
	}

	boolean matchesExactStack(String encodedStack);

	/**
	 * deep-compares this view's ingredient against an already-decoded exact-stack
	 * reference. Used by {@code CompiledFilter}'s folded exact-stack node so the run's selectors
	 * are decoded once up front and only the per-candidate normalization + component comparison
	 * happens here. The default returns {@code false}; only item-backed views override it.
	 */
	default boolean matchesDecodedExactStack(ItemStack decoded) {
		return false;
	}

	default boolean hasComponent(String componentTypeId, String encodedValue) {
		return false;
	}

	default boolean hasComponentPath(String componentTypeId, String path, String expectedValue) {
		return false;
	}
}
