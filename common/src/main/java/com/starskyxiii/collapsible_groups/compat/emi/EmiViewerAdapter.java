package com.starskyxiii.collapsible_groups.compat.emi;

import com.google.gson.JsonElement;
import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.client.editor.EditorRuntimeAccess;
import com.starskyxiii.collapsible_groups.group.GroupRepository;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.ScriptedGroupStore;
import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import com.starskyxiii.collapsible_groups.ingredient.GroupItemSelector;
import com.starskyxiii.collapsible_groups.persistence.GroupExpandState;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.GroupProjectionEngine;
import com.starskyxiii.collapsible_groups.viewer.ViewerAdapter;
import com.starskyxiii.collapsible_groups.viewer.ViewerBookmarkPolicy;
import com.starskyxiii.collapsible_groups.viewer.ViewerBootstrapContext;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerHeaderIconResolver;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerLifecycleCoordinator;
import com.starskyxiii.collapsible_groups.viewer.ViewerOverlayHook;
import com.starskyxiii.collapsible_groups.viewer.ViewerPresentation;
import com.starskyxiii.collapsible_groups.viewer.ViewerProjection;
import com.starskyxiii.collapsible_groups.viewer.ViewerRegistration;
import com.starskyxiii.collapsible_groups.viewer.ViewerSearchSnapshot;
import com.starskyxiii.collapsible_groups.viewer.ViewerSearchState;
import com.starskyxiii.collapsible_groups.viewer.ViewerUniverseProvider;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** EMI implementation. Raw EMI lists are read only; projection is exposed solely to ScreenSpace. */
public final class EmiViewerAdapter implements ViewerAdapter<EmiIngredient, ClientTooltipComponent> {
	private static final EmiViewerAdapter INSTANCE = new EmiViewerAdapter();
	private static final ViewerBookmarkPolicy<EmiIngredient> BOOKMARKS =
		ViewerBookmarkPolicy.headersCannotBeBookmarked();

	private final EmiBootstrapGate bootstrapGate = new EmiBootstrapGate();
	private final BootstrapContext bootstrapContext = new BootstrapContext();
	private final SearchState searchState = new SearchState();
	private final ViewerPresentation<EmiIngredient, ClientTooltipComponent> presentation = new Presentation();
	private final EmiViewerGroupIndex groupIndex = new EmiViewerGroupIndex();
	private final EditorRuntimeAccess editorRuntimeAccess = new EmiEditorRuntimeAccess(this, groupIndex);
	private final Set<String> fallbackWarnings = new LinkedHashSet<>();
	private volatile @Nullable ViewerRegistration registration;
	private volatile boolean refreshScheduled;

	private EmiViewerAdapter() {}

	public static EmiViewerAdapter instance() { return INSTANCE; }

	public static synchronized void registerRuntime() {
		if (INSTANCE.registration != null) INSTANCE.registration.close();
		INSTANCE.registration = ViewerLifecycleCoordinator.global().register(INSTANCE);
	}

	public static synchronized void unregisterRuntime() {
		if (INSTANCE.registration != null) INSTANCE.registration.close();
		INSTANCE.registration = null;
		INSTANCE.invalidate();
		ScriptedGroupStore.invalidate();
	}

	public synchronized void markDirty() {
		bootstrapGate.markDirty();
		ScriptedGroupStore.invalidate();
		bootstrapContext.clear();
		groupIndex.reset();
		refreshScheduled = false;
		searchState.clear();
		IngredientTypeIds.clearDiscovered();
	}

	public synchronized void invalidate() {
		markDirty();
		fallbackWarnings.clear();
	}

	@Override public String id() { return ViewerLifecycleCoordinator.EMI; }
	@Override public ViewerUniverseProvider<EmiIngredient> universeProvider() { return bootstrapContext; }
	@Override public ViewerSearchState<EmiIngredient> searchState() { return searchState; }
	@Override public ViewerBootstrapContext<EmiIngredient> bootstrapContext() { return bootstrapContext; }
	@Override public ViewerPresentation<EmiIngredient, ClientTooltipComponent> presentation() { return presentation; }
	@Override public ViewerBookmarkPolicy<EmiIngredient> bookmarkPolicy() { return BOOKMARKS; }
	@Override public ViewerOverlayHook overlayHook() { return EmiOverlayController.instance(); }
	@Override public ViewerGroupIndex groupIndex() { return groupIndex; }
	@Override public EditorRuntimeAccess editorRuntimeAccess() { return editorRuntimeAccess; }

