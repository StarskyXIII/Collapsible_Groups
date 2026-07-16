package com.starskyxiii.collapsible_groups.persistence;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import com.starskyxiii.collapsible_groups.group.GroupCatalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistence boundary for group definitions, enabled overrides, and expand state. */
public final class GroupStore {
	public List<GroupDefinition> loadGroups(List<DefaultGroupProvider> providers) {
		Map<String, GroupDefinition> merged = new LinkedHashMap<>();
		for (DefaultGroupProvider provider : providers) {
			for (GroupDefinition group : provider.getGroups()) merged.put(group.id(), group);
		}
		for (GroupDefinition group : GroupConfig.load()) {
			if (!group.id().startsWith("__default_")) merged.put(group.id(), group);
		}
		return GroupCatalog.applyEnabledOverrides(List.copyOf(merged.values()), loadEnabledOverrides());
	}

	public void loadExpandState() {
		GroupExpandState.load(GroupConfig.loadExpandState());
	}

	public Map<String, Boolean> loadEnabledOverrides() {
		return GroupConfig.loadEnabledOverrides();
	}

	public void saveEnabledOverride(String id, boolean enabled) {
		Map<String, Boolean> overrides = new LinkedHashMap<>(loadEnabledOverrides());
		overrides.put(id, enabled);
		GroupConfig.saveEnabledOverrides(overrides);
	}

	public void save(GroupDefinition group) {
		GroupConfig.save(group);
	}

	public void delete(String id) {
		GroupConfig.delete(id);
		GroupExpandState.remove(id);
	}
}
