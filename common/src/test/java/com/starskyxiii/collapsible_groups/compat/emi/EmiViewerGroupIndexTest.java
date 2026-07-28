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

	@Test void newestBuildFailureSettlesEveryReadinessFutureExceptionally() {
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
		assertTrue(initial.isCompletedExceptionally());
		assertTrue(requested.isCompletedExceptionally());
		assertFalse(index.ready());
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
		assertTrue(index.fullMatchSnapshot(empty).isPresent());
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