	@Override
	public synchronized void onGroupChange(GroupChangeEvent.Kind kind) {
		if (kind == GroupChangeEvent.Kind.KUBEJS_REPLACE && bootstrapGate.ready()) {
			ViewerLifecycleCoordinator.global().activeUniverseReady(id(), bootstrapContext);
		}
		groupIndex.onGroupChange(kind, GroupRepository.getAllIncludingScripted());
		EmiProjectionController.clearCache();
		searchState.publishCurrent();
	}

	/** Called only by the INDEX ScreenSpace return hook. */
	public List<? extends EmiIngredient> projectIndex(List<? extends EmiIngredient> original, boolean searchPanel) {
		if (!ViewerLifecycleCoordinator.isEmiSelected() || !ensureUniverseReady()) return original;
		GroupCandidateIndex ownership = groupIndex.candidates().orElse(null);
		if (!groupIndex.ready() || ownership == null || groupIndex.epoch() != bootstrapGate.epoch()) return original;
		List<ViewerIngredient<EmiIngredient>> filtered = new ArrayList<>(original.size());
		for (EmiIngredient ingredient : original) {
			ViewerIngredient<EmiIngredient> mapped = bootstrapContext.resolve(ingredient);
			if (mapped == null && ingredient instanceof EmiStack stack) {
				mapped = createIngredient(stack);
				scheduleUniverseRefresh();
			}
			if (mapped == null) return original;
			filtered.add(mapped);
		}
		String searchText = searchPanel ? EmiApi.getSearchText() : "";
		ViewerSearchSnapshot<EmiIngredient> snapshot = new ViewerSearchSnapshot<>(searchText, filtered,
			Services.CONFIG.searchUngroupSmallGroups(), Services.CONFIG.searchUngroupThreshold());
		if (searchPanel) searchState.update(snapshot);
		List<GroupDefinition> groups = GroupRepository.getAllIncludingScripted();
		ViewerProjection<EmiIngredient> projection = GroupProjectionEngine.project(
			bootstrapContext.universe(), snapshot, groups, GroupExpandState::isExpandedById, ownership);
		List<EmiIngredient> display = new ArrayList<>();
		for (EmiProjectionTranslation.Entry<EmiIngredient> translated : EmiProjectionTranslation.classify(projection)) {
			ViewerProjection.DisplayEntry<EmiIngredient> entry = translated.displayEntry();
			switch (entry) {
				case ViewerProjection.DisplayHeader<EmiIngredient> header ->
					display.add(new GroupHeaderEmiStack(withResolvedIcons(header.header())));
				case ViewerProjection.DisplayIngredient<EmiIngredient> ingredient -> display.add(
					ingredient.parentGroupId().<EmiIngredient>map(id ->
						new ProjectedChildEmiIngredient(ingredient.ingredient().entry(), id)).orElse(
						ingredient.ingredient().entry()));
			}
		}
		return List.copyOf(display);
	}

	private void scheduleUniverseRefresh() {
		if (refreshScheduled) return;
		refreshScheduled = true;
		Minecraft.getInstance().execute(() -> {
			try {
				bootstrapContext.update(ownershipSource());
				startIndexBuild(bootstrapGate.epoch());
			} finally {
				refreshScheduled = false;
			}
		});
	}

	private ViewerProjection.GroupHeader<EmiIngredient> withResolvedIcons(
		ViewerProjection.GroupHeader<EmiIngredient> header
	) {
		List<ViewerIngredient<EmiIngredient>> icons = resolveHeaderIcons(
			header.iconIds(), header.fallbackIconIngredients());
		return new ViewerProjection.GroupHeader<>(header.group(), header.children(), header.itemCount(),
			header.fluidCount(), header.genericCount(), header.expanded(), header.iconIds(), icons);
	}

	List<ViewerIngredient<EmiIngredient>> resolveHeaderIcons(
		List<com.starskyxiii.collapsible_groups.group.GroupIconDefinition> configured,
		List<ViewerIngredient<EmiIngredient>> fallback
	) {
		return ViewerHeaderIconResolver.resolve(configured, fallback, bootstrapContext.universe());
	}

