package com.starskyxiii.collapsible_groups.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentPathEnumerationTest {
	@Test
	void enumeratesExactlyTheNodesReachableByTheRestrictedGrammar() {
		JsonElement root = JsonParser.parseString("""
			{
			  "plain": 1,
			  "nested": {"name": "value", "bad:key": {"hidden": true}},
			  "items": [{"value": true}, [7], null],
			  "_ok-2": {"values": ["a", "b"]},
			  "bad:key": {"also_hidden": false}
			}
			""");

		List<ComponentPathNavigator.PathNode> nodes = ComponentPathNavigator.enumerateReachable(root);
		assertEquals(List.of(
			"plain",
			"nested", "nested.name",
			"items", "items[0]", "items[0].value", "items[1]", "items[2]",
			"_ok-2", "_ok-2.values", "_ok-2.values[0]", "_ok-2.values[1]"
		), nodes.stream().map(ComponentPathNavigator.PathNode::path).toList());

		for (ComponentPathNavigator.PathNode node : nodes) {
			JsonElement navigated = ComponentPathNavigator.navigatePath(root, node.path());
			assertNotNull(navigated, node.path());
			assertEquals(node.value(), navigated, node.path());
		}
		assertTrue(nodes.stream().noneMatch(node -> node.path().contains("bad:key")));
		assertTrue(nodes.stream().noneMatch(node -> node.path().equals("items[1][0]")),
			"a second index on the same segment is not representable by the grammar");
	}

	@Test
	void rootArrayAndPrimitiveHaveNoNonBlankReachablePath() {
		assertTrue(ComponentPathNavigator.enumerateReachable(JsonParser.parseString("[1,2]")).isEmpty());
		assertTrue(ComponentPathNavigator.enumerateReachable(JsonParser.parseString("1")).isEmpty());
	}
}
