package com.starskyxiii.collapsible_groups.client.manager.model;

public enum GroupSource {
	USER,
	BUILTIN,
	KUBEJS;

	public static GroupSource fromGroupId(String groupId) {
		return switch (com.starskyxiii.collapsible_groups.group.GroupSource.fromGroupId(groupId)) {
			case USER -> USER;
			case BUILTIN -> BUILTIN;
			case KUBEJS -> KUBEJS;
		};
	}

	public boolean userEditable() {
		return this == USER;
	}

	public boolean readOnlyDefinition() {
		return this != USER;
	}
}
