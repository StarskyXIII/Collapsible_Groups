package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidAmount;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidAmountUnit;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionInput;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionResult;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeJeiIngredientTypes implements JeiIngredientTypes.FluidTypeProvider {
	@Override
	public IIngredientType<?> getFluidType() {
		return NeoForgeTypes.FLUID_STACK;
	}

	@Override
	public FluidConversionResult convertFluid(Object viewerValue) {
		if (!(viewerValue instanceof FluidStack stack)) {
			return new FluidConversionResult.Unsupported("NeoForge JEI fluid conversion requires FluidStack");
		}
		return Services.PLATFORM.convertFluid(new FluidConversionInput(stack,
			new FluidAmount(stack.getAmount(), FluidAmountUnit.MILLIBUCKET)));
	}
}
