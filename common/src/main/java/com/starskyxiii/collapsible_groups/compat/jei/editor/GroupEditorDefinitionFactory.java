package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupDisplayName;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;

import java.util.List;
import java.util.Objects;

/**
 * Creates group definitions from editor input while preserving metadata that
 * the current editor UI cannot edit directly yet.
 */
public final class GroupEditorDefinitionFactory {
	private GroupEditorDefinitionFactory() {}

	public static GroupDefinition create(
		String id,
		String fallbackName,
		boolean enabled,
		GroupFilter filter,
		GroupDefinition existing
	) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(fallbackName, "fallbackName");
		Objects.requireNonNull(filter, "filter");

		return new GroupDefinition(
			id,
			displayName(id, fallbackName, existing),
			enabled,
			filter,
			preservedIconIds(existing)
		);
	}

	private static GroupDisplayName displayName(String id, String fallbackName, GroupDefinition existing) {
		String key = existing != null && existing.id().equals(id)
			? existing.displayName().key()
			: GroupTranslationHelper.keyForGroupId(id);
		return new GroupDisplayName.Localized(key, fallbackName);
	}

	private static List<String> preservedIconIds(GroupDefinition existing) {
		return existing != null ? existing.iconIds() : List.of();
	}
}
