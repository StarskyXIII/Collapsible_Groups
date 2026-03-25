package com.starskyxiii.collapsible_groups.defaults;

import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupDisplayName;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;

import java.util.List;

/**
 * Provides a set of built-in default groups to be loaded at startup.
 *
 * <p>Implementations may live in the common module or in a loader-specific module.
 * Providers that require loader APIs (e.g. {@code ModList.get().isLoaded()} on NeoForge)
 * should be placed in the corresponding loader module.
 *
 * <p>Groups returned here act as in-memory defaults. If a JSON file with the
 * same ID already exists in the user's config folder, the JSON version wins
 * (allowing users to customise or disable defaults without losing them on reload).
 */
public interface DefaultGroupProvider {

	/**
	 * Defines provider load order.
	 * Lower values load first; ties are broken by implementation class name.
	 */
	default int priority() {
		return 0;
	}

	/**
	 * Returns the groups this provider contributes.
	 * Return an empty list when the provider is disabled (e.g. config toggle off,
	 * required mod not installed).
	 */
	List<GroupDefinition> getGroups();

	// -----------------------------------------------------------------------
	// Static factory helpers available to all provider implementations.
	// These are thin sugar wrappers over the GroupFilter/Filters model.
	// -----------------------------------------------------------------------

	static GroupDefinition group(String id, String fallbackEnglishName, GroupFilter... filters) {
		return new GroupDefinition(
			id,
			new GroupDisplayName.Localized(GroupTranslationHelper.keyForGroupId(id), fallbackEnglishName),
			true,
			composeFilter(filters)
		);
	}

	static GroupFilter item(String itemId) {
		return Filters.itemId(itemId);
	}

	static GroupFilter tag(String tagId) {
		return Filters.itemTag(tagId);
	}

	/** Like {@link #tag} but only matches items from the given registry namespace. */
	static GroupFilter tagNs(String tagId, String namespace) {
		return Filters.all(Filters.itemTag(tagId), Filters.itemNamespace(namespace));
	}

	/** Matches items that belong to ALL of the listed tags simultaneously (AND logic). */
	static GroupFilter tagIntersect(String... tagIds) {
		return Filters.all(java.util.Arrays.stream(tagIds).map(Filters::itemTag).toArray(GroupFilter[]::new));
	}

	/** Like {@link #tagIntersect} but also restricts to items from the given registry namespace. */
	static GroupFilter tagIntersectNs(String namespace, String... tagIds) {
		GroupFilter[] tagChildren = java.util.Arrays.stream(tagIds).map(Filters::itemTag).toArray(GroupFilter[]::new);
		GroupFilter[] children = java.util.Arrays.copyOf(tagChildren, tagChildren.length + 1);
		children[children.length - 1] = Filters.itemNamespace(namespace);
		return Filters.all(children);
	}

	static GroupFilter genericTag(String type, String tagId) {
		return Filters.genericTag(type, tagId);
	}

	static GroupFilter component(String componentTypeId, String encodedValue) {
		return Filters.itemComponent(componentTypeId, encodedValue);
	}

	/**
	 * Creates an {@code all(itemId(...), componentPath(...))} filter suitable for
	 * built-in groups that need a pre-filter by item ID for query-planning efficiency.
	 */
	static GroupFilter itemWithComponentPath(String itemId, String componentTypeId, String path, String expectedValue) {
		return Filters.all(Filters.itemId(itemId), Filters.itemComponentPath(componentTypeId, path, expectedValue));
	}

	private static GroupFilter composeFilter(GroupFilter[] filters) {
		if (filters.length == 0) {
			throw new IllegalArgumentException("Built-in groups must contain at least one filter");
		}
		if (filters.length == 1) {
			return filters[0];
		}
		return Filters.any(filters);
	}
}
