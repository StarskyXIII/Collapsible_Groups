package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidAmount;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidAmountUnit;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionInput;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionResult;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraftforge.fluids.FluidStack;

public final class ForgeJeiIngredientTypes implements JeiIngredientTypes.FluidTypeProvider {
	@Override
	public IIngredientType<?> getFluidType() {
		return ForgeTypes.FLUID_STACK;
	}

	@Override
	public FluidConversionResult convertFluid(Object viewerValue) {
		if (!(viewerValue instanceof FluidStack stack)) {
			return new FluidConversionResult.Unsupported("Forge JEI fluid conversion requires FluidStack");
		}
		return Services.PLATFORM.convertFluid(new FluidConversionInput(stack,
			new FluidAmount(stack.getAmount(), FluidAmountUnit.MILLIBUCKET)));
	}
}
