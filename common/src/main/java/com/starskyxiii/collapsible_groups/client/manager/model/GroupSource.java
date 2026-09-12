package com.starskyxiii.collapsible_groups.client.manager.model;

public enum GroupSource {
	USER,
	BUILTIN,
	RESOURCE_PACK,
	OVERRIDE,
	KUBEJS;

	public static GroupSource fromGroupId(String groupId) {
		return switch (com.starskyxiii.collapsible_groups.group.GroupSource.fromGroupId(groupId)) {
			case USER -> USER;
			case BUILTIN -> BUILTIN;
			case RESOURCE_PACK -> RESOURCE_PACK;
			case OVERRIDE -> OVERRIDE;
			case KUBEJS -> KUBEJS;
		};
	}

	public boolean userEditable() {
		return this == USER || this == OVERRIDE;
	}

	public boolean readOnlyDefinition() {
		return !userEditable();
	}
}
