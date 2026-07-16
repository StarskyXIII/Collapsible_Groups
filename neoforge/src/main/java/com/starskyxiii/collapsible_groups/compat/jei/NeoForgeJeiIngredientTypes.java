package com.starskyxiii.collapsible_groups.compat.jei;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;

public final class NeoForgeJeiIngredientTypes implements JeiIngredientTypes.FluidTypeProvider {
	@Override
	public IIngredientType<?> getFluidType() {
		return NeoForgeTypes.FLUID_STACK;
	}
}
