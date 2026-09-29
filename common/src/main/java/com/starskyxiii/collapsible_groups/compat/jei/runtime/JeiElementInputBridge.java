package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import com.starskyxiii.collapsible_groups.compat.jei.element.GroupHeaderElement;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.overlay.elements.IElement;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class JeiElementInputBridge {
	private static volatile MethodHandle nativeClick;

	private JeiElementInputBridge() {}

	public static boolean handleClick(IElement<?> element, IJeiUserInput input, IInternalKeyMappings keys) {
		if (element instanceof GroupHeaderElement header) return header.handleJeiClick(input, keys);
		try {
			return (boolean) nativeClick().invokeExact(element, input, keys);
		} catch (RuntimeException | Error exception) {
			throw exception;
		} catch (Throwable exception) {
			throw new IllegalStateException("JEI element click failed for " + element.getClass().getName(), exception);
		}
	}

	private static MethodHandle nativeClick() {
		MethodHandle active = nativeClick;
		if (active != null) return active;
		synchronized (JeiElementInputBridge.class) {
			if (nativeClick == null) nativeClick = resolve();
			return nativeClick;
		}
	}

	private static MethodHandle resolve() {
		Method[] candidates = Arrays.stream(IElement.class.getMethods())
			.filter(m -> m.getName().equals("handleClick") && m.getReturnType() == boolean.class
				&& m.getParameterCount() == 2 && IJeiUserInput.class.isAssignableFrom(m.getParameterTypes()[0])
				&& m.getParameterTypes()[1] == IInternalKeyMappings.class).toArray(Method[]::new);
		if (candidates.length != 1) {
			throw new IllegalStateException("Expected one JEI IElement.handleClick binding, found "
				+ Arrays.toString(candidates));
		}
		try {
			return MethodHandles.publicLookup().unreflect(candidates[0]).asType(MethodType.methodType(
				boolean.class, IElement.class, IJeiUserInput.class, IInternalKeyMappings.class));
		} catch (IllegalAccessException exception) {
			throw new IllegalStateException("Could not bind JEI element input: " + candidates[0], exception);
		}
	}
}
