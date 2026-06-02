package com.starskyxiii.collapsible_groups.core;

import com.starskyxiii.collapsible_groups.config.ColorConfigParser;

/**
 * Resolves optional group theme color overrides against caller-provided
 * fallback colors.
 */
public final class GroupThemeColors {
	private GroupThemeColors() {}

	public static int nameColor(GroupTheme theme, int fallbackRgb) {
		GroupTheme resolved = nonNull(theme);
		return ColorConfigParser.parseRgb(resolved.nameColor(), fallbackRgb);
	}

	public static int collapsedHeaderBackground(GroupTheme theme, int fallbackArgb) {
		GroupTheme resolved = nonNull(theme);
		return ColorConfigParser.parseArgb(resolved.collapsedHeaderBackground(), fallbackArgb);
	}

	public static int expandedHeaderBackground(GroupTheme theme, int fallbackArgb) {
		GroupTheme resolved = nonNull(theme);
		int groupBackground = expandedGroupBackground(resolved, fallbackArgb);
		return ColorConfigParser.parseArgb(resolved.expandedHeaderBackground(), groupBackground);
	}

	public static int expandedGroupBackground(GroupTheme theme, int fallbackArgb) {
		GroupTheme resolved = nonNull(theme);
		return ColorConfigParser.parseArgb(resolved.expandedGroupBackground(), fallbackArgb);
	}

	public static int expandedGroupBorder(GroupTheme theme, int fallbackArgb) {
		GroupTheme resolved = nonNull(theme);
		return ColorConfigParser.parseArgb(resolved.expandedGroupBorder(), fallbackArgb);
	}

	private static GroupTheme nonNull(GroupTheme theme) {
		return theme != null ? theme : GroupTheme.EMPTY;
	}
}
