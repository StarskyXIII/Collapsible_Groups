package com.starskyxiii.collapsible_groups.core;

/**
 * Optional per-group visual overrides.
 *
 * <p>An empty theme means "use the global configuration or the current
 * built-in fallback". Values are validated user-facing color strings, not
 * pre-parsed integers, so RGB values can inherit the correct fallback alpha
 * at the point where they are applied.
 */
public record GroupTheme(
	String nameColor,
	String collapsedHeaderBackground,
	String expandedHeaderBackground,
	String expandedGroupBackground,
	String expandedGroupBorder
) {
	public static final GroupTheme EMPTY = new GroupTheme(null, null, null, null, null);

	public GroupTheme {
		nameColor = normalize(nameColor);
		collapsedHeaderBackground = normalize(collapsedHeaderBackground);
		expandedHeaderBackground = normalize(expandedHeaderBackground);
		expandedGroupBackground = normalize(expandedGroupBackground);
		expandedGroupBorder = normalize(expandedGroupBorder);
	}

	public boolean isEmpty() {
		return nameColor == null
			&& collapsedHeaderBackground == null
			&& expandedHeaderBackground == null
			&& expandedGroupBackground == null
			&& expandedGroupBorder == null;
	}

	private static String normalize(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
