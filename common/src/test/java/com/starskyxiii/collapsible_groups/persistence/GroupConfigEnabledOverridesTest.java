package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupConfigEnabledOverridesTest {
	@Test
	void parseEnabledOverridesAcceptsValidBooleans() {
		Map<String, Boolean> overrides = GroupConfig.parseEnabledOverrides("""
			{
				"overrides": {
					"__default_food": false,
					"__kjs_scripted": true
				}
			}
			""");

		assertEquals(Map.of("__default_food", false, "__kjs_scripted", true), overrides);
	}

	@Test
	void parseEnabledOverridesIgnoresInvalidEntries() {
		Map<String, Boolean> overrides = GroupConfig.parseEnabledOverrides("""
			{
				"overrides": {
					"": true,
					"   ": false,
					"string_value": "false",
					"number_value": 1,
					"valid": true
				}
			}
			""");

		assertEquals(Map.of("valid", true), overrides);
	}

	@Test
	void parseEnabledOverridesReturnsEmptyForBadOrMissingShape() {
		assertTrue(GroupConfig.parseEnabledOverrides("[]").isEmpty());
		assertTrue(GroupConfig.parseEnabledOverrides("{\"overrides\": []}").isEmpty());
		assertTrue(GroupConfig.parseEnabledOverrides("{\"other\": {\"__default_food\": false}}").isEmpty());
		assertTrue(GroupConfig.parseEnabledOverrides("not json").isEmpty());
	}

	@Test
	void serializeEnabledOverridesWritesStableObjectShape() {
		String json = GroupConfig.serializeEnabledOverrides(Map.of(
			"__kjs_scripted", true,
			"__default_food", false,
			"", true
		));

		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonObject overrides = root.getAsJsonObject("overrides");
		assertFalse(overrides.has(""));
		assertEquals(false, overrides.get("__default_food").getAsBoolean());
		assertEquals(true, overrides.get("__kjs_scripted").getAsBoolean());
	}
}
