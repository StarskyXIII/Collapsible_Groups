package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.platform.Services;

import java.util.List;
import java.util.Objects;

final class EditorFluidSelectionHelper {
	private final List<String> fluidIds;
	private final Runnable onContentsDraftChanged;

	EditorFluidSelectionHelper(List<String> fluidIds, Runnable onContentsDraftChanged) {
		this.fluidIds = Objects.requireNonNull(fluidIds, "fluidIds");
		this.onContentsDraftChanged = Objects.requireNonNull(onContentsDraftChanged, "onContentsDraftChanged");
	}

	boolean isSelected(Object fluid) {
		return isIdSelected(fluidId(fluid));
	}

	void toggleSelection(Object fluid) {
		toggleId(fluidId(fluid));
	}

	void removeSelection(Object fluid) {
		removeId(fluidId(fluid));
	}

	boolean isIdSelected(String id) {
		return fluidIds.contains(id);
	}

	void toggleId(String id) {
		if (!fluidIds.remove(id)) {
			fluidIds.add(id);
		}
		onContentsDraftChanged.run();
	}

	void addId(String id) {
		if (!fluidIds.contains(id)) {
			fluidIds.add(id);
			onContentsDraftChanged.run();
		}
	}

	void removeId(String id) {
		if (fluidIds.remove(id)) {
			onContentsDraftChanged.run();
		}
	}

	private static String fluidId(Object fluid) {
		return Services.PLATFORM.getFluidId(fluid);
	}
}
