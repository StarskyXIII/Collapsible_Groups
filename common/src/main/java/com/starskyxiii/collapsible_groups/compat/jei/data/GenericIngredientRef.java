package com.starskyxiii.collapsible_groups.compat.jei.data;

import mezz.jei.api.ingredients.IIngredientType;

/**
 * Runtime reference to a concrete JEI generic ingredient instance and its type.
 */
public record GenericIngredientRef(
	String typeId,
	IIngredientType<Object> type,
	Object ingredient
) {}
