package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.ScriptedGroupStore;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Holds all ephemeral KubeJS group data for the current JEI session.
 * All group types (item, fluid, generic) are stored as {@link GroupDefinition}.
 * These groups are populated at JEI load time via the platform-specific KubeJS bridge
 * and are never written to disk.
 *
 */
final class KubeJsGroupStore {

	private KubeJsGroupStore() {}

	static void setGroups(List<GroupDefinition> incoming) {
		ScriptedGroupStore.publish(incoming);
	}

	static boolean updateGroup(String id, UnaryOperator<GroupDefinition> updater) {
		return ScriptedGroupStore.update(id, updater);
	}

	static List<GroupDefinition> getGroups() {
		return ScriptedGroupStore.groups();
	}

	static boolean isGroupsEmpty() {
		return ScriptedGroupStore.isEmpty();
	}

	static boolean isApplied() {
		return ScriptedGroupStore.isApplied();
	}

	static void markApplied() {
		ScriptedGroupStore.markApplied();
	}

	static void clearAll() {
		ScriptedGroupStore.invalidate();
	}
}