	List<ViewerIngredient<EmiIngredient>> resolveHeaderIconsFromDefinitions(
		List<com.starskyxiii.collapsible_groups.group.GroupIconDefinition> configured,
		List<com.starskyxiii.collapsible_groups.group.GroupIconDefinition> fallback
	) {
		List<ViewerIngredient<EmiIngredient>> resolvedFallback = new ArrayList<>(fallback.size());
		for (var icon : fallback) {
			ViewerIngredient<EmiIngredient> resolved = ViewerHeaderIconResolver.find(
				icon, bootstrapContext.universe());
			if (resolved != null) resolvedFallback.add(resolved);
		}
		return resolveHeaderIcons(configured, resolvedFallback);
	}

	public void observeIndexSearch(List<? extends EmiIngredient> original) {
		if (!ViewerLifecycleCoordinator.isEmiSelected() || !bootstrapGate.ready()) return;
		List<ViewerIngredient<EmiIngredient>> filtered = new ArrayList<>(original.size());
		for (EmiIngredient ingredient : original) {
			ViewerIngredient<EmiIngredient> mapped = bootstrapContext.resolve(ingredient);
			if (mapped != null) filtered.add(mapped);
		}
		searchState.update(new ViewerSearchSnapshot<>(EmiApi.getSearchText(), filtered,
			Services.CONFIG.searchUngroupSmallGroups(), Services.CONFIG.searchUngroupThreshold()));
	}

	private synchronized boolean ensureUniverseReady() {
		if (bootstrapGate.ready()) {
			if (!groupIndex.ready() && groupIndex.whenReady().isDone()) {
				startIndexBuild(bootstrapGate.epoch());
			}
			return true;
		}
		if (!bootstrapGate.tryClaim(EmiReloadManager.isLoaded(), true)) return false;
		try {
			bootstrapContext.update(ownershipSource());
			ViewerLifecycleCoordinator.global().activeUniverseReady(id(), bootstrapContext);
			bootstrapGate.complete();
			startIndexBuild(bootstrapGate.epoch());
			return false; // raw fallback until the candidate generation publishes.
		} catch (RuntimeException exception) {
			bootstrapGate.releaseFailedClaim();
			Constants.LOG.error("[CollapsibleGroups] Failed to bootstrap the EMI universe", exception);
			return false;
		}
	}

	private synchronized void startIndexBuild(long epoch) {
		if (!bootstrapGate.ready() || bootstrapContext.universe().ordered().isEmpty()) return;
		ViewerIngredientUniverse<EmiIngredient> universe = bootstrapContext.universe();
		List<GroupDefinition> groups = GroupRepository.getAllIncludingScripted();
		groupIndex.requestRebuild(epoch, universe, groups).thenRun(() -> Minecraft.getInstance().execute(() -> {
			if (bootstrapGate.epoch() != epoch || !groupIndex.ready()) return;
			EmiProjectionController.clearCache();
			EmiScreenManager.forceRecalculate();
		}));
	}

	public ProjectionCacheKey projectionCacheKey() {
		return new ProjectionCacheKey(bootstrapGate.epoch(), bootstrapGate.ready(), groupIndex.ready(),
			groupIndex.revision());
	}

	public record ProjectionCacheKey(long epoch, boolean bootstrapReady, boolean indexReady, long revision) {}

	/** Editor-visible order: EMI's stable configured order with user-hidden entries removed. */
	public List<ViewerIngredient<EmiIngredient>> editorDisplayIngredients() {
		List<EmiStack> source = editorDisplaySource();
		List<ViewerIngredient<EmiIngredient>> result = new ArrayList<>(source.size());
		for (EmiStack stack : source) {
			ViewerIngredient<EmiIngredient> ingredient = bootstrapContext.resolve(stack);
			if (ingredient != null) result.add(ingredient);
		}
		return List.copyOf(result);
	}

	static List<EmiStack> ownershipSource() { return EmiIndexSources.snapshot().ownership(); }
	static List<EmiStack> editorDisplaySource() { return EmiIndexSources.snapshot().editorDisplay(); }

	public @Nullable ViewerIngredient<EmiIngredient> resolveIdentity(ViewerIngredientIdentity identity) {
		return bootstrapContext.byIdentity.get(identity);
	}

