package com.starskyxiii.collapsible_groups.client.editor;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EditorIdCatalogTest {
	private final Object generation = new Object();
	private final EditorIdCatalog catalog = new EditorIdCatalog(16, () -> 0);

	private void update(List<Supplier<String>> sources) {
		catalog.update(generation, "chemical", EditorIngredientIds.Status.READY, sources::iterator, () -> true);
	}

	@Test void sortsAndDeduplicatesIdsWithoutReturningMutableData() {
		update(List.of(() -> "c:z", () -> "c:a", () -> "c:z"));
		var result = catalog.snapshot(generation, "chemical");
		assertEquals(List.of("c:a", "c:z"), result.ids());
		assertEquals(EditorIngredientIds.Status.READY, result.status());
		assertFalse(result.partial());
		assertThrows(UnsupportedOperationException.class, () -> result.ids().add("c:b"));
	}

	@Test void emptySourcesAndUnavailableIdsAreDifferentFromFailedSources() {
		update(List.of());
		assertEquals(EditorIngredientIds.Status.READY, catalog.snapshot(generation, "chemical").status());
		assertFalse(catalog.snapshot(generation, "chemical").partial());
		catalog.clear();
		update(List.of(() -> null));
		assertEquals(EditorIngredientIds.Status.READY, catalog.snapshot(generation, "chemical").status());
		assertTrue(catalog.snapshot(generation, "chemical").ids().isEmpty());
		assertTrue(catalog.snapshot(generation, "chemical").partial());
		catalog.clear();
		update(List.of(() -> { throw new LinkageError(); }));
		assertEquals(EditorIngredientIds.Status.UNAVAILABLE, catalog.snapshot(generation, "chemical").status());
	}

	@Test void partialResultsSurviveNullAndExceptionsAndDoNotRetry() {
		AtomicInteger calls = new AtomicInteger();
		List<Supplier<String>> sources = List.of(() -> "c:a", () -> null,
			() -> { calls.incrementAndGet(); throw new IllegalArgumentException(); });
		update(sources);
		var result = catalog.snapshot(generation, "chemical");
		assertEquals(EditorIngredientIds.Status.READY, result.status());
		assertTrue(result.partial());
		assertEquals(List.of("c:a"), result.ids());
		catalog.cancel();
		update(sources);
		assertEquals(1, calls.get());
		assertSame(result, catalog.snapshot(generation, "chemical"));
	}

	@Test void budgetsApplyToBothCapturedIdsAndThirdPartyGetters() {
		for (int limit : List.of(16, 256)) {
			var bounded = new EditorIdCatalog(limit, () -> 0);
			AtomicInteger calls = new AtomicInteger();
			bounded.update(generation, "chemical", EditorIngredientIds.Status.READY,
				() -> IntStream.range(0, 1000).<Supplier<String>>mapToObj(i -> () -> { calls.incrementAndGet(); return "c:" + i; }).iterator(), () -> true);
			assertEquals(limit, calls.get());
			assertEquals(EditorIngredientIds.Status.PENDING, bounded.snapshot(generation, "chemical").status());
			assertTrue(bounded.snapshot(generation, "chemical").ids().isEmpty());
		}
	}

	@Test void slowGetterStopsAdditionalWorkAndSnapshotReadsDoNotCollect() {
		AtomicLong clock = new AtomicLong();
		var timed = new EditorIdCatalog(16, clock::get);
		AtomicInteger calls = new AtomicInteger();
		timed.update(generation, "chemical", EditorIngredientIds.Status.READY,
			() -> IntStream.range(0, 100).<Supplier<String>>mapToObj(i -> () -> {
				calls.incrementAndGet(); clock.addAndGet(3_000_000); return "c:" + i;
			}).iterator(), () -> true);
		for (int i = 0; i < 100; i++) timed.snapshot(generation, "chemical");
		assertEquals(1, calls.get());
	}

	@Test void invalidationDuringGetterCannotPublish() {
		AtomicBoolean valid = new AtomicBoolean(true);
		catalog.update(generation, "chemical", EditorIngredientIds.Status.READY,
			() -> List.<Supplier<String>>of(() -> { valid.set(false); return "c:old"; }).iterator(), valid::get);
		assertSame(EditorIngredientIds.UNAVAILABLE, catalog.snapshot(generation, "chemical"));
	}

	@Test void reentrantCancellationDuringSourceCreationOrReadCannotPublish() {
		catalog.update(generation, "chemical", EditorIngredientIds.Status.READY,
			() -> { catalog.clear(); return List.<Supplier<String>>of(() -> "c:old").iterator(); }, () -> true);
		assertSame(EditorIngredientIds.UNAVAILABLE, catalog.snapshot(generation, "chemical"));
		update(List.of(() -> { catalog.clear(); return "c:old"; }));
		assertSame(EditorIngredientIds.UNAVAILABLE, catalog.snapshot(generation, "chemical"));
	}

	@Test void cancelReleasesSourcesAndAllowsRestart() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		List<Supplier<String>> sources = IntStream.range(0, 100).<Supplier<String>>mapToObj(i -> () -> {
			calls.incrementAndGet(); return "c:" + i;
		}).toList();
		update(sources);
		catalog.cancel();
		var field = EditorIdCatalog.class.getDeclaredField("job");
		field.setAccessible(true);
		assertNull(field.get(catalog));
		update(sources);
		assertEquals(32, calls.get());
		catalog.clear();
		assertNull(field.get(catalog));
	}

	@Test void generationTypeAndWeakNullCannotAliasCachedResults() throws Exception {
		update(List.of(() -> "c:a"));
		assertSame(EditorIngredientIds.UNAVAILABLE, catalog.snapshot(generation, "other"));
		assertSame(EditorIngredientIds.UNAVAILABLE, catalog.snapshot(new Object(), "chemical"));
		var weak = EditorIdCatalog.class.getDeclaredField("generation");
		weak.setAccessible(true);
		((java.lang.ref.WeakReference<?>) weak.get(catalog)).clear();
		assertSame(EditorIngredientIds.UNAVAILABLE, catalog.snapshot(null, "chemical"));
		catalog.update(null, "chemical", EditorIngredientIds.Status.PENDING,
			() -> { fail("pending collection"); return null; }, () -> false);
		assertEquals(EditorIngredientIds.Status.PENDING, catalog.snapshot(null, "chemical").status());
		assertTrue(catalog.snapshot(null, "chemical").ids().isEmpty());
		catalog.update(generation, "other", EditorIngredientIds.Status.READY,
			() -> List.<Supplier<String>>of(() -> "c:b").iterator(), () -> true);
		assertEquals(List.of("c:b"), catalog.snapshot(generation, "other").ids());
	}

	@Test void throwingSourceIteratorAndFactoryTerminateWithoutRepeatedRetry() {
		AtomicInteger calls = new AtomicInteger();
		catalog.update(generation, "chemical", EditorIngredientIds.Status.READY, () -> new Iterator<>() {
			public boolean hasNext() { calls.incrementAndGet(); throw new IllegalStateException(); }
			public Supplier<String> next() { throw new AssertionError(); }
		}, () -> true);
		update(List.of(() -> "c:retry"));
		assertEquals(1, calls.get());
		assertEquals(EditorIngredientIds.Status.UNAVAILABLE, catalog.snapshot(generation, "chemical").status());
		catalog.clear();
		catalog.update(generation, "chemical", EditorIngredientIds.Status.READY,
			() -> { throw new IllegalArgumentException(); }, () -> true);
		assertEquals(EditorIngredientIds.Status.UNAVAILABLE, catalog.snapshot(generation, "chemical").status());
	}

	@Test void pendingAndMissingTypesNeverReadSources() {
		for (var status : List.of(EditorIngredientIds.Status.PENDING, EditorIngredientIds.Status.TYPE_MISSING)) {
			catalog.update(generation, "missing", status, () -> { fail("source read"); return null; }, () -> true);
			assertEquals(status, catalog.snapshot(generation, "missing").status());
		}
	}
}
