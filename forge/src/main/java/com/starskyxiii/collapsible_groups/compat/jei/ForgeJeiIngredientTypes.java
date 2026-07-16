package com.starskyxiii.collapsible_groups.compat.jei;

import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientType;

public final class ForgeJeiIngredientTypes implements JeiIngredientTypes.FluidTypeProvider {
	@Override
	public IIngredientType<?> getFluidType() {
		return ForgeTypes.FLUID_STACK;
	}
}
