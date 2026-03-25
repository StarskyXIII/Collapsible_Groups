package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.ingredients.IIngredientHelper;

/**
 * Matching helpers for fluid and generic ingredient types.
 *
 * <p>Fluid matching uses the loader-agnostic {@link Services#PLATFORM} abstraction,
 * so fluid stacks are passed as {@code Object} (e.g. NeoForge {@code FluidStack}).
 *
 * <p>All methods that were previously on {@link GroupDefinition} and depended on
 * loader/JEI APIs live here.  {@link GroupDefinition} itself now resides in
 * {@code common/core/} and is fully loader-agnostic.
 */
public final class GroupMatcher {

	private GroupMatcher() {}

	/** Returns {@code true} if {@code group} contains {@code stack} (exact ID or tag match), ignoring enabled state. */
	public static boolean matchesFluidIgnoringEnabled(GroupDefinition group, Object stack) {
		if (!group.hasFluidFilters()) return false;
		return group.compiledFilter().matches(Services.PLATFORM.createFluidView(stack));
	}

	/** Returns {@code true} if {@code group} contains {@code stack} (exact ID or tag match). */
	public static boolean matchesFluid(GroupDefinition group, Object stack) {
		return group.enabled() && matchesFluidIgnoringEnabled(group, stack);
	}

	/** Returns {@code true} if {@code group} contains {@code ingredient} of the given type, ignoring enabled state. */
	public static <T> boolean matchesGenericIgnoringEnabled(GroupDefinition group, String ingredientTypeId,
	                                                       T ingredient, IIngredientHelper<T> helper) {
		if (!group.hasGenericFilters()) return false;
		return group.compiledFilter().matches(new GenericJeiIngredientView<>(ingredientTypeId, ingredient, helper));
	}

	/** Returns {@code true} if {@code group} contains {@code ingredient} of the given type. */
	public static <T> boolean matchesGeneric(GroupDefinition group, String ingredientTypeId,
	                                        T ingredient, IIngredientHelper<T> helper) {
		return group.enabled() && matchesGenericIgnoringEnabled(group, ingredientTypeId, ingredient, helper);
	}
}
