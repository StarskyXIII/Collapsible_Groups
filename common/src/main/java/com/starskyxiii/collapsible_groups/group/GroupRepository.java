package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import com.starskyxiii.collapsible_groups.persistence.GroupExpandState;
import com.starskyxiii.collapsible_groups.persistence.GroupStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Viewer-neutral owner of persisted, provider, and scripted group state.
 *
 * <p>This class must remain loadable when neither JEI nor EMI is present. Viewer adapters react
 * to the published {@link GroupChangeEvent}; the repository never reaches into viewer caches.
 */
public final class GroupRepository {
	private static final GroupStore STORE = new GroupStore();
	private static final GroupService SERVICE = new GroupService();
	private static final GroupService.SourceKey USER_SOURCE =
		new GroupService.SourceKey(GroupSource.USER, "persisted");
	private static final GroupService.SourceKey BUILTIN_SOURCE =
		new GroupService.SourceKey(GroupSource.BUILTIN, "providers");
	private static final GroupService.SourceKey LEGACY_SCRIPT_SOURCE =
		new GroupService.SourceKey(GroupSource.KUBEJS, "legacy");
	private static final Map<String, PublicationAttempt> SCRIPTED_PUBLICATIONS = new LinkedHashMap<>();
	private static long scriptedGeneration;
	private static long publicationActivity;
	private static long lastRejectedPublicationActivity;
	private static long materializationCapture;
	private enum PublicationState { PENDING, ACCEPTED, REJECTED }
	private record PublicationAttempt(long generation, PublicationState state) {}

	private GroupRepository() {}

	public static void load(List<DefaultGroupProvider> providers) {
		List<GroupDefinition> loaded = STORE.loadGroups(providers);
		Map<GroupService.SourceKey, List<GroupDefinition>> replacements = new LinkedHashMap<>();
		replacements.put(BUILTIN_SOURCE, loaded.stream()
			.filter(group -> GroupSource.fromGroupId(group.id()) == GroupSource.BUILTIN).toList());
		replacements.put(USER_SOURCE, loaded.stream()
			.filter(group -> GroupSource.fromGroupId(group.id()) != GroupSource.BUILTIN).toList());
		SERVICE.replaceSources(replacements, java.util.Set.of());
		STORE.loadExpandState();
		List<GroupDefinition> groups = SERVICE.managedRegistrationOrder();
		long itemGroups = groups.stream().filter(GroupDefinition::hasItemFilters).count();
		long fluidGroups = groups.stream().filter(GroupDefinition::hasFluidFilters).count();
		long genericGroups = groups.stream().filter(GroupDefinition::hasGenericFilters).count();
		Constants.LOG.info("[CollapsibleGroups] Loaded {} groups (item={}, fluid={}, generic={})",
			groups.size(), itemGroups, fluidGroups, genericGroups);
	}

	public static boolean isBuiltin(String id) {
		return id != null && id.startsWith("__default_");
	}

	public static List<GroupDefinition> getAll() {
		return SERVICE.managedPriorityOrder();
	}

	/** Persisted/provider groups win over scripted groups on an ID collision. */
	public static Optional<GroupDefinition> findById(String id) {
		if (id == null || id.isBlank()) return Optional.empty();
		return SERVICE.findById(id);
	}

	public static List<GroupDefinition> getAllIncludingScripted() {
		return SERVICE.allPriorityOrder();
	}

	public static synchronized void setScriptedGroups(List<GroupDefinition> incoming) {
		replaceScriptedSource(LEGACY_SCRIPT_SOURCE.producerId(), incoming, true);
	}

	static synchronized void replaceScriptedSource(String producerId, List<GroupDefinition> incoming,
		boolean notify) {
		GroupService.SourceKey key = new GroupService.SourceKey(GroupSource.KUBEJS, producerId);
		Map<String, Boolean> overrides = STORE.loadEnabledOverrides();
		List<GroupDefinition> effective = incoming.stream()
			.map(group -> {
				Boolean enabled = overrides.get(group.id());
				return enabled != null && group.enabled() != enabled ? group.withEnabled(enabled) : group;
			})
			.toList();
		SERVICE.replaceSource(key, effective);
		if (notify) publish(GroupChangeEvent.Kind.KUBEJS_REPLACE);
	}

	static synchronized long beginScriptedPublication(String ownerId) {
		validatePublicationPart(ownerId, "ownerId");
		long generation = ++scriptedGeneration;
		++publicationActivity;
		SCRIPTED_PUBLICATIONS.put(ownerId, new PublicationAttempt(generation, PublicationState.PENDING));
		return generation;
	}

	static synchronized long nextMaterializationCapture() {
		return ++materializationCapture;
	}

