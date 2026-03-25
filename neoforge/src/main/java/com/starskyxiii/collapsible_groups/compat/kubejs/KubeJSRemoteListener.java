package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import dev.latvian.mods.kubejs.recipe.viewer.server.FluidData;
import dev.latvian.mods.kubejs.recipe.viewer.server.ItemData;
import dev.latvian.mods.kubejs.recipe.viewer.server.RecipeViewerData;
import dev.latvian.mods.kubejs.recipe.viewer.server.RemoteRecipeViewerDataUpdatedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;

/**
 * NeoForge game-event-bus listener for KubeJS server-side recipe viewer data.
 *
 * When the server sends remote group definitions, this class stores them so
 * KubeJSGroupBridge can incorporate them the next time JEI rebuilds its
 * ingredient list.
 *
 * Register via {@code NeoForge.EVENT_BUS.register(KubeJSRemoteListener.class)}
 * when KubeJS is present (see CollapsibleGroups).
 *
 * This class directly references KubeJS types, so it must only be loaded
 * when KubeJS is present ??guarded by a ModList check at the call site.
 */
public final class KubeJSRemoteListener {

	private static volatile List<ItemData.Group> pendingItemGroups = List.of();
	private static volatile List<FluidData.Group> pendingFluidGroups = List.of();

	private KubeJSRemoteListener() {}

	@SubscribeEvent
	public static void onRemoteData(RemoteRecipeViewerDataUpdatedEvent event) {
		RecipeViewerData data = event.data;
		if (data == null) {
			pendingItemGroups = List.of();
			pendingFluidGroups = List.of();
		} else {
			pendingItemGroups = List.copyOf(data.itemData().groupedEntries());
			pendingFluidGroups = List.copyOf(data.fluidData().groupedEntries());
		}

		// Force KubeJS groups to be re-collected on the next JEI render pass
		// so that the new remote group definitions are incorporated.
		GroupRegistry.clearKubeJsGroups();
		GroupRegistry.notifyJei();
	}

	public static List<ItemData.Group> getPendingItemGroups() {
		return pendingItemGroups;
	}

	public static List<FluidData.Group> getPendingFluidGroups() {
		return pendingFluidGroups;
	}
}
