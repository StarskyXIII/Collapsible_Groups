package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsFilterComposition;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupCollector;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupIds;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.KubeJsItemFilterLowering;
import dev.latvian.mods.kubejs.recipe.viewer.GroupEntriesKubeEvent;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.rhino.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Collects KubeJS RecipeViewerEvents.groupEntries() calls and converts them
 * into GroupDefinition objects for use by our JEI mixin.
 *
 * Groups defined here are ephemeral (not saved to disk) and take lower
 * priority than user-configured JSON groups.
 */
public class JEIGroupEntriesKubeEvent implements GroupEntriesKubeEvent, KubeJsGroupCollector {

	private final List<ItemStack> allItems;
	private final List<KubeJsLoweredGroup> collected = new ArrayList<>();

	public JEIGroupEntriesKubeEvent(List<ItemStack> allItems) {
		this.allItems = allItems;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void group(Context cx, Object filter, ResourceLocation groupId, Component description) {
		String id = KubeJsGroupIds.item(groupId.toString());
		String name = description.getString();

		GroupFilter compiled = KubeJsFilterCompiler.compileItemFilter(cx, filter);
		if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
			collected.add(new KubeJsLoweredGroup(id, name, compiled));
			return;
		}

		Predicate rawPredicate = (Predicate) RecipeViewerEntryType.ITEM.wrapPredicate(cx, filter);
		LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
		for (ItemStack stack : allItems) {
			if (rawPredicate.test(stack)) {
				nodes.add(KubeJsItemFilterLowering.lowerResolvedStack(stack));
			}
		}

		GroupFilter lowered = KubeJsFilterComposition.any(new ArrayList<>(nodes));
		if (lowered != null) {
			collected.add(new KubeJsLoweredGroup(id, name, lowered));
		}
	}

	@Override
	public List<KubeJsLoweredGroup> collectedGroups() {
		return List.copyOf(collected);
	}
}
