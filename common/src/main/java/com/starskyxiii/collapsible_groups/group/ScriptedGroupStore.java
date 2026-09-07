package com.starskyxiii.collapsible_groups.group;

import java.util.List;
import java.util.function.UnaryOperator;

/** Compatibility facade for the legacy singleton scripting source. */
public final class ScriptedGroupStore {
	private ScriptedGroupStore() {}

	public static void publish(List<GroupDefinition> incoming) {
		GroupRepository.setLegacyScriptedGroupsQuietly(incoming);
	}

	public static boolean update(String id, UnaryOperator<GroupDefinition> updater) {
		return GroupRepository.updateScriptedSource("legacy", id, updater);
	}

	public static List<GroupDefinition> groups() { return GroupRepository.scriptedSourceGroups("legacy"); }
	public static boolean isEmpty() { return groups().isEmpty(); }
	public static boolean isApplied() { return GroupRepository.areScriptedGroupsApplied(); }
	public static void markApplied() { GroupRepository.markScriptedGroupsApplied(); }

	/** Invalidates the published snapshot so the active viewer bootstrap recollects it. */
	public static void invalidate() {
		GroupRepository.clearLegacyScriptedGroupsQuietly();
	}

	/** Invalidates scripted data and neutrally asks the active viewer to rebuild it. */
	public static void invalidateAndNotify() {
		GroupRepository.clearLegacyScriptedGroupsAndNotify();
	}
}
