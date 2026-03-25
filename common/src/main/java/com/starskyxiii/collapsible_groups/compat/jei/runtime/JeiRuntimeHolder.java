package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import mezz.jei.api.runtime.IJeiRuntime;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the active IJeiRuntime instance supplied by CollapsibleGroupsJeiPlugin.
 * Available after JEI calls onRuntimeAvailable(); null before that point.
 */
public final class JeiRuntimeHolder {
	@Nullable
	private static volatile IJeiRuntime runtime;

	private JeiRuntimeHolder() {}

	public static void set(IJeiRuntime r) {
		runtime = r;
	}

	@Nullable
	public static IJeiRuntime get() {
		return runtime;
	}

	public static boolean isAvailable() {
		return runtime != null;
	}
}
