package com.starskyxiii.collapsible_groups.compat.emi;

import com.starskyxiii.collapsible_groups.Constants;
import com.starskyxiii.collapsible_groups.platform.Services;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;

import java.util.List;

/** Diagnostic-only EMI input trace, gated by the existing timing/debug option. */
public final class EmiInteractionTrace {
	private EmiInteractionTrace() {}

	public static boolean enabled() {
		return Services.CONFIG.debugTimingEnabled();
	}

	public static void input(String phase, int button, EmiStackInteraction hovered,
		EmiIngredient pressed, EmiIngredient dragged, boolean nativeFinallyReached) {
		if (!enabled()) return;
		try {
			EmiIngredient wrapper = hovered == null ? EmiStack.EMPTY : hovered.getStack();
			EmiIngredient delegate = wrapper instanceof ProjectedChildEmiIngredient child
				? child.delegate() : wrapper;
			List<EmiStack> stacks = wrapper.getEmiStacks();
			Constants.LOG.info("EMI input trace phase={} button={} wrapper={} delegate={} amount={} "
				+ "stacks={} pressed={} dragged={} nativeFinallyReached={}",
				phase, button, className(wrapper), className(delegate), wrapper.getAmount(),
				describeStacks(stacks), describeIngredient(pressed), describeIngredient(dragged),
				nativeFinallyReached);
		} catch (Throwable error) {
			exception(phase + "/trace", error);
		}
	}

	public static void exception(String phase, Throwable error) {
		if (enabled()) Constants.LOG.error("EMI input trace exception at {}", phase, error);
	}

	private static String describeStacks(List<EmiStack> stacks) {
		return stacks.stream().map(stack -> className(stack) + "[amount=" + stack.getAmount()
			+ ",id=" + stack.getId() + "]").toList().toString();
	}

	private static String describeIngredient(EmiIngredient ingredient) {
		return className(ingredient) + "[amount=" + (ingredient == null ? "n/a" : ingredient.getAmount()) + "]";
	}

	private static String className(Object value) {
		return value == null ? "null" : value.getClass().getName();
	}
}
