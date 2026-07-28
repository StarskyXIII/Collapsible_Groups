package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import com.starskyxiii.collapsible_groups.persistence.GroupExpandState;
import com.starskyxiii.collapsible_groups.persistence.GroupStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Viewer-neutral owner of persisted, provider, and scripted group state.
 *
 * <p>This class must remain loadable when neither JEI nor EMI is present. Viewer adapters react
 * to the published {@link GroupChangeEvent}; the repository never reaches into viewer caches.
 */
public final class GroupRepository {
	private static final GroupCatalog CATALOG = new GroupCatalog();
	private static final GroupStore STORE = new GroupStore();

	private GroupRepository() {}

	public static void load(List<DefaultGroupProvider> providers) {
		CATALOG.publish(STORE.loadGroups(providers));
		STORE.loadExpandState();
		List<GroupDefinition> groups = CATALOG.registrationOrder();
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
		return CATALOG.priorityOrder();
	}

	/** Persisted/provider groups win over scripted groups on an ID collision. */
	public static Optional<GroupDefinition> findById(String id) {
		if (id == null || id.isBlank()) return Optional.empty();
		Optional<GroupDefinition> persisted = CATALOG.findById(id);
		if (persisted.isPresent()) return persisted;
		return ScriptedGroupStore.groups().stream().filter(group -> id.equals(group.id())).findFirst();
	}

	public static List<GroupDefinition> getAllIncludingScripted() {
		List<GroupDefinition> scripted = ScriptedGroupStore.groups();
		if (scripted.isEmpty()) return CATALOG.priorityOrder();
		List<GroupDefinition> combined = new ArrayList<>(CATALOG.registrationOrder().size() + scripted.size());
		combined.addAll(CATALOG.registrationOrder());
		combined.addAll(scripted);
		return GroupCatalog.orderByPriority(combined);
	}

	public static void setScriptedGroups(List<GroupDefinition> incoming) {
		ScriptedGroupStore.publish(GroupCatalog.applyEnabledOverrides(incoming, STORE.loadEnabledOverrides()));
		publish(GroupChangeEvent.Kind.KUBEJS_REPLACE);
	}

	public static boolean areScriptedGroupsEmpty() { return ScriptedGroupStore.isEmpty(); }

	/** Clear without notification; used while a viewer runtime is being torn down. */
	public static void clearScriptedGroups() { ScriptedGroupStore.invalidate(); }

	public static boolean areScriptedGroupsApplied() { return ScriptedGroupStore.isApplied(); }
	public static void markScriptedGroupsApplied() { ScriptedGroupStore.markApplied(); }

	public static boolean isExpanded(GroupDefinition group) { return isExpandedById(group.id()); }
	public static boolean isExpandedById(String id) { return GroupExpandState.isExpandedById(id); }
	public static void toggle(GroupDefinition group) { toggleById(group.id()); }
	public static void toggleById(String id) { GroupExpandState.toggleById(id); }

	public static void save(GroupDefinition group) {
		saveQuietly(group);
		publish(GroupChangeEvent.Kind.FULL);
	}

	public static void saveQuietly(GroupDefinition group) {
		CATALOG.saveOrReplace(group);
		STORE.save(group);
	}

	public static Optional<GroupDefinition> copyAsCustomQuietly(String sourceId, String copiedDisplayName) {
		Optional<GroupDefinition> copied = createCustomCopyDraft(sourceId, copiedDisplayName);
		copied.ifPresent(GroupRepository::saveQuietly);
		return copied;
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

	public static boolean setEnabledQuietlyWithoutEvent(String id, boolean enabled) {
		if (id == null || id.isBlank()) return false;
		GroupDefinition existing = CATALOG.byId().get(id);
		if (existing != null) {
			if (existing.enabled() == enabled) return true;
			if (GroupSource.fromGroupId(id).usesEnabledOverride()) {
				CATALOG.setEnabled(id, enabled);
				STORE.saveEnabledOverride(id, enabled);
			} else {
				saveQuietly(existing.withEnabled(enabled));
			}
			return true;
		}
		for (GroupDefinition group : ScriptedGroupStore.groups()) {
			if (!id.equals(group.id())) continue;
			if (group.enabled() == enabled) return true;
			boolean updated = ScriptedGroupStore.update(id, current -> current.withEnabled(enabled));
			if (updated) STORE.saveEnabledOverride(id, enabled);
			return updated;
		}
		return false;
	}

	public static void notifyEnabledChanged() { publish(GroupChangeEvent.Kind.ENABLED); }

	public static void delete(String id) {
		deleteQuietly(id);
		publish(GroupChangeEvent.Kind.FULL);
	}

	public static void deleteQuietly(String id) {
		CATALOG.delete(id);
		STORE.delete(id);
	}

	public static void notifyViewer() { publish(GroupChangeEvent.Kind.FULL); }
	public static void notifyStructureChanged() { publish(GroupChangeEvent.Kind.STRUCTURE); }

	public static String generateUniqueId(String base) { return CATALOG.generateUniqueId(base); }

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
		CATALOG.publish(groups);
		ScriptedGroupStore.invalidate();
	}
}
