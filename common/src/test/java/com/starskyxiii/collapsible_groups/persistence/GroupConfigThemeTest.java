package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import com.starskyxiii.collapsible_groups.core.GroupTheme;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GroupConfigThemeTest {
	@Test
	void fromJsonUsesEmptyThemeWhenMissing() {
		GroupDefinition group = GroupConfig.fromJson("""
			{
				"id": "old_group",
				"name": "Old Group",
				"filter": {
					"type": "item",
					"id": "minecraft:stone"
				}
			}
			""");

		assertNotNull(group);
		assertSame(GroupTheme.EMPTY, group.theme());
	}

	@Test
	void fromJsonReadsFullTheme() {
		GroupDefinition group = GroupConfig.fromJson("""
			{
				"id": "themed_group",
				"name": "Themed Group",
				"theme": {
					"name_color": "#FFD166",
					"collapsed_header_background": "#332D6CDF",
					"expanded_header_background": "#334CAF50",
					"expanded_group_background": "#224CAF50",
					"expanded_group_border": "#884CAF50"
				},
				"filter": {
					"type": "item",
					"id": "minecraft:stone"
				}
			}
			""");

		assertNotNull(group);
		assertEquals(new GroupTheme(
			"#FFD166",
			"#332D6CDF",
			"#334CAF50",
			"#224CAF50",
			"#884CAF50"
		), group.theme());
	}

	@Test
	void fromJsonIgnoresInvalidThemeColors() {
		GroupDefinition group = GroupConfig.fromJson("""
			{
				"id": "partial_group",
				"name": "Partial Group",
				"theme": {
					"name_color": "#FFAA00",
					"collapsed_header_background": "#XYZ",
					"expanded_group_border": 123456
				},
				"filter": {
					"type": "item",
					"id": "minecraft:stone"
				}
			}
			""");

		assertNotNull(group);
		assertEquals(new GroupTheme("#FFAA00", null, null, null, null), group.theme());
	}

	@Test
	void fromJsonIgnoresNonObjectTheme() {
		GroupDefinition group = GroupConfig.fromJson("""
			{
				"id": "bad_theme",
				"name": "Bad Theme",
				"theme": "not an object",
				"filter": {
					"type": "item",
					"id": "minecraft:stone"
				}
			}
			""");

		assertNotNull(group);
		assertSame(GroupTheme.EMPTY, group.theme());
	}

	@Test
	void toJsonOmitsEmptyTheme() {
		GroupDefinition group = new GroupDefinition(
			"no_theme",
			"No Theme",
			true,
			Filters.itemId("minecraft:stone"),
			List.of(),
			GroupTheme.EMPTY
		);

		JsonObject json = JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject();

		assertFalse(json.has("theme"));
	}

	@Test
	void toJsonWritesOnlyPresentThemeFields() {
		GroupDefinition group = new GroupDefinition(
			"partial_theme",
			"Partial Theme",
			true,
			Filters.itemId("minecraft:stone"),
			List.of(),
			new GroupTheme("#FFAA00", null, null, null, "#66FFFFFF")
		);

		JsonObject json = JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject();
		JsonObject theme = json.getAsJsonObject("theme");

		assertEquals(2, theme.size());
		assertEquals("#FFAA00", theme.get("name_color").getAsString());
		assertEquals("#66FFFFFF", theme.get("expanded_group_border").getAsString());
	}
}
