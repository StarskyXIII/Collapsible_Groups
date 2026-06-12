package com.starskyxiii.collapsible_groups.compat.jei.runtime;

/** Pure policy helper for deciding whether a filtered group should be shown as regular entries during search. */
public final class SearchUngroupPolicy {
	private SearchUngroupPolicy() {}

	public static boolean shouldUngroup(boolean enabled, boolean searchActive, int childCount, int threshold) {
		return enabled
			&& searchActive
			&& threshold > 0
			&& childCount >= 2
			&& childCount < threshold;
	}
}
