package com.starskyxiii.collapsible_groups.compat.jei.data;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.network.chat.Component;

import java.util.Set;

/**
 * A resolved view of a generic (non-item, non-fluid) JEI ingredient,
 * carrying all data needed for rendering, searching, and selection.
 */
public record GenericIngredientView(
	String typeId,
	IIngredientType<Object> type,
	Object ingredient,
	IIngredientHelper<Object> helper,
	IIngredientRenderer<Object> renderer,
	Component displayName,
	String resourceId,
	Set<String> tagIds,
	String searchKey
) {}
