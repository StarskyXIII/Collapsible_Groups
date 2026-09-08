package com.starskyxiii.collapsible_groups.internal.query;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.ingredient.IngredientView;
import com.starskyxiii.collapsible_groups.viewer.GroupProjectionEngine;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredient;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientIdentity;
import com.starskyxiii.collapsible_groups.viewer.ViewerIngredientUniverse;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCatalogBoundaryTest {
	@Test void groupRuntimeQueryOwnsSemanticEvaluatorAndConservativePlan() {
		GroupFilter raw = new GroupFilter.Not(new GroupFilter.Any(List.of(
			new GroupFilter.Id("fluid", "minecraft:water"))));
		GroupDefinition group = new GroupDefinition("not_water", "Not Water", true, raw);

		assertSame(group.compiledFilter(), group.query().evaluator());
		assertEquals(group.filter(), group.query().source());
		assertFalse(group.query().plan().mayMatchItems());
		assertTrue(group.query().plan().mayMatchFluids());
		assertFalse(group.query().plan().mayMatchGeneric());
		assertEquals(CompiledFilter.Evaluation.NO_MATCH,
			group.query().evaluate(view("fluid", "minecraft:water")));
		assertEquals(CompiledFilter.Evaluation.MATCH,
			group.query().evaluate(view("fluid", "minecraft:lava")));
		assertEquals(CompiledFilter.Evaluation.NO_MATCH,
			group.query().evaluate(view("item", "minecraft:stone")));

		CompiledGroupQuery unavailable = CompiledGroupQuery.compile(
			new GroupFilter.Unsupported(new JsonObject(), "future"));
		assertEquals(CompiledFilter.Evaluation.UNAVAILABLE,
			unavailable.evaluate(view("item", "minecraft:stone")));
		assertFalse(unavailable.plan().mayMatchItems());
	}

	@Test void candidateBuildConsumesCatalogWithoutChangingViewerIdentityOrOrder() {
		ViewerIngredient<String> water = ingredient("fluid", "water", "minecraft:water", ViewerIngredient.Kind.FLUID);
		ViewerIngredient<String> lava = ingredient("fluid", "lava", "minecraft:lava", ViewerIngredient.Kind.FLUID);
		ViewerIngredient<String> duplicateWater = ingredient("fluid", "water-copy", "minecraft:water", ViewerIngredient.Kind.FLUID);
		Object token = new Object();
		ViewerIngredientUniverse<String> universe = new ViewerIngredientUniverse<>(
			List.of(water, lava, duplicateWater), token);

		assertEquals(List.of(water, lava), universe.ordered());
		assertSame(water, universe.byIdentity().get(duplicateWater.identity()));
		assertSame(token, universe.sourceToken());

		IngredientCatalog<ViewerIngredient<String>, ViewerIngredientIdentity> catalog =
			catalog(universe.ordered(), universe.byIdentity(), token);
		GroupDefinition group = new GroupDefinition("not_water", "Not Water", true,
			new GroupFilter.Not(new GroupFilter.Id("fluid", "minecraft:water")));
		var candidates = GroupProjectionEngine.buildCandidateIndex(catalog, List.of(group));

		assertEquals(List.of(lava.identity()), candidates.candidates().keySet().stream().toList());
		assertEquals(List.of("not_water"), candidates.candidates().get(lava.identity()));
	}

	private static IngredientCatalog<ViewerIngredient<String>, ViewerIngredientIdentity> catalog(
		List<ViewerIngredient<String>> ordered,
		Map<ViewerIngredientIdentity, ViewerIngredient<String>> byIdentity, Object token) {
		return new IngredientCatalog<>() {
			@Override public List<ViewerIngredient<String>> ordered() { return ordered; }
			@Override public Map<ViewerIngredientIdentity, ViewerIngredient<String>> byIdentity() { return byIdentity; }
			@Override public Object sourceToken() { return token; }
		};
	}

	private static ViewerIngredient<String> ingredient(String type, String entry, String id,
		ViewerIngredient.Kind kind) {
		return new ViewerIngredient<>(new ViewerIngredientIdentity(type, id), kind, entry, view(type, id));
	}

	private static IngredientView view(String type, String id) {
		return new IngredientView() {
			@Override public String ingredientType() { return type; }
			@Override public ResourceLocation resourceLocation() { return new ResourceLocation(id); }
			@Override public boolean hasTag(ResourceLocation tagId) { return false; }
			@Override public boolean matchesExactStack(String encodedStack) { return false; }
		};
	}
}
