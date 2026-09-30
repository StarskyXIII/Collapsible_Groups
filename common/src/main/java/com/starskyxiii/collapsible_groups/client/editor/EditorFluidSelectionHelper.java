package com.starskyxiii.collapsible_groups.client.editor;

import java.util.List;

final class EditorFluidSelectionHelper {
	boolean isSelected(EditorFluidIngredientView view, List<String> fluidIds) {
		return isIdSelected(view.resourceId(), fluidIds);
	}

	void toggleSelection(EditorFluidIngredientView view, List<String> fluidIds) {
		toggleId(view.resourceId(), fluidIds);
	}

	boolean removeSelection(EditorFluidIngredientView view, List<String> fluidIds) {
		return removeId(view.resourceId(), fluidIds);
	}

	boolean isIdSelected(String id, List<String> fluidIds) {
		return fluidIds.contains(id);
	}

	void toggleId(String id, List<String> fluidIds) {
		if (!fluidIds.remove(id)) {
			fluidIds.add(id);
		}
	}

	boolean addId(String id, List<String> fluidIds) {
		if (!fluidIds.contains(id)) {
			fluidIds.add(id);
			return true;
		}
		return false;
	}

	boolean removeId(String id, List<String> fluidIds) {
		return fluidIds.remove(id);
	}
}
