package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import mezz.jei.gui.elements.IconButton;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class JeiInputHandlerAdapter {

	private static volatile Bindings bindings;

	private JeiInputHandlerAdapter() {}

	static Object wrap(IconButton button, Object original, BooleanSupplier visible, String jeiVersion) {
		String operation = "resolve IconButton#createInputHandler";
		String inputType = "unresolved";
		try {
			Bindings active = bindings();
			inputType = active.handlerType().getName();
			operation = "validate original handler";
			active.requireHandler(original);
			operation = "create groups button handler";
			Object groups = active.requireHandler(active.createInputHandler().invoke(button));
			operation = "create CombinedInputHandler(String, List)";
			Object combined = active.requireHandler(active.combined().newInstance(
				"IngredientListOverlay_withGroups", List.of(groups, original)));
			operation = "create ProxyInputHandler(Supplier)";
			Supplier<Object> selected = () -> visible.getAsBoolean() ? combined : original;
			return active.requireHandler(active.proxy().newInstance(selected));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			Throwable cause = exception instanceof InvocationTargetException invocation
				? invocation.getTargetException() : exception;
			throw new IllegalStateException("Collapsible Groups could not " + operation
				+ " for JEI " + jeiVersion + " (input handler: " + inputType + ")", cause);
		}
	}

	private static Bindings bindings() throws ReflectiveOperationException {
		Bindings active = bindings;
		if (active != null) return active;
		synchronized (JeiInputHandlerAdapter.class) {
			if (bindings == null) bindings = resolve();
			return bindings;
		}
	}

	private static Bindings resolve() throws ReflectiveOperationException {
		Method create = IconButton.class.getMethod("createInputHandler");
		Class<?> handlerType = create.getReturnType();
		String combinedName = switch (handlerType.getName()) {
			case "mezz.jei.gui.input.IUserInputHandler" -> "mezz.jei.gui.input.handlers.CombinedInputHandler";
			case "mezz.jei.common.input.IUserInputHandler" -> "mezz.jei.common.input.handlers.CombinedInputHandler";
			default -> throw new IllegalStateException("Unsupported JEI input method: " + create);
		};
		ClassLoader loader = IconButton.class.getClassLoader();
		Class<?> combinedType = Class.forName(combinedName, false, loader);
		Class<?> proxyType = Class.forName("mezz.jei.gui.input.handlers.ProxyInputHandler", false, loader);
		if (!handlerType.isInterface() || !handlerType.isAssignableFrom(combinedType)
			|| !handlerType.isAssignableFrom(proxyType)) {
			throw new IllegalStateException("JEI input handlers do not implement " + handlerType.getName()
				+ ": " + combinedType.getName() + ", " + proxyType.getName());
		}
		return new Bindings(handlerType, create,
			combinedType.getConstructor(String.class, List.class), proxyType.getConstructor(Supplier.class));
	}

	private record Bindings(Class<?> handlerType, Method createInputHandler,
		Constructor<?> combined, Constructor<?> proxy) {
		Object requireHandler(Object value) {
			if (!handlerType.isInstance(value)) {
				throw new IllegalStateException("Expected " + handlerType.getName() + ", got "
					+ (value == null ? "null" : value.getClass().getName()));
			}
			return value;
		}
	}
}
