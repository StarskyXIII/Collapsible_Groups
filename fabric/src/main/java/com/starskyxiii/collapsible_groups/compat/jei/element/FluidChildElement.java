package com.starskyxiii.collapsible_groups.compat.jei.element;

import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.ingredients.ITypedIngredient;

public final class FluidChildElement extends AbstractFluidChildElement<IJeiFluidIngredient> {
	public FluidChildElement(ITypedIngredient<IJeiFluidIngredient> ingredient, String groupId) {
		super(ingredient, groupId);
	}
}
