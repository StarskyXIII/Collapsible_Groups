package com.starskyxiii.collapsible_groups.compat.jei.runtime;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupFilterEditorDraft;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.defaults.DefaultGroupProvider;
import com.starskyxiii.collapsible_groups.persistence.GroupConfig;
import com.starskyxiii.collapsible_groups.persistence.GroupExpandState;
import com.starskyxiii.collapsible_groups.platform.Services;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Central registry for all collapsible groups.
 *
 * <p>All group types (item, fluid, generic) are stored as {@link GroupDefinition}.
 * KubeJS ephemeral groups are stored in {@link KubeJsGroupStore}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>JSON-persisted group CRUD ({@link GroupConfig})
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
	 * Copy-on-write group list. Always an unmodifiable snapshot.
	 * Writers must replace the entire reference; never mutate in place.
	 * Volatile guarantees visibility across threads.
	 */
	private static volatile List<GroupDefinition> groups = List.of();

	/** Set by MixinIngredientFilter. Triggers a full JEI rebuild, including the ingredient-to-group ownership index. */
	public static volatile Runnable jeiInvalidateCallback = null;

	/** Set by MixinIngredientFilter. Rebuilds only the group structure and display caches; does not rebuild the ownership index. */
	public static volatile Runnable jeiStructureInvalidateCallback = null;

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
	private static volatile Map<String, List<ItemStack>> resolvedItemsByGroup  = null;
	private static volatile Map<String, List<Object>>    resolvedFluidsByGroup = null;
	private static volatile Map<String, List<ItemStack>> fullMatchItemsByGroup = null;
	private static volatile Map<String, List<Object>>    fullMatchFluidsByGroup = null;
	private static volatile Map<String, List<GenericIngredientRef>> fullMatchGenericByGroup = null;

	/** Maps item registry ID -> group IDs that include that item (built by IngredientFilterHelper). */
	private static volatile Map<String, Set<String>> itemIdToGroupIds = null;
	/** Maps fluid registry ID -> group IDs that include that fluid (built by MixinIngredientFilter). */
	private static volatile Map<String, Set<String>> fluidIdToGroupIds = null;

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
		// 1. Collect provider defaults (insertion order preserved)
		Map<String, GroupDefinition> merged = new LinkedHashMap<>();
		for (DefaultGroupProvider provider : providers) {
			for (GroupDefinition g : provider.getGroups()) {
				merged.put(g.id(), g);
			}
		}

		// 2. Disk JSON fills in user-created groups; __default_ IDs are reserved for providers
		for (GroupDefinition g : GroupConfig.load()) {
			if (!g.id().startsWith("__default_")) merged.put(g.id(), g);
		}

		groups = List.copyOf(merged.values());
		GroupExpandState.load(GroupConfig.loadExpandState());

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

	/** Returns all JSON-persisted groups. */
	public static List<GroupDefinition> getAll() {
		return groups;
	}

	/**
	 * Returns all groups visible to the editor, including ephemeral KubeJS groups.
	 * Use this when checking whether an ingredient already belongs to another group.
	 */
	public static List<GroupDefinition> getAllIncludingKubeJs() {
		List<GroupDefinition> kjs = KubeJsGroupStore.getGroups();
		List<GroupDefinition> snapshot = groups;
		if (kjs.isEmpty()) return snapshot;
		List<GroupDefinition> combined = new ArrayList<>(snapshot);
		combined.addAll(kjs);
		return Collections.unmodifiableList(combined);
	}

	/**
	 * Finds the first group (JSON-persisted or KubeJS ephemeral) that matches the given item.
	 * JSON groups are checked before KubeJS groups.
	 */
	public static Optional<GroupDefinition> findGroup(ItemStack stack) {
		List<GroupDefinition> snapshot = groups;
		for (GroupDefinition group : snapshot)                    if (group.matches(stack)) return Optional.of(group);
		for (GroupDefinition group : KubeJsGroupStore.getGroups()) if (group.matches(stack)) return Optional.of(group);
		return Optional.empty();
	}

	/**
	 * Finds the first group (JSON-persisted or KubeJS ephemeral) that matches the given fluid.
	 * The fluid is a loader-specific type (e.g. NeoForge {@code FluidStack}) passed as {@code Object}.
	 * JSON groups are checked before KubeJS groups.
	 */
	public static Optional<GroupDefinition> findFluidGroup(Object stack) {
		List<GroupDefinition> snapshot = groups;
		for (GroupDefinition group : snapshot)                    if (GroupMatcher.matchesFluid(group, stack)) return Optional.of(group);
		for (GroupDefinition group : KubeJsGroupStore.getGroups()) if (GroupMatcher.matchesFluid(group, stack)) return Optional.of(group);
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
		List<GroupDefinition> snapshot = groups;
		for (GroupDefinition group : snapshot)
			if (GroupMatcher.matchesGeneric(group, typeId, ingredient, helper)) return Optional.of(group);
		for (GroupDefinition group : KubeJsGroupStore.getGroups())
			if (GroupMatcher.matchesGeneric(group, typeId, ingredient, helper)) return Optional.of(group);
		return Optional.empty();
	}

	// --- Enable-agnostic finders (for Level-1 index that includes disabled groups) ---

	public static Optional<GroupDefinition> findGroupIgnoringEnabled(ItemStack stack) {
		List<GroupDefinition> snapshot = groups;
		for (GroupDefinition group : snapshot)                    if (group.matchesIgnoringEnabled(stack)) return Optional.of(group);
		for (GroupDefinition group : KubeJsGroupStore.getGroups()) if (group.matchesIgnoringEnabled(stack)) return Optional.of(group);
		return Optional.empty();
	}

	public static Optional<GroupDefinition> findFluidGroupIgnoringEnabled(Object stack) {
		List<GroupDefinition> snapshot = groups;
		for (GroupDefinition group : snapshot)                    if (GroupMatcher.matchesFluidIgnoringEnabled(group, stack)) return Optional.of(group);
		for (GroupDefinition group : KubeJsGroupStore.getGroups()) if (GroupMatcher.matchesFluidIgnoringEnabled(group, stack)) return Optional.of(group);
		return Optional.empty();
	}

	public static <T> Optional<GroupDefinition> findGenericGroupIgnoringEnabled(
		String typeId, T ingredient, IIngredientHelper<T> helper
	) {
		List<GroupDefinition> snapshot = groups;
		for (GroupDefinition group : snapshot)
			if (GroupMatcher.matchesGenericIgnoringEnabled(group, typeId, ingredient, helper)) return Optional.of(group);
		for (GroupDefinition group : KubeJsGroupStore.getGroups())
			if (GroupMatcher.matchesGenericIgnoringEnabled(group, typeId, ingredient, helper)) return Optional.of(group);
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
		if (!isJeiAllItemsEmpty()) return;
		var runtime = JeiRuntimeHolder.get();
		if (runtime == null) return;
		IIngredientManager manager = runtime.getIngredientManager();
		setJeiAllItems(new ArrayList<>(manager.getAllIngredients(VanillaTypes.ITEM_STACK)));
		IIngredientType<?> fluidType = Services.PLATFORM.getJeiFluidType();
		if (fluidType != null) {
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
	 * The cache includes both enabled and disabled groups (enable-agnostic index).
	 * <p>Use this in the manager screen's {@code rebuildCards()} for O(1) access.
	 * Never use this in the editor ??the editor needs live resolution so that
	 * in-progress edits are reflected immediately.
	 */
	public static List<ItemStack> getResolvedItems(String groupId) {
		var cache = resolvedItemsByGroup;
		if (cache == null) return null;
		return cache.get(groupId);
	}

	/** Same as {@link #getResolvedItems} but for fluids. */
	public static List<Object> getResolvedFluids(String groupId) {
		var cache = resolvedFluidsByGroup;
		if (cache == null) return null;
		return cache.get(groupId);
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
		var cache = fullMatchItemsByGroup;
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
		var cache = fullMatchFluidsByGroup;
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

	public static FullMatchLookup<GenericIngredientRef> getFullMatchGenericIngredientsLookup(GroupDefinition group) {
		String groupId = group.id();
		var cache = fullMatchGenericByGroup;
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
		for (Map.Entry<String, IIngredientType<?>> entry : IngredientTypeRegistry.getAll().entrySet()) {
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
		for (Map.Entry<String, IIngredientType<?>> entry : IngredientTypeRegistry.getAll().entrySet()) {
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

	/**
	 * Resolves the item preview for a structurally editable editor draft using the
	 * pre-built item index. This replaces the per-edit O(allItems) full scan.
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

	// -----------------------------------------------------------------------
	// Resolved-items cache  (pre-built by MixinIngredientFilter)
	// -----------------------------------------------------------------------

	/** Called by MixinIngredientFilter after building the ingredient-group index. */
	public static void setResolvedItemsByGroup(Map<String, List<ItemStack>> map) {
		resolvedItemsByGroup = freezeResolvedMap(map);
	}

	/** Called by MixinIngredientFilter after building the ingredient-group index. */
	public static void setResolvedFluidsByGroup(Map<String, List<Object>> map) {
		resolvedFluidsByGroup = freezeResolvedMap(map);
	}

	public static void setFullMatchItemsByGroup(Map<String, List<ItemStack>> map) {
		fullMatchItemsByGroup = freezeResolvedMap(map);
	}

	public static void setFullMatchFluidsByGroup(Map<String, List<Object>> map) {
		fullMatchFluidsByGroup = freezeResolvedMap(map);
	}

	public static void setFullMatchGenericByGroup(Map<String, List<GenericIngredientRef>> map) {
		fullMatchGenericByGroup = freezeResolvedMap(map);
	}

	public static void setItemIdToGroupIds(Map<String, Set<String>> map)  { itemIdToGroupIds = map; }
	public static void setFluidIdToGroupIds(Map<String, Set<String>> map) { fluidIdToGroupIds = map; }
	public static Map<String, Set<String>> getItemIdToGroupIds()  { return itemIdToGroupIds; }
	public static Map<String, Set<String>> getFluidIdToGroupIds() { return fluidIdToGroupIds; }

	/** Drops the whole resolved cache (called when JEI re-initialises). */
	public static void clearResolvedCaches() {
		resolvedItemsByGroup  = null;
		resolvedFluidsByGroup = null;
		itemIdToGroupIds      = null;
		fluidIdToGroupIds     = null;
		clearManagerPreviewCaches();
	}

	public static void clearManagerPreviewCaches() {
		fullMatchItemsByGroup = null;
		fullMatchFluidsByGroup = null;
		fullMatchGenericByGroup = null;
	}

	/** Removes the first-match ownership cache entry for the given group (built by MixinIngredientFilter). */
	public static void invalidateFirstMatchCache(String groupId) {
		var items = resolvedItemsByGroup;
		if (items != null) items.remove(groupId);
		var fluids = resolvedFluidsByGroup;
		if (fluids != null) fluids.remove(groupId);
	}

	/** Invalidates manager full-match preview cache (enable-agnostic filter results). */
	public static void invalidateFullMatchCache(String groupId) {
		var fullItems = fullMatchItemsByGroup;
		if (fullItems != null) fullItems.remove(groupId);
		var fullFluids = fullMatchFluidsByGroup;
		if (fullFluids != null) fullFluids.remove(groupId);
		var fullGeneric = fullMatchGenericByGroup;
		if (fullGeneric != null) fullGeneric.remove(groupId);
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
	// KubeJS group management (delegated to KubeJsGroupStore)
	// -----------------------------------------------------------------------

	public static void setKubeJsGroups(List<GroupDefinition> incoming) {
		KubeJsGroupStore.setGroups(incoming);
		clearManagerPreviewCaches();
	}
	public static boolean isKubeJsGroupsEmpty()                        { return KubeJsGroupStore.isGroupsEmpty(); }
	public static void clearKubeJsGroups() {
		KubeJsGroupStore.clearAll();
		clearManagerPreviewCaches();
	}

	public static boolean isKubeJsApplied()  { return KubeJsGroupStore.isApplied(); }
	public static void markKubeJsApplied()   { KubeJsGroupStore.markApplied(); }

	// -----------------------------------------------------------------------
	// Expand / collapse state (delegated to GroupExpandState)
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
		replaceGroups(snapshot -> {
			List<GroupDefinition> copy = new ArrayList<>(snapshot.size() + 1);
			boolean replaced = false;
			for (GroupDefinition existing : snapshot) {
				if (existing.id().equals(group.id())) {
					copy.add(group);
					replaced = true;
				} else {
					copy.add(existing);
				}
			}
			if (!replaced) {
				copy.add(group);
			}
			return List.copyOf(copy);
		});
		GroupConfig.save(group);
	}

	/** Removes a group by ID, deletes its file, and refreshes JEI. */
	public static void delete(String id) {
		deleteQuietly(id);
		notifyJei();
	}

	/** Removes a group without triggering JEI invalidation. */
	public static void deleteQuietly(String id) {
		invalidateResolvedCache(id);
		replaceGroups(snapshot -> List.copyOf(snapshot.stream().filter(g -> !g.id().equals(id)).toList()));
		GroupConfig.delete(id);
		GroupExpandState.remove(id);
	}

	/** Triggers a full JEI rebuild. Called only by {@link #save} and {@link #delete}; the Quietly variants do not trigger this. */
	public static void notifyJei() {
		Runnable cb = jeiInvalidateCallback;
		if (cb != null) cb.run();
	}

	/** Lightweight refresh: only Level-2+3 caches (structure + display), preserving Level-1 index. */
	public static void notifyJeiStructureOnly() {
		Runnable cb = jeiStructureInvalidateCallback;
		if (cb != null) cb.run();
	}

	/** Generates a unique group ID that doesn't collide with any existing group. */
	public static String generateUniqueId(String base) {
		String id = sanitizeGeneratedIdBase(base);
		if (id.isEmpty()) {
			id = fallbackGeneratedIdBase(base);
		}
		List<GroupDefinition> snapshot = groups;
		final String baseId = id;
		if (snapshot.stream().noneMatch(g -> g.id().equals(baseId))) return baseId;
		for (int i = 2; i < 1000; i++) {
			String candidate = baseId + "_" + i;
			if (snapshot.stream().noneMatch(g -> g.id().equals(candidate))) return candidate;
		}
		return baseId + "_" + System.currentTimeMillis();
	}

	/**
	 * Normalizes a user-facing group name into a filesystem-safe ASCII ID base.
	 * Repeated separators are collapsed and leading/trailing underscores are trimmed.
	 */
	public static String sanitizeGeneratedIdBase(String base) {
		if (base == null || base.isBlank()) {
			return "";
		}

		String normalized = Normalizer.normalize(base, Normalizer.Form.NFKC)
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9_]", "_")
			.replaceAll("_+", "_")
			.replaceAll("^_+|_+$", "");

		return normalized;
	}

	private static String fallbackGeneratedIdBase(String base) {
		if (base == null || base.isBlank()) {
			return "group";
		}

		String normalized = Normalizer.normalize(base, Normalizer.Form.NFKC);
		return "group_" + Integer.toUnsignedString(normalized.hashCode(), 36);
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
		if (decoded.structurallyEditable()) {
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
		var cache = fullMatchItemsByGroup;
		if (cache != null) return cache;
		synchronized (GroupRegistry.class) {
			if (fullMatchItemsByGroup == null) fullMatchItemsByGroup = new ConcurrentHashMap<>();
			return fullMatchItemsByGroup;
		}
	}

	private static Map<String, List<Object>> ensureFullMatchFluidsCache() {
		var cache = fullMatchFluidsByGroup;
		if (cache != null) return cache;
		synchronized (GroupRegistry.class) {
			if (fullMatchFluidsByGroup == null) fullMatchFluidsByGroup = new ConcurrentHashMap<>();
			return fullMatchFluidsByGroup;
		}
	}

	private static Map<String, List<GenericIngredientRef>> ensureFullMatchGenericCache() {
		var cache = fullMatchGenericByGroup;
		if (cache != null) return cache;
		synchronized (GroupRegistry.class) {
			if (fullMatchGenericByGroup == null) fullMatchGenericByGroup = new ConcurrentHashMap<>();
			return fullMatchGenericByGroup;
		}
	}

	private static <T> Map<String, List<T>> freezeResolvedMap(Map<String, List<T>> map) {
		Map<String, List<T>> copy = new ConcurrentHashMap<>(Math.max(16, map.size() * 2));
		for (var entry : map.entrySet()) {
			copy.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return copy;
	}

	private static void replaceGroups(UnaryOperator<List<GroupDefinition>> updater) {
		synchronized (GroupRegistry.class) {
			groups = updater.apply(groups);
		}
	}
}