	static synchronized boolean replaceScriptedOwner(String ownerId, long generation,
		Map<String, List<GroupDefinition>> incoming) {
		validatePublicationPart(ownerId, "ownerId");
		if (incoming == null) throw new NullPointerException("incoming");
		PublicationAttempt attempt = SCRIPTED_PUBLICATIONS.get(ownerId);
		if (attempt == null || attempt.generation() != generation) {
			lastRejectedPublicationActivity = ++publicationActivity;
			return false;
		}
		String ownerPrefix = scriptedOwnerPrefix(ownerId);
		Map<String, Boolean> overrides = STORE.loadEnabledOverrides();
		Map<GroupService.SourceKey, List<GroupDefinition>> replacements = new LinkedHashMap<>();
		for (Map.Entry<String, List<GroupDefinition>> entry : incoming.entrySet()) {
			validatePublicationPart(entry.getKey(), "sourceId");
			GroupService.SourceKey key = new GroupService.SourceKey(GroupSource.KUBEJS,
				ownerPrefix + entry.getKey());
			List<GroupDefinition> effective = entry.getValue().stream().map(group -> {
				Boolean enabled = overrides.get(group.id());
				return enabled != null && group.enabled() != enabled ? group.withEnabled(enabled) : group;
			}).toList();
			replacements.put(key, effective);
		}
		java.util.Set<GroupService.SourceKey> removals = SERVICE.categorySources(GroupSource.KUBEJS).stream()
			.filter(key -> key.producerId().startsWith(ownerPrefix))
			.filter(key -> !replacements.containsKey(key))
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
		try {
			SERVICE.replaceSources(replacements, removals);
			SCRIPTED_PUBLICATIONS.put(ownerId,
				new PublicationAttempt(generation, PublicationState.ACCEPTED));
			++publicationActivity;
			return true;
		} catch (RuntimeException failure) {
			SCRIPTED_PUBLICATIONS.put(ownerId,
				new PublicationAttempt(generation, PublicationState.REJECTED));
			lastRejectedPublicationActivity = ++publicationActivity;
			throw failure;
		}
	}

	static synchronized long scriptedPublicationCheckpoint() {
		return publicationActivity;
	}

	static synchronized boolean markScriptedAppliedAfter(long checkpoint) {
		boolean complete = lastRejectedPublicationActivity <= checkpoint
			&& SCRIPTED_PUBLICATIONS.values().stream()
				.allMatch(attempt -> attempt.state() == PublicationState.ACCEPTED);
		if (complete) SERVICE.markApplied(LEGACY_SCRIPT_SOURCE);
		return complete;
	}

	static synchronized void removeScriptedSource(String producerId, boolean notify) {
		SERVICE.removeSource(new GroupService.SourceKey(GroupSource.KUBEJS, producerId));
		if (notify) publish(GroupChangeEvent.Kind.KUBEJS_REPLACE);
	}

	static List<GroupDefinition> scriptedSourceGroups(String producerId) {
		return SERVICE.sourceGroups(new GroupService.SourceKey(GroupSource.KUBEJS, producerId));
	}

	static void markScriptedSourceApplied(String producerId) {
		SERVICE.markApplied(new GroupService.SourceKey(GroupSource.KUBEJS, producerId));
	}

	static boolean isScriptedSourceApplied(String producerId) {
		return SERVICE.isApplied(new GroupService.SourceKey(GroupSource.KUBEJS, producerId));
	}

	static boolean updateScriptedSource(String producerId, String id,
		java.util.function.UnaryOperator<GroupDefinition> updater) {
		return SERVICE.update(new GroupService.SourceKey(GroupSource.KUBEJS, producerId), id, updater);
	}

	static List<GroupDefinition> scriptedGroups() {
		return SERVICE.categoryGroups(GroupSource.KUBEJS);
	}

	public static boolean areScriptedGroupsEmpty() { return SERVICE.categoryGroups(GroupSource.KUBEJS).isEmpty(); }

	public static synchronized void clearScriptedGroups() {
		clearScriptedGroupsQuietly();
	}

	public static boolean areScriptedGroupsApplied() { return SERVICE.isApplied(LEGACY_SCRIPT_SOURCE); }
	public static void markScriptedGroupsApplied() { SERVICE.markApplied(LEGACY_SCRIPT_SOURCE); }

	static void setLegacyScriptedGroupsQuietly(List<GroupDefinition> incoming) {
		replaceScriptedSource(LEGACY_SCRIPT_SOURCE.producerId(), incoming, false);
	}

	static void clearLegacyScriptedGroupsQuietly() {
		SERVICE.removeSource(LEGACY_SCRIPT_SOURCE);
	}

	static void clearLegacyScriptedGroupsAndNotify() {
		SERVICE.removeSource(LEGACY_SCRIPT_SOURCE);
		publish(GroupChangeEvent.Kind.KUBEJS_REPLACE);
	}

	static synchronized void clearScriptedGroupsQuietly() {
		SCRIPTED_PUBLICATIONS.clear();
		++scriptedGeneration;
		lastRejectedPublicationActivity = ++publicationActivity;
		SERVICE.removeCategory(GroupSource.KUBEJS);
	}

	static synchronized void clearScriptedGroupsAndNotify() {
		clearScriptedGroupsQuietly();
		publish(GroupChangeEvent.Kind.KUBEJS_REPLACE);
	}

