package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsFilterComposition;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupCollector;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupIds;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweringResult;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsMaterializationCapture;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
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
	private final String source;
	private final KubeJsMaterializationCapture capture;
	private final List<KubeJsLoweredGroup> collected = new ArrayList<>();

	public JEIGroupEntriesKubeEvent(List<ItemStack> allItems, String source, KubeJsMaterializationCapture capture) {
		this.allItems = allItems;
		this.source = source;
		this.capture = capture;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void group(Context cx, Object filter, ResourceLocation groupId, Component description) {
		String id = KubeJsGroupIds.item(groupId.toString());
		String name = description.getString();

		GroupFilter compiled = KubeJsFilterCompiler.compileItemFilter(cx, filter);
		if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.exact(compiled, source)));
			return;
		}

		if (!KubeJsFilterCompiler.isMaterializableItemIdSet(filter)) {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(
				"Use an item ID, item tag, exact ItemStack, or explicit CG item filter; functions, partial components, counts, and unknown ingredients cannot be lowered safely.", source)));
			return;
		}

		Predicate rawPredicate;
		try {
			rawPredicate = (Predicate) RecipeViewerEntryType.ITEM.wrapPredicate(cx, filter);
		} catch (RuntimeException exception) {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(
				"Could not evaluate this item ID-set filter: " + exception.getMessage(), source)));
			return;
		}
		LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
		try {
			for (ItemStack stack : allItems) {
				if (rawPredicate.test(stack)) {
					nodes.add(Filters.itemId(stack.getItemHolder().getKey().location().toString()));
				}
			}
		} catch (RuntimeException exception) {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(
				"Item ID-set filter failed while testing the viewer generation: " + exception.getMessage(), source)));
			return;
		}

		GroupFilter lowered = KubeJsFilterComposition.any(new ArrayList<>(nodes));
		if (lowered != null) {
			collected.add(new KubeJsLoweredGroup(id, name,
				KubeJsLoweringResult.materialized(lowered, source, capture)));
		} else {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(
				"This item ID-set filter matched no IDs in the current viewer generation.", source)));
		}
	}

	@Override
	public List<KubeJsLoweredGroup> collectedGroups() {
		return List.copyOf(collected);
	}
}
