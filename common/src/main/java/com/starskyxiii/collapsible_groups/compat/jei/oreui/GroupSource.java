package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import java.util.Objects;

public enum GroupSource {
	USER,
	BUILTIN,
	KUBEJS;

	public static GroupSource fromGroupId(String groupId) {
		Objects.requireNonNull(groupId, "groupId");
		if (groupId.startsWith("__default_")) {
			return BUILTIN;
		}
		if (groupId.startsWith("__kjs_")) {
			return KUBEJS;
		}
		return USER;
	}

	public boolean userEditable() {
		return this == USER;
	}

	public boolean readOnlyDefinition() {
		return this != USER;
	}
}
