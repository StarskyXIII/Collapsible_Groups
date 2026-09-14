package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import mezz.jei.api.runtime.IIngredientManager;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JeiViewerGroupIndexContractTest {
	private enum Layer { CANDIDATES, RESOLVED, FULL_MATCH, PREVIEW }

	@TestFactory
	Stream<DynamicTest> everyLifecycleTableCellIsEnforced() {
		return Stream.of(GroupChangeEvent.Kind.FULL, GroupChangeEvent.Kind.ENABLED,
				GroupChangeEvent.Kind.STRUCTURE, GroupChangeEvent.Kind.KUBEJS_REPLACE, GroupChangeEvent.Kind.SOURCE_RELOAD)
			.flatMap(event -> Stream.of(Layer.values()).map(layer -> DynamicTest.dynamicTest(
				event + " / " + layer, () -> assertCell(event, layer))));
	}

	private static void assertCell(GroupChangeEvent.Kind event, Layer layer) {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition enabled = group("contract_group", true);
		GroupCandidateIndex originalCandidate = candidate(enabled);
		index.publishGeneration(generation(originalCandidate, enabled, List.of(new Object())));
		Map<?, ?> originalResolved = index.resolvedItemsCache();
		AtomicReference<String> rebuildThread = new AtomicReference<>();
		index.configureRebuild(() -> {
			rebuildThread.set(Thread.currentThread().getName());
			return generation(candidate(enabled), enabled, List.of(new Object(), new Object()));
		}, Runnable::run, () -> {});

		List<GroupDefinition> currentGroups = event == GroupChangeEvent.Kind.ENABLED
			? List.of(enabled.withEnabled(false)) : List.of(enabled);
		index.onGroupChange(event, currentGroups);
		index.whenReady().join();

		boolean rebuild = event == GroupChangeEvent.Kind.FULL || event == GroupChangeEvent.Kind.KUBEJS_REPLACE
			|| event == GroupChangeEvent.Kind.SOURCE_RELOAD;
		switch (layer) {
			case CANDIDATES -> {
				if (rebuild) {
					assertNotSame(originalCandidate, index.candidates().orElseThrow());
					assertTrue(rebuildThread.get().startsWith("CG-IndexRebuild"));
				} else assertSame(originalCandidate, index.candidates().orElseThrow());
			}
			case RESOLVED -> {
				if (event == GroupChangeEvent.Kind.STRUCTURE) assertSame(originalResolved, index.resolvedItemsCache());
				else assertNotSame(originalResolved, index.resolvedItemsCache());
				assertTrue(index.ready());
			}
			case FULL_MATCH -> assertEquals(rebuild ? 2 : 1,
				index.displaySnapshot().preview(enabled).orElseThrow().fluids().size());
			case PREVIEW -> assertTrue(index.displaySnapshot().preview(enabled).isPresent());
		}
	}

	@Test
	void rebuildRequestsAreCoalescedOffCallerAndServeStalePreviews() throws Exception {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition group = group("single_flight", true);
		GroupCandidateIndex stale = candidate(group);
		index.publishGeneration(generation(stale, group));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch listenerCalled = new CountDownLatch(1);
		AtomicInteger builds = new AtomicInteger();
		AtomicInteger listeners = new AtomicInteger();
		AtomicReference<String> buildThread = new AtomicReference<>();
		index.configureRebuild(() -> {
			builds.incrementAndGet();
			buildThread.set(Thread.currentThread().getName());
			entered.countDown();
			try {
				assertTrue(release.await(10, TimeUnit.SECONDS));
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
			return generation(candidate(group), group);
		}, Runnable::run, () -> {
			listeners.incrementAndGet();
			listenerCalled.countDown();
		});

		index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(group));
		assertTrue(entered.await(10, TimeUnit.SECONDS));
		var first = index.whenReady();
		assertSame(first, index.requestRebuild(List.of(group)));
		assertSame(first, index.ensureReadyAsync(List.of(group)));
		assertSame(stale, index.candidates().orElseThrow());
		assertNull(index.resolvedItemsCache());
		assertTrue(index.displaySnapshot().preview(group).isPresent());
		assertEquals(1, builds.get());
		var display = index.displaySnapshot();
		assertTrue(display.pending());
		assertTrue(display.preview(group).isPresent());
		index.updateUniverse(new ViewerIngredientUniverse<>(List.of()));
		assertTrue(display.preview(group).isPresent());
		index.onGroupChange(GroupChangeEvent.Kind.SOURCE_RELOAD, List.of(group));
		assertTrue(index.displaySnapshot().preview(group).isEmpty());
		assertTrue(display.preview(group).isPresent());
		release.countDown();
		first.join();
		assertEquals(2, builds.get());
		assertTrue(listenerCalled.await(10, TimeUnit.SECONDS));
		assertEquals(1, listeners.get());
		assertTrue(buildThread.get().startsWith("CG-IndexRebuild"));
		assertNotEquals(Thread.currentThread().getName(), buildThread.get());
		assertTrue(index.ready());
	}

	@Test
	void publishedPreviewKindsAreImmutableAndCapturedDisplaySurvivesReplacement() {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition group = group("atomic_preview", true);
		index.publishGeneration(generation(candidate(group), group));
		var before = index.displaySnapshot();
		Object fluid = new Object();
		GenericIngredientRef generic = new GenericIngredientRef("test:type", null, new Object());
		var replacement = new JeiViewerGroupIndex.Generation(candidate(group), Map.of(), Map.of(),
			Map.of(group.id(), List.of()), Map.of(group.id(), List.of(fluid)), Map.of(group.id(), List.of(generic)), Map.of(), Map.of());
		index.publishGeneration(replacement);
		var after = index.displaySnapshot();
		assertEquals(0, before.preview(group).orElseThrow().allValues().size());
		assertEquals(1, after.preview(group).orElseThrow().fluids().size());
		assertEquals(1, after.preview(group).orElseThrow().generic().size());
		assertThrows(UnsupportedOperationException.class, () -> replacement.fullMatchItems().put("forbidden", List.of()));
		assertThrows(UnsupportedOperationException.class, () -> replacement.fullMatchFluids().remove(group.id()));
		index.invalidateFullMatch(group.id());
		assertTrue(index.displaySnapshot().preview(group).isEmpty());
		assertTrue(after.preview(group).isPresent());
	}

	@Test
	void staleBuildCannotPublishAfterPreviewMutationAndTrailingBuildOwnsReadiness() throws Exception {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition group = group("interleaved", true);
		index.publishGeneration(generation(candidate(group), group));
		CountDownLatch firstEntered = new CountDownLatch(1);
		CountDownLatch firstRelease = new CountDownLatch(1);
		CountDownLatch listenerCalled = new CountDownLatch(1);
		AtomicInteger builds = new AtomicInteger();
		AtomicInteger listeners = new AtomicInteger();
		index.configureRebuild(() -> {
			int build = builds.incrementAndGet();
			if (build == 1) {
				firstEntered.countDown();
				try {
					assertTrue(firstRelease.await(10, TimeUnit.SECONDS));
				} catch (InterruptedException e) {
					throw new AssertionError(e);
				}
			}
			return generation(candidate(group), group);
		}, Runnable::run, () -> {
			listeners.incrementAndGet();
			listenerCalled.countDown();
		});

		index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(group));
		assertTrue(firstEntered.await(10, TimeUnit.SECONDS));
		CompletableFuture<Void> readiness = index.whenReady();
		index.invalidateFullMatch(group.id());
		firstRelease.countDown();

		readiness.join();
		assertEquals(2, builds.get());
		assertTrue(listenerCalled.await(10, TimeUnit.SECONDS));
		assertEquals(1, listeners.get());
		assertTrue(index.ready());
	}

	@Test
	void failedFullRebuildKeepsPreviewAndNextEventCanRetry() throws Exception {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition group = group("retry", true);
		index.publishGeneration(generation(candidate(group), group));
		AtomicInteger builds = new AtomicInteger();
		AtomicInteger listeners = new AtomicInteger();
		CountDownLatch listenerCalled = new CountDownLatch(1);
		index.configureRebuild(() -> {
			if (builds.getAndIncrement() == 0) throw new IllegalStateException("expected");
			return generation(candidate(group), group);
		}, Runnable::run, () -> {
			listeners.incrementAndGet();
			listenerCalled.countDown();
		});

		index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(group));
		assertThrows(CompletionException.class, () -> index.whenReady().join());
		assertTrue(index.displaySnapshot().preview(group).isPresent());
		assertTrue(index.readyGenerationSnapshot().isEmpty());
		assertEquals(0, listeners.get());

		index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(group));
		index.whenReady().join();
		assertTrue(listenerCalled.await(10, TimeUnit.SECONDS));
		assertEquals(2, builds.get());
		assertEquals(1, listeners.get());
		assertTrue(index.ready());
	}

	@Test
	void overlayReadinessRequiresAndPreservesProjectionContext() {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition group = group("projection_context", true);
		GroupCandidateIndex candidates = candidate(group);
		IIngredientManager manager = (IIngredientManager) Proxy.newProxyInstance(
			getClass().getClassLoader(), new Class<?>[]{IIngredientManager.class},
			(proxy, method, args) -> { throw new UnsupportedOperationException(method.toString()); });
		JeiViewerAdapter.ProjectionContext context = new JeiViewerAdapter.ProjectionContext(
			manager, new ViewerIngredientUniverse<>(List.of()), Map.of(), List.of());
		JeiViewerGroupIndex.Generation complete = new JeiViewerGroupIndex.Generation(
			candidates, Map.of(group.id(), List.of()), Map.of(group.id(), List.of()),
			Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), context);

		index.publishGeneration(complete);
		assertSame(context, index.readyGenerationSnapshot().orElseThrow().projectionContext());
		index.invalidateFullMatch(group.id());
		assertSame(context, index.readyGenerationSnapshot().orElseThrow().projectionContext());

		index.publishGeneration(new JeiViewerGroupIndex.Generation(
			candidates, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
		assertTrue(index.readyGenerationSnapshot().isEmpty());
	}

	@Test
	void kubeJsReplacementClearsAllPreviewKindsWhilePending() throws Exception {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition group = group("kubejs_pending", true);
		index.publishGeneration(generation(candidate(group), group));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		index.configureRebuild(() -> {
			entered.countDown();
			try {
				assertTrue(release.await(10, TimeUnit.SECONDS));
			} catch (InterruptedException e) {
				throw new AssertionError(e);
			}
			return generation(candidate(group), group);
		}, Runnable::run, () -> {});

		index.onGroupChange(GroupChangeEvent.Kind.KUBEJS_REPLACE, List.of(group));
		assertTrue(entered.await(10, TimeUnit.SECONDS));
		assertNull(index.displaySnapshot().candidates());
		release.countDown();
		index.whenReady().join();
	}

	@Test
	void fullAndKubeJsEventsCoalesceWithoutPublishingTheFirstBuild() throws Exception {
		assertEventPair(GroupChangeEvent.Kind.FULL, GroupChangeEvent.Kind.FULL, true);
		assertEventPair(GroupChangeEvent.Kind.FULL, GroupChangeEvent.Kind.KUBEJS_REPLACE, false);
		assertEventPair(GroupChangeEvent.Kind.KUBEJS_REPLACE, GroupChangeEvent.Kind.FULL, false);
	}

	private static void assertEventPair(GroupChangeEvent.Kind firstKind,
		GroupChangeEvent.Kind secondKind, boolean previewRetained) throws Exception {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		String suffix = firstKind.name().toLowerCase() + "_" + secondKind.name().toLowerCase();
		GroupDefinition first = group("first_" + suffix, true);
		GroupDefinition latest = group("latest_" + suffix, true);
		index.publishGeneration(generation(candidate(first), first));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger builds = new AtomicInteger();
		index.configureRebuild(() -> {
			int build = builds.incrementAndGet();
			if (build == 1) {
				entered.countDown();
				try {
					assertTrue(release.await(10, TimeUnit.SECONDS));
				} catch (InterruptedException e) {
					throw new AssertionError(e);
				}
				return generation(candidate(first), first);
			}
			return generation(candidate(latest), latest);
		}, Runnable::run, () -> {});

		index.onGroupChange(firstKind, List.of(first));
		assertTrue(entered.await(10, TimeUnit.SECONDS));
		CompletableFuture<Void> readiness = index.whenReady();
		index.onGroupChange(secondKind, List.of(latest));
		assertEquals(previewRetained, (index.displaySnapshot().candidates() != null));
		release.countDown();

		readiness.join();
		assertEquals(2, builds.get());
		assertTrue(index.candidates().orElseThrow().groupSnapshot().containsKey(latest.id()));
		assertFalse(index.candidates().orElseThrow().groupSnapshot().containsKey(first.id()));
	}

	@Test
	void completedGenerationContainsEveryFullMatchKindIncludingEmptyGroupEntries() {
		GroupDefinition matching = group("matching", true);
		GroupDefinition empty = group("empty", true);
		JeiViewerGroupIndex.Generation generation = new JeiViewerGroupIndex.Generation(
			candidate(matching), Map.of(matching.id(), List.of(), empty.id(), List.of()),
			Map.of(matching.id(), List.of(), empty.id(), List.of()),
			Map.of(matching.id(), List.of(), empty.id(), List.of()),
			Map.of(matching.id(), List.of(), empty.id(), List.of()),
			Map.of(matching.id(), List.of(), empty.id(), List.of()), Map.of(), Map.of());

		assertEquals(List.of(matching.id(), empty.id()).stream().sorted().toList(),
			generation.fullMatchItems().keySet().stream().sorted().toList());
		assertEquals(generation.fullMatchItems().keySet(), generation.fullMatchFluids().keySet());
		assertEquals(generation.fullMatchItems().keySet(), generation.fullMatchGeneric().keySet());
		assertTrue(generation.fullMatchItems().get(empty.id()).isEmpty());
	}

	private static JeiViewerGroupIndex.Generation generation(GroupCandidateIndex candidate,
		GroupDefinition group) {
		return generation(candidate, group, List.of());
	}

	private static JeiViewerGroupIndex.Generation generation(GroupCandidateIndex candidate,
		GroupDefinition group, List<Object> fluids) {
		return new JeiViewerGroupIndex.Generation(candidate, Map.of(group.id(), List.of()),
			Map.of(group.id(), List.of()), Map.of(group.id(), List.of()), Map.of(group.id(), fluids),
			Map.of(group.id(), List.of()), Map.of(), Map.of());
	}

	private static GroupDefinition group(String id, boolean enabled) {
		return new GroupDefinition(id, id, enabled, Filters.itemId("minecraft:stone"));
	}

	private static GroupCandidateIndex candidate(GroupDefinition group) {
		return new GroupCandidateIndex(Map.of(), Map.of(group.id(), group), 0, 0, 0);
	}
}
