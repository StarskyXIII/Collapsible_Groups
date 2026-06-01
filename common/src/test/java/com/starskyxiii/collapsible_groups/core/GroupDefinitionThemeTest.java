package com.starskyxiii.collapsible_groups.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GroupDefinitionThemeTest {
	@Test
	void oldConstructorsUseEmptyTheme() {
		GroupDefinition group = new GroupDefinition(
			"old_constructor",
			"Old Constructor",
			true,
			Filters.itemId("minecraft:stone")
		);

		assertSame(GroupTheme.EMPTY, group.theme());
	}

	@Test
	void copyMethodsPreserveTheme() {
		GroupTheme theme = new GroupTheme("#FFAA00", "#11111111", "#22222222", "#33333333", "#44444444");
		GroupDefinition group = new GroupDefinition(
			"themed_group",
			"Themed Group",
			true,
			Filters.itemId("minecraft:stone"),
			List.of("minecraft:diamond"),
			theme
		);

		assertEquals(theme, group.withEnabled(false).theme());
		assertEquals(theme, group.withName("Renamed").theme());
		assertEquals(theme, group.withDisplayName(group.displayName()).theme());
		assertEquals(theme, group.withIconIds(List.of("minecraft:emerald")).theme());
		assertEquals(theme, group.withFilter(Filters.itemId("minecraft:dirt")).theme());
	}
}
