package com.starskyxiii.collapsible_groups.compat.jei;

import com.starskyxiii.collapsible_groups.compat.jei.api.CGApi;
import com.starskyxiii.collapsible_groups.platform.fluid.FluidConversionResult;
import mezz.jei.api.ingredients.IIngredientType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

	@Test
	void existingFluidTypeProviderImplementationsGetExplicitUnsupportedConversion() {
		IIngredientType<Object> type = () -> Object.class;
		JeiIngredientTypes.FluidTypeProvider provider = () -> type;

		assertSame(type, provider.getFluidType());
		assertInstanceOf(FluidConversionResult.Unsupported.class, provider.convertFluid(new Object()));
	}
}
