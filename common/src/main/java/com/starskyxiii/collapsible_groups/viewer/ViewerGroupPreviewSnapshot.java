package com.starskyxiii.collapsible_groups.viewer;

import java.util.List;
import java.util.Objects;

/** One group's enabled-independent full-match values from one published viewer generation. */
public record ViewerGroupPreviewSnapshot(
	List<ViewerPreviewValue> items,
	List<ViewerPreviewValue> fluids,
	List<ViewerPreviewValue> generic
) {
	public ViewerGroupPreviewSnapshot {
		items = List.copyOf(Objects.requireNonNull(items, "items"));
		fluids = List.copyOf(Objects.requireNonNull(fluids, "fluids"));
		generic = List.copyOf(Objects.requireNonNull(generic, "generic"));
	}

	public List<ViewerPreviewValue> allValues() {
		java.util.ArrayList<ViewerPreviewValue> values =
			new java.util.ArrayList<>(items.size() + fluids.size() + generic.size());
		values.addAll(items);
		values.addAll(fluids);
		values.addAll(generic);
		return List.copyOf(values);
	}
}
