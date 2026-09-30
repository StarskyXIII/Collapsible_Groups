package com.starskyxiii.collapsible_groups.group;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Compatibility facade for the legacy singleton scripting source. */
public final class ScriptedGroupStore {
	private ScriptedGroupStore() {}

	public static void publish(List<GroupDefinition> incoming) {
		GroupRepository.setLegacyScriptedGroupsQuietly(incoming);
	}

	public static long beginPublication(String ownerId) {
		return GroupRepository.beginScriptedPublication(ownerId);
	}

	public static long nextMaterializationCapture() {
		return GroupRepository.nextMaterializationCapture();
	}

	public static boolean publishSources(String ownerId, long generation,
		Map<String, List<GroupDefinition>> replacements) {
		return GroupRepository.replaceScriptedOwner(ownerId, generation, replacements);
	}

	public static long publicationCheckpoint() {
		return GroupRepository.scriptedPublicationCheckpoint();
	}

	public static boolean markAppliedAfter(long checkpoint) {
		return GroupRepository.markScriptedAppliedAfter(checkpoint);
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
		GroupRepository.clearScriptedGroupsQuietly();
	}

	/** Invalidates scripted data and neutrally asks the active viewer to rebuild it. */
	public static void invalidateAndNotify() {
		GroupRepository.clearScriptedGroupsAndNotify();
	}
}
