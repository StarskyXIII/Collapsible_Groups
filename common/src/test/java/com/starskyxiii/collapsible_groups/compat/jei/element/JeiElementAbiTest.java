package com.starskyxiii.collapsible_groups.compat.jei.element;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.overlay.elements.IElement;
import mezz.jei.gui.overlay.elements.IngredientElement;
import mezz.jei.gui.overlay.ingredients.IngredientGridTooltipHelper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiElementAbiTest {
	private static final List<Class<?>> ELEMENT_TYPES = List.of(
		AbstractFluidChildElement.class,
		GenericChildElement.class,
		GroupChildElement.class,
		GroupHeaderElement.class
	);

	@Test
	void jeiElementContractIncludesTick() throws ReflectiveOperationException {
		Method tick = IElement.class.getMethod("tick");

		assertEquals(void.class, tick.getReturnType());
	}

	@Test
	void customElementsInheritTheActiveJeiClickDescriptor() throws ReflectiveOperationException {
		List<Method> clicks = Arrays.stream(IElement.class.getMethods())
			.filter(method -> method.getName().equals("handleClick")).toList();
		assertEquals(1, clicks.size());
		Method click = clicks.getFirst();
		assertTrue(click.isDefault());
		assertEquals(boolean.class, click.getReturnType());
		assertEquals(2, click.getParameterCount());
		assertTrue(IJeiUserInput.class.isAssignableFrom(click.getParameterTypes()[0]));
		assertEquals(IInternalKeyMappings.class, click.getParameterTypes()[1]);
		for (Class<?> elementType : ELEMENT_TYPES) {
			assertEquals(IElement.class, elementType.getMethod("handleClick", click.getParameterTypes()).getDeclaringClass());
		}
		assertEquals(IElement.class, IngredientElement.class
			.getMethod("handleClick", click.getParameterTypes()).getDeclaringClass());
	}

	@Test
	void ordinaryJeiElementsStillLeaveClickHandlingToTheNativeChain() throws ReflectiveOperationException {
		IElement<?> element = (IElement<?>) Proxy.newProxyInstance(IElement.class.getClassLoader(),
			new Class<?>[] { IElement.class }, (proxy, method, args) -> InvocationHandler.invokeDefault(proxy, method, args));
		Method click = Arrays.stream(IElement.class.getMethods())
			.filter(method -> method.getName().equals("handleClick")).findFirst().orElseThrow();
		assertEquals(Boolean.FALSE, click.invoke(element, new Object[] { null, null }));
	}

	@Test
	void headerClickBridgeUsesThePublicInputContract() throws ReflectiveOperationException {
		assertTrue(JeiClickableElement.class.isAssignableFrom(GroupHeaderElement.class));
		Method bridge = GroupHeaderElement.class.getDeclaredMethod("handleJeiClick", IJeiUserInput.class,
			IInternalKeyMappings.class);
		assertEquals(boolean.class, bridge.getReturnType());
	}

	@Test
	void everyCustomElementDeclaresTick() throws ReflectiveOperationException {
		for (Class<?> elementType : ELEMENT_TYPES) {
			Method tick = elementType.getDeclaredMethod("tick");
			assertEquals(void.class, tick.getReturnType(), elementType.getName());
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
