package com.starskyxiii.collapsible_groups.client.editor;

import java.util.List;
import java.util.Objects;

final class EditorFluidSelectionHelper {
	private final List<String> fluidIds;
	private final Runnable onContentsDraftChanged;

	EditorFluidSelectionHelper(List<String> fluidIds, Runnable onContentsDraftChanged) {
		this.fluidIds = Objects.requireNonNull(fluidIds, "fluidIds");
		this.onContentsDraftChanged = Objects.requireNonNull(onContentsDraftChanged, "onContentsDraftChanged");
	}

	boolean isSelected(EditorFluidIngredientView view) {
		return isIdSelected(view.resourceId());
	}

	void toggleSelection(EditorFluidIngredientView view) {
		toggleId(view.resourceId());
	}

	void removeSelection(EditorFluidIngredientView view) {
		removeId(view.resourceId());
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
}
