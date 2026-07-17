package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientTypes;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.element.GenericChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupHeaderElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.compat.jei.ui.GroupBackgroundRenderer;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerProjection;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.library.ingredients.TypedIngredient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Shared cache and projection logic behind the loader IngredientFilter mixins. */
public final class JeiIngredientFilterController {
	private static GroupChangeEvent.Subscription fullChangeSubscription;
	private static GroupChangeEvent.Subscription structureChangeSubscription;
	private static GroupChangeEvent.Subscription enabledChangeSubscription;
	private static GroupChangeEvent.Subscription kubeJsChangeSubscription;

	private final Supplier<String> filterText;
	private final Function<String, Stream<ITypedIngredient<?>>> uncachedIngredients;
	private final Runnable notifyListeners;
	private final IIngredientManager ingredientManager;
	private final Supplier<List<IElement<?>>> displayCache;
	private final Consumer<List<IElement<?>>> setDisplayCache;
	private final PlatformHooks hooks;
	private final JeiViewerGroupIndex viewerIndex = JeiViewerGroupIndex.instance();

	private @Nullable List<IElement<?>> baseList;
	private @Nullable List<String> baseListGroupIds;
	private @Nullable Map<String, List<IElement<?>>> childrenByGroupId;
	private @Nullable ViewerProjection<ITypedIngredient<?>> projection;
	private @Nullable List<ITypedIngredient<?>> cachedFullList;
	private String searchTextForCache = "";

	public JeiIngredientFilterController(
		Supplier<String> filterText,
		Function<String, Stream<ITypedIngredient<?>>> uncachedIngredients,
		Runnable notifyListeners,
		IIngredientManager ingredientManager,
		Supplier<List<IElement<?>>> displayCache,
		Consumer<List<IElement<?>>> setDisplayCache,
		PlatformHooks hooks
	) {
		this.filterText = filterText;
		this.uncachedIngredients = uncachedIngredients;
		this.notifyListeners = notifyListeners;
		this.ingredientManager = ingredientManager;
		this.displayCache = displayCache;
		this.setDisplayCache = setDisplayCache;
		this.hooks = hooks;
	}

