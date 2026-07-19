package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Shared lowering helpers for KubeJS bridge fallback paths.
 */
public final class KubeJsFilterLowering {
	private KubeJsFilterLowering() {}

	public static GroupFilter lowerResolvedFluidStack(FluidStack stack) {
		return Filters.fluidId(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
	}

	public static GroupFilter lowerResolvedGenericIngredient(String typeId, ResourceLocation id) {
		return Filters.genericId(typeId, id.toString());
	}

}
