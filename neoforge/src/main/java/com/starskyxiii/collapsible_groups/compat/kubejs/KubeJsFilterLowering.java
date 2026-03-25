package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

	public static @Nullable GroupFilter composeFallbackNodes(List<GroupFilter> nodes) {
		if (nodes.isEmpty()) {
			return null;
		}
		if (nodes.size() == 1) {
			return nodes.getFirst();
		}
		return Filters.any(nodes.toArray(GroupFilter[]::new));
	}
}
