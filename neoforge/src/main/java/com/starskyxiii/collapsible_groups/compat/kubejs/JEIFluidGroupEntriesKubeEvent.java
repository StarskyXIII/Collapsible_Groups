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
import net.minecraft.core.registries.BuiltInRegistries;
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
	private final String source;
	private final KubeJsMaterializationCapture capture;
	private final List<KubeJsLoweredGroup> collected = new ArrayList<>();

	public JEIFluidGroupEntriesKubeEvent(List<FluidStack> allFluids, String source, KubeJsMaterializationCapture capture) {
		this.allFluids = allFluids;
		this.source = source;
		this.capture = capture;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void group(Context cx, Object filter, ResourceLocation groupId, Component description) {
		String id = KubeJsGroupIds.fluid(groupId.toString());
		String name = description.getString();

		GroupFilter compiled = KubeJsFilterCompiler.compileFluidFilter(cx, filter);
		if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.exact(compiled, source)));
			return;
		}

		if (!KubeJsFilterCompiler.isMaterializableFluidIdSet(filter)) {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(
				"Use a fluid ID string, fluid tag, or CG fluidId filter; FluidStack amount/components, functions, and unknown ingredients cannot be lowered safely.", source)));
			return;
		}

		Predicate rawPredicate;
		try {
			rawPredicate = (Predicate) RecipeViewerEntryType.FLUID.wrapPredicate(cx, filter);
		} catch (RuntimeException exception) {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(
				"Could not evaluate this fluid ID-set filter: " + exception.getMessage(), source)));
			return;
		}
		LinkedHashSet<GroupFilter> nodes = new LinkedHashSet<>();
		try {
			for (FluidStack stack : allFluids) {
				if (rawPredicate.test(stack)) {
					nodes.add(Filters.fluidId(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString()));
				}
			}
		} catch (RuntimeException exception) {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(
				"Fluid ID-set filter failed while testing the viewer generation: " + exception.getMessage(), source)));
			return;
		}

		GroupFilter lowered = KubeJsFilterComposition.any(new ArrayList<>(nodes));
		if (lowered != null) {
			collected.add(new KubeJsLoweredGroup(id, name,
				KubeJsLoweringResult.materialized(lowered, source, capture)));
		} else {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(
				"This fluid ID-set filter matched no IDs in the current viewer generation.", source)));
		}
	}

	@Override
	public List<KubeJsLoweredGroup> collectedGroups() {
		return List.copyOf(collected);
	}
}