	public void initialize() {
		if (fullChangeSubscription != null) fullChangeSubscription.close();
		if (structureChangeSubscription != null) structureChangeSubscription.close();
		if (enabledChangeSubscription != null) enabledChangeSubscription.close();
		if (kubeJsChangeSubscription != null) kubeJsChangeSubscription.close();
		GroupRegistry.clearJeiAllItems();
		GroupRegistry.clearJeiAllFluids();
		GroupRegistry.clearKubeJsGroups();
		viewerIndex.reset();
		viewerIndex.configureRebuild(this::buildConfiguredIndex, Minecraft.getInstance()::execute,
			this::rebuildCompleted);
		fullChangeSubscription = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.FULL,
			() -> handleIndexedChange(GroupChangeEvent.Kind.FULL));
		structureChangeSubscription = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.STRUCTURE,
			() -> handleIndexedChange(GroupChangeEvent.Kind.STRUCTURE));
		enabledChangeSubscription = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.ENABLED,
			() -> handleIndexedChange(GroupChangeEvent.Kind.ENABLED));
		kubeJsChangeSubscription = GroupChangeEvent.subscribe(GroupChangeEvent.Kind.KUBEJS_REPLACE,
			() -> handleIndexedChange(GroupChangeEvent.Kind.KUBEJS_REPLACE));
		cachedFullList = null;
	}

	public List<IElement<?>> getElements() {
		List<IElement<?>> current = displayCache.get();
		if (current == null) {
			String raw = filterText.get();
			searchTextForCache = raw;
			buildStructureCache(uncachedIngredients.apply(raw.toLowerCase(Locale.ROOT)).toList());
			current = buildDisplayFromCache();
			setDisplayCache.accept(current);
		}
		return current;
	}

	private void handleIndexedChange(GroupChangeEvent.Kind kind) {
		clearStructureCaches();
		notifyListeners.run();
	}

	private JeiViewerGroupIndex.Generation buildConfiguredIndex() {
		List<ITypedIngredient<?>> snapshot = cachedFullList;
		if (snapshot == null) snapshot = uncachedIngredients.apply("").toList();
		cachedFullList = snapshot;
		return buildIngredientGroupIndex(snapshot);
	}

	private void rebuildCompleted() {
		clearStructureCaches();
		notifyListeners.run();
	}

	private void clearStructureCaches() {
		GroupBackgroundRenderer.clear();
		setDisplayCache.accept(null);
		baseList = null;
		baseListGroupIds = null;
		childrenByGroupId = null;
		projection = null;
		searchTextForCache = "";
	}

	private void toggleAndRebuildDisplay() {
		GroupBackgroundRenderer.clear();
		if (baseList != null) {
			projection = projection.withExpansion(GroupRegistry::isExpandedById);
			setDisplayCache.accept(buildDisplayFromCache());
		} else {
			setDisplayCache.accept(null);
		}
		notifyListeners.run();
	}

	private JeiViewerGroupIndex.Generation buildIngredientGroupIndex(List<ITypedIngredient<?>> all) {
		long traceStart = hooks.traceBuilds() ? PerformanceTrace.begin() : 0L;
		List<GroupDefinition> allGroups = GroupRegistry.getAllIncludingKubeJs();
		ItemOwnershipBuildResult itemResult =
			IngredientFilterHelper.buildItemOwnershipResult(all, allGroups);
		Map<ITypedIngredient<?>, GroupDefinition> index = itemResult.ingredientGroupIndex();
		Map<ITypedIngredient<?>, List<String>> candidateGroups = new IdentityHashMap<>();
		for (GroupDefinition group : allGroups) {
			for (IngredientFilterItemIndex.ItemEntry entry : itemResult.fullMatchEntriesByGroup().get(group.id())) {
				candidateGroups.computeIfAbsent(entry.typed(), ignored -> new ArrayList<>()).add(group.id());
			}
		}
		Map<String, List<Object>> fluidsByGroup = new HashMap<>();
		Map<String, List<Object>> fullMatchFluidsByGroup = new HashMap<>();
		Map<String, List<GenericIngredientRef>> fullMatchGenericByGroup = new HashMap<>();
		List<GroupDefinition> fluidGroups = allGroups.stream().filter(GroupDefinition::hasFluidFilters).toList();
		List<GroupDefinition> genericGroups = allGroups.stream().filter(GroupDefinition::hasGenericFilters).toList();

		for (ITypedIngredient<?> typed : all) {
			if (typed.getItemStack().isPresent()) continue;
			Object fluid = (!fluidGroups.isEmpty() || hooks.probeFluidWithoutGroups())
				&& hooks.hasFluidType() ? hooks.fluidIngredient(typed) : null;
			if (fluid != null) {
				GroupDefinition firstMatch = null;
				for (GroupDefinition group : fluidGroups) {
					if (!GroupMatcher.matchesFluidIgnoringEnabled(group, fluid)) continue;
					candidateGroups.computeIfAbsent(typed, ignored -> new ArrayList<>()).add(group.id());
					if (firstMatch == null && group.enabled()) {
						firstMatch = group;
						index.put(typed, group);
						fluidsByGroup.computeIfAbsent(group.id(), ignored -> new ArrayList<>()).add(fluid);
					}
					fullMatchFluidsByGroup.computeIfAbsent(group.id(), ignored -> new ArrayList<>()).add(fluid);
				}
				continue;
			}
			if (!genericGroups.isEmpty() && hooks.canIndexGeneric(ingredientManager)) {
				hooks.genericProbe().index(typed, ingredientManager, index, genericGroups,
					fullMatchGenericByGroup, candidateGroups);
			}
		}

		for (GroupDefinition group : allGroups) {
			fluidsByGroup.putIfAbsent(group.id(), List.of());
			fullMatchFluidsByGroup.putIfAbsent(group.id(), List.of());
			fullMatchGenericByGroup.putIfAbsent(group.id(), List.of());
		}
		Map<String, Set<String>> fluidIds = new HashMap<>();
		for (var entry : fluidsByGroup.entrySet()) {
			for (Object fluid : entry.getValue()) {
				String id = hooks.fluidId(fluid);
				if (id != null) fluidIds.computeIfAbsent(id, ignored -> new HashSet<>()).add(entry.getKey());
			}
		}
		GroupCandidateIndex candidates = JeiViewerAdapter.instance().buildOwnershipIndexFromMatches(
			all, ingredientManager, allGroups, candidateGroups);
		JeiViewerGroupIndex.Generation generation = new JeiViewerGroupIndex.Generation(
			candidates,
			IngredientFilterHelper.toStackMap(itemResult.resolvedEntriesByGroup()),
			fluidsByGroup,
			IngredientFilterHelper.toStackMap(itemResult.fullMatchEntriesByGroup()),
			fullMatchFluidsByGroup,
			fullMatchGenericByGroup,
			itemResult.itemIdToGroupIds(),
			fluidIds
		);
		if (hooks.traceBuilds()) {
			PerformanceTrace.logIfSlow("MixinIngredientFilter.buildIngredientGroupIndex", traceStart, 20,
				"ingredients=" + all.size() + " indexSize=" + index.size()
					+ " fluidGroups=" + fluidGroups.size() + " genericGroups=" + genericGroups.size()
					+ " resolvedFluids=" + fluidsByGroup.size() + " fullMatchFluids="
					+ fullMatchFluidsByGroup.size() + " fullMatchGeneric=" + fullMatchGenericByGroup.size()
					+ " fluidIds=" + fluidIds.size());
		}
		return generation;
	}

	private void buildStructureCache(List<ITypedIngredient<?>> ingredients) {
		long traceStart = hooks.traceBuilds() ? PerformanceTrace.begin() : 0L;
		List<ITypedIngredient<?>> all = cachedFullList;
		if (hooks.fluidCachePolicy() == FluidCachePolicy.INDEPENDENT) {
			if (all == null) {
				all = uncachedIngredients.apply("").toList();
				cachedFullList = all;
			}
			if (GroupRegistry.isJeiAllItemsEmpty()) {
				GroupRegistry.setJeiAllItems(all.stream().flatMap(i -> i.getItemStack().stream()).toList());
			}
			if (hooks.hasFluidType() && GroupRegistry.isJeiAllFluidsEmpty()) {
				GroupRegistry.setJeiAllFluids(extractFluids(all));
			}
		} else if (GroupRegistry.isJeiAllItemsEmpty()) {
			if (all == null) all = uncachedIngredients.apply("").toList();
			cachedFullList = all;
			GroupRegistry.setJeiAllItems(all.stream().flatMap(i -> i.getItemStack().stream()).toList());
			if (hooks.hasFluidType()) GroupRegistry.setJeiAllFluids(extractFluids(all));
		} else if (all == null) {
			all = uncachedIngredients.apply("").toList();
			cachedFullList = all;
		}

		if (hooks.beforeIndex(all, ingredientManager)) {
			viewerIndex.requestRebuild(GroupRegistry.getAllIncludingKubeJs());
		}
		List<GroupDefinition> groups = GroupRegistry.getAllIncludingKubeJs();
		viewerIndex.ensureReadyAsync(groups);
		GroupCandidateIndex ingredientGroupIndex = viewerIndex.candidates().orElse(null);
		if (ingredientGroupIndex == null) {
			ingredientGroupIndex = new GroupCandidateIndex(Map.of(),
				groups.stream().collect(java.util.stream.Collectors.toMap(GroupDefinition::id,
					Function.identity(), (left, right) -> left, LinkedHashMap::new)), 0, 0, 0);
		}

		ViewerProjection<ITypedIngredient<?>> projected = JeiViewerAdapter.instance().project(
			ingredients, searchTextForCache, Services.CONFIG.searchUngroupSmallGroups(),
			Services.CONFIG.searchUngroupThreshold(), groups, GroupRegistry::isExpandedById, ingredientGroupIndex);
		List<IElement<?>> newBaseList = new ArrayList<>();
		List<String> newGroupIds = new ArrayList<>();
		Map<String, List<IElement<?>>> newChildren = new HashMap<>();
		for (ViewerProjection.Entry<ITypedIngredient<?>> entry : projected.entries()) {
			switch (entry) {
				case ViewerProjection.IngredientEntry<ITypedIngredient<?>> ingredient -> {
					newBaseList.add(new IngredientElement<>(ingredient.ingredient().entry()));
					newGroupIds.add(null);
				}
				case ViewerProjection.GroupHeader<ITypedIngredient<?>> header -> {
					newBaseList.add(createGroupHeader(header, this::toggleAndRebuildDisplay));
					newGroupIds.add(header.group().id());
					newChildren.put(header.group().id(), createChildElements(header));
				}
			}
		}
		baseList = newBaseList;
		baseListGroupIds = newGroupIds;
		childrenByGroupId = newChildren;
		projection = projected;
		if (hooks.traceBuilds()) {
			PerformanceTrace.logIfSlow("MixinIngredientFilter.buildStructureCache", traceStart, 20,
				"filtered=" + ingredients.size() + " cachedFull=" + (all == null ? 0 : all.size())
					+ " base=" + newBaseList.size() + " groups=" + newChildren.size()
					+ " enabledGroups=" + groups.stream().filter(GroupDefinition::enabled).count()
					+ " pending=" + (!viewerIndex.whenReady().isDone()) + " indexReady=" + viewerIndex.ready());
		}
	}

	private List<Object> extractFluids(List<ITypedIngredient<?>> all) {
		List<Object> fluids = new ArrayList<>();
		for (ITypedIngredient<?> typed : all) {
			Object fluid = hooks.fluidIngredient(typed);
			if (fluid != null) fluids.add(fluid);
		}
		return List.copyOf(fluids);
	}

	private List<IElement<?>> buildDisplayFromCache() {
		Set<String> expanded = new HashSet<>();
		if (projection != null) {
			for (ViewerProjection.Entry<ITypedIngredient<?>> entry : projection.entries()) {
				if (entry instanceof ViewerProjection.GroupHeader<ITypedIngredient<?>> header && header.expanded()) {
					expanded.add(header.group().id());
				}
			}
		}
		List<IElement<?>> result = new ArrayList<>(baseList.size());
		for (int i = 0; i < baseList.size(); i++) {
			result.add(baseList.get(i));
			String groupId = baseListGroupIds.get(i);
			if (groupId != null && expanded.contains(groupId)) result.addAll(childrenByGroupId.get(groupId));
		}
		return result;
	}

	private IElement<?> createGroupHeader(ViewerProjection.GroupHeader<ITypedIngredient<?>> header, Runnable onToggle) {
		GroupDefinition group = header.group();
		List<ITypedIngredient<?>> display = JeiViewerAdapter.instance().assembleHeaderIcons(
			header.iconIds(), header.fallbackIconIngredients());
		GroupIcon icon = new GroupIcon(group.id(), group.displayName().key(), group.displayName().fallback(), display);
		ITypedIngredient<GroupIcon> typedIcon = TypedIngredient.createUnvalidated(GroupIcon.TYPE, icon);
		List<GroupPreviewEntry> preview = new ArrayList<>(header.children().size());
		List<ITypedIngredient<?>> generic = new ArrayList<>();
		for (ViewerIngredient<ITypedIngredient<?>> child : header.children()) {
			switch (child.kind()) {
				case ITEM -> child.entry().getItemStack().ifPresent(stack -> preview.add(GroupPreviewEntry.ofItem(stack)));
				case FLUID -> {
					Object fluid = hooks.previewFluid(child.entry());
					if (fluid != null) preview.add(GroupPreviewEntry.ofFluid(fluid));
				}
				case GENERIC -> generic.add(child.entry());
			}
		}
		preview.addAll(GroupPreviewEntry.fromTypedIngredients(generic));
		return new GroupHeaderElement(typedIcon,
			buildCountLabel(header.itemCount(), header.fluidCount(), header.genericCount()), preview, onToggle);
	}

	private List<IElement<?>> createChildElements(ViewerProjection.GroupHeader<ITypedIngredient<?>> header) {
		List<IElement<?>> children = new ArrayList<>(header.children().size());
		for (ViewerIngredient<ITypedIngredient<?>> child : header.children()) {
			switch (child.kind()) {
				case ITEM -> children.add(new GroupChildElement(child.entry().castToItemStackType(), header.group().id()));
				case FLUID -> children.add(hooks.createFluidChild(child.entry(), header.group().id()));
				case GENERIC -> children.add(wrapGenericChild(child.entry(), header.group().id()));
			}
		}
		return children;
	}

	private static Component buildCountLabel(int itemCount, int fluidCount, int genericCount) {
		MutableComponent result = null;
		if (itemCount > 0) result = Component.translatable(ModTranslationKeys.COUNT_ITEMS, itemCount);
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

	@SuppressWarnings("unchecked")
	private static <T> IElement<?> wrapGenericChild(ITypedIngredient<?> ingredient, String groupId) {
		return new GenericChildElement<>((ITypedIngredient<T>) ingredient, groupId);
	}

	public enum FluidCachePolicy { INDEPENDENT, WITH_ITEMS }

	public interface PlatformHooks {
		@Nullable Object fluidIngredient(ITypedIngredient<?> typed);
		default @Nullable Object previewFluid(ITypedIngredient<?> typed) { return fluidIngredient(typed); }
		boolean hasFluidType();
		String fluidId(Object fluid);
		IElement<?> createFluidChild(ITypedIngredient<?> typed, String groupId);
		GenericProbe genericProbe();
		FluidCachePolicy fluidCachePolicy();
		default boolean canIndexGeneric(IIngredientManager manager) { return true; }
		default boolean probeFluidWithoutGroups() { return false; }
		default boolean beforeIndex(List<ITypedIngredient<?>> all, IIngredientManager manager) { return false; }
		default boolean traceBuilds() { return false; }
	}

	@FunctionalInterface
	public interface GenericProbe {
		void index(ITypedIngredient<?> typed, IIngredientManager manager,
			Map<ITypedIngredient<?>, GroupDefinition> index, List<GroupDefinition> groups,
			Map<String, List<GenericIngredientRef>> fullMatches,
			Map<ITypedIngredient<?>, List<String>> candidateGroups);
	}

	public static GenericProbe castGenericProbe() {
		return JeiIngredientFilterController::indexGenericByCast;
	}

	public static GenericProbe exactGenericProbe() {
		return JeiIngredientFilterController::indexGenericByExactType;
	}

	@SuppressWarnings("unchecked")
	private static <T> void indexGenericByCast(ITypedIngredient<?> typed, IIngredientManager manager,
		Map<ITypedIngredient<?>, GroupDefinition> index, List<GroupDefinition> groups,
		Map<String, List<GenericIngredientRef>> fullMatches,
		Map<ITypedIngredient<?>, List<String>> candidateGroups) {
		for (Map.Entry<String, IIngredientType<?>> entry : JeiIngredientTypes.getAll().entrySet()) {
			IIngredientType<T> type = (IIngredientType<T>) entry.getValue();
			ITypedIngredient<T> cast = typed.cast(type);
			if (cast == null) continue;
			indexGeneric(entry.getKey(), type, cast.getIngredient(), typed, manager, index, groups,
				fullMatches, candidateGroups);
			return;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void indexGenericByExactType(ITypedIngredient<?> typed, IIngredientManager manager,
		Map<ITypedIngredient<?>, GroupDefinition> index, List<GroupDefinition> groups,
		Map<String, List<GenericIngredientRef>> fullMatches,
		Map<ITypedIngredient<?>, List<String>> candidateGroups) {
		for (Map.Entry<String, IIngredientType<?>> entry : JeiIngredientTypes.getAll().entrySet()) {
			IIngredientType<T> type = (IIngredientType<T>) entry.getValue();
			if (!typed.getType().equals(type)) continue;
			indexGeneric(entry.getKey(), type, ((ITypedIngredient<T>) typed).getIngredient(), typed,
				manager, index, groups, fullMatches, candidateGroups);
			break;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void indexGeneric(String typeId, IIngredientType<T> type, T ingredient,
		ITypedIngredient<?> typed, IIngredientManager manager, Map<ITypedIngredient<?>, GroupDefinition> index,
		List<GroupDefinition> groups, Map<String, List<GenericIngredientRef>> fullMatches,
		Map<ITypedIngredient<?>, List<String>> candidateGroups) {
		IIngredientHelper<T> helper = manager.getIngredientHelper(type);
		GroupDefinition firstMatch = null;
		for (GroupDefinition group : groups) {
			if (!GroupMatcher.matchesGenericIgnoringEnabled(group, typeId, ingredient, helper)) continue;
			candidateGroups.computeIfAbsent(typed, ignored -> new ArrayList<>()).add(group.id());
			if (firstMatch == null && group.enabled()) {
				firstMatch = group;
				index.put(typed, group);
			}
			fullMatches.computeIfAbsent(group.id(), ignored -> new ArrayList<>())
				.add(new GenericIngredientRef(typeId, (IIngredientType<Object>) type, ingredient));
		}
	}
}
