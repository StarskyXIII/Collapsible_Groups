package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;

import java.util.List;

/**
 * Holds all ephemeral KubeJS group data for the current JEI session.
 * All group types (item, fluid, generic) are stored as {@link GroupDefinition}.
 * These groups are populated at JEI load time via the platform-specific KubeJS bridge
 * and are never written to disk.
 *
 */
final class KubeJsGroupStore {

	private KubeJsGroupStore() {}

	private static volatile List<GroupDefinition> groups = List.of();
	private static volatile boolean applied = false;

	static void setGroups(List<GroupDefinition> incoming) {
		groups = List.copyOf(incoming);
	}

	static List<GroupDefinition> getGroups() {
		return groups;
	}

	static boolean isGroupsEmpty() {
		return groups.isEmpty();
	}

	static boolean isApplied() {
		return applied;
	}

	static void markApplied() {
		applied = true;
	}

	static void clearAll() {
		groups  = List.of();
		applied = false;
	}
}
