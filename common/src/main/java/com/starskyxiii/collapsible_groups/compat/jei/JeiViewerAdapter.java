package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIcon;
import com.starskyxiii.collapsible_groups.compat.jei.element.GroupIconRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GenericJeiIngredientView;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.JeiRuntimeHolder;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.PerformanceTrace;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.GroupExpansionState;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.GroupProjectionEngine;
import com.starskyxiii.collapsible_groups.viewer.ViewerAdapter;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import com.starskyxiii.collapsible_groups.viewer.ViewerBookmarkPolicy;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapContext;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import com.starskyxiii.collapsible_groups.viewer.ViewerOverlayHook;
import com.starskyxiii.collapsible_groups.viewer.ViewerPresentation;
import com.starskyxiii.collapsible_groups.viewer.ViewerProjection;
import com.starskyxiii.collapsible_groups.viewer.ViewerRegistration;
import com.starskyxiii.collapsible_groups.viewer.ViewerSearchSnapshot;
import com.starskyxiii.collapsible_groups.viewer.ViewerSearchState;
import com.starskyxiii.collapsible_groups.viewer.ViewerUniverseProvider;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** JEI implementation of the recipe-viewer contract. */
public final class JeiViewerAdapter implements ViewerAdapter<ITypedIngredient<?>, Component> {
	private static final JeiViewerAdapter INSTANCE = new JeiViewerAdapter();
	private static final ViewerOverlayHook OVERLAY = new StandardOverlayHook();
	private static final ViewerBookmarkPolicy<ITypedIngredient<?>> BOOKMARKS =
		ViewerBookmarkPolicy.headersCannotBeBookmarked();

	@Nullable private static ViewerRegistration runtimeRegistration;

	private final JeiSearchState searchState = new JeiSearchState();
	private final JeiBootstrapContext bootstrapContext = new JeiBootstrapContext();
	private final ViewerPresentation<ITypedIngredient<?>, Component> presentation = new JeiPresentation();

	private JeiViewerAdapter() {}

	public static JeiViewerAdapter instance() {
		return INSTANCE;
	}

	public static synchronized void registerRuntime() {
		if (runtimeRegistration != null) runtimeRegistration.close();
		runtimeRegistration = ViewerLifecycleCoordinator.global().register(INSTANCE);
	}

	public static synchronized void unregisterRuntime() {
		if (runtimeRegistration != null) {
			runtimeRegistration.close();
			runtimeRegistration = null;
		}
		INSTANCE.bootstrapContext.clear();
		JeiIngredientTypeDiscovery.clearRuntimeTypes();
		JeiHeaderIconResolver.clearWarnings();
	}

	@Override
	public String id() {
		return "jei";
	}

	@Override
	public ViewerUniverseProvider<ITypedIngredient<?>> universeProvider() {
		return () -> {
			IJeiRuntime runtime = JeiRuntimeHolder.get();
			if (runtime == null) return bootstrapContext.universe();
			return createUniverse(enumerate(runtime.getIngredientManager()), runtime.getIngredientManager());
		};
	}

	@Override
	public ViewerSearchState<ITypedIngredient<?>> searchState() {
		return searchState;
	}

	@Override
	public ViewerBootstrapContext<ITypedIngredient<?>> bootstrapContext() {
		return bootstrapContext;
	}

	@Override
	public ViewerPresentation<ITypedIngredient<?>, Component> presentation() {
		return presentation;
	}

	@Override
	public ViewerBookmarkPolicy<ITypedIngredient<?>> bookmarkPolicy() {
		return BOOKMARKS;
	}

	@Override
	public ViewerOverlayHook overlayHook() {
		return OVERLAY;
	}

	@Override
	public void onGroupChange(GroupChangeEvent.Kind kind) {
		searchState.publishCurrent();
	}

	public void updateBootstrap(List<ITypedIngredient<?>> ingredients, IIngredientManager manager) {
		JeiIngredientTypeDiscovery.discover(manager);
		bootstrapContext.update(ingredients, manager);
		JeiIngredientTypeDiscovery.warnUnresolvedTypesAfterBootstrap(GroupRegistry.getAllIncludingKubeJs());
		JeiHeaderIconResolver.warnUnresolvedAfterBootstrap(
			GroupRegistry.getAllIncludingKubeJs(), bootstrapContext.universe());
	}

