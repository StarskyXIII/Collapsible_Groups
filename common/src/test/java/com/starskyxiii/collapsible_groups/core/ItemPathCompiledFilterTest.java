package com.starskyxiii.collapsible_groups.core;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPathCompiledFilterTest {

	@Test
	void itemPathStartsWithMatchesOnlyItemPathsWithRequestedPrefix() {
		CompiledFilter filter = CompiledFilter.compile(Filters.itemPathStartsWith("gutter_"));

		assertTrue(filter.matches(new FakeIngredientView("item", Identifier.parse("mcwroofs:gutter_middle_yellow"))));
		assertFalse(filter.matches(new FakeIngredientView("item", Identifier.parse("mcwroofs:yellow_striped_awning"))));
	}

	@Test
	void itemPathEndsWithMatchesOnlyItemPathsWithRequestedSuffix() {
		CompiledFilter filter = CompiledFilter.compile(Filters.itemPathEndsWith("_chair"));

		assertTrue(filter.matches(new FakeIngredientView("item", Identifier.parse("mcwfurnitures:jungle_chair"))));
		assertFalse(filter.matches(new FakeIngredientView("item", Identifier.parse("mcwfurnitures:jungle_table"))));
	}

	@Test
	void itemPathFiltersDoNotMatchNonItemViews() {
		CompiledFilter startsWith = CompiledFilter.compile(Filters.itemPathStartsWith("gutter_"));
		CompiledFilter endsWith = CompiledFilter.compile(Filters.itemPathEndsWith("_chair"));

		assertFalse(startsWith.matches(new FakeIngredientView("fluid", Identifier.parse("minecraft:water"))));
		assertFalse(endsWith.matches(new FakeIngredientView("mekanism:chemical", Identifier.parse("mekanism:hydrogen"))));
	}

	@Test
	void itemPathFiltersDoNotMatchViewsWithoutResourceLocation() {
		CompiledFilter startsWith = CompiledFilter.compile(Filters.itemPathStartsWith("gutter_"));
		CompiledFilter endsWith = CompiledFilter.compile(Filters.itemPathEndsWith("_chair"));

		assertFalse(startsWith.matches(new FakeIngredientView("item", null)));
		assertFalse(endsWith.matches(new FakeIngredientView("item", null)));
	}

	private record FakeIngredientView(String ingredientType, Identifier resourceLocation) implements IngredientView {
		@Override
		public boolean hasTag(Identifier tagId) {
			return false;
		}

		@Override
		public boolean matchesExactStack(String encodedStack) {
			return false;
		}
	}
}
