package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class EditorNamespaceCatalogTest {
	private EditorIngredientIds source(Object token, String type, EditorIngredientIds.Status status, boolean partial, List<String> ids) {
		return new EditorIngredientIds(token, type, status, partial, ids);
	}

	@Test void readingDoesNotProjectAndUpdateDeduplicatesResourceNamespaces() {
		var catalog = new EditorNamespaceCatalog();
		var source = source(new Object(), "type:chemical", EditorIngredientIds.Status.READY, false,
			List.of("z:oxygen", "a:hydrogen", "z:hydrogen"));
		assertEquals(EditorIngredientIds.Status.PENDING, catalog.snapshot(source).status());
		assertTrue(catalog.snapshot(source).values().isEmpty());
		catalog.update(source);
		assertEquals(List.of("a", "z"), catalog.snapshot(source).values());
		assertEquals(EditorIngredientIds.Status.READY, catalog.snapshot(source).status());
		assertFalse(catalog.snapshot(source).partial());
	}

	@Test void sourceAndTypeChangesImmediatelyHideOldResultsAndClearReleasesCache() {
		var catalog = new EditorNamespaceCatalog();
		Object token = new Object();
		var first = source(token, "one", EditorIngredientIds.Status.READY, false, List.of("old:a"));
		catalog.update(first);
		for (var next : List.of(source(new Object(), "one", EditorIngredientIds.Status.READY, false, List.of("new:a")),
			source(token, "two", EditorIngredientIds.Status.READY, false, List.of("other:a")))) {
			assertTrue(catalog.snapshot(next).values().isEmpty());
			assertEquals(EditorIngredientIds.Status.PENDING, catalog.snapshot(next).status());
		}
		catalog.clear();
		assertEquals(EditorIngredientIds.Status.PENDING, catalog.snapshot(first).status());
	}

	@Test void availabilityPartialAndEmptyRemainDistinct() {
		var catalog = new EditorNamespaceCatalog();
		for (var status : EditorIngredientIds.Status.values()) {
			var source = source(new Object(), "type", status, true, List.of());
			catalog.update(source);
			assertEquals(status, catalog.snapshot(source).status());
			assertTrue(catalog.snapshot(source).partial());
			assertTrue(catalog.snapshot(source).values().isEmpty());
		}
	}

	@Test void tenThousandIdsProjectOnceAndReuseTheImmutableResult() {
		var catalog = new EditorNamespaceCatalog();
		var source = source(new Object(), "type", EditorIngredientIds.Status.READY, false,
			IntStream.range(0, 10_000).mapToObj(i -> "mod" + (i % 100) + ":value" + i).toList());
		long start = System.nanoTime();
		catalog.update(source);
		long elapsed = System.nanoTime() - start;
		var snapshot = catalog.snapshot(source);
		assertEquals(100, snapshot.values().size());
		catalog.update(source);
		assertSame(snapshot, catalog.snapshot(source));
		System.out.printf("Namespace projection 10000 IDs / 100 namespaces: %.3f ms%n", elapsed / 1_000_000.0);
	}

	@Test void actualMatcherUsesResourceNamespaceAndSelectedTypeIncludingFluid() {
		for (String type : List.of("item", "fluid", "type:chemical")) {
			var filter = CompiledFilter.compile(Filters.namespace(type, "resource"));
			assertTrue(filter.matches(view(type, "resource:oxygen")));
			assertTrue(filter.matches(view(type, "resource:hydrogen")));
			assertFalse(filter.matches(view("other:type", "resource:oxygen")));
			assertFalse(filter.matches(view(type, "type:oxygen")));
			assertFalse(filter.matches(view(type, null)));
		}
	}

	private IngredientView view(String type, String id) {
		return new IngredientView() {
			public String ingredientType() { return type; }
			public ResourceLocation resourceLocation() { return id == null ? null : ResourceLocation.parse(id); }
			public boolean hasTag(ResourceLocation tag) { return false; }
			public boolean matchesExactStack(String value) { return false; }
		};
	}
}
