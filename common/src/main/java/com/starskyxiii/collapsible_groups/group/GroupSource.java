package com.starskyxiii.collapsible_groups.group;

import java.util.Objects;

/** Stable source classification derived from reserved group ID prefixes. */
public enum GroupSource {
	USER,
	BUILTIN,
	KUBEJS;

	public static GroupSource fromGroupId(String groupId) {
		Objects.requireNonNull(groupId, "groupId");
		if (groupId.startsWith("__default_")) return BUILTIN;
		if (groupId.startsWith("__kjs_")) return KUBEJS;
		return USER;
	}

	public boolean usesEnabledOverride() {
		return this != USER;
	}
}
