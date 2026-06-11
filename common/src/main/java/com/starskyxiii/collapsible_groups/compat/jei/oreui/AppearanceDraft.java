package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AppearanceDraft(
	String bottomFrontIconId,
	String topBackIconId,
	List<String> extraIconIds,
	String nameColor,
	String collapsedHeaderBackground,
	String expandedHeaderBackground,
	String expandedGroupBackground,
	String expandedGroupBorder
) {
	public AppearanceDraft {
		bottomFrontIconId = normalize(bottomFrontIconId);
		topBackIconId = normalize(topBackIconId);
		if (bottomFrontIconId == null && topBackIconId != null) {
			bottomFrontIconId = topBackIconId;
			topBackIconId = null;
		}
		extraIconIds = copyNormalized(extraIconIds);
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

	public static AppearanceDraft fromIconIds(List<String> iconIds, GroupTheme theme) {
		Objects.requireNonNull(iconIds, "iconIds");
		GroupTheme resolvedTheme = theme != null ? theme : GroupTheme.EMPTY;
		String bottomFrontIconId = iconIds.isEmpty() ? null : iconIds.get(0);
		String topBackIconId = iconIds.size() < 2 ? null : iconIds.get(1);
		List<String> extraIconIds = iconIds.size() < 3 ? List.of() : iconIds.subList(2, iconIds.size());
		return new AppearanceDraft(
			bottomFrontIconId,
			topBackIconId,
			extraIconIds,
			resolvedTheme.nameColor(),
			resolvedTheme.collapsedHeaderBackground(),
			resolvedTheme.expandedHeaderBackground(),
			resolvedTheme.expandedGroupBackground(),
			resolvedTheme.expandedGroupBorder()
		);
	}

	public List<String> toIconIds() {
		List<String> out = new ArrayList<>();
		if (bottomFrontIconId != null) {
			out.add(bottomFrontIconId);
		}
		if (topBackIconId != null) {
			out.add(topBackIconId);
		}
		out.addAll(extraIconIds);
		return List.copyOf(out);
	}

	public GroupTheme toTheme() {
		return new GroupTheme(
			nameColor,
			collapsedHeaderBackground,
			expandedHeaderBackground,
			expandedGroupBackground,
			expandedGroupBorder
		);
	}

	public AppearanceDraft withBottomFrontIconId(String iconId) {
		String normalized = normalize(iconId);
		if (normalized == null) {
			return clearBottomFrontIcon();
		}
		return copy(normalized, topBackIconId, extraIconIds);
	}

	public AppearanceDraft withTopBackIconId(String iconId) {
		String normalized = normalize(iconId);
		if (normalized == null) {
			return clearTopBackIcon();
		}
		if (bottomFrontIconId == null) {
			return copy(normalized, null, extraIconIds);
		}
		return copy(bottomFrontIconId, normalized, extraIconIds);
	}

	public AppearanceDraft clearBottomFrontIcon() {
		if (topBackIconId != null) {
			return copy(topBackIconId, null, extraIconIds);
		}
		return copy(null, null, extraIconIds);
	}

	public AppearanceDraft clearTopBackIcon() {
		return topBackIconId == null ? this : copy(bottomFrontIconId, null, extraIconIds);
	}

	public AppearanceDraft swapIcons() {
		if (bottomFrontIconId == null || topBackIconId == null) {
			return this;
		}
		return copy(topBackIconId, bottomFrontIconId, extraIconIds);
	}

	public AppearanceDraft withExtraIconIds(List<String> iconIds) {
		return copy(bottomFrontIconId, topBackIconId, iconIds);
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

	private AppearanceDraft copy(String bottomFrontIconId, String topBackIconId, List<String> extraIconIds) {
		return new AppearanceDraft(
			bottomFrontIconId,
			topBackIconId,
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
			bottomFrontIconId,
			topBackIconId,
			extraIconIds,
			nameColor,
			collapsedHeaderBackground,
			expandedHeaderBackground,
			expandedGroupBackground,
			expandedGroupBorder
		);
	}

	private static List<String> copyNormalized(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>(values.size());
		for (String value : values) {
			String normalized = normalize(value);
			if (normalized != null) {
				out.add(normalized);
			}
		}
		return List.copyOf(out);
	}

	private static String normalize(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
