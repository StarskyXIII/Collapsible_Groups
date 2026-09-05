package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.client.preview.PreviewRenderCache;
import com.starskyxiii.collapsible_groups.client.preview.GroupPreviewEntry;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EditorPreviewCacheTest {
	@Test
	void headerInvalidatesOnPublicationConfigurationGenerationAndReset() {
		var calls = new AtomicInteger();
		Object[] generation = {new Object()};
		var entry = new EditorRuntimeAccess.PreviewEntry(EditorRuntimeAccess.PreviewEntry.Kind.ITEM,
			new Object(), GroupIconDefinition.item("test:item"));
		var runtime = (EditorRuntimeAccess) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{EditorRuntimeAccess.class}, (proxy, method, args) -> switch (method.getName()) {
				case "previewGeneration" -> generation[0];
				case "resolveHeaderIcons" -> { calls.incrementAndGet(); yield List.of(entry); }
				default -> throw new AssertionError(method);
			});
		var cache = new EditorPreviewCache();
		var members = List.of(entry);
		for (int frame = 0; frame < 100; frame++) assertEquals(members, cache.icons(runtime, List.of(), members));
		assertEquals(1, calls.get());
		cache.icons(runtime, List.of(), new java.util.ArrayList<>(members));
		assertEquals(2, calls.get());
		cache.icons(runtime, List.of(entry.icon()), members);
		assertEquals(3, calls.get());
		generation[0] = null;
		assertTrue(cache.icons(runtime, List.of(entry.icon()), members).isEmpty());
		generation[0] = new Object();
		cache.icons(runtime, List.of(entry.icon()), members);
		assertEquals(4, calls.get());
		cache.clear();
		cache.icons(runtime, List.of(entry.icon()), members);
		assertEquals(5, calls.get());
	}

	@Test
	void rendererReusesFullSnapshotForFramesAndTooltipWithoutLosingTotal() {
		var cache = new PreviewRenderCache();
		var converted = new AtomicInteger();
		var entry = new EditorRuntimeAccess.PreviewEntry(EditorRuntimeAccess.PreviewEntry.Kind.ITEM,
			new Object(), GroupIconDefinition.item("test:item"));
		var entries = List.copyOf(java.util.Collections.nCopies(6166, entry));
		Object generation = new Object();
		java.util.function.Function<EditorRuntimeAccess.PreviewEntry, GroupPreviewEntry> convert = ignored -> {
			converted.incrementAndGet();
			return GroupPreviewEntry.ofRenderer((g, x, y) -> {});
		};
		var snapshot = cache.resolve(generation, entries, convert);
		var headers = List.of(entry);
		for (int frame = 0; frame < 100; frame++) {
			cache.resolve(generation, headers, convert);
			assertSame(snapshot, cache.resolve(generation, entries, convert));
			assertSame(snapshot, cache.resolve(generation, entries, convert));
		}
		assertEquals(6167, converted.get());
		assertEquals(6166, snapshot.size());
		assertTrue(cache.resolve(null, entries, convert).isEmpty());
		assertNotSame(snapshot, cache.resolve(new Object(), entries, convert));
		assertEquals(12333, converted.get());
	}
}
