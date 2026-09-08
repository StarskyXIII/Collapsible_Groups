package com.starskyxiii.collapsible_groups.group.filter;

import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.Filters;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPathCompiledFilterTest {

	@Test
	void itemPathStartsWithMatchesOnlyItemPathsWithRequestedPrefix() {
		CompiledFilter filter = CompiledFilter.compile(Filters.itemPathStartsWith("gutter_"));

		assertTrue(filter.matches(new FakeIngredientView("item", new ResourceLocation("mcwroofs:gutter_middle_yellow"))));
		assertFalse(filter.matches(new FakeIngredientView("item", new ResourceLocation("mcwroofs:yellow_striped_awning"))));
	}

	@Test
	void itemPathEndsWithMatchesOnlyItemPathsWithRequestedSuffix() {
		CompiledFilter filter = CompiledFilter.compile(Filters.itemPathEndsWith("_chair"));

		assertTrue(filter.matches(new FakeIngredientView("item", new ResourceLocation("mcwfurnitures:jungle_chair"))));
		assertFalse(filter.matches(new FakeIngredientView("item", new ResourceLocation("mcwfurnitures:jungle_table"))));
	}

	@Test
	void itemPathContainsMatchesOnlyItemPathsWithRequestedNeedle() {
		CompiledFilter filter = CompiledFilter.compile(Filters.itemPathContains("_beam_"));

		assertTrue(filter.matches(new FakeIngredientView("item", new ResourceLocation("mcwbridges:oak_beam_bridge"))));
		assertFalse(filter.matches(new FakeIngredientView("item", new ResourceLocation("mcwbridges:oak_bridge"))));
	}

	@Test
	void itemPathFiltersDoNotMatchNonItemViews() {
		CompiledFilter startsWith = CompiledFilter.compile(Filters.itemPathStartsWith("gutter_"));
		CompiledFilter contains = CompiledFilter.compile(Filters.itemPathContains("_beam_"));
		CompiledFilter endsWith = CompiledFilter.compile(Filters.itemPathEndsWith("_chair"));

		assertFalse(startsWith.matches(new FakeIngredientView("fluid", new ResourceLocation("minecraft:water"))));
		assertFalse(contains.matches(new FakeIngredientView("fluid", new ResourceLocation("minecraft:water"))));
		assertFalse(endsWith.matches(new FakeIngredientView("mekanism:chemical", new ResourceLocation("mekanism:hydrogen"))));
	}

	@Test
	void itemPathFiltersDoNotMatchViewsWithoutResourceLocation() {
		CompiledFilter startsWith = CompiledFilter.compile(Filters.itemPathStartsWith("gutter_"));
		CompiledFilter contains = CompiledFilter.compile(Filters.itemPathContains("_beam_"));
		CompiledFilter endsWith = CompiledFilter.compile(Filters.itemPathEndsWith("_chair"));

		assertFalse(startsWith.matches(new FakeIngredientView("item", null)));
		assertFalse(contains.matches(new FakeIngredientView("item", null)));
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