	public ViewerIngredientIdentity identityFor(EmiStack stack) {
		ViewerIngredient<EmiIngredient> resolved = bootstrapContext.resolve(stack);
		return resolved == null ? createIngredient(stack).identity() : resolved.identity();
	}

	public CompletableFuture<Void> prepareEditorIndex() {
		ensureUniverseReady();
		return groupIndex.whenReady();
	}

	private ViewerIngredient<EmiIngredient> createIngredient(EmiStack stack) {
		EmiStack normalized = stack.copy().setAmount(1).setChance(1).setRemainder(EmiStack.EMPTY);
		JsonElement serialized = EmiIngredientSerializer.getSerialized(normalized);
		Object key = stack.getKey();
		EmiIdentityNormalizer.StandardKind standardKind = key instanceof Item
			? EmiIdentityNormalizer.StandardKind.ITEM
			: key instanceof Fluid ? EmiIdentityNormalizer.StandardKind.FLUID
			: EmiIdentityNormalizer.StandardKind.CUSTOM;
		var identity = EmiIdentityNormalizer.identify(standardKind, serialized, stack.getClass().getName(),
			stack.getId().toString(), canonicalExtraData(stack.getComponentChanges()));
		if (!identity.serializable() && fallbackWarnings.add(identity.typeId())) {
			Constants.LOG.warn("[CollapsibleGroups] EMI stack type {} has no serializer; using an unstable class/id identity and leaving tag capability unavailable.", identity.typeId());
		}
		registerDiscoveredType(identity);
		ViewerIngredient.Kind kind;
		IngredientView view;
		if (standardKind == EmiIdentityNormalizer.StandardKind.ITEM) {
			kind = ViewerIngredient.Kind.ITEM;
			view = new ItemStackIngredientView(stack.getItemStack());
		} else {
			kind = standardKind == EmiIdentityNormalizer.StandardKind.FLUID
				? ViewerIngredient.Kind.FLUID : ViewerIngredient.Kind.GENERIC;
			view = new EmiIngredientView(identity.typeId(), stack.getId(), key);
		}
		return new ViewerIngredient<>(new ViewerIngredientIdentity(identity.typeId(), identity.valueId()),
			kind, stack, view);
	}

	private static String canonicalExtraData(DataComponentPatch patch) {
		return DataComponentPatch.CODEC.encodeStart(GroupItemSelector.serializationContext(), patch)
			.result().map(EmiIdentityNormalizer::canonicalJson).orElseGet(patch::toString);
	}

	private static void registerDiscoveredType(EmiIdentityNormalizer.Result identity) {
		if (identity.typeId().equals("item") || identity.typeId().equals("fluid")) return;
		try {
			if (IngredientTypeIds.getCanonicalId(identity.typeId()) == null) {
				IngredientTypeIds.registerCanonical(identity.typeId(), IngredientTypeIds.RegistrationOrigin.DISCOVERED);
			}
			for (String alias : identity.aliases()) {
				if (IngredientTypeIds.getCanonicalId(alias) == null) {
					IngredientTypeIds.registerAlias(alias, identity.typeId(), IngredientTypeIds.RegistrationOrigin.DISCOVERED);
				}
			}
		} catch (IllegalArgumentException exception) {
			Constants.LOG.warn("[CollapsibleGroups] Could not register EMI ingredient type aliases for {}: {}",
				identity.typeId(), exception.getMessage());
		}
	}

	private final class BootstrapContext implements ViewerBootstrapContext<EmiIngredient> {
		private volatile ViewerIngredientUniverse<EmiIngredient> universe = new ViewerIngredientUniverse<>(List.of());
		private volatile List<ViewerIngredientType<EmiIngredient>> types = List.of();
		private volatile IdentityHashMap<EmiIngredient, ViewerIngredient<EmiIngredient>> byEntry = new IdentityHashMap<>();
		private volatile Map<ViewerIngredientIdentity, ViewerIngredient<EmiIngredient>> byIdentity = Map.of();