	/** Publishes the JEI universe, then lets the global lifecycle apply scripted groups once. */
	public boolean universeReady(List<ITypedIngredient<?>> ingredients, IIngredientManager manager) {
		updateBootstrap(ingredients, manager);
		return ViewerLifecycleCoordinator.global().activeUniverseReady(id(), bootstrapContext);
	}

	public List<ITypedIngredient<?>> assembleHeaderIcons(
		List<GroupIconDefinition> iconIds,
		List<ViewerIngredient<ITypedIngredient<?>>> fallback
	) {
		return JeiHeaderIconResolver.assemble(iconIds, fallback, bootstrapContext.universe());
	}

	public List<ITypedIngredient<?>> resolveHeaderIconIngredients(List<GroupIconDefinition> iconIds) {
		return JeiHeaderIconResolver.resolve(iconIds, bootstrapContext.universe());
	}

	public void discoverRuntimeTypes(IIngredientManager manager) {
		JeiIngredientTypeDiscovery.discover(manager);
		JeiIngredientTypeDiscovery.warnUnresolvedTypesAfterBootstrap(GroupRegistry.getAllIncludingKubeJs());
	}

	public GroupCandidateIndex buildOwnershipIndex(
		List<ITypedIngredient<?>> ingredients,
		IIngredientManager manager,
		List<GroupDefinition> groups
	) {
		long start = PerformanceTrace.begin();
		updateBootstrap(ingredients, manager);
		ViewerIngredientUniverse<ITypedIngredient<?>> universe = bootstrapContext.universe();
		JeiHeaderIconResolver.warnUnresolvedAfterBootstrap(groups, universe);
		JeiViewerGroupIndex.instance().updateUniverse(universe);
		GroupCandidateIndex result = GroupProjectionEngine.buildCandidateIndex(universe, groups);
		PerformanceTrace.logIfSlow("JeiViewerAdapter.buildOwnershipIndex", start, 0,
			"candidateEdges=" + result.candidateEdges()
				+ " avgCandidates=" + String.format(java.util.Locale.ROOT, "%.2f", result.averageCandidates())
				+ " maxCandidates=" + result.maxCandidates()
				+ " indexedIngredients=" + result.indexedIngredients()
				+ " buildMillis=" + PerformanceTrace.elapsedMillis(start));
		return result;
	}

	/** Builds candidates from matches already collected by the optimized per-kind indexers. */
	public GroupCandidateIndex buildOwnershipIndexFromMatches(
		List<ITypedIngredient<?>> ingredients,
		IIngredientManager manager,
		List<GroupDefinition> groups,
		Map<ITypedIngredient<?>, List<String>> matches
	) {
		updateBootstrap(ingredients, manager);
		ViewerIngredientUniverse<ITypedIngredient<?>> universe = bootstrapContext.universe();
		JeiViewerGroupIndex.instance().updateUniverse(universe);
		Map<ViewerIngredientIdentity, List<String>> candidates = new LinkedHashMap<>();
		long edges = 0;
		int maxCandidates = 0;
		for (ViewerIngredient<ITypedIngredient<?>> ingredient : universe.ordered()) {
			List<String> groupIds = matches.get(ingredient.entry());
			if (groupIds == null || groupIds.isEmpty()) continue;
			List<String> frozen = List.copyOf(groupIds);
			candidates.put(ingredient.identity(), frozen);
			edges += frozen.size();
			maxCandidates = Math.max(maxCandidates, frozen.size());
		}
		Map<String, GroupDefinition> snapshot = new LinkedHashMap<>();
		for (GroupDefinition group : groups) snapshot.put(group.id(), group);
		return new GroupCandidateIndex(candidates, snapshot, edges, candidates.size(), maxCandidates);
	}

