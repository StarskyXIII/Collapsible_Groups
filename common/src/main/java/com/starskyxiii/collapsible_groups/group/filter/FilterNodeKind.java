package com.starskyxiii.collapsible_groups.group.filter;

/** Stable capability-table keys for filter node forms. */
public enum FilterNodeKind {
	ANY,
	ALL,
	NOT,
	ID,
	TAG,
	BLOCK_TAG,
	ITEM_PATH_STARTS_WITH,
	ITEM_PATH_CONTAINS,
	ITEM_PATH_ENDS_WITH,
	NAMESPACE,
	EXACT_STACK,
	HAS_COMPONENT,
	COMPONENT_PATH,
	UNKNOWN
}
