package com.starskyxiii.collapsible_groups.viewer;

/** Pure recipe-viewer selection policy shared by runtime bootstrap and early mixin gating. */
public final class ViewerSelectionPolicy {
	public static final String JEI = "jei";
	public static final String EMI = "emi";
	public static final String TMRV = "toomanyrecipeviewers";

	private ViewerSelectionPolicy() {}

	public static Viewer select(boolean jeiPresent, boolean emiPresent, boolean tmrvPresent,
		boolean jeiSupported, boolean emiSupported) {
		boolean realJei = jeiPresent && !tmrvPresent;
		boolean effectiveEmi = emiPresent || tmrvPresent;
		if (effectiveEmi && emiSupported) return Viewer.EMI;
		if (realJei && jeiSupported) return Viewer.JEI;
		return Viewer.NONE;
	}

	public enum Viewer {
		NONE,
		JEI,
		EMI
	}
}
