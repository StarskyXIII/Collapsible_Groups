package com.starskyxiii.collapsible_groups.client.manager.model;

public enum GroupSource {
	USER,
	BUILTIN,
	RESOURCE_PACK,
	KUBEJS;

	public static GroupSource fromGroupId(String groupId) {
		return from(com.starskyxiii.collapsible_groups.group.GroupSource.fromGroupId(groupId));
	}

	public static GroupSource from(com.starskyxiii.collapsible_groups.group.GroupSource source) {
		return switch (source) {
			case USER -> USER;
			case BUILTIN -> BUILTIN;
			case RESOURCE_PACK -> RESOURCE_PACK;
			case KUBEJS -> KUBEJS;
		};
	}

	public boolean userEditable() {
		return this == USER;
	}

	public boolean readOnlyDefinition() {
		return !userEditable();
	}
}
