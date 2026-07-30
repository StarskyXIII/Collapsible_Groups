package com.starskyxiii.collapsible_groups.compat.jei.element;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JeiElementAbiTest {
	private static final List<Class<?>> ELEMENT_TYPES = List.of(
		AbstractFluidChildElement.class,
		GenericChildElement.class,
		GroupChildElement.class,
		GroupHeaderElement.class
	);

	@Test
	void everyCustomElementUsesJei29TooltipHelperDescriptor() throws ReflectiveOperationException {
		for (Class<?> elementType : ELEMENT_TYPES) {
			Method getTooltip = elementType.getDeclaredMethod(
				"getTooltip",
				JeiTooltip.class,
				IngredientGridTooltipHelper.class,
				IIngredientRenderer.class,
				IIngredientHelper.class
			);
			assertNotNull(getTooltip, elementType.getName());
		}
	}

	@Test
	void everyCustomElementImplementsJei29TickContract() throws ReflectiveOperationException {
		for (Class<?> elementType : ELEMENT_TYPES) {
			Method tick = elementType.getDeclaredMethod("tick");
			assertNotNull(tick, elementType.getName());
		}
	}
}
