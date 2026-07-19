package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsFilterComposition;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupCollector;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupIds;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import dev.latvian.mods.kubejs.recipe.viewer.GroupEntriesKubeEvent;
import dev.latvian.mods.kubejs.recipe.viewer.RecipeViewerEntryType;
import dev.latvian.mods.rhino.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Collects KubeJS RecipeViewerEvents.groupEntries() calls for FLUID type and
 * converts them into GroupDefinition objects for use by our JEI mixin.
 */
public class JEIFluidGroupEntriesKubeEvent implements GroupEntriesKubeEvent, KubeJsGroupCollector {

	private final List<FluidStack> allFluids;
	private final List<KubeJsLoweredGroup> collected = new ArrayList<>();

	public JEIFluidGroupEntriesKubeEvent(List<FluidStack> allFluids) {
		this.allFluids = allFluids;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void group(Context cx, Object filter, ResourceLocation groupId, Component description) {
		String id = KubeJsGroupIds.fluid(groupId.toString());
		String name = description.getString();

		GroupFilter compiled = KubeJsFilterCompiler.compileFluidFilter(cx, filter);
		if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
			collected.add(new KubeJsLoweredGroup(id, name, compiled));
			return;
		}

		Predicate rawPredicate = (Predicate) RecipeViewerEntryType.FLUID.wrapPredicate(cx, filter);
		LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
		for (FluidStack stack : allFluids) {
			if (rawPredicate.test(stack)) {
				nodes.add(KubeJsFilterLowering.lowerResolvedFluidStack(stack));
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
