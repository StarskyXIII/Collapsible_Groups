package com.starskyxiii.collapsible_groups.viewer;

import java.util.List;
import java.util.Objects;

/** Search text, filtered results, and the inputs required by search ungrouping policy. */
public record ViewerSearchSnapshot<E>(
	String searchText,
	List<ViewerIngredient<E>> filteredResults,
	boolean ungroupSmallGroups,
	int ungroupThreshold
) {
	public ViewerSearchSnapshot {
		Objects.requireNonNull(searchText, "searchText");
		filteredResults = List.copyOf(filteredResults);
	}

	public boolean searchActive() {
		return !searchText.isBlank();
	}
}
