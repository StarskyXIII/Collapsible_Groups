package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;

import java.util.ArrayList;
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

	private static volatile List<GroupDefinition> groups = List.of();
	private static volatile boolean applied = false;

	static void setGroups(List<GroupDefinition> incoming) {
		groups = List.copyOf(incoming);
	}

	static boolean updateGroup(String id, UnaryOperator<GroupDefinition> updater) {
		if (id == null || id.isBlank()) return false;
		List<GroupDefinition> snapshot = groups;
		List<GroupDefinition> updated = new ArrayList<>(snapshot.size());
		boolean changed = false;
		for (GroupDefinition group : snapshot) {
			if (id.equals(group.id())) {
				GroupDefinition next = updater.apply(group);
				updated.add(next);
				changed = true;
			} else {
				updated.add(group);
			}
		}
		if (!changed) return false;
		groups = List.copyOf(updated);
		return true;
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
