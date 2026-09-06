package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EditorIngredientIdsTest {
	@Test void canonicalAndAliasUseTheSameSourceWhileBuiltinsAndMissingTypesAreRejected() {
		var generic = new ViewerIngredientType<>("test:chemical", List.of("test:gas"),
			List.of(entry("test:chemical", "uid:a", "test:oxygen")));
		var item = new ViewerIngredientType<String>("item", List.of("minecraft:item"), List.of());
		var fluid = new ViewerIngredientType<String>("fluid", List.of(), List.of());
		var types = List.of(generic, item, fluid);
		assertSame(generic, EditorIngredientIds.findType(types, "test:chemical"));
		assertSame(generic, EditorIngredientIds.findType(types, "test:gas"));
		assertNull(EditorIngredientIds.findType(types, "item"));
		assertNull(EditorIngredientIds.findType(types, "minecraft:item"));
		assertNull(EditorIngredientIds.findType(types, "fluid"));
		assertNull(EditorIngredientIds.findType(types, "test:missing"));
	}

	@Test void selectedIdsMatchAllResourceVariantsAndNeverUseIdentityUid() {
		var first = entry("test:chemical", "uid:first_variant", "test:oxygen");
		var second = entry("test:chemical", "uid:second_variant", "test:oxygen");
		var foreignType = entry("test:energy", "uid:foreign", "test:oxygen");
		var foreignId = entry("test:chemical", "uid:another", "test:hydrogen");
		var type = new ViewerIngredientType<>("test:chemical", List.of(), List.of(first, second, foreignId));
		var catalog = new EditorIdCatalog(256, () -> 0);
		Object generation = new Object();
		catalog.update(generation, type.canonicalId(), EditorIngredientIds.Status.READY,
			() -> EditorIngredientIds.sources(type), () -> true);
		assertEquals(List.of("test:hydrogen", "test:oxygen"), catalog.snapshot(generation, "test:chemical").ids());
		var selected = CompiledFilter.compile(Filters.id("test:chemical", "test:oxygen"));
		assertTrue(selected.matches(first.view()));
		assertTrue(selected.matches(second.view()));
		assertFalse(selected.matches(foreignType.view()));
		assertFalse(selected.matches(foreignId.view()));
		assertFalse(CompiledFilter.compile(Filters.id("test:chemical", "uid:first_variant")).matches(first.view()));
	}

	@Test void sourceCreationIsLazyAndSkipsEntriesOutsideTheSelectedType() {
		AtomicInteger reads = new AtomicInteger();
		IngredientView view = new IngredientView() {
			public String ingredientType() { return "test:chemical"; }
			public ResourceLocation resourceLocation() { reads.incrementAndGet(); return ResourceLocation.parse("test:oxygen"); }
			public boolean hasTag(ResourceLocation tag) { return false; }
			public boolean matchesExactStack(String value) { return false; }
		};
		var entry = new ViewerIngredient<>(new ViewerIngredientIdentity("test:chemical", "uid:a"), ViewerIngredient.Kind.GENERIC, "a", view);
		var wrong = new ViewerIngredient<>(new ViewerIngredientIdentity("test:other", "uid:b"), ViewerIngredient.Kind.GENERIC, "b", view);
		var item = new ViewerIngredient<>(new ViewerIngredientIdentity("test:chemical", "uid:c"), ViewerIngredient.Kind.ITEM, "c", view);
		var type = new ViewerIngredientType<>("test:chemical", List.of(), List.of(entry, wrong, item));
		var sources = EditorIngredientIds.sources(type);
		var first = sources.next();
		assertEquals(0, reads.get());
		assertEquals("test:oxygen", first.get());
		assertNull(sources.next().get());
		assertNull(sources.next().get());
		assertEquals(1, reads.get());
	}

	private ViewerIngredient<String> entry(String type, String uid, String id) {
		IngredientView view = new IngredientView() {
			public String ingredientType() { return type; }
			public ResourceLocation resourceLocation() { return ResourceLocation.parse(id); }
			public boolean hasTag(ResourceLocation tag) { return false; }
			public boolean matchesExactStack(String value) { return false; }
		};
		return new ViewerIngredient<>(new ViewerIngredientIdentity(type, uid), ViewerIngredient.Kind.GENERIC, uid, view);
	}
}
