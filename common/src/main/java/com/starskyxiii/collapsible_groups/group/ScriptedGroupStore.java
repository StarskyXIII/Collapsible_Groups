package com.starskyxiii.collapsible_groups.group;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Viewer-neutral store for ephemeral groups supplied by scripting integrations. */
public final class ScriptedGroupStore {
	private static volatile List<GroupDefinition> groups = List.of();
	private static volatile boolean applied;

	private ScriptedGroupStore() {}

	public static void publish(List<GroupDefinition> incoming) {
		groups = List.copyOf(incoming);
	}

	public static synchronized boolean update(String id, UnaryOperator<GroupDefinition> updater) {
		if (id == null || id.isBlank()) return false;
		List<GroupDefinition> updated = new ArrayList<>(groups.size());
		boolean changed = false;
		for (GroupDefinition group : groups) {
			if (id.equals(group.id())) {
				updated.add(updater.apply(group));
				changed = true;
			} else {
				updated.add(group);
			}
		}
		if (changed) groups = List.copyOf(updated);
		return changed;
	}

	public static List<GroupDefinition> groups() { return groups; }
	public static boolean isEmpty() { return groups.isEmpty(); }
	public static boolean isApplied() { return applied; }
	public static void markApplied() { applied = true; }

	/** Invalidates the published snapshot so the active viewer bootstrap recollects it. */
	public static void invalidate() {
		groups = List.of();
		applied = false;
	}

	/** Invalidates scripted data and neutrally asks the active viewer to rebuild it. */
	public static void invalidateAndNotify() {
		invalidate();
		GroupChangeEvent.publish(GroupChangeEvent.Kind.KUBEJS_REPLACE);
	}
}
