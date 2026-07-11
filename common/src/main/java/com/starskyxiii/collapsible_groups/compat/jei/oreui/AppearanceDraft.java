package com.starskyxiii.collapsible_groups.compat.jei.oreui;

import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AppearanceDraft(
	String frontIconId,
	String backIconId,
	List<String> extraIconIds,
	String nameColor,
	String collapsedHeaderBackground,
	String expandedHeaderBackground,
	String expandedGroupBackground,
	String expandedGroupBorder
) {
	public AppearanceDraft {
		frontIconId = normalize(frontIconId);
		backIconId = normalize(backIconId);
		extraIconIds = copyNormalized(extraIconIds);
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

	public static AppearanceDraft fromIconIds(List<String> iconIds, GroupTheme theme) {
		Objects.requireNonNull(iconIds, "iconIds");
		GroupTheme resolvedTheme = theme != null ? theme : GroupTheme.EMPTY;
		String frontIconId = iconIds.isEmpty() ? null : iconIds.get(0);
		String backIconId = iconIds.size() < 2 ? null : iconIds.get(1);
		List<String> extraIconIds = iconIds.size() < 3 ? List.of() : iconIds.subList(2, iconIds.size());
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

	public List<String> toIconIds() {
		List<String> out = new ArrayList<>();
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

	public AppearanceDraft withFrontIconId(String iconId) {
		String normalized = normalize(iconId);
		if (normalized == null) {
			return clearFrontIcon();
		}
		return copy(normalized, backIconId, extraIconIds);
	}

	public AppearanceDraft withBackIconId(String iconId) {
		String normalized = normalize(iconId);
		if (normalized == null) {
			return clearBackIcon();
		}
		if (frontIconId == null) {
			return this;
		}
		return copy(frontIconId, normalized, extraIconIds);
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

	public AppearanceDraft withExtraIconIds(List<String> iconIds) {
		return copy(frontIconId, backIconId, iconIds);
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

	private AppearanceDraft copy(String frontIconId, String backIconId, List<String> extraIconIds) {
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
