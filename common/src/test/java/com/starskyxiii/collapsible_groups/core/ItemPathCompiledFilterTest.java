package com.starskyxiii.collapsible_groups.core;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPathCompiledFilterTest {

	@Test
	void itemPathStartsWithMatchesOnlyItemPathsWithRequestedPrefix() {
		CompiledFilter filter = CompiledFilter.compile(Filters.itemPathStartsWith("gutter_"));

		assertTrue(filter.matches(new FakeIngredientView("item", ResourceLocation.parse("mcwroofs:gutter_middle_yellow"))));
		assertFalse(filter.matches(new FakeIngredientView("item", ResourceLocation.parse("mcwroofs:yellow_striped_awning"))));
	}

	@Test
	void itemPathEndsWithMatchesOnlyItemPathsWithRequestedSuffix() {
		CompiledFilter filter = CompiledFilter.compile(Filters.itemPathEndsWith("_chair"));

		assertTrue(filter.matches(new FakeIngredientView("item", ResourceLocation.parse("mcwfurnitures:jungle_chair"))));
		assertFalse(filter.matches(new FakeIngredientView("item", ResourceLocation.parse("mcwfurnitures:jungle_table"))));
	}

	@Test
	void itemPathFiltersDoNotMatchNonItemViews() {
		CompiledFilter startsWith = CompiledFilter.compile(Filters.itemPathStartsWith("gutter_"));
		CompiledFilter endsWith = CompiledFilter.compile(Filters.itemPathEndsWith("_chair"));

		assertFalse(startsWith.matches(new FakeIngredientView("fluid", ResourceLocation.parse("minecraft:water"))));
		assertFalse(endsWith.matches(new FakeIngredientView("mekanism:chemical", ResourceLocation.parse("mekanism:hydrogen"))));
	}

	@Test
	void itemPathFiltersDoNotMatchViewsWithoutResourceLocation() {
		CompiledFilter startsWith = CompiledFilter.compile(Filters.itemPathStartsWith("gutter_"));
		CompiledFilter endsWith = CompiledFilter.compile(Filters.itemPathEndsWith("_chair"));

		assertFalse(startsWith.matches(new FakeIngredientView("item", null)));
		assertFalse(endsWith.matches(new FakeIngredientView("item", null)));
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
	}
}
