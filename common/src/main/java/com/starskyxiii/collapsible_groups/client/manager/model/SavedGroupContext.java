package com.starskyxiii.collapsible_groups.client.manager.model;

import java.util.Objects;

/** Typed handoff from an editor save to any group-manager UI. */
public record SavedGroupContext(String groupId, SaveKind kind) {
	public SavedGroupContext {
		groupId = Objects.requireNonNull(groupId, "groupId");
		kind = Objects.requireNonNull(kind, "kind");
	}

	public boolean shouldReveal() {
		return kind == SaveKind.CREATED || kind == SaveKind.COPIED;
	}

	public enum SaveKind {
		CREATED,
		COPIED,
		UPDATED
	}
}
