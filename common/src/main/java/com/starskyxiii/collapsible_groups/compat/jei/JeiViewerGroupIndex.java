package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.compat.jei.preview.PreviewIngredientRenderer;
import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.GroupProjectionEngine;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupPreviewSnapshot;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import com.starskyxiii.collapsible_groups.viewer.ViewerPreviewValue;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** JEI cache/index implementation of the neutral {@link ViewerGroupIndex} seam. */
public final class JeiViewerGroupIndex implements ViewerGroupIndex {
	private static final JeiViewerGroupIndex INSTANCE = new JeiViewerGroupIndex();
	private static final ExecutorService REBUILD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "CG-IndexRebuild");
		thread.setDaemon(true);
		return thread;
	});

	/** One coherently published ownership/cache generation. */
	public record Generation(
		GroupCandidateIndex candidates,
		@Nullable Map<String, List<ItemStack>> resolvedItems,
		@Nullable Map<String, List<Object>> resolvedFluids,
		@Nullable Map<String, List<ItemStack>> fullMatchItems,
		@Nullable Map<String, List<Object>> fullMatchFluids,
		@Nullable Map<String, List<GenericIngredientRef>> fullMatchGeneric,
		@Nullable Map<String, Set<String>> itemReverseIndex,
		@Nullable Map<String, Set<String>> fluidReverseIndex
	) {
		public Generation {
			resolvedItems = freezeNullableListMap(resolvedItems);
			resolvedFluids = freezeNullableListMap(resolvedFluids);
			fullMatchItems = freezeNullableListMap(fullMatchItems);
			fullMatchFluids = freezeNullableListMap(fullMatchFluids);
			fullMatchGeneric = freezeNullableListMap(fullMatchGeneric);
			itemReverseIndex = freezeNullableSetMap(itemReverseIndex);
			fluidReverseIndex = freezeNullableSetMap(fluidReverseIndex);
		}
	}

	private volatile @Nullable Generation published;
	private volatile ViewerIngredientUniverse<ITypedIngredient<?>> universe =
		new ViewerIngredientUniverse<>(List.of());
	private volatile CompletableFuture<Void> readyFuture = CompletableFuture.completedFuture(null);
	private volatile @Nullable Supplier<Generation> rebuildSource;
	private volatile Executor completionExecutor = Runnable::run;
	private volatile Runnable rebuildListener = () -> {};
	private volatile List<GroupDefinition> currentGroups = List.of();
	private long resetSequence;

	private JeiViewerGroupIndex() {}

	public static JeiViewerGroupIndex instance() { return INSTANCE; }

	public void configureRebuild(Supplier<Generation> source, Executor completionExecutor,
		Runnable rebuildListener) {
		this.rebuildSource = source;
		this.completionExecutor = completionExecutor;
		this.rebuildListener = rebuildListener;
	}

	public void updateUniverse(ViewerIngredientUniverse<ITypedIngredient<?>> universe) {
		this.universe = universe;
	}

	public synchronized void reset() {
		resetSequence++;
		published = null;
		universe = new ViewerIngredientUniverse<>(List.of());
		readyFuture = CompletableFuture.completedFuture(null);
		rebuildSource = null;
		completionExecutor = Runnable::run;
		rebuildListener = () -> {};
		currentGroups = List.of();
	}

	/** Compatibility hook for viewer bootstrap resets. Prefer {@link #requestRebuild(List)}. */
	public synchronized void invalidateCandidates() {
		published = null;
	}

	public synchronized void publishGeneration(Generation generation) {
		published = generation;
	}

	/** Test/compatibility publication path; production rebuilds publish a complete generation. */
	public synchronized void publishCandidateIndex(GroupCandidateIndex index, List<GroupDefinition> groups) {
		Resolved resolved = resolve(index, groups);
		Generation old = published;
		published = new Generation(index, resolved.items(), resolved.fluids(),
			old == null ? null : old.fullMatchItems(), old == null ? null : old.fullMatchFluids(),
			old == null ? null : old.fullMatchGeneric(), resolved.itemIds(), resolved.fluidIds());
	}

	@Override public Optional<GroupCandidateIndex> candidates() {
		Generation current = published;
		return current == null ? Optional.empty() : Optional.of(current.candidates());
	}

	@Override public boolean ready() {
		Generation current = published;
		return readyFuture.isDone() && current != null
			&& current.resolvedItems() != null && current.resolvedFluids() != null;
	}

	@Override public CompletableFuture<Void> whenReady() { return readyFuture; }

	@Override public Optional<ViewerGroupPreviewSnapshot> fullMatchSnapshot(GroupDefinition group) {
		if (published == null) return Optional.empty();
		List<ItemStack> items = GroupRegistry.getFullMatchItemsLookup(group).values();
		List<Object> fluids = GroupRegistry.getFullMatchFluidsLookup(group).values();
		List<GenericIngredientRef> generic = GroupRegistry.getFullMatchGenericIngredientsLookup(group).values();
		List<ViewerPreviewValue> itemValues = items.stream().map(ViewerPreviewValue::item).toList();
		List<ViewerPreviewValue> fluidValues = fluids.stream().map(fluid -> ViewerPreviewValue.rendered(
			(graphics, x, y) -> PreviewIngredientRenderer.renderFluid(graphics, fluid, x, y))).toList();
		List<ViewerPreviewValue> genericValues = generic.stream().map(ref -> ViewerPreviewValue.rendered(
			(graphics, x, y) -> renderGeneric(graphics, ref, x, y))).toList();
		return Optional.of(new ViewerGroupPreviewSnapshot(itemValues, fluidValues, genericValues));
	}

	@SuppressWarnings("unchecked")
	private static void renderGeneric(net.minecraft.client.gui.GuiGraphics graphics,
		GenericIngredientRef ref, int x, int y) {
		PreviewIngredientRenderer.renderGeneric(graphics,
			(mezz.jei.api.ingredients.IIngredientType<Object>) ref.type(), ref.ingredient(), x, y);
	}

	@Override public Optional<String> resolveOwner(ViewerIngredientIdentity identity,
		List<GroupDefinition> groups) {
		return Optional.ofNullable(resolveOwnership(groups).get(identity));
	}

	@Override public Map<ViewerIngredientIdentity, String> resolveOwnership(List<GroupDefinition> groups) {
		Generation current = published;
		return current == null ? Map.of()
			: GroupProjectionEngine.resolveOwnership(current.candidates(), groups);
	}

	@Override public synchronized void onGroupChange(GroupChangeEvent.Kind kind,
		List<GroupDefinition> groups) {
		currentGroups = List.copyOf(groups);
		switch (kind) {
			case ENABLED -> {
				Generation current = published;
				if (current != null) {
					Resolved resolved = resolve(current.candidates(), groups);
					published = new Generation(current.candidates(), resolved.items(), resolved.fluids(),
						current.fullMatchItems(), current.fullMatchFluids(), current.fullMatchGeneric(),
						resolved.itemIds(), resolved.fluidIds());
				}
			}
			case STRUCTURE -> { }
			case FULL, KUBEJS_REPLACE -> {
				// Keep the last candidate generation available to the render path while replacing it.
				Generation current = published;
				if (current != null) {
					published = new Generation(current.candidates(), null, null, null, null, null, null, null);
				}
				startConfiguredRebuild();
			}
		}
	}

	public synchronized CompletableFuture<Void> requestRebuild(List<GroupDefinition> groups) {
		currentGroups = List.copyOf(groups);
		startConfiguredRebuild();
		return readyFuture;
	}

	private void startConfiguredRebuild() {
		if (!readyFuture.isDone()) return;
		Supplier<Generation> source = rebuildSource;
		if (source == null) return;
		long sequence = resetSequence;
		CompletableFuture<Generation> build = CompletableFuture.supplyAsync(source, REBUILD_EXECUTOR);
		CompletableFuture<Void> publication = build.thenAccept(generation -> {
			synchronized (this) {
				if (sequence != resetSequence) return;
				publishGeneration(withCurrentEnabledState(generation));
			}
		});
		readyFuture = publication;
		publication.thenRunAsync(() -> {
			synchronized (this) {
				if (sequence != resetSequence) return;
			}
			rebuildListener.run();
		}, completionExecutor);
	}

	/** Starts the configured rebuild for a cold editor without running it on the render thread. */
	public synchronized CompletableFuture<Void> ensureReadyAsync(List<GroupDefinition> groups) {
		currentGroups = List.copyOf(groups);
		if (published != null && readyFuture.isDone()) return CompletableFuture.completedFuture(null);
		startConfiguredRebuild();
		return readyFuture;
	}

	public CompletableFuture<Void> prepareEditorAsync(List<GroupDefinition> groups, Runnable warmCaches) {
		return ensureReadyAsync(groups).thenRunAsync(warmCaches, REBUILD_EXECUTOR);
	}

	private Generation withCurrentEnabledState(Generation generation) {
		Map<String, GroupDefinition> snapshot = generation.candidates().groupSnapshot();
		boolean unchanged = snapshot.size() == currentGroups.size();
		if (unchanged) {
			for (GroupDefinition group : currentGroups) {
				GroupDefinition built = snapshot.get(group.id());
				if (built == null || built.enabled() != group.enabled()) {
					unchanged = false;
					break;
				}
			}
		}
		if (unchanged) return generation;
		Resolved resolved = resolve(generation.candidates(), currentGroups);
		return new Generation(generation.candidates(), resolved.items(), resolved.fluids(),
			generation.fullMatchItems(), generation.fullMatchFluids(), generation.fullMatchGeneric(),
			resolved.itemIds(), resolved.fluidIds());
	}

	private Resolved resolve(GroupCandidateIndex index, List<GroupDefinition> groups) {
		Map<ViewerIngredientIdentity, String> ownership = GroupProjectionEngine.resolveOwnership(index, groups);
		Map<String, List<ItemStack>> items = new LinkedHashMap<>();
		Map<String, List<Object>> fluids = new LinkedHashMap<>();
		Map<String, Set<String>> itemIds = new LinkedHashMap<>();
		Map<String, Set<String>> fluidIds = new LinkedHashMap<>();
		for (GroupDefinition group : groups) {
			items.put(group.id(), new ArrayList<>());
			fluids.put(group.id(), new ArrayList<>());
		}
		for (ViewerIngredient<ITypedIngredient<?>> ingredient : universe.ordered()) {
			String groupId = ownership.get(ingredient.identity());
			if (groupId == null) continue;
			switch (ingredient.kind()) {
				case ITEM -> ingredient.entry().getItemStack().ifPresent(stack -> {
					items.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(stack);
					String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
					itemIds.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(groupId);
				});
				case FLUID -> {
					Object fluid = ingredient.entry().getIngredient();
					fluids.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(fluid);
					String id = Services.PLATFORM.getFluidId(fluid);
					if (id != null) fluidIds.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(groupId);
				}
				case GENERIC -> { }
			}
		}
		return new Resolved(items, fluids, itemIds, fluidIds);
	}

	private record Resolved(Map<String, List<ItemStack>> items, Map<String, List<Object>> fluids,
		Map<String, Set<String>> itemIds, Map<String, Set<String>> fluidIds) {}

	public @Nullable List<ItemStack> resolvedItems(String groupId) {
		Map<String, List<ItemStack>> cache = resolvedItemsCache();
		return cache == null ? null : cache.get(groupId);
	}
	public @Nullable Map<String, List<ItemStack>> resolvedItemsCache() {
		Generation current = published; return current == null ? null : current.resolvedItems();
	}
	public @Nullable Map<String, List<Object>> resolvedFluidsCache() {
		Generation current = published; return current == null ? null : current.resolvedFluids();
	}
	public @Nullable List<Object> resolvedFluids(String groupId) {
		Map<String, List<Object>> cache = resolvedFluidsCache(); return cache == null ? null : cache.get(groupId);
	}
	public @Nullable Map<String, List<ItemStack>> fullMatchItems() {
		Generation current = published; return current == null ? null : current.fullMatchItems();
	}
	public @Nullable Map<String, List<Object>> fullMatchFluids() {
		Generation current = published; return current == null ? null : current.fullMatchFluids();
	}
	public @Nullable Map<String, List<GenericIngredientRef>> fullMatchGeneric() {
		Generation current = published; return current == null ? null : current.fullMatchGeneric();
	}
	public @Nullable Map<String, Set<String>> itemReverseIndex() {
		Generation current = published; return current == null ? null : current.itemReverseIndex();
	}
	public @Nullable Map<String, Set<String>> fluidReverseIndex() {
		Generation current = published; return current == null ? null : current.fluidReverseIndex();
	}
	public boolean previewCachesValid() {
		Generation current = published;
		return current != null && current.fullMatchItems() != null
			&& current.fullMatchFluids() != null && current.fullMatchGeneric() != null;
	}

	public synchronized void setResolvedItemsByGroup(Map<String, List<ItemStack>> values) {
		Generation g = baseGeneration(); published = new Generation(g.candidates(), values, g.resolvedFluids(),
			g.fullMatchItems(), g.fullMatchFluids(), g.fullMatchGeneric(), g.itemReverseIndex(), g.fluidReverseIndex());
	}
	public synchronized void setResolvedFluidsByGroup(Map<String, List<Object>> values) {
		Generation g = baseGeneration(); published = new Generation(g.candidates(), g.resolvedItems(), values,
			g.fullMatchItems(), g.fullMatchFluids(), g.fullMatchGeneric(), g.itemReverseIndex(), g.fluidReverseIndex());
	}
	public synchronized void setFullMatchItemsByGroup(Map<String, List<ItemStack>> values) {
		Generation g = baseGeneration(); published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(),
			values, g.fullMatchFluids(), g.fullMatchGeneric(), g.itemReverseIndex(), g.fluidReverseIndex());
	}
	public synchronized void setFullMatchFluidsByGroup(Map<String, List<Object>> values) {
		Generation g = baseGeneration(); published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(),
			g.fullMatchItems(), values, g.fullMatchGeneric(), g.itemReverseIndex(), g.fluidReverseIndex());
	}
	public synchronized void setFullMatchGenericByGroup(Map<String, List<GenericIngredientRef>> values) {
		Generation g = baseGeneration(); published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(),
			g.fullMatchItems(), g.fullMatchFluids(), values, g.itemReverseIndex(), g.fluidReverseIndex());
	}
	public synchronized void setItemReverseIndex(Map<String, Set<String>> values) {
		Generation g = baseGeneration(); published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(),
			g.fullMatchItems(), g.fullMatchFluids(), g.fullMatchGeneric(), values, g.fluidReverseIndex());
	}
	public synchronized void setFluidReverseIndex(Map<String, Set<String>> values) {
		Generation g = baseGeneration(); published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(),
			g.fullMatchItems(), g.fullMatchFluids(), g.fullMatchGeneric(), g.itemReverseIndex(), values);
	}

	public synchronized void clearResolvedCaches() {
		if (published == null) return; Generation g = published;
		published = new Generation(g.candidates(), null, null, g.fullMatchItems(), g.fullMatchFluids(),
			g.fullMatchGeneric(), null, null);
	}
	public synchronized void clearPreviewCaches() {
		if (published == null) return; Generation g = published;
		published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(), null, null, null,
			g.itemReverseIndex(), g.fluidReverseIndex());
	}
	public void invalidateFirstMatch(String groupId) {
		Map<String, List<ItemStack>> items = resolvedItemsCache(); if (items != null) items.remove(groupId);
		Map<String, List<Object>> fluids = resolvedFluidsCache(); if (fluids != null) fluids.remove(groupId);
	}
	public void invalidateFullMatch(String groupId) {
		Map<String, List<ItemStack>> items = fullMatchItems(); if (items != null) items.remove(groupId);
		Map<String, List<Object>> fluids = fullMatchFluids(); if (fluids != null) fluids.remove(groupId);
		Map<String, List<GenericIngredientRef>> generic = fullMatchGeneric(); if (generic != null) generic.remove(groupId);
	}
	public synchronized Map<String, List<ItemStack>> ensureFullMatchItems() {
		Map<String, List<ItemStack>> cache = fullMatchItems();
		if (cache != null) return cache;
		setFullMatchItemsByGroup(Map.of()); return fullMatchItems();
	}
	public synchronized Map<String, List<Object>> ensureFullMatchFluids() {
		Map<String, List<Object>> cache = fullMatchFluids();
		if (cache != null) return cache;
		setFullMatchFluidsByGroup(Map.of()); return fullMatchFluids();
	}
	public synchronized Map<String, List<GenericIngredientRef>> ensureFullMatchGeneric() {
		Map<String, List<GenericIngredientRef>> cache = fullMatchGeneric();
		if (cache != null) return cache;
		setFullMatchGenericByGroup(Map.of()); return fullMatchGeneric();
	}

	private Generation baseGeneration() {
		Generation current = published;
		if (current != null) return current;
		return new Generation(new GroupCandidateIndex(Map.of(), Map.of(), 0, 0, 0),
			null, null, null, null, null, null, null);
	}

	private static <T> @Nullable Map<String, List<T>> freezeNullableListMap(
		@Nullable Map<String, List<T>> values) {
		if (values == null) return null;
		if (values instanceof ConcurrentHashMap<?, ?>) return values;
		Map<String, List<T>> copy = new ConcurrentHashMap<>(Math.max(16, values.size() * 2));
		values.forEach((key, value) -> copy.put(key, List.copyOf(value)));
		return copy;
	}
	private static @Nullable Map<String, Set<String>> freezeNullableSetMap(
		@Nullable Map<String, Set<String>> values) {
		if (values == null) return null;
		if (values instanceof ConcurrentHashMap<?, ?>) return values;
		Map<String, Set<String>> copy = new ConcurrentHashMap<>(Math.max(16, values.size() * 2));
		values.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
		return copy;
	}
}
