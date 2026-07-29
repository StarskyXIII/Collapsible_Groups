package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.platform.Services;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.GroupProjectionEngine;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
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

	// Immutable, coherent view of all three manager preview caches from one generation.
	public record FullMatchCacheSnapshot(
		@Nullable Map<String, List<ItemStack>> items,
		@Nullable Map<String, List<Object>> fluids,
		@Nullable Map<String, List<GenericIngredientRef>> generic
	) {
		public boolean complete() {
			return items != null && fluids != null && generic != null;
		}

		public @Nullable FullMatchEntry entry(String groupId) {
			if (!complete() || !items.containsKey(groupId)
				|| !fluids.containsKey(groupId) || !generic.containsKey(groupId)) {
				return null;
			}
			return new FullMatchEntry(items.get(groupId), fluids.get(groupId), generic.get(groupId));
		}
	}

	// One group's three preview kinds, observed or published atomically.
	public record FullMatchEntry(
		List<ItemStack> items,
		List<Object> fluids,
		List<GenericIngredientRef> generic
	) {
		public FullMatchEntry {
			items = List.copyOf(items);
			fluids = List.copyOf(fluids);
			generic = List.copyOf(generic);
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
	private long desiredRevision;

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
		desiredRevision = 0L;
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
		desiredRevision++;
		published = null;
	}

	public synchronized void publishGeneration(Generation generation) {
		desiredRevision++;
		published = generation;
	}

	/** Test/compatibility publication path; production rebuilds publish a complete generation. */
	public synchronized void publishCandidateIndex(GroupCandidateIndex index, List<GroupDefinition> groups) {
		Resolved resolved = resolve(index, groups);
		Generation old = published;
		desiredRevision++;
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
			case FULL -> {
				// Keep the last candidate generation available to the render path while replacing it.
				Generation current = published;
				if (current != null) {
					published = new Generation(current.candidates(), null, null,
						current.fullMatchItems(), current.fullMatchFluids(), current.fullMatchGeneric(),
						null, null);
				}
				desiredRevision++;
				startConfiguredRebuild();
			}
			case KUBEJS_REPLACE -> {
				// Script replacement may redefine every group, so no preview entry is safe to retain.
				Generation current = published;
				if (current != null) {
					published = new Generation(current.candidates(), null, null,
						null, null, null, null, null);
				}
				desiredRevision++;
				startConfiguredRebuild();
			}
		}
	}

	public synchronized CompletableFuture<Void> requestRebuild(List<GroupDefinition> groups) {
		currentGroups = List.copyOf(groups);
		desiredRevision++;
		startConfiguredRebuild();
		return readyFuture;
	}

	private void startConfiguredRebuild() {
		if (!readyFuture.isDone()) return;
		Supplier<Generation> source = rebuildSource;
		if (source == null) return;
		long sequence = resetSequence;
		CompletableFuture<Void> loop = new CompletableFuture<>();
		readyFuture = loop;
		scheduleRebuild(sequence, loop);
	}

	private void scheduleRebuild(long sequence, CompletableFuture<Void> loop) {
		Supplier<Generation> source;
		long capturedRevision;
		synchronized (this) {
			if (sequence != resetSequence) {
				loop.complete(null);
				return;
			}
			source = rebuildSource;
			if (source == null) {
				loop.complete(null);
				return;
			}
			capturedRevision = desiredRevision;
		}

		CompletableFuture.supplyAsync(source, REBUILD_EXECUTOR).whenComplete((generation, failure) -> {
			boolean trailing;
			synchronized (this) {
				if (sequence != resetSequence) {
					loop.complete(null);
					return;
				}
				if (failure != null) {
					loop.completeExceptionally(failure);
					return;
				}
				trailing = capturedRevision != desiredRevision;
				if (!trailing) {
					published = withCurrentEnabledState(generation);
				}
			}
			if (trailing) {
				scheduleRebuild(sequence, loop);
				return;
			}
			loop.complete(null);
			completionExecutor.execute(() -> {
				synchronized (this) {
					if (sequence != resetSequence || readyFuture != loop) return;
				}
				rebuildListener.run();
			});
		});
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
	public FullMatchCacheSnapshot fullMatchSnapshot() {
		Generation current = published;
		return current == null
			? new FullMatchCacheSnapshot(null, null, null)
			: new FullMatchCacheSnapshot(current.fullMatchItems(), current.fullMatchFluids(),
				current.fullMatchGeneric());
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
	public synchronized void setFullMatchCachesByGroup(Map<String, List<ItemStack>> items,
		Map<String, List<Object>> fluids, Map<String, List<GenericIngredientRef>> generic) {
		Generation g = baseGeneration();
		desiredRevision++;
		published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(),
			items, fluids, generic, g.itemReverseIndex(), g.fluidReverseIndex());
	}
	public synchronized void setItemCaches(Map<String, List<ItemStack>> resolvedItems,
		Map<String, List<ItemStack>> fullMatchItems, Map<String, Set<String>> itemReverseIndex) {
		Generation g = baseGeneration();
		desiredRevision++;
		published = new Generation(g.candidates(), resolvedItems, g.resolvedFluids(),
			fullMatchItems, g.fullMatchFluids(), g.fullMatchGeneric(),
			itemReverseIndex, g.fluidReverseIndex());
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
		desiredRevision++;
		published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(), null, null, null,
			g.itemReverseIndex(), g.fluidReverseIndex());
	}
	public synchronized void invalidateFirstMatch(String groupId) {
		if (published == null) return;
		Generation g = published;
		Map<String, List<ItemStack>> items = copyWithout(g.resolvedItems(), groupId);
		Map<String, List<Object>> fluids = copyWithout(g.resolvedFluids(), groupId);
		desiredRevision++;
		published = new Generation(g.candidates(), items, fluids, g.fullMatchItems(),
			g.fullMatchFluids(), g.fullMatchGeneric(), g.itemReverseIndex(), g.fluidReverseIndex());
	}
	public synchronized void invalidateFullMatch(String groupId) {
		if (published == null) return;
		Generation g = published;
		desiredRevision++;
		published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(),
			copyWithout(g.fullMatchItems(), groupId), copyWithout(g.fullMatchFluids(), groupId),
			copyWithout(g.fullMatchGeneric(), groupId), g.itemReverseIndex(), g.fluidReverseIndex());
	}

	public synchronized void updateFullMatchEntry(String groupId, List<ItemStack> items,
		List<Object> fluids, List<GenericIngredientRef> generic) {
		Generation g = baseGeneration();
		Map<String, List<ItemStack>> nextItems = copyWith(g.fullMatchItems(), groupId, items);
		Map<String, List<Object>> nextFluids = copyWith(g.fullMatchFluids(), groupId, fluids);
		Map<String, List<GenericIngredientRef>> nextGeneric =
			copyWith(g.fullMatchGeneric(), groupId, generic);
		desiredRevision++;
		published = new Generation(g.candidates(), g.resolvedItems(), g.resolvedFluids(),
			nextItems, nextFluids, nextGeneric, g.itemReverseIndex(), g.fluidReverseIndex());
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
		Map<String, List<T>> copy = new LinkedHashMap<>(Math.max(16, values.size() * 2));
		values.forEach((key, value) -> copy.put(key, List.copyOf(value)));
		return Map.copyOf(copy);
	}
	private static @Nullable Map<String, Set<String>> freezeNullableSetMap(
		@Nullable Map<String, Set<String>> values) {
		if (values == null) return null;
		Map<String, Set<String>> copy = new LinkedHashMap<>(Math.max(16, values.size() * 2));
		values.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
		return Map.copyOf(copy);
	}

	private static <T> @Nullable Map<String, List<T>> copyWithout(
		@Nullable Map<String, List<T>> source, String groupId) {
		if (source == null) return null;
		Map<String, List<T>> copy = new LinkedHashMap<>(source);
		copy.remove(groupId);
		return copy;
	}

	private static <T> Map<String, List<T>> copyWith(@Nullable Map<String, List<T>> source,
		String groupId, List<T> values) {
		Map<String, List<T>> copy = source == null
			? new LinkedHashMap<>()
			: new LinkedHashMap<>(source);
		copy.put(groupId, List.copyOf(values));
		return copy;
	}
}
