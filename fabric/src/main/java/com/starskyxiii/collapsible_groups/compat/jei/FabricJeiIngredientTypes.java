package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidAmount;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidAmountUnit;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionInput;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionResult;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.ingredients.IIngredientType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

public final class FabricJeiIngredientTypes implements JeiIngredientTypes.FluidTypeProvider {
	@Override
	public IIngredientType<?> getFluidType() {
		return FabricTypes.FLUID_STACK;
	}

	@Override
	public FluidConversionResult convertFluid(Object viewerValue) {
		if (!(viewerValue instanceof IJeiFluidIngredient ingredient)) {
			return new FluidConversionResult.Unsupported("Fabric JEI fluid conversion requires IJeiFluidIngredient");
		}
		FluidVariant variant = ingredient.getTag()
			.map(tag -> FluidVariant.of(ingredient.getFluid(), tag))
			.orElseGet(() -> FluidVariant.of(ingredient.getFluid()));
		return Services.PLATFORM.convertFluid(new FluidConversionInput(variant,
			new FluidAmount(ingredient.getAmount(), FluidAmountUnit.FABRIC_TRANSFER)));
	}
}
