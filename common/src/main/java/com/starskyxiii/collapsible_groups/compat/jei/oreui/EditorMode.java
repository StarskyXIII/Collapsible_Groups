package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.Objects;
import java.util.Optional;

public enum EditorMode {
	ITEMS(EditorModeCategory.SOURCE_CONTENT, PreviewIngredientKind.ITEM, true, true, true, true, true),
	FLUIDS(EditorModeCategory.SOURCE_CONTENT, PreviewIngredientKind.FLUID, true, true, true, true, true),
	OTHER_TYPES(EditorModeCategory.SOURCE_CONTENT, PreviewIngredientKind.GENERIC, true, true, true, true, true),
	RULES(EditorModeCategory.RULES, null, false, false, false, false, true),
	APPEARANCE(EditorModeCategory.APPEARANCE, null, false, false, false, false, true);

	private final EditorModeCategory category;
	private final PreviewIngredientKind sourceIngredientKind;
	private final boolean searchEnabled;
	private final boolean hideUsedEnabled;
	private final boolean ownershipLabelsEnabled;
	private final boolean sourceGridEnabled;
	private final boolean rightPreviewEnabled;

	EditorMode(
		EditorModeCategory category,
		PreviewIngredientKind sourceIngredientKind,
		boolean searchEnabled,
		boolean hideUsedEnabled,
		boolean ownershipLabelsEnabled,
		boolean sourceGridEnabled,
		boolean rightPreviewEnabled
	) {
		this.category = Objects.requireNonNull(category, "category");
		this.sourceIngredientKind = sourceIngredientKind;
		this.searchEnabled = searchEnabled;
		this.hideUsedEnabled = hideUsedEnabled;
		this.ownershipLabelsEnabled = ownershipLabelsEnabled;
		this.sourceGridEnabled = sourceGridEnabled;
		this.rightPreviewEnabled = rightPreviewEnabled;
	}

	public EditorModeCategory category() {
		return category;
	}

	public Optional<PreviewIngredientKind> sourceIngredientKind() {
		return Optional.ofNullable(sourceIngredientKind);
	}

	public boolean searchEnabled() {
		return searchEnabled;
	}

	public boolean hideUsedEnabled() {
		return hideUsedEnabled;
	}

	public boolean ownershipLabelsEnabled() {
		return ownershipLabelsEnabled;
	}

	public boolean sourceGridEnabled() {
		return sourceGridEnabled;
	}

	public boolean rightPreviewEnabled() {
		return rightPreviewEnabled;
	}

	public boolean sourceContentMode() {
		return category == EditorModeCategory.SOURCE_CONTENT;
	}
}
