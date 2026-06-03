package com.starskyxiii.collapsible_groups.compat.jei.oreui;

public enum RuleFixedOperator {
	NONE("", ""),
	ITEM_PATH_STARTS_WITH("item_path_starts_with", "starts with"),
	ITEM_PATH_CONTAINS("item_path_contains", "contains"),
	ITEM_PATH_ENDS_WITH("item_path_ends_with", "ends with");

	private final String id;
	private final String label;

	RuleFixedOperator(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public String id() {
		return id;
	}

	public String label() {
		return label;
	}

	public boolean present() {
		return this != NONE;
	}
}
