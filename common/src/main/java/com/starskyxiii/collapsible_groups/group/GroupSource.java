package com.starskyxiii.collapsible_groups.group;

import java.util.Objects;

public enum GroupSource {
	USER,
	BUILTIN,
	RESOURCE_PACK,
	KUBEJS;

	public static GroupSource fromGroupId(String groupId) {
		Objects.requireNonNull(groupId, "groupId");
		return GroupRepository.sourceOf(groupId);
	}

	public boolean usesEnabledOverride() {
		return this != USER;
	}
}
