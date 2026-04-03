package com.starskyxiii.collapsible_groups.core;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public interface IngredientView {
	String ingredientType();

	@Nullable
	Identifier resourceLocation();

	boolean hasTag(Identifier tagId);

	boolean matchesExactStack(String encodedStack);

	default boolean hasComponent(String componentTypeId, String encodedValue) {
		return false;
	}

	default boolean hasComponentPath(String componentTypeId, String path, String expectedValue) {
		return false;
	}
}
