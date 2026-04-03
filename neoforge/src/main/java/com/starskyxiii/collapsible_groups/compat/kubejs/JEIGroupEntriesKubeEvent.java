package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.KubeJsItemFilterLowering;
import dev.latvian.mods.kubejs.recipe.viewer.GroupEntriesKubeEvent;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.rhino.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
public class JEIGroupEntriesKubeEvent implements GroupEntriesKubeEvent {

	private final List<ItemStack> allItems;
	private final List<GroupDefinition> collected = new ArrayList<>();

	public JEIGroupEntriesKubeEvent(List<ItemStack> allItems) {
		this.allItems = allItems;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void group(Context cx, Object filter, Identifier groupId, Component description) {
		String id = "__kjs_" + groupId.toString().replace(':', '_').replace('/', '_');
		String name = description.getString();

		GroupFilter compiled = KubeJsFilterCompiler.compileItemFilter(cx, filter);
		if (compiled != null) {
			collected.add(new GroupDefinition(id, name, true, compiled));
			return;
		}

		Predicate rawPredicate = (Predicate) RecipeViewerEntryType.ITEM.wrapPredicate(cx, filter);
		LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
		for (ItemStack stack : allItems) {
			if (rawPredicate.test(stack)) {
				nodes.add(KubeJsItemFilterLowering.lowerResolvedStack(stack));
			}
		}

		GroupFilter lowered = KubeJsFilterLowering.composeFallbackNodes(new ArrayList<>(nodes));
		if (lowered != null) {
			collected.add(new GroupDefinition(id, name, true, lowered));
		}
	}

	public List<GroupDefinition> getCollected() {
		return List.copyOf(collected);
	}
}
