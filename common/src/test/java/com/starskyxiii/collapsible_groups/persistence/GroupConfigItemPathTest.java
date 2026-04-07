package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.core.GroupFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupConfigItemPathTest {

	@Test
	void itemPathRecordsNullCheck() {
		assertThrows(NullPointerException.class, () -> new GroupFilter.ItemPathStartsWith(null));
		assertThrows(NullPointerException.class, () -> new GroupFilter.ItemPathEndsWith(null));
	}

	@Test
	void parseFilterDeserializesItemPathNodes() {
		JsonObject startsWithNode = JsonParser.parseString("""
			{
				"item_path_starts_with": "gutter_"
			}
			""").getAsJsonObject();
		JsonObject endsWithNode = JsonParser.parseString("""
			{
				"item_path_ends_with": "_chair"
			}
			""").getAsJsonObject();

		GroupFilter startsWith = GroupConfig.parseFilter(startsWithNode);
		GroupFilter endsWith = GroupConfig.parseFilter(endsWithNode);

		assertInstanceOf(GroupFilter.ItemPathStartsWith.class, startsWith);
		assertEquals("gutter_", ((GroupFilter.ItemPathStartsWith) startsWith).prefix());

		assertInstanceOf(GroupFilter.ItemPathEndsWith.class, endsWith);
		assertEquals("_chair", ((GroupFilter.ItemPathEndsWith) endsWith).suffix());
	}

	@Test
	void serializeFilterWritesItemPathNodes() {
		JsonObject startsWithJson = GroupConfig.serializeFilter(new GroupFilter.ItemPathStartsWith("gutter_"));
		JsonObject endsWithJson = GroupConfig.serializeFilter(new GroupFilter.ItemPathEndsWith("_chair"));

		assertEquals("gutter_", startsWithJson.get("item_path_starts_with").getAsString());
		assertFalse(startsWithJson.has("type"));

		assertEquals("_chair", endsWithJson.get("item_path_ends_with").getAsString());
		assertFalse(endsWithJson.has("type"));
	}

	@Test
	void itemPathRoundTripPreservesValues() {
		GroupFilter.ItemPathStartsWith originalStartsWith = new GroupFilter.ItemPathStartsWith("gutter_");
		GroupFilter.ItemPathEndsWith originalEndsWith = new GroupFilter.ItemPathEndsWith("_chair");

		GroupFilter parsedStartsWith = GroupConfig.parseFilter(GroupConfig.serializeFilter(originalStartsWith));
		GroupFilter parsedEndsWith = GroupConfig.parseFilter(GroupConfig.serializeFilter(originalEndsWith));

		assertInstanceOf(GroupFilter.ItemPathStartsWith.class, parsedStartsWith);
		assertEquals(originalStartsWith.prefix(), ((GroupFilter.ItemPathStartsWith) parsedStartsWith).prefix());

		assertInstanceOf(GroupFilter.ItemPathEndsWith.class, parsedEndsWith);
		assertEquals(originalEndsWith.suffix(), ((GroupFilter.ItemPathEndsWith) parsedEndsWith).suffix());
	}
}
