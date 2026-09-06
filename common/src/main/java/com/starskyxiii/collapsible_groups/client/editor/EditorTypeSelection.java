package com.starskyxiii.collapsible_groups.client.editor;

import java.util.List;

final class EditorTypeSelection {
	private EditorIngredientTypes catalog = EditorIngredientTypes.PENDING;
	private List<EditorIngredientTypes.Option> filtered = List.of();
	private String query = "";
	private String selected;

	boolean update(EditorIngredientTypes next) {
		if (catalog.token() == next.token()) return false;
		catalog = next;
		selected = null;
		filter();
		return true;
	}

	void search(String value) {
		query = value;
		filter();
		if (filtered.stream().noneMatch(option -> option.id().equals(selected))) selected = null;
	}

	private void filter() { filtered = catalog.options().stream().filter(option -> option.matches(query)).toList(); }
	List<EditorIngredientTypes.Option> rows() { return filtered; }
	EditorIngredientTypes catalog() { return catalog; }
	String selected() { return selected; }
	void select(int index) { selected = index >= 0 && index < filtered.size() ? filtered.get(index).id() : null; }
	int selectedIndex() {
		for (int i = 0; i < filtered.size(); i++) if (filtered.get(i).id().equals(selected)) return i;
		return -1;
	}
	void move(int direction) {
		if (filtered.isEmpty()) return;
		int index = selectedIndex();
		select(index < 0 ? (direction > 0 ? 0 : filtered.size() - 1)
			: Math.max(0, Math.min(filtered.size() - 1, index + direction)));
	}
}
