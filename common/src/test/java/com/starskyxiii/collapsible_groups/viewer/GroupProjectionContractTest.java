package com.starskyxiii.collapsible_groups.viewer;

import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.IngredientView;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
class GroupProjectionContractTest {
	@Test
	void projectsMixedChildrenInTypeOrderWithExpansionAndPassesThroughDisabledAndSmallGroups() {
		ViewerIngredient<String> generic = ingredient(ViewerIngredient.Kind.GENERIC, "test:chemical", "test:oxygen", "oxygen");
		ViewerIngredient<String> loose = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:loose", "loose");
		ViewerIngredient<String> itemA = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:item_a", "item-a");
		ViewerIngredient<String> fluid = ingredient(ViewerIngredient.Kind.FLUID, "fluid", "test:water", "water");
		ViewerIngredient<String> itemB = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:item_b", "item-b");
		ViewerIngredient<String> disabledA = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:disabled_a", "disabled-a");
		ViewerIngredient<String> disabledB = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:disabled_b", "disabled-b");
		ViewerIngredient<String> singleton = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:single", "single");
		List<ViewerIngredient<String>> filtered = List.of(
			generic, loose, itemA, fluid, itemB, disabledA, disabledB, singleton
		);

		GroupDefinition mixed = new GroupDefinition("mixed", "Mixed", true, Filters.any(
			Filters.itemId("test:item_a"),
			Filters.itemId("test:item_b"),
			Filters.fluidId("test:water"),
			Filters.genericId("test:chemical", "test:oxygen")
		));
		GroupDefinition disabled = new GroupDefinition("disabled", "Disabled", false, Filters.any(
			Filters.itemId("test:disabled_a"), Filters.itemId("test:disabled_b")
		));
		GroupDefinition small = new GroupDefinition("small", "Small", true, Filters.itemId("test:single"));

		ViewerIngredientUniverse<String> universe = new ViewerIngredientUniverse<>(filtered);
		FakeViewerAdapter adapter = new FakeViewerAdapter(
			universe,
			List.of(
				new ViewerIngredientType<>("item", List.of(), universe.items()),
				new ViewerIngredientType<>("fluid", List.of(), universe.fluids()),
				new ViewerIngredientType<>("test:chemical", List.of("chemical"), universe.generic())
			),
			new ViewerSearchSnapshot<>("", filtered, true, 4)
		);
		adapter.setRuntimeAvailable(true);

		ViewerProjection<String> projection = GroupProjectionEngine.project(
			adapter.universeProvider().universe(),
			adapter.searchState().snapshot(),
			List.of(disabled, small, mixed),
			"mixed"::equals
		);

		assertEquals(5, projection.entries().size());
		ViewerProjection.GroupHeader<String> header = assertInstanceOf(
			ViewerProjection.GroupHeader.class,
			projection.entries().get(0)
		);
		assertEquals("mixed", header.group().id());
		assertEquals(List.of("item-a", "item-b", "water", "oxygen"), entries(header.children()));
		assertEquals(2, header.itemCount());
		assertEquals(1, header.fluidCount());
		assertEquals(1, header.genericCount());
		assertTrue(header.expanded());
		assertEquals(List.of("item-a", "item-b"), entries(header.fallbackIconIngredients()));
		assertEquals(9, projection.displayEntries().size());
		assertEquals(5, projection.withExpansion(id -> false).displayEntries().size());
		assertFalse(projection.ownership().containsKey(disabledA.identity()));
		assertEquals("small", projection.ownership().get(singleton.identity()));
		assertInstanceOf(ViewerProjection.IngredientEntry.class, projection.entries().get(4));
	}

	@Test
	void searchUngroupsSmallFilteredGroupsAndPreservesFilteredOrder() {
		ViewerIngredient<String> a = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:a", "a");
		ViewerIngredient<String> b = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:b", "b");
		ViewerIngredient<String> c = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:c", "c");
		ViewerIngredientUniverse<String> universe = new ViewerIngredientUniverse<>(List.of(a, b, c));
		GroupDefinition group = new GroupDefinition("search", "Search", true, Filters.itemNamespace("test"));

		ViewerProjection<String> ungrouped = GroupProjectionEngine.project(
			universe,
			new ViewerSearchSnapshot<>("query", List.of(c, a), true, 3),
			List.of(group),
			id -> false
		);
		assertEquals(List.of("c", "a"), ungrouped.entries().stream()
			.map(ViewerProjection.IngredientEntry.class::cast)
			.map(entry -> entry.ingredient().entry())
			.toList());

		ViewerProjection<String> groupedAtThreshold = GroupProjectionEngine.project(
			universe,
			new ViewerSearchSnapshot<>("query", List.of(c, a), true, 2),
			List.of(group),
			id -> false
		);
		assertInstanceOf(ViewerProjection.GroupHeader.class, groupedAtThreshold.entries().getFirst());
	}

