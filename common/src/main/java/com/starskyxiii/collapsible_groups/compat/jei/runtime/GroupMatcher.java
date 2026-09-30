package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.JeiFluidIngredient;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidIngredient;
import mezz.jei.api.ingredients.IIngredientHelper;

public final class GroupMatcher {

	private GroupMatcher() {}

	/** Returns {@code true} if {@code group} contains {@code stack} (exact ID or tag match), ignoring enabled state. */
	public static boolean matchesFluidIgnoringEnabled(GroupDefinition group, Object stack) {
		FluidIngredient fluid = stack instanceof JeiFluidIngredient jei
			? jei.fluid() : stack instanceof FluidIngredient converted
				? converted : com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientTypes.convertFluid(stack).require();
		return matchesFluidIgnoringEnabled(group, fluid);
	}

	public static boolean matchesFluidIgnoringEnabled(GroupDefinition group, FluidIngredient fluid) {
		if (!group.hasFluidFilters()) return false;
		return group.query().matches(fluid.view());
	}

	/** Returns {@code true} if {@code group} contains {@code stack} (exact ID or tag match). */
	public static boolean matchesFluid(GroupDefinition group, Object stack) {
		return group.enabled() && matchesFluidIgnoringEnabled(group, stack);
	}

	public static boolean matchesFluid(GroupDefinition group, FluidIngredient fluid) {
		return group.enabled() && matchesFluidIgnoringEnabled(group, fluid);
	}

	/** Returns {@code true} if {@code group} contains {@code ingredient} of the given type, ignoring enabled state. */
	public static <T> boolean matchesGenericIgnoringEnabled(GroupDefinition group, String ingredientTypeId,
	                                                       T ingredient, IIngredientHelper<T> helper) {
		if (!group.hasGenericFilters()) return false;
		return group.query().matches(new GenericJeiIngredientView<>(ingredientTypeId, ingredient, helper));
	}

	/** Returns {@code true} if {@code group} contains {@code ingredient} of the given type. */
	public static <T> boolean matchesGeneric(GroupDefinition group, String ingredientTypeId,
	                                        T ingredient, IIngredientHelper<T> helper) {
		return group.enabled() && matchesGenericIgnoringEnabled(group, ingredientTypeId, ingredient, helper);
	}
}
