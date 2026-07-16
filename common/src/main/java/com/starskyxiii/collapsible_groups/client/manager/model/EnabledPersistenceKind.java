package com.starskyxiii.collapsible_groups.client.manager.model;

public enum EnabledPersistenceKind {
	GROUP_JSON(false),
	ENABLED_OVERRIDE_STORE(true),
	NONE(false);

	private final boolean requiresOverrideStore;

	EnabledPersistenceKind(boolean requiresOverrideStore) {
		this.requiresOverrideStore = requiresOverrideStore;
	}

	public boolean requiresOverrideStore() {
		return requiresOverrideStore;
	}
}
