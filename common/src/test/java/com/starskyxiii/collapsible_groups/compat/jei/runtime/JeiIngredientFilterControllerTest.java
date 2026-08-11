package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiIngredientFilterControllerTest {
	@Test
	void rawFallbackPreservesIngredientOrderAndContainsNoGroupState() {
		IIngredientType<String> type = new IIngredientType<>() {
			@Override public Class<? extends String> getIngredientClass() { return String.class; }
			@Override public String getUid() { return "test:raw_fallback"; }
		};
		ITypedIngredient<String> first = typed(type, "first");
		ITypedIngredient<String> second = typed(type, "second");

		JeiIngredientFilterController.RawStructure raw =
			JeiIngredientFilterController.buildRawStructure(List.of(first, second));

		assertEquals(2, raw.elements().size());
		assertSame(first, raw.elements().get(0).getTypedIngredient());
		assertSame(second, raw.elements().get(1).getTypedIngredient());
		assertEquals(2, raw.groupIds().size());
		assertTrue(raw.groupIds().stream().allMatch(id -> id == null));
		assertTrue(raw.childrenByGroupId().isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> raw.elements().clear());
		assertThrows(UnsupportedOperationException.class, () -> raw.groupIds().clear());
	}

	private static ITypedIngredient<String> typed(IIngredientType<String> type, String value) {
		return new ITypedIngredient<>() {
			@Override public IIngredientType<String> getType() { return type; }
			@Override public String getIngredient() { return value; }
		};
	}
}
