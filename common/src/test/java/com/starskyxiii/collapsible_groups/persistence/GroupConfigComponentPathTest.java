package com.starskyxiii.collapsible_groups.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.starskyxiii.collapsible_groups.group.filter.GroupFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupConfigComponentPathTest {
	@ParameterizedTest
	@ValueSource(strings = {
		"{\"type\":\"item\",\"component\":\"minecraft:custom_data\",\"value\":\"1b\"}",
		"{\"type\":\"item\",\"component\":\"minecraft:custom_name\",\"value\":\"Boat\"}",
		"{\"type\":\"item\",\"component\":\"minecraft:lore\",\"value\":\"[]\"}",
		"{\"type\":\"item\",\"component\":\"example:future\",\"value\":\"opaque\"}",
		"{\"component\":\"example:future\",\"value\":\"opaque\"}",
		"{\"type\":\"fluid\",\"component\":\"example:future\",\"value\":\"opaque\"}",
		"{\"type\":\"item\",\"component\":\"minecraft:custom_data\",\"path\":\"value\",\"value\":\"1b\"}",
		"{\"type\":\"item\",\"component\":\"minecraft:custom_data\",\"path\":\"nested.value\",\"value\":\"1\"}",
		"{\"type\":\"item\",\"component\":\"minecraft:custom_data\",\"path\":\"list[0]\",\"value\":\"x\"}",
		"{\"type\":\"item\",\"component\":\"minecraft:custom_data\",\"path\":\"future[*]\",\"value\":\"x\"}",
		"{\"type\":\"item\",\"component\":\"example:future\",\"path\":\"\",\"value\":\"x\"}",
		"{\"type\":\"item\",\"component\":\"example:future\",\"path\":\"value\",\"value\":\"\"}",
		"{\"type\":\"item\",\"component\":\"\",\"path\":\"value\",\"value\":\"x\"}"
	})
	void componentShapesRemainOpaqueAndByteStructurallyStable(String json) {
		assertOpaque(json);
	}

	@Test
	void hasComponentIsPreservedOpaque() {
		assertOpaque("""
			{"type":"item","component":"minecraft:custom_data","value":"{value:1b}"}
			""");
	}

	@Test
	void componentPathIsPreservedOpaqueWithoutInterpretingItsGrammar() {
		assertOpaque("""
			{"type":"item","component":"minecraft:custom_data","path":"future[*].value","value":"1b"}
			""");
	}

	@Test
	void componentNodeInsideCompositeRoundTripsInPlace() {
		JsonObject source = JsonParser.parseString("""
			{"any":[
			  {"type":"item","id":"minecraft:stone"},
			  {"type":"item","component":"minecraft:custom_data","path":"value","value":"1b"}
			]}
			""").getAsJsonObject();
		GroupFilter.Any parsed = assertInstanceOf(GroupFilter.Any.class, GroupConfig.parseFilter(source));
		assertInstanceOf(GroupFilter.Unsupported.class, parsed.children().get(1));
		assertEquals(source, GroupConfig.serializeFilter(parsed));
	}

	@Test
	void programmaticComponentNodesRetainTheirJsonShape() {
		JsonObject component = GroupConfig.serializeFilter(
			new GroupFilter.HasComponent("minecraft:custom_data", "{value:1b}"));
		JsonObject path = GroupConfig.serializeFilter(
			new GroupFilter.ComponentPath("minecraft:custom_data", "value", "1b"));

		assertTrue(component.has("component"));
		assertTrue(component.has("value"));
		assertFalse(component.has("path"));
		assertTrue(path.has("component"));
		assertTrue(path.has("path"));
		assertTrue(path.has("value"));
	}

	private static void assertOpaque(String json) {
		JsonObject source = JsonParser.parseString(json).getAsJsonObject();
		GroupFilter.Unsupported parsed = assertInstanceOf(
			GroupFilter.Unsupported.class, GroupConfig.parseFilter(source));
		assertEquals(source, parsed.rawJson());
		assertEquals(source, GroupConfig.serializeFilter(parsed));
	}
}
