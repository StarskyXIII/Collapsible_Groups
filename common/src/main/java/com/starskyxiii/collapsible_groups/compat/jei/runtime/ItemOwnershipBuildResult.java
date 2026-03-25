package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure item-ownership build result before publication into {@link GroupRegistry}.
 */
public record ItemOwnershipBuildResult(
	Map<ITypedIngredient<?>, GroupDefinition> ingredientGroupIndex,
	Map<String, List<IngredientFilterItemIndex.ItemEntry>> fullMatchEntriesByGroup,
	Map<String, List<IngredientFilterItemIndex.ItemEntry>> resolvedEntriesByGroup,
	Map<String, Set<String>> itemIdToGroupIds
) {}
