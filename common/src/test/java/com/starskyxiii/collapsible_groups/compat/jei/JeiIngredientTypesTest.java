package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.api.CGApi;
import mezz.jei.api.ingredients.IIngredientType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JeiIngredientTypesTest {
	@Test
	void cgApiRegistersCanonicalTypeAndAliasAcrossBothRegistries() {
		IIngredientType<Object> type = () -> Object.class;

		CGApi.registerIngredientType("test:jei_type", type);
		CGApi.registerIngredientTypeAlias("jei_alias", "test:jei_type");

		assertSame(type, JeiIngredientTypes.get("test:jei_type"));
		assertSame(type, JeiIngredientTypes.get("jei_alias"));
		assertEquals("test:jei_type", JeiIngredientTypes.getCanonicalId(type));
	}
}