		synchronized void update(List<EmiStack> stacks) {
			List<ViewerIngredient<EmiIngredient>> entries = new ArrayList<>(stacks.size());
			IdentityHashMap<EmiIngredient, ViewerIngredient<EmiIngredient>> identities = new IdentityHashMap<>();
			Map<ViewerIngredientIdentity, ViewerIngredient<EmiIngredient>> stable = new LinkedHashMap<>();
			for (EmiStack stack : stacks) {
				ViewerIngredient<EmiIngredient> ingredient = createIngredient(stack);
				entries.add(ingredient);
				identities.put(stack, ingredient);
				stable.putIfAbsent(ingredient.identity(), ingredient);
			}
			universe = new ViewerIngredientUniverse<>(entries);
			byEntry = identities;
			byIdentity = Map.copyOf(stable);
			Map<String, List<ViewerIngredient<EmiIngredient>>> buckets = new LinkedHashMap<>();
			for (ViewerIngredient<EmiIngredient> entry : entries) {
				buckets.computeIfAbsent(entry.identity().typeId(), ignored -> new ArrayList<>()).add(entry);
			}
			List<ViewerIngredientType<EmiIngredient>> discovered = new ArrayList<>();
			buckets.forEach((id, values) -> discovered.add(new ViewerIngredientType<>(id,
				IngredientTypeIds.getAliases().entrySet().stream().filter(e -> e.getValue().equals(id))
					.map(Map.Entry::getKey).toList(), values)));
			types = List.copyOf(discovered);
		}

		synchronized void clear() {
			universe = new ViewerIngredientUniverse<>(List.of());
			types = List.of();
			byEntry = new IdentityHashMap<>();
			byIdentity = Map.of();
		}

		@Nullable ViewerIngredient<EmiIngredient> resolve(EmiIngredient ingredient) {
			ViewerIngredient<EmiIngredient> direct = byEntry.get(ingredient);
			if (direct != null) return direct;
			if (ingredient instanceof ProjectedChildEmiIngredient child) return resolve(child.delegate());
			if (!(ingredient instanceof EmiStack stack)) return null;
			return byIdentity.get(createIngredient(stack).identity());
		}

		@Override public List<ViewerIngredientType<EmiIngredient>> ingredientTypes() { return types; }
		@Override public ViewerIngredientUniverse<EmiIngredient> universe() { return universe; }
	}

	private static final class SearchState implements ViewerSearchState<EmiIngredient> {
		private final CopyOnWriteArrayList<Consumer<ViewerSearchSnapshot<EmiIngredient>>> observers = new CopyOnWriteArrayList<>();
		private volatile ViewerSearchSnapshot<EmiIngredient> snapshot = new ViewerSearchSnapshot<>("", List.of(), false, 0);
		void update(ViewerSearchSnapshot<EmiIngredient> next) {
			if (snapshot.searchText().equals(next.searchText()) && snapshot.filteredResults().equals(next.filteredResults())) return;
			snapshot = next;
			publishCurrent();
		}
		void clear() { snapshot = new ViewerSearchSnapshot<>("", List.of(), false, 0); }
		void publishCurrent() { observers.forEach(observer -> observer.accept(snapshot)); }
		@Override public ViewerSearchSnapshot<EmiIngredient> snapshot() { return snapshot; }
		@Override public ViewerRegistration observe(Consumer<ViewerSearchSnapshot<EmiIngredient>> observer) {
			observers.add(observer);
			return () -> observers.remove(observer);
		}
	}

	private static final class Presentation implements ViewerPresentation<EmiIngredient, ClientTooltipComponent> {
		@Override public void renderIngredient(ViewerIngredient<EmiIngredient> ingredient, RenderContext context) {
			if (context.drawingContext() instanceof GuiGraphics graphics) ingredient.entry().render(graphics, context.x(), context.y(), 0);
		}
		@Override public void renderHeader(ViewerProjection.GroupHeader<EmiIngredient> header, RenderContext context) {
			if (context.drawingContext() instanceof GuiGraphics graphics) {
				new GroupHeaderEmiStack(header).render(graphics, context.x(), context.y(), 0, -1);
			}
		}
		@Override public List<ClientTooltipComponent> ingredientTooltip(ViewerIngredient<EmiIngredient> ingredient, TooltipContext context) {
			return ingredient.entry().getTooltip();
		}
		@Override public List<ClientTooltipComponent> headerTooltip(ViewerProjection.GroupHeader<EmiIngredient> header, TooltipContext context) {
			return new GroupHeaderEmiStack(header).getTooltip();
		}
	}
}
