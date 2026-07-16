package com.starskyxiii.collapsible_groups.client.editor.model;

import com.starskyxiii.collapsible_groups.group.GroupDefinition;
import com.starskyxiii.collapsible_groups.group.GroupIconDefinition;
import com.starskyxiii.collapsible_groups.group.GroupTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AppearanceDraft(
	GroupIconDefinition frontIconId,
	GroupIconDefinition backIconId,
	List<GroupIconDefinition> extraIconIds,
	String nameColor,
	String collapsedHeaderBackground,
	String expandedHeaderBackground,
	String expandedGroupBackground,
	String expandedGroupBorder
) {
	public AppearanceDraft {
		extraIconIds = extraIconIds == null ? List.of() : List.copyOf(extraIconIds);
		if (frontIconId == null) {
			backIconId = null;
			extraIconIds = List.of();
		}
		nameColor = normalize(nameColor);
		collapsedHeaderBackground = normalize(collapsedHeaderBackground);
		expandedHeaderBackground = normalize(expandedHeaderBackground);
		expandedGroupBackground = normalize(expandedGroupBackground);
		expandedGroupBorder = normalize(expandedGroupBorder);
	}

	public static AppearanceDraft from(GroupDefinition group) {
		Objects.requireNonNull(group, "group");
		return fromIconIds(group.iconIds(), group.theme());
	}

	public static AppearanceDraft fromIconIds(List<?> iconIds, GroupTheme theme) {
		Objects.requireNonNull(iconIds, "iconIds");
		List<GroupIconDefinition> typedIcons = iconIds.stream().map(AppearanceDraft::icon).toList();
		GroupTheme resolvedTheme = theme != null ? theme : GroupTheme.EMPTY;
		GroupIconDefinition frontIconId = typedIcons.isEmpty() ? null : typedIcons.get(0);
		GroupIconDefinition backIconId = typedIcons.size() < 2 ? null : typedIcons.get(1);
		List<GroupIconDefinition> extraIconIds = typedIcons.size() < 3 ? List.of() : typedIcons.subList(2, typedIcons.size());
		return new AppearanceDraft(
			frontIconId,
			backIconId,
			extraIconIds,
			resolvedTheme.nameColor(),
			resolvedTheme.collapsedHeaderBackground(),
			resolvedTheme.expandedHeaderBackground(),
			resolvedTheme.expandedGroupBackground(),
			resolvedTheme.expandedGroupBorder()
		);
	}

	public List<GroupIconDefinition> toIconIds() {
		List<GroupIconDefinition> out = new ArrayList<>();
		if (frontIconId != null) {
			out.add(frontIconId);
		}
		if (backIconId != null) {
			out.add(backIconId);
		}
		out.addAll(extraIconIds);
		return List.copyOf(out);
	}

	public GroupTheme toTheme() {
		if (nameColor == null
			&& collapsedHeaderBackground == null
			&& expandedHeaderBackground == null
			&& expandedGroupBackground == null
			&& expandedGroupBorder == null) {
			return GroupTheme.EMPTY;
		}
		return new GroupTheme(
			nameColor,
			collapsedHeaderBackground,
			expandedHeaderBackground,
			expandedGroupBackground,
			expandedGroupBorder
		);
	}

	public AppearanceDraft withFrontIconId(GroupIconDefinition iconId) {
		if (iconId == null) {
			return clearFrontIcon();
		}
		return copy(iconId, backIconId, extraIconIds);
	}

	public AppearanceDraft withFrontIconId(String itemId) {
		return withFrontIconId(itemId == null || itemId.isBlank() ? null : GroupIconDefinition.item(itemId));
	}

	public AppearanceDraft withBackIconId(GroupIconDefinition iconId) {
		if (iconId == null) {
			return clearBackIcon();
		}
		if (frontIconId == null) {
			return this;
		}
		return copy(frontIconId, iconId, extraIconIds);
	}

	public AppearanceDraft withBackIconId(String itemId) {
		return withBackIconId(itemId == null || itemId.isBlank() ? null : GroupIconDefinition.item(itemId));
	}

	public AppearanceDraft clearFrontIcon() {
		return copy(null, null, List.of());
	}

	public AppearanceDraft clearBackIcon() {
		return backIconId == null ? this : copy(frontIconId, null, extraIconIds);
	}

	public AppearanceDraft swapIcons() {
		if (frontIconId == null || backIconId == null) {
			return this;
		}
		return copy(backIconId, frontIconId, extraIconIds);
	}

	public AppearanceDraft withExtraIconIds(List<?> iconIds) {
		return copy(frontIconId, backIconId, iconIds.stream().map(AppearanceDraft::icon).toList());
	}

	/** Back/secondary icons require a front icon because the JSON array has no sparse slot representation. */
	public boolean canEditBackIcon() {
		return frontIconId != null;
	}

	public AppearanceDraft withNameColor(String color) {
		return copyColors(color, collapsedHeaderBackground, expandedHeaderBackground, expandedGroupBackground, expandedGroupBorder);
	}

	public AppearanceDraft withCollapsedHeaderBackground(String color) {
		return copyColors(nameColor, color, expandedHeaderBackground, expandedGroupBackground, expandedGroupBorder);
	}

	public AppearanceDraft withExpandedHeaderBackground(String color) {
		return copyColors(nameColor, collapsedHeaderBackground, color, expandedGroupBackground, expandedGroupBorder);
	}

	public AppearanceDraft withExpandedGroupBackground(String color) {
		return copyColors(nameColor, collapsedHeaderBackground, expandedHeaderBackground, color, expandedGroupBorder);
	}

	public AppearanceDraft withExpandedGroupBorder(String color) {
		return copyColors(nameColor, collapsedHeaderBackground, expandedHeaderBackground, expandedGroupBackground, color);
	}

	private AppearanceDraft copy(GroupIconDefinition frontIconId, GroupIconDefinition backIconId,
		List<GroupIconDefinition> extraIconIds) {
		return new AppearanceDraft(
			frontIconId,
			backIconId,
			extraIconIds,
			nameColor,
			collapsedHeaderBackground,
			expandedHeaderBackground,
			expandedGroupBackground,
			expandedGroupBorder
		);
	}

	private AppearanceDraft copyColors(
		String nameColor,
		String collapsedHeaderBackground,
		String expandedHeaderBackground,
		String expandedGroupBackground,
		String expandedGroupBorder
	) {
		return new AppearanceDraft(
			frontIconId,
			backIconId,
			extraIconIds,
			nameColor,
			collapsedHeaderBackground,
			expandedHeaderBackground,
			expandedGroupBackground,
			expandedGroupBorder
		);
	}

	private static String normalize(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static GroupIconDefinition icon(Object value) {
		return switch (value) {
			case GroupIconDefinition typed -> typed;
			case String itemId -> GroupIconDefinition.item(itemId);
			case null -> throw new NullPointerException("iconIds contains null");
			default -> throw new IllegalArgumentException("Unsupported icon value: " + value);
		};
	}
}
