package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.i18n.ModTranslationKeys;

public enum OreEditorContentFilter {
	ITEMS(ModTranslationKeys.EDITOR_TAB_ITEMS, PreviewIngredientKind.ITEM),
	FLUIDS(ModTranslationKeys.EDITOR_TAB_FLUIDS, PreviewIngredientKind.FLUID),
	OTHER_TYPES(ModTranslationKeys.EDITOR_TAB_GENERIC, PreviewIngredientKind.GENERIC);

	private final String labelKey;
	private final PreviewIngredientKind ingredientKind;

	OreEditorContentFilter(String labelKey, PreviewIngredientKind ingredientKind) {
		this.labelKey = labelKey;
		this.ingredientKind = ingredientKind;
	}

	public String labelKey() {
		return labelKey;
	}

	public PreviewIngredientKind ingredientKind() {
		return ingredientKind;
	}

	public boolean searchEnabled() {
		return true;
	}

	public boolean hideUsedEnabled() {
		return true;
	}

	public boolean ownershipLabelsEnabled() {
		return true;
	}
}
