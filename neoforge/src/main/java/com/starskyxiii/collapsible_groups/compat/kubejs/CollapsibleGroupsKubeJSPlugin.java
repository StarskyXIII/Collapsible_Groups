package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import net.neoforged.fml.ModList;

import java.util.function.Consumer;

/**
 * KubeJS plugin that exposes viewer-neutral custom ingredient type IDs to the script layer.
 *
 * <p>KubeJS freezes custom recipe-viewer types in a one-shot lazy map. Known optional-mod IDs are
 * therefore supplied even before a viewer universe exists, while types discovered before this
 * callback are appended from {@link IngredientTypeIds}.
 * Scripts can then use RecipeViewerEvents.groupEntries('mekanism:chemical', ...)
 * to group those ingredients.
 *
 * <p>The entry, predicate, and base components are intentionally null: filter logic for these
 * types is handled directly by the legacy-named {@link JEIGenericGroupEntriesKubeEvent}, bypassing
 * KubeJS wrapping.
 */
public class CollapsibleGroupsKubeJSPlugin implements KubeJSPlugin {

	@Override
	public void registerRecipeViewerEntryTypes(Consumer<RecipeViewerEntryType> consumer) {
		KnownRecipeViewerTypeIds.collect(
			modId -> ModList.get().isLoaded(modId), IngredientTypeIds.getAllIds().keySet()
		).forEach(id -> consumer.accept(new RecipeViewerEntryType(id, null, null, null)));
	}
}
