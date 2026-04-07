package com.starskyxiii.collapsible_groups.core;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface IngredientView {
	String ingredientType();

	@Nullable
	ResourceLocation resourceLocation();

	boolean hasTag(ResourceLocation tagId);

	default boolean hasBlockTag(ResourceLocation tagId) {
		return false;
	}

	boolean matchesExactStack(String encodedStack);

	default boolean hasComponent(String componentTypeId, String encodedValue) {
		return false;
	}

	default boolean hasComponentPath(String componentTypeId, String path, String expectedValue) {
		return false;
	}
}
