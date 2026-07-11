package com.starskyxiii.collapsible_groups.compat.jei.editor;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.compat.jei.oreui.AppearanceDraft;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupDisplayName;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.core.GroupTheme;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;

import java.util.List;
import java.util.Objects;

/**
 * Creates group definitions from editor input while preserving metadata that
 * remains outside the current editor surface.
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
			preservedAppearance(existing).toIconIds(),
			preservedAppearance(existing).toTheme(),
			preservedPriority(existing),
			preservedExtra(existing)
		);
	}

	public static GroupDefinition create(
		String id,
		String fallbackName,
		boolean enabled,
		GroupFilter filter,
		GroupDefinition existing,
		AppearanceDraft appearance,
		int priority
	) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(fallbackName, "fallbackName");
		Objects.requireNonNull(filter, "filter");

		AppearanceDraft resolvedAppearance = appearanceOrExisting(appearance, existing);
		return new GroupDefinition(
			id,
			displayName(id, fallbackName, existing),
			enabled,
			filter,
			resolvedAppearance.toIconIds(),
			resolvedAppearance.toTheme(),
			priority,
			preservedExtra(existing)
		);
	}

	public static GroupDefinition createWithDisplayName(
		String id,
		GroupDisplayName displayName,
		boolean enabled,
		GroupFilter filter,
		GroupDefinition existing
	) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(filter, "filter");

		return new GroupDefinition(
			id,
			displayName,
			enabled,
			filter,
			preservedAppearance(existing).toIconIds(),
			preservedAppearance(existing).toTheme(),
			preservedPriority(existing),
			preservedExtra(existing)
		);
	}

	public static GroupDefinition createWithDisplayName(
		String id,
		GroupDisplayName displayName,
		boolean enabled,
		GroupFilter filter,
		GroupDefinition existing,
		AppearanceDraft appearance,
		int priority
	) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(displayName, "displayName");
		Objects.requireNonNull(filter, "filter");

		AppearanceDraft resolvedAppearance = appearanceOrExisting(appearance, existing);
		return new GroupDefinition(
			id,
			displayName,
			enabled,
			filter,
			resolvedAppearance.toIconIds(),
			resolvedAppearance.toTheme(),
			priority,
			preservedExtra(existing)
		);
	}

	private static GroupDisplayName displayName(String id, String fallbackName, GroupDefinition existing) {
		String key = existing != null && existing.id().equals(id)
			? existing.displayName().key()
			: GroupTranslationHelper.keyForGroupId(id);
		return new GroupDisplayName.Localized(key, fallbackName);
	}

	private static AppearanceDraft preservedAppearance(GroupDefinition existing) {
		return existing != null
			? AppearanceDraft.from(existing)
			: AppearanceDraft.fromIconIds(List.of(), GroupTheme.EMPTY);
	}

	private static int preservedPriority(GroupDefinition existing) {
		return existing != null ? existing.priority() : 0;
	}

	private static AppearanceDraft appearanceOrExisting(AppearanceDraft appearance, GroupDefinition existing) {
		return appearance != null ? appearance : preservedAppearance(existing);
	}

	private static JsonObject preservedExtra(GroupDefinition existing) {
		return existing != null ? existing.extra() : new JsonObject();
	}
}