	private static String scriptedOwnerPrefix(String ownerId) {
		return "scoped:" + ownerId.length() + ':' + ownerId + ':';
	}

	private static void validatePublicationPart(String value, String label) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
	}

	public static boolean isExpanded(GroupDefinition group) { return isExpandedById(group.id()); }
	public static boolean isExpandedById(String id) { return GroupExpandState.isExpandedById(id); }
	public static void toggle(GroupDefinition group) { toggleById(group.id()); }
	public static void toggleById(String id) { GroupExpandState.toggleById(id); }

	public static synchronized void save(GroupDefinition group) {
		if (!saveQuietlyInternal(group)) return;
		publish(GroupChangeEvent.Kind.FULL);
	}

	public static synchronized void saveQuietly(GroupDefinition group) {
		saveQuietlyInternal(group);
	}

	private static boolean saveQuietlyInternal(GroupDefinition group) {
		SERVICE.validateGroup(group);
		if (!STORE.saveChecked(group)) return false;
		SERVICE.saveOrReplace(USER_SOURCE, group);
		return true;
	}

	public static Optional<GroupDefinition> copyAsCustomQuietly(String sourceId, String copiedDisplayName) {
		Optional<GroupDefinition> copied = createCustomCopyDraft(sourceId, copiedDisplayName);
		if (copied.isEmpty()) return Optional.empty();
		return saveQuietlyInternal(copied.get()) ? copied : Optional.empty();
	}

	public static Optional<GroupDefinition> createCustomCopyDraft(String sourceId, String copiedDisplayName) {
		if (sourceId == null || sourceId.isBlank()) return Optional.empty();
		return findById(sourceId).flatMap(source -> GroupCatalog.createCustomCopy(source, copiedDisplayName,
			getAllIncludingScripted().stream().map(GroupDefinition::id).toList()));
	}

	public static boolean setEnabledQuietly(String id, boolean enabled) {
		boolean changed = setEnabledQuietlyWithoutEvent(id, enabled);
		if (changed) publish(GroupChangeEvent.Kind.ENABLED);
		return changed;
	}

	public static synchronized boolean setEnabledQuietlyWithoutEvent(String id, boolean enabled) {
		if (id == null || id.isBlank()) return false;
		GroupDefinition existing = SERVICE.findById(id).orElse(null);
		if (existing == null) return false;
		if (existing.enabled() == enabled) return true;
		GroupSource source = SERVICE.visibleCategory(id);
		if ((source != null && source.usesEnabledOverride())
			|| GroupSource.fromGroupId(id).usesEnabledOverride()) {
			if (!STORE.saveEnabledOverrideChecked(id, enabled)) return false;
			return SERVICE.updateVisible(id, current -> current.withEnabled(enabled));
		}
		return saveQuietlyInternal(existing.withEnabled(enabled));
	}

	public static void notifyEnabledChanged() { publish(GroupChangeEvent.Kind.ENABLED); }

	public static synchronized void delete(String id) {
		if (!deleteQuietlyInternal(id)) return;
		publish(GroupChangeEvent.Kind.FULL);
	}

	public static synchronized void deleteQuietly(String id) {
		deleteQuietlyInternal(id);
	}

	private static boolean deleteQuietlyInternal(String id) {
		if (!STORE.deleteChecked(id)) return false;
		SERVICE.removeGroup(USER_SOURCE, id);
		return true;
	}

	public static void notifyViewer() { publish(GroupChangeEvent.Kind.FULL); }
	public static void notifyStructureChanged() { publish(GroupChangeEvent.Kind.STRUCTURE); }

	public static String generateUniqueId(String base) {
		return GroupCatalog.generateUniqueId(base,
			SERVICE.managedRegistrationOrder().stream().map(GroupDefinition::id).toList());
	}

	public static String generateUniqueIdIncludingScripted(String base) {
		return GroupCatalog.generateUniqueId(base,
			getAllIncludingScripted().stream().map(GroupDefinition::id).toList());
	}

	public static String sanitizeGeneratedIdBase(String base) {
		return GroupCatalog.sanitizeGeneratedIdBase(base);
	}

	private static void publish(GroupChangeEvent.Kind kind) {
		GroupChangeEvent.publish(kind);
	}

	/** Test seam for deterministic repository fixtures without reflective state mutation. */
	static void replaceForTesting(List<GroupDefinition> groups) {
		Map<GroupService.SourceKey, List<GroupDefinition>> sources = new LinkedHashMap<>();
		sources.put(BUILTIN_SOURCE, groups.stream()
			.filter(group -> GroupSource.fromGroupId(group.id()) == GroupSource.BUILTIN).toList());
		sources.put(USER_SOURCE, groups.stream()
			.filter(group -> GroupSource.fromGroupId(group.id()) != GroupSource.BUILTIN).toList());
		SERVICE.reset(sources);
		SCRIPTED_PUBLICATIONS.clear();
		++scriptedGeneration;
		lastRejectedPublicationActivity = ++publicationActivity;
	}
}
