package com.starskyxiii.collapsible_groups.compat.jei.element;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidChildElement extends AbstractFluidChildElement<FluidStack> {
	public FluidChildElement(ITypedIngredient<FluidStack> ingredient, String groupId) {
		super(ingredient, groupId);
	}
}
