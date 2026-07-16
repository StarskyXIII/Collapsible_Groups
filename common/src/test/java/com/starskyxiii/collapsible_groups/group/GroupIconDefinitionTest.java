package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.ingredient.IngredientTypeIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupIconDefinitionTest {
	@Test
	void resolvesRegisteredTypeAliasesWithoutMutatingStoredIdentity() {
		IngredientTypeIds.registerCanonical("test:icon_definition_canonical");
		IngredientTypeIds.registerAlias("test:icon_definition_alias", "test:icon_definition_canonical");
		GroupIconDefinition icon = new GroupIconDefinition("test:icon_definition_alias", "opaque UID value");

		assertEquals("test:icon_definition_alias", icon.ingredientType());
		assertEquals("test:icon_definition_canonical", icon.canonicalIngredientType());
		assertEquals("opaque UID value", icon.valueId());
	}

	@Test
	void rejectsBlankTypeAndValue() {
		assertThrows(IllegalArgumentException.class, () -> new GroupIconDefinition(" ", "value"));
		assertThrows(IllegalArgumentException.class, () -> new GroupIconDefinition("test:type", " "));
	}
}
