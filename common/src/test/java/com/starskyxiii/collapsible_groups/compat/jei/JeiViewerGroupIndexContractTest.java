package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.group.GroupChangeEvent;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.compat.jei.data.GenericIngredientRef;
import com.starskyxiii.collapsible_groups.viewer.GroupCandidateIndex;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Map;
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
				GroupChangeEvent.Kind.STRUCTURE, GroupChangeEvent.Kind.KUBEJS_REPLACE)
			.flatMap(event -> Stream.of(Layer.values()).map(layer -> DynamicTest.dynamicTest(
				event + " / " + layer, () -> assertCell(event, layer))));
	}

	private static void assertCell(GroupChangeEvent.Kind event, Layer layer) {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition enabled = group("contract_group", true);
		GroupCandidateIndex originalCandidate = candidate(enabled);
		index.publishGeneration(generation(originalCandidate, enabled));
		Map<?, ?> originalResolved = index.resolvedItemsCache();
		Map<?, ?> originalFullMatch = index.fullMatchItems();
		AtomicReference<String> rebuildThread = new AtomicReference<>();
		index.configureRebuild(() -> {
			rebuildThread.set(Thread.currentThread().getName());
			return generation(candidate(enabled), enabled);
		}, Runnable::run, () -> {});

		List<GroupDefinition> currentGroups = event == GroupChangeEvent.Kind.ENABLED
			? List.of(enabled.withEnabled(false)) : List.of(enabled);
		index.onGroupChange(event, currentGroups);
		index.whenReady().join();

		boolean rebuild = event == GroupChangeEvent.Kind.FULL || event == GroupChangeEvent.Kind.KUBEJS_REPLACE;
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
			case FULL_MATCH -> {
				if (rebuild) assertNotSame(originalFullMatch, index.fullMatchItems());
				else assertEquals(originalFullMatch, index.fullMatchItems());
			}
			case PREVIEW -> assertTrue(index.previewCachesValid());
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
		assertNotNull(index.fullMatchItems());
		assertNotNull(index.fullMatchFluids());
		assertNotNull(index.fullMatchGeneric());
		assertEquals(1, builds.get());
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
	void fullMatchEntryUpdateIsAtomicAndPublishedMapsAreReadOnly() {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition group = group("atomic_preview", true);
		index.publishGeneration(generation(candidate(group), group));
		JeiViewerGroupIndex.FullMatchCacheSnapshot before = index.fullMatchSnapshot();
		assertNotNull(before.entry(group.id()));
		assertTrue(before.entry(group.id()).items().isEmpty());

		Object fluid = new Object();
		GenericIngredientRef generic = new GenericIngredientRef("test:type", null, new Object());
		index.updateFullMatchEntry(group.id(), List.of(), List.of(fluid), List.of(generic));

		JeiViewerGroupIndex.FullMatchCacheSnapshot after = index.fullMatchSnapshot();
		JeiViewerGroupIndex.FullMatchEntry updated = after.entry(group.id());
		assertNotNull(updated);
		assertTrue(updated.items().isEmpty());
		assertEquals(List.of(fluid), updated.fluids());
		assertEquals(List.of(generic), updated.generic());
		assertTrue(before.entry(group.id()).items().isEmpty());
		assertThrows(UnsupportedOperationException.class,
			() -> after.items().put("forbidden", List.of()));
		assertThrows(UnsupportedOperationException.class,
			() -> after.fluids().remove(group.id()));

		index.invalidateFullMatch(group.id());
		assertNull(index.fullMatchSnapshot().entry(group.id()));
		assertNotNull(after.entry(group.id()));
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
		index.updateFullMatchEntry(group.id(), List.of(), List.of(new Object()), List.of());
		firstRelease.countDown();

		readiness.join();
		assertEquals(2, builds.get());
		assertTrue(listenerCalled.await(10, TimeUnit.SECONDS));
		assertEquals(1, listeners.get());
		assertTrue(index.ready());
	}

	@Test
	void failedFullRebuildKeepsPreviewAndNextEventCanRetry() {
		JeiViewerGroupIndex index = JeiViewerGroupIndex.instance();
		index.reset();
		GroupDefinition group = group("retry", true);
		index.publishGeneration(generation(candidate(group), group));
		AtomicInteger builds = new AtomicInteger();
		index.configureRebuild(() -> {
			if (builds.getAndIncrement() == 0) throw new IllegalStateException("expected");
			return generation(candidate(group), group);
		}, Runnable::run, () -> {});

		index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(group));
		assertThrows(CompletionException.class, () -> index.whenReady().join());
		assertNotNull(index.fullMatchSnapshot().entry(group.id()));

		index.onGroupChange(GroupChangeEvent.Kind.FULL, List.of(group));
		index.whenReady().join();
		assertEquals(2, builds.get());
		assertTrue(index.ready());
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
		assertFalse(index.fullMatchSnapshot().complete());
		assertNull(index.fullMatchItems());
		assertNull(index.fullMatchFluids());
		assertNull(index.fullMatchGeneric());
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
		assertEquals(previewRetained, index.fullMatchSnapshot().complete());
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
		return new JeiViewerGroupIndex.Generation(candidate, Map.of(group.id(), List.of()),
			Map.of(group.id(), List.of()), Map.of(group.id(), List.of()), Map.of(group.id(), List.of()),
			Map.of(group.id(), List.of()), Map.of(), Map.of());
	}

	private static GroupDefinition group(String id, boolean enabled) {
		return new GroupDefinition(id, id, enabled, Filters.itemId("minecraft:stone"));
	}

	private static GroupCandidateIndex candidate(GroupDefinition group) {
		return new GroupCandidateIndex(Map.of(), Map.of(group.id(), group), 0, 0, 0);
	}
}
