package com.starskyxiii.collapsible_groups.viewer;

/** Supplies expansion state without coupling projection to a viewer UI. */
@FunctionalInterface
public interface GroupExpansionState {
	boolean isExpanded(String groupId);
}
