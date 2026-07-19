package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientTypes;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import com.starskyxiii.collapsible_groups.group.GroupCatalog;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupSource;
import com.starskyxiii.collapsible_groups.persistence.GroupExpandState;
import com.starskyxiii.collapsible_groups.persistence.GroupStore;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Central registry for all collapsible groups.
 *
 * <p>All group types (item, fluid, generic) are stored as {@link GroupDefinition}.
 * KubeJS ephemeral groups are stored by the viewer-neutral
 * {@link com.starskyxiii.collapsible_groups.group.ScriptedGroupStore};
 * {@link KubeJsGroupStore} is only the compatibility delegate for this JEI-era API.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>JSON-persisted group CRUD ({@link GroupStore})
 *   <li>JEI ingredient caches (items and fluids)
 *   <li>Generic ingredient resolution
 *   <li>JEI invalidation callback
 *   <li>ID generation
 * </ul>
 *
 * <p>Expand/collapse state is managed by {@link GroupExpandState}.
 *
 * <p>Groups are loaded from {@code config/collapsiblegroups/groups/*.json}.
 * Call {@link #load(List)} on client setup; call {@link #save(GroupDefinition)} or
 * {@link #delete(String)} from the manager UI to persist changes.
 */
public final class GroupRegistry {
	public record FullMatchLookup<T>(List<T> values, boolean cacheHit, String fallbackReason) {}

	/**
	 * Copy-on-write group list in raw registration order. Always an unmodifiable snapshot.
	 * Writers must replace the entire reference; never mutate in place.
	 * Volatile guarantees visibility across threads.
	 */
	private static volatile List<GroupDefinition> groups = List.of();
	private static volatile List<GroupDefinition> orderedGroups = List.of();
	private static volatile Map<String, GroupDefinition> groupsById = Map.of();
	private static final GroupCatalog CATALOG = new GroupCatalog();
	private static final GroupStore STORE = new GroupStore();

	private static volatile List<ItemStack> jeiAllItems  = List.of();
	private static volatile List<Object>    jeiAllFluids = List.of();

	/** Lazily built editor item index; invalidated when jeiAllItems changes. */
	private static volatile EditorItemIndex editorItemIndex = null;

	/**
	 * Resolved items/fluids per group ID, pre-built by MixinIngredientFilter
	 * during {@code cg$buildIngredientGroupIndex()}. Null until JEI initialises.
	 * Partial entries are removed when a single group is saved/deleted;
	 * the whole map is cleared when JEI ingredient caches are reset.
	 */
	private static final JeiViewerGroupIndex VIEWER_INDEX = JeiViewerGroupIndex.instance();

	private GroupRegistry() {}

	// -----------------------------------------------------------------------
	// Load / init
	// -----------------------------------------------------------------------

	/**
	 * Loads all groups and the saved expand state.
	 *
	 * <p>Provider groups (IDs prefixed with {@code __default_}) are always read-only.
	 * Disk JSON files whose ID starts with {@code __default_} are skipped to prevent
	 * conflicts with built-in groups.
	 *
	 * @param providers built-in default group providers; pass an empty list for none
	 */
	public static void load(List<DefaultGroupProvider> providers) {
		publishGroups(STORE.loadGroups(providers));
		STORE.loadExpandState();

		long itemGroups  = groups.stream().filter(GroupDefinition::hasItemFilters).count();
		long fluidGroups = groups.stream().filter(GroupDefinition::hasFluidFilters).count();
		long genericGroups = groups.stream().filter(GroupDefinition::hasGenericFilters).count();
		Constants.LOG.info("[CollapsibleGroups] Loaded {} groups (item={}, fluid={}, generic={})",
			groups.size(), itemGroups, fluidGroups, genericGroups);
	}

	/** Returns true if the group ID belongs to a built-in provider default (prefixed with {@code __default_}). */
	public static boolean isBuiltin(String id) { return id.startsWith("__default_"); }

	// -----------------------------------------------------------------------
	// Group queries
	// -----------------------------------------------------------------------

	public static List<GroupDefinition> getAll() {
		syncCatalogFromLegacyFields();
		return CATALOG.priorityOrder();
	}

	/** Finds a group by ID, checking persisted/provider groups before ephemeral KubeJS groups. */
	public static Optional<GroupDefinition> findById(String id) {
		if (id == null || id.isBlank()) return Optional.empty();
		syncCatalogFromLegacyFields();
		Optional<GroupDefinition> group = CATALOG.findById(id);
		if (group.isPresent()) return group;
		for (GroupDefinition kjsGroup : KubeJsGroupStore.getGroups()) {
			if (id.equals(kjsGroup.id())) return Optional.of(kjsGroup);
		}
		return Optional.empty();
	}

	/**
	 * Returns all groups visible to the editor, including ephemeral KubeJS groups.
	 * Use this when checking whether an ingredient already belongs to another group.
	 */
	public static List<GroupDefinition> getAllIncludingKubeJs() {
		syncCatalogFromLegacyFields();
		List<GroupDefinition> kjs = KubeJsGroupStore.getGroups();
		List<GroupDefinition> snapshot = CATALOG.registrationOrder();
		if (kjs.isEmpty()) return CATALOG.priorityOrder();
		List<GroupDefinition> combined = new ArrayList<>(snapshot.size() + kjs.size());
		combined.addAll(snapshot);
		combined.addAll(kjs);
		return GroupCatalog.orderByPriority(combined);
	}

	/**
	 * Finds the first group (JSON-persisted or KubeJS ephemeral) that matches the given item.
	 * Higher priority groups are checked first; ties preserve registration order.
	 */
	public static Optional<GroupDefinition> findGroup(ItemStack stack) {
		for (GroupDefinition group : getAllIncludingKubeJs()) {
			if (group.matches(stack)) return Optional.of(group);
		}
		return Optional.empty();
	}

	/**
	 * Finds the first group (JSON-persisted or KubeJS ephemeral) that matches the given fluid.
	 * The fluid is a loader-specific type (e.g. NeoForge {@code FluidStack}) passed as {@code Object}.
	 * Higher priority groups are checked first; ties preserve registration order.
	 */
	public static Optional<GroupDefinition> findFluidGroup(Object stack) {
		for (GroupDefinition group : getAllIncludingKubeJs()) {
			if (GroupMatcher.matchesFluid(group, stack)) return Optional.of(group);
		}
		return Optional.empty();
	}

	/**
	 * Finds the first group (JSON-persisted or KubeJS ephemeral) that matches the given
	 * generic ingredient of the specified type.
	 * JSON groups are checked before KubeJS groups.
	 */
	public static <T> Optional<GroupDefinition> findGenericGroup(
		String typeId, T ingredient, IIngredientHelper<T> helper
	) {
		for (GroupDefinition group : getAllIncludingKubeJs()) {
			if (GroupMatcher.matchesGeneric(group, typeId, ingredient, helper)) return Optional.of(group);
		}
		return Optional.empty();
	}

	// --- Enable-agnostic finders (for full-match previews and editor diagnostics) ---

	public static Optional<GroupDefinition> findGroupIgnoringEnabled(ItemStack stack) {
		for (GroupDefinition group : getAllIncludingKubeJs()) {
			if (group.matchesIgnoringEnabled(stack)) return Optional.of(group);
		}
		return Optional.empty();
	}

	public static Optional<GroupDefinition> findFluidGroupIgnoringEnabled(Object stack) {
		for (GroupDefinition group : getAllIncludingKubeJs()) {
			if (GroupMatcher.matchesFluidIgnoringEnabled(group, stack)) return Optional.of(group);
		}
		return Optional.empty();
	}

	public static <T> Optional<GroupDefinition> findGenericGroupIgnoringEnabled(
		String typeId, T ingredient, IIngredientHelper<T> helper
	) {
		for (GroupDefinition group : getAllIncludingKubeJs()) {
			if (GroupMatcher.matchesGenericIgnoringEnabled(group, typeId, ingredient, helper)) return Optional.of(group);
		}
		return Optional.empty();
	}

	// -----------------------------------------------------------------------
	// Ingredient resolution
	// -----------------------------------------------------------------------

	/**
	 * Populates the JEI item and fluid caches directly from the ingredient manager if they are
	 * still empty (i.e. before {@code IngredientFilter.getElements()} has been called).
	 */
	@SuppressWarnings("unchecked")
	public static void populateJeiCachesIfEmpty() {
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null) return;
		IIngredientManager manager = runtime.getIngredientManager();
		if (isJeiAllItemsEmpty()) {
			setJeiAllItems(new ArrayList<>(manager.getAllIngredients(VanillaTypes.ITEM_STACK)));
		}
		IIngredientType<?> fluidType = JeiIngredientTypes.getFluidType();
		if (fluidType != null && isJeiAllFluidsEmpty()) {
			setJeiAllFluids(new ArrayList<>((List<Object>) (List<?>) manager.getAllIngredients(fluidType)));
		}
	}

	/** Resolves all items from the JEI cache that match the given group. Falls back to registry scan. */
	public static List<ItemStack> resolveItems(GroupDefinition group) {
		long traceStart = PerformanceTrace.begin();
		populateJeiCachesIfEmpty();
		List<ItemStack> result = !jeiAllItems.isEmpty()
			? jeiAllItems.stream().filter(group::matches).toList()
			: BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(group::matches).toList();
		PerformanceTrace.logIfSlow("GroupRegistry.resolveItems", traceStart, 5,
			"group=" + group.id() + " result=" + result.size() + " itemFilters=" + group.hasItemFilters());
		return result;
	}

	/** Returns all fluids from the JEI fluid cache that match the given group. */
	public static List<Object> resolveFluids(GroupDefinition group) {
		long traceStart = PerformanceTrace.begin();
		populateJeiCachesIfEmpty();
		List<Object> result = !jeiAllFluids.isEmpty()
			? jeiAllFluids.stream().filter(f -> GroupMatcher.matchesFluid(group, f)).toList()
			: List.of();
		PerformanceTrace.logIfSlow("GroupRegistry.resolveFluids", traceStart, 5,
			"group=" + group.id() + " result=" + result.size() + " fluidFilters=" + group.hasFluidFilters());
		return result;
	}

	/**
	 * Returns the pre-resolved item list for a group ID from the fast cache, or
	 * {@code null} if the cache is not yet populated.
	 * The cache has keys for all groups, but only enabled groups receive
	 * first-match members. Disabled groups still appear in full-match previews.
	 * <p>Use this in the manager screen's {@code rebuildCards()} for O(1) access.
	 * Never use this in the editor ??the editor needs live resolution so that
	 * in-progress edits are reflected immediately.
	 */
	public static List<ItemStack> getResolvedItems(String groupId) {
		return VIEWER_INDEX.resolvedItems(groupId);
	}

	/** Same as {@link #getResolvedItems} but for fluids. */
	public static List<Object> getResolvedFluids(String groupId) {
		return VIEWER_INDEX.resolvedFluids(groupId);
	}

	/**
	 * Returns the overlap-correct full-match item preview for manager cards.
	 * Falls back to a live resolve only when this group's cache entry is unavailable.
	 */
	public static List<ItemStack> getFullMatchItems(GroupDefinition group) {
		return getFullMatchItemsLookup(group).values();
	}

	public static FullMatchLookup<ItemStack> getFullMatchItemsLookup(GroupDefinition group) {
		String groupId = group.id();
		var cache = VIEWER_INDEX.fullMatchItems();
		if (cache != null && cache.containsKey(groupId)) {
			return new FullMatchLookup<>(cache.get(groupId), true, null);
		}
		String fallbackReason = cache == null ? "cache_map_null" : "entry_missing";
		List<ItemStack> resolved = resolveItems(managerPreviewDefinition(group));
		ensureFullMatchItemsCache().put(groupId, List.copyOf(resolved));
		return new FullMatchLookup<>(resolved, false, fallbackReason);
	}

	/** Same as {@link #getFullMatchItems(GroupDefinition)} but for fluids. */
	public static List<Object> getFullMatchFluids(GroupDefinition group) {
		return getFullMatchFluidsLookup(group).values();
	}

	public static FullMatchLookup<Object> getFullMatchFluidsLookup(GroupDefinition group) {
		String groupId = group.id();
		var cache = VIEWER_INDEX.fullMatchFluids();
		if (cache != null && cache.containsKey(groupId)) {
			return new FullMatchLookup<>(cache.get(groupId), true, null);
		}
		String fallbackReason = cache == null ? "cache_map_null" : "entry_missing";
		List<Object> resolved = resolveFluids(managerPreviewDefinition(group));
		ensureFullMatchFluidsCache().put(groupId, List.copyOf(resolved));
		return new FullMatchLookup<>(resolved, false, fallbackReason);
	}

	/** Same as {@link #getFullMatchItems(GroupDefinition)} but for generic ingredients. */
	public static List<GenericIngredientRef> getFullMatchGenericIngredients(GroupDefinition group) {
		return getFullMatchGenericIngredientsLookup(group).values();
	}

	public static List<ItemStack> getFullMatchItemsCached(String groupId) {
		Map<String, List<ItemStack>> cache = VIEWER_INDEX.fullMatchItems();
		return cache == null ? null : cache.get(groupId);
	}

	public static List<Object> getFullMatchFluidsCached(String groupId) {
		Map<String, List<Object>> cache = VIEWER_INDEX.fullMatchFluids();
		return cache == null ? null : cache.get(groupId);
	}

	public static List<GenericIngredientRef> getFullMatchGenericCached(String groupId) {
		Map<String, List<GenericIngredientRef>> cache = VIEWER_INDEX.fullMatchGeneric();
		return cache == null ? null : cache.get(groupId);
	}

	public static FullMatchLookup<GenericIngredientRef> getFullMatchGenericIngredientsLookup(GroupDefinition group) {
		String groupId = group.id();
		var cache = VIEWER_INDEX.fullMatchGeneric();
		if (cache != null && cache.containsKey(groupId)) {
			return new FullMatchLookup<>(cache.get(groupId), true, null);
		}
		String fallbackReason = cache == null ? "cache_map_null" : "entry_missing";
		List<GenericIngredientRef> resolved = resolveGenericIngredients(managerPreviewDefinition(group));
		ensureFullMatchGenericCache().put(groupId, List.copyOf(resolved));
		return new FullMatchLookup<>(resolved, false, fallbackReason);
	}

	/** Resolves all generic JEI ingredients that match the given group definition. */
	public static List<GenericIngredientRef> resolveGenericIngredients(GroupDefinition group) {
		long traceStart = PerformanceTrace.begin();
		if (!group.hasGenericFilters()) return List.of();
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null) return List.of();
		IIngredientManager ingredientManager = runtime.getIngredientManager();
		List<GenericIngredientRef> result = new ArrayList<>();
		for (Map.Entry<String, IIngredientType<?>> entry : JeiIngredientTypes.getAll().entrySet()) {
			appendMatchingGenericIngredients(group, entry.getKey(), entry.getValue(), ingredientManager, result);
		}
		List<GenericIngredientRef> copy = List.copyOf(result);
		PerformanceTrace.logIfSlow("GroupRegistry.resolveGenericIngredients", traceStart, 5,
			"group=" + group.id() + " result=" + copy.size() + " genericFilters=" + group.hasGenericFilters());
		return copy;
	}

	/** Returns every generic JEI ingredient registered with this mod's type registry. */
	public static List<GenericIngredientRef> getJeiAllGenericIngredients() {
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null) return List.of();
		IIngredientManager ingredientManager = runtime.getIngredientManager();
		List<GenericIngredientRef> result = new ArrayList<>();
		for (Map.Entry<String, IIngredientType<?>> entry : JeiIngredientTypes.getAll().entrySet()) {
			appendAllGenericIngredients(entry.getKey(), entry.getValue(), ingredientManager, result);
		}
		return List.copyOf(result);
	}

	// -----------------------------------------------------------------------
	// JEI ingredient caches
	// -----------------------------------------------------------------------

	public static void setJeiAllItems(List<ItemStack> items)   { jeiAllItems  = List.copyOf(items); editorItemIndex = null; }
	public static boolean isJeiAllItemsEmpty()                  { return jeiAllItems.isEmpty(); }
	public static List<ItemStack> getJeiAllItems()              { return jeiAllItems; }
	public static void clearJeiAllItems()                       { jeiAllItems  = List.of(); editorItemIndex = null; clearResolvedCaches(); }

	public static void setJeiAllFluids(List<Object> fluids)     { jeiAllFluids = List.copyOf(fluids); }
	public static boolean isJeiAllFluidsEmpty()                  { return jeiAllFluids.isEmpty(); }
	public static List<Object> getJeiAllFluids()                 { return jeiAllFluids; }
	public static void clearJeiAllFluids()                       { jeiAllFluids = List.of(); clearManagerPreviewCaches(); }

	// -----------------------------------------------------------------------
	// Editor item index (lazy, tied to jeiAllItems lifecycle)
	// -----------------------------------------------------------------------

	/**
	 * Returns the cached {@link EditorItemIndex}, building it lazily on first call.
	 * The index is invalidated whenever {@link #setJeiAllItems} or {@link #clearJeiAllItems}
	 * is called, so it always reflects the current JEI item cache generation.
	 */
	private static EditorItemIndex getOrCreateEditorItemIndex() {
		EditorItemIndex index = editorItemIndex;
		if (index != null) return index;
		synchronized (GroupRegistry.class) {
			if (editorItemIndex == null) {
				editorItemIndex = EditorItemIndex.build(jeiAllItems);
			}
			return editorItemIndex;
		}
	}

	public static void warmEditorItemIndex() {
		populateJeiCachesIfEmpty();
		getOrCreateEditorItemIndex();
	}

	/**
	 * Resolves the item preview for a structurally editable editor draft using the
	 * pre-built item index in O(selectorCount + matchedCandidates).
	 *
	 * <p>If {@code enabled} is false, returns an empty list without any resolution work.
	 * If the draft has no item selectors or tags, returns empty immediately.
	 *
	 * @param draft   the current editor draft
	 * @param enabled whether the group is currently enabled in the editor
	 * @return ordered, deduplicated list of matching JEI items
	 */
	public static List<ItemStack> resolveEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled) {
		if (!enabled) return List.of();
		if (draft.explicitItemSelectors().isEmpty() && draft.itemTags().isEmpty()) return List.of();
		populateJeiCachesIfEmpty();
		return getOrCreateEditorItemIndex().resolveDraft(draft);
	}

	/**
	 * item preview for a <em>hybrid</em> draft (flat contents leaves + preserved advanced
	 * subtrees). The flat part is resolved from the item index; the preserved part is full-scanned
	 * once and memoised in the index's single-slot cache. The two are unioned (identity-dedup,
	 * ordinal order) to preserve the JEI universe order.
	 *
	 * <p>Returns empty for a disabled group, mirroring {@code GroupDefinition.matches}
	 * ({@code enabled && …}) so the union stays equivalent to the full scan in that case too.
	 */
	public static List<ItemStack> resolveHybridEditorDraftItems(GroupFilterEditorDraft draft, boolean enabled) {
		if (!enabled) return List.of();
		populateJeiCachesIfEmpty();
		return getOrCreateEditorItemIndex().resolveHybridDraft(draft, GroupRegistry::resolveItemsForPreserved);
	}

	/**
	 * full-scan the JEI item cache for the union ({@code Any(...)}) of a hybrid draft's
	 * preserved advanced subtrees, wrapped as a throwaway preview definition so the existing
	 * {@link #resolveItems} machinery is reused verbatim. Called only on a preserved-fingerprint
	 * cache miss (rule edit / first entry).
	 */
	public static List<ItemStack> resolveItemsForPreserved(List<GroupFilter> preserved) {
		if (preserved.isEmpty()) return List.of();
		GroupFilter combined = preserved.size() == 1
			? preserved.get(0)
			: Filters.any(preserved.toArray(GroupFilter[]::new));
		try {
			return resolveItems(new GroupDefinition("__cg_preserved_preview__", "", true, combined));
		} catch (IllegalArgumentException e) {
			// A mid-edit rules draft can contain a not-yet-valid node (e.g. a pending
			// HAS_COMPONENT with empty fields). An invalid preserved tree matches nothing
			// until it validates; the fingerprint changes again once the node is completed,
			// so the cached empty result cannot go stale.
			return List.of();
		}
	}

	// -----------------------------------------------------------------------
	// Resolved-items cache  (pre-built by MixinIngredientFilter)
	// -----------------------------------------------------------------------

	public static void setResolvedItemsByGroup(Map<String, List<ItemStack>> map) {
		VIEWER_INDEX.setResolvedItemsByGroup(map);
	}

	public static void setResolvedFluidsByGroup(Map<String, List<Object>> map) {
		VIEWER_INDEX.setResolvedFluidsByGroup(map);
	}

	public static void setFullMatchItemsByGroup(Map<String, List<ItemStack>> map) {
		VIEWER_INDEX.setFullMatchItemsByGroup(map);
	}

	public static void setFullMatchFluidsByGroup(Map<String, List<Object>> map) {
		VIEWER_INDEX.setFullMatchFluidsByGroup(map);
	}

	public static void setFullMatchGenericByGroup(Map<String, List<GenericIngredientRef>> map) {
		VIEWER_INDEX.setFullMatchGenericByGroup(map);
	}

	public static void setItemIdToGroupIds(Map<String, Set<String>> map)  { VIEWER_INDEX.setItemReverseIndex(map); }
	public static void setFluidIdToGroupIds(Map<String, Set<String>> map) { VIEWER_INDEX.setFluidReverseIndex(map); }
	public static Map<String, Set<String>> getItemIdToGroupIds()  { return VIEWER_INDEX.itemReverseIndex(); }
	public static Map<String, Set<String>> getFluidIdToGroupIds() { return VIEWER_INDEX.fluidReverseIndex(); }

	/** Drops the whole resolved cache (called when JEI re-initialises). */
	public static void clearResolvedCaches() {
		VIEWER_INDEX.clearResolvedCaches();
		clearManagerPreviewCaches();
	}

	public static void clearManagerPreviewCaches() {
		VIEWER_INDEX.clearPreviewCaches();
	}

	/** Removes the first-match ownership cache entry for the given group (built by MixinIngredientFilter). */
	public static void invalidateFirstMatchCache(String groupId) {
		VIEWER_INDEX.invalidateFirstMatch(groupId);
	}

	/** Clears enabled-dependent ownership lookups while preserving full-match previews. */
	public static void clearFirstMatchCaches() {
		VIEWER_INDEX.clearResolvedCaches();
	}

	/** Invalidates manager full-match preview cache (enable-agnostic filter results). */
	public static void invalidateFullMatchCache(String groupId) {
		VIEWER_INDEX.invalidateFullMatch(groupId);
	}

	/**
	 * Removes just one group's entry from the resolved cache after a
	 * save or delete. The entry will be repopulated on the next JEI rebuild.
	 */
	public static void invalidateResolvedCache(String groupId) {
		invalidateFirstMatchCache(groupId);
		invalidateFullMatchCache(groupId);
	}

	// -----------------------------------------------------------------------
	// KubeJS group management
	// -----------------------------------------------------------------------

	public static void setKubeJsGroups(List<GroupDefinition> incoming) {
		KubeJsGroupStore.setGroups(GroupCatalog.applyEnabledOverrides(incoming, STORE.loadEnabledOverrides()));
		VIEWER_INDEX.onGroupChange(GroupChangeEvent.Kind.KUBEJS_REPLACE, getAllIncludingKubeJs());
		GroupChangeEvent.publish(GroupChangeEvent.Kind.KUBEJS_REPLACE);
	}
	public static boolean isKubeJsGroupsEmpty()                        { return KubeJsGroupStore.isGroupsEmpty(); }
	public static void clearKubeJsGroups() {
		KubeJsGroupStore.clearAll();
		clearManagerPreviewCaches();
	}

	public static boolean isKubeJsApplied()  { return KubeJsGroupStore.isApplied(); }
	public static void markKubeJsApplied()   { KubeJsGroupStore.markApplied(); }

	// -----------------------------------------------------------------------
	// Expand / collapse state
	// -----------------------------------------------------------------------

	public static boolean isExpanded(GroupDefinition group)  { return GroupExpandState.isExpandedById(group.id()); }
	public static boolean isExpandedById(String id)          { return GroupExpandState.isExpandedById(id); }
	public static void toggle(GroupDefinition group)         { GroupExpandState.toggleById(group.id()); }
	public static void toggleById(String id)                 { GroupExpandState.toggleById(id); }

	// -----------------------------------------------------------------------
	// CRUD
	// -----------------------------------------------------------------------

	/** Adds or updates a group definition, saves to disk, and refreshes JEI. */
	public static void save(GroupDefinition group) {
		saveQuietly(group);
		invalidateFullMatchCache(group.id());
		notifyJei();
	}

	/** Saves a group without triggering JEI invalidation. */
	public static void saveQuietly(GroupDefinition group) {
		invalidateFirstMatchCache(group.id());
		syncCatalogFromLegacyFields();
		CATALOG.saveOrReplace(group);
		syncLegacyFieldsFromCatalog();
		STORE.save(group);
	}

	public static Optional<GroupDefinition> copyAsCustomQuietly(String sourceId, String copiedDisplayName) {
		Optional<GroupDefinition> copied = createCustomCopyDraft(sourceId, copiedDisplayName);
		copied.ifPresent(GroupRegistry::saveQuietly);
		return copied;
	}

	public static Optional<GroupDefinition> createCustomCopyDraft(String sourceId, String copiedDisplayName) {
		if (sourceId == null || sourceId.isBlank()) return Optional.empty();
		Optional<GroupDefinition> source = findById(sourceId);
		if (source.isEmpty()) return Optional.empty();
		return createCustomCopy(
			source.get(),
			copiedDisplayName,
			getAllIncludingKubeJs().stream().map(GroupDefinition::id).toList()
		);
	}

	/**
	 * Updates enabled state and publishes an enabled-only change event.
	 *
	 * <p>User groups are persisted through their normal group JSON. Built-in and
	 * KubeJS groups are persisted through the enabled override store so provider
	 * definitions and ephemeral KubeJS definitions are never written as group JSON.
	 *
	 * @return {@code false} only when the id is blank or no current group exists.
	 */
	public static boolean setEnabledQuietly(String id, boolean enabled) {
		return setEnabledQuietly(id, enabled, true);
	}

	/** Updates enabled state without publishing the enabled event, for coalesced operations. */
	public static boolean setEnabledQuietlyWithoutEvent(String id, boolean enabled) {
		return setEnabledQuietly(id, enabled, false);
	}

	private static boolean setEnabledQuietly(String id, boolean enabled, boolean publishEvent) {
		if (id == null || id.isBlank()) return false;

		syncCatalogFromLegacyFields();
		GroupDefinition existing = CATALOG.byId().get(id);
		if (existing != null) {
			if (existing.enabled() == enabled) return true;
			clearFirstMatchCaches();
			if (GroupSource.fromGroupId(id).usesEnabledOverride()) {
				CATALOG.setEnabled(id, enabled);
				syncLegacyFieldsFromCatalog();
				STORE.saveEnabledOverride(id, enabled);
			} else {
				saveQuietly(existing.withEnabled(enabled));
			}
			if (publishEvent) notifyEnabledChanged();
			return true;
		}

		for (GroupDefinition group : KubeJsGroupStore.getGroups()) {
			if (!id.equals(group.id())) continue;
			if (group.enabled() == enabled) return true;
			boolean updated = KubeJsGroupStore.updateGroup(id, current -> current.withEnabled(enabled));
			if (updated) {
				clearFirstMatchCaches();
				STORE.saveEnabledOverride(id, enabled);
				if (publishEvent) notifyEnabledChanged();
			}
			return updated;
		}

		return false;
	}

	/** Publishes one coalesced enabled-state change. */
	public static void notifyEnabledChanged() {
		VIEWER_INDEX.onGroupChange(GroupChangeEvent.Kind.ENABLED, getAllIncludingKubeJs());
		GroupChangeEvent.publish(GroupChangeEvent.Kind.ENABLED);
	}

	/** Removes a group by ID, deletes its file, and refreshes JEI. */
	public static void delete(String id) {
		deleteQuietly(id);
		notifyJei();
	}

	/** Removes a group without triggering JEI invalidation. */
	public static void deleteQuietly(String id) {
		invalidateResolvedCache(id);
		syncCatalogFromLegacyFields();
		CATALOG.delete(id);
		syncLegacyFieldsFromCatalog();
		STORE.delete(id);
	}

	/** Triggers a full JEI rebuild. Called only by {@link #save} and {@link #delete}; the Quietly variants do not trigger this. */
	public static void notifyJei() {
		VIEWER_INDEX.onGroupChange(GroupChangeEvent.Kind.FULL, getAllIncludingKubeJs());
		GroupChangeEvent.publish(GroupChangeEvent.Kind.FULL);
	}

	/** Lightweight refresh: only Level-2+3 caches (structure + display), preserving Level-1 index. */
	public static void notifyJeiStructureOnly() {
		VIEWER_INDEX.onGroupChange(GroupChangeEvent.Kind.STRUCTURE, getAllIncludingKubeJs());
		GroupChangeEvent.publish(GroupChangeEvent.Kind.STRUCTURE);
	}

	/** Generates a unique group ID that doesn't collide with any existing group. */
	public static String generateUniqueId(String base) {
		syncCatalogFromLegacyFields();
		return CATALOG.generateUniqueId(base);
	}

	/** Generates a unique group ID that avoids both persisted/provider groups and ephemeral KubeJS groups. */
	public static String generateUniqueIdIncludingKubeJs(String base) {
		return GroupCatalog.generateUniqueId(
			base,
			getAllIncludingKubeJs().stream().map(GroupDefinition::id).toList()
		);
	}

	static Optional<GroupDefinition> createCustomCopy(
		GroupDefinition source,
		String copiedDisplayName,
		List<String> existingGroupIds
	) {
		return GroupCatalog.createCustomCopy(source, copiedDisplayName, existingGroupIds);
	}

	/**
	 * Normalizes a user-facing group name into a filesystem-safe ASCII ID base.
	 * Repeated separators are collapsed and leading/trailing underscores are trimmed.
	 */
	public static String sanitizeGeneratedIdBase(String base) {
		return GroupCatalog.sanitizeGeneratedIdBase(base);
	}

	// -----------------------------------------------------------------------
	// Private helpers
	// -----------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private static <T> void appendMatchingGenericIngredients(
		GroupDefinition group,
		String typeId,
		IIngredientType<?> rawType,
		IIngredientManager ingredientManager,
		List<GenericIngredientRef> out
	) {
		IIngredientType<T> type    = (IIngredientType<T>) rawType;
		IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(type);
		for (T ingredient : ingredientManager.getAllIngredients(type)) {
			if (GroupMatcher.matchesGeneric(group, typeId, ingredient, helper)) {
				out.add(new GenericIngredientRef(typeId, (IIngredientType<Object>) type, ingredient));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void appendAllGenericIngredients(
		String typeId,
		IIngredientType<?> rawType,
		IIngredientManager ingredientManager,
		List<GenericIngredientRef> out
	) {
		IIngredientType<T> type = (IIngredientType<T>) rawType;
		for (T ingredient : ingredientManager.getAllIngredients(type)) {
			out.add(new GenericIngredientRef(typeId, (IIngredientType<Object>) type, ingredient));
		}
	}

	private static GroupDefinition managerPreviewDefinition(GroupDefinition group) {
		return group.enabled() ? group : group.withEnabled(true);
	}

	/**
	 * Writes the given group's full-match preview cache entries immediately after save.
	 * This bridges the window before the async JEI rebuild republishes the authoritative maps.
	 */
	public static void populateFullMatchCacheFromSaved(GroupDefinition saved) {
		GroupDefinition previewDefinition = managerPreviewDefinition(saved);
		List<ItemStack> items;
		GroupFilterEditorDraft.DecodeResult decoded = GroupFilterEditorDraft.decode(saved.filter());
		// Gate the flat-index fast path on the flat-index-safe predicate,
		// not on editability. A hybrid draft with preserved advanced subtrees is editable but
		// its item membership cannot be resolved from the flat index alone — resolve fully.
		if (decoded.flatIndexSafe()) {
			populateJeiCachesIfEmpty();
			items = getOrCreateEditorItemIndex().resolveDraft(decoded.draft());
		} else {
			items = resolveItems(previewDefinition);
		}

		List<Object> fluids = resolveFluids(previewDefinition);
		List<GenericIngredientRef> generic = resolveGenericIngredients(previewDefinition);

		ensureFullMatchItemsCache().put(saved.id(), List.copyOf(items));
		ensureFullMatchFluidsCache().put(saved.id(), List.copyOf(fluids));
		ensureFullMatchGenericCache().put(saved.id(), List.copyOf(generic));
	}

	private static Map<String, List<ItemStack>> ensureFullMatchItemsCache() {
		return VIEWER_INDEX.ensureFullMatchItems();
	}

	private static Map<String, List<Object>> ensureFullMatchFluidsCache() {
		return VIEWER_INDEX.ensureFullMatchFluids();
	}

	private static Map<String, List<GenericIngredientRef>> ensureFullMatchGenericCache() {
		return VIEWER_INDEX.ensureFullMatchGeneric();
	}

	private static void publishGroups(List<GroupDefinition> registrationOrder) {
		CATALOG.publish(registrationOrder);
		syncLegacyFieldsFromCatalog();
	}

	static List<GroupDefinition> orderByPriority(List<GroupDefinition> source) {
		return GroupCatalog.orderByPriority(source);
	}

	static List<GroupDefinition> applyEnabledOverridesToManagedSources(
		List<GroupDefinition> source,
		Map<String, Boolean> overrides
	) {
		return GroupCatalog.applyEnabledOverrides(source, overrides);
	}

	private static synchronized void syncCatalogFromLegacyFields() {
		if (CATALOG.registrationOrder() != groups) {
			CATALOG.publish(groups);
			syncLegacyFieldsFromCatalog();
		}
	}

	private static synchronized void syncLegacyFieldsFromCatalog() {
		groups = CATALOG.registrationOrder();
		orderedGroups = CATALOG.priorityOrder();
		groupsById = CATALOG.byId();
	}
}
