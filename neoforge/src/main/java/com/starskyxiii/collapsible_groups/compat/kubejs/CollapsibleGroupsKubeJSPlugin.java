package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.fml.ModList;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * KubeJS plugin that exposes viewer-neutral custom ingredient type IDs to the script layer.
 *
 * <p>KubeJS freezes custom recipe-viewer types in a one-shot lazy map. Known optional-mod IDs are
 * therefore supplied even before a viewer universe exists, while types discovered before this
 * callback are appended from {@link IngredientTypeIds}.
 * Scripts can then use RecipeViewerEvents.groupEntries('mekanism:chemical', ...)
 * to group those ingredients.
 */
public class CollapsibleGroupsKubeJSPlugin implements KubeJSPlugin {
	@Override
	public void registerEvents(EventGroupRegistry registry) {
		if (!KubeJSCompatibility.isSupported()) return;
		registry.register(CGEvents.GROUP);
	}

	@Override
	public void afterScriptsLoaded(ScriptManager manager) {
		if (!KubeJSCompatibility.isSupported()) return;
		if (manager.scriptType == ScriptType.CLIENT) {
			com.starskyxiii.collapsible_groups.group.ScriptedGroupStore.invalidateAndNotify();
		}
	}

	@Override
	public void registerRecipeViewerEntryTypes(Consumer<RecipeViewerEntryType> consumer) {
		if (!KubeJSCompatibility.isSupported()) return;
		registerRecipeViewerEntryTypes(consumer,
			modId -> ModList.get().isLoaded(modId), IngredientTypeIds.getAllIds().keySet());
	}

	static void registerRecipeViewerEntryTypes(Consumer<RecipeViewerEntryType> consumer,
		Predicate<String> modLoaded, Collection<String> discoveredIds) {
		KnownRecipeViewerTypeIds.collect(modLoaded, discoveredIds)
			.forEach(id -> consumer.accept(new GenericRecipeViewerEntryType(id)));
	}
}
