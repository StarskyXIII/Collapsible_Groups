package com.starskyxiii.collapsible_groups.compat.jei.element;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.IngredientGridTooltipHelper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiElementAbiTest {
	private static final List<Class<?>> ELEMENT_TYPES = List.of(
		AbstractFluidChildElement.class,
		GenericChildElement.class,
		GroupChildElement.class,
		GroupHeaderElement.class
	);

	@Test
	void jeiElementContractDoesNotIncludeTick() {
		assertThrows(NoSuchMethodException.class, () -> IElement.class.getMethod("tick"));
	}

	@Test
	void customElementsDoNotDeclareRemovedTickHook() {
		for (Class<?> elementType : ELEMENT_TYPES) {
			assertThrows(NoSuchMethodException.class, () -> elementType.getDeclaredMethod("tick"), elementType.getName());
		}
	}

	@Test
	void everyCustomElementUsesMovedTooltipHelperDescriptor() throws ReflectiveOperationException {
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
	void everyCustomElementSupportsSameFramePreRender() throws ReflectiveOperationException {
		for (Class<?> elementType : ELEMENT_TYPES) {
			assertTrue(PreRenderIngredientGridElement.class.isAssignableFrom(elementType), elementType.getName());
			Method preRender = elementType.getMethod(
				"drawPreRender",
				net.minecraft.client.gui.GuiGraphics.class,
				int.class,
				int.class
			);
			assertEquals(void.class, preRender.getReturnType(), elementType.getName());
		}
	}
}
