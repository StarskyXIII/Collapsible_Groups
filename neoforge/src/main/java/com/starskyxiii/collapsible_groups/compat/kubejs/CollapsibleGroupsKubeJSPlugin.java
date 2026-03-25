package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;

import java.util.function.Consumer;

/**
 * KubeJS plugin that exposes custom JEI ingredient types registered via
 * {@link com.starskyxiii.collapsible_groups.compat.jei.api.CGApi} to the KubeJS script layer.
 *
 * For each type registered in IngredientTypeRegistry, this plugin creates a
 * RecipeViewerEntryType with the same string ID and passes it to KubeJS.
 * Scripts can then use RecipeViewerEvents.groupEntries('mekanism:chemical', ...)
 * to group those ingredients.
 *
 * Note: The wrapPredicate, wrapEntry, and icon arguments are intentionally null ??filter logic for
 * these types is handled directly by {@link JEIGenericGroupEntriesKubeEvent}, bypassing KubeJS wrapping.
 */
public class CollapsibleGroupsKubeJSPlugin implements KubeJSPlugin {

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	public void registerRecipeViewerEntryTypes(Consumer<RecipeViewerEntryType> consumer) {
		IngredientTypeRegistry.getAllWithAliases().forEach((id, type) ->
			consumer.accept(new RecipeViewerEntryType(id, null, null, null))
		);
	}
}
