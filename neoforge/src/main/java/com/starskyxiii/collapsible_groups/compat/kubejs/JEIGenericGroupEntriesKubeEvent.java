package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsFilterComposition;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupCollector;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsGroupIds;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweredGroup;
import com.starskyxiii.collapsible_groups.compat.kubejs.KubeJsLoweringResult;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import dev.latvian.mods.rhino.Context;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects KubeJS RecipeViewerEvents.groupEntries() calls for a generic
 * viewer ingredient type T (anything other than item and fluid).
 */
public class JEIGenericGroupEntriesKubeEvent<T> implements dev.latvian.mods.kubejs.recipe.viewer.GroupEntriesKubeEvent, KubeJsGroupCollector {

	private final String typeId;
	private final String source;
	private final List<KubeJsLoweredGroup> collected = new ArrayList<>();

	public JEIGenericGroupEntriesKubeEvent(String typeId, String source) {
		this.typeId = typeId;
		this.source = source;
	}

	@Override
	public void group(Context cx, Object filter, ResourceLocation groupId, Component description) {
		String id = KubeJsGroupIds.generic(typeId, groupId.toString());
		String name = description.getString();

		GroupFilter compiled = KubeJsFilterCompiler.compileGenericFilter(typeId, filter);
		if (compiled != null && KubeJsFilterComposition.supportsTree(compiled)) {
			collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.exact(compiled, source)));
			return;
		}

		collected.add(new KubeJsLoweredGroup(id, name, KubeJsLoweringResult.unsupported(
			"Use '@namespace', '#tag:id', an exact resource ID, or a string array for ingredient type '" + typeId + "'; functions and unknown filters cannot be lowered safely.", source)));
	}

	@Override
	public List<KubeJsLoweredGroup> collectedGroups() {
		return List.copyOf(collected);
	}
}