	public ViewerProjection<ITypedIngredient<?>> project(
		List<ITypedIngredient<?>> filtered,
		String searchText,
		boolean ungroupSmallGroups,
		int ungroupThreshold,
		List<GroupDefinition> groups,
		GroupExpansionState expansionState,
		GroupCandidateIndex ownershipIndex
	) {
		IIngredientManager manager = bootstrapContext.manager();
		List<ViewerIngredient<ITypedIngredient<?>>> filteredEntries = new ArrayList<>(filtered.size());
		for (ITypedIngredient<?> ingredient : filtered) {
			filteredEntries.add(bootstrapContext.getOrCreate(ingredient, manager));
		}
		ViewerSearchSnapshot<ITypedIngredient<?>> snapshot = new ViewerSearchSnapshot<>(
			searchText,
			filteredEntries,
			ungroupSmallGroups,
			ungroupThreshold
		);
		searchState.update(snapshot);
		Map<String, GroupDefinition> indexedGroups = ownershipIndex.groupSnapshot();
		List<GroupDefinition> projectionGroups = groups.stream()
			.map(group -> indexedGroups.getOrDefault(group.id(), group).withEnabled(group.enabled()))
			.toList();
		return GroupProjectionEngine.project(
			bootstrapContext.universe(), snapshot, projectionGroups, expansionState, ownershipIndex
		);
	}

	private ViewerIngredientUniverse<ITypedIngredient<?>> createUniverse(
		List<ITypedIngredient<?>> ingredients,
		IIngredientManager manager
	) {
		List<ViewerIngredient<ITypedIngredient<?>>> entries = new ArrayList<>(ingredients.size());
		for (ITypedIngredient<?> ingredient : ingredients) entries.add(createIngredient(ingredient, manager));
		return new ViewerIngredientUniverse<>(entries);
	}

	private static List<ITypedIngredient<?>> enumerate(IIngredientManager manager) {
		List<ITypedIngredient<?>> result = new ArrayList<>();
		for (IIngredientType<?> type : manager.getRegisteredIngredientTypes()) appendTyped(manager, type, result);
		return List.copyOf(result);
	}

	private static <T> void appendTyped(
		IIngredientManager manager,
		IIngredientType<T> type,
		List<ITypedIngredient<?>> output
	) {
		output.addAll(manager.getAllTypedIngredients(type));
	}

	private static ViewerIngredient<ITypedIngredient<?>> createIngredient(
		ITypedIngredient<?> typed,
		IIngredientManager manager
	) {
		return createIngredientUnchecked(typed, manager);
	}

	private static <T> ViewerIngredient<ITypedIngredient<?>> createIngredientUnchecked(
		ITypedIngredient<T> typed,
		IIngredientManager manager
	) {
		IIngredientHelper<T> helper = manager.getIngredientHelper(typed.getType());
		T value = typed.getIngredient();
		String typeId;
		ViewerIngredient.Kind kind;
		IngredientView view;
		if (typed.getType().equals(VanillaTypes.ITEM_STACK) && value instanceof ItemStack stack) {
			typeId = "item";
			kind = ViewerIngredient.Kind.ITEM;
			view = new ItemStackIngredientView(stack);
		} else if (typed.getType().equals(JeiIngredientTypes.getFluidType())) {
			typeId = "fluid";
			kind = ViewerIngredient.Kind.FLUID;
			view = Services.PLATFORM.createFluidView(value);
		} else {
			String registeredId = JeiIngredientTypes.getCanonicalId(typed.getType());
			typeId = registeredId != null ? registeredId : fallbackTypeId(typed.getType());
			kind = ViewerIngredient.Kind.GENERIC;
			view = new GenericJeiIngredientView<>(typeId, value, helper);
		}
		Object uid = helper.getUid(value, UidContext.Ingredient);
		String valueId = uid == null ? fallbackValueId(helper, value) : uid.toString();
		return new ViewerIngredient<>(
			new ViewerIngredientIdentity(typeId, valueId), kind, typed, view
		);
	}

	private static String fallbackTypeId(IIngredientType<?> type) {
		return "jei:" + type.getIngredientClass().getName().replaceAll("[^a-zA-Z0-9_.-]", "_");
	}

