package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.KubeJsItemFilterLowering;
import dev.latvian.mods.kubejs.plugin.builtin.event.RecipeViewerEvents;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.kubejs.recipe.viewer.server.FluidData;
import dev.latvian.mods.kubejs.recipe.viewer.server.ItemData;
import dev.latvian.mods.kubejs.script.ScriptType;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Bridges KubeJS RecipeViewerEvents.groupEntries() into our JEI group system.
 * All group types (item, fluid, generic) are unified as {@link GroupDefinition}.
 *
 * Handles both client-script groups (fired via the Rhino JS engine) and
 * server-side remote groups (received via RemoteRecipeViewerDataUpdatedEvent
 * and stored in KubeJSRemoteListener).
 *
 * This class directly references KubeJS types, so it must only be loaded
 * (i.e. called) when KubeJS is present ??guarded by a ModList check at the
 * call site in MixinIngredientFilter.
 */
public final class KubeJSGroupBridge {

	private KubeJSGroupBridge() {}

	/**
	 * @param ingredientManager must be obtained from IngredientFilter's own field (via @Shadow),
	 *                          NOT from JeiRuntimeHolder. This method is called during JEI's
	 *                          "Building ingredient filter" phase, before IJeiRuntime is
	 *                          constructed and JeiRuntimeHolder is populated (~150 ms earlier).
	 *                          Using JeiRuntimeHolder here would always return isAvailable()=false
	 *                          and silently skip all custom ingredient type groups.
	 */
	public static void applyGroups(List<ItemStack> allItems, List<FluidStack> allFluids, IIngredientManager ingredientManager) {
		List<GroupDefinition> allGroups = new ArrayList<>();

		// Client-script item groups
		if (RecipeViewerEvents.GROUP_ENTRIES.hasListeners(RecipeViewerEntryType.ITEM)) {
			var event = new JEIGroupEntriesKubeEvent(allItems);
			RecipeViewerEvents.GROUP_ENTRIES.post(ScriptType.CLIENT, RecipeViewerEntryType.ITEM, event);
			allGroups.addAll(event.getCollected());
		}

		// Client-script fluid groups
		if (RecipeViewerEvents.GROUP_ENTRIES.hasListeners(RecipeViewerEntryType.FLUID)) {
			var event = new JEIFluidGroupEntriesKubeEvent(allFluids);
			RecipeViewerEvents.GROUP_ENTRIES.post(ScriptType.CLIENT, RecipeViewerEntryType.FLUID, event);
			allGroups.addAll(event.getCollected());
		}

		// Client-script generic groups (custom ingredient types).
		// Iterate getAllWithAliases() so both canonical IDs and short aliases
		// (e.g. "chemical" alongside "mekanism:chemical") fire their KubeJS events.
		for (Map.Entry<String, IIngredientType<?>> entry : IngredientTypeRegistry.getAllWithAliases().entrySet()) {
			applyGenericType(entry.getKey(), entry.getValue(), ingredientManager, allGroups);
		}

		// Server-remote groups (from RemoteRecipeViewerDataUpdatedEvent)
		applyRemoteGroups(allItems, allFluids, allGroups);

		GroupRegistry.setKubeJsGroups(allGroups);
	}

	private static <T> void applyGenericType(
		String typeId,
		IIngredientType<T> type,
		IIngredientManager ingredientManager,
		List<GroupDefinition> out
	) {
		RecipeViewerEntryType entryType = RecipeViewerEntryType.fromString(typeId);
		if (entryType == null || !RecipeViewerEvents.GROUP_ENTRIES.hasListeners(entryType)) return;

		List<T> allIngredients = new ArrayList<>(ingredientManager.getAllIngredients(type));
		if (allIngredients.isEmpty()) return;

		IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(type);
		JEIGenericGroupEntriesKubeEvent<T> event = new JEIGenericGroupEntriesKubeEvent<>(typeId, type, allIngredients, helper);
		RecipeViewerEvents.GROUP_ENTRIES.post(ScriptType.CLIENT, entryType, event);
		out.addAll(event.getCollected());
	}

	private static void applyRemoteGroups(List<ItemStack> allItems, List<FluidStack> allFluids, List<GroupDefinition> out) {
		// Remote item groups
		for (ItemData.Group group : KubeJSRemoteListener.getPendingItemGroups()) {
			String id = "__kjs_remote_" + group.groupId().toString().replace(':', '_').replace('/', '_');
			String name = group.description().getString();

			GroupFilter compiled = KubeJsFilterCompiler.compileItemFilter(group.filter());
			if (compiled != null) {
				out.add(new GroupDefinition(id, name, true, compiled));
				continue;
			}

			LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
			for (ItemStack stack : allItems) {
				if (!group.filter().test(stack)) continue;
				nodes.add(KubeJsItemFilterLowering.lowerResolvedStack(stack));
			}

			GroupFilter lowered = KubeJsFilterLowering.composeFallbackNodes(new ArrayList<>(nodes));
			if (lowered != null) {
				out.add(new GroupDefinition(id, name, true, lowered));
			}
		}

		// Remote fluid groups
		for (FluidData.Group group : KubeJSRemoteListener.getPendingFluidGroups()) {
			String id = "__kjs_remote_fluid_" + group.groupId().toString().replace(':', '_').replace('/', '_');
			String name = group.description().getString();

			GroupFilter compiled = KubeJsFilterCompiler.compileFluidFilter(group.filter());
			if (compiled != null) {
				out.add(new GroupDefinition(id, name, true, compiled));
				continue;
			}

			LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
			for (FluidStack stack : allFluids) {
				if (group.filter().test(stack)) {
					nodes.add(KubeJsFilterLowering.lowerResolvedFluidStack(stack));
				}
			}

			GroupFilter lowered = KubeJsFilterLowering.composeFallbackNodes(new ArrayList<>(nodes));
			if (lowered != null) {
				out.add(new GroupDefinition(id, name, true, lowered));
			}
		}
	}
}