	@Test
	void priorityOrderingSelectsTheFirstEnabledOwner() {
		ViewerIngredient<String> first = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:stone", "stone-a");
		ViewerIngredient<String> second = new ViewerIngredient<>(
			new ViewerIngredientIdentity("item", "test:stone#second"),
			ViewerIngredient.Kind.ITEM,
			"stone-b",
			new FakeIngredientView("item", ResourceLocation.parse("test:stone"))
		);
		GroupDefinition low = new GroupDefinition("low", "Low", true, Filters.itemId("test:stone")).withPriority(0);
		GroupDefinition high = new GroupDefinition("high", "High", true, Filters.itemId("test:stone")).withPriority(10);

		ViewerProjection<String> projection = GroupProjectionEngine.project(
			new ViewerIngredientUniverse<>(List.of(first, second)),
			new ViewerSearchSnapshot<>("", List.of(first, second), false, 0),
			List.of(low, high),
			id -> false
		);

		ViewerProjection.GroupHeader<String> header = assertInstanceOf(
			ViewerProjection.GroupHeader.class,
			projection.entries().getFirst()
		);
		assertEquals("high", header.group().id());
		assertEquals("high", projection.ownership().get(first.identity()));
		assertEquals("high", projection.ownership().get(second.identity()));
	}

	@Test
	void candidateIndexKeepsDisabledMatchesAndResolvesCurrentEnabledFallback() {
		ViewerIngredient<String> first = ingredient(ViewerIngredient.Kind.ITEM, "item", "test:stone", "stone-a");
		ViewerIngredient<String> second = new ViewerIngredient<>(
			new ViewerIngredientIdentity("item", "test:stone#second"),
			ViewerIngredient.Kind.ITEM,
			"stone-b",
			new FakeIngredientView("item", ResourceLocation.parse("test:stone"))
		);
		ViewerIngredientUniverse<String> universe = new ViewerIngredientUniverse<>(List.of(first, second));
		GroupDefinition low = new GroupDefinition("low", "Low", true, Filters.itemId("test:stone")).withPriority(0);
		GroupDefinition high = new GroupDefinition("high", "High", false, Filters.itemId("test:stone")).withPriority(10);
		GroupCandidateIndex index = GroupProjectionEngine.buildCandidateIndex(universe, List.of(low, high));

		assertEquals(List.of("high", "low"), index.candidates().get(first.identity()));
		assertEquals(4, index.candidateEdges());
		assertEquals(2.0, index.averageCandidates());
		assertEquals(2, index.maxCandidates());

		ViewerProjection<String> fallback = GroupProjectionEngine.project(
			universe, new ViewerSearchSnapshot<>("", List.of(first, second), false, 0),
			List.of(low, high), id -> false, index
		);
		assertEquals("low", fallback.ownership().get(first.identity()));

		ViewerProjection<String> promoted = GroupProjectionEngine.project(
			universe, new ViewerSearchSnapshot<>("", List.of(first, second), false, 0),
			List.of(low, high.withEnabled(true)), id -> false, index
		);
		assertEquals("high", promoted.ownership().get(first.identity()));

		ViewerProjection<String> unowned = GroupProjectionEngine.project(
			universe, new ViewerSearchSnapshot<>("", List.of(first, second), false, 0),
			List.of(low.withEnabled(false), high), id -> false, index
		);
		assertTrue(unowned.ownership().isEmpty());
	}

	private static ViewerIngredient<String> ingredient(
		ViewerIngredient.Kind kind,
		String type,
		String id,
		String entry
	) {
		return new ViewerIngredient<>(
			new ViewerIngredientIdentity(type, id),
			kind,
			entry,
			new FakeIngredientView(type, ResourceLocation.parse(id))
		);
	}

	private static List<String> entries(List<ViewerIngredient<String>> ingredients) {
		return ingredients.stream().map(ViewerIngredient::entry).toList();
	}

	private record FakeIngredientView(String ingredientType, ResourceLocation resourceLocation) implements IngredientView {
		@Override
		public boolean hasTag(ResourceLocation tagId) {
			return false;
		}

		@Override
		public boolean matchesExactStack(String encodedStack) {
			return false;
		}

		@Override
		public boolean matchesDecodedExactStack(ItemStack decoded) {
			return false;
		}
	}
}
