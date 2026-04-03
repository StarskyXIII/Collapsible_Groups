package com.starskyxiii.collapsible_groups.mixin;

import com.starskyxiii.collapsible_groups.compat.jei.api.IngredientTypeRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.element.FluidChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GenericChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupHeaderElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupMatcher;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.IngredientFilterHelper;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.library.ingredients.TypedIngredient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(value = IngredientFilter.class, remap = false)
public abstract class MixinIngredientFilter {
	@Shadow
	@Nullable
	private List<IElement<?>> ingredientListCached;

	@Shadow
	@Final
	private IFilterTextSource filterTextSource;

	@Shadow
	@Final
	private IIngredientManager ingredientManager;

	@org.spongepowered.asm.mixin.gen.Invoker("getIngredientListUncached")
	protected abstract Stream<ITypedIngredient<?>> cg$getIngredientListUncached(String filterText);

	@org.spongepowered.asm.mixin.gen.Invoker("notifyListenersOfChange")
	protected abstract void cg$notifyListenersOfChange();

	// -----------------------------------------------------------------
	// Cache fields ??three levels of granularity
	//
	// Level 1  cg$ingredientGroupIndex
	//   Built once at JEI init (or after group/KubeJS changes).
	//   Maps every JEI ingredient instance ??its GroupDefinition.
	//   Cost: O(N?G) ??expensive, but paid only once.
	//   Benefit: makes every subsequent search O(1) per ingredient.
	//
	// Level 2  cg$baseList / cg$baseListGroupIds / cg$childrenByGroupId
	//   "Structure cache" ??rebuilt whenever the filtered ingredient set
	//   changes (search text change) using Level-1 O(1) lookups.
	//   Cost: O(N) ??cheap enough to run on each keystroke.
	//
	// Level 3  ingredientListCached  (JEI's own field, shadowed)
	//   "Display cache" ??the flat ordered element list actually shown.
	//   Rebuilt from Level-2 cache on toggle: O(N + expanded_children).
	//   Never rebuilt from scratch on toggle ??that was the original freeze.
	// -----------------------------------------------------------------

	/** ingredient instance ??owning group. Ungrouped ingredients have no entry in this map. Covers ALL JEI ingredients. */
	@Unique @Nullable private Map<ITypedIngredient<?>, GroupDefinition> cg$ingredientGroupIndex = null;

	/** Ordered element list without any children (all groups collapsed). */
	@Unique @Nullable private List<IElement<?>> cg$baseList = null;

	/** Parallel to cg$baseList ??null for regular elements, groupId for group headers. */
	@Unique @Nullable private List<String> cg$baseListGroupIds = null;

	/** Pre-built child element lists per group id. */
	@Unique @Nullable private Map<String, List<IElement<?>>> cg$childrenByGroupId = null;

	/** Set of enabled group IDs ??rebuilt on structure cache invalidation. */
	@Unique @Nullable private Set<String> cg$enabledGroupIds = null;

	/** Cached full ingredient list to avoid re-fetching on rebuild. */
	@Unique @Nullable private List<ITypedIngredient<?>> cg$cachedFullList = null;

	/** Pending async Level-1 index rebuild. */
	@Unique @Nullable private CompletableFuture<Map<ITypedIngredient<?>, GroupDefinition>> cg$pendingIndex = null;

