package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorIngredientTypesTest {
	@Test void onlyCurrentGenericTypesAppearWithStableIdsAndMergedAliases() {
		String longId = "Example.Chemical".repeat(50);
		var snapshot = EditorIngredientTypes.from(List.of(
			type("item", ViewerIngredient.Kind.ITEM, List.of()),
			type("fluid", ViewerIngredient.Kind.FLUID, List.of()),
			new ViewerIngredientType<>("absent", List.of("ghost"), List.of()),
			type("z:chemical", ViewerIngredient.Kind.GENERIC, List.of("Gas", "Gas")),
			type("z:chemical", ViewerIngredient.Kind.GENERIC, List.of("Chemical")),
			type(longId, ViewerIngredient.Kind.GENERIC, List.of())));
		assertEquals(List.of(longId, "z:chemical"), snapshot.options().stream().map(EditorIngredientTypes.Option::id).toList());
		var option = snapshot.options().get(1);
		assertEquals(List.of("Chemical", "Gas"), option.aliases());
		assertTrue(option.matches("GAS"));
		assertTrue(option.identifies("Gas"));
		assertFalse(option.identifies("gas"));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.options().clear());
	}

	@Test void cacheRefreshesOnGenerationAndReleasesOnPendingOrClose() {
		var cache = new EditorIngredientTypes.Cache();
		var calls = new AtomicInteger();
		java.util.function.Supplier<EditorIngredientTypes> build = () -> {
			calls.incrementAndGet();
			return EditorIngredientTypes.from(List.of(type("Chemical", ViewerIngredient.Kind.GENERIC, List.of())));
		};
		Object generation = new Object();
		var first = cache.get(generation, build);
		assertSame(first, cache.get(generation, build));
		assertNotSame(generation, first.token());
		assertEquals(1, calls.get());
		assertSame(EditorIngredientTypes.PENDING, cache.get(null, build));
		assertNotSame(first.token(), cache.get(generation, build).token());
		cache.clear();
		var reopened = cache.get(generation, build);
		assertNotSame(reopened.token(), cache.get(new Object(), build).token());
		assertEquals(4, calls.get());
	}

	@Test void unreadyCaptureIsRetriedAndRuntimeCachesRemainIsolated() {
		var cache = new EditorIngredientTypes.Cache();
		Object generation = new Object();
		assertSame(EditorIngredientTypes.PENDING, cache.get(generation, () -> EditorIngredientTypes.PENDING));
		var ready = cache.get(generation, () -> EditorIngredientTypes.from(List.of()));
		assertEquals(EditorIngredientTypes.Status.READY, ready.status());
		assertTrue(ready.options().isEmpty());
		var other = new EditorIngredientTypes.Cache().get(generation, () -> EditorIngredientTypes.from(List.of()));
		assertNotSame(ready.token(), other.token());
	}

	private static ViewerIngredientType<String> type(String id, ViewerIngredient.Kind kind, List<String> aliases) {
		IngredientView view = new IngredientView() {
			public String ingredientType() { return id; }
			public ResourceLocation resourceLocation() { return null; }
			public boolean hasTag(ResourceLocation tag) { throw new AssertionError(); }
			public boolean matchesExactStack(String value) { return false; }
		};
		return new ViewerIngredientType<>(id, aliases, List.of(new ViewerIngredient<>(
			new ViewerIngredientIdentity(id, "value"), kind, "value", view)));
	}
}
