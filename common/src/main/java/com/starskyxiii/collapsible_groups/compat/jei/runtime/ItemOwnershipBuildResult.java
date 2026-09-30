package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
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
	Map<String, Set<String>> itemIdToGroupIds,
	Map<String, String> evaluationFailures
) {
	public ItemOwnershipBuildResult(Map<ITypedIngredient<?>, GroupDefinition> owners,
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> fullMatches,
		Map<String, List<IngredientFilterItemIndex.ItemEntry>> resolved,
		Map<String, Set<String>> reverse) {
		this(owners, fullMatches, resolved, reverse, Map.of());
	}

	public ItemOwnershipBuildResult withFailures(Map<String, String> failures) {
		return new ItemOwnershipBuildResult(ingredientGroupIndex, fullMatchEntriesByGroup, resolvedEntriesByGroup,
			itemIdToGroupIds, Map.copyOf(failures));
	}
}
