package com.starskyxiii.collapsible_groups.compat.kubejs;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnownRecipeViewerTypeIdsTest {
	@Test
	void gatesKnownCanonicalIdsAndAliasesByLoadedModInFixedOrder() {
		assertEquals(List.of(), KnownRecipeViewerTypeIds.collect(id -> false, List.of()));
		assertEquals(List.of("mekanism:chemical", "chemical"),
			KnownRecipeViewerTypeIds.collect("mekanism"::equals, List.of()));
		assertEquals(List.of("productivebees:bee", "bee"),
			KnownRecipeViewerTypeIds.collect("productivebees"::equals, List.of()));
		assertEquals(List.of("mekanism:chemical", "chemical", "productivebees:bee", "bee"),
			KnownRecipeViewerTypeIds.collect(id -> true, List.of()));
	}

	@Test
	void earlyAndLateSnapshotsMergeRuntimeIdsDeterministicallyWithoutDuplicates() {
		assertEquals(List.of("mekanism:chemical", "chemical"),
			KnownRecipeViewerTypeIds.collect("mekanism"::equals, List.of()));
		assertEquals(List.of("mekanism:chemical", "chemical", "emi:mekanism:chemical", "other:type"),
			KnownRecipeViewerTypeIds.collect("mekanism"::equals,
				List.of("emi:mekanism:chemical", "mekanism:chemical", "other:type")));
	}

	@Test
	void catalogQueryDoesNotMutateViewerIdentityRegistry() {
		Set<String> canonicalBefore = IngredientTypeIds.getCanonicalIds();
		var aliasesBefore = IngredientTypeIds.getAliases();
		var allBefore = IngredientTypeIds.getAllIds();

		KnownRecipeViewerTypeIds.collect(id -> true, List.of("emi:mekanism:chemical"));

		assertEquals(canonicalBefore, IngredientTypeIds.getCanonicalIds());
		assertEquals(aliasesBefore, IngredientTypeIds.getAliases());
		assertEquals(allBefore, IngredientTypeIds.getAllIds());
	}
}
