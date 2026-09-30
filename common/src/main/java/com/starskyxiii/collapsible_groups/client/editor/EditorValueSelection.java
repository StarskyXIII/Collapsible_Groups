package com.starskyxiii.collapsible_groups.client.editor;

import java.util.List;
import java.util.Locale;

final class EditorValueSelection {
	private EditorValuePickerKind.Snapshot catalog = EditorValuePickerKind.TAG.snapshot(null, "");
	private List<String> rows = List.of();
	private String query = "";
	private String selected;

	boolean update(EditorValuePickerKind.Snapshot next) {
		if (catalog.kind() == next.kind() && catalog.token() == next.token()) return false;
		catalog = next;
		selected = null;
		filter();
		return true;
	}

	void search(String value) {
		query = value;
		filter();
		if (selected != null && !rows.contains(selected)) selected = null;
	}

	private void filter() {
		String needle = query.toLowerCase(Locale.ROOT);
		rows = catalog.ready()
			? catalog.values().stream().filter(value -> value.toLowerCase(Locale.ROOT).contains(needle)).toList() : List.of();
	}

	EditorValuePickerKind.Snapshot catalog() { return catalog; }
	List<String> rows() { return rows; }
	String selected() { return selected; }
	int selectedIndex() { return selected == null ? -1 : rows.indexOf(selected); }
	void select(int index) { selected = index >= 0 && index < rows.size() ? rows.get(index) : null; }
	void move(int direction) {
		int index = selectedIndex();
		select(index < 0 ? (direction > 0 ? 0 : rows.size() - 1)
			: Math.max(0, Math.min(rows.size() - 1, index + direction)));
	}
}
