package com.starskyxiii.collapsible_groups.client.editor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EditorTagCatalogTest {
	private final Object generation = new Object();
	private final EditorTagCatalog catalog = new EditorTagCatalog(() -> 0);

	private void update(List<EditorTagCatalog.Source> sources) {
		catalog.update(generation, "chemical", EditorIngredientTags.Status.READY,
			EditorIngredientTags.Coverage.REGISTRY_BACKED, sources::iterator, () -> true);
	}

	@Test void sortsDeduplicatesAndIncludesEmptyRegistryTags() {
		update(List.of(new EditorTagCatalog.Source(null, () -> Stream.of("c:z", "c:empty", "c:z", "c:a"))));
		var result = catalog.snapshot(generation, "chemical");
		assertEquals(List.of("c:a", "c:empty", "c:z"), result.tags());
		assertEquals(EditorIngredientTags.Status.READY, result.status());
		assertFalse(result.partial());
		assertThrows(UnsupportedOperationException.class, () -> result.tags().add("c:b"));
	}

	@Test void sharedRegistrySetsAreReadOnce() {
		Object shared = new Object();
		AtomicInteger reads = new AtomicInteger();
		var sources = IntStream.range(0, 1000).mapToObj(i -> new EditorTagCatalog.Source(shared,
			() -> { reads.incrementAndGet(); return Stream.of("c:empty"); })).toList();
		for (int i = 0; i < 70; i++) update(sources);
		assertEquals(1, reads.get());
		assertEquals(List.of("c:empty"), catalog.snapshot(generation, "chemical").tags());
	}

	@Test void lazyStreamsAreBoundedAndClosedOnCancel() {
		AtomicInteger reads = new AtomicInteger();
		AtomicInteger closes = new AtomicInteger();
		update(List.of(new EditorTagCatalog.Source(null, () -> Stream.generate(() -> "c:t" + reads.incrementAndGet())
			.onClose(closes::incrementAndGet))));
		assertTrue(reads.get() > 0 && reads.get() <= 256);
		assertEquals(EditorIngredientTags.Status.PENDING, catalog.snapshot(generation, "chemical").status());
		assertTrue(catalog.snapshot(generation, "chemical").tags().isEmpty());
		catalog.cancel();
		assertEquals(1, closes.get());
		catalog.cancel();
		assertEquals(1, closes.get());
	}

	@Test void helperCallsAreBounded() {
		AtomicInteger calls = new AtomicInteger();
		var sources = IntStream.range(0, 100).mapToObj(i -> new EditorTagCatalog.Source(null,
			() -> { calls.incrementAndGet(); return Stream.empty(); })).toList();
		update(sources);
		assertEquals(16, calls.get());
		assertEquals(EditorIngredientTags.Status.PENDING, catalog.snapshot(generation, "chemical").status());
		for (int i = 0; i < 7; i++) update(sources);
		assertEquals(100, calls.get());
		assertEquals(EditorIngredientTags.Status.READY, catalog.snapshot(generation, "chemical").status());
	}

	@Test void timeBudgetStopsCollection() {
		AtomicLong clock = new AtomicLong();
		var timed = new EditorTagCatalog(() -> clock.getAndAdd(1_000_000));
		AtomicInteger calls = new AtomicInteger();
		timed.update(generation, "chemical", EditorIngredientTags.Status.READY,
			EditorIngredientTags.Coverage.OBSERVED_ONLY,
			() -> List.of(new EditorTagCatalog.Source(null, () -> { calls.incrementAndGet(); return Stream.generate(() -> "c:a"); })).iterator(), () -> true);
		assertEquals(1, calls.get());
		assertEquals(EditorIngredientTags.Status.PENDING, timed.snapshot(generation, "chemical").status());
		timed.clear();
	}

	@Test void failuresArePartialAndNotRetriedAfterCompletion() {
		AtomicInteger calls = new AtomicInteger();
		var sources = List.of(new EditorTagCatalog.Source(null, () -> { calls.incrementAndGet(); throw new LinkageError(); }),
			new EditorTagCatalog.Source(null, () -> Stream.of("c:a", null)), new EditorTagCatalog.Source(null, () -> null));
		update(sources);
		var result = catalog.snapshot(generation, "chemical");
		assertEquals(EditorIngredientTags.Status.READY, result.status());
		assertTrue(result.partial());
		assertEquals(List.of("c:a"), result.tags());
		catalog.cancel();
		update(sources);
		assertSame(result, catalog.snapshot(generation, "chemical"));
		assertEquals(1, calls.get());
	}

	@Test void allFailureIsUnavailableAndMemoized() {
		AtomicInteger calls = new AtomicInteger();
		var sources = List.of(new EditorTagCatalog.Source(null, () -> { calls.incrementAndGet(); return null; }));
		update(sources);
		update(sources);
		assertEquals(EditorIngredientTags.Status.UNAVAILABLE, catalog.snapshot(generation, "chemical").status());
		assertEquals(1, calls.get());
	}

	@Test void successfulEmptyJeiSourceRemainsObservedOnly() {
		catalog.update(generation, "chemical", EditorIngredientTags.Status.READY,
			EditorIngredientTags.Coverage.OBSERVED_ONLY,
			() -> List.of(new EditorTagCatalog.Source(null, Stream::empty)).iterator(), () -> true);
		var result = catalog.snapshot(generation, "chemical");
		assertEquals(EditorIngredientTags.Status.READY, result.status());
		assertEquals(EditorIngredientTags.Coverage.OBSERVED_ONLY, result.coverage());
		assertTrue(result.tags().isEmpty());
	}

	@Test void generationChangeCancelsOldStreamAndRejectsOldSnapshot() {
		AtomicBoolean closed = new AtomicBoolean();
		update(List.of(new EditorTagCatalog.Source(null, () -> Stream.generate(() -> "c:old").onClose(() -> closed.set(true)))));
		Object next = new Object();
		assertSame(EditorIngredientTags.UNAVAILABLE, catalog.snapshot(next, "chemical"));
		catalog.update(next, "chemical", EditorIngredientTags.Status.READY, EditorIngredientTags.Coverage.OBSERVED_ONLY,
			() -> List.of(new EditorTagCatalog.Source(null, () -> Stream.of("c:new"))).iterator(), () -> true);
		assertTrue(closed.get());
		assertEquals(List.of("c:new"), catalog.snapshot(next, "chemical").tags());
		assertSame(EditorIngredientTags.UNAVAILABLE, catalog.snapshot(generation, "chemical"));
	}

	@Test void typeChangeClearsCachedTagsAndClearDisposesFailingClose() {
		update(List.of(new EditorTagCatalog.Source(null, () -> Stream.of("c:a"))));
		catalog.update(generation, "other", EditorIngredientTags.Status.READY, EditorIngredientTags.Coverage.OBSERVED_ONLY,
			() -> List.of(new EditorTagCatalog.Source(null, () -> Stream.generate(() -> "c:b")
				.onClose(() -> { throw new IllegalStateException(); }))).iterator(), () -> true);
		assertSame(EditorIngredientTags.UNAVAILABLE, catalog.snapshot(generation, "chemical"));
		assertDoesNotThrow(catalog::clear);
		assertSame(EditorIngredientTags.UNAVAILABLE, catalog.snapshot(generation, "other"));
	}

	@Test void invalidationDuringIterationCannotPublish() {
		AtomicBoolean valid = new AtomicBoolean(true);
		AtomicBoolean closed = new AtomicBoolean();
		catalog.update(generation, "chemical", EditorIngredientTags.Status.READY, EditorIngredientTags.Coverage.OBSERVED_ONLY,
			() -> List.of(new EditorTagCatalog.Source(null, () -> Stream.of("c:a").peek(tag -> valid.set(false))
				.onClose(() -> closed.set(true)))).iterator(), valid::get);
		assertTrue(closed.get());
		assertSame(EditorIngredientTags.UNAVAILABLE, catalog.snapshot(generation, "chemical"));
	}

	@Test void missingTypeAndPendingDoNotAccessSources() {
		for (var status : List.of(EditorIngredientTags.Status.PENDING, EditorIngredientTags.Status.TYPE_MISSING)) {
			catalog.update(generation, "missing", status, EditorIngredientTags.Coverage.OBSERVED_ONLY,
				() -> { fail("source read"); return null; }, () -> true);
			assertEquals(status, catalog.snapshot(generation, "missing").status());
		}
	}

	@Test void partialLazyFailureKeepsAlreadyReadTags() {
		AtomicInteger reads = new AtomicInteger();
		AtomicBoolean closed = new AtomicBoolean();
		update(List.of(new EditorTagCatalog.Source(null, () -> Stream.generate(() -> {
			if (reads.getAndIncrement() == 0) return "c:a";
			throw new IllegalStateException();
		}).onClose(() -> closed.set(true)))));
		var result = catalog.snapshot(generation, "chemical");
		assertEquals(EditorIngredientTags.Status.READY, result.status());
		assertEquals(List.of("c:a"), result.tags());
		assertTrue(result.partial());
		assertTrue(closed.get());
	}

	@Test void reentrantCancelDuringHelperClosesReturnedStream() {
		AtomicBoolean closed = new AtomicBoolean();
		update(List.of(new EditorTagCatalog.Source(null, () -> {
			catalog.clear();
			return Stream.of("c:a").onClose(() -> closed.set(true));
		})));
		assertTrue(closed.get());
		assertSame(EditorIngredientTags.UNAVAILABLE, catalog.snapshot(generation, "chemical"));
	}

	@Test void throwingSourceIteratorTerminatesWithoutRetry() {
		AtomicInteger calls = new AtomicInteger();
		catalog.update(generation, "chemical", EditorIngredientTags.Status.READY, EditorIngredientTags.Coverage.OBSERVED_ONLY,
			() -> new java.util.Iterator<>() {
				public boolean hasNext() { calls.incrementAndGet(); throw new IllegalStateException(); }
				public EditorTagCatalog.Source next() { throw new AssertionError(); }
			}, () -> true);
		assertEquals(1, calls.get());
		assertEquals(EditorIngredientTags.Status.UNAVAILABLE, catalog.snapshot(generation, "chemical").status());
	}

	@Test void collectedGenerationCannotAliasAbsentRuntime() throws Exception {
		update(List.of(new EditorTagCatalog.Source(null, () -> Stream.of("c:a"))));
		var field = EditorTagCatalog.class.getDeclaredField("generation");
		field.setAccessible(true);
		((java.lang.ref.WeakReference<?>) field.get(catalog)).clear();
		assertSame(EditorIngredientTags.UNAVAILABLE, catalog.snapshot(null, "chemical"));
		catalog.update(null, "chemical", EditorIngredientTags.Status.PENDING, EditorIngredientTags.Coverage.OBSERVED_ONLY,
			() -> { fail("source read"); return null; }, () -> false);
		assertEquals(EditorIngredientTags.Status.PENDING, catalog.snapshot(null, "chemical").status());
		assertTrue(catalog.snapshot(null, "chemical").tags().isEmpty());
	}

	@Test void sharedSourcesDoNotConsumeHelperBudget() {
		AtomicInteger calls = new AtomicInteger();
		Object shared = new Object();
		var sources = IntStream.range(0, 100).mapToObj(i -> new EditorTagCatalog.Source(shared,
			() -> { calls.incrementAndGet(); return Stream.of("c:a"); })).toList();
		update(sources);
		assertEquals(1, calls.get());
		assertEquals(EditorIngredientTags.Status.READY, catalog.snapshot(generation, "chemical").status());
	}

	@Test void largeSharedCatalogRemainsIncrementalAndReopensWithoutRescanning() {
		var measured = new EditorTagCatalog();
		Object shared = new Object();
		AtomicInteger calls = new AtomicInteger();
		AtomicInteger closes = new AtomicInteger();
		var sources = IntStream.range(0, 10000).mapToObj(i -> new EditorTagCatalog.Source(shared,
			() -> { calls.incrementAndGet(); return IntStream.range(0, 10000).mapToObj(tag -> "c:tag/" + tag)
				.onClose(closes::incrementAndGet); })).toList();
		int ticks = 0;
		long longest = 0;
		long total = 0;
		do {
			long started = System.nanoTime();
			measured.update(generation, "chemical", EditorIngredientTags.Status.READY, EditorIngredientTags.Coverage.REGISTRY_BACKED,
				sources::iterator, () -> true);
			long elapsed = System.nanoTime() - started;
			total += elapsed;
			longest = Math.max(longest, elapsed);
			assertTrue(++ticks < 10000);
		} while (measured.snapshot(generation, "chemical").status() == EditorIngredientTags.Status.PENDING);
		var snapshot = measured.snapshot(generation, "chemical");
		assertEquals(10000, snapshot.tags().size());
		assertTrue(ticks > 1);
		assertEquals(1, calls.get());
		assertEquals(1, closes.get());
		measured.cancel();
		measured.update(generation, "chemical", EditorIngredientTags.Status.READY, EditorIngredientTags.Coverage.REGISTRY_BACKED,
			() -> { fail("cached result rescanned"); return null; }, () -> true);
		assertSame(snapshot, measured.snapshot(generation, "chemical"));
		System.out.printf("10k shared sources / 10k tags: ticks=%d total=%.3fms maxUpdate=%.3fms%n", ticks, total / 1e6, longest / 1e6);
	}
}