	@Unique private static final ExecutorService REBUILD_EXECUTOR =
		Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "CG-IndexRebuild"); t.setDaemon(true); return t; });

	// -----------------------------------------------------------------
	// JEI initialisation hook
	// -----------------------------------------------------------------

	@Inject(method = "<init>", at = @At("TAIL"))
	private void cg$onInit(CallbackInfo ci) {
		GroupRegistry.jeiInvalidateCallback = this::cg$invalidateAndNotify;
		GroupRegistry.jeiStructureInvalidateCallback = this::cg$invalidateStructureAndNotify;
		GroupRegistry.clearJeiAllItems();
		GroupRegistry.clearJeiAllFluids();
		GroupRegistry.clearKubeJsGroups();
		this.cg$cachedFullList = null;
	}

	// -----------------------------------------------------------------
	// getElements override
	// -----------------------------------------------------------------

	@Inject(method = "getElements", at = @At("HEAD"), cancellable = true)
	private void cg$onGetElements(CallbackInfoReturnable<List<IElement<?>>> cir) {
		if (this.ingredientListCached == null) {
			String filterText = this.filterTextSource.getFilterText().toLowerCase(Locale.ROOT);
			List<ITypedIngredient<?>> ingredients = this.cg$getIngredientListUncached(filterText).toList();
			this.cg$buildStructureCache(ingredients);
			this.ingredientListCached = this.cg$buildDisplayFromCache();
		}
		cir.setReturnValue(this.ingredientListCached);
	}

	// -----------------------------------------------------------------
	// Invalidation helpers
	// -----------------------------------------------------------------

	/**
	 * Full invalidation: clears all three cache levels.
	 * Called when group content changes (edit/delete). Uses async rebuild
	 * when a cached full list is available to avoid blocking the render thread.
	 */
	private void cg$invalidateAndNotify() {
		this.cg$clearStructureCaches();

		List<ITypedIngredient<?>> snapshot = this.cg$cachedFullList;
		if (snapshot != null) {
			CompletableFuture<Map<ITypedIngredient<?>, GroupDefinition>> future = CompletableFuture.supplyAsync(
				() -> this.cg$buildIngredientGroupIndex(snapshot), REBUILD_EXECUTOR);
			this.cg$pendingIndex = future;
			future.thenRunAsync(() -> {
				if (this.cg$pendingIndex != future) return;
				this.cg$clearStructureCaches();
				this.cg$notifyListenersOfChange();
			}, Minecraft.getInstance()::execute);
			// Keep old cg$ingredientGroupIndex to serve requests until new one is ready
		} else {
			this.cg$pendingIndex = null;
			this.cg$ingredientGroupIndex = null;  // fallback: synchronous rebuild
		}
		this.cg$notifyListenersOfChange();
	}

	@Unique
	private void cg$clearStructureCaches() {
		this.ingredientListCached = null;
		this.cg$baseList = null;
		this.cg$baseListGroupIds = null;
		this.cg$childrenByGroupId = null;
		this.cg$enabledGroupIds = null;
	}

	/**
	 * Structure-only invalidation: clears Level-2+3 caches but preserves Level-1 index.
	 * Called when only the enabled state changes (toggle), avoiding O(N?G) rebuild.
	 */
	@Unique
	private void cg$invalidateStructureAndNotify() {
		this.cg$clearStructureCaches();
		this.cg$notifyListenersOfChange();
	}

	/**
	 * Partial rebuild: reassembles only the display list from the structure
	 * cache in O(N + expanded_children). Used as the onToggle callback for
	 * all group header elements so expand/collapse is instantaneous.
	 */
	@Unique
	private void cg$toggleAndRebuildDisplay() {
		if (this.cg$baseList != null) {
			this.ingredientListCached = this.cg$buildDisplayFromCache();
		} else {
			// Edge case at startup before structure is built.
			this.ingredientListCached = null;
		}
		this.cg$notifyListenersOfChange();
	}

	// -----------------------------------------------------------------
	// Level-1 index builder  O(N?G) ??once per JEI init or group change
	// -----------------------------------------------------------------

	/**
	 * Builds a complete ingredient?roup mapping over the entire unfiltered
	 * JEI ingredient list. Each ingredient instance is mapped to the first
	 * group that matches it. Unmapped ingredients are not grouped.
	 */
	@Unique
	private Map<ITypedIngredient<?>, GroupDefinition> cg$buildIngredientGroupIndex(
		List<ITypedIngredient<?>> all
	) {
		long traceStart = PerformanceTrace.begin();
		// Item indexing (+ setResolvedItemsByGroup) delegated to common helper.
		Map<ITypedIngredient<?>, GroupDefinition> index = IngredientFilterHelper.buildItemGroupIndex(all);

		// Fluid and generic indexing ??NeoForge-specific.
		Map<String, List<Object>> fluidsByGroup = new HashMap<>();
		Map<String, List<Object>> fullMatchFluidsByGroup = new HashMap<>();
		Map<String, List<GenericIngredientRef>> fullMatchGenericByGroup = new HashMap<>();
		IIngredientManager mgr = this.ingredientManager;
		List<GroupDefinition> allGroups = GroupRegistry.getAllIncludingKubeJs();
		List<GroupDefinition> fluidGroups = allGroups.stream()
			.filter(GroupDefinition::hasFluidFilters)
			.toList();
		List<GroupDefinition> genericGroups = allGroups.stream()
			.filter(GroupDefinition::hasGenericFilters)
			.toList();

		for (ITypedIngredient<?> ingredient : all) {
			if (ingredient.getItemStack().isPresent()) continue; // already handled by helper
			// Fluids
			var fluidOpt = ingredient.getIngredient(NeoForgeTypes.FLUID_STACK);
			if (fluidOpt.isPresent()) {
				Object fluid = fluidOpt.get();
				GroupDefinition firstMatch = null;
				for (GroupDefinition group : fluidGroups) {
					if (!GroupMatcher.matchesFluidIgnoringEnabled(group, fluid)) continue;
					if (firstMatch == null) {
						firstMatch = group;
						index.put(ingredient, group);
						fluidsByGroup.computeIfAbsent(group.id(), k -> new ArrayList<>()).add(fluid);
					}
					fullMatchFluidsByGroup.computeIfAbsent(group.id(), k -> new ArrayList<>()).add(fluid);
				}
				continue;
			}
			// Generic types registered with our mod
			if (mgr != null && !genericGroups.isEmpty()) {
				cg$indexGenericIngredient(ingredient, mgr, index, genericGroups, fullMatchGenericByGroup);
			}
		}

		// Ensure every group (including disabled) has a fluid entry so the resolved
		// cache covers all groups.
		for (GroupDefinition g : allGroups) {
			fluidsByGroup.putIfAbsent(g.id(), List.of());
			fullMatchFluidsByGroup.putIfAbsent(g.id(), List.of());
			fullMatchGenericByGroup.putIfAbsent(g.id(), List.of());
		}
		GroupRegistry.setResolvedFluidsByGroup(fluidsByGroup);
		GroupRegistry.setFullMatchFluidsByGroup(fullMatchFluidsByGroup);
		GroupRegistry.setFullMatchGenericByGroup(fullMatchGenericByGroup);

		// Build fluid reverse index (fluidRegistryId ??Set<groupId>)
		Map<String, Set<String>> fluidIdToGroupIds = new HashMap<>();
		for (var fluidEntry : fluidsByGroup.entrySet()) {
			String groupId = fluidEntry.getKey();
			for (Object fluid : fluidEntry.getValue()) {
				if (fluid instanceof FluidStack fs) {
					String registryId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fs.getFluid()).toString();
					fluidIdToGroupIds.computeIfAbsent(registryId, k -> new HashSet<>()).add(groupId);
				}
			}
		}
		GroupRegistry.setFluidIdToGroupIds(fluidIdToGroupIds);
		PerformanceTrace.logIfSlow("MixinIngredientFilter.buildIngredientGroupIndex", traceStart, 20,
			"ingredients=" + all.size()
				+ " indexSize=" + index.size()
				+ " fluidGroups=" + fluidGroups.size()
				+ " genericGroups=" + genericGroups.size()
				+ " resolvedFluids=" + fluidsByGroup.size()
				+ " fullMatchFluids=" + fullMatchFluidsByGroup.size()
				+ " fullMatchGeneric=" + fullMatchGenericByGroup.size()
				+ " fluidIds=" + fluidIdToGroupIds.size());

		return index;
	}

	@Unique
	@SuppressWarnings("unchecked")
	private <T> void cg$indexGenericIngredient(
		ITypedIngredient<?> typed,
		IIngredientManager mgr,
		Map<ITypedIngredient<?>, GroupDefinition> index,
		List<GroupDefinition> genericGroups,
		Map<String, List<GenericIngredientRef>> fullMatchGenericByGroup
	) {
		for (Map.Entry<String, IIngredientType<?>> entry : IngredientTypeRegistry.getAll().entrySet()) {
			IIngredientType<T> type = (IIngredientType<T>) entry.getValue();
			if (!typed.getType().equals(type)) continue;
			IIngredientHelper<T> helper = mgr.getIngredientHelper(type);
			T ingredient = ((ITypedIngredient<T>) typed).getIngredient();
			GroupDefinition firstMatch = null;
			for (GroupDefinition group : genericGroups) {
				if (!GroupMatcher.matchesGenericIgnoringEnabled(group, entry.getKey(), ingredient, helper)) continue;
				if (firstMatch == null) {
					firstMatch = group;
					index.put(typed, group);
				}
				fullMatchGenericByGroup.computeIfAbsent(group.id(), k -> new ArrayList<>())
					.add(new GenericIngredientRef(entry.getKey(), (IIngredientType<Object>) type, ingredient));
			}
			break;
		}
	}

	// -----------------------------------------------------------------
	// Level-2 structure cache builder  O(N) ??once per search update
	// -----------------------------------------------------------------

	/**
	 * Buckets the filtered ingredient list into groups (Pass 1) and builds
	 * the collapsed-state structure cache (Pass 2). Uses the Level-1 index
	 * for O(1) per-ingredient group lookups instead of O(G) linear scans.
	 */
	@Unique
	private void cg$buildStructureCache(List<ITypedIngredient<?>> ingredients) {
		long traceStart = PerformanceTrace.begin();
		// --- Ensure global caches and the ingredient?roup index are ready ---

		List<ITypedIngredient<?>> all = this.cg$cachedFullList;

		if (GroupRegistry.isJeiAllItemsEmpty()) {
			if (all == null) all = this.cg$getIngredientListUncached("").toList();
			this.cg$cachedFullList = all;
			GroupRegistry.setJeiAllItems(all.stream().flatMap(i -> i.getItemStack().stream()).toList());
			GroupRegistry.setJeiAllFluids(all.stream().flatMap(i -> i.getIngredient(NeoForgeTypes.FLUID_STACK).stream()).map(o -> (Object) o).toList());
		} else if (all == null) {
			all = this.cg$getIngredientListUncached("").toList();
			this.cg$cachedFullList = all;
		}

		// KubeJS integration deferred until a 26.1.1-compatible build is available
		// TODO: re-enable KubeJS block when KubeJS publishes a 26.1.1 build
		if (false && !GroupRegistry.isKubeJsApplied() && ModList.get().isLoaded("kubejs")) {
			@SuppressWarnings("unchecked")
			List<FluidStack> fluidsForKjs = (List<FluidStack>) (List<?>) GroupRegistry.getJeiAllFluids();
			// com.starskyxiii.collapsible_groups.compat.kubejs.KubeJSGroupBridge.applyGroups(
			//     GroupRegistry.getJeiAllItems(), fluidsForKjs, this.ingredientManager);
			GroupRegistry.markKubeJsApplied();
			// KubeJS added new groups — the existing index (if any) is stale.
			this.cg$ingredientGroupIndex = null;
			this.cg$pendingIndex = null;
		}

		// Check if async rebuild completed
		if (this.cg$pendingIndex != null && this.cg$pendingIndex.isDone()) {
			try {
				this.cg$ingredientGroupIndex = this.cg$pendingIndex.join();
			} catch (Exception e) {
				this.cg$ingredientGroupIndex = null; // fallback to sync
			}
			this.cg$pendingIndex = null;
		}

		if (this.cg$ingredientGroupIndex == null) {
			this.cg$ingredientGroupIndex = this.cg$buildIngredientGroupIndex(all);
		}

		// --- Rebuild enabled group ID set ---
		this.cg$enabledGroupIds = GroupRegistry.getAllIncludingKubeJs().stream()
			.filter(GroupDefinition::enabled).map(GroupDefinition::id)
			.collect(Collectors.toSet());

		// --- Pass 1: bucket each filtered ingredient into its group  O(N) ---

		Map<GroupDefinition, List<ITypedIngredient<ItemStack>>> itemGroups   = new LinkedHashMap<>();
		Map<GroupDefinition, List<ITypedIngredient<FluidStack>>> fluidGroups = new LinkedHashMap<>();
		Map<GroupDefinition, List<ITypedIngredient<?>>> genericGroups        = new LinkedHashMap<>();

		for (ITypedIngredient<?> ingredient : ingredients) {
			GroupDefinition group = this.cg$ingredientGroupIndex.get(ingredient);
			if (group == null || !this.cg$enabledGroupIds.contains(group.id())) continue;

			if (ingredient.getItemStack().isPresent()) {
				@SuppressWarnings("unchecked")
				ITypedIngredient<ItemStack> item = (ITypedIngredient<ItemStack>) ingredient;
				itemGroups.computeIfAbsent(group, x -> new ArrayList<>()).add(item);
			} else if (ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).isPresent()) {
				@SuppressWarnings("unchecked")
				ITypedIngredient<FluidStack> fluid = (ITypedIngredient<FluidStack>) ingredient;
				fluidGroups.computeIfAbsent(group, x -> new ArrayList<>()).add(fluid);
			} else {
				genericGroups.computeIfAbsent(group, x -> new ArrayList<>()).add(ingredient);
			}
		}

		// --- Pass 2: build structure cache  O(N) ---

		List<IElement<?>> baseList              = new ArrayList<>();
		List<String>      baseListGroupIds       = new ArrayList<>();
		Map<String, List<IElement<?>>> childrenByGroupId = new HashMap<>();
		Set<String> emittedGroupHeaders          = new HashSet<>();

		for (ITypedIngredient<?> ingredient : ingredients) {
			GroupDefinition group = this.cg$ingredientGroupIndex.get(ingredient);

			if (group != null) {
				List<ITypedIngredient<ItemStack>> itemChildren   = itemGroups.getOrDefault(group, List.of());
				List<ITypedIngredient<FluidStack>> fluidChildren = fluidGroups.getOrDefault(group, List.of());
				List<ITypedIngredient<?>> genericChildren        = genericGroups.getOrDefault(group, List.of());
				int totalChildren = itemChildren.size() + fluidChildren.size() + genericChildren.size();

				if (totalChildren >= 2) {
					if (emittedGroupHeaders.add(group.id())) {
						IElement<?> header = cg$createGroupHeader(
							group, itemChildren, fluidChildren, genericChildren,
							this::cg$toggleAndRebuildDisplay);
						baseList.add(header);
						baseListGroupIds.add(group.id());

						List<IElement<?>> children = new ArrayList<>(totalChildren);
						for (ITypedIngredient<ItemStack> child : itemChildren)
							children.add(new GroupChildElement(child, group.id()));
						for (ITypedIngredient<FluidStack> child : fluidChildren)
							children.add(new FluidChildElement(child, group.id()));
						for (ITypedIngredient<?> child : genericChildren)
							children.add(cg$wrapGenericChild(child, group.id()));
						childrenByGroupId.put(group.id(), children);
					}
					// All members of an active group are suppressed from the base list
					continue;
				}
			}

			baseList.add(new IngredientElement<>(ingredient));
			baseListGroupIds.add(null);
		}

		this.cg$baseList          = baseList;
		this.cg$baseListGroupIds  = baseListGroupIds;
		this.cg$childrenByGroupId = childrenByGroupId;
		PerformanceTrace.logIfSlow("MixinIngredientFilter.buildStructureCache", traceStart, 20,
			"filtered=" + ingredients.size()
				+ " cachedFull=" + (all == null ? 0 : all.size())
				+ " base=" + baseList.size()
				+ " groups=" + childrenByGroupId.size()
				+ " enabledGroups=" + (this.cg$enabledGroupIds == null ? 0 : this.cg$enabledGroupIds.size())
				+ " pending=" + (this.cg$pendingIndex != null)
				+ " indexReady=" + (this.cg$ingredientGroupIndex != null));
	}

	// -----------------------------------------------------------------
	// Level-3 display list builder  O(N) ??on every toggle
	// -----------------------------------------------------------------

	@Unique
	private List<IElement<?>> cg$buildDisplayFromCache() {
		List<IElement<?>> result = new ArrayList<>(this.cg$baseList.size());
		for (int i = 0; i < this.cg$baseList.size(); i++) {
			result.add(this.cg$baseList.get(i));
			String groupId = this.cg$baseListGroupIds.get(i);
			if (groupId != null && GroupRegistry.isExpandedById(groupId)) {
				result.addAll(this.cg$childrenByGroupId.get(groupId));
			}
		}
		return result;
	}

	// -----------------------------------------------------------------
	// Header factory helpers
	// -----------------------------------------------------------------

	/**
	 * Unified header factory: builds a {@link GroupIcon} with the first 1??
	 * display ingredients, constructs a count label, collects preview entries,
	 * and wraps everything in a single {@link GroupHeaderElement}.
	 */
	@Unique
	private IElement<?> cg$createGroupHeader(
		GroupDefinition group,
		List<ITypedIngredient<ItemStack>> itemChildren,
		List<ITypedIngredient<FluidStack>> fluidChildren,
		List<ITypedIngredient<?>> genericChildren,
		Runnable onToggle
	) {
		// --- Display ingredients for stacked icon ---
		List<ITypedIngredient<?>> displayIngredients = group.iconIds().isEmpty()
			? List.of() : cg$resolveIconIds(group.iconIds());
		if (displayIngredients.isEmpty()) {
			List<ITypedIngredient<?>> allChildren = new ArrayList<>(
				itemChildren.size() + fluidChildren.size() + genericChildren.size());
			allChildren.addAll(itemChildren);
			allChildren.addAll(fluidChildren);
			allChildren.addAll(genericChildren);
			displayIngredients = allChildren.subList(0, Math.min(2, allChildren.size()));
		}

		GroupIcon icon = new GroupIcon(group.id(), group.displayName().key(), group.displayName().fallback(), displayIngredients);
		ITypedIngredient<GroupIcon> typedIcon = TypedIngredient.createUnvalidated(GroupIcon.TYPE, icon);

		// --- Count label ---
		Component countLabel = cg$buildCountLabel(itemChildren.size(), fluidChildren.size(), genericChildren.size());

		// --- Preview entries for tooltip ---
		int totalChildren = itemChildren.size() + fluidChildren.size() + genericChildren.size();
		List<GroupPreviewEntry> previewEntries = new ArrayList<>(totalChildren);
		for (ITypedIngredient<ItemStack> child : itemChildren)
			previewEntries.add(GroupPreviewEntry.ofItem(child.getIngredient()));
		for (ITypedIngredient<FluidStack> child : fluidChildren)
			previewEntries.add(GroupPreviewEntry.ofFluid(child.getIngredient()));
		previewEntries.addAll(GroupPreviewEntry.fromTypedIngredients(genericChildren));

		return new GroupHeaderElement(typedIcon, countLabel, previewEntries, onToggle);
	}

	@Unique
	private static List<ITypedIngredient<?>> cg$resolveIconIds(List<String> iconIds) {
		List<ITypedIngredient<?>> result = new ArrayList<>(iconIds.size());
		for (String iconId : iconIds) {
			Identifier loc = Identifier.tryParse(iconId);
			if (loc == null) continue;
			Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc).map(ref -> ref.value()).orElse(null);
			if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
			ItemStack stack = new ItemStack(item);
			result.add(TypedIngredient.createUnvalidated(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack));
		}
		return result;
	}

	@Unique
	private static Component cg$buildCountLabel(int itemCount, int fluidCount, int genericCount) {
		MutableComponent result = null;
		if (itemCount > 0) {
			result = Component.translatable(ModTranslationKeys.COUNT_ITEMS, itemCount);
		}
		if (fluidCount > 0) {
			MutableComponent part = Component.translatable(ModTranslationKeys.COUNT_FLUIDS, fluidCount);
			result = result == null ? part : result.append(", ").append(part);
		}
		if (genericCount > 0) {
			MutableComponent part = Component.translatable(ModTranslationKeys.COUNT_ENTRIES, genericCount);
			result = result == null ? part : result.append(", ").append(part);
		}
		return (result != null ? result : Component.empty()).withStyle(ChatFormatting.GRAY);
	}

	@Unique
	@SuppressWarnings("unchecked")
	private <T> IElement<?> cg$wrapGenericChild(ITypedIngredient<?> ingredient, String groupId) {
		return new GenericChildElement<>((ITypedIngredient<T>) ingredient, groupId);
	}
}
