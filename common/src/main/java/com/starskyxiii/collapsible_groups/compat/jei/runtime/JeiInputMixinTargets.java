package com.starskyxiii.collapsible_groups.compat.jei.runtime;

import java.util.function.Predicate;

public final class JeiInputMixinTargets {
	public static final String LEGACY = "mezz.jei.gui.input.handlers.FocusInputHandler";
	public static final String CURRENT = "mezz.jei.gui.input.handlers.ElementInputHandler";

	private JeiInputMixinTargets() {}

	public static String select(boolean selected, Predicate<String> present, String version) {
		if (!selected) return null;
		if (present.test(CURRENT)) return CURRENT;
		if (present.test(LEGACY)) return LEGACY;
		throw new IllegalStateException("Collapsible Groups cannot find a JEI element input target for JEI "
			+ version + "; missing " + CURRENT + " and " + LEGACY);
	}
}
