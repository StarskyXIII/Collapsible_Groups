package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.JeiIngredientTypes;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerAdapter;
import com.starskyxiii.collapsible_groups.compat.jei.JeiFluidIngredient;
import com.starskyxiii.collapsible_groups.compat.jei.JeiViewerGroupIndex;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.element.GenericChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupChildElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupHeaderElement;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.GroupIndexUpdate;
import com.starskyxiii.collapsible_groups.viewer.ViewerProjection;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
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
	private volatile @Nullable List<ITypedIngredient<?>> cachedFullList;
	private long sourceRevision;
	private @Nullable IndexedSource indexedSource;
	private String searchTextForCache = "";

	private record IndexedSource(long revision, List<ITypedIngredient<?>> ingredients,
		JeiViewerGroupIndex.Generation generation) {}

	private record GroupMatches(GroupCandidateIndex candidates, Map<String, List<ItemStack>> items,
		Map<String, List<Object>> fluids, Map<String, List<GenericIngredientRef>> generic) {}

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
		JeiIngredientSourceState.deactivate();
		sourceRevision++;
		indexedSource = null;
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
		viewerIndex.configureSourceInvalidation(this::invalidateSource);
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

	private synchronized void invalidateSource() {
		sourceRevision++;
		indexedSource = null;
		cachedFullList = null;
		GroupRegistry.clearJeiAllItems();
		GroupRegistry.clearJeiAllFluids();
		clearStructureCaches();
	}

	private JeiViewerGroupIndex.Generation buildConfiguredIndex() {
		long revision;
		List<ITypedIngredient<?>> snapshot;
		synchronized (this) {
			revision = sourceRevision;
			snapshot = cachedFullList;
		}
		if (snapshot == null) snapshot = uncachedIngredients.apply("").toList();
		synchronized (this) {
			if (sourceRevision == revision) cachedFullList = snapshot;
		}
		return buildIngredientGroupIndex(snapshot, revision);
	}

	private void rebuildCompleted() {
		clearStructureCaches();
		notifyListeners.run();
	}

	private void clearStructureCaches() {
		setDisplayCache.accept(null);
		baseList = null;
		baseListGroupIds = null;
		childrenByGroupId = null;
		projection = null;
		searchTextForCache = "";
	}

	private void toggleAndRebuildDisplay() {
		if (baseList != null) {
			projection = projection.withExpansion(GroupRegistry::isExpandedById);
			setDisplayCache.accept(buildDisplayFromCache());
		} else {
			setDisplayCache.accept(null);
		}
		notifyListeners.run();
	}

	private JeiViewerGroupIndex.Generation buildIngredientGroupIndex(List<ITypedIngredient<?>> all, long revision) {
		long traceStart = hooks.traceBuilds() ? PerformanceTrace.begin() : 0L;
		List<GroupDefinition> allGroups = GroupRegistry.getAllIncludingKubeJs();
		JeiViewerGroupIndex.Generation previous = null;
		synchronized (this) {
			if (indexedSource != null && indexedSource.revision() == revision && indexedSource.ingredients() == all) {
				previous = indexedSource.generation();
			}
		}
		JeiViewerAdapter.ProjectionContext context = previous == null
			? JeiViewerAdapter.instance().updateBootstrap(all, ingredientManager) : previous.projectionContext();
		GroupIndexUpdate update = GroupIndexUpdate.between(previous == null ? null : previous.candidates(), allGroups);
		List<GroupDefinition> changedGroups = update.changedGroups();
		GroupMatches changed = buildChangedGroups(all, changedGroups, context);
		JeiViewerGroupIndex.Generation generation = JeiViewerGroupIndex.completeGeneration(
			update.mergeCandidates(previous == null ? null : previous.candidates(), changed.candidates()),
			update.mergeMatches(previous == null ? Map.of() : previous.fullMatchItems(), changed.items()),
			update.mergeMatches(previous == null ? Map.of() : previous.fullMatchFluids(), changed.fluids()),
			update.mergeMatches(previous == null ? Map.of() : previous.fullMatchGeneric(), changed.generic()),
			context, update.groups());
		synchronized (this) {
			if (sourceRevision == revision) indexedSource = new IndexedSource(revision, all, generation);
		}
		if (hooks.traceBuilds()) {
			PerformanceTrace.logIfSlow("MixinIngredientFilter.buildIngredientGroupIndex", traceStart, 0,
				"ingredients=" + all.size() + " groups=" + allGroups.size()
					+ " evaluated=" + changedGroups.size() + " reused=" + update.reusedIds().size());
		}
		return generation;
	}

	private GroupMatches buildChangedGroups(List<ITypedIngredient<?>> all, List<GroupDefinition> allGroups,
		JeiViewerAdapter.ProjectionContext context) {
        if (allGroups.isEmpty()) return new GroupMatches(
            new GroupCandidateIndex(Map.of(), Map.of(), 0, context.universe().ordered().size(), 0), Map.of(), Map.of(), Map.of());
		ItemOwnershipBuildResult itemResult =
			IngredientFilterHelper.buildItemOwnershipResult(all, allGroups);
		Map<ITypedIngredient<?>, GroupDefinition> index = itemResult.ingredientGroupIndex();
		Map<ITypedIngredient<?>, List<String>> candidateGroups = new IdentityHashMap<>();
		Map<String, String> failures = new LinkedHashMap<>(itemResult.evaluationFailures());
		for (GroupDefinition group : allGroups) {
			for (IngredientFilterItemIndex.ItemEntry entry : itemResult.fullMatchEntriesByGroup().get(group.id())) {
				candidateGroups.computeIfAbsent(entry.typed(), ignored -> new ArrayList<>()).add(group.id());
			}
		}
		Map<String, List<Object>> fullMatchFluidsByGroup = new HashMap<>();
		Map<String, List<GenericIngredientRef>> fullMatchGenericByGroup = new HashMap<>();
		List<GroupDefinition> fluidGroups = allGroups.stream().filter(GroupDefinition::hasFluidFilters).toList();
		List<GroupDefinition> genericGroups = allGroups.stream().filter(GroupDefinition::hasGenericFilters).toList();

		for (ITypedIngredient<?> typed : all) {
			if (typed.getItemStack().isPresent()) continue;
			JeiFluidIngredient fluid = (!fluidGroups.isEmpty() || hooks.probeFluidWithoutGroups())
				&& hooks.hasFluidType() ? JeiIngredientTypes.fluidIngredient(typed) : null;
			if (fluid != null) {
				for (GroupDefinition group : fluidGroups) {
					if (com.starskyxiii.collapsible_groups.viewer.GroupEvaluations.evaluate(group, fluid.fluid().view(), failures)
						!= com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.Evaluation.MATCH) continue;
					candidateGroups.computeIfAbsent(typed, ignored -> new ArrayList<>()).add(group.id());
					fullMatchFluidsByGroup.computeIfAbsent(group.id(), ignored -> new ArrayList<>()).add(fluid.viewerValue());
				}
				continue;
			}
			if (!genericGroups.isEmpty() && hooks.canIndexGeneric(ingredientManager)) {
				hooks.genericProbe().index(typed, ingredientManager, index, genericGroups,
					fullMatchGenericByGroup, candidateGroups, failures);
			}
		}

		for (GroupDefinition group : allGroups) {
			fullMatchFluidsByGroup.putIfAbsent(group.id(), List.of());
			fullMatchGenericByGroup.putIfAbsent(group.id(), List.of());
		}
		JeiViewerAdapter.PreparedOwnershipBuild prepared =
			JeiViewerAdapter.instance().buildOwnershipIndexFromMatches(
			context, allGroups, candidateGroups, failures);
		return new GroupMatches(prepared.candidates(),
			IngredientFilterHelper.toStackMap(itemResult.fullMatchEntriesByGroup()),
			fullMatchFluidsByGroup,
			fullMatchGenericByGroup
		);
	}

	private void buildStructureCache(List<ITypedIngredient<?>> ingredients) {
		long traceStart = hooks.traceBuilds() ? PerformanceTrace.begin() : 0L;
		JeiIngredientSourceState.SourceToken expected;
		long revision;
		List<ITypedIngredient<?>> all;
		synchronized (this) {
			expected = JeiIngredientSourceState.capture(ingredientManager);
			revision = sourceRevision;
			all = cachedFullList;
		}
		if (all == null) all = uncachedIngredients.apply("").toList();
		synchronized (this) {
			if (sourceRevision != revision) {
				installRawStructure(ingredients);
				return;
			}
			cachedFullList = all;
		}
		boolean needsItems = GroupRegistry.isJeiAllItemsEmpty();
		boolean needsFluids = hooks.hasFluidType() && GroupRegistry.isJeiAllFluidsEmpty()
			&& (needsItems || hooks.fluidCachePolicy() == FluidCachePolicy.INDEPENDENT);
		JeiIngredientSourceState.install(expected,
			needsItems ? all.stream().flatMap(i -> i.getItemStack().stream()).toList() : null,
			needsFluids ? extractFluids(all) : null);

		if (hooks.beforeIndex(all, ingredientManager)) {
			viewerIndex.requestRebuild(GroupRegistry.getAllIncludingKubeJs());
		}
		List<GroupDefinition> groups = GroupRegistry.getAllIncludingKubeJs();
		viewerIndex.ensureReadyAsync(groups);
		JeiViewerGroupIndex.ProjectableSnapshot readyGeneration = viewerIndex.projectableSnapshot().orElse(null);
		if (readyGeneration == null) {
			installRawStructure(ingredients);
			if (hooks.traceBuilds()) {
				PerformanceTrace.logIfSlow("MixinIngredientFilter.buildStructureCache", traceStart, 20,
					"filtered=" + ingredients.size() + " cachedFull=" + (all == null ? 0 : all.size())
						+ " base=" + ingredients.size() + " groups=0"
						+ " enabledGroups=" + groups.stream().filter(GroupDefinition::enabled).count()
						+ " pending=" + (!viewerIndex.whenReady().isDone()) + " indexReady=false rawFallback=true");
			}
			return;
		}

		ViewerProjection<ITypedIngredient<?>> projected = JeiViewerAdapter.instance().project(
			ingredients, searchTextForCache, Services.CONFIG.searchUngroupSmallGroups(),
			Services.CONFIG.searchUngroupThreshold(), readyGeneration.groups(), GroupRegistry::isExpandedById,
			readyGeneration.projectionContext(), readyGeneration.candidates());
		List<IElement<?>> newBaseList = new ArrayList<>();
		List<String> newGroupIds = new ArrayList<>();
		Map<String, List<IElement<?>>> newChildren = new HashMap<>();
		for (ViewerProjection.Entry<ITypedIngredient<?>> entry : projected.entries()) {
			if (entry instanceof ViewerProjection.IngredientEntry<ITypedIngredient<?>> ingredient) {
				newBaseList.add(new IngredientElement<>(ingredient.ingredient().entry()));
				newGroupIds.add(null);
			} else if (entry instanceof ViewerProjection.GroupHeader<ITypedIngredient<?>> header) {
				newBaseList.add(createGroupHeader(header, this::toggleAndRebuildDisplay));
				newGroupIds.add(header.group().id());
				newChildren.put(header.group().id(), createChildElements(header));
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

	private void installRawStructure(List<ITypedIngredient<?>> ingredients) {
		RawStructure rawStructure = buildRawStructure(ingredients);
		baseList = rawStructure.elements();
		baseListGroupIds = rawStructure.groupIds();
		childrenByGroupId = rawStructure.childrenByGroupId();
		projection = null;
	}

	static RawStructure buildRawStructure(List<ITypedIngredient<?>> ingredients) {
		List<IElement<?>> raw = new ArrayList<>(ingredients.size());
		List<String> groupIds = new ArrayList<>(ingredients.size());
		for (ITypedIngredient<?> ingredient : ingredients) {
			raw.add(new IngredientElement<>(ingredient));
			groupIds.add(null);
		}
		return new RawStructure(List.copyOf(raw), Collections.unmodifiableList(groupIds), Map.of());
	}

	record RawStructure(
		List<IElement<?>> elements,
		List<String> groupIds,
		Map<String, List<IElement<?>>> childrenByGroupId
	) {}

	private List<Object> extractFluids(List<ITypedIngredient<?>> all) {
		List<Object> fluids = new ArrayList<>();
		for (ITypedIngredient<?> typed : all) {
			JeiFluidIngredient fluid = JeiIngredientTypes.fluidIngredient(typed);
			if (fluid != null) fluids.add(fluid.viewerValue());
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
		ITypedIngredient<GroupIcon> typedIcon = ingredientManager
			.createTypedIngredient(GroupIcon.TYPE, icon)
			.orElseThrow(() -> new IllegalStateException(
				"JEI could not create a GroupIcon typed ingredient; GroupIcon.TYPE must be registered first"));
		List<GroupPreviewEntry> preview = new ArrayList<>(header.children().size());
		List<ITypedIngredient<?>> generic = new ArrayList<>();
		for (ViewerIngredient<ITypedIngredient<?>> child : header.children()) {
			switch (child.kind()) {
				case ITEM -> child.entry().getItemStack().ifPresent(stack -> preview.add(GroupPreviewEntry.ofItem(stack)));
				case FLUID -> {
					JeiFluidIngredient fluid = JeiIngredientTypes.fluidIngredient(child.entry());
					if (fluid != null) preview.add(com.starskyxiii.collapsible_groups.compat.jei.preview.JeiGroupPreviewEntries.ofFluid(fluid.viewerValue()));
				}
				case GENERIC -> generic.add(child.entry());
			}
		}
		preview.addAll(com.starskyxiii.collapsible_groups.compat.jei.preview.JeiGroupPreviewEntries.fromTypedIngredients(generic));
		return new GroupHeaderElement(typedIcon,
			buildCountLabel(header.itemCount(), header.fluidCount(), header.genericCount()), preview, onToggle);
	}

	private List<IElement<?>> createChildElements(ViewerProjection.GroupHeader<ITypedIngredient<?>> header) {
		List<IElement<?>> children = new ArrayList<>(header.children().size());
		for (ViewerIngredient<ITypedIngredient<?>> child : header.children()) {
			switch (child.kind()) {
				case ITEM -> children.add(new GroupChildElement(itemTyped(child.entry()), header.group().id()));
				case FLUID -> children.add(hooks.createFluidChild(child.entry(), header.group().id()));
				case GENERIC -> children.add(wrapGenericChild(child.entry(), header.group().id()));
			}
		}
		return children;
	}

	private ITypedIngredient<ItemStack> itemTyped(ITypedIngredient<?> ingredient) {
		ItemStack stack = ingredient.getItemStack().orElseThrow();
		return ingredientManager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack).orElseThrow();
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
		boolean hasFluidType();
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
			Map<ITypedIngredient<?>, List<String>> candidateGroups, Map<String, String> failures);
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
		Map<ITypedIngredient<?>, List<String>> candidateGroups, Map<String, String> failures) {
		for (Map.Entry<String, IIngredientType<?>> entry : JeiIngredientTypes.getAll().entrySet()) {
			IIngredientType<T> type = (IIngredientType<T>) entry.getValue();
			T cast = typed.getIngredient(type).orElse(null);
			if (cast == null) continue;
			indexGeneric(entry.getKey(), type, cast, typed, manager, index, groups,
				fullMatches, candidateGroups, failures);
			return;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void indexGenericByExactType(ITypedIngredient<?> typed, IIngredientManager manager,
		Map<ITypedIngredient<?>, GroupDefinition> index, List<GroupDefinition> groups,
		Map<String, List<GenericIngredientRef>> fullMatches,
		Map<ITypedIngredient<?>, List<String>> candidateGroups, Map<String, String> failures) {
		for (Map.Entry<String, IIngredientType<?>> entry : JeiIngredientTypes.getAll().entrySet()) {
			IIngredientType<T> type = (IIngredientType<T>) entry.getValue();
			if (!typed.getType().equals(type)) continue;
			indexGeneric(entry.getKey(), type, ((ITypedIngredient<T>) typed).getIngredient(), typed,
				manager, index, groups, fullMatches, candidateGroups, failures);
			break;
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> void indexGeneric(String typeId, IIngredientType<T> type, T ingredient,
		ITypedIngredient<?> typed, IIngredientManager manager, Map<ITypedIngredient<?>, GroupDefinition> index,
		List<GroupDefinition> groups, Map<String, List<GenericIngredientRef>> fullMatches,
		Map<ITypedIngredient<?>, List<String>> candidateGroups, Map<String, String> failures) {
		IIngredientHelper<T> helper = manager.getIngredientHelper(type);
		GroupDefinition firstMatch = null;
		for (GroupDefinition group : groups) {
			if (com.starskyxiii.collapsible_groups.viewer.GroupEvaluations.evaluate(group,
				new GenericJeiIngredientView<>(typeId, ingredient, helper), failures)
				!= com.starskyxiii.collapsible_groups.group.filter.CompiledFilter.Evaluation.MATCH) continue;
			candidateGroups.computeIfAbsent(typed, ignored -> new ArrayList<>()).add(group.id());
			if (firstMatch == null && com.starskyxiii.collapsible_groups.group.GroupRepository.isActive(group)) {
				firstMatch = group;
				index.put(typed, group);
			}
			fullMatches.computeIfAbsent(group.id(), ignored -> new ArrayList<>())
				.add(new GenericIngredientRef(typeId, (IIngredientType<Object>) type, ingredient));
		}
	}
}
