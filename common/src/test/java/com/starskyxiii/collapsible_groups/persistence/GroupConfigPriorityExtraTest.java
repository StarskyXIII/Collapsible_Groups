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

class GroupConfigPriorityExtraTest {
	@Test
	void fromJsonUsesDefaultPriorityAndEmptyExtraWhenMissing() {
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
		assertEquals(0, group.priority());
		assertFalse(group.hasExtra());
		assertEquals(new JsonObject(), group.extra());
	}

	@Test
	void toJsonOmitsDefaultPriorityAndEmptyExtra() {
		GroupDefinition group = new GroupDefinition(
			"default_metadata",
			"Default Metadata",
			true,
			Filters.itemId("minecraft:stone"),
			List.of(),
			GroupTheme.EMPTY
		);

		JsonObject json = JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject();

		assertFalse(json.has("priority"));
		assertFalse(json.has("extra"));
	}

	@Test
	void priorityAndNestedExtraRoundTrip() {
		String source = """
			{
				"id": "metadata_group",
				"name": "Metadata Group",
				"enabled": false,
				"priority": 7,
				"extra": {
					"foreign_flag": true,
					"foreign_tree": {
						"values": [1, "two", { "deep": "keep" }]
					}
				},
				"filter": {
					"type": "item",
					"id": "minecraft:stone"
				}
			}
			""";
		JsonObject sourceJson = JsonParser.parseString(source).getAsJsonObject();

		GroupDefinition group = GroupConfig.fromJson(source);

		assertNotNull(group);
		assertEquals(7, group.priority());
		assertEquals(sourceJson.getAsJsonObject("extra"), group.extra());

		JsonObject serialized = JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject();
		assertEquals(7, serialized.get("priority").getAsInt());
		assertEquals(sourceJson.getAsJsonObject("extra"), serialized.getAsJsonObject("extra"));
	}

	@Test
	void priorityZeroIsOmittedEvenWhenProvidedByJson() {
		GroupDefinition group = GroupConfig.fromJson("""
			{
				"id": "zero_priority",
				"name": "Zero Priority",
				"priority": 0,
				"filter": {
					"type": "item",
					"id": "minecraft:stone"
				}
			}
			""");

		assertNotNull(group);
		JsonObject serialized = JsonParser.parseString(GroupConfig.toJson(group)).getAsJsonObject();

		assertEquals(0, group.priority());
		assertFalse(serialized.has("priority"));
	}
}
