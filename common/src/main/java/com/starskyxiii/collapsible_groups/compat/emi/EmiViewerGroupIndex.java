package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.GroupProjectionEngine;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerGroupPreviewSnapshot;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import com.starskyxiii.collapsible_groups.viewer.ViewerPreviewValue;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import dev.emi.emi.api.stack.EmiIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** EMI-owned candidate and editor-preview generation built from {@code EmiStackList.stacks}. */
public final class EmiViewerGroupIndex implements ViewerGroupIndex {
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "CollapsibleGroups-EmiIndex");
		thread.setDaemon(true);
		return thread;
	});

	public record Generation(
		long epoch,
		long buildGeneration,
		ViewerIngredientUniverse<EmiIngredient> universe,
		GroupCandidateIndex candidates,
		Map<String, List<ViewerIngredient<EmiIngredient>>> fullMatchItems,
		Map<String, List<ViewerIngredient<EmiIngredient>>> fullMatchFluids,
		Map<String, List<ViewerIngredient<EmiIngredient>>> fullMatchGeneric
	) {
		public Generation {
			fullMatchItems = freeze(fullMatchItems);
			fullMatchFluids = freeze(fullMatchFluids);
			fullMatchGeneric = freeze(fullMatchGeneric);
		}
	}

	private final Executor executor;
	private volatile @Nullable Generation published;
	private volatile ViewerIngredientUniverse<EmiIngredient> sourceUniverse = emptyUniverse();
	private volatile List<GroupDefinition> currentGroups = List.of();
	private volatile long sourceEpoch = -1;
	private volatile long requestedBuildGeneration;
	private volatile long runningBuildGeneration = -1;
	private volatile CompletableFuture<Void> readyFuture = new CompletableFuture<>();
	private final List<CompletableFuture<Void>> readinessWaiters = new ArrayList<>();
	private boolean rebuildRequested;
	private volatile long revision;

	public EmiViewerGroupIndex() { this(EXECUTOR); }

	EmiViewerGroupIndex(Executor executor) {
		this.executor = executor;
		readinessWaiters.add(readyFuture);
	}

	public synchronized void updateSource(long epoch, ViewerIngredientUniverse<EmiIngredient> universe) {
		sourceEpoch = epoch;
		sourceUniverse = universe;
	}

	public synchronized CompletableFuture<Void> requestRebuild(long epoch,
		ViewerIngredientUniverse<EmiIngredient> universe, List<GroupDefinition> groups) {
		updateSource(epoch, universe);
		currentGroups = List.copyOf(groups);
		requestedBuildGeneration++;
		readyFuture = new CompletableFuture<>();
		readinessWaiters.add(readyFuture);
		rebuildRequested = true;
		revision++;
		startNextIfIdle();
		return readyFuture;
	}

	private void startNextIfIdle() {
		if (runningBuildGeneration >= 0 || !rebuildRequested) return;
		long build = requestedBuildGeneration;
		long epoch = sourceEpoch;
		ViewerIngredientUniverse<EmiIngredient> universe = sourceUniverse;
		List<GroupDefinition> groups = currentGroups;
		runningBuildGeneration = build;
		rebuildRequested = false;
		CompletableFuture<Generation> computation = CompletableFuture.supplyAsync(
			() -> buildGeneration(epoch, build, universe, groups), executor);
		computation.handle((generation, error) -> {
			List<CompletableFuture<Void>> settled = List.of();
			synchronized (this) {
				if (error == null && build == requestedBuildGeneration && epoch == sourceEpoch) {
					published = withCurrentEnabledState(generation);
					revision++;
					settled = drainReadinessWaiters();
				}
				runningBuildGeneration = -1;
				if (build != requestedBuildGeneration) startNextIfIdle();
				if (error != null && build == requestedBuildGeneration) {
					settled = drainReadinessWaiters();
				}
			}
			for (CompletableFuture<Void> waiter : settled) {
				if (error == null) waiter.complete(null);
				else waiter.completeExceptionally(error);
			}
			return null;
		});
	}

	private List<CompletableFuture<Void>> drainReadinessWaiters() {
		List<CompletableFuture<Void>> settled = List.copyOf(readinessWaiters);
		readinessWaiters.clear();
		return settled;
	}

	private static Generation buildGeneration(long epoch, long build,
		ViewerIngredientUniverse<EmiIngredient> universe, List<GroupDefinition> groups) {
		GroupCandidateIndex candidates = GroupProjectionEngine.buildCandidateIndex(universe, groups);
		Map<String, List<ViewerIngredient<EmiIngredient>>> items = buckets(groups);
		Map<String, List<ViewerIngredient<EmiIngredient>>> fluids = buckets(groups);
		Map<String, List<ViewerIngredient<EmiIngredient>>> generic = buckets(groups);
		for (ViewerIngredient<EmiIngredient> ingredient : universe.ordered()) {
			for (String groupId : candidates.candidates().getOrDefault(ingredient.identity(), List.of())) {
				switch (ingredient.kind()) {
					case ITEM -> items.get(groupId).add(ingredient);
					case FLUID -> fluids.get(groupId).add(ingredient);
					case GENERIC -> generic.get(groupId).add(ingredient);
				}
			}
		}
		return new Generation(epoch, build, universe, candidates, items, fluids, generic);
	}

	private Generation withCurrentEnabledState(Generation generation) {
		// Full-match maps are enabled-independent. Ownership is resolved against currentGroups on demand.
		return generation;
	}

	@Override public Optional<GroupCandidateIndex> candidates() {
		Generation current = published;
		return current == null ? Optional.empty() : Optional.of(current.candidates());
	}

	@Override public boolean ready() {
		Generation current = published;
		return current != null && current.epoch() == sourceEpoch
			&& current.buildGeneration() == requestedBuildGeneration && runningBuildGeneration < 0;
	}

	@Override public CompletableFuture<Void> whenReady() { return readyFuture; }

	@Override public Optional<ViewerGroupPreviewSnapshot> fullMatchSnapshot(GroupDefinition group) {
		Generation current = published;
		if (current == null) return Optional.empty();
		String groupId = group.id();
		if (!current.fullMatchItems().containsKey(groupId)
			|| !current.fullMatchFluids().containsKey(groupId)
			|| !current.fullMatchGeneric().containsKey(groupId)) return Optional.empty();
		return Optional.of(new ViewerGroupPreviewSnapshot(
			previewValues(current.fullMatchItems().get(groupId), true),
			previewValues(current.fullMatchFluids().get(groupId), false),
			previewValues(current.fullMatchGeneric().get(groupId), false)));
	}

	private static List<ViewerPreviewValue> previewValues(
		List<ViewerIngredient<EmiIngredient>> ingredients, boolean items) {
		return ingredients.stream().map(ingredient -> {
			if (items && ingredient.view() instanceof ItemStackIngredientView item) {
				return ViewerPreviewValue.item(item.stack());
			}
			return ViewerPreviewValue.rendered(
				(graphics, x, y) -> ingredient.entry().render(graphics, x, y, 0));
		}).toList();
	}

	public long revision() { return revision; }
	public long epoch() { return sourceEpoch; }
	public long requestedBuildGeneration() { return requestedBuildGeneration; }

	@Override public Optional<String> resolveOwner(ViewerIngredientIdentity identity,
		List<GroupDefinition> groups) {
		return Optional.ofNullable(resolveOwnership(groups).get(identity));
	}

	@Override public Map<ViewerIngredientIdentity, String> resolveOwnership(List<GroupDefinition> groups) {
		Generation current = published;
		return current == null ? Map.of() : GroupProjectionEngine.resolveOwnership(current.candidates(), groups);
	}

	@Override public synchronized void onGroupChange(GroupChangeEvent.Kind kind, List<GroupDefinition> groups) {
		currentGroups = List.copyOf(groups);
		switch (kind) {
			case FULL, KUBEJS_REPLACE -> requestRebuild(sourceEpoch, sourceUniverse, groups);
			case ENABLED -> revision++;
			case STRUCTURE -> { }
		}
	}

	public synchronized void reset() {
		sourceEpoch++;
		requestedBuildGeneration++;
		published = null;
		sourceUniverse = emptyUniverse();
		currentGroups = List.of();
		readyFuture = new CompletableFuture<>();
		readinessWaiters.add(readyFuture);
		rebuildRequested = false;
		revision++;
	}

	public List<ViewerIngredient<EmiIngredient>> fullMatchItems(String groupId) {
		Generation current = published;
		return current == null ? List.of() : current.fullMatchItems().getOrDefault(groupId, List.of());
	}

	public List<ViewerIngredient<EmiIngredient>> fullMatchFluids(String groupId) {
		Generation current = published;
		return current == null ? List.of() : current.fullMatchFluids().getOrDefault(groupId, List.of());
	}

	public List<ViewerIngredient<EmiIngredient>> fullMatchGeneric(String groupId) {
		Generation current = published;
		return current == null ? List.of() : current.fullMatchGeneric().getOrDefault(groupId, List.of());
	}

	/** Replaces one editor draft's three entries together, including explicit empty lists. */
	public synchronized void prepareFullMatch(GroupDefinition definition) {
		Generation current = published;
		if (current == null) return;
		Generation draft = buildGeneration(current.epoch(), current.buildGeneration(), current.universe(),
			List.of(definition));
		Map<String, List<ViewerIngredient<EmiIngredient>>> items = mutable(current.fullMatchItems());
		Map<String, List<ViewerIngredient<EmiIngredient>>> fluids = mutable(current.fullMatchFluids());
		Map<String, List<ViewerIngredient<EmiIngredient>>> generic = mutable(current.fullMatchGeneric());
		items.put(definition.id(), draft.fullMatchItems().getOrDefault(definition.id(), List.of()));
		fluids.put(definition.id(), draft.fullMatchFluids().getOrDefault(definition.id(), List.of()));
		generic.put(definition.id(), draft.fullMatchGeneric().getOrDefault(definition.id(), List.of()));
		published = new Generation(current.epoch(), current.buildGeneration(), current.universe(),
			current.candidates(), items, fluids, generic);
		revision++;
	}

	public synchronized void invalidateFullMatch(String groupId) {
		Generation current = published;
		if (current == null) return;
		Map<String, List<ViewerIngredient<EmiIngredient>>> items = mutable(current.fullMatchItems());
		Map<String, List<ViewerIngredient<EmiIngredient>>> fluids = mutable(current.fullMatchFluids());
		Map<String, List<ViewerIngredient<EmiIngredient>>> generic = mutable(current.fullMatchGeneric());
		items.remove(groupId); fluids.remove(groupId); generic.remove(groupId);
		published = new Generation(current.epoch(), current.buildGeneration(), current.universe(),
			current.candidates(), items, fluids, generic);
	}

	private static Map<String, List<ViewerIngredient<EmiIngredient>>> buckets(List<GroupDefinition> groups) {
		Map<String, List<ViewerIngredient<EmiIngredient>>> result = new LinkedHashMap<>();
		groups.forEach(group -> result.put(group.id(), new ArrayList<>()));
		return result;
	}

	private static Map<String, List<ViewerIngredient<EmiIngredient>>> mutable(
		Map<String, List<ViewerIngredient<EmiIngredient>>> source) {
		Map<String, List<ViewerIngredient<EmiIngredient>>> result = new LinkedHashMap<>();
		source.forEach((key, value) -> result.put(key, new ArrayList<>(value)));
		return result;
	}

	private static Map<String, List<ViewerIngredient<EmiIngredient>>> freeze(
		Map<String, List<ViewerIngredient<EmiIngredient>>> source) {
		Map<String, List<ViewerIngredient<EmiIngredient>>> result = new LinkedHashMap<>();
		source.forEach((key, value) -> result.put(key, List.copyOf(value)));
		return Map.copyOf(result);
	}

	private static ViewerIngredientUniverse<EmiIngredient> emptyUniverse() {
		return new ViewerIngredientUniverse<>(List.of());
	}
}
