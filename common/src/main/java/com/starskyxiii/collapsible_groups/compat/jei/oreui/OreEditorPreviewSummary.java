package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;

public record OreEditorPreviewSummary(int itemCount, int fluidCount, int genericCount) {
	public static OreEditorPreviewSummary of(int itemCount, int fluidCount, int genericCount) {
		return new OreEditorPreviewSummary(
			Math.max(0, itemCount),
			Math.max(0, fluidCount),
			Math.max(0, genericCount)
		);
	}

	public String labelKey() {
		if (nonZeroKinds() == 1) {
			if (itemCount > 0) return ModTranslationKeys.ORE_EDITOR_PREVIEW_SUMMARY_ITEMS_ONLY;
			if (fluidCount > 0) return ModTranslationKeys.ORE_EDITOR_PREVIEW_SUMMARY_FLUIDS_ONLY;
			return ModTranslationKeys.ORE_EDITOR_PREVIEW_SUMMARY_GENERIC_ONLY;
		}
		return ModTranslationKeys.COUNT_ENTRIES;
	}

	public Object[] labelArgs() {
		if (nonZeroKinds() == 1) {
			if (itemCount > 0) return new Object[] {itemCount};
			if (fluidCount > 0) return new Object[] {fluidCount};
			return new Object[] {genericCount};
		}
		return new Object[] {itemCount + fluidCount + genericCount};
	}

	private int nonZeroKinds() {
		int kinds = 0;
		if (itemCount > 0) kinds++;
		if (fluidCount > 0) kinds++;
		if (genericCount > 0) kinds++;
		return kinds;
	}
}
