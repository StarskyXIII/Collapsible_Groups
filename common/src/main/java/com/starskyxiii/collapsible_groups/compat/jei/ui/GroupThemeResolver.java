package com.starskyxiii.collapsible_groups.compat.jei.ui;

import com.starskyxiii.collapsible_groups.compat.jei.runtime.GroupRegistry;
import com.starskyxiii.collapsible_groups.core.GroupTheme;
import com.starskyxiii.collapsible_groups.core.GroupThemeColors;
import com.starskyxiii.collapsible_groups.platform.Services;

/** Resolves runtime group theme colors for JEI overlay rendering. */
public final class GroupThemeResolver {
	private GroupThemeResolver() {}

	public static int groupNameColor(String groupId) {
		return GroupThemeColors.nameColor(themeFor(groupId), Services.CONFIG.groupNameColor());
	}

	public static int headerBackgroundColor(String groupId, boolean expanded) {
		return expanded
			? expandedHeaderBackgroundColor(groupId)
			: collapsedHeaderBackgroundColor(groupId);
	}

	public static int collapsedHeaderBackgroundColor(String groupId) {
		return GroupThemeColors.collapsedHeaderBackground(
			themeFor(groupId),
			Services.CONFIG.collapsedGroupBackgroundColor()
		);
	}

	public static int expandedHeaderBackgroundColor(String groupId) {
		return GroupThemeColors.expandedHeaderBackground(
			themeFor(groupId),
			Services.CONFIG.expandedGroupBackgroundColor()
		);
	}

	public static int expandedGroupBackgroundColor(String groupId) {
		return GroupThemeColors.expandedGroupBackground(
			themeFor(groupId),
			Services.CONFIG.expandedGroupBackgroundColor()
		);
	}

	public static int expandedGroupBorderColor(String groupId) {
		return GroupThemeColors.expandedGroupBorder(
			themeFor(groupId),
			Services.CONFIG.expandedGroupBorderColor()
		);
	}

	private static GroupTheme themeFor(String groupId) {
		return GroupRegistry.findById(groupId)
			.map(group -> group.theme())
			.orElse(GroupTheme.EMPTY);
	}
}
