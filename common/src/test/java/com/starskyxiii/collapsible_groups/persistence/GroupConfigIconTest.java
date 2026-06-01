package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.core.Filters;
import com.starskyxiii.collapsible_groups.core.GroupDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupConfigIconTest {
	@Test
	void fromJsonAcceptsSingleIconString() {
		GroupDefinition group = GroupConfig.fromJson("""
			{
				"id": "single_icon",
				"name": "Single Icon",
				"icon": "minecraft:diamond",
				"filter": {
					"type": "item",
					"id": "minecraft:stone"
				}
			}
			""");

		assertNotNull(group);
		assertEquals(List.of("minecraft:diamond"), group.iconIds());
	}

	@Test
	void fromJsonAcceptsIconArray() {
		GroupDefinition group = GroupConfig.fromJson("""
			{
				"id": "icon_array",
				"name": "Icon Array",
				"icon": ["minecraft:diamond", "minecraft:emerald"],
				"filter": {
					"type": "item",
					"id": "minecraft:stone"
				}
			}
			""");

		assertNotNull(group);
		assertEquals(List.of("minecraft:diamond", "minecraft:emerald"), group.iconIds());
	}

	@Test
	void toJsonWritesSingleIconString() {
		GroupDefinition group = new GroupDefinition(
			"single_icon",
			"Single Icon",
			true,
			Filters.itemId("minecraft:stone"),
			List.of("minecraft:diamond")
		);

		JsonObject json = JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject();

		assertTrue(json.get("icon").isJsonPrimitive());
		assertEquals("minecraft:diamond", json.get("icon").getAsString());
	}

	@Test
	void toJsonWritesIconArray() {
		GroupDefinition group = new GroupDefinition(
			"icon_array",
			"Icon Array",
			true,
			Filters.itemId("minecraft:stone"),
			List.of("minecraft:diamond", "minecraft:emerald")
		);

		JsonObject json = JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject();
		JsonArray icons = json.getAsJsonArray("icon");

		assertEquals(2, icons.size());
		assertEquals("minecraft:diamond", icons.get(0).getAsString());
		assertEquals("minecraft:emerald", icons.get(1).getAsString());
	}

	@Test
	void toJsonOmitsIconWhenEmpty() {
		GroupDefinition group = new GroupDefinition(
			"no_icon",
			"No Icon",
			true,
			Filters.itemId("minecraft:stone")
		);

		JsonObject json = JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject();

		assertFalse(json.has("icon"));
	}
}
