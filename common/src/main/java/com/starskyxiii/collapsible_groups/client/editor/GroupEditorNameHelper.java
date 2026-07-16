package com.starskyxiii.collapsible_groups.client.editor;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;

final class GroupEditorNameHelper {
	private GroupEditorNameHelper() {}

	static String initialEditName(GroupDefinition existing) {
		if (existing == null) {
			return "";
		}
		String resolved = resolveClientName(existing);
		if (!resolved.isEmpty()) {
			return resolved;
		}
		String fallback = normalize(existing.displayName().fallback());
		if (!fallback.isEmpty()) {
			return fallback;
		}
		return existing.id();
	}

	private static String resolveClientName(GroupDefinition existing) {
		try {
			return normalize(existing.name());
		} catch (LinkageError | RuntimeException e) {
			return "";
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
