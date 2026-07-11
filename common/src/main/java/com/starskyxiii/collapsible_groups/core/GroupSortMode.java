package com.starskyxiii.collapsible_groups.core;

/** Recipe-viewer-neutral view order for group manager cards. */
public enum GroupSortMode {
	PRIORITY("priority"),
	NAME_ASC("name_asc"),
	NAME_DESC("name_desc");

	private final String id;

	GroupSortMode(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static GroupSortMode fromId(String id) {
		for (GroupSortMode mode : values()) {
			if (mode.id.equals(id)) return mode;
		}
		return PRIORITY;
	}
}
