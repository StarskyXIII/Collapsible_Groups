package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable definition of a collapsible ingredient group: ID, display name, enabled state, filter, and icons.
 *
 * <p>{@link #displayName()} is the <b>authoritative</b> data source for persistence,
 * dump, and editor operations.  {@link #name()} is a convenience accessor that returns
 * the resolved display text for the current language (overlay ??Minecraft lang ??fallback).
 */
public final class GroupDefinition {
	private final String id;
	private final GroupDisplayName displayName;
	private final boolean enabled;
	private final GroupFilter filter;
	private final List<String> iconIds;
	private final CompiledFilter compiledFilter;

	public GroupDefinition(String id, String name, boolean enabled, GroupFilter filter) {
		this(id, name, enabled, filter, List.of());
	}

	public GroupDefinition(String id, String name, boolean enabled, GroupFilter filter, List<String> iconIds) {
		this(
			Objects.requireNonNull(id, "id"),
			new GroupDisplayName.Localized(GroupTranslationHelper.keyForGroupId(id), Objects.requireNonNull(name, "name")),
			enabled,
			filter,
			iconIds
		);
	}

	public GroupDefinition(String id, GroupDisplayName displayName, boolean enabled, GroupFilter filter) {
		this(id, displayName, enabled, filter, List.of());
	}

	public GroupDefinition(String id, GroupDisplayName displayName, boolean enabled, GroupFilter filter, List<String> iconIds) {
		this.id = Objects.requireNonNull(id, "id");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.enabled = enabled;
		this.filter = GroupFilterNormalizer.normalize(Objects.requireNonNull(filter, "filter"));
		List<String> validationErrors = GroupFilterValidator.validate(this.filter);
		if (!validationErrors.isEmpty()) {
			throw new IllegalArgumentException("Invalid group filter: " + String.join("; ", validationErrors));
		}
		this.iconIds = List.copyOf(Objects.requireNonNull(iconIds, "iconIds"));
		this.compiledFilter = CompiledFilter.compile(this.filter);
	}

	public static GroupDefinition of(String id, String name, GroupFilter filter) {
		return new GroupDefinition(id, name, true, filter);
	}

	public String id() {
		return id;
	}

	/**
	 * Returns the resolved display text for the current language.
	 * Resolution order: overlay ??Minecraft lang ??fallback.
	 *
	 * <p><b>Do not use for persistence or dump.</b>  Use {@link #displayName()} instead.
	 */
	public String name() {
		return displayName.resolveClientDisplayText();
	}

	/**
	 * Authoritative data source for all persistence, dump, editor, and overlay operations.
	 */
	public GroupDisplayName displayName() {
		return displayName;
	}

	public boolean enabled() {
		return enabled;
	}

	public GroupFilter filter() {
		return filter;
	}

	public List<String> iconIds() {
		return iconIds;
	}

	public CompiledFilter compiledFilter() {
		return compiledFilter;
	}

	public boolean matchesIgnoringEnabled(ItemStack stack) {
		return compiledFilter.matches(new ItemStackIngredientView(stack));
	}

	public boolean matches(ItemStack stack) {
		return enabled && matchesIgnoringEnabled(stack);
	}

	public boolean hasItemFilters() {
		return hasFilterForType("item");
	}

	public boolean hasFluidFilters() {
		return hasFilterForType("fluid");
	}

	public boolean hasGenericFilters() {
		return hasAtomicNodeMatching(filter, node -> {
			String type = atomicType(node);
			return type != null && !"item".equals(type) && !"fluid".equals(type);
		});
	}

	public GroupDefinition withEnabled(boolean enabled) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds);
	}

	/** Returns a copy with the given fallback name; the translation key is auto-generated from the group ID. */
	public GroupDefinition withName(String fallbackName) {
		return withDisplayName(new GroupDisplayName.Localized(
			GroupTranslationHelper.keyForGroupId(id),
			fallbackName
		));
	}

	public GroupDefinition withDisplayName(GroupDisplayName displayName) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds);
	}

	public GroupDefinition withIconIds(List<String> iconIds) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds);
	}

	public GroupDefinition withFilter(GroupFilter filter) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds);
	}

	public boolean isStructurallyEditable() {
		return GroupFilterEditorDraft.decode(filter).structurallyEditable();
	}

	private boolean hasFilterForType(String type) {
		return hasAtomicNodeMatching(filter, node -> type.equals(atomicType(node)));
	}

	private static boolean hasAtomicNodeMatching(GroupFilter filter, Predicate<GroupFilter> test) {
		return switch (filter) {
			case GroupFilter.Any any -> any.children().stream().anyMatch(child -> hasAtomicNodeMatching(child, test));
			case GroupFilter.All all -> all.children().stream().anyMatch(child -> hasAtomicNodeMatching(child, test));
			case GroupFilter.Not not -> hasAtomicNodeMatching(not.child(), test);
			default -> test.test(filter);
		};
	}

	private static String atomicType(GroupFilter node) {
		return switch (node) {
			case GroupFilter.Id id -> id.ingredientType();
			case GroupFilter.Tag tag -> tag.ingredientType();
			case GroupFilter.BlockTag ignored -> "item";
			case GroupFilter.ItemPathStartsWith ignored -> "item";
			case GroupFilter.ItemPathEndsWith ignored -> "item";
			case GroupFilter.Namespace namespace -> namespace.ingredientType();
			case GroupFilter.ExactStack ignored -> "item";
			case GroupFilter.HasComponent ignored -> "item";
			case GroupFilter.ComponentPath ignored -> "item";
			default -> null;
		};
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof GroupDefinition other)) return false;
		return enabled == other.enabled
			&& Objects.equals(id, other.id)
			&& Objects.equals(displayName, other.displayName)
			&& Objects.equals(filter, other.filter)
			&& Objects.equals(iconIds, other.iconIds);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, displayName, enabled, filter, iconIds);
	}

	@Override
	public String toString() {
		return "GroupDefinition[id=" + id
			+ ", displayName=" + displayName
			+ ", enabled=" + enabled
			+ ", filter=" + filter
			+ ", iconIds=" + iconIds + ']';
	}
}
