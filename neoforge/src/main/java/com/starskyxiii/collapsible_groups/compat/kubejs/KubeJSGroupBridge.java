package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsFilterComposition;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupCollector;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupConsumer;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupIds;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.KubeJsItemFilterLowering;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapContext;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapEntries;
import dev.latvian.mods.kubejs.plugin.builtin.event.RecipeViewerEvents;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.kubejs.recipe.viewer.server.FluidData;
import dev.latvian.mods.kubejs.recipe.viewer.server.ItemData;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Bridges KubeJS RecipeViewerEvents.groupEntries() into the active viewer's group system.
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

	public static void applyGroupsNeutral(ViewerBootstrapContext<?> bootstrap) {
		applyGroups(bootstrap);
	}

	/**
	 * @param bootstrap early viewer context supplied while the ingredient filter is being built,
	 *                  before the normal viewer runtime is available
	 */
	public static void applyGroups(ViewerBootstrapContext<?> bootstrap) {
		List<GroupDefinition> allGroups = new ArrayList<>();
		List<ItemStack> allItems = ViewerBootstrapEntries.itemStacks(bootstrap);
		List<FluidStack> allFluids = ViewerBootstrapEntries.resourceIds(bootstrap, ViewerIngredient.Kind.FLUID).stream()
			.map(BuiltInRegistries.FLUID::get)
			.filter(java.util.Objects::nonNull)
			.map(fluid -> new FluidStack(fluid, 1000))
			.toList();

		// Client-script item groups
		if (RecipeViewerEvents.GROUP_ENTRIES.hasListeners(RecipeViewerEntryType.ITEM)) {
			var event = new JEIGroupEntriesKubeEvent(allItems);
			RecipeViewerEvents.GROUP_ENTRIES.post(ScriptType.CLIENT, RecipeViewerEntryType.ITEM, event);
			addCollected(event, allGroups);
		}

		// Client-script fluid groups
		if (RecipeViewerEvents.GROUP_ENTRIES.hasListeners(RecipeViewerEntryType.FLUID)) {
			var event = new JEIFluidGroupEntriesKubeEvent(allFluids);
			RecipeViewerEvents.GROUP_ENTRIES.post(ScriptType.CLIENT, RecipeViewerEntryType.FLUID, event);
			addCollected(event, allGroups);
		}

		// Client-script generic groups (custom ingredient types).
		// Preserve canonical-then-alias registration order while resolving from the early context.
		for (String typeId : IngredientTypeIds.getAllIds().keySet()) {
			ViewerIngredientType<?> type = bootstrap.resolveType(typeId).orElse(null);
			if (type != null) applyGenericType(typeId, type.ingredients(), allGroups);
		}

		// Server-remote groups (from RemoteRecipeViewerDataUpdatedEvent)
		applyRemoteGroups(allItems, allFluids, allGroups);

		KubeJsGroupConsumer consumer = GroupRepository::setScriptedGroups;
		consumer.replace(allGroups);
	}

	private static void applyGenericType(
		String typeId,
		List<? extends ViewerIngredient<?>> ingredients,
		List<GroupDefinition> out
	) {
		RecipeViewerEntryType entryType = RecipeViewerEntryType.fromString(typeId);
		if (entryType == null || !RecipeViewerEvents.GROUP_ENTRIES.hasListeners(entryType)) return;

		if (ingredients.isEmpty()) return;

		JEIGenericGroupEntriesKubeEvent<Object> event = new JEIGenericGroupEntriesKubeEvent<>(typeId, ingredients);
		RecipeViewerEvents.GROUP_ENTRIES.post(ScriptType.CLIENT, entryType, event);
		addCollected(event, out);
	}

	private static void addCollected(KubeJsGroupCollector collector, List<GroupDefinition> out) {
		collector.collectedGroups().stream().map(KubeJSGroupBridge::toDefinition).forEach(out::add);
	}

	private static GroupDefinition toDefinition(KubeJsLoweredGroup group) {
		return new GroupDefinition(group.id(), group.name(), true, group.filter());
	}

	private static void applyRemoteGroups(List<ItemStack> allItems, List<FluidStack> allFluids, List<GroupDefinition> out) {
		// Remote item groups
		for (ItemData.Group group : KubeJSRemoteListener.getPendingItemGroups()) {
			String id = KubeJsGroupIds.remoteItem(group.groupId().toString());
			String name = group.description().getString();

			GroupFilter compiled = KubeJsFilterCompiler.compileItemFilter(group.filter());
			if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
				out.add(new GroupDefinition(id, name, true, compiled));
				continue;
			}

			LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
			for (ItemStack stack : allItems) {
				if (!group.filter().test(stack)) continue;
				nodes.add(KubeJsItemFilterLowering.lowerResolvedStack(stack));
			}

			GroupFilter lowered = KubeJsFilterComposition.any(new ArrayList<>(nodes));
			if (lowered != null) {
				out.add(new GroupDefinition(id, name, true, lowered));
			}
		}

		// Remote fluid groups
		for (FluidData.Group group : KubeJSRemoteListener.getPendingFluidGroups()) {
			String id = KubeJsGroupIds.remoteFluid(group.groupId().toString());
			String name = group.description().getString();

			GroupFilter compiled = KubeJsFilterCompiler.compileFluidFilter(group.filter());
			if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
				out.add(new GroupDefinition(id, name, true, compiled));
				continue;
			}

			LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
			for (FluidStack stack : allFluids) {
				if (group.filter().test(stack)) {
					nodes.add(KubeJsFilterLowering.lowerResolvedFluidStack(stack));
				}
			}

			GroupFilter lowered = KubeJsFilterComposition.any(new ArrayList<>(nodes));
			if (lowered != null) {
				out.add(new GroupDefinition(id, name, true, lowered));
			}
		}
	}
}
