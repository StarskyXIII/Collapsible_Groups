package com.starskyxiii.collapsible_groups.ingredient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngredientTypeIdsTest {
	@Test
	void resolvesCanonicalIdsAndAliasesWithoutViewerTypes() {
		IngredientTypeIds.registerCanonical("test:neutral_type");
		IngredientTypeIds.registerAlias("neutral_alias", "test:neutral_type");

		assertEquals("test:neutral_type", IngredientTypeIds.getCanonicalId("test:neutral_type"));
		assertEquals("test:neutral_type", IngredientTypeIds.getCanonicalId("neutral_alias"));
	}

	@Test
	void rejectsReservedIdsAndUnknownAliasTargets() {
		assertThrows(IllegalArgumentException.class, () -> IngredientTypeIds.registerCanonical("item"));
		assertThrows(
			IllegalArgumentException.class,
			() -> IngredientTypeIds.registerAlias("missing_alias", "test:missing_type")
		);
	}
}
