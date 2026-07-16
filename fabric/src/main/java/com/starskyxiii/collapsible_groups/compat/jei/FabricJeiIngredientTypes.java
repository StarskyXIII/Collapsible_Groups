package com.starskyxiii.collapsible_groups.compat.jei;

import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.ingredients.IIngredientType;

public final class FabricJeiIngredientTypes implements JeiIngredientTypes.FluidTypeProvider {
	@Override
	public IIngredientType<?> getFluidType() {
		return FabricTypes.FLUID_STACK;
	}
}