	private static <T> String fallbackValueId(IIngredientHelper<T> helper, T value) {
		ResourceLocation id = helper.getResourceLocation(value);
		return id != null ? id.toString() : helper.getErrorInfo(value);
	}

	private final class JeiPresentation implements ViewerPresentation<ITypedIngredient<?>, Component> {
		private final GroupIconRenderer groupIconRenderer = new GroupIconRenderer();

		@Override
		public void renderIngredient(ViewerIngredient<ITypedIngredient<?>> ingredient, RenderContext context) {
			if (!(context.drawingContext() instanceof GuiGraphics graphics)) return;
			renderTyped(graphics, ingredient.entry(), context.x(), context.y());
		}

		@Override
		public void renderHeader(ViewerProjection.GroupHeader<ITypedIngredient<?>> header, RenderContext context) {
			if (!(context.drawingContext() instanceof GuiGraphics graphics)) return;
			graphics.pose().pushPose();
			graphics.pose().translate(context.x(), context.y(), 0);
			groupIconRenderer.render(graphics, createGroupIcon(header));
			graphics.pose().popPose();
		}

		@Override
		public List<Component> ingredientTooltip(
			ViewerIngredient<ITypedIngredient<?>> ingredient,
			TooltipContext context
		) {
			return getTooltip(ingredient.entry());
		}

		@Override
		public List<Component> headerTooltip(
			ViewerProjection.GroupHeader<ITypedIngredient<?>> header,
			TooltipContext context
		) {
			return List.of(header.group().displayName().toComponent());
		}

		private GroupIcon createGroupIcon(ViewerProjection.GroupHeader<ITypedIngredient<?>> header) {
			List<ITypedIngredient<?>> display = assembleHeaderIcons(
				header.iconIds(), header.fallbackIconIngredients());
			return new GroupIcon(
				header.group().id(),
				header.group().displayName().key(),
				header.group().displayName().fallback(),
				display
			);
		}

		private <T> void renderTyped(GuiGraphics graphics, ITypedIngredient<T> typed, int x, int y) {
			IJeiRuntime runtime = JeiRuntimeHolder.get();
			if (runtime == null) return;
			IIngredientRenderer<T> renderer = runtime.getIngredientManager().getIngredientRenderer(typed.getType());
			graphics.pose().pushPose();
			graphics.pose().translate(x, y, 0);
			renderer.render(graphics, typed.getIngredient());
			graphics.pose().popPose();
		}

		private <T> List<Component> getTooltip(ITypedIngredient<T> typed) {
			IJeiRuntime runtime = JeiRuntimeHolder.get();
			if (runtime == null) return List.of();
			return runtime.getIngredientManager().getIngredientRenderer(typed.getType())
				.getTooltip(typed.getIngredient(), TooltipFlag.NORMAL);
		}
	}

	private static final class StandardOverlayHook implements ViewerOverlayHook {
		@Override
		public boolean shouldShowButton(boolean configuredVisible, boolean ingredientListVisible) {
			return configuredVisible && ingredientListVisible;
		}

		@Override
		public Bounds placeButton(Bounds configButton, int gap) {
			return new Bounds(
				configButton.x() - configButton.width() - gap,
				configButton.y(),
				configButton.width(),
				configButton.height()
			);
		}

		@Override
		public int adjustSearchFieldWidth(Bounds searchField, Bounds button, int gap) {
			return Math.max(0, button.x() - gap - searchField.x());
		}

		@Override
		public boolean handleInput(Input input) {
			return false;
		}
	}

	private final class JeiBootstrapContext implements ViewerBootstrapContext<ITypedIngredient<?>> {
		private volatile ViewerIngredientUniverse<ITypedIngredient<?>> universe =
			new ViewerIngredientUniverse<>(List.of());
		private volatile List<ViewerIngredientType<ITypedIngredient<?>>> types = List.of();
		@Nullable private volatile IIngredientManager manager;
		private volatile IdentityHashMap<ITypedIngredient<?>, ViewerIngredient<ITypedIngredient<?>>> byEntry =
			new IdentityHashMap<>();

