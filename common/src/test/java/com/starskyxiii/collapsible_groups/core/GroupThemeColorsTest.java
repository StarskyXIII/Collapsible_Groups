package com.starskyxiii.collapsible_groups.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupThemeColorsTest {
	@Test
	void emptyThemeUsesFallbacks() {
		assertEquals(0x00FFAA00, GroupThemeColors.nameColor(GroupTheme.EMPTY, 0x00FFAA00));
		assertEquals(0x24FFFFFF, GroupThemeColors.collapsedHeaderBackground(GroupTheme.EMPTY, 0x24FFFFFF));
		assertEquals(0x24FFFFFF, GroupThemeColors.expandedHeaderBackground(GroupTheme.EMPTY, 0x24FFFFFF));
		assertEquals(0x24FFFFFF, GroupThemeColors.expandedGroupBackground(GroupTheme.EMPTY, 0x24FFFFFF));
		assertEquals(0x66FFFFFF, GroupThemeColors.expandedGroupBorder(GroupTheme.EMPTY, 0x66FFFFFF));
	}

	@Test
	void explicitThemeFieldsOverrideFallbacks() {
		GroupTheme theme = new GroupTheme(
			"#FFD166",
			"#332D6CDF",
			"#334CAF50",
			"#224CAF50",
			"#884CAF50"
		);

		assertEquals(0x00FFD166, GroupThemeColors.nameColor(theme, 0x00FFAA00));
		assertEquals(0x332D6CDF, GroupThemeColors.collapsedHeaderBackground(theme, 0x24FFFFFF));
		assertEquals(0x334CAF50, GroupThemeColors.expandedHeaderBackground(theme, 0x24FFFFFF));
		assertEquals(0x224CAF50, GroupThemeColors.expandedGroupBackground(theme, 0x24FFFFFF));
		assertEquals(0x884CAF50, GroupThemeColors.expandedGroupBorder(theme, 0x66FFFFFF));
	}

	@Test
	void rgbBackgroundValuesKeepFallbackAlpha() {
		GroupTheme theme = new GroupTheme(null, "#2D6CDF", null, "#4CAF50", "#FFFFFF");

		assertEquals(0x332D6CDF, GroupThemeColors.collapsedHeaderBackground(theme, 0x33FFFFFF));
		assertEquals(0x224CAF50, GroupThemeColors.expandedGroupBackground(theme, 0x22FFFFFF));
		assertEquals(0x66FFFFFF, GroupThemeColors.expandedGroupBorder(theme, 0x66CCCCCC));
	}

	@Test
	void nameColorIgnoresAlpha() {
		GroupTheme theme = new GroupTheme("#80FFD166", null, null, null, null);

		assertEquals(0x00FFD166, GroupThemeColors.nameColor(theme, 0x00FFAA00));
	}

	@Test
	void expandedHeaderFallsBackToExpandedGroupBackgroundBeforeGlobalFallback() {
		GroupTheme theme = new GroupTheme(null, null, null, "#224CAF50", null);

		assertEquals(0x224CAF50, GroupThemeColors.expandedHeaderBackground(theme, 0x24FFFFFF));
	}

	@Test
	void invalidManuallyConstructedThemeValuesUseFallbacks() {
		GroupTheme theme = new GroupTheme("not-a-color", "#XYZ", null, "#12345", "#too-long-123456");

		assertEquals(0x00FFAA00, GroupThemeColors.nameColor(theme, 0x00FFAA00));
		assertEquals(0x24FFFFFF, GroupThemeColors.collapsedHeaderBackground(theme, 0x24FFFFFF));
		assertEquals(0x24FFFFFF, GroupThemeColors.expandedGroupBackground(theme, 0x24FFFFFF));
		assertEquals(0x66FFFFFF, GroupThemeColors.expandedGroupBorder(theme, 0x66FFFFFF));
	}
}
