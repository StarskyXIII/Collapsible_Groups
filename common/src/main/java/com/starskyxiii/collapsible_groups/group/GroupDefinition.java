package com.starskyxiii.collapsible_groups.group;

import com.starskyxiii.collapsible_groups.group.filter.CompiledFilter;
import com.starskyxiii.collapsible_groups.group.filter.FilterNodeCapabilities;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterEditorDraft;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterNormalizer;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilterValidator;
import com.starskyxiii.collapsible_groups.ingredient.ItemStackIngredientView;
import com.starskyxiii.collapsible_groups.internal.query.CompiledGroupQuery;

import com.google.gson.JsonObject;
import com.starskyxiii.collapsible_groups.internal.version.data.ItemDataPayload;
import com.starskyxiii.collapsible_groups.i18n.GroupTranslationHelper;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of a collapsible ingredient group: ID, display name, enabled state, filter, icons, theme, priority, and extra metadata.
 *
 * <p>{@link #displayName()} is the <b>authoritative</b> data source for persistence,
 * dump, and editor operations.  {@link #name()} is a convenience accessor that returns
 * the resolved display text for the current language (overlay ??Minecraft lang ??fallback).
 */
public final class GroupDefinition {
	private final GroupDocumentFormat documentFormat;
	private final JsonObject rawDocument;
	private final String id;
	private final GroupDisplayName displayName;
	private final boolean enabled;
	private final GroupFilter filter;
	private final List<GroupIconDefinition> iconIds;
	private final GroupTheme theme;
	private final int priority;
	private final JsonObject extra;
	private final CompiledGroupQuery query;

	public GroupDefinition(String id, String name, boolean enabled, GroupFilter filter) {
		this(id, name, enabled, filter, List.of());
	}

	public GroupDefinition(String id, String name, boolean enabled, GroupFilter filter, List<?> iconIds) {
		this(id, name, enabled, filter, iconIds, GroupTheme.EMPTY);
	}

	public GroupDefinition(String id, String name, boolean enabled, GroupFilter filter, List<?> iconIds, GroupTheme theme) {
		this(id, name, enabled, filter, iconIds, theme, 0, new JsonObject());
	}

	public GroupDefinition(
		String id,
		String name,
		boolean enabled,
		GroupFilter filter,
		List<?> iconIds,
		GroupTheme theme,
		int priority,
		JsonObject extra
	) {
		this(
			Objects.requireNonNull(id, "id"),
			new GroupDisplayName.Localized(GroupTranslationHelper.keyForGroupId(id), Objects.requireNonNull(name, "name")),
			enabled,
			filter,
			iconIds,
			theme,
			priority,
			extra
		);
	}

	public GroupDefinition(String id, GroupDisplayName displayName, boolean enabled, GroupFilter filter) {
		this(id, displayName, enabled, filter, List.of());
	}

	public GroupDefinition(String id, GroupDisplayName displayName, boolean enabled, GroupFilter filter, List<?> iconIds) {
		this(id, displayName, enabled, filter, iconIds, GroupTheme.EMPTY);
	}

	public GroupDefinition(
		String id,
		GroupDisplayName displayName,
		boolean enabled,
		GroupFilter filter,
		List<?> iconIds,
		GroupTheme theme
	) {
		this(id, displayName, enabled, filter, iconIds, theme, 0, new JsonObject());
	}

	public GroupDefinition(
		String id,
		GroupDisplayName displayName,
		boolean enabled,
		GroupFilter filter,
		List<?> iconIds,
		GroupTheme theme,
		int priority,
		JsonObject extra
	) {
        this(id, displayName, enabled, filter, iconIds, theme, priority, extra,
            containsTypedData(filter) ? GroupDocumentFormat.V1 : GroupDocumentFormat.LEGACY, null);
    }

    public GroupDefinition(String id, GroupDisplayName displayName, boolean enabled, GroupFilter filter,
        List<?> iconIds, GroupTheme theme, int priority, JsonObject extra,
        GroupDocumentFormat documentFormat, JsonObject rawDocument) {
        this.documentFormat = Objects.requireNonNull(documentFormat, "documentFormat");
        this.rawDocument = rawDocument == null ? null : rawDocument.deepCopy();
        if (documentFormat == GroupDocumentFormat.UNSUPPORTED && rawDocument == null) {
            throw new IllegalArgumentException("Unsupported documents require their original JSON");
        }
		this.id = Objects.requireNonNull(id, "id");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.enabled = enabled;
		GroupFilter sourceFilter = documentFormat == GroupDocumentFormat.UNSUPPORTED
            ? new GroupFilter.Unsupported(this.rawDocument, "document") : Objects.requireNonNull(filter, "filter");
		this.filter = GroupFilterNormalizer.normalize(documentFormat == GroupDocumentFormat.V1 ? typedExactData(sourceFilter) : sourceFilter);
		List<String> validationErrors = GroupFilterValidator.validate(this.filter);
		if (!validationErrors.isEmpty()) {
			throw new IllegalArgumentException("Invalid group filter: " + String.join("; ", validationErrors));
		}
		this.iconIds = normalizeIcons(iconIds);
		this.theme = Objects.requireNonNullElse(theme, GroupTheme.EMPTY);
		this.priority = priority;
		this.extra = copyExtra(extra);
		this.query = CompiledGroupQuery.compile(this.filter, sourceFilter);
	}

    private static GroupFilter typedExactData(GroupFilter filter) {
        return switch (filter) {
            case GroupFilter.Any any -> new GroupFilter.Any(any.children().stream().map(GroupDefinition::typedExactData).toList());
            case GroupFilter.All all -> new GroupFilter.All(all.children().stream().map(GroupDefinition::typedExactData).toList());
            case GroupFilter.Not not -> new GroupFilter.Not(typedExactData(not.child()));
            case GroupFilter.ExactStack exact -> exact.payload() == null
                ? new GroupFilter.ExactStack(new ItemDataPayload(ItemDataPayload.ITEM_COMPONENTS, ItemDataPayload.parseLiteral(exact.encodedStack()))) : exact;
            default -> filter;
        };
    }

    public GroupDocumentFormat documentFormat() { return documentFormat; }

    public JsonObject rawDocument() { return rawDocument == null ? null : rawDocument.deepCopy(); }

    private static boolean containsTypedData(GroupFilter filter) {
        return switch (filter) {
            case GroupFilter.Any any -> any.children().stream().anyMatch(GroupDefinition::containsTypedData);
            case GroupFilter.All all -> all.children().stream().anyMatch(GroupDefinition::containsTypedData);
            case GroupFilter.Not not -> containsTypedData(not.child());
            case GroupFilter.ExactStack stack -> stack.payload() != null;
            case GroupFilter.HasComponent component -> component.payload() != null;
            case GroupFilter.ComponentPath path -> path.payload() != null;
            default -> false;
        };
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

	public List<GroupIconDefinition> iconIds() {
		return iconIds;
	}

	public GroupTheme theme() {
		return theme;
	}

	public int priority() {
		return priority;
	}

	public JsonObject extra() {
		return extra.deepCopy();
	}

	public boolean hasExtra() {
		return extra.size() > 0;
	}

	public CompiledFilter compiledFilter() {
		return query.evaluator();
	}

	public CompiledGroupQuery query() {
		return query;
	}

	public boolean hasUnavailableFilter() {
		return documentFormat == GroupDocumentFormat.UNSUPPORTED || FilterNodeCapabilities.containsUnavailable(filter);
	}

	public boolean matchesIgnoringEnabled(ItemStack stack) {
		return query.matches(new ItemStackIngredientView(stack));
	}

	public boolean matches(ItemStack stack) {
		return enabled && matchesIgnoringEnabled(stack);
	}

	public boolean hasItemFilters() {
		return query.plan().mayMatchItems();
	}

	public boolean hasFluidFilters() {
		return query.plan().mayMatchFluids();
	}

	public boolean hasGenericFilters() {
		return query.plan().mayMatchGeneric();
	}

	public GroupDefinition withEnabled(boolean enabled) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds, theme, priority, extra, documentFormat, rawDocument);
	}

	/** Returns a copy with the given fallback name; the translation key is auto-generated from the group ID. */
	public GroupDefinition withName(String fallbackName) {
		return withDisplayName(new GroupDisplayName.Localized(
			GroupTranslationHelper.keyForGroupId(id),
			fallbackName
		));
	}

	public GroupDefinition withDisplayName(GroupDisplayName displayName) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds, theme, priority, extra, documentFormat, rawDocument);
	}

	public GroupDefinition withIconIds(List<?> iconIds) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds, theme, priority, extra, documentFormat, rawDocument);
	}

	public GroupDefinition withFilter(GroupFilter filter) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds, theme, priority, extra, documentFormat, rawDocument);
	}

	public GroupDefinition withTheme(GroupTheme theme) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds, theme, priority, extra, documentFormat, rawDocument);
	}

	public GroupDefinition withPriority(int priority) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds, theme, priority, extra, documentFormat, rawDocument);
	}

	public GroupDefinition withExtra(JsonObject extra) {
		return new GroupDefinition(id, displayName, enabled, filter, iconIds, theme, priority, extra, documentFormat, rawDocument);
	}

	public boolean isStructurallyEditable() {
		return !hasUnavailableFilter() && GroupFilterEditorDraft.decode(filter).structurallyEditable();
	}

	private static JsonObject copyExtra(JsonObject extra) {
		return extra == null ? new JsonObject() : extra.deepCopy();
	}

	private static List<GroupIconDefinition> normalizeIcons(List<?> values) {
		Objects.requireNonNull(values, "iconIds");
		return values.stream().map(value -> switch (value) {
			case GroupIconDefinition icon -> icon;
			case String itemId -> GroupIconDefinition.item(itemId);
			case null -> throw new NullPointerException("iconIds contains null");
			default -> throw new IllegalArgumentException("Unsupported icon value: " + value);
		}).toList();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof GroupDefinition other)) return false;
		return enabled == other.enabled
			&& priority == other.priority
			&& Objects.equals(id, other.id)
			&& Objects.equals(displayName, other.displayName)
			&& Objects.equals(filter, other.filter)
			&& Objects.equals(iconIds, other.iconIds)
			&& Objects.equals(theme, other.theme)
			&& Objects.equals(extra, other.extra)
            && documentFormat == other.documentFormat && Objects.equals(rawDocument, other.rawDocument);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, displayName, enabled, filter, iconIds, theme, priority, extra, documentFormat, rawDocument);
	}

	@Override
	public String toString() {
		return "GroupDefinition[id=" + id
			+ ", displayName=" + displayName
			+ ", enabled=" + enabled
			+ ", filter=" + filter
			+ ", iconIds=" + iconIds
			+ ", theme=" + theme
			+ ", priority=" + priority
			+ ", extra=" + extra + ']';
	}
}
