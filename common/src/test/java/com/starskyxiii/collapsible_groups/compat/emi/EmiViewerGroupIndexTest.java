package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class EmiViewerGroupIndexTest {
    @Test void pendingProjectionRetainsOnlyCurrentReusableGroupsAndNeverPublishesSupersededWork() {
        var executor = new ControlledExecutor();
        var active = new java.util.concurrent.atomic.AtomicBoolean(true);
        var index = new EmiViewerGroupIndex(executor, active::get);
        var universe = new ViewerIngredientUniverse<>(List.of(ingredient("one", "test:one")));
        var stable = group("stable", "test:one");
        var changing = group("changing", "test:one");
        index.requestRebuild(1, universe, List.of(stable, changing));
        executor.runNext();
        assertEquals(2, index.projectableSnapshot().orElseThrow().groups().size());
        var edited = changing.withFilter(new GroupFilter.Id("item", "test:two"));
        index.requestRebuild(1, universe, List.of(stable, edited));
        assertFalse(index.ready());
        assertTrue(index.readyGenerationSnapshot().isEmpty());
        assertEquals(List.of(stable), index.projectableSnapshot().orElseThrow().groups());
        index.requestRebuild(1, universe, List.of(stable));
        executor.runNext();
        assertFalse(index.ready());
        assertEquals(List.of(stable), index.projectableSnapshot().orElseThrow().groups());
        executor.runNext();
        assertTrue(index.ready());
        assertEquals(java.util.Set.of("stable"), index.candidates().orElseThrow().groupSnapshot().keySet());
        active.set(false);
        assertTrue(index.projectableSnapshot().isEmpty());
        active.set(true);
        index.onGroupChange(com.starskyxiii.collapsible_groups.group.GroupChangeEvent.Kind.SOURCE_RELOAD, List.of(stable));
        assertTrue(index.projectableSnapshot().isEmpty());
        executor.runNext();
        assertTrue(index.projectableSnapshot().isPresent());
        index.updateSource(1, new ViewerIngredientUniverse<>(List.of()));
        assertTrue(index.projectableSnapshot().isEmpty());
        index.reset();
        assertTrue(index.projectableSnapshot().isEmpty());
    }

	@Test void headerEditUsesCapturedUniverseAndPreservesFullMatches() {
		var rendered = new java.util.ArrayList<String>();
		var first = ingredient("first", "minecraft:stone");
		var second = ingredient("second", "minecraft:stone");
		var iconTemplate = ingredient("icon", "test:icon");
		EmiIngredient icon = (EmiIngredient) java.lang.reflect.Proxy.newProxyInstance(
			EmiIngredient.class.getClassLoader(), new Class<?>[]{EmiIngredient.class}, (proxy, method, args) -> {
				if (method.getName().equals("render")) rendered.add("icon");
				return null;
			});
		var iconEntry = new ViewerIngredient<>(iconTemplate.identity(), iconTemplate.kind(), icon, iconTemplate.view());
		var universe = new ViewerIngredientUniverse<>(List.of(first, second, iconEntry));
		var group = group("bees", "minecraft:stone");
		var index = new EmiViewerGroupIndex(Runnable::run);
		index.requestRebuild(1, universe, List.of(group)).join();
		var matches = index.fullMatchItems(group.id());
		var display = index.displaySnapshot();
		index.updateSource(2, new ViewerIngredientUniverse<>(List.of()));
		var edited = group.withIconIds(List.of(com.starskyxiii.collapsible_groups.group.GroupIconDefinition.item("test:icon")));
		var preview = display.preview(edited).orElseThrow();
		preview.headers().getFirst().renderer().render(null, 0, 0);
		assertEquals(List.of("icon"), rendered);
		assertEquals(2, preview.headers().size());
		assertEquals(2, preview.items().size());
		assertSame(matches, index.fullMatchItems(group.id()));
		assertEquals(2, display.preview(group.withIconIds(List.of(
			com.starskyxiii.collapsible_groups.group.GroupIconDefinition.item("test:missing"))))
			.orElseThrow().headers().size());
	}

	@Test void replacementSourceCannotPublishAnInFlightResultWithTheSameEpoch() {
		var executor = new ControlledExecutor();
		var index = new EmiViewerGroupIndex(executor);
		var stone = group("stone", "minecraft:stone");
		var old = new ViewerIngredientUniverse<>(List.of(ingredient("old", "minecraft:stone")));
		var replacement = new ViewerIngredientUniverse<>(List.of(ingredient("new", "minecraft:stone")));
		index.requestRebuild(1, old, List.of(stone));
		index.updateSource(1, replacement);
		executor.runNext();
		assertTrue(index.candidates().isEmpty());
		assertFalse(index.ready());
		index.requestRebuild(1, replacement, List.of(stone));
		executor.runNext();
		assertEquals("new", index.fullMatchItems("stone").getFirst().identity().valueId());
	}

	@Test void editsReuseOnlySameSourceGroupsAndReloadReevaluatesThem() {
		var calls = new java.util.HashMap<String, Integer>();
		var entry = new ViewerIngredient<>(new ViewerIngredientIdentity("item", "one"), ViewerIngredient.Kind.ITEM,
			emiIngredient(), new IngredientView() {
				public String ingredientType() { return "item"; }
				public ResourceLocation resourceLocation() { return ResourceLocation.parse("test:one"); }
				public boolean hasTag(ResourceLocation tag) { calls.merge(tag.getPath(), 1, Integer::sum); return true; }
				public boolean matchesExactStack(String encoded) { return false; }
			});
		var universe = new ViewerIngredientUniverse<>(List.of(entry));
		var stable = new GroupDefinition("stable", "stable", true, new GroupFilter.Tag("item", "test:stable"));
		var edited = new GroupDefinition("edited", "edited", true, new GroupFilter.Tag("item", "test:before"));
		var index = new EmiViewerGroupIndex(Runnable::run);
		index.requestRebuild(1, universe, List.of(stable, edited)).join();
		var retained = index.fullMatchItems("stable");
		calls.clear();
        index.onGroupChange(com.starskyxiii.collapsible_groups.group.GroupChangeEvent.Kind.STRUCTURE, List.of(stable, edited));
        assertTrue(calls.isEmpty());
        assertSame(retained, index.fullMatchItems("stable"));
        assertTrue(index.ready());
		edited = edited.withFilter(new GroupFilter.Tag("item", "test:after"));
		var groups = List.of(stable, edited);
		index.requestRebuild(1, universe, groups).join();
		assertEquals(java.util.Map.of("after", 1), calls);
		assertSame(retained, index.fullMatchItems("stable"));
		assertEquals(List.of(entry), index.fullMatchItems("edited"));
		calls.clear();
		index.requestRebuild(1, universe, List.of(stable)).join();
		assertTrue(calls.isEmpty());
		assertTrue(index.fullMatchItems("edited").isEmpty());
		index.requestRebuild(2, universe, groups).join();
		assertEquals(java.util.Map.of("stable", 1, "after", 1), calls);
		calls.clear();
		index.requestRebuild(2, new ViewerIngredientUniverse<>(List.of(entry)), groups).join();
		assertEquals(java.util.Map.of("stable", 1, "after", 1), calls);
		calls.clear();
		index.onGroupChange(com.starskyxiii.collapsible_groups.group.GroupChangeEvent.Kind.KUBEJS_REPLACE, groups);
		assertEquals(java.util.Map.of("stable", 1, "after", 1), calls);
	}

	@Test void bootstrapFailureSettlesWaitersAndReloadCanRecover() {
		ControlledExecutor executor = new ControlledExecutor();
		EmiViewerGroupIndex index = new EmiViewerGroupIndex(executor);
		var waiting = index.whenReady();
		index.failRebuild(new IllegalStateException("bootstrap failed"));
		assertTrue(waiting.isCompletedExceptionally());
		assertTrue(index.displaySnapshot().failed());
		assertFalse(index.displaySnapshot().pending());
		index.reset();
		var group = group("stone", "minecraft:stone");
		var universe = new ViewerIngredientUniverse<>(List.of(ingredient("stone", "minecraft:stone")));
		index.requestRebuild(2, universe, List.of(group));
		executor.runNext();
		assertTrue(index.ready());
		assertFalse(index.displaySnapshot().failed());
		assertTrue(index.displaySnapshot().preview(group).isPresent());
	}

	@Test void reloadGateHidesPublishedResultsAndRejectsLateBuilds() {
		ControlledExecutor executor = new ControlledExecutor();
		var loaded = new java.util.concurrent.atomic.AtomicBoolean(true);
		EmiViewerGroupIndex index = new EmiViewerGroupIndex(executor, loaded::get);
		var universe = new ViewerIngredientUniverse<>(List.of(ingredient("stone", "minecraft:stone")));
		var group = group("stone", "minecraft:stone");
		index.requestRebuild(1, universe, List.of(group));
		executor.runNext();
		assertTrue(index.ready());
		loaded.set(false);
		assertFalse(index.ready());
		assertTrue(index.candidates().isEmpty());
		assertTrue(index.displaySnapshot().preview(group).isEmpty());
		assertTrue(index.resolveOwnership(List.of(group)).isEmpty());
		index.reset();
		index.requestRebuild(2, universe, List.of(group));
		executor.runNext();
		loaded.set(true);
		assertFalse(index.ready());
		assertTrue(index.candidates().isEmpty());
		index.requestRebuild(3, universe, List.of(group));
		executor.runNext();
		assertTrue(index.ready());
	}
	@Test void ownershipSnapshotRejectsPendingCancelledAndReplacedSources() {
		ControlledExecutor executor = new ControlledExecutor();
		EmiViewerGroupIndex index = new EmiViewerGroupIndex(executor);
		var universe = new ViewerIngredientUniverse<>(List.of(ingredient("stone", "minecraft:stone")));
		index.requestRebuild(1, universe, List.of(group("stone", "minecraft:stone")));
		assertTrue(index.readyGenerationSnapshot().isEmpty());
		executor.runNext();
		assertSame(universe, index.readyGenerationSnapshot().orElseThrow().universe());
		index.updateSource(1, new ViewerIngredientUniverse<>(List.of()));
		assertTrue(index.readyGenerationSnapshot().isEmpty());
		var cancelled = index.requestRebuild(2, universe, List.of());
		cancelled.cancel(false);
		executor.runNext();
		assertTrue(index.readyGenerationSnapshot().isEmpty());
		index.requestRebuild(3, universe, List.of());
		executor.runNext();
		assertTrue(index.readyGenerationSnapshot().isPresent());
		index.reset();
		assertTrue(index.readyGenerationSnapshot().isEmpty());
	}
	@Test void ownershipSnapshotUsesCatalogSourceTokenAcrossEquivalentWrappers() {
		ControlledExecutor executor = new ControlledExecutor();
		EmiViewerGroupIndex index = new EmiViewerGroupIndex(executor);
		Object token = new Object();
		var source = new ViewerIngredientUniverse<>(
			List.of(ingredient("stone", "minecraft:stone")), token);
		index.requestRebuild(1, source, List.of(group("stone", "minecraft:stone")));
		executor.runNext();
		var equivalent = new ViewerIngredientUniverse<>(source.ordered(), token);
		index.updateSource(1, equivalent);
		assertTrue(index.readyGenerationSnapshot().isPresent());
		index.updateSource(1, new ViewerIngredientUniverse<>(source.ordered(), new Object()));
		assertTrue(index.readyGenerationSnapshot().isEmpty());
	}
	@Test void coalescesBuildsAndSuppressesAnObsoleteBuildInTheSameEpoch() {
		ControlledExecutor executor = new ControlledExecutor();
		EmiViewerGroupIndex index = new EmiViewerGroupIndex(executor);
		ViewerIngredientUniverse<EmiIngredient> universe = new ViewerIngredientUniverse<>(List.of(
			ingredient("stone", "minecraft:stone"), ingredient("dirt", "minecraft:dirt")));
		GroupDefinition stone = group("stone", "minecraft:stone");
		GroupDefinition dirt = group("dirt", "minecraft:dirt");

		var firstReadiness = index.requestRebuild(4, universe, List.of(stone));
		var newestReadiness = index.requestRebuild(4, universe, List.of(dirt));
		assertEquals(1, executor.size(), "only one generation may be in flight");
		executor.runNext();
		assertFalse(index.ready(), "the obsolete same-epoch result must not publish");
		assertEquals(1, executor.size(), "the newest request starts after the old build exits");
		executor.runNext();

		assertTrue(index.ready());
		assertTrue(firstReadiness.isDone(), "coalesced callers must settle with the newest publication");
		assertTrue(newestReadiness.isDone());
		assertEquals(List.of("dirt"), index.candidates().orElseThrow().candidates()
			.get(new ViewerIngredientIdentity("item", "dirt")));
		assertFalse(index.candidates().orElseThrow().candidates()
			.containsKey(new ViewerIngredientIdentity("item", "stone")));
	}

	@Test void resetAndAllPreviouslyHandedReadinessFuturesSettleOnNewestPublication() {
		ControlledExecutor executor = new ControlledExecutor();
		EmiViewerGroupIndex index = new EmiViewerGroupIndex(executor);
		CompletableFuture<Void> initial = index.whenReady();
		index.reset();
		CompletableFuture<Void> reset = index.whenReady();
		CompletableFuture<Void> requested = index.requestRebuild(9,
			new ViewerIngredientUniverse<>(List.of(ingredient("stone", "minecraft:stone"))),
			List.of(group("stone", "minecraft:stone")));

		assertFalse(initial.isDone());
		assertFalse(reset.isDone());
		executor.runNext();
		assertAll(() -> assertTrue(initial.isDone()), () -> assertTrue(reset.isDone()),
			() -> assertTrue(requested.isDone()));
	}

	@Test void candidateEvaluationFailurePublishesVisibleUnavailableDiagnostic() {
		ControlledExecutor executor = new ControlledExecutor();
		EmiViewerGroupIndex index = new EmiViewerGroupIndex(executor);
		CompletableFuture<Void> initial = index.whenReady();
		ViewerIngredient<EmiIngredient> broken = new ViewerIngredient<>(
			new ViewerIngredientIdentity("item", "broken"), ViewerIngredient.Kind.ITEM, emiIngredient(),
			new IngredientView() {
				@Override public String ingredientType() { return "item"; }
				@Override public ResourceLocation resourceLocation() { throw new IllegalStateException("broken"); }
				@Override public boolean hasTag(ResourceLocation tagId) { return false; }
				@Override public boolean matchesExactStack(String encodedStack) { return false; }
			});
		CompletableFuture<Void> requested = index.requestRebuild(10,
			new ViewerIngredientUniverse<>(List.of(broken)), List.of(group("broken", "minecraft:stone")));

		executor.runNext();
		assertTrue(initial.isDone());
        assertFalse(initial.isCompletedExceptionally());
        assertTrue(requested.isDone());
        assertTrue(index.ready());
        var evaluation = index.evaluation(group("broken", "minecraft:stone"));
        assertEquals(com.starskyxiii.collapsible_groups.group.GroupEvaluation.Status.UNAVAILABLE, evaluation.status());
        assertFalse(evaluation.empty());
        assertTrue(evaluation.issues().stream().anyMatch(issue -> issue.reason().contains("broken")));
	}

	@Test void publishesStableOrderAndThreeNonNullFullMatchBucketsIncludingEmptyOnes() {
		EmiViewerGroupIndex index = new EmiViewerGroupIndex(Runnable::run);
		ViewerIngredient<EmiIngredient> first = ingredient("first", "minecraft:stone");
		ViewerIngredient<EmiIngredient> second = ingredient("second", "minecraft:stone");
		GroupDefinition matching = group("matching", "minecraft:stone");
		GroupDefinition empty = group("empty", "minecraft:air");

		index.requestRebuild(8, new ViewerIngredientUniverse<>(List.of(first, second)), List.of(matching, empty));

		assertEquals(List.of(first, second), index.fullMatchItems("matching"));
		assertNotNull(index.fullMatchItems("empty"));
		assertNotNull(index.fullMatchFluids("empty"));
		assertNotNull(index.fullMatchGeneric("empty"));
		assertTrue(index.fullMatchItems("empty").isEmpty());
		assertTrue(index.fullMatchFluids("empty").isEmpty());
		assertTrue(index.fullMatchGeneric("empty").isEmpty());
		assertTrue(index.displaySnapshot().preview(empty).isPresent());
	}

	@Test void transientProjectionStatesAreNeverMemoized() {
		assertFalse(EmiProjectionController.shouldMemoize(
			new EmiViewerAdapter.ProjectionCacheKey(1, false, false, 1)));
		assertFalse(EmiProjectionController.shouldMemoize(
			new EmiViewerAdapter.ProjectionCacheKey(1, true, false, 2)));
		assertTrue(EmiProjectionController.shouldMemoize(
			new EmiViewerAdapter.ProjectionCacheKey(1, true, true, 3)));
	}

	private static GroupDefinition group(String id, String value) {
		return new GroupDefinition(id, id, true, new GroupFilter.Id("item", value));
	}

	private static ViewerIngredient<EmiIngredient> ingredient(String identity, String id) {
		return new ViewerIngredient<>(new ViewerIngredientIdentity("item", identity), ViewerIngredient.Kind.ITEM,
			emiIngredient(), new IngredientView() {
				@Override public String ingredientType() { return "item"; }
				@Override public ResourceLocation resourceLocation() { return ResourceLocation.parse(id); }
				@Override public boolean hasTag(ResourceLocation tagId) { return false; }
				@Override public boolean matchesExactStack(String encodedStack) { return false; }
			});
	}

	private static EmiIngredient emiIngredient() {
		return (EmiIngredient) java.lang.reflect.Proxy.newProxyInstance(EmiIngredient.class.getClassLoader(),
			new Class<?>[]{EmiIngredient.class}, (proxy, method, args) -> null);
	}

	private static final class ControlledExecutor implements Executor {
		private final Queue<Runnable> tasks = new ArrayDeque<>();
		@Override public void execute(Runnable command) { tasks.add(command); }
		int size() { return tasks.size(); }
		void runNext() { tasks.remove().run(); }
	}
}
