package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.InputType;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.overlay.elements.IElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JeiElementInputBridgeTest {
	@Test void nativeOverrideReceivesOriginalArgumentsExactlyOnce() {
		UserInput input = new UserInput(null, 12, 34, 0, InputType.EXECUTE);
		IInternalKeyMappings keys = (IInternalKeyMappings) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] {IInternalKeyMappings.class}, (proxy, method, args) -> null);
		AtomicInteger calls = new AtomicInteger();
		IElement<?> element = (IElement<?>) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] {IElement.class}, (proxy, method, args) -> {
				assertEquals("handleClick", method.getName());
				assertSame(input, args[0]);
				assertSame(keys, args[1]);
				return calls.incrementAndGet() == 1;
			});
		assertTrue(JeiElementInputBridge.handleClick(element, input, keys));
		assertFalse(JeiElementInputBridge.handleClick(element, input, keys));
		assertEquals(2, calls.get());
	}

	@Test void nativeExceptionsAreNotSwallowedOrReplaced() {
		RuntimeException expected = new IllegalArgumentException("native override");
		IElement<?> element = (IElement<?>) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] {IElement.class}, (proxy, method, args) -> { throw expected; });
		UserInput input = new UserInput(null, 0, 0, 0, InputType.EXECUTE);
		assertSame(expected, assertThrows(IllegalArgumentException.class,
			() -> JeiElementInputBridge.handleClick(element, input, null)));
	}
}
