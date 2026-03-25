package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
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
public class JEIFluidGroupEntriesKubeEvent implements GroupEntriesKubeEvent {

	private final List<FluidStack> allFluids;
	private final List<GroupDefinition> collected = new ArrayList<>();

	public JEIFluidGroupEntriesKubeEvent(List<FluidStack> allFluids) {
		this.allFluids = allFluids;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void group(Context cx, Object filter, ResourceLocation groupId, Component description) {
		String id = "__kjs_fluid_" + groupId.toString().replace(':', '_').replace('/', '_');
		String name = description.getString();

		GroupFilter compiled = KubeJsFilterCompiler.compileFluidFilter(cx, filter);
		if (compiled != null) {
			collected.add(new GroupDefinition(id, name, true, compiled));
			return;
		}

		Predicate rawPredicate = (Predicate) RecipeViewerEntryType.FLUID.wrapPredicate(cx, filter);
		LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
		for (FluidStack stack : allFluids) {
			if (rawPredicate.test(stack)) {
				nodes.add(KubeJsFilterLowering.lowerResolvedFluidStack(stack));
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