		private synchronized void update(List<ITypedIngredient<?>> ingredients, IIngredientManager manager) {
			this.manager = Objects.requireNonNull(manager, "manager");
			this.universe = createUniverse(ingredients, manager);
			IdentityHashMap<ITypedIngredient<?>, ViewerIngredient<ITypedIngredient<?>>> indexed =
				new IdentityHashMap<>();
			for (ViewerIngredient<ITypedIngredient<?>> ingredient : universe.ordered()) {
				indexed.put(ingredient.entry(), ingredient);
			}
			this.byEntry = indexed;
			this.types = buildTypes(universe, manager);
		}

		private synchronized void clear() {
			manager = null;
			universe = new ViewerIngredientUniverse<>(List.of());
			types = List.of();
			byEntry = new IdentityHashMap<>();
		}

		private IIngredientManager manager() {
			return Objects.requireNonNull(manager, "JEI bootstrap context is not available");
		}

		private ViewerIngredient<ITypedIngredient<?>> getOrCreate(
			ITypedIngredient<?> ingredient,
			IIngredientManager manager
		) {
			ViewerIngredient<ITypedIngredient<?>> existing = byEntry.get(ingredient);
			return existing != null ? existing : createIngredient(ingredient, manager);
		}

		@Override
		public List<ViewerIngredientType<ITypedIngredient<?>>> ingredientTypes() {
			return types;
		}

		@Override
		public ViewerIngredientUniverse<ITypedIngredient<?>> universe() {
			return universe;
		}

		private List<ViewerIngredientType<ITypedIngredient<?>>> buildTypes(
			ViewerIngredientUniverse<ITypedIngredient<?>> universe,
			IIngredientManager manager
		) {
			List<ViewerIngredientType<ITypedIngredient<?>>> result = new ArrayList<>();
			for (String canonicalId : IngredientTypeIds.getCanonicalIds()) {
				List<String> aliases = IngredientTypeIds.getAliases().entrySet().stream()
					.filter(entry -> entry.getValue().equals(canonicalId))
					.map(Map.Entry::getKey)
					.toList();
				IIngredientType<?> type = JeiIngredientTypes.get(canonicalId);
				List<ViewerIngredient<ITypedIngredient<?>>> ingredients = type == null
					? universe.generic().stream()
						.filter(ingredient -> ingredient.identity().typeId().equals(canonicalId))
						.toList()
					: createTypeIngredients(manager, type);
				result.add(new ViewerIngredientType<>(canonicalId, aliases, ingredients));
			}
			return List.copyOf(result);
		}

		private <T> List<ViewerIngredient<ITypedIngredient<?>>> createTypeIngredients(
			IIngredientManager manager,
			IIngredientType<T> type
		) {
			return manager.getAllTypedIngredients(type).stream()
				.map(ingredient -> createIngredient(ingredient, manager))
				.toList();
		}
	}

	private static final class JeiSearchState implements ViewerSearchState<ITypedIngredient<?>> {
		private final CopyOnWriteArrayList<Consumer<ViewerSearchSnapshot<ITypedIngredient<?>>>> observers =
			new CopyOnWriteArrayList<>();
		private volatile ViewerSearchSnapshot<ITypedIngredient<?>> snapshot =
			new ViewerSearchSnapshot<>("", List.of(), false, 0);

		private void update(ViewerSearchSnapshot<ITypedIngredient<?>> snapshot) {
			this.snapshot = snapshot;
			publishCurrent();
		}

		private void publishCurrent() {
			for (Consumer<ViewerSearchSnapshot<ITypedIngredient<?>>> observer : observers) observer.accept(snapshot);
		}

		@Override
		public ViewerSearchSnapshot<ITypedIngredient<?>> snapshot() {
			return snapshot;
		}

		@Override
		public ViewerRegistration observe(Consumer<ViewerSearchSnapshot<ITypedIngredient<?>>> observer) {
			observers.add(observer);
			return () -> observers.remove(observer);
		}
	}
}
